package com.silver.authorization;

import java.util.Objects;

/** A data-defined permission rule attached to a data-defined role. */
public record RolePermission(
        RoleId roleId,
        PermissionPattern pattern,
        AuthorizationScope scope,
        PermissionEffect effect) {
    public RolePermission {
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(effect, "effect");
    }
}
