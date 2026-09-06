package com.silver.aipets.fabric.gametest;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.HeldPlacement;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetAppearance;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.ResourceId;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.common.transport.PetAdoptionWireRequest;
import com.silver.aipets.common.transport.PetAdoptionWireResult;
import com.silver.aipets.common.transport.PetRecallWireResult;
import com.silver.aipets.fabric.authority.AuthorityMutationResult;
import com.silver.aipets.fabric.authority.PetAuthorityGateway;
import com.silver.aipets.fabric.authority.PetAuthoritySnapshot;
import com.silver.aipets.fabric.conversation.PetConversationCoordinator;
import com.silver.aipets.fabric.conversation.PetConversationSessionRegistry;
import com.silver.aipets.fabric.conversation.PetDialogueRequest;
import com.silver.aipets.fabric.conversation.PetDialogueResponse;
import com.silver.aipets.fabric.conversation.PrivateChatPetTextInputUi;
import com.silver.aipets.fabric.entity.PetEntityFactory;
import com.silver.aipets.fabric.speech.PetSpeechDisplayManager;
import com.silver.aipets.fabric.speech.PetSpeechPolicy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class PetConversationGameTests {
    @GameTest(maxTicks = 150)
    @SuppressWarnings("removal")
    public void privateInputAsyncReplyAndSpeechLifecycle(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        BackendId backend = new BackendId("survival");
        UUID petId = UUID.fromString("10000000-0000-0000-0000-0000000000f2");
        UUID entityId = UUID.fromString("30000000-0000-0000-0000-0000000000f2");
        Instant now = Instant.parse("2026-08-31T12:00:00Z");
        Vec3d near = context.getAbsolute(new Vec3d(1.5, 1.0, 1.5));
        owner.refreshPositionAndAngles(near.x + 1, near.y, near.z, 0, 0);

        Pet held = new Pet(
                petId,
                owner.getUuid(),
                "Mochi",
                PetAppearance.create(
                        PetSpecies.CAT, ResourceId.parse("minecraft:tabby"), 0.67,
                        OptionalLong.of(0xF2L), AppearanceRules.defaults()),
                PetTraits.initial(50, 50, 50, 50, 50, now),
                PetMood.initial(50, 20, 10, 10, now),
                HeldPlacement.INSTANCE,
                0,
                now,
                now);
        String dimension = world.getRegistryKey().getValue().toString();
        Pet placed = new Pet(
                held.petId(), held.ownerUuid(), held.name(), held.appearance(), held.traits(), held.mood(),
                PlacedPlacement.materialized(
                        backend, DimensionId.parse(dimension), new WorldPosition(near.x, near.y, near.z), entityId),
                held.recordVersion(), held.createdAt(), held.updatedAt());
        TameableEntity pet = new PetEntityFactory().prepare(
                world, placed, entityId, new WorldPosition(near.x, near.y, near.z), false).entity();
        pet.refreshPositionAndAngles(near, 30.0F, 0.0F);
        context.assertTrue(world.spawnEntity(pet), Text.literal("Conversation pet did not spawn"));

        MutableClock clock = new MutableClock(now);
        PrivateChatPetTextInputUi input = new PrivateChatPetTextInputUi(clock);
        PetSpeechDisplayManager displays = new PetSpeechDisplayManager(PetSpeechPolicy.defaults());
        StubAuthority authority = new StubAuthority(new PetAuthoritySnapshot(placed, false, false));
        AtomicInteger dialogueCalls = new AtomicInteger();
        AtomicReference<PetDialogueRequest> capturedRequest = new AtomicReference<>();
        CompletableFuture<PetDialogueResponse> pendingReply = new CompletableFuture<>();
        Queue<UUID> requestIds = new ArrayDeque<>();
        requestIds.add(UUID.fromString("40000000-0000-0000-0000-0000000000f2"));
        requestIds.add(UUID.fromString("50000000-0000-0000-0000-0000000000f2"));
        requestIds.add(UUID.fromString("40000000-0000-0000-0000-0000000000f3"));
        requestIds.add(UUID.fromString("50000000-0000-0000-0000-0000000000f3"));
        requestIds.add(UUID.fromString("50000000-0000-0000-0000-0000000000f4"));
        Queue<UUID> sessionIds = new ArrayDeque<>();
        sessionIds.add(UUID.fromString("60000000-0000-0000-0000-0000000000f2"));
        sessionIds.add(UUID.fromString("60000000-0000-0000-0000-0000000000f3"));
        PetConversationCoordinator coordinator = new PetConversationCoordinator(
                backend,
                authority,
                request -> {
                    dialogueCalls.incrementAndGet();
                    capturedRequest.set(request);
                    return pendingReply;
                },
                input,
                new PetConversationSessionRegistry(
                        Duration.ofSeconds(30), Duration.ofSeconds(5), 500, sessionIds::remove),
                displays,
                clock,
                Duration.ofSeconds(2),
                Duration.ofSeconds(20),
                4.0,
                requestIds::remove,
                Runnable::run);

        context.addInstantFinalTask(() -> {
            input.clear();
            coordinator.clear();
            displays.clear();
            if (!pet.isRemoved()) pet.discard();
            removeMockPlayer(world, owner);
        });

        // Inactive access gives deterministic feedback without a text session/model call.
        coordinator.open(owner, pet);
        context.runAtTick(2, () -> {
            context.assertFalse(input.isActive(owner.getUuid()),
                    Text.literal("Inactive access opened private input"));
            context.assertEquals(0, dialogueCalls.get(),
                    Text.literal("Inactive interaction invoked dialogue"));
            authority.setSnapshot(new PetAuthoritySnapshot(placed, false, true));

            // Opening while too far never creates a text session or a dialogue request.
            owner.refreshPositionAndAngles(near.x + 8, near.y, near.z, 0, 0);
            coordinator.open(owner, pet);
        });
        context.runAtTick(4, () -> {
            context.assertFalse(input.isActive(owner.getUuid()),
                    Text.literal("Far-away interaction opened private input"));
            context.assertEquals(0, dialogueCalls.get(),
                    Text.literal("Opening interaction invoked dialogue"));
            owner.refreshPositionAndAngles(near.x + 1, near.y, near.z, 0, 0);
            coordinator.open(owner, pet);
        });
        context.runAtTick(6, () -> {
            context.assertTrue(input.isActive(owner.getUuid()),
                    Text.literal("Valid interaction did not open private input"));
            context.assertEquals(0, dialogueCalls.get(),
                    Text.literal("Opening private input invoked dialogue"));

            // Submission is revalidated: moving away consumes the private message but calls no AI.
            owner.refreshPositionAndAngles(near.x + 8, near.y, near.z, 0, 0);
            context.assertTrue(input.handleChatMessage(owner, "too far"),
                    Text.literal("Private input did not capture message"));
            context.assertTrue(input.shouldSuppressBroadcast(owner),
                    Text.literal("Captured private message was not marked for suppression"));
        });
        context.runAtTick(9, () -> {
            context.assertEquals(0, dialogueCalls.get(),
                    Text.literal("Invalid-distance submission reached dialogue"));
            clock.advance(Duration.ofSeconds(6));
            owner.refreshPositionAndAngles(near.x + 1, near.y, near.z, 0, 0);
            coordinator.open(owner, pet);
        });
        context.runAtTick(11, () -> {
            context.assertTrue(input.isActive(owner.getUuid()),
                    Text.literal("Reopened private input is not active"));
            context.assertTrue(input.handleChatMessage(owner, "Hello, Mochi"),
                    Text.literal("Valid message was not privately captured"));
            context.assertTrue(input.shouldSuppressBroadcast(owner),
                    Text.literal("Valid private message was not suppressed"));
        });
        context.runAtTick(14, () -> {
            context.assertEquals(1, dialogueCalls.get(),
                    Text.literal("Valid submit did not make exactly one async request"));
            PetDialogueRequest request = capturedRequest.get();
            context.assertTrue(request != null, Text.literal("Dialogue request was not captured"));
            context.assertEquals(owner.getUuid(), request.ownerUuid(), Text.literal("Request owner mismatch"));
            context.assertEquals(petId, request.petId(), Text.literal("Request pet mismatch"));
            context.assertEquals(backend, request.backendId(), Text.literal("Request backend mismatch"));
            context.assertEquals(dimension, request.dimensionId(), Text.literal("Request dimension mismatch"));
            pendingReply.complete(new PetDialogueResponse(
                    request.requestId(), request.sessionId(), request.petId(),
                    PetDialogueResponse.Status.SUCCEEDED,
                    "I am happy to see you. Let's explore together! Third sentence is removed."));
        });
        context.runAtTick(18, () -> {
            DisplayEntity.TextDisplayEntity first = displays.active(petId).orElseThrow(() ->
                    context.createError("Valid correlated reply did not create a Text Display"));
            context.assertEquals(1, displays.activeCount(),
                    Text.literal("Reply created more than one active display"));
            context.assertEquals(DisplayEntity.BillboardMode.FIXED, first.getBillboardMode(),
                    Text.literal("Speech display is not fixed-orientation"));
            context.assertTrue(Math.abs(first.getY() - (pet.getY() + pet.getHeight() + 0.35F)) < 0.01,
                    Text.literal("Speech display is not above the scaled pet"));
            context.assertTrue(Math.abs(MathHelper.wrapDegrees(first.getYaw() - (pet.getYaw() + 180.0F))) < 0.01,
                    Text.literal("Speech display yaw does not track pet yaw plus 180 degrees"));

            pet.refreshPositionAndAngles(near.x + 2, near.y, near.z + 1, 75.0F, 0.0F);
            displays.tick(world.getServer());
            context.assertTrue(Math.abs(first.getX() - pet.getX()) < 0.01
                            && Math.abs(first.getZ() - pet.getZ()) < 0.01,
                    Text.literal("Visible speech display did not follow pet position"));
            context.assertTrue(Math.abs(MathHelper.wrapDegrees(first.getYaw() - 255.0F)) < 0.01,
                    Text.literal("Visible speech display did not follow pet facing"));

            DisplayEntity.TextDisplayEntity replacement = displays.show(pet, "A newer reply replaces it.");
            context.assertTrue(first.isRemoved(), Text.literal("New reply left old display alive"));
            context.assertTrue(replacement != first && displays.activeCount() == 1,
                    Text.literal("New reply did not replace with exactly one display"));
        });
        context.runAtTick(127, () -> {
            displays.tick(world.getServer());
            context.assertTrue(displays.active(petId).isEmpty(),
                    Text.literal("Speech display did not expire at its bounded timeout"));
            DisplayEntity.TextDisplayEntity unloadDisplay = displays.show(pet, "Goodbye for now.");
            displays.onPetUnloaded(pet);
            context.assertTrue(unloadDisplay.isRemoved() && displays.active(petId).isEmpty(),
                    Text.literal("Pet unload did not remove speech display"));
            DisplayEntity.TextDisplayEntity shutdownDisplay = displays.show(pet, "Server is stopping.");
            displays.clear();
            context.assertTrue(shutdownDisplay.isRemoved() && displays.activeCount() == 0,
                    Text.literal("Shutdown clear did not remove speech display"));
            context.complete();
        });
    }

    private static void removeMockPlayer(ServerWorld world, ServerPlayerEntity player) {
        PlayerManager manager = world.getServer().getPlayerManager();
        if (manager.getPlayer(player.getUuid()) == player) manager.remove(player);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class StubAuthority implements PetAuthorityGateway {
        private volatile PetAuthoritySnapshot snapshot;

        private StubAuthority(PetAuthoritySnapshot snapshot) {
            this.snapshot = snapshot;
        }

        private void setSnapshot(PetAuthoritySnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public CompletionStage<Optional<PetAuthoritySnapshot>> findByPetId(UUID petId) {
            return CompletableFuture.completedFuture(
                    snapshot.pet().petId().equals(petId) ? Optional.of(snapshot) : Optional.empty());
        }

        @Override
        public CompletionStage<Optional<PetAuthoritySnapshot>> findByOwner(UUID ownerUuid) {
            return CompletableFuture.completedFuture(
                    snapshot.pet().ownerUuid().equals(ownerUuid) ? Optional.of(snapshot) : Optional.empty());
        }

        @Override
        public CompletionStage<PetAdoptionWireResult> adopt(PetAdoptionWireRequest request) {
            return unsupported();
        }

        @Override
        public CompletionStage<AuthorityMutationResult> place(
                UUID operationId, UUID petId, PetTransitions.Place command) {
            return unsupported();
        }

        @Override
        public CompletionStage<AuthorityMutationResult> compensatePlaceFailure(
                UUID operationId, UUID petId, PetTransitions.CompensatePlaceFailure command) {
            return unsupported();
        }

        @Override
        public CompletionStage<AuthorityMutationResult> pickup(
                UUID operationId, UUID petId, PetTransitions.Pickup command) {
            return unsupported();
        }

        @Override
        public CompletionStage<PetRecallWireResult> recall(
                UUID operationId, UUID petId, PetTransitions.Recall command) {
            return unsupported();
        }

        @Override
        public CompletionStage<PetRecallWireResult> compensateRecallFailure(
                UUID recallOperationId, UUID petId, PetTransitions.CompensateRecallFailure command) {
            return unsupported();
        }

        private static <T> CompletionStage<T> unsupported() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("not used"));
        }
    }
}
