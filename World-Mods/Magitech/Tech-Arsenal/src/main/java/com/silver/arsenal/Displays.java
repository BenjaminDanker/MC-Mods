package com.silver.arsenal;

import com.mojang.math.Transformation;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
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
 * Item displays that stand in the world (a mine on the ground, a deployed turret). They are configured through private setters,
 * resolved once here; if Minecraft changes them the arsenal says so loudly. Anything left over from a crash is removed when it loads.
 */
final class Displays {
    private static final Logger LOGGER = LoggerFactory.getLogger("tech-arsenal");
    static final String TAG = "arsenal_display";

    private static final Method SET_ITEM = method("setItemStack", ItemStack.class);
    private static final Method SET_TRANSFORMATION = method("setTransformation", Transformation.class);
    private static final Method SET_BRIGHTNESS = method("setBrightnessOverride", Brightness.class);
    private static boolean spawning;

    private Displays() {
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
        LOGGER.error("ALERT: Display.{} is missing; mines and turrets cannot be drawn", name);
        return null;
    }

    static boolean available() {
        return SET_ITEM != null && SET_TRANSFORMATION != null && SET_BRIGHTNESS != null;
    }

    static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!spawning && entity.entityTags().contains(TAG)) {
                level.getServer().execute(entity::discard);
            }
        });
    }

    /** Spawns a display of the item model {@code arsenal:<model>}, {@code scale} blocks across, turned by {@code rotation}. */
    static Display.ItemDisplay spawn(ServerLevel level, Vec3 at, float yaw, String model, float scale, Quaternionf rotation) {
        Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        if (display == null) {
            throw new IllegalStateException("item display could not be created");
        }
        try {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("arsenal", model));
            SET_ITEM.invoke(display, stack);
            SET_TRANSFORMATION.invoke(display, new Transformation(new Vector3f(), rotation, new Vector3f(scale), new Quaternionf()));
            SET_BRIGHTNESS.invoke(display, new Brightness(12, 12));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not configure the display", failure);
        }
        display.setInvulnerable(true);
        display.setNoGravity(true);
        display.addTag(TAG);
        display.snapTo(at.x, at.y, at.z, yaw, 0.0F);
        spawning = true;
        try {
            level.addFreshEntity(display);
        } finally {
            spawning = false;
        }
        return display;
    }
}
