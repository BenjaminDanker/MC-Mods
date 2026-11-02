package com.silver.aipets.service.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The amount of a subscription that may be spent on AI calls during one billing period, the
 * token prices used when a provider reports tokens but not dollars, and the temporary per-call
 * holds used for admission.
 *
 * OpenAI responses carry token counts only, so those calls are priced here (USD per one million
 * tokens). A call served by OpenRouter (the fallback provider) is charged its reported
 * {@code usage.cost} instead. A hold only reserves headroom while a call is in flight so
 * concurrent calls cannot all pass the last few cents of a period; it is released when the call
 * settles with its real cost. Stripe percentages are percentages of the gross subscription
 * amount (2.9 means 2.9%).
 */
public record AiPricing(
        BigDecimal subscriptionGrossUsd,
        BigDecimal stripePaymentPercent,
        BigDecimal stripeFixedFeeUsd,
        BigDecimal stripeBillingPercent,
        BigDecimal dialogueInputUsdPerMillion,
        BigDecimal dialogueCachedInputUsdPerMillion,
        BigDecimal dialogueOutputUsdPerMillion,
        BigDecimal embeddingInputUsdPerMillion,
        BigDecimal dialogueHoldUsd,
        BigDecimal consolidationHoldUsd,
        BigDecimal planningHoldUsd,
        BigDecimal embeddingHoldUsd) {

    public AiPricing {
        subscriptionGrossUsd = money(subscriptionGrossUsd, "subscriptionGrossUsd", false);
        stripePaymentPercent = percent(stripePaymentPercent, "stripePaymentPercent");
        stripeFixedFeeUsd = money(stripeFixedFeeUsd, "stripeFixedFeeUsd", true);
        stripeBillingPercent = percent(stripeBillingPercent, "stripeBillingPercent");
        dialogueInputUsdPerMillion = nonNegative(dialogueInputUsdPerMillion, "dialogueInputUsdPerMillion");
        dialogueCachedInputUsdPerMillion = nonNegative(
                dialogueCachedInputUsdPerMillion, "dialogueCachedInputUsdPerMillion");
        dialogueOutputUsdPerMillion = nonNegative(dialogueOutputUsdPerMillion, "dialogueOutputUsdPerMillion");
        embeddingInputUsdPerMillion = nonNegative(embeddingInputUsdPerMillion, "embeddingInputUsdPerMillion");
        dialogueHoldUsd = hold(dialogueHoldUsd, "dialogueHoldUsd");
        consolidationHoldUsd = hold(consolidationHoldUsd, "consolidationHoldUsd");
        planningHoldUsd = hold(planningHoldUsd, "planningHoldUsd");
        embeddingHoldUsd = hold(embeddingHoldUsd, "embeddingHoldUsd");
        if (net(subscriptionGrossUsd, stripePaymentPercent, stripeFixedFeeUsd, stripeBillingPercent).signum() < 0) {
            throw new IllegalArgumentException("Stripe fees exceed the subscription amount");
        }
    }

    /** $2.00 subscription, standard Stripe fees, GPT-6 Luna and text-embedding-3-small token prices. */
    public static AiPricing defaults() {
        return new AiPricing(
                new BigDecimal("2.00"), new BigDecimal("2.9"), new BigDecimal("0.30"), new BigDecimal("0.7"),
                Defaults.DIALOGUE_INPUT, Defaults.DIALOGUE_CACHED_INPUT, Defaults.DIALOGUE_OUTPUT,
                Defaults.EMBEDDING_INPUT, Defaults.DIALOGUE_HOLD, Defaults.CONSOLIDATION_HOLD, Defaults.PLANNING_HOLD,
                Defaults.EMBEDDING_HOLD);
    }

    /** Gross minus the configured payment percentage, fixed fee, and Billing percentage. */
    public BigDecimal netBudgetUsd() {
        return net(subscriptionGrossUsd, stripePaymentPercent, stripeFixedFeeUsd, stripeBillingPercent)
                .setScale(8, RoundingMode.HALF_UP);
    }

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);

    /** Cost of an OpenAI chat call from its reported token usage. */
    public BigDecimal dialogueCost(int inputTokens, int cachedInputTokens, int outputTokens) {
        if (inputTokens < 0 || cachedInputTokens < 0 || cachedInputTokens > inputTokens || outputTokens < 0) {
            throw new IllegalArgumentException("Token counts are invalid");
        }
        BigDecimal billableInput = BigDecimal.valueOf((long) inputTokens - cachedInputTokens);
        return billableInput.multiply(dialogueInputUsdPerMillion)
                .add(BigDecimal.valueOf(cachedInputTokens).multiply(dialogueCachedInputUsdPerMillion))
                .add(BigDecimal.valueOf(outputTokens).multiply(dialogueOutputUsdPerMillion))
                .divide(MILLION, 12, RoundingMode.HALF_UP);
    }

    /** Cost of an OpenAI embedding call from its reported token usage. */
    public BigDecimal embeddingCost(int inputTokens) {
        if (inputTokens < 0) throw new IllegalArgumentException("inputTokens cannot be negative");
        return BigDecimal.valueOf(inputTokens).multiply(embeddingInputUsdPerMillion)
                .divide(MILLION, 12, RoundingMode.HALF_UP);
    }

    private static BigDecimal net(BigDecimal gross, BigDecimal paymentPercent, BigDecimal fixedFee,
            BigDecimal billingPercent) {
        BigDecimal paymentFee = gross.multiply(paymentPercent).divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP);
        BigDecimal billingFee = gross.multiply(billingPercent).divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP);
        return gross.subtract(paymentFee).subtract(fixedFee).subtract(billingFee);
    }

    private static BigDecimal money(BigDecimal value, String name, boolean zeroAllowed) {
        BigDecimal result = nonNegative(value, name);
        if (!zeroAllowed && result.signum() == 0) throw new IllegalArgumentException(name + " must be positive");
        if (result.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException(name + " has an unsafe magnitude");
        }
        return result;
    }

    private static BigDecimal hold(BigDecimal value, String name) {
        BigDecimal result = nonNegative(value, name);
        if (result.signum() == 0 || result.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " must be greater than 0 and at most 1 USD");
        }
        return result;
    }

    private static BigDecimal percent(BigDecimal value, String name) {
        BigDecimal result = nonNegative(value, name);
        if (result.compareTo(BigDecimal.valueOf(100)) > 0) throw new IllegalArgumentException(name + " must be <= 100");
        return result;
    }

    private static BigDecimal nonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0 || value.scale() > 8) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }

    /** Default token prices (USD per 1M tokens) and per-call holds (USD). */
    public static final class Defaults {
        public static final BigDecimal DIALOGUE_INPUT = new BigDecimal("0.10");
        public static final BigDecimal DIALOGUE_CACHED_INPUT = new BigDecimal("0.01");
        public static final BigDecimal DIALOGUE_OUTPUT = new BigDecimal("0.50");
        public static final BigDecimal EMBEDDING_INPUT = new BigDecimal("0.02");
        public static final BigDecimal DIALOGUE_HOLD = new BigDecimal("0.003");
        public static final BigDecimal CONSOLIDATION_HOLD = new BigDecimal("0.005");
        public static final BigDecimal PLANNING_HOLD = new BigDecimal("0.002");
        public static final BigDecimal EMBEDDING_HOLD = new BigDecimal("0.0002");

        private Defaults() {
        }
    }
}
