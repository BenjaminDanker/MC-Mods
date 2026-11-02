package com.silver.authorization;

import java.util.Objects;

/** A role membership scoped to GLOBAL or one server; expiry is filtered by the persistence layer. */
public record RoleAssignment(RoleId roleId, AuthorizationScope scope) {
    public RoleAssignment {
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(scope, "scope");
    }
}
