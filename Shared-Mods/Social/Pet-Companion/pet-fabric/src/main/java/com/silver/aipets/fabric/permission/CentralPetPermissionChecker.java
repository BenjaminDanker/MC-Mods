package com.silver.aipets.fabric.permission;

import com.silver.authorization.Authorization;
import com.silver.authorization.PermissionNode;
import com.silver.authorization.ServerId;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;

/** Pet Companion adapter to the shared local evaluator; it deliberately has no vanilla-level fallback. */
public final class CentralPetPermissionChecker implements PetPermissions.PermissionChecker {
    private final Authorization authorization;
    private final ServerId serverId;

    public CentralPetPermissionChecker(Authorization authorization, ServerId serverId) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, PermissionNode permission) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(permission, "permission");
        var player = source.getPlayer();
        return player != null && hasPermission(player.getUUID(), permission);
    }

    boolean hasPermission(UUID playerId, PermissionNode permission) {
        return playerId != null && permission != null
                && authorization.has(playerId, permission, serverId);
    }
}
