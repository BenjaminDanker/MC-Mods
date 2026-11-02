package com.silver.arcade.selftest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reads and calls private members of the product classes, so the self-test needs no hooks in the rooms themselves. */
public final class Reflect {
    private Reflect() {
    }

    private static Field find(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException next) {
                // keep looking in the superclass
            }
        }
        throw new IllegalStateException("no field " + name + " in " + type.getName());
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object target, String name) {
        try {
            return (T) find(target.getClass(), name).get(target);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getStatic(Class<?> type, String name) {
        try {
            return (T) find(type, name).get(null);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static void set(Object target, String name, Object value) {
        try {
            find(target.getClass(), name).set(target, value);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static int getInt(Object target, String name) {
        return this_int(get(target, name));
    }

    private static int this_int(Object o) {
        return ((Number) o).intValue();
    }

    /** Calls a (possibly private) method by name and argument count; the first match wins. */
    @SuppressWarnings("unchecked")
    public static <T> T call(Object target, String name, Object... args) {
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                    try {
                        m.setAccessible(true);
                        return (T) m.invoke(target instanceof Class<?> ? null : target, args);
                    } catch (java.lang.reflect.InvocationTargetException failure) {
                        if (failure.getCause() instanceof RuntimeException r) {
                            throw r;
                        }
                        throw new IllegalStateException(failure.getCause());
                    } catch (IllegalAccessException | IllegalArgumentException failure) {
                        // try another overload
                    }
                }
            }
        }
        throw new IllegalStateException("no method " + name + "/" + args.length + " in " + type.getName());
    }
}
