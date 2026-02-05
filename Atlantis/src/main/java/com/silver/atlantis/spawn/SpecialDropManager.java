package com.silver.atlantis.spawn;

import com.silver.atlantis.AtlantisMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class SpecialDropManager {

    private SpecialDropManager() {
    }

    public static int markRandomSpecialMobs(List<Entity> spawnedEntities, Random random) {
        int target = SpawnSpecialConfig.SPECIAL_MOBS_PER_RUN;
        if (target <= 0 || spawnedEntities == null || spawnedEntities.isEmpty()) {
            return 0;
        }

        List<LivingEntity> living = new ArrayList<>();
        for (Entity e : spawnedEntities) {
            if (e instanceof LivingEntity le) {
                living.add(le);
            }
        }

        if (living.isEmpty()) {
            return 0;
        }

        Collections.shuffle(living, random);
        int count = Math.min(target, living.size());
        for (int i = 0; i < count; i++) {
            living.get(i).addCommandTag(SpawnSpecialConfig.SPECIAL_MOB_TAG);
        }
        return count;
    }

    public static void tryDropIfSpecial(LivingEntity entity) {
        if (!(entity.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }

        if (!entity.getCommandTags().contains(SpawnSpecialConfig.SPECIAL_MOB_TAG)) {
            return;
        }

        entity.removeCommandTag(SpawnSpecialConfig.SPECIAL_MOB_TAG);

        ItemStack stack = createSpecialDropStack();
        if (stack.isEmpty()) {
            return;
        }

        ItemEntity item = new ItemEntity(world, entity.getX(), entity.getBodyY(0.6), entity.getZ(), stack);
        world.spawnEntity(item);
    }

    private static ItemStack createSpecialDropStack() {
        Identifier id = Identifier.tryParse(SpawnSpecialConfig.SPECIAL_DROP_ITEM_ID);
        if (id == null) {
            AtlantisMod.LOGGER.warn("Invalid special drop item id: {}", SpawnSpecialConfig.SPECIAL_DROP_ITEM_ID);
            return ItemStack.EMPTY;
        }

        Item item = Registries.ITEM.get(id);
        if (item == null) {
            AtlantisMod.LOGGER.warn("Unknown special drop item: {}", SpawnSpecialConfig.SPECIAL_DROP_ITEM_ID);
            return ItemStack.EMPTY;
        }

        int count = Math.max(1, SpawnSpecialConfig.SPECIAL_DROP_ITEM_COUNT);
        ItemStack stack = new ItemStack(item, count);

        String displayName = SpawnSpecialConfig.SPECIAL_DROP_DISPLAY_NAME;
        if (displayName != null && !displayName.isBlank()) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
        }

        String snbt = SpawnSpecialConfig.SPECIAL_DROP_CUSTOM_DATA_SNBT;
        if (snbt != null && !snbt.isBlank()) {
            try {
                NbtCompound tag = NbtHelper.fromNbtProviderString(snbt);
                if (tag != null && !tag.isEmpty()) {
                    stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
                }
            } catch (Exception e) {
                AtlantisMod.LOGGER.warn("Invalid special drop SNBT: {} ({})", snbt, e.getMessage());
            }
        }

        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, tag -> {
            tag.putString("id", "special_drop");
        });

        return stack;
    }
}
