package com.silver.aipets.common.authority;

import static com.silver.aipets.common.authority.TransitionFailure.BACKEND_MISMATCH;
import static com.silver.aipets.common.authority.TransitionFailure.DESTINATION_MISMATCH;
import static com.silver.aipets.common.authority.TransitionFailure.DIMENSION_MISMATCH;
import static com.silver.aipets.common.authority.TransitionFailure.ENTITY_NOT_MATERIALIZED;
import static com.silver.aipets.common.authority.TransitionFailure.ENTITY_UUID_MISMATCH;
import static com.silver.aipets.common.authority.TransitionFailure.ENTITY_UUID_NOT_FRESH;
import static com.silver.aipets.common.authority.TransitionFailure.OUT_OF_RANGE;
import static com.silver.aipets.common.authority.TransitionFailure.OWNER_MISMATCH;
import static com.silver.aipets.common.authority.TransitionFailure.STATE_MISMATCH;
import static com.silver.aipets.common.authority.TransitionFailure.TIMESTAMP_BEFORE_CURRENT;
import static com.silver.aipets.common.authority.TransitionFailure.TRANSFER_EXPIRED;
import static com.silver.aipets.common.authority.TransitionFailure.TRANSFER_ID_MISMATCH;
import static com.silver.aipets.common.authority.TransitionFailure.TRANSFER_NOT_EXPIRED;
import static com.silver.aipets.common.authority.TransitionFailure.VERSION_EXHAUSTED;
import static com.silver.aipets.common.authority.TransitionFailure.VERSION_MISMATCH;

import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.HeldPlacement;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.TransferMetadata;
import com.silver.aipets.common.domain.TransferringPlacement;
import com.silver.aipets.common.domain.WorldPosition;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure authoritative placement state machine. A repository must persist an applied result with a
 * compare-and-set predicate on the command's expected version; this class never performs I/O.
 */
public final class PetTransitions {
    private PetTransitions() {
    }

