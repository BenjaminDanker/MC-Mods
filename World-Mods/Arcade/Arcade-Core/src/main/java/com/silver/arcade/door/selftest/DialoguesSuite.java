package com.silver.arcade.door.selftest;

import com.silver.arcade.selftest.Bots;
import com.silver.arcade.selftest.PacketLog;
import com.silver.arcade.selftest.Reflect;
import com.silver.arcade.selftest.T;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The scripted NPCs (Villager-Interface dialogues): talking to each one shows its dialogue screen; every choice is clicked
 * (through the real custom-click packet, with the screen's nonce) and the next screen is checked against the script; every
 * node of every script is reached; and Rowan's Soulbound offer with and without the payment.
 */
final class DialoguesSuite {
    private static Class<?> dialogues;
    private static Class<?> mod;

    private DialoguesSuite() {
    }

    static void run(T t) throws Exception {
        try {
            mod = Class.forName("com.silver.villagerinterface.VillagerInterfaceMod");
            dialogues = Class.forName("com.silver.villagerinterface.dialogue.Dialogues");
        } catch (ClassNotFoundException missing) {
            t.fail("Villager-Interface is loaded", missing.toString());
            return;
        }
        Object configManager = mod.getMethod("getConfigManager").invoke(null);
        Object config = Reflect.call(configManager, "getConfig");
        List<?> villagers = Reflect.call(config, "villagers");
        Object manager = mod.getMethod("getVillagerManager").invoke(null);
        List<Object> scripted = new ArrayList<>();
        for (Object entry : villagers) {
            String dialogue = Reflect.call(entry, "dialogue");
            if (dialogue != null && !dialogue.isBlank()) {
                scripted.add(entry);
            }
        }
        t.check("scripted NPCs are configured", scripted.size() >= 6, scripted.size() + " (Oswin, Rowan, four heralds expected)");
        for (Object entry : scripted) {
            String id = Reflect.call(entry, "id");
            t.guard(id + ": dialogue checks ran", () -> npc(t, manager, entry, id));
        }
    }

    private static final Identifier CLICK = Identifier.fromNamespaceAndPath("villagerinterface", "choice");

    private static Object openOf(UUID player) {
        Map<UUID, Object> open = Reflect.getStatic(dialogues, "OPEN");
        return open.get(player);
    }

    private static String nodeOf(UUID player) {
        Object open = openOf(player);
        return open == null ? null : Reflect.call(open, "node");
    }

    private static List<Object> choicesOf(UUID player) {
        Object open = openOf(player);
        return open == null ? List.of() : new ArrayList<>(Reflect.<List<Object>>call(open, "choices"));
    }

    private static void click(ServerPlayer p, int index) {
        Object open = openOf(p.getUUID());
        int nonce = Reflect.call(open, "nonce");
        CompoundTag tag = new CompoundTag();
        tag.putInt("nonce", nonce);
        tag.putInt("choice", index);
        p.connection.handleCustomClickAction(new ServerboundCustomClickActionPacket(CLICK, Optional.of(tag)));
    }

    private static void npc(T t, Object manager, Object entry, String id) throws Exception {
        String name = Bots.PREFIX + "d" + (id.hashCode() & 0xfff);
        Object where = Reflect.call(entry, "position");
        Vec3 at = Reflect.call(where, "toVec3");
        String dialogueId = Reflect.call(entry, "dialogue");
        ServerPlayer bot = Bots.spawn(t, name, at.x + 1.5, at.y, at.z + 0.5, false);
        t.run(() -> {
            DoorSuites.p(t, name).setNoGravity(true);
            DoorSuites.p(t, name).getAbilities().invulnerable = true;
        });
        UUID uid = DoorSuites.uuid(t, name);
        // the NPC stands there
        Entity[] npc = {null};
        boolean found = t.until(() -> {
            for (Entity e : t.server.overworld().getEntities((Entity) null, new AABB(at, at).inflate(8), x -> true)) {
                Object entryOf = Reflect.call(manager, "getEntryForVillager", e);
                if (entryOf != null && id.equals(Reflect.call(entryOf, "id"))) {
                    npc[0] = e;
                    return true;
                }
            }
            return false;
        }, 20 * 20);
        t.check(id + ": the NPC stands in the world", found, "");
        if (!found) {
            Bots.kill(t, name);
            return;
        }
        // real interaction: right-click the NPC
        long mark = PacketLog.mark(name);
        t.run(() -> DoorSuites.p(t, name).interactOn(npc[0], InteractionHand.MAIN_HAND, npc[0].position()));
        boolean shown = t.until(() -> openOf(uid) != null, 40);
        List<PacketLog.Rec> got = PacketLog.since(name, mark);
        t.check(id + ": right-clicking shows the dialogue screen", shown && PacketLog.has(got, "ClientboundShowDialogPacket"),
            shown ? "" : "no screen after interact");
        if (!shown) {
            t.run(() -> Reflect.call(dialogues, "open", DoorSuites.p(t, name), entry));
        }
        Object script = Reflect.call(dialogues, "script", dialogueId);
        Map<String, Object> nodes = Reflect.get(script, "nodes");
        Set<String> reached = new LinkedHashSet<>();
        List<String> problems = new ArrayList<>();
        for (int pass = 0; pass < 3; pass++) {
            Set<String> initialTags = t.on(() -> new HashSet<>(DoorSuites.p(t, name).entityTags()));
            if (pass >= 1) {
                // later passes: carrying what the script asks for (everything, then a single fragment), with every tag it asks for
                Set<String> carries = new LinkedHashSet<>();
                Set<String> tags = new LinkedHashSet<>();
                for (Object node : nodes.values()) {
                    for (Object choice : Reflect.<List<Object>>get(node, "choices")) {
                        List<String> need = Reflect.get(choice, "ifCarries");
                        if (need != null) {
                            carries.addAll(need);
                        }
                        String tag = Reflect.get(choice, "ifTag");
                        if (tag != null) {
                            tags.add(tag);
                        }
                    }
                }
                if (pass == 1) {
                    carries.addAll(List.of("special_arrow", "special_sea_lantern", "special_dead_bush", "special_coal_ore"));
                } else {
                    carries.clear();
                    carries.add("special_arrow");
                }
                t.run(() -> {
                    ServerPlayer p = DoorSuites.p(t, name);
                    int slot = 0;
                    for (String carried : carries) {
                        ItemStack stack = new ItemStack(Items.PAPER);
                        CompoundTag data = new CompoundTag();
                        data.putString("id", carried);
                        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
                        p.getInventory().setItem(slot++, stack);
                    }
                    tags.forEach(p::addTag);
                });
                initialTags = t.on(() -> new HashSet<>(DoorSuites.p(t, name).entityTags()));
            }
            Set<String> baseline = initialTags;
            Deque<List<Integer>> queue = new ArrayDeque<>();
            queue.add(List.of());
            Set<String> expanded = new HashSet<>();
            int clicks = 0;
            while (!queue.isEmpty() && clicks < 300) {
                List<Integer> path = queue.poll();
                // replay the path from the opening
                List<Integer> done = new ArrayList<>();
                String[] node = {null};
                List<Object>[] visible = new List[1];
                t.run(() -> {
                    ServerPlayer p = DoorSuites.p(t, name);
                    p.entityTags().stream().filter(x -> !baseline.contains(x)).toList().forEach(p::removeTag);
                    baseline.forEach(p::addTag);
                    Reflect.call(dialogues, "open", p, entry);
                });
                boolean replayed = true;
                for (int index : path) {
                    long mark2 = PacketLog.mark(name);
                    Object choice = t.on(() -> choicesOf(uid).get(index));
                    String go = Reflect.get(choice, "go");
                    String action = Reflect.get(choice, "action");
                    t.run(() -> click(DoorSuites.p(t, name), index));
                    clicks++;
                    if (action != null) {
                        t.ticks(3);
                    }
                    List<PacketLog.Rec> after = PacketLog.since(name, mark2);
                    String now = t.on(() -> nodeOf(uid));
                    boolean ended = Reflect.<Boolean>get(choice, "end");
                    if (go != null && action == null) {
                        if (!go.equals(now)) {
                            problems.add(id + ": choice '" + Reflect.get(choice, "label") + "' should lead to '" + go + "' but the open screen is '" + now + "'");
                            replayed = false;
                            break;
                        }
                        // the screen sent matches the node's text and the visible choices
                        String info = after.stream().filter(r -> r.type().equals("ClientboundShowDialogPacket")).map(PacketLog.Rec::info).reduce((a, b) -> b).orElse(null);
                        if (info == null) {
                            problems.add(id + ": no screen was sent for node '" + go + "'");
                        } else {
                            Object target = nodes.get(go);
                            for (String line : Reflect.<List<String>>get(target, "text")) {
                                if (!info.contains(line)) {
                                    problems.add(id + ": node '" + go + "' text line missing from the screen: " + line);
                                }
                            }
                            for (Object shown2 : t.on(() -> choicesOf(uid))) {
                                String label = Reflect.get(shown2, "label");
                                if (label != null && !info.contains(label)) {
                                    problems.add(id + ": node '" + go + "' button missing from the screen: " + label);
                                }
                            }
                        }
                    }
                    if (ended) {
                        replayed = false;
                        break;
                    }
                }
                if (!replayed) {
                    continue;
                }
                String here = t.on(() -> nodeOf(uid));
                if (here == null) {
                    continue;
                }
                reached.add(here);
                if (!expanded.add(here + "#" + pass)) {
                    continue;
                }
                List<Object> choices = t.on(() -> choicesOf(uid));
                for (int i = 0; i < choices.size(); i++) {
                    Object choice = choices.get(i);
                    String action = Reflect.get(choice, "action");
                    boolean end = Reflect.get(choice, "end");
                    String go = Reflect.get(choice, "go");
                    if (action != null) {
                        continue; // the soulbound actions are tested on their own below
                    }
                    List<Integer> next = new ArrayList<>(path);
                    next.add(i);
                    queue.add(next);
                    if (end) {
                        // leaving closes the screen
                        long mark3 = PacketLog.mark(name);
                        t.run(() -> {
                            click(DoorSuites.p(t, name), i2(choices, choice));
                        });
                        boolean closed = t.on(() -> openOf(uid) == null);
                        if (!closed) {
                            problems.add(id + ": the end choice '" + Reflect.get(choice, "label") + "' did not close the screen");
                        }
                        // re-open for further exploration
                        t.run(() -> Reflect.call(dialogues, "open", DoorSuites.p(t, name), entry));
                    }
                }
            }
            t.run(() -> {
                ServerPlayer p = DoorSuites.p(t, name);
                p.getInventory().clearContent();
            });
        }
        t.check(id + ": every choice leads to the node the script names, with its text and buttons", problems.isEmpty(),
            problems.isEmpty() ? nodes.size() + " nodes" : String.join("; ", problems.subList(0, Math.min(5, problems.size()))));
        Set<String> missing = new LinkedHashSet<>(nodes.keySet());
        missing.removeAll(reached);
        t.check(id + ": all " + nodes.size() + " nodes of '" + dialogueId + "' are reachable", missing.isEmpty(), "unreached: " + missing);
        if (id.contains("rowan") || dialogueId.equals("rowan")) {
            soulbound(t, name, uid, entry, script);
        }
        t.run(() -> Reflect.call(dialogues, "clear", uid));
        Bots.kill(t, name);
    }

    private static int i2(List<Object> choices, Object choice) {
        return choices.indexOf(choice);
    }

    /** Rowan binds the held item to the soul for a spare of the same item; without the spare he cannot. */
    private static void soulbound(T t, String name, UUID uid, Object entry, Object script) throws Exception {
        Map<String, Object> nodes = Reflect.get(script, "nodes");
        // Player Sync applies the bot's saved state (soulbound items only) some time after the join; if that lands in the middle of
        // the test the freshly bound sword is wiped from the inventory, so wait for it to be logged first
        for (long until = System.currentTimeMillis() + 15_000; com.silver.arcade.selftest.LogScan.grep("Gave " + name + " their saved state").isEmpty()
            && System.currentTimeMillis() < until;) {
            T.sleep(250);
        }
        t.ticks(20);
        // a player with room in their Soul: the capacity the blacksmith asks Player Sync for is put in its short-lived cache
        Class<?> api = Class.forName("com.silver.soulbounditems.playersync.SoulboundSlots");
        Map<String, Object> cache = Reflect.getStatic(api, "CACHE");
        Class<?> entryType = Class.forName(api.getName() + "$CacheEntry");
        var ctor = entryType.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        String key = Reflect.call(api, "toCacheKey", name, uid.toString());
        cache.put(key, ctor.newInstance(3, System.currentTimeMillis()));
        try {
            soulboundRounds(t, name, uid, entry, nodes);
        } finally {
            cache.remove(key);
        }
    }

    private static void soulboundRounds(T t, String name, UUID uid, Object entry, Map<String, Object> nodes) throws Exception {
        for (int round = 0; round < 2; round++) {
            boolean paid = round == 1;
            t.run(() -> {
                ServerPlayer p = DoorSuites.p(t, name);
                p.getInventory().clearContent();
            });
            final boolean pay = paid;
            t.run(() -> {
                ServerPlayer p = DoorSuites.p(t, name);
                p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(Items.DIAMOND_SWORD));
                if (pay) {
                    p.getInventory().setItem(8, new ItemStack(Items.DIAMOND_SWORD));
                }
                Reflect.call(dialogues, "open", p, entry);
            });
            long mark = PacketLog.mark(name);
            List<Object> choices = t.on(() -> choicesOf(uid));
            int q = -1;
            for (int i = 0; i < choices.size(); i++) {
                if ("soulbound_quote".equals(Reflect.get(choices.get(i), "action"))) {
                    q = i;
                }
            }
            String tag = paid ? "with payment" : "without payment";
            if (q < 0) {
                t.fail("Rowan: the Soulbound choice is offered (" + tag + ")", "no soulbound_quote choice visible");
                continue;
            }
            int qi = q;
            t.run(() -> click(DoorSuites.p(t, name), qi));
            t.ticks(5);
            List<PacketLog.Rec> got = PacketLog.since(name, mark);
            String reply = got.stream().filter(r -> r.type().equals("ClientboundShowDialogPacket")).map(PacketLog.Rec::info).reduce((a, b) -> b).orElse("");
            t.check("Rowan: the quote is answered on screen (" + tag + ")", !reply.isBlank(), reply);
            boolean offersConfirm = t.on(() -> choicesOf(uid).stream().anyMatch(c -> "soulbound_confirm".equals(Reflect.get(c, "action"))));
            if (!paid) {
                t.check("Rowan: without payment there is no 'Do it' button", !offersConfirm, reply);
                boolean unchanged = t.on(() -> !soulbound(DoorSuites.p(t, name).getInventory().getSelectedItem()));
                t.check("Rowan: the item is untouched without payment", unchanged, "");
            } else {
                t.check("Rowan: with payment a 'Do it' button is offered", offersConfirm, reply);
                if (offersConfirm) {
                    int ci = t.on(() -> {
                        List<Object> now = choicesOf(uid);
                        for (int i = 0; i < now.size(); i++) {
                            if ("soulbound_confirm".equals(Reflect.get(now.get(i), "action"))) {
                                return i;
                            }
                        }
                        return -1;
                    });
                    long mark2 = PacketLog.mark(name);
                    t.run(() -> click(DoorSuites.p(t, name), ci));
                    t.ticks(5);
                    String result = PacketLog.since(name, mark2).stream().filter(r -> r.type().equals("ClientboundShowDialogPacket"))
                        .map(PacketLog.Rec::info).reduce((a, b) -> b).orElse("");
                    boolean bound = t.on(() -> soulbound(DoorSuites.p(t, name).getInventory().getSelectedItem()));
                    int swords = t.on(() -> {
                        int n = 0;
                        ServerPlayer p = DoorSuites.p(t, name);
                        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                            if (p.getInventory().getItem(i).is(Items.DIAMOND_SWORD)) {
                                n += p.getInventory().getItem(i).getCount();
                            }
                        }
                        return n;
                    });
                    t.check("Rowan: confirming binds the held item to the soul", bound, result);
                    t.check("Rowan: the spare item is taken as payment", swords == 1, swords + " diamond swords left");
                }
            }
        }
    }

    private static boolean soulbound(ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data != null && "Soulbound".equals(data.copyTag().getStringOr("idType", ""))
            || stack.getHoverName().getString().startsWith("Soulbound");
    }
}
