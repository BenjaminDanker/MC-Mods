package com.silver.aipets.fabric.reconciliation;

import com.silver.aipets.fabric.entity.PetEntityData;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;

/** Bounded, loaded-entity-only periodic authority checks with duplicate request suppression. */
public final class PeriodicPetReconciliation {
    private final PetReconciliationConfig config;
    private final Supplier<PetEntityReconciler> reconcilerSupplier;
    private final Map<ServerLevel, WorldSchedule> worlds = new IdentityHashMap<>();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public PeriodicPetReconciliation(
            PetReconciliationConfig config,
            Supplier<PetEntityReconciler> reconcilerSupplier) {
        this.config = Objects.requireNonNull(config, "config");
        this.reconcilerSupplier = Objects.requireNonNull(reconcilerSupplier, "reconcilerSupplier");
    }

    public void onEntityLoad(Entity entity, ServerLevel world) {
        reconcile(entity, world);
    }

    public void onEndWorldTick(ServerLevel world) {
        Objects.requireNonNull(world, "world");
        if (reconcilerSupplier.get() == null) {
            worlds.remove(world);
            return;
        }
        WorldSchedule schedule = worlds.computeIfAbsent(
                world,
                ignored -> new WorldSchedule(world.getGameTime() + config.intervalTicks()));
        if (schedule.pending().isEmpty() && world.getGameTime() >= schedule.nextScanAt()) {
            scanLoadedNow(world);
            schedule = worlds.get(world);
        }

        int checks = 0;
        while (checks < config.maximumChecksPerTick() && !schedule.pending().isEmpty()) {
            reconcile(schedule.pending().removeFirst(), world);
            checks++;
        }
    }

    public void clear() {
        worlds.clear();
        inFlight.clear();
    }

    public Set<UUID> inFlightEntityIds() {
        return Collections.unmodifiableSet(inFlight);
    }

    /** Queues a fresh loaded-only scan; also serves the future admin reconciliation operation. */
    public int scanLoadedNow(ServerLevel world) {
        Objects.requireNonNull(world, "world");
        WorldSchedule schedule = worlds.computeIfAbsent(
                world,
                ignored -> new WorldSchedule(world.getGameTime() + config.intervalTicks()));
        schedule.pending().clear();
        for (Entity entity : world.getAllEntities()) {
            if (isMarkedPet(entity)) {
                schedule.pending().add(entity);
            }
        }
        schedule.nextScanAt = world.getGameTime() + config.intervalTicks();
        return schedule.pending().size();
    }

    private void reconcile(Entity entity, ServerLevel world) {
        PetEntityReconciler reconciler = reconcilerSupplier.get();
        if (reconciler == null || !isMarkedPet(entity) || !inFlight.add(entity.getUUID())) {
            return;
        }
        try {
            reconciler.reconcileLoaded(entity, world).whenComplete((ignored, failure) ->
                    inFlight.remove(entity.getUUID()));
        } catch (RuntimeException failure) {
            inFlight.remove(entity.getUUID());
        }
    }

    private static boolean isMarkedPet(Entity entity) {
        return entity instanceof TamableAnimal
                && entity instanceof PetEntityData data
                && data.aipets$isPet()
                && !entity.isRemoved();
    }

    private static final class WorldSchedule {
        private final ArrayDeque<Entity> pending = new ArrayDeque<>();
        private long nextScanAt;

        private WorldSchedule(long nextScanAt) {
            this.nextScanAt = nextScanAt;
        }

        private ArrayDeque<Entity> pending() {
            return pending;
        }

        private long nextScanAt() {
            return nextScanAt;
        }
    }
}
