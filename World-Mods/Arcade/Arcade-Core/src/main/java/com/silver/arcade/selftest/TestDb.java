package com.silver.arcade.selftest;

import com.silver.authorization.NetworkShared;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The self-test's own database access (the arcade_* tables): to look at the rows the product wrote and to remove the rows
 * the test created. Rows are only ever removed by the UUID of a test bot.
 */
public final class TestDb implements AutoCloseable {
    public static final List<String> TABLES = List.of("arcade_entries", "arcade_prizes", "arcade_dice", "arcade_discovery", "arcade_suspended", "arcade_prize_log");
    private final Connection connection;

    public TestDb() throws SQLException {
        NetworkShared.DatabaseLogin login = NetworkShared.databaseLogin()
            .orElseThrow(() -> new SQLException("no database login in ~/.config/mc-network/database.env"));
        connection = DriverManager.getConnection("jdbc:mysql://" + login.hostAndPort() + "/" + login.database()
            + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000", login.user(), login.password());
    }

    public int count(String table, UUID id) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE uuid = ?")) {
            p.setString(1, id.toString());
            try (ResultSet r = p.executeQuery()) {
                r.next();
                return r.getInt(1);
            }
        }
    }

    public int countAll(Collection<UUID> ids) throws SQLException {
        int total = 0;
        for (UUID id : ids) {
            for (String table : TABLES) {
                total += count(table, id);
            }
        }
        return total;
    }

    /** (server, tier) of the prizes waiting for a bot, oldest first. */
    public List<String> prizes(UUID id) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement p = connection.prepareStatement("SELECT server, tier FROM arcade_prizes WHERE uuid = ? ORDER BY id")) {
            p.setString(1, id.toString());
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    out.add(r.getString(1) + ":" + r.getInt(2));
                }
            }
        }
        return out;
    }

    public void insert(String sql, Object... args) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                p.setObject(i + 1, args[i]);
            }
            p.executeUpdate();
        }
    }

    /**
     * Rows about the given test bots in every other table of the network database (other mods record joins too: player
     * data sync, auth, statistics). Returns "table.column" -> row count. Matching is by the bot's UUID only.
     */
    public java.util.Map<String, Integer> foreign(Collection<UUID> ids, boolean delete) throws SQLException {
        java.util.Map<String, Integer> found = new java.util.LinkedHashMap<>();
        List<String[]> columns = new ArrayList<>();
        try (PreparedStatement p = connection.prepareStatement("SELECT table_name, column_name, data_type, character_maximum_length FROM information_schema.columns"
                + " WHERE table_schema = DATABASE() AND column_name LIKE '%uuid%' AND column_name NOT LIKE '%entity%' AND table_name NOT IN"
                + " ('arcade_entries','arcade_prizes','arcade_dice','arcade_discovery','arcade_suspended')");
                ResultSet r = p.executeQuery()) {
            while (r.next()) {
                columns.add(new String[] {r.getString(1), r.getString(2), r.getString(3), r.getString(4)});
            }
        }
        for (String[] col : columns) {
            boolean binary = col[2].contains("binary");
            for (UUID id : ids) {
                String sql = (delete ? "DELETE FROM `" : "SELECT COUNT(*) FROM `") + col[0] + "` WHERE `" + col[1] + "` = ?";
                if (delete) {
                    sql = "DELETE FROM `" + col[0] + "` WHERE `" + col[1] + "` = ?";
                }
                try (PreparedStatement p = connection.prepareStatement(sql)) {
                    if (binary) {
                        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(16);
                        b.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
                        p.setBytes(1, b.array());
                    } else {
                        p.setString(1, id.toString());
                    }
                    int n;
                    if (delete) {
                        n = p.executeUpdate();
                    } else {
                        try (ResultSet r = p.executeQuery()) {
                            r.next();
                            n = r.getInt(1);
                        }
                    }
                    if (n > 0) {
                        found.merge(col[0] + "." + col[1], n, Integer::sum);
                    }
                } catch (SQLException skip) {
                    // a column of a type that cannot be compared with a uuid string: not about our bots
                }
            }
        }
        return found;
    }

    /** Removes every row of the given test bots from the arcade tables; returns the number of rows removed. */
    public int deleteFor(Collection<UUID> ids) throws SQLException {
        int removed = 0;
        for (UUID id : ids) {
            for (String table : TABLES) {
                try (PreparedStatement p = connection.prepareStatement("DELETE FROM " + table + " WHERE uuid = ?")) {
                    p.setString(1, id.toString());
                    removed += p.executeUpdate();
                }
            }
        }
        return removed;
    }

    /** The product pays less for repeated cash-outs (arcade_prize_log); a test that expects a full prize starts from an empty log. */
    public void clearPrizeLog(Collection<UUID> ids) throws SQLException {
        for (UUID id : ids) {
            try (PreparedStatement p = connection.prepareStatement("DELETE FROM arcade_prize_log WHERE uuid = ?")) {
                p.setString(1, id.toString());
                p.executeUpdate();
            }
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
