package com.silver.atlantis.spawn.config;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.spawn.drop.SpawnSpecialConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Hard-coded mob difficulty catalogs for Atlantis spawn points.
 *
 * The same position-derived difficulty value is fed into each category:
 * mob type, health, effects, armor, weapon, and special drop amount.
 */
public final class SpawnMobConfig {

    private SpawnMobConfig() {
    }

    public static final int YEAR_TICKS = 630_720_000;

    /** Max non-boss spawns per /structuremob run (<= 0 disables the cap). */
    public static final int MAX_NON_BOSS_PER_RUN = 3000;

    /** Max air-mob spawns per /structuremob run (<= 0 disables air mobs). */
    public static final int MAX_AIR_PER_RUN = 30;

    /** Minimum continuous air-column height directly below glass required for AIR mob placement. */
    public static final int MIN_AIR_STRETCH_TO_GLASS_BLOCKS = 16;

    /** Max spawn attempts processed per tick for gradual /structuremob execution. */
    public static final int MAX_SPAWN_ATTEMPTS_PER_TICK = 96;

    /** Per-tick time budget for /structuremob execution (nanoseconds). */
    public static final long SPAWN_TICK_BUDGET_NANOS = 4_000_000L;

    /** Enables extra diagnostics for chunk tickets and spawn density troubleshooting. */
    public static final boolean DIAGNOSTIC_LOGS = true;
    
    /** Minimum horizontal spacing between non-boss spawns (<= 0 disables spacing). */
    public static final int MIN_SPAWN_SEPARATION_BLOCKS = 6;

    /** Percentage randomness applied when deriving a per-category budget from position difficulty. */
    public static final double CATEGORY_BUDGET_RANDOMNESS_PERCENT = 8.0;

    /** Top-percent candidate band used for random single-option picks near the budget cap. */
    public static final double SINGLE_PICK_TOP_BAND_PERCENT = 35.0;

    /** Scales the mob-type budget so high-tier mobs appear at high position difficulty. */
    public static final double MOB_TYPE_BUDGET_MULTIPLIER = 1.60;

    /** Probability to pick the highest affordable mob type directly. */
    public static final double MOB_TYPE_PICK_HIGHEST_CHANCE = 0.75;

    /** For skeleton/stray, probability to prefer a bow over melee when a bow is affordable. */
    public static final double BOW_FAVOR_CHANCE_FOR_SKELETON_TYPES = 0.75;

    /** Scales effect budget so high-difficulty mobs can roll multiple effects more often. */
    public static final double EFFECT_BUDGET_SCALE = 1.25;

    /** Difficulty band where effect-less rolls are still common; above this, effects become consistent. */
    public static final int EFFECT_OPTIONAL_UNTIL_DIFFICULTY = 16;

    /** Portion of armor budget reserved for base armor pieces before enchantment spend. */
    public static final double ARMOR_BASE_BUDGET_BIAS = 0.78;

    /** Maximum extra enchantments allowed per armor piece when the mob has multiple armor pieces. */
    public static final int MAX_ARMOR_ENCHANTS_PER_PIECE = 2;

    /** If true, leftover armor-enchant budget can continue spending after balanced limits are reached. */
    public static final boolean ALLOW_ARMOR_ENCHANT_OVERFLOW = true;

    /** Difficulty at or above which creepers can spawn charged. */
    public static final int POWERED_CREEPER_MIN_DIFFICULTY = 22;

    /** Difficulty where charged creeper explosion radius starts scaling above vanilla default. */
    public static final int POWERED_CREEPER_RADIUS_SCALE_MIN_DIFFICULTY = 30;

    /** Max charged creeper explosion radius applied by Atlantis scaling. */
    public static final int POWERED_CREEPER_MAX_EXPLOSION_RADIUS = 10;

    public enum SpawnType {
        LAND,
        WATER,
        AIR,
        BOSS
    }

    public record EnchantmentConfig(String enchantmentId, int level) {
    }

    public record EquipmentConfig(String slot, String itemId, List<EnchantmentConfig> enchantments) {
    }

    public record EffectConfig(String effectId, int amplifier, boolean ambient, boolean showParticles) {
    }

    public record MobTypeConfig(int difficulty, String entityId) {
    }

    public record HealthConfig(int difficulty, double health) {
    }

    public record EffectOption(int difficulty, EffectConfig effect) {
    }

    public record ArmorOption(int difficulty, EquipmentConfig equipment) {
    }

    public record WeaponOption(int difficulty, EquipmentConfig equipment) {
    }

    public record SpecialDropOption(int difficulty, int amount) {
    }

    public record ArmorEnchantOption(int difficulty, String slot, EnchantmentConfig enchantment) {
    }

    public record WeaponEnchantOption(int difficulty, String weaponKind, EnchantmentConfig enchantment) {
    }

    public record MobLoadout(
        String entityId,
        double health,
        List<EquipmentConfig> equipment,
        List<EffectConfig> effects,
        int specialDropAmount,
        boolean creeperPowered,
        int creeperExplosionRadius
    ) {
    }

    private record Catalog(
        List<MobTypeConfig> mobTypes,
        List<HealthConfig> health,
        List<EffectOption> effects,
        List<ArmorOption> armor,
        List<WeaponOption> weapons,
        List<ArmorEnchantOption> armorEnchantments,
        List<WeaponEnchantOption> weaponEnchantments,
        List<SpecialDropOption> specialDrops,
        int maxDifficulty
    ) {
    }

    private record ArmorLoadout(int difficulty, List<EquipmentConfig> equipment) {
    }

    private record ArmorEnchantCandidate(int pieceIndex, ArmorEnchantOption option) {
    }

    private static final Map<SpawnType, Catalog> CATALOGS = buildCatalogs();

