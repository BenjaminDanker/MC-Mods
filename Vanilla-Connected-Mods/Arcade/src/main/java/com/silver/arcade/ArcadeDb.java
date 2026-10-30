package com.silver.arcade;

import com.silver.authorization.NetworkShared;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The arcade's shared records, in the network database (login from the host's shared config): where each visitor came
 * from, and the prizes waiting for them in that world. All calls run on one background thread.
 */
public final class ArcadeDb {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "arcade-db");
        thread.setDaemon(true);
        return thread;
    });

    public record Prize(long id, int tier, String fromRun) {
    }

    /** What the door side needs to know about a player: been to the arcade? has the die? discovery doors offered so far. */
    public record Profile(boolean visited, boolean hasDie, int offers, long secondsSinceOffer) {
    }

    /** A run left mid-way: what was earned and the tier it was at; it can be resumed for 15 minutes. */
    public record Suspended(int tier, int prizeTier, String origin, long ageSeconds) {
    }

    private Connection connection;

    private synchronized Connection connection() throws SQLException {
        if (connection != null && connection.isValid(2)) {
            return connection;
        }
        NetworkShared.DatabaseLogin login = NetworkShared.databaseLogin()
            .orElseThrow(() -> new SQLException("no database login in ~/.config/mc-network/database.env"));
        connection = DriverManager.getConnection("jdbc:mysql://" + login.hostAndPort() + "/" + login.database()
            + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000", login.user(), login.password());
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS arcade_entries (uuid char(36) PRIMARY KEY, name varchar(32) NOT NULL,"
                + " origin varchar(64) NOT NULL, entered_at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3))");
            s.execute("CREATE TABLE IF NOT EXISTS arcade_prizes (id bigint AUTO_INCREMENT PRIMARY KEY, uuid char(36) NOT NULL,"
                + " server varchar(64) NOT NULL, tier int NOT NULL, run varchar(64) NOT NULL,"
                + " created_at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX (uuid, server))");
            s.execute("CREATE TABLE IF NOT EXISTS arcade_prize_log (id bigint AUTO_INCREMENT PRIMARY KEY, uuid char(36) NOT NULL,"
                + " tier int NOT NULL, at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX (uuid, at))");
            s.execute("CREATE TABLE IF NOT EXISTS arcade_room_log (id bigint AUTO_INCREMENT PRIMARY KEY, room varchar(32) NOT NULL,"
                + " level int NOT NULL, players int NOT NULL, cleared int NOT NULL, used_s int NOT NULL, limit_s int NOT NULL,"
                + " test boolean NOT NULL, at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX (room, level), INDEX (at))");
            // rooms were renamed to their titles (2026-10-30); older log rows follow
            for (var r : com.silver.arcade.realm.Rooms.RENAMED.entrySet()) {
                s.execute("UPDATE arcade_room_log SET room = '" + r.getValue() + "' WHERE room = '" + r.getKey() + "'");
            }
            s.execute("CREATE TABLE IF NOT EXISTS arcade_dice (uuid char(36) PRIMARY KEY, given_at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3))");
            s.execute("CREATE TABLE IF NOT EXISTS arcade_discovery (uuid char(36) PRIMARY KEY, offers int NOT NULL DEFAULT 0,"
                + " last_offer timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3))");
            s.execute("CREATE TABLE IF NOT EXISTS arcade_suspended (uuid char(36) PRIMARY KEY, tier int NOT NULL, prize_tier int NOT NULL,"
                + " origin varchar(64) NOT NULL, since timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3))");
        }
        return connection;
    }

    /** Door side: the player is stepping into the arcade from {@code origin}. */
    public CompletableFuture<Void> recordEntry(UUID player, String name, String origin) {
        return CompletableFuture.runAsync(() -> run(c -> {
            try (PreparedStatement p = c.prepareStatement("REPLACE INTO arcade_entries (uuid, name, origin) VALUES (?, ?, ?)")) {
                p.setString(1, player.toString());
                p.setString(2, name);
                p.setString(3, origin);
                p.executeUpdate();
            }
        }), IO);
    }

    /** Realm side: the world the player came from, if known. */
    public CompletableFuture<Optional<String>> originOf(UUID player) {
        return CompletableFuture.supplyAsync(() -> call(c -> {
            try (PreparedStatement p = c.prepareStatement("SELECT origin FROM arcade_entries WHERE uuid = ?")) {
                p.setString(1, player.toString());
                try (ResultSet r = p.executeQuery()) {
                    return r.next() ? Optional.of(r.getString(1)) : Optional.<String>empty();
                }
            }
        }), IO);
    }

    /** Realm side: a cashed-out run's prize, kept for the world it is delivered in. */
    public CompletableFuture<Void> addPrize(UUID player, String server, int tier, String run) {
        return CompletableFuture.runAsync(() -> run(c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO arcade_prizes (uuid, server, tier, run) VALUES (?, ?, ?, ?)")) {
                p.setString(1, player.toString());
                p.setString(2, server);
                p.setInt(3, tier);
                p.setString(4, run);
                p.executeUpdate();
            }
        }), IO);
    }

    /** How many prizes this player has cashed out in the last {@code hours} hours (any world). */
    public CompletableFuture<Integer> recentPrizes(UUID player, int hours) {
        return CompletableFuture.supplyAsync(() -> {
            int[] count = {0};
            run(c -> {
                try (PreparedStatement p = c.prepareStatement(
                        "SELECT COUNT(*) FROM arcade_prize_log WHERE uuid = ? AND at > NOW(3) - INTERVAL ? HOUR")) {
                    p.setString(1, player.toString());
                    p.setInt(2, hours);
                    try (var r = p.executeQuery()) {
                        if (r.next()) {
                            count[0] = r.getInt(1);
                        }
                    }
                }
            });
            return count[0];
        }, IO);
    }

    public CompletableFuture<Void> logPrize(UUID player, int tier) {
        return CompletableFuture.runAsync(() -> run(c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO arcade_prize_log (uuid, tier) VALUES (?, ?)")) {
                p.setString(1, player.toString());
                p.setInt(2, tier);
                p.executeUpdate();
            }
        }), IO);
    }

    /** One room's outcome, for tuning difficulty. */
    public CompletableFuture<Void> logRoom(String room, int level, int players, int cleared, int usedSeconds, int limitSeconds, boolean test) {
        return CompletableFuture.runAsync(() -> run(c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO arcade_room_log (room, level, players, cleared, used_s, limit_s, test) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                p.setString(1, room);
                p.setInt(2, level);
                p.setInt(3, players);
                p.setInt(4, cleared);
                p.setInt(5, usedSeconds);
                p.setInt(6, limitSeconds);
                p.setBoolean(7, test);
                p.executeUpdate();
            }
        }), IO);
    }

    /** Per room and level over the last {@code days}: plays, players, clears, and the average share of the clock used by clears. */
    public record RoomStat(String room, int level, int plays, int players, int cleared, double clockUsed) {
    }

    public CompletableFuture<List<RoomStat>> roomStats(String room, int days) {
        return CompletableFuture.supplyAsync(() -> call(c -> {
            List<RoomStat> out = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement("SELECT room, level, COUNT(*), SUM(players), SUM(cleared),"
                    + " AVG(CASE WHEN cleared > 0 THEN used_s / limit_s END) FROM arcade_room_log"
                    + " WHERE NOT test AND at > NOW() - INTERVAL ? DAY AND (? IS NULL OR room = ?) GROUP BY room, level ORDER BY room, level")) {
                p.setInt(1, days);
                p.setString(2, room);
                p.setString(3, room);
                try (var r = p.executeQuery()) {
                    while (r.next()) {
                        out.add(new RoomStat(r.getString(1), r.getInt(2), r.getInt(3), r.getInt(4), r.getInt(5), r.getDouble(6)));
                    }
                }
            }
            return out;
        }), IO);
    }

    /** Door side: takes (removes and returns) the prizes waiting for this player on this server. */
    public CompletableFuture<List<Prize>> takePrizes(UUID player, String server) {
        return CompletableFuture.supplyAsync(() -> call(c -> {
            List<Prize> prizes = new ArrayList<>();
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("SELECT id, tier, run FROM arcade_prizes WHERE uuid = ? AND server = ? FOR UPDATE")) {
                    p.setString(1, player.toString());
                    p.setString(2, server);
                    try (ResultSet r = p.executeQuery()) {
                        while (r.next()) {
                            prizes.add(new Prize(r.getLong(1), r.getInt(2), r.getString(3)));
                        }
                    }
                }
                for (Prize prize : prizes) {
                    try (PreparedStatement d = c.prepareStatement("DELETE FROM arcade_prizes WHERE id = ?")) {
                        d.setLong(1, prize.id());
                        d.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException failure) {
                c.rollback();
                throw failure;
            } finally {
                c.setAutoCommit(auto);
            }
            return prizes;
        }), IO);
    }

    /** Realm side: keeps a run the player left mid-way. */
    public CompletableFuture<Void> suspend(UUID player, int tier, int prizeTier, String origin) {
        return CompletableFuture.runAsync(() -> run(c -> {
            try (PreparedStatement p = c.prepareStatement("REPLACE INTO arcade_suspended (uuid, tier, prize_tier, origin) VALUES (?, ?, ?, ?)")) {
                p.setString(1, player.toString());
                p.setInt(2, tier);
                p.setInt(3, prizeTier);
                p.setString(4, origin);
                p.executeUpdate();
            }
        }), IO);
    }

    /** Realm side: takes (removes and returns) the player's suspended run, if any. */
    public CompletableFuture<Optional<Suspended>> takeSuspended(UUID player) {
        return CompletableFuture.supplyAsync(() -> call(c -> {
            Optional<Suspended> found = Optional.empty();
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT tier, prize_tier, origin, TIMESTAMPDIFF(SECOND, since, CURRENT_TIMESTAMP(3)) FROM arcade_suspended WHERE uuid = ?")) {
                p.setString(1, player.toString());
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) {
                        found = Optional.of(new Suspended(r.getInt(1), r.getInt(2), r.getString(3), r.getLong(4)));
                    }
                }
            }
            try (PreparedStatement d = c.prepareStatement("DELETE FROM arcade_suspended WHERE uuid = ?")) {
                d.setString(1, player.toString());
                d.executeUpdate();
            }
            return found;
        }), IO);
    }

    public CompletableFuture<Profile> profile(UUID player) {
        return CompletableFuture.supplyAsync(() -> call(c -> {
            String id = player.toString();
            boolean visited;
            boolean die;
            int offers = 0;
            long since = Long.MAX_VALUE;
            try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM arcade_entries WHERE uuid = ?")) {
                p.setString(1, id);
                try (ResultSet r = p.executeQuery()) {
                    visited = r.next();
                }
            }
            try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM arcade_dice WHERE uuid = ?")) {
                p.setString(1, id);
                try (ResultSet r = p.executeQuery()) {
                    die = r.next();
                }
            }
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT offers, TIMESTAMPDIFF(SECOND, last_offer, CURRENT_TIMESTAMP(3)) FROM arcade_discovery WHERE uuid = ?")) {
                p.setString(1, id);
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) {
                        offers = r.getInt(1);
                        since = r.getLong(2);
                    }
                }
            }
            return new Profile(visited, die, offers, since);
        }), IO);
    }

    public CompletableFuture<Void> markDieGiven(UUID player) {
        return CompletableFuture.runAsync(() -> run(c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT IGNORE INTO arcade_dice (uuid) VALUES (?)")) {
                p.setString(1, player.toString());
                p.executeUpdate();
            }
        }), IO);
    }

    public CompletableFuture<Void> recordDiscoveryOffer(UUID player) {
        return CompletableFuture.runAsync(() -> run(c -> {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO arcade_discovery (uuid, offers) VALUES (?, 1)"
                    + " ON DUPLICATE KEY UPDATE offers = offers + 1, last_offer = CURRENT_TIMESTAMP(3)")) {
                p.setString(1, player.toString());
                p.executeUpdate();
            }
        }), IO);
    }

    @FunctionalInterface
    private interface Work {
        void apply(Connection c) throws SQLException;
    }

    @FunctionalInterface
    private interface Query<T> {
        T apply(Connection c) throws SQLException;
    }

    private void run(Work work) {
        call(c -> {
            work.apply(c);
            return null;
        });
    }

    private synchronized <T> T call(Query<T> query) {
        try {
            return query.apply(connection());
        } catch (SQLException failure) {
            connection = null;
            throw new IllegalStateException(failure);
        }
    }
}
