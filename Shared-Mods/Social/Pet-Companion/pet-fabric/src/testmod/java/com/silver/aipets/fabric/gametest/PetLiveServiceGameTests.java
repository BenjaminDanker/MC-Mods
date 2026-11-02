package com.silver.aipets.fabric.gametest;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
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
import com.silver.aipets.common.transport.PetDialogueAction;
import com.silver.aipets.common.transport.PetRecallWireResult;
import com.silver.aipets.fabric.ai.PetActionController;
import com.silver.aipets.fabric.authority.AuthorityMutationResult;
import com.silver.aipets.fabric.authority.PetAuthorityGateway;
import com.silver.aipets.fabric.authority.PetAuthoritySnapshot;
import com.silver.aipets.fabric.config.PetServiceClientConfig;
import com.silver.aipets.fabric.conversation.HttpPetDialogueGateway;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Live end-to-end conversation through the real Fabric chat capture, coordinator, HTTP gateway,
 * a running pet-service, and real OpenRouter models. It runs only when PET_LIVE_* settings point
 * at an isolated test service (never production); otherwise it succeeds immediately.
 *
 * Every turn asserts what a player would see: a successful reply rendered in exactly one
 * floating Text Display above the pet, bounded and free of stage directions, and the chosen
 * action carried out by the pet's local controller.
 */
public final class PetLiveServiceGameTests {
    private static final long TURN_TIMEOUT_NANOS = Duration.ofSeconds(30).toNanos();
    /** The service enforces a five-second cooldown between successful replies. */
    private static final long BETWEEN_TURNS_NANOS = Duration.ofSeconds(6).toNanos();

    // The GameTest server ticks faster than real time, so waits use the wall clock.
    @GameTest(maxTicks = 1_000_000)
    public void liveServiceConversationReachesThePlayer(GameTestHelper context) {
        Optional<Live> configured = Live.fromEnvironment();
        if (configured.isEmpty()) {
            context.succeed();
            return;
        }
        Live live = configured.orElseThrow();
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        BackendId backend = new BackendId(live.backend());
        Instant now = Instant.now();
        Vec3 near = context.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        owner.snapTo(near.x + 1.5, near.y, near.z, 90, 0);
        String dimension = world.dimension().identifier().toString();
        Pet placed = new Pet(live.pet(), owner.getUUID(), live.name(),
                PetAppearance.create(live.species(), ResourceId.parse(live.species() == PetSpecies.CAT
                                ? "minecraft:tabby" : "minecraft:pale"), 0.7,
                        OptionalLong.of(0x5EEDL), AppearanceRules.defaults()),
                PetTraits.initial(50, 50, 50, 50, 50, now), PetMood.initial(50, 20, 10, 10, now),
                PlacedPlacement.materialized(backend, DimensionId.parse(dimension),
                        new WorldPosition(near.x, near.y, near.z), live.entity()),
                0, now, now);
        TamableAnimal pet = new PetEntityFactory().prepare(
                world, placed, live.entity(), new WorldPosition(near.x, near.y, near.z), false).entity();
        pet.snapTo(near, 30.0F, 0.0F);
        context.assertTrue(world.addFreshEntity(pet), Component.literal("Live test pet did not spawn"));

        PrivateChatPetTextInputUi input = new PrivateChatPetTextInputUi(Clock.systemUTC());
        PetSpeechDisplayManager displays = new PetSpeechDisplayManager(PetSpeechPolicy.defaults());
        HttpPetDialogueGateway http = new HttpPetDialogueGateway(new PetServiceClientConfig(
                backend, live.url(), live.token(), Duration.ofSeconds(3), Duration.ofSeconds(25)));
        ConcurrentLinkedQueue<Turn> completed = new ConcurrentLinkedQueue<>();
        PetConversationCoordinator coordinator = new PetConversationCoordinator(
                backend,
                new LiveAuthority(new PetAuthoritySnapshot(placed, false, true)),
                request -> {
                    long started = System.nanoTime();
                    // The mock player's UUID is random; the service owns the synthetic test owner.
                    PetDialogueRequest remapped = new PetDialogueRequest(request.requestId(), request.sessionId(),
                            live.owner(), request.petId(), request.petEntityUuid(), request.backendId(),
                            live.dimension(), request.message(), request.scene());
                    System.out.println("[live-test] sending " + request.requestId() + ": " + request.message()
                    + " sceneVersion=" + PetActionController.sceneVersion(pet));
                    return http.submit(remapped).whenComplete((response, failure) -> {
                        System.out.println("[live-test] completed " + request.requestId() + " status="
                                + (response == null ? "-" : response.status()) + " failure=" + failure);
                        completed.add(new Turn(request.message(), response, failure,
                                (System.nanoTime() - started) / 1_000_000));
                    });
                },
                input,
                new PetConversationSessionRegistry(Duration.ofSeconds(60), Duration.ofSeconds(5), 500, UUID::randomUUID),
                displays,
                Clock.systemUTC(),
                Duration.ofSeconds(2),
                Duration.ofSeconds(25),
                4.0,
                UUID::randomUUID,
                Runnable::run);
        List<String> report = new ArrayList<>();
        report.add("# Live in-game conversation (" + Instant.now() + ", service " + live.url() + ")");

        Runnable cleanup = () -> {
            input.clear();
            coordinator.clear();
            displays.clear();
            if (!pet.isRemoved()) pet.discard();
            var players = world.getServer().getPlayerList();
            if (players.getPlayer(owner.getUUID()) == owner) players.remove(owner);
        };
        new Driver(context, live, owner, pet, placed, input, displays, coordinator, completed, report, cleanup).start();
    }

