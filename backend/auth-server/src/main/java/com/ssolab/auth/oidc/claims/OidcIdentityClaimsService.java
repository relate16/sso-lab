package com.ssolab.auth.oidc.claims;

import com.ssolab.auth.identity.crypto.EmailCipher;
import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.IdentityGroupEntity;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.passwordless.session.PasswordlessPrincipal;
import com.ssolab.auth.oidc.logout.OidcSessionIdHasher;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OidcIdentityClaimsService {

    public static final String ACR_LOA_1 = "urn:jb:loa:1";

    private final UserIdentityRepository userRepository;
    private final EmailCipher emailCipher;

    public OidcIdentityClaimsService(
        UserIdentityRepository userRepository,
        EmailCipher emailCipher
    ) {
        this.userRepository = userRepository;
        this.emailCipher = emailCipher;
    }

    @Transactional(readOnly = true)
    public OidcIdentityClaims load(PasswordlessPrincipal principal, Set<String> scopes) {
        UserIdentityEntity user = userRepository.findById(principal.userId())
            .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
            .orElseThrow(this::inactiveIdentity);

        List<String> roles = user.getRoles().stream()
            .map(role -> role.getName().name())
            .sorted()
            .collect(Collectors.toCollection(ArrayList::new));
        List<String> groups = user.getGroups().stream()
            .map(this::fullPath)
            .sorted()
            .collect(Collectors.toCollection(ArrayList::new));

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.getId().toString());
        claims.put("userId", user.getUserId());
        claims.put("preferred_username", user.getUserId());
        claims.put("username", user.getUsername());
        claims.put("name", user.getUsername());
        if (scopes.contains("email")) {
            claims.put("email", emailCipher.decrypt(user.encryptedEmail()));
        }
        claims.put("roles", roles);
        claims.put("groups", groups);
        claims.put("amr", new ArrayList<>(List.of(
            principal.authenticationMethod().name().toLowerCase(Locale.ROOT)
        )));
        claims.put("acr", ACR_LOA_1);
        claims.put("auth_time", Date.from(principal.authenticatedAt()));
        claims.put("sid", OidcSessionIdHasher.hash(principal.sessionId()));
        return new OidcIdentityClaims(claims);
    }

    private String fullPath(IdentityGroupEntity group) {
        ArrayDeque<String> segments = new ArrayDeque<>();
        IdentityGroupEntity current = group;
        int depth = 0;
        while (current != null) {
            if (++depth > 100) {
                throw new IllegalStateException("identity group hierarchy exceeds safe depth");
            }
            segments.addFirst(current.getName());
            current = current.getParent();
        }
        return "/" + String.join("/", segments);
    }

    private OAuth2AuthenticationException inactiveIdentity() {
        return new OAuth2AuthenticationException(
            new OAuth2Error(
                OAuth2ErrorCodes.INVALID_GRANT,
                "identity is unavailable",
                null
            )
        );
    }
}
