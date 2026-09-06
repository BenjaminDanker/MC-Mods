package com.silver.aipets.service.recall;

import java.util.UUID;

@FunctionalInterface
public interface RecallAdminRepository {
    boolean resetConsumedPeriod(UUID petId, String periodKey);
}