    private static Map<SpawnType, Catalog> buildCatalogs() {
        Map<SpawnType, Catalog> catalogs = new HashMap<>();

        catalogs.put(SpawnType.LAND, new Catalog(
            List.of(
                new MobTypeConfig(9, "minecraft:skeleton"),
                new MobTypeConfig(10, "minecraft:zombie"),
                new MobTypeConfig(13, "minecraft:husk"),
                new MobTypeConfig(15, "minecraft:cave_spider"),
                new MobTypeConfig(17, "minecraft:bogged"),
                new MobTypeConfig(19, "minecraft:witch"),
                new MobTypeConfig(22, "minecraft:creeper"),
                new MobTypeConfig(20, "minecraft:stray"),
                new MobTypeConfig(22, "minecraft:blaze"),
                new MobTypeConfig(24, "minecraft:vindicator"),
                new MobTypeConfig(26, "minecraft:zombified_piglin"),
                new MobTypeConfig(28, "minecraft:evoker"),
                new MobTypeConfig(30, "minecraft:piglin_brute")
            ),
            List.of(
                new HealthConfig(4, 14),
                new HealthConfig(8, 20),
                new HealthConfig(12, 28),
                new HealthConfig(18, 40),
                new HealthConfig(24, 56),
                new HealthConfig(30, 76),
                new HealthConfig(36, 100)
            ),
            List.of(
                new EffectOption(3, new EffectConfig("minecraft:speed", 0, false, true)),
                new EffectOption(5, new EffectConfig("minecraft:strength", 0, false, true)),
                new EffectOption(6, new EffectConfig("minecraft:resistance", 0, false, true)),
                new EffectOption(8, new EffectConfig("minecraft:regeneration", 0, false, true)),
                new EffectOption(10, new EffectConfig("minecraft:strength", 1, false, true)),
                new EffectOption(12, new EffectConfig("minecraft:resistance", 1, false, true))
            ),
            List.of(
                new ArmorOption(3, equipment("head", "minecraft:iron_helmet")),
                new ArmorOption(4, equipment("chest", "minecraft:iron_chestplate")),
                new ArmorOption(3, equipment("legs", "minecraft:iron_leggings")),
                new ArmorOption(2, equipment("feet", "minecraft:iron_boots")),
                new ArmorOption(6, equipment("head", "minecraft:diamond_helmet", ench("minecraft:protection", 2))),
                new ArmorOption(8, equipment("chest", "minecraft:diamond_chestplate", ench("minecraft:protection", 2))),
                new ArmorOption(6, equipment("legs", "minecraft:diamond_leggings", ench("minecraft:protection", 2))),
                new ArmorOption(4, equipment("feet", "minecraft:diamond_boots", ench("minecraft:protection", 2)))
            ),
            List.of(
                new WeaponOption(2, equipment("mainhand", "minecraft:stone_sword")),
                new WeaponOption(4, equipment("mainhand", "minecraft:iron_sword")),
                new WeaponOption(5, equipment("mainhand", "minecraft:bow")),
                new WeaponOption(7, equipment("mainhand", "minecraft:diamond_sword", ench("minecraft:sharpness", 2))),
                new WeaponOption(9, equipment("mainhand", "minecraft:bow", ench("minecraft:power", 2))),
                new WeaponOption(11, equipment("mainhand", "minecraft:netherite_sword", ench("minecraft:sharpness", 3))),
                new WeaponOption(13, equipment("mainhand", "minecraft:bow", ench("minecraft:power", 4))),
                new WeaponOption(14, equipment("mainhand", "minecraft:netherite_axe", ench("minecraft:sharpness", 4)))
            ),
            List.of(
                new ArmorEnchantOption(6, "head", ench("minecraft:protection", 1)),
                new ArmorEnchantOption(6, "chest", ench("minecraft:protection", 1)),
                new ArmorEnchantOption(6, "legs", ench("minecraft:protection", 1)),
                new ArmorEnchantOption(6, "feet", ench("minecraft:feather_falling", 2)),
                new ArmorEnchantOption(12, "head", ench("minecraft:protection", 3)),
                new ArmorEnchantOption(12, "chest", ench("minecraft:projectile_protection", 3)),
                new ArmorEnchantOption(14, "legs", ench("minecraft:protection", 3)),
                new ArmorEnchantOption(14, "feet", ench("minecraft:feather_falling", 4)),
                new ArmorEnchantOption(22, "head", ench("minecraft:protection", 4)),
                new ArmorEnchantOption(24, "chest", ench("minecraft:thorns", 2)),
                new ArmorEnchantOption(22, "legs", ench("minecraft:protection", 4)),
                new ArmorEnchantOption(20, "feet", ench("minecraft:depth_strider", 3))
            ),
            List.of(
                new WeaponEnchantOption(5, "any_melee", ench("minecraft:sharpness", 1)),
                new WeaponEnchantOption(7, "sword", ench("minecraft:knockback", 1)),
                new WeaponEnchantOption(8, "bow", ench("minecraft:power", 2)),
                new WeaponEnchantOption(10, "bow", ench("minecraft:punch", 1)),
                new WeaponEnchantOption(12, "any_melee", ench("minecraft:sharpness", 3)),
                new WeaponEnchantOption(12, "bow", ench("minecraft:power", 3)),
                new WeaponEnchantOption(14, "sword", ench("minecraft:fire_aspect", 1)),
                new WeaponEnchantOption(16, "bow", ench("minecraft:flame", 1)),
                new WeaponEnchantOption(22, "any_melee", ench("minecraft:sharpness", 5)),
                new WeaponEnchantOption(24, "sword", ench("minecraft:fire_aspect", 2)),
                new WeaponEnchantOption(24, "sword", ench("minecraft:sweeping_edge", 3)),
                new WeaponEnchantOption(24, "bow", ench("minecraft:power", 5)),
                new WeaponEnchantOption(26, "bow", ench("minecraft:punch", 2))
            ),
            List.of(
                new SpecialDropOption(4, 1),
                new SpecialDropOption(10, 2),
                new SpecialDropOption(18, 4),
                new SpecialDropOption(24, 6),
                new SpecialDropOption(30, 9),
                new SpecialDropOption(36, 12)
            ),
            36
        ));

        catalogs.put(SpawnType.WATER, new Catalog(
            List.of(
                new MobTypeConfig(4, "minecraft:drowned"),
                new MobTypeConfig(12, "minecraft:guardian")
            ),
            List.of(
                new HealthConfig(4, 18),
                new HealthConfig(12, 30),
                new HealthConfig(18, 46),
                new HealthConfig(24, 68),
                new HealthConfig(30, 90),
                new HealthConfig(36, 120)
            ),
            List.of(
                new EffectOption(4, new EffectConfig("minecraft:water_breathing", 0, false, true)),
                new EffectOption(5, new EffectConfig("minecraft:dolphins_grace", 0, false, true)),
                new EffectOption(6, new EffectConfig("minecraft:resistance", 0, false, true)),
                new EffectOption(8, new EffectConfig("minecraft:strength", 0, false, true)),
                new EffectOption(10, new EffectConfig("minecraft:resistance", 1, false, true)),
                new EffectOption(12, new EffectConfig("minecraft:strength", 1, false, true))
            ),
            List.of(
                new ArmorOption(2, equipment("head", "minecraft:turtle_helmet")),
                new ArmorOption(3, equipment("chest", "minecraft:chainmail_chestplate")),
                new ArmorOption(2, equipment("legs", "minecraft:chainmail_leggings")),
                new ArmorOption(1, equipment("feet", "minecraft:chainmail_boots")),
                new ArmorOption(5, equipment("head", "minecraft:diamond_helmet", ench("minecraft:respiration", 2))),
                new ArmorOption(7, equipment("chest", "minecraft:diamond_chestplate", ench("minecraft:protection", 2))),
                new ArmorOption(5, equipment("legs", "minecraft:diamond_leggings", ench("minecraft:protection", 2))),
                new ArmorOption(3, equipment("feet", "minecraft:diamond_boots", ench("minecraft:depth_strider", 2)))
            ),
            List.of(
                new WeaponOption(3, equipment("mainhand", "minecraft:trident")),
                new WeaponOption(6, equipment("mainhand", "minecraft:trident", ench("minecraft:impaling", 2))),
                new WeaponOption(10, equipment("mainhand", "minecraft:trident", ench("minecraft:impaling", 4)))
            ),
            List.of(
                new ArmorEnchantOption(5, "head", ench("minecraft:respiration", 1)),
                new ArmorEnchantOption(6, "feet", ench("minecraft:depth_strider", 1)),
                new ArmorEnchantOption(10, "head", ench("minecraft:respiration", 3)),
                new ArmorEnchantOption(11, "chest", ench("minecraft:protection", 3)),
                new ArmorEnchantOption(11, "legs", ench("minecraft:protection", 3)),
                new ArmorEnchantOption(10, "feet", ench("minecraft:depth_strider", 3)),
                new ArmorEnchantOption(18, "head", ench("minecraft:aqua_affinity", 1)),
                new ArmorEnchantOption(20, "chest", ench("minecraft:thorns", 3)),
                new ArmorEnchantOption(18, "legs", ench("minecraft:protection", 4)),
                new ArmorEnchantOption(18, "feet", ench("minecraft:depth_strider", 3))
            ),
            List.of(
                new WeaponEnchantOption(6, "trident", ench("minecraft:impaling", 2)),
                new WeaponEnchantOption(8, "trident", ench("minecraft:loyalty", 1)),
                new WeaponEnchantOption(12, "trident", ench("minecraft:impaling", 4)),
                new WeaponEnchantOption(14, "trident", ench("minecraft:loyalty", 3)),
                new WeaponEnchantOption(16, "trident", ench("minecraft:channeling", 1)),
                new WeaponEnchantOption(22, "trident", ench("minecraft:impaling", 5)),
                new WeaponEnchantOption(24, "trident", ench("minecraft:riptide", 3)),
                new WeaponEnchantOption(24, "trident", ench("minecraft:channeling", 1))
            ),
            List.of(
                new SpecialDropOption(4, 1),
                new SpecialDropOption(10, 2),
                new SpecialDropOption(18, 4),
                new SpecialDropOption(24, 7),
                new SpecialDropOption(30, 10),
                new SpecialDropOption(36, 14)
            ),
            36
        ));

        catalogs.put(SpawnType.AIR, new Catalog(
            List.of(
                new MobTypeConfig(34, "minecraft:phantom"),
                new MobTypeConfig(34, "minecraft:ghast")
            ),
            List.of(
                new HealthConfig(18, 28),
                new HealthConfig(24, 42),
                new HealthConfig(30, 62),
                new HealthConfig(36, 88)
            ),
            List.of(
                new EffectOption(10, new EffectConfig("minecraft:speed", 0, false, true)),
                new EffectOption(12, new EffectConfig("minecraft:strength", 0, false, true)),
                new EffectOption(14, new EffectConfig("minecraft:resistance", 0, false, true)),
                new EffectOption(20, new EffectConfig("minecraft:speed", 1, false, true)),
                new EffectOption(24, new EffectConfig("minecraft:strength", 1, false, true))
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new SpecialDropOption(24, 2),
                new SpecialDropOption(30, 4),
                new SpecialDropOption(36, 7)
            ),
            36
        ));

        catalogs.put(SpawnType.BOSS, new Catalog(
            List.of(
                new MobTypeConfig(36, "minecraft:ravager")
            ),
            List.of(
                new HealthConfig(36, 280)
            ),
            List.of(
                new EffectOption(8, new EffectConfig("minecraft:strength", 1, false, true)),
                new EffectOption(10, new EffectConfig("minecraft:resistance", 1, false, true)),
                new EffectOption(12, new EffectConfig("minecraft:regeneration", 1, false, true))
            ),
            List.of(),
            List.of(),
            List.of(
                new ArmorEnchantOption(20, "head", ench("minecraft:protection", 4)),
                new ArmorEnchantOption(22, "chest", ench("minecraft:protection", 4)),
                new ArmorEnchantOption(22, "legs", ench("minecraft:protection", 4)),
                new ArmorEnchantOption(20, "feet", ench("minecraft:feather_falling", 4)),
                new ArmorEnchantOption(28, "head", ench("minecraft:respiration", 3)),
                new ArmorEnchantOption(30, "chest", ench("minecraft:thorns", 3)),
                new ArmorEnchantOption(30, "legs", ench("minecraft:protection", 4)),
                new ArmorEnchantOption(28, "feet", ench("minecraft:depth_strider", 3))
            ),
            List.of(
                new WeaponEnchantOption(20, "any_melee", ench("minecraft:sharpness", 4)),
                new WeaponEnchantOption(22, "sword", ench("minecraft:fire_aspect", 2)),
                new WeaponEnchantOption(26, "any_melee", ench("minecraft:sharpness", 5)),
                new WeaponEnchantOption(30, "sword", ench("minecraft:sweeping_edge", 3))
            ),
            List.of(
                new SpecialDropOption(20, 8),
                new SpecialDropOption(28, 14),
                new SpecialDropOption(36, 20)
            ),
            36
        ));

        return catalogs;
    }

