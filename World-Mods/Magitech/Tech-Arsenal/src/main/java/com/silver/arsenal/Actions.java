package com.silver.arsenal;

/** Picks the behaviour named by a spec's {@code use.kind}. */
final class Actions {
    private Actions() {
    }

    static Action create(String kind) {
        return switch (kind) {
            case "hitscan" -> new Hitscan();
            case "bolt" -> new Bolt();
            case "stream" -> new Stream();
            case "slam" -> new Melee.Slam();
            case "lunge" -> new Melee.Lunge();
            case "lash" -> new Melee.Lash();
            case "rev" -> new Melee.Rev();
            case "grapple" -> new Gadgets.Grapple();
            case "stim" -> new Gadgets.Stim();
            case "barrier" -> new Gadgets.Barrier();
            case "mine" -> new Gadgets.Mine();
            case "turret" -> new Gadgets.Turret();
            default -> null;
        };
    }
}
