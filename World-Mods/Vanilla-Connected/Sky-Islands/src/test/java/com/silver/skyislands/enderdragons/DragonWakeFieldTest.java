package com.silver.skyislands.enderdragons;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DragonWakeFieldTest {
    private static final UUID OWNER=new UUID(0,1);
    @Test void attackDraftStaysForThirtySecondsAndFadesOnlyAtEnd() {
        var field=new DragonWakeField();
        assertTrue(field.emit(OWNER,DragonDraft.updraft(Vec3.ZERO),0));
        double initial=field.sample(Vec3.ZERO,0,s -> true).y;
        assertEquals(initial,field.sample(Vec3.ZERO,400,s -> true).y,1e-9);
        assertEquals(initial*.5,field.sample(Vec3.ZERO,500,s -> true).y,1e-9);
        field.expire(600);
        assertEquals(0,field.size());
    }
    @Test void swoopSegmentsRequireTimeAndDistanceWhileOtherAttackMayReplaceThem() {
        var field=new DragonWakeField();
        assertTrue(field.emit(OWNER,DragonDraft.updraft(Vec3.ZERO),0));
        assertFalse(field.emit(OWNER,DragonDraft.updraft(new Vec3(20,0,0)),5));
        assertFalse(field.emit(OWNER,DragonDraft.updraft(new Vec3(5,0,0)),6));
        assertTrue(field.emit(OWNER,DragonDraft.updraft(new Vec3(10,0,0)),6));
        assertTrue(field.emit(OWNER,DragonDraft.downdraft(Vec3.ZERO),7));
        assertTrue(field.sample(Vec3.ZERO,7,s -> true).y<0);
    }
    @Test void manyDragonsCannotStackForceOrExceedWorldCap() {
        var field=new DragonWakeField();
        for(int tick=0;tick<600;tick+=6) for(int dragon=0;dragon<20;dragon++)
            field.emit(new UUID(0,dragon),DragonDraft.updraft(new Vec3(tick*2,0,dragon*20)),tick);
        assertEquals(DragonWakeField.MAX_SEGMENTS,field.size());
        Vec3 point=new Vec3(1188,0,380);
        assertTrue(field.sample(point,594,s -> true).y<=.24);
        assertEquals(Vec3.ZERO,field.sample(point,594,s -> false));
        assertTrue(field.nearest(point,594).size()<=6);
    }
}
