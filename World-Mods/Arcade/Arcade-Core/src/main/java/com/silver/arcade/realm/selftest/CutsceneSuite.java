package com.silver.arcade.realm.selftest;

import com.silver.arcade.selftest.Bots;
import com.silver.arcade.selftest.Cleanup;
import com.silver.arcade.selftest.PacketLog;
import com.silver.arcade.selftest.Reflect;
import com.silver.arcade.selftest.SelfTest;
import com.silver.arcade.selftest.T;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/** The first-visit scene and the Gamemaster himself. */
final class CutsceneSuite {
    private CutsceneSuite() {
    }

    private static List<Entity> tagged(T t, String tag) {
        return t.on(() -> t.server.overworld().getEntities((Entity) null, new AABB(-200, 0, -200, 200, 300, 200), e -> e.entityTags().contains(tag)));
    }

    static void run(T t) throws Exception {
        Cleanup.unforce();
        gamemaster(t);
        introPlays(t);
        sneakSkips(t);
    }

    private static void gamemaster(T t) {
        List<Entity> gm = tagged(t, "arcade_gamemaster");
        t.check("exactly one Gamemaster entity exists", gm.size() == 1, gm.size() + " entities");
        if (gm.size() == 1) {
            Entity e = gm.get(0);
            double dx = e.getX() - 4.5;
            double dz = e.getZ() - 0.5;
            t.check("...floating over his spot in the hall", Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6 && e.getY() > 101.7 && e.getY() < 103.0,
                "at " + e.position());
        }
        // no NPC that nobody configured
        Object manager;
        try {
            manager = Class.forName("com.silver.villagerinterface.VillagerInterfaceMod").getMethod("getVillagerManager").invoke(null);
        } catch (ReflectiveOperationException missing) {
            manager = null;
        }
        Object mgr = manager;
        List<String> strays = t.on(() -> {
            List<String> out = new java.util.ArrayList<>();
            for (Entity e : t.server.overworld().getEntities((Entity) null, new AABB(-300, 0, -300, 300, 400, 300), x -> true)) {
                boolean npc = e instanceof net.minecraft.world.entity.npc.villager.Villager || e instanceof net.minecraft.world.entity.decoration.Mannequin;
                if (npc && !e.entityTags().contains("arcade_gamemaster")) {
                    boolean configured = mgr != null && Reflect.call(mgr, "getEntryForVillager", e) != null;
                    if (!configured) {
                        out.add(e.getType() + " at " + e.position());
                    }
                }
            }
            return out;
        });
        t.check("no unconfigured NPCs stand in the arcade", strays.isEmpty(), strays.toString());
        // right-click him: a line
        String name = Bots.PREFIX + "g1";
        Bots.spawn(t, name, 4.5, 101, 3.5, false);
        long mark = PacketLog.mark(name);
        t.run(() -> {
            ServerPlayer p = t.server.getPlayerList().getPlayerByName(name);
            Entity e = tagged(t, "arcade_gamemaster").get(0);
            Bots.ready(p);
            p.connection.handleInteract(new net.minecraft.network.protocol.game.ServerboundInteractPacket(e.getId(), net.minecraft.world.InteractionHand.MAIN_HAND, e.position().subtract(e.position()), false));
        });
        t.ticks(3);
        t.check("right-clicking the Gamemaster gets a line of his", PacketLog.hasText(PacketLog.since(name, mark), "ClientboundSystemChatPacket", "Gamemaster:"), "");
        Bots.kill(t, name);
    }

