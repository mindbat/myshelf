CREATE TABLE IF NOT EXISTS user_friends (
  user_id text PRIMARY KEY,
  friends text [],
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
