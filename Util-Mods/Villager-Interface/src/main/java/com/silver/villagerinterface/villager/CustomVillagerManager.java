package com.silver.villagerinterface.villager;

import com.silver.villagerinterface.VillagerInterfaceMod;
import com.silver.villagerinterface.config.ConfigManager;
import com.silver.villagerinterface.config.VillagerConfigEntry;
import com.silver.villagerinterface.config.VillagerInterfaceConfig;
import com.silver.villagerinterface.config.VillagerPosition;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
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

public final class CustomVillagerManager {
    private static final double SEARCH_RADIUS = 32.0;

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

    public String getCustomId(Villager villager) {
        if (villager instanceof CustomVillagerData data) {
            return data.villagerinterface$getCustomId();
        }
        return null;
    }

    public VillagerConfigEntry getEntryForVillager(Villager villager) {
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

        Villager villager = findCustomVillager(world, entry);
        if (villager == null) {
            spawnVillager(world, entry);
            return;
        }

        refreshVillagerState(villager, entry);
    }

    private Villager findCustomVillager(ServerLevel world, VillagerConfigEntry entry) {
        VillagerPosition pos = entry.position();
        Vec3 center = pos != null ? pos.toVec3() : Vec3.ZERO;
        AABB searchBox = new AABB(
            center.x - SEARCH_RADIUS, center.y - SEARCH_RADIUS, center.z - SEARCH_RADIUS,
            center.x + SEARCH_RADIUS, center.y + SEARCH_RADIUS, center.z + SEARCH_RADIUS
        );
        List<Villager> villagers = world.getEntities(
            EntityTypeTest.forClass(Villager.class),
            searchBox,
            villager -> Objects.equals(entry.id(), getCustomId(villager))
        );

        if (villagers.isEmpty()) {
            return null;
        }

        Villager primary = selectPrimaryVillager(villagers, center);
        for (Villager candidate : villagers) {
            if (candidate == primary) {
                continue;
            }
            candidate.discard();
            VillagerInterfaceMod.LOGGER.warn("Removed duplicate villager '{}' in {}", entry.id(), entry.dimension());
        }

        return primary;
    }

    private Villager selectPrimaryVillager(List<Villager> villagers, Vec3 target) {
        Villager primary = villagers.get(0);
        double bestDistance = squaredDistance(primary, target);
        for (int i = 1; i < villagers.size(); i++) {
            Villager candidate = villagers.get(i);
            double distance = squaredDistance(candidate, target);
            if (distance < bestDistance) {
                bestDistance = distance;
                primary = candidate;
            }
        }
        return primary;
    }

    private double squaredDistance(Villager villager, Vec3 target) {
        double dx = villager.getX() - target.x;
        double dy = villager.getY() - target.y;
        double dz = villager.getZ() - target.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void spawnVillager(ServerLevel world, VillagerConfigEntry entry) {
        @SuppressWarnings("unchecked")
        EntityType<Villager> villagerType = (EntityType<Villager>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:villager"));
        Villager villager = villagerType.create(world, EntitySpawnReason.COMMAND);
        if (villager == null) {
            VillagerInterfaceMod.LOGGER.warn("Unable to create villager entity for {}", entry.id());
            return;
        }

        applyCustomId(villager, entry.id());
        refreshVillagerState(villager, entry);
        world.addFreshEntity(villager);
        refreshVillagerState(villager, entry);
        VillagerInterfaceMod.LOGGER.info("Spawned villager '{}' in {}", entry.id(), entry.dimension());
    }

    private void refreshVillagerState(Villager villager, VillagerConfigEntry entry) {
        if (entry.displayName() != null && !entry.displayName().isBlank()) {
            villager.setCustomName(Component.literal(entry.displayName()));
            villager.setCustomNameVisible(true);
        }

        VillagerPosition position = entry.position();
        if (position != null) {
            Vec3 target = position.toVec3();
            Vec3 current = new Vec3(villager.getX(), villager.getY(), villager.getZ());
            if (current.distanceToSqr(target) > 0.25) {
                if (villager.isPassenger()) {
                    villager.stopRiding();
                }
                villager.setPos(target.x, target.y, target.z);
                villager.setDeltaMovement(Vec3.ZERO);
            }

            applyRotation(villager, entry.yaw(), entry.pitch());
        }

        villager.setNoAi(true);
        villager.setPersistenceRequired();
    }

    private void applyRotation(Villager villager, float yaw, float pitch) {
        villager.setYRot(yaw);
        villager.setXRot(pitch);
        villager.setYBodyRot(yaw);
        villager.setYHeadRot(yaw);
    }

    private void applyCustomId(Villager villager, String id) {
        if (villager instanceof CustomVillagerData data) {
            data.villagerinterface$setCustomId(id);
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
