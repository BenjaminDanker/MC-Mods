package com.silver.skyislands.enderdragons;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DragonTargetSelectionTest {
    @Test void twentyPlayerEncounterIsIndependentOfJoinOrder() {
        var candidates=new ArrayList<DragonTargetSelection.Candidate>();
        for(int i=0;i<20;i++) candidates.add(new DragonTargetSelection.Candidate(new UUID(0,i),40,0));
        UUID selected=new UUID(0,8);
        for(int i=0;i<20;i++) {
            Collections.rotate(candidates,1);
            assertEquals(selected,DragonTargetSelection.choose(candidates,selected,100));
        }
        assertEquals(new UUID(0,0),DragonTargetSelection.choose(candidates,null,100));
    }
    @Test void freshRangedAttackerCanTakeAttentionWithoutTinyDistanceChangesThrashingIt() {
        UUID first=new UUID(0,1), second=new UUID(0,2);
        assertEquals(first,DragonTargetSelection.choose(List.of(new DragonTargetSelection.Candidate(first,40,100),
                new DragonTargetSelection.Candidate(second,35,100)),first,100));
        assertEquals(second,DragonTargetSelection.choose(List.of(new DragonTargetSelection.Candidate(first,40,0),
                new DragonTargetSelection.Candidate(second,35,200)),first,200));
    }
    @Test void invalidOriginalTargetDoesNotEndRemainingPlayersFight() {
        UUID original=new UUID(0,1), remaining=new UUID(0,2);
        assertEquals(remaining,DragonTargetSelection.choose(List.of(new DragonTargetSelection.Candidate(remaining,50,200)),original,220));
        assertNull(DragonTargetSelection.choose(List.of(),original,220));
    }
}
