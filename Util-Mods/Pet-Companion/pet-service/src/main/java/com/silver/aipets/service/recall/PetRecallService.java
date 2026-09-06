package com.silver.aipets.service.recall;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.service.metrics.PetOperationalMetrics;
import com.silver.aipets.service.persistence.PetRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Service-time UTC monthly recall policy over an atomic persistence boundary. */
public final class PetRecallService {
    private final PetRepository repository;
    private final Clock clock;
    private final PetOperationalMetrics metrics;

    public PetRecallService(PetRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public PetRecallService(PetRepository repository, Clock clock) {
        this(repository, clock, new PetOperationalMetrics());
    }

    public PetRecallService(
            PetRepository repository, Clock clock, PetOperationalMetrics metrics) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public RecallOperationResult recall(
        UUID operationId, UUID petId, PetTransitions.Recall untrustedCommand) {
        Objects.requireNonNull(untrustedCommand, "untrustedCommand");
        metrics.increment(PetOperationalMetrics.Counter.RECALL_ATTEMPTS);
        Instant now = clock.instant();
        YearMonth period = YearMonth.from(now.atZone(ZoneOffset.UTC));
        Instant next = period.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        PetTransitions.Recall command = new PetTransitions.Recall(
                untrustedCommand.ownerUuid(),
                untrustedCommand.expectedVersion(),
                untrustedCommand.destinationBackendId(),
                untrustedCommand.destinationDimensionId(),
                untrustedCommand.position(),
                untrustedCommand.entityUuid(),
                now);
        try {
            RecallOperationResult result = repository.recall(new RecallPersistenceRequest(
                    operationId,
                    petId,
                    fingerprint(petId, untrustedCommand),
                    period.toString(),
                    next,
                    command));
            if (result.disposition() == RecallOperationDisposition.KEY_CONFLICT) {
                metrics.increment(PetOperationalMetrics.Counter.RECALL_FAILURES);
            } else {
                metrics.increment(PetOperationalMetrics.Counter.RECALL_SUCCESSES);
            }
            return result;
        } catch (RuntimeException failure) {
            metrics.increment(PetOperationalMetrics.Counter.RECALL_FAILURES);
            throw failure;
        }
    }

    public RecallOperationResult compensateFailure(
            UUID recallOperationId,
            UUID petId,
            PetTransitions.CompensateRecallFailure untrustedCommand) {
        Objects.requireNonNull(untrustedCommand, "untrustedCommand");
        PetTransitions.CompensateRecallFailure command =
                new PetTransitions.CompensateRecallFailure(
                        untrustedCommand.expectedVersion(),
                        untrustedCommand.destinationBackendId(),
                        untrustedCommand.entityUuid(),
                        clock.instant());
        return repository.compensateRecallFailure(new RecallCompensationRequest(
                recallOperationId, petId, compensationFingerprint(petId, untrustedCommand), command));
    }

    private static String fingerprint(UUID petId, PetTransitions.Recall command) {
        return digest(
                petId.toString(), command.ownerUuid().toString(),
                Long.toString(command.expectedVersion()), command.destinationBackendId().value(),
                command.destinationDimensionId().toString(),
                Double.toHexString(command.position().x()),
                Double.toHexString(command.position().y()),
                Double.toHexString(command.position().z()), command.entityUuid().toString());
    }

    private static String compensationFingerprint(
            UUID petId, PetTransitions.CompensateRecallFailure command) {
        return digest(
                petId.toString(), Long.toString(command.expectedVersion()),
                command.destinationBackendId().value(), command.entityUuid().toString());
    }

    private static String digest(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
