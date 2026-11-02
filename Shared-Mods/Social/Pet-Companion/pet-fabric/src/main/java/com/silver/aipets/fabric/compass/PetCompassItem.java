package com.silver.aipets.fabric.compass;

import com.silver.aipets.common.domain.Pet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;

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
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.literal(pet.name() + "'s Compass").withStyle(ChatFormatting.AQUA));

        CompoundTag root = new CompoundTag();
        root.putInt(MARKER_KEY, MARKER_VERSION);
        root.putString(OWNER_KEY, pet.ownerUuid().toString());
        root.putString(PET_KEY, pet.petId().toString());
        root.putString(SIGNATURE_KEY, signer.sign(pet.ownerUuid(), pet.petId()));
        CompoundTag custom = new CompoundTag();
        custom.put(ROOT_KEY, root);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        return stack;
    }

    public static boolean isCandidate(ItemStack stack) {
        if (!stack.is(Items.COMPASS)) {
            return false;
        }
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom != null && custom.copyTag().contains(ROOT_KEY);
    }

    public static Optional<PetCompassMarker> trustedMarker(
            ItemStack stack,
            PetCompassSigner signer) {
        if (!isCandidate(stack)) {
            return Optional.empty();
        }
        try {
            CompoundTag custom = stack.get(DataComponents.CUSTOM_DATA).copyTag();
            CompoundTag root = custom.getCompound(ROOT_KEY).orElse(null);
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
                DataComponents.LORE,
                new ItemLore(List.of(Component.literal(status).withStyle(ChatFormatting.GRAY))));
        if (target.isPresent()) {
            stack.set(
                    DataComponents.LODESTONE_TRACKER,
                    new LodestoneTracker(target, false));
        } else {
            stack.remove(DataComponents.LODESTONE_TRACKER);
        }
    }
}
