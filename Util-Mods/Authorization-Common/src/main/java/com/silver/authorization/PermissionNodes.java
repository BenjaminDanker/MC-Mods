package com.silver.authorization;

/** Typed identifiers already used by first-party code; role membership remains data-driven. */
public final class PermissionNodes {
    public static final PermissionNode WAKEUPLOBBY_SERVER = PermissionNode.of("wakeuplobby.server");
    public static final PermissionNode WAKEUPLOBBY_QUEUE_BIGVIP = PermissionNode.of("wakeuplobby.queue.bigvip");
    public static final PermissionNode WAKEUPLOBBY_QUEUE_VIP = PermissionNode.of("wakeuplobby.queue.vip");
    public static final PermissionNode WAKEUPLOBBY_SELECTORS = PermissionNode.of("wakeuplobby.selectors");

    public static final PermissionNode AIPETS_USE = PermissionNode.of("aipets.use");
    public static final PermissionNode AIPETS_ADOPT = PermissionNode.of("aipets.adopt");
    public static final PermissionNode AIPETS_CHAT = PermissionNode.of("aipets.chat");
    public static final PermissionNode AIPETS_COMPASS = PermissionNode.of("aipets.compass");
    public static final PermissionNode AIPETS_RECALL = PermissionNode.of("aipets.recall");
    public static final PermissionNode AIPETS_ADMIN_INSPECT = PermissionNode.of("aipets.admin.inspect");
    public static final PermissionNode AIPETS_ADMIN_RECOVER = PermissionNode.of("aipets.admin.recover");
    public static final PermissionNode AIPETS_ADMIN_SUBSCRIPTION = PermissionNode.of("aipets.admin.subscription");
    public static final PermissionNode AIPETS_ADMIN_MEMORY = PermissionNode.of("aipets.admin.memory");
    public static final PermissionNode AIPETS_ADMIN_RECONCILE = PermissionNode.of("aipets.admin.reconcile");

    public static final PermissionNode STAFF_ROLES_MODIFY = PermissionNode.of("staff.roles.modify");
    public static final PermissionNode STAFF_PERMISSIONS_MODIFY = PermissionNode.of("staff.permissions.modify");

    public static final PermissionNode WAKEUPLOBBY_MANAGE = PermissionNode.of("wakeuplobby.manage");
    public static final PermissionNode SERVER_SWITCH_FORCE = PermissionNode.of("server.switch.force");
    public static final PermissionNode ADMISSION_MANAGE = PermissionNode.of("admission.manage");
    public static final PermissionNode PORTAL_USE = PermissionNode.of("portal.use");
    public static final PermissionNode PORTAL_REGISTRY_VIEW = PermissionNode.of("portal.registry.view");
    public static final PermissionNode PORTAL_REGISTRY_MANAGE = PermissionNode.of("portal.registry.manage");
    public static final PermissionNode PORTAL_RELOAD = PermissionNode.of("portal.reload");
    public static final PermissionNode MPDS_SKIP_MANAGE = PermissionNode.of("mpds.skip.manage");
    public static final PermissionNode MPDS_SKIP_INSPECT = PermissionNode.of("mpds.skip.inspect");
    public static final PermissionNode MPDS_PROGRESSION_INSPECT = PermissionNode.of("mpds.progression.inspect");
    public static final PermissionNode MPDS_PROGRESSION_MODIFY = PermissionNode.of("mpds.progression.modify");
    public static final PermissionNode MPDS_REWARD_GRANT = PermissionNode.of("mpds.reward.grant");
    public static final PermissionNode MPDS_INVENTORY_METADATA_MODIFY = PermissionNode.of("mpds.inventory.metadata.modify");
    public static final PermissionNode MPDS_INVENTORY_METADATA_INSPECT = PermissionNode.of("mpds.inventory.metadata.inspect");
    public static final PermissionNode SPAWNPROTECT_BYPASS = PermissionNode.of("spawnprotect.bypass");
    public static final PermissionNode DIMENSIONS_MANAGE = PermissionNode.of("dimensions.manage");
    public static final PermissionNode DIMENSIONS_BYPASS = PermissionNode.of("dimensions.bypass");
    public static final PermissionNode ATLANTIS_MANAGE = PermissionNode.of("atlantis.manage");
    public static final PermissionNode ATLANTIS_PROTECTION_BYPASS = PermissionNode.of("atlantis.protection.bypass");
    public static final PermissionNode ATLANTIS_HEIGHTCAP_BYPASS = PermissionNode.of("atlantis.heightcap.bypass");
    public static final PermissionNode SKYISLANDS_INSPECT = PermissionNode.of("skyislands.inspect");
    public static final PermissionNode ENDERFIGHT_RESET = PermissionNode.of("enderfight.reset");
    public static final PermissionNode VILLAGERINTERFACE_DEVTEST = PermissionNode.of("villagerinterface.devtest");
    public static final PermissionNode BORDER_BYPASS = PermissionNode.of("border.bypass");

    private PermissionNodes() {
    }

    /** Returns the typed first-party permission registry without a second maintained name list. */
    public static java.util.Set<PermissionNode> all() {
        java.util.Set<PermissionNode> nodes = new java.util.TreeSet<>(java.util.Comparator.comparing(PermissionNode::value));
        for (java.lang.reflect.Field field : PermissionNodes.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || !PermissionNode.class.isAssignableFrom(field.getType())) continue;
            try {
                nodes.add((PermissionNode) field.get(null));
            } catch (IllegalAccessException impossible) {
                throw new IllegalStateException("Could not read typed permission registry field " + field.getName(), impossible);
            }
        }
        return java.util.Set.copyOf(nodes);
    }
}
