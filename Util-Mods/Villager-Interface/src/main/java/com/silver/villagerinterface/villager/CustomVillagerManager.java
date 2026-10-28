package com.silver.villagerinterface.villager;

import com.silver.villagerinterface.VillagerInterfaceMod;
import com.silver.villagerinterface.config.ConfigManager;
import com.silver.villagerinterface.config.VillagerConfigEntry;
import com.silver.villagerinterface.config.VillagerInterfaceConfig;
import com.silver.villagerinterface.config.VillagerPosition;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;

/**
 * Keeps each configured NPC in the world at its spot: a villager (the original kind) or a mannequin (player-shaped,
 * skinned from the server's resource pack). Villagers carry their id through VillagerMixin; mannequins carry it as an
 * entity tag ({@link #NPC_TAG_PREFIX} + id).
 */
public final class CustomVillagerManager {
    private static final double SEARCH_RADIUS = 32.0;
    public static final String NPC_TAG_PREFIX = "villagerinterface_npc:";

    private final ConfigManager configManager;
    private long lastCheckTick;

    public CustomVillagerManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void onServerStarted(MinecraftServer server) {
        ensureVillagers(server);
    }

    public void onServerTick(MinecraftServer server) {
        VillagerInterfaceConfig config = configManager.getConfig();
        int intervalTicks = Math.max(20, config.conversation().checkIntervalSeconds() * 20);
        if (server.getTickCount() - lastCheckTick < intervalTicks) {
            return;
        }

        lastCheckTick = server.getTickCount();
        ensureVillagers(server);
    }

    public boolean isCustomVillager(Villager villager) {
        return getCustomId(villager) != null;
    }

    public boolean isCustomNpc(Entity entity) {
        return getCustomId(entity) != null;
    }