    public static int maxDifficultyFor(SpawnType spawnType) {
        Catalog catalog = CATALOGS.get(spawnType);
        if (catalog == null) {
            return 1;
        }
        return Math.max(1, catalog.maxDifficulty());
    }

    public static MobLoadout buildLoadout(SpawnType spawnType, int positionDifficulty, Random random, boolean rollSpecialDrops) {
        Catalog catalog = CATALOGS.get(spawnType);
        if (catalog == null) {
            return null;
        }

        int difficulty = Math.max(minBaselineDifficulty(catalog), Math.min(positionDifficulty, catalog.maxDifficulty()));

        MobTypeConfig mobType = pickMobType(catalog.mobTypes(), difficulty, random);
        HealthConfig health = pickAtOrBelow(catalog.health(), difficulty, HealthConfig::difficulty, random, true);

        if (mobType == null || health == null) {
            return null;
        }

        List<EffectConfig> effects = pickEffectSet(catalog.effects(), difficulty, random);
        int armorBudgetTotal = randomizedBudget(difficulty, random);
        int armorBaseBudget = Math.max(1, (int) Math.round(armorBudgetTotal * ARMOR_BASE_BUDGET_BIAS));
        ArmorLoadout armorLoadout = pickArmorLoadout(catalog.armor(), armorBaseBudget, random);
        WeaponOption weaponOption = pickWeaponOption(catalog.weapons(), difficulty, random, mobType.entityId());
        List<EquipmentConfig> armor = armorLoadout.equipment();
        EquipmentConfig weapon = weaponOption == null ? null : weaponOption.equipment();

        if (weapon != null && isBowItem(weapon.itemId()) && !isBowEligibleMob(mobType.entityId())) {
            AtlantisMod.LOGGER.warn("[SpawnDiag] Prevented illegal bow assignment: entityId={} weapon={}", mobType.entityId(), weapon.itemId());
            weapon = null;
            weaponOption = null;
        }

        int armorEnchantBudget = Math.max(0, armorBudgetTotal - armorLoadout.difficulty());
        armor = applyArmorEnchantmentRolls(catalog.armorEnchantments(), armor, armorEnchantBudget, random);

        int weaponBudget = randomizedBudget(difficulty, random);
        int weaponBaseDifficulty = weaponOption == null ? 0 : weaponOption.difficulty();
        int weaponEnchantBudget = Math.max(0, weaponBudget - weaponBaseDifficulty);
        weapon = applyWeaponEnchantmentRolls(catalog.weaponEnchantments(), weapon, weaponEnchantBudget, random);

        int specialDropAmount = rollSpecialDrops ? pickSpecialDropAmount(spawnType, catalog, difficulty, random) : 0;
        boolean creeperPowered = false;
        int creeperExplosionRadius = 0;

        if ("minecraft:creeper".equalsIgnoreCase(mobType.entityId())) {
            creeperPowered = difficulty >= POWERED_CREEPER_MIN_DIFFICULTY;
            if (creeperPowered) {
                if (difficulty >= POWERED_CREEPER_RADIUS_SCALE_MIN_DIFFICULTY) {
                    double progress = (difficulty - POWERED_CREEPER_RADIUS_SCALE_MIN_DIFFICULTY)
                        / (double) Math.max(1, catalog.maxDifficulty() - POWERED_CREEPER_RADIUS_SCALE_MIN_DIFFICULTY);
                    progress = Math.max(0.0, Math.min(1.0, progress));
                    int scaled = 3 + (int) Math.round(progress * (POWERED_CREEPER_MAX_EXPLOSION_RADIUS - 3));
                    creeperExplosionRadius = Math.max(3, Math.min(POWERED_CREEPER_MAX_EXPLOSION_RADIUS, scaled));
                } else {
                    creeperExplosionRadius = 3;
                }
            }
        }

        List<EquipmentConfig> equipment = new ArrayList<>(armor.size() + (weapon == null ? 0 : 1));
        equipment.addAll(armor);
        if (weapon != null) {
            equipment.add(weapon);
        }
        return new MobLoadout(
            mobType.entityId(),
            health.health(),
            equipment,
            effects,
            specialDropAmount,
            creeperPowered,
            creeperExplosionRadius
        );
    }

