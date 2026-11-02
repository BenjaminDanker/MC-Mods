package com.silver.arsenal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Keeps the arsenal in line with Bending. In bending.conf (already x2.5) a mage's best single ability does about 3.3-3.8 damage
 * a second at long range and a full rotation of four or five abilities 8-10, with shields and mobility on top. So one tech
 * weapon is held to 3-9 damage a second at its own best range, and nothing may out-burst a mage's biggest hit by much.
 */
class BalanceTest {
    private static final List<Spec> SPECS = Tuning.readDefaults();

    private static Spec spec(String id) {
        return SPECS.stream().filter(s -> s.id.equals(id)).findFirst().orElseThrow();
    }

    private static void within(String what, double value, double low, double high) {
        assertTrue(value >= low && value <= high, what + " = " + String.format("%.2f", value) + ", expected " + low + " to " + high);
    }

    private static double perSecond(Params use, double perUse) {
        return perUse / (use.num("cooldown", 20) / 20.0);
    }

    @Test
    void hitScanGunsSitInTheSameBandAsAMagesBestAbility() {
        for (Spec s : SPECS) {
            if (s.use == null || !"hitscan".equals(new Params(s.use).kind())) {
                continue;
            }
            Params use = new Params(s.use);
            double perUse = use.num("damage", 0) * use.integer("pellets", 1);
            double dps = perSecond(use, perUse);
            // a shotgun only delivers its full spread at point blank; the taser is a control tool, its worth is the slow and weakness
            double high = s.id.equals("scatter_cannon") ? 10.0 : s.id.equals("taser") ? 3.0 : 9.0;
            double low = s.id.equals("taser") ? 0.2 : 3.0;
            within(s.id + " damage per second", dps, low, high);
        }
    }

    @Test
    void projectileWeaponsDealAMagesWorthOfDamagePerSecond() {
        for (String id : List.of("grenade_launcher", "rocket_launcher", "flare_gun", "harpoon_gun")) {
            Params use = new Params(spec(id).use);
            Params blast = use.child("blast");
            double perUse = blast.has("damage") ? blast.num("damage", 0)
                : use.child("flare").has("damage") ? use.child("flare").num("damage", 0) + 3.0 // plus about 3 of burning
                : use.child("harpoon").num("damage", 0);
            // the harpoon is a control tool: it drags a target in, the damage is incidental
            double low = id.equals("harpoon_gun") ? 0.5 : 1.5;
            double high = id.equals("harpoon_gun") ? 4.0 : 9.0;
            within(id + " damage per second", perSecond(use, perUse), low, high);
        }
    }

    @Test
    void theFlameProjectorBurstsHardButTiresQuickly() {
        Params use = new Params(spec("flame_projector").use);
        double burst = use.num("damage", 0) * (20.0 / use.num("pulse", 1));
        double cycle = (use.num("duration", 0) + use.num("cooldown", 0)) / 20.0;
        double perCycle = use.num("damage", 0) * (use.num("duration", 0) / use.num("pulse", 1));
        within("flame burst damage per second", burst, 4.0, 9.5);
        within("flame average damage per second", perCycle / cycle, 1.5, 6.0);
    }

    @Test
    void meleeWeaponsOutDamageGunsInCloseButNotByAMile() {
        for (Spec s : SPECS) {
            if (s.melee == null) {
                continue;
            }
            Params melee = new Params(s.melee);
            double dps = melee.num("damage", 0) * melee.num("speed", 1);
            // the monowire whip fights with its lash (see its use), so its plain swing may be weak
            within(s.id + " melee damage per second", dps, s.id.equals("monowire_whip") ? 3.0 : 4.0, 12.0);
        }
    }

    @Test
    void nothingOneShotsAPlayerFromRange() {
        for (Spec s : SPECS) {
            if (s.use == null) {
                continue;
            }
            Params use = new Params(s.use);
            double biggest = Math.max(use.num("damage", 0), Math.max(use.child("blast").num("damage", 0), use.num("damage", 0) * use.integer("pellets", 1)));
            assertTrue(biggest <= 18.0, s.id + " hits for " + biggest + " in one go; a player has 20 health");
        }
    }

    @Test
    void temperingIsABonusNotADifferentGame() {
        assertTrue(Tuning.temperedDamage > 1.0 && Tuning.temperedDamage <= 1.5);
    }
}
