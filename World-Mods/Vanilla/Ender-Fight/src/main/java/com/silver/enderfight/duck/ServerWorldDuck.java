package com.silver.enderfight.duck;

/**
 * Duck-typing interface for ServerWorld seed override.
 * Implemented by ServerWorldAccessor mixin.
 */
public interface ServerWorldDuck {
    void enderfight$setSeed(long seed);
    Long enderfight$getOverriddenSeed();
}