    private record Turn(String message, PetDialogueResponse response, Throwable failure, long latencyMillis) {
    }

    /** One owner message at a time; each waits for the reply and checks what the player sees. */
    private static final class Driver {
        private final GameTestHelper context;
        private final Live live;
        private final ServerPlayer owner;
        private final TamableAnimal pet;
        private final Pet placed;
        private final PrivateChatPetTextInputUi input;
        private final PetSpeechDisplayManager displays;
        private final PetConversationCoordinator coordinator;
        private final ConcurrentLinkedQueue<Turn> completed;
        private final List<String> report;
        private final Runnable cleanup;
        private int index;
        private long deadline;
        private int successes;

        private Driver(GameTestHelper context, Live live, ServerPlayer owner, TamableAnimal pet, Pet placed,
                PrivateChatPetTextInputUi input, PetSpeechDisplayManager displays,
                PetConversationCoordinator coordinator, ConcurrentLinkedQueue<Turn> completed, List<String> report,
                Runnable cleanup) {
            this.context = context;
            this.live = live;
            this.owner = owner;
            this.pet = pet;
            this.placed = placed;
            this.input = input;
            this.displays = displays;
            this.coordinator = coordinator;
            this.completed = completed;
            this.report = report;
            this.cleanup = cleanup;
        }

        private void start() {
            context.runAfterDelay(5, this::open);
        }

        private void open() {
            if (index >= live.messages().size()) {
                finish();
                return;
            }
            pet.setOrderedToSit(false);
            coordinator.open(owner, pet);
            context.runAfterDelay(10, this::send);
        }

        private void send() {
            context.assertTrue(input.isActive(owner.getUUID()),
                    Component.literal("Right-click did not open private pet chat"));
            String message = live.messages().get(index);
            context.assertTrue(input.handleChatMessage(owner, message),
                    Component.literal("Private chat did not capture: " + message));
            context.assertTrue(input.shouldSuppressBroadcast(owner),
                    Component.literal("Private message would have been broadcast"));
            deadline = System.nanoTime() + TURN_TIMEOUT_NANOS;
            // At accelerated tick rates a waiting pet would live through hundreds of AI decisions
            // in two real seconds; hold it still as a 20 TPS server effectively would.
            pet.setNoAi(true);
            context.runAfterDelay(1, this::await);
        }

