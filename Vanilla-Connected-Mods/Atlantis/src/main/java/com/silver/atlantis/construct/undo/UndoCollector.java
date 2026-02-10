package com.silver.atlantis.construct.undo;

import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.enginehub.linbus.format.snbt.LinStringIO;
import org.enginehub.linbus.tree.LinCompoundTag;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class UndoCollector {

    private UndoCollector() {
    }

    public static List<UndoEntry> collectPreviousBlocks(ChangeSet changeSet) {
        if (changeSet == null || changeSet.size() == 0) {
            return List.of();
        }

        List<UndoEntry> out = new ArrayList<>(Math.max(16, changeSet.size()));
        Iterator<Change> iterator = changeSet.forwardIterator();
        while (iterator.hasNext()) {
            Change change = iterator.next();
            if (!(change instanceof BlockChange blockChange)) {
                continue;
            }

            var pos = blockChange.position();
            BaseBlock previous = blockChange.previous();

            String nbtSnbt = null;
            if (previous != null) {
                LazyReference<LinCompoundTag> ref = previous.getNbtReference();
                if (ref != null) {
                    LinCompoundTag tag = ref.getValue();
                    if (tag != null) {
                        nbtSnbt = LinStringIO.writeToString(tag);
                    }
                }
            }

            out.add(new UndoEntry(pos.x(), pos.y(), pos.z(),
                previous != null ? previous.getAsString() : "minecraft:air",
                nbtSnbt
            ));
        }

        return out;
    }
}
