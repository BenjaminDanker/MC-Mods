package com.silver.aipets.service.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Configuration-driven provider pricing and the amount of a subscription that
 * may be spent on AI calls during one billing period.
 *
 * Prices are expressed in USD per one million tokens.  Stripe percentages are
 * percentages of the gross subscription amount (for example, 2.9 means 2.9%).
 */
public record AiPricing(
        BigDecimal subscriptionGrossUsd,
        BigDecimal stripePaymentPercent,
        BigDecimal stripeFixedFeeUsd,
        BigDecimal stripeBillingPercent,
        BigDecimal dialogueInputUsdPerMillion,
        BigDecimal dialogueCachedInputUsdPerMillion,
        BigDecimal dialogueOutputUsdPerMillion,
        BigDecimal embeddingInputUsdPerMillion) {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);

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
        BigDecimal paymentFee = subscriptionGrossUsd.multiply(stripePaymentPercent)
                .divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP);
        BigDecimal billingFee = subscriptionGrossUsd.multiply(stripeBillingPercent)
                .divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP);
        if (subscriptionGrossUsd.subtract(paymentFee).subtract(stripeFixedFeeUsd)
                .subtract(billingFee).signum() < 0) {
            throw new IllegalArgumentException("Stripe fees exceed the subscription amount");
        }
    }

    /** Defaults for GPT-5.6 Luna and text-embedding-3-small. */
    public static AiPricing defaults() {
        return new AiPricing(
                new BigDecimal("2.00"), new BigDecimal("2.9"), new BigDecimal("0.30"),
                new BigDecimal("0.7"), new BigDecimal("0.20"), new BigDecimal("0.02"),
                new BigDecimal("1.20"), new BigDecimal("0.02"));
    }

    /** Gross minus the configured payment percentage, fixed fee, and Billing percentage. */
    public BigDecimal netBudgetUsd() {
        BigDecimal paymentFee = subscriptionGrossUsd.multiply(stripePaymentPercent)
                .divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP);
        BigDecimal billingFee = subscriptionGrossUsd.multiply(stripeBillingPercent)
                .divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP);
        return subscriptionGrossUsd.subtract(paymentFee).subtract(stripeFixedFeeUsd)
                .subtract(billingFee).setScale(8, RoundingMode.HALF_UP);
    }

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

    public BigDecimal embeddingCost(int inputTokens) {
        if (inputTokens < 0) throw new IllegalArgumentException("inputTokens cannot be negative");
        return BigDecimal.valueOf(inputTokens).multiply(embeddingInputUsdPerMillion)
                .divide(MILLION, 12, RoundingMode.HALF_UP);
    }

    public BigDecimal maximumDialogueCost(int inputTokens, int outputTokens) {
        return dialogueCost(inputTokens, 0, outputTokens);
    }

    public BigDecimal maximumEmbeddingCost(int inputTokens) {
        return embeddingCost(inputTokens);
    }

    private static BigDecimal money(BigDecimal value, String name, boolean zeroAllowed) {
        BigDecimal result = nonNegative(value, name);
        if (!zeroAllowed && result.signum() == 0) throw new IllegalArgumentException(name + " must be positive");
        if (result.scale() > 8 || result.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException(name + " has an unsafe precision or magnitude");
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
}
