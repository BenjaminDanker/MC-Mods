package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class OwnerMemoryWriterTest {
    @Test void staleMemoryVersionDoesNotRetireMemoryOrWriteEvidence() throws Exception {
        var writes=new AtomicInteger();
        var proposal=new OwnerMemoryProposal(OwnerMemoryProposal.Kind.CORRECTION,"Birch, not oak.","Birch, not oak.",
                Optional.of(UUID.randomUUID()),Optional.empty(),OwnerMemoryProposal.Operation.NONE,1,0);
        OwnerMemoryWriter.write(missingVersion(writes),request(),proposal);
        assertEquals(0,writes.get());
    }

    @Test void staleOrWrongPetThreadDoesNotCompleteThreadOrRetireItsCards() throws Exception {
        var writes=new AtomicInteger();
        var proposal=new OwnerMemoryProposal(OwnerMemoryProposal.Kind.OPEN_THREAD,"Finished the bridge.","Finished the bridge.",
                Optional.empty(),Optional.of(UUID.randomUUID()),OwnerMemoryProposal.Operation.COMPLETE,0,1);
        OwnerMemoryWriter.write(missingVersion(writes),request(),proposal);
        assertEquals(0,writes.get());
    }

    /** Simulates a version/pet-qualified SELECT that finds no row. Any attempted mutation is counted. */
    private static Connection missingVersion(AtomicInteger writes) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,args)->{
            if(!m.getName().equals("prepareStatement")) return null;
            String sql=(String)args[0];
            assertTrue(sql.contains("pet_id=?") && sql.contains("version=?"));
            return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(q,n,values)->{
                if(n.getName().equals("executeUpdate")) { writes.incrementAndGet(); return 1; }
                if(n.getName().equals("executeQuery")) return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                        new Class<?>[]{ResultSet.class},(r,o,v)->o.getName().equals("next")?false:null);
                return null;
            });
        });
    }

    private static DialoguePersistRequest request() {
        Instant now=Instant.parse("2026-10-01T12:00:00Z"); UUID owner=UUID.randomUUID(),id=UUID.randomUUID();
        Pet pet=new Pet(id,owner,"Mica",PetAppearance.create(PetSpecies.CAT,"minecraft:tabby",.7,1L,AppearanceRules.defaults()),
                PetTraits.initial(50,50,50,50,50,now),PetMood.initial(50,10,10,10,now),HeldPlacement.INSTANCE,0,now,now);
        Map<TraitName,Integer> traits=new EnumMap<>(TraitName.class); for(var t:TraitName.values())traits.put(t,0);
        Map<MoodDimension,Integer> mood=new EnumMap<>(MoodDimension.class);for(var t:MoodDimension.values())mood.put(t,0);
        var output=new ValidatedDialogueOutput("Okay.",DialogueImportance.MEDIUM,Optional.empty(),traits,mood);
        var usage=new DialogueUsage(UUID.randomUUID(),UUID.randomUUID(),id,owner,"test",Optional.empty(),1,0,1,
                BigDecimal.ZERO,1,DialogueUsage.Status.SUCCEEDED,Optional.empty(),now);
        return new DialoguePersistRequest(UUID.randomUUID(),pet,"Finished the bridge.",output,usage,
                new DialogueGameContext("dev","minecraft:overworld","PLAYER_MESSAGE"),now);
    }
}
