package com.silver.aipets.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.TransferringPlacement;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.common.transport.PetAdoptionWireRequest;
import com.silver.aipets.common.transport.PetAdoptionWireResult;
import com.silver.aipets.common.transport.PetAdoptionWireStatus;
import com.silver.aipets.common.transport.AccountLinkWireResult;
import com.silver.aipets.common.transport.AccountLinkWireStatus;
import com.silver.aipets.common.transport.CustomerPortalWireResult;
import com.silver.aipets.common.transport.CustomerPortalWireStatus;
import com.silver.aipets.common.transport.SubscriptionAccessWireResult;
import com.silver.aipets.common.transport.DialogueContextUsageWire;
import com.silver.aipets.common.transport.DialogueHistoryWireResult;
import com.silver.aipets.common.transport.RecallResetWireResult;
import com.silver.aipets.common.transport.RecallResetWireStatus;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.authority.PetAuthorityGateway;
import com.silver.aipets.fabric.authority.PetAuthoritySnapshot;
import com.silver.aipets.fabric.authority.AuthorityMutationResult;
import com.silver.aipets.fabric.authority.AuthorityMutationStatus;
import com.silver.aipets.fabric.placement.PetPickupCoordinator;
import com.silver.aipets.fabric.compass.PetCompassIssueResult;
import com.silver.aipets.fabric.compass.PetCompassManager;
import com.silver.aipets.fabric.placement.PetPickupOutcome;
import com.silver.aipets.fabric.placement.PetPickupStatus;
import com.silver.aipets.fabric.placement.PetPlacementCoordinator;
import com.silver.aipets.fabric.placement.PetPlacementOutcome;
import com.silver.aipets.fabric.placement.PetPlacementStatus;
import com.silver.aipets.fabric.recall.PetRecallCoordinator;
import com.silver.aipets.fabric.recall.PetRecallOutcome;
import com.silver.aipets.fabric.recall.PetRecallStatus;
import com.silver.aipets.fabric.permission.PetPermission;
import com.silver.aipets.fabric.permission.PetPermissions;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.net.URI;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

/** Player command surface; physical mutations delegate to commit-safe async coordinators. */
public final class PetCommands {
    private static final Map<UUID, Boolean> HAS_PET = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> BILLING_WATCHES = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> ADOPTION_WATCHES = new ConcurrentHashMap<>();

