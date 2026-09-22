package com.silver.authorization;

import java.util.Objects;

/** A direct user ALLOW or DENY rule. */
public record DirectPermissionRule(
        PermissionPattern pattern,
        AuthorizationScope scope,
        PermissionEffect effect) {
    public DirectPermissionRule {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(effect, "effect");
    }
}
