package com.silver.aipets.fabric.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.HeldPlacement;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetAppearance;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.ResourceId;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.authority.TransitionResult;
import com.silver.aipets.common.transport.PetAdoptionWireRequest;
import com.silver.aipets.common.transport.PetAdoptionWireResult;
import com.silver.aipets.common.transport.PetAdoptionWireStatus;
import com.silver.aipets.common.transport.PetRecallWireResult;
import com.silver.aipets.common.transport.PetRecallWireStatus;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.PlacementState;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.authority.AuthorityMutationResult;
import com.silver.aipets.fabric.authority.PetAuthorityGateway;
import com.silver.aipets.fabric.authority.PetAuthoritySnapshot;
import com.silver.aipets.fabric.compass.PetCompassIssueResult;
import com.silver.aipets.fabric.compass.PetCompassIssueStatus;
import com.silver.aipets.fabric.compass.PetCompassItem;
import com.silver.aipets.fabric.compass.PetCompassManager;
import com.silver.aipets.fabric.config.PetPhysicalConfig;
import com.silver.aipets.fabric.entity.PetEntityController;
import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.entity.PetEntityFactory;
import com.silver.aipets.fabric.entity.PreparedPetEntity;
import com.silver.aipets.fabric.interaction.PetInteractionRouter;
import com.silver.aipets.fabric.mixin.WolfEntityVariantInvoker;
import com.silver.aipets.fabric.placement.PetPlacementCoordinator;
import com.silver.aipets.fabric.placement.PetPlacementOutcome;
import com.silver.aipets.fabric.placement.PetPlacementStatus;
import com.silver.aipets.fabric.placement.PetPickupCoordinator;
import com.silver.aipets.fabric.placement.PetPickupOutcome;
import com.silver.aipets.fabric.placement.PetPickupStatus;
import com.silver.aipets.fabric.placement.SafePlacementFinder;
import com.silver.aipets.fabric.permission.PetPermission;
import com.silver.aipets.fabric.permission.PetPermissions;
import com.silver.aipets.fabric.reconciliation.PeriodicPetReconciliation;
import com.silver.aipets.fabric.reconciliation.PetEntityReconciler;
import com.silver.aipets.fabric.reconciliation.PetReconciliationConfig;
import com.silver.aipets.fabric.reconciliation.PetEntityRecoveryCoordinator;
import com.silver.aipets.fabric.reconciliation.PetRecoveryStatus;
import com.silver.aipets.fabric.reconciliation.PetEntityReconciliationStatus;
import com.silver.aipets.fabric.transfer.PetTransferConfig;
import com.silver.aipets.fabric.transfer.PetTransferCoordinator;
import com.silver.aipets.fabric.transfer.PetTransferStatus;
import com.silver.aipets.service.adoption.AdoptionProfile;
import com.silver.aipets.service.adoption.AppearanceCatalog;
import com.silver.aipets.service.adoption.InitialMood;
import com.silver.aipets.service.adoption.PetRandomizer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.CatVariant;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.passive.WolfVariant;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.screen.slot.Slot;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

public final class PetPhysicalGameTests {
    private static final AppearanceRules APPEARANCE_RULES = AppearanceRules.defaults();
    private static final Instant ADOPTED_AT = Instant.parse("2026-08-30T12:00:00Z");
    private static final UUID OWNER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @GameTest
    public void randomizedAdoptionAppearanceMatchesRuntimeRegistries(TestContext context) {
        ServerWorld world = context.getWorld();
        AppearanceCatalog catalog = AppearanceCatalog.vanilla12110();
        Registry<CatVariant> cats = world.getRegistryManager().getOrThrow(RegistryKeys.CAT_VARIANT);
        Registry<WolfVariant> wolves = world.getRegistryManager().getOrThrow(RegistryKeys.WOLF_VARIANT);

        catalog.variantsFor(PetSpecies.CAT).forEach(variant ->
                assertRegistryContains(context, cats, variant));
        catalog.variantsFor(PetSpecies.DOG).forEach(variant ->
                assertRegistryContains(context, wolves, variant));

        PetRandomizer randomizer = new PetRandomizer(
                APPEARANCE_RULES,
                catalog,
                InitialMood.defaults(),
                new Random(0xA1C0FFEE));
        for (PetSpecies species : PetSpecies.values()) {
            for (int sample = 0; sample < 512; sample++) {
                AdoptionProfile profile = randomizer.generate(species, ADOPTED_AT);
                boolean registered = switch (species) {
                    case CAT -> cats.containsId(Identifier.of(profile.appearance().variantId().value()));
                    case DOG -> wolves.containsId(Identifier.of(profile.appearance().variantId().value()));
                };
                context.assertTrue(registered, Text.literal("Randomized variant must exist at runtime"));
                context.assertTrue(
                        APPEARANCE_RULES.scaleRange(species).contains(profile.appearance().scale()),
                        Text.literal("Randomized scale must remain inside configured species bounds"));
            }
        }
        context.complete();
    }

