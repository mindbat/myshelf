DROP TABLE IF EXISTS users;
--;;
CREATE TABLE users (
       user_id text PRIMARY KEY,
       handle text UNIQUE,
       access_token text,
       access_token_secret text,
       friends text[],
       created_at timestamp DEFAULT CURRENT_TIMESTAMP,
       updated_at timestamp DEFAULT CURRENT_TIMESTAMP
);
