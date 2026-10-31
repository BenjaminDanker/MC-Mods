package com.silver.playersync;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Story and boss rewards tied to advancements: a magic chapter's skill points, finishing the Magic or Tech story, the
 * desert and cave bosses. Completing one records the reward. Advancements not in the table cost one map lookup.
 * The database work runs on a worker; a boss's "rewarded" tag is added on the server thread afterwards, a story's
 * completion tag at once (the gates read it).
 */
final class AdvancementRewards {
    /** Player tags marking progress (other mods read these). */
    static final String TAG_PREFIX = "progress_";

    private sealed interface Reward permits SkillPoints, Branch, Boss {
    }

    private record SkillPoints(int points) implements Reward {
    }

    private record Branch(String branch, int points) implements Reward {
        String tag() {
            return TAG_PREFIX + branch + "_completed";
        }
    }

    /** {@code tag} marks the reward as given, so repeats skip the database. */
    private record Boss(String boss, String tag) implements Reward {
    }

    private static final Map<Identifier, Reward> REWARDS = Map.of(
        Identifier.parse("magicstory:temple"), new SkillPoints(6),
        Identifier.parse("magicstory:fire"), new SkillPoints(9),
        Identifier.parse("magicstory:tower"), new SkillPoints(6),
        Identifier.parse("magicstory:wizard"), new SkillPoints(6),
        Identifier.parse("magicstory:void"), new SkillPoints(9),
        Identifier.parse("magicstory:convergence"), new Branch("magic", 12),
        Identifier.parse("techstory:foundry_lit"), new Branch("tech", 0),
        // revoked and earned again on every legendary kill; the tag makes the repeats free
        Identifier.parse("infinity_cave:kill/legendary"), new Boss("cave", TAG_PREFIX + "cave_defeated"),
        Identifier.parse("petroglyph-guide:pharao_boss"), new Boss("desert", TAG_PREFIX + "desert_defeated"));

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Player-Sync-Rewards");
        thread.setDaemon(true);
        return thread;
    });

    private AdvancementRewards() {
    }

    /** Cheap check, called on every advancement award. */
    static boolean rewarded(Identifier advancement) {
        return REWARDS.containsKey(advancement);
    }

    /** Server thread: the player has just completed {@code advancement}. */
    static void completed(ServerPlayer player, Identifier advancement) {
        Reward reward = REWARDS.get(advancement);
        if (reward == null || (reward instanceof Boss boss && player.entityTags().contains(boss.tag()))) {
            return;
        }
        if (reward instanceof Branch branch) {
            player.addTag(branch.tag());
        }
        MinecraftServer server = player.level().getServer();
        String name = player.getName().getString();
        String uuid = player.getStringUUID();
        UUID id = player.getUUID();
        WORKER.execute(() -> {
            try {
                switch (reward) {
                    case SkillPoints points -> ProgressStore.addSkillPoints(name, uuid, points.points());
                    case Branch branch -> {
                        if (branch.points() > 0) {
                            ProgressStore.addSkillPoints(name, uuid, branch.points());
                        }
                        ProgressStore.completeBranch(name, uuid, branch.branch());
                    }
                    case Boss boss -> {
                        boolean given = ProgressStore.grantBossReward(name, uuid, boss.boss(), 1);
                        server.execute(() -> {
                            ServerPlayer online = server.getPlayerList().getPlayer(id);
                            if (online != null) {
                                online.addTag(boss.tag());
                            }
                        });
                        if (given) {
                            PlayerSync.announceBossReward(server, id, boss.boss());
                        }
                    }
                }
                PlayerSyncMod.LOGGER.info("Recorded the {} reward for {}", advancement, name);
            } catch (Exception failure) {
                PlayerSyncMod.LOGGER.error("ALERT: could not record the {} reward for {}", advancement, name, failure);
            }
        });
    }
}
