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

/** Mental arithmetic against the clock: answer in chat. More problems, bigger numbers, more operations per tier. */
public final class MathSprint extends BaseRoom {
    private final Random random = new Random();
    private final Map<UUID, Integer> done = new HashMap<>();
    private final Map<UUID, Long> answer = new HashMap<>();

    public String id() { return "math"; }
    public String title() { return "Mental Math"; }
    public String explain() {
        return "Sums appear in your chat. Type the answer and press enter. One wrong answer and you're out. Think fast.";
    }

    int problems() { return Math.min(15, 4 + tier / 2); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(problems() * Math.max(6.0, 12.0 - tier * 0.3) + 5);
    }

    @Override
    protected void build() {
        // The observatory floor: a star chart in night-blue deepslate, a copper rim with gold quarter marks, a gold
        // meridian cross and a lapis star at the centre.
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                int m = Math.max(Math.abs(x), Math.abs(z));
                String b;
                if (m == 4) {
                    b = (x == 0 || z == 0) ? "gold_block" : "waxed_cut_copper";
                } else if (x == 0 && z == 0) {
                    b = "lapis_block";
                } else if (x == 0 || z == 0) {
                    b = "gold_block";
                } else {
                    b = Math.floorMod(x * 5 + z * 3, 7) == 0 ? "calcite" : "polished_deepslate";
                }
                arena.set(x, 0, z, st(b));
            }
        }
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.ObservatoryLook.build(arena, tier));
        int i = 0;
        for (ServerPlayer player : players.values()) {
            arena.put(player, -2.5 + (i++ % 4) * 1.5, 1, 0.5, 0F);
            next(player);
        }
    }

    /** A problem for this tier: + and - early, x from tier 3, three terms from tier 6, bigger numbers all along. */
    String[] problem() {
        int max = 10 + Math.min(tier, 20) * 6;
        long a = 1 + random.nextInt(max);
        long b = 1 + random.nextInt(max);
        int op = random.nextInt(tier >= 3 ? 3 : 2);
        String text;
        long value;
        switch (op) {
            case 0 -> { text = a + " + " + b; value = a + b; }
            case 1 -> { text = Math.max(a, b) + " - " + Math.min(a, b); value = Math.abs(a - b); }
            default -> {
                long x = 2 + random.nextInt(Math.min(12, 3 + tier));
                text = a + " x " + x;
                value = a * x;
            }
        }
        if (tier >= 6) {
            long c = 1 + random.nextInt(max);
            if (random.nextBoolean()) { text = "(" + text + ") + " + c; value += c; } else { text = "(" + text + ") - " + c; value -= c; }
        }
        return new String[] {text, Long.toString(value)};
    }

    private void next(ServerPlayer player) {
        String[] p = problem();
        answer.put(player.getUUID(), Long.parseLong(p[1]));
        player.sendSystemMessage(Component.literal(">> ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal(p[0] + " = ?").withStyle(ChatFormatting.GOLD)));
    }

    @Override
    public void tick(long tickInRoom) {
    }

    @Override
    public boolean chat(ServerPlayer player, String message) {
        UUID id = player.getUUID();
        if (!players.containsKey(id) || decided(id)) {
            return false;
        }
        long given;
        try {
            given = Long.parseLong(message.trim().replace(",", ""));
        } catch (NumberFormatException notANumber) {
            Gamemaster.bar(player, "Numbers only.");
            return true;
        }
        if (given != answer.get(id)) {
            Gamemaster.say(player, "No, it was " + answer.get(id) + ".");
            fail(player);
            return true;
        }
        int count = done.merge(id, 1, Integer::sum);
        Gamemaster.bar(player, "Right! (" + count + "/" + problems() + ")");
        if (count >= problems()) {
            clear(player);
        } else {
            next(player);
        }
        return true;
    }
}
