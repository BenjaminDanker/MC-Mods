package com.silver.aipets.common.transport;

import java.util.Objects;

public record RecallResetWireResult(RecallResetWireStatus status, String periodKey) {
    public RecallResetWireResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(periodKey, "periodKey");
        if (!periodKey.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("periodKey must be YYYY-MM");
        }
    }
}
