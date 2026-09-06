package com.silver.aipets.service.persistence;

public enum IdempotentMutationDisposition {
    EXECUTED,
    REPLAYED,
    KEY_CONFLICT,
    IN_PROGRESS
}
