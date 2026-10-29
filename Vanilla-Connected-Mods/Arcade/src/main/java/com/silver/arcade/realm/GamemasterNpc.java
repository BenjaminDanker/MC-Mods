package com.silver.arcade.realm;

import java.util.List;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Gamemaster himself, floating over the hall: a god does not stand on the floor. Scripted, not an AI: right-click
 * him for a line of his (rules and hints in his own voice, never solutions). He drifts up and down and turns to whoever
 * is nearest.
 */
public final class GamemasterNpc {
    private static final Logger LOGGER = LoggerFactory.getLogger("arcade");
    static final String TAG = "arcade_gamemaster";
    /** Where he hovers (his feet), over the hall. */
    static final Vec3 HOME = new Vec3(4.5, 102.2, 0.5);
    /** The skin, from the arcade's resource pack (missing texture until the pack carries it). */
    private static final String SKIN = "arcade:entity/gamemaster";

    private static final List<String> LINES = List.of(
        "Every game here is fair. I checked. Mostly.",
        "Step onto the purple circle. Bring friends if you have any. If you don't, I'll be your friend. Briefly.",
        "After every room you choose: the gold takes your winnings home, the emerald takes you higher. I love the emerald.",
        "Forgot a rule mid-room? Type !explain. I never tire of explaining. Well. Rarely.",
        "Roll my die whenever you want to play. Lost it? My altar near your spawn keeps spares.",
        "The crying obsidian sends you home. It cries because you're leaving.",
        "No, I won't tell you what the next room is. Where's the fun in that?",
        "Lose a partner and I'll find you a room of your own. I'm generous like that.",
        "The higher you climb, the more your world will thank you. Your nerves, less so.");

    private final ServerLevel level;
    private final Random random = new Random();
    private Mannequin body;
    private int line;

    public GamemasterNpc(ServerLevel level) {
        this.level = level;
    }

    public void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer user) || !entity.entityTags().contains(TAG)) {
                return InteractionResult.PASS;
            }
            Gamemaster.say(user, LINES.get(line++ % LINES.size()));
            return InteractionResult.SUCCESS;
        });
    }

    /** Spawns him (removing any old copy); waits until the hall's entities are loaded and ticking. */
    public void ensure() {
        if (!level.isPositionEntityTicking(net.minecraft.core.BlockPos.containing(HOME))) {
            return;
        }
        AABB around = new AABB(-16, 90, -16, 16, 120, 16);
        level.getEntities(EntityTypeTest.forClass(Mannequin.class), around, e -> e.entityTags().contains(TAG)).forEach(Entity::discard);
        try {
            CompoundTag tag = TagParser.parseCompoundFully("{NoGravity:1b,Invulnerable:1b,immovable:1b,hide_description:1b,"
                + "PersistenceRequired:1b,CustomName:\"The Gamemaster\",CustomNameVisible:1b,profile:{texture:\"" + SKIN + "\",model:\"wide\"}}");
            Entity loaded = EntityType.loadEntityRecursive(EntityTypes.MANNEQUIN, tag, level, EntitySpawnReason.COMMAND, e -> e);
            if (loaded instanceof Mannequin mannequin) {
                mannequin.addTag(TAG);
                mannequin.snapTo(HOME.x, HOME.y, HOME.z, 90F, 0F);
                if (level.addFreshEntity(mannequin)) {
                    body = mannequin;
                    LOGGER.info("The Gamemaster hovers over the hall");
                } else {
                    LOGGER.warn("The Gamemaster could not be added to the world");
                }
            } else {
                LOGGER.warn("The Gamemaster's body did not load: {}", loaded);
            }
        } catch (Exception failure) {
            LOGGER.error("ALERT: could not create the Gamemaster", failure);
        }
    }

    /** Every tick: a slow float and a glance at the nearest player. */
    public void tick(long tick) {
        if (body == null || body.isRemoved()) {
            if (tick % 20 == 0) {
                ensure();
            }
            return;
        }
        double y = HOME.y + 0.35 * Math.sin(tick / 30.0);
        float yaw = body.getYRot();
        ServerPlayer nearest = null;
        double best = 144;
        for (ServerPlayer player : level.players()) {
            double d = player.distanceToSqr(body);
            if (d < best) {
                best = d;
                nearest = player;
            }
        }
        if (nearest != null) {
            yaw = (float) Math.toDegrees(Math.atan2(-(nearest.getX() - HOME.x), nearest.getZ() - HOME.z));
        }
        body.teleportTo(level, HOME.x, y, HOME.z, Set.of(), yaw, 0F, false);
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
        if (tick % 10 == 0) {
            level.sendParticles(random.nextBoolean() ? ParticleTypes.ENCHANT : ParticleTypes.END_ROD, HOME.x, y + 0.2, HOME.z, 2, 0.3, 0.1, 0.3, 0.01);
        }
    }

    public Vec3 position() {
        return body == null ? HOME : body.position();
    }

    public Component name() {
        return Component.literal("The Gamemaster");
    }
}
