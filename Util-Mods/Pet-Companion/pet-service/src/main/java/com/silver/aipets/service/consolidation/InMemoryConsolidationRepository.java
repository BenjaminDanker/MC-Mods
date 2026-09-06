package com.silver.aipets.service.consolidation;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.TraitCategory;
import com.silver.aipets.common.domain.TraitName;
import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.dialogue.DialogueUsage;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Thread-safe development adapter that applies the full consolidation commit atomically. */
public final class InMemoryConsolidationRepository implements ConsolidationRepository {
    private final Object monitor = new Object();
    private final Supplier<UUID> memoryIds;
    private final Supplier<UUID> auditIds;
    private final Map<UUID, Pet> pets = new HashMap<>();
    private final Map<UUID, StoredEvent> events = new LinkedHashMap<>();
    private final Map<UUID, ConsolidationJob> jobs = new LinkedHashMap<>();
    private final Map<String, UUID> jobsByKey = new HashMap<>();
    private final Map<UUID, LongTermMemoryCard> memories = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> memorySources = new HashMap<>();
    private final Map<DailyTraitKey, Integer> dailyAbsoluteChanges = new HashMap<>();
    private final List<ConsolidationTraitAudit> audits = new ArrayList<>();
    private final Map<UUID, DialogueUsage> usageByRequest = new HashMap<>();

    public InMemoryConsolidationRepository(
            Supplier<UUID> memoryIds, Supplier<UUID> auditIds) {
        this.memoryIds = Objects.requireNonNull(memoryIds, "memoryIds");
        this.auditIds = Objects.requireNonNull(auditIds, "auditIds");
    }

    public void putPet(Pet pet) {
        synchronized (monitor) {
            pets.put(pet.petId(), Objects.requireNonNull(pet, "pet"));
        }
    }

    public void addEvent(ConsolidationEvent event) {
        synchronized (monitor) {
            Objects.requireNonNull(event, "event");
            if (!pets.containsKey(event.petId()) || events.putIfAbsent(
                    event.eventId(), new StoredEvent(event, ConsolidationEventStatus.PENDING, true)) != null) {
                throw new IllegalStateException("Event pet is missing or event ID already exists");
            }
        }
    }

    @Override
    public EnqueueResult enqueue(ConsolidationJob proposed) {
        synchronized (monitor) {
            Objects.requireNonNull(proposed, "proposed");
            UUID existingId = jobsByKey.get(proposed.idempotencyKey());
            if (existingId != null) return new EnqueueResult(jobs.get(existingId), false);
            if (!pets.containsKey(proposed.petId()) || jobs.containsKey(proposed.jobId())) {
                throw new IllegalStateException("Job pet is missing or job ID already exists");
            }
            jobs.put(proposed.jobId(), proposed);
            jobsByKey.put(proposed.idempotencyKey(), proposed.jobId());
            return new EnqueueResult(proposed, true);
        }
    }

    @Override
    public List<ConsolidationJob> claimDue(
            String workerId, Instant now, Duration lease, int limit) {
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(lease, "lease");
        if (workerId.isBlank() || workerId.length() > 191 || lease.isNegative()
                || lease.isZero() || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid consolidation claim");
        }
        synchronized (monitor) {
            List<ConsolidationJob> due = jobs.values().stream()
                    .filter(job -> (job.status() == ConsolidationJobStatus.PENDING
                            || job.status() == ConsolidationJobStatus.RETRY
                            || (job.status() == ConsolidationJobStatus.RUNNING
                            && !job.lockedUntil().orElseThrow().isAfter(now))))
                    .filter(job -> !job.notBefore().isAfter(now))
                    .sorted(Comparator.comparing(ConsolidationJob::notBefore)
                            .thenComparing(job -> job.jobId().toString()))
                    .limit(limit).toList();
            List<ConsolidationJob> claimed = new ArrayList<>();
            for (ConsolidationJob job : due) {
                ConsolidationJob running = new ConsolidationJob(
                        job.jobId(), job.petId(), job.sleepCycleId(), job.idempotencyKey(),
                        ConsolidationJobStatus.RUNNING, job.attemptCount() + 1, job.notBefore(),
                        Optional.of(workerId), Optional.of(now.plus(lease)),
                        job.lastErrorCategory());
                jobs.put(job.jobId(), running);
                claimed.add(running);
            }
            return List.copyOf(claimed);
        }
    }

    @Override
    public Optional<Pet> findPet(UUID petId) {
        synchronized (monitor) {
            return Optional.ofNullable(pets.get(Objects.requireNonNull(petId, "petId")));
        }
    }

    @Override
    public List<ConsolidationEvent> pendingEvents(UUID petId) {
        synchronized (monitor) {
            return events.values().stream()
                    .filter(stored -> stored.event().petId().equals(petId)
                            && stored.status() == ConsolidationEventStatus.PENDING)
                    .map(StoredEvent::event)
                    .sorted(Comparator.comparing(ConsolidationEvent::occurredAt)
                            .thenComparing(event -> event.eventId().toString()))
                    .toList();
        }
    }

