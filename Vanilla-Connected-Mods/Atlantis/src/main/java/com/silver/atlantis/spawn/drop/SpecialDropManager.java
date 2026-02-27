package com.silver.atlantis.spawn.drop;

import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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

public final class SpecialDropManager {

    private static boolean initialized;
    private static int tagLogCounter = 0;
    private static final int TAG_LOG_SAMPLE_RATE = 100; // Log approximately 1 in 100

    private SpecialDropManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> tryDropIfSpecial(entity));
        AtlantisMod.LOGGER.info("Atlantis special-drop death tracking enabled");
    }

    public static void markSpecialDropAmount(Entity entity, int amount) {
        if (entity == null || amount <= 0) {
            return;
        }

        String longTag = SpawnSpecialConfig.SPECIAL_DROP_AMOUNT_TAG_PREFIX + amount;
        boolean added = entity.addCommandTag(longTag);
        if (added) {
            if (SpawnSpecialConfig.SPECIAL_DROP_DEBUG_LOGS && shouldLogTagSample()) {
                AtlantisMod.LOGGER.info("[SpecialDropTag] entity={} amount={} tag={}", entity.getType(), amount, longTag);
            }
            return;
        }

        String compactTag = SpawnSpecialConfig.SPECIAL_DROP_AMOUNT_TAG_PREFIX_COMPACT + amount;
        if (entity.addCommandTag(compactTag)) {
            if (SpawnSpecialConfig.SPECIAL_DROP_DEBUG_LOGS && shouldLogTagSample()) {
                AtlantisMod.LOGGER.info("[SpecialDropTag] entity={} amount={} tag={} (fallback)", entity.getType(), amount, compactTag);
            }
            return;
        }

        AtlantisMod.LOGGER.warn("Failed to add special-drop amount tag to {} (amount={})", entity.getType(), amount);
    }

    private static boolean shouldLogTagSample() {
        tagLogCounter++;
        return (tagLogCounter % TAG_LOG_SAMPLE_RATE) == 0;
    }

    public static void tryDropIfSpecial(LivingEntity entity) {
        if (!(entity.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }

        int specialAmount = readSpecialDropAmount(entity);
        boolean atlantisSpawned = entity.getCommandTags().contains(SpawnSpecialConfig.ATLANTIS_SPAWNED_MOB_TAG);

        if (specialAmount <= 0) {
            return;
        }

        ItemStack stack = createSpecialDropStack(specialAmount);
        if (stack.isEmpty()) {
            return;
        }

        ItemEntity item = new ItemEntity(world, entity.getX(), entity.getBodyY(0.6), entity.getZ(), stack);
        world.spawnEntity(item);
        if (SpawnSpecialConfig.SPECIAL_DROP_DEBUG_LOGS) {
            AtlantisMod.LOGGER.info(
                "[SpecialDropDeath] entity={} atlantisSpawned={} hasAmountTag={} dropped={} ",
                entity.getType(),
                atlantisSpawned,
                true,
                specialAmount
            );
        }
    }

    private static int readSpecialDropAmount(LivingEntity entity) {
        int amount = 0;
        String longPrefix = SpawnSpecialConfig.SPECIAL_DROP_AMOUNT_TAG_PREFIX;
        String compactPrefix = SpawnSpecialConfig.SPECIAL_DROP_AMOUNT_TAG_PREFIX_COMPACT;
        for (String tag : entity.getCommandTags()) {
            amount = Math.max(amount, parseAmountTag(tag, longPrefix));
            amount = Math.max(amount, parseAmountTag(tag, compactPrefix));
        }
        return Math.max(0, amount);
    }

    private static int parseAmountTag(String tag, String prefix) {
        if (prefix == null || prefix.isBlank() || tag == null || !tag.startsWith(prefix)) {
            return 0;
        }

        String raw = tag.substring(prefix.length());
        try {
            int parsed = Integer.parseInt(raw);
            return Math.max(0, parsed);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static ItemStack createSpecialDropStack(int countOverride) {
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

        int count = Math.max(1, countOverride);
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
            tag.putBoolean("no_despawn", true);
        });

        return stack;
    }
}
