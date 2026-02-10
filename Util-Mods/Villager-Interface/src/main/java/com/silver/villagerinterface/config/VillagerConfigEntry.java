package com.silver.villagerinterface.config;

public final class VillagerConfigEntry {
    private final String id;
    private final String villagerType;
    private final String displayName;
    private final String dimension;
    private final VillagerPosition position;
    private final float yaw;
    private final float pitch;
    private final double maxDistance;
    private final String systemPrompt;

    public VillagerConfigEntry(
        String id,
        String villagerType,
        String displayName,
        String dimension,
        VillagerPosition position,
        float yaw,
        float pitch,
        double maxDistance,
        String systemPrompt
    ) {
        this.id = id;
        this.villagerType = villagerType;
        this.displayName = displayName;
        this.dimension = dimension;
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.maxDistance = maxDistance;
        this.systemPrompt = systemPrompt;
    }

    public String id() {
        return id;
    }

    public String villagerType() {
        return villagerType;
    }

    public String displayName() {
        return displayName;
    }

    public String dimension() {
        return dimension;
    }

    public VillagerPosition position() {
        return position;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public double maxDistance() {
        return maxDistance;
    }

    public String systemPrompt() {
        return systemPrompt;
    }
}
