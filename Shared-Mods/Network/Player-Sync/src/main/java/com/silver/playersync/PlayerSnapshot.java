package com.silver.playersync;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.silver.playersync.mixin.FoodDataAccessor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * What follows a player from server to server: health, air, food, XP, effects and their soulbound items (taken off
 * the player here and given back on the next server). On the family servers (Magic, Tech, Magitech) everything else
 * they carry is copied too, so their whole kit follows them between those three; elsewhere it stays per server.
 *
 * <p>The field names match the player table's columns and the queue files on disk.
 */
final class PlayerSnapshot {
    private static final Gson GSON = new Gson();
    /** Armor slots in the order the table stores them (0 = feet ... 3 = head). */
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final int MAIN_SLOTS = 36;

    String uuid;
    String name;
    int air;
    float health;
    float exhaustion;
    int foodLevel;
    float saturationLevel;
    int foodTickTimer;
    int experienceLevel;
    float experienceProgress;
    int selectedSlot;
    /** Soulbound items: inventory, offhand, armor, ender chest. */
    String main = "";
    String off = "";
    String armor = "";
    String enderChestInventory = "";
    String effects = "";
    /** Everything else, on family servers only (null elsewhere: the stored family copy is then left alone). */
    String famMain;
    String famOff;
    String famArmor;
    String famEnder;

