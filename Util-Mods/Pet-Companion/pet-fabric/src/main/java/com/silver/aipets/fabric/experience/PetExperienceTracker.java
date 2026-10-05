package com.silver.aipets.fabric.experience;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.fabric.ai.PetIntent;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.config.PetServiceClientConfig;
import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.ai.PetActionController;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/** Loaded, local, owner-aware capture. Debounce precedes all asynchronous transport. */
public final class PetExperienceTracker {
    private final PetServiceClientConfig config;
    private final HttpClient http;
    private final Map<Entity, Boolean> loaded = new IdentityHashMap<>();
    private final java.util.Set<UUID> liveEntityIds =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastOwnerSeen = new HashMap<>();
    private final Map<UUID, String> lastDimension = new HashMap<>();
    private final ExperienceGate gate = new ExperienceGate();
    private final PetDecisionDeliveryGate decisions = new PetDecisionDeliveryGate();
    private final Semaphore inFlight = new Semaphore(64);
    private final ThreadPoolExecutor transport = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128), task -> {
                Thread thread = new Thread(task, "pet-experience-transport");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());
    private long lastSampleTick;

    public PetExperienceTracker(PetServiceClientConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
    }

    public void onLoad(Entity entity) {
        if (entity instanceof TamableAnimal && entity instanceof PetEntityData data
                && data.aipets$isPet()) {
            loaded.put(entity, true);
            liveEntityIds.add(entity.getUUID());
        }
    }

    public void onUnload(Entity entity) {
        loaded.remove(entity);
        liveEntityIds.remove(entity.getUUID());
        if (entity instanceof PetEntityData data && data.aipets$isPet()) {
            if (entity instanceof TamableAnimal pet) PetActionController.suspend(pet);
            decisions.invalidate(data.aipets$getPetId());
            lastOwnerSeen.remove(data.aipets$getPetId());
            lastDimension.remove(data.aipets$getPetId());
        }
    }

    public void clear() {
        loaded.clear(); liveEntityIds.clear(); lastOwnerSeen.clear(); lastDimension.clear();
        gate.clear(); decisions.clear();
        transport.shutdownNow();
    }

    public void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (tick % 5 == 0) sampleNearbyDamage();
        if (tick - lastSampleTick < 100) return;
        lastSampleTick = tick;
        loaded.keySet().removeIf(entity -> {
            if (!entity.isRemoved()) return false;
            liveEntityIds.remove(entity.getUUID());
            if (entity instanceof PetEntityData data && data.aipets$isPet()) {
                if (entity instanceof TamableAnimal pet) PetActionController.invalidate(pet);
                lastOwnerSeen.remove(data.aipets$getPetId());
                lastDimension.remove(data.aipets$getPetId());
            }
            return true;
        });
        for (Entity entity : loaded.keySet()) {
            if (!(entity instanceof TamableAnimal pet) || !(entity instanceof PetEntityData data)
                    || !data.aipets$isPet() || data.aipets$isSleeping()
                    || !(pet.level() instanceof ServerLevel world)) continue;
            Player owner = world.getPlayerInAnyDimension(data.aipets$getOwnerUuid());
            if (owner == null || owner.level() != world
                    || owner.distanceToSqr(pet) > 16 * 16
                    || !pet.hasLineOfSight(owner)) continue;
            Long previous = lastOwnerSeen.put(data.aipets$getPetId(), tick);
            String dimension = world.dimension().identifier().toString();
            String previousDimension = lastDimension.put(data.aipets$getPetId(), dimension);
            if (previousDimension == null || !previousDimension.equals(dimension)) {
                report(pet, "ARRIVED_TOGETHER", "Owner and pet arrived together in "
                        + config.backendId().value() + " / " + dimension
                        + " biome=" + biome(world, pet.blockPosition()), 6 * 60 * 60 * 20L);
                if (previous == null) PetActionController.greet(pet);
            }
            if (previous != null && tick - previous > 60 * 20L) {
                PetActionController.greet(pet);
                report(pet, "OWNER_RETURNED", "Owner returned to the nearby pet", 5 * 60 * 20L);
                if (tick - previous > 30 * 60 * 20L)
                    report(pet, "OWNER_RETURNED_AFTER_ABSENCE",
                            "Owner returned after a long absence while the pet was present biome="
                                    + biome(world, pet.blockPosition()),
                            30 * 60 * 20L);
            }
            var threats = world.getEntitiesOfClass(Monster.class,
                    pet.getBoundingBox().inflate(10), mob -> mob.isAlive()
                            && pet.hasLineOfSight(mob));
            if (!threats.isEmpty()) {
                PetActionController.danger(pet, threats.getFirst());
                report(pet, "NEARBY_THREAT", "A hostile mob was near the pet while its owner was nearby",
                        10 * 60 * 20L);
            }
            String area = world.dimension().identifier() + ":" + (pet.blockPosition().getX() >> 7)
                    + ":" + (pet.blockPosition().getZ() >> 7)
                    + " biome=" + biome(world, pet.blockPosition());
            report(pet, "NEW_AREA", "Owner and pet visited area " + area,
                    365L * 24 * 60 * 60 * 20);
        }
    }

    public void interaction(TamableAnimal pet) {
        if (pet instanceof PetEntityData data && data.aipets$isPet()) {
            decisions.invalidate(data.aipets$getPetId());
            // Give a local, interruptible response now; text arrives asynchronously.
            PetActionController.greet(pet);
        }
        report(pet, "DIRECT_INTERACTION", "Owner interacted directly with the pet", 30 * 20L);
    }

    public void action(TamableAnimal pet, String action) {
        action(pet,action,"UNKNOWN");
    }

    /** Carries the controller's existing local reason into this witnessed completion event. */
    public void action(TamableAnimal pet, String action, String reason) {
        String safeAction=action!=null&&action.matches("[A-Z_]{3,32}")?action:"UNKNOWN";
        String safeReason=reason!=null&&java.util.Set.of("OWNER_NEAR","OWNER_MOVING","OWNER_INTERACTION",
                "THREAT","CURIOSITY","REST","STANCE","WITNESSED_EVENT","RELEVANT_MEMORY").contains(reason)
                ?reason:"UNKNOWN";
        report(pet, "ACTION_COMPLETED", "Pet completed " + safeAction + " reason=" + safeReason,
                5 * 60 * 20L);
    }

    private void sampleNearbyDamage() {
        for (Entity entity : loaded.keySet()) {
            if (!(entity instanceof TamableAnimal pet) || !(pet instanceof PetEntityData data)
                    || !data.aipets$isPet() || pet.isRemoved() || data.aipets$isSleeping()
                    || !(pet.level() instanceof ServerLevel world)) continue;
            Player owner = world.getPlayerInAnyDimension(data.aipets$getOwnerUuid());
            if (owner != null && owner.isAlive() && owner.level() == world
                    && owner.distanceToSqr(pet) <= 16 * 16
                    && pet.hasLineOfSight(owner) && owner.hurtTime > 0) {
                report(pet, "SURVIVED_DANGER",
                        "Owner survived nearby damage with the pet present", 10 * 60 * 20L);
            }
        }
    }

    /** A rare sample of a completed owner action, never a per-block or per-attack stream. */
    public void ownerBrokeBlock(ServerPlayer owner, BlockPos position, BlockState state) {
        for (Entity entity : loaded.keySet()) {
            if (!(entity instanceof TamableAnimal pet)
                    || !witnesses(owner, pet, position)) continue;
            String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            report(pet, "OWNER_ACTION", "Owner broke " + block + " nearby in biome="
                    + biome((ServerLevel) pet.level(), position), 15 * 60 * 20L,
                    "block_break", position);
        }
    }

    public void ownerDefeatedMob(LivingEntity defeated, DamageSource damage) {
        if (!(damage.getEntity() instanceof ServerPlayer owner)) return;
        for (Entity entity : loaded.keySet()) {
            if (!(entity instanceof TamableAnimal pet)
                    || !witnesses(owner, pet, defeated.blockPosition())
                    || !pet.hasLineOfSight(defeated)) continue;
            String mob = BuiltInRegistries.ENTITY_TYPE.getKey(defeated.getType()).toString();
            report(pet, "OWNER_ACTION", "Owner defeated " + mob + " nearby in biome="
                    + biome((ServerLevel) pet.level(), defeated.blockPosition()),
                    5 * 60 * 20L, "defeated_mob", defeated.blockPosition());
        }
    }

    private boolean witnesses(ServerPlayer owner, TamableAnimal pet, BlockPos action) {
        if (!(pet instanceof PetEntityData data) || !data.aipets$isPet()
                || !(pet.level() instanceof ServerLevel world)) return false;
        return owner.getUUID().equals(data.aipets$getOwnerUuid())
                && ExperienceGate.witnessedOwnerAction(loaded.containsKey(pet),
                        data.aipets$isSleeping(), pet.isRemoved(), owner.level() == world,
                        owner.distanceToSqr(pet), pet.hasLineOfSight(owner))
                && action.distSqr(pet.blockPosition()) <= 16 * 16;
    }

    private static String biome(ServerLevel world, BlockPos position) {
        return world.getBiome(position).unwrapKey()
                .map(key -> key.identifier().toString()).orElse("unknown");
    }

    private void report(TamableAnimal pet, String type, String summary, long cooldownTicks) {
        report(pet, type, summary, cooldownTicks,
                type.equals("NEW_AREA") ? summary : "");
    }

    private void report(TamableAnimal pet, String type, String summary,
            long cooldownTicks, String debounceDetail) {
        report(pet, type, summary, cooldownTicks, debounceDetail, null);
    }

    private void report(TamableAnimal pet, String type, String summary,
            long cooldownTicks, String debounceDetail, BlockPos observedTarget) {
        if (!(pet instanceof PetEntityData data) || !data.aipets$isPet()
                || !(pet.level() instanceof ServerLevel world)) return;
        Player owner = world.getPlayerInAnyDimension(data.aipets$getOwnerUuid());
        if (!ExperienceGate.aware(loaded.containsKey(pet), data.aipets$isSleeping(),
                pet.isRemoved(), owner != null && owner.level() == world,
                owner == null ? Double.POSITIVE_INFINITY : owner.distanceToSqr(pet))) return;
        long now = world.getServer().getTickCount();
        if (!gate.claim(data.aipets$getPetId(), type, debounceDetail,
                now, cooldownTicks)) return;
        if (observedTarget != null) PetActionController.notice(pet, observedTarget);
        JsonObject body = new JsonObject();
        UUID eventId = UUID.randomUUID();
        UUID petId = data.aipets$getPetId();
        long expectedIntentionVersion = PetActionController.intentionVersion(pet);
        decisions.begin(petId, pet.getUUID(), eventId, Instant.now());
        body.addProperty("eventId", eventId.toString());
        body.addProperty("petId", petId.toString());
        body.addProperty("ownerUuid", data.aipets$getOwnerUuid().toString());
        UUID entityId = pet.getUUID();
        body.addProperty("entityUuid", entityId.toString());
        body.addProperty("backend", config.backendId().value());
        body.addProperty("dimension", world.dimension().identifier().toString());
        body.add("location", com.silver.aipets.common.transport.SpatialLocationCodec.encode(
                com.silver.aipets.fabric.conversation.PetSpatialLocationCapture.capture(world,
                        config.backendId().value(),
                        observedTarget == null ? pet.getX() : observedTarget.getX() + .5,
                        observedTarget == null ? pet.getY() : observedTarget.getY() + .5,
                        observedTarget == null ? pet.getZ() : observedTarget.getZ() + .5)));
        body.addProperty("type", type);
        body.addProperty("summary", summary);
        body.addProperty("occurredAt", Instant.now().toString());
        String payload = body.toString();
        transport.execute(() -> {
            if (!liveEntityIds.contains(entityId)) return;
            if (!inFlight.tryAcquire()) return;
            sendAttempt(world, pet, petId, entityId, eventId, payload,
                    expectedIntentionVersion, 0);
        });
    }

    private void sendAttempt(ServerLevel world, TamableAnimal pet, UUID petId,
            UUID entityId, UUID eventId, String payload,
            long expectedIntentionVersion, int attempt) {
        try {
            HttpRequest request = HttpRequest.newBuilder(config.baseUri().resolve("v1/experiences"))
                    // Service planning has an eight-second provider deadline plus SQL work.
                    .timeout(Duration.ofSeconds(14))
                    .header("Authorization", "Bearer " + config.bearerToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, failure) -> {
                if ((failure != null || response == null || response.statusCode() >= 500)
                        && attempt == 0 && liveEntityIds.contains(entityId)
                        && decisions.isPending(petId, entityId, eventId, Instant.now())) {
                    sendAttempt(world, pet, petId, entityId, eventId, payload,
                            expectedIntentionVersion, 1);
                    return;
                }
                inFlight.release();
                if (failure != null || response == null) return;
                if (response.statusCode() == 409) {
                    world.getServer().execute(() -> {
                        if (world.getEntityInAnyDimension(entityId) == pet) pet.discard();
                    });
                    return;
                }
                if (response.statusCode() != 200 || response.body().length() > 512) return;
                try {
                    JsonObject decision = JsonParser.parseString(response.body()).getAsJsonObject();
                    PetIntent intent = PetIntent.valueOf(decision.get("intent").getAsString());
                    Instant expiry = Instant.parse(decision.get("expiresAt").getAsString());
                    String words = decision.has("utterance")
                            ? decision.get("utterance").getAsString() : null;
                    UUID memoryId = decision.has("memoryId")
                            ? UUID.fromString(decision.get("memoryId").getAsString()) : null;
                    world.getServer().execute(() -> {
                        if (pet.isRemoved() || pet.level() != world
                                || world.getEntityInAnyDimension(entityId) != pet
                                || !(pet instanceof PetEntityData data)
                                || !data.aipets$isPet() || data.aipets$isSleeping()
                                || !petId.equals(data.aipets$getPetId())
                                || !entityId.equals(pet.getUUID())) return;
                        Player owner = world.getPlayerInAnyDimension(data.aipets$getOwnerUuid());
                        if (owner == null || !owner.isAlive() || owner.level() != world
                                || owner.distanceToSqr(pet) > 16 * 16
                                || !pet.hasLineOfSight(owner)) return;
                        if (PetActionController.intentionVersion(pet)
                                != expectedIntentionVersion) return;
                        if (!decisions.accept(petId, entityId, eventId, Instant.now())) return;
                        boolean applied = PetActionController.suggest(pet, intent, expiry,
                                expectedIntentionVersion, eventId, memoryId);
                        if (applied && words != null && !words.isBlank() && words.length() <= 100) {
                            PetCompanionMod.speechDisplayManager().show(pet, words);
                            PetActionController.outputSound(pet);
                        }
                    });
                } catch (RuntimeException ignored) { }
            });
        } catch (RuntimeException failure) { inFlight.release(); }
    }
}
