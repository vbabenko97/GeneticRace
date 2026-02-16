-- Schema migration: Add bcrypt password hash column
-- Run this on existing databases to support secure password storage

-- Add password_hash column if it doesn't exist
ALTER TABLE Users ADD COLUMN password_hash TEXT;

-- Mark existing users for password reset (NULL hash means legacy password)
-- They will be migrated on first successful login
UPDATE Users SET password_hash = NULL WHERE password_hash IS NULL;

-- Note: The legacy 'password' column is kept for migration purposes
-- After all users have logged in at least once, it can be dropped:
-- ALTER TABLE Users DROP COLUMN password;
