CREATE TABLE bootstrap_admin_state (
    singleton_id SMALLINT PRIMARY KEY,
    email_lookup_hash BYTEA NOT NULL,
    claimed_by UUID,
    claimed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT bootstrap_admin_singleton_ck CHECK (singleton_id = 1),
    CONSTRAINT bootstrap_admin_hash_ck CHECK (octet_length(email_lookup_hash) = 32),
    CONSTRAINT bootstrap_admin_claim_ck CHECK (
        (claimed_by IS NULL AND claimed_at IS NULL)
        OR (claimed_by IS NOT NULL AND claimed_at IS NOT NULL)
    )
);

CREATE TABLE admin_reauth_proofs (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    proof_hash BYTEA NOT NULL UNIQUE,
    authentication_method VARCHAR(20) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT admin_reauth_proof_hash_ck CHECK (octet_length(proof_hash) = 32),
    CONSTRAINT admin_reauth_method_ck CHECK (authentication_method IN ('EMAIL_OTP', 'TOTP')),
    CONSTRAINT admin_reauth_expiry_ck CHECK (expires_at > issued_at)
);

CREATE INDEX admin_reauth_actor_expiry_ix
    ON admin_reauth_proofs (actor_id, expires_at, revoked_at);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    actor_id UUID,
    target_id UUID,
    success BOOLEAN NOT NULL,
    source VARCHAR(30) NOT NULL,
    trace_id VARCHAR(100),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT audit_event_type_ck CHECK (event_type IN (
        'BOOTSTRAP_ADMIN_CLAIMED',
        'ACCOUNT_SUSPENDED', 'ACCOUNT_RESUMED',
        'GROUP_CREATED', 'GROUP_UPDATED', 'GROUP_MOVED', 'GROUP_DELETED',
        'GROUP_ASSIGNED', 'GROUP_REMOVED',
        'ROLE_ASSIGNED', 'ROLE_REMOVED',
        'ADMIN_REAUTH_SUCCESS', 'ADMIN_REAUTH_FAILED', 'ADMIN_EMAIL_REVEALED'
    )),
    CONSTRAINT audit_source_ck CHECK (source IN ('ADMIN_WEB', 'INTERNAL_API', 'BOOTSTRAP'))
);

CREATE INDEX audit_logs_occurred_at_ix ON audit_logs (occurred_at DESC);
CREATE INDEX audit_logs_actor_ix ON audit_logs (actor_id, occurred_at DESC);
CREATE INDEX audit_logs_target_ix ON audit_logs (target_id, occurred_at DESC);
