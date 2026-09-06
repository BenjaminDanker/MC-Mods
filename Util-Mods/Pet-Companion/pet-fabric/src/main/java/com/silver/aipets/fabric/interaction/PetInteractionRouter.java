package com.silver.aipets.fabric.interaction;

import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.permission.PetPermission;
import com.silver.aipets.fabric.permission.PetPermissions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Marker- and owner-scoped routing boundary for the later text-entry UI. */
public final class PetInteractionRouter {
    private static final AtomicReference<PetInteractionHandler> HANDLER = new AtomicReference<>();

    private PetInteractionRouter() {
    }

    public static ActionResult interact(PlayerEntity player, Entity entity) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(entity, "entity");
        if (!(entity instanceof TameableEntity tameable)
                || !(entity instanceof PetEntityData data)
                || !data.aipets$isPet()) {
            return ActionResult.PASS;
        }
        if (player.isSpectator() || !data.aipets$getOwnerUuid().equals(player.getUuid())) {
            return ActionResult.FAIL;
        }
        if (player.getEntityWorld().isClient()) {
            return ActionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.FAIL;
        }
        if (!PetPermissions.check(serverPlayer.getCommandSource(), PetPermission.CHAT)) {
            return ActionResult.FAIL;
        }
        if (data.aipets$isSleeping()) {
            serverPlayer.sendMessage(
                    Text.literal("Your pet is sleeping and cannot chat yet."), false);
            return ActionResult.SUCCESS_SERVER;
        }

        PetInteractionHandler handler = HANDLER.get();
        if (handler != null) {
            try {
                handler.open(serverPlayer, tameable);
            } catch (RuntimeException failure) {
                PetCompanionMod.LOGGER.warn(StructuredPetEvent
                        .operation("interaction_open")
                        .pet(data.aipets$getPetId())
                        .owner(data.aipets$getOwnerUuid())
                        .failure(failure)
                        .outcome("failed")
                        .toJson());
                return ActionResult.FAIL;
            }
        }
        return ActionResult.SUCCESS_SERVER;
    }

    public static Runnable installHandler(PetInteractionHandler handler) {
        PetInteractionHandler installed = Objects.requireNonNull(handler, "handler");
        HANDLER.set(installed);
        return () -> HANDLER.compareAndSet(installed, null);
    }

    public static void uninstallHandler(PetInteractionHandler handler) {
        HANDLER.compareAndSet(Objects.requireNonNull(handler, "handler"), null);
    }
}
