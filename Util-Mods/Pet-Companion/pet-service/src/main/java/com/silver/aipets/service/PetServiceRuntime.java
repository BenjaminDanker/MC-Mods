package com.silver.aipets.service;

import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.common.transport.PetWireCodec;
import com.silver.aipets.common.transport.PetAdoptionWireCodec;
import com.silver.aipets.service.adoption.AppearanceCatalog;
import com.silver.aipets.service.adoption.InitialMood;
import com.silver.aipets.service.adoption.PetAdoptionService;
import com.silver.aipets.service.adoption.PetAdoptionWorkflowService;
import com.silver.aipets.service.adoption.PendingAdoptionCompletionScheduler;
import com.silver.aipets.service.adoption.PendingAdoptionCompletionWorker;
import com.silver.aipets.service.adoption.PetRandomizer;
import com.silver.aipets.service.config.PetServiceConfig;
import com.silver.aipets.service.health.JdbcPetReadinessProbe;
import com.silver.aipets.service.http.JdbcPetSleepStateReader;
import com.silver.aipets.service.http.PetAuthorityHttpHandler;
import com.silver.aipets.service.http.PetAdoptionHttpHandler;
import com.silver.aipets.service.http.PetAdoptionNotificationHttpHandler;
import com.silver.aipets.service.http.PetHealthHttpHandler;
import com.silver.aipets.service.http.PetPresenceHttpHandler;
import com.silver.aipets.service.http.AccountLinkHttpHandler;
import com.silver.aipets.service.http.CheckoutHttpHandler;
import com.silver.aipets.service.http.CustomerPortalHttpHandler;
import com.silver.aipets.service.http.SubscriptionAccessHttpHandler;
import com.silver.aipets.service.http.StripeWebhookHttpHandler;
import com.silver.aipets.service.http.RecallAdminHttpHandler;
import com.silver.aipets.service.metrics.MetricsHttpHandler;
import com.silver.aipets.service.metrics.JdbcOperationalMetricsSampler;
import com.silver.aipets.service.metrics.PetOperationalMetrics;
import com.silver.aipets.service.metrics.PetMetricsEventHttpHandler;
import com.silver.aipets.service.persistence.JdbcPetRepository;
import com.silver.aipets.service.persistence.PetDataSourceFactory;
import com.silver.aipets.service.placement.PetPlacementService;
import com.silver.aipets.service.subscription.JdbcSubscriptionAccess;
import com.silver.aipets.service.subscription.AccountLinkService;
import com.silver.aipets.service.subscription.CheckoutLaunchService;
import com.silver.aipets.service.subscription.CustomerPortalService;
import com.silver.aipets.service.subscription.JdbcSubscriptionCustomerLookup;
import com.silver.aipets.service.subscription.JdbcAccountLinkRepository;
import com.silver.aipets.service.subscription.JdbcStripeWebhookRepository;
import com.silver.aipets.service.subscription.StripeSignatureVerifier;
import com.silver.aipets.service.subscription.StripeWebhookParser;
import com.silver.aipets.service.subscription.StripeWebhookService;
import com.silver.aipets.service.subscription.StripeHttpCheckoutClient;
import com.silver.aipets.service.subscription.StripeHttpPortalClient;
import com.silver.aipets.service.recall.PetRecallService;
import com.silver.aipets.service.recall.JdbcRecallAdminRepository;
import com.silver.aipets.service.recall.RecallAdminService;
import com.silver.aipets.service.retention.JdbcRetentionCleanupRepository;
import com.silver.aipets.service.retention.RetentionCleanupScheduler;
import com.silver.aipets.service.retention.RetentionCleanupWorker;
import com.silver.aipets.service.retention.RetentionPolicy;
import com.silver.aipets.service.consolidation.ConsolidationCandidateSelector;
import com.silver.aipets.service.consolidation.ConsolidationConfig;
import com.silver.aipets.service.consolidation.ConsolidationOutputCodec;
import com.silver.aipets.service.consolidation.ConsolidationOutputValidator;
import com.silver.aipets.service.consolidation.ConsolidationScheduler;
import com.silver.aipets.service.consolidation.ConsolidationWorker;
import com.silver.aipets.service.consolidation.JdbcConsolidationRepository;
import com.silver.aipets.service.consolidation.OpenAiConsolidationModelClient;
import com.silver.aipets.service.consolidation.SleepConsolidationTrigger;
import com.silver.aipets.service.sleep.JdbcPetSleepStateStore;
import com.silver.aipets.service.sleep.PetSleepPolicy;
import com.silver.aipets.service.sleep.PetSleepScheduler;
import com.silver.aipets.service.sleep.PetSleepService;
import com.silver.aipets.service.transfer.PetTransferExpiryScheduler;
import com.silver.aipets.service.transfer.PetTransferExpiryWorker;
import com.silver.aipets.service.memory.JdbcLongTermMemoryStore;
import com.silver.aipets.service.vector.EmbeddingScheduler;
import com.silver.aipets.service.vector.EmbeddingModelClient;
import com.silver.aipets.service.vector.JdbcEmbeddingJobStore;
import com.silver.aipets.service.vector.MemoryEmbeddingWorker;
import com.silver.aipets.service.vector.MemoryReindexService;
import com.silver.aipets.service.vector.MemoryRetrievalService;
import com.silver.aipets.service.vector.OpenAiEmbeddingModelClient;
import com.silver.aipets.service.vector.QdrantVectorMemoryRepository;
import com.silver.aipets.service.http.MemoryReindexHttpHandler;
import com.silver.aipets.service.http.PetDialogueHttpHandler;
import com.silver.aipets.service.http.DialogueAdminHistoryHttpHandler;
import com.silver.aipets.service.dialogue.UnavailableDialogueResponder;
import com.silver.aipets.service.dialogue.DialogueAdmissionController;
import com.silver.aipets.service.dialogue.DialogueLimits;
import com.silver.aipets.service.dialogue.DialogueOutputCodec;
import com.silver.aipets.service.dialogue.DialogueOutputValidator;
import com.silver.aipets.service.dialogue.DialoguePromptBuilder;
import com.silver.aipets.service.dialogue.DialogueService;
import com.silver.aipets.service.dialogue.DialogueWireResponder;
import com.silver.aipets.service.dialogue.JdbcDialogueContextLoader;
import com.silver.aipets.service.dialogue.JdbcDialogueHistoryReader;
import com.silver.aipets.service.dialogue.JdbcDialogueStateStore;
import com.silver.aipets.service.dialogue.JdbcDialogueAdminHistoryReader;
import com.silver.aipets.service.dialogue.JTokkitDialogueTokenCounter;
import com.silver.aipets.service.dialogue.ModeratedDialogueSafety;
import com.silver.aipets.service.dialogue.OpenAiDialogueModelClient;
import com.silver.aipets.service.dialogue.OpenAiModerationClient;
import com.silver.aipets.service.dialogue.DialogueMemoryRetriever;
import com.silver.aipets.service.dialogue.RelationalDialogueMemoryRetriever;
import com.silver.aipets.service.dialogue.VectorDialogueMemoryRetriever;
import com.silver.aipets.service.billing.AiBudgetService;
import com.silver.aipets.service.billing.JdbcAiBudgetService;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Random;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Consumer;

