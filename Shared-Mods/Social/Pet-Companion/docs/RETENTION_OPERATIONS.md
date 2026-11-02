# Event retention operations

The service starts one bounded retention scheduler immediately and then checks
hourly. Each UTC date has one durable job with type `EVENT_RETENTION_CLEANUP` and
idempotency key `event-retention:YYYY-MM-DD`. Restarted or parallel service
instances therefore converge on the same daily row.

Default policy (`PET_RAW_TEXT_RETENTION_DAYS=7`, accepted range 1–30):

- clear `prompt_eligible` once a non-null `short_term_expires_at` is due;
- redact `raw_player_text` and `raw_pet_reply` at exactly seven days old;
- retain the compact summary, event/pet identity, timestamps, importance,
  consolidation status, and long-term source references;
- update at most 1,000 rows per operation/run;
- lease for two minutes and retry failures after five minutes, up to three attempts.

The cleanup deliberately does not delete event rows or long-term memories. This
preserves factual grounding and auditability while removing short-lived dialogue.
No raw text or database value is written to logs or job errors.

Safe status query:

```sql
SELECT status, COUNT(*)
FROM jobs
WHERE job_type = 'EVENT_RETENTION_CLEANUP'
GROUP BY status;
```

Safe aggregate verification:

```sql
SELECT
  SUM(prompt_eligible = TRUE AND short_term_expires_at <= UTC_TIMESTAMP(6)) AS overdue_prompts,
  SUM(occurred_at <= UTC_TIMESTAMP(6) - INTERVAL 7 DAY
      AND (raw_player_text IS NOT NULL OR raw_pet_reply IS NOT NULL)) AS overdue_raw
FROM pet_events;
```

Do not manually delete jobs or event rows. A failed terminal job can be inspected
through its sanitized category, then retried through an operator workflow while
preserving its original UTC-day idempotency key.
