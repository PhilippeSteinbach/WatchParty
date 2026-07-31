ALTER TABLE chat_messages ADD COLUMN user_reactions JSONB NOT NULL DEFAULT '{}';