    @Override
    public void completeWithoutModel(
            ConsolidationJob job, String workerId,
            List<UUID> discardedEventIds, Instant at) {
        synchronized (monitor) {
            requireLock(job, workerId);
            markEvents(job.petId(), discardedEventIds, ConsolidationEventStatus.DISCARDED);
            finish(job, ConsolidationJobStatus.SUCCEEDED, at, Optional.empty());
        }
    }

    @Override
    public ConsolidationCommitResult commit(
            ConsolidationJob job,
            String workerId,
            CandidateSelection selection,
            ValidatedConsolidationOutput output,
            DialogueUsage usage,
            Instant at) {
        synchronized (monitor) {
            requireLock(job, workerId);
            Objects.requireNonNull(usage, "usage");
            if (usageByRequest.containsKey(usage.requestId())
                    || !usage.petId().equals(job.petId())
                    || usage.status() != DialogueUsage.Status.SUCCEEDED) {
                throw new IllegalStateException("Invalid or duplicate consolidation usage");
            }
            Pet current = Objects.requireNonNull(pets.get(job.petId()), "Unknown job pet");
            Set<UUID> selectedIds = selection.selected().stream()
                    .map(ConsolidationEvent::eventId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            for (UUID eventId : selectedIds) requirePendingPetEvent(eventId, job.petId());
            for (UUID eventId : selection.discardedEventIds()) requirePendingPetEvent(eventId, job.petId());

            // Construct and validate every new object before mutating any stored state.
            List<LongTermMemoryCard> created = new ArrayList<>();
            Set<UUID> allocatedMemoryIds = new HashSet<>();
            for (ConsolidationCardProposal proposal : output.memoryCards()) {
                UUID memoryId = Objects.requireNonNull(memoryIds.get(), "memoryIds returned null");
                if (memories.containsKey(memoryId) || !allocatedMemoryIds.add(memoryId)) {
                    throw new IllegalStateException("Memory ID collision");
                }
                if (!selectedIds.containsAll(proposal.sourceEventIds())) {
                    throw new IllegalStateException("Memory cites an unselected source event");
                }
                LongTermMemoryCard card = new LongTermMemoryCard(
                        memoryId, job.petId(), 1, proposal.text(), proposal.importance(),
                        proposal.emotionTags(), proposal.entityTags(), proposal.locationTags(), true);
                created.add(card);
            }
            AppliedTraits applied = applyTraits(current.traits(), output, job, at);
            Pet updated = new Pet(
                    current.petId(), current.ownerUuid(), current.name(), current.appearance(),
                    applied.traits(), current.mood(), current.placement(), current.recordVersion(),
                    current.createdAt(), at);
            LocalDate day = at.atZone(ZoneOffset.UTC).toLocalDate();
            for (ConsolidationTraitAudit audit : applied.audits()) {
                dailyAbsoluteChanges.merge(
                        new DailyTraitKey(job.petId(), day, audit.trait()),
                        Math.abs(audit.appliedDelta()), Math::addExact);
            }
            for (int index = 0; index < created.size(); index++) {
                LongTermMemoryCard card = created.get(index);
                ConsolidationCardProposal proposal = output.memoryCards().get(index);
                memories.put(card.memoryId(), card);
                memorySources.put(card.memoryId(), Set.copyOf(proposal.sourceEventIds()));
            }
            markEvents(job.petId(), selectedIds, ConsolidationEventStatus.CONSOLIDATED);
            markEvents(job.petId(), selection.discardedEventIds(), ConsolidationEventStatus.DISCARDED);
            pets.put(job.petId(), updated);
            audits.addAll(applied.audits());
            usageByRequest.put(usage.requestId(), usage);
            finish(job, ConsolidationJobStatus.SUCCEEDED, at, Optional.empty());
            return new ConsolidationCommitResult(
                    created, applied.traits(), selectedIds.size(), selection.discardedEventIds().size());
        }
    }

    @Override
    public void recordUsage(DialogueUsage usage) {
        synchronized (monitor) {
            Objects.requireNonNull(usage, "usage");
            usageByRequest.putIfAbsent(usage.requestId(), usage);
        }
    }

    @Override
    public void retry(
            ConsolidationJob job, String workerId, Instant attemptedAt,
            Instant notBefore, String errorCategory) {
        synchronized (monitor) {
            requireLock(job, workerId);
            jobs.put(job.jobId(), new ConsolidationJob(
                    job.jobId(), job.petId(), job.sleepCycleId(), job.idempotencyKey(),
                    ConsolidationJobStatus.RETRY, job.attemptCount(), notBefore,
                    Optional.empty(), Optional.empty(), Optional.of(boundedError(errorCategory))));
        }
    }

    @Override
    public void failed(
            ConsolidationJob job, String workerId, Instant at, String errorCategory) {
        synchronized (monitor) {
            requireLock(job, workerId);
            finish(job, ConsolidationJobStatus.FAILED, at,
                    Optional.of(boundedError(errorCategory)));
        }
    }

    @Override
    public Optional<ConsolidationJob> findByKey(String idempotencyKey) {
        synchronized (monitor) {
            UUID jobId = jobsByKey.get(Objects.requireNonNull(idempotencyKey, "idempotencyKey"));
            return jobId == null ? Optional.empty() : Optional.of(jobs.get(jobId));
        }
    }

    public Pet pet(UUID petId) {
        synchronized (monitor) { return Objects.requireNonNull(pets.get(petId)); }
    }

    public List<LongTermMemoryCard> memories(UUID petId) {
        synchronized (monitor) {
            return memories.values().stream().filter(card -> card.petId().equals(petId)).toList();
        }
    }

    public Set<UUID> memorySources(UUID memoryId) {
        synchronized (monitor) { return Set.copyOf(memorySources.getOrDefault(memoryId, Set.of())); }
    }

    public ConsolidationEventStatus eventStatus(UUID eventId) {
        synchronized (monitor) { return Objects.requireNonNull(events.get(eventId)).status(); }
    }

    public boolean promptEligible(UUID eventId) {
        synchronized (monitor) { return Objects.requireNonNull(events.get(eventId)).promptEligible(); }
    }

    public List<ConsolidationTraitAudit> audits() {
        synchronized (monitor) { return List.copyOf(audits); }
    }

    public int usageCount() {
        synchronized (monitor) { return usageByRequest.size(); }
    }

    private AppliedTraits applyTraits(
            PetTraits current,
            ValidatedConsolidationOutput output,
            ConsolidationJob job,
            Instant at) {
        LocalDate day = at.atZone(ZoneOffset.UTC).toLocalDate();
        int[] values = java.util.Arrays.stream(TraitName.values()).mapToInt(current::value).toArray();
        List<ConsolidationTraitAudit> createdAudits = new ArrayList<>();
        for (TraitName trait : TraitName.values()) {
            int perJob = trait.category() == TraitCategory.TEMPERAMENT ? 1 : 2;
            int daily = trait.category() == TraitCategory.TEMPERAMENT ? 3 : 6;
            DailyTraitKey key = new DailyTraitKey(job.petId(), day, trait);
            int remaining = Math.max(0, daily - dailyAbsoluteChanges.getOrDefault(key, 0));
            int proposed = output.proposedTraitDeltas().get(trait);
            int bounded = clamp(clamp(proposed, -perJob, perJob), -remaining, remaining);
            int oldValue = values[trait.ordinal()];
            int newValue = clamp(oldValue + bounded, 0, 100);
            int delta = newValue - oldValue;
            values[trait.ordinal()] = newValue;
            if (delta != 0) {
                createdAudits.add(new ConsolidationTraitAudit(
                        Objects.requireNonNull(auditIds.get(), "auditIds returned null"),
                        job.petId(), job.jobId(), trait, oldValue, proposed, delta, newValue, at));
            }
        }
        PetTraits traits = new PetTraits(
                values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7],
                output.relationshipSummary(), current.summaryVersion() + 1, at);
        return new AppliedTraits(traits, createdAudits);
    }

