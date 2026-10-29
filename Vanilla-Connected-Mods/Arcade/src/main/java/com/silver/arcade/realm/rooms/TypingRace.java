package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Type the line exactly as shown, fast. Three lines per room; each line must reach the tier's accuracy and speed.
 * Speed asked: 20 words per minute at tier 1, +4 per tier (up to 110, fast but human); accuracy 85% rising to 97%.
 */
public final class TypingRace extends BaseRoom {
    private static final int LINES = 3;
    private final Random random = new Random();
    private final Map<UUID, Integer> done = new HashMap<>();
    private final Map<UUID, String> line = new HashMap<>();
    private final Map<UUID, Long> shownAt = new HashMap<>();
    private long now;

    public String id() { return "typing"; }
    public String title() { return "Typing Race"; }
    public String explain() {
        return "Words appear in your chat. Type them back exactly, quick as you can, and press enter. Three lines; miss the speed "
            + "or make too many mistakes and you're out.";
    }

    int words() { return Math.min(40, 4 + tier); }
    double wpm() { return Math.min(110, 20 + 4 * (tier - 1)); }
    double accuracyNeeded() { return Math.min(0.97, 0.85 + 0.01 * tier); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(LINES * (words() * 60.0 / wpm() + 6) + 10);
    }

    @Override
    protected void build() {
        arena.fill(-4, 0, -4, 4, 0, 4, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        arena.set(0, 1, 4, Blocks.LECTERN.defaultBlockState());
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.LibraryLook.build(arena, tier));
        int i = 0;
        for (ServerPlayer player : players.values()) {
            arena.put(player, -2.5 + (i++ % 4) * 1.5, 1, 0.5, 0F);
            next(player);
        }
    }

    private void next(ServerPlayer player) {
        String text = Words.line(random, words(), tier);
        line.put(player.getUUID(), text);
        shownAt.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal(">> ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal(text).withStyle(ChatFormatting.GOLD)));
    }

    @Override
    public void tick(long tickInRoom) {
        now = tickInRoom;
    }

    @Override
    public boolean chat(ServerPlayer player, String message) {
        UUID id = player.getUUID();
        if (!players.containsKey(id) || decided(id)) {
            return false;
        }
        String expected = line.get(id);
        double seconds = Math.max(0.05, (now - shownAt.get(id)) / 20.0);
        double speed = expected.split(" ").length / (seconds / 60.0);
        double accuracy = Words.accuracy(expected, message.trim());
        if (accuracy < accuracyNeeded() || speed < wpm()) {
            Gamemaster.say(player, String.format("%.0f wpm at %.0f%%. Not enough: %.0f wpm and %.0f%% were needed.",
                speed, accuracy * 100, wpm(), accuracyNeeded() * 100));
            fail(player);
            return true;
        }
        int count = done.merge(id, 1, Integer::sum);
        Gamemaster.bar(player, String.format("%.0f wpm, %.0f%%  (%d/%d)", speed, accuracy * 100, count, LINES));
        if (count >= LINES) {
            clear(player);
        } else {
            next(player);
        }
        return true;
    }
}
