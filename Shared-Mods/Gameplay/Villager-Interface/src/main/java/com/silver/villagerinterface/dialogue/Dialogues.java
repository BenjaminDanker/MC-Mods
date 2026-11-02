package com.silver.villagerinterface.dialogue;

import com.google.gson.Gson;
import com.silver.villagerinterface.VillagerInterfaceMod;
import com.silver.villagerinterface.config.VillagerConfigEntry;
import com.silver.villagerinterface.conversation.BlacksmithInteraction;
import com.silver.villagerinterface.conversation.ConversationSession;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Scripted NPC conversations: no AI, just written lines and choices, shown in Minecraft's own dialog screen (Geyser shows
 * it to Bedrock players as a form). Scripts live in config/villagerinterface/dialogues/<id>.json, or ship in a mod under
 * /villagerinterface/dialogues/<id>.json.
 *
 * <p>A script has nodes; each node says some lines and offers choices. A choice goes to another node, ends the talk, or
 * runs an action (the blacksmith's Soulbound work). Choices and openings can depend on what the player carries (the
 * four worlds' fragments) or on tags the dialogue set earlier, so the NPC notices the player's progress.
 */
public final class Dialogues {
    public static final Identifier CLICK = Identifier.fromNamespaceAndPath("villagerinterface", "choice");
    private static final Gson GSON = new Gson();
    private static final Map<String, Script> SCRIPTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Open> OPEN = new ConcurrentHashMap<>();

    /** What the player is looking at now, so a click is only accepted for the screen it came from. */
    private record Open(VillagerConfigEntry npc, String node, List<Choice> choices, int nonce, ConversationSession session) {
    }

    public static final class Script {
        public String start = "start";
        /** Openings picked by condition (first match wins), e.g. a different greeting once the player holds a fragment. */
        public List<Choice> openings = List.of();
        public Map<String, Node> nodes = Map.of();
    }

    public static final class Node {
        public List<String> text = List.of();
        public List<Choice> choices = List.of();
    }

    public static final class Choice {
        public String label;
        /** The node this choice leads to (or, for openings, the node to open with). */
        public String go;
        /** "soulbound_quote" or "soulbound_confirm". */
        public String action;
        public boolean end;
        /** Shown only if the player carries all of these (custom item ids, e.g. "special_arrow"). */
        public List<String> ifCarries;
        /** Hidden if the player carries any of these. */
        public List<String> unlessCarries;
        /** Shown only with this player tag / without it. */
        public String ifTag;
        public String unlessTag;
        /** Number of the four worlds' fragments the player must carry at least. */
        public int minFragments;
        /** A player tag this choice sets (to remember a topic was heard). */
        public String setTag;
    }

    private Dialogues() {
    }

    public static void clear(UUID player) {
        OPEN.remove(player);
    }

    /** Drops cached scripts (after a config reload). */
    public static void reload() {
        SCRIPTS.clear();
    }

    public static boolean has(VillagerConfigEntry npc) {
        return npc.dialogue() != null && !npc.dialogue().isBlank() && script(npc.dialogue()) != null;
    }

    /** Starts the talk: the first opening whose condition holds, else the start node. */
    public static void open(ServerPlayer player, VillagerConfigEntry npc) {
        Script script = script(npc.dialogue());
        if (script == null) {
            return;
        }
        String node = script.start;
        for (Choice opening : script.openings) {
            if (visible(player, opening)) {
                node = opening.go;
                break;
            }
        }
        Open previous = OPEN.get(player.getUUID());
        ConversationSession session = previous != null && previous.npc().id().equals(npc.id()) ? previous.session() : new ConversationSession(npc, 1);
        show(player, npc, node, session, null);
    }

    /** A dialog button was pressed. Returns true when the click was one of ours. */
    public static boolean click(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        if (!CLICK.equals(id)) {
            return false;
        }
        Open open = OPEN.get(player.getUUID());
        if (open == null || payload.isEmpty() || !(payload.get() instanceof CompoundTag tag)) {
            return true;
        }
        if (tag.getIntOr("nonce", -1) != open.nonce()) {
            return true; // an old screen
        }
        int index = tag.getIntOr("choice", -1);
        if (index < 0 || index >= open.choices().size() || !inReach(player, open.npc())) {
            OPEN.remove(player.getUUID());
            return true;
        }
        Choice choice = open.choices().get(index);
        if (choice.setTag != null && !choice.setTag.isBlank()) {
            player.addTag(choice.setTag);
        }
        if (choice.end) {
            OPEN.remove(player.getUUID());
            return true;
        }
        if ("soulbound_quote".equals(choice.action)) {
            BlacksmithInteraction.quote(player, open.session(), text -> showResult(player, open, text, true));
            return true;
        }
        if ("soulbound_confirm".equals(choice.action)) {
            BlacksmithInteraction.confirm(player, open.session(), text -> showResult(player, open, text, false));
            return true;
        }
        if (choice.go != null) {
            show(player, open.npc(), choice.go, open.session(), null);
        }
        return true;
    }

    /** The blacksmith's answer, with Confirm (when a quote is pending) and Back. */
    private static void showResult(ServerPlayer player, Open from, String text, boolean offerConfirm) {
        if (player.isRemoved()) {
            return;
        }
        List<Choice> extra = new ArrayList<>();
        if (offerConfirm && from.session().getPendingModification() != null) {
            Choice yes = new Choice();
            yes.label = "Do it";
            yes.action = "soulbound_confirm";
            extra.add(yes);
        }
        Choice back = new Choice();
        back.label = "Back";
        back.go = from.node();
        extra.add(back);
        show(player, from.npc(), from.node(), from.session(), new Shown(List.of(text), extra));
    }

    private record Shown(List<String> text, List<Choice> choices) {
    }

    private static void show(ServerPlayer player, VillagerConfigEntry npc, String nodeId, ConversationSession session, Shown override) {
        Script script = script(npc.dialogue());
        Node node = script == null ? null : script.nodes.get(nodeId);
        if (node == null && override == null) {
            VillagerInterfaceMod.LOGGER.warn("Dialogue {} has no node {}", npc.dialogue(), nodeId);
            OPEN.remove(player.getUUID());
            return;
        }
        List<String> text = override != null ? override.text() : node.text;
        List<Choice> choices = new ArrayList<>();
        for (Choice choice : override != null ? override.choices() : node.choices) {
            if (visible(player, choice)) {
                choices.add(choice);
            }
        }
        int nonce = player.getRandom().nextInt(Integer.MAX_VALUE);
        OPEN.put(player.getUUID(), new Open(npc, nodeId, choices, nonce, session));

        List<DialogBody> body = new ArrayList<>();
        for (String line : text) {
            body.add(new PlainMessage(Component.literal(line), 300));
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < choices.size(); i++) {
            CompoundTag payload = new CompoundTag();
            payload.putInt("nonce", nonce);
            payload.putInt("choice", i);
            Choice choice = choices.get(i);
            Component label = Component.literal(choice.label == null ? "..." : choice.label);
            buttons.add(new ActionButton(new CommonButtonData(choice.end ? label.copy().withStyle(ChatFormatting.GRAY) : label, Optional.empty(), 280),
                Optional.of(new StaticAction(new ClickEvent.Custom(CLICK, Optional.of(payload))))));
        }
        String name = npc.displayName() != null && !npc.displayName().isBlank() ? npc.displayName() : npc.id();
        MultiActionDialog dialog = new MultiActionDialog(
            new CommonDialogData(Component.literal(name).withStyle(ChatFormatting.GOLD), Optional.empty(), true, false,
                DialogAction.CLOSE, body, List.of()),
            buttons, Optional.empty(), 1);
        player.openDialog(Holder.direct(dialog));
    }

    private static boolean inReach(ServerPlayer player, VillagerConfigEntry npc) {
        if (npc.position() == null) {
            return true;
        }
        double reach = npc.maxDistance() > 0 ? npc.maxDistance() : 5.0;
        return npc.position().toVec3().distanceToSqr(player.position()) <= reach * reach * 4;
    }

    static boolean visible(ServerPlayer player, Choice choice) {
        if (choice.ifTag != null && !player.entityTags().contains(choice.ifTag)) {
            return false;
        }
        if (choice.unlessTag != null && player.entityTags().contains(choice.unlessTag)) {
            return false;
        }
        List<String> carried = carriedIds(player);
        if (choice.ifCarries != null && !carried.containsAll(choice.ifCarries)) {
            return false;
        }
        if (choice.unlessCarries != null && choice.unlessCarries.stream().anyMatch(carried::contains)) {
            return false;
        }
        if (choice.minFragments > 0) {
            long fragments = FRAGMENTS.stream().filter(carried::contains).count();
            return fragments >= choice.minFragments;
        }
        return true;
    }

    private static final List<String> FRAGMENTS = List.of("special_arrow", "special_sea_lantern", "special_dead_bush", "special_coal_ore");

    private static List<String> carriedIds(ServerPlayer player) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
            if (data != null) {
                data.copyTag().getString("id").ifPresent(ids::add);
            }
        }
        return ids;
    }

    private static Script script(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return SCRIPTS.computeIfAbsent(id, Dialogues::load);
    }

    private static Script load(String id) {
        if (!id.matches("[a-z0-9_]+")) {
            return null;
        }
        Path file = FabricLoader.getInstance().getConfigDir().resolve("villagerinterface/dialogues/" + id + ".json");
        try {
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    return GSON.fromJson(reader, Script.class);
                }
            }
            try (InputStream in = Dialogues.class.getResourceAsStream("/villagerinterface/dialogues/" + id + ".json")) {
                if (in != null) {
                    return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Script.class);
                }
            }
        } catch (Exception failure) {
            VillagerInterfaceMod.LOGGER.error("ALERT: dialogue {} could not be read", id, failure);
            return null;
        }
        VillagerInterfaceMod.LOGGER.error("ALERT: dialogue {} not found", id);
        return null;
    }
}
