package com.silver.aipets.fabric.interaction;

import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.permission.PetPermission;
import com.silver.aipets.fabric.permission.PetPermissions;
import com.silver.aipets.fabric.placement.PetPickupOutcome;
import com.silver.aipets.fabric.placement.PetPickupStatus;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Marker- and owner-scoped routing boundary for the later text-entry UI. */
public final class PetInteractionRouter {
    private static final AtomicReference<PetInteractionHandler> HANDLER = new AtomicReference<>();
    /**
     * UseEntityCallback and the cat/wolf interactMob mixin can observe one physical
     * click in the same server tick. Keep the interaction side effect single-shot.
     */
    private static final ConcurrentHashMap<InteractionKey, Long> SERVER_INTERACTIONS =
            new ConcurrentHashMap<>();

    private PetInteractionRouter() {
    }

    public static ActionResult interact(PlayerEntity player, Entity entity) {
        return interact(player, entity, Hand.MAIN_HAND);
    }

    public static ActionResult interact(PlayerEntity player, Entity entity, Hand hand) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(hand, "hand");
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
        long serverTick = serverPlayer.getEntityWorld().getServer().getTicks();
        InteractionKey interactionKey = new InteractionKey(
                serverPlayer.getUuid(), entity.getUuid());
        Long previousTick = SERVER_INTERACTIONS.put(interactionKey, serverTick);
        SERVER_INTERACTIONS.entrySet().removeIf(entry -> entry.getValue() < serverTick - 2);
        if (previousTick != null && serverTick - previousTick <= 1) {
            return ActionResult.SUCCESS_SERVER;
        }
        if (serverPlayer.isSneaking()) {
            return pickup(serverPlayer);
        }
        if (!PetPermissions.check(serverPlayer.getCommandSource(), PetPermission.CHAT)) {
            return ActionResult.FAIL;
        }
        if (data.aipets$isSleeping()) {
            try {
                PetCompanionMod.speechDisplayManager().show(tameable, "Zzzz...");
            } catch (RuntimeException failure) {
                PetCompanionMod.LOGGER.warn(StructuredPetEvent
                        .operation("sleeping_pet_display")
                        .pet(data.aipets$getPetId())
                        .owner(data.aipets$getOwnerUuid())
                        .failure(failure)
                        .outcome("failed")
                        .toJson());
            }
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

    private static ActionResult pickup(ServerPlayerEntity player) {
        if (!PetPermissions.check(player.getCommandSource(), PetPermission.USE)) {
            return ActionResult.FAIL;
        }
        var configured = PetCompanionMod.pickupCoordinator();
        if (configured.isEmpty()) {
            player.sendMessage(Text.literal("Pet pickup is temporarily unavailable."), false);
            return ActionResult.SUCCESS_SERVER;
        }
        player.sendMessage(Text.literal("Picking up your pet…"), false);
        try {
            configured.orElseThrow().pickup(player).whenComplete((outcome, failure) -> {
                player.getEntityWorld().getServer().execute(() ->
                        completePickup(player, outcome, failure));
            });
        } catch (RuntimeException failure) {
            player.sendMessage(Text.literal("Pet pickup is temporarily unavailable."), false);
        }
        return ActionResult.SUCCESS_SERVER;
    }

    private static void completePickup(
            ServerPlayerEntity player,
            PetPickupOutcome outcome,
            Throwable failure) {
        if (failure != null || outcome == null) {
            player.sendMessage(Text.literal("Pet pickup is temporarily unavailable."), false);
            return;
        }
        if (outcome.status() == PetPickupStatus.PICKED_UP) {
            player.sendMessage(Text.literal("Your pet is now held."), false);
            return;
        }
        String message = switch (outcome.status()) {
            case NO_PET -> "You have not adopted a pet yet.";
            case NOT_PLACED_HERE -> "Your pet is somewhere else right now.";
            case ENTITY_MISSING_OR_STALE -> "Your pet could not be found here; try /pet status.";
            case OUT_OF_RANGE -> "Move within 4 blocks of your pet to pick it up.";
            case AUTHORITY_REJECTED -> "Your pet's location changed; check /pet status and try again.";
            case SERVICE_FAILURE -> "Pet pickup is temporarily unavailable.";
            case PICKED_UP -> throw new IllegalStateException("Handled above");
        };
        player.sendMessage(Text.literal(message), false);
    }

    public static Runnable installHandler(PetInteractionHandler handler) {
        PetInteractionHandler installed = Objects.requireNonNull(handler, "handler");
        HANDLER.set(installed);
        return () -> HANDLER.compareAndSet(installed, null);
    }

    public static void uninstallHandler(PetInteractionHandler handler) {
        HANDLER.compareAndSet(Objects.requireNonNull(handler, "handler"), null);
    }

    private record InteractionKey(UUID playerUuid, UUID entityUuid) {
    }
}
