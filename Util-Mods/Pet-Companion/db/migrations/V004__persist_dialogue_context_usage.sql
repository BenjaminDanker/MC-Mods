-- Persist bounded prompt composition accounting alongside provider usage.
-- Raw prompts and conversation text are deliberately not stored here.
ALTER TABLE ai_usage
    ADD COLUMN context_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL
        AFTER error_category;
