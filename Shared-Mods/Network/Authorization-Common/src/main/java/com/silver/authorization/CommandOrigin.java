package com.silver.authorization;

/** Namespace of a command tree, preventing identical literals from sharing policy accidentally. */
public enum CommandOrigin {
    FABRIC_BACKEND,
    VELOCITY_PROXY
}
