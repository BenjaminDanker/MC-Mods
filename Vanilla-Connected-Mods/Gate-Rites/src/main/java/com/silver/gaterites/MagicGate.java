package com.silver.gaterites;

import com.silver.elementchoice.ElementChoiceMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import me.moros.bending.api.GameProvider;
import me.moros.bending.api.event.AbilityActivationEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Magic side. A player who has finished the Magic story and begins the rite with /skills ascend casts their pinnacle (an
 * ability at its highest level, or the Avatar State) and a portal opens before them: a glowing, spiky oval that floats where
 * they cast it. Only they can step through, and it vanishes as they do. Everyone else is turned away.
 */
final class MagicGate {
    private static final Logger LOGGER = LoggerFactory.getLogger("gate-rites");

    private static final class Open {
        final UUID caster;
        final ServerLevel level;
        final PortalShape shape;
        final Vec3 centre;
        final String pinnacle;
        final Display.ItemDisplay oval;
        final Display.ItemDisplay corona;
        final long closeAtTick;
        final Map<UUID, Long> lastRefusal = new HashMap<>();

        Open(UUID caster, ServerLevel level, Vec3 centre, String pinnacle, Display.ItemDisplay oval, Display.ItemDisplay corona, long closeAtTick) {
            this.caster = caster;
            this.level = level;
            this.centre = centre;
            this.shape = new PortalShape(centre.x, centre.y, centre.z);
            this.pinnacle = pinnacle;
            this.oval = oval;
            this.corona = corona;
            this.closeAtTick = closeAtTick;
        }
    }

    private final MinecraftServer server;
    private final GateConfig config;
    private final Crossing crossing;
    private final List<Open> open = new ArrayList<>();
    /** True while this gate is adding its own displays, which must not be mistaken for leftovers when they load. */
    private boolean spawning;

    MagicGate(MinecraftServer server, GateConfig config) {
        this.server = server;
        this.config = config;
        this.crossing = new Crossing(config);
        if (!PortalDisplay.available()) {
            LOGGER.error("ALERT: the magic gate is disabled because the portal cannot be drawn");
            return;
        }
        GameProvider.get().eventBus().subscribe(AbilityActivationEvent.class, this::onActivation);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerEntityEvents.ENTITY_LOAD.register(this::sweepLeftover);
        LOGGER.info("Magic gate active: a pinnacle cast opens a portal for {}s to {}", config.openSeconds, config.targetServer);
    }

    /** A portal display that survived a crash or restart belongs to no gate; remove it. */
    private void sweepLeftover(Entity entity, ServerLevel level) {
        if (!spawning && entity.entityTags().contains(PortalDisplay.TAG)) {
            server.execute(entity::discard);
        }
    }

    void shutdown() {
        for (Open gate : List.copyOf(open)) {
            remove(gate);
        }
    }