    /** Commits a caller-vetted safe placement before the physical entity is spawned. */
    public static TransitionResult place(Pet current, Place command) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(command, "command");
        TransitionFailure common = ownerCasFailure(
                current, command.ownerUuid(), command.expectedVersion(), command.occurredAt());
        if (common != null) {
            return reject(current, common);
        }
        if (!(current.placement() instanceof HeldPlacement)) {
            return reject(current, STATE_MISMATCH);
        }
        return advance(
                current,
                PlacedPlacement.materialized(
                        command.backendId(),
                        command.dimensionId(),
                        command.position(),
                        command.entityUuid()),
                command.occurredAt());
    }

    /**
     * Compensates a post-commit spawn failure only while version, backend, and operation entity UUID
     * still identify that exact placement.
     */
    public static TransitionResult compensatePlaceFailure(
            Pet current, CompensatePlaceFailure command) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(command, "command");
        TransitionFailure common = casFailure(
                current, command.expectedVersion(), command.occurredAt());
        if (common != null) {
            return reject(current, common);
        }
        if (!(current.placement() instanceof PlacedPlacement placed)) {
            return reject(current, STATE_MISMATCH);
        }
        if (!placed.backendId().equals(command.backendId())) {
            return reject(current, BACKEND_MISMATCH);
        }
        if (placed.entityUuid().isEmpty()) {
            return reject(current, ENTITY_NOT_MATERIALIZED);
        }
        if (!placed.entityUuid().orElseThrow().equals(command.entityUuid())) {
            return reject(current, ENTITY_UUID_MISMATCH);
        }
        return advance(current, HeldPlacement.INSTANCE, command.occurredAt());
    }

    /** Pickup validates ownership, exact live identity, local context, and three-dimensional range. */
    public static TransitionResult pickup(Pet current, Pickup command) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(command, "command");
        TransitionFailure common = ownerCasFailure(
                current, command.ownerUuid(), command.expectedVersion(), command.occurredAt());
        if (common != null) {
            return reject(current, common);
        }
        if (!(current.placement() instanceof PlacedPlacement placed)) {
            return reject(current, STATE_MISMATCH);
        }
        TransitionFailure identity = liveIdentityFailure(
                placed, command.backendId(), command.dimensionId(), command.entityUuid());
        if (identity != null) {
            return reject(current, identity);
        }
        if (!command.ownerPosition().isWithin(command.entityPosition(), command.maximumDistance())) {
            return reject(current, OUT_OF_RANGE);
        }
        return advance(current, HeldPlacement.INSTANCE, command.occurredAt());
    }

    /**
     * Invalidates any previous physical representation and commits the recalled representation in
     * one revision. Monthly entitlement enforcement belongs to the transactional service layer.
     */
    public static TransitionResult recall(Pet current, Recall command) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(command, "command");
        TransitionFailure common = ownerCasFailure(
                current, command.ownerUuid(), command.expectedVersion(), command.occurredAt());
        if (common != null) {
            return reject(current, common);
        }
        return advance(
                current,
                PlacedPlacement.materialized(
                        command.destinationBackendId(),
                        command.destinationDimensionId(),
                        command.position(),
                        command.entityUuid()),
                command.occurredAt());
    }

    /** Releases a failed recall only while its exact committed representation is authoritative. */
    public static TransitionResult compensateRecallFailure(
            Pet current, CompensateRecallFailure command) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(command, "command");
        TransitionFailure common = casFailure(
                current, command.expectedVersion(), command.occurredAt());
        if (common != null) {
            return reject(current, common);
        }
        if (!(current.placement() instanceof PlacedPlacement placed)) {
            return reject(current, STATE_MISMATCH);
        }
        if (!placed.backendId().equals(command.destinationBackendId())) {
            return reject(current, BACKEND_MISMATCH);
        }
        if (placed.entityUuid().isEmpty()) {
            return reject(current, ENTITY_NOT_MATERIALIZED);
        }
        if (!placed.entityUuid().orElseThrow().equals(command.entityUuid())) {
            return reject(current, ENTITY_UUID_MISMATCH);
        }
        return advance(current, HeldPlacement.INSTANCE, command.occurredAt());
    }

    /** Reserves a near, exact source representation for one final destination. */
    public static TransitionResult prepareTransfer(Pet current, PrepareTransfer command) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(command, "command");
        TransferMetadata transfer = command.transfer();
        TransitionFailure common = ownerCasFailure(
                current, command.ownerUuid(), command.expectedVersion(), transfer.startedAt());
        if (common != null) {
            return reject(current, common);
        }
        if (!(current.placement() instanceof PlacedPlacement placed)) {
            return reject(current, STATE_MISMATCH);
        }
        TransitionFailure identity = liveIdentityFailure(
                placed,
                transfer.sourceBackendId(),
                command.sourceDimensionId(),
                transfer.sourceEntityUuid());
        if (identity != null) {
            return reject(current, identity);
        }
        if (!command.ownerPosition().isWithin(
                command.entityPosition(), command.maximumDistance())) {
            return reject(current, OUT_OF_RANGE);
        }
        return advance(current, new TransferringPlacement(transfer), transfer.startedAt());
    }

    /** Claims a non-expired transfer only for its exact ID and reserved final backend. */
    public static TransitionResult completeTransfer(Pet current, CompleteTransfer command) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(command, "command");
        TransitionFailure common = casFailure(
                current, command.expectedVersion(), command.occurredAt());
        if (common != null) {
            return reject(current, common);
        }
        if (!(current.placement() instanceof TransferringPlacement transferring)) {
            return reject(current, STATE_MISMATCH);
        }
        TransferMetadata transfer = transferring.transfer();
        if (!transfer.transferId().equals(command.transferId())) {
            return reject(current, TRANSFER_ID_MISMATCH);
        }
        if (!transfer.destinationBackendId().equals(command.destinationBackendId())) {
            return reject(current, DESTINATION_MISMATCH);
        }
        if (transfer.isExpiredAt(command.occurredAt())) {
            return reject(current, TRANSFER_EXPIRED);
        }
        if (transfer.sourceEntityUuid().equals(command.newEntityUuid())) {
            return reject(current, ENTITY_UUID_NOT_FRESH);
        }
        return advance(
                current,
                PlacedPlacement.materialized(
                        command.destinationBackendId(),
                        command.destinationDimensionId(),
                        command.position(),
                        command.newEntityUuid()),
                command.occurredAt());
    }

    /** Expires the exact one-shot reservation at or after its persisted expiry instant. */
    public static TransitionResult expireTransfer(Pet current, ExpireTransfer command) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(command, "command");
        TransitionFailure common = casFailure(
                current, command.expectedVersion(), command.occurredAt());
        if (common != null) {
            return reject(current, common);
        }
        if (!(current.placement() instanceof TransferringPlacement transferring)) {
            return reject(current, STATE_MISMATCH);
        }
        TransferMetadata transfer = transferring.transfer();
        if (!transfer.transferId().equals(command.transferId())) {
            return reject(current, TRANSFER_ID_MISMATCH);
        }
        if (!transfer.isExpiredAt(command.occurredAt())) {
            return reject(current, TRANSFER_NOT_EXPIRED);
        }
        return advance(current, HeldPlacement.INSTANCE, command.occurredAt());
    }

    /** Explicit operator recovery invalidates any expected physical representation. */
    public static TransitionResult adminRecover(Pet current, AdminRecover command) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(command, "command");
        TransitionFailure common = casFailure(
                current, command.expectedVersion(), command.occurredAt());
        if (common != null) {
            return reject(current, common);
        }
        if (current.placement() instanceof HeldPlacement) {
            return reject(current, STATE_MISMATCH);
        }
        return advance(current, HeldPlacement.INSTANCE, command.occurredAt());
    }

    private static TransitionFailure ownerCasFailure(
            Pet current, UUID ownerUuid, long expectedVersion, Instant occurredAt) {
        if (!current.ownerUuid().equals(ownerUuid)) {
            return OWNER_MISMATCH;
        }
        return casFailure(current, expectedVersion, occurredAt);
    }

    private static TransitionFailure casFailure(
            Pet current, long expectedVersion, Instant occurredAt) {
        if (current.recordVersion() != expectedVersion) {
            return VERSION_MISMATCH;
        }
        if (occurredAt.isBefore(current.updatedAt())) {
            return TIMESTAMP_BEFORE_CURRENT;
        }
        if (current.recordVersion() == Long.MAX_VALUE) {
            return VERSION_EXHAUSTED;
        }
        return null;
    }

    private static TransitionFailure liveIdentityFailure(
            PlacedPlacement placed,
            BackendId backendId,
            DimensionId dimensionId,
            UUID entityUuid) {
        if (!placed.backendId().equals(backendId)) {
            return BACKEND_MISMATCH;
        }
        if (!placed.dimensionId().equals(dimensionId)) {
            return DIMENSION_MISMATCH;
        }
        if (placed.entityUuid().isEmpty()) {
            return ENTITY_NOT_MATERIALIZED;
        }
        if (!placed.entityUuid().orElseThrow().equals(entityUuid)) {
            return ENTITY_UUID_MISMATCH;
        }
        return null;
    }

    private static TransitionResult advance(
            Pet current, com.silver.aipets.common.domain.PetPlacement placement, Instant occurredAt) {
        return TransitionResult.applied(new Pet(
                current.petId(),
                current.ownerUuid(),
                current.name(),
                current.appearance(),
                current.traits(),
                current.mood(),
                placement,
                current.recordVersion() + 1,
                current.createdAt(),
                occurredAt));
    }

    private static TransitionResult reject(Pet current, TransitionFailure failure) {
        return TransitionResult.rejected(current, failure);
    }

    public record Place(
            UUID ownerUuid,
            long expectedVersion,
            BackendId backendId,
            DimensionId dimensionId,
            WorldPosition position,
            UUID entityUuid,
            Instant occurredAt) {
        public Place {
            Objects.requireNonNull(ownerUuid, "ownerUuid");
            requireExpectedVersion(expectedVersion);
            Objects.requireNonNull(backendId, "backendId");
            Objects.requireNonNull(dimensionId, "dimensionId");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(entityUuid, "entityUuid");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    public record CompensatePlaceFailure(
            long expectedVersion,
            BackendId backendId,
            UUID entityUuid,
            Instant occurredAt) {
        public CompensatePlaceFailure {
            requireExpectedVersion(expectedVersion);
            Objects.requireNonNull(backendId, "backendId");
            Objects.requireNonNull(entityUuid, "entityUuid");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    public record Pickup(
            UUID ownerUuid,
            long expectedVersion,
            BackendId backendId,
            DimensionId dimensionId,
            UUID entityUuid,
            WorldPosition ownerPosition,
            WorldPosition entityPosition,
            double maximumDistance,
            Instant occurredAt) {
        public Pickup {
            Objects.requireNonNull(ownerUuid, "ownerUuid");
            requireExpectedVersion(expectedVersion);
            Objects.requireNonNull(backendId, "backendId");
            Objects.requireNonNull(dimensionId, "dimensionId");
            Objects.requireNonNull(entityUuid, "entityUuid");
            Objects.requireNonNull(ownerPosition, "ownerPosition");
            Objects.requireNonNull(entityPosition, "entityPosition");
            requireDistance(maximumDistance);
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    public record Recall(
            UUID ownerUuid,
            long expectedVersion,
            BackendId destinationBackendId,
            DimensionId destinationDimensionId,
            WorldPosition position,
            UUID entityUuid,
            Instant occurredAt) {
        public Recall {
            Objects.requireNonNull(ownerUuid, "ownerUuid");
            requireExpectedVersion(expectedVersion);
            Objects.requireNonNull(destinationBackendId, "destinationBackendId");
            Objects.requireNonNull(destinationDimensionId, "destinationDimensionId");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(entityUuid, "entityUuid");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    public record CompensateRecallFailure(
            long expectedVersion,
            BackendId destinationBackendId,
            UUID entityUuid,
            Instant occurredAt) {
        public CompensateRecallFailure {
            requireExpectedVersion(expectedVersion);
            Objects.requireNonNull(destinationBackendId, "destinationBackendId");
            Objects.requireNonNull(entityUuid, "entityUuid");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    public record PrepareTransfer(
            UUID ownerUuid,
            long expectedVersion,
            DimensionId sourceDimensionId,
            WorldPosition ownerPosition,
            WorldPosition entityPosition,
            double maximumDistance,
            TransferMetadata transfer) {
        public PrepareTransfer {
            Objects.requireNonNull(ownerUuid, "ownerUuid");
            requireExpectedVersion(expectedVersion);
            Objects.requireNonNull(sourceDimensionId, "sourceDimensionId");
            Objects.requireNonNull(ownerPosition, "ownerPosition");
            Objects.requireNonNull(entityPosition, "entityPosition");
            requireDistance(maximumDistance);
            Objects.requireNonNull(transfer, "transfer");
        }
    }

    public record CompleteTransfer(
            long expectedVersion,
            UUID transferId,
            BackendId destinationBackendId,
            DimensionId destinationDimensionId,
            WorldPosition position,
            UUID newEntityUuid,
            Instant occurredAt) {
        public CompleteTransfer {
            requireExpectedVersion(expectedVersion);
            Objects.requireNonNull(transferId, "transferId");
            Objects.requireNonNull(destinationBackendId, "destinationBackendId");
            Objects.requireNonNull(destinationDimensionId, "destinationDimensionId");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(newEntityUuid, "newEntityUuid");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    public record ExpireTransfer(long expectedVersion, UUID transferId, Instant occurredAt) {
        public ExpireTransfer {
            requireExpectedVersion(expectedVersion);
            Objects.requireNonNull(transferId, "transferId");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    public record AdminRecover(long expectedVersion, Instant occurredAt) {
        public AdminRecover {
            requireExpectedVersion(expectedVersion);
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    private static void requireExpectedVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }
    }

    private static void requireDistance(double maximumDistance) {
        if (!Double.isFinite(maximumDistance) || maximumDistance < 0.0) {
            throw new IllegalArgumentException("maximumDistance must be finite and non-negative");
        }
    }
}
