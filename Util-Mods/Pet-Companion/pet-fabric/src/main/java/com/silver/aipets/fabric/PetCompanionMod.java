package com.silver.aipets.fabric;

import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.transport.PetWireCodec;
import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.fabric.authority.HttpPetAuthorityGateway;
import com.silver.aipets.fabric.authority.PetAuthorityGateway;
import com.silver.aipets.fabric.command.PetCommands;
import com.silver.aipets.fabric.compass.PetCompassItem;
import com.silver.aipets.fabric.compass.PetCompassManager;
import com.silver.aipets.fabric.compass.PetCompassSigner;
import com.silver.aipets.fabric.config.PetPhysicalConfig;
import com.silver.aipets.fabric.config.PetServiceClientConfig;
import com.silver.aipets.fabric.conversation.PetConversationCoordinator;
import com.silver.aipets.fabric.conversation.ConfiguredPetDialogueGateway;
import com.silver.aipets.fabric.conversation.HttpPetDialogueGateway;
import com.silver.aipets.fabric.conversation.PetDialogueGateway;
import com.silver.aipets.fabric.conversation.PrivateChatPetTextInputUi;
import com.silver.aipets.fabric.metrics.HttpPetMetricsReporter;
import com.silver.aipets.fabric.metrics.PetMetricsReporter;
import com.silver.aipets.fabric.interaction.PetInteractionRouter;
import com.silver.aipets.fabric.entity.PetEntityFactory;
import com.silver.aipets.fabric.placement.PetPickupCoordinator;
import com.silver.aipets.fabric.placement.PetPlacementCoordinator;
import com.silver.aipets.fabric.placement.SafePlacementFinder;
import com.silver.aipets.fabric.reconciliation.PetEntityReconciler;
import com.silver.aipets.fabric.reconciliation.PetEntityRecoveryCoordinator;
import com.silver.aipets.fabric.reconciliation.PeriodicPetReconciliation;
import com.silver.aipets.fabric.reconciliation.PetReconciliationConfig;
import com.silver.aipets.fabric.recall.PetRecallCoordinator;
import com.silver.aipets.fabric.speech.PetSpeechDisplayManager;
import com.silver.aipets.fabric.speech.PetSpeechPolicy;
import com.silver.aipets.fabric.transfer.PetTransferConfig;
import com.silver.aipets.fabric.transfer.PetTransferCoordinator;
import com.silver.aipets.fabric.transfer.PetTransferOutcome;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;

