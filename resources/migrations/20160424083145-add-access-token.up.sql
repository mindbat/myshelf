CREATE TABLE IF NOT EXISTS users (
  user_id text PRIMARY KEY,
  handle text UNIQUE NOT NULL,
  access_token text,
  access_token_secret text,
  friends text [],
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
--;;
SELECT * INTO users FROM user_friends;
--;;
DROP TABLE user_friends;
