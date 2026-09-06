package com.silver.aipets.fabric.speech;

import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Server-thread owner of at most one fixed-orientation Text Display per pet. */
public final class PetSpeechDisplayManager {
    private final PetSpeechPolicy policy;
    private final Map<UUID, ActiveDisplay> active = new HashMap<>();

    public PetSpeechDisplayManager(PetSpeechPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public DisplayEntity.TextDisplayEntity show(TameableEntity pet, String reply) {
        requirePet(pet);
        if (!(pet.getEntityWorld() instanceof ServerWorld world)) {
            throw new IllegalArgumentException("Speech requires a server-world pet");
        }
        UUID petId = ((PetEntityData) pet).aipets$getPetId();
        remove(petId);
        PetSpeechPolicy.PreparedSpeech prepared = policy.prepare(reply);
        DisplayEntity.TextDisplayEntity display = EntityType.TEXT_DISPLAY.create(
                world, SpawnReason.COMMAND);
        if (display == null) throw new IllegalStateException("Minecraft did not create a Text Display");
        display.setText(Text.literal(prepared.wrappedText()));
        display.setBillboardMode(DisplayEntity.BillboardMode.FIXED);
        display.setLineWidth(policy.wrapColumns() * 6);
        display.setViewRange(policy.viewRange());
        position(display, pet);
        if (!world.spawnEntity(display)) {
            display.discard();
            throw new IllegalStateException("Minecraft rejected the pet speech display spawn");
        }
        long lifetimeTicks = Math.max(1, (prepared.lifetime().toMillis() + 49L) / 50L);
        active.put(petId, new ActiveDisplay(
                petId, pet.getUuid(), display, world.getTime() + lifetimeTicks));
        return display;
    }

    public void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        active.values().removeIf(current -> {
            DisplayEntity.TextDisplayEntity display = current.display();
            if (display.isRemoved()) return true;
            ServerWorld world = (ServerWorld) display.getEntityWorld();
            Entity entity = world.getEntity(current.petEntityId());
            if (!(entity instanceof TameableEntity pet)
                    || !(pet instanceof PetEntityData data)
                    || !data.aipets$isPet()
                    || !data.aipets$getPetId().equals(current.petId())
                    || world.getTime() >= current.expiresAtTick()) {
                display.discard(); return true;
            }
            position(display, pet);
            return false;
        });
    }

    public void onPetUnloaded(Entity entity) {
        if (entity instanceof PetEntityData data && data.aipets$isPet()) remove(data.aipets$getPetId());
    }

    public void remove(UUID petId) {
        ActiveDisplay removed = active.remove(Objects.requireNonNull(petId, "petId"));
        if (removed != null && !removed.display().isRemoved()) removed.display().discard();
    }

    public void clear() {
        active.values().forEach(value -> value.display().discard());
        active.clear();
    }

    public Optional<DisplayEntity.TextDisplayEntity> active(UUID petId) {
        ActiveDisplay found = active.get(petId);
        return found == null || found.display().isRemoved()
                ? Optional.empty() : Optional.of(found.display());
    }

    public int activeCount() { return active.size(); }

    private void position(DisplayEntity.TextDisplayEntity display, TameableEntity pet) {
        display.refreshPositionAndAngles(
                pet.getX(), pet.getY() + pet.getHeight() + policy.verticalGap(), pet.getZ(),
                MathHelper.wrapDegrees(pet.getYaw() + policy.yawOffsetDegrees()), 0.0F);
    }

    private static void requirePet(TameableEntity pet) {
        Objects.requireNonNull(pet, "pet");
        if (!(pet instanceof PetEntityData data) || !data.aipets$isPet()) {
            throw new IllegalArgumentException("Speech target is not a marked pet");
        }
    }

    private record ActiveDisplay(
            UUID petId, UUID petEntityId,
            DisplayEntity.TextDisplayEntity display, long expiresAtTick) { }
}
