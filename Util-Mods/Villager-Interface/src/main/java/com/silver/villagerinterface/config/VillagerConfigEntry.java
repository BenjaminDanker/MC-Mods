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
    /** "villager" (default) or "mannequin" (a player-shaped NPC; its skin comes from the server's resource pack). */
    private final String entity;
    /** Mannequins: the skin texture in the resource pack, e.g. "villagerinterface:entity/npc/foreman". */
    private final String skin;
    /** Mannequins: the slim (Alex) arm model. */
    private final boolean slim;
    /** The first line this NPC says; empty for the default. */
    private final String greeting;
    /** Right-clicking this NPC while holding a story's Seal turns it in (Magitech). */
    private final boolean sealTurnIn;
    /** Villagers: their profession, e.g. "minecraft:librarian" (empty keeps none). */
    private final String profession;
    /** Villagers: their biome look, e.g. "minecraft:desert" (empty keeps plains). */
    private final String biome;

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
        this(id, villagerType, displayName, dimension, position, yaw, pitch, maxDistance, systemPrompt, "villager", null, false, null, false, null, null);
    }

    public VillagerConfigEntry(
        String id,
        String villagerType,
        String displayName,
        String dimension,
        VillagerPosition position,
        float yaw,
        float pitch,
        double maxDistance,
        String systemPrompt,
        String entity,
        String skin,
        boolean slim,
        String greeting,
        boolean sealTurnIn,
        String profession,
        String biome
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
        this.entity = entity;
        this.skin = skin;
        this.slim = slim;
        this.greeting = greeting;
        this.sealTurnIn = sealTurnIn;
        this.profession = profession;
        this.biome = biome;
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

    public String entity() {
        return entity == null || entity.isBlank() ? "villager" : entity.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean isMannequin() {
        return "mannequin".equals(entity());
    }

    public String skin() {
        return skin;
    }

    public boolean slim() {
        return slim;
    }

    public String greeting() {
        return greeting;
    }

    public boolean sealTurnIn() {
        return sealTurnIn;
    }

    public String profession() {
        return profession;
    }

    public String biome() {
        return biome;
    }
}
