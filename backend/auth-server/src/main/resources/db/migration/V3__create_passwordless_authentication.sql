CREATE TABLE pending_registrations (
    id UUID NOT NULL,
    user_id VARCHAR(30) NOT NULL,
    normalized_user_id VARCHAR(30) NOT NULL,
    username VARCHAR(100) NOT NULL,
    email_ciphertext BYTEA NOT NULL,
    email_iv BYTEA NOT NULL,
    email_key_version INTEGER NOT NULL,
    email_lookup_hash BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pending_registrations_pk PRIMARY KEY (id),
    CONSTRAINT pending_registrations_user_id_lowercase_ck
        CHECK (user_id = normalized_user_id),
    CONSTRAINT pending_registrations_email_iv_length_ck
        CHECK (octet_length(email_iv) = 12),
    CONSTRAINT pending_registrations_email_hash_length_ck
        CHECK (octet_length(email_lookup_hash) = 32),
    CONSTRAINT pending_registrations_email_key_version_ck
        CHECK (email_key_version > 0),
    CONSTRAINT pending_registrations_expiry_ck CHECK (expires_at > created_at)
);

CREATE INDEX pending_registrations_user_id_ix
    ON pending_registrations (normalized_user_id);
CREATE INDEX pending_registrations_email_hash_ix
    ON pending_registrations (email_lookup_hash);
CREATE INDEX pending_registrations_expiry_ix
    ON pending_registrations (expires_at);

CREATE TABLE email_otp_challenges (
    id UUID NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    user_id UUID,
    pending_registration_id UUID,
    verifier_hash BYTEA NOT NULL,
    failed_attempts INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resend_available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT email_otp_challenges_pk PRIMARY KEY (id),
    CONSTRAINT email_otp_challenges_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT email_otp_challenges_pending_fk FOREIGN KEY (pending_registration_id)
        REFERENCES pending_registrations (id) ON DELETE CASCADE,
    CONSTRAINT email_otp_challenges_purpose_ck
        CHECK (purpose IN ('SIGNUP', 'LOGIN', 'EMAIL_CHANGE', 'ADMIN_REAUTH')),
    CONSTRAINT email_otp_challenges_verifier_length_ck
        CHECK (octet_length(verifier_hash) = 32),
    CONSTRAINT email_otp_challenges_attempts_ck
        CHECK (failed_attempts >= 0 AND max_attempts > 0 AND failed_attempts <= max_attempts),
    CONSTRAINT email_otp_challenges_context_ck CHECK (
        (purpose = 'SIGNUP' AND pending_registration_id IS NOT NULL AND user_id IS NULL)
        OR
        (purpose <> 'SIGNUP' AND pending_registration_id IS NULL)
    ),
    CONSTRAINT email_otp_challenges_expiry_ck CHECK (expires_at > created_at)
);

CREATE INDEX email_otp_challenges_user_purpose_ix
    ON email_otp_challenges (user_id, purpose);
CREATE INDEX email_otp_challenges_pending_ix
    ON email_otp_challenges (pending_registration_id);
CREATE INDEX email_otp_challenges_expiry_ix
    ON email_otp_challenges (expires_at);

CREATE TABLE totp_credentials (
    user_id UUID NOT NULL,
    secret_ciphertext BYTEA NOT NULL,
    secret_iv BYTEA NOT NULL,
    secret_key_version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_verified_counter BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    activated_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT totp_credentials_pk PRIMARY KEY (user_id),
    CONSTRAINT totp_credentials_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT totp_credentials_iv_length_ck CHECK (octet_length(secret_iv) = 12),
    CONSTRAINT totp_credentials_key_version_ck CHECK (secret_key_version > 0),
    CONSTRAINT totp_credentials_status_ck CHECK (status IN ('PENDING', 'ACTIVE')),
    CONSTRAINT totp_credentials_activation_ck CHECK (
        (status = 'PENDING' AND activated_at IS NULL)
        OR
        (status = 'ACTIVE' AND activated_at IS NOT NULL)
    )
);

CREATE TABLE recovery_codes (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT recovery_codes_pk PRIMARY KEY (id),
    CONSTRAINT recovery_codes_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX recovery_codes_user_active_ix
    ON recovery_codes (user_id, used_at, invalidated_at);
CREATE INDEX recovery_codes_batch_ix ON recovery_codes (batch_id);

CREATE TABLE user_session_metadata (
    session_id VARCHAR(128) NOT NULL,
    user_id UUID NOT NULL,
    authentication_method VARCHAR(30) NOT NULL,
    session_scope VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_accessed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    absolute_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reauthenticated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT user_session_metadata_pk PRIMARY KEY (session_id),
    CONSTRAINT user_session_metadata_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT user_session_metadata_method_ck
        CHECK (authentication_method IN ('EMAIL_OTP', 'TOTP', 'RECOVERY_CODE')),
    CONSTRAINT user_session_metadata_scope_ck
        CHECK (session_scope IN ('NORMAL', 'RECOVERY_ONLY')),
    CONSTRAINT user_session_metadata_expiry_ck CHECK (absolute_expires_at > created_at)
);

CREATE INDEX user_session_metadata_user_active_ix
    ON user_session_metadata (user_id, invalidated_at);
CREATE INDEX user_session_metadata_expiry_ix
    ON user_session_metadata (absolute_expires_at);
