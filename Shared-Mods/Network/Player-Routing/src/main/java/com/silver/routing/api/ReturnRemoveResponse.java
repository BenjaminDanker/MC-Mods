package com.silver.routing.api;

/** Player Sync's answer to an item-return request: what it removed and what is left. */
public record ReturnRemoveResponse(boolean success,
                                   int removedInventory,
                                   int removedDb,
                                   int remainingInventory,
                                   int remainingDb,
                                   String error) {
}