    private static List<EquipmentConfig> applyArmorEnchantmentRolls(
        List<ArmorEnchantOption> options,
        List<EquipmentConfig> armor,
        int budget,
        Random random
    ) {
        if (armor == null || armor.isEmpty()) {
            return List.of();
        }

        List<EquipmentConfig> result = copyEquipmentList(armor);
        if (options == null || options.isEmpty() || budget <= 0) {
            return result;
        }

        int remaining = budget;
        int maxPerPiece = result.size() <= 1 ? 1 : MAX_ARMOR_ENCHANTS_PER_PIECE;
        boolean overflowMode = false;
        while (remaining > 0) {
            List<ArmorEnchantCandidate> candidates = collectArmorEnchantCandidates(
                options,
                result,
                remaining,
                maxPerPiece,
                !overflowMode
            );

            if (candidates.isEmpty() && !overflowMode && ALLOW_ARMOR_ENCHANT_OVERFLOW) {
                overflowMode = true;
                continue;
            }

            ArmorEnchantCandidate selected = pickAtOrBelow(candidates, remaining, c -> c.option().difficulty(), random, false);
            if (selected == null) {
                break;
            }

            int index = selected.pieceIndex();
            ArmorEnchantOption option = selected.option();
            result.set(index, addEnchantment(result.get(index), option.enchantment()));
            remaining -= option.difficulty();
        }

        return result;
    }

