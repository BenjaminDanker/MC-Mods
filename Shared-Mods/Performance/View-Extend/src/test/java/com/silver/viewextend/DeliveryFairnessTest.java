package com.silver.viewextend;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryFairnessTest {
    @Test void blockedSubscriberDoesNotBlockOtherPlayersSharingTheChunk() {
        var slow = new ViewExtendService.LoadSubscriber(UUID.randomUUID(), null, 1);
        var fast = new ViewExtendService.LoadSubscriber(UUID.randomUUID(), null, 1);
        var delivery = new ViewExtendService.ReadyDelivery(null, null, List.of(slow, fast));
        assertSame(slow, delivery.nextSubscriber());
        delivery.defer(slow);
        assertSame(fast, delivery.nextSubscriber());
        assertSame(slow, delivery.nextSubscriber());
        assertFalse(delivery.hasSubscribers());
    }

    @Test void outOfOrderCompletionStillRanksReadyTerrainNearFirst() {
        record Ready(int tier, int distance, long completedAt) {}
        var completed = new java.util.ArrayList<>(List.of(
                new Ready(2, 50, 1),
                new Ready(1, 15, 3),
                new Ready(0, 5, 5),
                new Ready(1, 8, 4)));
        completed.sort((left, right) -> ViewExtendService.compareReadyPriority(
                left.tier(), left.distance(), left.completedAt(),
                right.tier(), right.distance(), right.completedAt()));
        assertEquals(List.of(5, 8, 15, 50), completed.stream().map(Ready::distance).toList());
    }

    @Test void detachingOneSubscriberPreservesCoalescedWorkForTheOther() {
        var firstState = new PlayerViewState(UUID.randomUUID(), "minecraft:overworld");
        var secondState = new PlayerViewState(UUID.randomUUID(), "minecraft:overworld");
        var first = new ViewExtendService.LoadSubscriber(firstState.playerUuid, firstState, 1);
        var second = new ViewExtendService.LoadSubscriber(secondState.playerUuid, secondState, 2);
        var delivery = new ViewExtendService.ReadyDelivery(null, null, List.of(first, second));
        delivery.removeSubscriber(firstState, 1);
        assertTrue(delivery.hasSubscribers());
        assertSame(second, delivery.nextSubscriber());
        assertFalse(delivery.hasSubscribers());
    }

    @Test void substantiallyCloserDemandMaySupersedeFarActiveWorkButNotPeers() {
        assertTrue(ViewExtendService.shouldSupersede(0, 6, 2, 50));
        assertTrue(ViewExtendService.shouldSupersede(1, 12, 1, 20));
        assertFalse(ViewExtendService.shouldSupersede(1, 12, 1, 19));
        assertFalse(ViewExtendService.shouldSupersede(2, 40, 1, 18));
    }
}
