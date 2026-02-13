package com.silver.villagerinterface.villager;

import com.silver.villagerinterface.VillagerInterfaceMod;
import com.silver.villagerinterface.config.ConfigManager;
import com.silver.villagerinterface.config.VillagerConfigEntry;
import com.silver.villagerinterface.config.VillagerInterfaceConfig;
import com.silver.villagerinterface.config.VillagerPosition;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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
        int intervalTicks = Math.max(20, config.checkIntervalSeconds() * 20);
        if (server.getTicks() - lastCheckTick < intervalTicks) {
            return;
        }

        lastCheckTick = server.getTicks();
        ensureVillagers(server);
    }

    public boolean isCustomVillager(VillagerEntity villager) {
        return getCustomId(villager) != null;
    }

    public String getCustomId(VillagerEntity villager) {
        if (villager instanceof CustomVillagerData data) {
            return data.villagerinterface$getCustomId();
        }
        return null;
    }

    public VillagerConfigEntry getEntryForVillager(VillagerEntity villager) {
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
        ServerWorld world = server.getWorld(toWorldKey(entry.dimension()));
        if (world == null) {
            VillagerInterfaceMod.LOGGER.warn("Configured villager '{}' has unknown world {}", entry.id(), entry.dimension());
            return;
        }

        VillagerPosition position = entry.position();
        if (position != null) {
            BlockPos blockPos = BlockPos.ofFloored(position.x(), position.y(), position.z());
            long chunkKey = ChunkPos.toLong(blockPos.getX() >> 4, blockPos.getZ() >> 4);
            if (!world.isChunkLoaded(chunkKey)) {
                return;
            }
        }

        VillagerEntity villager = findCustomVillager(world, entry);
        if (villager == null) {
            spawnVillager(world, entry);
            return;
        }

        refreshVillagerState(villager, entry);
    }

    private VillagerEntity findCustomVillager(ServerWorld world, VillagerConfigEntry entry) {
        VillagerPosition pos = entry.position();
        Vec3d center = pos != null ? pos.toVec3d() : Vec3d.ZERO;
        Box searchBox = Box.of(center, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS);
        List<VillagerEntity> villagers = world.getEntitiesByClass(
            VillagerEntity.class,
            searchBox,
            villager -> Objects.equals(entry.id(), getCustomId(villager))
        );

        if (villagers.isEmpty()) {
            return null;
        }

        VillagerEntity primary = selectPrimaryVillager(villagers, center);
        for (VillagerEntity candidate : villagers) {
            if (candidate == primary) {
                continue;
            }
            candidate.discard();
            VillagerInterfaceMod.LOGGER.warn("Removed duplicate villager '{}' in {}", entry.id(), entry.dimension());
        }

        return primary;
    }

    private VillagerEntity selectPrimaryVillager(List<VillagerEntity> villagers, Vec3d target) {
        VillagerEntity primary = villagers.get(0);
        double bestDistance = squaredDistance(primary, target);
        for (int i = 1; i < villagers.size(); i++) {
            VillagerEntity candidate = villagers.get(i);
            double distance = squaredDistance(candidate, target);
            if (distance < bestDistance) {
                bestDistance = distance;
                primary = candidate;
            }
        }
        return primary;
    }

    private double squaredDistance(VillagerEntity villager, Vec3d target) {
        double dx = villager.getX() - target.x;
        double dy = villager.getY() - target.y;
        double dz = villager.getZ() - target.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void spawnVillager(ServerWorld world, VillagerConfigEntry entry) {
        VillagerEntity villager = EntityType.VILLAGER.create(world, SpawnReason.COMMAND);
        if (villager == null) {
            VillagerInterfaceMod.LOGGER.warn("Unable to create villager entity for {}", entry.id());
            return;
        }

        applyCustomId(villager, entry.id());
        refreshVillagerState(villager, entry);
        world.spawnEntity(villager);
        VillagerInterfaceMod.LOGGER.info("Spawned villager '{}' in {}", entry.id(), entry.dimension());
    }

    private void refreshVillagerState(VillagerEntity villager, VillagerConfigEntry entry) {
        if (entry.displayName() != null && !entry.displayName().isBlank()) {
            villager.setCustomName(Text.literal(entry.displayName()));
            villager.setCustomNameVisible(true);
        }

        VillagerPosition position = entry.position();
        if (position != null) {
            Vec3d target = position.toVec3d();
            Vec3d current = new Vec3d(villager.getX(), villager.getY(), villager.getZ());
            if (current.squaredDistanceTo(target) > 0.25) {
                if (villager.hasVehicle()) {
                    villager.stopRiding();
                }
                villager.refreshPositionAndAngles(target.x, target.y, target.z, entry.yaw(), entry.pitch());
                villager.setVelocity(Vec3d.ZERO);
            }
        }

        villager.setAiDisabled(true);
        villager.setPersistent();
    }

    private void applyCustomId(VillagerEntity villager, String id) {
        if (villager instanceof CustomVillagerData data) {
            data.villagerinterface$setCustomId(id);
        }
    }

    private RegistryKey<World> toWorldKey(String dimensionId) {
        Identifier id = Identifier.tryParse(dimensionId);
        if (id == null) {
            id = Identifier.of("minecraft", "overworld");
        }
        return RegistryKey.of(RegistryKeys.WORLD, id);
    }
}
