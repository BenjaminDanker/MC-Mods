package com.silver.aipets.fabric.speech;

import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.TamableAnimal;
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

    public Display.TextDisplay show(TamableAnimal pet, String reply) {
        requirePet(pet);
        if (!(pet.level() instanceof ServerLevel world)) {
            throw new IllegalArgumentException("Speech requires a server-world pet");
        }
        UUID petId = ((PetEntityData) pet).aipets$getPetId();
        remove(petId);
        PetSpeechPolicy.PreparedSpeech prepared = policy.prepare(reply);
        Display.TextDisplay display = EntityTypes.TEXT_DISPLAY.create(
                world, EntitySpawnReason.COMMAND);
        if (display == null) throw new IllegalStateException("Minecraft did not create a Text Display");
        display.setText(Component.literal(prepared.wrappedText()));
        // CENTER is rendered per-client toward that viewer's camera. The server only
        // maintains the display's position above the pet and never rotates per viewer.
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display.setLineWidth(policy.wrapColumns() * 6);
        display.setViewRange(policy.viewRange());
        position(display, pet);
        if (!world.addFreshEntity(display)) {
            display.discard();
            throw new IllegalStateException("Minecraft rejected the pet speech display spawn");
        }
        long lifetimeTicks = Math.max(1, (prepared.lifetime().toMillis() + 49L) / 50L);
        active.put(petId, new ActiveDisplay(
                petId, pet.getUUID(), display, world.getGameTime() + lifetimeTicks));
        return display;
    }

    public void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        active.values().removeIf(current -> {
            Display.TextDisplay display = current.display();
            if (display.isRemoved()) return true;
            ServerLevel world = (ServerLevel) display.level();
            Entity entity = world.getEntity(current.petEntityId());
            if (!(entity instanceof TamableAnimal pet)
                    || !(pet instanceof PetEntityData data)
                    || !data.aipets$isPet()
                    || !data.aipets$getPetId().equals(current.petId())
                    || world.getGameTime() >= current.expiresAtTick()) {
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

    public Optional<Display.TextDisplay> active(UUID petId) {
        ActiveDisplay found = active.get(petId);
        return found == null || found.display().isRemoved()
                ? Optional.empty() : Optional.of(found.display());
    }

    public int activeCount() { return active.size(); }

    private void position(Display.TextDisplay display, TamableAnimal pet) {
        display.snapTo(
                pet.getX(), pet.getY() + pet.getBbHeight() + policy.verticalGap(), pet.getZ(),
                0.0F, 0.0F);
    }

    private static void requirePet(TamableAnimal pet) {
        Objects.requireNonNull(pet, "pet");
        if (!(pet instanceof PetEntityData data) || !data.aipets$isPet()) {
            throw new IllegalArgumentException("Speech target is not a marked pet");
        }
    }

    private record ActiveDisplay(
            UUID petId, UUID petEntityId,
            Display.TextDisplay display, long expiresAtTick) { }
}
