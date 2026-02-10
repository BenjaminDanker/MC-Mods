package com.silver.villagerinterface.soulbound;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class MpdsSoulboundApi {
    private MpdsSoulboundApi() {
    }

    public record Capacity(int current, int max) {
        public boolean hasRoom() {
            return current < max;
        }
    }

    public static Capacity getCapacity(String playerName, String uuid) throws Exception {
        Class<?> sqlClass = Class.forName("mpds.mpds.sql");
        Method method = sqlClass.getMethod("getCraftedSoulboundCapacity", String.class, String.class);
        int[] cap = (int[]) invokeStatic(method, playerName, uuid);
        int current = cap.length > 0 ? cap[0] : 0;
        int max = cap.length > 1 ? cap[1] : 0;
        return new Capacity(current, max);
    }

    public static boolean tryReserveOne(String playerName, String uuid) throws Exception {
        Class<?> sqlClass = Class.forName("mpds.mpds.sql");
        Method method = sqlClass.getMethod("tryReserveCraftedSoulbound", String.class, String.class);
        Object result = invokeStatic(method, playerName, uuid);
        return (boolean) result;
    }

    private static Object invokeStatic(Method method, Object... args) throws Exception {
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
}
