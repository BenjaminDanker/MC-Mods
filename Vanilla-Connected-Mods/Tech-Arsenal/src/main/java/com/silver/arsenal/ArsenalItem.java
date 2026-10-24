package com.silver.arsenal;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A weapon, gadget or round of ammunition. Vanilla clients see its base item with the resource pack's model on top. */
final class ArsenalItem extends SimplePolymerItem {
    final Spec spec;
    private final Action action;
    private final HitProc hitProc;
    private final boolean tempered;
    private final boolean consumed;

    ArsenalItem(Item.Properties properties, Item base, Spec spec, boolean tempered, Action action, HitProc hitProc) {
        super(properties, base, true);
        this.spec = spec;
        this.tempered = tempered;
        this.action = action;
        this.hitProc = hitProc;
        this.consumed = spec.use != null && Boolean.TRUE.equals(spec.use.get("consume"));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (action == null || !(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer shooter)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (Claims.blocked(shooter, new Params(spec.use).kind())) {
            shooter.sendSystemMessage(Component.literal("Weapons do not work on land claimed by someone else."), true);
            return InteractionResult.FAIL;
        }
        if (!Ammo.canAfford(shooter, spec, stack)) {
            return InteractionResult.FAIL;
        }
        Params params = new Params(spec.use);
        boolean fired = action.use(new Action.Ctx(serverLevel, shooter, stack, spec, params, tempered));
        if (!fired) {
            return InteractionResult.FAIL;
        }
        Ammo.spend(shooter, spec, stack);
        int cooldown = params.integer("cooldown", 0);
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(stack, cooldown);
        }
        if (consumed && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (hitProc != null && attacker instanceof ServerPlayer player && target.level() instanceof ServerLevel level) {
            hitProc.onHit(level, player, target, spec, tempered);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack);
    }
}
