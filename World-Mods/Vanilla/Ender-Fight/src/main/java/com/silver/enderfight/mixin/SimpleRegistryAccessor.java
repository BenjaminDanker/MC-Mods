package com.silver.enderfight.mixin;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;

@Mixin(MappedRegistry.class)
public interface SimpleRegistryAccessor<T> {
    @Accessor("byKey")
    Map<?, Holder.Reference<T>> getKeyToEntry();

    @Accessor("byLocation")
    Map<?, Holder.Reference<T>> getIdToEntry();

    @Accessor("byValue")
    Map<T, Holder.Reference<T>> getValueToEntry();

    @Accessor("toId")
    Reference2IntMap<T> getEntryToRawId();

    @Accessor("byId")
    ObjectList<Holder.Reference<T>> getRawIdToEntry();

    @Accessor("frozen")
    boolean getFrozen();

    @Accessor("frozen")
    void setFrozen(boolean frozen);

    @Accessor("unregisteredIntrusiveHolders")
    @Nullable
    Map<T, Holder.Reference<T>> getIntrusiveValueToEntry();
}
