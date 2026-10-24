package com.silver.soulbounditems.mpds;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MpdsSoulboundApi {
    private static final long CACHE_TTL_MILLIS = 300_000L;
    private static final long RETRY_AFTER_FAILURE_MILLIS = 30_000L;
    private static final ConcurrentMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Long> FAILED_AT = new ConcurrentHashMap<>();
    private static volatile Class<?> SQL_CLASS;
    private static volatile Method GET_SOULBOUND_MAX_METHOD;

    private MpdsSoulboundApi() {
    }

    /** Throws when MPDS can't answer and nothing is cached, so each caller picks its own fallback. */
    public static int getSoulboundMax(String playerName, String uuid) throws Exception {
        String cacheKey = toCacheKey(playerName, uuid);
        long now = System.currentTimeMillis();

        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null && now - cached.loadedAtMillis() < CACHE_TTL_MILLIS) {
            return cached.value();
        }

        Long failedAt = FAILED_AT.get(cacheKey);
        if (failedAt != null && now - failedAt < RETRY_AFTER_FAILURE_MILLIS) {
            if (cached != null) {
                return cached.value();
            }
            throw new RecentFailureException();
        }

        try {
            int freshValue = fetchSoulboundMax(playerName, uuid);
            CACHE.put(cacheKey, new CacheEntry(freshValue, now));
            FAILED_AT.remove(cacheKey);
            return freshValue;
        } catch (Exception ex) {
            FAILED_AT.put(cacheKey, now);
            if (cached != null) {
                return cached.value();
            }
            throw ex;
        }
    }

    private static int fetchSoulboundMax(String playerName, String uuid) throws Exception {
        Method method = getSoulboundMaxMethod();
        Object result = invokeStatic(method, playerName, uuid);
        return (int) result;
    }

    private static Method getSoulboundMaxMethod() throws Exception {
        Method cachedMethod = GET_SOULBOUND_MAX_METHOD;
        if (cachedMethod != null) {
            return cachedMethod;
        }

        synchronized (MpdsSoulboundApi.class) {
            if (GET_SOULBOUND_MAX_METHOD != null) {
                return GET_SOULBOUND_MAX_METHOD;
            }

            if (SQL_CLASS == null) {
                SQL_CLASS = Class.forName("mpds.mpds.sql");
            }

            GET_SOULBOUND_MAX_METHOD = SQL_CLASS.getMethod("getSoulboundMax", String.class, String.class);
            return GET_SOULBOUND_MAX_METHOD;
        }
    }

    private static String toCacheKey(String playerName, String uuid) {
        if (uuid != null && !uuid.isBlank()) {
            return uuid.trim().toLowerCase();
        }
        if (playerName != null && !playerName.isBlank()) {
            return "name:" + playerName.trim().toLowerCase();
        }
        return "unknown";
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

    private record CacheEntry(int value, long loadedAtMillis) {
    }

    /** The lookup failed less than 30 seconds ago; the failure itself was already reported. */
    public static final class RecentFailureException extends IllegalStateException {
        RecentFailureException() {
            super("MPDS SoulboundMax lookup failed recently; retrying later", null);
        }
    }
}
