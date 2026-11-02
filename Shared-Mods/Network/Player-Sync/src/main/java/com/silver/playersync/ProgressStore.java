package com.silver.playersync;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A player's network-wide progress, kept in their row: bosses defeated, how many items they may bind to their soul,
 * the Magic and Tech stories, and their bending elements and skill points. Talks to the database: call it off the
 * server thread.
 */
final class ProgressStore {
    private static final String T = SyncStore.TABLE;

    private ProgressStore() {
    }

    /** The column of a boss's "defeated" flag; accepts "cave", "CaveDefeated", ... Unknown: IllegalArgumentException. */
    static String bossColumn(String boss) {
        return switch (boss.toLowerCase(Locale.ROOT).replace("defeated", "")) {
            case "skyisland" -> "SkyIslandDefeated";
            case "desert" -> "DesertDefeated";
            case "ocean" -> "OceanDefeated";
            case "cave" -> "CaveDefeated";
            default -> throw new IllegalArgumentException("Unknown boss: " + boss + " (skyisland, desert, ocean, cave)");
        };
    }

    static boolean defeated(String name, String uuid, String boss) throws SQLException {
        String column = bossColumn(boss);
        SyncStore.ensureRow(name, uuid);
        return SyncStore.query("SELECT IFNULL(" + column + ", false) FROM " + T + " WHERE uuid = ?", s -> s.setString(1, uuid),
            rows -> rows.next() && rows.getBoolean(1));
    }

    static void setDefeated(String name, String uuid, String boss, boolean value) throws SQLException {
        String column = bossColumn(boss);
        SyncStore.ensureRow(name, uuid);
        SyncStore.update("UPDATE " + T + " SET " + column + " = ? WHERE uuid = ?", s -> {
            s.setBoolean(1, value);
            s.setString(2, uuid);
        });
    }

    /** A boss's reward, once per player: marks it defeated and adds {@code slots} soulbound slots. False when already given. */
    static boolean grantBossReward(String name, String uuid, String boss, int slots) throws SQLException {
        String column = bossColumn(boss);
        SyncStore.ensureRow(name, uuid);
        return SyncStore.update("UPDATE " + T + " SET SoulboundMax = GREATEST(0, IFNULL(SoulboundMax, 0) + ?), " + column
            + " = TRUE WHERE uuid = ? AND IFNULL(" + column + ", FALSE) = FALSE", s -> {
                s.setInt(1, slots);
                s.setString(2, uuid);
            }) > 0;
    }

    static int soulboundSlots(String name, String uuid) throws SQLException {
        SyncStore.ensureRow(name, uuid);
        return SyncStore.query("SELECT IFNULL(SoulboundMax, 0) FROM " + T + " WHERE uuid = ?", s -> s.setString(1, uuid),
            rows -> rows.next() ? rows.getInt(1) : 0);
    }

    static void addSoulboundSlots(String name, String uuid, int delta) throws SQLException {
        SyncStore.ensureRow(name, uuid);
        SyncStore.update("UPDATE " + T + " SET SoulboundMax = GREATEST(0, IFNULL(SoulboundMax, 0) + ?) WHERE uuid = ?", s -> {
            s.setInt(1, delta);
            s.setString(2, uuid);
        });
    }

    static void setStage1Cleared(String name, String uuid, boolean value) throws SQLException {
        SyncStore.ensureRow(name, uuid);
        SyncStore.update("UPDATE " + T + " SET Stage1Cleared = ? WHERE uuid = ?", s -> {
            s.setBoolean(1, value);
            s.setString(2, uuid);
        });
    }

    // ---- the Magic and Tech stories

    static PlayerSync.Completions completions(String uuid) throws SQLException {
        return SyncStore.query("SELECT IFNULL(MagicCompleted, FALSE), IFNULL(TechCompleted, FALSE) FROM " + T + " WHERE uuid = ?",
            s -> s.setString(1, uuid), rows -> rows.next() ? new PlayerSync.Completions(rows.getBoolean(1), rows.getBoolean(2))
                : PlayerSync.Completions.NONE);
    }

    private static String completedColumn(String branch) {
        return switch (branch) {
            case "magic" -> "MagicCompleted";
            case "tech" -> "TechCompleted";
            default -> throw new IllegalArgumentException("Unknown branch: " + branch + " (magic, tech)");
        };
    }

    static void completeBranch(String name, String uuid, String branch) throws SQLException {
        String column = completedColumn(branch);
        SyncStore.ensureRow(name, uuid);
        SyncStore.update("UPDATE " + T + " SET " + column + " = TRUE WHERE uuid = ?", s -> s.setString(1, uuid));
    }

