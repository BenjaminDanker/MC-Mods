package com.silver.authorization;

import java.util.Objects;

/** GLOBAL applies on every server; SERVER applies only on its one canonical server ID. */
public record AuthorizationScope(Type type, ServerId serverId) {
    public AuthorizationScope {
        Objects.requireNonNull(type, "type");
        if ((type == Type.GLOBAL) != (serverId == null)) {
            throw new IllegalArgumentException("GLOBAL scope has no server ID; SERVER scope requires one");
        }
    }

    public static AuthorizationScope global() {
        return new AuthorizationScope(Type.GLOBAL, null);
    }

    public static AuthorizationScope server(ServerId serverId) {
        return new AuthorizationScope(Type.SERVER, Objects.requireNonNull(serverId, "serverId"));
    }

    public boolean appliesTo(ServerId server) {
        return type == Type.GLOBAL || serverId.equals(server);
    }

    /** Stable database key used by auth_scopes. */
    public String scopeKey() {
        return type == Type.GLOBAL ? "GLOBAL" : "SERVER:" + serverId.value();
    }

    public enum Type {
        GLOBAL,
        SERVER
    }
}