/** Owns the standalone HTTP server, bounded worker executor, and bounded database pool. */
public final class PetServiceRuntime implements AutoCloseable {
    private final PetServiceConfig config;
    private final HikariDataSource dataSource;
    private final ThreadPoolExecutor workers;
    private final HttpServer server;
    private final PetSleepScheduler sleepScheduler;
    private final RetentionCleanupScheduler retentionScheduler;
    private final PetTransferExpiryScheduler transferExpiryScheduler;
    private final EmbeddingScheduler embeddingScheduler;
    private final ConsolidationScheduler consolidationScheduler;
    private final PendingAdoptionCompletionScheduler adoptionCompletionScheduler;
    private final AtomicBoolean closed = new AtomicBoolean();

    private PetServiceRuntime(
            PetServiceConfig config,
            HikariDataSource dataSource,
            ThreadPoolExecutor workers,
            HttpServer server,
            PetSleepScheduler sleepScheduler,
            RetentionCleanupScheduler retentionScheduler,
            PetTransferExpiryScheduler transferExpiryScheduler,
            EmbeddingScheduler embeddingScheduler,
            ConsolidationScheduler consolidationScheduler,
            PendingAdoptionCompletionScheduler adoptionCompletionScheduler) {
        this.config = config;
        this.dataSource = dataSource;
        this.workers = workers;
        this.server = server;
        this.sleepScheduler = sleepScheduler;
        this.retentionScheduler = retentionScheduler;
        this.transferExpiryScheduler = transferExpiryScheduler;
        this.embeddingScheduler = embeddingScheduler;
        this.consolidationScheduler = consolidationScheduler;
        this.adoptionCompletionScheduler = adoptionCompletionScheduler;
    }

