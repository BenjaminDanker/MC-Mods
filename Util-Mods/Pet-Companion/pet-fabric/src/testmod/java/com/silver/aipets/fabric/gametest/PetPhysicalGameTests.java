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
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
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
    public void randomizedAdoptionAppearanceMatchesRuntimeRegistries(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        AppearanceCatalog catalog = AppearanceCatalog.vanilla12110();
        Registry<CatVariant> cats = world.registryAccess().lookupOrThrow(Registries.CAT_VARIANT);
        Registry<WolfVariant> wolves = world.registryAccess().lookupOrThrow(Registries.WOLF_VARIANT);

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
                    case CAT -> cats.containsKey(Identifier.parse(profile.appearance().variantId().value()));
                    case DOG -> wolves.containsKey(Identifier.parse(profile.appearance().variantId().value()));
                };
                context.assertTrue(registered, Component.literal("Randomized variant must exist at runtime"));
                context.assertTrue(
                        APPEARANCE_RULES.scaleRange(species).contains(profile.appearance().scale()),
                        Component.literal("Randomized scale must remain inside configured species bounds"));
            }
        }
        context.succeed();
    }

    @GameTest
    public void factoryMaterializesExactIdentityAppearanceAndSafety(GameTestHelper context) {
        PetEntityFactory factory = new PetEntityFactory();
        Vec3 catPosition = context.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        Vec3 dogPosition = context.absoluteVec(new Vec3(3.5, 1.0, 1.5));
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
                context.getLevel(),
                catPet,
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                position(catPosition),
                false);
        PreparedPetEntity preparedDog = factory.prepare(
                context.getLevel(),
                dogPet,
                UUID.fromString("30000000-0000-0000-0000-000000000002"),
                position(dogPosition),
                false);

        context.assertFalse(preparedCat.compatibilityRepairRequired(), Component.literal("Known cat variant repaired"));
        context.assertFalse(preparedDog.compatibilityRepairRequired(), Component.literal("Known dog variant repaired"));
        context.assertTrue(context.getLevel().addFreshEntity(preparedCat.entity()), Component.literal("Cat spawn failed"));
        context.assertTrue(context.getLevel().addFreshEntity(preparedDog.entity()), Component.literal("Dog spawn failed"));

        assertPhysicalPet(context, preparedCat.entity(), catPet, "minecraft:tabby");
        assertPhysicalPet(context, preparedDog.entity(), dogPet, "minecraft:ashen");
        context.succeed();
    }

    @GameTest
    public void genericScaleControlsCatAndWolfHitboxes(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Cat smallCat = EntityTypes.CAT.create(world, EntitySpawnReason.COMMAND);
        Cat largeCat = EntityTypes.CAT.create(world, EntitySpawnReason.COMMAND);
        Wolf smallWolf = EntityTypes.WOLF.create(world, EntitySpawnReason.COMMAND);
        Wolf largeWolf = EntityTypes.WOLF.create(world, EntitySpawnReason.COMMAND);
        context.assertTrue(smallCat != null && largeCat != null && smallWolf != null && largeWolf != null,
                Component.literal("Minecraft failed to construct scale test entities"));
        setScale(smallCat, 0.5);
        setScale(largeCat, 1.25);
        setScale(smallWolf, 0.5);
        setScale(largeWolf, 1.25);
        Vec3 smallCatPos = context.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        Vec3 largeCatPos = context.absoluteVec(new Vec3(3.5, 1.0, 1.5));
        Vec3 smallWolfPos = context.absoluteVec(new Vec3(1.5, 1.0, 3.5));
        Vec3 largeWolfPos = context.absoluteVec(new Vec3(3.5, 1.0, 3.5));
        smallCat.snapTo(smallCatPos.x, smallCatPos.y, smallCatPos.z, 0, 0);
        largeCat.snapTo(largeCatPos.x, largeCatPos.y, largeCatPos.z, 0, 0);
        smallWolf.snapTo(smallWolfPos.x, smallWolfPos.y, smallWolfPos.z, 0, 0);
        largeWolf.snapTo(largeWolfPos.x, largeWolfPos.y, largeWolfPos.z, 0, 0);
        world.addFreshEntity(smallCat);
        world.addFreshEntity(largeCat);
        world.addFreshEntity(smallWolf);
        world.addFreshEntity(largeWolf);
        context.runAfterDelay(1, () -> {
            context.assertTrue(smallCat.getBbWidth() < largeCat.getBbWidth(),
                    Component.literal("Cat generic.scale did not change hitbox width"));
            context.assertTrue(smallCat.getBbHeight() < largeCat.getBbHeight(),
                    Component.literal("Cat generic.scale did not change hitbox height"));
            context.assertTrue(smallWolf.getBbWidth() < largeWolf.getBbWidth(),
                    Component.literal("Wolf generic.scale did not change hitbox width"));
            context.assertTrue(smallWolf.getBbHeight() < largeWolf.getBbHeight(),
                    Component.literal("Wolf generic.scale did not change hitbox height"));
            smallCat.discard();
            largeCat.discard();
            smallWolf.discard();
            largeWolf.discard();
            context.succeed();
        });
    }

    @GameTest
    public void identitySurvivesVanillaNbtRoundTrip(GameTestHelper context) {
        Pet pet = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000003"),
                PetSpecies.CAT,
                "minecraft:calico",
                0.72,
                19L);
        Vec3 absolute = context.absoluteVec(new Vec3(2.5, 1.0, 2.5));
        TamableAnimal original = new PetEntityFactory().prepare(
                context.getLevel(),
                pet,
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                position(absolute),
                true).entity();

        TagValueOutput writeView = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING,
                context.getLevel().registryAccess());
        original.saveWithoutId(writeView);

        Cat restored = EntityTypes.CAT.create(context.getLevel(), EntitySpawnReason.COMMAND);
        context.assertTrue(restored != null, Component.literal("Minecraft failed to create restore target"));
        restored.load(TagValueInput.create(
                ProblemReporter.DISCARDING,
                context.getLevel().registryAccess(),
                writeView.buildResult()));

        PetEntityData restoredData = (PetEntityData) restored;
        assertPhysicalPet(context, restored, pet, "minecraft:calico");
        context.assertTrue(restoredData.aipets$isSleeping(), Component.literal("Sleeping flag changed"));
        context.succeed();
    }

    @GameTest
    public void markedPetsRejectVanillaCombatBreedingTamingAndTeleport(GameTestHelper context) {
        Vec3 absolute = context.absoluteVec(new Vec3(2.5, 1.0, 2.5));
        Cat pet = (Cat) new PetEntityFactory().prepare(
                context.getLevel(),
                pet(
                        UUID.fromString("10000000-0000-0000-0000-000000000004"),
                        PetSpecies.CAT,
                        "minecraft:jellie",
                        0.70,
                        3L),
                UUID.fromString("30000000-0000-0000-0000-000000000004"),
                position(absolute),
                false).entity();
        Vec3 dogAbsolute = context.absoluteVec(new Vec3(5.5, 1.0, 2.5));
        Wolf dog = (Wolf) new PetEntityFactory().prepare(
                context.getLevel(),
                pet(
                        UUID.fromString("10000000-0000-0000-0000-000000000006"),
                        PetSpecies.DOG,
                        "minecraft:woods",
                        0.64,
                        4L),
                UUID.fromString("30000000-0000-0000-0000-000000000007"),
                position(dogAbsolute),
                false).entity();
        Cat ordinary = context.spawnWithNoFreeWill(EntityTypes.CAT, new Vec3(4.5, 1.0, 2.5));
        context.assertTrue(context.getLevel().addFreshEntity(pet), Component.literal("Marked cat spawn failed"));
        context.assertTrue(context.getLevel().addFreshEntity(dog), Component.literal("Marked dog spawn failed"));

        float health = pet.getHealth();
        boolean damaged = pet.hurtServer(context.getLevel(), pet.damageSources().generic(), 2.0F);
        context.assertFalse(damaged, Component.literal("Marked pet accepted damage"));
        context.assertTrue(pet.getHealth() == health, Component.literal("Marked pet health changed"));

        pet.setTarget(ordinary);
        context.assertTrue(pet.getTarget() == null, Component.literal("Marked pet accepted a target"));
        context.assertFalse(
                pet.doHurtTarget(context.getLevel(), ordinary),
                Component.literal("Marked pet attacked another entity"));
        pet.setAge(-24_000);
        context.assertValueEqual(0, pet.getAge(), Component.literal("Marked pet became a baby"));
        pet.setInLoveTime(100);
        context.assertValueEqual(0, pet.getInLoveTime(), Component.literal("Marked pet entered love mode"));
        context.assertFalse(pet.canMate(ordinary), Component.literal("Marked pet can breed"));
        pet.setTame(true, true);
        context.assertFalse(pet.isTame(), Component.literal("Marked pet entered vanilla tamed state"));
        pet.setOwner(ordinary);
        context.assertTrue(pet.getOwnerReference() == null, Component.literal("Marked pet owner changed"));
        context.assertFalse(pet.canBeLeashed(), Component.literal("Marked pet can be leashed"));
        context.assertFalse(
                dog.wantsToAttack(ordinary, pet),
                Component.literal("Marked dog can assist owner combat"));
        dog.setTame(true, true);
        context.assertFalse(dog.isTame(), Component.literal("Marked dog entered vanilla tamed state"));
        dog.setOwner(ordinary);
        context.assertTrue(dog.getOwnerReference() == null, Component.literal("Marked dog owner changed"));
        context.assertFalse(
                pet.shouldTryTeleportToOwner(),
                Component.literal("Marked pet requested vanilla owner teleport"));
        Vec3 beforeTeleportAttempt = pet.position();
        pet.tryToTeleportToOwner();
        context.assertValueEqual(
                beforeTeleportAttempt,
                pet.position(),
                Component.literal("Marked pet used vanilla owner teleport"));
        context.succeed();
    }

    @GameTest
    public void ordinaryCatsAndWolvesRetainVanillaBehavior(GameTestHelper context) {
        Cat ordinary = context.spawnWithNoFreeWill(EntityTypes.CAT, new Vec3(2.5, 1.0, 2.5));
        Wolf ordinaryWolf = context.spawnWithNoFreeWill(EntityTypes.WOLF, new Vec3(4.5, 1.0, 2.5));
        PetEntityData data = (PetEntityData) ordinary;
        context.assertFalse(data.aipets$isPet(), Component.literal("Ordinary cat was marked as a pet"));
        context.assertFalse(
                ((PetEntityData) ordinaryWolf).aipets$isPet(),
                Component.literal("Ordinary wolf was marked as a pet"));

        ordinary.setAge(-24_000);
        context.assertValueEqual(-24_000, ordinary.getAge(), Component.literal("Ordinary cat age was intercepted"));
        ordinary.setAge(0);
        ordinary.setInLoveTime(100);
        context.assertValueEqual(100, ordinary.getInLoveTime(), Component.literal("Ordinary cat love mode was intercepted"));
        ordinary.setTame(true, true);
        context.assertTrue(ordinary.isTame(), Component.literal("Ordinary cat taming was intercepted"));

        ordinary.setInvulnerable(false);
        float health = ordinary.getHealth();
        boolean damaged = ordinary.hurtServer(context.getLevel(), ordinary.damageSources().generic(), 1.0F);
        context.assertTrue(damaged, Component.literal("Ordinary cat damage was intercepted"));
        context.assertTrue(ordinary.getHealth() < health, Component.literal("Ordinary cat health did not decrease"));

        ordinaryWolf.setAge(-24_000);
        context.assertValueEqual(
                -24_000,
                ordinaryWolf.getAge(),
                Component.literal("Ordinary wolf age was intercepted"));
        ordinaryWolf.setAge(0);
        ordinaryWolf.setInLoveTime(100);
        context.assertValueEqual(
                100,
                ordinaryWolf.getInLoveTime(),
                Component.literal("Ordinary wolf love mode was intercepted"));
        ordinaryWolf.setTame(true, true);
        context.assertTrue(ordinaryWolf.isTame(), Component.literal("Ordinary wolf taming was intercepted"));
        ordinaryWolf.setOwner(ordinary);
        context.assertTrue(
                ordinaryWolf.getOwnerReference() != null,
                Component.literal("Ordinary wolf ownership was intercepted"));
        ordinaryWolf.setInvulnerable(false);
        float wolfHealth = ordinaryWolf.getHealth();
        boolean wolfDamaged = ordinaryWolf.hurtServer(
                context.getLevel(),
                ordinaryWolf.damageSources().generic(),
                1.0F);
        context.assertTrue(wolfDamaged, Component.literal("Ordinary wolf damage was intercepted"));
        context.assertTrue(
                ordinaryWolf.getHealth() < wolfHealth,
                Component.literal("Ordinary wolf health did not decrease"));
        context.succeed();
    }

    @GameTest
    public void removedVariantUsesDeterministicRegistryFallback(GameTestHelper context) {
        Pet missingVariant = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000005"),
                PetSpecies.DOG,
                "example:removed_variant",
                0.60,
                31L);
        PetEntityFactory factory = new PetEntityFactory();
        Vec3 absolute = context.absoluteVec(new Vec3(2.5, 1.0, 2.5));
        PreparedPetEntity first = factory.prepare(
                context.getLevel(),
                missingVariant,
                UUID.fromString("30000000-0000-0000-0000-000000000005"),
                position(absolute),
                false);
        PreparedPetEntity second = factory.prepare(
                context.getLevel(),
                missingVariant,
                UUID.fromString("30000000-0000-0000-0000-000000000006"),
                position(absolute),
                false);

        context.assertTrue(first.compatibilityRepairRequired(), Component.literal("Missing variant was not detected"));
        context.assertValueEqual(
                first.effectiveVariantId(),
                second.effectiveVariantId(),
                Component.literal("Compatibility fallback was not deterministic"));
        Registry<WolfVariant> wolves = context.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.WOLF_VARIANT);
        context.assertTrue(
                wolves.containsKey(Identifier.parse(first.effectiveVariantId().value())),
                Component.literal("Compatibility fallback was not a runtime wolf variant"));
        context.succeed();
    }

    @GameTest
    public void safePlacementRejectsHazardsVoidSolidBlocksAndEntities(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos relativeFeet = new BlockPos(3, 1, 3);
        BlockPos absoluteFeet = context.absolutePos(relativeFeet);
        Vec3 center = Vec3.atBottomCenterOf(absoluteFeet);
        TamableAnimal prototype = new PetEntityFactory().prepare(
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
        Set<Long> forcedChunksBefore = Set.copyOf(world.getForceLoadedChunks());

        context.setBlock(3, 0, 3, Blocks.STONE);
        context.setBlock(3, 1, 3, Blocks.AIR);
        context.setBlock(3, 2, 3, Blocks.AIR);
        context.assertTrue(
                exact.find(world, prototype, absoluteFeet).isPresent(),
                Component.literal("Clear supported position was rejected"));

        context.setBlock(3, 1, 3, Blocks.LAVA);
        context.assertTrue(
                exact.find(world, prototype, absoluteFeet).isEmpty(),
                Component.literal("Lava position was accepted"));
        context.setBlock(3, 1, 3, Blocks.STONE);
        context.assertTrue(
                exact.find(world, prototype, absoluteFeet).isEmpty(),
                Component.literal("Solid/suffocating position was accepted"));
        context.setBlock(3, 1, 3, Blocks.AIR);
        context.setBlock(3, 0, 3, Blocks.AIR);
        context.assertTrue(
                exact.find(world, prototype, absoluteFeet).isEmpty(),
                Component.literal("Unsupported void position was accepted"));

        context.setBlock(3, 0, 3, Blocks.STONE);
        Cat occupant = context.spawnWithNoFreeWill(EntityTypes.CAT, new Vec3(3.5, 1.0, 3.5));
        context.assertTrue(
                exact.find(world, prototype, absoluteFeet).isEmpty(),
                Component.literal("Entity-occupied position was accepted"));

        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                context.setBlock(x, 0, z, Blocks.STONE);
                context.setBlock(x, 1, z, Blocks.AIR);
                context.setBlock(x, 2, z, Blocks.AIR);
            }
        }
        Optional<WorldPosition> nearby = new SafePlacementFinder(1, 0)
                .find(world, prototype, absoluteFeet);
        context.assertTrue(nearby.isPresent(), Component.literal("Nearby safe position was not found"));
        context.assertFalse(
                nearby.orElseThrow().equals(position(center)),
                Component.literal("Finder reused the entity-occupied origin"));
        context.assertValueEqual(
                forcedChunksBefore,
                Set.copyOf(world.getForceLoadedChunks()),
                Component.literal("Safe placement search changed forced chunks"));
        occupant.discard();
        context.succeed();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void placementAndPickupCommitOrderingAndCompensation(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        try {
            for (int x = 1; x <= 6; x++) {
                for (int z = 1; z <= 6; z++) {
                    context.setBlock(x, 0, z, Blocks.STONE);
                    context.setBlock(x, 1, z, Blocks.AIR);
                    context.setBlock(x, 2, z, Blocks.AIR);
                }
            }
            Vec3 ownerPosition = context.absoluteVec(new Vec3(3.5, 1.0, 3.5));
            owner.snapTo(ownerPosition, 0.0F, 0.0F);
            world.getChunkSource().move(owner);

            UUID successfulEntityId = UUID.fromString("30000000-0000-0000-0000-000000000010");
            InMemoryAuthorityGateway successAuthority = new InMemoryAuthorityGateway(
                    world,
                    pet(
                            UUID.fromString("10000000-0000-0000-0000-000000000009"),
                            owner.getUUID(),
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
            context.assertTrue(successFuture.isDone(), Component.literal("Synchronous placement did not complete"));
            PetPlacementOutcome success = successFuture.getNow(null);
            context.assertValueEqual(
                    PetPlacementStatus.PLACED,
                    success.status(),
                    Component.literal("Commit-first placement did not succeed"));
            context.assertTrue(
                    successAuthority.entityWasAbsentAtCommit,
                    Component.literal("Entity existed before authoritative placement commit"));
            TamableAnimal spawned = (TamableAnimal) world.getEntityInAnyDimension(successfulEntityId);
            context.assertTrue(spawned != null, Component.literal("Committed physical entity was not spawned"));
            context.assertValueEqual(
                    successAuthority.current.recordVersion(),
                    ((PetEntityData) spawned).aipets$getRecordVersion(),
                    Component.literal("Spawned entity revision is stale"));
            context.assertValueEqual(
                    PlacementState.PLACED,
                    successAuthority.current.placementState(),
                    Component.literal("Authority is not placed after successful spawn"));
            successAuthority.sleeping = true;
            ((PetEntityData) spawned).aipets$setSleeping(true);
            PetPickupCoordinator pickupCoordinator = new PetPickupCoordinator(
                    new BackendId("gametest"),
                    successAuthority,
                    4.0,
                    Clock.fixed(ADOPTED_AT, ZoneOffset.UTC),
                    () -> UUID.fromString("40000000-0000-0000-0000-000000000004"));
            CompletableFuture<PetPickupOutcome> pickupFuture = pickupCoordinator.pickup(owner);
            context.assertTrue(pickupFuture.isDone(), Component.literal("Pickup did not complete"));
            PetPickupOutcome pickup = pickupFuture.getNow(null);
            context.assertValueEqual(
                    PetPickupStatus.PICKED_UP,
                    pickup.status(),
                    Component.literal("Nearby sleeping pet was not picked up"));
            context.assertTrue(spawned.isRemoved(), Component.literal("Pickup did not discard physical entity"));
            context.assertValueEqual(
                    PlacementState.HELD,
                    successAuthority.current.placementState(),
                    Component.literal("Pickup did not commit held state"));
            context.assertValueEqual(
                    2L,
                    successAuthority.current.recordVersion(),
                    Component.literal("Place plus pickup should advance two revisions"));

            PetPlacementOutcome repeatedPlacement = successCoordinator.place(owner).getNow(null);
            context.assertValueEqual(
                    PetPlacementStatus.PLACED,
                    repeatedPlacement.status(),
                    Component.literal("Repeated placement did not succeed"));
            TamableAnimal repeatedEntity =
                    (TamableAnimal) world.getEntityInAnyDimension(repeatedEntityId);
            context.assertTrue(repeatedEntity != null, Component.literal("Repeated entity is missing"));
            assertPhysicalPet(context, repeatedEntity, successAuthority.current, "minecraft:tabby");
            context.assertTrue(
                    ((PetEntityData) repeatedEntity).aipets$isSleeping(),
                    Component.literal("Pickup/place reset the authoritative sleeping state"));

            ServerPlayer otherPlayer = context.makeMockServerPlayerInLevel();
            try {
                otherPlayer.snapTo(ownerPosition, 0.0F, 0.0F);
                world.getChunkSource().move(otherPlayer);
                int mutationsBeforeUnauthorizedPickup = successAuthority.mutationCount;
                PetPickupOutcome unauthorizedPickup = new PetPickupCoordinator(
                        new BackendId("gametest"),
                        successAuthority,
                        4.0,
                        Clock.fixed(ADOPTED_AT, ZoneOffset.UTC),
                        () -> UUID.fromString("40000000-0000-0000-0000-000000000008"))
                        .pickup(otherPlayer).getNow(null);
                context.assertValueEqual(
                        PetPickupStatus.NO_PET,
                        unauthorizedPickup.status(),
                        Component.literal("Another player could resolve the owner's pet for pickup"));
                context.assertValueEqual(
                        mutationsBeforeUnauthorizedPickup,
                        successAuthority.mutationCount,
                        Component.literal("Unauthorized pickup reached the mutation boundary"));
                context.assertFalse(
                        repeatedEntity.isRemoved(),
                        Component.literal("Unauthorized pickup removed the owner's entity"));
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
            context.assertValueEqual(
                    PetPickupStatus.PICKED_UP,
                    repeatedPickup.status(),
                    Component.literal("Owner could not pick up the repeated placement"));
            context.assertTrue(repeatedEntity.isRemoved(), Component.literal("Repeated pickup left the entity"));
            context.assertValueEqual(
                    4L,
                    successAuthority.current.recordVersion(),
                    Component.literal("Two place/pickup cycles did not advance exactly four revisions"));

            UUID failedEntityId = UUID.fromString("30000000-0000-0000-0000-000000000011");
            InMemoryAuthorityGateway failureAuthority = new InMemoryAuthorityGateway(
                    world,
                    pet(
                            UUID.fromString("10000000-0000-0000-0000-000000000010"),
                            owner.getUUID(),
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
            context.assertTrue(failureFuture.isDone(), Component.literal("Spawn-failure path did not complete"));
            PetPlacementOutcome failure = failureFuture.getNow(null);
            context.assertValueEqual(
                    PetPlacementStatus.SPAWN_FAILED_COMPENSATED,
                    failure.status(),
                    Component.literal("Spawn failure was not compensated"));
            context.assertTrue(
                    failureAuthority.entityWasAbsentAtCommit,
                    Component.literal("Failed entity existed before authoritative commit"));
            context.assertValueEqual(
                    1,
                    failureAuthority.compensationCount,
                    Component.literal("Expected one exact compensation"));
            context.assertValueEqual(
                    PlacementState.HELD,
                    failureAuthority.current.placementState(),
                    Component.literal("Failed spawn did not return authority to held"));
            context.assertValueEqual(
                    2L,
                    failureAuthority.current.recordVersion(),
                    Component.literal("Place plus compensation should advance two revisions"));
            context.assertTrue(
                    world.getEntityInAnyDimension(failedEntityId) == null,
                    Component.literal("Failed placement left a physical entity"));

            UUID farEntityId = UUID.fromString("30000000-0000-0000-0000-000000000012");
            InMemoryAuthorityGateway farAuthority = new InMemoryAuthorityGateway(
                    world,
                    pet(
                            UUID.fromString("10000000-0000-0000-0000-000000000011"),
                            owner.getUUID(),
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
            context.assertValueEqual(
                    PetPlacementStatus.PLACED,
                    farPlacement.status(),
                    Component.literal("Far-pickup fixture placement failed"));
            TamableAnimal farEntity = (TamableAnimal) world.getEntityInAnyDimension(farEntityId);
            context.assertTrue(farEntity != null, Component.literal("Far-pickup fixture entity is missing"));

            Vec3 farOwnerPosition = context.absoluteVec(new Vec3(14.5, 1.0, 3.5));
            owner.snapTo(farOwnerPosition, 0.0F, 0.0F);
            world.getChunkSource().move(owner);
            PetPickupCoordinator farPickupCoordinator = new PetPickupCoordinator(
                    new BackendId("gametest"),
                    farAuthority,
                    4.0,
                    Clock.fixed(ADOPTED_AT, ZoneOffset.UTC),
                    () -> UUID.fromString("40000000-0000-0000-0000-000000000006"));
            PetPickupOutcome farPickup = farPickupCoordinator.pickup(owner).getNow(null);
            context.assertValueEqual(
                    PetPickupStatus.OUT_OF_RANGE,
                    farPickup.status(),
                    Component.literal("Pickup beyond four blocks was accepted"));
            context.assertValueEqual(
                    PlacementState.PLACED,
                    farAuthority.current.placementState(),
                    Component.literal("Rejected far pickup changed authority"));
            context.assertFalse(farEntity.isRemoved(), Component.literal("Rejected far pickup removed entity"));
            farEntity.discard();
        } finally {
            removeMockPlayer(world, owner);
        }
        context.succeed();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void concurrentPlacementRequestsCreateOneRepresentation(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        TamableAnimal spawned = null;
        try {
            for (int x = 1; x <= 6; x++) {
                for (int z = 1; z <= 6; z++) {
                    context.setBlock(x, 0, z, Blocks.STONE);
                    context.setBlock(x, 1, z, Blocks.AIR);
                    context.setBlock(x, 2, z, Blocks.AIR);
                }
            }
            Vec3 ownerPosition = context.absoluteVec(new Vec3(3.5, 1.0, 3.5));
            owner.snapTo(ownerPosition, 0.0F, 0.0F);
            world.getChunkSource().move(owner);

            UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000095");
            UUID entityId = UUID.fromString("30000000-0000-0000-0000-000000000095");
            InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(
                    world,
                    pet(petId, owner.getUUID(), PetSpecies.CAT,
                            "minecraft:tabby", 0.66, 0L));
            authority.pendingOwnerLookup = new CompletableFuture<>();
            PetPlacementCoordinator coordinator = new PetPlacementCoordinator(
                    new BackendId("gametest"), authority, new SafePlacementFinder(2, 1),
                    new PetEntityFactory(), Clock.fixed(ADOPTED_AT, ZoneOffset.UTC),
                    () -> UUID.fromString("40000000-0000-0000-0000-000000000095"),
                    () -> entityId);

            CompletableFuture<PetPlacementOutcome> first = coordinator.place(owner);
            CompletableFuture<PetPlacementOutcome> simultaneous = coordinator.place(owner);
            context.assertFalse(first.isDone(), Component.literal("First placement was not held in flight"));
            context.assertValueEqual(
                    PetPlacementStatus.ALREADY_IN_PROGRESS,
                    simultaneous.getNow(null).status(),
                    Component.literal("Simultaneous placement was not locally deduplicated"));
            context.assertValueEqual(
                    1,
                    authority.ownerLookupCount,
                    Component.literal("Simultaneous placement made a second authority read"));

            authority.pendingOwnerLookup.complete(Optional.of(
                    new PetAuthoritySnapshot(authority.current, false, true)));
            context.assertValueEqual(
                    PetPlacementStatus.PLACED,
                    first.getNow(null).status(),
                    Component.literal("Winning placement did not complete"));
            context.assertValueEqual(
                    1,
                    authority.mutationCount,
                    Component.literal("Concurrent placement reached authority more than once"));

            int representations = 0;
            for (net.minecraft.world.entity.Entity entity : world.getAllEntities()) {
                if (entity instanceof PetEntityData data
                        && data.aipets$isPet()
                        && petId.equals(data.aipets$getPetId())
                        && !entity.isRemoved()) {
                    representations++;
                    spawned = (TamableAnimal) entity;
                }
            }
            context.assertValueEqual(
                    1, representations,
                    Component.literal("Concurrent placement created duplicate representations"));
            context.assertTrue(
                    world.getEntityInAnyDimension(entityId) == spawned,
                    Component.literal("The sole representation has the wrong authoritative UUID"));
        } finally {
            if (spawned != null) spawned.discard();
            removeMockPlayer(world, owner);
        }
        context.succeed();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void supportedPortalTransferCarriesNearPetOnlyToReservedFinalBackend(
            GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        try {
            for (int x = 1; x <= 6; x++) {
                for (int z = 1; z <= 6; z++) {
                    context.setBlock(x, 0, z, Blocks.STONE);
                    context.setBlock(x, 1, z, Blocks.AIR);
                    context.setBlock(x, 2, z, Blocks.AIR);
                }
            }
            Vec3 ownerPosition = context.absoluteVec(new Vec3(3.5, 1.0, 3.5));
            owner.snapTo(ownerPosition, 0.0F, 0.0F);
            world.getChunkSource().move(owner);
            BackendId source = new BackendId("gametest");
            BackendId destination = new BackendId("destination");
            UUID sourceEntityId = UUID.fromString("30000000-0000-0000-0000-000000000081");
            Pet held = pet(
                    UUID.fromString("10000000-0000-0000-0000-000000000081"),
                    owner.getUUID(), PetSpecies.CAT, "minecraft:tabby", 0.66, 0L);
            Pet placed = PetTransitions.place(held, new PetTransitions.Place(
                    owner.getUUID(), 0, source,
                    DimensionId.parse(world.dimension().identifier().toString()),
                    position(ownerPosition), sourceEntityId, ADOPTED_AT.plusSeconds(1))).pet();
            TamableAnimal sourceEntity = new PetEntityFactory().prepare(
                    world, placed, sourceEntityId, position(ownerPosition), false).entity();
            context.assertTrue(world.addFreshEntity(sourceEntity), Component.literal("Source pet spawn failed"));

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
            context.assertValueEqual(PetTransferStatus.SOURCE_PREPARED, prepared.status(),
                    Component.literal("Near pet was not reserved for transfer"));
            context.assertTrue(authority.entityWasPresentAtTransferCommit,
                    Component.literal("Source entity disappeared before TRANSFERRING commit"));
            context.assertValueEqual(PlacementState.TRANSFERRING,
                    authority.current.placementState(), Component.literal("Authority is not transferring"));
            context.assertTrue(sourceEntity.isRemoved(),
                    Component.literal("Source entity survived successful reservation"));

            int mutationsBeforeLobby = authority.mutationCount;
            PetTransferCoordinator waitingLobby = new PetTransferCoordinator(
                    new BackendId("waiting-lobby"), authority,
                    new SafePlacementFinder(2, 1), new PetEntityFactory(), transferConfig,
                    Clock.fixed(ADOPTED_AT.plusSeconds(3), ZoneOffset.UTC),
                    UUID::randomUUID, UUID::randomUUID);
            var lobby = waitingLobby.claimDestination(owner).getNow(null);
            context.assertValueEqual(PetTransferStatus.DESTINATION_NOT_RESERVED, lobby.status(),
                    Component.literal("Waiting lobby attempted to materialize the pet"));
            context.assertValueEqual(mutationsBeforeLobby, authority.mutationCount,
                    Component.literal("Waiting lobby reached transfer mutation authority"));

            PetTransferCoordinator destinationCoordinator = new PetTransferCoordinator(
                    destination, authority,
                    new SafePlacementFinder(2, 1), new PetEntityFactory(), transferConfig,
                    Clock.fixed(ADOPTED_AT.plusSeconds(3), ZoneOffset.UTC),
                    UUID::randomUUID, operationIds::remove);
            var claimed = destinationCoordinator.claimDestination(owner).getNow(null);
            context.assertValueEqual(PetTransferStatus.DESTINATION_PLACED, claimed.status(),
                    Component.literal("Final backend did not claim reserved transfer"));
            context.assertValueEqual(PlacementState.PLACED, authority.current.placementState(),
                    Component.literal("Destination did not become authoritative"));
            PlacedPlacement destinationPlacement = (PlacedPlacement) authority.current.placement();
            context.assertValueEqual(destination, destinationPlacement.backendId(),
                    Component.literal("Pet was placed on the wrong backend"));
            TamableAnimal destinationEntity = (TamableAnimal) world.getEntityInAnyDimension(
                    destinationPlacement.entityUuid().orElseThrow());
            context.assertTrue(destinationEntity != null,
                    Component.literal("Destination physical pet was not spawned"));
            assertPhysicalPet(context, destinationEntity, authority.current, "minecraft:tabby");

            int mutationsAfterClaim = authority.mutationCount;
            var duplicate = destinationCoordinator.claimDestination(owner).getNow(null);
            context.assertValueEqual(PetTransferStatus.DESTINATION_NOT_RESERVED, duplicate.status(),
                    Component.literal("Completed transfer was claimable twice"));
            context.assertValueEqual(mutationsAfterClaim, authority.mutationCount,
                    Component.literal("Duplicate destination join issued another mutation"));
            destinationEntity.discard();

            UUID farEntityId = UUID.fromString("30000000-0000-0000-0000-000000000083");
            Vec3 farPosition = ownerPosition.add(20.0, 0.0, 0.0);
            Pet farHeld = pet(
                    UUID.fromString("10000000-0000-0000-0000-000000000083"),
                    owner.getUUID(), PetSpecies.DOG, "minecraft:pale", 0.62, 0L);
            Pet farPlaced = PetTransitions.place(farHeld, new PetTransitions.Place(
                    owner.getUUID(), 0, source,
                    DimensionId.parse(world.dimension().identifier().toString()),
                    position(farPosition), farEntityId, ADOPTED_AT.plusSeconds(1))).pet();
            TamableAnimal farEntity = new PetEntityFactory().prepare(
                    world, farPlaced, farEntityId, position(farPosition), false).entity();
            context.assertTrue(world.addFreshEntity(farEntity), Component.literal("Far pet spawn failed"));
            InMemoryAuthorityGateway farAuthority = new InMemoryAuthorityGateway(world, farPlaced);
            var far = new PetTransferCoordinator(
                    source, farAuthority, new SafePlacementFinder(2, 1), new PetEntityFactory(),
                    transferConfig, Clock.fixed(ADOPTED_AT.plusSeconds(2), ZoneOffset.UTC),
                    UUID::randomUUID, UUID::randomUUID)
                    .prepareSource(owner, destination.value()).getNow(null);
            context.assertValueEqual(PetTransferStatus.LEFT_BEHIND, far.status(),
                    Component.literal("Far pet was automatically carried"));
            context.assertValueEqual(PlacementState.PLACED, farAuthority.current.placementState(),
                    Component.literal("Far pet authority changed"));
            context.assertFalse(farEntity.isRemoved(), Component.literal("Far pet entity was removed"));
            farEntity.discard();

            InMemoryAuthorityGateway heldAuthority = new InMemoryAuthorityGateway(world, held);
            var heldResult = new PetTransferCoordinator(
                    source, heldAuthority, new SafePlacementFinder(2, 1), new PetEntityFactory(),
                    transferConfig, Clock.fixed(ADOPTED_AT.plusSeconds(2), ZoneOffset.UTC),
                    UUID::randomUUID, UUID::randomUUID)
                    .prepareSource(owner, destination.value()).getNow(null);
            context.assertValueEqual(PetTransferStatus.HELD_UNCHANGED, heldResult.status(),
                    Component.literal("Manually held pet changed during transfer"));
            context.assertTrue(heldAuthority.current.placement() instanceof HeldPlacement,
                    Component.literal("Held authority was not preserved"));
        } finally {
            removeMockPlayer(world, owner);
        }
        context.succeed();
    }

    @GameTest(maxTicks = 240)
    @SuppressWarnings("removal")
    public void petFollowsRegisteredOwnerWithoutTeleportOrForcedChunks(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        TamableAnimal spawned = null;
        try {
            for (int x = 1; x <= 24; x++) {
                context.setBlock(x, 0, 3, Blocks.STONE);
                context.setBlock(x, 1, 3, Blocks.AIR);
                context.setBlock(x, 2, 3, Blocks.AIR);
            }

            Vec3 ownerPosition = context.absoluteVec(new Vec3(22.75, 1.0, 3.5));
            owner.snapTo(ownerPosition, 0.0F, 0.0F);
            world.getChunkSource().move(owner);
            context.assertTrue(
                    world.getPlayerInAnyDimension(owner.getUUID()) == owner,
                    Component.literal("Mock owner is not registered"));

            Vec3 petPosition = context.absoluteVec(new Vec3(1.25, 1.0, 3.5));
            Pet aggregate = pet(
                    UUID.fromString("10000000-0000-0000-0000-000000000007"),
                    owner.getUUID(),
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
            context.assertTrue(world.addFreshEntity(spawned), Component.literal("Following pet spawn failed"));

            TamableAnimal pet = spawned;
            double initialSquaredDistance = pet.distanceToSqr(owner);
            Vec3[] previousPosition = {pet.position()};
            double[] maximumStepSquared = {0.0};
            double[] farMaximumStepSquared = {0.0};
            double[] nearMaximumStepSquared = {0.0};
            boolean[] moved = {false};
            boolean[] visitedNear = {false};
            boolean[] visitedMedium = {false};
            boolean[] visitedFar = {false};
            Vec3[] cagedPosition = {null};
            Vec3[] releasedPosition = {null};
            java.util.List<BlockPos> cage = new java.util.ArrayList<>();
            PetPhysicalConfig movementConfig = PetPhysicalConfig.defaults();
            Set<Long> forcedChunksBefore = Set.copyOf(world.getForceLoadedChunks());
            ChunkPos testChunk = ChunkPos.containing(context.absolutePos(BlockPos.ZERO));
            ChunkPos remoteSentinel = new ChunkPos(testChunk.x() + 128, testChunk.z() + 128);
            context.assertFalse(
                    world.getChunkSource().hasChunk(remoteSentinel.x(), remoteSentinel.z()),
                    Component.literal("Remote sentinel chunk was already loaded"));

            context.failIfEver(() -> {
                if (!pet.isRemoved()) {
                    double stepSquared = pet.position().distanceToSqr(previousPosition[0]);
                    maximumStepSquared[0] = Math.max(maximumStepSquared[0], stepSquared);
                    moved[0] |= stepSquared > 0.0025;
                    double distanceSquared = pet.distanceToSqr(owner);
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
                    previousPosition[0] = pet.position();
                }
            });
            context.runAtTickTime(75, () -> {
                context.assertTrue(
                        pet.distanceToSqr(owner) < initialSquaredDistance - 25.0,
                        Component.literal("Far-speed following did not prevent routine separation"));
                Vec3 nearOwnerPosition = pet.position().add(6.0, 0.0, 0.0);
                owner.snapTo(nearOwnerPosition, 0.0F, 0.0F);
                world.getChunkSource().move(owner);
            });
            context.runAtTickTime(110, () -> {
                BlockPos center = pet.blockPosition();
                cagedPosition[0] = pet.position();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        for (int dy = 0; dy <= 2; dy++) {
                            BlockPos wall = center.offset(dx, dy, dz);
                            world.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                            cage.add(wall);
                        }
                    }
                }
                Vec3 blockedOwnerPosition = pet.position().add(6.0, 0.0, 0.0);
                owner.snapTo(blockedOwnerPosition, 0.0F, 0.0F);
                world.getChunkSource().move(owner);
            });
            context.runAtTickTime(180, () -> {
                context.assertTrue(
                        pet.position().distanceToSqr(cagedPosition[0]) < 2.25,
                        Component.literal("Ordinary blockage did not actually hold the pet"));
                cage.forEach(position -> world.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState()));
                releasedPosition[0] = pet.position();
            });
            context.runAtTickTime(225, () -> {
                try {
                    context.assertFalse(pet.isRemoved(), Component.literal("Following pet disappeared"));
                    context.assertTrue(moved[0], Component.literal("Following pet never moved"));
                    context.assertTrue(
                            visitedFar[0] && visitedMedium[0] && visitedNear[0],
                            Component.literal("Follow path did not exercise all distance bands"));
                    context.assertTrue(
                            farMaximumStepSquared[0] > 0.0025,
                            Component.literal("Far-distance acceleration produced no meaningful movement"));
                    context.assertTrue(
                            nearMaximumStepSquared[0] > 0.0,
                            Component.literal("Near-distance following produced no movement"));
                    context.assertTrue(
                            pet.position().distanceToSqr(releasedPosition[0]) > 0.25,
                            Component.literal("Pet did not recalculate and move after blockage removal"));
                    context.assertTrue(
                            maximumStepSquared[0] < 2.25,
                            Component.literal("Following pet made a teleport-like jump"));
                    context.assertValueEqual(
                            forcedChunksBefore,
                            Set.copyOf(world.getForceLoadedChunks()),
                            Component.literal("Forced-chunk set changed while following"));
                    context.assertFalse(
                            world.getChunkSource().hasChunk(remoteSentinel.x(), remoteSentinel.z()),
                            Component.literal("Following loaded a remote sentinel chunk"));
                } finally {
                    cage.forEach(position -> world.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState()));
                    pet.discard();
                    removeMockPlayer(world, owner);
                }
                context.succeed();
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
    public void sleepingPetStopsActiveFollowUntilAwake(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        TamableAnimal pet = null;
        try {
            for (int x = 1; x <= 24; x++) {
                context.setBlock(x, 0, 3, Blocks.STONE);
                context.setBlock(x, 1, 3, Blocks.AIR);
                context.setBlock(x, 2, 3, Blocks.AIR);
            }
            Vec3 ownerPosition = context.absoluteVec(new Vec3(22.75, 1.0, 3.5));
            owner.snapTo(ownerPosition, 0.0F, 0.0F);
            world.getChunkSource().move(owner);
            Vec3 initialPosition = context.absoluteVec(new Vec3(1.25, 1.0, 3.5));
            Pet aggregate = pet(
                    UUID.fromString("10000000-0000-0000-0000-000000000087"),
                    owner.getUUID(), PetSpecies.CAT, "minecraft:black", 0.67, 0L);
            pet = new PetEntityFactory().prepare(
                    world, aggregate,
                    UUID.fromString("30000000-0000-0000-0000-000000000087"),
                    position(initialPosition), false).entity();
            context.assertTrue(world.addFreshEntity(pet), Component.literal("Sleeping test pet did not spawn"));

            TamableAnimal testedPet = pet;
            Vec3[] sleepingPosition = {null};
            // The complete GameTest batch runs many pathfinders concurrently; allow
            // the same bounded follow behavior a little more wall-clock tick time
            // before asserting it, without changing the movement contract.
            context.runAtTickTime(40, () -> {
                context.assertTrue(
                        testedPet.position().distanceToSqr(initialPosition) > 0.25,
                        Component.literal("Pet was not actively following before sleep"));
                ((PetEntityData) testedPet).aipets$setSleeping(true);
            });
            context.runAtTickTime(45, () -> sleepingPosition[0] = testedPet.position());
            context.runAtTickTime(80, () -> {
                context.assertTrue(
                        testedPet.getNavigation().isDone(),
                        Component.literal("Sleeping pet retained an active navigation path"));
                context.assertTrue(
                        testedPet.position().distanceToSqr(sleepingPosition[0]) < 0.04,
                        Component.literal("Sleeping pet moved while its owner remained distant"));
                ((PetEntityData) testedPet).aipets$setSleeping(false);
            });
            context.runAtTickTime(125, () -> {
                try {
                    context.assertTrue(
                            testedPet.position().distanceToSqr(sleepingPosition[0]) > 0.25,
                            Component.literal("Awakened pet did not resume owner following"));
                } finally {
                    testedPet.discard();
                    removeMockPlayer(world, owner);
                }
                context.succeed();
            });
        } catch (RuntimeException | Error failure) {
            if (pet != null) pet.discard();
            removeMockPlayer(world, owner);
            throw failure;
        }
    }

    @GameTest
    public void entityLoadReconciliationReusesExactAndDiscardsStale(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BackendId backend = new BackendId("gametest");
        DimensionId dimension = DimensionId.parse(world.dimension().identifier().toString());
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000013");
        UUID exactEntityId = UUID.fromString("30000000-0000-0000-0000-000000000013");
        UUID staleEntityId = UUID.fromString("30000000-0000-0000-0000-000000000014");
        Pet held = pet(petId, PetSpecies.CAT, "minecraft:tabby", 0.67, 0L);
        Vec3 exactPosition = context.absoluteVec(new Vec3(1.5, 1.0, 1.5));
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
        context.assertTrue(placedTransition.applied(), Component.literal("Reconciliation fixture did not place"));
        Pet placed = placedTransition.pet();
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, placed);
        authority.sleeping = true;
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(backend, authority);
        Set<Long> forcedChunksBefore = Set.copyOf(world.getForceLoadedChunks());

        TamableAnimal exact = null;
        try {
            exact = new PetEntityFactory().prepare(
                    world,
                    held,
                    exactEntityId,
                    position(exactPosition),
                    false).entity();
            exact.snapTo(exactPosition, 0.0F, 0.0F);
            context.assertTrue(world.addFreshEntity(exact), Component.literal("Exact reconciliation entity did not spawn"));
            context.assertFalse(exact.isRemoved(), Component.literal("Exact authority match was discarded"));
            PetEntityData exactData = (PetEntityData) exact;
            context.assertValueEqual(
                    placed.recordVersion(),
                    exactData.aipets$getRecordVersion(),
                    Component.literal("Entity-load event did not refresh the older revision"));
            context.assertTrue(
                    exactData.aipets$isSleeping(),
                    Component.literal("Entity-load event did not refresh sleep state"));

            Vec3 stalePosition = context.absoluteVec(new Vec3(3.5, 1.0, 1.5));
            TamableAnimal stale = new PetEntityFactory().prepare(
                    world,
                    placed,
                    staleEntityId,
                    position(stalePosition),
                    false).entity();
            stale.snapTo(stalePosition, 0.0F, 0.0F);
            context.assertTrue(world.addFreshEntity(stale), Component.literal("Stale reconciliation entity did not spawn"));
            context.assertTrue(
                    stale.isRemoved(),
                    Component.literal("Entity-load event left a wrong-UUID physical entity loaded"));
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
                    Component.literal("Periodic reconciliation did not queue the loaded pet"));
            periodic.onEndWorldTick(world);
            context.assertValueEqual(
                    lookupsBeforePeriodicScan + 1,
                    authority.petIdLookupCount,
                    Component.literal("Periodic reconciliation did not make one bounded lookup"));
            context.assertTrue(
                    periodic.inFlightEntityIds().isEmpty(),
                    Component.literal("Completed reconciliation remained in-flight"));
            context.assertValueEqual(
                    forcedChunksBefore,
                    Set.copyOf(world.getForceLoadedChunks()),
                    Component.literal("Reconciliation changed the forced-chunk set"));
        } finally {
            uninstallGateway.run();
            if (exact != null) {
                exact.discard();
            }
        }
        context.succeed();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void lazyRestartRecoveryReconstructsOnceAndNeverLoadsRemoteChunk(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        BackendId backend = new BackendId("gametest");
        DimensionId dimension = DimensionId.parse(world.dimension().identifier().toString());
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000093");
        UUID expectedEntityId = UUID.fromString("30000000-0000-0000-0000-000000000093");
        UUID staleEntityId = UUID.fromString("30000000-0000-0000-0000-000000000094");
        Vec3 location = context.absoluteVec(new Vec3(2.5, 1.0, 2.5));
        Pet adopted = pet(
                petId, owner.getUUID(), PetSpecies.CAT, "minecraft:tabby", 0.66, 0L);
        Pet placed = PetTransitions.place(adopted, new PetTransitions.Place(
                owner.getUUID(), adopted.recordVersion(), backend, dimension,
                position(location), expectedEntityId, ADOPTED_AT.plusSeconds(1))).pet();
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, placed);

        PetEntityRecoveryCoordinator firstProcess = new PetEntityRecoveryCoordinator(
                backend, authority, new PetEntityReconciler(backend, authority),
                new PetEntityFactory());
        context.assertValueEqual(
                PetRecoveryStatus.RECONSTRUCTED,
                firstProcess.recoverOwner(world.getServer(), owner.getUUID())
                        .toCompletableFuture().join(),
                Component.literal("Restart recovery did not reconstruct the missing entity"));
        TamableAnimal exact = (TamableAnimal) world.getEntityInAnyDimension(expectedEntityId);
        context.assertTrue(exact != null, Component.literal("Expected entity UUID was not reconstructed"));
        assertPhysicalPet(context, exact, placed, "minecraft:tabby");

        TamableAnimal stale = new PetEntityFactory().prepare(
                world, placed, staleEntityId, position(location.add(1, 0, 0)), false).entity();
        context.assertTrue(world.addFreshEntity(stale), Component.literal("Old saved entity did not load"));
        PetEntityRecoveryCoordinator secondProcess = new PetEntityRecoveryCoordinator(
                backend, authority, new PetEntityReconciler(backend, authority),
                new PetEntityFactory());
        context.assertValueEqual(
                PetRecoveryStatus.AUTHORITATIVE_ENTITY_PRESENT,
                secondProcess.recoverOwner(world.getServer(), owner.getUUID())
                        .toCompletableFuture().join(),
                Component.literal("Second process did not reuse the authoritative entity"));
        context.assertTrue(stale.isRemoved(), Component.literal("Old saved entity was not discarded"));
        context.assertTrue(
                world.getEntityInAnyDimension(expectedEntityId) == exact,
                Component.literal("Second process duplicated the authoritative entity"));

        exact.discard();
        PetEntityRecoveryCoordinator thirdProcess = new PetEntityRecoveryCoordinator(
                backend, authority, new PetEntityReconciler(backend, authority),
                new PetEntityFactory());
        context.assertValueEqual(
                PetRecoveryStatus.RECONSTRUCTED,
                thirdProcess.recoverOwner(world.getServer(), owner.getUUID())
                        .toCompletableFuture().join(),
                Component.literal("Deleted entity was not reconstructed from persisted authority"));
        TamableAnimal reconstructed = (TamableAnimal) world.getEntityInAnyDimension(expectedEntityId);
        assertPhysicalPet(context, reconstructed, placed, "minecraft:tabby");

        ChunkPos testChunk = ChunkPos.containing(context.absolutePos(BlockPos.ZERO));
        ChunkPos remoteChunk = new ChunkPos(testChunk.x() + 128, testChunk.z() + 128);
        context.assertFalse(
                world.getChunkSource().hasChunk(remoteChunk.x(), remoteChunk.z()),
                Component.literal("Remote recovery sentinel started loaded"));
        reconstructed.discard();
        authority.current = withPlacement(placed, PlacedPlacement.materialized(
                backend, dimension,
                new WorldPosition(remoteChunk.getMinBlockX() + 0.5, 70, remoteChunk.getMinBlockZ() + 0.5),
                expectedEntityId));
        context.assertValueEqual(
                PetRecoveryStatus.DEFERRED_UNLOADED_CHUNK,
                new PetEntityRecoveryCoordinator(
                        backend, authority, new PetEntityReconciler(backend, authority),
                        new PetEntityFactory())
                        .recoverOwner(world.getServer(), owner.getUUID()).toCompletableFuture().join(),
                Component.literal("Unloaded recovery was not deferred"));
        context.assertFalse(
                world.getChunkSource().hasChunk(remoteChunk.x(), remoteChunk.z()),
                Component.literal("Recovery force-loaded the remote chunk"));

        removeMockPlayer(world, owner);
        context.succeed();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void markedPetInteractionIsOwnerOnlyAndOrdinaryMobsPassThrough(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        ServerPlayer intruder = context.makeMockServerPlayerInLevel();
        TamableAnimal pet = null;
        Cat ordinary = null;
        Runnable uninstallHandler = () -> { };
        try {
            context.assertFalse(
                    owner.getUUID().equals(intruder.getUUID()),
                    Component.literal("Interaction test players share an identity"));
            Vec3 petPosition = context.absoluteVec(new Vec3(1.5, 1.0, 1.5));
            Pet aggregate = pet(
                    UUID.fromString("10000000-0000-0000-0000-000000000015"),
                    owner.getUUID(),
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
            pet.snapTo(petPosition, 0.0F, 0.0F);
            context.assertTrue(world.addFreshEntity(pet), Component.literal("Interaction pet did not spawn"));

            AtomicInteger opens = new AtomicInteger();
            TamableAnimal expectedPet = pet;
            uninstallHandler = PetInteractionRouter.installHandler((actualOwner, actualPet) -> {
                context.assertTrue(actualOwner == owner, Component.literal("Wrong owner reached handler"));
                context.assertTrue(actualPet == expectedPet, Component.literal("Wrong pet reached handler"));
                opens.incrementAndGet();
            });
            InteractionResult ownerResult = UseEntityCallback.EVENT.invoker().interact(
                    owner,
                    world,
                    InteractionHand.MAIN_HAND,
                    pet,
                    new EntityHitResult(pet));
            context.assertValueEqual(
                    InteractionResult.SUCCESS_SERVER,
                    ownerResult,
                    Component.literal("Owner interaction was not consumed"));
            context.assertValueEqual(1, opens.get(), Component.literal("Owner interaction did not open once"));

            Runnable restoreChatPermission = PetPermissions.install(
                    (ignored, node, defaultLevel) -> !node.equals(PetPermission.CHAT.node()));
            try {
                InteractionResult deniedChatResult = UseEntityCallback.EVENT.invoker().interact(
                        owner,
                        world,
                        InteractionHand.MAIN_HAND,
                        pet,
                        new EntityHitResult(pet));
                context.assertValueEqual(
                        InteractionResult.FAIL,
                        deniedChatResult,
                        Component.literal("Denied aipets.chat interaction was not blocked"));
                context.assertValueEqual(
                        1,
                        opens.get(),
                        Component.literal("Denied aipets.chat reached interaction handler"));
            } finally {
                restoreChatPermission.run();
            }

            ((PetEntityData) pet).aipets$setSleeping(true);
            InteractionResult sleepingResult = UseEntityCallback.EVENT.invoker().interact(
                    owner,
                    world,
                    InteractionHand.MAIN_HAND,
                    pet,
                    new EntityHitResult(pet));
            context.assertValueEqual(
                    InteractionResult.SUCCESS_SERVER,
                    sleepingResult,
                    Component.literal("Sleeping interaction was not consumed with feedback"));
            context.assertValueEqual(
                    1,
                    opens.get(),
                    Component.literal("Sleeping pet reached the interaction handler"));
            InteractionResult duplicateSleepingResult = UseEntityCallback.EVENT.invoker().interact(
                    owner,
                    world,
                    InteractionHand.MAIN_HAND,
                    pet,
                    new EntityHitResult(pet));
            context.assertValueEqual(
                    InteractionResult.SUCCESS_SERVER,
                    duplicateSleepingResult,
                    Component.literal("Duplicate sleeping interaction was not consumed"));
            context.assertValueEqual(
                    1,
                    opens.get(),
                    Component.literal("Duplicate sleeping interaction reached the handler"));
            ((PetEntityData) pet).aipets$setSleeping(false);

            InteractionResult intruderResult = UseEntityCallback.EVENT.invoker().interact(
                    intruder,
                    world,
                    InteractionHand.MAIN_HAND,
                    pet,
                    new EntityHitResult(pet));
            context.assertValueEqual(
                    InteractionResult.FAIL,
                    intruderResult,
                    Component.literal("Non-owner interaction was not blocked"));
            context.assertValueEqual(1, opens.get(), Component.literal("Non-owner reached interaction handler"));

            ordinary = context.spawn(EntityTypes.CAT, new BlockPos(3, 1, 1));
            InteractionResult ordinaryResult = UseEntityCallback.EVENT.invoker().interact(
                    owner,
                    world,
                    InteractionHand.MAIN_HAND,
                    ordinary,
                    new EntityHitResult(ordinary));
            context.assertValueEqual(
                    InteractionResult.PASS,
                    ordinaryResult,
                    Component.literal("Ordinary cat interaction was intercepted"));
            context.assertValueEqual(1, opens.get(), Component.literal("Ordinary cat reached pet handler"));
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
        context.succeed();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void petRootHelpAndStatusUseAsyncAuthority(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        Pet aggregate = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000022"),
                owner.getUUID(),
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
        CommandSourceStack source = owner.createCommandSourceStack().withSource(output);
        try {
            int helpResult = world.getServer().getCommands().getDispatcher()
                    .execute("pet", source);
            int statusResult = world.getServer().getCommands().getDispatcher()
                    .execute("pet status", source);
            context.assertValueEqual(1, helpResult, Component.literal("/pet help failed"));
            context.assertValueEqual(1, statusResult, Component.literal("/pet status failed"));
            context.assertValueEqual(
                    1,
                    authority.ownerLookupCount,
                    Component.literal("/pet status did not make exactly one owner lookup"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("/pet status")),
                    Component.literal("Root help omitted /pet status"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message ->
                            message.contains("Pepper")
                                    && message.contains("DOG")
                                    && message.contains("HELD")
                                    && message.contains("sleeping")
                                    && (message.contains("hibernating") || message.contains("quiet"))),
                    Component.literal("Status omitted authoritative held/sleep state"));
        } catch (CommandSyntaxException failure) {
            throw context.assertionException("Pet command execution failed: %s", failure.getMessage());
        } finally {
            uninstallGateway.run();
            removeMockPlayer(world, owner);
        }
        context.succeed();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void externalServiceFailuresStayInsidePetCommands(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        Pet aggregate = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000044"),
                owner.getUUID(), PetSpecies.CAT, "minecraft:tabby", 0.66, 0L);
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, aggregate);
        authority.failAllRequests = true;
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(
                new BackendId("gametest"), authority);
        CapturingCommandOutput output = new CapturingCommandOutput();
        CommandSourceStack source = owner.createCommandSourceStack().withSource(output);
        try {
            List<String> commands = List.of(
                    "pet link", "pet portal", "pet status", "pet adopt cat Safe",
                    "pet place", "pet pickup", "pet recall", "pet compass");
            for (String command : commands) {
                context.assertValueEqual(
                        1,
                        world.getServer().getCommands().getDispatcher().execute(command, source),
                        Component.literal(command + " did not contain its service failure"));
            }
            context.runAfterDelay(2, () -> {
                try {
                    try {
                        context.assertValueEqual(
                                1,
                                world.getServer().getCommands().getDispatcher().execute("pet", source),
                                Component.literal("Command dispatcher did not remain usable after failures"));
                    } catch (CommandSyntaxException failure) {
                        throw context.assertionException(
                                "Root command failed after contained outages: %s", failure.getMessage());
                    }
                    context.assertValueEqual(0, authority.mutationCount,
                            Component.literal("Failed service calls reached a physical mutation"));
                    context.assertValueEqual(aggregate, authority.current,
                            Component.literal("Failed service calls changed authoritative state"));
                    context.assertTrue(
                            output.messages.stream().anyMatch(message -> message.startsWith("Pet Companion:")),
                            Component.literal("Root help feedback was unavailable after contained failures"));
                } finally {
                    uninstallGateway.run();
                    removeMockPlayer(world, owner);
                }
                context.succeed();
            });
        } catch (CommandSyntaxException failure) {
            uninstallGateway.run();
            removeMockPlayer(world, owner);
            throw context.assertionException("Failure-isolation command execution failed: %s", failure.getMessage());
        } catch (RuntimeException | Error failure) {
            uninstallGateway.run();
            removeMockPlayer(world, owner);
            throw failure;
        }
    }

    @GameTest
    @SuppressWarnings("removal")
    public void petPlaceAndPickupCommandsUseCommitSafeCoordinators(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        for (int x = 1; x <= 6; x++) {
            for (int z = 1; z <= 6; z++) {
                context.setBlock(x, 0, z, Blocks.STONE);
                context.setBlock(x, 1, z, Blocks.AIR);
                context.setBlock(x, 2, z, Blocks.AIR);
            }
        }
        Vec3 ownerPosition = context.absoluteVec(new Vec3(3.5, 1.0, 3.5));
        owner.snapTo(ownerPosition, 0.0F, 0.0F);
        world.getChunkSource().move(owner);
        Pet aggregate = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000023"),
                owner.getUUID(),
                PetSpecies.CAT,
                "minecraft:tabby",
                0.66,
                0L);
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, aggregate);
        authority.aiAccessEnabled = false;
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(
                new BackendId("gametest"), authority);
        CapturingCommandOutput output = new CapturingCommandOutput();
        CommandSourceStack source = owner.createCommandSourceStack().withSource(output);
        try {
            int placeResult = world.getServer().getCommands().getDispatcher()
                    .execute("pet place", source);
            context.assertValueEqual(1, placeResult, Component.literal("/pet place did not schedule"));
            context.assertValueEqual(
                    PlacementState.PLACED,
                    authority.current.placementState(),
                    Component.literal("/pet place did not commit placement"));
            PlacedPlacement placed = (PlacedPlacement) authority.current.placement();
            context.assertTrue(
                    placed.entityUuid().isPresent()
                            && world.getEntityInAnyDimension(placed.entityUuid().orElseThrow()) != null,
                    Component.literal("/pet place did not spawn its committed entity"));

            UUID firstPlacedEntity = placed.entityUuid().orElseThrow();
            int duplicatePlaceResult = world.getServer().getCommands().getDispatcher()
                    .execute("pet place", source);
            context.assertValueEqual(1, duplicatePlaceResult, Component.literal("Second /pet place did not schedule"));
            context.assertValueEqual(
                    1L,
                    authority.current.recordVersion(),
                    Component.literal("Second /pet place mutated authoritative state"));
            context.assertValueEqual(
                    firstPlacedEntity,
                    ((PlacedPlacement) authority.current.placement()).entityUuid().orElseThrow(),
                    Component.literal("Second /pet place replaced the authoritative entity"));
            context.assertValueEqual(
                    1,
                    authority.mutationCount,
                    Component.literal("Second /pet place reached the mutation endpoint"));

            int pickupResult = world.getServer().getCommands().getDispatcher()
                    .execute("pet pickup", source);
            context.assertValueEqual(1, pickupResult, Component.literal("/pet pickup did not schedule"));
            context.assertValueEqual(
                    PlacementState.HELD,
                    authority.current.placementState(),
                    Component.literal("/pet pickup did not commit held state"));
            context.assertValueEqual(
                    2L,
                    authority.current.recordVersion(),
                    Component.literal("Command place/pickup did not advance exactly two revisions"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("placed safely")),
                    Component.literal("/pet place omitted success feedback"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("now held")),
                    Component.literal("/pet pickup omitted success feedback"));
        } catch (CommandSyntaxException failure) {
            throw context.assertionException("Pet mutation command execution failed: %s", failure.getMessage());
        } finally {
            uninstallGateway.run();
            removeMockPlayer(world, owner);
        }
        context.succeed();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void petRecallCommandInvalidatesOldEntityAndReportsMonthlyAvailability(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        for (int x = 1; x <= 6; x++) {
            for (int z = 1; z <= 6; z++) {
                context.setBlock(x, 0, z, Blocks.STONE);
                context.setBlock(x, 1, z, Blocks.AIR);
                context.setBlock(x, 2, z, Blocks.AIR);
            }
        }
        Vec3 ownerPosition = context.absoluteVec(new Vec3(3.5, 1.0, 3.5));
        owner.snapTo(ownerPosition, 0.0F, 0.0F);
        world.getChunkSource().move(owner);
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000026");
        UUID oldEntityId = UUID.fromString("30000000-0000-0000-0000-000000000026");
        DimensionId dimension = DimensionId.parse(world.dimension().identifier().toString());
        BackendId backend = new BackendId("gametest");
        Pet base = pet(petId, owner.getUUID(), PetSpecies.CAT, "minecraft:tabby", 0.66, 1L);
        Pet placed = withPlacement(base, PlacedPlacement.materialized(
                backend,
                dimension,
                position(context.absoluteVec(new Vec3(1.5, 1.0, 1.5))),
                oldEntityId));
        TamableAnimal oldEntity = new PetEntityFactory().prepare(
                world, placed, oldEntityId,
                position(context.absoluteVec(new Vec3(1.5, 1.0, 1.5))), false).entity();
        context.assertTrue(world.addFreshEntity(oldEntity), Component.literal("Old recall entity did not spawn"));
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, placed);
        authority.aiAccessEnabled = false;
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(backend, authority);
        CapturingCommandOutput output = new CapturingCommandOutput();
        CommandSourceStack source = owner.createCommandSourceStack().withSource(output);
        try {
            context.assertValueEqual(
                    1,
                    world.getServer().getCommands().getDispatcher().execute("pet recall", source),
                    Component.literal("/pet recall did not schedule"));
            context.assertTrue(oldEntity.isRemoved(), Component.literal("Recall did not discard old loaded entity"));
            PlacedPlacement recalled = (PlacedPlacement) authority.current.placement();
            context.assertTrue(
                    !oldEntityId.equals(recalled.entityUuid().orElseThrow()),
                    Component.literal("Recall reused the stale entity UUID"));
            context.assertTrue(
                    world.getEntityInAnyDimension(recalled.entityUuid().orElseThrow()) != null,
                    Component.literal("Recall did not spawn the committed destination entity"));
            context.assertValueEqual(
                    1,
                    world.getServer().getCommands().getDispatcher().execute("pet recall", source),
                    Component.literal("Second /pet recall did not schedule"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message ->
                            message.contains("recalled safely") && message.contains("Next recall:")),
                    Component.literal("Recall success omitted next availability"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message ->
                            message.contains("already used") && message.contains("Next recall:")),
                    Component.literal("Second recall omitted monthly availability feedback"));
        } catch (CommandSyntaxException failure) {
            throw context.assertionException("Pet recall command execution failed: %s", failure.getMessage());
        } finally {
            if (authority.current.placement() instanceof PlacedPlacement finalPlacement) {
                finalPlacement.entityUuid().ifPresent(id -> {
                    net.minecraft.world.entity.Entity entity = world.getEntityInAnyDimension(id);
                    if (entity != null) entity.discard();
                });
            }
            uninstallGateway.run();
            removeMockPlayer(world, owner);
        }
        context.succeed();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void offlineRecallSourceDiscardsSavedEntityWhenItReturns(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        BackendId sourceBackend = new BackendId("offline-source");
        BackendId destinationBackend = new BackendId("gametest");
        DimensionId dimension = DimensionId.parse(world.dimension().identifier().toString());
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000096");
        UUID oldEntityId = UUID.fromString("30000000-0000-0000-0000-000000000096");
        UUID recalledEntityId = UUID.fromString("30000000-0000-0000-0000-000000000097");
        Vec3 oldLocation = context.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        Vec3 destination = context.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        TamableAnimal destinationEntity = null;
        try {
            Pet adopted = pet(
                    petId, owner.getUUID(), PetSpecies.CAT, "minecraft:tabby", 0.66, 0L);
            Pet sourcePlaced = PetTransitions.place(adopted, new PetTransitions.Place(
                    owner.getUUID(), adopted.recordVersion(), sourceBackend, dimension,
                    position(oldLocation), oldEntityId, ADOPTED_AT.plusSeconds(1))).pet();
            context.assertTrue(
                    world.getEntityInAnyDimension(oldEntityId) == null,
                    Component.literal("Offline source entity was unexpectedly loaded during recall"));
            Pet recalled = PetTransitions.recall(sourcePlaced, new PetTransitions.Recall(
                    owner.getUUID(), sourcePlaced.recordVersion(), destinationBackend, dimension,
                    position(destination), recalledEntityId, ADOPTED_AT.plusSeconds(2))).pet();
            InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, recalled);

            destinationEntity = new PetEntityFactory().prepare(
                    world, recalled, recalledEntityId, position(destination), false).entity();
            context.assertTrue(
                    world.addFreshEntity(destinationEntity),
                    Component.literal("Recalled destination entity did not spawn"));

            PetEntityReconciler restartedSource = new PetEntityReconciler(sourceBackend, authority);
            TamableAnimal oldSaved = new PetEntityFactory().prepare(
                    world, sourcePlaced, oldEntityId, position(oldLocation), false).entity();
            context.assertTrue(world.addFreshEntity(oldSaved), Component.literal("Saved source entity did not load"));
            context.assertValueEqual(
                    PetEntityReconciliationStatus.STALE_DISCARDED,
                    restartedSource.reconcileLoaded(oldSaved, world).join().status(),
                    Component.literal("Returned offline source entity was not classified stale"));
            context.assertTrue(
                    oldSaved.isRemoved(),
                    Component.literal("Returned offline source entity survived reconciliation"));

            TamableAnimal replayedSave = new PetEntityFactory().prepare(
                    world, sourcePlaced, oldEntityId, position(oldLocation), false).entity();
            context.assertTrue(world.addFreshEntity(replayedSave), Component.literal("Replayed old save did not load"));
            context.assertValueEqual(
                    PetEntityReconciliationStatus.STALE_DISCARDED,
                    new PetEntityReconciler(sourceBackend, authority)
                            .reconcileLoaded(replayedSave, world).join().status(),
                    Component.literal("Fresh source process did not reject the replayed save"));
            context.assertTrue(replayedSave.isRemoved(), Component.literal("Replayed save survived"));

            PetEntityRecoveryCoordinator destinationProcess = new PetEntityRecoveryCoordinator(
                    destinationBackend, authority,
                    new PetEntityReconciler(destinationBackend, authority),
                    new PetEntityFactory());
            context.assertValueEqual(
                    PetRecoveryStatus.AUTHORITATIVE_ENTITY_PRESENT,
                    destinationProcess.recoverOwner(world.getServer(), owner.getUUID())
                            .toCompletableFuture().join(),
                    Component.literal("Destination did not retain its sole authoritative entity"));
            int liveRepresentations = 0;
            for (net.minecraft.world.entity.Entity entity : world.getAllEntities()) {
                if (entity instanceof PetEntityData data
                        && data.aipets$isPet()
                        && petId.equals(data.aipets$getPetId())
                        && !entity.isRemoved()) {
                    liveRepresentations++;
                }
            }
            context.assertValueEqual(
                    1, liveRepresentations,
                    Component.literal("Offline recall produced more than one live representation"));
            context.assertTrue(
                    world.getEntityInAnyDimension(recalledEntityId) == destinationEntity,
                    Component.literal("Recall destination identity changed during reconciliation"));
        } finally {
            if (destinationEntity != null) destinationEntity.discard();
            removeMockPlayer(world, owner);
        }
        context.succeed();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void petCompassLifecycleIsSignedBoundAndLocationAware(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        ServerPlayer intruder = context.makeMockServerPlayerInLevel();
        BackendId localBackend = new BackendId("gametest");
        BackendId remoteBackend = new BackendId("survival");
        DimensionId localDimension = DimensionId.parse(
                world.dimension().identifier().toString());
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000024");
        Pet held = pet(
                petId,
                owner.getUUID(),
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
        CommandSourceStack source = owner.createCommandSourceStack().withSource(output);
        TamableAnimal liveEntity = null;
        ItemEntity dropped = null;
        try {
            int issueResult = world.getServer().getCommands().getDispatcher()
                    .execute("pet compass", source);
            context.assertValueEqual(1, issueResult, Component.literal("/pet compass did not schedule"));
            PetCompassManager manager = PetCompanionMod.petCompassManager().orElseThrow();
            ItemStack compass = findCompass(owner);
            context.assertTrue(
                    manager.isAllowedInPlayerInventory(compass, owner.getUUID()),
                    Component.literal("Issued compass did not have a valid owner/pet signature"));
            context.assertValueEqual(
                    "Held by you",
                    compass.get(DataComponents.LORE).lines().getFirst().getString(),
                    Component.literal("Held compass status was incorrect"));

            owner.getInventory().setItem(owner.getInventory().getFreeSlot(), compass.copy());
            ItemStack forged = compass.copy();
            CompoundTag forgedCustom = forged.get(DataComponents.CUSTOM_DATA).copyTag();
            CompoundTag forgedRoot = forgedCustom.getCompound("pet_companion_compass").orElseThrow();
            forgedRoot.putString("signature", "0".repeat(64));
            forgedCustom.put("pet_companion_compass", forgedRoot);
            forged.set(DataComponents.CUSTOM_DATA, CustomData.of(forgedCustom));
            context.assertFalse(
                    manager.isAllowedInPlayerInventory(forged, owner.getUUID()),
                    Component.literal("Tampered compass signature was trusted"));
            owner.getInventory().setItem(owner.getInventory().getFreeSlot(), forged);
            PetCompassIssueResult deduplicated = manager.issueOrRefresh(
                    owner,
                    new PetAuthoritySnapshot(held, false));
            context.assertValueEqual(
                    PetCompassIssueStatus.REFRESHED,
                    deduplicated.status(),
                    Component.literal("Existing compass was not refreshed"));
            context.assertValueEqual(
                    2,
                    deduplicated.removedInvalidOrDuplicate(),
                    Component.literal("Duplicate/tampered compass was not removed"));
            context.assertValueEqual(1, countCompasses(owner), Component.literal("More than one compass remains"));

            Pet remote = withPlacement(
                    held,
                    PlacedPlacement.virtualized(
                            remoteBackend,
                            localDimension,
                            new WorldPosition(100.0, 70.0, 100.0)));
            manager.issueOrRefresh(owner, new PetAuthoritySnapshot(remote, false));
            compass = findCompass(owner);
            context.assertValueEqual(
                    "On Survival Realm",
                    compass.get(DataComponents.LORE).lines().getFirst().getString(),
                    Component.literal("Remote backend friendly name was missing"));
            context.assertTrue(
                    compass.get(DataComponents.LODESTONE_TRACKER) == null,
                    Component.literal("Remote compass retained a misleading direction"));

            Pet otherDimension = withPlacement(
                    held,
                    PlacedPlacement.virtualized(
                            localBackend,
                            DimensionId.parse("minecraft:the_nether"),
                            new WorldPosition(3.0, 65.0, 3.0)));
            manager.issueOrRefresh(owner, new PetAuthoritySnapshot(otherDimension, false));
            compass = findCompass(owner);
            context.assertValueEqual(
                    "In minecraft:the_nether",
                    compass.get(DataComponents.LORE).lines().getFirst().getString(),
                    Component.literal("Different-dimension status was incorrect"));
            context.assertTrue(
                    compass.get(DataComponents.LODESTONE_TRACKER) == null,
                    Component.literal("Different-dimension compass retained a direction"));

            BlockPos lastKnown = context.absolutePos(new BlockPos(1, 1, 1));
            Pet virtualized = withPlacement(
                    held,
                    PlacedPlacement.virtualized(
                            localBackend,
                            localDimension,
                            new WorldPosition(lastKnown.getX(), lastKnown.getY(), lastKnown.getZ())));
            manager.issueOrRefresh(owner, new PetAuthoritySnapshot(virtualized, false));
            compass = findCompass(owner);
            LodestoneTracker lastKnownTracker = compass.get(DataComponents.LODESTONE_TRACKER);
            context.assertValueEqual(
                    lastKnown,
                    lastKnownTracker.target().orElseThrow().pos(),
                    Component.literal("Compass did not point to last authoritative coordinates"));

            UUID entityId = UUID.fromString("30000000-0000-0000-0000-000000000024");
            BlockPos authoritativePosition = context.absolutePos(new BlockPos(2, 1, 2));
            BlockPos livePosition = context.absolutePos(new BlockPos(4, 1, 4));
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
            liveEntity.snapTo(Vec3.atCenterOf(livePosition), 0.0F, 0.0F);
            context.assertTrue(world.addFreshEntity(liveEntity), Component.literal("Live compass fixture did not spawn"));
            manager.issueOrRefresh(owner, new PetAuthoritySnapshot(materialized, false));
            compass = findCompass(owner);
            context.assertValueEqual(
                    liveEntity.blockPosition(),
                    compass.get(DataComponents.LODESTONE_TRACKER).target().orElseThrow().pos(),
                    Component.literal("Compass did not prefer the loaded live entity position"));
            PetCompassIssueResult inactiveAccess = manager.issueOrRefresh(
                    owner,
                    new PetAuthoritySnapshot(materialized, false, false));
            context.assertValueEqual(
                    PetCompassIssueStatus.REFRESHED,
                    inactiveAccess.status(),
                    Component.literal("Inactive AI access disabled physical compass refresh"));

            ItemStack enforcementCopy = compass.copy();
            context.assertFalse(
                    new Slot(new SimpleContainer(1), 0, 0, 0).mayPlace(enforcementCopy),
                    Component.literal("Container slot accepted a pet compass"));
            context.assertFalse(
                    new Slot(intruder.getInventory(), 0, 0, 0).mayPlace(enforcementCopy),
                    Component.literal("Another player's inventory accepted the pet compass"));
            SimpleContainer hopperSource = new SimpleContainer(enforcementCopy.copy());
            SimpleContainer hopperTarget = new SimpleContainer(1);
            ItemStack hopperRemainder = HopperBlockEntity.addItem(
                    hopperSource,
                    hopperTarget,
                    enforcementCopy.copy(),
                    Direction.DOWN);
            context.assertFalse(hopperRemainder.isEmpty(), Component.literal("Hopper consumed pet compass"));
            context.assertTrue(hopperTarget.isEmpty(), Component.literal("Hopper transferred pet compass"));

            owner.getInventory().clearContent();
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                owner.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            }
            authority.current = held;
            int fullResult = world.getServer().getCommands().getDispatcher()
                    .execute("pet compass", source);
            context.assertValueEqual(1, fullResult, Component.literal("Full-inventory compass command did not schedule"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("inventory is full")),
                    Component.literal("Full inventory did not produce clear feedback"));
            context.assertValueEqual(0, countCompasses(owner), Component.literal("Full inventory received a compass"));
            context.assertValueEqual(2, authority.ownerLookupCount, Component.literal("Compass command lookup count changed"));
            context.assertValueEqual(0, authority.mutationCount, Component.literal("Compass behavior wrote authority state"));

            Vec3 dropPosition = context.absoluteVec(new Vec3(3.5, 2.0, 3.5));
            dropped = new ItemEntity(
                    world,
                    dropPosition.x,
                    dropPosition.y,
                    dropPosition.z,
                    enforcementCopy);
            context.assertTrue(world.addFreshEntity(dropped), Component.literal("Dropped compass fixture did not spawn"));
            ItemEntity droppedReference = dropped;
            TamableAnimal liveReference = liveEntity;
            context.runAfterDelay(2, () -> {
                try {
                    context.assertTrue(
                            droppedReference.isRemoved(),
                            Component.literal("Dropped pet compass item entity was not removed"));
                } finally {
                    liveReference.discard();
                    uninstallGateway.run();
                    removeMockPlayer(world, intruder);
                    removeMockPlayer(world, owner);
                }
                context.succeed();
            });
        } catch (CommandSyntaxException failure) {
            throw context.assertionException("Pet compass command execution failed: %s", failure.getMessage());
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
    public void petAdoptCommandRequiresAccessAndNeverRerollsExistingPet(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, null);
        authority.adoptionAccess = false;
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(
                new BackendId("gametest"), authority);
        CapturingCommandOutput output = new CapturingCommandOutput();
        CommandSourceStack source = owner.createCommandSourceStack().withSource(output);
        try {
            var dispatcher = world.getServer().getCommands().getDispatcher();
            context.assertValueEqual(1, dispatcher.execute("pet adopt", source),
                    Component.literal("Adoption menu did not open"));
            context.assertValueEqual(1, dispatcher.execute("pet adopt cat", source),
                    Component.literal("Cat name entry did not open"));
            context.assertValueEqual(1, dispatcher.execute("pet adopt dog", source),
                    Component.literal("Dog name entry did not open"));
            context.assertValueEqual(0, authority.adoptionRequestCount,
                    Component.literal("Opening adoption UI submitted an adoption"));
            context.assertTrue(output.components.stream().flatMap(text -> text.toFlatList(
                    net.minecraft.network.chat.Style.EMPTY).stream()).anyMatch(text ->
                    text.getStyle().getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.SuggestCommand click
                            && click.command().equals("/pet adopt dog ")),
                    Component.literal("Dog name action did not populate a private slash command"));
            context.assertValueEqual(
                    1,
                    world.getServer().getCommands().getDispatcher()
                            .execute("pet adopt cat Luna", source),
                    Component.literal("Denied adoption command did not schedule"));
            context.assertTrue(authority.current == null, Component.literal("Denied adoption created a pet"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("active subscription")),
                    Component.literal("Denied adoption omitted subscription feedback"));

            authority.adoptionAccess = true;
            context.assertValueEqual(
                    1,
                    world.getServer().getCommands().getDispatcher()
                            .execute("pet adopt dog Pepper", source),
                    Component.literal("Allowed adoption command did not schedule"));
            Pet created = authority.current;
            context.assertValueEqual("Pepper", created.name(), Component.literal("Adoption name changed"));
            context.assertValueEqual(
                    PetSpecies.DOG,
                    created.appearance().species(),
                    Component.literal("Adoption species changed"));

            context.assertValueEqual(
                    1,
                    world.getServer().getCommands().getDispatcher()
                            .execute("pet adopt cat Reroll", source),
                    Component.literal("Repeat adoption command did not schedule"));
            context.assertTrue(authority.current == created, Component.literal("Repeat adoption rerolled the pet"));
            context.assertValueEqual(3, authority.adoptionRequestCount, Component.literal("Adoption request count changed"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("Adopted Pepper the dog")),
                    Component.literal("Successful adoption feedback was missing"));
            context.assertTrue(
                    output.messages.stream().anyMatch(message -> message.contains("already own Pepper")),
                    Component.literal("Existing-pet feedback was missing"));
        } catch (CommandSyntaxException failure) {
            throw context.assertionException("Pet adoption command execution failed: %s", failure.getMessage());
        } finally {
            uninstallGateway.run();
            removeMockPlayer(world, owner);
        }
        context.succeed();
    }

    @GameTest
    @SuppressWarnings("removal")
    public void petCommandsEnforceExplicitPermissionNodes(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = context.makeMockServerPlayerInLevel();
        Pet aggregate = pet(
                UUID.fromString("10000000-0000-0000-0000-000000000027"),
                owner.getUUID(), PetSpecies.CAT, "minecraft:tabby", 0.66, 0L);
        InMemoryAuthorityGateway authority = new InMemoryAuthorityGateway(world, aggregate);
        Runnable uninstallGateway = PetCompanionMod.installAuthorityGateway(
                new BackendId("gametest"), authority);
        CapturingCommandOutput output = new CapturingCommandOutput();
        CommandSourceStack source = owner.createCommandSourceStack().withSource(output);
        Runnable restoreFeaturePermissions = PetPermissions.install(
                (ignored, node, defaultLevel) ->
                        !node.equals(PetPermission.ADOPT.node())
                                && !node.equals(PetPermission.RECALL.node()));
        try {
            context.assertValueEqual(
                    1,
                    world.getServer().getCommands().getDispatcher()
                            .execute("pet status", source),
                    Component.literal("Allowed aipets.use command failed"));
            context.assertValueEqual(
                    1,
                    world.getServer().getCommands().getDispatcher().execute("pet", source),
                    Component.literal("Permission-filtered /pet help failed"));
            String help = output.messages.stream()
                    .filter(message -> message.startsWith("Pet Companion:"))
                    .findFirst()
                    .orElseThrow(() -> context.assertionException("Permission-filtered help was missing"));
            context.assertFalse(help.contains("/pet adopt"), Component.literal("Help exposed denied adopt"));
            context.assertFalse(help.contains("/pet recall"), Component.literal("Help exposed denied recall"));
            context.assertTrue(help.contains("/pet compass"), Component.literal("Help omitted allowed compass"));
            assertCommandDenied(context, world, source, "pet adopt cat Blocked");
            assertCommandDenied(context, world, source, "pet recall");
            context.assertValueEqual(
                    0,
                    authority.adoptionRequestCount,
                    Component.literal("Denied aipets.adopt reached adoption authority"));
            context.assertValueEqual(
                    0,
                    authority.mutationCount,
                    Component.literal("Denied aipets.recall reached mutation authority"));
            restoreFeaturePermissions.run();

            Runnable restoreUse = PetPermissions.install(
                    (ignored, node, defaultLevel) -> !node.equals(PetPermission.USE.node()));
            try {
                assertCommandDenied(context, world, source, "pet status");
            } finally {
                restoreUse.run();
            }
        } catch (CommandSyntaxException failure) {
            throw context.assertionException("Allowed permission command failed: %s", failure.getMessage());
        } finally {
            restoreFeaturePermissions.run();
            uninstallGateway.run();
            removeMockPlayer(world, owner);
        }
        context.succeed();
    }

    private static void assertCommandDenied(
            GameTestHelper context,
            ServerLevel world,
            CommandSourceStack source,
            String command) {
        try {
            world.getServer().getCommands().getDispatcher().execute(command, source);
            throw context.assertionException("Permission gate allowed command: %s", command);
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

    private static ItemStack findCompass(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (PetCompassItem.isCandidate(stack)) {
                return stack;
            }
        }
        throw new IllegalStateException("No pet compass in player inventory");
    }

    private static int countCompasses(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (PetCompassItem.isCandidate(player.getInventory().getItem(slot))) {
                count++;
            }
        }
        return count;
    }

    private static void assertPhysicalPet(
            GameTestHelper context,
            TamableAnimal entity,
            Pet pet,
            String expectedVariantId) {
        PetEntityData data = (PetEntityData) entity;
        context.assertTrue(data.aipets$isPet(), Component.literal("Physical entity is not marked"));
        context.assertValueEqual(pet.petId(), data.aipets$getPetId(), Component.literal("Pet ID mismatch"));
        context.assertValueEqual(pet.ownerUuid(), data.aipets$getOwnerUuid(), Component.literal("Owner ID mismatch"));
        context.assertValueEqual(pet.recordVersion(), data.aipets$getRecordVersion(), Component.literal("Revision mismatch"));
        context.assertTrue(entity.isInvulnerable(), Component.literal("Physical entity is not invulnerable"));
        context.assertTrue(entity.isPersistenceRequired(), Component.literal("Physical entity is not persistent"));
        context.assertTrue(
                entity.entityTags().contains(PetEntityController.NO_DESPAWN_TAG),
                Component.literal("Physical entity lacks no_despawn tag"));
        context.assertTrue(
                Math.abs(entity.getAttributeBaseValue(Attributes.SCALE) - pet.appearance().scale()) < 1.0E-9,
                Component.literal("Scale attribute base value mismatch"));
        context.assertTrue(
                Math.abs(entity.getScale() - pet.appearance().scale()) < 1.0E-6,
                Component.literal("Effective entity scale mismatch"));

        Identifier materializedVariant;
        if (entity instanceof Cat cat) {
            Registry<CatVariant> registry = context.getLevel()
                    .registryAccess()
                    .lookupOrThrow(Registries.CAT_VARIANT);
            materializedVariant = registry.getKey(cat.getVariant().value());
        } else if (entity instanceof Wolf wolf) {
            Registry<WolfVariant> registry = context.getLevel()
                    .registryAccess()
                    .lookupOrThrow(Registries.WOLF_VARIANT);
            materializedVariant = registry.getKey(
                    ((WolfEntityVariantInvoker) wolf).aipets$getVariant().value());
        } else {
            throw context.assertionException("Unexpected physical entity type: %s", entity.getType());
        }
        context.assertValueEqual(
                expectedVariantId,
                materializedVariant.toString(),
                Component.literal("Materialized variant mismatch"));
    }

    private static void setScale(TamableAnimal entity, double scale) {
        entity.getAttribute(Attributes.SCALE).setBaseValue(scale);
    }

    private static <T> void assertRegistryContains(
            GameTestHelper context,
            Registry<T> registry,
            ResourceId variant) {
        context.assertTrue(
                registry.containsKey(Identifier.parse(variant.value())),
                Component.literal("Configured variant missing at runtime: " + variant));
    }

    private static WorldPosition position(Vec3 position) {
        return new WorldPosition(position.x, position.y, position.z);
    }

    private static void removeMockPlayer(ServerLevel world, ServerPlayer player) {
        PlayerList playerManager = world.getServer().getPlayerList();
        if (playerManager.getPlayer(player.getUUID()) == player) {
            playerManager.remove(player);
        }
    }

    private static final class InMemoryAuthorityGateway implements PetAuthorityGateway {
        private final ServerLevel world;
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

        private InMemoryAuthorityGateway(ServerLevel world, Pet initial) {
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
            entityWasAbsentAtCommit = world.getEntityInAnyDimension(command.entityUuid()) == null;
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
            entityWasPresentAtTransferCommit = world.getEntityInAnyDimension(
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

    private static final class CapturingCommandOutput implements CommandSource {
        private final List<String> messages = new ArrayList<>();
        private final List<Component> components = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
            components.add(message.copy());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
