CREATE TABLE IF NOT EXISTS users (
    id SERIAL NOT NULL,
    password STRING NULL,
    session_id STRING(36) NULL,
    username STRING(24) NULL UNIQUE,
    CONSTRAINT users_pkey PRIMARY KEY (id ASC),
    INDEX users_id_idx (id ASC),
    INDEX username_idx (username ASC)
);

--rollback DROP TABLE IF EXISTS users;
