package com.silver.playersync;

import com.silver.authorization.NetworkShared;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

/**
 * Advancements shared between servers, one record per player, criterion and advancement (table player_advancements).
 *
 * <p>What is shared (network values, see {@link #configure}): every advancement on the survival servers
 * ({@code player_sync.advancement_servers}), except recipe-book unlocks ({@code recipes/...}), which only the vanilla
 * family shares ({@code player_sync.recipe_servers}) and only for Minecraft's own recipes. A server applies only the
 * advancements it defines, so a datapack's advancements show wherever that pack is installed; unknown ones are kept.
 *
 * <p>Earning or revoking a criterion writes it at once. On join the two sides are merged, criterion by criterion: what
 * the record has is granted here; a criterion revoked in the record after it was earned here is revoked here; what only
 * this server has goes into the record (that also fills it the first time). Applying never runs the advancement's
 * rewards or announces it (it was announced where it was earned), and the player's advancement screen is resent in full
 * so no toasts pop. A branch switch marks the left branch's own advancements revoked ({@code player_sync.branch_advancements.<branch>}).
 */
public final class SharedAdvancements {
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Player-Sync-Advancements");
        thread.setDaemon(true);
        return thread;
    });

    private static boolean shares;
    private static boolean sharesRecipes;

    private SharedAdvancements() {
    }

    static void configure(String server) {
        shares = list("player_sync.advancement_servers", "vanilla,cave,desert,ocean,sky-island,magic,tech,magitech").contains(server);
        sharesRecipes = list("player_sync.recipe_servers", "vanilla,cave,desert,ocean,sky-island").contains(server);
        PlayerSyncMod.LOGGER.info("Shared advancements on {}: {}{}", server, shares ? "on" : "off",
            shares ? sharesRecipes ? " (with vanilla recipes)" : " (recipes stay here)" : "");
    }

    private static List<String> list(String key, String fallback) {
        return Arrays.stream(NetworkShared.get(key, fallback).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** The advancement namespaces that belong to a branch (cleared when a player leaves it). */
    static List<String> branchNamespaces(String branch) {
        return list("player_sync.branch_advancements." + branch, switch (branch) {
            case "magic" -> "magicstory,skilltree";
            case "tech" -> "techstory";
            default -> "";
        });
    }

    static boolean inScope(Identifier id) {
        if (!shares) {
            return false;
        }
        if (id.getPath().startsWith("recipes/")) {
            return sharesRecipes && id.getNamespace().equals("minecraft");
        }
        return true;
    }

    // ---------------------------------------------------------------- writes as they happen (server thread -> writer)

    public static void earned(ServerPlayer player, AdvancementHolder advancement, String criterion) {
        if (!inScope(advancement.id())) {
            return;
        }
        CriterionProgress progress = player.getAdvancements().getOrStartProgress(advancement).getCriterion(criterion);
        long at = progress != null && progress.getObtained() != null ? progress.getObtained().toEpochMilli() : System.currentTimeMillis();
        write(player.getStringUUID(), List.of(new Row(advancement.id().toString(), criterion, at, false)));
    }

    public static void revoked(ServerPlayer player, AdvancementHolder advancement, String criterion) {
        if (inScope(advancement.id())) {
            write(player.getStringUUID(), List.of(new Row(advancement.id().toString(), criterion, System.currentTimeMillis(), true)));
        }
    }

    private static void write(String uuid, List<Row> rows) {
        if (rows.isEmpty()) {
            return;
        }
        WRITER.execute(() -> {
            try {
                SyncStore.transaction(c -> {
                    for (Row row : rows) {
                        SyncStore.execute(c, "INSERT INTO player_advancements (uuid, advancement, criterion, obtained, revoked)"
                            + " VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE obtained = VALUES(obtained), revoked = VALUES(revoked)", s -> {
                                s.setString(1, uuid);
                                s.setString(2, row.advancement);
                                s.setString(3, row.criterion);
                                s.setLong(4, row.at);
                                s.setBoolean(5, row.revoked);
                            });
                    }
                    return null;
                });
            } catch (SQLException failure) {
                // Not lost: the criterion stays in this server's file and goes up at the player's next join here.
                PlayerSyncMod.LOGGER.warn("Could not save {} shared advancement change(s) for {}: {}", rows.size(), uuid, failure.getMessage());
            }
        });
    }

    /** A branch switch: the left branch's own advancements are revoked everywhere (their servers wipe the files too). */
    static void revokeBranch(java.sql.Connection c, String uuid, String branch) throws SQLException {
        for (String namespace : branchNamespaces(branch)) {
            SyncStore.execute(c, "UPDATE player_advancements SET revoked = TRUE, obtained = ? WHERE uuid = ? AND advancement LIKE ?", s -> {
                s.setLong(1, System.currentTimeMillis());
                s.setString(2, uuid);
                s.setString(3, namespace + ":%");
            });
        }
    }

    // ---------------------------------------------------------------- the merge on join

    /** Called on join: reads the record off the server thread, then merges on it. */
    static void joined(MinecraftServer server, ServerPlayer player) {
        if (!shares) {
            return;
        }
        String uuid = player.getStringUUID();
        WRITER.execute(() -> {
            Map<String, Map<String, Row>> record;
            try {
                SyncStore.awaitDatabase();
                record = SyncStore.query("SELECT advancement, criterion, obtained, revoked FROM player_advancements WHERE uuid = ?",
                    s -> s.setString(1, uuid), rows -> {
                        Map<String, Map<String, Row>> out = new HashMap<>();
                        while (rows.next()) {
                            Row row = new Row(rows.getString(1), rows.getString(2), rows.getLong(3), rows.getBoolean(4));
                            out.computeIfAbsent(row.advancement, k -> new HashMap<>()).put(row.criterion, row);
                        }
                        return out;
                    });
            } catch (SQLException | InterruptedException failure) {
                PlayerSyncMod.LOGGER.warn("Shared advancements for {} not loaded: {}", player.getName().getString(), failure.toString());
                return;
            }
            server.execute(() -> merge(server, player, record));
        });
    }

    private static void merge(MinecraftServer server, ServerPlayer player, Map<String, Map<String, Row>> record) {
        if (player.hasDisconnected()) {
            return;
        }
        PlayerAdvancements mine = player.getAdvancements();
        List<Row> upload = new ArrayList<>();
        int granted = 0;
        int revoked = 0;
        int recipes = 0;
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            if (!inScope(holder.id())) {
                continue;
            }
            String id = holder.id().toString();
            Map<String, Row> theirs = record.getOrDefault(id, Map.of());
            Set<String> criteria = holder.value().criteria().keySet();
            AdvancementProgress progress = mine.getOrStartProgress(holder);
            for (String criterion : criteria) {
                CriterionProgress here = progress.getCriterion(criterion);
                if (here == null) {
                    continue;
                }
                Row there = theirs.get(criterion);
                long hereAt = here.isDone() && here.getObtained() != null ? here.getObtained().toEpochMilli() : -1;
                if (there == null) {
                    if (hereAt >= 0) {
                        upload.add(new Row(id, criterion, hereAt, false));
                    }
                } else if (!there.revoked) {
                    if (hereAt < 0 && progress.grantProgress(criterion)) {
                        granted++;
                        if (progress.isDone() && holder.id().getPath().startsWith("recipes/")) {
                            // A recipe unlock's point is its recipe: put it in the book quietly (no "new recipe" toast).
                            for (var recipe : holder.value().rewards().recipes()) {
                                if (!player.getRecipeBook().contains(recipe)) {
                                    player.getRecipeBook().add(recipe);
                                    recipes++;
                                }
                            }
                        }
                    }
                } else if (hereAt >= 0) {
                    if (hereAt > there.at) {
                        upload.add(new Row(id, criterion, hereAt, false)); // earned here again after it was revoked
                    } else if (progress.revokeProgress(criterion)) {
                        revoked++;
                    }
                }
            }
        }
        write(player.getStringUUID(), upload);
        if (granted + revoked > 0) {
            // Save what changed and load it again: the player gets their whole advancement screen afresh (no toasts, no
            // rewards), the same way they got it when they joined.
            mine.save();
            mine.reload(server.getAdvancements());
            if (recipes > 0) {
                player.getRecipeBook().sendInitialRecipeBook(player); // the whole book again, without notifications
            }
            PlayerSyncMod.LOGGER.info("Shared advancements for {}: {} criteria granted ({} recipes), {} revoked, {} uploaded",
                player.getName().getString(), granted, recipes, revoked, upload.size());
        } else if (!upload.isEmpty()) {
            PlayerSyncMod.LOGGER.info("Shared advancements for {}: {} criteria uploaded", player.getName().getString(), upload.size());
        }
    }

    private record Row(String advancement, String criterion, long at, boolean revoked) {
    }
}
