package com.silver.aipets.fabric.permission;

import com.silver.authorization.PermissionNode;
import com.silver.authorization.PermissionNodes;

/** Typed Pet Companion capabilities shared with the network authorization API. */
public enum PetPermission {
    USE(PermissionNodes.AIPETS_USE),
    ADOPT(PermissionNodes.AIPETS_ADOPT),
    CHAT(PermissionNodes.AIPETS_CHAT),
    COMPASS(PermissionNodes.AIPETS_COMPASS),
    RECALL(PermissionNodes.AIPETS_RECALL),
    ADMIN_INSPECT(PermissionNodes.AIPETS_ADMIN_INSPECT),
    ADMIN_RECOVER(PermissionNodes.AIPETS_ADMIN_RECOVER),
    ADMIN_SUBSCRIPTION(PermissionNodes.AIPETS_ADMIN_SUBSCRIPTION),
    ADMIN_MEMORY(PermissionNodes.AIPETS_ADMIN_MEMORY),
    ADMIN_RECONCILE(PermissionNodes.AIPETS_ADMIN_RECONCILE);

    private final PermissionNode node;

    PetPermission(PermissionNode node) { this.node = node; }

    public PermissionNode node() { return node; }
}