    @GameTest
    public void factoryMaterializesExactIdentityAppearanceAndSafety(TestContext context) {
        PetEntityFactory factory = new PetEntityFactory();
        Vec3d catPosition = context.getAbsolute(new Vec3d(1.5, 1.0, 1.5));
        Vec3d dogPosition = context.getAbsolute(new Vec3d(3.5, 1.0, 1.5));
        Pet catPet = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                PetSpecies.CAT,
                "minecraft:tabby",
                0.67,
                7L);
        Pet dogPet = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000002"),
                PetSpecies.DOG,
                "minecraft:ashen",
                0.63,
                11L);

        PreparedPetEntity preparedCat = factory.prepare(
                context.getWorld(),
                catPet,
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                position(catPosition),
                false);
        PreparedPetEntity preparedDog = factory.prepare(
                context.getWorld(),
                dogPet,
                UUID.fromString("30000000-0000-0000-0000-000000000002"),
                position(dogPosition),
                false);

        context.assertFalse(preparedCat.compatibilityRepairRequired(), Text.literal("Known cat variant repaired"));
        context.assertFalse(preparedDog.compatibilityRepairRequired(), Text.literal("Known dog variant repaired"));
        context.assertTrue(context.getWorld().spawnEntity(preparedCat.entity()), Text.literal("Cat spawn failed"));
        context.assertTrue(context.getWorld().spawnEntity(preparedDog.entity()), Text.literal("Dog spawn failed"));

        assertPhysicalPet(context, preparedCat.entity(), catPet, "minecraft:tabby");
        assertPhysicalPet(context, preparedDog.entity(), dogPet, "minecraft:ashen");
        context.complete();
    }

    @GameTest
    public void genericScaleControlsCatAndWolfHitboxes(TestContext context) {
        ServerWorld world = context.getWorld();
        CatEntity smallCat = EntityType.CAT.create(world, SpawnReason.COMMAND);
        CatEntity largeCat = EntityType.CAT.create(world, SpawnReason.COMMAND);
        WolfEntity smallWolf = EntityType.WOLF.create(world, SpawnReason.COMMAND);
        WolfEntity largeWolf = EntityType.WOLF.create(world, SpawnReason.COMMAND);
        context.assertTrue(smallCat != null && largeCat != null && smallWolf != null && largeWolf != null,
                Text.literal("Minecraft failed to construct scale test entities"));
        setScale(smallCat, 0.5);
        setScale(largeCat, 1.25);
        setScale(smallWolf, 0.5);
        setScale(largeWolf, 1.25);
        Vec3d smallCatPos = context.getAbsolute(new Vec3d(1.5, 1.0, 1.5));
        Vec3d largeCatPos = context.getAbsolute(new Vec3d(3.5, 1.0, 1.5));
        Vec3d smallWolfPos = context.getAbsolute(new Vec3d(1.5, 1.0, 3.5));
        Vec3d largeWolfPos = context.getAbsolute(new Vec3d(3.5, 1.0, 3.5));
        smallCat.refreshPositionAndAngles(smallCatPos.x, smallCatPos.y, smallCatPos.z, 0, 0);
        largeCat.refreshPositionAndAngles(largeCatPos.x, largeCatPos.y, largeCatPos.z, 0, 0);
        smallWolf.refreshPositionAndAngles(smallWolfPos.x, smallWolfPos.y, smallWolfPos.z, 0, 0);
        largeWolf.refreshPositionAndAngles(largeWolfPos.x, largeWolfPos.y, largeWolfPos.z, 0, 0);
        world.spawnEntity(smallCat);
        world.spawnEntity(largeCat);
        world.spawnEntity(smallWolf);
        world.spawnEntity(largeWolf);
        context.waitAndRun(1, () -> {
            context.assertTrue(smallCat.getWidth() < largeCat.getWidth(),
                    Text.literal("Cat generic.scale did not change hitbox width"));
            context.assertTrue(smallCat.getHeight() < largeCat.getHeight(),
                    Text.literal("Cat generic.scale did not change hitbox height"));
            context.assertTrue(smallWolf.getWidth() < largeWolf.getWidth(),
                    Text.literal("Wolf generic.scale did not change hitbox width"));
            context.assertTrue(smallWolf.getHeight() < largeWolf.getHeight(),
                    Text.literal("Wolf generic.scale did not change hitbox height"));
            smallCat.discard();
            largeCat.discard();
            smallWolf.discard();
            largeWolf.discard();
            context.complete();
        });
    }

    @GameTest
    public void identitySurvivesVanillaNbtRoundTrip(TestContext context) {
        Pet pet = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000003"),
                PetSpecies.CAT,
                "minecraft:calico",
                0.72,
                19L);
        Vec3d absolute = context.getAbsolute(new Vec3d(2.5, 1.0, 2.5));
        TameableEntity original = new PetEntityFactory().prepare(
                context.getWorld(),
                pet,
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                position(absolute),
                true).entity();

        NbtWriteView writeView = NbtWriteView.create(
                ErrorReporter.EMPTY,
                context.getWorld().getRegistryManager());
        original.writeData(writeView);

        CatEntity restored = EntityType.CAT.create(context.getWorld(), SpawnReason.COMMAND);
        context.assertTrue(restored != null, Text.literal("Minecraft failed to create restore target"));
        restored.readData(NbtReadView.create(
                ErrorReporter.EMPTY,
                context.getWorld().getRegistryManager(),
                writeView.getNbt()));

        PetEntityData restoredData = (PetEntityData) restored;
        assertPhysicalPet(context, restored, pet, "minecraft:calico");
        context.assertTrue(restoredData.aipets$isSleeping(), Text.literal("Sleeping flag changed"));
        context.complete();
    }

    @GameTest
    public void markedPetsRejectVanillaCombatBreedingTamingAndTeleport(TestContext context) {
        Vec3d absolute = context.getAbsolute(new Vec3d(2.5, 1.0, 2.5));
        CatEntity pet = (CatEntity) new PetEntityFactory().prepare(
                context.getWorld(),
                pet(
                        UUID.fromString("10000000-0000-0000-0000-000000000004"),
                        PetSpecies.CAT,
                        "minecraft:jellie",
                        0.70,
                        3L),
                UUID.fromString("30000000-0000-0000-0000-000000000004"),
                position(absolute),
                false).entity();
        Vec3d dogAbsolute = context.getAbsolute(new Vec3d(5.5, 1.0, 2.5));
        WolfEntity dog = (WolfEntity) new PetEntityFactory().prepare(
                context.getWorld(),
                pet(
                        UUID.fromString("10000000-0000-0000-0000-000000000006"),
                        PetSpecies.DOG,
                        "minecraft:woods",
                        0.64,
                        4L),
                UUID.fromString("30000000-0000-0000-0000-000000000007"),
                position(dogAbsolute),
                false).entity();
        CatEntity ordinary = context.spawnMob(EntityType.CAT, new Vec3d(4.5, 1.0, 2.5));
        context.assertTrue(context.getWorld().spawnEntity(pet), Text.literal("Marked cat spawn failed"));
        context.assertTrue(context.getWorld().spawnEntity(dog), Text.literal("Marked dog spawn failed"));

        float health = pet.getHealth();
        boolean damaged = pet.damage(context.getWorld(), pet.getDamageSources().generic(), 2.0F);
        context.assertFalse(damaged, Text.literal("Marked pet accepted damage"));
        context.assertTrue(pet.getHealth() == health, Text.literal("Marked pet health changed"));

        pet.setTarget(ordinary);
        context.assertTrue(pet.getTarget() == null, Text.literal("Marked pet accepted a target"));
        context.assertFalse(
                pet.tryAttack(context.getWorld(), ordinary),
                Text.literal("Marked pet attacked another entity"));
        pet.setBreedingAge(-24_000);
        context.assertEquals(0, pet.getBreedingAge(), Text.literal("Marked pet became a baby"));
        pet.setLoveTicks(100);
        context.assertEquals(0, pet.getLoveTicks(), Text.literal("Marked pet entered love mode"));
        context.assertFalse(pet.canBreedWith(ordinary), Text.literal("Marked pet can breed"));
        pet.setTamed(true, true);
        context.assertFalse(pet.isTamed(), Text.literal("Marked pet entered vanilla tamed state"));
        pet.setOwner(ordinary);
        context.assertTrue(pet.getOwnerReference() == null, Text.literal("Marked pet owner changed"));
        context.assertFalse(pet.canBeLeashed(), Text.literal("Marked pet can be leashed"));
        context.assertFalse(
                dog.canAttackWithOwner(ordinary, pet),
                Text.literal("Marked dog can assist owner combat"));
        dog.setTamed(true, true);
        context.assertFalse(dog.isTamed(), Text.literal("Marked dog entered vanilla tamed state"));
        dog.setOwner(ordinary);
        context.assertTrue(dog.getOwnerReference() == null, Text.literal("Marked dog owner changed"));
        context.assertFalse(
                pet.shouldTryTeleportToOwner(),
                Text.literal("Marked pet requested vanilla owner teleport"));
        Vec3d beforeTeleportAttempt = pet.getEntityPos();
        pet.tryTeleportToOwner();
        context.assertEquals(
                beforeTeleportAttempt,
                pet.getEntityPos(),
                Text.literal("Marked pet used vanilla owner teleport"));
        context.complete();
    }

    @GameTest
    public void ordinaryCatsAndWolvesRetainVanillaBehavior(TestContext context) {
        CatEntity ordinary = context.spawnMob(EntityType.CAT, new Vec3d(2.5, 1.0, 2.5));
        WolfEntity ordinaryWolf = context.spawnMob(EntityType.WOLF, new Vec3d(4.5, 1.0, 2.5));
        PetEntityData data = (PetEntityData) ordinary;
        context.assertFalse(data.aipets$isPet(), Text.literal("Ordinary cat was marked as a pet"));
        context.assertFalse(
                ((PetEntityData) ordinaryWolf).aipets$isPet(),
                Text.literal("Ordinary wolf was marked as a pet"));

        ordinary.setBreedingAge(-24_000);
        context.assertEquals(-24_000, ordinary.getBreedingAge(), Text.literal("Ordinary cat age was intercepted"));
        ordinary.setBreedingAge(0);
        ordinary.setLoveTicks(100);
        context.assertEquals(100, ordinary.getLoveTicks(), Text.literal("Ordinary cat love mode was intercepted"));
        ordinary.setTamed(true, true);
        context.assertTrue(ordinary.isTamed(), Text.literal("Ordinary cat taming was intercepted"));

        ordinary.setInvulnerable(false);
        float health = ordinary.getHealth();
        boolean damaged = ordinary.damage(context.getWorld(), ordinary.getDamageSources().generic(), 1.0F);
        context.assertTrue(damaged, Text.literal("Ordinary cat damage was intercepted"));
        context.assertTrue(ordinary.getHealth() < health, Text.literal("Ordinary cat health did not decrease"));

        ordinaryWolf.setBreedingAge(-24_000);
        context.assertEquals(
                -24_000,
                ordinaryWolf.getBreedingAge(),
                Text.literal("Ordinary wolf age was intercepted"));
        ordinaryWolf.setBreedingAge(0);
        ordinaryWolf.setLoveTicks(100);
        context.assertEquals(
                100,
                ordinaryWolf.getLoveTicks(),
                Text.literal("Ordinary wolf love mode was intercepted"));
        ordinaryWolf.setTamed(true, true);
        context.assertTrue(ordinaryWolf.isTamed(), Text.literal("Ordinary wolf taming was intercepted"));
        ordinaryWolf.setOwner(ordinary);
        context.assertTrue(
                ordinaryWolf.getOwnerReference() != null,
                Text.literal("Ordinary wolf ownership was intercepted"));
        ordinaryWolf.setInvulnerable(false);
        float wolfHealth = ordinaryWolf.getHealth();
        boolean wolfDamaged = ordinaryWolf.damage(
                context.getWorld(),
                ordinaryWolf.getDamageSources().generic(),
                1.0F);
        context.assertTrue(wolfDamaged, Text.literal("Ordinary wolf damage was intercepted"));
        context.assertTrue(
                ordinaryWolf.getHealth() < wolfHealth,
                Text.literal("Ordinary wolf health did not decrease"));
        context.complete();
    }

    @GameTest
    public void removedVariantUsesDeterministicRegistryFallback(TestContext context) {
        Pet missingVariant = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000005"),
                PetSpecies.DOG,
                "example:removed_variant",
                0.60,
                31L);
        PetEntityFactory factory = new PetEntityFactory();
        Vec3d absolute = context.getAbsolute(new Vec3d(2.5, 1.0, 2.5));
        PreparedPetEntity first = factory.prepare(
                context.getWorld(),
                missingVariant,
                UUID.fromString("30000000-0000-0000-0000-000000000005"),
                position(absolute),
                false);
        PreparedPetEntity second = factory.prepare(
                context.getWorld(),
                missingVariant,
                UUID.fromString("30000000-0000-0000-0000-000000000006"),
                position(absolute),
                false);

        context.assertTrue(first.compatibilityRepairRequired(), Text.literal("Missing variant was not detected"));
        context.assertEquals(
                first.effectiveVariantId(),
                second.effectiveVariantId(),
                Text.literal("Compatibility fallback was not deterministic"));
        Registry<WolfVariant> wolves = context.getWorld()
                .getRegistryManager()
                .getOrThrow(RegistryKeys.WOLF_VARIANT);
        context.assertTrue(
                wolves.containsId(Identifier.of(first.effectiveVariantId().value())),
                Text.literal("Compatibility fallback was not a runtime wolf variant"));
        context.complete();
    }

    @GameTest
    public void safePlacementRejectsHazardsVoidSolidBlocksAndEntities(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos relativeFeet = new BlockPos(3, 1, 3);
        BlockPos absoluteFeet = context.getAbsolutePos(relativeFeet);
        Vec3d center = Vec3d.ofBottomCenter(absoluteFeet);
        TameableEntity prototype = new PetEntityFactory().prepare(
                world,
                pet(
                        UUID.fromString("10000000-0000-0000-0000-000000000008"),
                        PetSpecies.CAT,
                        "minecraft:white",
                        0.65,
                        0L),
                UUID.fromString("30000000-0000-0000-0000-000000000009"),
                position(center),
                false).entity();
        SafePlacementFinder exact = new SafePlacementFinder(0, 0);
        Set<Long> forcedChunksBefore = Set.copyOf(world.getForcedChunks());

        context.setBlockState(3, 0, 3, Blocks.STONE);
        context.setBlockState(3, 1, 3, Blocks.AIR);
        context.setBlockState(3, 2, 3, Blocks.AIR);
        context.assertTrue(
                exact.find(world, prototype, absoluteFeet).isPresent(),
                Text.literal("Clear supported position was rejected"));

        context.setBlockState(3, 1, 3, Blocks.LAVA);
        context.assertTrue(
                exact.find(world, prototype, absoluteFeet).isEmpty(),
                Text.literal("Lava position was accepted"));
        context.setBlockState(3, 1, 3, Blocks.STONE);
        context.assertTrue(
                exact.find(world, prototype, absoluteFeet).isEmpty(),
                Text.literal("Solid/suffocating position was accepted"));
        context.setBlockState(3, 1, 3, Blocks.AIR);
        context.setBlockState(3, 0, 3, Blocks.AIR);
        context.assertTrue(
                exact.find(world, prototype, absoluteFeet).isEmpty(),
                Text.literal("Unsupported void position was accepted"));

        context.setBlockState(3, 0, 3, Blocks.STONE);
        CatEntity occupant = context.spawnMob(EntityType.CAT, new Vec3d(3.5, 1.0, 3.5));
        context.assertTrue(
                exact.find(world, prototype, absoluteFeet).isEmpty(),
                Text.literal("Entity-occupied position was accepted"));

        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                context.setBlockState(x, 0, z, Blocks.STONE);
                context.setBlockState(x, 1, z, Blocks.AIR);
                context.setBlockState(x, 2, z, Blocks.AIR);
            }
        }
        Optional<WorldPosition> nearby = new SafePlacementFinder(1, 0)
                .find(world, prototype, absoluteFeet);
        context.assertTrue(nearby.isPresent(), Text.literal("Nearby safe position was not found"));
        context.assertFalse(
                nearby.orElseThrow().equals(position(center)),
                Text.literal("Finder reused the entity-occupied origin"));
        context.assertEquals(
                forcedChunksBefore,
                Set.copyOf(world.getForcedChunks()),
                Text.literal("Safe placement search changed forced chunks"));
        occupant.discard();
        context.complete();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void placementAndPickupCommitOrderingAndCompensation(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        try {
            for (int x = 1; x <= 6; x++) {
                for (int z = 1; z <= 6; z++) {
                    context.setBlockState(x, 0, z, Blocks.STONE);
                    context.setBlockState(x, 1, z, Blocks.AIR);
                    context.setBlockState(x, 2, z, Blocks.AIR);
                }
            }
            Vec3d ownerPosition = context.getAbsolute(new Vec3d(3.5, 1.0, 3.5));
            owner.refreshPositionAndAngles(ownerPosition, 0.0F, 0.0F);
            world.getChunkManager().updatePosition(owner);

            UUID successfulEntityId = UUID.fromString("30000000-0000-0000-0000-000000000010");
            InMemoryAuthorityGateway successAuthority = new InMemoryAuthorityGateway(
                    world,
                    pet(
                            UUID.fromString("10000000-0000-0000-0000-000000000009"),
                            owner.getUuid(),
                            PetSpecies.CAT,
                            "minecraft:tabby",
                            0.66,
                            0L));
            Queue<UUID> successOperations = new ArrayDeque<>(List.of(
                    UUID.fromString("40000000-0000-0000-0000-000000000001"),
                    UUID.fromString("40000000-0000-0000-0000-000000000007")));
            UUID repeatedEntityId = UUID.fromString("30000000-0000-0000-0000-000000000013");
            Queue<UUID> successEntityIds = new ArrayDeque<>(List.of(
                    successfulEntityId, repeatedEntityId));
            PetPlacementCoordinator successCoordinator = new PetPlacementCoordinator(
                    new BackendId("gametest"),
                    successAuthority,
                    new SafePlacementFinder(2, 1),
                    new PetEntityFactory(),
                    Clock.fixed(ADOPTED_AT, ZoneOffset.UTC),
                    successOperations::remove,
                    successEntityIds::remove);

            CompletableFuture<PetPlacementOutcome> successFuture = successCoordinator.place(owner);
            context.assertTrue(successFuture.isDone(), Text.literal("Synchronous placement did not complete"));
            PetPlacementOutcome success = successFuture.getNow(null);
            context.assertEquals(
                    PetPlacementStatus.PLACED,
                    success.status(),
                    Text.literal("Commit-first placement did not succeed"));
            context.assertTrue(
                    successAuthority.entityWasAbsentAtCommit,
                    Text.literal("Entity existed before authoritative placement commit"));
            TameableEntity spawned = (TameableEntity) world.getEntityAnyDimension(successfulEntityId);
            context.assertTrue(spawned != null, Text.literal("Committed physical entity was not spawned"));
            context.assertEquals(
                    successAuthority.current.recordVersion(),
                    ((PetEntityData) spawned).aipets$getRecordVersion(),
                    Text.literal("Spawned entity revision is stale"));
            context.assertEquals(
                    PlacementState.PLACED,
                    successAuthority.current.placementState(),
                    Text.literal("Authority is not placed after successful spawn"));
            successAuthority.sleeping = true;
            ((PetEntityData) spawned).aipets$setSleeping(true);
            PetPickupCoordinator pickupCoordinator = new PetPickupCoordinator(
                    new BackendId("gametest"),
                    successAuthority,
                    4.0,
                    Clock.fixed(ADOPTED_AT, ZoneOffset.UTC),
                    () -> UUID.fromString("40000000-0000-0000-0000-000000000004"));
            CompletableFuture<PetPickupOutcome> pickupFuture = pickupCoordinator.pickup(owner);
            context.assertTrue(pickupFuture.isDone(), Text.literal("Pickup did not complete"));
            PetPickupOutcome pickup = pickupFuture.getNow(null);
            context.assertEquals(
                    PetPickupStatus.PICKED_UP,
                    pickup.status(),
                    Text.literal("Nearby sleeping pet was not picked up"));
            context.assertTrue(spawned.isRemoved(), Text.literal("Pickup did not discard physical entity"));
            context.assertEquals(
                    PlacementState.HELD,
                    successAuthority.current.placementState(),
                    Text.literal("Pickup did not commit held state"));
            context.assertEquals(
                    2L,
                    successAuthority.current.recordVersion(),
                    Text.literal("Place plus pickup should advance two revisions"));

            PetPlacementOutcome repeatedPlacement = successCoordinator.place(owner).getNow(null);
            context.assertEquals(
                    PetPlacementStatus.PLACED,
                    repeatedPlacement.status(),
                    Text.literal("Repeated placement did not succeed"));
            TameableEntity repeatedEntity =
                    (TameableEntity) world.getEntityAnyDimension(repeatedEntityId);
            context.assertTrue(repeatedEntity != null, Text.literal("Repeated entity is missing"));
            assertPhysicalPet(context, repeatedEntity, successAuthority.current, "minecraft:tabby");
            context.assertTrue(
                    ((PetEntityData) repeatedEntity).aipets$isSleeping(),
                    Text.literal("Pickup/place reset the authoritative sleeping state"));

            ServerPlayerEntity otherPlayer = context.createMockCreativeServerPlayerInWorld();
            try {
                otherPlayer.refreshPositionAndAngles(ownerPosition, 0.0F, 0.0F);
                world.getChunkManager().updatePosition(otherPlayer);
                int mutationsBeforeUnauthorizedPickup = successAuthority.mutationCount;
                PetPickupOutcome unauthorizedPickup = new PetPickupCoordinator(
                        new BackendId("gametest"),
                        successAuthority,
                        4.0,
                        Clock.fixed(ADOPTED_AT, ZoneOffset.UTC),
                        () -> UUID.fromString("40000000-0000-0000-0000-000000000008"))
                        .pickup(otherPlayer).getNow(null);
                context.assertEquals(
                        PetPickupStatus.NO_PET,
                        unauthorizedPickup.status(),
                        Text.literal("Another player could resolve the owner's pet for pickup"));
                context.assertEquals(
                        mutationsBeforeUnauthorizedPickup,
                        successAuthority.mutationCount,
                        Text.literal("Unauthorized pickup reached the mutation boundary"));
                context.assertFalse(
                        repeatedEntity.isRemoved(),
                        Text.literal("Unauthorized pickup removed the owner's entity"));
            } finally {
                removeMockPlayer(world, otherPlayer);
            }

            PetPickupOutcome repeatedPickup = new PetPickupCoordinator(
                    new BackendId("gametest"),
                    successAuthority,
                    4.0,
                    Clock.fixed(ADOPTED_AT, ZoneOffset.UTC),
                    () -> UUID.fromString("40000000-0000-0000-0000-000000000009"))
                    .pickup(owner).getNow(null);
            context.assertEquals(
                    PetPickupStatus.PICKED_UP,
                    repeatedPickup.status(),
                    Text.literal("Owner could not pick up the repeated placement"));
            context.assertTrue(repeatedEntity.isRemoved(), Text.literal("Repeated pickup left the entity"));
            context.assertEquals(
                    4L,
                    successAuthority.current.recordVersion(),
                    Text.literal("Two place/pickup cycles did not advance exactly four revisions"));

            UUID failedEntityId = UUID.fromString("30000000-0000-0000-0000-000000000011");
            InMemoryAuthorityGateway failureAuthority = new InMemoryAuthorityGateway(
                    world,
                    pet(
                            UUID.fromString("10000000-0000-0000-0000-000000000010"),
                            owner.getUuid(),
                            PetSpecies.DOG,
                            "minecraft:pale",
                            0.62,
                            0L));
            Queue<UUID> failureOperations = new ArrayDeque<>(List.of(
                    UUID.fromString("40000000-0000-0000-0000-000000000002"),
                    UUID.fromString("40000000-0000-0000-0000-000000000003")));
            PetPlacementCoordinator failingCoordinator = new PetPlacementCoordinator(
                    new BackendId("gametest"),
                    failureAuthority,
                    new SafePlacementFinder(2, 1),
                    new PetEntityFactory(),
                    Clock.fixed(ADOPTED_AT, ZoneOffset.UTC),
                    failureOperations::remove,
                    () -> failedEntityId,
                    (ignoredWorld, ignoredEntity) -> false);

            CompletableFuture<PetPlacementOutcome> failureFuture = failingCoordinator.place(owner);
            context.assertTrue(failureFuture.isDone(), Text.literal("Spawn-failure path did not complete"));
            PetPlacementOutcome failure = failureFuture.getNow(null);
            context.assertEquals(
                    PetPlacementStatus.SPAWN_FAILED_COMPENSATED,
                    failure.status(),
                    Text.literal("Spawn failure was not compensated"));
            context.assertTrue(
                    failureAuthority.entityWasAbsentAtCommit,
                    Text.literal("Failed entity existed before authoritative commit"));
            context.assertEquals(
                    1,
                    failureAuthority.compensationCount,
                    Text.literal("Expected one exact compensation"));
            context.assertEquals(
                    PlacementState.HELD,
                    failureAuthority.current.placementState(),
                    Text.literal("Failed spawn did not return authority to held"));
            context.assertEquals(
                    2L,
                    failureAuthority.current.recordVersion(),
                    Text.literal("Place plus compensation should advance two revisions"));
            context.assertTrue(
                    world.getEntityAnyDimension(failedEntityId) == null,
                    Text.literal("Failed placement left a physical entity"));

            UUID farEntityId = UUID.fromString("30000000-0000-0000-0000-000000000012");
            InMemoryAuthorityGateway farAuthority = new InMemoryAuthorityGateway(
                    world,
                    pet(
                            UUID.fromString("10000000-0000-0000-0000-000000000011"),
                            owner.getUuid(),
                            PetSpecies.CAT,
                            "minecraft:siamese",
                            0.68,
                            0L));
            PetPlacementCoordinator farPlacementCoordinator = new PetPlacementCoordinator(
                    new BackendId("gametest"),
                    farAuthority,
                    new SafePlacementFinder(2, 1),
                    new PetEntityFactory(),
                    Clock.fixed(ADOPTED_AT, ZoneOffset.UTC),
                    () -> UUID.fromString("40000000-0000-0000-0000-000000000005"),
                    () -> farEntityId);
            PetPlacementOutcome farPlacement = farPlacementCoordinator.place(owner).getNow(null);
            context.assertEquals(
                    PetPlacementStatus.PLACED,
                    farPlacement.status(),
                    Text.literal("Far-pickup fixture placement failed"));
            TameableEntity farEntity = (TameableEntity) world.getEntityAnyDimension(farEntityId);
            context.assertTrue(farEntity != null, Text.literal("Far-pickup fixture entity is missing"));

            Vec3d farOwnerPosition = context.getAbsolute(new Vec3d(14.5, 1.0, 3.5));
            owner.refreshPositionAndAngles(farOwnerPosition, 0.0F, 0.0F);
            world.getChunkManager().updatePosition(owner);
            PetPickupCoordinator farPickupCoordinator = new PetPickupCoordinator(
                    new BackendId("gametest"),
                    farAuthority,
                    4.0,
                    Clock.fixed(ADOPTED_AT, ZoneOffset.UTC),
                    () -> UUID.fromString("40000000-0000-0000-0000-000000000006"));
            PetPickupOutcome farPickup = farPickupCoordinator.pickup(owner).getNow(null);
            context.assertEquals(
                    PetPickupStatus.OUT_OF_RANGE,
                    farPickup.status(),
                    Text.literal("Pickup beyond four blocks was accepted"));
            context.assertEquals(
                    PlacementState.PLACED,
                    farAuthority.current.placementState(),
                    Text.literal("Rejected far pickup changed authority"));
            context.assertFalse(farEntity.isRemoved(), Text.literal("Rejected far pickup removed entity"));
            farEntity.discard();
        } finally {
            removeMockPlayer(world, owner);
        }
        context.complete();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void concurrentPlacementRequestsCreateOneRepresentation(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        TameableEntity spawned = null;
        try {
            for (int x = 1; x <= 6; x++) {
                for (int z = 1; z <= 6; z++) {
                    context.setBlockState(x, 0, z, Blocks.STONE);
                    context.setBlockState(x, 1, z, Blocks.AIR);
                    context.setBlockState(x, 2, z, Blocks.AIR);
                }
            }
            Vec3d ownerPosition = context.getAbsolute(new Vec3d(3.5, 1.0, 3.5));
            owner.refreshPositionAndAngles(ownerPosition, 0.0F, 0.0F);
            world.getChunkManager().updatePosition(owner);

            UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000095");
            UUID entityId = UUID.fromString("30000000-0000-0000-0000-000000000095");
            InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(
                    world,
                    pet(petId, owner.getUuid(), PetSpecies.CAT,
                            "minecraft:tabby", 0.66, 0L));
            authority.pendingOwnerLookup = new CompletableFuture<>();
            PetPlacementCoordinator coordinator = new PetPlacementCoordinator(
                    new BackendId("gametest"), authority, new SafePlacementFinder(2, 1),
                    new PetEntityFactory(), Clock.fixed(ADOPTED_AT, ZoneOffset.UTC),
                    () -> UUID.fromString("40000000-0000-0000-0000-000000000095"),
                    () -> entityId);

            CompletableFuture<PetPlacementOutcome> first = coordinator.place(owner);
            CompletableFuture<PetPlacementOutcome> simultaneous = coordinator.place(owner);
            context.assertFalse(first.isDone(), Text.literal("First placement was not held in flight"));
            context.assertEquals(
                    PetPlacementStatus.ALREADY_IN_PROGRESS,
                    simultaneous.getNow(null).status(),
                    Text.literal("Simultaneous placement was not locally deduplicated"));
            context.assertEquals(
                    1,
                    authority.ownerLookupCount,
                    Text.literal("Simultaneous placement made a second authority read"));

            authority.pendingOwnerLookup.complete(Optional.of(
                    new PetAuthoritySnapshot(authority.current, false, true)));
            context.assertEquals(
                    PetPlacementStatus.PLACED,
                    first.getNow(null).status(),
                    Text.literal("Winning placement did not complete"));
            context.assertEquals(
                    1,
                    authority.mutationCount,
                    Text.literal("Concurrent placement reached authority more than once"));

            int representations = 0;
            for (net.minecraft.entity.Entity entity : world.iterateEntities()) {
                if (entity instanceof PetEntityData data
                        && data.aipets$isPet()
                        && petId.equals(data.aipets$getPetId())
                        && !entity.isRemoved()) {
                    representations++;
                    spawned = (TameableEntity) entity;
                }
            }
            context.assertEquals(
                    1, representations,
                    Text.literal("Concurrent placement created duplicate representations"));
            context.assertTrue(
                    world.getEntityAnyDimension(entityId) == spawned,
                    Text.literal("The sole representation has the wrong authoritative UUID"));
        } finally {
            if (spawned != null) spawned.discard();
            removeMockPlayer(world, owner);
        }
        context.complete();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void supportedPortalTransferCarriesNearPetOnlyToReservedFinalBackend(
            TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        try {
            for (int x = 1; x <= 6; x++) {
                for (int z = 1; z <= 6; z++) {
                    context.setBlockState(x, 0, z, Blocks.STONE);
                    context.setBlockState(x, 1, z, Blocks.AIR);
                    context.setBlockState(x, 2, z, Blocks.AIR);
                }
            }
            Vec3d ownerPosition = context.getAbsolute(new Vec3d(3.5, 1.0, 3.5));
            owner.refreshPositionAndAngles(ownerPosition, 0.0F, 0.0F);
            world.getChunkManager().updatePosition(owner);
            BackendId source = new BackendId("gametest");
            BackendId destination = new BackendId("destination");
            UUID sourceEntityId = UUID.fromString("30000000-0000-0000-0000-000000000081");
            Pet held = pet(
                    UUID.fromString("10000000-0000-0000-0000-000000000081"),
                    owner.getUuid(), PetSpecies.CAT, "minecraft:tabby", 0.66, 0L);
            Pet placed = PetTransitions.place(held, new PetTransitions.Place(
                    owner.getUuid(), 0, source,
                    DimensionId.parse(world.getRegistryKey().getValue().toString()),
                    position(ownerPosition), sourceEntityId, ADOPTED_AT.plusSeconds(1))).pet();
            TameableEntity sourceEntity = new PetEntityFactory().prepare(
                    world, placed, sourceEntityId, position(ownerPosition), false).entity();
            context.assertTrue(world.spawnEntity(sourceEntity), Text.literal("Source pet spawn failed"));

            InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, placed);
            Queue<UUID> transferIds = new ArrayDeque<>(List.of(
                    UUID.fromString("40000000-0000-0000-0000-000000000081")));
            Queue<UUID> operationIds = new ArrayDeque<>(List.of(
                    UUID.fromString("40000000-0000-0000-0000-000000000082")));
            PetTransferConfig transferConfig = new PetTransferConfig(
                    16.0, Duration.ofMinutes(5));
            PetTransferCoordinator sourceCoordinator = new PetTransferCoordinator(
                    source, authority, new SafePlacementFinder(2, 1), new PetEntityFactory(),
                    transferConfig,
                    Clock.fixed(ADOPTED_AT.plusSeconds(2), ZoneOffset.UTC),
                    transferIds::remove, operationIds::remove);

            var prepared = sourceCoordinator.prepareSource(owner, destination.value()).getNow(null);
            context.assertEquals(PetTransferStatus.SOURCE_PREPARED, prepared.status(),
                    Text.literal("Near pet was not reserved for transfer"));
            context.assertTrue(authority.entityWasPresentAtTransferCommit,
                    Text.literal("Source entity disappeared before TRANSFERRING commit"));
            context.assertEquals(PlacementState.TRANSFERRING,
                    authority.current.placementState(), Text.literal("Authority is not transferring"));
            context.assertTrue(sourceEntity.isRemoved(),
                    Text.literal("Source entity survived successful reservation"));

            int mutationsBeforeLobby = authority.mutationCount;
            PetTransferCoordinator waitingLobby = new PetTransferCoordinator(
                    new BackendId("waiting-lobby"), authority,
                    new SafePlacementFinder(2, 1), new PetEntityFactory(), transferConfig,
                    Clock.fixed(ADOPTED_AT.plusSeconds(3), ZoneOffset.UTC),
                    UUID::randomUUID, UUID::randomUUID);
            var lobby = waitingLobby.claimDestination(owner).getNow(null);
            context.assertEquals(PetTransferStatus.DESTINATION_NOT_RESERVED, lobby.status(),
                    Text.literal("Waiting lobby attempted to materialize the pet"));
            context.assertEquals(mutationsBeforeLobby, authority.mutationCount,
                    Text.literal("Waiting lobby reached transfer mutation authority"));

            PetTransferCoordinator destinationCoordinator = new PetTransferCoordinator(
                    destination, authority,
                    new SafePlacementFinder(2, 1), new PetEntityFactory(), transferConfig,
                    Clock.fixed(ADOPTED_AT.plusSeconds(3), ZoneOffset.UTC),
                    UUID::randomUUID, operationIds::remove);
            var claimed = destinationCoordinator.claimDestination(owner).getNow(null);
            context.assertEquals(PetTransferStatus.DESTINATION_PLACED, claimed.status(),
                    Text.literal("Final backend did not claim reserved transfer"));
            context.assertEquals(PlacementState.PLACED, authority.current.placementState(),
                    Text.literal("Destination did not become authoritative"));
            PlacedPlacement destinationPlacement = (PlacedPlacement) authority.current.placement();
            context.assertEquals(destination, destinationPlacement.backendId(),
                    Text.literal("Pet was placed on the wrong backend"));
            TameableEntity destinationEntity = (TameableEntity) world.getEntityAnyDimension(
                    destinationPlacement.entityUuid().orElseThrow());
            context.assertTrue(destinationEntity != null,
                    Text.literal("Destination physical pet was not spawned"));
            assertPhysicalPet(context, destinationEntity, authority.current, "minecraft:tabby");

            int mutationsAfterClaim = authority.mutationCount;
            var duplicate = destinationCoordinator.claimDestination(owner).getNow(null);
            context.assertEquals(PetTransferStatus.DESTINATION_NOT_RESERVED, duplicate.status(),
                    Text.literal("Completed transfer was claimable twice"));
            context.assertEquals(mutationsAfterClaim, authority.mutationCount,
                    Text.literal("Duplicate destination join issued another mutation"));
            destinationEntity.discard();

            UUID farEntityId = UUID.fromString("30000000-0000-0000-0000-000000000083");
            Vec3d farPosition = ownerPosition.add(20.0, 0.0, 0.0);
            Pet farHeld = pet(
                    UUID.fromString("10000000-0000-0000-0000-000000000083"),
                    owner.getUuid(), PetSpecies.DOG, "minecraft:pale", 0.62, 0L);
            Pet farPlaced = PetTransitions.place(farHeld, new PetTransitions.Place(
                    owner.getUuid(), 0, source,
                    DimensionId.parse(world.getRegistryKey().getValue().toString()),
                    position(farPosition), farEntityId, ADOPTED_AT.plusSeconds(1))).pet();
            TameableEntity farEntity = new PetEntityFactory().prepare(
                    world, farPlaced, farEntityId, position(farPosition), false).entity();
            context.assertTrue(world.spawnEntity(farEntity), Text.literal("Far pet spawn failed"));
            InMemoryAuthorityGateway farAuthority = new InMemoryAuthorityGateway(world, farPlaced);
            var far = new PetTransferCoordinator(
                    source, farAuthority, new SafePlacementFinder(2, 1), new PetEntityFactory(),
                    transferConfig, Clock.fixed(ADOPTED_AT.plusSeconds(2), ZoneOffset.UTC),
                    UUID::randomUUID, UUID::randomUUID)
                    .prepareSource(owner, destination.value()).getNow(null);
            context.assertEquals(PetTransferStatus.LEFT_BEHIND, far.status(),
                    Text.literal("Far pet was automatically carried"));
            context.assertEquals(PlacementState.PLACED, farAuthority.current.placementState(),
                    Text.literal("Far pet authority changed"));
            context.assertFalse(farEntity.isRemoved(), Text.literal("Far pet entity was removed"));
            farEntity.discard();

            InMemoryAuthorityGateway heldAuthority = new InMemoryAuthorityGateway(world, held);
            var heldResult = new PetTransferCoordinator(
                    source, heldAuthority, new SafePlacementFinder(2, 1), new PetEntityFactory(),
                    transferConfig, Clock.fixed(ADOPTED_AT.plusSeconds(2), ZoneOffset.UTC),
                    UUID::randomUUID, UUID::randomUUID)
                    .prepareSource(owner, destination.value()).getNow(null);
            context.assertEquals(PetTransferStatus.HELD_UNCHANGED, heldResult.status(),
                    Text.literal("Manually held pet changed during transfer"));
            context.assertTrue(heldAuthority.current.placement() instanceof HeldPlacement,
                    Text.literal("Held authority was not preserved"));
        } finally {
            removeMockPlayer(world, owner);
        }
        context.complete();
    }

    @GameTest(maxTicks = 240)
    @SuppressWarnings("removal")
    public void petFollowsRegisteredOwnerWithoutTeleportOrForcedChunks(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        TameableEntity spawned = null;
        try {
            for (int x = 1; x <= 24; x++) {
                context.setBlockState(x, 0, 3, Blocks.STONE);
                context.setBlockState(x, 1, 3, Blocks.AIR);
                context.setBlockState(x, 2, 3, Blocks.AIR);
            }

            Vec3d ownerPosition = context.getAbsolute(new Vec3d(22.75, 1.0, 3.5));
            owner.refreshPositionAndAngles(ownerPosition, 0.0F, 0.0F);
            world.getChunkManager().updatePosition(owner);
            context.assertTrue(
                    world.getPlayerAnyDimension(owner.getUuid()) == owner,
                    Text.literal("Mock owner is not registered"));

            Vec3d petPosition = context.getAbsolute(new Vec3d(1.25, 1.0, 3.5));
            Pet aggregate = pet(
                    UUID.fromString("10000000-0000-0000-0000-000000000007"),
                    owner.getUuid(),
                    PetSpecies.CAT,
                    "minecraft:black",
                    0.67,
                    0L);
            spawned = new PetEntityFactory().prepare(
                    world,
                    aggregate,
                    UUID.fromString("30000000-0000-0000-0000-000000000008"),
                    position(petPosition),
                    false).entity();
            context.assertTrue(world.spawnEntity(spawned), Text.literal("Following pet spawn failed"));

            TameableEntity pet = spawned;
            double initialSquaredDistance = pet.squaredDistanceTo(owner);
            Vec3d[] previousPosition = {pet.getEntityPos()};
            double[] maximumStepSquared = {0.0};
            double[] farMaximumStepSquared = {0.0};
            double[] nearMaximumStepSquared = {0.0};
            boolean[] moved = {false};
            boolean[] visitedNear = {false};
            boolean[] visitedMedium = {false};
            boolean[] visitedFar = {false};
            Vec3d[] cagedPosition = {null};
            Vec3d[] releasedPosition = {null};
            java.util.List<BlockPos> cage = new java.util.ArrayList<>();
            PetPhysicalConfig movementConfig = PetPhysicalConfig.defaults();
            Set<Long> forcedChunksBefore = Set.copyOf(world.getForcedChunks());
            ChunkPos testChunk = new ChunkPos(context.getAbsolutePos(BlockPos.ORIGIN));
            ChunkPos remoteSentinel = new ChunkPos(testChunk.x + 128, testChunk.z + 128);
            context.assertFalse(
                    world.getChunkManager().isChunkLoaded(remoteSentinel.x, remoteSentinel.z),
                    Text.literal("Remote sentinel chunk was already loaded"));

            context.runAtEveryTick(() -> {
                if (!pet.isRemoved()) {
                    double stepSquared = pet.getEntityPos().squaredDistanceTo(previousPosition[0]);
                    maximumStepSquared[0] = Math.max(maximumStepSquared[0], stepSquared);
                    moved[0] |= stepSquared > 0.0025;
                    double distanceSquared = pet.squaredDistanceTo(owner);
                    double selectedSpeed = movementConfig.speedForSquaredDistance(distanceSquared);
                    if (selectedSpeed == movementConfig.farSpeed()) {
                        visitedFar[0] = true;
                        farMaximumStepSquared[0] = Math.max(farMaximumStepSquared[0], stepSquared);
                    } else if (selectedSpeed == movementConfig.mediumSpeed()) {
                        visitedMedium[0] = true;
                    } else {
                        visitedNear[0] = true;
                        nearMaximumStepSquared[0] = Math.max(nearMaximumStepSquared[0], stepSquared);
                    }
                    previousPosition[0] = pet.getEntityPos();
                }
            });
            context.runAtTick(75, () -> {
                context.assertTrue(
                        pet.squaredDistanceTo(owner) < initialSquaredDistance - 25.0,
                        Text.literal("Far-speed following did not prevent routine separation"));
                Vec3d nearOwnerPosition = pet.getEntityPos().add(6.0, 0.0, 0.0);
                owner.refreshPositionAndAngles(nearOwnerPosition, 0.0F, 0.0F);
                world.getChunkManager().updatePosition(owner);
            });
            context.runAtTick(110, () -> {
                BlockPos center = pet.getBlockPos();
                cagedPosition[0] = pet.getEntityPos();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        for (int dy = 0; dy <= 2; dy++) {
                            BlockPos wall = center.add(dx, dy, dz);
                            world.setBlockState(wall, Blocks.STONE.getDefaultState());
                            cage.add(wall);
                        }
                    }
                }
                Vec3d blockedOwnerPosition = pet.getEntityPos().add(6.0, 0.0, 0.0);
                owner.refreshPositionAndAngles(blockedOwnerPosition, 0.0F, 0.0F);
                world.getChunkManager().updatePosition(owner);
            });
            context.runAtTick(180, () -> {
                context.assertTrue(
                        pet.getEntityPos().squaredDistanceTo(cagedPosition[0]) < 2.25,
                        Text.literal("Ordinary blockage did not actually hold the pet"));
                cage.forEach(position -> world.setBlockState(position, Blocks.AIR.getDefaultState()));
                releasedPosition[0] = pet.getEntityPos();
            });
            context.runAtTick(225, () -> {
                try {
                    context.assertFalse(pet.isRemoved(), Text.literal("Following pet disappeared"));
                    context.assertTrue(moved[0], Text.literal("Following pet never moved"));
                    context.assertTrue(
                            visitedFar[0] && visitedMedium[0] && visitedNear[0],
                            Text.literal("Follow path did not exercise all distance bands"));
                    context.assertTrue(
                            farMaximumStepSquared[0] > 0.0025,
                            Text.literal("Far-distance acceleration produced no meaningful movement"));
                    context.assertTrue(
                            nearMaximumStepSquared[0] > 0.0,
                            Text.literal("Near-distance following produced no movement"));
                    context.assertTrue(
                            pet.getEntityPos().squaredDistanceTo(releasedPosition[0]) > 0.25,
                            Text.literal("Pet did not recalculate and move after blockage removal"));
                    context.assertTrue(
                            maximumStepSquared[0] < 2.25,
                            Text.literal("Following pet made a teleport-like jump"));
                    context.assertEquals(
                            forcedChunksBefore,
                            Set.copyOf(world.getForcedChunks()),
                            Text.literal("Forced-chunk set changed while following"));
                    context.assertFalse(
                            world.getChunkManager().isChunkLoaded(remoteSentinel.x, remoteSentinel.z),
                            Text.literal("Following loaded a remote sentinel chunk"));
                } finally {
                    cage.forEach(position -> world.setBlockState(position, Blocks.AIR.getDefaultState()));
                    pet.discard();
                    removeMockPlayer(world, owner);
                }
                context.complete();
            });
        } catch (RuntimeException | Error failure) {
            if (spawned != null) {
                spawned.discard();
            }
            removeMockPlayer(world, owner);
            throw failure;
        }
    }

    @GameTest(maxTicks = 150)
    @SuppressWarnings("removal")
    public void sleepingPetStopsActiveFollowUntilAwake(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        TameableEntity pet = null;
        try {
            for (int x = 1; x <= 24; x++) {
                context.setBlockState(x, 0, 3, Blocks.STONE);
                context.setBlockState(x, 1, 3, Blocks.AIR);
                context.setBlockState(x, 2, 3, Blocks.AIR);
            }
            Vec3d ownerPosition = context.getAbsolute(new Vec3d(22.75, 1.0, 3.5));
            owner.refreshPositionAndAngles(ownerPosition, 0.0F, 0.0F);
            world.getChunkManager().updatePosition(owner);
            Vec3d initialPosition = context.getAbsolute(new Vec3d(1.25, 1.0, 3.5));
            Pet aggregate = pet(
                    UUID.fromString("10000000-0000-0000-0000-000000000087"),
                    owner.getUuid(), PetSpecies.CAT, "minecraft:black", 0.67, 0L);
            pet = new PetEntityFactory().prepare(
                    world, aggregate,
                    UUID.fromString("30000000-0000-0000-0000-000000000087"),
                    position(initialPosition), false).entity();
            context.assertTrue(world.spawnEntity(pet), Text.literal("Sleeping test pet did not spawn"));

            TameableEntity testedPet = pet;
            Vec3d[] sleepingPosition = {null};
            // The complete GameTest batch runs many pathfinders concurrently; allow
            // the same bounded follow behavior a little more wall-clock tick time
            // before asserting it, without changing the movement contract.
            context.runAtTick(40, () -> {
                context.assertTrue(
                        testedPet.getEntityPos().squaredDistanceTo(initialPosition) > 0.25,
                        Text.literal("Pet was not actively following before sleep"));
                ((PetEntityData) testedPet).aipets$setSleeping(true);
            });
            context.runAtTick(45, () -> sleepingPosition[0] = testedPet.getEntityPos());
            context.runAtTick(80, () -> {
                context.assertTrue(
                        testedPet.getNavigation().isIdle(),
                        Text.literal("Sleeping pet retained an active navigation path"));
                context.assertTrue(
                        testedPet.getEntityPos().squaredDistanceTo(sleepingPosition[0]) < 0.04,
                        Text.literal("Sleeping pet moved while its owner remained distant"));
                ((PetEntityData) testedPet).aipets$setSleeping(false);
            });
            context.runAtTick(125, () -> {
                try {
                    context.assertTrue(
                            testedPet.getEntityPos().squaredDistanceTo(sleepingPosition[0]) > 0.25,
                            Text.literal("Awakened pet did not resume owner following"));
                } finally {
                    testedPet.discard();
                    removeMockPlayer(world, owner);
                }
                context.complete();
            });
        } catch (RuntimeException | Error failure) {
            if (pet != null) pet.discard();
            removeMockPlayer(world, owner);
            throw failure;
        }
    }

    @GameTest
    public void entityLoadReconciliationReusesExactAndDiscardsStale(TestContext context) {
        ServerWorld world = context.getWorld();
        BackendId backend = new BackendId("gametest");
        DimensionId dimension = DimensionId.parse(world.getRegistryKey().getValue().toString());
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000013");
        UUID exactEntityId = UUID.fromString("30000000-0000-0000-0000-000000000013");
        UUID staleEntityId = UUID.fromString("30000000-0000-0000-0000-000000000014");
        Pet held = pet(petId, PetSpecies.CAT, "minecraft:tabby", 0.67, 0L);
        Vec3d exactPosition = context.getAbsolute(new Vec3d(1.5, 1.0, 1.5));
        TransitionResult placedTransition = PetTransitions.place(
                held,
                new PetTransitions.Place(
                        held.ownerUuid(),
                        held.recordVersion(),
                        backend,
                        dimension,
                        position(exactPosition),
                        exactEntityId,
                        ADOPTED_AT.plusSeconds(1)));
        context.assertTrue(placedTransition.applied(), Text.literal("Reconciliation fixture did not place"));
        Pet placed = placedTransition.pet();
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, placed);
        authority.sleeping = true;
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(backend, authority);
        Set<Long> forcedChunksBefore = Set.copyOf(world.getForcedChunks());

        TameableEntity exact = null;
        try {
            exact = new PetEntityFactory().prepare(
                    world,
                    held,
                    exactEntityId,
                    position(exactPosition),
                    false).entity();
            exact.refreshPositionAndAngles(exactPosition, 0.0F, 0.0F);
            context.assertTrue(world.spawnEntity(exact), Text.literal("Exact reconciliation entity did not spawn"));
            context.assertFalse(exact.isRemoved(), Text.literal("Exact authority match was discarded"));
            PetEntityData exactData = (PetEntityData) exact;
            context.assertEquals(
                    placed.recordVersion(),
                    exactData.aipets$getRecordVersion(),
                    Text.literal("Entity-load event did not refresh the older revision"));
            context.assertTrue(
                    exactData.aipets$isSleeping(),
                    Text.literal("Entity-load event did not refresh sleep state"));

            Vec3d stalePosition = context.getAbsolute(new Vec3d(3.5, 1.0, 1.5));
            TameableEntity stale = new PetEntityFactory().prepare(
                    world,
                    placed,
                    staleEntityId,
                    position(stalePosition),
                    false).entity();
            stale.refreshPositionAndAngles(stalePosition, 0.0F, 0.0F);
            context.assertTrue(world.spawnEntity(stale), Text.literal("Stale reconciliation entity did not spawn"));
            context.assertTrue(
                    stale.isRemoved(),
                    Text.literal("Entity-load event left a wrong-UUID physical entity loaded"));
            int lookupsBeforePeriodicScan = authority.petIdLookupCount;
            PeriodicPetReconciliation periodic = new PeriodicPetReconciliation(
                    new PetReconciliationConfig(20, 1),
                    () -> new PetEntityReconciler(backend, authority));
            int queuedLoadedPets = periodic.scanLoadedNow(world);
            // GameTest batches share one ServerWorld, so other tests may leave
            // independently loaded marked pets in it.  The production contract
            // is to scan every loaded marked pet, while the per-tick budget
            // remains bounded by maximumChecksPerTick (one here).
            context.assertTrue(
                    queuedLoadedPets >= 1,
                    Text.literal("Periodic reconciliation did not queue the loaded pet"));
            periodic.onEndWorldTick(world);
            context.assertEquals(
                    lookupsBeforePeriodicScan + 1,
                    authority.petIdLookupCount,
                    Text.literal("Periodic reconciliation did not make one bounded lookup"));
            context.assertTrue(
                    periodic.inFlightEntityIds().isEmpty(),
                    Text.literal("Completed reconciliation remained in-flight"));
            context.assertEquals(
                    forcedChunksBefore,
                    Set.copyOf(world.getForcedChunks()),
                    Text.literal("Reconciliation changed the forced-chunk set"));
        } finally {
            uninstallGateway.run();
            if (exact != null) {
                exact.discard();
            }
        }
        context.complete();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void lazyRestartRecoveryReconstructsOnceAndNeverLoadsRemoteChunk(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        BackendId backend = new BackendId("gametest");
        DimensionId dimension = DimensionId.parse(world.getRegistryKey().getValue().toString());
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000093");
        UUID expectedEntityId = UUID.fromString("30000000-0000-0000-0000-000000000093");
        UUID staleEntityId = UUID.fromString("30000000-0000-0000-0000-000000000094");
        Vec3d location = context.getAbsolute(new Vec3d(2.5, 1.0, 2.5));
        Pet adopted = pet(
                petId, owner.getUuid(), PetSpecies.CAT, "minecraft:tabby", 0.66, 0L);
        Pet placed = PetTransitions.place(adopted, new PetTransitions.Place(
                owner.getUuid(), adopted.recordVersion(), backend, dimension,
                position(location), expectedEntityId, ADOPTED_AT.plusSeconds(1))).pet();
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, placed);

        PetEntityRecoveryCoordinator firstProcess = new PetEntityRecoveryCoordinator(
                backend, authority, new PetEntityReconciler(backend, authority),
                new PetEntityFactory());
        context.assertEquals(
                PetRecoveryStatus.RECONSTRUCTED,
                firstProcess.recoverOwner(world.getServer(), owner.getUuid())
                        .toCompletableFuture().join(),
                Text.literal("Restart recovery did not reconstruct the missing entity"));
        TameableEntity exact = (TameableEntity) world.getEntityAnyDimension(expectedEntityId);
        context.assertTrue(exact != null, Text.literal("Expected entity UUID was not reconstructed"));
        assertPhysicalPet(context, exact, placed, "minecraft:tabby");

        TameableEntity stale = new PetEntityFactory().prepare(
                world, placed, staleEntityId, position(location.add(1, 0, 0)), false).entity();
        context.assertTrue(world.spawnEntity(stale), Text.literal("Old saved entity did not load"));
        PetEntityRecoveryCoordinator secondProcess = new PetEntityRecoveryCoordinator(
                backend, authority, new PetEntityReconciler(backend, authority),
                new PetEntityFactory());
        context.assertEquals(
                PetRecoveryStatus.AUTHORITATIVE_ENTITY_PRESENT,
                secondProcess.recoverOwner(world.getServer(), owner.getUuid())
                        .toCompletableFuture().join(),
                Text.literal("Second process did not reuse the authoritative entity"));
        context.assertTrue(stale.isRemoved(), Text.literal("Old saved entity was not discarded"));
        context.assertTrue(
                world.getEntityAnyDimension(expectedEntityId) == exact,
                Text.literal("Second process duplicated the authoritative entity"));

        exact.discard();
        PetEntityRecoveryCoordinator thirdProcess = new PetEntityRecoveryCoordinator(
                backend, authority, new PetEntityReconciler(backend, authority),
                new PetEntityFactory());
        context.assertEquals(
                PetRecoveryStatus.RECONSTRUCTED,
                thirdProcess.recoverOwner(world.getServer(), owner.getUuid())
                        .toCompletableFuture().join(),
                Text.literal("Deleted entity was not reconstructed from persisted authority"));
        TameableEntity reconstructed = (TameableEntity) world.getEntityAnyDimension(expectedEntityId);
        assertPhysicalPet(context, reconstructed, placed, "minecraft:tabby");

        ChunkPos testChunk = new ChunkPos(context.getAbsolutePos(BlockPos.ORIGIN));
        ChunkPos remoteChunk = new ChunkPos(testChunk.x + 128, testChunk.z + 128);
        context.assertFalse(
                world.getChunkManager().isChunkLoaded(remoteChunk.x, remoteChunk.z),
                Text.literal("Remote recovery sentinel started loaded"));
        reconstructed.discard();
        authority.current = withPlacement(placed, PlacedPlacement.materialized(
                backend, dimension,
                new WorldPosition(remoteChunk.getStartX() + 0.5, 70, remoteChunk.getStartZ() + 0.5),
                expectedEntityId));
        context.assertEquals(
                PetRecoveryStatus.DEFERRED_UNLOADED_CHUNK,
                new PetEntityRecoveryCoordinator(
                        backend, authority, new PetEntityReconciler(backend, authority),
                        new PetEntityFactory())
                        .recoverOwner(world.getServer(), owner.getUuid()).toCompletableFuture().join(),
                Text.literal("Unloaded recovery was not deferred"));
        context.assertFalse(
                world.getChunkManager().isChunkLoaded(remoteChunk.x, remoteChunk.z),
                Text.literal("Recovery force-loaded the remote chunk"));

        removeMockPlayer(world, owner);
        context.complete();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void markedPetInteractionIsOwnerOnlyAndOrdinaryMobsPassThrough(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity intruder = context.createMockCreativeServerPlayerInWorld();
        TameableEntity pet = null;
        CatEntity ordinary = null;
        Runnable uninstallHandler = () -> { };
        try {
            context.assertFalse(
                    owner.getUuid().equals(intruder.getUuid()),
                    Text.literal("Interaction test players share an identity"));
            Vec3d petPosition = context.getAbsolute(new Vec3d(1.5, 1.0, 1.5));
            Pet aggregate = pet(
                    UUID.fromString("10000000-0000-0000-0000-000000000015"),
                    owner.getUuid(),
                    PetSpecies.CAT,
                    "minecraft:tabby",
                    0.67,
                    0L);
            pet = new PetEntityFactory().prepare(
                    world,
                    aggregate,
                    UUID.fromString("30000000-0000-0000-0000-000000000015"),
                    position(petPosition),
                    false).entity();
            pet.refreshPositionAndAngles(petPosition, 0.0F, 0.0F);
            context.assertTrue(world.spawnEntity(pet), Text.literal("Interaction pet did not spawn"));

            AtomicInteger opens = new AtomicInteger();
            TameableEntity expectedPet = pet;
            uninstallHandler = PetInteractionRouter.installHandler((actualOwner, actualPet) -> {
                context.assertTrue(actualOwner == owner, Text.literal("Wrong owner reached handler"));
                context.assertTrue(actualPet == expectedPet, Text.literal("Wrong pet reached handler"));
                opens.incrementAndGet();
            });
            ActionResult ownerResult = UseEntityCallback.EVENT.invoker().interact(
                    owner,
                    world,
                    Hand.MAIN_HAND,
                    pet,
                    new EntityHitResult(pet));
            context.assertEquals(
                    ActionResult.SUCCESS_SERVER,
                    ownerResult,
                    Text.literal("Owner interaction was not consumed"));
            context.assertEquals(1, opens.get(), Text.literal("Owner interaction did not open once"));

            Runnable restoreChatPermission = PetPermissions.install(
                    (ignored, node, defaultLevel) -> !node.equals(PetPermission.CHAT.node()));
            try {
                ActionResult deniedChatResult = UseEntityCallback.EVENT.invoker().interact(
                        owner,
                        world,
                        Hand.MAIN_HAND,
                        pet,
                        new EntityHitResult(pet));
                context.assertEquals(
                        ActionResult.FAIL,
                        deniedChatResult,
                        Text.literal("Denied aipets.chat interaction was not blocked"));
                context.assertEquals(
                        1,
                        opens.get(),
                        Text.literal("Denied aipets.chat reached interaction handler"));
            } finally {
                restoreChatPermission.run();
            }

            ((PetEntityData) pet).aipets$setSleeping(true);
            ActionResult sleepingResult = UseEntityCallback.EVENT.invoker().interact(
                    owner,
                    world,
                    Hand.MAIN_HAND,
                    pet,
                    new EntityHitResult(pet));
            context.assertEquals(
                    ActionResult.SUCCESS_SERVER,
                    sleepingResult,
                    Text.literal("Sleeping interaction was not consumed with feedback"));
            context.assertEquals(
                    1,
                    opens.get(),
                    Text.literal("Sleeping pet reached the interaction handler"));
            ActionResult duplicateSleepingResult = UseEntityCallback.EVENT.invoker().interact(
                    owner,
                    world,
                    Hand.MAIN_HAND,
                    pet,
                    new EntityHitResult(pet));
            context.assertEquals(
                    ActionResult.SUCCESS_SERVER,
                    duplicateSleepingResult,
                    Text.literal("Duplicate sleeping interaction was not consumed"));
            context.assertEquals(
                    1,
                    opens.get(),
                    Text.literal("Duplicate sleeping interaction reached the handler"));
            ((PetEntityData) pet).aipets$setSleeping(false);

            ActionResult intruderResult = UseEntityCallback.EVENT.invoker().interact(
                    intruder,
                    world,
                    Hand.MAIN_HAND,
                    pet,
                    new EntityHitResult(pet));
            context.assertEquals(
                    ActionResult.FAIL,
                    intruderResult,
                    Text.literal("Non-owner interaction was not blocked"));
            context.assertEquals(1, opens.get(), Text.literal("Non-owner reached interaction handler"));

            ordinary = context.spawnEntity(EntityType.CAT, new BlockPos(3, 1, 1));
            ActionResult ordinaryResult = UseEntityCallback.EVENT.invoker().interact(
                    owner,
                    world,
                    Hand.MAIN_HAND,
                    ordinary,
                    new EntityHitResult(ordinary));
            context.assertEquals(
                    ActionResult.PASS,
                    ordinaryResult,
                    Text.literal("Ordinary cat interaction was intercepted"));
            context.assertEquals(1, opens.get(), Text.literal("Ordinary cat reached pet handler"));
        } finally {
            uninstallHandler.run();
            if (pet != null) {
                pet.discard();
            }
            if (ordinary != null) {
                ordinary.discard();
            }
            removeMockPlayer(world, intruder);
            removeMockPlayer(world, owner);
        }
        context.complete();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void petRootHelpAndStatusUseAsyncAuthority(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        Pet aggregate = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000022"),
                owner.getUuid(),
                PetSpecies.DOG,
                "minecraft:ashen",
                0.63,
                0L);
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, aggregate);
        authority.sleeping = true;
        authority.aiAccessEnabled = false;
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(
                new BackendId("gametest"), authority);
        CapturingCommandOutput output = new CapturingCommandOutput();
        ServerCommandSource source = owner.getCommandSource().withOutput(output);
        try {
            int helpResult = world.getServer().getCommandManager().getDispatcher()
                    .execute("pet", source);
            int statusResult = world.getServer().getCommandManager().getDispatcher()
                    .execute("pet status", source);
            context.assertEquals(1, helpResult, Text.literal("/pet help failed"));
            context.assertEquals(1, statusResult, Text.literal("/pet status failed"));
            context.assertEquals(
                    1,
                    authority.ownerLookupCount,
                    Text.literal("/pet status did not make exactly one owner lookup"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("/pet status")),
                    Text.literal("Root help omitted /pet status"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message ->
                            message.contains("Pepper")
                                    && message.contains("DOG")
                                    && message.contains("HELD")
                                    && message.contains("sleeping")
                                    && (message.contains("hibernating") || message.contains("quiet"))),
                    Text.literal("Status omitted authoritative held/sleep state"));
        } catch (CommandSyntaxException failure) {
            throw context.createError("Pet command execution failed: %s", failure.getMessage());
        } finally {
            uninstallGateway.run();
            removeMockPlayer(world, owner);
        }
        context.complete();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void externalServiceFailuresStayInsidePetCommands(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        Pet aggregate = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000044"),
                owner.getUuid(), PetSpecies.CAT, "minecraft:tabby", 0.66, 0L);
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, aggregate);
        authority.failAllRequests = true;
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(
                new BackendId("gametest"), authority);
        CapturingCommandOutput output = new CapturingCommandOutput();
        ServerCommandSource source = owner.getCommandSource().withOutput(output);
        try {
            List<String> commands = List.of(
                    "pet link", "pet portal", "pet status", "pet adopt cat Safe",
                    "pet place", "pet pickup", "pet recall", "pet compass");
            for (String command : commands) {
                context.assertEquals(
                        1,
                        world.getServer().getCommandManager().getDispatcher().execute(command, source),
                        Text.literal(command + " did not contain its service failure"));
            }
            context.waitAndRun(2, () -> {
                try {
                    try {
                        context.assertEquals(
                                1,
                                world.getServer().getCommandManager().getDispatcher().execute("pet", source),
                                Text.literal("Command dispatcher did not remain usable after failures"));
                    } catch (CommandSyntaxException failure) {
                        throw context.createError(
                                "Root command failed after contained outages: %s", failure.getMessage());
                    }
                    context.assertEquals(0, authority.mutationCount,
                            Text.literal("Failed service calls reached a physical mutation"));
                    context.assertEquals(aggregate, authority.current,
                            Text.literal("Failed service calls changed authoritative state"));
                    context.assertTrue(
                            output.messages.stream().anyMatch(message -> message.startsWith("Pet Companion:")),
                            Text.literal("Root help feedback was unavailable after contained failures"));
                } finally {
                    uninstallGateway.run();
                    removeMockPlayer(world, owner);
                }
                context.complete();
            });
        } catch (CommandSyntaxException failure) {
            uninstallGateway.run();
            removeMockPlayer(world, owner);
            throw context.createError("Failure-isolation command execution failed: %s", failure.getMessage());
        } catch (RuntimeException | Error failure) {
            uninstallGateway.run();
            removeMockPlayer(world, owner);
            throw failure;
        }
    }

    @GameTest
    @SuppressWarnings("removal")
    public void petPlaceAndPickupCommandsUseCommitSafeCoordinators(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        for (int x = 1; x <= 6; x++) {
            for (int z = 1; z <= 6; z++) {
                context.setBlockState(x, 0, z, Blocks.STONE);
                context.setBlockState(x, 1, z, Blocks.AIR);
                context.setBlockState(x, 2, z, Blocks.AIR);
            }
        }
        Vec3d ownerPosition = context.getAbsolute(new Vec3d(3.5, 1.0, 3.5));
        owner.refreshPositionAndAngles(ownerPosition, 0.0F, 0.0F);
        world.getChunkManager().updatePosition(owner);
        Pet aggregate = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000023"),
                owner.getUuid(),
                PetSpecies.CAT,
                "minecraft:tabby",
                0.66,
                0L);
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, aggregate);
        authority.aiAccessEnabled = false;
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(
                new BackendId("gametest"), authority);
        CapturingCommandOutput output = new CapturingCommandOutput();
        ServerCommandSource source = owner.getCommandSource().withOutput(output);
        try {
            int placeResult = world.getServer().getCommandManager().getDispatcher()
                    .execute("pet place", source);
            context.assertEquals(1, placeResult, Text.literal("/pet place did not schedule"));
            context.assertEquals(
                    PlacementState.PLACED,
                    authority.current.placementState(),
                    Text.literal("/pet place did not commit placement"));
            PlacedPlacement placed = (PlacedPlacement) authority.current.placement();
            context.assertTrue(
                    placed.entityUuid().isPresent()
                            && world.getEntityAnyDimension(placed.entityUuid().orElseThrow()) != null,
                    Text.literal("/pet place did not spawn its committed entity"));

            UUID firstPlacedEntity = placed.entityUuid().orElseThrow();
            int duplicatePlaceResult = world.getServer().getCommandManager().getDispatcher()
                    .execute("pet place", source);
            context.assertEquals(1, duplicatePlaceResult, Text.literal("Second /pet place did not schedule"));
            context.assertEquals(
                    1L,
                    authority.current.recordVersion(),
                    Text.literal("Second /pet place mutated authoritative state"));
            context.assertEquals(
                    firstPlacedEntity,
                    ((PlacedPlacement) authority.current.placement()).entityUuid().orElseThrow(),
                    Text.literal("Second /pet place replaced the authoritative entity"));
            context.assertEquals(
                    1,
                    authority.mutationCount,
                    Text.literal("Second /pet place reached the mutation endpoint"));

            int pickupResult = world.getServer().getCommandManager().getDispatcher()
                    .execute("pet pickup", source);
            context.assertEquals(1, pickupResult, Text.literal("/pet pickup did not schedule"));
            context.assertEquals(
                    PlacementState.HELD,
                    authority.current.placementState(),
                    Text.literal("/pet pickup did not commit held state"));
            context.assertEquals(
                    2L,
                    authority.current.recordVersion(),
                    Text.literal("Command place/pickup did not advance exactly two revisions"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("placed safely")),
                    Text.literal("/pet place omitted success feedback"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("now held")),
                    Text.literal("/pet pickup omitted success feedback"));
        } catch (CommandSyntaxException failure) {
            throw context.createError("Pet mutation command execution failed: %s", failure.getMessage());
        } finally {
            uninstallGateway.run();
            removeMockPlayer(world, owner);
        }
        context.complete();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void petRecallCommandInvalidatesOldEntityAndReportsMonthlyAvailability(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        for (int x = 1; x <= 6; x++) {
            for (int z = 1; z <= 6; z++) {
                context.setBlockState(x, 0, z, Blocks.STONE);
                context.setBlockState(x, 1, z, Blocks.AIR);
                context.setBlockState(x, 2, z, Blocks.AIR);
            }
        }
        Vec3d ownerPosition = context.getAbsolute(new Vec3d(3.5, 1.0, 3.5));
        owner.refreshPositionAndAngles(ownerPosition, 0.0F, 0.0F);
        world.getChunkManager().updatePosition(owner);
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000026");
        UUID oldEntityId = UUID.fromString("30000000-0000-0000-0000-000000000026");
        DimensionId dimension = DimensionId.parse(world.getRegistryKey().getValue().toString());
        BackendId backend = new BackendId("gametest");
        Pet base = pet(petId, owner.getUuid(), PetSpecies.CAT, "minecraft:tabby", 0.66, 1L);
        Pet placed = withPlacement(base, PlacedPlacement.materialized(
                backend,
                dimension,
                position(context.getAbsolute(new Vec3d(1.5, 1.0, 1.5))),
                oldEntityId));
        TameableEntity oldEntity = new PetEntityFactory().prepare(
                world, placed, oldEntityId,
                position(context.getAbsolute(new Vec3d(1.5, 1.0, 1.5))), false).entity();
        context.assertTrue(world.spawnEntity(oldEntity), Text.literal("Old recall entity did not spawn"));
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, placed);
        authority.aiAccessEnabled = false;
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(backend, authority);
        CapturingCommandOutput output = new CapturingCommandOutput();
        ServerCommandSource source = owner.getCommandSource().withOutput(output);
        try {
            context.assertEquals(
                    1,
                    world.getServer().getCommandManager().getDispatcher().execute("pet recall", source),
                    Text.literal("/pet recall did not schedule"));
            context.assertTrue(oldEntity.isRemoved(), Text.literal("Recall did not discard old loaded entity"));
            PlacedPlacement recalled = (PlacedPlacement) authority.current.placement();
            context.assertTrue(
                    !oldEntityId.equals(recalled.entityUuid().orElseThrow()),
                    Text.literal("Recall reused the stale entity UUID"));
            context.assertTrue(
                    world.getEntityAnyDimension(recalled.entityUuid().orElseThrow()) != null,
                    Text.literal("Recall did not spawn the committed destination entity"));
            context.assertEquals(
                    1,
                    world.getServer().getCommandManager().getDispatcher().execute("pet recall", source),
                    Text.literal("Second /pet recall did not schedule"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message ->
                            message.contains("recalled safely") && message.contains("Next recall:")),
                    Text.literal("Recall success omitted next availability"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message ->
                            message.contains("already used") && message.contains("Next recall:")),
                    Text.literal("Second recall omitted monthly availability feedback"));
        } catch (CommandSyntaxException failure) {
            throw context.createError("Pet recall command execution failed: %s", failure.getMessage());
        } finally {
            if (authority.current.placement() instanceof PlacedPlacement finalPlacement) {
                finalPlacement.entityUuid().ifPresent(id -> {
                    net.minecraft.entity.Entity entity = world.getEntityAnyDimension(id);
                    if (entity != null) entity.discard();
                });
            }
            uninstallGateway.run();
            removeMockPlayer(world, owner);
        }
        context.complete();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void offlineRecallSourceDiscardsSavedEntityWhenItReturns(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        BackendId sourceBackend = new BackendId("offline-source");
        BackendId destinationBackend = new BackendId("gametest");
        DimensionId dimension = DimensionId.parse(world.getRegistryKey().getValue().toString());
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000096");
        UUID oldEntityId = UUID.fromString("30000000-0000-0000-0000-000000000096");
        UUID recalledEntityId = UUID.fromString("30000000-0000-0000-0000-000000000097");
        Vec3d oldLocation = context.getAbsolute(new Vec3d(1.5, 1.0, 1.5));
        Vec3d destination = context.getAbsolute(new Vec3d(4.5, 1.0, 4.5));
        TameableEntity destinationEntity = null;
        try {
            Pet adopted = pet(
                    petId, owner.getUuid(), PetSpecies.CAT, "minecraft:tabby", 0.66, 0L);
            Pet sourcePlaced = PetTransitions.place(adopted, new PetTransitions.Place(
                    owner.getUuid(), adopted.recordVersion(), sourceBackend, dimension,
                    position(oldLocation), oldEntityId, ADOPTED_AT.plusSeconds(1))).pet();
            context.assertTrue(
                    world.getEntityAnyDimension(oldEntityId) == null,
                    Text.literal("Offline source entity was unexpectedly loaded during recall"));
            Pet recalled = PetTransitions.recall(sourcePlaced, new PetTransitions.Recall(
                    owner.getUuid(), sourcePlaced.recordVersion(), destinationBackend, dimension,
                    position(destination), recalledEntityId, ADOPTED_AT.plusSeconds(2))).pet();
            InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, recalled);

            destinationEntity = new PetEntityFactory().prepare(
                    world, recalled, recalledEntityId, position(destination), false).entity();
            context.assertTrue(
                    world.spawnEntity(destinationEntity),
                    Text.literal("Recalled destination entity did not spawn"));

            PetEntityReconciler restartedSource = new PetEntityReconciler(sourceBackend, authority);
            TameableEntity oldSaved = new PetEntityFactory().prepare(
                    world, sourcePlaced, oldEntityId, position(oldLocation), false).entity();
            context.assertTrue(world.spawnEntity(oldSaved), Text.literal("Saved source entity did not load"));
            context.assertEquals(
                    PetEntityReconciliationStatus.STALE_DISCARDED,
                    restartedSource.reconcileLoaded(oldSaved, world).join().status(),
                    Text.literal("Returned offline source entity was not classified stale"));
            context.assertTrue(
                    oldSaved.isRemoved(),
                    Text.literal("Returned offline source entity survived reconciliation"));

            TameableEntity replayedSave = new PetEntityFactory().prepare(
                    world, sourcePlaced, oldEntityId, position(oldLocation), false).entity();
            context.assertTrue(world.spawnEntity(replayedSave), Text.literal("Replayed old save did not load"));
            context.assertEquals(
                    PetEntityReconciliationStatus.STALE_DISCARDED,
                    new PetEntityReconciler(sourceBackend, authority)
                            .reconcileLoaded(replayedSave, world).join().status(),
                    Text.literal("Fresh source process did not reject the replayed save"));
            context.assertTrue(replayedSave.isRemoved(), Text.literal("Replayed save survived"));

            PetEntityRecoveryCoordinator destinationProcess = new PetEntityRecoveryCoordinator(
                    destinationBackend, authority,
                    new PetEntityReconciler(destinationBackend, authority),
                    new PetEntityFactory());
            context.assertEquals(
                    PetRecoveryStatus.AUTHORITATIVE_ENTITY_PRESENT,
                    destinationProcess.recoverOwner(world.getServer(), owner.getUuid())
                            .toCompletableFuture().join(),
                    Text.literal("Destination did not retain its sole authoritative entity"));
            int liveRepresentations = 0;
            for (net.minecraft.entity.Entity entity : world.iterateEntities()) {
                if (entity instanceof PetEntityData data
                        && data.aipets$isPet()
                        && petId.equals(data.aipets$getPetId())
                        && !entity.isRemoved()) {
                    liveRepresentations++;
                }
            }
            context.assertEquals(
                    1, liveRepresentations,
                    Text.literal("Offline recall produced more than one live representation"));
            context.assertTrue(
                    world.getEntityAnyDimension(recalledEntityId) == destinationEntity,
                    Text.literal("Recall destination identity changed during reconciliation"));
        } finally {
            if (destinationEntity != null) destinationEntity.discard();
            removeMockPlayer(world, owner);
        }
        context.complete();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void petCompassLifecycleIsSignedBoundAndLocationAware(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity intruder = context.createMockCreativeServerPlayerInWorld();
        BackendId localBackend = new BackendId("gametest");
        BackendId remoteBackend = new BackendId("survival");
        DimensionId localDimension = DimensionId.parse(
                world.getRegistryKey().getValue().toString());
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000024");
        Pet held = pet(
                petId,
                owner.getUuid(),
                PetSpecies.CAT,
                "minecraft:tabby",
                0.66,
                0L);
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, held);
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(
                localBackend,
                authority,
                "gametest-compass-signing-secret-0123456789abcdef",
                java.util.Map.of(
                        localBackend, "Game Test",
                        remoteBackend, "Survival Realm"));
        CapturingCommandOutput output = new CapturingCommandOutput();
        ServerCommandSource source = owner.getCommandSource().withOutput(output);
        TameableEntity liveEntity = null;
        ItemEntity dropped = null;
        try {
            int issueResult = world.getServer().getCommandManager().getDispatcher()
                    .execute("pet compass", source);
            context.assertEquals(1, issueResult, Text.literal("/pet compass did not schedule"));
            PetCompassManager manager = PetCompanionMod.petCompassManager().orElseThrow();
            ItemStack compass = findCompass(owner);
            context.assertTrue(
                    manager.isAllowedInPlayerInventory(compass, owner.getUuid()),
                    Text.literal("Issued compass did not have a valid owner/pet signature"));
            context.assertEquals(
                    "Held by you",
                    compass.get(DataComponentTypes.LORE).lines().getFirst().getString(),
                    Text.literal("Held compass status was incorrect"));

            owner.getInventory().setStack(owner.getInventory().getEmptySlot(), compass.copy());
            ItemStack forged = compass.copy();
            NbtCompound forgedCustom = forged.get(DataComponentTypes.CUSTOM_DATA).copyNbt();
            NbtCompound forgedRoot = forgedCustom.getCompound("pet_companion_compass").orElseThrow();
            forgedRoot.putString("signature", "0".repeat(64));
            forgedCustom.put("pet_companion_compass", forgedRoot);
            forged.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(forgedCustom));
            context.assertFalse(
                    manager.isAllowedInPlayerInventory(forged, owner.getUuid()),
                    Text.literal("Tampered compass signature was trusted"));
            owner.getInventory().setStack(owner.getInventory().getEmptySlot(), forged);
            PetCompassIssueResult deduplicated = manager.issueOrRefresh(
                    owner,
                    new PetAuthoritySnapshot(held, false));
            context.assertEquals(
                    PetCompassIssueStatus.REFRESHED,
                    deduplicated.status(),
                    Text.literal("Existing compass was not refreshed"));
            context.assertEquals(
                    2,
                    deduplicated.removedInvalidOrDuplicate(),
                    Text.literal("Duplicate/tampered compass was not removed"));
            context.assertEquals(1, countCompasses(owner), Text.literal("More than one compass remains"));

            Pet remote = withPlacement(
                    held,
                    PlacedPlacement.virtualized(
                            remoteBackend,
                            localDimension,
                            new WorldPosition(100.0, 70.0, 100.0)));
            manager.issueOrRefresh(owner, new PetAuthoritySnapshot(remote, false));
            compass = findCompass(owner);
            context.assertEquals(
                    "On Survival Realm",
                    compass.get(DataComponentTypes.LORE).lines().getFirst().getString(),
                    Text.literal("Remote backend friendly name was missing"));
            context.assertTrue(
                    compass.get(DataComponentTypes.LODESTONE_TRACKER) == null,
                    Text.literal("Remote compass retained a misleading direction"));

            Pet otherDimension = withPlacement(
                    held,
                    PlacedPlacement.virtualized(
                            localBackend,
                            DimensionId.parse("minecraft:the_nether"),
                            new WorldPosition(3.0, 65.0, 3.0)));
            manager.issueOrRefresh(owner, new PetAuthoritySnapshot(otherDimension, false));
            compass = findCompass(owner);
            context.assertEquals(
                    "In minecraft:the_nether",
                    compass.get(DataComponentTypes.LORE).lines().getFirst().getString(),
                    Text.literal("Different-dimension status was incorrect"));
            context.assertTrue(
                    compass.get(DataComponentTypes.LODESTONE_TRACKER) == null,
                    Text.literal("Different-dimension compass retained a direction"));

            BlockPos lastKnown = context.getAbsolutePos(new BlockPos(1, 1, 1));
            Pet virtualized = withPlacement(
                    held,
                    PlacedPlacement.virtualized(
                            localBackend,
                            localDimension,
                            new WorldPosition(lastKnown.getX(), lastKnown.getY(), lastKnown.getZ())));
            manager.issueOrRefresh(owner, new PetAuthoritySnapshot(virtualized, false));
            compass = findCompass(owner);
            LodestoneTrackerComponent lastKnownTracker = compass.get(DataComponentTypes.LODESTONE_TRACKER);
            context.assertEquals(
                    lastKnown,
                    lastKnownTracker.target().orElseThrow().pos(),
                    Text.literal("Compass did not point to last authoritative coordinates"));

            UUID entityId = UUID.fromString("30000000-0000-0000-0000-000000000024");
            BlockPos authoritativePosition = context.getAbsolutePos(new BlockPos(2, 1, 2));
            BlockPos livePosition = context.getAbsolutePos(new BlockPos(4, 1, 4));
            Pet materialized = withPlacement(
                    held,
                    PlacedPlacement.materialized(
                            localBackend,
                            localDimension,
                            new WorldPosition(
                                    authoritativePosition.getX(),
                                    authoritativePosition.getY(),
                                    authoritativePosition.getZ()),
                            entityId));
            authority.current = materialized;
            liveEntity = new PetEntityFactory().prepare(
                    world,
                    materialized,
                    entityId,
                    new WorldPosition(livePosition.getX(), livePosition.getY(), livePosition.getZ()),
                    false).entity();
            liveEntity.refreshPositionAndAngles(Vec3d.ofCenter(livePosition), 0.0F, 0.0F);
            context.assertTrue(world.spawnEntity(liveEntity), Text.literal("Live compass fixture did not spawn"));
            manager.issueOrRefresh(owner, new PetAuthoritySnapshot(materialized, false));
            compass = findCompass(owner);
            context.assertEquals(
                    liveEntity.getBlockPos(),
                    compass.get(DataComponentTypes.LODESTONE_TRACKER).target().orElseThrow().pos(),
                    Text.literal("Compass did not prefer the loaded live entity position"));
            PetCompassIssueResult inactiveAccess = manager.issueOrRefresh(
                    owner,
                    new PetAuthoritySnapshot(materialized, false, false));
            context.assertEquals(
                    PetCompassIssueStatus.REFRESHED,
                    inactiveAccess.status(),
                    Text.literal("Inactive AI access disabled physical compass refresh"));

            ItemStack enforcementCopy = compass.copy();
            context.assertFalse(
                    new Slot(new SimpleInventory(1), 0, 0, 0).canInsert(enforcementCopy),
                    Text.literal("Container slot accepted a pet compass"));
            context.assertFalse(
                    new Slot(intruder.getInventory(), 0, 0, 0).canInsert(enforcementCopy),
                    Text.literal("Another player's inventory accepted the pet compass"));
            SimpleInventory hopperSource = new SimpleInventory(enforcementCopy.copy());
            SimpleInventory hopperTarget = new SimpleInventory(1);
            ItemStack hopperRemainder = HopperBlockEntity.transfer(
                    hopperSource,
                    hopperTarget,
                    enforcementCopy.copy(),
                    Direction.DOWN);
            context.assertFalse(hopperRemainder.isEmpty(), Text.literal("Hopper consumed pet compass"));
            context.assertTrue(hopperTarget.isEmpty(), Text.literal("Hopper transferred pet compass"));

            owner.getInventory().clear();
            for (int slot = 0; slot < owner.getInventory().size(); slot++) {
                owner.getInventory().setStack(slot, new ItemStack(Items.STONE, 64));
            }
            authority.current = held;
            int fullResult = world.getServer().getCommandManager().getDispatcher()
                    .execute("pet compass", source);
            context.assertEquals(1, fullResult, Text.literal("Full-inventory compass command did not schedule"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("inventory is full")),
                    Text.literal("Full inventory did not produce clear feedback"));
            context.assertEquals(0, countCompasses(owner), Text.literal("Full inventory received a compass"));
            context.assertEquals(2, authority.ownerLookupCount, Text.literal("Compass command lookup count changed"));
            context.assertEquals(0, authority.mutationCount, Text.literal("Compass behavior wrote authority state"));

            Vec3d dropPosition = context.getAbsolute(new Vec3d(3.5, 2.0, 3.5));
            dropped = new ItemEntity(
                    world,
                    dropPosition.x,
                    dropPosition.y,
                    dropPosition.z,
                    enforcementCopy);
            context.assertTrue(world.spawnEntity(dropped), Text.literal("Dropped compass fixture did not spawn"));
            ItemEntity droppedReference = dropped;
            TameableEntity liveReference = liveEntity;
            context.waitAndRun(2, () -> {
                try {
                    context.assertTrue(
                            droppedReference.isRemoved(),
                            Text.literal("Dropped pet compass item entity was not removed"));
                } finally {
                    liveReference.discard();
                    uninstallGateway.run();
                    removeMockPlayer(world, intruder);
                    removeMockPlayer(world, owner);
                }
                context.complete();
            });
        } catch (CommandSyntaxException failure) {
            throw context.createError("Pet compass command execution failed: %s", failure.getMessage());
        } catch (RuntimeException | Error failure) {
            if (dropped != null) {
                dropped.discard();
            }
            if (liveEntity != null) {
                liveEntity.discard();
            }
            uninstallGateway.run();
            removeMockPlayer(world, intruder);
            removeMockPlayer(world, owner);
            throw failure;
        }
    }

    @GameTest
    @SuppressWarnings("removal")
    public void petAdoptCommandRequiresAccessAndNeverRerollsExistingPet(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, null);
        authority.adoptionAccess = false;
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(
                new BackendId("gametest"), authority);
        CapturingCommandOutput output = new CapturingCommandOutput();
        ServerCommandSource source = owner.getCommandSource().withOutput(output);
        try {
            var dispatcher = world.getServer().getCommandManager().getDispatcher();
            context.assertEquals(1, dispatcher.execute("pet adopt", source),
                    Text.literal("Adoption menu did not open"));
            context.assertEquals(1, dispatcher.execute("pet adopt cat", source),
                    Text.literal("Cat name entry did not open"));
            context.assertEquals(1, dispatcher.execute("pet adopt dog", source),
                    Text.literal("Dog name entry did not open"));
            context.assertEquals(0, authority.adoptionRequestCount,
                    Text.literal("Opening adoption UI submitted an adoption"));
            context.assertTrue(output.components.stream().flatMap(text -> text.getWithStyle(
                    net.minecraft.text.Style.EMPTY).stream()).anyMatch(text ->
                    text.getStyle().getClickEvent() instanceof net.minecraft.text.ClickEvent.SuggestCommand click
                            && click.command().equals("/pet adopt dog ")),
                    Text.literal("Dog name action did not populate a private slash command"));
            context.assertEquals(
                    1,
                    world.getServer().getCommandManager().getDispatcher()
                            .execute("pet adopt cat Luna", source),
                    Text.literal("Denied adoption command did not schedule"));
            context.assertTrue(authority.current == null, Text.literal("Denied adoption created a pet"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("active subscription")),
                    Text.literal("Denied adoption omitted subscription feedback"));

            authority.adoptionAccess = true;
            context.assertEquals(
                    1,
                    world.getServer().getCommandManager().getDispatcher()
                            .execute("pet adopt dog Pepper", source),
                    Text.literal("Allowed adoption command did not schedule"));
            Pet created = authority.current;
            context.assertEquals("Pepper", created.name(), Text.literal("Adoption name changed"));
            context.assertEquals(
                    PetSpecies.DOG,
                    created.appearance().species(),
                    Text.literal("Adoption species changed"));

            context.assertEquals(
                    1,
                    world.getServer().getCommandManager().getDispatcher()
                            .execute("pet adopt cat Reroll", source),
                    Text.literal("Repeat adoption command did not schedule"));
            context.assertTrue(authority.current == created, Text.literal("Repeat adoption rerolled the pet"));
            context.assertEquals(3, authority.adoptionRequestCount, Text.literal("Adoption request count changed"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("Adopted Pepper the dog")),
                    Text.literal("Successful adoption feedback was missing"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("already own Pepper")),
                    Text.literal("Existing-pet feedback was missing"));
        } catch (CommandSyntaxException failure) {
            throw context.createError("Pet adoption command execution failed: %s", failure.getMessage());
        } finally {
            uninstallGateway.run();
            removeMockPlayer(world, owner);
        }
        context.complete();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void petCommandsEnforceExplicitPermissionNodes(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
        Pet aggregate = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000027"),
                owner.getUuid(), PetSpecies.CAT, "minecraft:tabby", 0.66, 0L);
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, aggregate);
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(
                new BackendId("gametest"), authority);
        CapturingCommandOutput output = new CapturingCommandOutput();
        ServerCommandSource source = owner.getCommandSource().withOutput(output);
        Runnable restoreFeaturePermissions = PetPermissions.install(
                (ignored, node, defaultLevel) ->
                        !node.equals(PetPermission.ADOPT.node())
                                && !node.equals(PetPermission.RECALL.node()));
        try {
            context.assertEquals(
                    1,
                    world.getServer().getCommandManager().getDispatcher()
                            .execute("pet status", source),
                    Text.literal("Allowed aipets.use command failed"));
            context.assertEquals(
                    1,
                    world.getServer().getCommandManager().getDispatcher().execute("pet", source),
                    Text.literal("Permission-filtered /pet help failed"));
            String help = output.messages.stream()
                    .filter(message -> message.startsWith("Pet Companion:"))
                    .findFirst()
                    .orElseThrow(() -> context.createError("Permission-filtered help was missing"));
            context.assertFalse(help.contains("/pet adopt"), Text.literal("Help exposed denied adopt"));
            context.assertFalse(help.contains("/pet recall"), Text.literal("Help exposed denied recall"));
            context.assertTrue(help.contains("/pet compass"), Text.literal("Help omitted allowed compass"));
            assertCommandDenied(context, world, source, "pet adopt cat Blocked");
            assertCommandDenied(context, world, source, "pet recall");
            context.assertEquals(
                    0,
                    authority.adoptionRequestCount,
                    Text.literal("Denied aipets.adopt reached adoption authority"));
            context.assertEquals(
                    0,
                    authority.mutationCount,
                    Text.literal("Denied aipets.recall reached mutation authority"));
            restoreFeaturePermissions.run();

            Runnable restoreUse = PetPermissions.install(
                    (ignored, node, defaultLevel) -> !node.equals(PetPermission.USE.node()));
            try {
                assertCommandDenied(context, world, source, "pet status");
            } finally {
                restoreUse.run();
            }
        } catch (CommandSyntaxException failure) {
            throw context.createError("Allowed permission command failed: %s", failure.getMessage());
        } finally {
            restoreFeaturePermissions.run();
            uninstallGateway.run();
            removeMockPlayer(world, owner);
        }
        context.complete();
    }

    private static void assertCommandDenied(
            TestContext context,
            ServerWorld world,
            ServerCommandSource source,
            String command) {
        try {
            world.getServer().getCommandManager().getDispatcher().execute(command, source);
            throw context.createError("Permission gate allowed command: %s", command);
        } catch (CommandSyntaxException expected) {
            // Brigadier hides nodes whose requirement predicate denies the source.
        }
    }

    private static Pet pet(
            UUID petId,
            PetSpecies species,
            String variantId,
            double scale,
            long recordVersion) {
        return pet(petId, OWNER_ID, species, variantId, scale, recordVersion);
    }

    private static Pet pet(
            UUID petId,
            UUID ownerId,
            PetSpecies species,
            String variantId,
            double scale,
            long recordVersion) {
        return new Pet(
                petId,
                ownerId,
                species == PetSpecies.CAT ? "Mochi" : "Pepper",
                PetAppearance.create(
                        species,
                        ResourceId.parse(variantId),
                        scale,
                        OptionalLong.of(0x5EEDL),
                        APPEARANCE_RULES),
                PetTraits.initial(50, 50, 50, 50, 50, ADOPTED_AT),
                PetMood.initial(50, 20, 10, 10, ADOPTED_AT),
                HeldPlacement.INSTANCE,
                recordVersion,
                ADOPTED_AT,
                ADOPTED_AT);
    }

    private static Pet withPlacement(
            Pet pet,
            com.silver.aipets.common.domain.PetPlacement placement) {
        return new Pet(
                pet.petId(),
                pet.ownerUuid(),
                pet.name(),
                pet.appearance(),
                pet.traits(),
                pet.mood(),
                placement,
                pet.recordVersion(),
                pet.createdAt(),
                pet.updatedAt());
    }

    private static ItemStack findCompass(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (PetCompassItem.isCandidate(stack)) {
                return stack;
            }
        }
        throw new IllegalStateException("No pet compass in player inventory");
    }

    private static int countCompasses(ServerPlayerEntity player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (PetCompassItem.isCandidate(player.getInventory().getStack(slot))) {
                count++;
            }
        }
        return count;
    }

    private static void assertPhysicalPet(
            TestContext context,
            TameableEntity entity,
            Pet pet,
            String expectedVariantId) {
        PetEntityData data = (PetEntityData) entity;
        context.assertTrue(data.aipets$isPet(), Text.literal("Physical entity is not marked"));
        context.assertEquals(pet.petId(), data.aipets$getPetId(), Text.literal("Pet ID mismatch"));
        context.assertEquals(pet.ownerUuid(), data.aipets$getOwnerUuid(), Text.literal("Owner ID mismatch"));
        context.assertEquals(pet.recordVersion(), data.aipets$getRecordVersion(), Text.literal("Revision mismatch"));
        context.assertTrue(entity.isInvulnerable(), Text.literal("Physical entity is not invulnerable"));
        context.assertTrue(entity.isPersistent(), Text.literal("Physical entity is not persistent"));
        context.assertTrue(
                entity.getCommandTags().contains(PetEntityController.NO_DESPAWN_TAG),
                Text.literal("Physical entity lacks no_despawn tag"));
        context.assertTrue(
                Math.abs(entity.getAttributeBaseValue(EntityAttributes.SCALE) - pet.appearance().scale()) < 1.0E-9,
                Text.literal("Scale attribute base value mismatch"));
        context.assertTrue(
                Math.abs(entity.getScale() - pet.appearance().scale()) < 1.0E-6,
                Text.literal("Effective entity scale mismatch"));

        Identifier materializedVariant;
        if (entity instanceof CatEntity cat) {
            Registry<CatVariant> registry = context.getWorld()
                    .getRegistryManager()
                    .getOrThrow(RegistryKeys.CAT_VARIANT);
            materializedVariant = registry.getId(cat.getVariant().value());
        } else if (entity instanceof WolfEntity wolf) {
            Registry<WolfVariant> registry = context.getWorld()
                    .getRegistryManager()
                    .getOrThrow(RegistryKeys.WOLF_VARIANT);
            materializedVariant = registry.getId(
                    ((WolfEntityVariantInvoker) wolf).aipets$getVariant().value());
        } else {
            throw context.createError("Unexpected physical entity type: %s", entity.getType());
        }
        context.assertEquals(
                expectedVariantId,
                materializedVariant.toString(),
                Text.literal("Materialized variant mismatch"));
    }

    private static void setScale(TameableEntity entity, double scale) {
        entity.getAttributeInstance(EntityAttributes.SCALE).setBaseValue(scale);
    }

    private static <T> void assertRegistryContains(
            TestContext context,
            Registry<T> registry,
            ResourceId variant) {
        context.assertTrue(
                registry.containsId(Identifier.of(variant.value())),
                Text.literal("Configured variant missing at runtime: " + variant));
    }

    private static WorldPosition position(Vec3d position) {
        return new WorldPosition(position.x, position.y, position.z);
    }

    private static void removeMockPlayer(ServerWorld world, ServerPlayerEntity player) {
        PlayerManager playerManager = world.getServer().getPlayerManager();
        if (playerManager.getPlayer(player.getUuid()) == player) {
            playerManager.remove(player);
        }
    }

    private static final class InMemoryAuthorityGateway implements PetAuthorityGateway {
        private final ServerWorld world;
        private Pet current;
        private boolean entityWasAbsentAtCommit;
        private int compensationCount;
        private int petIdLookupCount;
        private int ownerLookupCount;
        private int mutationCount;
        private boolean sleeping;
        private boolean aiAccessEnabled = true;
        private boolean adoptionAccess = true;
        private int adoptionRequestCount;
        private CompletableFuture<Optional<PetAuthoritySnapshot>> pendingOwnerLookup;
        private boolean recallConsumed;
        private UUID recallOperationId;
        private boolean entityWasPresentAtTransferCommit;
        private boolean failAllRequests;

        private InMemoryAuthorityGateway(ServerWorld world, Pet initial) {
            this.world = world;
            this.current = initial;
        }

        @Override
        public CompletionStage<PetAdoptionWireResult> adopt(PetAdoptionWireRequest request) {
            if (failAllRequests) {
                return CompletableFuture.failedFuture(new IllegalStateException("simulated outage"));
            }
            adoptionRequestCount++;
            if (current != null && current.ownerUuid().equals(request.ownerUuid())) {
                return CompletableFuture.completedFuture(new PetAdoptionWireResult(
                        PetAdoptionWireStatus.EXISTING, Optional.of(current)));
            }
            if (!adoptionAccess) {
                return CompletableFuture.completedFuture(new PetAdoptionWireResult(
                        PetAdoptionWireStatus.SUBSCRIPTION_REQUIRED, Optional.empty()));
            }
            String variant = request.species() == PetSpecies.CAT
                    ? "minecraft:tabby"
                    : "minecraft:ashen";
            double scale = request.species() == PetSpecies.CAT ? 0.66 : 0.63;
            current = Pet.adopted(
                    UUID.fromString("10000000-0000-0000-0000-000000000025"),
                    request.ownerUuid(),
                    request.name(),
                    PetAppearance.create(
                            request.species(),
                            ResourceId.parse(variant),
                            scale,
                            OptionalLong.of(0xA10L),
                            APPEARANCE_RULES),
                    PetTraits.initial(50, 50, 50, 50, 50, ADOPTED_AT),
                    PetMood.initial(50, 20, 10, 10, ADOPTED_AT),
                    ADOPTED_AT);
            return CompletableFuture.completedFuture(new PetAdoptionWireResult(
                    PetAdoptionWireStatus.CREATED, Optional.of(current)));
        }

        @Override
        public CompletionStage<Boolean> findSubscriptionAccess(UUID ownerUuid) {
            if (failAllRequests) {
                return CompletableFuture.failedFuture(new IllegalStateException("simulated outage"));
            }
            return CompletableFuture.completedFuture(adoptionAccess);
        }

        @Override
        public CompletionStage<Optional<PetAuthoritySnapshot>> findByOwner(UUID ownerUuid) {
            ownerLookupCount++;
            if (failAllRequests) {
                return CompletableFuture.failedFuture(new IllegalStateException("simulated outage"));
            }
            if (pendingOwnerLookup != null) {
                return pendingOwnerLookup;
            }
            Optional<PetAuthoritySnapshot> snapshot = current != null && current.ownerUuid().equals(ownerUuid)
                    ? Optional.of(new PetAuthoritySnapshot(current, sleeping, aiAccessEnabled))
                    : Optional.empty();
            return CompletableFuture.completedFuture(snapshot);
        }

        @Override
        public CompletionStage<Optional<PetAuthoritySnapshot>> findByPetId(UUID petId) {
            petIdLookupCount++;
            Optional<PetAuthoritySnapshot> snapshot = current != null && current.petId().equals(petId)
                    ? Optional.of(new PetAuthoritySnapshot(current, sleeping, aiAccessEnabled))
                    : Optional.empty();
            return CompletableFuture.completedFuture(snapshot);
        }

        @Override
        public CompletionStage<AuthorityMutationResult> place(
                UUID operationId,
                UUID petId,
                PetTransitions.Place command) {
            mutationCount++;
            entityWasAbsentAtCommit = world.getEntityAnyDimension(command.entityUuid()) == null;
            return CompletableFuture.completedFuture(apply(
                    petId,
                    PetTransitions.place(current, command)));
        }

        @Override
        public CompletionStage<AuthorityMutationResult> compensatePlaceFailure(
                UUID operationId,
                UUID petId,
                PetTransitions.CompensatePlaceFailure command) {
            mutationCount++;
            compensationCount++;
            return CompletableFuture.completedFuture(apply(
                    petId,
                    PetTransitions.compensatePlaceFailure(current, command)));
        }

        @Override
        public CompletionStage<AuthorityMutationResult> pickup(
                UUID operationId,
                UUID petId,
                PetTransitions.Pickup command) {
            mutationCount++;
            return CompletableFuture.completedFuture(apply(
                    petId,
                    PetTransitions.pickup(current, command)));
        }

        @Override
        public CompletionStage<AuthorityMutationResult> prepareTransfer(
                UUID operationId,
                UUID petId,
                PetTransitions.PrepareTransfer command) {
            mutationCount++;
            entityWasPresentAtTransferCommit = world.getEntityAnyDimension(
                    command.transfer().sourceEntityUuid()) != null;
            return CompletableFuture.completedFuture(apply(
                    petId, PetTransitions.prepareTransfer(current, command)));
        }

        @Override
        public CompletionStage<AuthorityMutationResult> completeTransfer(
                UUID operationId,
                UUID petId,
                PetTransitions.CompleteTransfer command) {
            mutationCount++;
            return CompletableFuture.completedFuture(apply(
                    petId, PetTransitions.completeTransfer(current, command)));
        }

        @Override
        public CompletionStage<AuthorityMutationResult> expireTransfer(
                UUID operationId,
                UUID petId,
                PetTransitions.ExpireTransfer command) {
            mutationCount++;
            return CompletableFuture.completedFuture(apply(
                    petId, PetTransitions.expireTransfer(current, command)));
        }

        @Override
        public CompletionStage<PetRecallWireResult> recall(
                UUID operationId,
                UUID petId,
                PetTransitions.Recall command) {
            mutationCount++;
            Instant next = Instant.parse("2026-09-01T00:00:00Z");
            if (recallConsumed) {
                return CompletableFuture.completedFuture(new PetRecallWireResult(
                        PetRecallWireStatus.UNAVAILABLE,
                        Optional.of(current), Optional.empty(), Optional.of(next)));
            }
            TransitionResult transition = PetTransitions.recall(current, command);
            if (!transition.applied()) {
                return CompletableFuture.completedFuture(new PetRecallWireResult(
                        PetRecallWireStatus.REJECTED,
                        Optional.of(current), Optional.of(transition.failureOrThrow()), Optional.empty()));
            }
            current = transition.pet();
            recallConsumed = true;
            recallOperationId = operationId;
            return CompletableFuture.completedFuture(new PetRecallWireResult(
                    PetRecallWireStatus.APPLIED,
                    Optional.of(current), Optional.empty(), Optional.of(next)));
        }

        @Override
        public CompletionStage<PetRecallWireResult> compensateRecallFailure(
                UUID operationId,
                UUID petId,
                PetTransitions.CompensateRecallFailure command) {
            mutationCount++;
            if (!operationId.equals(recallOperationId)) {
                return CompletableFuture.completedFuture(new PetRecallWireResult(
                        PetRecallWireStatus.REJECTED,
                        Optional.of(current),
                        Optional.of(com.silver.aipets.common.authority.TransitionFailure.STATE_MISMATCH),
                        Optional.empty()));
            }
            TransitionResult transition = PetTransitions.compensateRecallFailure(current, command);
            if (!transition.applied()) {
                return CompletableFuture.completedFuture(new PetRecallWireResult(
                        PetRecallWireStatus.REJECTED,
                        Optional.of(current), Optional.of(transition.failureOrThrow()), Optional.empty()));
            }
            current = transition.pet();
            recallConsumed = false;
            return CompletableFuture.completedFuture(new PetRecallWireResult(
                    PetRecallWireStatus.COMPENSATED,
                    Optional.of(current), Optional.empty(), Optional.empty()));
        }

        private AuthorityMutationResult apply(UUID petId, TransitionResult transition) {
            if (current == null || !current.petId().equals(petId)) {
                return AuthorityMutationResult.notFound();
            }
            if (!transition.applied()) {
                return AuthorityMutationResult.rejected(current, transition.failureOrThrow());
            }
            current = transition.pet();
            return AuthorityMutationResult.applied(current);
        }
    }

    private static final class CapturingCommandOutput implements CommandOutput {
        private final List<String> messages = new ArrayList<>();
        private final List<Text> components = new ArrayList<>();

        @Override
        public void sendMessage(Text message) {
            messages.add(message.getString());
            components.add(message.copy());
        }

        @Override
        public boolean shouldReceiveFeedback() {
            return true;
        }

        @Override
        public boolean shouldTrackOutput() {
            return false;
        }

        @Override
        public boolean shouldBroadcastConsoleToOps() {
            return false;
        }
    }
}