public final class PetCompanionMod implements ModInitializer {
    public static final String MOD_ID = "pet_companion";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final PetPhysicalConfig PHYSICAL_CONFIG = PetPhysicalConfig.defaults();
    private static final AtomicReference<PetEntityReconciler> ENTITY_RECONCILER =
            new AtomicReference<>();
    private static final AtomicReference<PetEntityRecoveryCoordinator> ENTITY_RECOVERY =
            new AtomicReference<>();
    private static final AtomicReference<PetAuthorityGateway> AUTHORITY_GATEWAY =
            new AtomicReference<>();
    private static final AtomicReference<PetMetricsReporter> METRICS_REPORTER =
            new AtomicReference<>();
    private static final AtomicReference<BackendId> AUTHORITY_BACKEND =
            new AtomicReference<>();
    private static final AtomicReference<PetPlacementCoordinator> PLACEMENT_COORDINATOR =
            new AtomicReference<>();
    private static final AtomicReference<PetPickupCoordinator> PICKUP_COORDINATOR =
            new AtomicReference<>();
    private static final AtomicReference<PetCompassManager> COMPASS_MANAGER =
            new AtomicReference<>();
    private static final AtomicReference<PetRecallCoordinator> RECALL_COORDINATOR =
            new AtomicReference<>();
    private static final AtomicReference<PetConversationCoordinator> CONVERSATION_COORDINATOR =
            new AtomicReference<>();
    private static final AtomicReference<PetTransferCoordinator> TRANSFER_COORDINATOR =
            new AtomicReference<>();
    private static final PrivateChatPetTextInputUi PRIVATE_CHAT_INPUT =
            PrivateChatPetTextInputUi.systemClock();
    private static final PetSpeechDisplayManager SPEECH_DISPLAY_MANAGER =
            new PetSpeechDisplayManager(PetSpeechPolicy.defaults());
    private static final PeriodicPetReconciliation PERIODIC_RECONCILIATION =
            new PeriodicPetReconciliation(
                    PetReconciliationConfig.defaults(),
                    ENTITY_RECONCILER::get);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PetCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                PetCommands.register(server.getCommands().getDispatcher()));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            var result = PetInteractionRouter.interact(player, entity, hand);
            if (!world.isClientSide() && result == net.minecraft.world.InteractionResult.PASS
                    && PRIVATE_CHAT_INPUT.isActive(player.getUUID())) {
                // Switching to another entity (including an interactive villager) ends pet input.
                PRIVATE_CHAT_INPUT.endOwner((net.minecraft.server.level.ServerPlayer) player,
                        "Your pet conversation has ended.");
            }
            return result;
        });
        ServerEntityEvents.ENTITY_LOAD.register(PERIODIC_RECONCILIATION::onEntityLoad);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            SPEECH_DISPLAY_MANAGER.onPetUnloaded(entity);
            conversationCoordinator().ifPresent(coordinator -> coordinator.onPetUnloaded(entity));
            if (entity instanceof com.silver.aipets.fabric.entity.PetEntityData data
                    && data.aipets$isPet()) {
                PRIVATE_CHAT_INPUT.cancelPet(data.aipets$getPetId());
            }
        });
        ServerTickEvents.END_LEVEL_TICK.register(PERIODIC_RECONCILIATION::onEndWorldTick);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            petCompassManager().ifPresent(manager -> manager.onEndServerTick(server));
            entityRecoveryCoordinator().ifPresent(manager -> manager.tick(server));
            conversationCoordinator().ifPresent(PetConversationCoordinator::tick);
            PRIVATE_CHAT_INPUT.tick(server);
            SPEECH_DISPLAY_MANAGER.tick(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PetCommands.refreshVisibility(handler.player);
            petCompassManager().ifPresent(manager -> manager.validateAsync(handler.player));
            transferCoordinator().ifPresentOrElse(coordinator ->
                    coordinator.claimDestination(handler.player).thenAccept(outcome -> {
                            LOGGER.info(StructuredPetEvent.operation("transfer_destination_join")
                                    .owner(handler.player.getUUID())
                                    .backend(AUTHORITY_BACKEND.get())
                                    .outcome(outcome.status().name().toLowerCase(java.util.Locale.ROOT))
                                    .toJson());
                            entityRecoveryCoordinator().ifPresent(recovery ->
                                    recovery.recoverOwner(server, handler.player.getUUID()));
                        }), () -> entityRecoveryCoordinator().ifPresent(recovery ->
                            recovery.recoverOwner(server, handler.player.getUUID())));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID ownerUuid = handler.player.getUUID();
            PetCommands.clearPlayerState(ownerUuid);
            PRIVATE_CHAT_INPUT.cancelOwner(ownerUuid);
            conversationCoordinator().ifPresent(coordinator ->
                    coordinator.onOwnerDisconnected(ownerUuid));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PERIODIC_RECONCILIATION.clear();
            entityRecoveryCoordinator().ifPresent(PetEntityRecoveryCoordinator::clear);
            petCompassManager().ifPresent(PetCompassManager::clear);
            conversationCoordinator().ifPresent(PetConversationCoordinator::clear);
            PRIVATE_CHAT_INPUT.clear();
            SPEECH_DISPLAY_MANAGER.clear();
            PetMetricsReporter reporter = METRICS_REPORTER.getAndSet(null);
            closeMetricsReporter(reporter);
        });
        configureAuthorityClient();
        LOGGER.info(StructuredPetEvent.operation("fabric_startup")
                .outcome("initialized").toJson());
    }

    public static PetPhysicalConfig physicalConfig() {
        return PHYSICAL_CONFIG;
    }

    /** Installs the authenticated central-service boundary once runtime configuration is available. */
    public static Runnable installAuthorityGateway(BackendId backendId, PetAuthorityGateway gateway) {
        String ephemeralCompassSecret = UUID.randomUUID().toString() + UUID.randomUUID();
        return installAuthorityGateway(
                backendId,
                gateway,
                ephemeralCompassSecret,
                Map.of(backendId, backendId.value()),
                PetMetricsReporter.noop());
    }

    /** Installs authority plus the persistent server-side compass verifier. */
    public static Runnable installAuthorityGateway(
        BackendId backendId,
        PetAuthorityGateway gateway,
        String compassSigningSecret,
        Map<BackendId, String> backendFriendlyNames) {
        return installAuthorityGateway(
                backendId, gateway, compassSigningSecret, backendFriendlyNames,
                PetMetricsReporter.noop());
    }

    /** Installs authority and an optional non-blocking central metrics reporter. */
    public static Runnable installAuthorityGateway(
            BackendId backendId,
            PetAuthorityGateway gateway,
            String compassSigningSecret,
            Map<BackendId, String> backendFriendlyNames,
            PetMetricsReporter metricsReporter) {
        PetAuthorityGateway installedGateway = Objects.requireNonNull(gateway, "gateway");
        PetMetricsReporter installedMetrics = Objects.requireNonNull(metricsReporter, "metricsReporter");
        PetEntityReconciler reconciler = new PetEntityReconciler(
                Objects.requireNonNull(backendId, "backendId"),
                installedGateway,
                installedMetrics);
        PetEntityRecoveryCoordinator recoveryCoordinator = new PetEntityRecoveryCoordinator(
                backendId, installedGateway, reconciler, new PetEntityFactory());
        PetPlacementCoordinator placementCoordinator = new PetPlacementCoordinator(
                backendId,
                installedGateway,
                new SafePlacementFinder(4, 2),
                new PetEntityFactory(),
                Clock.systemUTC(),
                UUID::randomUUID,
                UUID::randomUUID);
        PetPickupCoordinator pickupCoordinator = new PetPickupCoordinator(
                backendId,
                installedGateway,
                4.0,
                Clock.systemUTC(),
                UUID::randomUUID);
        PetCompassManager compassManager = new PetCompassManager(
                backendId,
                installedGateway,
                new PetCompassSigner(compassSigningSecret),
                backendFriendlyNames);
        PetRecallCoordinator recallCoordinator = new PetRecallCoordinator(
                backendId,
                installedGateway,
                new SafePlacementFinder(4, 2),
                new PetEntityFactory(),
                Clock.systemUTC(),
                UUID::randomUUID,
                UUID::randomUUID);
        PetTransferCoordinator transferCoordinator = new PetTransferCoordinator(
                backendId,
                installedGateway,
                new SafePlacementFinder(4, 2),
                new PetEntityFactory(),
                PetTransferConfig.defaults(),
                Clock.systemUTC(),
                UUID::randomUUID,
                UUID::randomUUID,
                installedMetrics);
        AUTHORITY_GATEWAY.set(installedGateway);
        AUTHORITY_BACKEND.set(backendId);
        PetMetricsReporter previousMetrics = METRICS_REPORTER.getAndSet(installedMetrics);
        if (previousMetrics != installedMetrics) {
            closeMetricsReporter(previousMetrics);
        }
        ENTITY_RECONCILER.set(reconciler);
        ENTITY_RECOVERY.set(recoveryCoordinator);
        PLACEMENT_COORDINATOR.set(placementCoordinator);
        PICKUP_COORDINATOR.set(pickupCoordinator);
        COMPASS_MANAGER.set(compassManager);
        RECALL_COORDINATOR.set(recallCoordinator);
        TRANSFER_COORDINATOR.set(transferCoordinator);
        return () -> {
            TRANSFER_COORDINATOR.compareAndSet(transferCoordinator, null);
            if (METRICS_REPORTER.compareAndSet(installedMetrics, null)) {
                closeMetricsReporter(installedMetrics);
            }
            RECALL_COORDINATOR.compareAndSet(recallCoordinator, null);
            COMPASS_MANAGER.compareAndSet(compassManager, null);
            compassManager.clear();
            PICKUP_COORDINATOR.compareAndSet(pickupCoordinator, null);
            PLACEMENT_COORDINATOR.compareAndSet(placementCoordinator, null);
            ENTITY_RECONCILER.compareAndSet(reconciler, null);
            if (ENTITY_RECOVERY.compareAndSet(recoveryCoordinator, null)) {
                recoveryCoordinator.clear();
            }
            if (AUTHORITY_GATEWAY.compareAndSet(installedGateway, null)) {
                AUTHORITY_BACKEND.compareAndSet(backendId, null);
                PetConversationCoordinator conversation = CONVERSATION_COORDINATOR.getAndSet(null);
                if (conversation != null) {
                    conversation.clear();
                    PRIVATE_CHAT_INPUT.clear();
                    PetInteractionRouter.uninstallHandler(conversation);
                }
            }
        };
    }

    /** Installs the async service conversation transport without coupling Fabric to a provider. */
    public static Runnable installConversationGateway(PetDialogueGateway dialogueGateway) {
        PetAuthorityGateway authority = AUTHORITY_GATEWAY.get();
        BackendId backendId = AUTHORITY_BACKEND.get();
        if (authority == null || backendId == null) {
            throw new IllegalStateException("Install the authority gateway before conversation transport");
        }
        PetConversationCoordinator coordinator = PetConversationCoordinator.defaults(
                backendId,
                authority,
                Objects.requireNonNull(dialogueGateway, "dialogueGateway"),
                SPEECH_DISPLAY_MANAGER,
                PRIVATE_CHAT_INPUT);
        PetConversationCoordinator previous = CONVERSATION_COORDINATOR.getAndSet(coordinator);
        if (previous != null) previous.clear();
        Runnable uninstallHandler = PetInteractionRouter.installHandler(coordinator);
        return () -> {
            if (CONVERSATION_COORDINATOR.compareAndSet(coordinator, null)) {
                coordinator.clear();
                PRIVATE_CHAT_INPUT.clear();
            }
            uninstallHandler.run();
        };
    }

    public static Optional<PetAuthorityGateway> authorityGateway() {
        return Optional.ofNullable(AUTHORITY_GATEWAY.get());
    }

    public static Optional<PetPlacementCoordinator> placementCoordinator() {
        return Optional.ofNullable(PLACEMENT_COORDINATOR.get());
    }

    public static Optional<PetPickupCoordinator> pickupCoordinator() {
        return Optional.ofNullable(PICKUP_COORDINATOR.get());
    }

    public static Optional<PetCompassManager> petCompassManager() {
        return Optional.ofNullable(COMPASS_MANAGER.get());
    }

    public static Optional<PetRecallCoordinator> recallCoordinator() {
        return Optional.ofNullable(RECALL_COORDINATOR.get());
    }

    public static Optional<PetConversationCoordinator> conversationCoordinator() {
        return Optional.ofNullable(CONVERSATION_COORDINATOR.get());
    }

    public static Optional<PetTransferCoordinator> transferCoordinator() {
        return Optional.ofNullable(TRANSFER_COORDINATOR.get());
    }

    public static Optional<PetEntityRecoveryCoordinator> entityRecoveryCoordinator() {
        return Optional.ofNullable(ENTITY_RECOVERY.get());
    }

    /** Queues bounded authority checks for marked entities that are already loaded. */
    public static int queueLoadedReconciliation(net.minecraft.server.MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        int queued = 0;
        for (net.minecraft.server.level.ServerLevel world : server.getAllLevels()) {
            queued += PERIODIC_RECONCILIATION.scanLoadedNow(world);
        }
        entityRecoveryCoordinator().ifPresent(recovery ->
                server.getPlayerList().getPlayers().forEach(player ->
                        recovery.recoverOwner(server, player.getUUID())));
        return queued;
    }

    /**
     * Additive source hook used by the existing portal mod before it requests a proxy switch.
     * The returned future never performs database I/O on the Minecraft server thread.
     */
    public static CompletableFuture<PetTransferOutcome> preparePortalTransfer(
            net.minecraft.server.level.ServerPlayer player,
            String finalDestinationBackend) {
        PetTransferCoordinator coordinator = TRANSFER_COORDINATOR.get();
        if (coordinator == null) {
            return CompletableFuture.completedFuture(PetTransferOutcome.of(
                    com.silver.aipets.fabric.transfer.PetTransferStatus.SERVICE_FAILURE,
                    null,
                    "Pet transfer coordinator is not configured"));
        }
        return coordinator.prepareSource(player, finalDestinationBackend);
    }

    public static PrivateChatPetTextInputUi privateChatInput() {
        return PRIVATE_CHAT_INPUT;
    }

    public static PetSpeechDisplayManager speechDisplayManager() {
        return SPEECH_DISPLAY_MANAGER;
    }

    /** Shared by inventory mixins; unsigned or wrong-owner candidates are never accepted. */
    public static boolean isPetCompassAllowed(Inventory inventory, ItemStack stack) {
        if (!PetCompassItem.isCandidate(stack)) {
            return true;
        }
        return petCompassManager()
                .map(manager -> manager.isAllowedInPlayerInventory(stack, inventory.player.getUUID()))
                .orElse(false);
    }

    private static void configureAuthorityClient() {
        try {
            Optional<PetServiceClientConfig> configured = PetServiceClientConfig.load(
                    FabricLoader.getInstance().getConfigDir(),
                    System.getenv());
            if (configured.isEmpty()) {
                LOGGER.warn(StructuredPetEvent.operation("authority_client_config")
                        .outcome("disabled").toJson());
                return;
            }
            PetServiceClientConfig config = configured.orElseThrow();
            HttpPetMetricsReporter metricsReporter = new HttpPetMetricsReporter(config);
            installAuthorityGateway(
                    config.backendId(),
                    new HttpPetAuthorityGateway(
                            config,
                            new PetWireCodec(AppearanceRules.defaults())),
                    config.compassSigningSecret(),
                    config.backendFriendlyNames(),
                    metricsReporter);
            if ("service".equals(config.conversationMode())) {
                installConversationGateway(new HttpPetDialogueGateway(config));
            } else {
                ConfiguredPetDialogueGateway.Mode dialogueMode =
                        "staging".equals(config.conversationMode())
                                ? ConfiguredPetDialogueGateway.Mode.STAGING
                                : ConfiguredPetDialogueGateway.Mode.DISABLED;
                installConversationGateway(new ConfiguredPetDialogueGateway(dialogueMode));
            }
            LOGGER.info(StructuredPetEvent.operation("authority_client_config")
                    .backend(config.backendId()).outcome("enabled").toJson());
            LOGGER.info(StructuredPetEvent.operation("conversation_transport")
                    .backend(config.backendId()).outcome(config.conversationMode()).toJson());
        } catch (Exception failure) {
            LOGGER.error(StructuredPetEvent.operation("authority_client_config")
                    .failure(failure).outcome("invalid_disabled").toJson());
        }
    }

    private static void closeMetricsReporter(PetMetricsReporter reporter) {
        if (reporter instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Diagnostic transport shutdown must never affect Minecraft shutdown.
            }
        }
    }
}
