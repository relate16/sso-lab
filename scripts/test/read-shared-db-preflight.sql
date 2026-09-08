\set ON_ERROR_STOP on
SELECT 'flyway|' || version || '|' || success || '|' || checksum
FROM auth.flyway_schema_history
ORDER BY installed_rank;

SELECT 'production_clients|' || count(*)
FROM auth.oauth2_registered_client
WHERE client_id IN ('hr-client', 'approval-client', 'admin-client');

SELECT 'production_client_fingerprint|' || md5(string_agg(
    concat_ws('|', client_id, client_secret, client_name,
        client_authentication_methods, authorization_grant_types,
        redirect_uris, post_logout_redirect_uris, scopes,
        client_settings, token_settings),
    E'\n' ORDER BY client_id
))
FROM auth.oauth2_registered_client
WHERE client_id IN ('hr-client', 'approval-client', 'admin-client');

SELECT 'local_clients|' || count(*)
FROM auth.oauth2_registered_client
WHERE client_id LIKE 'sso-local-%';
