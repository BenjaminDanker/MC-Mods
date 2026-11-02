package com.silver.aipets.service.vector;

public enum EmbeddingJobStatus {
    PENDING,
    RUNNING,
    RETRY,
    SUCCEEDED,
    FAILED,
    CANCELED
}
