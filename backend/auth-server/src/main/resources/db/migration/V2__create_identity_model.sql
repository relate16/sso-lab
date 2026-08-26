CREATE TABLE users (
    id UUID NOT NULL,
    user_id VARCHAR(30) NOT NULL,
    normalized_user_id VARCHAR(30) NOT NULL,
    username VARCHAR(100) NOT NULL,
    email_ciphertext BYTEA NOT NULL,
    email_iv BYTEA NOT NULL,
    email_key_version INTEGER NOT NULL,
    email_lookup_hash BYTEA NOT NULL,
    account_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT users_pk PRIMARY KEY (id),
    CONSTRAINT users_normalized_user_id_uk UNIQUE (normalized_user_id),
    CONSTRAINT users_email_lookup_hash_uk UNIQUE (email_lookup_hash),
    CONSTRAINT users_user_id_lowercase_ck CHECK (user_id = normalized_user_id),
    CONSTRAINT users_email_iv_length_ck CHECK (octet_length(email_iv) = 12),
    CONSTRAINT users_email_hash_length_ck CHECK (octet_length(email_lookup_hash) = 32),
    CONSTRAINT users_email_key_version_ck CHECK (email_key_version > 0),
    CONSTRAINT users_account_status_ck CHECK (account_status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE roles (
    id UUID NOT NULL,
    name VARCHAR(30) NOT NULL,
    CONSTRAINT roles_pk PRIMARY KEY (id),
    CONSTRAINT roles_name_uk UNIQUE (name),
    CONSTRAINT roles_name_ck CHECK (name IN ('USER', 'ADMIN'))
);

INSERT INTO roles (id, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'USER'),
    ('00000000-0000-0000-0000-000000000002', 'ADMIN');

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT user_roles_pk PRIMARY KEY (user_id, role_id),
    CONSTRAINT user_roles_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT user_roles_role_fk FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
);

CREATE INDEX user_roles_role_id_ix ON user_roles (role_id);

CREATE TABLE groups (
    id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    parent_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT groups_pk PRIMARY KEY (id),
    CONSTRAINT groups_parent_fk FOREIGN KEY (parent_id) REFERENCES groups (id) ON DELETE RESTRICT,
    CONSTRAINT groups_not_own_parent_ck CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT groups_sibling_name_uk UNIQUE NULLS NOT DISTINCT (parent_id, normalized_name)
);

CREATE INDEX groups_parent_id_ix ON groups (parent_id);

CREATE TABLE user_groups (
    user_id UUID NOT NULL,
    group_id UUID NOT NULL,
    CONSTRAINT user_groups_pk PRIMARY KEY (user_id, group_id),
    CONSTRAINT user_groups_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT user_groups_group_fk FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE RESTRICT
);

CREATE INDEX user_groups_group_id_ix ON user_groups (group_id);

CREATE TABLE system_config (
    config_key VARCHAR(100) NOT NULL,
    config_value VARCHAR(200) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT system_config_pk PRIMARY KEY (config_key),
    CONSTRAINT system_config_key_format_ck CHECK (config_key ~ '^[A-Z][A-Z0-9_]*$')
);
