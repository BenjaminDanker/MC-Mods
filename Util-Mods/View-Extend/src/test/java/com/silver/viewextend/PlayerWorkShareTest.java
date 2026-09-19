package com.silver.viewextend;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class PlayerWorkShareTest {
    @Test void twentyPlayersEarnIndependentSharesAndExpensiveClientsPayMore() {
        PlayerWorkShare[] players = new PlayerWorkShare[20];
        for(int i=0;i<20;i++) { players[i]=new PlayerWorkShare();players[i].accrue(50_000,100_000); }
        players[0].charge(60_000,10_000);
        players[1].charge(100,110_000);
        assertFalse(players[0].available()); assertFalse(players[1].available());
        for(int i=2;i<20;i++) assertTrue(players[i].available());
        players[0].accrue(50_000,100_000);assertTrue(players[0].available());
    }
    @Test void hugePacketDebtIsBoundedAndEventuallyRecovers() {
        var player=new PlayerWorkShare();player.accrue(50_000,100_000);
        player.charge(Long.MAX_VALUE,Long.MAX_VALUE);
        for(int i=0;i<30;i++) player.accrue(50_000,100_000);
        assertTrue(player.available());
    }
}
