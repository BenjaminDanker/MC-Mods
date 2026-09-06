package com.silver.aipets.fabric.compass;

import com.silver.aipets.common.domain.Pet;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.GlobalPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Construction and strict parsing for the otherwise ordinary compass item. */
public final class PetCompassItem {
    private static final String ROOT_KEY = "pet_companion_compass";
    private static final String MARKER_KEY = "marker";
    private static final String OWNER_KEY = "owner_uuid";
    private static final String PET_KEY = "pet_uuid";
    private static final String SIGNATURE_KEY = "signature";
    private static final int MARKER_VERSION = 1;

    private PetCompassItem() {
    }

    public static ItemStack create(Pet pet, PetCompassSigner signer) {
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.literal(pet.name() + "'s Compass").formatted(Formatting.AQUA));

        NbtCompound root = new NbtCompound();
        root.putInt(MARKER_KEY, MARKER_VERSION);
        root.putString(OWNER_KEY, pet.ownerUuid().toString());
        root.putString(PET_KEY, pet.petId().toString());
        root.putString(SIGNATURE_KEY, signer.sign(pet.ownerUuid(), pet.petId()));
        NbtCompound custom = new NbtCompound();
        custom.put(ROOT_KEY, root);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(custom));
        return stack;
    }

    public static boolean isCandidate(ItemStack stack) {
        if (!stack.isOf(Items.COMPASS)) {
            return false;
        }
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        return custom != null && custom.copyNbt().contains(ROOT_KEY);
    }

    public static Optional<PetCompassMarker> trustedMarker(
            ItemStack stack,
            PetCompassSigner signer) {
        if (!isCandidate(stack)) {
            return Optional.empty();
        }
        try {
            NbtCompound custom = stack.get(DataComponentTypes.CUSTOM_DATA).copyNbt();
            NbtCompound root = custom.getCompound(ROOT_KEY).orElse(null);
            if (root == null || root.getInt(MARKER_KEY).orElse(-1) != MARKER_VERSION) {
                return Optional.empty();
            }
            UUID ownerUuid = UUID.fromString(root.getString(OWNER_KEY).orElse(""));
            UUID petId = UUID.fromString(root.getString(PET_KEY).orElse(""));
            String signature = root.getString(SIGNATURE_KEY).orElse("");
            return signer.verifies(ownerUuid, petId, signature)
                    ? Optional.of(new PetCompassMarker(ownerUuid, petId))
                    : Optional.empty();
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    public static void showStatus(ItemStack stack, String status, Optional<GlobalPos> target) {
        stack.set(
                DataComponentTypes.LORE,
                new LoreComponent(List.of(Text.literal(status).formatted(Formatting.GRAY))));
        if (target.isPresent()) {
            stack.set(
                    DataComponentTypes.LODESTONE_TRACKER,
                    new LodestoneTrackerComponent(target, false));
        } else {
            stack.remove(DataComponentTypes.LODESTONE_TRACKER);
        }
    }
}
