package com.silver.prettychat.fabric.mixin;

import com.silver.prettychat.fabric.LocalChatFormatter;
import java.util.ArrayDeque;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
abstract class PlayerListChatMixin {
    @Unique
    private static final ThreadLocal<ArrayDeque<ChatContext>> PRETTY_CHAT_CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"))
    private void prettyChat$pushContext(PlayerChatMessage message,
                                       java.util.function.Predicate<ServerPlayer> filterPredicate,
                                       ServerPlayer sender, ChatType.Bound chatType, CallbackInfo ci) {
        PRETTY_CHAT_CONTEXT.get().push(new ChatContext(message, sender,
                chatType.chatType().is(ChatType.CHAT)));
    }

    @Inject(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("RETURN"))
    private void prettyChat$popContext(PlayerChatMessage message,
                                      java.util.function.Predicate<ServerPlayer> filterPredicate,
                                      ServerPlayer sender, ChatType.Bound chatType, CallbackInfo ci) {
        ArrayDeque<ChatContext> stack = PRETTY_CHAT_CONTEXT.get();
        stack.pop();
        if (stack.isEmpty()) PRETTY_CHAT_CONTEXT.remove();
    }

    @Redirect(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V"))
    private void prettyChat$formatLocalChat(ServerPlayer recipient, OutgoingChatMessage outgoing,
                                            boolean filtered, ChatType.Bound chatType) {
        ArrayDeque<ChatContext> stack = PRETTY_CHAT_CONTEXT.get();
        ChatContext context = stack.peek();
        if (context == null || context.sender() == null || !context.isPlayerChat()
                || recipient.getChatVisibility() != ChatVisiblity.FULL) {
            recipient.sendChatMessage(outgoing, filtered, chatType);
            return;
        }

        Component body = context.message().filter(filtered).decoratedContent();
        Component rendered = LocalChatFormatter.render(context.sender().getUUID(),
                context.sender().getGameProfile().name(), body);
        recipient.sendSystemMessage(rendered, false);
    }

    @Unique
    private record ChatContext(PlayerChatMessage message, ServerPlayer sender, boolean isPlayerChat) {}
}
