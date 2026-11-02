package com.silver.authorization;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A local, explainable authorization result. It contains no transport credentials. */
public record AuthorizationDecision(
        PermissionNode requestedPermission,
        AuthorizationScope requestedScope,
        long authorizationRevision,
        Outcome outcome,
        Optional<PermissionRule> matchedRule,
        List<RuleExplanation> consideredRules) {
    public AuthorizationDecision {
        Objects.requireNonNull(requestedPermission, "requestedPermission");
        Objects.requireNonNull(requestedScope, "requestedScope");
        if (authorizationRevision < 0) throw new IllegalArgumentException("authorizationRevision must be non-negative");
        Objects.requireNonNull(outcome, "outcome");
        matchedRule = Objects.requireNonNull(matchedRule, "matchedRule");
        if ((outcome == Outcome.DEFAULT_DENY) != matchedRule.isEmpty()) {
            throw new IllegalArgumentException("Only default-deny decisions have no matched rule");
        }
        consideredRules = List.copyOf(Objects.requireNonNull(consideredRules, "consideredRules"));
    }

    public boolean allowed() {
        return outcome == Outcome.ALLOW;
    }

    public Optional<PermissionPattern> winningPattern() { return matchedRule.map(rule -> rule.pattern()); }
    public Optional<PermissionEffect> winningEffect() { return matchedRule.map(rule -> rule.effect()); }
    public Optional<RuleOrigin> sourceType() { return matchedRule.map(rule -> rule.origin()); }
    public Optional<RoleId> sourceRole() { return matchedRule.flatMap(rule -> rule.sourceRole()); }
    public Optional<AuthorizationScope> ruleScope() { return matchedRule.map(rule -> rule.scope()); }

    public static AuthorizationDecision of(PermissionNode permission, ServerId server, long revision,
                                           Optional<PermissionRule> winner,
                                           List<RuleExplanation> considered) {
        Objects.requireNonNull(server, "server");
        Outcome outcome = winner.map(rule -> rule.effect() == PermissionEffect.ALLOW
                ? Outcome.ALLOW : Outcome.DENY).orElse(Outcome.DEFAULT_DENY);
        return new AuthorizationDecision(permission, AuthorizationScope.server(server), revision,
                outcome, winner, considered);
    }

    public static AuthorizationDecision defaultDeny(PermissionNode permission, ServerId server, long revision) {
        return of(permission, server, revision, Optional.empty(), List.of());
    }

    public record RuleExplanation(PermissionRule rule, boolean winner, LossReason reason) {
        public RuleExplanation {
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(reason, "reason");
            if (winner != (reason == LossReason.WINNER)) {
                throw new IllegalArgumentException("Winner marker and explanation reason disagree");
            }
        }
    }

    public enum LossReason {
        WINNER,
        LOWER_SCOPE,
        LESS_SPECIFIC_PATTERN,
        WILDCARD_BEHIND_EXACT,
        ROLE_BEHIND_DIRECT_RULE,
        ALLOW_LOST_TO_DENY,
        DETERMINISTIC_ROLE_TIEBREAK
    }

    public enum Outcome {
        ALLOW,
        DENY,
        DEFAULT_DENY
    }
}
