package com.silver.qaclient;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Samples what the HUD shows (titles, subtitle, action bar, boss bars, held item) and logs every change. */
public final class Hud {
    private static String lastTitle = "", lastSub = "", lastAction = "", lastBars = "", lastHeld = "";
    public static volatile int titles, actionbars, bossNames, chats;
    public static volatile String curTitle = "", curSub = "", curAction = "", curBars = "";
    private static final java.util.Set<String> seenBars = new java.util.HashSet<>();
    public static final java.util.concurrent.CopyOnWriteArrayList<String> chatLog = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Everything the HUD showed, in order: "T:" title+subtitle, "A:" action bar, "C:" chat, "B:" boss bars. */
    public static final java.util.concurrent.CopyOnWriteArrayList<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static boolean seen(int since, String needle) {
        for (int i = since; i < log.size(); i++) {
            if (log.get(i).toLowerCase().contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private Hud() {
    }

    static void init() {
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            chats++;
            chatLog.add(msg.getString());
            log.add((overlay ? "A:" : "C:") + msg.getString());
            Rec.event(overlay ? "actionbar_msg" : "chat", "text", msg.getString());
        });
        ClientReceiveMessageEvents.CHAT.register((msg, signed, sender, params, ts) -> {
            chats++;
            chatLog.add(msg.getString());
            log.add("C:" + msg.getString());
            Rec.event("chat", "text", msg.getString(), "from", sender == null ? "-" : sender.name());
        });
    }

    public static void resetCounters() {
        titles = 0;
        actionbars = 0;
        bossNames = 0;
        seenBars.clear();
    }

    private static String s(Component c) {
        return c == null ? "" : c.getString();
    }

    public static String held(Minecraft mc) {
        if (mc.player == null) {
            return "";
        }
        ItemStack st = mc.player.getMainHandItem();
        return st.isEmpty() ? "(empty)" : st.getCount() + "x " + st.getHoverName().getString() + " [" + st.getItem() + "]";
    }

    public static String hotbar(Minecraft mc) {
        if (mc.player == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if (!st.isEmpty()) {
                b.append(i).append(':').append(st.getCount()).append('x').append(st.getHoverName().getString()).append(" | ");
            }
        }
        return b.toString();
    }

    /** Called every client tick on the client thread. */
    static void sample(Minecraft mc) {
        var hud = mc.gui.hud;
        String title = hud.titleTime > 0 ? s(hud.title) : "";
        String sub = hud.titleTime > 0 ? s(hud.subtitle) : "";
        String action = hud.overlayMessageTime > 0 ? s(hud.overlayMessageString) : "";
        List<String> bars = new ArrayList<>();
        for (LerpingBossEvent e : hud.getBossOverlay().events.values()) {
            bars.add(e.getName().getString() + " (" + Math.round(e.getProgress() * 100) + "%)");
        }
        String barKey = String.join(" || ", bars);
        curTitle = title;
        curSub = sub;
        curAction = action;
        curBars = barKey;
        if (!title.equals(lastTitle) || !sub.equals(lastSub)) {
            if (!title.isEmpty() || !sub.isEmpty()) {
                titles++;
                log.add("T:" + title + " / " + sub);
                Rec.event("title", "title", title, "subtitle", sub);
            }
            lastTitle = title;
            lastSub = sub;
        }
        if (!action.equals(lastAction)) {
            if (!action.isEmpty()) {
                actionbars++;
                Rec.event("actionbar", "text", action);
            }
            lastAction = action;
        }
        if (!barKey.equals(lastBars)) {
            for (String b : bars) {
                String name = b.replaceAll(" \\(\\d+%\\)$", "");
                if (seenBars.add(name)) {
                    bossNames++;
                }
            }
            log.add("B:" + barKey);
            Rec.event("bossbar", "bars", barKey);
            lastBars = barKey;
        }
        String held = held(mc);
        if (!held.equals(lastHeld)) {
            Rec.event("held", "item", held, "hotbar", hotbar(mc));
            lastHeld = held;
        }
    }
}
