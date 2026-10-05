package com.silver.aipets.fabric.ai;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.entity.PetEntityData;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import java.time.Duration;

/** Main-thread local actions. Follow and validity always interrupt optional work. */
public final class PetActionController {
    private static final Map<Mob, Window> WINDOWS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> CONTROLLED_SOUND = ThreadLocal.withInitial(() -> false);

    private PetActionController() { }

    public static void updateState(Mob entity, Pet pet) {
        Window window = WINDOWS.computeIfAbsent(entity, ignored -> new Window());
        window.traits = pet.traits();
        window.mood = pet.mood();
        window.species = pet.appearance().species();
    }

    public static boolean controlledSound() { return CONTROLLED_SOUND.get(); }

    public static String currentIntent(TamableAnimal pet) {
        Window window = WINDOWS.get(pet);
        return window == null ? "UNKNOWN" : window.intent.name();
    }

    public static long intentionVersion(TamableAnimal pet) {
        Window window = WINDOWS.get(pet);
        return window == null ? 0 : window.version;
    }

    public static long sceneVersion(TamableAnimal pet) {
        Window window = WINDOWS.get(pet);
        return window == null ? 0 : window.sceneVersion;
    }

    public static PetIntentionState snapshot(TamableAnimal pet) {
        Window window = WINDOWS.get(pet);
        return window == null ? null : window.state;
    }

    public static void restore(TamableAnimal pet, PetIntentionState state) {
        if (state == null || !state.resumable(Instant.now())) return;
        Window window = WINDOWS.computeIfAbsent(pet, ignored -> new Window());
        window.state = state;
        window.version = Math.max(window.version, state.version());
        window.intent = state.intent();
        window.until = -1; // Re-establish the safe local posture on the next tick.
    }

    public static void invalidate(TamableAnimal pet) {
        Window window = WINDOWS.get(pet);
        if (window == null) return;
        if (window.state != null || window.until != 0 || window.suggested != null)
            window.version++;
        window.state = null;
        window.until = 0;
        window.suggested = null;
        window.intent = PetIntent.PAUSE;
    }

    public static void suspend(TamableAnimal pet) {
        Window window = WINDOWS.get(pet);
        if (window == null) return;
        window.sceneVersion++;
        pet.getNavigation().stop();
        if (window.state == null || !window.state.resumable(Instant.now()))
            invalidate(pet);
        else window.until = -1;
    }

    static void playControlledAmbient(Mob pet) {
        if (CONTROLLED_SOUND.get()) return;
        CONTROLLED_SOUND.set(true);
        try { pet.playAmbientSound(); }
        finally { CONTROLLED_SOUND.set(false); }
    }

    public static void cancelForFollow(TamableAnimal pet) {
        Window window = WINDOWS.get(pet);
        if (window != null) {
            if (window.state != null) window.sceneVersion++;
            if (window.until > 0 && window.intent == PetIntent.EXPLORE_NEARBY)
                pet.getNavigation().stop();
            invalidate(pet);
        }
        PetActionCapabilities.forEntity(pet).sit(pet, false);
    }

    public static void greet(TamableAnimal pet) {
        if (!(pet instanceof PetEntityData data) || !data.aipets$isPet()
                || data.aipets$isSleeping() || pet.isRemoved()
                || !(pet.level() instanceof ServerLevel world)) return;
        Player owner = world.getPlayerInAnyDimension(data.aipets$getOwnerUuid());
        double followStop = PetCompanionMod.physicalConfig().followStopDistance();
        if (owner == null || owner.level() != world
                || pet.distanceToSqr(owner) > 16 * 16
                || !pet.hasLineOfSight(owner)) return;
        Window window = WINDOWS.computeIfAbsent(pet, ignored -> new Window());
        window.sceneVersion++;
        long tick = world.getServer().getTickCount();
        begin(pet, window, PetIntent.GREET_OWNER,
                PetIntentionState.Reason.OWNER_INTERACTION, null, null, null, tick, 60);
        window.lastDecision = tick;
        PetActionCapabilities capabilities = PetActionCapabilities.forEntity(pet);
        capabilities.sit(pet, false);
        pet.getLookControl().setLookAt(owner, 30, 30);
        if (pet.distanceToSqr(owner) > followStop * followStop)
            approach(pet, owner, followStop, 1.1);
        if (PetVocalizationPolicy.allow(PetVocalizationPolicy.Trigger.ATTENTION,
                tick, window.lastSound) && capabilities.supports(PetIntent.VOCALIZE)) {
            capabilities.vocalize(pet);
            window.lastSound = tick;
        }
    }

