package com.silver.playersync;

import com.silver.authorization.NetworkShared;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The shared player table and the rules for who may write it.
 *
 * <p>Each player has one row. Whichever server the player is on owns the row: it takes it with a single conditional
 * update ({@code sync} "true" -> "false", {@code server} = this server) and renews a lease ({@code lock_heartbeat})
 * while it holds it. Saving on leave writes the row and hands it back ({@code sync} = "true"). A lease nobody renewed
 * for {@link #STALE_MILLIS} belongs to a server that died, and another server may take it over.
 *
 * <p>Saves are queued, kept on disk until written, and retried by a worker thread, so a database outage or a restart
 * never loses what a player carried. All database work is serialised on this class.
 */
final class SyncStore {
    static final String TABLE = "player";
    static final long STALE_MILLIS = 120_000L;
    private static final long RENEW_EVERY_MILLIS = 30_000L;
    private static final String NOW_MILLIS = "CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS SIGNED)";

    /** Columns added to the table over time (kept so an old table is brought up to date). */
    private static final String[] LATER_COLUMNS = {
        "SoulboundMax int NOT NULL DEFAULT 0", "SkyIslandDefeated boolean NOT NULL DEFAULT false",
        "DesertDefeated boolean NOT NULL DEFAULT false", "OceanDefeated boolean NOT NULL DEFAULT false",
        "CaveDefeated boolean NOT NULL DEFAULT false", "Stage1Cleared boolean NOT NULL DEFAULT false",
        "ElementUnlocks int NOT NULL DEFAULT 0", "SkillPoints int NOT NULL DEFAULT 0", "SkillTree varchar(1024) NOT NULL DEFAULT ''",
        "MagicCompleted boolean NOT NULL DEFAULT false", "TechCompleted boolean NOT NULL DEFAULT false",
        "fmain longtext NULL", "foff longtext NULL", "farmor longtext NULL", "fender longtext NULL",
        "lock_heartbeat bigint NOT NULL DEFAULT 0"};

    private static Connection connection;
    private static String server = "";
    private static Path queueDirectory;
    /** Rows this server owns and must save. */
    private static final Set<UUID> owned = ConcurrentHashMap.newKeySet();
    /** Saves not yet written (also kept on disk in {@link #queueDirectory}). */
    private static final Map<UUID, PlayerSnapshot> queued = new ConcurrentHashMap<>();
    private static final AtomicBoolean working = new AtomicBoolean();
    private static volatile Thread worker;
    private static volatile long lastRenewal;

    private SyncStore() {
    }

    @FunctionalInterface
    interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    interface Reader<T> {
        T read(ResultSet rows) throws SQLException;
    }

    @FunctionalInterface
    interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    /** What the table holds for a player: who has it ({@code sync}, {@code server}) and their saved state. */
    record Stored(boolean free, String server, PlayerSnapshot.Saved saved) {
    }

    // ---- setup and connection

    /** Loads saves left on disk by an earlier run (they are written by the worker). */
    static void start(String serverName, Path queueDir) {
        server = serverName;
        queueDirectory = queueDir;
        try {
            Files.createDirectories(queueDir);
            try (var files = Files.list(queueDir)) {
                files.filter(file -> file.getFileName().toString().endsWith(".json")).forEach(SyncStore::loadQueued);
            }
        } catch (IOException failure) {
            PlayerSyncMod.LOGGER.error("Could not read the queued saves in {}", queueDir, failure);
        }
        startWorker();
    }

    private static void loadQueued(Path file) {
        try {
            PlayerSnapshot snapshot = PlayerSnapshot.fromJson(Files.readString(file));
            queued.put(UUID.fromString(snapshot.uuid), snapshot);
        } catch (Exception broken) {
            PlayerSyncMod.LOGGER.error("Ignoring an unreadable queued save {}", file.getFileName(), broken);
        }
    }

    static String server() {
        return server;
    }

    private static synchronized Connection connection() throws SQLException {
        try {
            if (connection != null && connection.isValid(2)) {
                return connection;
            }
        } catch (SQLException stale) {
            // reconnect below
        }
        closeQuietly();
        NetworkShared.DatabaseLogin login = NetworkShared.databaseLogin()
            .orElseThrow(() -> new SQLException("no database login in ~/.config/mc-network/database.env"));
        connection = DriverManager.getConnection("jdbc:mysql://" + login.hostAndPort() + "/" + login.database()
            + "?connectTimeout=5000&socketTimeout=10000", login.user(), login.password());
        try (Statement schema = connection.createStatement()) {
            schema.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " (id int AUTO_INCREMENT PRIMARY KEY, Name char(16),"
                + " uuid char(36) UNIQUE, Air int, Health float, enderChestInventory longtext, exhaustion float, foodLevel int,"
                + " saturationLevel float, foodTickTimer int, main longtext, off longtext, armor longtext, selectedSlot int,"
                + " experienceLevel int, experienceProgress float, effects longtext, sync char(5), server text)");
            for (String column : LATER_COLUMNS) {
                schema.execute("ALTER TABLE " + TABLE + " ADD COLUMN IF NOT EXISTS " + column);
            }
            schema.execute("CREATE TABLE IF NOT EXISTS branch_wipe (id bigint AUTO_INCREMENT PRIMARY KEY, uuid char(36) NOT NULL,"
                + " name varchar(16), branch varchar(16) NOT NULL, requested_at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),"
                + " done_at timestamp(3) NULL, INDEX branch_pending(branch, done_at))");
            // Shared advancements (SharedAdvancements): one row per player, advancement and criterion.
            schema.execute("CREATE TABLE IF NOT EXISTS player_advancements (uuid char(36) NOT NULL, advancement varchar(255) NOT NULL,"
                + " criterion varchar(255) NOT NULL, obtained bigint NOT NULL, revoked boolean NOT NULL DEFAULT FALSE,"
                + " PRIMARY KEY (uuid, advancement, criterion))");
        }
        return connection;
    }

    private static synchronized void closeQuietly() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // already gone
            }
            connection = null;
        }
    }

    /** Blocks until the database answers, retrying with growing pauses (up to 30 s). */
    static void awaitDatabase() throws InterruptedException {
        long pause = 1_000;
        long nextWarning = 0;
        while (true) {
            try {
                connection();
                return;
            } catch (SQLException down) {
                if (System.currentTimeMillis() >= nextWarning) {
                    PlayerSyncMod.LOGGER.warn("Database unavailable; retrying", down);
                    nextWarning = System.currentTimeMillis() + 30_000;
                }
                Thread.sleep(pause);
                pause = Math.min(30_000, pause * 2);
            }
        }
    }

    // ---- small helpers for everything that talks to the table

    static synchronized int update(String sql, Binder binder) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        }
    }

    static synchronized <T> T query(String sql, Binder binder, Reader<T> reader) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                return reader.read(rows);
            }
        }
    }

    /** Runs {@code work} as one transaction (committed, or rolled back on failure). */
    static synchronized <T> T transaction(Work<T> work) throws SQLException {
        Connection c = connection();
        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            T result = work.run(c);
            c.commit();
            return result;
        } catch (SQLException | RuntimeException failure) {
            try {
                c.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            c.setAutoCommit(autoCommit);
        }
    }

    static int execute(Connection c, String sql, Binder binder) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        }
    }

    /** Makes sure the player has a row (new rows start free). */
    static void ensureRow(String name, String uuid) throws SQLException {
        update("INSERT INTO " + TABLE + " (Name, uuid, sync, server) VALUES (?, ?, 'true', '*') ON DUPLICATE KEY UPDATE Name = VALUES(Name)",
            s -> {
                s.setString(1, name);
                s.setString(2, uuid);
            });
    }

    static Optional<Stored> find(String uuid) throws SQLException {
        return query("SELECT * FROM " + TABLE + " WHERE uuid = ?", s -> s.setString(1, uuid), rows -> rows.next()
            ? Optional.of(new Stored("true".equalsIgnoreCase(rows.getString("sync")), rows.getString("server"), PlayerSnapshot.Saved.read(rows)))
            : Optional.empty());
    }

    // ---- ownership

    /** Takes the row if it is free; false when another server holds it. */
    static boolean take(String uuid) throws SQLException {
        boolean taken = update("UPDATE " + TABLE + " SET sync = 'false', server = ?, lock_heartbeat = " + NOW_MILLIS
            + " WHERE uuid = ? AND sync = 'true'", s -> {
                s.setString(1, server);
                s.setString(2, uuid);
            }) == 1;
        if (taken) {
            owned.add(UUID.fromString(uuid));
        }
        return taken;
    }

    /** Takes a row from {@code deadOwner}, which stopped renewing its lease (it crashed or was killed). */
    static boolean takeFromDead(String uuid, String deadOwner) throws SQLException {
        boolean taken = update("UPDATE " + TABLE + " SET server = ?, lock_heartbeat = " + NOW_MILLIS
            + " WHERE uuid = ? AND sync = 'false' AND server = ? AND lock_heartbeat < " + NOW_MILLIS + " - ?", s -> {
                s.setString(1, server);
                s.setString(2, uuid);
                s.setString(3, deadOwner);
                s.setLong(4, STALE_MILLIS);
            }) == 1;
        if (taken) {
            owned.add(UUID.fromString(uuid));
        }
        return taken;
    }

    /** This server already held the row (it went down with the player online): own it again and renew the lease. */
    static void reclaim(String uuid) throws SQLException {
        owned.add(UUID.fromString(uuid));
        renew(UUID.fromString(uuid));
    }

    /** The player left and a save is queued; the queue keeps renewing the lease until it is written. */
    static void forget(UUID player) {
        owned.remove(player);
    }

    /** The player left before their saved state was applied: the row is untouched, so free it again. */
    static void giveBack(UUID player) throws SQLException {
        if (!owned.remove(player)) {
            return;
        }
        update("UPDATE " + TABLE + " SET sync = 'true' WHERE uuid = ? AND server = ? AND sync = 'false'", s -> {
            s.setString(1, player.toString());
            s.setString(2, server);
        });
    }

    private static void renew(UUID player) throws SQLException {
        update("UPDATE " + TABLE + " SET lock_heartbeat = " + NOW_MILLIS + " WHERE uuid = ? AND server = ? AND sync = 'false'", s -> {
            s.setString(1, player.toString());
            s.setString(2, server);
        });
    }

    private static void renewLeases() {
        long now = System.currentTimeMillis();
        if (now - lastRenewal < RENEW_EVERY_MILLIS) {
            return;
        }
        lastRenewal = now;
        Set<UUID> held = new HashSet<>(owned);
        held.addAll(queued.keySet());
        for (UUID player : held) {
            try {
                renew(player);
            } catch (SQLException failure) {
                PlayerSyncMod.LOGGER.warn("Could not renew the lease on {}", player, failure);
                return;
            }
        }
    }

    // ---- saving

    /** Queues a player's state to be written (on disk at once, in the database as soon as it answers). */
    static void queue(PlayerSnapshot snapshot) {
        UUID player = UUID.fromString(snapshot.uuid);
        queued.put(player, snapshot);
        writeQueueFile(snapshot);
        startWorker();
        Thread running = worker;
        if (running != null) {
            running.interrupt(); // write it now rather than at the next pass
        }
    }

    /** Waits until this player's queued save (if any) is written; used before loading them again on this server. */
    static void awaitSaved(UUID player) throws InterruptedException {
        long pause = 1_000;
        PlayerSnapshot snapshot;
        while ((snapshot = queued.get(player)) != null) {
            try {
                awaitDatabase();
                write(snapshot);
            } catch (SQLException failure) {
                PlayerSyncMod.LOGGER.warn("Retrying the queued save of {}", player, failure);
                Thread.sleep(pause);
                pause = Math.min(30_000, pause * 2);
            }
        }
    }

    /** At shutdown: one attempt to write every queued save (anything left stays on disk for the next start). */
    static void writeQueuedNow() {
        if (queued.isEmpty()) {
            return;
        }
        try {
            connection();
        } catch (SQLException down) {
            PlayerSyncMod.LOGGER.warn("Database unavailable at shutdown; {} save(s) stay queued on disk", queued.size());
            return;
        }
        writeQueued();
    }

    private static void writeQueued() {
        for (PlayerSnapshot snapshot : queued.values()) {
            try {
                write(snapshot);
            } catch (SQLException failure) {
                PlayerSyncMod.LOGGER.warn("The save of {} stays queued", snapshot.name, failure);
            }
        }
    }

    /** Writes one queued save, unless another server has taken the row meanwhile (then its data is newer). */
    private static void write(PlayerSnapshot snapshot) throws SQLException {
        boolean written = transaction(c -> {
            String holder = null;
            try (PreparedStatement lock = c.prepareStatement("SELECT server FROM " + TABLE + " WHERE uuid = ? FOR UPDATE")) {
                lock.setString(1, snapshot.uuid);
                try (ResultSet rows = lock.executeQuery()) {
                    if (rows.next()) {
                        holder = rows.getString("server");
                    }
                }
            }
            if (holder != null && !holder.equals(server) && !"*".equals(holder)) {
                return false;
            }
            snapshot.upsert(c);
            return true;
        });
        if (!written) {
            PlayerSyncMod.LOGGER.warn("Dropped the queued save of {}: another server holds their data now", snapshot.name);
        }
        UUID player = UUID.fromString(snapshot.uuid);
        if (queued.remove(player, snapshot)) {
            try {
                Files.deleteIfExists(queueDirectory.resolve(snapshot.uuid + ".json"));
            } catch (IOException failure) {
                PlayerSyncMod.LOGGER.warn("Saved {} but could not delete their queue file", snapshot.name, failure);
            }
        }
    }

    private static void writeQueueFile(PlayerSnapshot snapshot) {
        if (queueDirectory == null) {
            return;
        }
        Path target = queueDirectory.resolve(snapshot.uuid + ".json");
        Path partial = queueDirectory.resolve(snapshot.uuid + ".tmp");
        try {
            Files.createDirectories(queueDirectory);
            Files.writeString(partial, snapshot.toJson());
            try {
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException plainMove) {
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            PlayerSyncMod.LOGGER.error("Could not write the queue file for {}; the save is kept in memory", snapshot.name, failure);
        }
    }

    /** Writes queued saves and renews leases every few seconds, waiting out database outages. */
    private static void startWorker() {
        if (!working.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(() -> {
            long pause = 1_000;
            while (working.get()) {
                try {
                    awaitDatabase();
                    pause = 1_000;
                    writeQueued();
                    renewLeases();
                    Thread.sleep(5_000);
                } catch (InterruptedException woken) {
                    if (!working.get()) {
                        return;
                    }
                } catch (RuntimeException failure) {
                    PlayerSyncMod.LOGGER.error("The save worker failed; retrying", failure);
                    try {
                        Thread.sleep(pause);
                    } catch (InterruptedException ignored) {
                        // carry on
                    }
                    pause = Math.min(30_000, pause * 2);
                }
            }
        }, "Player-Sync-Saver");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    static void stop() {
        working.set(false);
        Thread running = worker;
        if (running != null) {
            running.interrupt();
        }
        closeQuietly();
    }

    /** Replaces the stored inventories of a player (after removing items from them). */
    static void replaceInventories(String uuid, String main, String off, String armor, String ender) throws SQLException {
        update("UPDATE " + TABLE + " SET main = ?, off = ?, armor = ?, enderChestInventory = ? WHERE uuid = ?", s -> {
            s.setString(1, main);
            s.setString(2, off);
            s.setString(3, armor);
            s.setString(4, ender);
            s.setString(5, uuid);
        });
    }
}