    private void onActivation(AbilityActivationEvent event) {
        UUID id = event.user().uuid();
        String ability = event.ability().key().value();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                return;
            }
            Optional<String> pinnacle = ElementChoiceMod.pinnacleCast(player, ability);
            pinnacle.ifPresent(realm -> openFor(player, realm));
        });
    }

    private void openFor(ServerPlayer player, String pinnacle) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        open.stream().filter(gate -> gate.caster.equals(player.getUUID())).toList().forEach(this::remove);
        Vec3 centre = placeInFront(level, player);
        spawning = true;
        try {
            Display.ItemDisplay oval = PortalDisplay.spawn(level, centre, player.getYRot(), "portal_" + pinnacle + "_oval", 2.2F);
            Display.ItemDisplay corona = PortalDisplay.spawn(level, centre, player.getYRot(), "portal_" + pinnacle + "_corona", 3.3F);
            open.add(new Open(player.getUUID(), level, centre, pinnacle, oval, corona, server.getTickCount() + config.openSeconds * 20L));
        } catch (RuntimeException failure) {
            LOGGER.error("ALERT: the portal for {} could not be created", player.getName().getString(), failure);
            player.sendSystemMessage(Component.literal("The gate will not open. Tell an admin.").withStyle(ChatFormatting.RED));
            return;
        } finally {
            spawning = false;
        }
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.7F);
        burst(level, centre, particleFor(pinnacle), 80);
        player.sendSystemMessage(Component.literal("The way opens before you. Step through; it will not wait for long.").withStyle(ChatFormatting.AQUA));
        LOGGER.info("{} cast a pinnacle ({}) and opened a gate at {}", player.getName().getString(), pinnacle, centre);
    }

    /** A free spot a few blocks ahead of the caster, as close as needed, with the oval's feet just off the ground. */
    private static Vec3 placeInFront(ServerLevel level, ServerPlayer player) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
        for (double distance : new double[] {3.0, 2.4, 1.8}) {
            Vec3 centre = new Vec3(player.getX() + forward.x * distance, player.getY() + 1.35, player.getZ() + forward.z * distance);
            AABB body = new AABB(centre.x - 0.5, centre.y - 1.1, centre.z - 0.5, centre.x + 0.5, centre.y + 1.1, centre.z + 0.5);
            if (level.noCollision(body)) {
                return centre;
            }
        }
        return new Vec3(player.getX() + forward.x * 1.4, player.getY() + 1.35, player.getZ() + forward.z * 1.4);
    }

    private void tick(MinecraftServer unused) {
        if (open.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (Iterator<Open> it = open.iterator(); it.hasNext();) {
            Open gate = it.next();
            ServerPlayer caster = server.getPlayerList().getPlayer(gate.caster);
            if (caster == null) {
                discard(gate);
                it.remove();
                LOGGER.info("The gate of an absent caster ({}) was removed", gate.caster);
                continue;
            }
            if (now >= gate.closeAtTick) {
                caster.sendSystemMessage(Component.literal("The way closes.").withStyle(ChatFormatting.GRAY));
                fade(gate);
                it.remove();
                continue;
            }
            if (now % 4 == 0) {
                gate.level.sendParticles(particleFor(gate.pinnacle), gate.centre.x, gate.centre.y, gate.centre.z, 3, 0.7, 1.0, 0.7, 0.02);
            }
            if (caster.level() == gate.level && gate.shape.contains(caster.getX(), caster.getY(), caster.getZ())) {
                if (crossing.send(caster)) {
                    LOGGER.info("{} stepped through their gate", caster.getName().getString());
                    enter(gate, caster);
                } else {
                    caster.sendSystemMessage(Component.literal("The gate flickers and will not carry you. Tell an admin.").withStyle(ChatFormatting.RED));
                    fade(gate);
                }
                it.remove();
                continue;
            }
            for (ServerPlayer other : gate.level.players()) {
                if (other != caster && gate.shape.near(other.getX(), other.getY(), other.getZ())) {
                    long last = gate.lastRefusal.getOrDefault(other.getUUID(), Long.MIN_VALUE / 2);
                    boolean tell = now - last >= Gate.REFUSAL_SPACING_TICKS;
                    if (tell) {
                        gate.lastRefusal.put(other.getUUID(), now);
                    }
                    Gate.refuse(other, other.position().subtract(gate.centre), tell);
                }
            }
        }
    }

    /** The caster is crossing: the portal collapses in a flash. */
    private void enter(Open gate, ServerPlayer caster) {
        gate.level.playSound(null, gate.centre.x, gate.centre.y, gate.centre.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
        burst(gate.level, gate.centre, particleFor(gate.pinnacle), 120);
        discard(gate);
    }

    private void fade(Open gate) {
        gate.level.playSound(null, gate.centre.x, gate.centre.y, gate.centre.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.0F);
        burst(gate.level, gate.centre, ParticleTypes.POOF, 30);
        discard(gate);
    }

    private void remove(Open gate) {
        open.remove(gate);
        discard(gate);
    }

    private static void discard(Open gate) {
        gate.oval.discard();
        gate.corona.discard();
    }

    private static void burst(ServerLevel level, Vec3 centre, ParticleOptions particle, int count) {
        level.sendParticles(particle, centre.x, centre.y, centre.z, count, 0.6, 1.0, 0.6, 0.15);
    }

    private static ParticleOptions particleFor(String pinnacle) {
        return switch (pinnacle) {
            case "fire" -> ParticleTypes.FLAME;
            case "water" -> ParticleTypes.BUBBLE_POP;
            case "earth" -> ParticleTypes.COMPOSTER;
            case "air" -> ParticleTypes.CLOUD;
            default -> ParticleTypes.END_ROD;
        };
    }
}
