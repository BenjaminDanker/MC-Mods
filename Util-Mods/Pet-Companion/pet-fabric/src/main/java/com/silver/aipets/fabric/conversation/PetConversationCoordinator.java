package com.silver.aipets.fabric.conversation;

import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.authority.PetAuthorityGateway;
import com.silver.aipets.fabric.authority.PetAuthoritySnapshot;
import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.interaction.PetInteractionHandler;
import com.silver.aipets.fabric.speech.PetSpeechDisplayManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Coordinates the full Fabric-side conversation lifecycle. Authority and model work remain
 * asynchronous; every entity/UI/display mutation is marshalled back to the server thread.
 */
public final class PetConversationCoordinator implements PetInteractionHandler {
    private static final Text SLEEPING = Text.literal("Your pet is sleeping and cannot chat yet.");
    private static final Text INACTIVE = Text.literal("Your pet is quiet because AI access is inactive.");
    private static final Text UNAVAILABLE = Text.literal("Your pet cannot chat right now. Please try again.");

    private final BackendId backendId;
    private final PetAuthorityGateway authority;
    private final AsyncPetDialogueDispatcher dialogueRequests;
    private final PetTextInputUi textUi;
    private final PetConversationSessionRegistry sessions;
    private final PetSpeechDisplayManager displays;
    private final Clock clock;
    private final Duration authorityTimeout;
    private final double maximumDistanceSquared;
    private final Supplier<UUID> requestIds;
    private final Map<UUID, UUID> pendingOpenByOwner = new HashMap<>();

    public PetConversationCoordinator(
            BackendId backendId,
            PetAuthorityGateway authority,
            PetDialogueGateway dialogue,
            PetTextInputUi textUi,
            PetConversationSessionRegistry sessions,
            PetSpeechDisplayManager displays,
            Clock clock,
            Duration authorityTimeout,
            Duration dialogueTimeout,
            double maximumDistance,
            Supplier<UUID> requestIds,
            Executor requestExecutor) {
        this.backendId = Objects.requireNonNull(backendId, "backendId");
        this.authority = Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(dialogue, "dialogue");
        this.textUi = Objects.requireNonNull(textUi, "textUi");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.displays = Objects.requireNonNull(displays, "displays");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.authorityTimeout = positive(authorityTimeout, "authorityTimeout");
        positive(dialogueTimeout, "dialogueTimeout");
        if (!Double.isFinite(maximumDistance) || maximumDistance <= 0 || maximumDistance > 16) {
            throw new IllegalArgumentException("maximumDistance is outside safe bounds");
        }
        this.maximumDistanceSquared = maximumDistance * maximumDistance;
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.dialogueRequests = new AsyncPetDialogueDispatcher(
                dialogue, dialogueTimeout, Objects.requireNonNull(requestExecutor, "requestExecutor"));
    }

    public static PetConversationCoordinator defaults(
            BackendId backendId,
            PetAuthorityGateway authority,
            PetDialogueGateway dialogue,
            PetSpeechDisplayManager displays,
            PetTextInputUi textUi) {
        return new PetConversationCoordinator(
                backendId, authority, dialogue, textUi,
                PetConversationSessionRegistry.defaults(), displays,
                Clock.systemUTC(), Duration.ofSeconds(3), Duration.ofSeconds(20),
                4.0, UUID::randomUUID, ForkJoinPool.commonPool());
    }

    /** Verifies current central authority before opening; this method never calls the model. */
    @Override
    public void open(ServerPlayerEntity owner, TameableEntity pet) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(pet, "pet");
        if (VillagerPrivateChatCompat.isConversationActive(owner)) {
            feedback(owner, "End your villager conversation with !exit before talking to your pet.");
            return;
        }
        if (!(pet instanceof PetEntityData data) || !data.aipets$isPet()
                || !data.aipets$getOwnerUuid().equals(owner.getUuid())
                || !isLocalInteractionValid(owner, pet, data.aipets$getPetId(), pet.getUuid(), false)) {
            feedback(owner, "Move closer to your placed pet to talk.");
            return;
        }
        if (data.aipets$isSleeping()) {
            owner.sendMessage(SLEEPING, false);
            return;
        }