    public static PetServiceRuntime create(PetServiceConfig config) throws IOException {
        Objects.requireNonNull(config, "config");
        HikariDataSource dataSource = PetDataSourceFactory.create(config);
        ThreadPoolExecutor workers = createWorkers(config);
        PetSleepScheduler sleepScheduler = null;
        RetentionCleanupScheduler retentionScheduler = null;
        PetTransferExpiryScheduler transferExpiryScheduler = null;
        EmbeddingScheduler embeddingScheduler = null;
        ConsolidationScheduler consolidationScheduler = null;
        PendingAdoptionCompletionScheduler adoptionCompletionScheduler = null;
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(
                            InetAddress.getByName(config.bindAddress()), config.port()),
                    config.backlog());
            AppearanceRules appearanceRules = AppearanceRules.defaults();
            PetWireCodec petCodec = new PetWireCodec(appearanceRules);
            JdbcPetRepository repository = new JdbcPetRepository(dataSource, appearanceRules);
            JdbcSubscriptionAccess subscriptions = new JdbcSubscriptionAccess(dataSource);
            com.silver.aipets.service.subscription.SubscriptionAccess subscriptionAccess =
                    new com.silver.aipets.service.subscription.SubscriptionAccess() {
                        @Override
                        public boolean canAdopt(UUID ownerUuid) {
                            return details(ownerUuid).aiAccessEnabled();
                        }

                        @Override
                        public com.silver.aipets.service.subscription.SubscriptionAccessDetails details(
                                UUID ownerUuid) {
                            if (config.dummySubscriptionEnabled()
                                    && ownerUuid.equals(config.dummySubscriptionOwnerUuid())) {
                                return new com.silver.aipets.service.subscription.SubscriptionAccessDetails(
                                        true, "ACTIVE", false, null);
                            }
                            return subscriptions.details(ownerUuid);
                        }
                    };
            com.silver.aipets.service.subscription.SubscriptionAccess aiAccess =
                    ownerUuid -> config.aiEnabled() && subscriptionAccess.canAdopt(ownerUuid);
            Clock billingClock = Clock.systemUTC();
            PetOperationalMetrics metrics = new PetOperationalMetrics();
            JdbcAccountLinkRepository accountLinkRepository = new JdbcAccountLinkRepository(dataSource);
            AccountLinkService accountLinks = config.stripeEnabled()
                    ? new AccountLinkService(
                            accountLinkRepository,
                            billingClock,
                            Duration.ofMinutes(config.accountLinkTtlMinutes()),
                            new SecureRandom(),
                            config.accountLinkPepper(),
                            config.publicBaseUri())
                    : null;
            PetAdoptionService adoptionService = new PetAdoptionService(
                    repository,
                    aiAccess,
                    new PetRandomizer(
                            appearanceRules,
                            AppearanceCatalog.vanilla12110(),
                            InitialMood.defaults(),
                            new Random()),
                    billingClock,
                    UUID::randomUUID,
                    config.allowedSpecies(),
                    metrics);
            PetAdoptionWorkflowService adoptionWorkflow = accountLinks == null ? null
                    : new PetAdoptionWorkflowService(
                            adoptionService,
                            aiAccess,
                            accountLinkRepository,
                            accountLinks,
                            billingClock);
            adoptionCompletionScheduler = new PendingAdoptionCompletionScheduler(
                    new PendingAdoptionCompletionWorker(
                            accountLinkRepository, adoptionService, aiAccess, billingClock));
            AiBudgetService budget = (config.stripeEnabled() || config.dummySubscriptionEnabled())
                    ? new JdbcAiBudgetService(dataSource, subscriptionAccess, config.aiPricing())
                    : AiBudgetService.UNLIMITED;
            JdbcPetSleepStateStore sleepStore = new JdbcPetSleepStateStore(dataSource);
            AtomicReference<Consumer<com.silver.aipets.service.sleep.PetSleepTransition>>
                    consolidationTrigger = new AtomicReference<>(ignored -> { });
            PetSleepService sleepService =
                    new PetSleepService(
                            sleepStore, PetSleepPolicy.defaults(), Clock.systemUTC(),
                            transition -> {
                                try {
                                    consolidationTrigger.get().accept(transition);
                                } catch (RuntimeException failure) {
                                    System.getLogger(PetServiceRuntime.class.getName()).log(
                                            System.Logger.Level.WARNING,
                                            "Sleep consolidation trigger failed safely", failure);
                                }
                            });
            sleepScheduler = new PetSleepScheduler(
                    sleepService,
                    100,
                    Duration.ofSeconds(30));
            RetentionPolicy retentionDefaults = RetentionPolicy.defaults();
            RetentionPolicy retentionPolicy = new RetentionPolicy(
                    Duration.ofDays(config.rawTextRetentionDays()),
                    retentionDefaults.maximumRowsPerRun(),
                    retentionDefaults.maximumAttempts(),
                    retentionDefaults.leaseDuration(),
                    retentionDefaults.retryDelay(),
                    retentionDefaults.scheduleInterval());
            retentionScheduler = new RetentionCleanupScheduler(
                    new RetentionCleanupWorker(
                            retentionPolicy,
                            new JdbcRetentionCleanupRepository(dataSource),
                            Clock.systemUTC(), UUID::randomUUID),
                    retentionPolicy,
                    "retention-" + UUID.randomUUID());
            PetPlacementService placements = new PetPlacementService(repository);
            transferExpiryScheduler =
                    new PetTransferExpiryScheduler(
                            new PetTransferExpiryWorker(
                                    repository, placements, Clock.systemUTC()),
                            100,
                            Duration.ofSeconds(5));
            JdbcOperationalMetricsSampler metricsSampler =
                    new JdbcOperationalMetricsSampler(dataSource, metrics);
            server.createContext(
                    "/v1/pets",
                    new MetricsHttpHandler(new PetAuthorityHttpHandler(
                            repository,
                            new JdbcPetSleepStateReader(dataSource),
                            aiAccess,
                            petCodec,
                            placements,
                            new PetRecallService(repository, Clock.systemUTC(), metrics),
                            config.bearerToken()), metrics));
            server.createContext(
                    "/v1/adoptions",
                    new MetricsHttpHandler(new PetAdoptionHttpHandler(
                            adoptionService,
                            adoptionWorkflow,
                            new PetAdoptionWireCodec(petCodec),
                            config.bearerToken()), metrics));
            server.createContext(
                    "/v1/adoptions/notifications/",
                    new MetricsHttpHandler(new PetAdoptionNotificationHttpHandler(
                            accountLinkRepository, config.bearerToken(), billingClock), metrics));
            server.createContext(
                    "/v1/subscriptions",
                    new MetricsHttpHandler(new SubscriptionAccessHttpHandler(
                            subscriptionAccess, config.bearerToken(), budget), metrics));
            server.createContext(
                    "/health",
                    new MetricsHttpHandler(new PetHealthHttpHandler(
                    new JdbcPetReadinessProbe(
                            dataSource,
                            config.validationTimeoutMs(),
                            config.stripeEnabled(),
                            config.qdrantEnabled(),
                            (config.qdrantEnabled()
                                    || config.dialogueEnabled()
                                    || config.consolidationEnabled())
                                    && !config.openAiApiKey().isBlank()),
                            config.bearerToken(), metrics, metricsSampler::sample), metrics));
            server.createContext(
                    "/v1/presence",
                    new MetricsHttpHandler(
                            new PetPresenceHttpHandler(sleepService, config.bearerToken()), metrics));
            server.createContext(
                    "/v1/metrics/events",
                    new MetricsHttpHandler(
                            new PetMetricsEventHttpHandler(metrics, config.bearerToken()), metrics));
            server.createContext(
                    "/v1/admin/dialogue/history/",
                    new MetricsHttpHandler(new DialogueAdminHistoryHttpHandler(
                            new JdbcDialogueAdminHistoryReader(dataSource, repository),
                            config.bearerToken()), metrics));
            JdbcLongTermMemoryStore sharedMemories =
                    (config.dialogueEnabled() || config.qdrantEnabled())
                            ? new JdbcLongTermMemoryStore(dataSource) : null;
            QdrantVectorMemoryRepository sharedVectors = null;
            EmbeddingModelClient sharedEmbeddingModel = null;
            AtomicReference<MemoryEmbeddingWorker> embeddingWorkerRef = new AtomicReference<>();
            if (config.qdrantEnabled()) {
                sharedVectors = QdrantVectorMemoryRepository.fromConfig(config);
                sharedEmbeddingModel = OpenAiEmbeddingModelClient.fromConfig(config);
            }
            if (config.dialogueEnabled()) {
                // Token/dollar budget is authoritative in production; retain the legacy counter only
                // as an effectively unreachable integer safety ceiling for compatibility.
                DialogueLimits dialogueLimits = DialogueLimits.defaultsWithoutReplyCap();
                DialogueMemoryRetriever memoryRetriever =
                        new RelationalDialogueMemoryRetriever(sharedMemories);
                if (config.qdrantEnabled()) {
                    memoryRetriever = new VectorDialogueMemoryRetriever(
                            new MemoryRetrievalService(
                                    sharedMemories,
                                    sharedVectors,
                                    sharedEmbeddingModel,
                                    workers,
                                    Clock.systemUTC(),
                                    metrics,
                                    new com.silver.aipets.service.vector.JdbcEmbeddingUsageRecorder(dataSource, repository),
                                    budget,
                                    petId -> repository.findById(petId).map(com.silver.aipets.common.domain.Pet::ownerUuid)),
                            memoryRetriever);
                }
                JdbcDialogueContextLoader contexts = new JdbcDialogueContextLoader(
                        repository,
                        new JdbcPetSleepStateReader(dataSource),
                        memoryRetriever,
                        new JdbcDialogueHistoryReader(dataSource),
                        aiAccess);
                OpenAiModerationClient moderation = new OpenAiModerationClient(
                        config.openAiBaseUri(), config.moderationModel(), config.openAiApiKey(),
                        Duration.ofMillis(config.dialogueTimeoutMs()));
                DialogueService dialogue = new DialogueService(
                        dialogueLimits,
                        new ModeratedDialogueSafety(moderation),
                        new DialoguePromptBuilder(dialogueLimits, new JTokkitDialogueTokenCounter()),
                        new OpenAiDialogueModelClient(
                                config.openAiBaseUri(), config.dialogueModel(), config.openAiApiKey(),
                                Duration.ofMillis(config.dialogueTimeoutMs()), "pet_dialogue", config.aiPricing()),
                        new DialogueOutputCodec(),
                        new DialogueOutputValidator(dialogueLimits),
                        new DialogueAdmissionController(dialogueLimits, budget, config.aiPricing()),
                        new JdbcDialogueStateStore(dataSource, UUID::randomUUID),
                        Clock.systemUTC(), UUID::randomUUID, UUID::randomUUID,
                        () -> config.aiEnabled(), metrics);
                server.createContext(
                        "/v1/dialogue",
                        new MetricsHttpHandler(new PetDialogueHttpHandler(
                                new DialogueWireResponder(contexts, dialogue, Clock.systemUTC()),
                                config.bearerToken()), metrics));
            } else {
                // Keep the authenticated route explicit while paid model calls are disabled.
                server.createContext(
                        "/v1/dialogue",
                        new MetricsHttpHandler(new PetDialogueHttpHandler(
                                new UnavailableDialogueResponder(
                                        "Pet conversation is temporarily unavailable."),
                                config.bearerToken()), metrics));
            }
            if (config.qdrantEnabled()) {
                JdbcEmbeddingJobStore jobs = new JdbcEmbeddingJobStore(dataSource);
                MemoryEmbeddingWorker embeddingWorker = new MemoryEmbeddingWorker(
                        sharedMemories,
                        jobs,
                        sharedEmbeddingModel,
                        sharedVectors,
                        Clock.systemUTC(),
                        UUID::randomUUID,
                        5,
                        Duration.ofMinutes(2),
                        Duration.ofSeconds(30),
                        new com.silver.aipets.service.vector.JdbcEmbeddingUsageRecorder(dataSource, repository),
                        budget,
                        petId -> repository.findById(petId).map(com.silver.aipets.common.domain.Pet::ownerUuid));
                embeddingWorkerRef.set(embeddingWorker);
                server.createContext(
                        "/v1/admin/memory/reindex",
                        new MetricsHttpHandler(new MemoryReindexHttpHandler(
                                new MemoryReindexService(sharedMemories, embeddingWorker),
                                config.bearerToken()), metrics));
                embeddingScheduler = new EmbeddingScheduler(
                        embeddingWorker, 4, Duration.ofSeconds(15),
                        "embedding-" + UUID.randomUUID(), metrics);
            }
            if (config.consolidationEnabled()) {
                ConsolidationConfig consolidationConfig = ConsolidationConfig.defaults();
                JTokkitDialogueTokenCounter consolidationTokens =
                        new JTokkitDialogueTokenCounter();
                ConsolidationWorker consolidationWorker = new ConsolidationWorker(
                        consolidationConfig,
                        new JdbcConsolidationRepository(
                                dataSource, repository, UUID::randomUUID, UUID::randomUUID),
                        aiAccess,
                        new ConsolidationCandidateSelector(consolidationConfig, consolidationTokens),
                        new com.silver.aipets.service.consolidation.ConsolidationPromptBuilder(
                                consolidationConfig, consolidationTokens),
                        new OpenAiConsolidationModelClient(
                                config.openAiBaseUri(), config.consolidationModel(),
                                config.openAiApiKey(),
                                Duration.ofMillis(config.dialogueTimeoutMs()), config.aiPricing()),
                        new ConsolidationOutputCodec(),
                        new ConsolidationOutputValidator(consolidationConfig, consolidationTokens),
                        card -> {
                            MemoryEmbeddingWorker worker = embeddingWorkerRef.get();
                            if (worker != null) {
                                try {
                                    worker.enqueue(card);
                                } catch (RuntimeException ignored) {
                                    // The authoritative SQL card remains pending for reindex recovery.
                                }
                            }
                        },
                        Clock.systemUTC(), UUID::randomUUID, UUID::randomUUID,
                        config::aiEnabled, budget);
                consolidationTrigger.set(new SleepConsolidationTrigger(consolidationWorker));
                consolidationScheduler = new ConsolidationScheduler(
                        consolidationWorker,
                        "consolidation-" + UUID.randomUUID(),
                        4,
                        Duration.ofSeconds(30),
                        metrics);
            }
            Clock adminClock = Clock.systemUTC();
            server.createContext(
                    "/v1/admin/recall-reset/",
                    new MetricsHttpHandler(new RecallAdminHttpHandler(
                            new RecallAdminService(
                                    new JdbcRecallAdminRepository(dataSource), adminClock),
                            config.bearerToken(), adminClock), metrics));
            if (config.stripeEnabled()) {
                server.createContext(
                        "/v1/account-links",
                        new MetricsHttpHandler(new AccountLinkHttpHandler(
                                accountLinks, config.bearerToken(), billingClock), metrics));
                server.createContext(
                        "/checkout/",
                        new MetricsHttpHandler(new CheckoutHttpHandler(
                                new CheckoutLaunchService(
                                        accountLinks,
                                        new StripeHttpCheckoutClient(config.stripeSecretKey()),
                                        config.stripePriceId(),
                                        config.publicBaseUri(),
                                        subscriptionAccess),
                                billingClock), metrics));
                server.createContext(
                        "/v1/customer-portal/",
                        new MetricsHttpHandler(new CustomerPortalHttpHandler(
                                new CustomerPortalService(
                                        new JdbcSubscriptionCustomerLookup(dataSource),
                                        new StripeHttpPortalClient(config.stripeSecretKey()),
                                        config.publicBaseUri()),
                                config.bearerToken(), billingClock), metrics));
                StripeWebhookService stripeWebhooks = new StripeWebhookService(
                        new StripeSignatureVerifier(
                                config.stripeWebhookSecret(), billingClock),
                        new StripeWebhookParser(),
                        new JdbcStripeWebhookRepository(dataSource),
                        billingClock,
                        config.stripePriceId(),
                        config.stripePaymentGraceDays());
                server.createContext(
                        "/v1/stripe/webhook",
                        new MetricsHttpHandler(
                                new StripeWebhookHttpHandler(stripeWebhooks), metrics));
            }
            server.setExecutor(workers);
            return new PetServiceRuntime(
                    config, dataSource, workers, server, sleepScheduler, retentionScheduler,
                    transferExpiryScheduler, embeddingScheduler, consolidationScheduler,
                    adoptionCompletionScheduler);
        } catch (IOException | RuntimeException failure) {
            if (embeddingScheduler != null) {
                embeddingScheduler.close();
            }
            if (consolidationScheduler != null) {
                consolidationScheduler.close();
            }
            if (adoptionCompletionScheduler != null) {
                adoptionCompletionScheduler.close();
            }
            if (transferExpiryScheduler != null) {
                transferExpiryScheduler.close();
            }
            if (retentionScheduler != null) {
                retentionScheduler.close();
            }
            if (sleepScheduler != null) {
                sleepScheduler.close();
            }
            workers.shutdownNow();
            dataSource.close();
            throw failure;
        }
    }

    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("Pet service runtime is closed");
        }
        workers.prestartAllCoreThreads();
        server.start();
        sleepScheduler.start();
        retentionScheduler.start();
        transferExpiryScheduler.start();
        if (embeddingScheduler != null) {
            embeddingScheduler.start();
        }
        if (consolidationScheduler != null) {
            consolidationScheduler.start();
        }
        adoptionCompletionScheduler.start();
    }

    public InetSocketAddress address() {
        return server.getAddress();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        server.stop(config.shutdownGraceSeconds());
        sleepScheduler.close();
        retentionScheduler.close();
        transferExpiryScheduler.close();
        if (embeddingScheduler != null) {
            embeddingScheduler.close();
        }
        if (consolidationScheduler != null) {
            consolidationScheduler.close();
        }
        adoptionCompletionScheduler.close();
        workers.shutdown();
        try {
            if (!workers.awaitTermination(config.shutdownGraceSeconds(), TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            dataSource.close();
        }
    }

    private static ThreadPoolExecutor createWorkers(PetServiceConfig config) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(
                    runnable, "pet-service-http-" + sequence.incrementAndGet());
            thread.setDaemon(false);
            thread.setUncaughtExceptionHandler((failedThread, failure) ->
                    System.getLogger(PetServiceRuntime.class.getName()).log(
                            System.Logger.Level.ERROR,
                            StructuredPetEvent.operation("http_worker_uncaught")
                                    .failure(failure).outcome("terminated").toJson()));
            return thread;
        };
        return new ThreadPoolExecutor(
                config.workerThreads(),
                config.workerThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(config.workerQueueCapacity()),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
    }
}
