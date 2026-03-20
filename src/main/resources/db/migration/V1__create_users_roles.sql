-- V1: Users, Roles, and RBAC
CREATE TABLE roles (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Seed roles
INSERT INTO roles (name) VALUES
    ('ADMIN'),
    ('MAKER'),
    ('CHECKER'),
    ('MERCHANT_USER');

-- Seed default admin user (password: Admin@123 — CHANGE IN PRODUCTION)
INSERT INTO users (username, password_hash, email, status)
VALUES ('admin',
        '$2a$12$pXgQk/Q9X1Jf4YTOGKdKsuVs4F9tDdOmjMT6P6pJWz1J3sVqxH3dW',
        'admin@minipay.com',
        'ACTIVE');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'ADMIN';

-- Indexes
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email    ON users(email);
CREATE INDEX idx_users_status   ON users(status);
