-- Dialogue rows preserve both speakers. Their event summary must represent
-- the owner's authored input; the generated pet reply remains in
-- raw_pet_reply. If retention already removed the owner input, the old summary
-- is not safe to treat as an owner statement or witnessed fact.
UPDATE pet_events
SET summary = raw_player_text,
    source_provenance = 'OWNER_STATEMENT',
    confidence = 1.000
WHERE event_type = 'DIALOGUE'
  AND raw_player_text IS NOT NULL
  AND raw_player_text <> '';

UPDATE pet_events
SET source_provenance = 'INFERENCE',
    confidence = 0.500
WHERE event_type = 'DIALOGUE'
  AND (raw_player_text IS NULL OR raw_player_text = '');
