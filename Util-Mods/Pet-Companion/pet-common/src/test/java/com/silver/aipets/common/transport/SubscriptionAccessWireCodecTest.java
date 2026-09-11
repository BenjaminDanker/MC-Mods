package com.silver.aipets.common.transport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigDecimal;

class SubscriptionAccessWireCodecTest {
    private final SubscriptionAccessWireCodec codec = new SubscriptionAccessWireCodec();

    @Test
    void roundTripsScheduledCancellationProjection() {
        SubscriptionAccessWireResult expected = new SubscriptionAccessWireResult(
                true,
                "ACTIVE",
                true,
                "2026-10-08T03:22:15Z");

        assertEquals(expected, codec.decode(codec.encode(expected)));
    }

    @Test
    void rejectsLegacyBooleanOnlyAndInvalidPeriodEndPayloads() {
        assertThrows(PetWireFormatException.class,
                () -> codec.decode("{\"aiAccessEnabled\":true}"));
        assertThrows(PetWireFormatException.class,
                () -> codec.decode("{\"aiAccessEnabled\":true,\"status\":\"ACTIVE\","
                        + "\"cancelAtPeriodEnd\":true,\"currentPeriodEnd\":\"tomorrow\"}"));
    }

    @Test
    void roundTripsPeriodBudgetProjection() {
        SubscriptionAccessWireResult expected = new SubscriptionAccessWireResult(
                true, "ACTIVE", false, "2026-10-08T03:22:15Z",
                new BigDecimal("1.62800000"), new BigDecimal("0.00021940"),
                new BigDecimal("1.62778060"));
        assertEquals(expected, codec.decode(codec.encode(expected)));
    }
}