    private static List<ArmorEnchantCandidate> collectArmorEnchantCandidates(
        List<ArmorEnchantOption> options,
        List<EquipmentConfig> armor,
        int remaining,
        int maxPerPiece,
        boolean enforceSpread
    ) {
        List<ArmorEnchantCandidate> candidates = new ArrayList<>();
        int minEnchantCount = Integer.MAX_VALUE;

        if (enforceSpread) {
            for (EquipmentConfig eq : armor) {
                int count = eq.enchantments() == null ? 0 : eq.enchantments().size();
                minEnchantCount = Math.min(minEnchantCount, count);
            }
        }

        for (int pieceIndex = 0; pieceIndex < armor.size(); pieceIndex++) {
            EquipmentConfig eq = armor.get(pieceIndex);
            int currentCount = eq.enchantments() == null ? 0 : eq.enchantments().size();
            if (!ALLOW_ARMOR_ENCHANT_OVERFLOW || enforceSpread) {
                if (currentCount >= maxPerPiece) {
                    continue;
                }
            }
            if (enforceSpread && currentCount > minEnchantCount) {
                continue;
            }

            String slot = eq.slot();
            for (ArmorEnchantOption option : options) {
                if (option.difficulty() > remaining) {
                    continue;
                }
                if (!slot.equalsIgnoreCase(option.slot())) {
                    continue;
                }
                if (hasEnchantment(eq.enchantments(), option.enchantment().enchantmentId())) {
                    continue;
                }
                candidates.add(new ArmorEnchantCandidate(pieceIndex, option));
            }
        }

        return candidates;
    }

    private static EquipmentConfig applyWeaponEnchantmentRolls(
        List<WeaponEnchantOption> options,
        EquipmentConfig weapon,
        int budget,
        Random random
    ) {
        if (weapon == null || options == null || options.isEmpty() || budget <= 0) {
            return weapon;
        }

        String weaponKind = weaponKindForItem(weapon.itemId());
        int remaining = budget;
        EquipmentConfig current = weapon;

        while (remaining > 0) {
            List<WeaponEnchantOption> candidates = new ArrayList<>();
            for (WeaponEnchantOption option : options) {
                if (option.difficulty() > remaining) {
                    continue;
                }
                if (!isWeaponEnchantCompatible(option.weaponKind(), weaponKind)) {
                    continue;
                }
                if (hasEnchantment(current.enchantments(), option.enchantment().enchantmentId())) {
                    continue;
                }
                candidates.add(option);
            }

            WeaponEnchantOption selected = pickAtOrBelow(candidates, remaining, WeaponEnchantOption::difficulty, random, false);
            if (selected == null) {
                break;
            }

            current = addEnchantment(current, selected.enchantment());
            remaining -= selected.difficulty();
        }

        return current;
    }

    private static String weaponKindForItem(String itemId) {
        if (itemId == null) {
            return "unknown";
        }

        String path = itemId.toLowerCase();
        int colon = path.indexOf(':');
        if (colon >= 0 && colon + 1 < path.length()) {
            path = path.substring(colon + 1);
        }

        if (path.endsWith("_sword")) {
            return "sword";
        }
        if (path.endsWith("_axe")) {
            return "axe";
        }
        if ("bow".equals(path) || (path.endsWith("_bow") && !path.endsWith("crossbow"))) {
            return "bow";
        }
        if (path.endsWith("crossbow")) {
            return "crossbow";
        }
        if (path.equals("trident")) {
            return "trident";
        }
        return "any_melee";
    }

    private static boolean isWeaponEnchantCompatible(String expectedKind, String actualKind) {
        if (expectedKind == null || expectedKind.isBlank()) {
            return false;
        }
        if ("any".equalsIgnoreCase(expectedKind)) {
            return true;
        }
        if ("any_melee".equalsIgnoreCase(expectedKind)) {
            return "sword".equalsIgnoreCase(actualKind)
                || "axe".equalsIgnoreCase(actualKind)
                || "any_melee".equalsIgnoreCase(actualKind);
        }
        return expectedKind.equalsIgnoreCase(actualKind);
    }

    private static boolean hasEnchantment(List<EnchantmentConfig> enchantments, String id) {
        if (id == null || id.isBlank() || enchantments == null || enchantments.isEmpty()) {
            return false;
        }

        for (EnchantmentConfig enchantment : enchantments) {
            if (enchantment != null && id.equalsIgnoreCase(enchantment.enchantmentId())) {
                return true;
            }
        }
        return false;
    }

    private static EquipmentConfig addEnchantment(EquipmentConfig equipment, EnchantmentConfig enchantment) {
        List<EnchantmentConfig> merged = new ArrayList<>();
        if (equipment.enchantments() != null) {
            merged.addAll(equipment.enchantments());
        }
        merged.add(enchantment);
        return new EquipmentConfig(equipment.slot(), equipment.itemId(), merged);
    }

    private static List<EquipmentConfig> copyEquipmentList(List<EquipmentConfig> equipment) {
        List<EquipmentConfig> result = new ArrayList<>(equipment.size());
        for (EquipmentConfig eq : equipment) {
            List<EnchantmentConfig> copied = new ArrayList<>();
            if (eq.enchantments() != null) {
                copied.addAll(eq.enchantments());
            }
            result.add(new EquipmentConfig(eq.slot(), eq.itemId(), copied));
        }
        return result;
    }

