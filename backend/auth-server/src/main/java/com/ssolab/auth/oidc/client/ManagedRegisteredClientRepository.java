package com.ssolab.auth.oidc.client;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

public class ManagedRegisteredClientRepository extends JdbcRegisteredClientRepository {

    private static final String UPDATE_SQL = """
        UPDATE oauth2_registered_client SET
          client_id=?, client_id_issued_at=?, client_secret=?, client_secret_expires_at=?,
          client_name=?, client_authentication_methods=?, authorization_grant_types=?,
          redirect_uris=?, post_logout_redirect_uris=?, scopes=?, client_settings=?, token_settings=?
        WHERE id=?
        """;

    private final JdbcOperations jdbcOperations;

    public ManagedRegisteredClientRepository(JdbcOperations jdbcOperations) {
        super(jdbcOperations);
        this.jdbcOperations = jdbcOperations;
    }

    public void update(RegisteredClient client) {
        List<SqlParameterValue> insert = getRegisteredClientParametersMapper().apply(client);
        List<Object> update = new ArrayList<>(insert.subList(1, insert.size()));
        update.add(insert.getFirst());
        int affected = jdbcOperations.update(UPDATE_SQL, update.toArray());
        if (affected != 1) {
            throw new IllegalStateException("registered OIDC client update did not affect one row");
        }
    }
}
