CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL NOT NULL,
    password TEXT NULL,
    session_id VARCHAR(36) NULL,
    username VARCHAR(24) NULL UNIQUE,
    CONSTRAINT users_pkey PRIMARY KEY (id)
);

DROP INDEX IF EXISTS users_id_idx;

CREATE INDEX IF NOT EXISTS username_idx ON users (username ASC);

--rollback DROP TABLE IF EXISTS users;

CREATE TABLE IF NOT EXISTS channels (
    uploader_id VARCHAR(24) NOT NULL,
    uploader VARCHAR(100) NULL,
    uploader_avatar VARCHAR(150) NULL,
    verified BOOL NULL,
    CONSTRAINT channels_pkey PRIMARY KEY (uploader_id)
);

CREATE INDEX IF NOT EXISTS channels_uploader_idx ON channels (uploader ASC);

--rollback DROP TABLE IF EXISTS channels;

CREATE TABLE IF NOT EXISTS pubsub (
    id VARCHAR(24) NOT NULL,
    subbed_at INT8 NULL,
    CONSTRAINT pubsub_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS pubsub_id_idx ON pubsub (id ASC);

--rollback DROP TABLE IF EXISTS pubsub;

CREATE TABLE IF NOT EXISTS playlists (
    id BIGSERIAL NOT NULL,
    name VARCHAR(200) NULL,
    playlist_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    short_description VARCHAR(100) NULL,
    thumbnail VARCHAR(300) NULL,
    owner INT8 NOT NULL,
    CONSTRAINT playlists_pkey PRIMARY KEY (id),
    CONSTRAINT fk_playlists_owner FOREIGN KEY (owner) REFERENCES users(id)
);

--rollback DROP TABLE IF EXISTS playlists;

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

--rollback DROP TABLE IF EXISTS playlist_videos;

CREATE TABLE IF NOT EXISTS playlists_videos_ids (
    playlist_id INT8 NOT NULL,
    videos_id VARCHAR(11) NOT NULL,
    videos_order INT4 NOT NULL,
    CONSTRAINT playlists_videos_ids_pkey PRIMARY KEY (playlist_id, videos_order),
    CONSTRAINT fk_playlists_videos_video_id_playlist_video FOREIGN KEY (videos_id) REFERENCES playlist_videos(id),
    CONSTRAINT fk_playlists_videos_playlist_id_playlist FOREIGN KEY (playlist_id) REFERENCES playlists(id)
);

CREATE INDEX IF NOT EXISTS playlists_videos_ids_playlist_id_idx ON playlists_videos_ids (playlist_id ASC);

--rollback DROP TABLE IF EXISTS playlists_videos_ids;

CREATE TABLE IF NOT EXISTS unauthenticated_subscriptions (
    id VARCHAR(24) NOT NULL,
    subscribed_at INT8 NOT NULL,
    CONSTRAINT unauthenticated_subscriptions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_unauthenticated_subscriptions_id_channels FOREIGN KEY (id) REFERENCES channels(uploader_id)
);

CREATE INDEX IF NOT EXISTS unauthenticated_subscriptions_subscribed_at_idx ON unauthenticated_subscriptions (subscribed_at ASC);

--rollback DROP TABLE IF EXISTS unauthenticated_subscriptions;
