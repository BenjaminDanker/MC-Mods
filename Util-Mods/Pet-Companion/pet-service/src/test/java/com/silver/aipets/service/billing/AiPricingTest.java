package com.silver.aipets.service.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiPricingTest {
    @Test
    void derivesNetAllowanceAndPricesAllTokenKinds() {
        AiPricing pricing = AiPricing.defaults();
        assertEquals(new BigDecimal("1.62800000"), pricing.netBudgetUsd());
        assertEquals(0, pricing.dialogueCost(5, 2, 1).compareTo(new BigDecimal("0.00000184")));
        assertEquals(0, pricing.embeddingCost(5).compareTo(new BigDecimal("0.00000010")));
    }
}
