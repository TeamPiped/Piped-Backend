CREATE TABLE IF NOT EXISTS users (
    id SERIAL NOT NULL,
    password TEXT NULL,
    session_id VARCHAR(36) NULL,
    username VARCHAR(24) NULL UNIQUE,
    CONSTRAINT users_pkey PRIMARY KEY (id)
);

DROP INDEX IF EXISTS users_id_idx;

CREATE INDEX IF NOT EXISTS username_idx ON users (username ASC);

--rollback DROP TABLE IF EXISTS users;
