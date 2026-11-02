package com.silver.prettychat.fabric;

import com.silver.prettychat.common.PlayerColors;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.Style;

public final class LocalChatFormatter {
    private LocalChatFormatter() {}

    public static Component render(UUID playerId, String username, Component message) {
        int nameColor = PlayerColors.nameColor(playerId);
        int dimColor = PlayerColors.messageColor(playerId);
        return Component.literal(username).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor)))
                .append(Component.literal(" › ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(dimColor))))
                .append(message.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(dimColor))));
    }
}
