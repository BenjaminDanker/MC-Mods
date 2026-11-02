package com.silver.aipets.service.retention;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RetentionCleanupRepository {
    EnqueueResult enqueue(RetentionCleanupJob proposed);
    List<RetentionCleanupJob> claimDue(String workerId, Instant now, Duration lease, int limit);
    RetentionCleanupResult cleanup(
            RetentionCleanupJob job, String workerId, Instant now, int maximumRows);
    void retry(RetentionCleanupJob job, String workerId, Instant now, Instant notBefore, String category);
    void failed(RetentionCleanupJob job, String workerId, Instant now, String category);
    Optional<RetentionCleanupJob> findByKey(String key);

    record EnqueueResult(RetentionCleanupJob job, boolean created) { }
}
