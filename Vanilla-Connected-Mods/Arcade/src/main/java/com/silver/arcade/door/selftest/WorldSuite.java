package com.silver.arcade.door.selftest;

import com.silver.arcade.ArcadeConfig;
import com.silver.arcade.door.Die;
import com.silver.arcade.selftest.Bots;
import com.silver.arcade.selftest.LogScan;
import com.silver.arcade.selftest.PacketLog;
import com.silver.arcade.selftest.Reflect;
import com.silver.arcade.selftest.SelfTest;
import com.silver.arcade.selftest.T;
import com.silver.arcade.selftest.TestDb;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Doors (discovery and the Die), the altar, and prizes delivered on join. */
final class WorldSuite {
    private WorldSuite() {
    }

    static void run(T t) throws Exception {
        ArcadeConfig config = SelfTest.ENV.config;
        int[] configured = config.door.altar;
        Vec3 altar = configured != null && configured.length == 3 ? new Vec3(configured[0] + 0.5, configured[1], configured[2] + 0.5) : DoorSuites.altarAt(t);
        if (altar == null) {
            t.fail("the altar has a position", "no altar placed yet");
            return;
        }
        t.ok("the altar has a position", altar.toString());
        // the altar's display entities exist while its chunk is loaded: stand a bot near it first
        String lookout = bot("wlook");
        Bots.spawn(t, lookout, altar.x + 8, altar.y, altar.z + 8, false);
        t.until(() -> DoorSuites.near(t, altar, 3, "arcade_altar").size() >= 3, 20 * 15);
        t.check("the altar's display entities stand", DoorSuites.near(t, altar, 3, "arcade_altar").size() >= 3,
            DoorSuites.near(t, altar, 3, "arcade_altar").size() + " parts");
        Bots.kill(t, lookout);
        String server = config.serverName;
        salt = Integer.toString((int) (System.currentTimeMillis() / 1000 % 1296), 36);
        prizes(t, server, altar);
        theDie(t, server, altar);
        altar(t, altar);
        discovery(t, config, altar);
    }

