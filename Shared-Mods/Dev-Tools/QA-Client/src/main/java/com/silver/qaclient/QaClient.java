package com.silver.qaclient;

import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * QA client: a real game client that connects to a test server and plays it the way a person would (a QaScenario from
 * another mod's "qaclient" entrypoint) while recording the HUD, chunk state and screenshots. System properties:
 * qaclient.out (output dir), qaclient.server (host:port), qaclient.scenario (which scenario, if several are installed).
 */
public final class QaClient implements ClientModInitializer {
    public static Bot bot;
    private static boolean connecting, scenarioStarted;

    @Override
    public void onInitializeClient() {
        Rec.dir = Path.of(System.getProperty("qaclient.out", "qa-out"));
        Hud.init();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Rec.tick++;
            if (bot == null) {
                bot = new Bot();
            }
            if (mc.player != null && mc.level != null) {
                Hud.sample(mc);
            }
            bot.tickPulse();
            if (!connecting && mc.gui.screen() instanceof TitleScreen && mc.gui.overlay() == null) {
                connecting = true;
                String addr = System.getProperty("qaclient.server", "127.0.0.1:25595");
                ServerData data = new ServerData("qa", addr, ServerData.Type.OTHER);
                data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
                Rec.event("connecting", "addr", addr);
                ConnectScreen.startConnecting(mc.gui.screen(), mc, ServerAddress.parseString(addr), data, false, null);
            }
            if (!scenarioStarted && mc.player != null && mc.level != null) {
                scenarioStarted = true;
                Thread t = new Thread(() -> {
                    try {
                        scenario().run(bot);
                    } catch (Throwable e) {
                        Rec.event("scenario_crash", "error", e.toString());
                        e.printStackTrace();
                    } finally {
                        Rec.event("done");
                        mc.execute(mc::stop);
                    }
                }, "qaclient-scenario");
                t.setDaemon(true);
                t.start();
            }
        });
    }

    private static QaScenario scenario() {
        List<QaScenario> all = FabricLoader.getInstance().getEntrypoints("qaclient", QaScenario.class);
        String wanted = System.getProperty("qaclient.scenario");
        for (QaScenario s : all) {
            if (wanted == null ? all.size() == 1 : s.name().equals(wanted)) {
                return s;
            }
        }
        throw new IllegalStateException("no QA scenario " + (wanted == null ? "(installed: " : wanted + " (installed: ")
            + all.stream().map(QaScenario::name).toList() + ")");
    }
}
