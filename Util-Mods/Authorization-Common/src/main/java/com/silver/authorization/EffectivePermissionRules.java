package com.silver.authorization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Expands data-driven role memberships and direct rules into candidates for one destination. */
public final class EffectivePermissionRules {
    private static final Comparator<PermissionRule> ORDER =
            Comparator.comparing((PermissionRule rule) -> rule.scope().scopeKey())
                    .thenComparing(rule -> rule.pattern().value())
                    .thenComparing(rule -> rule.origin().name())
                    .thenComparing(rule -> rule.effect().name());

    private EffectivePermissionRules() {
    }

    /**
     * A server assignment caps even a role's GLOBAL permission rules to that server. A global
     * assignment may contribute GLOBAL rules and rules scoped to the requested server. Rules
     * scoped to another server never enter this candidate set.
     */
    public static List<PermissionRule> forServer(
            Collection<RoleAssignment> assignments,
            Collection<RolePermission> rolePermissions,
            Collection<DirectPermissionRule> directPermissions,
            ServerId server) {
        Objects.requireNonNull(assignments, "assignments");
        Objects.requireNonNull(rolePermissions, "rolePermissions");
        Objects.requireNonNull(directPermissions, "directPermissions");
        Objects.requireNonNull(server, "server");

        List<PermissionRule> candidates = new ArrayList<>();
        for (RoleAssignment assignment : assignments) {
            Objects.requireNonNull(assignment, "assignment");
            for (RolePermission permission : rolePermissions) {
                Objects.requireNonNull(permission, "rolePermission");
                if (!assignment.roleId().equals(permission.roleId())) continue;
                effectiveScope(assignment.scope(), permission.scope(), server).ifPresent(scope ->
                        candidates.add(PermissionRule.role(permission.pattern(), scope,
                                permission.effect(), assignment.roleId())));
            }
        }
        for (DirectPermissionRule permission : directPermissions) {
            Objects.requireNonNull(permission, "directPermission");
            if (permission.scope().appliesTo(server)) {
                candidates.add(PermissionRule.direct(permission.pattern(), permission.scope(), permission.effect()));
            }
        }
        return candidates.stream().distinct().sorted(ORDER).toList();
    }

    private static Optional<AuthorizationScope> effectiveScope(
            AuthorizationScope assignment, AuthorizationScope permission, ServerId server) {
        if (assignment.type() == AuthorizationScope.Type.GLOBAL) {
            return permission.appliesTo(server) ? Optional.of(permission) : Optional.empty();
        }
        if (!assignment.serverId().equals(server)) return Optional.empty();
        if (permission.type() == AuthorizationScope.Type.GLOBAL
                || permission.serverId().equals(server)) {
            return Optional.of(AuthorizationScope.server(server));
        }
        return Optional.empty();
    }
}
