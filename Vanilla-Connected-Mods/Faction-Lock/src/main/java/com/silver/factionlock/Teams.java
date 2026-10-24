package com.silver.factionlock;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Scoreboard teams so each faction's names are coloured: Magic aqua, Tech gold. */
final class Teams {
    private Teams() {
    }

    static void assign(MinecraftServer server, ServerPlayer player, Faction faction) {
        if (faction == Faction.NONE) {
            return;
        }
        Scoreboard board = server.getScoreboard();
        String teamName = "faction_" + faction.id();
        PlayerTeam team = board.getPlayerTeam(teamName);
        if (team == null) {
            team = board.addPlayerTeam(teamName);
            team.setColor(java.util.Optional.of(faction == Faction.MAGIC ? net.minecraft.world.scores.TeamColor.AQUA : net.minecraft.world.scores.TeamColor.GOLD));
        }
        board.addPlayerToTeam(player.getScoreboardName(), team);
    }
}
