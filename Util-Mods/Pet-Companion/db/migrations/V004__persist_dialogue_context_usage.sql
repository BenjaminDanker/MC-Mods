-- Persist bounded prompt composition accounting alongside provider usage.
-- Raw prompts and conversation text are deliberately not stored here.
ALTER TABLE ai_usage
    ADD context_json NVARCHAR(MAX) NULL;
