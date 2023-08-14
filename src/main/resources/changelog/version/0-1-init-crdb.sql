CREATE INDEX IF NOT EXISTS users_session_id_idx ON users (session_id ASC) STORING (password, username);

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
    CONSTRAINT videos_pkey PRIMARY KEY (id ASC, uploader_id ASC) USING HASH,
    CONSTRAINT fk_videos_channels_uploader_id FOREIGN KEY (uploader_id) REFERENCES channels(uploader_id),
    INDEX videos_id_idx (id ASC),
    INDEX video_uploaded_idx (uploaded ASC) USING HASH,
    INDEX video_uploader_id_idx (uploader_id ASC) STORING (duration, thumbnail, title, uploaded, views, is_short),
    UNIQUE INDEX videos_id_key (id ASC) STORING (duration, thumbnail, title, uploaded, views, is_short)
);

--rollback DROP TABLE IF EXISTS videos;

CREATE TABLE IF NOT EXISTS users_subscribed (
    subscriber INT8 NOT NULL,
    channel VARCHAR(24) NOT NULL,
    CONSTRAINT users_subscribed_pkey PRIMARY KEY (subscriber ASC, channel ASC) USING HASH,
    CONSTRAINT fk_subscriber_users FOREIGN KEY (subscriber) REFERENCES users(id),
    INDEX users_subscribed_subscriber_idx (subscriber ASC),
    INDEX users_subscribed_channel_idx (channel ASC)
);

--rollback DROP TABLE IF EXISTS users_subscribed;

CREATE INDEX IF NOT EXISTS pubsub_subbed_at_idx ON pubsub (subbed_at ASC) USING HASH;

--rollback DROP INDEX IF EXISTS pubsub_subbed_at_idx;

CREATE INDEX IF NOT EXISTS playlists_playlist_id_idx ON playlists (playlist_id ASC) STORING (name, short_description, thumbnail, owner);
CREATE INDEX IF NOT EXISTS playlists_owner_idx ON playlists (owner ASC) STORING (name, short_description, thumbnail, playlist_id);

--rollback DROP INDEX IF EXISTS playlists_playlist_id_idx;
--rollback DROP INDEX IF EXISTS playlists_owner_idx;

CREATE INDEX IF NOT EXISTS unauthenticated_subscriptions_id_idx ON unauthenticated_subscriptions (id ASC) USING HASH STORING (subscribed_at);

--rollback DROP INDEX IF EXISTS unauthenticated_subscriptions_id_idx;
