package com.silver.prettychat.velocity;

import com.silver.prettychat.api.ChatKind;
import com.silver.prettychat.api.PrettyChatRenderer;
import com.silver.prettychat.common.PlayerColors;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

final class PrettyChatFormatter implements PrettyChatRenderer {
    private static final TextColor AMETHYST = TextColor.color(0xC084FC);

    @Override
    public Component render(UUID playerId, String username, String message, ChatKind kind) {
        TextColor name = TextColor.color(PlayerColors.nameColor(playerId));
        TextColor dim = TextColor.color(PlayerColors.messageColor(playerId));
        Component rendered = Component.empty();
        if (kind == ChatKind.RESONANT) rendered = rendered.append(Component.text("✧ ", AMETHYST));
        return rendered.append(Component.text(username, name))
                .append(Component.text(" › ", dim))
                .append(Component.text(message, dim));
    }
}