    private static List<EffectConfig> pickEffectSet(List<EffectOption> options, int difficulty, Random random) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }

        int minEffectDifficulty = minDifficulty(options, EffectOption::difficulty);
        if (difficulty <= minEffectDifficulty) {
            return List.of();
        }

        if (difficulty < EFFECT_OPTIONAL_UNTIL_DIFFICULTY) {
            double progress = (difficulty - minEffectDifficulty)
                / (double) Math.max(1, EFFECT_OPTIONAL_UNTIL_DIFFICULTY - minEffectDifficulty);
            progress = Math.max(0.0, Math.min(1.0, progress));

            // At low difficulty, keep a meaningful chance of no effects.
            double noEffectChance = 0.65 * (1.0 - progress);
            if (random.nextDouble() < noEffectChance) {
                return List.of();
            }
        }

        int effectBudget = Math.max(1, (int) Math.round(difficulty * EFFECT_BUDGET_SCALE));
        List<EffectOption> chosen = pickSubsetAtOrBelow(options, effectBudget, EffectOption::difficulty, random, false);
        List<EffectConfig> effects = new ArrayList<>(chosen.size());
        for (EffectOption option : chosen) {
            effects.add(option.effect());
        }
        return effects;
    }

    private static ArmorLoadout pickArmorLoadout(List<ArmorOption> options, int targetDifficulty, Random random) {
        List<ArmorLoadout> loadouts = enumerateArmorLoadouts(options);
        List<ArmorLoadout> nonEmpty = new ArrayList<>();
        for (ArmorLoadout loadout : loadouts) {
            if (!loadout.equipment().isEmpty()) {
                nonEmpty.add(loadout);
            }
        }

        if (nonEmpty.isEmpty()) {
            return new ArmorLoadout(0, List.of());
        }

        List<ArmorLoadout> underBudget = new ArrayList<>();
        ArmorLoadout lowest = nonEmpty.get(0);
        for (ArmorLoadout loadout : nonEmpty) {
            if (loadout.difficulty() <= targetDifficulty) {
                underBudget.add(loadout);
            }
            if (loadout.difficulty() < lowest.difficulty()) {
                lowest = loadout;
            }
        }

        if (underBudget.isEmpty()) {
            return lowest;
        }

        underBudget.sort((a, b) -> Double.compare(
            armorLoadoutScore(b, targetDifficulty),
            armorLoadoutScore(a, targetDifficulty)
        ));

        int bandSize = Math.max(1, (int) Math.ceil(underBudget.size() * (SINGLE_PICK_TOP_BAND_PERCENT / 100.0)));
        return underBudget.get(random.nextInt(bandSize));
    }

    private static double armorLoadoutScore(ArmorLoadout loadout, int targetDifficulty) {
        if (loadout == null) {
            return 0.0;
        }

        double difficultyRatio = Math.max(0.0, Math.min(1.0, loadout.difficulty() / (double) Math.max(1, targetDifficulty)));
        double pieceRatio = Math.max(0.0, Math.min(1.0, loadout.equipment().size() / 4.0));
        return (difficultyRatio * 0.70) + (pieceRatio * 0.30);
    }

    private static WeaponOption pickWeaponOption(List<WeaponOption> options, int targetDifficulty, Random random, String entityId) {
        if (options == null || options.isEmpty()) {
            return null;
        }

        if (!canUseWeapons(entityId)) {
            return null;
        }

        boolean bowEligible = isBowEligibleMob(entityId);
        List<WeaponOption> allowed = new ArrayList<>();
        List<WeaponOption> bowOnly = new ArrayList<>();

        for (WeaponOption option : options) {
            boolean isBow = isBowItem(option.equipment().itemId());
            if (isBow && !bowEligible) {
                continue;
            }

            allowed.add(option);
            if (isBow) {
                bowOnly.add(option);
            }
        }

        if (allowed.isEmpty()) {
            return null;
        }

        if (bowEligible && !bowOnly.isEmpty() && random.nextDouble() < BOW_FAVOR_CHANCE_FOR_SKELETON_TYPES) {
            WeaponOption favoredBow = pickAtOrBelow(bowOnly, targetDifficulty, WeaponOption::difficulty, random, false);
            if (favoredBow != null) {
                return favoredBow;
            }
        }

        return pickAtOrBelow(allowed, targetDifficulty, WeaponOption::difficulty, random, true);
    }

    private static boolean isBowItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }

        String path = itemId.toLowerCase();
        int colon = path.indexOf(':');
        if (colon >= 0 && colon + 1 < path.length()) {
            path = path.substring(colon + 1);
        }

        return "bow".equals(path) || (path.endsWith("_bow") && !path.endsWith("crossbow"));
    }

    private static boolean isBowEligibleMob(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return false;
        }

        return "minecraft:skeleton".equalsIgnoreCase(entityId)
            || "minecraft:stray".equalsIgnoreCase(entityId);
    }

    private static boolean canUseWeapons(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return true;
        }

        return !"minecraft:blaze".equalsIgnoreCase(entityId)
            && !"minecraft:witch".equalsIgnoreCase(entityId)
            && !"minecraft:evoker".equalsIgnoreCase(entityId);
    }

    private static MobTypeConfig pickMobType(List<MobTypeConfig> options, int targetDifficulty, Random random) {
        if (options == null || options.isEmpty()) {
            return null;
        }

        int scaledTarget = Math.max(1, (int) Math.round(targetDifficulty * MOB_TYPE_BUDGET_MULTIPLIER));
        int budget = randomizedBudget(scaledTarget, random);

        MobTypeConfig lowest = options.get(0);
        for (MobTypeConfig option : options) {
            if (option.difficulty() < lowest.difficulty()) {
                lowest = option;
            }
        }

        List<MobTypeConfig> affordable = new ArrayList<>();

        for (MobTypeConfig option : options) {
            if (option.difficulty() <= budget) {
                affordable.add(option);
            }
        }

        if (affordable.isEmpty()) {
            return lowest;
        }

        affordable.sort(Comparator.comparingInt(MobTypeConfig::difficulty));

        int highestDifficulty = affordable.get(affordable.size() - 1).difficulty();
        if (random.nextDouble() < MOB_TYPE_PICK_HIGHEST_CHANCE) {
            return pickRandomAmongDifficulty(affordable, highestDifficulty, random);
        }

        int bandSize = Math.max(1, (int) Math.ceil(affordable.size() * (SINGLE_PICK_TOP_BAND_PERCENT / 100.0)));
        int start = Math.max(0, affordable.size() - bandSize);

        // Include all ties at the band boundary so equal-difficulty entries (e.g. phantom/ghast)
        // don't get deterministically excluded by stable sort ordering.
        int boundaryDifficulty = affordable.get(start).difficulty();
        while (start > 0 && affordable.get(start - 1).difficulty() == boundaryDifficulty) {
            start--;
        }

        MobTypeConfig picked = affordable.get(start + random.nextInt(affordable.size() - start));
        return pickRandomAmongDifficulty(affordable, picked.difficulty(), random);
    }

    private static MobTypeConfig pickRandomAmongDifficulty(List<MobTypeConfig> options, int difficulty, Random random) {
        if (options == null || options.isEmpty()) {
            return null;
        }

        int count = 0;
        for (MobTypeConfig option : options) {
            if (option != null && option.difficulty() == difficulty) {
                count++;
            }
        }

        if (count <= 1) {
            for (MobTypeConfig option : options) {
                if (option != null && option.difficulty() == difficulty) {
                    return option;
                }
            }
            return options.get(options.size() - 1);
        }

        int pick = random.nextInt(count);
        for (MobTypeConfig option : options) {
            if (option != null && option.difficulty() == difficulty) {
                if (pick-- == 0) {
                    return option;
                }
            }
        }

        return options.get(options.size() - 1);
    }

    private static int pickSpecialDropAmount(SpawnType spawnType, Catalog catalog, int difficulty, Random random) {
        List<SpecialDropOption> options = catalog.specialDrops();
        SpecialDropOption special = pickAtOrBelow(options, difficulty, SpecialDropOption::difficulty, random, true);
        if (special == null) {
            return 0;
        }

        double averageOptionAmount = averageSpecialAmountAtOrBelow(options, difficulty);
        if (averageOptionAmount <= 0) {
            return 0;
        }

        int expectedNonBoss = Math.max(1, SpawnMobConfig.MAX_NON_BOSS_PER_RUN);
        int expectedBoss = Math.max(1, SpawnSpecialConfig.EXPECTED_BOSS_MOBS_PER_RUN);
        double targetItems = Math.max(1.0,
            SpawnSpecialConfig.SPECIAL_ITEMS_PER_SEA_LANTERN * SpawnSpecialConfig.TARGET_CONVERSIONS_PER_FULL_CLEAR
        );

        double expectedTotalMobs = expectedNonBoss + expectedBoss;
        double expectedPerMob = targetItems / expectedTotalMobs;

        double normalizedDifficulty = Math.max(0.0, Math.min(1.0, difficulty / (double) Math.max(1, catalog.maxDifficulty())));
        double curvedDifficulty = Math.pow(normalizedDifficulty, 1.75);
        double difficultyFactor = 1.00 + (0.55 * curvedDifficulty);

        double optionWeightFactor = special.amount() / averageOptionAmount;
        optionWeightFactor = Math.max(0.65, Math.min(1.50, optionWeightFactor));
        double bossFactor = spawnType == SpawnType.BOSS ? SpawnSpecialConfig.BOSS_SPECIAL_DROP_MULTIPLIER : 1.0;

        double expectedAmount = expectedPerMob * difficultyFactor * optionWeightFactor * bossFactor;

        double variance = SpawnSpecialConfig.SPECIAL_DROP_RANDOMNESS_PERCENT / 100.0;
        double varianceRoll = 1.0 + ((random.nextDouble() * 2.0 - 1.0) * variance);
        varianceRoll = Math.max(0.0, varianceRoll);

        double rolledAmount = expectedAmount * varianceRoll;
        int whole = (int) Math.floor(rolledAmount);
        double fractional = rolledAmount - whole;
        int amount = whole + (random.nextDouble() < fractional ? 1 : 0);
        if (spawnType == SpawnType.BOSS && amount <= 0) {
            amount = 1;
        }

        if (SpawnSpecialConfig.SPECIAL_DROP_DEBUG_LOGS) {
            AtlantisMod.LOGGER.info(
                "[SpecialDropRoll] type={} difficulty={} option={} expectedPerMob={} difficultyFactor={} optionFactor={} bossFactor={} expected={} varianceRoll={} rolled={} final={}",
                spawnType,
                difficulty,
                special.amount(),
                expectedPerMob,
                difficultyFactor,
                optionWeightFactor,
                bossFactor,
                expectedAmount,
                varianceRoll,
                rolledAmount,
                amount
            );
        }

        return Math.max(0, amount);
    }

    private static double averageSpecialAmount(List<SpecialDropOption> options) {
        if (options == null || options.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        for (SpecialDropOption option : options) {
            total += Math.max(0, option.amount());
        }
        return total / options.size();
    }

    private static double averageSpecialAmountAtOrBelow(List<SpecialDropOption> options, int difficulty) {
        if (options == null || options.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        int count = 0;
        for (SpecialDropOption option : options) {
            if (option.difficulty() <= difficulty) {
                total += Math.max(0, option.amount());
                count++;
            }
        }

        if (count <= 0) {
            return averageSpecialAmount(options);
        }
        return total / count;
    }

    private static List<ArmorLoadout> enumerateArmorLoadouts(List<ArmorOption> options) {
        if (options == null || options.isEmpty()) {
            return List.of(new ArmorLoadout(0, List.of()));
        }

        Map<String, List<ArmorOption>> bySlot = new HashMap<>();
        for (ArmorOption option : options) {
            String slot = option.equipment().slot().toLowerCase();
            bySlot.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(option);
        }

        List<String> slots = new ArrayList<>(bySlot.keySet());
        slots.sort(Comparator.naturalOrder());

        List<ArmorLoadout> results = new ArrayList<>();
        buildArmorLoadouts(slots, bySlot, 0, 0, new ArrayList<>(), results);
        return results;
    }

    private static void buildArmorLoadouts(
        List<String> slots,
        Map<String, List<ArmorOption>> bySlot,
        int index,
        int currentDifficulty,
        List<EquipmentConfig> currentEquipment,
        List<ArmorLoadout> results
    ) {
        if (index >= slots.size()) {
            results.add(new ArmorLoadout(currentDifficulty, List.copyOf(currentEquipment)));
            return;
        }

        String slot = slots.get(index);

        buildArmorLoadouts(slots, bySlot, index + 1, currentDifficulty, currentEquipment, results);

        List<ArmorOption> slotOptions = bySlot.get(slot);
        if (slotOptions == null || slotOptions.isEmpty()) {
            return;
        }

        for (ArmorOption option : slotOptions) {
            currentEquipment.add(option.equipment());
            buildArmorLoadouts(
                slots,
                bySlot,
                index + 1,
                currentDifficulty + option.difficulty(),
                currentEquipment,
                results
            );
            currentEquipment.remove(currentEquipment.size() - 1);
        }
    }

    private static <T> T pickAtOrBelow(
        List<T> options,
        int targetDifficulty,
        DifficultyGetter<T> difficultyGetter,
        Random random,
        boolean required
    ) {
        if (options == null || options.isEmpty()) {
            return null;
        }

        int budget = randomizedBudget(targetDifficulty, random);

        List<T> underBudget = new ArrayList<>();
        T lowest = options.get(0);
        int lowestDifficulty = difficultyGetter.getDifficulty(lowest);

        for (T option : options) {
            int difficulty = difficultyGetter.getDifficulty(option);

            if (difficulty <= budget) {
                underBudget.add(option);
            }

            if (difficulty < lowestDifficulty) {
                lowestDifficulty = difficulty;
                lowest = option;
            }
        }

        if (!underBudget.isEmpty()) {
            underBudget.sort(Comparator.comparingInt(difficultyGetter::getDifficulty));
            int bandSize = Math.max(1, (int) Math.ceil(underBudget.size() * (SINGLE_PICK_TOP_BAND_PERCENT / 100.0)));
            int start = Math.max(0, underBudget.size() - bandSize);
            return underBudget.get(start + random.nextInt(underBudget.size() - start));
        }

        return required ? lowest : null;
    }

    private static <T> List<T> pickSubsetAtOrBelow(
        List<T> options,
        int targetDifficulty,
        DifficultyGetter<T> difficultyGetter,
        Random random,
        boolean required
    ) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }

        int budget = randomizedBudget(targetDifficulty, random);
        List<T> pool = new ArrayList<>(options);
        shuffleStable(pool, random);
        pool.sort(Comparator.comparingInt(difficultyGetter::getDifficulty).reversed());

        List<T> picked = new ArrayList<>();
        int total = 0;

        for (T option : pool) {
            int optionDifficulty = difficultyGetter.getDifficulty(option);
            if (total + optionDifficulty <= budget) {
                picked.add(option);
                total += optionDifficulty;
            }
        }

        if (!picked.isEmpty()) {
            return picked;
        }

        if (required) {
            T smallest = options.get(0);
            int smallestDifficulty = difficultyGetter.getDifficulty(smallest);
            for (T option : options) {
                int difficulty = difficultyGetter.getDifficulty(option);
                if (difficulty < smallestDifficulty) {
                    smallestDifficulty = difficulty;
                    smallest = option;
                }
            }
            return List.of(smallest);
        }

        return List.of();
    }

    private static int randomizedBudget(int targetDifficulty, Random random) {
        int clamped = Math.max(1, targetDifficulty);
        double spread = CATEGORY_BUDGET_RANDOMNESS_PERCENT / 100.0;
        double offset = (random.nextDouble() * 2.0 - 1.0) * spread;
        double factor = Math.max(0.0, 1.0 + offset);
        return Math.max(1, (int) Math.round(clamped * factor));
    }

    private static int minBaselineDifficulty(Catalog catalog) {
        int min = 1;
        min = Math.max(min, minDifficulty(catalog.mobTypes(), MobTypeConfig::difficulty));
        min = Math.max(min, minDifficulty(catalog.health(), HealthConfig::difficulty));

        if (!catalog.effects().isEmpty()) {
            min = Math.max(min, minDifficulty(catalog.effects(), EffectOption::difficulty));
        }

        if (!catalog.weapons().isEmpty()) {
            min = Math.max(min, minDifficulty(catalog.weapons(), WeaponOption::difficulty));
        }

        if (!catalog.armor().isEmpty()) {
            min = Math.max(min, minDifficulty(catalog.armor(), ArmorOption::difficulty));
        }

        return min;
    }

    private static <T> int minDifficulty(List<T> options, DifficultyGetter<T> getter) {
        if (options == null || options.isEmpty()) {
            return 1;
        }

        int min = Integer.MAX_VALUE;
        for (T option : options) {
            min = Math.min(min, getter.getDifficulty(option));
        }
        return min == Integer.MAX_VALUE ? 1 : min;
    }

    private static <T> void shuffleStable(List<T> list, Random random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            if (i != j) {
                T tmp = list.get(i);
                list.set(i, list.get(j));
                list.set(j, tmp);
            }
        }
    }

    private static EquipmentConfig equipment(String slot, String itemId, EnchantmentConfig... enchantments) {
        List<EnchantmentConfig> list = new ArrayList<>();
        if (enchantments != null) {
            for (EnchantmentConfig enchantment : enchantments) {
                if (enchantment != null) {
                    list.add(enchantment);
                }
            }
        }
        return new EquipmentConfig(Objects.requireNonNull(slot), Objects.requireNonNull(itemId), list);
    }

    private static EnchantmentConfig ench(String id, int level) {
        return new EnchantmentConfig(id, level);
    }

    @FunctionalInterface
    private interface DifficultyGetter<T> {
        int getDifficulty(T option);
    }
}
