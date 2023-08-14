CREATE INDEX IF NOT EXISTS users_session_id_idx ON users (session_id ASC);

--rollback DROP INDEX IF EXISTS users_session_id_idx;

CREATE TABLE IF NOT EXISTS videos (
    id VARCHAR(16) NOT NULL UNIQUE,
    duration INT8 NULL,
    thumbnail VARCHAR(400) NULL,
    title VARCHAR(120) NULL,
    uploaded INT8 NULL,
    views INT8 NULL,
    uploader_id VARCHAR(24) NOT NULL,
    is_short BOOL NOT NULL DEFAULT false,
    CONSTRAINT videos_pkey PRIMARY KEY (id, uploader_id),
    CONSTRAINT fk_videos_channels_uploader_id FOREIGN KEY (uploader_id) REFERENCES channels(uploader_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS videos_id_idx ON videos (id ASC);
CREATE INDEX IF NOT EXISTS video_uploaded_idx ON videos (uploaded ASC);
CREATE INDEX IF NOT EXISTS video_uploader_id_idx ON videos (uploader_id ASC);

--rollback DROP TABLE IF EXISTS videos;
