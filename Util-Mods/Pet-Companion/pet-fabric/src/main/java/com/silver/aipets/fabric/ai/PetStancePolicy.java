package com.silver.aipets.fabric.ai;

import com.silver.aipets.common.domain.PetTraits;

/** Independent traits shape local attention; none is a friendship progression meter. */
public final class PetStancePolicy {
    private PetStancePolicy() { }
    public record Choice(PetIntent intent, PetIntentionState.Reason reason) { }

    public static Choice choose(PetTraits traits, int anxiety, boolean cat,
            double ownerDistanceSquared, double followStopSquared, int roll,
            boolean canSit, long sinceExplore, long sinceSit,
            PetIntent previous, long previousAge) {
        int curiosity = traits == null ? 50 : traits.curiosity();
        int independence = traits == null ? 50 : traits.independence();
        int attachment = traits == null ? 50 : traits.attachment();
        int security = traits == null ? 50 : traits.security();
        if (anxiety > 65 && security < 60)
            return new Choice(ownerDistanceSquared > 4 ? PetIntent.APPROACH_OWNER
                    : PetIntent.LOOK_AT_OWNER, PetIntentionState.Reason.STANCE);
        int exploreChance = Math.clamp(8 + curiosity / 4 + (cat ? 10 : 0)
                + independence / 10 - attachment / 10 - anxiety / 8, 5, 35);
        int approachChance = Math.clamp((cat ? 10 : 20)
                + attachment / 5 - independence / 8, 5, 30);
        Choice choice;
        if (roll < exploreChance && sinceExplore >= 900)
            choice = new Choice(PetIntent.EXPLORE_NEARBY, PetIntentionState.Reason.CURIOSITY);
        else if (roll < exploreChance + approachChance
                && ownerDistanceSquared > followStopSquared)
            choice = new Choice(PetIntent.APPROACH_OWNER, PetIntentionState.Reason.STANCE);
        else if (roll < exploreChance + approachChance + 25)
            choice = new Choice(PetIntent.LOOK_AT_OWNER, PetIntentionState.Reason.OWNER_NEAR);
        else if (roll < exploreChance + approachChance + 33 && canSit
                && sinceSit >= 1200)
            choice = new Choice(PetIntent.SIT, PetIntentionState.Reason.REST);
        else choice = new Choice(PetIntent.PAUSE, PetIntentionState.Reason.STANCE);
        if (choice.intent() == previous && previousAge < 400
                && (previous == PetIntent.SIT || previous == PetIntent.EXPLORE_NEARBY))
            return new Choice(PetIntent.LOOK_AT_OWNER,
                    PetIntentionState.Reason.OWNER_NEAR);
        return choice;
    }
}
