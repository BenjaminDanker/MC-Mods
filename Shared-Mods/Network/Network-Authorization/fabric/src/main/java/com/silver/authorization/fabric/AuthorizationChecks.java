package com.silver.authorization.fabric;

import com.silver.authorization.AuthorizationDecision;
import com.silver.authorization.AuthorizationSubject;
import com.silver.authorization.PermissionNode;
import com.silver.authorization.PermissionPattern;
import com.silver.authorization.RoleId;
import com.silver.authorization.ServerId;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.level.ServerPlayer;

/** Shared Fabric adapter for local authorization checks. Non-player sources fail closed. */
public final class AuthorizationChecks {
    private AuthorizationChecks() { }

    public static boolean has(ServerPlayer player, PermissionNode permission) {
        if (player == null || permission == null) return false;
        return NetworkAuthorizationMod.authorization().has(
                AuthorizationSubject.player(player.getUUID()), permission, NetworkAuthorizationMod.serverId());
    }

    /** Namespace policy gates are checked through a deterministic child capability in the same evaluator. */
    public static boolean has(ServerPlayer player, PermissionPattern permission) {
        return player != null && permission != null && has(player, permission.policyGateNode());
    }

    public static boolean has(ServerPlayer player, String permission) {
        try {
            return has(player, PermissionNode.of(permission));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /** OWNER is a management role marker in the signed snapshot, not a vanilla operator level. */
    public static boolean isOwner(ServerPlayer player) {
        return player != null && NetworkAuthorizationMod.authorization().hasRole(
                AuthorizationSubject.player(player.getUUID()), RoleId.of("OWNER"), NetworkAuthorizationMod.serverId());
    }

    /** Only the dedicated server console is trusted; command blocks, functions, and RCON fail closed. */
    public static boolean has(CommandSourceStack source, PermissionNode permission) {
        if (source == null) return false;
        if (isDedicatedConsole(source)) return true;
        ServerPlayer player = source.getPlayer();
        return has(player, permission);
    }

    public static boolean has(CommandSourceStack source, String permission) {
        try {
            return has(source, PermissionNode.of(permission));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static Predicate<CommandSourceStack> requires(PermissionNode permission) {
        return source -> has(source, permission);
    }

    /** The vanilla dedicated-server console is trusted; RCON and command sources are not. */
    public static boolean isDedicatedConsole(CommandSourceStack source) {
        return source != null
                && source.getServer() != null
                && source.getEntity() == null
                && "Server".equals(source.getTextName())
                && source.permissions() == LevelBasedPermissionSet.OWNER;
    }

    public static Optional<AuthorizationDecision> explain(ServerPlayer player, PermissionNode permission) {
        if (player == null || permission == null) return Optional.empty();
        return Optional.of(NetworkAuthorizationMod.authorization().decide(
                AuthorizationSubject.player(player.getUUID()), permission, NetworkAuthorizationMod.serverId()));
    }

    public static ServerId serverId() {
        return NetworkAuthorizationMod.serverId();
    }
}
