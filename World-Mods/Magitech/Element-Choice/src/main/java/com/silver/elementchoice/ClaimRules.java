package com.silver.elementchoice;

import java.util.UUID;

/** Where bending is allowed: unclaimed land and the player's own claims. Server claims (spawn) and everyone else's are off limits. */
final class ClaimRules {
    private ClaimRules() {
    }

    /** {@code owner} is the claim's owner, or null when the spot is unclaimed. Server and expired claims have their own special owners. */
    static boolean forbids(UUID owner, UUID player) {
        return owner != null && !owner.equals(player);
    }
}
