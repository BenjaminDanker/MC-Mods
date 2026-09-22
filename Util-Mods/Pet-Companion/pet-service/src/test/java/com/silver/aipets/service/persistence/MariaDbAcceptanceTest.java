package com.silver.aipets.service.persistence;

import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.service.adoption.AppearanceCatalog;
import com.silver.aipets.service.adoption.InitialMood;
import com.silver.aipets.service.adoption.PetAdoptionService;
import com.silver.aipets.service.adoption.PetRandomizer;
import com.silver.aipets.service.subscription.SubscriptionAccess;
import com.silver.aipets.service.sleep.JdbcPetSleepStateStore;
import com.silver.aipets.service.sleep.PetSleepPolicy;
import com.silver.aipets.service.sleep.PetSleepService;
import com.silver.aipets.service.sleep.PetSleepEvent;
import com.silver.aipets.service.memory.JdbcLongTermMemoryStore;
import com.silver.aipets.service.consolidation.ConsolidationCandidateSelector;
import com.silver.aipets.service.consolidation.ConsolidationConfig;
import com.silver.aipets.service.consolidation.ConsolidationModelClient;
import com.silver.aipets.service.consolidation.ConsolidationOutputCodec;
import com.silver.aipets.service.consolidation.ConsolidationOutputValidator;
import com.silver.aipets.service.consolidation.ConsolidationPromptBuilder;
import com.silver.aipets.service.consolidation.ConsolidationWorker;
import com.silver.aipets.service.dialogue.DialogueModelResponse;
import com.silver.aipets.service.dialogue.DialoguePrompt;
import com.silver.aipets.service.dialogue.DialogueTokenCounter;
import com.silver.aipets.service.consolidation.JdbcConsolidationRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs only when deploy/scripts/local-mariadb.ps1 supplies a disposable JDBC instance. */
final class MariaDbAcceptanceTest {
    @Test
    void schemaConstraintsAndConcurrentAdoptionUseRealMariaDb() throws Exception {
        String url = System.getenv("PET_TEST_DB_URL");
        String user = System.getenv("PET_TEST_DB_USER");
        String password = System.getenv("PET_TEST_DB_PASSWORD");
        if (url == null || user == null || password == null) {
            return;
        }
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(url);
            dataSource.setUsername(user);
            dataSource.setPassword(password);
            dataSource.setMaximumPoolSize(8);
            dataSource.setMinimumIdle(1);
            dataSource.setConnectionTimeout(3_000);
            try (Connection connection = dataSource.getConnection()) {
                assertEquals("MariaDB", connection.getMetaData().getDatabaseProductName());
                assertTrue(connection.getMetaData().getDatabaseProductVersion().startsWith("11.4.10"));
                try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    List<String> tables = List.of(
                            "pets", "pet_traits", "pet_mood", "pet_sleep_state", "pet_events",
                            "long_term_memories", "long_term_memory_source_events",
                            "long_term_memory_revisions", "trait_change_audit", "subscriptions",
                            "stripe_webhook_events", "pet_recall_usage", "ai_usage", "jobs",
                            "account_link_tokens", "idempotency_requests", "pending_adoptions");
                    for (int index = 0; index < tables.size(); index++) statement.setString(index + 1, tables.get(index));
                    try (var rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals(17, rows.getInt(1));
                    }
                }
                resetDisposableSchema(connection);
                assertThrows(SQLException.class, () -> {
                    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO pets (pet_id, owner_uuid, name, mob_type, variant_id, scale, placement_state, placed_server, placed_dimension, placed_x, placed_y, placed_z, record_version, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                        String id = UUID.randomUUID().toString();
                        statement.setString(1, id);
                        statement.setString(2, UUID.randomUUID().toString());
                        statement.setString(3, "Invalid");
                        statement.setString(4, "minecraft:cat");
                        statement.setString(5, "minecraft:tabby");
                        statement.setDouble(6, 0.66);
                        statement.setString(7, "HELD");
                        statement.setString(8, "gametest");
                        statement.setString(9, "minecraft:overworld");
                        statement.setDouble(10, 0);
                        statement.setDouble(11, 64);
                        statement.setDouble(12, 0);
                        statement.setLong(13, 0);
                        statement.setObject(14, java.time.LocalDateTime.now(ZoneOffset.UTC));
                        statement.setObject(15, java.time.LocalDateTime.now(ZoneOffset.UTC));
                        statement.executeUpdate();
                    }
                });
            }

