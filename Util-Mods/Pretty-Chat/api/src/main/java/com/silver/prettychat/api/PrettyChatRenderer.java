package com.silver.prettychat.api;

import java.util.UUID;
import net.kyori.adventure.text.Component;

/** Velocity-side rendering API for messages delivered by server-side features. */
public interface PrettyChatRenderer {
    Component render(UUID playerId, String username, String message, ChatKind kind);
}
