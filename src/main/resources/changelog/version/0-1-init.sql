CREATE TABLE IF NOT EXISTS users (
    id SERIAL NOT NULL,
    password TEXT NULL,
    session_id VARCHAR(36) NULL,
    username VARCHAR(24) NULL UNIQUE,
    CONSTRAINT users_pkey PRIMARY KEY (id)
);

DROP INDEX IF EXISTS users_id_idx;

CREATE INDEX IF NOT EXISTS username_idx ON users (username ASC);

--rollback DROP INDEX IF EXISTS username_idx;
--rollback DROP TABLE IF EXISTS users;

CREATE TABLE IF NOT EXISTS channels (
    uploader_id VARCHAR(24) NOT NULL,
    uploader VARCHAR(100) NULL,
    uploader_avatar VARCHAR(150) NULL,
    verified BOOL NULL,
    CONSTRAINT channels_pkey PRIMARY KEY (uploader_id)
);

CREATE INDEX IF NOT EXISTS channels_uploader_idx ON channels (uploader ASC);

--rollback DROP INDEX IF EXISTS channels_uploader_idx;
--rollback DROP TABLE IF EXISTS channels;
