package com.silver.aipets.service.transfer;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.TransferringPlacement;
import com.silver.aipets.service.persistence.PetRepository;
import com.silver.aipets.service.placement.PetMutationStatus;
import com.silver.aipets.service.placement.PetPlacementService;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Recovers persisted, abandoned transfer reservations without relying on proxy process memory. */
public final class PetTransferExpiryWorker {
    private final PetRepository repository;
    private final PetPlacementService placements;
    private final Clock clock;

    public PetTransferExpiryWorker(
            PetRepository repository, PetPlacementService placements, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.placements = Objects.requireNonNull(placements, "placements");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PetTransferExpiryRun processDue(int limit) {
        List<Pet> due = repository.findExpiredTransfers(clock.instant(), limit);
        int expired = 0;
        for (Pet pet : due) {
            if (!(pet.placement() instanceof TransferringPlacement transferring)) continue;
            var transfer = transferring.transfer();
            var operation = placements.expireTransfer(
                    transfer.transferId(),
                    pet.petId(),
                    new PetTransitions.ExpireTransfer(
                            pet.recordVersion(), transfer.transferId(), transfer.expiresAt()));
            if (operation.mutation().isPresent()
                    && operation.mutation().orElseThrow().status() == PetMutationStatus.APPLIED) {
                expired++;
            }
        }
        return new PetTransferExpiryRun(due.size(), expired, due.size() - expired);
    }
}
