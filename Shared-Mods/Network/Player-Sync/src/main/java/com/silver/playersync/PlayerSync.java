package com.silver.playersync;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What other mods use from Player Sync. Mods that build against it call this directly; mods that don't call it by
 * name ({@code Class.forName("com.silver.playersync.PlayerSync")}). Every method that talks to the database must be
 * called off the server thread.
 */
public final class PlayerSync {
    private PlayerSync() {
    }

    /** Which story branches a player has finished; this opens Magitech and decides their faction there. */
    public record Completions(boolean magic, boolean tech) {
        public static final Completions NONE = new Completions(false, false);
    }

    /** A request for a branch server (magic, tech, magitech) to erase what it holds about a player. */
    public record BranchWipe(long id, String uuid, String name, String branch) {
    }

    /** The elements a player may choose (bits), their unspent skill points and their bought skill-tree nodes. */
    public record ElementProgress(int unlocks, int skillPoints, String skillTree) {
        public static final int AIR = 1;
        public static final int WATER = 2;
        public static final int EARTH = 4;
        public static final int FIRE = 8;

        public boolean unlocked(int element) {
            return (unlocks & element) != 0;
        }

        /** The bit of "air", "water", "earth" or "fire"; 0 for anything else. */
        public static int bit(String element) {
            return switch (element.toLowerCase(Locale.ROOT)) {
                case "air" -> AIR;
                case "water" -> WATER;
                case "earth" -> EARTH;
                case "fire" -> FIRE;
                default -> 0;
            };
        }
    }

    // ---- bosses and soulbound slots

    /**
     * A boss's reward, once per player: marks the boss defeated and adds {@code slots} soulbound slots. True when given
     * now, false when they already had it. {@code boss}: skyisland, desert, ocean or cave.
     */
    public static boolean grantBossReward(String name, String uuid, String boss, int slots) throws SQLException {
        return ProgressStore.grantBossReward(name, uuid, boss, slots);
    }

    /** Tells the player (if online) what a boss reward means. Any thread. Call once, after a grant returned true. */
    public static void announceBossReward(MinecraftServer server, UUID player, String boss) {
        String domain = switch (boss.toLowerCase(Locale.ROOT).replace("defeated", "")) {
            case "skyisland" -> "sky";
            case "ocean" -> "sea";
            case "desert" -> "sand";
            case "cave" -> "deep";
            default -> null;
        };
        if (server == null || domain == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer online = server.getPlayerList().getPlayer(player);
            if (online != null) {
                online.sendSystemMessage(Component.literal("The " + domain + " remembers you.").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
                online.sendSystemMessage(Component.literal("You can bind one more item to your soul.").withStyle(ChatFormatting.GRAY));
            }
        });
    }

    public static boolean bossDefeated(String name, String uuid, String boss) throws SQLException {
        return ProgressStore.defeated(name, uuid, boss);
    }

    public static int soulboundSlots(String name, String uuid) throws SQLException {
        return ProgressStore.soulboundSlots(name, uuid);
    }

    public static void addSoulboundSlots(String name, String uuid, int delta) throws SQLException {
        ProgressStore.addSoulboundSlots(name, uuid, delta);
    }

    // ---- the stories

    public static Completions completions(String uuid) throws SQLException {
        return ProgressStore.completions(uuid);
    }

    public static void completeBranch(String name, String uuid, String branch) throws SQLException {
        ProgressStore.completeBranch(name, uuid, branch);
    }

    /** Moves the player from one exclusive branch (magic, tech) to the other; the old branch's server wipes itself. */
    public static void switchBranch(String name, String uuid, String from, String to) throws SQLException {
        ProgressStore.switchBranch(name, uuid, from, to);
    }

    public static List<BranchWipe> pendingWipes(String branch) throws SQLException {
        return ProgressStore.pendingWipes(branch);
    }

    public static void markWiped(long id) throws SQLException {
        ProgressStore.markWiped(id);
    }

    public static void setStage1Cleared(String name, String uuid, boolean cleared) throws SQLException {
        ProgressStore.setStage1Cleared(name, uuid, cleared);
    }

    // ---- elements and skill points

    public static ElementProgress elements(String name, String uuid) throws SQLException {
        return ProgressStore.elements(name, uuid);
    }

    public static boolean unlockElement(String name, String uuid, int element) throws SQLException {
        return ProgressStore.unlockElement(name, uuid, element);
    }

    public static void addSkillPoints(String name, String uuid, int delta) throws SQLException {
        ProgressStore.addSkillPoints(name, uuid, delta);
    }

    public static boolean spendSkillPoints(String name, String uuid, int cost, String expectedTree, String newTree) throws SQLException {
        return ProgressStore.spendSkillPoints(name, uuid, cost, expectedTree, newTree);
    }

    // ---- stored items

    /** Removes items with custom data {@code key} = {@code value} from the player's stored (between-server) items; returns how many. */
    public static int removeStoredItems(String uuid, String key, String value) throws SQLException {
        return StoredItems.removeStored(uuid, key, value);
    }
}
