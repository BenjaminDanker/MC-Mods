package com.silver.aipets.service.placement;

import com.silver.aipets.common.authority.PetTransitions;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Canonical fingerprints prevent one operation key from being reused for another command. */
public final class PetMutationFingerprints {
    private PetMutationFingerprints() {
    }

    public static String forPet(UUID petId, String commandFingerprint) {
        return digest(petId.toString(), commandFingerprint);
    }

    public static String place(PetTransitions.Place command) {
        return digest(
                command.ownerUuid().toString(), Long.toString(command.expectedVersion()),
                command.backendId().value(), command.dimensionId().toString(),
                Double.toHexString(command.position().x()), Double.toHexString(command.position().y()),
                Double.toHexString(command.position().z()), command.entityUuid().toString(),
                command.occurredAt().toString());
    }

    public static String compensate(PetTransitions.CompensatePlaceFailure command) {
        return digest(
                Long.toString(command.expectedVersion()), command.backendId().value(),
                command.entityUuid().toString(), command.occurredAt().toString());
    }

    public static String pickup(PetTransitions.Pickup command) {
        return digest(
                command.ownerUuid().toString(), Long.toString(command.expectedVersion()),
                command.backendId().value(), command.dimensionId().toString(),
                command.entityUuid().toString(),
                Double.toHexString(command.ownerPosition().x()),
                Double.toHexString(command.ownerPosition().y()),
                Double.toHexString(command.ownerPosition().z()),
                Double.toHexString(command.entityPosition().x()),
                Double.toHexString(command.entityPosition().y()),
                Double.toHexString(command.entityPosition().z()),
                Double.toHexString(command.maximumDistance()), command.occurredAt().toString());
    }

    public static String prepareTransfer(PetTransitions.PrepareTransfer command) {
        var transfer = command.transfer();
        return digest(
                command.ownerUuid().toString(), Long.toString(command.expectedVersion()),
                command.sourceDimensionId().toString(),
                Double.toHexString(command.ownerPosition().x()),
                Double.toHexString(command.ownerPosition().y()),
                Double.toHexString(command.ownerPosition().z()),
                Double.toHexString(command.entityPosition().x()),
                Double.toHexString(command.entityPosition().y()),
                Double.toHexString(command.entityPosition().z()),
                Double.toHexString(command.maximumDistance()), transfer.transferId().toString(),
                transfer.sourceBackendId().value(), transfer.sourceEntityUuid().toString(),
                transfer.destinationBackendId().value(), transfer.startedAt().toString(),
                transfer.expiresAt().toString());
    }

    public static String completeTransfer(PetTransitions.CompleteTransfer command) {
        return digest(
                Long.toString(command.expectedVersion()), command.transferId().toString(),
                command.destinationBackendId().value(), command.destinationDimensionId().toString(),
                Double.toHexString(command.position().x()), Double.toHexString(command.position().y()),
                Double.toHexString(command.position().z()), command.newEntityUuid().toString(),
                command.occurredAt().toString());
    }

    public static String expireTransfer(PetTransitions.ExpireTransfer command) {
        return digest(
                Long.toString(command.expectedVersion()), command.transferId().toString(),
                command.occurredAt().toString());
    }

    public static String adminRecover(PetTransitions.AdminRecover command) {
        return digest(Long.toString(command.expectedVersion()), command.occurredAt().toString());
    }

    private static String digest(String... fields) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        for (String field : fields) {
            byte[] encoded = field.getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(encoded.length).array());
            digest.update(encoded);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
}