    /** What the table holds for a player. {@code hasStats} is false for a row no server has saved yet. */
    record Saved(boolean hasStats, int air, float health, float exhaustion, int foodLevel, float saturationLevel, int foodTickTimer,
            int experienceLevel, float experienceProgress, int selectedSlot, String main, String off, String armor,
            String enderChestInventory, String effects, String famMain, String famOff, String famArmor, String famEnder) {
        static Saved read(ResultSet row) throws SQLException {
            return new Saved(row.getObject("Health") != null, row.getInt("Air"), row.getFloat("Health"), row.getFloat("exhaustion"),
                row.getInt("foodLevel"), row.getFloat("saturationLevel"), row.getInt("foodTickTimer"), row.getInt("experienceLevel"),
                row.getFloat("experienceProgress"), row.getInt("selectedSlot"), text(row.getString("main")), text(row.getString("off")),
                text(row.getString("armor")), text(row.getString("enderChestInventory")), text(row.getString("effects")),
                row.getString("fmain"), row.getString("foff"), row.getString("farmor"), row.getString("fender"));
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    /** Takes the player's state as they leave: soulbound items and effects are removed from them here. */
    static PlayerSnapshot capture(ServerPlayer player) {
        PlayerSnapshot s = new PlayerSnapshot();
        s.uuid = player.getStringUUID();
        s.name = player.getName().getString();
        s.air = player.getAirSupply();
        s.health = player.getHealth();
        FoodDataAccessor food = (FoodDataAccessor) player.getFoodData();
        s.exhaustion = food.playersync$getExhaustion();
        s.foodLevel = player.getFoodData().getFoodLevel();
        s.saturationLevel = player.getFoodData().getSaturationLevel();
        s.foodTickTimer = food.playersync$getTickTimer();
        s.experienceLevel = player.experienceLevel;
        s.experienceProgress = player.experienceProgress;
        s.selectedSlot = player.getInventory().getSelectedSlot();

        Container ender = player.getEnderChestInventory();
        s.enderChestInventory = takeSoulbound(ender, ender.getContainerSize());
        if (PlayerSyncMod.familyServer()) {
            // the family copy is taken before the soulbound items leave, and stays on this server too
            s.famMain = ItemCodec.encodeSlots(player.getInventory(), MAIN_SLOTS, stack -> !ItemCodec.soulbound(stack));
            ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
            s.famOff = !offhand.isEmpty() && !ItemCodec.soulbound(offhand) ? ItemCodec.encode(offhand) : "";
            StringBuilder worn = new StringBuilder();
            for (int i = 0; i < ARMOR.length; i++) {
                ItemStack stack = player.getItemBySlot(ARMOR[i]);
                if (!stack.isEmpty() && !ItemCodec.soulbound(stack)) {
                    ItemCodec.append(worn, stack, i);
                }
            }
            s.famArmor = worn.toString();
            s.famEnder = ItemCodec.encodeSlots(ender, ender.getContainerSize(), stack -> !ItemCodec.soulbound(stack));
        }

        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (ItemCodec.soulbound(offhand)) {
            s.off = ItemCodec.encode(offhand);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
        s.main = takeSoulbound(player.getInventory(), MAIN_SLOTS);
        StringBuilder worn = new StringBuilder();
        for (int i = 0; i < ARMOR.length; i++) {
            ItemStack stack = player.getItemBySlot(ARMOR[i]);
            if (ItemCodec.soulbound(stack)) {
                ItemCodec.append(worn, stack, i);
                player.setItemSlot(ARMOR[i], ItemStack.EMPTY);
            }
        }
        s.armor = worn.toString();

        StringBuilder active = new StringBuilder();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            active.append(MobEffectInstance.CODEC.encodeStart(PlayerSyncMod.ops(), effect)
                .resultOrPartial(PlayerSyncMod.LOGGER::error).orElseThrow()).append('&');
        }
        s.effects = active.toString();
        player.removeAllEffects();
        return s;
    }

    /** Encodes and removes the soulbound items in the first {@code count} slots. */
    private static String takeSoulbound(Container container, int count) {
        StringBuilder out = new StringBuilder();
        for (int slot = 0; slot < count; slot++) {
            ItemStack stack = container.getItem(slot);
            if (ItemCodec.soulbound(stack)) {
                ItemCodec.append(out, stack, slot);
                container.setItem(slot, ItemStack.EMPTY);
            }
        }
        return out.toString();
    }

    /** Gives a joining player their saved state. Throws (and changes nothing more) if a stored item can't be read. */
    static void apply(ServerPlayer player, Saved saved) {
        if (saved.hasStats()) { // a row nobody saved yet has empty stats (health 0 would kill)
            player.setAirSupply(saved.air());
            player.setHealth(saved.health());
            FoodDataAccessor food = (FoodDataAccessor) player.getFoodData();
            food.playersync$setExhaustion(saved.exhaustion());
            player.getFoodData().setFoodLevel(saved.foodLevel());
            player.getFoodData().setSaturation(saved.saturationLevel());
            food.playersync$setTickTimer(saved.foodTickTimer());
            player.setExperienceLevels(saved.experienceLevel());
            player.experienceProgress = saved.experienceProgress();
        }

        if (PlayerSyncMod.familyServer() && saved.famMain() != null) {
            applyFamily(player, saved);
        }

        Container ender = player.getEnderChestInventory();
        remove(ender, ender.getContainerSize(), ItemCodec::soulbound);
        for (ItemCodec.Placed item : ItemCodec.decodeList(saved.enderChestInventory())) {
            if (ItemCodec.soulbound(item.stack())) {
                place(player, ender, item.slot(), item.stack());
            }
        }

        remove(player.getInventory(), MAIN_SLOTS, ItemCodec::soulbound);
        if (ItemCodec.soulbound(player.getItemInHand(InteractionHand.OFF_HAND))) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
        for (EquipmentSlot slot : ARMOR) {
            if (ItemCodec.soulbound(player.getItemBySlot(slot))) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        ItemStack offhand = ItemCodec.decodeStrict(saved.off());
        if (ItemCodec.soulbound(offhand)) {
            holdInOffhand(player, offhand);
        }
        player.getInventory().setSelectedSlot(saved.selectedSlot());
        player.connection.send(new ClientboundSetHeldSlotPacket(saved.selectedSlot()));
        for (ItemCodec.Placed item : ItemCodec.decodeList(saved.main())) {
            if (ItemCodec.soulbound(item.stack())) {
                place(player, player.getInventory(), item.slot(), item.stack());
            }
        }
        for (ItemCodec.Placed item : ItemCodec.decodeList(saved.armor())) {
            if (ItemCodec.soulbound(item.stack()) && item.slot() >= 0 && item.slot() < ARMOR.length) {
                wear(player, ARMOR[item.slot()], item.stack());
            }
        }

        for (String effect : saved.effects().split("&")) {
            if (!effect.isEmpty()) {
                player.addEffect(MobEffectInstance.CODEC.parse(PlayerSyncMod.ops(), JsonParser.parseString(effect))
                    .resultOrPartial(PlayerSyncMod.LOGGER::error).orElseThrow());
            }
        }
    }

    /** Family servers: this server's non-soulbound items are replaced by the copy from the last family server. */
    private static void applyFamily(ServerPlayer player, Saved saved) {
        java.util.function.Predicate<ItemStack> everyday = stack -> !stack.isEmpty() && !ItemCodec.soulbound(stack);
        remove(player.getInventory(), MAIN_SLOTS, everyday);
        for (EquipmentSlot slot : ARMOR) {
            if (everyday.test(player.getItemBySlot(slot))) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        if (everyday.test(player.getItemInHand(InteractionHand.OFF_HAND))) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
        for (ItemCodec.Placed item : ItemCodec.decodeList(saved.famMain())) {
            place(player, player.getInventory(), item.slot(), item.stack());
        }
        for (ItemCodec.Placed item : ItemCodec.decodeList(text(saved.famArmor()))) {
            if (item.slot() >= 0 && item.slot() < ARMOR.length) {
                wear(player, ARMOR[item.slot()], item.stack());
            }
        }
        ItemStack offhand = ItemCodec.decodeStrict(text(saved.famOff()));
        if (!offhand.isEmpty()) {
            holdInOffhand(player, offhand);
        }
        Container ender = player.getEnderChestInventory();
        remove(ender, ender.getContainerSize(), everyday);
        for (ItemCodec.Placed item : ItemCodec.decodeList(text(saved.famEnder()))) {
            place(player, ender, item.slot(), item.stack());
        }
    }

    private static void remove(Container container, int count, java.util.function.Predicate<ItemStack> which) {
        for (int slot = 0; slot < Math.min(count, container.getContainerSize()); slot++) {
            if (which.test(container.getItem(slot))) {
                container.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    /** Puts an item back in its old slot if free, else the first free slot, else the player's inventory. */
    private static void place(ServerPlayer player, Container container, int slot, ItemStack stack) {
        boolean inventory = container == player.getInventory();
        int size = inventory ? Math.min(MAIN_SLOTS, container.getContainerSize()) : container.getContainerSize();
        if (slot >= 0 && slot < size && container.getItem(slot).isEmpty()) {
            container.setItem(slot, stack);
            return;
        }
        for (int i = 0; i < size; i++) {
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, stack);
                return;
            }
        }
        if (!inventory && player.getInventory().add(stack)) {
            return;
        }
        PlayerSyncMod.LOGGER.warn("No room to give {} back to {}", stack, player.getName().getString());
    }

    /** Wears {@code stack}, moving what was worn there into the inventory (or ender chest); if that is full, it goes in the inventory instead. */
    private static void wear(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        ItemStack worn = player.getItemBySlot(slot);
        if (worn.isEmpty() || moveAway(player, worn)) {
            player.setItemSlot(slot, stack);
        } else if (!player.getInventory().add(stack)) {
            PlayerSyncMod.LOGGER.warn("No room to give {} back to {}", stack, player.getName().getString());
        }
    }

    private static void holdInOffhand(ServerPlayer player, ItemStack stack) {
        ItemStack held = player.getItemInHand(InteractionHand.OFF_HAND);
        if (held.isEmpty() || moveAway(player, held)) {
            player.setItemInHand(InteractionHand.OFF_HAND, stack);
        } else if (!player.getInventory().add(stack)) {
            PlayerSyncMod.LOGGER.warn("No room to give {} back to {}", stack, player.getName().getString());
        }
    }

    /** Moves a copy of {@code stack} into the inventory or ender chest; true when it fit. */
    private static boolean moveAway(ServerPlayer player, ItemStack stack) {
        ItemStack copy = stack.copy();
        return player.getInventory().add(copy) || player.getEnderChestInventory().addItem(copy).isEmpty();
    }

    /** Writes this snapshot to the table and frees the row (sync = 'true'). */
    void upsert(Connection c) throws SQLException {
        boolean family = famMain != null;
        String columns = "Name, uuid, Air, Health, enderChestInventory, exhaustion, foodLevel, saturationLevel, foodTickTimer, main, off,"
            + " armor, selectedSlot, experienceLevel, experienceProgress, effects" + (family ? ", fmain, foff, farmor, fender" : "");
        String[] updated = columns.replace("Name, uuid, ", "").split(", ");
        StringBuilder update = new StringBuilder();
        for (String column : updated) {
            update.append(column).append(" = VALUES(").append(column).append("), ");
        }
        String marks = "?, ".repeat(columns.split(", ").length);
        try (PreparedStatement s = c.prepareStatement("INSERT INTO " + SyncStore.TABLE + " (" + columns + ", sync) VALUES (" + marks
                + "'true') ON DUPLICATE KEY UPDATE " + update + "sync = VALUES(sync)")) {
            int i = 1;
            s.setString(i++, name);
            s.setString(i++, uuid);
            s.setInt(i++, air);
            s.setFloat(i++, health);
            s.setString(i++, enderChestInventory);
            s.setFloat(i++, exhaustion);
            s.setInt(i++, foodLevel);
            s.setFloat(i++, saturationLevel);
            s.setInt(i++, foodTickTimer);
            s.setString(i++, main);
            s.setString(i++, off);
            s.setString(i++, armor);
            s.setInt(i++, selectedSlot);
            s.setInt(i++, experienceLevel);
            s.setFloat(i++, experienceProgress);
            s.setString(i++, effects);
            if (family) {
                s.setString(i++, famMain);
                s.setString(i++, famOff);
                s.setString(i++, famArmor);
                s.setString(i++, famEnder);
            }
            s.executeUpdate();
        }
    }

    String toJson() {
        return GSON.toJson(this);
    }

    static PlayerSnapshot fromJson(String json) {
        PlayerSnapshot snapshot = GSON.fromJson(json, PlayerSnapshot.class);
        if (snapshot == null || snapshot.uuid == null || snapshot.name == null) {
            throw new IllegalArgumentException("not a player save");
        }
        return snapshot;
    }
}
