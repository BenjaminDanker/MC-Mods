package com.silver.authorization.fabric;

import com.silver.authorization.PermissionNode;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/** Optional bridge to Open Parties and Claims' public permission-system addon API. */
final class OpenPacPermissionProvider {
    private static final String PROVIDER_ID = "network-authorization";
    private static final String NODE_PREFIX = "xaero.pac_";

    private OpenPacPermissionProvider() { }

    static void register(Logger logger) {
        Class<?> callbackType = loadFirst(
                "xaero.pac.common.event.api.v3.OPACServerAddonRegister",
                "xaero.pac.common.event.api.v2.OPACServerAddonRegister",
                "xaero.pac.common.event.api.OPACServerAddonRegister");
        if (callbackType == null) {
            logger.debug("[NetworkAuthorization] OpenPAC is not installed; permission bridge not registered");
            return;
        }
        try {
            Object event = callbackType.getField("EVENT").get(null);
            Class<?> fabricEventApi = Class.forName(
                    "net.fabricmc.fabric.api.event.Event", true, callbackType.getClassLoader());
            Method register = fabricEventApi.getMethod("register", Object.class);
            Object listener = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType},
                    (proxy, method, args) -> {
                        if (method.getName().equals("registerAddons")) {
                            Object[] parameters = args == null ? new Object[0] : args;
                            Object registrationApi = registrationApi(parameters);
                            registerProvider(registrationApi, logger);
                            return null;
                        }
                        return objectMethod(proxy, method, args);
                    });
            register.invoke(event, listener);
            logger.info("[NetworkAuthorization] Registered OpenPAC addon permission provider listener");
        } catch (ReflectiveOperationException failure) {
            logger.error("[NetworkAuthorization] OpenPAC permission addon API was found but could not be registered", failure);
        }
    }

    private static Object registrationApi(Object[] parameters) throws ReflectiveOperationException {
        if (parameters.length == 1) {
            return parameters[0].getClass().getMethod("getPermissionSystemManagerAPI").invoke(parameters[0]);
        }
        if (parameters.length >= 2) return parameters[1];
        throw new ReflectiveOperationException("OpenPAC addon callback supplied no registration context");
    }

    private static void registerProvider(Object registrationApi, Logger logger) throws ReflectiveOperationException {
        Class<?> providerApi = Class.forName(
                "xaero.pac.common.server.player.permission.api.IPlayerPermissionSystemAPI",
                true, registrationApi.getClass().getClassLoader());
        Object provider = Proxy.newProxyInstance(providerApi.getClassLoader(), new Class<?>[]{providerApi},
                (proxy, method, args) -> providerCall(proxy, method, args));
        try {
            registrationApi.getClass().getMethod("register", String.class, providerApi)
                    .invoke(registrationApi, PROVIDER_ID, provider);
            logger.info("[NetworkAuthorization] OpenPAC permission provider '{}' registered", PROVIDER_ID);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            logger.error("[NetworkAuthorization] OpenPAC rejected permission provider registration", cause);
            throw new ReflectiveOperationException("OpenPAC rejected provider registration", cause);
        }
    }

    private static Object providerCall(Object proxy, Method method, Object[] args) throws ReflectiveOperationException {
        return switch (method.getName()) {
            case "getPermission" -> hasCentralPermission((ServerPlayer) args[0], permissionName(args[1]));
            // Network Authorization is boolean capability based. Leave OpenPAC numeric/typed
            // configuration values absent so OpenPAC's own server configuration remains in force.
            case "getIntPermission" -> OptionalInt.empty();
            case "getPermissionTyped" -> isBooleanNode(args[1])
                    ? Optional.of(hasCentralPermission((ServerPlayer) args[0], permissionName(args[1])))
                    : Optional.empty();
            default -> objectMethod(proxy, method, args);
        };
    }

    private static boolean hasCentralPermission(ServerPlayer player, String node) {
        if (player == null || node == null || !node.startsWith(NODE_PREFIX)) return false;
        try {
            return AuthorizationChecks.has(player, PermissionNode.of(node));
        } catch (IllegalArgumentException invalidNode) {
            return false;
        }
    }

    private static String permissionName(Object node) throws ReflectiveOperationException {
        if (node == null) return null;
        Class<?> nodeApi = Class.forName(
                "xaero.pac.common.server.player.permission.api.IPermissionNodeAPI",
                true, node.getClass().getClassLoader());
        return (String) nodeApi.getMethod("getNodeString").invoke(node);
    }

    private static Class<?> loadFirst(String... names) {
        ClassLoader loader = OpenPacPermissionProvider.class.getClassLoader();
        for (String name : names) {
            try {
                return Class.forName(name, true, loader);
            } catch (ClassNotFoundException ignored) {
                // Try the previous supported API generation.
            }
        }
        return null;
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "NetworkAuthorizationOpenPacProvider";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null ? null : args[0]);
            default -> null;
        };
    }

    private static boolean isBooleanNode(Object node) {
        try {
            if (node == null) return false;
            Class<?> nodeApi = Class.forName(
                    "xaero.pac.common.server.player.permission.api.IPermissionNodeAPI",
                    true, node.getClass().getClassLoader());
            return nodeApi.getMethod("getType").invoke(node) == Boolean.class;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
