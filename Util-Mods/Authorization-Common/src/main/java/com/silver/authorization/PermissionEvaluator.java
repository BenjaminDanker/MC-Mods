package com.silver.authorization;

import java.util.Comparator;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The sole deterministic rule algorithm shared by proxy and backend code. */
public final class PermissionEvaluator {
    private static final Comparator<PermissionRule> PRECEDENCE =
            Comparator.comparingInt((PermissionRule rule) -> rule.scope().type() == AuthorizationScope.Type.SERVER ? 1 : 0)
                    .thenComparingInt(rule -> rule.pattern().specificityDepth())
                    .thenComparingInt(rule -> rule.pattern().isExact() ? 1 : 0)
                    .thenComparingInt(rule -> rule.origin() == RuleOrigin.DIRECT_USER ? 1 : 0)
                    .thenComparingInt(rule -> rule.effect() == PermissionEffect.DENY ? 1 : 0)
                    .thenComparing(rule -> rule.pattern().value())
                    .thenComparing(rule -> rule.sourceRole().map(RoleId::value).orElse(""), Comparator.reverseOrder());

    private PermissionEvaluator() {
    }

    public static AuthorizationDecision evaluate(
            Collection<PermissionRule> rules,
            PermissionNode permission,
            ServerId server) {
        return evaluate(rules, permission, server, 0);
    }

    public static AuthorizationDecision evaluate(
            Collection<PermissionRule> rules,
            PermissionNode permission,
            ServerId server,
            long revision) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(server, "server");

        List<PermissionRule> matching = rules.stream()
                .filter(rule -> rule.scope().appliesTo(server) && rule.pattern().matches(permission))
                .distinct().sorted(PRECEDENCE.reversed()).toList();
        if (matching.isEmpty()) return AuthorizationDecision.defaultDeny(permission, server, revision);
        PermissionRule winner = matching.get(0);
        List<AuthorizationDecision.RuleExplanation> trace = new ArrayList<>(matching.size());
        for (PermissionRule rule : matching) {
            boolean isWinner = rule.equals(winner);
            trace.add(new AuthorizationDecision.RuleExplanation(rule, isWinner,
                    isWinner ? AuthorizationDecision.LossReason.WINNER : explainLoss(rule, winner)));
        }
        return AuthorizationDecision.of(permission, server, revision, Optional.of(winner), trace);
    }

    /** Allocation-light check path; it uses the same comparator and winner selection as explain(). */
    public static boolean has(Collection<PermissionRule> rules, PermissionNode permission, ServerId server) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(server, "server");
        PermissionRule winner = winningRule(rules, permission, server);
        return winner != null && winner.effect() == PermissionEffect.ALLOW;
    }

    private static PermissionRule winningRule(Collection<PermissionRule> rules,
                                               PermissionNode permission, ServerId server) {
        PermissionRule winner = null;
        for (PermissionRule rule : rules) {
            if (!rule.scope().appliesTo(server) || !rule.pattern().matches(permission)) continue;
            if (winner == null || PRECEDENCE.compare(rule, winner) > 0) winner = rule;
        }
        return winner;
    }

    private static AuthorizationDecision.LossReason explainLoss(PermissionRule losing, PermissionRule winner) {
        if (losing.scope().type() != winner.scope().type()) return AuthorizationDecision.LossReason.LOWER_SCOPE;
        if (losing.pattern().specificityDepth() != winner.pattern().specificityDepth()) {
            return AuthorizationDecision.LossReason.LESS_SPECIFIC_PATTERN;
        }
        if (losing.pattern().isExact() != winner.pattern().isExact()) {
            return AuthorizationDecision.LossReason.WILDCARD_BEHIND_EXACT;
        }
        if (losing.origin() != winner.origin()) return AuthorizationDecision.LossReason.ROLE_BEHIND_DIRECT_RULE;
        if (losing.effect() != winner.effect()) return AuthorizationDecision.LossReason.ALLOW_LOST_TO_DENY;
        return AuthorizationDecision.LossReason.DETERMINISTIC_ROLE_TIEBREAK;
    }
}