    /** Small immediate acknowledgment of an actually witnessed event. */
    public static void notice(TamableAnimal pet, BlockPos target) {
        if (!(pet instanceof PetEntityData data) || !data.aipets$isPet()
                || data.aipets$isSleeping() || pet.isRemoved()
                || !(pet.level() instanceof ServerLevel world)
                || target.distSqr(pet.blockPosition()) > 16 * 16
                || !world.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4))
            return;
        Window window = WINDOWS.computeIfAbsent(pet, ignored -> new Window());
        window.sceneVersion++;
        window.interest = net.minecraft.world.phys.Vec3.atCenterOf(target);
        long tick = world.getServer().getTickCount();
        window.interestExpires = tick + 400;
        begin(pet, window, PetIntent.LOOK_AT_INTEREST,
                PetIntentionState.Reason.WITNESSED_EVENT, target.immutable(),
                null, null, tick, 80);
        window.lastDecision = tick;
        pet.getLookControl().setLookAt(window.interest.x,
                window.interest.y, window.interest.z, 30, 30);
    }

    /** One species-appropriate vocalization for actual pet speech, never for system notices. */
    public static void outputSound(TamableAnimal pet) {
        if (!(pet instanceof PetEntityData data) || !data.aipets$isPet()
                || data.aipets$isSleeping() || pet.isRemoved()
                || !(pet.level() instanceof ServerLevel world)) return;
        Player owner = world.getPlayerInAnyDimension(data.aipets$getOwnerUuid());
        if (owner == null || owner.level() != world) return;
        Window window = WINDOWS.computeIfAbsent(pet, ignored -> new Window());
        long tick = world.getServer().getTickCount();
        if (!PetVocalizationPolicy.allow(PetVocalizationPolicy.Trigger.SPEECH,
                tick, window.lastSound)) return;
        PetActionCapabilities capabilities = PetActionCapabilities.forEntity(pet);
        if (!capabilities.supports(PetIntent.VOCALIZE)) return;
        capabilities.vocalize(pet);
        window.lastSound = tick;
    }

    public static boolean suggest(TamableAnimal pet, PetIntent intent, Instant expiresAt) {
        return suggest(pet, intent, expiresAt, intentionVersion(pet), null, null);
    }

    public static boolean suggest(TamableAnimal pet, PetIntent intent, Instant expiresAt,
            long expectedVersion, UUID sourceEventId, UUID memoryId) {
        if (!(pet instanceof PetEntityData data) || !data.aipets$isPet()
                || data.aipets$isSleeping() || pet.isRemoved()
                || !(pet.level() instanceof ServerLevel world)
                || !PetActionCapabilities.forEntity(pet).supports(intent)) return false;
        Player owner = world.getPlayerInAnyDimension(data.aipets$getOwnerUuid());
        if (owner == null || owner.level() != world
                || pet.distanceToSqr(owner) > 16 * 16
                || !pet.hasLineOfSight(owner)) return false;
        long seconds = Duration.between(Instant.now(), expiresAt).toSeconds();
        if (seconds <= 0) return false;
        Window window = WINDOWS.computeIfAbsent(pet, ignored -> new Window());
        if (window.version != expectedVersion) return false;
        if (intent == PetIntent.LOOK_AT_INTEREST
                && (window.interest == null
                        || world.getServer().getTickCount() >= window.interestExpires))
            return false;
        window.version++;
        window.suggested = intent;
        window.suggestedEventId = sourceEventId;
        window.suggestedMemoryId = memoryId;
        window.suggestionExpires = world.getServer().getTickCount()
                + Math.min(120, seconds) * 20;
        window.until = 0;
        window.state = null;
        window.lastDecision = world.getServer().getTickCount() - 100;
        return true;
    }

    public static void danger(TamableAnimal pet, Monster threat) {
        if (!(pet instanceof PetEntityData data) || !data.aipets$isPet()
                || data.aipets$isSleeping() || pet.isRemoved()) return;
        Window window = WINDOWS.computeIfAbsent(pet, ignored -> new Window());
        if (window.state != null
                && window.state.reason() == PetIntentionState.Reason.THREAT
                && threat.blockPosition().equals(window.state.target())
                && window.state.active(Instant.now())) {
            window.dangerUntil = pet.level().getGameTime() + 100;
            return;
        }
        window.dangerUntil = pet.level().getGameTime() + 100;
        window.sceneVersion++;
        window.interest = threat.position();
        window.interestExpires = ((ServerLevel) pet.level()).getServer().getTickCount() + 400;
        if (window.until > 0 && window.intent == PetIntent.EXPLORE_NEARBY)
            pet.getNavigation().stop();
        begin(pet, window, PetIntent.LOOK_AT_INTEREST,
                PetIntentionState.Reason.THREAT, threat.blockPosition(), null, null,
                ((ServerLevel) pet.level()).getServer().getTickCount(), 100);
        PetActionCapabilities.forEntity(pet).sit(pet, false);
    }

    public static void tick(TamableAnimal pet) {
        if (!(pet instanceof PetEntityData data) || !data.aipets$isPet()
                || data.aipets$isSleeping() || pet.isRemoved()
                || !(pet.level() instanceof ServerLevel world)) return;
        Player owner = world.getPlayerInAnyDimension(data.aipets$getOwnerUuid());
        if (owner == null || owner.level() != world || !owner.isAlive() || owner.isSpectator()) {
            cancelForFollow(pet);
            pet.getNavigation().stop();
            return;
        }
        double distance = pet.distanceToSqr(owner);
        double followStart = PetCompanionMod.physicalConfig().followStartDistance();
        double followStop = PetCompanionMod.physicalConfig().followStopDistance();
        double actionRadius = Math.min(followStart - .5, followStop + 1.5);
        if (distance > actionRadius * actionRadius) {
            cancelForFollow(pet);
            // The existing follow goal owns longer paths. Cover its stop/start hysteresis band.
            if (distance <= followStart * followStart
                    && world.getServer().getTickCount() % 10 == 0) {
                    approach(pet, owner, followStop, 1.1);
            }
            return;
        }
        Window window = WINDOWS.computeIfAbsent(pet, ignored -> new Window());
        long tick = world.getServer().getTickCount();
        if (window.until == -1) {
            PetIntentionState saved = window.state;
            if (saved == null || !saved.resumable(Instant.now())) invalidate(pet);
            else {
                window.startedAt = tick;
                window.startPosition = pet.position();
                window.until = tick + Math.max(1, Math.min(3600,
                        Duration.between(Instant.now(), saved.expiresAt()).toSeconds() * 20));
                if (saved.intent() == PetIntent.SIT || saved.intent() == PetIntent.REST)
                    PetActionCapabilities.forEntity(pet).sit(pet, true);
            }
        }
        if (owner.getDeltaMovement().horizontalDistanceSqr() > .01) {
            cancelForFollow(pet);
            if (distance > followStop * followStop && tick % 10 == 0)
                approach(pet, owner, followStop, 1.1);
            return;
        }
        if (world.getGameTime() < window.dangerUntil && window.interest != null) {
            PetActionCapabilities.forEntity(pet).sit(pet, false);
            pet.getLookControl().setLookAt(window.interest.x,
                    window.interest.y, window.interest.z, 30, 30);
            return;
        }
        if (window.until > 0 && window.until <= tick && window.intent != PetIntent.PAUSE) {
            String completed = window.intent.name();
            String reason = window.state == null ? "UNKNOWN" : window.state.reason().name();
            PetCompanionMod.experienceTracker().ifPresent(tracker -> tracker.action(pet, completed, reason));
            invalidate(pet);
        }
        if (window.until > tick) {
            if (PetActionSafety.interrupt(window.intent, distance,
                    followStart * followStart,
                    owner.getDeltaMovement().horizontalDistanceSqr() > .01,
                    pet.getNavigation().isDone(), tick - window.startedAt,
                    pet.position().distanceToSqr(window.startPosition))) {
                invalidate(pet);
                capabilitiesFor(pet).sit(pet, false);
                pet.getNavigation().stop();
            }
            if (window.intent == PetIntent.LOOK_AT_OWNER) {
                pet.getLookControl().setLookAt(owner, 30, 30);
            }
            if (window.intent == PetIntent.LOOK_AT_INTEREST && window.state != null
                    && window.state.target() != null) {
                BlockPos target = window.state.target();
                if (!world.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4)
                        || target.distSqr(pet.blockPosition()) > 16 * 16)
                    invalidate(pet);
                else pet.getLookControl().setLookAt(target.getX() + .5,
                        target.getY() + .5, target.getZ() + .5, 30, 30);
            }
            if (window.until > tick) return;
        }
        PetActionCapabilities capabilities = PetActionCapabilities.forEntity(pet);
        if (tick - window.lastDecision < 100) return;
        window.lastDecision = tick;
        PetTraits traits = window.traits;
        PetMood mood = window.mood;
        boolean cat = pet instanceof net.minecraft.world.entity.animal.feline.Cat;
        int anxiety = mood == null ? 15 : mood.decay(window.species,
                traits, Instant.now()).anxious();
        PetStancePolicy.Choice stance = PetStancePolicy.choose(traits, anxiety, cat,
                distance, followStop * followStop,
                PetActionChoice.roll(data.aipets$getPetId(), tick),
                capabilities.supports(PetIntent.SIT), tick - window.lastExploreAt,
                tick - window.lastSitAt, window.intent, tick - window.startedAt);
        PetIntent intent = stance.intent();
        PetIntentionState.Reason reason = stance.reason();
        if (window.suggested != null && tick < window.suggestionExpires) {
            intent = window.suggested;
            window.suggested = null;
            reason = window.suggestedMemoryId == null
                    ? PetIntentionState.Reason.WITNESSED_EVENT
                    : PetIntentionState.Reason.RELEVANT_MEMORY;
        }
        if (!capabilities.supports(intent)) intent = PetIntent.PAUSE;
        if (window.intent == PetIntent.SIT || window.intent == PetIntent.REST) {
            capabilities.sit(pet, false);
        }
        if (window.intent == PetIntent.EXPLORE_NEARBY) pet.getNavigation().stop();
        int duration = intent == PetIntent.EXPLORE_NEARBY ? 160
                : intent == PetIntent.SIT || intent == PetIntent.REST ? 600 : 300;
        BlockPos interestTarget = intent == PetIntent.LOOK_AT_INTEREST
                && window.interest != null && tick < window.interestExpires
                ? BlockPos.containing(window.interest) : null;
        if (intent == PetIntent.LOOK_AT_INTEREST && interestTarget == null)
            intent = PetIntent.LOOK_AT_OWNER;
        begin(pet, window, intent, reason, interestTarget, window.suggestedEventId,
                window.suggestedMemoryId, tick, duration);
        window.suggestedEventId = null;
        window.suggestedMemoryId = null;
        if (intent == PetIntent.SIT || intent == PetIntent.REST) window.lastSitAt = tick;
        if (intent == PetIntent.EXPLORE_NEARBY) window.lastExploreAt = tick;
        switch (intent) {
            case APPROACH_OWNER, GREET_OWNER, RETURN_TO_OWNER ->
                approach(pet, owner, followStop, 1.1);
            case LOOK_AT_OWNER -> pet.getLookControl().setLookAt(owner, 30, 30);
            case LOOK_AT_INTEREST -> {
                if (window.interest == null) pet.getLookControl().setLookAt(owner, 30, 30);
                else pet.getLookControl().setLookAt(window.interest.x,
                        window.interest.y, window.interest.z, 30, 30);
            }
            case SIT, REST -> capabilities.sit(pet, true);
            case VOCALIZE -> outputSound(pet);
            case EXPLORE_NEARBY -> {
                int offset = (int) Math.floorMod(tick / 200 + data.aipets$getPetId()
                        .getMostSignificantBits(), 4);
                boolean started = false;
                for (int attempt = 0; attempt < 4 && !started; attempt++) {
                    int direction = (offset + attempt) & 3;
                    BlockPos target = pet.blockPosition().offset(
                            direction == 0 ? 3 : direction == 1 ? -3 : 0,
                            0, direction == 2 ? 3 : direction == 3 ? -3 : 0);
                    if (PetInvestigationBounds.contains(owner.blockPosition(), pet.blockPosition(),
                            target, world.getServer().getPlayerList().getSimulationDistance())
                            && target.distSqr(owner.blockPosition()) >= 9
                            && target.distSqr(owner.blockPosition()) <= actionRadius * actionRadius
                            && world.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4)
                            && world.getBlockState(target).isAir()
                            && world.getBlockState(target.below()).isSolid()) {
                        started = pet.getNavigation().moveTo(target.getX() + .5, target.getY(),
                                target.getZ() + .5, .8);
                        if (started) {
                            PetIntentionState old = window.state;
                            window.state = new PetIntentionState(old.intent(), old.reason(),
                                    target.immutable(), old.sourceEventId(), old.memoryId(),
                                    old.beganAt(), old.reconsiderAt(), old.expiresAt(), old.version());
                        }
                    }
                }
                if (!started) invalidate(pet);
            }
            default -> { }
        }
    }

    private static void approach(TamableAnimal pet, Player owner, double stopDistance,
            double speed) {
        var target = PetOwnerApproachTarget.target(pet.position(), owner.position(), stopDistance);
        pet.getNavigation().moveTo(target.x, target.y, target.z, speed);
    }

    private static void begin(TamableAnimal pet, Window window, PetIntent intent,
            PetIntentionState.Reason reason, BlockPos target, UUID sourceEventId,
            UUID memoryId, long tick, int durationTicks) {
        Instant now = Instant.now();
        window.version++;
        window.intent = intent;
        window.startedAt = tick;
        window.startPosition = pet.position();
        window.until = tick + durationTicks;
        window.state = new PetIntentionState(intent, reason, target, sourceEventId,
                memoryId, now, now.plusMillis(durationTicks * 25L),
                now.plusMillis(durationTicks * 50L), window.version);
        if (pet instanceof PetEntityData data && data.aipets$isPet()) {
            String trace = "pet_intention pet_id=" + data.aipets$getPetId()
                    + " version=" + window.version + " intent=" + intent
                    + " reason=" + reason + " source_event=" + sourceEventId
                    + " memory_id=" + memoryId;
            if (reason == PetIntentionState.Reason.WITNESSED_EVENT
                    || reason == PetIntentionState.Reason.RELEVANT_MEMORY
                    || reason == PetIntentionState.Reason.THREAT)
                PetCompanionMod.LOGGER.info(trace);
            else PetCompanionMod.LOGGER.debug(trace);
        }
    }

    private static final class Window {
        private PetTraits traits;
        private PetMood mood;
        private com.silver.aipets.common.domain.PetSpecies species =
                com.silver.aipets.common.domain.PetSpecies.DOG;
        private PetIntent intent = PetIntent.PAUSE;
        private PetIntent suggested;
        private UUID suggestedEventId, suggestedMemoryId;
        private PetIntentionState state;
        private long version;
        private long sceneVersion;
        private long suggestionExpires;
        private long until, lastDecision, startedAt;
        private long lastSitAt = -1200, lastExploreAt = -600;
        private long lastSound = -600;
        private net.minecraft.world.phys.Vec3 startPosition = net.minecraft.world.phys.Vec3.ZERO;
        private net.minecraft.world.phys.Vec3 interest;
        private long interestExpires;
        private long dangerUntil;
    }

    private static PetActionCapabilities capabilitiesFor(Mob pet) {
        return PetActionCapabilities.forEntity(pet);
    }
}
