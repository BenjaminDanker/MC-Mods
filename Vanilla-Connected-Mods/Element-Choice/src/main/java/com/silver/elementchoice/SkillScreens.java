package com.silver.elementchoice;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.silver.elementchoice.skills.AbilityCatalog;
import com.silver.elementchoice.skills.AbilityDef;
import com.silver.elementchoice.skills.Group;
import com.silver.elementchoice.skills.Realm;
import com.silver.elementchoice.skills.SkillState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The skill tree as a Path-of-Exile style map. Each screen is a six-row chest menu whose artwork (starfield, spokes,
 * animal guardians, medallions, even the player's inventory frame) comes from the Magic resource pack: it is a bitmap
 * glyph drawn through the menu title in the font {@code skilltree:gui}. The only items are the small rings laid over
 * each medallion to show its state, so vanilla clients need nothing but the pack.
 *
 * Screen layouts live in {@code skill-layout.json}, written by {@code tools/build_skill_art.py}, which also paints the
 * artwork, so slot positions and picture always agree.
 */
final class SkillScreens {
    private static final Logger LOGGER = LoggerFactory.getLogger("element-choice");
    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final Identifier FONT = Identifier.fromNamespaceAndPath("skilltree", "gui");
    private static final Identifier TOOLTIP = Identifier.fromNamespaceAndPath("skilltree", "skill");
    /** Moves the glyph left so it starts at the screen's own left edge instead of the title's inset. */
    private static final String SHIFT = "";

    private static final Map<String, ChatFormatting> TEXT_COLOR = Map.of(
        "water", ChatFormatting.AQUA, "air", ChatFormatting.WHITE, "earth", ChatFormatting.GREEN,
        "fire", ChatFormatting.RED, "gold", ChatFormatting.GOLD);
    private static final Map<String, String> ANIMAL = Map.of("water", "Tortoise", "air", "Dragon", "fire", "Phoenix", "earth", "Tiger");

    private record Node(String id, String kind, int row, int col, String color, String group) {
        int slot() {
            return row * 9 + col;
        }
    }

    private record Screen(String glyph, List<Node> nodes) {
    }

    private record Layout(Map<String, Screen> screens, Map<String, List<String>> groupNeeds) {
    }

    private static volatile Layout layout;

    private SkillScreens() {
    }

    private static Layout layout() {
        Layout loaded = layout;
        if (loaded != null) {
            return loaded;
        }
        synchronized (SkillScreens.class) {
            if (layout == null) {
                try (var stream = SkillScreens.class.getResourceAsStream("/skill-layout.json")) {
                    if (stream == null) {
                        throw new IOException("skill-layout.json is missing from the jar");
                    }
                    JsonObject root = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
                    Map<String, Screen> screens = new LinkedHashMap<>();
                    JsonObject glyphs = root.getAsJsonObject("glyphs");
                    for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("screens").entrySet()) {
                        List<Node> nodes = new ArrayList<>();
                        JsonArray array = entry.getValue().getAsJsonObject().getAsJsonArray("nodes");
                        for (JsonElement element : array) {
                            JsonObject n = element.getAsJsonObject();
                            nodes.add(new Node(n.get("id").getAsString(), n.get("kind").getAsString(), n.get("row").getAsInt(),
                                n.get("col").getAsInt(), n.get("color").getAsString(),
                                n.has("group") ? n.get("group").getAsString() : null));
                        }
                        screens.put(entry.getKey(), new Screen(glyphs.get(entry.getKey()).getAsString(), nodes));
                    }
                    Map<String, List<String>> needs = new HashMap<>();
                    for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("groups").entrySet()) {
                        needs.put(entry.getKey(), new ArrayList<>(entry.getValue().getAsJsonObject().getAsJsonObject("needs").keySet()));
                    }
                    layout = new Layout(screens, needs);
                } catch (IOException | RuntimeException failure) {
                    LOGGER.error("ALERT: could not load the skill screen layout", failure);
                    throw new IllegalStateException(failure);
                }
            }
            return layout;
        }
    }

    /** What a click does: the player, and whether it was a right click. */
    private interface Click extends BiConsumer<ServerPlayer, Boolean> {
    }

    /** Opens the map ({@code "map"}) or one element's screen ({@code "air"}, {@code "water"}, ...). */
    static void open(ServerPlayer player, SkillState state, String screenName, SkillManager manager) {
        Screen screen = layout().screens().get(screenName);
        if (screen == null) {
            screen = layout().screens().get("map");
        }
        Realm screenRealm = Realm.fromId(screenName);
        Map<Integer, Click> clicks = new HashMap<>();
        SimpleContainer container = new SimpleContainer(SIZE);
        for (Node node : screen.nodes()) {
            ItemStack stack = switch (node.kind()) {
                case "realm" -> realmNode(state, node);
                case "group" -> groupNode(state, node);
                case "points" -> pointsNode(state);
                case "resilience" -> resilienceNode(state);
                case "back" -> info("ring_gold_open", 1, Component.literal("Back to the map").withStyle(ChatFormatting.GOLD), List.of());
                case "root" -> rootNode(state, node, screenRealm);
                default -> abilityNode(state, node);
            };
            container.setItem(node.slot(), stack);
            clicks.put(node.slot(), clickFor(node, state, screenRealm, manager));
        }
        Component title = Component.literal(SHIFT + screen.glyph()).withStyle(Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(FONT)).withColor(0xFFFFFF));
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new ScreenMenu(id, inventory, container, clicks), title));
    }

    // ---- clicks -------------------------------------------------------------------------------------

    private static Click clickFor(Node node, SkillState state, Realm screenRealm, SkillManager manager) {
        return (player, right) -> {
            switch (node.kind()) {
                case "realm" -> {
                    Realm realm = Realm.fromId(node.id());
                    if (state.owns(realm)) {
                        manager.open(player, SkillManager.Page.valueOf(realm.name()));
                    } else if (state.unlocked(realm)) {
                        manager.buyRealm(player, realm);
                    } else {
                        lockedMessage(player, realm.display());
                    }
                }
                case "group" -> {
                    String home = layout().groupNeeds().get(node.group()).get(0);
                    Realm realm = Realm.fromId(home);
                    if (realm != null && state.owns(realm)) {
                        manager.open(player, SkillManager.Page.valueOf(realm.name()));
                    } else {
                        lockedMessage(player, node.group().equals("AVATAR") ? "The Ascendant" : Group.valueOf(node.group()).display());
                    }
                }
                case "points" -> player.sendSystemMessage(Component.literal("You have " + state.points()
                    + " skill point" + (state.points() == 1 ? "" : "s") + ". Earn more from advancements (L).").withStyle(ChatFormatting.GOLD));
                case "resilience" -> manager.levelUpResilience(player);
                case "back" -> manager.open(player, SkillManager.Page.MAP);
                case "root" -> {
                    if (screenRealm != null && SkillManager.crystal() != null) {
                        manager.attune(player, screenRealm);
                    }
                }
                default -> {
                    AbilityDef def = AbilityCatalog.byId(node.id());
                    if (def == null) {
                        return;
                    }
                    if (right) {
                        if (state.level(def.id()) >= 1) {
                            player.level().getServer().getCommands().performPrefixedCommand(
                                player.createCommandSourceStack(), "bending bind " + def.id());
                        } else {
                            player.sendSystemMessage(Component.literal("Learn " + def.display() + " first.").withStyle(ChatFormatting.RED));
                        }
                    } else {
                        manager.levelUp(player, def);
                    }
                }
            }
        };
    }

    private static void lockedMessage(ServerPlayer player, String what) {
        player.sendSystemMessage(Component.literal(what + " is locked.").withStyle(ChatFormatting.RED));
    }

    // ---- node items ---------------------------------------------------------------------------------

    private static String ring(String color, String state) {
        return state.equals("veil") ? "veil" : "ring_" + color + "_" + state;
    }

    private static ItemStack info(String model, int count, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(Items.PAPER, Math.max(1, Math.min(64, count)));
        stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("skilltree", model));
        stack.set(DataComponents.TOOLTIP_STYLE, TOOLTIP);
        stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(style -> style.withItalic(false)));
        if (!lore.isEmpty()) {
            List<Component> plain = new ArrayList<>();
            for (Component line : lore) {
                plain.add(line.copy().withStyle(style -> style.withItalic(false)));
            }
            stack.set(DataComponents.LORE, new ItemLore(plain));
        }
        return stack;
    }

    private static MutableComponent line(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    private static ItemStack realmNode(SkillState state, Node node) {
        Realm realm = Realm.fromId(node.id());
        ChatFormatting color = TEXT_COLOR.get(node.color());
        List<Component> lore = new ArrayList<>();
        lore.add(line("Spirit of the " + ANIMAL.get(node.id()), ChatFormatting.GRAY));
        String stateName;
        if (state.owns(realm)) {
            stateName = "owned";
            lore.add(line(state.invested(realm) + " points invested", ChatFormatting.GREEN));
            lore.add(line("Click to open its abilities.", ChatFormatting.YELLOW));
        } else if (state.unlocked(realm)) {
            stateName = "open";
            lore.add(line("Costs " + state.realmCost() + " skill points.", ChatFormatting.AQUA));
            lore.add(line("Click to take it.", ChatFormatting.YELLOW));
        } else {
            stateName = "veil";
            lore.add(line("Locked.", ChatFormatting.DARK_GRAY));
        }
        return info(ring(node.color(), stateName), 1, Component.literal(realm.display()).withStyle(color, ChatFormatting.BOLD), lore);
    }

    private static boolean groupHasLevel(SkillState state, Group group) {
        for (AbilityDef def : AbilityCatalog.all()) {
            if (def.group() == group && state.level(def.id()) >= 1) {
                return true;
            }
        }
        return false;
    }

    private static List<Component> needs(SkillState state, Group group) {
        List<Component> lore = new ArrayList<>();
        for (Map.Entry<Realm, Integer> need : group.requirements().entrySet()) {
            lore.add(line("Needs " + need.getValue() + " in " + need.getKey().display()
                + " (you have " + state.invested(need.getKey()) + ")", ChatFormatting.DARK_GRAY));
        }
        return lore;
    }

    private static ItemStack groupNode(SkillState state, Node node) {
        Group group = Group.valueOf(node.group());
        boolean open = state.groupOpen(group);
        String name = group == Group.AVATAR ? "Ascendant" : group.display();
        ChatFormatting color = group == Group.AVATAR ? ChatFormatting.LIGHT_PURPLE : TEXT_COLOR.get(node.color());
        List<Component> lore = new ArrayList<>();
        String stateName;
        if (groupHasLevel(state, group)) {
            stateName = "owned";
            lore.add(line("Learned. Click to see its abilities.", ChatFormatting.YELLOW));
        } else if (open) {
            stateName = "open";
            lore.add(line("Open. Click to see its abilities.", ChatFormatting.YELLOW));
        } else {
            stateName = "veil";
            lore.addAll(needs(state, group));
        }
        return info(ring(node.color(), stateName), 1, Component.literal(name).withStyle(color, ChatFormatting.BOLD), lore);
    }

    private static ItemStack pointsNode(SkillState state) {
        return info("ring_gold_open", state.points(), Component.literal("Skill points: " + state.points()).withStyle(ChatFormatting.GOLD),
            List.of(line("Earn points from advancements (L).", ChatFormatting.GRAY)));
    }

    private static ItemStack resilienceNode(SkillState state) {
        boolean maxed = state.resilience() >= SkillState.RESILIENCE_MAX;
        return info(state.resilience() > 0 ? "ring_gold_owned" : "ring_gold_open", state.resilience(),
            Component.literal("Resilience  Lv " + state.resilience() + "/" + SkillState.RESILIENCE_MAX).withStyle(ChatFormatting.GREEN),
            List.of(line("Tougher skin for every element.", ChatFormatting.GRAY),
                line(maxed ? "Fully trained." : "Click to train for " + state.resilienceCost() + " points.", ChatFormatting.YELLOW)));
    }

    private static ItemStack rootNode(SkillState state, Node node, Realm realm) {
        List<Component> lore = new ArrayList<>();
        lore.add(line("Spirit of the " + ANIMAL.get(node.color()), ChatFormatting.GRAY));
        lore.add(line(state.invested(realm) + " points invested", ChatFormatting.GREEN));
        if (SkillManager.crystal() != null) {
            int level = state.attunement(realm);
            lore.add(line("Ley Attunement " + level + "/" + SkillState.ATTUNE_MAX + ". Click to attune with Ley Crystals.", ChatFormatting.LIGHT_PURPLE));
        }
        return info(ring(node.color(), state.owns(realm) ? "owned" : "veil"), 1,
            Component.literal(realm.display()).withStyle(TEXT_COLOR.get(node.color()), ChatFormatting.BOLD), lore);
    }

    private static ItemStack abilityNode(SkillState state, Node node) {
        AbilityDef def = AbilityCatalog.byId(node.id());
        if (def == null) {
            return ItemStack.EMPTY;
        }
        int level = state.level(def.id());
        SkillState.Reason reason = state.checkAbility(def.id());
        String stateName = level >= 1 ? "owned" : reason == SkillState.Reason.OK ? "open" : "veil";
        List<Component> lore = describe(state, def, reason);
        return info(ring(node.color(), stateName), level, Component.literal(def.display()).withStyle(TEXT_COLOR.getOrDefault(node.color(), ChatFormatting.WHITE), ChatFormatting.BOLD), lore);
    }

    static List<Component> describe(SkillState state, AbilityDef def, SkillState.Reason reason) {
        List<Component> lore = new ArrayList<>();
        if (def.defense()) {
            lore.add(line("Defense. Free with " + def.realm().display() + ".", ChatFormatting.GRAY));
        } else if (def.free()) {
            lore.add(line("Free with " + def.realm().display() + ".", ChatFormatting.GRAY));
        } else if (def.group() != Group.BASE) {
            lore.add(line(def.group().display() + " family", ChatFormatting.GRAY));
        }
        lore.add(line("Each level: +10% damage, -5% cooldown, +5% range.", ChatFormatting.DARK_GRAY));
        int level = state.level(def.id());
        if (level >= 1) {
            lore.add(line("Level " + level + "/" + AbilityDef.MAX_LEVEL, ChatFormatting.GREEN));
        }
        switch (reason) {
            case OK -> lore.add(line("Click to " + (level == 0 ? "learn it" : "raise it to level " + (level + 1))
                + " for " + state.abilityCost(def.id()) + " points.", ChatFormatting.YELLOW));
            case MAX_LEVEL -> lore.add(line("Maximum level.", ChatFormatting.GOLD));
            case NOT_ENOUGH_POINTS -> lore.add(line("Needs " + state.abilityCost(def.id()) + " skill points.", ChatFormatting.RED));
            case NOT_OWNED -> lore.add(line("You do not own " + def.realm().display() + ".", ChatFormatting.RED));
            case GROUP_LOCKED -> lore.addAll(needs(state, def.group()));
            default -> { }
        }
        if (level >= 1) {
            lore.add(line("Right-click: bind to your selected hotbar slot.", ChatFormatting.AQUA));
        }
        return lore;
    }

    // ---- the menu -----------------------------------------------------------------------------------

    /** A chest menu where every click is a button press and nothing can be taken or moved. */
    private static final class ScreenMenu extends ChestMenu {
        private final Map<Integer, Click> clicks;

        ScreenMenu(int id, Inventory inventory, Container container, Map<Integer, Click> clicks) {
            super(MenuType.GENERIC_9x6, id, inventory, container, ROWS);
            this.clicks = clicks;
        }

        @Override
        public void clicked(int slotId, int button, ContainerInput input, Player player) {
            Click click = clicks.get(slotId);
            if (click != null && input == ContainerInput.PICKUP && player instanceof ServerPlayer serverPlayer) {
                click.accept(serverPlayer, button == 1);
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
