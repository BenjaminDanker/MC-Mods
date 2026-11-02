package com.silver.arcade.door;

import static org.junit.jupiter.api.Assertions.*;

import com.silver.arcade.ArcadeConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class RewardsTest {
    private static ArcadeConfig.RewardBand band(int min, int rolls) {
        ArcadeConfig.RewardBand b = new ArcadeConfig.RewardBand();
        b.minTier = min;
        b.rolls = rolls;
        return b;
    }

    private static ArcadeConfig.RewardItem item(String id, int w) {
        ArcadeConfig.RewardItem i = new ArcadeConfig.RewardItem();
        i.item = id;
        i.weight = w;
        return i;
    }

    @Test void bandFor() {
        ArcadeConfig.RewardBand a = band(1, 1), b = band(5, 1), c = band(10, 1);
        List<ArcadeConfig.RewardBand> l = List.of(c, a, b);
        assertNull(Rewards.bandFor(l, 0));
        assertNull(Rewards.bandFor(new ArrayList<>(), 3));
        assertSame(a, Rewards.bandFor(l, 4));
        assertSame(b, Rewards.bandFor(l, 5));
        assertSame(b, Rewards.bandFor(l, 9));
        assertSame(c, Rewards.bandFor(l, 50));
    }

    @Test void draws() {
        assertEquals(2, Rewards.draws(band(0, 2), 4));
        assertEquals(4, Rewards.draws(band(0, 2), 10));
        assertEquals(1, Rewards.draws(band(0, 0), 3));
        assertEquals(1, Rewards.draws(band(0, -5), 0));
    }

    @Test void pickWeights() {
        List<ArcadeConfig.RewardItem> pool = List.of(item("a", 1), item("b", 3), item("z", 0), item("n", -4));
        RandomSource r = RandomSource.create(1234L);
        int a = 0, b = 0;
        for (int i = 0; i < 20000; i++) {
            String id = Rewards.pick(pool, r).item;
            if (id.equals("a")) a++; else if (id.equals("b")) b++; else fail("picked " + id);
        }
        assertEquals(0.25, a / 20000.0, 0.02);
        assertEquals(0.75, b / 20000.0, 0.02);
    }

    @Test void pickEmpty() {
        assertNull(Rewards.pick(List.of(), RandomSource.create(1L)));
        assertNull(Rewards.pick(List.of(item("a", 0)), RandomSource.create(1L)));
    }
}
