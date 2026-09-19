package com.silver.skyislands.enderdragons;

import java.util.List;
import java.util.UUID;

/** Selection over already validated participants; stable ties do not depend on player-list order. */
public final class DragonTargetSelection {
    public record Candidate(UUID id,double distance,long lastHit) {}
    private DragonTargetSelection() {}
    public static UUID choose(List<Candidate> candidates,UUID current,long now) {
        UUID best=null;
        double bestScore=-Double.MAX_VALUE;
        for (Candidate candidate:candidates) {
            double score=-candidate.distance()+(candidate.id().equals(current)?20:0)
                    +Math.max(0,50-Math.max(0,now-candidate.lastHit())*.25);
            if (score>bestScore || (score==bestScore && (best==null || candidate.id().compareTo(best)<0))) {
                best=candidate.id(); bestScore=score;
            }
        }
        return best;
    }
}
