package com.silver.aipets.service.dialogue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Atomic cooldown/daily/concurrency/spend/circuit admission with one active call per pet. */
public final class DialogueAdmissionController {
    private final DialogueLimits limits;
    private final Set<UUID> activePets = new HashSet<>();
    private final Map<UUID, Instant> lastSuccessByOwner = new HashMap<>();
    private final Map<OwnerDay, Integer> successesByOwnerDay = new HashMap<>();
    private final Map<LocalDate, BigDecimal> dailyCost = new HashMap<>();
    private final Map<YearMonth, BigDecimal> monthlyCost = new HashMap<>();
    private int activeGlobal;
    private int consecutiveProviderFailures;
    private Optional<Instant> circuitOpenUntil = Optional.empty();

    public DialogueAdmissionController(DialogueLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public synchronized Admission acquire(UUID petId, UUID ownerUuid, Instant now) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(now, "now");
        if (circuitOpenUntil.filter(deadline -> deadline.isAfter(now)).isPresent()) {
            return Admission.denied(Denial.CIRCUIT_OPEN);
        }
        circuitOpenUntil = Optional.empty();
        LocalDate day = now.atZone(ZoneOffset.UTC).toLocalDate();
        YearMonth month = YearMonth.from(now.atZone(ZoneOffset.UTC));
        if (dailyCost.getOrDefault(day, BigDecimal.ZERO)
                .compareTo(limits.globalDailyCostCap()) >= 0
                || monthlyCost.getOrDefault(month, BigDecimal.ZERO)
                .compareTo(limits.globalMonthlyCostCap()) >= 0) {
            return Admission.denied(Denial.GLOBAL_COST_CAP);
        }
        if (successesByOwnerDay.getOrDefault(new OwnerDay(ownerUuid, day), 0)
                >= limits.dailySuccessfulReplyCap()) {
            return Admission.denied(Denial.DAILY_REPLY_CAP);
        }
        Instant lastSuccess = lastSuccessByOwner.get(ownerUuid);
        if (lastSuccess != null && now.isBefore(lastSuccess.plus(limits.cooldown()))) {
            return Admission.denied(Denial.COOLDOWN);
        }
        if (activePets.contains(petId)) {
            return Admission.denied(Denial.PET_BUSY);
        }
        if (activeGlobal >= limits.globalConcurrency()) {
            return Admission.denied(Denial.GLOBAL_CONCURRENCY);
        }
        activePets.add(petId);
        activeGlobal++;
        return Admission.allowed(new Permit(this, petId, ownerUuid, now));
    }

    private synchronized void success(Permit permit, BigDecimal cost, Instant completedAt) {
        release(permit);
        Objects.requireNonNull(cost, "cost");
        if (cost.signum() < 0) {
            throw new IllegalArgumentException("cost cannot be negative");
        }
        LocalDate day = completedAt.atZone(ZoneOffset.UTC).toLocalDate();
        YearMonth month = YearMonth.from(completedAt.atZone(ZoneOffset.UTC));
        lastSuccessByOwner.put(permit.ownerUuid, completedAt);
        successesByOwnerDay.merge(new OwnerDay(permit.ownerUuid, day), 1, Math::addExact);
        dailyCost.merge(day, cost, BigDecimal::add);
        monthlyCost.merge(month, cost, BigDecimal::add);
        consecutiveProviderFailures = 0;
        circuitOpenUntil = Optional.empty();
    }

    private synchronized void providerFailure(
            Permit permit, BigDecimal billedCost, Instant failedAt) {
        release(permit);
        Objects.requireNonNull(billedCost, "billedCost");
        if (billedCost.signum() < 0) {
            throw new IllegalArgumentException("billedCost cannot be negative");
        }
        LocalDate day = failedAt.atZone(ZoneOffset.UTC).toLocalDate();
        YearMonth month = YearMonth.from(failedAt.atZone(ZoneOffset.UTC));
        dailyCost.merge(day, billedCost, BigDecimal::add);
        monthlyCost.merge(month, billedCost, BigDecimal::add);
        consecutiveProviderFailures++;
        if (consecutiveProviderFailures >= limits.circuitFailureThreshold()) {
            circuitOpenUntil = Optional.of(failedAt.plus(limits.circuitOpenDuration()));
        }
    }

    private synchronized void nonProviderFailure(
            Permit permit, BigDecimal billedCost, Instant failedAt) {
        release(permit);
        Objects.requireNonNull(billedCost, "billedCost");
        if (billedCost.signum() < 0) {
            throw new IllegalArgumentException("billedCost cannot be negative");
        }
        LocalDate day = failedAt.atZone(ZoneOffset.UTC).toLocalDate();
        YearMonth month = YearMonth.from(failedAt.atZone(ZoneOffset.UTC));
        dailyCost.merge(day, billedCost, BigDecimal::add);
        monthlyCost.merge(month, billedCost, BigDecimal::add);
    }

    private void release(Permit permit) {
        if (!permit.closed || !activePets.remove(permit.petId)) {
            throw new IllegalStateException("Dialogue permit was already released");
        }
        permit.closed = false;
        activeGlobal--;
    }

    public enum Denial {
        PET_BUSY,
        COOLDOWN,
        DAILY_REPLY_CAP,
        GLOBAL_CONCURRENCY,
        GLOBAL_COST_CAP,
        CIRCUIT_OPEN
    }

    public record Admission(Optional<Permit> permit, Optional<Denial> denial) {
        public Admission {
            Objects.requireNonNull(permit, "permit");
            Objects.requireNonNull(denial, "denial");
            if (permit.isPresent() == denial.isPresent()) {
                throw new IllegalArgumentException("Admission must be exactly allowed or denied");
            }
        }

        static Admission allowed(Permit permit) {
            return new Admission(Optional.of(permit), Optional.empty());
        }

        static Admission denied(Denial denial) {
            return new Admission(Optional.empty(), Optional.of(denial));
        }
    }

    public static final class Permit {
        private final DialogueAdmissionController controller;
        private final UUID petId;
        private final UUID ownerUuid;
        private final Instant acquiredAt;
        private boolean closed = true;

        private Permit(
                DialogueAdmissionController controller,
                UUID petId,
                UUID ownerUuid,
                Instant acquiredAt) {
            this.controller = controller;
            this.petId = petId;
            this.ownerUuid = ownerUuid;
            this.acquiredAt = acquiredAt;
        }

        public void succeeded(BigDecimal cost, Instant completedAt) {
            controller.success(this, cost, completedAt);
        }

        public void providerFailed(BigDecimal billedCost, Instant failedAt) {
            controller.providerFailure(this, billedCost, failedAt);
        }

        public void providerFailed(Instant failedAt) {
            providerFailed(BigDecimal.ZERO, failedAt);
        }

        public void rejected(BigDecimal billedCost, Instant failedAt) {
            controller.nonProviderFailure(this, billedCost, failedAt);
        }

        public void rejected() {
            rejected(BigDecimal.ZERO, acquiredAt);
        }
    }

    private record OwnerDay(UUID ownerUuid, LocalDate day) {
    }
}