        private static void throttle() {
            // Wall-clock waits must not burn the tick budget at unthrottled GameTest speed.
            try { Thread.sleep(5); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }

        private void await() {
            Turn turn = completed.poll();
            if (turn == null) {
                throttle();
                if (System.nanoTime() > deadline) {
                    throw context.assertionException("No reply within 30 seconds for: " + live.messages().get(index));
                }
                context.runAfterDelay(1, this::await);
                return;
            }
            // Let the coordinator's server.execute hop render the reply and queue the action.
            context.runAfterDelay(3, () -> check(turn));
        }

        private void check(Turn turn) {
            System.out.println("[live-test] check: inputActive=" + input.isActive(owner.getUUID())
                    + " sceneVersion=" + PetActionController.sceneVersion(pet)
                    + " distance=" + String.format("%.2f", pet.distanceTo(owner))
                    + " ownerListed=" + (owner.level().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner)
                    + " displays=" + displays.activeCount() + " petRemoved=" + pet.isRemoved());
            pet.setNoAi(false);
            if (turn.failure() != null) {
                throw context.assertionException("Transport failed for '" + turn.message() + "': " + turn.failure());
            }
            PetDialogueResponse response = turn.response();
            boolean succeeded = response.status() == PetDialogueResponse.Status.SUCCEEDED;
            String shown = displays.active(placed.petId()).map(Display.TextDisplay::getText).map(Component::getString).orElse("<none>");
            PetActionController.updateState(pet, placed);
            for (int tick = 0; tick < 4; tick++) PetActionController.tick(pet);
            String observed = observe(response.action());
            report.add(String.format("- [%s] owner: %s%n  pet: %s%n  shown: %s%n  action=%s attention=%s observed=%s latency=%dms",
                    response.status(), turn.message(), response.message(), shown, response.action(),
                    response.attention(), observed, turn.latencyMillis()));
            write();
            if (succeeded) {
                successes++;
                context.assertTrue(displays.activeCount() == 1, Component.literal("Reply did not render exactly one display"));
                context.assertTrue(shown.contains(response.message().strip().substring(0, Math.min(20, response.message().strip().length()))),
                        Component.literal("Displayed text does not match the reply"));
                context.assertFalse(response.message().matches("(?s).*\\*[^*]{1,80}\\*.*"),
                        Component.literal("Reply contains stage directions: " + response.message()));
                context.assertTrue(response.message().length() <= 240,
                        Component.literal("Reply exceeds the floating-text bound"));
                if (response.action() == PetDialogueAction.SIT || response.action() == PetDialogueAction.REST) {
                    context.assertTrue(pet.isOrderedToSit(), Component.literal(response.action() + " was not carried out"));
                }
            }
            write();
            index++;
            deadline = System.nanoTime() + BETWEEN_TURNS_NANOS;
            context.runAfterDelay(1, this::pause);
        }

        private void pause() {
            if (System.nanoTime() < deadline) {
                throttle();
                context.runAfterDelay(1, this::pause);
                return;
            }
            open();
        }

        private String observe(PetDialogueAction action) {
            return switch (action) {
                case SIT, REST -> "sitting=" + pet.isOrderedToSit();
                case APPROACH_OWNER -> String.format("distance_to_owner=%.1f navigating=%s",
                        pet.distanceTo(owner), !pet.getNavigation().isDone());
                case LOOK_AT_OWNER, LOOK_AT_INTEREST, EXPLORE_NEARBY, VOCALIZE, NONE ->
                        String.format("yaw=%.0f navigating=%s", pet.getYRot(), !pet.getNavigation().isDone());
            };
        }

        private void finish() {
            report.add("successes=" + successes + "/" + live.messages().size());
            write();
            context.assertTrue(successes >= live.messages().size() - 1,
                    Component.literal("Too many failed turns: " + successes + "/" + live.messages().size()));
            cleanup.run();
            context.succeed();
        }

        private void write() {
            try {
                Files.writeString(live.log(), String.join(System.lineSeparator(), report) + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ignored) {
                // The report is a convenience; assertions decide the result.
            }
        }
    }

    private record Live(URI url, String token, UUID owner, UUID pet, UUID entity, String name, PetSpecies species,
            String backend, String dimension, List<String> messages, Path log) {
        static Optional<Live> fromEnvironment() {
            String url = setting("PET_LIVE_URL");
            String tokenFile = setting("PET_LIVE_TOKEN_FILE");
            if (url == null || tokenFile == null) return Optional.empty();
            try {
                String token = Files.readString(Path.of(tokenFile), StandardCharsets.UTF_8).strip();
                String messages = setting("PET_LIVE_MESSAGES");
                List<String> turns = messages == null ? List.of(
                        "Hey Mica! Who's the cutest kitty in the whole world?",
                        "Sit down for a bit, okay?",
                        "You're a useless furball, you know that?",
                        "Sorry. I didn't mean that.",
                        "I'm building a sandstone castle by the desert well.",
                        "What am I building again?",
                        "Come here!")
                        : List.of(messages.split("\\|"));
                return Optional.of(new Live(URI.create(url), token, UUID.fromString(setting("PET_LIVE_OWNER")),
                        UUID.fromString(setting("PET_LIVE_PET")), UUID.fromString(setting("PET_LIVE_ENTITY")),
                        Optional.ofNullable(setting("PET_LIVE_NAME")).orElse("Mica"),
                        PetSpecies.valueOf(Optional.ofNullable(setting("PET_LIVE_SPECIES")).orElse("CAT")),
                        Optional.ofNullable(setting("PET_LIVE_BACKEND")).orElse("dev_player_simulated"),
                        Optional.ofNullable(setting("PET_LIVE_DIMENSION")).orElse("minecraft:overworld"),
                        turns, Path.of(Optional.ofNullable(setting("PET_LIVE_LOG")).orElse("live-gametest.md"))));
            } catch (IOException | RuntimeException invalid) {
                throw new IllegalStateException("PET_LIVE_* settings are incomplete", invalid);
            }
        }

        private static String setting(String name) {
            String value = System.getenv(name);
            if (value == null) value = System.getProperty(name);
            return value == null || value.isBlank() ? null : value.strip();
        }
    }

    /** The test pet is placed and paid; ownership/subscription checks then run in the live service. */
    private static final class LiveAuthority implements PetAuthorityGateway {
        private final PetAuthoritySnapshot snapshot;

        private LiveAuthority(PetAuthoritySnapshot snapshot) {
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
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<PetAdoptionWireResult> adopt(PetAdoptionWireRequest request) {
            return unsupported();
        }

        @Override
        public CompletionStage<AuthorityMutationResult> place(UUID operationId, UUID petId, PetTransitions.Place command) {
            return unsupported();
        }

        @Override
        public CompletionStage<AuthorityMutationResult> compensatePlaceFailure(
                UUID operationId, UUID petId, PetTransitions.CompensatePlaceFailure command) {
            return unsupported();
        }

        @Override
        public CompletionStage<AuthorityMutationResult> pickup(UUID operationId, UUID petId, PetTransitions.Pickup command) {
            return unsupported();
        }

        @Override
        public CompletionStage<PetRecallWireResult> recall(UUID operationId, UUID petId, PetTransitions.Recall command) {
            return unsupported();
        }

        @Override
        public CompletionStage<PetRecallWireResult> compensateRecallFailure(
                UUID recallOperationId, UUID petId, PetTransitions.CompensateRecallFailure command) {
            return unsupported();
        }

        private static <T> CompletionStage<T> unsupported() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("not used by the live test"));
        }
    }
}
