package com.ssolab.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;

class SessionCookieConfigurationTest {

    @Test
    void configuresPostgresqlUpsertForConcurrentSessionAttributeCreation() {
        SessionCookieConfiguration configuration = new SessionCookieConfiguration();
        JdbcIndexedSessionRepository repository = mock(JdbcIndexedSessionRepository.class);

        var customizer = configuration.postgresSessionQueries();

        customizer.customize(repository);

        verify(repository).setCreateSessionAttributeQuery(argThat(query ->
            query.contains("ON CONFLICT (SESSION_PRIMARY_ID, ATTRIBUTE_NAME)")
                && query.contains("DO UPDATE SET ATTRIBUTE_BYTES = EXCLUDED.ATTRIBUTE_BYTES")
        ));
        assertThat(customizer).isInstanceOf(PostgreSqlJdbcIndexedSessionRepositoryCustomizer.class);
    }
}
