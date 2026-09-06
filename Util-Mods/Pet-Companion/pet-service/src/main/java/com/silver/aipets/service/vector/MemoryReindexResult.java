package com.silver.aipets.service.vector;

public record MemoryReindexResult(int scanned, int enqueued, int alreadyQueued) {
    public MemoryReindexResult {
        if (scanned < 0 || enqueued < 0 || alreadyQueued < 0
                || scanned != enqueued + alreadyQueued) {
            throw new IllegalArgumentException("Invalid reindex counts");
        }
    }
}