    private void markEvents(
            UUID petId, Iterable<UUID> eventIds, ConsolidationEventStatus status) {
        for (UUID eventId : eventIds) {
            StoredEvent current = requirePendingPetEvent(eventId, petId);
            events.put(eventId, new StoredEvent(current.event(), status, false));
        }
    }

    private StoredEvent requirePendingPetEvent(UUID eventId, UUID petId) {
        StoredEvent event = events.get(eventId);
        if (event == null || !event.event().petId().equals(petId)
                || event.status() != ConsolidationEventStatus.PENDING) {
            throw new IllegalStateException("Event is missing, cross-pet, or already processed");
        }
        return event;
    }

    private void requireLock(ConsolidationJob claimed, String workerId) {
        ConsolidationJob current = jobs.get(claimed.jobId());
        if (current == null || current.status() != ConsolidationJobStatus.RUNNING
                || !current.lockedBy().equals(Optional.of(workerId))
                || current.attemptCount() != claimed.attemptCount()) {
            throw new IllegalStateException("Consolidation job lease is not owned by worker");
        }
    }

    private void finish(
            ConsolidationJob job, ConsolidationJobStatus status,
            Instant at, Optional<String> error) {
        jobs.put(job.jobId(), new ConsolidationJob(
                job.jobId(), job.petId(), job.sleepCycleId(), job.idempotencyKey(),
                status, job.attemptCount(), at, Optional.empty(), Optional.empty(), error));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String boundedError(String error) {
        Objects.requireNonNull(error, "error");
        String value = error.isBlank() ? "RuntimeException" : error;
        return value.substring(0, Math.min(value.length(), 128));
    }

    private record StoredEvent(
            ConsolidationEvent event, ConsolidationEventStatus status, boolean promptEligible) {
    }

    private record DailyTraitKey(UUID petId, LocalDate day, TraitName trait) {
    }

    private record AppliedTraits(PetTraits traits, List<ConsolidationTraitAudit> audits) {
    }
}
