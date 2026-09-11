package com.silver.aipets.fabric.reconciliation;

import com.silver.aipets.common.authority.EntityAuthority;
import com.silver.aipets.common.authority.EntityAuthorityDecision;
import com.silver.aipets.common.authority.PetEntityIdentity;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.authority.PetAuthorityGateway;
import com.silver.aipets.fabric.authority.PetAuthoritySnapshot;
import com.silver.aipets.fabric.entity.PetEntityController;
import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.metrics.PetMetricsReporter;
import com.silver.aipets.fabric.metrics.PetMetricsClassifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Reconciles only an already-loaded marked entity and never loads or scans chunks. */
public final class PetEntityReconciler {
    private final BackendId backendId;
    private final PetAuthorityGateway authority;
    private final PetMetricsReporter metrics;

    public PetEntityReconciler(BackendId backendId, PetAuthorityGateway authority) {
        this(backendId, authority, PetMetricsReporter.noop());
    }

    public PetEntityReconciler(
            BackendId backendId,
            PetAuthorityGateway authority,
            PetMetricsReporter metrics) {
        this.backendId = Objects.requireNonNull(backendId, "backendId");
        this.authority = Objects.requireNonNull(authority, "authority");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public CompletableFuture<PetEntityReconciliationResult> reconcileLoaded(
            Entity entity,
            ServerWorld world) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(world, "world");
        if (!(entity instanceof TameableEntity tameable)
                || !(entity instanceof PetEntityData data)
                || !data.aipets$isPet()) {
            return CompletableFuture.completedFuture(PetEntityReconciliationResult.of(
                    PetEntityReconciliationStatus.IGNORED_NOT_PET));
        }

        PetEntityIdentity identity = identity(world, entity, data);
        long entityRecordVersion = data.aipets$getRecordVersion();
        MinecraftServer server = world.getServer();
        CompletableFuture<PetEntityReconciliationResult> result = new CompletableFuture<>();
        try {
            authority.findByPetId(identity.petId()).whenComplete((snapshot, failure) -> {
                if (failure != null || snapshot == null) {
                    PetCompanionMod.LOGGER.warn(StructuredPetEvent
                            .operation("entity_reconciliation_lookup")
                            .pet(identity.petId()).owner(identity.ownerUuid())
                            .backend(identity.backendId())
                            .failure(failure == null ? new IllegalStateException() : failure)
                            .outcome("service_failure").toJson());
                    result.complete(PetEntityReconciliationResult.of(
                            PetEntityReconciliationStatus.SERVICE_FAILURE));
                    return;
                }
                onServer(server, () -> apply(
                        tameable,
                        world,
                        identity,
                        entityRecordVersion,
                        snapshot,
                        result));
            });
        } catch (RuntimeException failure) {
            PetCompanionMod.LOGGER.warn(StructuredPetEvent
                    .operation("entity_reconciliation_lookup")
                    .pet(identity.petId()).owner(identity.ownerUuid())
                    .backend(identity.backendId()).failure(failure)
                    .outcome("service_failure").toJson());
            result.complete(PetEntityReconciliationResult.of(
                    PetEntityReconciliationStatus.SERVICE_FAILURE));
        }
        return result;
    }

    private void apply(
            TameableEntity entity,
            ServerWorld world,
            PetEntityIdentity identity,
            long entityRecordVersion,
            Optional<PetAuthoritySnapshot> snapshot,
            CompletableFuture<PetEntityReconciliationResult> result) {
        if (!sameLoadedEntity(entity, world, identity)) {
            result.complete(PetEntityReconciliationResult.of(
                    PetEntityReconciliationStatus.CONTEXT_CHANGED));
            return;
        }
        if (snapshot.isEmpty()) {
            discard(entity, identity, entityRecordVersion, null, null);
            record(PetMetricsClassifier.missingEntityDiscard());
            result.complete(PetEntityReconciliationResult.of(
                    PetEntityReconciliationStatus.STALE_DISCARDED));
            return;
        }

        PetAuthoritySnapshot authoritative = snapshot.orElseThrow();
        Pet pet = authoritative.pet();
        EntityAuthorityDecision decision = EntityAuthority.decide(
                pet,
                identity,
                entityRecordVersion);
        if (decision.shouldDiscard()) {
            discard(entity, identity, entityRecordVersion, pet, decision);
            record(PetMetricsClassifier.entityDiscard(decision));
            result.complete(PetEntityReconciliationResult.decided(
                    PetEntityReconciliationStatus.STALE_DISCARDED,
                    decision));
            return;
        }

        PetEntityData data = (PetEntityData) entity;
        data.aipets$setRecordVersion(pet.recordVersion());
        data.aipets$setSleeping(authoritative.sleeping());
        PetEntityController.configure(entity);
        result.complete(PetEntityReconciliationResult.decided(
                PetEntityReconciliationStatus.AUTHORITATIVE_REUSED,
                decision));
    }

    private void discard(
            TameableEntity entity,
            PetEntityIdentity identity,
            long entityRecordVersion,
            Pet pet,
            EntityAuthorityDecision decision) {
        entity.discard();
        PetCompanionMod.LOGGER.warn(StructuredPetEvent
                .operation("entity_reconciliation_discard")
                .pet(identity.petId()).owner(identity.ownerUuid())
                .backend(identity.backendId())
                .outcome(decision == null ? "pet_not_found" : decision.name())
                .toJson());
    }

    private PetEntityIdentity identity(ServerWorld world, Entity entity, PetEntityData data) {
        return new PetEntityIdentity(
                data.aipets$getPetId(),
                data.aipets$getOwnerUuid(),
                backendId,
                DimensionId.parse(world.getRegistryKey().getValue().toString()),
                entity.getUuid());
    }

    private boolean sameLoadedEntity(
            TameableEntity entity,
            ServerWorld world,
            PetEntityIdentity expected) {
        if (entity.isRemoved()
                || entity.getEntityWorld() != world
                || world.getEntityAnyDimension(expected.entityUuid()) != entity
                || !(entity instanceof PetEntityData data)
                || !data.aipets$isPet()) {
            return false;
        }
        return expected.equals(identity(world, entity, data));
    }

    private static void onServer(MinecraftServer server, Runnable action) {
        if (server.isOnThread()) {
            action.run();
        } else {
            server.execute(action);
        }
    }

    private void record(com.silver.aipets.common.transport.PetMetricWireEvent.Metric metric) {
        try {
            metrics.increment(metric);
        } catch (RuntimeException ignored) {
            // Diagnostics must never affect entity reconciliation.
        }
    }
}
