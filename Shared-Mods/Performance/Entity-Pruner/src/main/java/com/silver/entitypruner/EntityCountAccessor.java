package com.silver.entitypruner;

public interface EntityCountAccessor {
    int getMobCount(long chunkKey);
    int getItemCount(long chunkKey);
    void incrementMobCount(long chunkKey);
    void decrementMobCount(long chunkKey);
    void incrementItemCount(long chunkKey);
    void decrementItemCount(long chunkKey);

    boolean isResyncInProgress();
    void incrementResyncMobDelta(long chunkKey);
    void decrementResyncMobDelta(long chunkKey);
    void incrementResyncItemDelta(long chunkKey);
    void decrementResyncItemDelta(long chunkKey);
}