    public String getCustomId(Entity entity) {
        if (entity instanceof CustomVillagerData data && data.villagerinterface$getCustomId() != null) {
            return data.villagerinterface$getCustomId();
        }
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(NPC_TAG_PREFIX)) {
                return tag.substring(NPC_TAG_PREFIX.length());
            }
        }
        return null;
    }

    public VillagerConfigEntry getEntryForVillager(Entity villager) {
        String id = getCustomId(villager);
        if (id == null) {
            return null;
        }

        for (VillagerConfigEntry entry : configManager.getConfig().villagers()) {
            if (id.equals(entry.id())) {
                return entry;
            }
        }

        return null;
    }

    public void ensureVillagers(MinecraftServer server) {
        for (VillagerConfigEntry entry : configManager.getConfig().villagers()) {
            ensureVillager(server, entry);
        }
    }

    private void ensureVillager(MinecraftServer server, VillagerConfigEntry entry) {
        ServerLevel world = server.getLevel(toWorldKey(entry.dimension()));
        if (world == null) {
            VillagerInterfaceMod.LOGGER.warn("Configured villager '{}' has unknown world {}", entry.id(), entry.dimension());
            return;
        }

        VillagerPosition position = entry.position();
        if (position != null) {
            BlockPos blockPos = BlockPos.containing(position.x(), position.y(), position.z());
            if (!world.hasChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4)) {
                return;
            }
        }

        LivingEntity npc = findCustomNpc(world, entry);
        if (npc == null) {
            spawnNpc(world, entry);
            return;
        }

        refreshState(npc, entry);
    }

    private LivingEntity findCustomNpc(ServerLevel world, VillagerConfigEntry entry) {
        VillagerPosition pos = entry.position();
        Vec3 center = pos != null ? pos.toVec3() : Vec3.ZERO;
        AABB searchBox = new AABB(
            center.x - SEARCH_RADIUS, center.y - SEARCH_RADIUS, center.z - SEARCH_RADIUS,
            center.x + SEARCH_RADIUS, center.y + SEARCH_RADIUS, center.z + SEARCH_RADIUS
        );
        Class<? extends LivingEntity> kind = entry.isMannequin() ? Mannequin.class : Villager.class;
        List<? extends LivingEntity> found = world.getEntities(
            EntityTypeTest.forClass(kind),
            searchBox,
            npc -> Objects.equals(entry.id(), getCustomId(npc))
        );

        if (found.isEmpty()) {
            return null;
        }

        LivingEntity primary = found.get(0);
        for (LivingEntity candidate : found) {
            if (squaredDistance(candidate, center) < squaredDistance(primary, center)) {
                primary = candidate;
            }
        }
        for (LivingEntity candidate : found) {
            if (candidate == primary) {
                continue;
            }
            candidate.discard();
            VillagerInterfaceMod.LOGGER.warn("Removed duplicate NPC '{}' in {}", entry.id(), entry.dimension());
        }

        return primary;
    }

    private double squaredDistance(Entity entity, Vec3 target) {
        double dx = entity.getX() - target.x;
        double dy = entity.getY() - target.y;
        double dz = entity.getZ() - target.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void spawnNpc(ServerLevel world, VillagerConfigEntry entry) {
        LivingEntity npc = entry.isMannequin() ? createMannequin(world, entry) : createVillager(world, entry);
        if (npc == null) {
            VillagerInterfaceMod.LOGGER.warn("Unable to create the {} for NPC {}", entry.entity(), entry.id());
            return;
        }
        refreshState(npc, entry);
        world.addFreshEntity(npc);
        refreshState(npc, entry);
        VillagerInterfaceMod.LOGGER.info("Spawned {} '{}' in {}", entry.entity(), entry.id(), entry.dimension());
    }

    private Villager createVillager(ServerLevel world, VillagerConfigEntry entry) {
        @SuppressWarnings("unchecked")
        EntityType<Villager> villagerType = (EntityType<Villager>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:villager"));
        Villager villager = villagerType.create(world, EntitySpawnReason.COMMAND);
        if (villager != null && villager instanceof CustomVillagerData data) {
            data.villagerinterface$setCustomId(entry.id());
        }
        return villager;
    }

    /** A mannequin from its saved form: the skin, immovable, the description line hidden, tagged with the NPC id. */
    private Mannequin createMannequin(ServerLevel world, VillagerConfigEntry entry) {
        StringBuilder snbt = new StringBuilder("{immovable:1b,hide_description:1b,Invulnerable:1b,PersistenceRequired:1b,profile:{");
        if (entry.skin() != null && !entry.skin().isBlank()) {
            snbt.append("texture:").append(quote(entry.skin())).append(',');
        }
        snbt.append("model:").append(entry.slim() ? "\"slim\"" : "\"wide\"").append("}}");
        try {
            CompoundTag tag = TagParser.parseCompoundFully(snbt.toString());
            Entity loaded = EntityType.loadEntityRecursive(net.minecraft.world.entity.EntityTypes.MANNEQUIN, tag, world, EntitySpawnReason.COMMAND, entity -> entity);
            if (loaded instanceof Mannequin mannequin) {
                mannequin.addTag(NPC_TAG_PREFIX + entry.id());
                return mannequin;
            }
        } catch (Exception failure) {
            VillagerInterfaceMod.LOGGER.error("Could not create the mannequin for NPC {}", entry.id(), failure);
        }
        return null;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void refreshState(LivingEntity npc, VillagerConfigEntry entry) {
        if (entry.displayName() != null && !entry.displayName().isBlank()) {
            npc.setCustomName(Component.literal(entry.displayName()));
            npc.setCustomNameVisible(true);
        }

        VillagerPosition position = entry.position();
        if (position != null) {
            Vec3 target = position.toVec3();
            Vec3 current = new Vec3(npc.getX(), npc.getY(), npc.getZ());
            if (current.distanceToSqr(target) > 0.25) {
                if (npc.isPassenger()) {
                    npc.stopRiding();
                }
                npc.setPos(target.x, target.y, target.z);
                npc.setDeltaMovement(Vec3.ZERO);
            }

            npc.setYRot(entry.yaw());
            npc.setXRot(entry.pitch());
            npc.setYBodyRot(entry.yaw());
            npc.setYHeadRot(entry.yaw());
        }

        if (npc instanceof Villager villager) {
            villager.setNoAi(true);
            villager.setPersistenceRequired();
        } else {
            npc.setInvulnerable(true);
        }
    }

    private ResourceKey<Level> toWorldKey(String dimensionId) {
        Identifier id = Identifier.tryParse(dimensionId);
        if (id == null) {
            id = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        }
        return ResourceKey.create(Registries.DIMENSION, id);
    }
}