    /**
     * Moves a player from one exclusive branch to the other, in one transaction: the proxy now lets them into
     * {@code to} and keeps them out of {@code from}; {@code from}'s server and Magitech are asked to wipe what they hold
     * about them; their skill tree, family inventory and {@code from}'s completion are cleared.
     */
    static void switchBranch(String name, String uuid, String from, String to) throws SQLException {
        SyncStore.ensureRow(name, uuid);
        String finished = "magic".equals(from) || "tech".equals(from) ? completedColumn(from) + " = FALSE, " : "";
        SyncStore.transaction(c -> {
            SyncStore.execute(c, "DELETE FROM routing_player_visits WHERE player_uuid = ? AND server_name = ?", s -> {
                s.setString(1, uuid);
                s.setString(2, from);
            });
            SyncStore.execute(c, "INSERT INTO routing_player_visits (player_uuid, server_name) VALUES (?, ?)"
                + " ON DUPLICATE KEY UPDATE last_visited_at = CURRENT_TIMESTAMP(3)", s -> {
                    s.setString(1, uuid);
                    s.setString(2, to);
                });
            for (String branch : new String[] {from, "magitech"}) {
                SyncStore.execute(c, "INSERT INTO branch_wipe (uuid, name, branch) VALUES (?, ?, ?)", s -> {
                    s.setString(1, uuid);
                    s.setString(2, name);
                    s.setString(3, branch);
                });
            }
            SyncStore.execute(c, "UPDATE " + T + " SET " + finished + "ElementUnlocks = 0, SkillPoints = 0, SkillTree = '',"
                + " fmain = '', foff = '', farmor = '', fender = '' WHERE uuid = ?", s -> s.setString(1, uuid));
            return null;
        });
    }

    static List<PlayerSync.BranchWipe> pendingWipes(String branch) throws SQLException {
        return SyncStore.query("SELECT id, uuid, IFNULL(name, '') FROM branch_wipe WHERE branch = ? AND done_at IS NULL ORDER BY id",
            s -> s.setString(1, branch), rows -> {
                List<PlayerSync.BranchWipe> wipes = new ArrayList<>();
                while (rows.next()) {
                    wipes.add(new PlayerSync.BranchWipe(rows.getLong(1), rows.getString(2), rows.getString(3), branch));
                }
                return wipes;
            });
    }

    static void markWiped(long id) throws SQLException {
        SyncStore.update("UPDATE branch_wipe SET done_at = CURRENT_TIMESTAMP(3) WHERE id = ?", s -> s.setLong(1, id));
    }

    // ---- bending elements and skill points

    static PlayerSync.ElementProgress elements(String name, String uuid) throws SQLException {
        SyncStore.ensureRow(name, uuid);
        return SyncStore.query("SELECT IFNULL(ElementUnlocks, 0), IFNULL(SkillPoints, 0), IFNULL(SkillTree, '') FROM " + T
            + " WHERE uuid = ?", s -> s.setString(1, uuid), rows -> rows.next()
                ? new PlayerSync.ElementProgress(rows.getInt(1), rows.getInt(2), rows.getString(3))
                : new PlayerSync.ElementProgress(0, 0, ""));
    }

    /** True only when the element was not unlocked yet. */
    static boolean unlockElement(String name, String uuid, int element) throws SQLException {
        SyncStore.ensureRow(name, uuid);
        return SyncStore.update("UPDATE " + T + " SET ElementUnlocks = IFNULL(ElementUnlocks, 0) | ? WHERE uuid = ?"
            + " AND (IFNULL(ElementUnlocks, 0) & ?) = 0", s -> {
                s.setInt(1, element);
                s.setString(2, uuid);
                s.setInt(3, element);
            }) > 0;
    }

    /** Adds (or, negative, removes) skill points; never below zero. */
    static void addSkillPoints(String name, String uuid, int delta) throws SQLException {
        SyncStore.ensureRow(name, uuid);
        SyncStore.update("UPDATE " + T + " SET SkillPoints = GREATEST(0, IFNULL(SkillPoints, 0) + ?) WHERE uuid = ?", s -> {
            s.setInt(1, delta);
            s.setString(2, uuid);
        });
    }

    /**
     * Spends points and replaces the bought-node list in one step, only if the player can afford it and the tree is
     * still {@code expectedTree} (so two servers can't both spend the same points).
     */
    static boolean spendSkillPoints(String name, String uuid, int cost, String expectedTree, String newTree) throws SQLException {
        SyncStore.ensureRow(name, uuid);
        return SyncStore.update("UPDATE " + T + " SET SkillPoints = SkillPoints - ?, SkillTree = ? WHERE uuid = ?"
            + " AND IFNULL(SkillPoints, 0) >= ? AND IFNULL(SkillTree, '') = ?", s -> {
                s.setInt(1, cost);
                s.setString(2, newTree);
                s.setString(3, uuid);
                s.setInt(4, cost);
                s.setString(5, expectedTree);
            }) > 0;
    }
}
