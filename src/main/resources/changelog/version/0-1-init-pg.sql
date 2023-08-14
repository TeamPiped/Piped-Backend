CREATE INDEX IF NOT EXISTS users_session_id_idx ON users (session_id ASC);

--rollback DROP INDEX IF EXISTS users_session_id_idx;

CREATE TABLE IF NOT EXISTS videos (
    id VARCHAR(11) NOT NULL UNIQUE,
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

CREATE TABLE IF NOT EXISTS users_subscribed (
    subscriber INT8 NOT NULL,
    channel VARCHAR(24) NOT NULL,
    CONSTRAINT users_subscribed_pkey PRIMARY KEY (subscriber, channel),
    CONSTRAINT fk_subscriber_users FOREIGN KEY (subscriber) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS users_subscribed_subscriber_idx ON users_subscribed (subscriber ASC);
CREATE INDEX IF NOT EXISTS users_subscribed_channel_idx ON users_subscribed (channel ASC);

--rollback DROP TABLE IF EXISTS users_subscribed;

CREATE INDEX IF NOT EXISTS pubsub_subbed_at_idx ON pubsub (subbed_at ASC);

--rollback DROP INDEX IF EXISTS pubsub_subbed_at_idx;

CREATE INDEX IF NOT EXISTS playlists_playlist_id_idx ON playlists (playlist_id ASC);
CREATE INDEX IF NOT EXISTS playlists_owner_idx ON playlists (owner ASC);

--rollback DROP INDEX IF EXISTS playlists_playlist_id_idx;
--rollback DROP INDEX IF EXISTS playlists_owner_idx;

CREATE INDEX IF NOT EXISTS unauthenticated_subscriptions_id_idx ON unauthenticated_subscriptions (id ASC);

--rollback DROP INDEX IF EXISTS unauthenticated_subscriptions_id_idx;
