package com.silver.enderfight.mixin;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(SimpleRegistry.class)
public interface SimpleRegistryAccessor<T> {
    @Accessor("keyToEntry")
    Map<?, RegistryEntry.Reference<T>> getKeyToEntry();

    @Accessor("idToEntry")
    Map<?, RegistryEntry.Reference<T>> getIdToEntry();

    @Accessor("valueToEntry")
    Map<T, RegistryEntry.Reference<T>> getValueToEntry();

    @Accessor("entryToRawId")
    Reference2IntMap<T> getEntryToRawId();

    @Accessor("rawIdToEntry")
    ObjectList<RegistryEntry.Reference<T>> getRawIdToEntry();

    @Accessor("frozen")
    boolean getFrozen();

    @Accessor("frozen")
    void setFrozen(boolean frozen);

    @Accessor("intrusiveValueToEntry")
    @Nullable
    Map<T, RegistryEntry.Reference<T>> getIntrusiveValueToEntry();

    @Invoker("method_45938")
    static void invokeSetValue(Object value, RegistryEntry.Reference<?> reference) {
        throw new UnsupportedOperationException("Accessor method body replaced at runtime");
    }
}
