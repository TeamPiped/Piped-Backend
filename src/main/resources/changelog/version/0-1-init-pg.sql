CREATE INDEX IF NOT EXISTS users_session_id_idx ON users (session_id ASC);

--rollback DROP INDEX IF EXISTS users_session_id_idx;
