package com.silver.authorization;

import java.util.Objects;
import java.util.UUID;

/** Synchronous, in-memory authorization API. Implementations must not perform SQL in these methods. */
public interface Authorization {
    AuthorizationDecision decide(AuthorizationSubject subject, PermissionNode permission, ServerId server);

    default boolean has(AuthorizationSubject subject, PermissionNode permission, ServerId server) {
        return decide(subject, permission, server).allowed();
    }

    default boolean has(UUID player, PermissionNode permission, ServerId server) {
        return has(AuthorizationSubject.player(Objects.requireNonNull(player, "player")), permission, server);
    }

    /** Role identity is used only for explicit platform policy such as OWNER override, never ranking. */
    default boolean hasRole(AuthorizationSubject subject, RoleId role, ServerId server) {
        return false;
    }
}
