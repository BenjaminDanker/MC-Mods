package com.silver.aipets.fabric.interaction;

import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.permission.PetPermission;
import com.silver.aipets.fabric.permission.PetPermissions;
import com.silver.aipets.fabric.placement.PetPickupOutcome;
import com.silver.aipets.fabric.placement.PetPickupStatus;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

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

    public static InteractionResult interact(Player player, Entity entity) {
        return interact(player, entity, InteractionHand.MAIN_HAND);
    }

    public static InteractionResult interact(Player player, Entity entity, InteractionHand hand) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(hand, "hand");
        if (!(entity instanceof TamableAnimal tameable)
                || !(entity instanceof PetEntityData data)
                || !data.aipets$isPet()) {
            return InteractionResult.PASS;
        }
        if (player.isSpectator() || !data.aipets$getOwnerUuid().equals(player.getUUID())) {
            return InteractionResult.FAIL;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }
        if (!PetPermissions.check(serverPlayer.createCommandSourceStack(), PetPermission.CHAT)) {
            return InteractionResult.FAIL;
        }
        long serverTick = serverPlayer.level().getServer().getTickCount();
        InteractionKey interactionKey = new InteractionKey(
                serverPlayer.getUUID(), entity.getUUID());
        Long previousTick = SERVER_INTERACTIONS.put(interactionKey, serverTick);
        SERVER_INTERACTIONS.entrySet().removeIf(entry -> entry.getValue() < serverTick - 2);
        if (previousTick != null && serverTick - previousTick <= 1) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (serverPlayer.isShiftKeyDown()) {
            return pickup(serverPlayer);
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
            return InteractionResult.SUCCESS_SERVER;
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
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult pickup(ServerPlayer player) {
        if (!PetPermissions.check(player.createCommandSourceStack(), PetPermission.USE)) {
            return InteractionResult.FAIL;
        }
        var configured = PetCompanionMod.pickupCoordinator();
        if (configured.isEmpty()) {
            player.sendSystemMessage(Component.literal("Pet pickup is temporarily unavailable."), false);
            return InteractionResult.SUCCESS_SERVER;
        }
        player.sendSystemMessage(Component.literal("Picking up your pet…"), false);
        try {
            configured.orElseThrow().pickup(player).whenComplete((outcome, failure) -> {
                player.level().getServer().execute(() ->
                        completePickup(player, outcome, failure));
            });
        } catch (RuntimeException failure) {
            player.sendSystemMessage(Component.literal("Pet pickup is temporarily unavailable."), false);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void completePickup(
            ServerPlayer player,
            PetPickupOutcome outcome,
            Throwable failure) {
        if (failure != null || outcome == null) {
            player.sendSystemMessage(Component.literal("Pet pickup is temporarily unavailable."), false);
            return;
        }
        if (outcome.status() == PetPickupStatus.PICKED_UP) {
            player.sendSystemMessage(Component.literal("Your pet is now held."), false);
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
        player.sendSystemMessage(Component.literal(message), false);
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
