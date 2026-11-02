package com.silver.authorization;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Candidate rules for one authenticated subject on one destination server. */
public record AuthorizationSnapshot(
        int protocolVersion,
        AuthorizationSubject subject,
        ServerId serverId,
        long revision,
        UUID backendEpoch,
        UUID nonce,
        Instant issuedAt,
        Instant expiresAt,
        List<PermissionRule> rules) {
    public static final int CURRENT_PROTOCOL_VERSION = 2;
    public static final int MAX_RULES = 1024;

    private static final Comparator<PermissionRule> CANONICAL_RULE_ORDER =
            Comparator.comparing((PermissionRule rule) -> rule.scope().type().name())
                    .thenComparing(rule -> rule.scope().serverId() == null ? "" : rule.scope().serverId().value())
                    .thenComparing(rule -> rule.pattern().value())
                    .thenComparing(rule -> rule.origin().name())
                    .thenComparing(rule -> rule.sourceRole().map(RoleId::value).orElse(""))
                    .thenComparing(rule -> rule.effect().name());

    public AuthorizationSnapshot {
        if (protocolVersion != CURRENT_PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported authorization snapshot protocol: " + protocolVersion);
        }
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(serverId, "serverId");
        if (revision < 0) throw new IllegalArgumentException("revision must be non-negative");
        Objects.requireNonNull(backendEpoch, "backendEpoch");
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("expiresAt must follow issuedAt");
        Objects.requireNonNull(rules, "rules");
        if (rules.size() > MAX_RULES) throw new IllegalArgumentException("snapshot has too many rules");
        rules = rules.stream().map(rule -> Objects.requireNonNull(rule, "rule"))
                .distinct().sorted(CANONICAL_RULE_ORDER).toList();
        if (rules.stream().anyMatch(rule -> !rule.scope().appliesTo(serverId))) {
            throw new IllegalArgumentException("Snapshot contains a rule for a different server");
        }
    }
}
