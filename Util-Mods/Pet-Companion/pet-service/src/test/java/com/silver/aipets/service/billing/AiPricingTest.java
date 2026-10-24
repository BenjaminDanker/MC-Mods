package com.silver.aipets.service.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiPricingTest {
    @Test
    void derivesNetAllowancePricesOpenAiTokensAndKeepsFixedHolds() {
        AiPricing pricing = AiPricing.defaults();
        assertEquals(new BigDecimal("1.62800000"), pricing.netBudgetUsd());
        assertEquals(0, pricing.dialogueCost(5, 2, 1).compareTo(new BigDecimal("0.00000082")));
        assertEquals(0, pricing.embeddingCost(5).compareTo(new BigDecimal("0.00000010")));
        assertEquals(0, new BigDecimal("0.003").compareTo(pricing.dialogueHoldUsd()));
        assertThrows(IllegalArgumentException.class, () -> pricing.dialogueCost(1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new AiPricing(
                new BigDecimal("2.00"), new BigDecimal("2.9"), new BigDecimal("0.30"), new BigDecimal("0.7"),
                new BigDecimal("0.10"), new BigDecimal("0.01"), new BigDecimal("0.50"), new BigDecimal("0.02"),
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
    }
}
