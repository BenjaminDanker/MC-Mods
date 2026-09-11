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
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.util.Formatting;

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

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

/** Player command surface; physical mutations delegate to commit-safe async coordinators. */
public final class PetCommands {
    private static final Map<UUID, Boolean> HAS_PET = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> BILLING_WATCHES = new ConcurrentHashMap<>();

    private PetCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("pet")
                .executes(PetCommands::help)
                .then(literal("help")
                        .requires(PetCommands::hasPetAccess)
                        .executes(PetCommands::help))
                .then(literal("status")
                        .requires(source -> hasPetAccess(source)
                                && PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::status))
                .then(literal("link")
                        .requires(source -> hasPetAccess(source)
                                && PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::link))
                .then(literal("portal")
                        .requires(source -> hasPetAccess(source)
                                && PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::portal))
                .then(literal("billing")
                        .requires(source -> hasPetAccess(source)
                                && PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::billing))
                .then(literal("adopt")
                        .requires(source -> !hasPetAccess(source)
                                && PetPermissions.check(source, PetPermission.ADOPT))
                        .executes(context -> adoptionMenu(context, null))
                        .then(literal("cat")
                                .executes(context -> adoptionMenu(context, PetSpecies.CAT))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(context -> adopt(context, PetSpecies.CAT))))
                        .then(literal("dog")
                                .executes(context -> adoptionMenu(context, PetSpecies.DOG))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(context -> adopt(context, PetSpecies.DOG))))
                        .then(literal("subscribe")
                                .executes(PetCommands::link)))
                .then(literal("place")
                        .requires(source -> hasPetAccess(source)
                                && PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::place))
                .then(literal("pickup")
                        .requires(source -> hasPetAccess(source)
                                && PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::pickup))
                .then(literal("recall")
                        .requires(source -> hasPetAccess(source)
                                && PetPermissions.check(source, PetPermission.RECALL))
                        .executes(PetCommands::recall))
                .then(literal("compass")
                        .requires(source -> hasPetAccess(source)
                                && PetPermissions.check(source, PetPermission.COMPASS))
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

    private static int help(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        boolean ownsPet = hasPetAccess(source);
        boolean canUse = ownsPet && PetPermissions.check(source, PetPermission.USE);
        StringBuilder index = new StringBuilder("Pet Companion:");
        if (canUse) {
            index.append(" /pet status, /pet billing, /pet place, /pet pickup");
        }
        if (!ownsPet && PetPermissions.check(source, PetPermission.ADOPT)) {
            index.append(", /pet adopt <cat|dog> <name>");
        }
        if (PetPermissions.check(source, PetPermission.RECALL)) {
            index.append(", /pet recall");
        }
        if (PetPermissions.check(source, PetPermission.COMPASS)) {
            index.append(", /pet compass");
        }
        if (hasAdminAccess(source)) {
            index.append(", /pet admin …");
        }
        source.sendFeedback(
                () -> Text.literal(index.toString())
                        .formatted(Formatting.AQUA),
                false);
        if (canUse) {
            source.sendFeedback(() -> Text.empty()
                    .append(Text.literal("VIEW     ").formatted(Formatting.YELLOW, Formatting.BOLD))
                    .append(commandAction("[STATUS]", "/pet status")), false);
            source.sendFeedback(() -> Text.empty()
                    .append(Text.literal("BILLING  ").formatted(Formatting.YELLOW, Formatting.BOLD))
                    .append(commandAction("[OPEN BILLING MENU]", "/pet billing")), false);
            source.sendFeedback(() -> Text.empty()
                    .append(Text.literal("PET      ").formatted(Formatting.YELLOW, Formatting.BOLD))
                    .append(commandAction("[PLACE]", "/pet place"))
                    .append(Text.literal("  "))
                    .append(commandAction("[PICKUP]", "/pet pickup")), false);
            source.sendFeedback(() -> Text.literal(
                    "Tip: hold Shift and right-click your pet to pick them up quickly.")
                    .formatted(Formatting.GRAY), false);
        }
        if (!ownsPet && PetPermissions.check(source, PetPermission.ADOPT)) {
            source.sendFeedback(() -> Text.empty()
                    .append(Text.literal("ADOPTION ").formatted(Formatting.YELLOW, Formatting.BOLD))
                    .append(commandAction("[OPEN MENU]", "/pet adopt"))
                    .append(Text.literal("  Choose a species, then name it.")), false);
        }
        if (PetPermissions.check(source, PetPermission.RECALL)
                || PetPermissions.check(source, PetPermission.COMPASS)) {
            source.sendFeedback(() -> Text.empty()
                    .append(Text.literal("TOOLS    ").formatted(Formatting.YELLOW, Formatting.BOLD))
                    .append(PetPermissions.check(source, PetPermission.RECALL)
                            ? commandAction("[RECALL]", "/pet recall")
                            : Text.empty())
                    .append(PetPermissions.check(source, PetPermission.RECALL)
                            && PetPermissions.check(source, PetPermission.COMPASS)
                            ? Text.literal("  ")
                            : Text.empty())
                    .append(PetPermissions.check(source, PetPermission.COMPASS)
                            ? commandAction("[COMPASS]", "/pet compass")
                            : Text.empty()), false);
        }
        if (hasAdminAccess(source)) {
            source.sendFeedback(() -> Text.literal(
                    "ADMIN    /pet admin inspect|recover|recall-reset|history|reconcile (permission-gated; UUID defaults to you)")
                    .formatted(Formatting.DARK_GRAY), false);
        }
        return 1;
    }

    private static int link(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        if (gateway.isEmpty()) {
            source.sendError(Text.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        UUID ownerUuid = player.getUuid();
        source.sendFeedback(
                () -> Text.literal("Checking your subscription status…")
                        .formatted(Formatting.GRAY),
                false);
        try {
            gateway.orElseThrow().findSubscriptionDetails(ownerUuid).whenComplete((details, statusFailure) ->
                    onServer(source, () -> {
                        if (statusFailure != null || details == null) {
                            source.sendError(Text.literal(
                                    "Subscription status is temporarily unavailable; no checkout link was created."));
                            return;
                        }
                        if (details.aiAccessEnabled()) {
                            source.sendFeedback(() -> Text.literal("SUBSCRIPTION ALREADY ACTIVE")
                                    .formatted(Formatting.YELLOW, Formatting.BOLD), false);
                            source.sendFeedback(() -> Text.empty()
                                    .append(Text.literal(
                                            "No new checkout was created. " + billingStateText(details)
                                                    + " Manage it in ")
                                            .formatted(Formatting.GRAY))
                                    .append(commandAction("[STRIPE BILLING PORTAL]", "/pet portal"))
                                    .append(Text.literal(".")), false);
                            return;
                        }
                        source.sendFeedback(() -> Text.literal("Creating your secure pet checkout link…")
                                .formatted(Formatting.GRAY), false);
                        try {
                            gateway.orElseThrow().createAccountLink(ownerUuid).whenComplete((result, failure) ->
                                    onServer(source, () -> completeLink(
                                            source, ownerUuid, gateway.orElseThrow(), details,
                                            result, failure)));
                        } catch (RuntimeException failure) {
                            source.sendError(Text.literal("Account linking is temporarily unavailable."));
                        }
                    }));
        } catch (RuntimeException failure) {
            source.sendError(Text.literal(
                    "Subscription status is temporarily unavailable; no checkout link was created."));
            return 0;
        }
        return 1;
    }

    private static void completeLink(
            ServerCommandSource source,
            UUID ownerUuid,
            PetAuthorityGateway gateway,
            SubscriptionAccessWireResult baseline,
            AccountLinkWireResult result,
            Throwable failure) {
        if (source.getServer().getPlayerManager().getPlayer(ownerUuid) == null) return;
        if (failure != null || result == null) {
            source.sendError(Text.literal("Account linking is temporarily unavailable."));
            return;
        }
        if (result.status() == AccountLinkWireStatus.RATE_LIMITED) {
            source.sendFeedback(
                    () -> Text.literal("Too many checkout links were requested; wait a few minutes.")
                            .formatted(Formatting.YELLOW),
                    false);
            return;
        }
        URI checkoutUrl = URI.create(result.checkoutUrl().orElseThrow());
        source.sendFeedback(() -> Text.literal("STRIPE CHECKOUT")
                .formatted(Formatting.GREEN, Formatting.BOLD), false);
        source.sendFeedback(() -> Text.empty()
                        .append(Text.literal("Open the one checkout link below, finish payment in your browser, then return to Minecraft. ")
                                .formatted(Formatting.GRAY))
                        .append(Text.literal("[OPEN STRIPE CHECKOUT]")
                                .formatted(Formatting.AQUA, Formatting.UNDERLINE)
                                .styled(style -> style.withClickEvent(
                                        new ClickEvent.OpenUrl(checkoutUrl))))
                        .append(Text.literal(" (expires "
                                + formatUtc(result.expiresAt().orElseThrow()) + ")")
                                .formatted(Formatting.GRAY)), false);
        watchBilling(source, ownerUuid, gateway, baseline);
    }

    private static int portal(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        if (gateway.isEmpty()) {
            source.sendError(Text.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        UUID ownerUuid = player.getUuid();
        source.sendFeedback(() -> Text.literal("Preparing your secure billing page…")
                .formatted(Formatting.GRAY), false);
        try {
            gateway.orElseThrow().findSubscriptionDetails(ownerUuid).whenComplete((baseline, statusFailure) ->
                    onServer(source, () -> {
                        if (statusFailure != null || baseline == null) {
                            source.sendError(Text.literal("Billing is temporarily unavailable; please retry."));
                            return;
                        }
                        gateway.orElseThrow().createCustomerPortal(ownerUuid).whenComplete((result, failure) ->
                                onServer(source, () -> completePortal(
                                        source, ownerUuid, gateway.orElseThrow(), baseline,
                                        result, failure)));
                    }));
        } catch (RuntimeException failure) {
            source.sendError(Text.literal("Billing management is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static int billing(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        if (gateway.isEmpty()) {
            source.sendError(Text.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        UUID ownerUuid = player.getUuid();
        source.sendFeedback(() -> Text.literal("BILLING — checking your subscription…")
                .formatted(Formatting.GRAY), false);
        try {
            gateway.orElseThrow().findSubscriptionDetails(ownerUuid).whenComplete((details, failure) ->
                    onServer(source, () -> completeBilling(source, ownerUuid, details, failure)));
        } catch (RuntimeException failure) {
            source.sendError(Text.literal("Subscription status is temporarily unavailable; please retry."));
            return 0;
        }
        return 1;
    }

    private static void completeBilling(
            ServerCommandSource source,
            UUID ownerUuid,
            SubscriptionAccessWireResult details,
            Throwable failure) {
        if (source.getServer().getPlayerManager().getPlayer(ownerUuid) == null) return;
        if (failure != null || details == null) {
            source.sendError(Text.literal("Subscription status is temporarily unavailable; please retry."));
            return;
        }
        source.sendFeedback(() -> Text.literal("BILLING")
                .formatted(Formatting.AQUA, Formatting.BOLD), false);
        if (hasAdminAccess(source)
                && details.budgetUsd() != null
                && details.remainingUsd() != null) {
            source.sendFeedback(() -> Text.literal(
                    "Admin quota: " + usd(details.budgetUsd())
                            + " total • " + usd(details.remainingUsd()) + " remaining"
                            + (details.consumedUsd() == null
                            ? "" : " • " + usd(details.consumedUsd()) + " consumed"))
                    .formatted(Formatting.LIGHT_PURPLE), false);
        }
        if (details.aiAccessEnabled()) {
            source.sendFeedback(() -> Text.literal(
                    "Your subscription is active. " + billingStateText(details))
                    .formatted(Formatting.GRAY), false);
            source.sendFeedback(() -> Text.empty()
                    .append(Text.literal("Next action: ").formatted(Formatting.GRAY))
                    .append(commandAction("[MANAGE / CANCEL IN STRIPE]", "/pet portal")), false);
        } else {
            source.sendFeedback(() -> Text.literal(
                    "No active subscription is linked to this Minecraft account.")
                    .formatted(Formatting.GRAY), false);
            source.sendFeedback(() -> Text.empty()
                    .append(Text.literal("Next action: ").formatted(Formatting.GRAY))
                    .append(commandAction("[START SUBSCRIPTION]", "/pet link")), false);
        }
    }

    private static String usd(java.math.BigDecimal value) {
        return "$" + value.stripTrailingZeros().toPlainString();
    }

    private static void completePortal(
            ServerCommandSource source,
            UUID ownerUuid,
            PetAuthorityGateway gateway,
            SubscriptionAccessWireResult baseline,
            CustomerPortalWireResult result,
            Throwable failure) {
        if (source.getServer().getPlayerManager().getPlayer(ownerUuid) == null) return;
        if (failure != null || result == null) {
            source.sendError(Text.literal("Billing management is temporarily unavailable."));
            return;
        }
        if (result.status() == CustomerPortalWireStatus.NOT_LINKED) {
            source.sendFeedback(() -> Text.literal("NO BILLING ACCOUNT LINKED")
                    .formatted(Formatting.YELLOW, Formatting.BOLD), false);
            source.sendFeedback(() -> Text.empty()
                    .append(Text.literal("Start checkout first: ").formatted(Formatting.GRAY))
                    .append(commandAction("[SUBSCRIBE]", "/pet link")), false);
            return;
        }
        if (result.status() == CustomerPortalWireStatus.RATE_LIMITED) {
            source.sendFeedback(
                    () -> Text.literal("Too many portal links were requested; wait a few minutes.")
                            .formatted(Formatting.YELLOW),
                    false);
            return;
        }
        URI portalUrl = URI.create(result.portalUrl().orElseThrow());
        source.sendFeedback(() -> Text.literal("BILLING PORTAL READY")
                .formatted(Formatting.GREEN, Formatting.BOLD), false);

        source.sendFeedback(() -> Text.empty()
                .append(Text.literal("[OPEN STRIPE BILLING PORTAL]")
                        .formatted(Formatting.AQUA, Formatting.UNDERLINE)
                        .styled(style -> style.withClickEvent(
                                new ClickEvent.OpenUrl(portalUrl)))), false);
        watchBilling(source, ownerUuid, gateway, baseline);
    }

    /** Refreshes the command tree after the authoritative ownership read completes. */
    public static void refreshVisibility(ServerPlayerEntity player) {
        Objects.requireNonNull(player, "player");
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        if (gateway.isEmpty()) return;
        UUID ownerUuid = player.getUuid();
        gateway.orElseThrow().findByOwner(ownerUuid).whenComplete((snapshot, failure) -> {
            if (failure != null || snapshot == null) return;
            player.getEntityWorld().getServer().execute(() -> {
                ServerPlayerEntity current = player.getEntityWorld().getServer()
                        .getPlayerManager().getPlayer(ownerUuid);
                if (current == null) return;
                HAS_PET.put(ownerUuid, snapshot.isPresent());
                current.getEntityWorld().getServer().getCommandManager().sendCommandTree(current);
            });
        });
    }

    public static void clearPlayerState(UUID ownerUuid) {
        HAS_PET.remove(ownerUuid);
        BILLING_WATCHES.remove(ownerUuid);
    }

    private static boolean hasPetAccess(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        return player == null || Boolean.TRUE.equals(HAS_PET.get(player.getUuid()));
    }

    private static boolean hasAdminAccess(ServerCommandSource source) {
        return PetPermissions.check(source, PetPermission.ADMIN_INSPECT)
                || PetPermissions.check(source, PetPermission.ADMIN_RECOVER)
                || PetPermissions.check(source, PetPermission.ADMIN_RECONCILE)
                || PetPermissions.check(source, PetPermission.ADMIN_SUBSCRIPTION)
                || PetPermissions.check(source, PetPermission.ADMIN_MEMORY);
    }

    private static void rememberOwnership(
            ServerCommandSource source, UUID ownerUuid, boolean hasPet) {
        HAS_PET.put(ownerUuid, hasPet);
        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (player != null) source.getServer().getCommandManager().sendCommandTree(player);
    }

    private static void watchBilling(
            ServerCommandSource source,
            UUID ownerUuid,
            PetAuthorityGateway gateway,
            SubscriptionAccessWireResult baseline) {
        UUID watchId = UUID.randomUUID();
        BILLING_WATCHES.put(ownerUuid, watchId);
        pollBilling(source, ownerUuid, gateway, baseline, watchId, 0);
    }

    private static void pollBilling(
            ServerCommandSource source,
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
                            ServerPlayerEntity player = source.getServer().getPlayerManager()
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
            ServerCommandSource source,
            SubscriptionAccessWireResult before,
            SubscriptionAccessWireResult after) {
        if (!before.aiAccessEnabled() && after.aiAccessEnabled()) {
            source.sendFeedback(() -> Text.empty()
                    .append(Text.literal("Your membership is active! ")
                            .formatted(Formatting.GREEN))
                    .append(commandAction("[CONTINUE TO ADOPTION]", "/pet adopt")), false);
            return;
        }
        if (after.cancelAtPeriodEnd()) {
            source.sendFeedback(() -> Text.literal("Cancellation scheduled. "
                    + billingStateText(after)).formatted(Formatting.YELLOW), false);
            return;
        }
        if (!after.aiAccessEnabled() || "CANCELED".equals(after.status())) {
            source.sendFeedback(() -> Text.literal("Your subscription has been cancelled.")
                    .formatted(Formatting.YELLOW), false);
        }
    }

    private static Text commandAction(String label, String command) {
        return Text.literal(label)
                .formatted(Formatting.GREEN, Formatting.UNDERLINE)
                .styled(style -> style.withClickEvent(new ClickEvent.RunCommand(command)));
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

    private static int recall(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetRecallCoordinator> configured = PetCompanionMod.recallCoordinator();
        if (configured.isEmpty()) {
            source.sendError(Text.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        UUID ownerUuid = player.getUuid();
        source.sendFeedback(() -> Text.literal("Recalling your pet…").formatted(Formatting.GRAY), false);
        try {
            configured.orElseThrow().recall(player).whenComplete((outcome, failure) ->
                    onServer(source, () -> completeRecall(source, ownerUuid, outcome, failure)));
        } catch (RuntimeException failure) {
            source.sendError(Text.literal("Pet recall is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completeRecall(
            ServerCommandSource source,
            UUID ownerUuid,
            PetRecallOutcome outcome,
            Throwable failure) {
        if (source.getServer().getPlayerManager().getPlayer(ownerUuid) == null) return;
        if (failure != null || outcome == null) {
            source.sendError(Text.literal("Pet recall is temporarily unavailable."));
            return;
        }
        if (outcome.status() == PetRecallStatus.RECALLED) {
            source.sendFeedback(
                    () -> Text.literal("Your pet was recalled safely. Next recall: "
                                    + formatUtc(outcome.nextAvailableAt().orElseThrow()))
                            .formatted(Formatting.GREEN),
                    false);
            return;
        }
        if (outcome.status() == PetRecallStatus.UNAVAILABLE) {
            source.sendFeedback(
                    () -> Text.literal("This month's recall is already used. Next recall: "
                                    + formatUtc(outcome.nextAvailableAt().orElseThrow()))
                            .formatted(Formatting.YELLOW),
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
        source.sendError(Text.literal(message));
    }

    private static String formatUtc(java.time.Instant value) {
        return DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'UTC'", Locale.ROOT)
                .withZone(ZoneOffset.UTC)
                .format(value);
    }

    private static int adopt(CommandContext<ServerCommandSource> context, PetSpecies species) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        if (gateway.isEmpty()) {
            source.sendError(Text.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        PetAdoptionWireRequest request;
        try {
            request = new PetAdoptionWireRequest(
                    player.getUuid(),
                    species,
                    StringArgumentType.getString(context, "name"));
        } catch (IllegalArgumentException invalidName) {
            source.sendError(Text.literal(invalidName.getMessage()));
            return 0;
        }

        UUID ownerUuid = player.getUuid();
        source.sendFeedback(() -> Text.literal("Preparing your adoption…").formatted(Formatting.GRAY), false);
        try {
            gateway.orElseThrow().adopt(request).whenComplete((result, failure) ->
                    onServer(source, () -> completeAdoption(source, ownerUuid, result, failure)));
        } catch (RuntimeException failure) {
            source.sendError(Text.literal("Pet adoption is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static int adoptionMenu(CommandContext<ServerCommandSource> context, PetSpecies species) {
        ServerCommandSource source = context.getSource();
        if (source.getPlayer() == null) {
            source.sendError(Text.literal("This command is available only to players."));
            return 0;
        }
        if (species == null) {
            Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
            if (gateway.isEmpty()) {
                source.sendError(Text.literal("Pet Companion is temporarily unavailable."));
                return 0;
            }
            UUID ownerUuid = source.getPlayer().getUuid();
            source.sendFeedback(() -> Text.literal("Opening the adoption center…")
                    .formatted(Formatting.GRAY), false);
            try {
                gateway.orElseThrow().findSubscriptionDetails(ownerUuid).whenComplete((details, failure) ->
                        onServer(source, () -> {
                            if (source.getServer().getPlayerManager().getPlayer(ownerUuid) == null) return;
                            if (failure != null || details == null) {
                                source.sendError(Text.literal(
                                        "Subscription status is temporarily unavailable; please retry."));
                                return;
                            }
                            PetAdoptionMenu.open(source, details.aiAccessEnabled());
                        }));
            } catch (RuntimeException failure) {
                source.sendError(Text.literal("Subscription status is temporarily unavailable; please retry."));
                return 0;
            }
        } else {
            PetAdoptionMenu.choose(source, species);
        }
        return 1;
    }

    private static void completeAdoption(
            ServerCommandSource source,
            UUID ownerUuid,
            PetAdoptionWireResult result,
            Throwable failure) {
        if (source.getServer().getPlayerManager().getPlayer(ownerUuid) == null) {
            return;
        }
        if (failure != null || result == null) {
            source.sendError(Text.literal("Pet adoption is temporarily unavailable."));
            return;
        }
        if (result.status() == PetAdoptionWireStatus.SUBSCRIPTION_REQUIRED) {
            source.sendFeedback(
                    () -> Text.empty()
                            .append(Text.literal("A pet membership is needed first. "))
                            .append(commandAction("[CONTINUE TO SUBSCRIPTION]", "/pet adopt subscribe"))
                            .formatted(Formatting.YELLOW),
                    false);
            return;
        }
        if (result.status() == PetAdoptionWireStatus.SPECIES_UNAVAILABLE) {
            source.sendFeedback(
                    () -> Text.literal("That kind of pet isn't available right now.")
                            .formatted(Formatting.YELLOW),
                    false);
            return;
        }
        Pet pet = result.pet().orElseThrow();
        if (result.status() == PetAdoptionWireStatus.EXISTING) {
            rememberOwnership(source, ownerUuid, true);
            source.sendFeedback(
                    () -> Text.literal("You already own " + pet.name() + "; adoption did not reroll it.")
                            .formatted(Formatting.YELLOW),
                    false);
            return;
        }
        source.sendFeedback(
                () -> Text.literal("Adopted " + pet.name() + " the "
                                + pet.appearance().species().name().toLowerCase(Locale.ROOT) + ".")
                        .formatted(Formatting.GREEN),
                false);
        rememberOwnership(source, ownerUuid, true);
        source.sendFeedback(() -> Text.literal(
                "Use /pet place to bring your new friend into the world. To pick them up before leaving, "
                        + "hold Shift and right-click them (or use /pet pickup).")
                .formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal(
                "Run /pet help whenever you want to see everything you can do together.")
                .formatted(Formatting.GRAY), false);
    }

    private static int compass(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        Optional<PetCompassManager> manager = PetCompanionMod.petCompassManager();
        if (gateway.isEmpty() || manager.isEmpty()) {
            source.sendError(Text.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }

        UUID ownerUuid = player.getUuid();
        source.sendFeedback(() -> Text.literal("Checking your pet compass…").formatted(Formatting.GRAY), false);
        try {
            gateway.orElseThrow().findByOwner(ownerUuid).whenComplete((snapshot, failure) ->
                    onServer(source, () -> completeCompass(
                            source,
                            ownerUuid,
                            manager.orElseThrow(),
                            snapshot,
                            failure)));
        } catch (RuntimeException failure) {
            source.sendError(Text.literal("Pet compass is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completeCompass(
            ServerCommandSource source,
            UUID ownerUuid,
            PetCompassManager manager,
            Optional<PetAuthoritySnapshot> snapshot,
            Throwable failure) {
        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (player == null) {
            return;
        }
        if (failure != null || snapshot == null) {
            source.sendError(Text.literal("Pet compass is temporarily unavailable."));
            return;
        }
        if (snapshot.isEmpty()) {
            source.sendFeedback(
                    () -> Text.literal("You have not adopted a pet yet.").formatted(Formatting.YELLOW),
                    false);
            return;
        }

        PetCompassIssueResult result = manager.issueOrRefresh(player, snapshot.orElseThrow());
        if (result.status() == com.silver.aipets.fabric.compass.PetCompassIssueStatus.INVENTORY_FULL) {
            source.sendFeedback(
                    () -> Text.literal("Your inventory is full; no pet compass was dropped.")
                            .formatted(Formatting.RED),
                    false);
            return;
        }
        String verb = result.status() == com.silver.aipets.fabric.compass.PetCompassIssueStatus.ISSUED
                ? "issued"
                : "refreshed";
        String cleanup = result.removedInvalidOrDuplicate() == 0
                ? ""
                : " (removed " + result.removedInvalidOrDuplicate() + " invalid/duplicate)";
        source.sendFeedback(
                () -> Text.literal("Pet compass " + verb + ": " + result.presentation() + cleanup)
                        .formatted(Formatting.GREEN),
                false);
        source.sendFeedback(() -> Text.literal(
                "Drop the pet compass whenever you no longer want it.")
                .formatted(Formatting.GRAY), false);
    }

    private static int place(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetPlacementCoordinator> configured = PetCompanionMod.placementCoordinator();
        if (configured.isEmpty()) {
            source.sendError(Text.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        UUID ownerUuid = player.getUuid();
        source.sendFeedback(() -> Text.literal("Placing your pet…").formatted(Formatting.GRAY), false);
        try {
            configured.orElseThrow().place(player).whenComplete((outcome, failure) ->
                    onServer(source, () -> completePlace(source, ownerUuid, outcome, failure)));
        } catch (RuntimeException failure) {
            source.sendError(Text.literal("Pet placement is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completePlace(
            ServerCommandSource source,
            UUID ownerUuid,
            PetPlacementOutcome outcome,
            Throwable failure) {
        if (source.getServer().getPlayerManager().getPlayer(ownerUuid) == null) {
            return;
        }
        if (failure != null || outcome == null) {
            source.sendError(Text.literal("Pet placement is temporarily unavailable."));
            return;
        }
        if (outcome.status() == PetPlacementStatus.PLACED) {
            source.sendFeedback(
                    () -> Text.literal("Your pet has been placed safely.").formatted(Formatting.GREEN),
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
        source.sendError(Text.literal(message));
    }

    private static int pickup(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetPickupCoordinator> configured = PetCompanionMod.pickupCoordinator();
        if (configured.isEmpty()) {
            source.sendError(Text.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }
        UUID ownerUuid = player.getUuid();
        source.sendFeedback(() -> Text.literal("Picking up your pet…").formatted(Formatting.GRAY), false);
        try {
            configured.orElseThrow().pickup(player).whenComplete((outcome, failure) ->
                    onServer(source, () -> completePickup(source, ownerUuid, outcome, failure)));
        } catch (RuntimeException failure) {
            source.sendError(Text.literal("Pet pickup is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completePickup(
            ServerCommandSource source,
            UUID ownerUuid,
            PetPickupOutcome outcome,
            Throwable failure) {
        if (source.getServer().getPlayerManager().getPlayer(ownerUuid) == null) {
            return;
        }
        if (failure != null || outcome == null) {
            source.sendError(Text.literal("Pet pickup is temporarily unavailable."));
            return;
        }
        if (outcome.status() == PetPickupStatus.PICKED_UP) {
            source.sendFeedback(
                    () -> Text.literal("Your pet is now held.").formatted(Formatting.GREEN),
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
        source.sendError(Text.literal(message));
    }

    private static int status(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command is available only to players."));
            return 0;
        }
        Optional<PetAuthorityGateway> configured = PetCompanionMod.authorityGateway();
        if (configured.isEmpty()) {
            source.sendError(Text.literal("Pet Companion is temporarily unavailable."));
            return 0;
        }

        UUID ownerUuid = player.getUuid();
        source.sendFeedback(() -> Text.literal("Checking pet status…").formatted(Formatting.GRAY), false);
        try {
            configured.orElseThrow().findByOwner(ownerUuid).whenComplete((snapshot, failure) ->
                    onServer(source, () -> completeStatus(source, ownerUuid, snapshot, failure)));
        } catch (RuntimeException failure) {
            source.sendError(Text.literal("Pet status is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completeStatus(
            ServerCommandSource source,
            UUID ownerUuid,
            Optional<PetAuthoritySnapshot> snapshot,
            Throwable failure) {
        ServerPlayerEntity current = source.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (current == null) {
            return;
        }
        if (failure != null || snapshot == null) {
            source.sendError(Text.literal("Pet status is temporarily unavailable."));
            return;
        }
        if (snapshot.isEmpty()) {
            rememberOwnership(source, ownerUuid, false);
            source.sendFeedback(
                    () -> Text.literal("You have not adopted a pet yet.").formatted(Formatting.YELLOW),
                    false);
            return;
        }
        PetAuthoritySnapshot authority = snapshot.orElseThrow();
        rememberOwnership(source, ownerUuid, true);
        source.sendFeedback(
                () -> statusText(
                        authority.pet(), authority.sleeping(), authority.aiAccessEnabled()),
                false);
    }

    static Text statusText(Pet pet, boolean sleeping, boolean aiAccessEnabled) {
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
                ? " • resting until they wake up"
                : aiAccessEnabled ? " • ready to chat" : " • quiet for now";
        return Text.literal(pet.name() + " (" + pet.appearance().species() + ") — "
                        + location + sleep + membership)
                .formatted(Formatting.AQUA);
    }

    private static int adminInspect(CommandContext<ServerCommandSource> context) {
        UUID ownerUuid = ownerArgument(context);
        if (ownerUuid == null) return 0;
        Optional<PetAuthorityGateway> gateway = requireAdminGateway(context.getSource());
        if (gateway.isEmpty()) return 0;
        try {
            gateway.orElseThrow().findByOwner(ownerUuid).whenComplete((snapshot, failure) ->
                    onServer(context.getSource(), () -> completeAdminInspect(
                            context.getSource(), ownerUuid, snapshot, failure)));
        } catch (RuntimeException failure) {
            context.getSource().sendError(Text.literal("Pet inspection is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completeAdminInspect(
            ServerCommandSource source,
            UUID ownerUuid,
            Optional<PetAuthoritySnapshot> snapshot,
            Throwable failure) {
        if (failure != null || snapshot == null) {
            source.sendError(Text.literal("Pet inspection is temporarily unavailable."));
            return;
        }
        if (snapshot.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No pet exists for owner " + ownerUuid)
                    .formatted(Formatting.YELLOW), false);
            return;
        }
        PetAuthoritySnapshot found = snapshot.orElseThrow();
        Pet pet = found.pet();
        source.sendFeedback(() -> Text.literal(
                "Owner=" + ownerUuid + " Pet=" + pet.petId() + " Revision=" + pet.recordVersion())
                .formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> statusText(pet, found.sleeping(), found.aiAccessEnabled()), false);
    }

    private static int adminRecover(CommandContext<ServerCommandSource> context) {
        UUID ownerUuid = ownerArgument(context);
        if (ownerUuid == null) return 0;
        Optional<PetAuthorityGateway> gateway = requireAdminGateway(context.getSource());
        if (gateway.isEmpty()) return 0;
        try {
            gateway.orElseThrow().findByOwner(ownerUuid).whenComplete((snapshot, failure) ->
                    onServer(context.getSource(), () -> beginAdminRecovery(
                            context.getSource(), gateway.orElseThrow(), snapshot, failure)));
        } catch (RuntimeException failure) {
            context.getSource().sendError(Text.literal("Pet recovery is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void beginAdminRecovery(
            ServerCommandSource source,
            PetAuthorityGateway gateway,
            Optional<PetAuthoritySnapshot> snapshot,
            Throwable failure) {
        if (failure != null || snapshot == null) {
            source.sendError(Text.literal("Pet recovery is temporarily unavailable."));
            return;
        }
        if (snapshot.isEmpty()) {
            source.sendError(Text.literal("No pet exists for that owner."));
            return;
        }
        Pet pet = snapshot.orElseThrow().pet();
        if (pet.placementState() == com.silver.aipets.common.domain.PlacementState.HELD) {
            source.sendFeedback(() -> Text.literal("Pet is already safely held; no recovery needed.")
                    .formatted(Formatting.YELLOW), false);
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
            source.sendError(Text.literal("Pet recovery is temporarily unavailable."));
        }
    }

    private static void completeAdminRecovery(
            ServerCommandSource source, AuthorityMutationResult result, Throwable failure) {
        if (failure != null || result == null) {
            source.sendError(Text.literal("Pet recovery is temporarily unavailable."));
            return;
        }
        if (result.status() != AuthorityMutationStatus.APPLIED) {
            source.sendError(Text.literal(
                    "Recovery did not apply because authoritative state changed; inspect and retry."));
            return;
        }
        int queued = PetCompanionMod.queueLoadedReconciliation(source.getServer());
        source.sendFeedback(() -> Text.literal(
                "Pet recovered to HELD without changing identity or appearance; queued "
                        + queued + " loaded representation(s) for reconciliation.")
                .formatted(Formatting.GREEN), false);
    }

    private static int adminRecallReset(CommandContext<ServerCommandSource> context) {
        UUID ownerUuid = ownerArgument(context);
        if (ownerUuid == null) return 0;
        Optional<PetAuthorityGateway> gateway = requireAdminGateway(context.getSource());
        if (gateway.isEmpty()) return 0;
        try {
            gateway.orElseThrow().findByOwner(ownerUuid).whenComplete((snapshot, failure) ->
                    onServer(context.getSource(), () -> beginRecallReset(
                            context.getSource(), gateway.orElseThrow(), snapshot, failure)));
        } catch (RuntimeException failure) {
            context.getSource().sendError(Text.literal("Recall reset is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void beginRecallReset(
            ServerCommandSource source,
            PetAuthorityGateway gateway,
            Optional<PetAuthoritySnapshot> snapshot,
            Throwable failure) {
        if (failure != null || snapshot == null) {
            source.sendError(Text.literal("Recall reset is temporarily unavailable."));
            return;
        }
        if (snapshot.isEmpty()) {
            source.sendError(Text.literal("No pet exists for that owner."));
            return;
        }
        try {
            gateway.resetRecall(snapshot.orElseThrow().pet().petId())
                    .whenComplete((result, resetFailure) -> onServer(
                            source, () -> completeRecallReset(source, result, resetFailure)));
        } catch (RuntimeException resetFailure) {
            source.sendError(Text.literal("Recall reset is temporarily unavailable."));
        }
    }

    private static void completeRecallReset(
            ServerCommandSource source, RecallResetWireResult result, Throwable failure) {
        if (failure != null || result == null) {
            source.sendError(Text.literal("Recall reset is temporarily unavailable."));
            return;
        }
        String message = switch (result.status()) {
            case RESET -> "Recall entitlement reset for UTC period " + result.periodKey() + ".";
            case NOT_USED -> "Recall was not consumed for UTC period " + result.periodKey() + ".";
            case RATE_LIMITED -> "Too many reset requests; wait a few minutes.";
        };
        Formatting color = result.status() == RecallResetWireStatus.RESET
                ? Formatting.GREEN : Formatting.YELLOW;
        source.sendFeedback(() -> Text.literal(message).formatted(color), false);
    }

    private static int adminReconcile(CommandContext<ServerCommandSource> context) {
        int queued = PetCompanionMod.queueLoadedReconciliation(context.getSource().getServer());
        context.getSource().sendFeedback(() -> Text.literal(
                "Queued " + queued + " loaded pet representation(s) for authority reconciliation.")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int adminHistory(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        UUID ownerUuid = ownerArgument(context);
        if (ownerUuid == null) return 0;
        Optional<PetAuthorityGateway> gateway = requireAdminGateway(source);
        if (gateway.isEmpty()) return 0;
        source.sendFeedback(() -> Text.literal(
                "Loading recent conversation history for " + ownerUuid + "…")
                .formatted(Formatting.GRAY), false);
        try {
            gateway.orElseThrow().findDialogueHistory(ownerUuid).whenComplete((history, failure) ->
                    onServer(source, () -> completeAdminHistory(source, history, failure)));
        } catch (RuntimeException failure) {
            source.sendError(Text.literal("Conversation history is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completeAdminHistory(
            ServerCommandSource source,
            DialogueHistoryWireResult history,
            Throwable failure) {
        if (failure != null || history == null) {
            source.sendError(Text.literal("Conversation history is temporarily unavailable."));
            return;
        }
        source.sendFeedback(() -> Text.literal(
                "DIALOGUE HISTORY — " + history.petName() + " (owner " + history.ownerUuid() + ")")
                .formatted(Formatting.AQUA, Formatting.BOLD), false);
        if (history.conversations().isEmpty()) {
            source.sendFeedback(() -> Text.literal("No retained conversation entries.")
                    .formatted(Formatting.GRAY), false);
        } else {
            history.conversations().forEach(entry -> {
                source.sendFeedback(() -> Text.literal(
                        formatUtc(entry.occurredAt()) + " [" + entry.importance() + "] "
                                + "owner: " + (entry.ownerText() == null ? "(expired)" : entry.ownerText()))
                        .formatted(Formatting.GRAY), false);
                if (entry.petReply() != null) {
                    source.sendFeedback(() -> Text.literal("  pet: " + entry.petReply())
                            .formatted(Formatting.WHITE), false);
                }
            });
        }
        source.sendFeedback(() -> Text.literal("PROVIDER USAGE")
                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        if (history.usage().isEmpty()) {
            source.sendFeedback(() -> Text.literal("No provider usage recorded.")
                    .formatted(Formatting.GRAY), false);
            return;
        }
        history.usage().forEach(entry -> {
            source.sendFeedback(() -> Text.literal(
                    formatUtc(entry.createdAt()) + " " + entry.operation() + " " + entry.model()
                            + " — input=" + entry.inputTokens()
                            + " cached=" + entry.cachedInputTokens()
                            + " output=" + entry.outputTokens()
                            + " cost=" + usd(entry.estimatedCost())
                    + " status=" + entry.status())
                    .formatted(Formatting.GRAY), false);
            if (entry.context().isPresent()) {
                source.sendFeedback(() -> Text.literal(
                        "  prompt parts: " + formatContext(entry.context().orElseThrow()))
                        .formatted(Formatting.DARK_GRAY), false);
            } else {
                source.sendFeedback(() -> Text.literal(
                        "  prompt parts: not captured for this older/provider-only usage record")
                        .formatted(Formatting.DARK_GRAY), false);
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

    private static UUID ownerArgument(CommandContext<ServerCommandSource> context) {
        String value;
        try {
            value = StringArgumentType.getString(context, "ownerUuid");
        } catch (IllegalArgumentException missing) {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player == null) {
                context.getSource().sendError(Text.literal(
                        "ownerUuid is required when running this command from the console."));
                return null;
            }
            return player.getUuid();
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) throw new IllegalArgumentException();
            return parsed;
        } catch (IllegalArgumentException malformed) {
            context.getSource().sendError(Text.literal("ownerUuid must be a canonical UUID."));
            return null;
        }
    }

    private static Optional<PetAuthorityGateway> requireAdminGateway(ServerCommandSource source) {
        Optional<PetAuthorityGateway> gateway = PetCompanionMod.authorityGateway();
        if (gateway.isEmpty()) {
            source.sendError(Text.literal("Pet Companion is temporarily unavailable."));
        }
        return gateway;
    }

    private static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void onServer(ServerCommandSource source, Runnable action) {
        Runnable guarded = () -> {
            try {
                action.run();
            } catch (RuntimeException failure) {
                PetCompanionMod.LOGGER.warn(StructuredPetEvent
                        .operation("command_completion")
                        .failure(failure)
                        .outcome("contained")
                        .toJson());
                source.sendError(Text.literal("Pet operation failed safely; please retry."));
            }
        };
        if (source.getServer().isOnThread()) {
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