    private static String inventory(T t, String name) {
        return t.on(() -> {
            ServerPlayer p = DoorSuites.p(t, name);
            StringBuilder out = new StringBuilder("at " + p.position() + " items:");
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (!s.isEmpty()) {
                    out.append(' ').append(s.getCount()).append('x').append(s.getItem());
                }
            }
            return out.toString();
        });
    }

    /** Bots of this suite carry a per-run suffix: the product remembers per-player timers (altar, doors) until the server restarts. */
    private static String salt = "";

    private static String bot(String base) {
        return Bots.PREFIX + base + salt;
    }

    private static ServerPlayer join(T t, String name, Vec3 at) {
        ServerPlayer bot = Bots.spawn(t, name, at.x + 4.5, at.y, at.z + 0.5, false);
        t.run(() -> DoorSuites.p(t, name).setGameMode(net.minecraft.world.level.GameType.SURVIVAL)); // doors are for survival players
        return bot;
    }

    // --- prizes waiting in the database are delivered on join (the row consumed, items in the inventory)
    private static void prizes(T t, String server, Vec3 altar) throws Exception {
        String name = bot("wprz");
        join(t, name, altar);
        UUID id = DoorSuites.uuid(t, name);
        Bots.kill(t, name);
        try (TestDb db = new TestDb()) {
            for (int tier : new int[] {1, 5, 20}) {
                db.insert("INSERT INTO arcade_prizes (uuid, server, tier, run) VALUES (?, ?, ?, ?)", id.toString(), server, tier, "selftest");
            }
        }
        long mark = PacketLog.mark(name);
        join(t, name, altar);
        boolean delivered = t.until(() -> {
            ServerPlayer p = DoorSuites.p(t, name);
            return p != null && !p.getInventory().isEmpty();
        }, 20 * 90);
        List<PacketLog.Rec> got = PacketLog.since(name, mark);
        t.check("prizes on join: items arrive in the inventory", delivered, "");
        long messages = got.stream().filter(r -> r.type().equals("ClientboundSystemChatPacket") && r.info().contains("keeps his word")).count();
        t.check("prizes on join: one 'keeps his word' message per prize (3)", messages == 3, messages + " messages");
        T.sleep(500);
        t.check("prizes on join: the rows are consumed", DoorSuites.rows("arcade_prizes", id) == 0, DoorSuites.rows("arcade_prizes", id) + " rows left");
        Bots.kill(t, name);
    }

    // --- a visitor is given the Die once; rolling it opens a door; the door lets them through; the door closes
    private static void theDie(T t, String server, Vec3 altar) throws Exception {
        String name = bot("wvis");
        join(t, name, altar);
        UUID id = DoorSuites.uuid(t, name);
        t.check("a never-visited player is not given the Die", !t.on(() -> Die.carries(DoorSuites.p(t, name))), inventory(t, name));
        Bots.kill(t, name);
        try (TestDb db = new TestDb()) {
            db.insert("REPLACE INTO arcade_entries (uuid, name, origin) VALUES (?, ?, ?)", id.toString(), name, server);
        }
        long mark = PacketLog.mark(name);
        join(t, name, altar);
        boolean announced = t.until(() -> PacketLog.hasText(PacketLog.since(name, mark), "ClientboundSystemChatPacket", "rattles in your pocket"), 20 * 90);
        t.check("a visitor is told by the Gamemaster that the Die is in their pocket on join", announced, "");
        // the Die is given 10 s after joining and recorded once it is confirmed present 5 s later
        for (long until = System.currentTimeMillis() + 20_000; DoorSuites.rows("arcade_dice", id) != 1 && System.currentTimeMillis() < until;) {
            T.sleep(500);
        }
        t.check("...and it is recorded (arcade_dice)", DoorSuites.rows("arcade_dice", id) == 1, DoorSuites.rows("arcade_dice", id) + " rows");
        boolean dieSent = PacketLog.since(name, mark).stream().anyMatch(r -> r.info().contains("echo_shard"));
        T.sleep(3000); // the player-data sync (Player Sync) applies its saved state about a second after the join
        t.ticks(60);
        boolean gotDie = t.on(() -> Die.carries(DoorSuites.p(t, name)));
        t.check("...and the Die is still in the inventory three seconds later (not wiped by the player-data sync)", gotDie,
            gotDie ? "" : "the Die " + (dieSent ? "WAS" : "was not") + " sent to the player's inventory at join, then it was gone; arcade_dice says it was given; "
                + LogScan.grep("Gave " + name + " their saved state").size() + " Player Sync loads logged for this bot");
        // not given twice: take it away, leave and come back
        t.run(() -> DoorSuites.p(t, name).getInventory().clearContent());
        Bots.kill(t, name);
        join(t, name, altar);
        t.ticks(60);
        t.check("the Die is not given a second time", !t.on(() -> Die.carries(DoorSuites.p(t, name))), "");
        // Roll it. Claim protection around spawn may veto item use, so try the altar first and then sites further out until a door opens.
        t.run(() -> {
            ServerPlayer p = DoorSuites.p(t, name);
            p.getInventory().setItem(p.getInventory().getSelectedSlot(), Die.create());
        });
        Vec3[] sites = {altar, altar.add(160, 0, 0), altar.add(-160, 0, 0), altar.add(0, 0, 160), altar.add(0, 0, -160), altar.add(400, 0, 400),
            altar.add(-400, 0, 400), altar.add(400, 0, -400), altar.add(-400, 0, -400)};
        StringBuilder siteLog = new StringBuilder();
        Vec3 area = altar;
        int parts = 0;
        long openedAt = 0;
        long mark2 = PacketLog.mark(name);
        int doorsBefore = Reflect.<List<?>>get(SelfTest.ENV.roleObject, "doors").size();
        for (Vec3 site : sites) {
            Vec3 spot = openGround(t, name, site);
            if (spot == null) {
                siteLog.append(String.format("(%.0f,%.0f) no open ground; ", site.x, site.z));
                continue;
            }
            t.run(() -> {
                ServerPlayer p = DoorSuites.p(t, name);
                p.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, p.getYRot(), p.getXRot()));
            });
            t.ticks(4);
            boolean opened = t.on(() -> Reflect.<List<?>>get(SelfTest.ENV.roleObject, "doors").size() > doorsBefore);
            parts = opened ? 1 : 0;
            siteLog.append(String.format("(%.0f,%.0f) %s; ", site.x, site.z, opened ? "door opened" : "refused"));
            if (opened) {
                area = spot;
                openedAt = t.tick();
                break;
            }
        }
        Vec3 doorArea = area;
        List<PacketLog.Rec> rolled = PacketLog.since(name, mark2);
        t.check("rolling the Die opens a door", parts > 0, "sites tried: " + siteLog + " chat: " + DoorSuites.chat(name, mark2));
        int standing = parts == 0 ? 0 : t.on(() -> {
            List<?> list = Reflect.get(SelfTest.ENV.roleObject, "doors");
            List<Entity> partList = Reflect.get(list.get(list.size() - 1), "parts");
            return (int) partList.stream().filter(e -> !e.isRemoved()).count();
        });
        int total = parts == 0 ? 0 : t.on(() -> {
            List<?> list = Reflect.get(SelfTest.ENV.roleObject, "doors");
            return Reflect.<List<Entity>>get(list.get(list.size() - 1), "parts").size();
        });
        t.check("...its display entities (frame, glass, sign) stand in the world", total >= 6 && standing == total, standing + " of " + total + " parts are in the world");
        t.check("...with the Gamemaster's line", PacketLog.hasText(rolled, "ClientboundSystemChatPacket", "dice are rolling"), DoorSuites.chat(name, mark2));
        t.check("...and a sound", PacketLog.hasAny(rolled, "ClientboundSoundPacket|ClientboundSoundEntityPacket"), "");
        // a second roll straight away is refused
        int partsNow = Reflect.<List<?>>get(SelfTest.ENV.roleObject, "doors").size();
        long mark3 = PacketLog.mark(name);
        t.run(() -> {
            ServerPlayer p = DoorSuites.p(t, name);
            p.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, p.getYRot(), p.getXRot()));
        });
        t.ticks(4);
        t.check("a second roll right away opens no second door", Reflect.<List<?>>get(SelfTest.ENV.roleObject, "doors").size() == partsNow, "");
        t.check("...and says the die is still warm", PacketLog.hasText(PacketLog.since(name, mark3), "ClientboundSystemChatPacket", "still warm"), DoorSuites.chat(name, mark3));
        // walk through
        if (parts > 0) {
            Object role = SelfTest.ENV.roleObject;
            List<?> doors = Reflect.get(role, "doors");
            Object door = doors.get(doors.size() - 1);
            Vec3 centre = Reflect.get(door, "centre");
            long closesAt = Reflect.get(door, "closesAt");
            long mark4 = PacketLog.mark(name);
            t.run(() -> {
                ServerPlayer p = DoorSuites.p(t, name);
                p.teleportTo(t.server.overworld(), centre.x, centre.y, centre.z, java.util.Set.of(), p.getYRot(), 0F, false);
            });
            boolean sent = t.until(() -> DoorSuites.logged("arcade door requested proxy transfer for " + name), 20 * 10);
            List<PacketLog.Rec> through = PacketLog.since(name, mark4);
            t.check("walking into the door queues a transfer to the arcade (server-portals request)", sent,
                sent ? "" : "no 'requested proxy transfer' log line; recent: " + DoorSuites.chat(name, mark4));
            t.check("...the transfer request was sent to the player as a packet", PacketLog.hasText(through, "ClientboundCustomPayloadPacket", "portal"),
                DoorSuites.describe(through, "ClientboundCustomPayloadPacket"));
            T.sleep(500);
            t.check("...and the entry is recorded (arcade_entries)", DoorSuites.rows("arcade_entries", id) == 1, "");
            // the door closes when its time is up
            long left = closesAt - t.tick();
            // This very door, not the list size: a door left over from an earlier run (frozen while the empty server was paused) closes on its
            // own schedule right after the run starts, which shrinks the list while this door still stands.
            boolean closed = t.until(() -> !Reflect.<List<?>>get(SelfTest.ENV.roleObject, "doors").contains(door), (int) left + 100);
            long lived = t.tick() - openedAt;
            int configured = SelfTest.ENV.config.door.dieDoorSeconds * 20;
            t.check("the door is taken down after " + SelfTest.ENV.config.door.dieDoorSeconds + " s", closed && Math.abs(lived - configured) < 40,
                "stood " + lived + " ticks (configured " + configured + ")");
        }
        Bots.kill(t, name);
    }

    /** Puts the bot on open ground near the site (loading the chunks first); null when there is no room for a door there. */
    private static Vec3 openGround(T t, String name, Vec3 site) {
        t.run(() -> {
            ServerPlayer p = DoorSuites.p(t, name);
            p.teleportTo(t.server.overworld(), site.x, t.server.overworld().getMaxY() - 2, site.z, java.util.Set.of(), 0F, 0F, false);
            p.setNoGravity(true);
        });
        t.until(() -> t.server.overworld().hasChunk((int) Math.floor(site.x) >> 4, (int) Math.floor(site.z) >> 4), 400);
        t.ticks(40); // the chunks round it
        Vec3 spot = DoorSuites.openSpot(t, site);
        t.run(() -> {
            ServerPlayer p = DoorSuites.p(t, name);
            p.setNoGravity(false);
            Vec3 at = spot != null ? spot : site;
            p.teleportTo(t.server.overworld(), at.x, at.y, at.z, java.util.Set.of(), 0F, 0F, false);
        });
        t.ticks(3);
        return spot;
    }

    // --- the altar gives a Die to a player without one, and not to one that has it
    private static void altar(T t, Vec3 altar) throws Exception {
        String name = bot("walt");
        Bots.spawn(t, name, altar.x + 8, altar.y, altar.z + 8, false);
        T.sleep(2500); // the player-data sync (Player Sync) applies the saved state about a second after the join; give it time
        t.ticks(20);
        t.check("a player standing away from the altar is given nothing", !t.on(() -> Die.carries(DoorSuites.p(t, name))), inventory(t, name));
        long mark = PacketLog.mark(name);
        t.run(() -> {
            ServerPlayer p = DoorSuites.p(t, name);
            p.teleportTo(t.server.overworld(), altar.x, altar.y, altar.z, java.util.Set.of(), 0F, 0F, false);
        });
        boolean given = t.until(() -> Die.carries(DoorSuites.p(t, name)), 100);
        t.check("the altar gives a Die to a player who has none", given, "");
        // leave and come back after the altar's cooldown (60 s): the die in hand is not doubled
        t.run(() -> DoorSuites.p(t, name).teleportTo(t.server.overworld(), altar.x + 8, altar.y, altar.z + 8, java.util.Set.of(), 0F, 0F, false));
        t.ticks(20 * 60 + 40);
        long mark2 = PacketLog.mark(name);
        t.run(() -> DoorSuites.p(t, name).teleportTo(t.server.overworld(), altar.x, altar.y, altar.z, java.util.Set.of(), 0F, 0F, false));
        t.ticks(30);
        int dice = t.on(() -> {
            int n = 0;
            ServerPlayer p = DoorSuites.p(t, name);
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                if (Die.is(p.getInventory().getItem(i))) {
                    n += p.getInventory().getItem(i).getCount();
                }
            }
            return n;
        });
        t.check("the altar gives no second Die to a player who carries one", dice == 1, dice + " dice");
        t.check("...and tells them so", PacketLog.hasText(PacketLog.since(name, mark2), "ClientboundSystemChatPacket", "already carry"), DoorSuites.chat(name, mark2));
        Bots.kill(t, name);
    }

    // --- a never-visited player may be offered a discovery door (the roll is forced to certain)
    private static void discovery(T t, ArcadeConfig config, Vec3 altar) throws Exception {
        String name = bot("wnew");
        double chance = config.door.discoveryChance;
        int every = config.door.checkEveryMinutes;
        try {
            config.door.discoveryChance = 1.0;
            config.door.checkEveryMinutes = 1;
            long mark = PacketLog.mark(name);
            join(t, name, altar);
            UUID id = DoorSuites.uuid(t, name);
            Vec3 spot = openGround(t, name, altar);
            Vec3 at = spot != null ? spot : altar;
            int before = Reflect.<List<?>>get(SelfTest.ENV.roleObject, "doors").size();
            boolean opened = t.until(() -> Reflect.<List<?>>get(SelfTest.ENV.roleObject, "doors").size() > before, 20 * 150);
            t.check("a never-visited player is offered a discovery door (roll forced)", opened, opened ? "" : "profiles: "
                + Reflect.get(SelfTest.ENV.roleObject, "profiles") + " next checks: " + Reflect.get(SelfTest.ENV.roleObject, "nextCheckAt")
                + " tick " + t.tick() + " mode " + t.on(() -> DoorSuites.p(t, name).gameMode()));
            if (opened) {
                int standing = t.on(() -> {
                    List<?> list = Reflect.get(SelfTest.ENV.roleObject, "doors");
                    return (int) Reflect.<List<Entity>>get(list.get(list.size() - 1), "parts").stream().filter(e -> !e.isRemoved()).count();
                });
                t.check("...its display entities stand in the world", standing >= 6, standing + " parts in the world");
            }
            T.sleep(800);
            t.check("...the offer is recorded (arcade_discovery)", DoorSuites.rows("arcade_discovery", id) == 1, DoorSuites.rows("arcade_discovery", id) + " rows");
            t.check("...with the Gamemaster's line", PacketLog.hasText(PacketLog.since(name, mark), "ClientboundSystemChatPacket", "dice are rolling"), "");
        } finally {
            config.door.discoveryChance = chance;
            config.door.checkEveryMinutes = every;
        }
        Bots.kill(t, name);
    }
}
