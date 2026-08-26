package com.ssolab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssolab.auth.identity.model.IdentityGroupEntity;
import com.ssolab.auth.identity.model.RoleName;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.identity.service.CreateUserIdentityCommand;
import com.ssolab.auth.identity.service.GroupHierarchyService;
import com.ssolab.auth.identity.service.IdentityConflictException;
import com.ssolab.auth.identity.service.IdentityMembershipService;
import com.ssolab.auth.identity.service.InvalidGroupHierarchyException;
import com.ssolab.auth.identity.service.UserIdentityService;
import com.ssolab.auth.systemconfig.SystemConfigDefinitions;
import com.ssolab.auth.systemconfig.TypedSystemConfigService;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AuthServerPostgresqlIntegrationTest {

    private static final String TEST_EMAIL_ENCRYPTION_KEY = testKey((byte) 0x31);
    private static final String TEST_EMAIL_LOOKUP_HMAC_KEY = testKey((byte) 0x72);

    @Container
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("SSO_EMAIL_ENCRYPTION_KEY", () -> TEST_EMAIL_ENCRYPTION_KEY);
        registry.add("SSO_EMAIL_LOOKUP_HMAC_KEY", () -> TEST_EMAIL_LOOKUP_HMAC_KEY);
        OidcTestProperties.register(registry);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    HealthEndpoint healthEndpoint;

    @Autowired
    UserIdentityService userIdentityService;

    @Autowired
    UserIdentityRepository userIdentityRepository;

    @Autowired
    GroupHierarchyService groupHierarchyService;

    @Autowired
    IdentityMembershipService membershipService;

    @Autowired
    TypedSystemConfigService systemConfigService;

    @Test
    void appliesFlywayMigrationAndReportsHealthy() {
        Integer appliedMigrationCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.flyway_schema_history WHERE version IN ('1', '2')",
            Integer.class
        );
        Integer sessionTableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = 'auth' AND table_name = 'spring_session'",
            Integer.class
        );

        Integer identityTableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'auth' " +
                "AND table_name IN ('users', 'roles', 'user_roles', 'groups', 'user_groups', 'system_config')",
            Integer.class
        );

        assertThat(appliedMigrationCount).isEqualTo(2);
        assertThat(sessionTableCount).isEqualTo(1);
        assertThat(identityTableCount).isEqualTo(6);
        assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @Transactional
    void persistsEncryptedIdentityRolesAndHierarchicalGroups() {
        UserIdentityEntity user = userIdentityService.createIdentity(
            new CreateUserIdentityCommand("Phase2.User", "  페이즈 투  ", " Phase2@Example.COM ")
        );
        IdentityGroupEntity company = groupHierarchyService.createGroup("회사", null);
        IdentityGroupEntity engineering = groupHierarchyService.createGroup("개발본부", company.getId());
        IdentityGroupEntity backend = groupHierarchyService.createGroup("백엔드팀", engineering.getId());
        membershipService.assignGroup(user.getId(), backend.getId());

        UserIdentityEntity reloaded = userIdentityRepository.findById(user.getId()).orElseThrow();
        byte[] storedCiphertext = jdbcTemplate.queryForObject(
            "SELECT email_ciphertext FROM auth.users WHERE id = ?",
            byte[].class,
            user.getId()
        );
        Integer ivLength = jdbcTemplate.queryForObject(
            "SELECT octet_length(email_iv) FROM auth.users WHERE id = ?",
            Integer.class,
            user.getId()
        );
        Integer hashLength = jdbcTemplate.queryForObject(
            "SELECT octet_length(email_lookup_hash) FROM auth.users WHERE id = ?",
            Integer.class,
            user.getId()
        );
        Integer plaintextEmailColumnCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'auth' " +
                "AND table_name = 'users' AND column_name = 'email'",
            Integer.class
        );

        assertThat(reloaded.getUserId()).isEqualTo("phase2.user");
        assertThat(reloaded.getUsername()).isEqualTo("페이즈 투");
        assertThat(reloaded.getRoles()).extracting(role -> role.getName())
            .containsExactly(RoleName.USER);
        assertThat(reloaded.getGroups()).extracting(group -> group.getId())
            .containsExactly(backend.getId());
        assertThat(userIdentityService.decryptEmail(reloaded)).isEqualTo("phase2@example.com");
        assertThat(userIdentityService.findByEmail("PHASE2@example.com"))
            .map(UserIdentityEntity::getId)
            .contains(user.getId());
        assertThat(storedCiphertext)
            .isNotEqualTo("phase2@example.com".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(ivLength).isEqualTo(12);
        assertThat(hashLength).isEqualTo(32);
        assertThat(plaintextEmailColumnCount).isZero();
        assertThat(groupHierarchyService.fullPath(backend.getId()))
            .isEqualTo("/회사/개발본부/백엔드팀");
    }

    @Test
    @Transactional
    void enforcesIdentityUniquenessAndGroupSafetyRules() {
        userIdentityService.createIdentity(
            new CreateUserIdentityCommand("unique.user", "사용자", "unique@example.com")
        );

        assertThatThrownBy(() -> userIdentityService.createIdentity(
            new CreateUserIdentityCommand("UNIQUE.USER", "다른 사용자", "other@example.com")
        )).isInstanceOf(IdentityConflictException.class);

        IdentityGroupEntity root = groupHierarchyService.createGroup("Root", null);
        IdentityGroupEntity child = groupHierarchyService.createGroup("Child", root.getId());

        assertThatThrownBy(() -> groupHierarchyService.createGroup(" root ", null))
            .isInstanceOf(IdentityConflictException.class);
        assertThatThrownBy(() -> groupHierarchyService.moveGroup(root.getId(), child.getId()))
            .isInstanceOf(InvalidGroupHierarchyException.class);
        assertThatThrownBy(() -> groupHierarchyService.deleteGroup(root.getId()))
            .isInstanceOf(IdentityConflictException.class)
            .hasMessageContaining("child");
    }

    @Test
    @Transactional
    void readsAndOverridesTypedSystemConfiguration() {
        assertThat(systemConfigService.identityPolicy().accessTokenTtl())
            .isEqualTo(Duration.ofMinutes(5));
        assertThat(systemConfigService.identityPolicy().emailOtpMaxAttempts()).isEqualTo(5);

        systemConfigService.update(SystemConfigDefinitions.ACCESS_TOKEN_TTL, Duration.ofMinutes(10));

        assertThat(systemConfigService.get(SystemConfigDefinitions.ACCESS_TOKEN_TTL))
            .isEqualTo(Duration.ofMinutes(10));
        Set<String> configuredKeys = Set.copyOf(
            jdbcTemplate.queryForList("SELECT config_key FROM auth.system_config", String.class)
        );
        assertThat(configuredKeys).containsExactly(SystemConfigDefinitions.ACCESS_TOKEN_TTL.key());
    }

    private static String testKey(byte fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, fill);
        return Base64.getEncoder().encodeToString(key);
    }
}
