package com.silver.arcade.realm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

/** Rooms that need it hide name tags (they show through walls) by putting their players on a team with tags off. */
final class NameTags {
    private static final String TEAM = "arcade_no_tags";

    private NameTags() {
    }

    static void set(ServerPlayer player, boolean hidden) {
        Scoreboard board = player.level().getServer().getScoreboard();
        String name = player.getScoreboardName();
        if (!hidden) {
            PlayerTeam team = board.getPlayersTeam(name);
            if (team != null && TEAM.equals(team.getName())) {
                board.removePlayerFromTeam(name, team);
            }
            return;
        }
        PlayerTeam team = board.getPlayerTeam(TEAM);
        if (team == null) {
            team = board.addPlayerTeam(TEAM);
            team.setNameTagVisibility(Team.Visibility.NEVER);
        }
        board.addPlayerToTeam(name, team);
    }
}