    private static void introPlays(T t) throws Exception {
        String name = Bots.PREFIX + "c1";
        List<Entity> before = tagged(t, "arcade_gamemaster");
        int gmId = before.isEmpty() ? -1 : before.get(0).getId();
        Bots.spawn(t, name, 0.5, 101, 0.5, true);
        UUID id = t.on(() -> t.server.getPlayerList().getPlayerByName(name).getUUID());
        // the scene starts once the bot's records are read
        boolean started = t.until(() -> {
            ServerPlayer p = t.server.getPlayerList().getPlayerByName(name);
            return p.isSpectator() && p.getCamera() != p;
        }, 400);
        t.check("a first-time bot is watched through the scene camera as a spectator", started, "");
        if (!started) {
            Bots.kill(t, name);
            return;
        }
        int atStart = t.tick();
        long mark = PacketLog.mark(name);
        int camId = t.on(() -> t.server.getPlayerList().getPlayerByName(name).getCamera().getId());
        t.check("...the camera entity exists in the world", tagged(t, "villagerinterface_camera").size() == 1, tagged(t, "villagerinterface_camera").size() + " cameras");
        // watch it play to the end
        boolean ended = t.until(() -> {
            ServerPlayer p = t.server.getPlayerList().getPlayerByName(name);
            return !p.isSpectator() && p.getCamera() == p;
        }, 20 * 60);
        List<PacketLog.Rec> scene = PacketLog.since(name, 0);
        long teleports = PacketLog.count(scene, "ClientboundTeleportEntityPacket");
        t.check("the scene ends by itself (" + (t.tick() - atStart) + " ticks) and the bot is a player again", ended, "");
        t.check("packets: the camera is set", PacketLog.has(scene, "ClientboundSetCameraPacket"), "");
        t.check("packets: the cast is added (AddEntity) for the viewer", PacketLog.count(scene, "ClientboundAddEntityPacket") >= 2, PacketLog.count(scene, "ClientboundAddEntityPacket") + " AddEntity packets");
        t.check("packets: the camera and actors are moved every tick (" + teleports + " teleports)", teleports >= 100, teleports + " TeleportEntity packets");
        t.check("packets: head rotation updates for the actor", PacketLog.count(scene, "ClientboundRotateHeadPacket") >= 20, PacketLog.count(scene, "ClientboundRotateHeadPacket") + " packets");
        t.check("packets: the speech bubble (text display) is updated and removed", PacketLog.count(scene, "ClientboundSetEntityDataPacket") >= 10
            && PacketLog.has(scene, "ClientboundRemoveEntitiesPacket"), PacketLog.count(scene, "ClientboundSetEntityDataPacket") + " data packets");
        t.check("packets: titles and chat lines from the script", PacketLog.has(scene, "ClientboundSetTitleTextPacket"), "");
        final int gm = gmId;
        t.ticks(40);
        scene = PacketLog.since(name, 0);
        List<Integer> cameraTicks = scene.stream().filter(r -> r.type().equals("ClientboundSetCameraPacket")).map(PacketLog.Rec::tick).toList();
        int sceneStart = cameraTicks.isEmpty() ? -1 : cameraTicks.get(0);
        int sceneEnd = cameraTicks.isEmpty() ? -1 : cameraTicks.get(cameraTicks.size() - 1);
        String order = scene.stream().filter(r -> (r.type().equals("ClientboundAddEntityPacket") && r.info().endsWith("#" + gm))
            || (r.type().equals("ClientboundRemoveEntitiesPacket") && r.info().contains(Integer.toString(gm)))).map(r -> r.type().replace("Clientbound", "").replace("Packet", "") + "@" + r.tick())
            .toList().toString();
        boolean shownDuring = scene.stream().anyMatch(r -> r.type().equals("ClientboundAddEntityPacket") && r.info().endsWith("#" + gm) && r.tick() > sceneStart && r.tick() < sceneEnd);
        boolean shownAfter = scene.stream().anyMatch(r -> r.type().equals("ClientboundAddEntityPacket") && r.info().endsWith("#" + gm) && r.tick() >= sceneEnd);
        t.check("the real Gamemaster is not shown to the viewer while the scene plays (scene ticks " + sceneStart + " to " + sceneEnd + ")", !shownDuring, "his packets: " + order);
        t.check("...and is shown to them again once it is over", shownAfter, "his packets: " + order);
        ServerPlayer back = t.on(() -> t.server.getPlayerList().getPlayerByName(name));
        t.check("at the end the bot is back where it started, in its old game mode", t.on(() -> back.gameMode() == GameType.ADVENTURE && Math.abs(back.getX() - 0.5) < 1.5
            && Math.abs(back.getZ() - 0.5) < 1.5), t.on(() -> back.gameMode() + " at " + back.position()));
        t.check("the camera entity is gone", tagged(t, "villagerinterface_camera").isEmpty(), tagged(t, "villagerinterface_camera").size() + " left");
        boolean welcomed = t.until(() -> PacketLog.hasText(PacketLog.since(name, 0), "ClientboundSystemChatPacket", "Welcome to my arcade"), 400);
        t.check("the Gamemaster's welcome comes after the scene", welcomed, "");
        t.check("the bot is marked as met (no second scene)", t.on(() -> back.entityTags().contains("arcade_met")), "");
        t.check("exactly one Gamemaster entity exists after the scene", tagged(t, "arcade_gamemaster").size() == 1, tagged(t, "arcade_gamemaster").size() + "");
        Bots.kill(t, name);
    }

    private static void sneakSkips(T t) throws Exception {
        String name = Bots.PREFIX + "c2";
        Bots.spawn(t, name, 0.5, 101, 0.5, true);
        boolean started = t.until(() -> {
            ServerPlayer p = t.server.getPlayerList().getPlayerByName(name);
            return p.isSpectator() && p.getCamera() != p;
        }, 400);
        t.check("skip: the scene starts for a second first-time bot", started, "");
        if (!started) {
            Bots.kill(t, name);
            return;
        }
        t.ticks(20);
        int at = t.tick();
        t.run(() -> {
            ServerPlayer p = t.server.getPlayerList().getPlayerByName(name);
            p.connection.handlePlayerInput(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, true, false)));
        });
        boolean skipped = t.until(() -> {
            ServerPlayer p = t.server.getPlayerList().getPlayerByName(name);
            return !p.isSpectator() && p.getCamera() == p;
        }, 100);
        t.check("sneaking skips the scene (back to normal within " + (t.tick() - at) + " ticks)", skipped, "");
        Bots.kill(t, name);
    }
}
