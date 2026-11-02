package com.silver.aipets.fabric.permission;

import com.silver.authorization.PermissionNode;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.commands.CommandSourceStack;

/**
 * Named permission boundary. Missing central integration fails closed; there is no vanilla OP fallback.
 */
public final class PetPermissions {
    private static final AtomicReference<PermissionChecker> CHECKER =
            new AtomicReference<>((source, node) -> false);

    private PetPermissions() {
    }

    public static boolean check(CommandSourceStack source, PetPermission permission) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(permission, "permission");
        return CHECKER.get().hasPermission(source, permission.node());
    }

    /** Installs a provider adapter and returns an idempotent restoration handle. */
    public static Runnable install(PermissionChecker checker) {
        PermissionChecker installed = Objects.requireNonNull(checker, "checker");
        PermissionChecker previous = CHECKER.getAndSet(installed);
        return () -> CHECKER.compareAndSet(installed, previous);
    }

    @FunctionalInterface
    public interface PermissionChecker {
        boolean hasPermission(CommandSourceStack source, PermissionNode permission);
    }
}
