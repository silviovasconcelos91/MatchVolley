-- Default admin for existing data
INSERT INTO app_users (id, email, pseudo, password, created_at)
VALUES ('00000000-0000-0000-0000-000000000001',
        'admin@matchvolley.local',
        'admin',
        '$2a$10$mCclOFEkCyy2CyEFp9Av8.3EGE4GioJYj5P0cGCDGdtgMRExLVnkm',
        NOW());

-- Add user_id nullable first
ALTER TABLE teams   ADD COLUMN user_id UUID REFERENCES app_users (id);
ALTER TABLE players ADD COLUMN user_id UUID REFERENCES app_users (id);
ALTER TABLE matches ADD COLUMN user_id UUID REFERENCES app_users (id);

-- Assign existing records to admin
UPDATE teams   SET user_id = '00000000-0000-0000-0000-000000000001';
UPDATE players SET user_id = '00000000-0000-0000-0000-000000000001';
UPDATE matches SET user_id = '00000000-0000-0000-0000-000000000001';

-- Enforce NOT NULL
ALTER TABLE teams   ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE players ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE matches ALTER COLUMN user_id SET NOT NULL;
