package com.silver.aipets.fabric.permission;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Named permission boundary with vanilla permission levels as a no-dependency fallback. A server
 * permission-provider adapter may install one checker during startup without changing commands.
 */
public final class PetPermissions {
    private static final PermissionChecker VANILLA =
            (source, node, defaultRequiredLevel) -> source.permissions() instanceof LevelBasedPermissionSet levels
                    && levels.level().isEqualOrHigherThan(PermissionLevel.byId(defaultRequiredLevel));
    private static final AtomicReference<PermissionChecker> CHECKER =
            new AtomicReference<>(VANILLA);

    private PetPermissions() {
    }

    public static boolean check(CommandSourceStack source, PetPermission permission) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(permission, "permission");
        return CHECKER.get().hasPermission(
                source, permission.node(), permission.defaultRequiredLevel());
    }

    /** Installs a provider adapter and returns an idempotent restoration handle. */
    public static Runnable install(PermissionChecker checker) {
        PermissionChecker installed = Objects.requireNonNull(checker, "checker");
        PermissionChecker previous = CHECKER.getAndSet(installed);
        return () -> CHECKER.compareAndSet(installed, previous);
    }

    @FunctionalInterface
    public interface PermissionChecker {
        boolean hasPermission(
                CommandSourceStack source,
                String permissionNode,
                int defaultRequiredLevel);
    }
}
