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
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
                try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    List<String> tables = List.of(
                            "pets", "pet_traits", "pet_mood", "pet_sleep_state", "pet_events",
                            "long_term_memories", "long_term_memory_source_events",
                            "long_term_memory_revisions", "trait_change_audit", "subscriptions",
                            "stripe_webhook_events", "pet_recall_usage", "ai_usage", "jobs",
                            "account_link_tokens", "idempotency_requests");
                    for (int index = 0; index < tables.size(); index++) statement.setString(index + 1, tables.get(index));
                    try (var rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals(16, rows.getInt(1));
                    }
                }
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

                Instant start = Instant.now().plus(Duration.ofMinutes(1));
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
            } finally {
                workers.shutdownNow();
                workers.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }
}
