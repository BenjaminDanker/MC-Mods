package com.silver.gaterites;

import com.mojang.math.Transformation;
import java.lang.reflect.Method;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A vanilla item display showing one of the portal models from the Magic resource pack. Display entities are configured through
 * private setters (the commands reach them with NBT), so they are resolved once here; if Minecraft changes them the portal fails
 * loudly instead of showing nothing.
 */
final class PortalDisplay {
    private static final Logger LOGGER = LoggerFactory.getLogger("gate-rites");
    /** Every display the gates spawn carries this tag, so leftovers from a crash can be told apart and removed. */
    static final String TAG = "gaterites_portal";

    private static final Method SET_ITEM = method("setItemStack", ItemStack.class);
    private static final Method SET_TRANSFORMATION = method("setTransformation", Transformation.class);
    private static final Method SET_BRIGHTNESS = method("setBrightnessOverride", Brightness.class);

    private PortalDisplay() {
    }

    private static Method method(String name, Class<?> argument) {
        for (Class<?> type : new Class<?>[] {Display.ItemDisplay.class, Display.class}) {
            try {
                Method found = type.getDeclaredMethod(name, argument);
                found.setAccessible(true);
                return found;
            } catch (NoSuchMethodException | RuntimeException notHere) {
                // try the superclass
            }
        }
        LOGGER.error("ALERT: Display.{} is missing; the portal cannot be drawn", name);
        return null;
    }

    static boolean available() {
        return SET_ITEM != null && SET_TRANSFORMATION != null && SET_BRIGHTNESS != null;
    }

    /** Spawns a full-bright display of the item model {@code gaterites:<model>}, {@code scale} blocks tall, facing {@code yaw}. */
    static Display.ItemDisplay spawn(ServerLevel level, Vec3 centre, float yaw, String model, float scale) {
        Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        if (display == null) {
            throw new IllegalStateException("item display could not be created");
        }
        try {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("gaterites", model));
            SET_ITEM.invoke(display, stack);
            SET_TRANSFORMATION.invoke(display, new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf()));
            SET_BRIGHTNESS.invoke(display, Brightness.FULL_BRIGHT);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not configure the portal display", failure);
        }
        display.setInvulnerable(true);
        display.setNoGravity(true);
        display.addTag(TAG);
        display.snapTo(centre.x, centre.y, centre.z, yaw, 0.0F);
        level.addFreshEntity(display);
        return display;
    }
}
