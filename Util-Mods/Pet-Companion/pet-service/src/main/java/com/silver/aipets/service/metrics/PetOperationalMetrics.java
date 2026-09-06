package com.silver.aipets.service.metrics;

import com.silver.aipets.service.health.PetReadinessSnapshot;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Low-cardinality process metrics. Owner, pet, conversation, and authorization values are never
 * labels, so exporting this snapshot cannot disclose player data or create unbounded series.
 */
public final class PetOperationalMetrics {
    public enum Counter {
        PETS_CREATED_CAT("aipets_pets_created_cat_total"),
        PETS_CREATED_DOG("aipets_pets_created_dog_total"),
        DIALOGUE_CALLS("aipets_dialogue_calls_total"),
        DIALOGUE_SUCCEEDED("aipets_dialogue_succeeded_total"),
        DIALOGUE_FAILED("aipets_dialogue_failed_total"),
        DIALOGUE_TIMEOUTS("aipets_dialogue_timeouts_total"),
        INPUT_TOKENS("aipets_input_tokens_total"),
        CACHED_INPUT_TOKENS("aipets_cached_input_tokens_total"),
        OUTPUT_TOKENS("aipets_output_tokens_total"),
        ESTIMATED_COST_MICROS("aipets_estimated_cost_microunits_total"),
        CONSOLIDATION_SKIPPED("aipets_consolidation_skipped_total"),
        CONSOLIDATION_RUN("aipets_consolidation_run_total"),
        CONSOLIDATION_FAILED("aipets_consolidation_failed_total"),
        MEMORIES_CREATED("aipets_memories_created_total"),
        MEMORIES_REINFORCED("aipets_memories_reinforced_total"),
        MEMORIES_RETRIEVED("aipets_memories_retrieved_total"),
        EMBEDDING_FAILURES("aipets_embedding_failures_total"),
        HTTP_REQUESTS("aipets_http_requests_total"),
        HTTP_ERRORS("aipets_http_errors_total"),
        HTTP_LATENCY_MILLIS("aipets_http_latency_milliseconds_total"),
        DUPLICATE_ENTITY_DISCARDS("aipets_duplicate_entity_discards_total"),
        STALE_ENTITY_DISCARDS("aipets_stale_entity_discards_total"),
        RECALL_ATTEMPTS("aipets_recall_attempts_total"),
        RECALL_SUCCESSES("aipets_recall_successes_total"),
        RECALL_FAILURES("aipets_recall_failures_total"),
        TRANSFER_AUTO_PICKUP_FAILURES("aipets_transfer_auto_pickup_failures_total"),
        TRANSFER_AUTO_PLACE_FAILURES("aipets_transfer_auto_place_failures_total");

        private final String wireName;
        Counter(String wireName) { this.wireName = wireName; }
    }

    public enum Gauge {
        SUBSCRIPTIONS_ACTIVE("aipets_subscriptions_active"),
        SUBSCRIPTIONS_INACTIVE("aipets_subscriptions_inactive"),
        PETS_HELD("aipets_pets_held"),
        PETS_PLACED("aipets_pets_placed"),
        PETS_SLEEPING("aipets_pets_sleeping"),
        CAT_SCALE_SMALL("aipets_cat_scale_bucket_small"),
        CAT_SCALE_MEDIUM("aipets_cat_scale_bucket_medium"),
        CAT_SCALE_LARGE("aipets_cat_scale_bucket_large"),
        DOG_SCALE_SMALL("aipets_dog_scale_bucket_small"),
        DOG_SCALE_MEDIUM("aipets_dog_scale_bucket_medium"),
        DOG_SCALE_LARGE("aipets_dog_scale_bucket_large"),
        SUBSCRIBERS_USAGE_0_10("aipets_subscriber_usage_bucket_0_10"),
        SUBSCRIBERS_USAGE_11_25("aipets_subscriber_usage_bucket_11_25"),
        SUBSCRIBERS_USAGE_26_50("aipets_subscriber_usage_bucket_26_50"),
        SUBSCRIBERS_USAGE_51_100("aipets_subscriber_usage_bucket_51_100"),
        SUBSCRIBERS_USAGE_OVER_100("aipets_subscriber_usage_bucket_over_100"),
        EMBEDDING_QUEUE_DEPTH("aipets_embedding_queue_depth"),
        DATABASE_HEALTH("aipets_database_health"),
        VECTOR_HEALTH("aipets_vector_health");

        private final String wireName;
        Gauge(String wireName) { this.wireName = wireName; }
    }

    private final Map<Counter, LongAdder> counters = new EnumMap<>(Counter.class);
    private final Map<Gauge, AtomicLong> gauges = new EnumMap<>(Gauge.class);

    public PetOperationalMetrics() {
        Arrays.stream(Counter.values()).forEach(metric -> counters.put(metric, new LongAdder()));
        Arrays.stream(Gauge.values()).forEach(metric -> gauges.put(metric, new AtomicLong()));
    }

    public void increment(Counter metric) { add(metric, 1); }

    public void add(Counter metric, long amount) {
        if (amount < 0) throw new IllegalArgumentException("counter amount must be non-negative");
        counters.get(metric).add(amount);
    }

    public void set(Gauge metric, long value) {
        if (value < 0) throw new IllegalArgumentException("gauge value must be non-negative");
        gauges.get(metric).set(value);
    }

    public void recordHttp(long elapsedNanos, int responseCode) {
        increment(Counter.HTTP_REQUESTS);
        add(Counter.HTTP_LATENCY_MILLIS, Math.max(0, elapsedNanos / 1_000_000));
        if (responseCode >= 400 || responseCode < 0) increment(Counter.HTTP_ERRORS);
    }

    public void recordReadiness(PetReadinessSnapshot snapshot) {
        set(Gauge.DATABASE_HEALTH, "UP".equals(snapshot.database()) ? 1 : 0);
        set(Gauge.VECTOR_HEALTH, snapshot.vector().startsWith("UP") ? 1 : 0);
    }

    public byte[] prometheusSnapshot() {
        StringBuilder output = new StringBuilder(2_048);
        counters.forEach((metric, value) -> output.append(metric.wireName).append(' ')
                .append(value.sum()).append('\n'));
        gauges.forEach((metric, value) -> output.append(metric.wireName).append(' ')
                .append(value.get()).append('\n'));
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }
}
