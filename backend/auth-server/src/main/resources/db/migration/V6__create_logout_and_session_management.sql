ALTER TABLE user_session_metadata
    ADD COLUMN public_id UUID,
    ADD COLUMN device_label VARCHAR(160) NOT NULL DEFAULT 'Unknown browser';

UPDATE user_session_metadata SET public_id = gen_random_uuid() WHERE public_id IS NULL;

ALTER TABLE user_session_metadata
    ALTER COLUMN public_id SET NOT NULL,
    ADD CONSTRAINT user_session_metadata_public_id_uk UNIQUE (public_id);

CREATE TABLE oidc_client_sessions (
    id UUID NOT NULL,
    auth_session_id VARCHAR(128) NOT NULL,
    user_id UUID NOT NULL,
    authorization_id VARCHAR(100),
    registered_client_id VARCHAR(100) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    oidc_sid VARCHAR(64) NOT NULL,
    backchannel_logout_uri VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_accessed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT oidc_client_sessions_pk PRIMARY KEY (id),
    CONSTRAINT oidc_client_sessions_auth_session_fk FOREIGN KEY (auth_session_id)
        REFERENCES user_session_metadata (session_id) ON DELETE CASCADE,
    CONSTRAINT oidc_client_sessions_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT oidc_client_sessions_client_session_uk
        UNIQUE (auth_session_id, registered_client_id),
    CONSTRAINT oidc_client_sessions_sid_length_ck CHECK (char_length(oidc_sid) = 43)
);

CREATE INDEX oidc_client_sessions_user_ix ON oidc_client_sessions (user_id);
CREATE INDEX oidc_client_sessions_authorization_ix
    ON oidc_client_sessions (authorization_id);
CREATE INDEX oidc_client_sessions_sid_ix ON oidc_client_sessions (oidc_sid);
