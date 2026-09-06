package com.silver.aipets.service.adoption;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.service.persistence.CreatePetResult;
import com.silver.aipets.service.persistence.PetRepository;
import com.silver.aipets.service.subscription.SubscriptionAccess;
import com.silver.aipets.service.metrics.PetOperationalMetrics;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.Set;
import java.util.EnumSet;

/** Transactional one-pet-per-owner adoption orchestration. */
public final class PetAdoptionService {
    private final PetRepository repository;
    private final SubscriptionAccess subscriptionAccess;
    private final PetRandomizer randomizer;
    private final Clock clock;
    private final Supplier<UUID> petIdGenerator;
    private final Set<PetSpecies> allowedSpecies;
    private final PetOperationalMetrics metrics;

    public PetAdoptionService(
            PetRepository repository,
            SubscriptionAccess subscriptionAccess,
            PetRandomizer randomizer,
            Clock clock,
            Supplier<UUID> petIdGenerator) {
        this(repository, subscriptionAccess, randomizer, clock, petIdGenerator,
                EnumSet.allOf(PetSpecies.class), new PetOperationalMetrics());
    }

    public PetAdoptionService(
            PetRepository repository,
            SubscriptionAccess subscriptionAccess,
            PetRandomizer randomizer,
            Clock clock,
            Supplier<UUID> petIdGenerator,
            Set<PetSpecies> allowedSpecies) {
        this(repository, subscriptionAccess, randomizer, clock, petIdGenerator,
                allowedSpecies, new PetOperationalMetrics());
    }

    public PetAdoptionService(
            PetRepository repository,
            SubscriptionAccess subscriptionAccess,
            PetRandomizer randomizer,
            Clock clock,
            Supplier<UUID> petIdGenerator,
            Set<PetSpecies> allowedSpecies,
            PetOperationalMetrics metrics) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.subscriptionAccess = Objects.requireNonNull(subscriptionAccess, "subscriptionAccess");
        this.randomizer = Objects.requireNonNull(randomizer, "randomizer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.petIdGenerator = Objects.requireNonNull(petIdGenerator, "petIdGenerator");
        this.allowedSpecies = Set.copyOf(Objects.requireNonNull(allowedSpecies, "allowedSpecies"));
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        if (this.allowedSpecies.isEmpty()) {
            throw new IllegalArgumentException("At least one supported pet species must be allowed");
        }
    }

    public AdoptionResult adopt(UUID ownerUuid, PetSpecies species, String name) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(species, "species");

        var existing = repository.findByOwner(ownerUuid);
        if (existing.isPresent()) {
            return AdoptionResult.existing(existing.orElseThrow());
        }
        if (!allowedSpecies.contains(species)) {
            return AdoptionResult.speciesUnavailable();
        }
        if (!subscriptionAccess.canAdopt(ownerUuid)) {
            return AdoptionResult.subscriptionRequired();
        }

        Instant adoptedAt = clock.instant();
        AdoptionProfile profile = randomizer.generate(species, adoptedAt);
        Pet proposed = Pet.adopted(
                Objects.requireNonNull(petIdGenerator.get(), "Generated pet ID"),
                ownerUuid,
                name,
                profile.appearance(),
                profile.traits(),
                profile.mood(),
                adoptedAt);
        CreatePetResult result = repository.createIfOwnerAbsent(proposed);
        if (!result.created()) {
            return AdoptionResult.existing(result.pet());
        }
        if (species == PetSpecies.CAT) {
            metrics.increment(PetOperationalMetrics.Counter.PETS_CREATED_CAT);
        } else if (species == PetSpecies.DOG) {
            metrics.increment(PetOperationalMetrics.Counter.PETS_CREATED_DOG);
        }
        return AdoptionResult.created(result.pet());
    }
}
