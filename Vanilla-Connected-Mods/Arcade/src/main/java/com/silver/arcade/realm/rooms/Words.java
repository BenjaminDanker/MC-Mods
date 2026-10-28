package com.silver.arcade.realm.rooms;

import java.util.Random;

/** Word material for the typing race: common words at low tiers, longer and odder words as tiers climb. */
final class Words {
    private static final String[] EASY = ("the of and to in is it you that he was for on are with as his they be at one have this from or had by "
        + "word but what some we can out other were all there when up use your how said an each she which do their time if will way about "
        + "many then them write would like so these her long make thing see him two has look more day could go come did number sound no "
        + "most people my over know water than call first who may down side been now find any new work part take get place made live where "
        + "after back little only round man year came show every good me give our under name very through just form sentence great think "
        + "say help low line differ turn cause much mean before move right boy old too same tell does set three want air well also play "
        + "small end put home read hand port large spell add even land here must big high such follow act why ask men change went light kind").split(" ");
    private static final String[] HARD = ("quarry rhythm syzygy cobblestone obsidian netherite beacon labyrinth gamemaster parchment lantern "
        + "phantom enchantment brewing redstone observer comparator repeater piston elytra trident shulker axolotl amethyst deepslate "
        + "archaeology sniffer breeze chorus wither guardian leviathan pharaoh bastion fortress stronghold mangrove cherry bamboo "
        + "crystalline juxtapose quixotic zephyr whimsical labyrinthine perpendicular onomatopoeia chrysanthemum kaleidoscope").split(" ");

    private Words() {
    }

    /** A line of {@code count} words; from tier 6 on, hard words mix in, and from tier 10 punctuation and capitals. */
    static String line(Random random, int count, int tier) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            boolean hard = tier >= 6 && random.nextInt(10) < Math.min(6, tier - 5);
            String word = hard ? HARD[random.nextInt(HARD.length)] : EASY[random.nextInt(EASY.length)];
            if (tier >= 10 && random.nextInt(4) == 0) {
                word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
            }
            if (i > 0) {
                out.append(' ');
            }
            out.append(word);
            if (tier >= 10 && i < count - 1 && random.nextInt(6) == 0) {
                out.append(random.nextBoolean() ? ',' : ';');
            }
        }
        return out.toString();
    }

    /** Similarity in [0,1] from the Levenshtein distance. */
    static double accuracy(String expected, String typed) {
        int[] prev = new int[typed.length() + 1];
        int[] cur = new int[typed.length() + 1];
        for (int j = 0; j <= typed.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= expected.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= typed.length(); j++) {
                int cost = expected.charAt(i - 1) == typed.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        int distance = prev[typed.length()];
        return 1.0 - (double) distance / Math.max(1, Math.max(expected.length(), typed.length()));
    }
}
