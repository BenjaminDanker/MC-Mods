package com.silver.aipets.service.recall;

import com.silver.aipets.common.transport.RecallResetWireResult;
import com.silver.aipets.common.transport.RecallResetWireStatus;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

public final class RecallAdminService {
    private final RecallAdminRepository repository;
    private final Clock clock;

    public RecallAdminService(RecallAdminRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RecallResetWireResult resetCurrentPeriod(UUID petId) {
        Objects.requireNonNull(petId, "petId");
        String period = YearMonth.now(clock.withZone(ZoneOffset.UTC)).toString();
        boolean reset = repository.resetConsumedPeriod(petId, period);
        return new RecallResetWireResult(
                reset ? RecallResetWireStatus.RESET : RecallResetWireStatus.NOT_USED,
                period);
    }
}
