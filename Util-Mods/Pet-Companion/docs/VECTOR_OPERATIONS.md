# Vector memory operations

Qdrant is the selected self-hosted vector provider, and OpenAI `text-embedding-3-small` is the
selected embedding provider. The Raspberry Pi stores the Qdrant index and all authoritative
memory cards locally in MariaDB; OpenAI receives only compact memory-card text or deterministic
search text and returns vectors. The initial fixed vector size is 1536, matching the model's
default output; changing model or dimensions requires a new collection/reindex.

Qdrant is a separate open-source HTTP service, so MariaDB remains authoritative without a vector
extension or schema replacement. The adapter is implemented in `QdrantVectorMemoryRepository`;
the OpenAI client is implemented in `OpenAiEmbeddingModelClient`. Production endpoint, API-key
delivery, collection sizing, authentication, and backup/restore details still require staging
and host approval.

## Invariants

- MariaDB `long_term_memories` remains authoritative; vectors are rebuildable indexes.
- Only active compact long-term memory cards are embedded. Raw event/dialogue columns are not
  accepted by the embedding worker API.
- Provider adapters must select the `pet_id` namespace before similarity ranking.
- Retrieval accepts at most three cards and rechecks pet ID, memory version, active state,
  relevance threshold, and metadata against MariaDB.
- An unavailable vector store returns a degraded empty long-term-memory result; it must not block
  short-term dialogue or physical pet functions.

## Local selection and verification

The provider choice was made locally against the checked-in constraints: MariaDB 11.4.10 is
available without a vector extension, cards must remain rebuildable from SQL, and the service
needs pet-scoped pre-ranking filters. Qdrant satisfies those constraints through a collection
with cosine distance and payload filters for `pet_id`, `memory_id`, `memory_version`, and
`active`. The Java adapters use only the JDK HTTP client and the existing Gson dependency.

Run the real endpoint test when Docker/Qdrant is available:

```powershell
$env:PET_TEST_QDRANT_URL = "http://127.0.0.1:6333"
.\gradlew.bat :pet-service:test --tests com.silver.aipets.service.vector.QdrantVectorMemoryRepositoryTest --offline
```

The test creates an isolated collection, upserts one card, verifies same-pet retrieval and
cross-pet isolation, then deletes the point. The current workstation has Docker installed but
its Linux engine is unavailable, so the real endpoint test remains a staging/local-environment
step rather than being represented as passed here.

## Reindex procedure

After a provider adapter and embedding client are installed in the service runtime, expose an
authenticated admin entrypoint that invokes `MemoryReindexService.enqueueAll(pageSize)`. Use a
page size from 1 through 1,000 (250 is a conservative starting point). The command reads every
active relational card and creates `MEMORY_EMBEDDING` jobs keyed by pet, memory, version, and
embedding model. Rerunning it is safe: the jobs table's idempotency key returns the existing job.

Process queued jobs with a unique worker ID through `MemoryEmbeddingWorker.processDue`. The
worker uses leases, bounded batches, exponential retry, terminal failure, and version checks.
Changed memory versions/model names naturally receive new keys; stale jobs cancel safely.

Monitor only metadata/status, never private memory text:

```sql
SELECT status, COUNT(*)
FROM jobs
WHERE job_type = 'MEMORY_EMBEDDING'
GROUP BY status;

SELECT embedding_status, COUNT(*)
FROM long_term_memories
WHERE active = TRUE
GROUP BY embedding_status;
```

If a provider is lost or replaced, disable retrieval, retain MariaDB rows/jobs, install the new
adapter/model configuration, then rerun the same full reindex. Do not delete relational memories.
