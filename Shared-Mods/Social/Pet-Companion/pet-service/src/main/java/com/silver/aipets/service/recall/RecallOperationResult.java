package com.silver.aipets.service.recall;

import com.silver.aipets.common.transport.PetRecallWireResult;
import java.util.Objects;
import java.util.Optional;

public record RecallOperationResult(
        RecallOperationDisposition disposition,
        Optional<PetRecallWireResult> result) {
    public RecallOperationResult {
        Objects.requireNonNull(disposition, "disposition");
        result = Objects.requireNonNull(result, "result");
        if ((disposition == RecallOperationDisposition.KEY_CONFLICT) == result.isPresent()) {
            throw new IllegalArgumentException("Only successful recall operations carry a result");
        }
    }

    public static RecallOperationResult executed(PetRecallWireResult result) {
        return new RecallOperationResult(RecallOperationDisposition.EXECUTED, Optional.of(result));
    }

    public static RecallOperationResult replayed(PetRecallWireResult result) {
        return new RecallOperationResult(RecallOperationDisposition.REPLAYED, Optional.of(result));
    }

    public static RecallOperationResult keyConflict() {
        return new RecallOperationResult(RecallOperationDisposition.KEY_CONFLICT, Optional.empty());
    }
}
