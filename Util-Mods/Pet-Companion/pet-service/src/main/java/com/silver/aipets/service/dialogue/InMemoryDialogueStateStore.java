package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.MoodDimension;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.TraitCategory;
import com.silver.aipets.common.domain.TraitName;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Thread-safe transactional development adapter with exact daily and final clamps. */
public final class InMemoryDialogueStateStore implements DialogueStateStore {
    private final Object monitor = new Object();
    private final Map<UUID, Pet> pets = new HashMap<>();
    private final Map<DailyTraitKey, Integer> dailyAbsoluteChanges = new HashMap<>();
    private final Map<UUID, DialogueEvent> events = new HashMap<>();
    private final Map<UUID, DialogueUsage> usageByRequest = new HashMap<>();
    private final List<TraitAudit> audits = new ArrayList<>();

    public void put(Pet pet) {
        synchronized (monitor) {
            pets.put(pet.petId(), Objects.requireNonNull(pet, "pet"));
        }
    }

    public Pet pet(UUID petId) {
        synchronized (monitor) {
            return Objects.requireNonNull(pets.get(petId), "Unknown pet");
        }
    }

    @Override
    public DialoguePersistResult commit(DialoguePersistRequest request) {
        Objects.requireNonNull(request, "request");
        synchronized (monitor) {
            if (usageByRequest.containsKey(request.usage().requestId())) {
                throw new IllegalStateException("Dialogue request was already persisted");
            }
            if (events.containsKey(request.eventId())) {
                throw new IllegalStateException("Dialogue event ID already exists");
            }
            Pet current = pets.get(request.expectedPet().petId());
            if (current == null || !current.ownerUuid().equals(request.expectedPet().ownerUuid())
                    || !current.traits().equals(request.expectedPet().traits())
                    || !current.mood().equals(request.expectedPet().mood())) {
                throw new IllegalStateException("Authoritative pet state changed before dialogue commit");
            }

            LocalDate day = request.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
            Map<TraitName, Integer> appliedTraits = new EnumMap<>(TraitName.class);
            int[] traitValues = traitValues(current.traits());
            for (TraitName trait : TraitName.values()) {
                int proposed = request.output().proposedTraitDeltas().get(trait);
                int perEventLimit = trait.category() == TraitCategory.TEMPERAMENT ? 1 : 2;
                int dailyLimit = trait.category() == TraitCategory.TEMPERAMENT ? 3 : 6;
                DailyTraitKey key = new DailyTraitKey(current.petId(), day, trait);
                int remaining = Math.max(0, dailyLimit - dailyAbsoluteChanges.getOrDefault(key, 0));
                int bounded = clamp(proposed, -perEventLimit, perEventLimit);
                bounded = clamp(bounded, -remaining, remaining);
                int oldValue = traitValues[trait.ordinal()];
                int newValue = clamp(oldValue + bounded, 0, 100);
                int applied = newValue - oldValue;
                traitValues[trait.ordinal()] = newValue;
                appliedTraits.put(trait, applied);
                if (applied != 0) {
                    dailyAbsoluteChanges.merge(key, Math.abs(applied), Math::addExact);
                    audits.add(new TraitAudit(
                            current.petId(), request.eventId(), trait, oldValue,
                            proposed, applied, newValue));
                }
            }

            Map<MoodDimension, Integer> appliedMood = new EnumMap<>(MoodDimension.class);
            int[] moodValues = moodValues(current.mood());
            for (MoodDimension dimension : MoodDimension.values()) {
                int proposed = request.output().proposedMoodDeltas().get(dimension);
                int bounded = clamp(proposed, -10, 10);
                int oldValue = moodValues[dimension.ordinal()];
                int newValue = clamp(oldValue + bounded, 0, 100);
                moodValues[dimension.ordinal()] = newValue;
                appliedMood.put(dimension, newValue - oldValue);
            }

            PetTraits traits = new PetTraits(
                    traitValues[0], traitValues[1], traitValues[2], traitValues[3],
                    traitValues[4], traitValues[5], traitValues[6], traitValues[7],
                    current.traits().relationshipSummary(), current.traits().summaryVersion(),
                    request.occurredAt());
            PetMood mood = new PetMood(
                    moodValues[0], moodValues[1], moodValues[2], moodValues[3],
                    current.mood().lastDecayAt(), request.occurredAt());
            Pet updated = new Pet(
                    current.petId(), current.ownerUuid(), current.name(), current.appearance(),
                    traits, mood, current.placement(), current.recordVersion(),
                    current.createdAt(), request.occurredAt());

            Optional<java.time.Instant> expiry = request.output().importance()
                    .expiresAt(request.occurredAt());
            events.put(request.eventId(), new DialogueEvent(
                    request.eventId(), current.petId(), current.ownerUuid(),
                    request.output().importance(), request.output().memoryCandidate(), expiry));
            usageByRequest.put(request.usage().requestId(), request.usage());
            pets.put(current.petId(), updated);
            return new DialoguePersistResult(traits, mood, appliedTraits, appliedMood, expiry);
        }
    }

    @Override
    public void recordUsage(DialogueUsage usage) {
        Objects.requireNonNull(usage, "usage");
        synchronized (monitor) {
            usageByRequest.putIfAbsent(usage.requestId(), usage);
        }
    }

    public int eventCount() {
        synchronized (monitor) {
            return events.size();
        }
    }

    public int usageCount() {
        synchronized (monitor) {
            return usageByRequest.size();
        }
    }

    public Optional<java.time.Instant> eventExpiry(UUID eventId) {
        synchronized (monitor) {
            DialogueEvent event = Objects.requireNonNull(events.get(eventId), "Unknown event");
            return event.expiresAt();
        }
    }

    public List<TraitAudit> audits() {
        synchronized (monitor) {
            return List.copyOf(audits);
        }
    }

    private static int[] traitValues(PetTraits traits) {
        return java.util.Arrays.stream(TraitName.values()).mapToInt(traits::value).toArray();
    }

    private static int[] moodValues(PetMood mood) {
        return java.util.Arrays.stream(MoodDimension.values()).mapToInt(mood::value).toArray();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record DailyTraitKey(UUID petId, LocalDate day, TraitName trait) {
    }

    private record DialogueEvent(
            UUID eventId,
            UUID petId,
            UUID ownerUuid,
            DialogueImportance importance,
            Optional<String> memoryCandidate,
            Optional<java.time.Instant> expiresAt) {
    }

    public record TraitAudit(
            UUID petId,
            UUID eventId,
            TraitName trait,
            int oldValue,
            int proposedDelta,
            int appliedDelta,
            int newValue) {
    }
}
