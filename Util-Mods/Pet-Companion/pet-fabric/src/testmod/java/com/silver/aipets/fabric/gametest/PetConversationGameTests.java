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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;
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
    public void privateInputAsyncReplyAndSpeechLifecycle(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        BackendId backend = new BackendId("survival");
        UUID petId = UUID.fromString("10000000-0000-0000-0000-0000000000f2");
        UUID entityId = UUID.fromString("30000000-0000-0000-0000-0000000000f2");
        Instant now = Instant.parse("2026-08-31T12:00:00Z");
        Vec3 near = context.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        owner.snapTo(near.x + 1, near.y, near.z, 0, 0);

        Pet held = new Pet(
                petId,
                owner.getUUID(),
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
        String dimension = world.dimension().identifier().toString();
        Pet placed = new Pet(
                held.petId(), held.ownerUuid(), held.name(), held.appearance(), held.traits(), held.mood(),
                PlacedPlacement.materialized(
                        backend, DimensionId.parse(dimension), new WorldPosition(near.x, near.y, near.z), entityId),
                held.recordVersion(), held.createdAt(), held.updatedAt());
        TamableAnimal pet = new PetEntityFactory().prepare(
                world, placed, entityId, new WorldPosition(near.x, near.y, near.z), false).entity();
        pet.snapTo(near, 30.0F, 0.0F);
        context.assertTrue(world.addFreshEntity(pet), Component.literal("Conversation pet did not spawn"));

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

        context.succeedWhen(() -> {
            input.clear();
            coordinator.clear();
            displays.clear();
            if (!pet.isRemoved()) pet.discard();
            removeMockPlayer(world, owner);
        });

        // Inactive access gives deterministic feedback without a text session/model call.
        coordinator.open(owner, pet);
        context.runAtTickTime(2, () -> {
            context.assertFalse(input.isActive(owner.getUUID()),
                    Component.literal("Inactive access opened private input"));
            context.assertValueEqual(0, dialogueCalls.get(),
                    Component.literal("Inactive interaction invoked dialogue"));
            authority.setSnapshot(new PetAuthoritySnapshot(placed, false, true));

            // Opening while too far never creates a text session or a dialogue request.
            owner.snapTo(near.x + 8, near.y, near.z, 0, 0);
            coordinator.open(owner, pet);
        });
        context.runAtTickTime(4, () -> {
            context.assertFalse(input.isActive(owner.getUUID()),
                    Component.literal("Far-away interaction opened private input"));
            context.assertValueEqual(0, dialogueCalls.get(),
                    Component.literal("Opening interaction invoked dialogue"));
            owner.snapTo(near.x + 1, near.y, near.z, 0, 0);
            coordinator.open(owner, pet);
        });
        context.runAtTickTime(6, () -> {
            context.assertTrue(input.isActive(owner.getUUID()),
                    Component.literal("Valid interaction did not open private input"));
            context.assertValueEqual(0, dialogueCalls.get(),
                    Component.literal("Opening private input invoked dialogue"));

            // Submission is revalidated: moving away consumes the private message but calls no AI.
            owner.snapTo(near.x + 8, near.y, near.z, 0, 0);
            context.assertTrue(input.handleChatMessage(owner, "too far"),
                    Component.literal("Private input did not capture message"));
            context.assertTrue(input.shouldSuppressBroadcast(owner),
                    Component.literal("Captured private message was not marked for suppression"));
        });
        context.runAtTickTime(9, () -> {
            context.assertValueEqual(0, dialogueCalls.get(),
                    Component.literal("Invalid-distance submission reached dialogue"));
            clock.advance(Duration.ofSeconds(6));
            owner.snapTo(near.x + 1, near.y, near.z, 0, 0);
            coordinator.open(owner, pet);
        });
        context.runAtTickTime(11, () -> {
            context.assertTrue(input.isActive(owner.getUUID()),
                    Component.literal("Reopened private input is not active"));
            context.assertTrue(input.handleChatMessage(owner, "Hello, Mochi"),
                    Component.literal("Valid message was not privately captured"));
            context.assertTrue(input.shouldSuppressBroadcast(owner),
                    Component.literal("Valid private message was not suppressed"));
        });
        context.runAtTickTime(14, () -> {
            context.assertValueEqual(1, dialogueCalls.get(),
                    Component.literal("Valid submit did not make exactly one async request"));
            PetDialogueRequest request = capturedRequest.get();
            context.assertTrue(request != null, Component.literal("Dialogue request was not captured"));
            context.assertValueEqual(owner.getUUID(), request.ownerUuid(), Component.literal("Request owner mismatch"));
            context.assertValueEqual(petId, request.petId(), Component.literal("Request pet mismatch"));
            context.assertValueEqual(backend, request.backendId(), Component.literal("Request backend mismatch"));
            context.assertValueEqual(dimension, request.dimensionId(), Component.literal("Request dimension mismatch"));
            pendingReply.complete(new PetDialogueResponse(
                    request.requestId(), request.sessionId(), request.petId(),
                    PetDialogueResponse.Status.SUCCEEDED,
                    "I am happy to see you. Let's explore together! Third sentence is removed."));
        });
        context.runAtTickTime(18, () -> {
            Display.TextDisplay first = displays.active(petId).orElseThrow(() ->
                    context.assertionException("Valid correlated reply did not create a Text Display"));
            context.assertValueEqual(1, displays.activeCount(),
                    Component.literal("Reply created more than one active display"));
            context.assertValueEqual(Display.BillboardConstraints.CENTER, first.getBillboardConstraints(),
                    Component.literal("Speech display is not client-side camera-facing"));
            context.assertTrue(Math.abs(first.getY() - (pet.getY() + pet.getBbHeight() + 0.65F)) < 0.01,
                    Component.literal("Speech display is not above the scaled pet"));
            context.assertTrue(Math.abs(first.getYRot()) < 0.01F,
                    Component.literal("Speech display retained server-side viewer rotation"));

            pet.snapTo(near.x + 2, near.y, near.z + 1, 75.0F, 0.0F);
            displays.tick(world.getServer());
            context.assertTrue(Math.abs(first.getX() - pet.getX()) < 0.01
                            && Math.abs(first.getZ() - pet.getZ()) < 0.01,
                    Component.literal("Visible speech display did not follow pet position"));
            context.assertTrue(Math.abs(first.getYRot()) < 0.01F,
                    Component.literal("Visible speech display should not rotate on the server"));

            Display.TextDisplay replacement = displays.show(pet, "A newer reply replaces it.");
            context.assertTrue(first.isRemoved(), Component.literal("New reply left old display alive"));
            context.assertTrue(replacement != first && displays.activeCount() == 1,
                    Component.literal("New reply did not replace with exactly one display"));
        });
        context.runAtTickTime(127, () -> {
            displays.tick(world.getServer());
            context.assertTrue(displays.active(petId).isEmpty(),
                    Component.literal("Speech display did not expire at its bounded timeout"));
            Display.TextDisplay unloadDisplay = displays.show(pet, "Goodbye for now.");
            displays.onPetUnloaded(pet);
            context.assertTrue(unloadDisplay.isRemoved() && displays.active(petId).isEmpty(),
                    Component.literal("Pet unload did not remove speech display"));
            Display.TextDisplay shutdownDisplay = displays.show(pet, "Server is stopping.");
            displays.clear();
            context.assertTrue(shutdownDisplay.isRemoved() && displays.activeCount() == 0,
                    Component.literal("Shutdown clear did not remove speech display"));
            context.succeed();
        });
    }

    private static void removeMockPlayer(ServerLevel world, ServerPlayer player) {
        PlayerList manager = world.getServer().getPlayerList();
        if (manager.getPlayer(player.getUUID()) == player) manager.remove(player);
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
        public CompletionStage<Boolean> findSubscriptionAccess(UUID ownerUuid) {
            return CompletableFuture.completedFuture(snapshot.aiAccessEnabled());
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
