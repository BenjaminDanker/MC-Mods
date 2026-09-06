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
import java.util.Optional;
import java.util.UUID;
import java.net.URI;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

/** Player command surface; physical mutations delegate to commit-safe async coordinators. */
public final class PetCommands {
    private PetCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("pet")
                .executes(PetCommands::help)
                .then(literal("status")
                        .requires(source -> PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::status))
                .then(literal("link")
                        .requires(source -> PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::link))
                .then(literal("portal")
                        .requires(source -> PetPermissions.check(source, PetPermission.USE))
                        .executes(PetCommands::portal))
                .then(literal("adopt")
                        .requires(source -> PetPermissions.check(source, PetPermission.ADOPT))
                        .executes(context -> adoptionMenu(context, null))
                        .then(literal("cat")
                                .executes(context -> adoptionMenu(context, PetSpecies.CAT))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(context -> adopt(context, PetSpecies.CAT))))
                        .then(literal("dog")
                                .executes(context -> adoptionMenu(context, PetSpecies.DOG))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(context -> adopt(context, PetSpecies.DOG)))))
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
                        .then(literal("inspect")
                                .requires(source -> PetPermissions.check(
                                        source, PetPermission.ADMIN_INSPECT))
                                .then(argument("ownerUuid", StringArgumentType.word())
                                        .executes(PetCommands::adminInspect)))
                        .then(literal("recover")
                                .requires(source -> PetPermissions.check(
                                        source, PetPermission.ADMIN_RECOVER))
                                .then(argument("ownerUuid", StringArgumentType.word())
                                        .executes(PetCommands::adminRecover)))
                        .then(literal("recall-reset")
                                .requires(source -> PetPermissions.check(
                                        source, PetPermission.ADMIN_RECOVER))
                                .then(argument("ownerUuid", StringArgumentType.word())
                                        .executes(PetCommands::adminRecallReset)))
                        .then(literal("reconcile")
                                .requires(source -> PetPermissions.check(
                                        source, PetPermission.ADMIN_RECONCILE))
                                .executes(PetCommands::adminReconcile))));
    }

    private static int help(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        StringBuilder commands = new StringBuilder(
                "Pet Companion: /pet status, /pet link, /pet portal, /pet place, /pet pickup");
        if (PetPermissions.check(source, PetPermission.ADOPT)) {
            commands.append(", /pet adopt <cat|dog> <name>");
        }
        if (PetPermissions.check(source, PetPermission.RECALL)) {
            commands.append(", /pet recall");
        }
        if (PetPermissions.check(source, PetPermission.COMPASS)) {
            commands.append(", /pet compass");
        }
        if (PetPermissions.check(source, PetPermission.ADMIN_INSPECT)) {
            commands.append(", /pet admin …");
        }
        context.getSource().sendFeedback(
                () -> Text.literal(commands.toString())
                        .formatted(Formatting.AQUA),
                false);
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
            source.sendError(Text.literal("Pet authority service is not configured."));
            return 0;
        }
        UUID ownerUuid = player.getUuid();
        source.sendFeedback(
                () -> Text.literal("Creating your secure pet checkout link…")
                        .formatted(Formatting.GRAY),
                false);
        try {
            gateway.orElseThrow().createAccountLink(ownerUuid).whenComplete((result, failure) ->
                    onServer(source, () -> completeLink(source, ownerUuid, result, failure)));
        } catch (RuntimeException failure) {
            source.sendError(Text.literal("Account linking is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completeLink(
            ServerCommandSource source,
            UUID ownerUuid,
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
        source.sendFeedback(() -> Text.empty()
                        .append(Text.literal("Open secure pet checkout ")
                                .formatted(Formatting.GREEN))
                        .append(Text.literal("[CLICK HERE]")
                                .formatted(Formatting.AQUA, Formatting.UNDERLINE)
                                .styled(style -> style.withClickEvent(
                                        new ClickEvent.OpenUrl(checkoutUrl))))
                        .append(Text.literal(" (expires "
                                + formatUtc(result.expiresAt().orElseThrow()) + ")")
                                .formatted(Formatting.GRAY)), false);
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
            source.sendError(Text.literal("Pet authority service is not configured."));
            return 0;
        }
        UUID ownerUuid = player.getUuid();
        source.sendFeedback(
                () -> Text.literal("Creating your secure billing-management link…")
                        .formatted(Formatting.GRAY),
                false);
        try {
            gateway.orElseThrow().createCustomerPortal(ownerUuid).whenComplete((result, failure) ->
                    onServer(source, () -> completePortal(source, ownerUuid, result, failure)));
        } catch (RuntimeException failure) {
            source.sendError(Text.literal("Billing management is temporarily unavailable."));
            return 0;
        }
        return 1;
    }

    private static void completePortal(
            ServerCommandSource source,
            UUID ownerUuid,
            CustomerPortalWireResult result,
            Throwable failure) {
        if (source.getServer().getPlayerManager().getPlayer(ownerUuid) == null) return;
        if (failure != null || result == null) {
            source.sendError(Text.literal("Billing management is temporarily unavailable."));
            return;
        }
        if (result.status() == CustomerPortalWireStatus.NOT_LINKED) {
            source.sendFeedback(
                    () -> Text.literal("No Stripe subscription is linked yet; use /pet link.")
                            .formatted(Formatting.YELLOW),
                    false);
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
        source.sendFeedback(() -> Text.empty()
                .append(Text.literal("Manage or cancel your pet subscription ")
                        .formatted(Formatting.GREEN))
                .append(Text.literal("[OPEN BILLING PORTAL]")
                        .formatted(Formatting.AQUA, Formatting.UNDERLINE)
                        .styled(style -> style.withClickEvent(
                                new ClickEvent.OpenUrl(portalUrl)))), false);
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
            source.sendError(Text.literal("Pet authority service is not configured."));
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
            case AUTHORITY_REJECTED -> "The authoritative pet state changed; check /pet status.";
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
            source.sendError(Text.literal("Pet authority service is not configured."));
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
        source.sendFeedback(() -> Text.literal("Checking adoption access…").formatted(Formatting.GRAY), false);
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
            PetAdoptionMenu.open(source);
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
                    () -> Text.literal("An active subscription is required to adopt a pet.")
                            .formatted(Formatting.YELLOW),
                    false);
            return;
        }
        if (result.status() == PetAdoptionWireStatus.SPECIES_UNAVAILABLE) {
            source.sendFeedback(
                    () -> Text.literal("That pet species is disabled by the server allowlist.")
                            .formatted(Formatting.YELLOW),
                    false);
            return;
        }
        Pet pet = result.pet().orElseThrow();
        if (result.status() == PetAdoptionWireStatus.EXISTING) {
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
            source.sendError(Text.literal("Pet authority service is not configured."));
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
            source.sendError(Text.literal("Pet authority service is not configured."));
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
            case AUTHORITY_REJECTED -> "The authoritative pet state changed; check /pet status.";
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
            source.sendError(Text.literal("Pet authority service is not configured."));
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
            case NOT_PLACED_HERE -> "Your pet is not placed on this backend and dimension.";
            case ENTITY_MISSING_OR_STALE -> "The authoritative pet entity is missing or stale.";
            case OUT_OF_RANGE -> "Move within 4 blocks of your pet to pick it up.";
            case AUTHORITY_REJECTED -> "The authoritative pet state changed; check /pet status.";
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
            source.sendError(Text.literal("Pet authority service is not configured."));
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
            source.sendFeedback(
                    () -> Text.literal("You have not adopted a pet yet.").formatted(Formatting.YELLOW),
                    false);
            return;
        }
        PetAuthoritySnapshot authority = snapshot.orElseThrow();
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
        String aiAccess = aiAccessEnabled ? " • AI access active" : " • AI access inactive";
        return Text.literal(pet.name() + " (" + pet.appearance().species() + ") — "
                        + location + sleep + aiAccess)
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

    private static UUID ownerArgument(CommandContext<ServerCommandSource> context) {
        String value = StringArgumentType.getString(context, "ownerUuid");
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
            source.sendError(Text.literal("Pet authority service is not configured."));
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
