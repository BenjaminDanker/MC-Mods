package com.silver.aipets.service.recall;

import com.silver.aipets.common.transport.RecallResetWireStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecallAdminServiceTest {
    @Test
    void resetsOnlyTheServiceDerivedUtcPeriodAndReportsAlreadyAvailable() {
        UUID petId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        AtomicInteger calls = new AtomicInteger();
        RecallAdminService service = new RecallAdminService((actualPetId, period) -> {
            assertEquals(petId, actualPetId);
            assertEquals("2026-09", period);
            return calls.getAndIncrement() == 0;
        }, Clock.fixed(Instant.parse("2026-09-30T23:59:59Z"), ZoneOffset.ofHours(-7)));

        assertEquals(RecallResetWireStatus.RESET, service.resetCurrentPeriod(petId).status());
        assertEquals(RecallResetWireStatus.NOT_USED, service.resetCurrentPeriod(petId).status());
        assertEquals(2, calls.get());
    }
}
