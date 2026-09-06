# Consolidation operations

Sleep consolidation uses the existing `jobs` table with job type
`SLEEP_CONSOLIDATION`. The idempotency key is the pet UUID plus the deterministic
sleep-cycle UUID. A worker claims bounded batches with a lease; expired leases are
restart-reclaimable.

The configured runtime must connect `SleepConsolidationTrigger` to
`PetSleepService`, start one `ConsolidationScheduler`, and provide the selected
model/tokenizer plus `MemoryEmbeddingWorker::enqueue`. Keep the model call off game
and HTTP threads.

Safe status inspection (no raw dialogue):

```sql
SELECT status, COUNT(*)
FROM jobs
WHERE job_type = 'SLEEP_CONSOLIDATION'
GROUP BY status;
```

Provider failures retain source events as `PENDING` and use exponential retry.
Terminal failures do not change sleep timestamps. Successful commits atomically
write memory cards and their pet-scoped source references, the bounded relationship
summary and trait audits, secret-free `ai_usage`, final event statuses, and the completed job. Failed/rejected
attempts record only bounded provider metrics and a sanitized category. Embeddings
remain recoverable from relational cards if enqueueing is temporarily unavailable.

Do not manually mark jobs successful or delete source events. Retry/reconciliation
and any operator endpoint must preserve the original idempotency key and pet scope.
