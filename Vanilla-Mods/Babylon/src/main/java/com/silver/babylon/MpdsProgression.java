package com.silver.babylon;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Calls MPDS's progression API (mpds.mpds.api.Progression) by name, so Babylon builds without MPDS. These calls
 * talk to the database, so they run on {@link #IO}, never on the server thread.
 */
final class MpdsProgression {
    static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "babylon-mpds");
        thread.setDaemon(true);
        return thread;
    });
    private static final String API = "mpds.mpds.api.Progression";

    private MpdsProgression() {
    }

    static void switchBranch(String name, String uuid, String from, String to) throws Exception {
        call("switchBranch", new Class<?>[] {String.class, String.class, String.class, String.class}, name, uuid, from, to);
    }

    static void setStage1Cleared(String name, String uuid, boolean cleared) throws Exception {
        call("setStage1Cleared", new Class<?>[] {String.class, String.class, boolean.class}, name, uuid, cleared);
    }

    static int removeCustomIdFromStoredInventories(String uuid, String key, String value) throws Exception {
        return (int) call("removeCustomIdFromStoredInventories", new Class<?>[] {String.class, String.class, String.class}, uuid, key, value);
    }

    private static Object call(String method, Class<?>[] types, Object... args) throws Exception {
        Method target = Class.forName(API).getMethod(method, types);
        try {
            return target.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }
}
