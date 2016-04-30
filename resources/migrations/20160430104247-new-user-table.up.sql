DROP TABLE IF EXISTS users;
--;;
CREATE TABLE IF NOT EXISTS users (
       user_id SERIAL PRIMARY KEY,
       goodreads_id text UNIQUE,
       handle text UNIQUE,
       last_tweet bigint,
       oauth_token text,
       oauth_token_secret text,
       created_at timestamp DEFAULT CURRENT_TIMESTAMP,
       updated_at timestamp DEFAULT CURRENT_TIMESTAMP
);
