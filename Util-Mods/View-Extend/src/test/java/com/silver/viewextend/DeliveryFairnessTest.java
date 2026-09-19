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
}
