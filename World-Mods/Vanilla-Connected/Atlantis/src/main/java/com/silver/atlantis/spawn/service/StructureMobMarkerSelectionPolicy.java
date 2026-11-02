package com.silver.atlantis.spawn.service;

import com.silver.atlantis.spawn.config.SpawnMobConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class StructureMobMarkerSelectionPolicy {

    List<ProximitySpawnService.SpawnMarker> selectMarkers(List<ProximitySpawnService.SpawnMarker> markers, Random random) {
        int maxNonBoss = SpawnMobConfig.MAX_NON_BOSS_PER_RUN;
        int maxAir = SpawnMobConfig.MAX_AIR_PER_RUN;

        List<ProximitySpawnService.SpawnMarker> bosses = new ArrayList<>();
        List<ProximitySpawnService.SpawnMarker> nonBoss = new ArrayList<>();
        List<ProximitySpawnService.SpawnMarker> air = new ArrayList<>();

        for (ProximitySpawnService.SpawnMarker marker : markers) {
            if (marker.spawnType() == SpawnMobConfig.SpawnType.BOSS) {
                bosses.add(marker);
            } else if (marker.spawnType() == SpawnMobConfig.SpawnType.AIR) {
                air.add(marker);
            } else {
                nonBoss.add(marker);
            }
        }

        Collections.shuffle(nonBoss, random);
        Collections.shuffle(air, random);

        int nonBossLimit = maxNonBoss <= 0 ? 0 : Math.min(maxNonBoss, nonBoss.size());
        int airLimit = maxAir <= 0 ? 0 : Math.min(maxAir, air.size());

        List<ProximitySpawnService.SpawnMarker> selected = new ArrayList<>(bosses.size() + nonBossLimit + airLimit);
        selected.addAll(bosses);
        for (int i = 0; i < nonBossLimit; i++) {
            selected.add(nonBoss.get(i));
        }
        for (int i = 0; i < airLimit; i++) {
            selected.add(air.get(i));
        }
        return selected;
    }
}
