package com.silver.skyislands.portal;

import com.silver.skyislands.config.SkyIslandsPortalConfig;
import com.silver.skyislands.config.SkyIslandsPortalConfigManager;
import com.silver.skyislands.proxy.PortalRequestPayload;
import com.silver.skyislands.proxy.PortalRequestPayloadCodec;
import com.silver.skyislands.proxy.PortalRequestSigner;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalRedirector {
    public static final String VANILLA_ENTRANCE_PORTAL = "vanilla_entrance";
    private static final Logger LOGGER = LoggerFactory.getLogger(PortalRedirector.class);
    private static final long REDIRECT_COOLDOWN_MS = 3_000L;
    private static final Map<UUID, Long> recentRedirects = new ConcurrentHashMap<>();

    private static SkyIslandsPortalConfigManager configManager;

    private PortalRedirector() {
    }

    public static void init() {
        configManager = new SkyIslandsPortalConfigManager(LOGGER);
        configManager.load();
        SkyIslandsPortalConfig config = configManager.getConfig();
        LOGGER.info("[Sky-Islands][portal] PortalRedirector initialized enabled={} targetServer={} targetPortal='{}'",
            config.portalRedirectEnabled(),
            config.portalRedirectTargetServer(),
            VANILLA_ENTRANCE_PORTAL);
    }

    public static boolean redirectToConfiguredServer(ServerPlayerEntity player, String reason) {
        return redirectToConfiguredServer(player, reason, null);
    }

    public static boolean redirectToConfiguredServer(ServerPlayerEntity player, String reason, String portalOverride) {
        if (player == null || configManager == null) {
            LOGGER.info("[Sky-Islands][portal] redirect skipped: player or config manager missing ({})", reason);
            return false;
        }

        SkyIslandsPortalConfig config = configManager.getConfig();
        if (config == null || !config.portalRedirectEnabled()) {
            LOGGER.info("[Sky-Islands][portal] redirect skipped for {}: config missing or disabled ({})",
                player.getName().getString(),
                reason);
            return false;
        }

        LOGGER.info("[Sky-Islands][portal] redirect request start player={} reason={} world={} pos=({}, {}, {}) target={}",
            player.getName().getString(),
            reason,
            player.getCommandSource().getWorld().getRegistryKey().getValue(),
            player.getX(),
            player.getY(),
            player.getZ(),
            config.portalRedirectTargetServer());

        long now = System.currentTimeMillis();
        Long lastRedirect = recentRedirects.get(player.getUuid());
        if (lastRedirect != null && now - lastRedirect < REDIRECT_COOLDOWN_MS) {
            LOGGER.info("[Sky-Islands][portal] redirect skipped for {} due to cooldown {}ms ({})",
                player.getName().getString(),
                now - lastRedirect,
                reason);
            return false;
        }

        if (!sendPortalRequest(player, config, reason, portalOverride)) {
            return false;
        }

        recentRedirects.put(player.getUuid(), now);
        return true;
    }

    public static boolean isPortalRedirectEnabled() {
        return configManager != null && configManager.getConfig().portalRedirectEnabled();
    }

    public static String getConfiguredTargetServer() {
        if (configManager == null || configManager.getConfig() == null) {
            return null;
        }
        return configManager.getConfig().portalRedirectTargetServer();
    }

    private static boolean sendPortalRequest(ServerPlayerEntity player, SkyIslandsPortalConfig config, String reason, String portalOverride) {
        String targetServer = config.portalRedirectTargetServer();
        if (targetServer == null || targetServer.isBlank()) {
            return false;
        }

        String secret = config.portalRequestSecret();
        if (secret == null || secret.isBlank()) {
            return false;
        }

        String destinationPortal = portalOverride != null && !portalOverride.isBlank()
            ? portalOverride
            : VANILLA_ENTRANCE_PORTAL;

        player.sendMessage(Text.literal("Redirecting you to " + targetServer + "..."), false);

        try {
            long issuedAtMs = System.currentTimeMillis();
            String nonce = PortalRequestPayloadCodec.generateNonce();
            byte[] unsigned = PortalRequestPayloadCodec.encodeUnsigned(player.getUuid(), targetServer.trim(), destinationPortal, issuedAtMs, nonce);
            byte[] signature = PortalRequestSigner.hmacSha256(secret.trim(), unsigned);
            byte[] signed = PortalRequestPayloadCodec.encodeSigned(player.getUuid(), targetServer.trim(), destinationPortal, issuedAtMs, nonce, signature);
            LOGGER.info("[Sky-Islands][portal] sending portal request player={} target={} portal='{}' issuedAtMs={} nonce={}",
                player.getName().getString(),
                targetServer.trim(),
                destinationPortal,
                issuedAtMs,
                nonce);
            ServerPlayNetworking.send(player, new PortalRequestPayload(signed));
            LOGGER.info("Sent Sky-Islands portal request for {} -> {} ({})", player.getName().getString(), targetServer, reason);
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed sending Sky-Islands portal request for {} ({})", player.getName().getString(), reason, ex);
            return false;
        }
    }
}