            JdbcPetRepository repository = new JdbcPetRepository(dataSource, AppearanceRules.defaults());
            SubscriptionAccess access = owner -> true;
            PetAdoptionService adoption = new PetAdoptionService(
                    repository, access,
                    new PetRandomizer(AppearanceRules.defaults(), AppearanceCatalog.vanilla12110(),
                            InitialMood.defaults(), new java.util.Random(42)),
                    Clock.systemUTC(), UUID::randomUUID);
            UUID owner = UUID.randomUUID();
            ExecutorService workers = Executors.newFixedThreadPool(8);
            try {
                List<CompletableFuture<com.silver.aipets.service.adoption.AdoptionResult>> futures = new ArrayList<>();
                for (int index = 0; index < 8; index++) {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> adoption.adopt(owner, PetSpecies.CAT, "Mochi"), workers));
                }
                List<com.silver.aipets.service.adoption.AdoptionResult> results = futures.stream()
                        .map(CompletableFuture::join).toList();
                assertEquals(8, results.size());
                UUID petId = results.getFirst().pet().orElseThrow().petId();
                assertTrue(results.stream().allMatch(result -> result.pet().orElseThrow().petId().equals(petId)));
                assertNotNull(repository.findByOwner(owner).orElseThrow());

                List<CompletableFuture<Boolean>> statusReads = new ArrayList<>();
                for (int index = 0; index < 32; index++) {
                    statusReads.add(CompletableFuture.supplyAsync(
                            () -> repository.findByOwner(owner).isPresent(), workers));
                }
                CompletableFuture.allOf(statusReads.toArray(CompletableFuture[]::new))
                        .get(10, TimeUnit.SECONDS);
                assertTrue(statusReads.stream().allMatch(CompletableFuture::join));
                assertTrue(dataSource.getHikariPoolMXBean().getTotalConnections() <= 8);

                var persisted = repository.findByOwner(owner).orElseThrow();
                UUID memoryId = UUID.randomUUID();
                try (Connection memoryConnection = dataSource.getConnection();
                     PreparedStatement memoryInsert = memoryConnection.prepareStatement(
                             "INSERT INTO long_term_memories (memory_id, pet_id, memory_text, importance, emotion_tags, entity_tags, location_tags, embedding_status, active) VALUES (?,?,?,?,?,?,?,?,TRUE)")) {
                    memoryInsert.setString(1, memoryId.toString());
                    memoryInsert.setString(2, persisted.petId().toString());
                    memoryInsert.setString(3, "Mochi enjoys the garden.");
                    memoryInsert.setString(4, "MEDIUM");
                    memoryInsert.setString(5, "[]");
                    memoryInsert.setString(6, "[\"Mochi\"]");
                    memoryInsert.setString(7, "[\"garden\"]");
                    memoryInsert.setString(8, "PENDING");
                    assertEquals(1, memoryInsert.executeUpdate());
                }
                JdbcLongTermMemoryStore memories = new JdbcLongTermMemoryStore(dataSource);
                assertTrue(memories.findActive(persisted.petId(), memoryId).isPresent());
                assertTrue(memories.findActive(UUID.randomUUID(), memoryId).isEmpty());

                // Keep the synthetic presence timeline after the persisted creation timestamp;
                // this prevents a slow acceptance run from making the forced-awake deadline win.
                Instant start = Instant.now().plus(Duration.ofHours(2));
                try (Connection reset = dataSource.getConnection();
                     PreparedStatement statement = reset.prepareStatement(
                             "UPDATE pet_sleep_state SET sleeping=FALSE, sleep_started_at=NULL, sleep_ends_at=NULL, "
                                     + "last_sleep_completed_at=NULL, forced_sleep_due_at=?, owner_network_online=TRUE, "
                                     + "owner_last_logout_at=NULL, owner_absence_session_id=NULL, absence_sleep_triggered=FALSE, "
                                     + "last_presence_update_at=?, updated_at=? WHERE pet_id=?")) {
                    statement.setObject(1, java.time.LocalDateTime.ofInstant(
                            start.plus(Duration.ofHours(24)), ZoneOffset.UTC));
                    statement.setObject(2, java.time.LocalDateTime.ofInstant(
                            start.minusSeconds(2), ZoneOffset.UTC));
                    statement.setObject(3, java.time.LocalDateTime.ofInstant(
                            start.minusSeconds(2), ZoneOffset.UTC));
                    statement.setString(4, persisted.petId().toString());
                    assertEquals(1, statement.executeUpdate());
                }
                JdbcPetSleepStateStore sleepStore = new JdbcPetSleepStateStore(dataSource);
                PetSleepService firstService = new PetSleepService(
                        sleepStore, PetSleepPolicy.defaults(), Clock.fixed(start, ZoneOffset.UTC));
                UUID absence = UUID.randomUUID();
                firstService.ownerOnline(owner, start.minusSeconds(1));
                firstService.ownerOffline(owner, absence, start);
                PetSleepService restartedService = new PetSleepService(
                        sleepStore, PetSleepPolicy.defaults(),
                        Clock.fixed(start.plus(Duration.ofMinutes(31)), ZoneOffset.UTC));
                var sleepTransition = restartedService.processDue(10).getFirst();
                assertEquals(PetSleepEvent.SLEEP_STARTED_AFTER_LOGOUT, sleepTransition.event());
                assertEquals(absence, sleepTransition.state().ownerAbsenceSessionId().orElseThrow());
                assertTrue(sleepStore.find(persisted.petId()).orElseThrow().sleeping());

                UUID eventId = UUID.randomUUID();
                Instant consolidationStart = Instant.now().plusSeconds(2);
                try (Connection eventConnection = dataSource.getConnection();
                     PreparedStatement eventInsert = eventConnection.prepareStatement(
                             "INSERT INTO pet_events (event_id,pet_id,owner_uuid,event_type,occurred_at,importance,summary,prompt_eligible,consolidation_status) VALUES (?,?,?,?,?,?,'Mochi explored the garden with the owner.',TRUE,'PENDING')")) {
                    eventInsert.setString(1, eventId.toString());
                    eventInsert.setString(2, persisted.petId().toString());
                    eventInsert.setString(3, owner.toString());
                    eventInsert.setString(4, "GAMEPLAY");
                    eventInsert.setObject(5, java.time.LocalDateTime.ofInstant(consolidationStart, ZoneOffset.UTC));
                    eventInsert.setString(6, "HIGH");
                    assertEquals(1, eventInsert.executeUpdate());
                }

                JdbcConsolidationRepository consolidationRepository = new JdbcConsolidationRepository(
                        dataSource, repository, UUID::randomUUID, UUID::randomUUID);
                QueueConsolidationModel model = new QueueConsolidationModel(eventId);
                ConsolidationConfig consolidationConfig = ConsolidationConfig.defaults();
                WordTokens tokens = new WordTokens();
                ConsolidationWorker firstWorker = consolidationWorker(
                        consolidationRepository, model, consolidationConfig, tokens,
                        Clock.fixed(consolidationStart, ZoneOffset.UTC));
                UUID cycle = UUID.randomUUID();
                String idempotencyKey = "sleep-consolidation:" + persisted.petId() + ':' + cycle;
                assertTrue(firstWorker.enqueueAtSleepStart(persisted.petId(), cycle).created());
                assertEquals(1, firstWorker.processDue("mariadb-worker-a", 10));
                assertEquals("RETRY", consolidationRepository.findByKey(idempotencyKey).orElseThrow().status().name());

                ConsolidationWorker restartedWorker = consolidationWorker(
                        consolidationRepository, model, consolidationConfig, tokens,
                        Clock.fixed(consolidationStart.plus(Duration.ofMinutes(2)), ZoneOffset.UTC));
                assertEquals(1, restartedWorker.processDue("mariadb-worker-b", 10));
                var completedJob = consolidationRepository.findByKey(idempotencyKey).orElseThrow();
                assertEquals("SUCCEEDED", completedJob.status().name(),
                        () -> "restart retry remained " + completedJob.status()
                                + " error=" + completedJob.lastErrorCategory().orElse("none"));
                try (Connection verify = dataSource.getConnection();
                     PreparedStatement statement = verify.prepareStatement(
                             "SELECT consolidation_status FROM pet_events WHERE event_id=? AND pet_id=?")) {
                    statement.setString(1, eventId.toString());
                    statement.setString(2, persisted.petId().toString());
                    try (var rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals("CONSOLIDATED", rows.getString(1));
                    }
                }
                try (Connection verify = dataSource.getConnection();
                     PreparedStatement statement = verify.prepareStatement(
                             "SELECT COUNT(*) FROM long_term_memories WHERE pet_id=? AND active=TRUE")) {
                    statement.setString(1, persisted.petId().toString());
                    try (var rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals(2, rows.getInt(1));
                    }
                }

                UUID killedEvent = UUID.randomUUID();
                Instant killedStart = Instant.now();
                try (Connection eventConnection = dataSource.getConnection();
                     PreparedStatement eventInsert = eventConnection.prepareStatement(
                             "INSERT INTO pet_events (event_id,pet_id,owner_uuid,event_type,occurred_at,importance,summary,prompt_eligible,consolidation_status) VALUES (?,?,?,?,?,?,'Mochi watched the sunset with the owner.',TRUE,'PENDING')")) {
                    eventInsert.setString(1, killedEvent.toString());
                    eventInsert.setString(2, persisted.petId().toString());
                    eventInsert.setString(3, owner.toString());
                    eventInsert.setString(4, "GAMEPLAY");
                    eventInsert.setObject(5, java.time.LocalDateTime.ofInstant(killedStart, ZoneOffset.UTC));
                    eventInsert.setString(6, "HIGH");
                    assertEquals(1, eventInsert.executeUpdate());
                }
                QueueConsolidationModel killModel = new QueueConsolidationModel(killedEvent, false);
                ConsolidationWorker killRecoveryWorker = consolidationWorker(
                        consolidationRepository, killModel,
                        consolidationConfig, tokens, Clock.fixed(killedStart, ZoneOffset.UTC));
                UUID killedCycle = UUID.randomUUID();
                String killedKey = "sleep-consolidation:" + persisted.petId() + ':' + killedCycle;
                assertTrue(killRecoveryWorker.enqueueAtSleepStart(persisted.petId(), killedCycle).created());

                Process killedProcess = startLeaseClaimProcess(killedKey);
                try {
                    assertTrue(awaitClaimed(killedProcess));
                    killedProcess.destroyForcibly();
                    assertTrue(killedProcess.waitFor(5, TimeUnit.SECONDS));
                } finally {
                    if (killedProcess.isAlive()) killedProcess.destroyForcibly();
                }
                Thread.sleep(1_500);
                ConsolidationWorker restartedAfterKill = consolidationWorker(
                        consolidationRepository, killModel, consolidationConfig, tokens,
                        Clock.fixed(Instant.now(), ZoneOffset.UTC));
                assertEquals(1, restartedAfterKill.processDue("mariadb-after-kill", 10));
                var recoveredJob = consolidationRepository.findByKey(killedKey).orElseThrow();
                assertEquals("SUCCEEDED", recoveredJob.status().name());
            } finally {
                workers.shutdownNow();
                workers.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    private static ConsolidationWorker consolidationWorker(
            JdbcConsolidationRepository repository,
            QueueConsolidationModel model,
            ConsolidationConfig config,
            DialogueTokenCounter tokens,
            Clock clock) {
        return new ConsolidationWorker(
                config, repository, owner -> true,
                new ConsolidationCandidateSelector(config, tokens),
                new ConsolidationPromptBuilder(config, tokens), model,
                new ConsolidationOutputCodec(), new ConsolidationOutputValidator(config, tokens),
                card -> { }, clock, UUID::randomUUID, UUID::randomUUID);
    }

    private static void resetDisposableSchema(Connection connection) throws SQLException {
        List<String> tables = List.of(
                "long_term_memory_source_events", "long_term_memory_revisions", "long_term_memories", "trait_change_audit",
                "pet_events", "jobs", "ai_usage", "pet_recall_usage", "stripe_webhook_events",
                "pending_adoptions", "account_link_tokens", "idempotency_requests", "pet_sleep_state", "pet_mood",
                "pet_traits", "subscriptions", "pets");
        try (var statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String table : tables) statement.execute("TRUNCATE TABLE " + table);
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    private static final class QueueConsolidationModel implements ConsolidationModelClient {
        private final UUID source;
        private final Deque<Boolean> failures;

        private QueueConsolidationModel(UUID source) { this(source, true); }

        private QueueConsolidationModel(UUID source, boolean failFirst) {
            this.source = source;
            this.failures = new ArrayDeque<>();
            if (failFirst) failures.add(true);
        }

        @Override public String model() { return "mariadb-acceptance"; }

        @Override
        public DialogueModelResponse complete(
                UUID requestId, DialoguePrompt prompt, String schema, Duration timeout) {
            if (!failures.isEmpty() && failures.removeFirst()) {
                throw new IllegalStateException("simulated process interruption");
            }
            StringBuilder traits = new StringBuilder();
            for (var trait : com.silver.aipets.common.domain.TraitName.values()) {
                if (!traits.isEmpty()) traits.append(',');
                traits.append('"').append(trait.name().toLowerCase()).append("\":0");
            }
            String json = "{\"memory_cards\":[{\"text\":\"Mochi explored the garden with the owner.\","
                    + "\"importance\":\"HIGH\",\"source_event_ids\":[\"" + source + "\"],"
                    + "\"emotion_tags\":[],\"entity_tags\":[\"Mochi\"],\"location_tags\":[\"garden\"]}],"
                    + "\"relationship_summary\":\"Mochi explored the garden with the owner.\","
                    + "\"trait_deltas\":{" + traits + "}}";
            return new DialogueModelResponse(json, Optional.of(requestId.toString()),
                    prompt.inputTokens(), 0, 40, new BigDecimal("0.01"), 5);
        }
    }

    private static final class WordTokens implements DialogueTokenCounter {
        @Override public int count(String text) {
            String stripped = text.strip();
            return stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
        }

        @Override public String truncate(String text, int maximumTokens) {
            String[] words = text.strip().split("\\s+");
            return String.join(" ", java.util.Arrays.copyOf(words, Math.min(words.length, maximumTokens)));
        }
    }

    private static Process startLeaseClaimProcess(String idempotencyKey) throws Exception {
        String javaExecutable = java.nio.file.Path.of(
                System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder process = new ProcessBuilder(
                javaExecutable, "-cp", System.getProperty("java.class.path"),
                MariaDbAcceptanceTest.class.getName() + "$LeaseClaimProcess", idempotencyKey);
        process.redirectErrorStream(true);
        return process.start();
    }

    private static boolean awaitClaimed(Process process) throws Exception {
        CompletableFuture<Boolean> output = CompletableFuture.supplyAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if ("CLAIMED".equals(line.strip())) return true;
                }
                return false;
            } catch (IOException failure) {
                return false;
            }
        });
        return output.get(5, TimeUnit.SECONDS);
    }

    /** Child process used only by the disposable process-kill acceptance drill. */
    public static final class LeaseClaimProcess {
        public static void main(String[] args) throws Exception {
            String url = requireEnvironment("PET_TEST_DB_URL");
            String user = requireEnvironment("PET_TEST_DB_USER");
            String password = requireEnvironment("PET_TEST_DB_PASSWORD");
            try (HikariDataSource dataSource = new HikariDataSource()) {
                dataSource.setJdbcUrl(url);
                dataSource.setUsername(user);
                dataSource.setPassword(password);
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT job_id FROM jobs WHERE job_type='SLEEP_CONSOLIDATION' AND idempotency_key=? FOR UPDATE")) {
                        statement.setString(1, args[0]);
                        try (var rows = statement.executeQuery()) {
                            if (!rows.next()) throw new IllegalStateException("lease drill job missing");
                            String jobId = rows.getString(1);
                            try (PreparedStatement update = connection.prepareStatement(
                                    "UPDATE jobs SET status='RUNNING',attempt_count=attempt_count+1,locked_by='killed-process',locked_until=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 SECOND),updated_at=CURRENT_TIMESTAMP(6),completed_at=NULL WHERE job_id=?")) {
                                update.setString(1, jobId);
                                if (update.executeUpdate() != 1) throw new IllegalStateException("lease claim failed");
                            }
                        }
                    }
                    connection.commit();
                    System.out.println("CLAIMED");
                    System.out.flush();
                    for (;;) Thread.sleep(1_000);
                }
            }
        }

        private static String requireEnvironment(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
            return value;
        }
    }
}
