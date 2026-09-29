CREATE TABLE users (
                       id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       email         VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       full_name     VARCHAR(255) NOT NULL,
                       role          VARCHAR(20)  NOT NULL,
                       manager_id    BIGINT,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

                       CONSTRAINT fk_users_manager FOREIGN KEY (manager_id) REFERENCES users (id),
                       CONSTRAINT chk_users_role CHECK (role IN ('EMPLOYEE', 'MANAGER', 'ACCOUNTANT'))
);

CREATE INDEX idx_users_manager_id ON users (manager_id);