        MinecraftServer server = ((ServerWorld) owner.getEntityWorld()).getServer();
        UUID openToken = Objects.requireNonNull(requestIds.get(), "requestIds returned null");
        pendingOpenByOwner.put(owner.getUuid(), openToken);
        UUID petId = data.aipets$getPetId();
        UUID entityId = pet.getUuid();
        timed(authority.findByPetId(petId), authorityTimeout).whenComplete((snapshot, failure) ->
                server.execute(() -> finishOpen(
                        server, owner.getUuid(), petId, entityId, openToken, snapshot, failure)));
    }

    public void tick() {
        sessions.purgeExpired(clock.instant());
    }

    public void onOwnerDisconnected(UUID ownerUuid) {
        pendingOpenByOwner.remove(Objects.requireNonNull(ownerUuid, "ownerUuid"));
        sessions.cancelOwner(ownerUuid);
    }

    public void onPetUnloaded(Entity entity) {
        if (entity instanceof PetEntityData data && data.aipets$isPet()) {
            sessions.cancelPet(data.aipets$getPetId());
        }
    }

    public void clear() {
        pendingOpenByOwner.clear();
        sessions.clear();
    }

    public int activeSessionCount() {
        return sessions.activeCount();
    }

    private void finishOpen(
            MinecraftServer server,
            UUID ownerUuid,
            UUID petId,
            UUID entityId,
            UUID openToken,
            Optional<PetAuthoritySnapshot> optionalSnapshot,
            Throwable failure) {
        if (!pendingOpenByOwner.remove(ownerUuid, openToken)) return;
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerUuid);
        if (owner == null) return;
        TameableEntity pet = resolveLocalPet(owner, petId, entityId, false).orElse(null);
        if (pet == null) {
            feedback(owner, "Move closer to your placed pet to talk.");
            return;
        }
        if (failure != null || optionalSnapshot == null || optionalSnapshot.isEmpty()) {
            owner.sendMessage(UNAVAILABLE, false);
            logFailure("authority-open", ownerUuid, petId, null, failure);
            return;
        }
        PetAuthoritySnapshot snapshot = optionalSnapshot.orElseThrow();
        if (snapshot.sleeping()) {
            owner.sendMessage(SLEEPING, false);
            return;
        }
        if (!snapshot.aiAccessEnabled()) {
            owner.sendMessage(INACTIVE, false);
            return;
        }
        if (!matchesAuthority(snapshot, ownerUuid, petId, entityId, owner)) {
            owner.sendMessage(UNAVAILABLE, false);
            return;
        }

        String dimension = owner.getEntityWorld().getRegistryKey().getValue().toString();
        PetConversationSession session = sessions.open(
                ownerUuid, petId, entityId, backendId, dimension, clock.instant());
        try {
            textUi.open(
                    owner,
                    session,
                    snapshot.pet().name(),
                    input -> server.execute(() ->
                            submitOnServer(server, ownerUuid, session.sessionId(), input)),
                    () -> sessions.cancelSession(session.sessionId()));
        } catch (RuntimeException uiFailure) {
            sessions.cancelOwner(ownerUuid);
            owner.sendMessage(UNAVAILABLE, false);
            logFailure("ui-open", ownerUuid, petId, null, uiFailure);
        }
    }

    private void submitOnServer(
            MinecraftServer server, UUID ownerUuid, UUID sessionId, String input) {
        UUID requestId = Objects.requireNonNull(requestIds.get(), "requestIds returned null");
        PetConversationSessionRegistry.BeginResult begun = sessions.beginSubmission(
                sessionId, ownerUuid, requestId, input, clock.instant());
        if (begun.status() != PetConversationSessionRegistry.BeginStatus.ACCEPTED) {
            feedbackRejectedSubmission(server.getPlayerManager().getPlayer(ownerUuid), begun.status());
            return;
        }
        PetConversationSession session = begun.session().orElseThrow();
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerUuid);
        TameableEntity pet = owner == null ? null
                : resolveSessionPet(owner, session).orElse(null);
        if (owner == null || pet == null) {
            completeFailure(server, session, requestId,
                    "Move closer to your placed pet before sending a message.", null);
            return;
        }
        if (((PetEntityData) pet).aipets$isSleeping()) {
            completeFailure(server, session, requestId, SLEEPING.getString(), null);
            return;
        }

        timed(authority.findByPetId(session.petId()), authorityTimeout).whenComplete((snapshot, failure) ->
                server.execute(() -> afterSubmissionAuthority(
                        server, session, requestId, begun.normalizedMessage().orElseThrow(),
                        snapshot, failure)));
    }

    private void afterSubmissionAuthority(
            MinecraftServer server,
            PetConversationSession session,
            UUID requestId,
            String normalizedMessage,
            Optional<PetAuthoritySnapshot> optionalSnapshot,
            Throwable failure) {
        if (sessions.current(session.sessionId(), requestId, clock.instant()).isEmpty()) return;
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(session.ownerUuid());
        TameableEntity pet = owner == null ? null
                : resolveSessionPet(owner, session).orElse(null);
        if (owner == null || pet == null) {
            completeFailure(server, session, requestId, null, failure);
            return;
        }
        if (((PetEntityData) pet).aipets$isSleeping()) {
            completeFailure(server, session, requestId, SLEEPING.getString(), null);
            return;
        }
        if (failure != null || optionalSnapshot == null || optionalSnapshot.isEmpty()) {
            completeFailure(server, session, requestId,
                    "Your pet cannot chat right now. Please try again.", failure);
            return;
        }
        PetAuthoritySnapshot snapshot = optionalSnapshot.orElseThrow();
        if (snapshot.sleeping()) {
            completeFailure(server, session, requestId, SLEEPING.getString(), null);
            return;
        }
        if (!snapshot.aiAccessEnabled()) {
            completeFailure(server, session, requestId, INACTIVE.getString(), null);
            return;
        }
        if (!matchesAuthority(snapshot, session.ownerUuid(), session.petId(),
                session.petEntityUuid(), owner)) {
            completeFailure(server, session, requestId,
                    "Your pet's placed state changed. Open the conversation again.", null);
            return;
        }

        PetDialogueRequest request = new PetDialogueRequest(
                requestId, session.sessionId(), session.ownerUuid(), session.petId(),
                session.petEntityUuid(), session.backendId(), session.dimensionId(),
                normalizedMessage);
        CompletableFuture<PetDialogueResponse> response = dialogueRequests.submit(request);
        response.whenComplete((result, requestFailure) -> server.execute(() ->
                finishResponse(server, session, requestId, result, requestFailure)));
    }

    private void finishResponse(
            MinecraftServer server,
            PetConversationSession session,
            UUID requestId,
            PetDialogueResponse response,
            Throwable failure) {
        if (sessions.current(session.sessionId(), requestId, clock.instant()).isEmpty()) return;
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(session.ownerUuid());
        TameableEntity pet = owner == null ? null
                : resolveSessionPet(owner, session).orElse(null);
        if (owner == null || pet == null) {
            sessions.complete(session.sessionId(), requestId, clock.instant());
            return;
        }
        if (((PetEntityData) pet).aipets$isSleeping()) {
            completeFailure(server, session, requestId, SLEEPING.getString(), null);
            return;
        }
        if (failure != null || response == null) {
            completeFailure(server, session, requestId,
                    "Your pet cannot find the words right now.", failure);
            return;
        }
        if (!response.requestId().equals(requestId)
                || !response.sessionId().equals(session.sessionId())
                || !response.petId().equals(session.petId())) {
            completeFailure(server, session, requestId,
                    "Your pet's reply could not be matched safely.", null);
            return;
        }
        if (!sessions.complete(session.sessionId(), requestId, clock.instant())) return;
        if (response.status() == PetDialogueResponse.Status.DENIED) {
            feedback(owner, response.message());
            return;
        }
        try {
            displays.show(pet, response.message());
        } catch (RuntimeException displayFailure) {
            feedback(owner, "Your pet replied, but the speech display could not be shown.");
            logFailure("speech-display", session.ownerUuid(), session.petId(), requestId, displayFailure);
        }
    }

    private Optional<TameableEntity> resolveLocalPet(
            ServerPlayerEntity owner, UUID petId, UUID entityId, boolean requireAwake) {
        if (!(owner.getEntityWorld() instanceof ServerWorld world)) return Optional.empty();
        Entity found = world.getEntity(entityId);
        if (!(found instanceof TameableEntity pet)
                || !(found instanceof PetEntityData data)
                || !isLocalInteractionValid(owner, pet, petId, entityId, requireAwake)) {
            return Optional.empty();
        }
        return Optional.of(pet);
    }

    private Optional<TameableEntity> resolveSessionPet(
            ServerPlayerEntity owner, PetConversationSession session) {
        String currentDimension = owner.getEntityWorld().getRegistryKey().getValue().toString();
        if (!session.backendId().equals(backendId)
                || !session.dimensionId().equals(currentDimension)) {
            return Optional.empty();
        }
        return resolveLocalPet(owner, session.petId(), session.petEntityUuid(), false);
    }

    private boolean isLocalInteractionValid(
            ServerPlayerEntity owner,
            TameableEntity pet,
            UUID petId,
            UUID entityId,
            boolean requireAwake) {
        if (!(pet instanceof PetEntityData data) || !data.aipets$isPet()) return false;
        return !owner.isRemoved()
                && !pet.isRemoved()
                && owner.getEntityWorld() == pet.getEntityWorld()
                && owner.squaredDistanceTo(pet) <= maximumDistanceSquared
                && owner.getUuid().equals(data.aipets$getOwnerUuid())
                && petId.equals(data.aipets$getPetId())
                && entityId.equals(pet.getUuid())
                && (!requireAwake || !data.aipets$isSleeping());
    }

    private boolean matchesAuthority(
            PetAuthoritySnapshot snapshot,
            UUID ownerUuid,
            UUID petId,
            UUID entityId,
            ServerPlayerEntity owner) {
        Pet pet = snapshot.pet();
        if (!pet.petId().equals(petId) || !pet.ownerUuid().equals(ownerUuid)
                || !(pet.placement() instanceof PlacedPlacement placed)) {
            return false;
        }
        return placed.backendId().equals(backendId)
                && placed.dimensionId().toString().equals(
                        owner.getEntityWorld().getRegistryKey().getValue().toString())
                && placed.entityUuid().filter(entityId::equals).isPresent();
    }

    private void completeFailure(
            MinecraftServer server,
            PetConversationSession session,
            UUID requestId,
            String playerMessage,
            Throwable failure) {
        if (!sessions.complete(session.sessionId(), requestId, clock.instant())) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.ownerUuid());
        if (player != null && playerMessage != null) feedback(player, playerMessage);
        if (failure != null) {
            logFailure("dialogue-request", session.ownerUuid(), session.petId(), requestId, failure);
        }
    }

    private static void feedbackRejectedSubmission(
            ServerPlayerEntity player, PetConversationSessionRegistry.BeginStatus status) {
        if (player == null) return;
        String message = switch (status) {
            case INVALID_INPUT -> "Enter a non-empty message within the configured length limit.";
            case COOLDOWN -> "Your pet needs a moment before another message.";
            case ALREADY_SUBMITTING -> "Your pet is already thinking about a reply.";
            case EXPIRED, SESSION_NOT_FOUND -> "That conversation expired. Right-click your pet again.";
            case OWNER_MISMATCH -> "That conversation does not belong to you.";
            case ACCEPTED -> throw new IllegalArgumentException("Accepted is not a rejection");
        };
        feedback(player, message);
    }

    private static void feedback(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message), false);
    }

    private static void logFailure(
            String stage, UUID ownerId, UUID petId, UUID requestId, Throwable failure) {
        StructuredPetEvent event = StructuredPetEvent.operation("conversation_" + stage)
                .owner(ownerId).pet(petId).outcome(failure == null ? "rejected" : "failed");
        if (requestId != null) event.correlation(requestId);
        if (failure != null) event.failure(failure);
        PetCompanionMod.LOGGER.warn(event.toJson());
    }

    private static <T> CompletableFuture<T> timed(
            CompletionStage<T> stage, Duration timeout) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture().orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
