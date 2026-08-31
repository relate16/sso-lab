CREATE TABLE pending_email_changes (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    email_ciphertext BYTEA NOT NULL,
    email_iv BYTEA NOT NULL,
    email_key_version INTEGER NOT NULL,
    email_lookup_hash BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pending_email_changes_pk PRIMARY KEY (id),
    CONSTRAINT pending_email_changes_id_user_uk UNIQUE (id, user_id),
    CONSTRAINT pending_email_changes_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT pending_email_changes_email_iv_length_ck
        CHECK (octet_length(email_iv) = 12),
    CONSTRAINT pending_email_changes_email_hash_length_ck
        CHECK (octet_length(email_lookup_hash) = 32),
    CONSTRAINT pending_email_changes_email_key_version_ck
        CHECK (email_key_version > 0),
    CONSTRAINT pending_email_changes_expiry_ck CHECK (expires_at > created_at),
    CONSTRAINT pending_email_changes_terminal_state_ck
        CHECK (consumed_at IS NULL OR invalidated_at IS NULL)
);

CREATE INDEX pending_email_changes_user_active_ix
    ON pending_email_changes (user_id, consumed_at, invalidated_at, expires_at);

ALTER TABLE email_otp_challenges
    ADD COLUMN pending_email_change_id UUID;

ALTER TABLE email_otp_challenges
    ADD CONSTRAINT email_otp_challenges_pending_email_change_fk
        FOREIGN KEY (pending_email_change_id, user_id)
        REFERENCES pending_email_changes (id, user_id) ON DELETE CASCADE;

ALTER TABLE email_otp_challenges
    DROP CONSTRAINT email_otp_challenges_context_ck,
    ADD CONSTRAINT email_otp_challenges_context_ck CHECK (
        (purpose = 'SIGNUP'
            AND pending_registration_id IS NOT NULL
            AND pending_email_change_id IS NULL
            AND user_id IS NULL)
        OR
        (purpose = 'LOGIN'
            AND pending_registration_id IS NULL
            AND pending_email_change_id IS NULL)
        OR
        (purpose = 'EMAIL_CHANGE'
            AND pending_registration_id IS NULL
            AND pending_email_change_id IS NOT NULL
            AND user_id IS NOT NULL)
        OR
        (purpose = 'ADMIN_REAUTH'
            AND pending_registration_id IS NULL
            AND pending_email_change_id IS NULL
            AND user_id IS NOT NULL)
    );

CREATE INDEX email_otp_challenges_pending_email_change_ix
    ON email_otp_challenges (pending_email_change_id);

ALTER TABLE audit_logs
    DROP CONSTRAINT audit_event_type_ck,
    ADD CONSTRAINT audit_event_type_ck CHECK (event_type IN (
        'BOOTSTRAP_ADMIN_CLAIMED',
        'ACCOUNT_SUSPENDED', 'ACCOUNT_RESUMED', 'ACCOUNT_DELETED',
        'GROUP_CREATED', 'GROUP_UPDATED', 'GROUP_MOVED', 'GROUP_DELETED',
        'GROUP_ASSIGNED', 'GROUP_REMOVED',
        'ROLE_ASSIGNED', 'ROLE_REMOVED',
        'USERNAME_CHANGED', 'EMAIL_CHANGED',
        'ADMIN_REAUTH_SUCCESS', 'ADMIN_REAUTH_FAILED', 'ADMIN_EMAIL_REVEALED'
    )),
    DROP CONSTRAINT audit_source_ck,
    ADD CONSTRAINT audit_source_ck
        CHECK (source IN ('AUTH_WEB', 'ADMIN_WEB', 'INTERNAL_API', 'BOOTSTRAP'));
