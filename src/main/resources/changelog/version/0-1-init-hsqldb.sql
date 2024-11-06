CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL NOT NULL,
    password TEXT NULL,
    session_id VARCHAR(36) NULL,
    username VARCHAR(24) NULL UNIQUE,
    CONSTRAINT users_pkey PRIMARY KEY (id)
);

DROP INDEX users.users_id_idx IF EXISTS;

CREATE INDEX IF NOT EXISTS username_idx ON users (username ASC);

-- rollback DROP TABLE users IF EXISTS

CREATE TABLE IF NOT EXISTS channels (
    uploader_id VARCHAR(24) NOT NULL,
    uploader VARCHAR(100) NULL,
    uploader_avatar VARCHAR(150) NULL,
    verified BOOLEAN NULL,
    CONSTRAINT channels_pkey PRIMARY KEY (uploader_id)
);

CREATE INDEX IF NOT EXISTS channels_uploader_idx ON channels (uploader ASC);

-- rollback DROP TABLE channels IF EXISTS

CREATE TABLE IF NOT EXISTS pubsub (
    id VARCHAR(24) NOT NULL,
    subbed_at INT8 NULL,
    CONSTRAINT pubsub_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS pubsub_id_idx ON pubsub (id ASC);

-- rollback DROP TABLE pubsub IF EXISTS

CREATE TABLE IF NOT EXISTS playlists (
    id BIGSERIAL NOT NULL,
    name VARCHAR(200) NULL,
    playlist_id UUID NOT NULL UNIQUE DEFAULT uuid(),
    short_description VARCHAR(100) NULL,
    thumbnail VARCHAR(300) NULL,
    owner INT8 NOT NULL,
    CONSTRAINT playlists_pkey PRIMARY KEY (id),
    CONSTRAINT fk_playlists_owner FOREIGN KEY (owner) REFERENCES users(id)
);

-- rollback DROP TABLE playlists IF EXISTS

CREATE TABLE IF NOT EXISTS playlist_videos (
    id VARCHAR(11) NOT NULL,
    duration INT8 NULL,
    thumbnail VARCHAR(400) NULL,
    title VARCHAR(120) NULL,
    uploader_id VARCHAR(24) NOT NULL,
    CONSTRAINT playlist_videos_pkey PRIMARY KEY (id),
    CONSTRAINT fk_playlist_video_uploader_id FOREIGN KEY (uploader_id) REFERENCES channels(uploader_id)
);

CREATE INDEX IF NOT EXISTS playlist_videos_id_idx ON playlist_videos (id ASC);
CREATE INDEX IF NOT EXISTS playlist_videos_uploader_id_idx ON playlist_videos (uploader_id ASC);

-- rollback DROP TABLE playlist_videos IF EXISTS

CREATE TABLE IF NOT EXISTS playlists_videos_ids (
    playlist_id INT8 NOT NULL,
    videos_id VARCHAR(11) NOT NULL,
    videos_order INT4 NOT NULL,
    CONSTRAINT playlists_videos_ids_pkey PRIMARY KEY (playlist_id, videos_order),
    CONSTRAINT fk_playlists_videos_video_id_playlist_video FOREIGN KEY (videos_id) REFERENCES playlist_videos(id),
    CONSTRAINT fk_playlists_videos_playlist_id_playlist FOREIGN KEY (playlist_id) REFERENCES playlists(id)
);

CREATE INDEX IF NOT EXISTS playlists_videos_ids_playlist_id_idx ON playlists_videos_ids (playlist_id ASC);

-- rollback DROP TABLE playlists_videos_ids IF EXISTS

CREATE TABLE IF NOT EXISTS unauthenticated_subscriptions (
    id VARCHAR(24) NOT NULL,
    subscribed_at INT8 NOT NULL,
    CONSTRAINT unauthenticated_subscriptions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_unauthenticated_subscriptions_id_channels FOREIGN KEY (id) REFERENCES channels(uploader_id)
);

CREATE INDEX IF NOT EXISTS unauthenticated_subscriptions_subscribed_at_idx ON unauthenticated_subscriptions (subscribed_at ASC);

-- rollback DROP TABLE unauthenticated_subscriptions IF EXISTS

CREATE INDEX IF NOT EXISTS users_session_id_idx ON users (session_id ASC);

-- rollback DROP INDEX users_session_id_idx IF EXISTS

CREATE TABLE IF NOT EXISTS videos (
    id VARCHAR(11) NOT NULL UNIQUE,
    duration INT8 NULL,
    thumbnail VARCHAR(400) NULL,
    title VARCHAR(120) NULL,
    uploaded INT8 NULL,
    views INT8 NULL,
    uploader_id VARCHAR(24) NOT NULL,
    is_short BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT videos_pkey PRIMARY KEY (id, uploader_id),
    CONSTRAINT fk_videos_channels_uploader_id FOREIGN KEY (uploader_id) REFERENCES channels(uploader_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS videos_id_idx ON videos (id ASC);
CREATE INDEX IF NOT EXISTS video_uploaded_idx ON videos (uploaded ASC);
CREATE INDEX IF NOT EXISTS video_uploader_id_idx ON videos (uploader_id ASC);

-- rollback DROP TABLE videos IF EXISTS

CREATE TABLE IF NOT EXISTS users_subscribed (
    subscriber INT8 NOT NULL,
    channel VARCHAR(24) NOT NULL,
    CONSTRAINT users_subscribed_pkey PRIMARY KEY (subscriber, channel),
    CONSTRAINT fk_subscriber_users FOREIGN KEY (subscriber) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS users_subscribed_subscriber_idx ON users_subscribed (subscriber ASC);
CREATE INDEX IF NOT EXISTS users_subscribed_channel_idx ON users_subscribed (channel ASC);

-- rollback DROP TABLE users_subscribed IF EXISTS

CREATE INDEX IF NOT EXISTS pubsub_subbed_at_idx ON pubsub (subbed_at ASC);

-- rollback DROP INDEX pubsub_subbed_at_idx IF EXISTS

CREATE INDEX IF NOT EXISTS playlists_playlist_id_idx ON playlists (playlist_id ASC);
CREATE INDEX IF NOT EXISTS playlists_owner_idx ON playlists (owner ASC);

-- rollback DROP INDEX playlists_playlist_id_idx IF EXISTS
-- rollback DROP INDEX playlists_owner_idx IF EXISTS
CREATE INDEX IF NOT EXISTS unauthenticated_subscriptions_id_idx ON unauthenticated_subscriptions (id ASC);

-- rollback DROP INDEX unauthenticated_subscriptions_id_idx IF EXISTS