    private PetCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("pet")
                .executes(PetCommands::usage)
                .then(literal("help")
                        .requires(source -> PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::help))
                .then(literal("status")
                        .requires(source -> PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::status))
                .then(literal("link")
                        .requires(source -> PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::link))
                .then(literal("portal")
                        .requires(source -> PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::portal))
                .then(literal("billing")
                        .requires(source -> PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::billing))
                .then(literal("adopt")
                        .requires(source -> PetPermissions.check(source, PetPermission.ADOPT))
                        .executes(context -> adoptionUsage(context, null))
                        .then(literal("cat")
                                .executes(context -> adoptionUsage(context, PetSpecies.CAT))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(context -> adopt(context, PetSpecies.CAT))))
                        .then(literal("dog")
                                .executes(context -> adoptionUsage(context, PetSpecies.DOG))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(context -> adopt(context, PetSpecies.DOG))))
                        .then(literal("subscribe")
                                .requires(source -> PetPermissions.check(source, PetPermission.USE))
                                .executes(PetCommands::link)))
                .then(literal("place")
                        .requires(source -> PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::place))
                .then(literal("pickup")
                        .requires(source -> PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::pickup))
                .then(literal("recall")
                        .requires(source -> PetPermissions.check(source, PetPermission.RECALL))
                        .executes(PetCommands::recall))
                .then(literal("compass")
                        .requires(source -> PetPermissions.check(source, PetPermission.COMPASS))
                        .executes(PetCommands::compass))
                .then(literal("admin")
                        .requires(PetCommands::hasAdminAccess)
                        .then(literal("inspect")
                                .requires(source -> PetPermissions.check(
                                        source, PetPermission.ADMIN_INSPECT))
                                .executes(PetCommands::adminInspect)
                                .then(argument("ownerUuid", StringArgumentType.word())
                                        .executes(PetCommands::adminInspect)))
                        .then(literal("recover")
                                .requires(source -> PetPermissions.check(
                                        source, PetPermission.ADMIN_RECOVER))
                                .executes(PetCommands::adminRecover)
                                .then(argument("ownerUuid", StringArgumentType.word())
                                        .executes(PetCommands::adminRecover)))
                        .then(literal("recall-reset")
                                .requires(source -> PetPermissions.check(
                                        source, PetPermission.ADMIN_RECOVER))
                                .executes(PetCommands::adminRecallReset)
                                .then(argument("ownerUuid", StringArgumentType.word())
                                        .executes(PetCommands::adminRecallReset)))
                        .then(literal("history")
                                .requires(source -> PetPermissions.check(
                                        source, PetPermission.ADMIN_MEMORY))
                                .executes(PetCommands::adminHistory)
                                .then(argument("ownerUuid", StringArgumentType.word())
                                        .executes(PetCommands::adminHistory)))
                        .then(literal("reconcile")
                                .requires(source -> PetPermissions.check(
                                        source, PetPermission.ADMIN_RECONCILE))
                                .executes(PetCommands::adminReconcile))));
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Pet Companion commands")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        sendHelpLine(source, "/pet adopt <cat|dog> <name> — adopts now with an active subscription.");
        sendHelpLine(source, "Without one, open the checkout link within 15 minutes; after checkout starts,"
                + " your named pet is adopted automatically when payment is confirmed (no second command).");
        sendHelpLine(source, "/pet status — show your pet and current status.");
        sendHelpLine(source, "/pet place — bring your pet into the world.");
        sendHelpLine(source, "/pet pickup — pick up your placed pet.");
        sendHelpLine(source, "/pet compass — get a compass pointing to your pet.");
        sendHelpLine(source, "/pet recall — use your monthly pet recall.");
        sendHelpLine(source, "/pet link — start a subscription if needed.");
        sendHelpLine(source, "/pet billing — check subscription status.");
        sendHelpLine(source, "/pet portal — manage or cancel billing.");
        sendHelpLine(source, "Conversation: right-click your awake, placed pet, then type normally in chat. Use !exit to leave.");
        sendHelpLine(source, "An active subscription is required to adopt and use conversation. If you cancel, access lasts through the paid period; your pet remains stored.");
        return 1;
    }

    private static int usage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
                "Pet Companion: use /pet help for commands, or type /pet and press Tab.")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static void sendHelpLine(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
    }

    private static int link(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        if (gateway.isEmpty()) {
            source.sendFailure(Component.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        UUID ownerUuid = player.getUUID();
        source.sendSuccess(
                () -> Component.literal("Checking your subscription status…")
                        .withStyle(ChatFormatting.GRAY),
                false);
        try {
            gateway.orElseThrow().findSubscriptionDetails(ownerUuid).whenComplete((details, statusFailure) ->
                    onServer(source, () -> {
                        if (statusFailure != null || details == null) {
                            source.sendFailure(Component.literal(
                                    "Subscription status is temporarily unavailable; no checkout link was created."));
                            return;
                        }
                        if (details.aiAccessEnabled()) {
                            source.sendSuccess(() -> Component.literal("SUBSCRIPTION ALREADY ACTIVE")
                                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false);
                            source.sendSuccess(() -> Component.empty()
                                    .append(Component.literal(
                                            "No new checkout was created. " + billingStateText(details)
                                                    + " Manage it with /pet portal.")
                                            .withStyle(ChatFormatting.GRAY)), false);
                            return;
                        }
                        source.sendSuccess(() -> Component.literal("Creating your secure pet checkout link…")
                                .withStyle(ChatFormatting.GRAY), false);
                        try {
                            gateway.orElseThrow().createAccountLink(ownerUuid).whenComplete((result, failure) ->
                                    onServer(source, () -> completeLink(
                                            source, ownerUuid, gateway.orElseThrow(), details,
                                            result, failure)));
                        } catch (RuntimeException failure) {
                            source.sendFailure(Component.literal("Account linking is temporarily unavailable."));
                        }
                    }));
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal(
                    "Subscription status is temporarily unavailable; no checkout link was created."));
            return 0;
        }
        return 1;
    }

    private static void completeLink(
            CommandSourceStack source,
            UUID ownerUuid,
            PetAuthorityGateway gateway,
            SubscriptionAccessWireResult baseline,
            AccountLinkWireResult result,
            Throwable failure) {
        if (source.getServer().getPlayerList().getPlayer(ownerUuid) == null) return;
        if (failure != null || result == null) {
            source.sendFailure(Component.literal("Account linking is temporarily unavailable."));
            return;
        }
        if (result.status() == AccountLinkWireStatus.RATE_LIMITED) {
            source.sendSuccess(
                    () -> Component.literal("Too many checkout links were requested; wait a few minutes.")
                            .withStyle(ChatFormatting.YELLOW),
                    false);
            return;
        }
        if (result.status() == AccountLinkWireStatus.CHECKOUT_IN_PROGRESS) {
            source.sendSuccess(() -> Component.literal(
                    "Your adoption checkout is already in progress. Complete that checkout to adopt your pet.")
                    .withStyle(ChatFormatting.YELLOW), false);
            watchAdoptionNotice(source.getServer().getPlayerList().getPlayer(ownerUuid), gateway);
            return;
        }
        URI checkoutUrl = URI.create(result.checkoutUrl().orElseThrow());
        source.sendSuccess(() -> Component.literal("STRIPE CHECKOUT")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("Subscription link (expires "
                        + formatUtc(result.expiresAt().orElseThrow()) + "):")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> urlComponent(checkoutUrl), false);
        source.sendSuccess(() -> Component.literal(
                "Open or copy the full URL in a browser, complete payment, then return to Minecraft.")
                .withStyle(ChatFormatting.GRAY), false);
        watchBilling(source, ownerUuid, gateway, baseline);
    }

    private static int portal(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        if (gateway.isEmpty()) {
            source.sendFailure(Component.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        UUID ownerUuid = player.getUUID();
        source.sendSuccess(() -> Component.literal("Preparing your secure billing page…")
                .withStyle(ChatFormatting.GRAY), false);
        try {
            gateway.orElseThrow().findSubscriptionDetails(ownerUuid).whenComplete((baseline, statusFailure) ->
                    onServer(source, () -> {
                        if (statusFailure != null || baseline == null) {
                            source.sendFailure(Component.literal("Billing is temporarily unavailable; please retry."));
                            return;
                        }
                        gateway.orElseThrow().createCustomerPortal(ownerUuid).whenComplete((result, failure) ->
                                onServer(source, () -> completePortal(
                                        source, ownerUuid, gateway.orElseThrow(), baseline,
                                        result, failure)));
                    }));
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal("Billing management is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static int billing(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        if (gateway.isEmpty()) {
            source.sendFailure(Component.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        UUID ownerUuid = player.getUUID();
        source.sendSuccess(() -> Component.literal("BILLING — checking your subscription…")
                .withStyle(ChatFormatting.GRAY), false);
        try {
            gateway.orElseThrow().findSubscriptionDetails(ownerUuid).whenComplete((details, failure) ->
                    onServer(source, () -> completeBilling(source, ownerUuid, details, failure)));
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal("Subscription status is temporarily unavailable; please retry."));
            return 0;
        }
        return 1;
    }

    private static void completeBilling(
            CommandSourceStack source,
            UUID ownerUuid,
            SubscriptionAccessWireResult details,
            Throwable failure) {
        if (source.getServer().getPlayerList().getPlayer(ownerUuid) == null) return;
        if (failure != null || details == null) {
            source.sendFailure(Component.literal("Subscription status is temporarily unavailable; please retry."));
            return;
        }
        source.sendSuccess(() -> Component.literal("BILLING")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        if (hasAdminAccess(source)
                && details.budgetUsd() != null
                && details.remainingUsd() != null) {
            source.sendSuccess(() -> Component.literal(
                    "Admin quota: " + usd(details.budgetUsd())
                            + " total • " + usd(details.remainingUsd()) + " remaining"
                            + (details.consumedUsd() == null
                            ? "" : " • " + usd(details.consumedUsd()) + " consumed"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        if (details.aiAccessEnabled()) {
            source.sendSuccess(() -> Component.literal(
                    "Your subscription is active. " + billingStateText(details))
                    .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.empty()
                    .append(Component.literal("Next action: run /pet portal to manage or cancel billing.")
                            .withStyle(ChatFormatting.GRAY)), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "No active subscription is linked to this Minecraft account.")
                    .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.empty()
                    .append(Component.literal("Next action: run /pet link to start a subscription.")
                            .withStyle(ChatFormatting.GRAY)), false);
        }
    }

    private static String usd(java.math.BigDecimal value) {
        return "$" + value.stripTrailingZeros().toPlainString();
    }

    private static void completePortal(
            CommandSourceStack source,
            UUID ownerUuid,
            PetAuthorityGateway gateway,
            SubscriptionAccessWireResult baseline,
            CustomerPortalWireResult result,
            Throwable failure) {
        if (source.getServer().getPlayerList().getPlayer(ownerUuid) == null) return;
        if (failure != null || result == null) {
            source.sendFailure(Component.literal("Billing management is temporarily unavailable."));
            return;
        }
        if (result.status() == CustomerPortalWireStatus.NOT_LINKED) {
            source.sendSuccess(() -> Component.literal("NO BILLING ACCOUNT LINKED")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false);
            source.sendSuccess(() -> Component.empty()
                    .append(Component.literal("Start checkout first with /pet link.")
                            .withStyle(ChatFormatting.GRAY)), false);
            return;
        }
        if (result.status() == CustomerPortalWireStatus.RATE_LIMITED) {
            source.sendSuccess(
                    () -> Component.literal("Too many portal links were requested; wait a few minutes.")
                            .withStyle(ChatFormatting.YELLOW),
                    false);
            return;
        }
        URI portalUrl = URI.create(result.portalUrl().orElseThrow());
        source.sendSuccess(() -> Component.literal("BILLING PORTAL READY")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);

        source.sendSuccess(() -> Component.literal("Billing portal link:")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> urlComponent(portalUrl), false);
        source.sendSuccess(() -> Component.literal(
                "Open or copy the full URL in a browser to manage your subscription.")
                .withStyle(ChatFormatting.GRAY), false);
        watchBilling(source, ownerUuid, gateway, baseline);
        watchAdoptionNotice(source.getServer().getPlayerList().getPlayer(ownerUuid), gateway);
    }

    private static Component urlComponent(URI url) {
        return Component.literal(url.toString())
                .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(new ClickEvent.OpenUrl(url)));
    }

    /** Refreshes the command tree after the authoritative ownership read completes. */
    public static void refreshVisibility(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        if (gateway.isEmpty()) return;
        UUID ownerUuid = player.getUUID();
        gateway.orElseThrow().findByOwner(ownerUuid).whenComplete((snapshot, failure) -> {
            if (failure != null || snapshot == null) return;
            player.level().getServer().execute(() -> {
                ServerPlayer current = player.level().getServer()
                        .getPlayerList().getPlayer(ownerUuid);
                if (current == null) return;
                HAS_PET.put(ownerUuid, snapshot.isPresent());
                current.level().getServer().getCommands().sendCommands(current);
            });
        });
    }

    public static void clearPlayerState(UUID ownerUuid) {
        HAS_PET.remove(ownerUuid);
        BILLING_WATCHES.remove(ownerUuid);
        ADOPTION_WATCHES.remove(ownerUuid);
    }

    public static void checkPendingAdoptionNotice(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        PetCompanionMod.authorityGateway().ifPresent(gateway -> watchAdoptionNotice(player, gateway));
    }

    private static void watchAdoptionNotice(ServerPlayer player, PetAuthorityGateway gateway) {
        if (player == null) return;
        UUID ownerUuid = player.getUUID();
        UUID watchId = UUID.randomUUID();
        ADOPTION_WATCHES.put(ownerUuid, watchId);
        pollAdoptionNotice(player.level().getServer(), ownerUuid, gateway, watchId, 0);
    }

    private static void pollAdoptionNotice(
            net.minecraft.server.MinecraftServer server,
            UUID ownerUuid,
            PetAuthorityGateway gateway,
            UUID watchId,
            int attempt) {
        if (attempt >= 5_760 || !watchId.equals(ADOPTION_WATCHES.get(ownerUuid))) {
            ADOPTION_WATCHES.remove(ownerUuid, watchId);
            return;
        }
        try {
            gateway.findPendingAdoptionNotice(ownerUuid).whenComplete((notice, failure) ->
                    server.execute(() -> {
                        if (!watchId.equals(ADOPTION_WATCHES.get(ownerUuid))) return;
                        ServerPlayer online = server.getPlayerList().getPlayer(ownerUuid);
                        if (online == null) {
                            ADOPTION_WATCHES.remove(ownerUuid, watchId);
                            return;
                        }
                        if (failure == null && notice != null && notice.isPresent()) {
                            var completed = notice.orElseThrow();
                            online.sendSystemMessage(Component.literal(completed.petName()
                                            + " has been adopted. Use /pet place when you're ready.")
                                    .withStyle(ChatFormatting.GREEN));
                            gateway.acknowledgePendingAdoptionNotice(ownerUuid, completed.intentId())
                                    .whenComplete((acknowledged, ackFailure) -> server.execute(() -> {
                                        if (ackFailure == null && Boolean.TRUE.equals(acknowledged)) {
                                            ADOPTION_WATCHES.remove(ownerUuid, watchId);
                                        } else {
                                            scheduleAdoptionNoticePoll(
                                                    server, ownerUuid, gateway, watchId, attempt + 1);
                                        }
                                    }));
                        } else {
                            scheduleAdoptionNoticePoll(server, ownerUuid, gateway, watchId, attempt + 1);
                        }
                    }));
        } catch (RuntimeException failure) {
            scheduleAdoptionNoticePoll(server, ownerUuid, gateway, watchId, attempt + 1);
        }
    }

    private static void scheduleAdoptionNoticePoll(
            net.minecraft.server.MinecraftServer server,
            UUID ownerUuid,
            PetAuthorityGateway gateway,
            UUID watchId,
            int attempt) {
        CompletableFuture.delayedExecutor(15, TimeUnit.SECONDS).execute(() ->
                pollAdoptionNotice(server, ownerUuid, gateway, watchId, attempt));
    }

    private static boolean hasPetAccess(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null || Boolean.TRUE.equals(HAS_PET.get(player.getUUID()));
    }

    private static boolean hasAdminAccess(CommandSourceStack source) {
        return PetPermissions.check(source, PetPermission.ADMIN_INSPECT)
                || PetPermissions.check(source, PetPermission.ADMIN_RECOVER)
                || PetPermissions.check(source, PetPermission.ADMIN_RECONCILE)
                || PetPermissions.check(source, PetPermission.ADMIN_SUBSCRIPTION)
                || PetPermissions.check(source, PetPermission.ADMIN_MEMORY);
    }

    private static void rememberOwnership(
            CommandSourceStack source, UUID ownerUuid, boolean hasPet) {
        HAS_PET.put(ownerUuid, hasPet);
        ServerPlayer player = source.getServer().getPlayerList().getPlayer(ownerUuid);
        if (player != null) source.getServer().getCommands().sendCommands(player);
    }

    private static void watchBilling(
            CommandSourceStack source,
            UUID ownerUuid,
            PetAuthorityGateway gateway,
            SubscriptionAccessWireResult baseline) {
        UUID watchId = UUID.randomUUID();
        BILLING_WATCHES.put(ownerUuid, watchId);
        pollBilling(source, ownerUuid, gateway, baseline, watchId, 0);
    }

    private static void pollBilling(
            CommandSourceStack source,
            UUID ownerUuid,
            PetAuthorityGateway gateway,
            SubscriptionAccessWireResult baseline,
            UUID watchId,
            int attempt) {
        if (attempt >= 60 || !watchId.equals(BILLING_WATCHES.get(ownerUuid))) {
            BILLING_WATCHES.remove(ownerUuid, watchId);
            return;
        }
        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
            if (!watchId.equals(BILLING_WATCHES.get(ownerUuid))) return;
            try {
                gateway.findSubscriptionDetails(ownerUuid).whenComplete((current, failure) ->
                        onServer(source, () -> {
                            ServerPlayer player = source.getServer().getPlayerList()
                                    .getPlayer(ownerUuid);
                            if (player == null) {
                                BILLING_WATCHES.remove(ownerUuid, watchId);
                                return;
                            }
                            if (failure == null && current != null
                                    && billingChanged(baseline, current)) {
                                BILLING_WATCHES.remove(ownerUuid, watchId);
                                announceBillingChange(source, baseline, current);
                                return;
                            }
                            pollBilling(source, ownerUuid, gateway, baseline, watchId, attempt + 1);
                        }));
            } catch (RuntimeException failure) {
                onServer(source, () -> pollBilling(
                        source, ownerUuid, gateway, baseline, watchId, attempt + 1));
            }
        });
    }

    private static boolean billingChanged(
            SubscriptionAccessWireResult before, SubscriptionAccessWireResult after) {
        return before.aiAccessEnabled() != after.aiAccessEnabled()
                || before.cancelAtPeriodEnd() != after.cancelAtPeriodEnd()
                || !Objects.equals(before.status(), after.status());
    }

    private static void announceBillingChange(
            CommandSourceStack source,
            SubscriptionAccessWireResult before,
            SubscriptionAccessWireResult after) {
        if (!before.aiAccessEnabled() && after.aiAccessEnabled()) {
            source.sendSuccess(() -> Component.literal(
                    "Your membership is active! Run /pet adopt <cat|dog> <name> to adopt.")
                    .withStyle(ChatFormatting.GREEN), false);
            return;
        }
        if (after.cancelAtPeriodEnd()) {
            source.sendSuccess(() -> Component.literal("Cancellation scheduled. "
                    + billingStateText(after)).withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (!after.aiAccessEnabled() || "CANCELED".equals(after.status())) {
            source.sendSuccess(() -> Component.literal("Your subscription has been cancelled.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private static String billingStateText(SubscriptionAccessWireResult details) {
        if (details.cancelAtPeriodEnd() && details.currentPeriodEnd() != null) {
            try {
                return "Your membership remains active until "
                        + formatUtc(Instant.parse(details.currentPeriodEnd()))
                        + "; cancellation is scheduled for then.";
            } catch (RuntimeException ignored) {
                return "Your membership remains active through the current paid period; cancellation is scheduled.";
            }
        }
        return switch (details.status()) {
            case "PAST_DUE" -> "There is a payment problem; your membership is in its grace period.";
            case "CANCELED" -> "This membership is canceled.";
            case "TRIALING" -> "Your trial is active.";
            default -> "You can update payment or cancel from the billing page.";
        };
    }

    private static int recall(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetRecallCoordinator> configured = PetCompanionMod.recallCoordinator();
        if (configured.isEmpty()) {
            source.sendFailure(Component.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        UUID ownerUuid = player.getUUID();
        source.sendSuccess(() -> Component.literal("Recalling your pet…").withStyle(ChatFormatting.GRAY), false);
        try {
            configured.orElseThrow().recall(player).whenComplete((outcome, failure) ->
                    onServer(source, () -> completeRecall(source, ownerUuid, outcome, failure)));
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal("Pet recall is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completeRecall(
            CommandSourceStack source,
            UUID ownerUuid,
            PetRecallOutcome outcome,
            Throwable failure) {
        if (source.getServer().getPlayerList().getPlayer(ownerUuid) == null) return;
        if (failure != null || outcome == null) {
            source.sendFailure(Component.literal("Pet recall is temporarily unavailable."));
            return;
        }
        if (outcome.status() == PetRecallStatus.RECALLED) {
            source.sendSuccess(
                    () -> Component.literal("Your pet was recalled safely. Next recall: "
                                    + formatUtc(outcome.nextAvailableAt().orElseThrow()))
                            .withStyle(ChatFormatting.GREEN),
                    false);
            return;
        }
        if (outcome.status() == PetRecallStatus.UNAVAILABLE) {
            source.sendSuccess(
                    () -> Component.literal("This month's recall is already used. Next recall: "
                                    + formatUtc(outcome.nextAvailableAt().orElseThrow()))
                            .withStyle(ChatFormatting.YELLOW),
                    false);
            return;
        }
        String message = switch (outcome.status()) {
            case NO_PET -> "You have not adopted a pet yet.";
            case NO_SAFE_POSITION -> "No safe loaded position was found nearby; recall was not used.";
            case PLAYER_CONTEXT_CHANGED -> "Recall stopped because your player context changed.";
            case SPAWN_FAILED_COMPENSATED ->
                    "The pet could not spawn; it is held and this month's recall was not used.";
            case SPAWN_FAILED_UNRESOLVED ->
                    "The pet could not spawn; an administrator should reconcile it.";
            case AUTHORITY_REJECTED -> "Your pet's location changed; check /pet status and try again.";
            case SERVICE_FAILURE -> "Pet recall is temporarily unavailable.";
            case RECALLED, UNAVAILABLE -> throw new IllegalStateException("Handled above");
        };
        source.sendFailure(Component.literal(message));
    }

    private static String formatUtc(java.time.Instant value) {
        return DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'UTC'", Locale.ROOT)
                .withZone(ZoneOffset.UTC)
                .format(value);
    }

    private static int adopt(CommandContext<CommandSourceStack> context, PetSpecies species) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        if (gateway.isEmpty()) {
            source.sendFailure(Component.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        PetAdoptionWireRequest request;
        try {
            request = new PetAdoptionWireRequest(
                    player.getUUID(),
                    species,
                    StringArgumentType.getString(context, "name"));
        } catch (IllegalArgumentException invalidName) {
            source.sendFailure(Component.literal(invalidName.getMessage()));
            return 0;
        }

        UUID ownerUuid = player.getUUID();
        source.sendSuccess(() -> Component.literal("Preparing your adoption…").withStyle(ChatFormatting.GRAY), false);
        try {
            gateway.orElseThrow().adopt(request).whenComplete((result, failure) ->
                    onServer(source, () -> completeAdoption(
                            source, ownerUuid, request.name(), gateway.orElseThrow(), result, failure)));
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal("Pet adoption is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static int adoptionUsage(CommandContext<CommandSourceStack> context, PetSpecies species) {
        CommandSourceStack source = context.getSource();
        if (source.getPlayer() == null) {
            source.sendFailure(Component.literal("This command is available only to players."));
            return 0;
        }
        if (species == null) {
            source.sendFailure(Component.literal("Usage: /pet adopt <cat|dog> <name>"));
        } else {
            source.sendFailure(Component.literal("Usage: /pet adopt "
                    + species.name().toLowerCase(Locale.ROOT) + " <name>"));
        }
        return 0;
    }

    private static void completeAdoption(
            CommandSourceStack source,
            UUID ownerUuid,
            String requestedName,
            PetAuthorityGateway gateway,
            PetAdoptionWireResult result,
            Throwable failure) {
        if (source.getServer().getPlayerList().getPlayer(ownerUuid) == null) {
            return;
        }
        if (failure != null || result == null) {
            source.sendFailure(Component.literal("Pet adoption is temporarily unavailable."));
            return;
        }
        if (result.status() == PetAdoptionWireStatus.CHECKOUT_REQUIRED) {
            URI checkoutUrl = URI.create(result.checkoutUrl().orElseThrow());
            source.sendSuccess(() -> Component.literal(
                    "An active subscription is required to adopt " + requestedName + ".")
                    .withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal("Open this checkout link within 15 minutes:")
                    .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> urlComponent(checkoutUrl), false);
            source.sendSuccess(() -> Component.literal(
                    requestedName + " will be adopted automatically after your subscription is confirmed.")
                    .withStyle(ChatFormatting.GRAY), false);
            watchAdoptionNotice(source.getServer().getPlayerList().getPlayer(ownerUuid), gateway);
            return;
        }
        if (result.status() == PetAdoptionWireStatus.CHECKOUT_IN_PROGRESS) {
            source.sendSuccess(() -> Component.literal(
                    "Your adoption checkout is already in progress. Complete it to adopt your pet automatically.")
                    .withStyle(ChatFormatting.YELLOW), false);
            watchAdoptionNotice(source.getServer().getPlayerList().getPlayer(ownerUuid), gateway);
            return;
        }
        if (result.status() == PetAdoptionWireStatus.CHECKOUT_RATE_LIMITED) {
            source.sendFailure(Component.literal(
                    "Too many checkout links were requested recently. Wait a few minutes, then retry /pet adopt."));
            return;
        }
        if (result.status() == PetAdoptionWireStatus.SUBSCRIPTION_REQUIRED) {
            source.sendSuccess(() -> Component.literal(
                    "An active subscription is needed first. Run /pet link to start checkout.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (result.status() == PetAdoptionWireStatus.SPECIES_UNAVAILABLE) {
            source.sendSuccess(
                    () -> Component.literal("That kind of pet isn't available right now.")
                            .withStyle(ChatFormatting.YELLOW),
                    false);
            return;
        }
        Pet pet = result.pet().orElseThrow();
        if (result.status() == PetAdoptionWireStatus.EXISTING) {
            rememberOwnership(source, ownerUuid, true);
            source.sendSuccess(
                    () -> Component.literal("You already own " + pet.name() + "; adoption did not reroll it.")
                            .withStyle(ChatFormatting.YELLOW),
                    false);
            return;
        }
        source.sendSuccess(
                () -> Component.literal("Adopted " + pet.name() + " the "
                                + pet.appearance().species().name().toLowerCase(Locale.ROOT) + ".")
                        .withStyle(ChatFormatting.GREEN),
                false);
        rememberOwnership(source, ownerUuid, true);
        source.sendSuccess(() -> Component.literal(
                "Use /pet place to bring your new friend into the world. To pick them up before leaving, "
                        + "hold Shift and right-click them (or use /pet pickup).")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(
                "Run /pet help whenever you want to see everything you can do together.")
                .withStyle(ChatFormatting.GRAY), false);
    }

    private static int compass(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        Optional<PetCompassManager> manager = PetCompanionMod.petCompassManager();
        if (gateway.isEmpty() || manager.isEmpty()) {
            source.sendFailure(Component.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }

        UUID ownerUuid = player.getUUID();
        source.sendSuccess(() -> Component.literal("Checking your pet compass…").withStyle(ChatFormatting.GRAY), false);
        try {
            gateway.orElseThrow().findByOwner(ownerUuid).whenComplete((snapshot, failure) ->
                    onServer(source, () -> completeCompass(
                            source,
                            ownerUuid,
                            manager.orElseThrow(),
                            snapshot,
                            failure)));
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal("Pet compass is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completeCompass(
            CommandSourceStack source,
            UUID ownerUuid,
            PetCompassManager manager,
            Optional<PetAuthoritySnapshot> snapshot,
            Throwable failure) {
        ServerPlayer player = source.getServer().getPlayerList().getPlayer(ownerUuid);
        if (player == null) {
            return;
        }
        if (failure != null || snapshot == null) {
            source.sendFailure(Component.literal("Pet compass is temporarily unavailable."));
            return;
        }
        if (snapshot.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("You have not adopted a pet yet.").withStyle(ChatFormatting.YELLOW),
                    false);
            return;
        }

        PetCompassIssueResult result = manager.issueOrRefresh(player, snapshot.orElseThrow());
        if (result.status() == com.silver.aipets.fabric.compass.PetCompassIssueStatus.INVENTORY_FULL) {
            source.sendSuccess(
                    () -> Component.literal("Your inventory is full; no pet compass was dropped.")
                            .withStyle(ChatFormatting.RED),
                    false);
            return;
        }
        String verb = result.status() == com.silver.aipets.fabric.compass.PetCompassIssueStatus.ISSUED
                ? "issued"
                : "refreshed";
        String cleanup = result.removedInvalidOrDuplicate() == 0
                ? ""
                : " (removed " + result.removedInvalidOrDuplicate() + " invalid/duplicate)";
        source.sendSuccess(
                () -> Component.literal("Pet compass " + verb + ": " + result.presentation() + cleanup)
                        .withStyle(ChatFormatting.GREEN),
                false);
        source.sendSuccess(() -> Component.literal(
                "Drop the pet compass whenever you no longer want it.")
                .withStyle(ChatFormatting.GRAY), false);
    }

    private static int place(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetPlacementCoordinator> configured = PetCompanionMod.placementCoordinator();
        if (configured.isEmpty()) {
            source.sendFailure(Component.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        UUID ownerUuid = player.getUUID();
        source.sendSuccess(() -> Component.literal("Placing your pet…").withStyle(ChatFormatting.GRAY), false);
        try {
            configured.orElseThrow().place(player).whenComplete((outcome, failure) ->
                    onServer(source, () -> completePlace(source, ownerUuid, outcome, failure)));
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal("Pet placement is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completePlace(
            CommandSourceStack source,
            UUID ownerUuid,
            PetPlacementOutcome outcome,
            Throwable failure) {
        if (source.getServer().getPlayerList().getPlayer(ownerUuid) == null) {
            return;
        }
        if (failure != null || outcome == null) {
            source.sendFailure(Component.literal("Pet placement is temporarily unavailable."));
            return;
        }
        if (outcome.status() == PetPlacementStatus.PLACED) {
            source.sendSuccess(
                    () -> Component.literal("Your pet has been placed safely.").withStyle(ChatFormatting.GREEN),
                    false);
            return;
        }
        String message = switch (outcome.status()) {
            case NO_PET -> "You have not adopted a pet yet.";
            case NOT_HELD -> "Your pet must be held before it can be placed.";
            case ALREADY_IN_PROGRESS -> "A pet placement is already in progress.";
            case NO_SAFE_POSITION -> "No safe loaded position was found nearby.";
            case PLAYER_CONTEXT_CHANGED -> "Placement stopped because your player context changed.";
            case SPAWN_FAILED_COMPENSATED -> "The pet could not spawn and was safely returned to held.";
            case SPAWN_FAILED_UNRESOLVED -> "The pet could not spawn; an administrator should reconcile it.";
            case AUTHORITY_REJECTED -> "Your pet's location changed; check /pet status and try again.";
            case SERVICE_FAILURE -> "Pet placement is temporarily unavailable.";
            case PLACED -> throw new IllegalStateException("Handled above");
        };
        source.sendFailure(Component.literal(message));
    }

    private static int pickup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetPickupCoordinator> configured = PetCompanionMod.pickupCoordinator();
        if (configured.isEmpty()) {
            source.sendFailure(Component.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        UUID ownerUuid = player.getUUID();
        source.sendSuccess(() -> Component.literal("Picking up your pet…").withStyle(ChatFormatting.GRAY), false);
        try {
            configured.orElseThrow().pickup(player).whenComplete((outcome, failure) ->
                    onServer(source, () -> completePickup(source, ownerUuid, outcome, failure)));
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal("Pet pickup is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completePickup(
            CommandSourceStack source,
            UUID ownerUuid,
            PetPickupOutcome outcome,
            Throwable failure) {
        if (source.getServer().getPlayerList().getPlayer(ownerUuid) == null) {
            return;
        }
        if (failure != null || outcome == null) {
            source.sendFailure(Component.literal("Pet pickup is temporarily unavailable."));
            return;
        }
        if (outcome.status() == PetPickupStatus.PICKED_UP) {
            source.sendSuccess(
                    () -> Component.literal("Your pet is now held.").withStyle(ChatFormatting.GREEN),
                    false);
            return;
        }
        String message = switch (outcome.status()) {
            case NO_PET -> "You have not adopted a pet yet.";
            case NOT_PLACED_HERE -> "Your pet is somewhere else right now.";
            case ENTITY_MISSING_OR_STALE -> "Your pet could not be found here; try /pet status.";
            case OUT_OF_RANGE -> "Move within 4 blocks of your pet to pick it up.";
            case AUTHORITY_REJECTED -> "Your pet's location changed; check /pet status and try again.";
            case SERVICE_FAILURE -> "Pet pickup is temporarily unavailable.";
            case PICKED_UP -> throw new IllegalStateException("Handled above");
        };
        source.sendFailure(Component.literal(message));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetAuthorityGateway> configured = PetCompanionMod.authorityGateway();
        if (configured.isEmpty()) {
            source.sendFailure(Component.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }

        UUID ownerUuid = player.getUUID();
        source.sendSuccess(() -> Component.literal("Checking pet status…").withStyle(ChatFormatting.GRAY), false);
        try {
            configured.orElseThrow().findByOwner(ownerUuid).whenComplete((snapshot, failure) ->
                    onServer(source, () -> completeStatus(source, ownerUuid, snapshot, failure)));
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal("Pet status is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completeStatus(
            CommandSourceStack source,
            UUID ownerUuid,
            Optional<PetAuthoritySnapshot> snapshot,
            Throwable failure) {
        ServerPlayer current = source.getServer().getPlayerList().getPlayer(ownerUuid);
        if (current == null) {
            return;
        }
        if (failure != null || snapshot == null) {
            source.sendFailure(Component.literal("Pet status is temporarily unavailable."));
            return;
        }
        if (snapshot.isEmpty()) {
            rememberOwnership(source, ownerUuid, false);
            source.sendSuccess(
                    () -> Component.literal("You have not adopted a pet yet.").withStyle(ChatFormatting.YELLOW),
                    false);
            return;
        }
        PetAuthoritySnapshot authority = snapshot.orElseThrow();
        rememberOwnership(source, ownerUuid, true);
        source.sendSuccess(
                () -> statusText(authority.pet(), authority.sleeping(), authority.aiAccessEnabled()),
                false);
    }

    static Component statusText(Pet pet, boolean sleeping, boolean aiAccessEnabled) {
        String location = switch (pet.placement()) {
            case PlacedPlacement placed -> "PLACED on " + placed.backendId()
                    + " in " + placed.dimensionId()
                    + " at " + coordinate(placed.position().x())
                    + ", " + coordinate(placed.position().y())
                    + ", " + coordinate(placed.position().z());
            case TransferringPlacement transferring -> "TRANSFERRING to "
                    + transferring.transfer().destinationBackendId();
            default -> "HELD";
        };
        String sleep = sleeping ? " • sleeping" : " • awake";
        String membership = sleeping
                ? " • quiet for now"
                : aiAccessEnabled ? " • ready to chat" : " • quiet for now";
        return Component.literal(pet.name() + " (" + pet.appearance().species() + ") — "
                        + location + sleep + membership)
                .withStyle(ChatFormatting.AQUA);
    }

    private static int adminInspect(CommandContext<CommandSourceStack> context) {
        UUID ownerUuid = ownerArgument(context);
        if (ownerUuid == null) return 0;
        Optional<PetAuthorityGateway> gateway = requireAdminGateway(context.getSource());
        if (gateway.isEmpty()) return 0;
        try {
            gateway.orElseThrow().findByOwner(ownerUuid).whenComplete((snapshot, failure) ->
                    onServer(context.getSource(), () -> completeAdminInspect(
                            context.getSource(), ownerUuid, snapshot, failure)));
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Pet inspection is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completeAdminInspect(
            CommandSourceStack source,
            UUID ownerUuid,
            Optional<PetAuthoritySnapshot> snapshot,
            Throwable failure) {
        if (failure != null || snapshot == null) {
            source.sendFailure(Component.literal("Pet inspection is temporarily unavailable."));
            return;
        }
        if (snapshot.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No pet exists for owner " + ownerUuid)
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        PetAuthoritySnapshot found = snapshot.orElseThrow();
        Pet pet = found.pet();
        source.sendSuccess(() -> Component.literal(
                "Owner=" + ownerUuid + " Pet=" + pet.petId() + " Revision=" + pet.recordVersion())
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> statusText(pet, found.sleeping(), found.aiAccessEnabled()), false);
    }

    private static int adminRecover(CommandContext<CommandSourceStack> context) {
        UUID ownerUuid = ownerArgument(context);
        if (ownerUuid == null) return 0;
        Optional<PetAuthorityGateway> gateway = requireAdminGateway(context.getSource());
        if (gateway.isEmpty()) return 0;
        try {
            gateway.orElseThrow().findByOwner(ownerUuid).whenComplete((snapshot, failure) ->
                    onServer(context.getSource(), () -> beginAdminRecovery(
                            context.getSource(), gateway.orElseThrow(), snapshot, failure)));
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Pet recovery is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void beginAdminRecovery(
            CommandSourceStack source,
            PetAuthorityGateway gateway,
            Optional<PetAuthoritySnapshot> snapshot,
            Throwable failure) {
        if (failure != null || snapshot == null) {
            source.sendFailure(Component.literal("Pet recovery is temporarily unavailable."));
            return;
        }
        if (snapshot.isEmpty()) {
            source.sendFailure(Component.literal("No pet exists for that owner."));
            return;
        }
        Pet pet = snapshot.orElseThrow().pet();
        if (pet.placementState() == com.silver.aipets.common.domain.PlacementState.HELD) {
            source.sendSuccess(() -> Component.literal("Pet is already safely held; no recovery needed.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        Instant now = Instant.now();
        Instant occurredAt = now.isBefore(pet.updatedAt()) ? pet.updatedAt() : now;
        try {
            gateway.adminRecover(UUID.randomUUID(), pet.petId(),
                    new com.silver.aipets.common.authority.PetTransitions.AdminRecover(
                            pet.recordVersion(), occurredAt)).whenComplete((result, mutationFailure) ->
                    onServer(source, () -> completeAdminRecovery(source, result, mutationFailure)));
        } catch (RuntimeException mutationFailure) {
            source.sendFailure(Component.literal("Pet recovery is temporarily unavailable."));
        }
    }

    private static void completeAdminRecovery(
            CommandSourceStack source, AuthorityMutationResult result, Throwable failure) {
        if (failure != null || result == null) {
            source.sendFailure(Component.literal("Pet recovery is temporarily unavailable."));
            return;
        }
        if (result.status() != AuthorityMutationStatus.APPLIED) {
            source.sendFailure(Component.literal(
                    "Recovery did not apply because authoritative state changed; inspect and retry."));
            return;
        }
        int queued = PetCompanionMod.queueLoadedReconciliation(source.getServer());
        source.sendSuccess(() -> Component.literal(
                "Pet recovered to HELD without changing identity or appearance; queued "
                        + queued + " loaded representation(s) for reconciliation.")
                .withStyle(ChatFormatting.GREEN), false);
    }

    private static int adminRecallReset(CommandContext<CommandSourceStack> context) {
        UUID ownerUuid = ownerArgument(context);
        if (ownerUuid == null) return 0;
        Optional<PetAuthorityGateway> gateway = requireAdminGateway(context.getSource());
        if (gateway.isEmpty()) return 0;
        try {
            gateway.orElseThrow().findByOwner(ownerUuid).whenComplete((snapshot, failure) ->
                    onServer(context.getSource(), () -> beginRecallReset(
                            context.getSource(), gateway.orElseThrow(), snapshot, failure)));
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Recall reset is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void beginRecallReset(
            CommandSourceStack source,
            PetAuthorityGateway gateway,
            Optional<PetAuthoritySnapshot> snapshot,
            Throwable failure) {
        if (failure != null || snapshot == null) {
            source.sendFailure(Component.literal("Recall reset is temporarily unavailable."));
            return;
        }
        if (snapshot.isEmpty()) {
            source.sendFailure(Component.literal("No pet exists for that owner."));
            return;
        }
        try {
            gateway.resetRecall(snapshot.orElseThrow().pet().petId())
                    .whenComplete((result, resetFailure) -> onServer(
                            source, () -> completeRecallReset(source, result, resetFailure)));
        } catch (RuntimeException resetFailure) {
            source.sendFailure(Component.literal("Recall reset is temporarily unavailable."));
        }
    }

    private static void completeRecallReset(
            CommandSourceStack source, RecallResetWireResult result, Throwable failure) {
        if (failure != null || result == null) {
            source.sendFailure(Component.literal("Recall reset is temporarily unavailable."));
            return;
        }
        String message = switch (result.status()) {
            case RESET -> "Recall entitlement reset for UTC period " + result.periodKey() + ".";
            case NOT_USED -> "Recall was not consumed for UTC period " + result.periodKey() + ".";
            case RATE_LIMITED -> "Too many reset requests; wait a few minutes.";
        };
        ChatFormatting color = result.status() == RecallResetWireStatus.RESET
                ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        source.sendSuccess(() -> Component.literal(message).withStyle(color), false);
    }

    private static int adminReconcile(CommandContext<CommandSourceStack> context) {
        int queued = PetCompanionMod.queueLoadedReconciliation(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal(
                "Queued " + queued + " loaded pet representation(s) for authority reconciliation.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int adminHistory(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID ownerUuid = ownerArgument(context);
        if (ownerUuid == null) return 0;
        Optional<PetAuthorityGateway> gateway = requireAdminGateway(source);
        if (gateway.isEmpty()) return 0;
        source.sendSuccess(() -> Component.literal(
                "Loading recent conversation history for " + ownerUuid + "…")
                .withStyle(ChatFormatting.GRAY), false);
        try {
            gateway.orElseThrow().findDialogueHistory(ownerUuid).whenComplete((history, failure) ->
                    onServer(source, () -> completeAdminHistory(source, history, failure)));
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal("Conversation history is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completeAdminHistory(
            CommandSourceStack source,
            DialogueHistoryWireResult history,
            Throwable failure) {
        if (failure != null || history == null) {
            source.sendFailure(Component.literal("Conversation history is temporarily unavailable."));
            return;
        }
        source.sendSuccess(() -> Component.literal(
                "DIALOGUE HISTORY — " + history.petName() + " (owner " + history.ownerUuid() + ")")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        if (history.conversations().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No retained conversation entries.")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            history.conversations().forEach(entry -> {
                source.sendSuccess(() -> Component.literal(
                        formatUtc(entry.occurredAt()) + " [" + entry.importance() + "] "
                                + "owner: " + (entry.ownerText() == null ? "(expired)" : entry.ownerText()))
                        .withStyle(ChatFormatting.GRAY), false);
                if (entry.petReply() != null) {
                    source.sendSuccess(() -> Component.literal("  pet: " + entry.petReply())
                            .withStyle(ChatFormatting.WHITE), false);
                }
            });
        }
        source.sendSuccess(() -> Component.literal("PROVIDER USAGE")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        if (history.usage().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No provider usage recorded.")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        history.usage().forEach(entry -> {
            source.sendSuccess(() -> Component.literal(
                    formatUtc(entry.createdAt()) + " " + entry.operation() + " " + entry.model()
                            + " — input=" + entry.inputTokens()
                            + " cached=" + entry.cachedInputTokens()
                            + " output=" + entry.outputTokens()
                            + " cost=" + usd(entry.estimatedCost())
                    + " status=" + entry.status())
                    .withStyle(ChatFormatting.GRAY), false);
            if (entry.context().isPresent()) {
                source.sendSuccess(() -> Component.literal(
                        "  prompt parts: " + formatContext(entry.context().orElseThrow()))
                        .withStyle(ChatFormatting.DARK_GRAY), false);
            } else {
                source.sendSuccess(() -> Component.literal(
                        "  prompt parts: not captured for this older/provider-only usage record")
                        .withStyle(ChatFormatting.DARK_GRAY), false);
            }
        });
    }

    private static String formatContext(DialogueContextUsageWire context) {
        return "total=" + context.totalPromptTokens()
                + " [system " + context.systemTokens()
                + ", identity " + context.identityTokens()
                + ", short-term DB " + context.shortTermDbTokens()
                + ", long-term relational DB " + context.longTermRelationalTokens()
                + ", long-term vector DB " + context.longTermVectorTokens()
                + ", recent turns DB " + context.recentTurnsDbTokens()
                + ", game " + context.gameContextTokens()
                + ", owner input " + context.ownerInputTokens()
                + ", instructions " + context.instructionTokens() + "]";
    }

    private static UUID ownerArgument(CommandContext<CommandSourceStack> context) {
        String value;
        try {
            value = StringArgumentType.getString(context, "ownerUuid");
        } catch (IllegalArgumentException missing) {
            ServerPlayer player = context.getSource().getPlayer();
            if (player == null) {
                context.getSource().sendFailure(Component.literal(
                        "ownerUuid is required when running this command from the console."));
                return null;
            }
            return player.getUUID();
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) throw new IllegalArgumentException();
            return parsed;
        } catch (IllegalArgumentException malformed) {
            context.getSource().sendFailure(Component.literal("ownerUuid must be a canonical UUID."));
            return null;
        }
    }

    private static Optional<PetAuthorityGateway> requireAdminGateway(CommandSourceStack source) {
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        if (gateway.isEmpty()) {
            source.sendFailure(Component.literal("Pet Companion is temporarily unavailable."));
        }
        return gateway;
    }

    private static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void onServer(CommandSourceStack source, Runnable action) {
        Runnable guarded = () -> {
            try {
                action.run();
            } catch (RuntimeException failure) {
                PetCompanionMod.LOGGER.warn(StructuredPetEvent
                        .operation("command_completion")
                        .failure(failure)
                        .outcome("contained")
                        .toJson());
                source.sendFailure(Component.literal("Pet operation failed safely; please retry."));
            }
        };
        if (source.getServer().isSameThread()) {
            guarded.run();
        } else {
            try {
                source.getServer().execute(guarded);
            } catch (RuntimeException failure) {
                PetCompanionMod.LOGGER.warn(StructuredPetEvent
                        .operation("command_completion_schedule")
                        .failure(failure)
                        .outcome("contained")
                        .toJson());
            }
        }
    }
}
