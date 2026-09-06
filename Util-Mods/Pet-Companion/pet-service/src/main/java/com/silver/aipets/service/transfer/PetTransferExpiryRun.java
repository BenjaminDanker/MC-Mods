package com.silver.aipets.service.transfer;

/** Bounded scheduler result with no owner or pet identifiers. */
public record PetTransferExpiryRun(int scanned, int expired, int unchanged) {
    public PetTransferExpiryRun {
        if (scanned < 0 || expired < 0 || unchanged < 0 || expired + unchanged != scanned) {
            throw new IllegalArgumentException("Invalid transfer expiry counts");
        }
    }
}
