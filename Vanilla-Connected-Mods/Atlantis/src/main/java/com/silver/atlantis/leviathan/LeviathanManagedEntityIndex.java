package com.silver.atlantis.leviathan;

import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class LeviathanManagedEntityIndex {

    private static final Map<UUID, Entity> managedLoadedByIdIndex = new HashMap<>();
    private static final Map<UUID, UUID> managedEntityUuidToIdIndex = new HashMap<>();
    private static boolean bootstrapped;
    private static boolean hooksRegistered;

    private LeviathanManagedEntityIndex() {
    }

    static void registerHooks() {
        if (hooksRegistered) {
            return;
        }

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> onEntityLoaded(entity));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> onEntityUnloaded(entity));
        hooksRegistered = true;
    }

    static void reset() {
        managedLoadedByIdIndex.clear();
        managedEntityUuidToIdIndex.clear();
        bootstrapped = false;
    }

    static Map<UUID, Entity> snapshot(ServerWorld world) {
        if (!bootstrapped) {
            bootstrap(world);
        }

        Map<UUID, Entity> loaded = new HashMap<>();
        List<UUID> staleIds = new ArrayList<>();
        for (Map.Entry<UUID, Entity> entry : managedLoadedByIdIndex.entrySet()) {
            UUID id = entry.getKey();
            Entity entity = entry.getValue();
            if (entity == null || !entity.isAlive() || entity.getEntityWorld() != world || !isManaged(entity)) {
                staleIds.add(id);
                continue;
            }
            loaded.put(id, entity);
            managedEntityUuidToIdIndex.put(entity.getUuid(), id);
        }

        for (UUID staleId : staleIds) {
            Entity removed = managedLoadedByIdIndex.remove(staleId);
            if (removed != null) {
                managedEntityUuidToIdIndex.remove(removed.getUuid());
            }
        }

        return loaded;
    }

    private static void bootstrap(ServerWorld world) {
        managedLoadedByIdIndex.clear();
        managedEntityUuidToIdIndex.clear();
        for (Entity entity : world.iterateEntities()) {
            indexManagedEntity(entity, true);
        }
        bootstrapped = true;
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] managed index bootstrapped count={}", managedLoadedByIdIndex.size());
    }

    private static void onEntityLoaded(Entity entity) {
        indexManagedEntity(entity, false);
    }

    private static void onEntityUnloaded(Entity entity) {
        if (entity == null) {
            return;
        }

        UUID managedId = managedEntityUuidToIdIndex.remove(entity.getUuid());
        if (managedId != null) {
            managedLoadedByIdIndex.remove(managedId);
            return;
        }

        if (!isManaged(entity)) {
            return;
        }

        LeviathanIdTags.getId(entity).ifPresent(managedLoadedByIdIndex::remove);
    }

    private static void indexManagedEntity(Entity entity, boolean allowAssignId) {
        if (entity == null || !isManaged(entity)) {
            return;
        }

        Optional<UUID> id = LeviathanIdTags.getId(entity);
        if (id.isEmpty()) {
            if (!allowAssignId) {
                return;
            }
            UUID assigned = UUID.randomUUID();
            entity.addCommandTag(LeviathanIdTags.toTag(assigned));
            id = Optional.of(assigned);
            AtlantisMod.LOGGER.warn("[Atlantis][leviathan] managed entity missing id tag; assigned id={} uuid={}",
                shortId(assigned),
                entity.getUuidAsString());
        }

        UUID managedId = id.get();
        managedLoadedByIdIndex.put(managedId, entity);
        managedEntityUuidToIdIndex.put(entity.getUuid(), managedId);
    }

    private static boolean isManaged(Entity entity) {
        return entity.getCommandTags().contains(LeviathanManager.MANAGED_TAG);
    }

    private static String shortId(UUID id) {
        String s = id.toString();
        return s.substring(0, 8);
    }
}
