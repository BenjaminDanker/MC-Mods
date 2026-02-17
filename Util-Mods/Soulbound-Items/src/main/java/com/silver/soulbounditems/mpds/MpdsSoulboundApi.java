package com.silver.soulbounditems.mpds;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class MpdsSoulboundApi {
    private MpdsSoulboundApi() {
    }

    public static int getSoulboundMax(String playerName, String uuid) throws Exception {
        Class<?> sqlClass = Class.forName("mpds.mpds.sql");
        Method method = sqlClass.getMethod("getSoulboundMax", String.class, String.class);
        Object result = invokeStatic(method, playerName, uuid);
        return (int) result;
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
