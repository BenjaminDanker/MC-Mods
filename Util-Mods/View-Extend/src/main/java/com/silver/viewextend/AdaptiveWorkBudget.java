package com.silver.viewextend;

/** Feedback controller with explicit memory/read ceilings and a bounded server-thread time slice. */
final class AdaptiveWorkBudget {
    private final int maximumReads;
    private int reads;
    private int healthyTicks;
    private double latencyTicks = 2;
    private long deadline;
    private long allowance = 4_000_000;
    AdaptiveWorkBudget(int maximumReads) { this.maximumReads = maximumReads; reads = Math.min(32, maximumReads); }
    void begin(long now, long vanillaNanos, double workerPressure, boolean backlog) {
        allowance = Math.clamp((50_000_000L - vanillaNanos) / 5, 100_000L, 4_000_000L);
        deadline = now + allowance;
        if (vanillaNanos > 45_000_000L || workerPressure > .75) {
            reads = Math.max(1, reads * 3 / 4); healthyTicks = 0;
        } else if (backlog && ++healthyTicks >= 20) {
            reads = Math.min(maximumReads, reads + Math.max(1, reads / 8)); healthyTicks = 0;
        }
    }
    boolean hasTime(long now) { return now < deadline; }
    int reads() { return reads; }
    long allowance() { return allowance; }
    double latencyTicks() { return latencyTicks; }
    void completed(int elapsedTicks) { latencyTicks += .1 * (Math.clamp(elapsedTicks, 1, 200) - latencyTicks); }
    int lookahead(float consumption, int maximum) {
        if (!Float.isFinite(consumption)) consumption = 1;
        return Math.clamp((int) Math.ceil(Math.max(.01, consumption) * (latencyTicks + 2)), Math.min(4, maximum), maximum);
    }
}
