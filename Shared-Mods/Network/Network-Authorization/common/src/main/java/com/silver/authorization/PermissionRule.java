package com.silver.authorization;

import java.util.Objects;
import java.util.Optional;

/** An applicable rule candidate. Role identity and numeric management priority are not needed to evaluate it. */
public record PermissionRule(
        PermissionPattern pattern,
        AuthorizationScope scope,
        RuleOrigin origin,
        PermissionEffect effect,
        Optional<RoleId> sourceRole) {
    public PermissionRule {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(effect, "effect");
        sourceRole = Objects.requireNonNull(sourceRole, "sourceRole");
        if (origin == RuleOrigin.DIRECT_USER && sourceRole.isPresent()) {
            throw new IllegalArgumentException("Direct-user rules cannot identify a source role");
        }
    }

    public PermissionRule(PermissionPattern pattern, AuthorizationScope scope,
                          RuleOrigin origin, PermissionEffect effect) {
        this(pattern, scope, origin, effect, Optional.empty());
    }

    public static PermissionRule role(PermissionPattern pattern, AuthorizationScope scope,
                                      PermissionEffect effect, RoleId roleId) {
        return new PermissionRule(pattern, scope, RuleOrigin.ROLE, effect, Optional.of(roleId));
    }

    public static PermissionRule direct(PermissionPattern pattern, AuthorizationScope scope,
                                        PermissionEffect effect) {
        return new PermissionRule(pattern, scope, RuleOrigin.DIRECT_USER, effect, Optional.empty());
    }
}
