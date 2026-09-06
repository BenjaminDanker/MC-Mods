package com.silver.aipets.service;

import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.common.transport.PetWireCodec;
import com.silver.aipets.common.transport.PetAdoptionWireCodec;
import com.silver.aipets.service.adoption.AppearanceCatalog;
import com.silver.aipets.service.adoption.InitialMood;
import com.silver.aipets.service.adoption.PetAdoptionService;
import com.silver.aipets.service.adoption.PetRandomizer;
import com.silver.aipets.service.config.PetServiceConfig;
import com.silver.aipets.service.health.JdbcPetReadinessProbe;
import com.silver.aipets.service.http.JdbcPetSleepStateReader;
import com.silver.aipets.service.http.PetAuthorityHttpHandler;
import com.silver.aipets.service.http.PetAdoptionHttpHandler;
import com.silver.aipets.service.http.PetHealthHttpHandler;
import com.silver.aipets.service.http.PetPresenceHttpHandler;
import com.silver.aipets.service.http.AccountLinkHttpHandler;
import com.silver.aipets.service.http.CheckoutHttpHandler;
import com.silver.aipets.service.http.CustomerPortalHttpHandler;
import com.silver.aipets.service.http.StripeWebhookHttpHandler;
import com.silver.aipets.service.http.RecallAdminHttpHandler;
import com.silver.aipets.service.metrics.MetricsHttpHandler;
import com.silver.aipets.service.metrics.PetOperationalMetrics;
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
import com.silver.aipets.service.sleep.JdbcPetSleepStateStore;
import com.silver.aipets.service.sleep.PetSleepPolicy;
import com.silver.aipets.service.sleep.PetSleepScheduler;
import com.silver.aipets.service.sleep.PetSleepService;
import com.silver.aipets.service.transfer.PetTransferExpiryScheduler;
import com.silver.aipets.service.transfer.PetTransferExpiryWorker;
import com.silver.aipets.service.memory.JdbcLongTermMemoryStore;
import com.silver.aipets.service.vector.EmbeddingScheduler;
import com.silver.aipets.service.vector.JdbcEmbeddingJobStore;
import com.silver.aipets.service.vector.MemoryEmbeddingWorker;
import com.silver.aipets.service.vector.MemoryReindexService;
import com.silver.aipets.service.vector.OpenAiEmbeddingModelClient;
import com.silver.aipets.service.vector.QdrantVectorMemoryRepository;
import com.silver.aipets.service.http.MemoryReindexHttpHandler;
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
import java.util.Random;
import java.security.SecureRandom;
import java.util.UUID;

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
    private final AtomicBoolean closed = new AtomicBoolean();

    private PetServiceRuntime(
            PetServiceConfig config,
            HikariDataSource dataSource,
            ThreadPoolExecutor workers,
            HttpServer server,
            PetSleepScheduler sleepScheduler,
            RetentionCleanupScheduler retentionScheduler,
            PetTransferExpiryScheduler transferExpiryScheduler,
            EmbeddingScheduler embeddingScheduler) {
        this.config = config;
        this.dataSource = dataSource;
        this.workers = workers;
        this.server = server;
        this.sleepScheduler = sleepScheduler;
        this.retentionScheduler = retentionScheduler;
        this.transferExpiryScheduler = transferExpiryScheduler;
        this.embeddingScheduler = embeddingScheduler;
    }

    public static PetServiceRuntime create(PetServiceConfig config) throws IOException {
        Objects.requireNonNull(config, "config");
        HikariDataSource dataSource = PetDataSourceFactory.create(config);
        ThreadPoolExecutor workers = createWorkers(config);
        PetSleepScheduler sleepScheduler = null;
        RetentionCleanupScheduler retentionScheduler = null;
        PetTransferExpiryScheduler transferExpiryScheduler = null;
        EmbeddingScheduler embeddingScheduler = null;
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(
                            InetAddress.getByName(config.bindAddress()), config.port()),
                    config.backlog());
            AppearanceRules appearanceRules = AppearanceRules.defaults();
            PetWireCodec petCodec = new PetWireCodec(appearanceRules);
            JdbcPetRepository repository = new JdbcPetRepository(dataSource, appearanceRules);
            JdbcSubscriptionAccess subscriptions = new JdbcSubscriptionAccess(dataSource);
            com.silver.aipets.service.subscription.SubscriptionAccess aiAccess =
                    ownerUuid -> config.aiEnabled() && subscriptions.canAdopt(ownerUuid);
            JdbcPetSleepStateStore sleepStore = new JdbcPetSleepStateStore(dataSource);
            PetSleepService sleepService =
                    new PetSleepService(sleepStore, PetSleepPolicy.defaults(), Clock.systemUTC());
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
            PetOperationalMetrics metrics = new PetOperationalMetrics();
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
                            new PetAdoptionService(
                                    repository,
                                    subscriptions,
                                    new PetRandomizer(
                                            appearanceRules,
                                            AppearanceCatalog.vanilla12110(),
                                            InitialMood.defaults(),
                                            new Random()),
                                    java.time.Clock.systemUTC(),
                                    UUID::randomUUID,
                                    config.allowedSpecies(),
                                    metrics),
                            new PetAdoptionWireCodec(petCodec),
                            config.bearerToken()), metrics));
            server.createContext(
                    "/health",
                    new MetricsHttpHandler(new PetHealthHttpHandler(
                            new JdbcPetReadinessProbe(
                                    dataSource, config.validationTimeoutMs(), config.stripeEnabled()),
                            config.bearerToken(), metrics), metrics));
            server.createContext(
                    "/v1/presence",
                    new MetricsHttpHandler(
                            new PetPresenceHttpHandler(sleepService, config.bearerToken()), metrics));
            if (config.qdrantEnabled()) {
                JdbcLongTermMemoryStore memories = new JdbcLongTermMemoryStore(dataSource);
                JdbcEmbeddingJobStore jobs = new JdbcEmbeddingJobStore(dataSource);
                MemoryEmbeddingWorker embeddingWorker = new MemoryEmbeddingWorker(
                        memories,
                        jobs,
                        OpenAiEmbeddingModelClient.fromConfig(config),
                        QdrantVectorMemoryRepository.fromConfig(config),
                        Clock.systemUTC(),
                        UUID::randomUUID,
                        5,
                        Duration.ofMinutes(2),
                        Duration.ofSeconds(30));
                server.createContext(
                        "/v1/admin/memory/reindex",
                        new MetricsHttpHandler(new MemoryReindexHttpHandler(
                                new MemoryReindexService(memories, embeddingWorker),
                                config.bearerToken()), metrics));
                embeddingScheduler = new EmbeddingScheduler(
                        embeddingWorker, 4, Duration.ofSeconds(15),
                        "embedding-" + UUID.randomUUID(), metrics);
            }
            Clock adminClock = Clock.systemUTC();
            server.createContext(
                    "/v1/admin/recall-reset/",
                    new MetricsHttpHandler(new RecallAdminHttpHandler(
                            new RecallAdminService(
                                    new JdbcRecallAdminRepository(dataSource), adminClock),
                            config.bearerToken(), adminClock), metrics));
            if (config.stripeEnabled()) {
                Clock billingClock = Clock.systemUTC();
                AccountLinkService accountLinks = new AccountLinkService(
                        new JdbcAccountLinkRepository(dataSource),
                        billingClock,
                        Duration.ofMinutes(config.accountLinkTtlMinutes()),
                        new SecureRandom(),
                        config.accountLinkPepper(),
                        config.publicBaseUri());
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
                                        config.publicBaseUri()),
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
                    transferExpiryScheduler, embeddingScheduler);
        } catch (IOException | RuntimeException failure) {
            if (embeddingScheduler != null) {
                embeddingScheduler.close();
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
