package com.silver.elementchoice;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.silver.elementchoice.skills.Realm;
import me.moros.bending.api.ability.AbilityDescription;
import me.moros.bending.api.registry.Registries;
import me.moros.bending.api.user.User;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reshapes Bending's own commands for the skill tree: {@code bind} only suggests abilities the player can use right now,
 * and {@code help} only teaches what the player has access to. Both read the live permission check each time, so they
 * follow the player as they learn new abilities.
 */
final class BendingCommandTweaks {
    private static final Logger LOGGER = LoggerFactory.getLogger("element-choice");
    private static final String[] ROOTS = {"bending", "b"};

    private BendingCommandTweaks() {
    }

    /** Marker so a repeated install (after a datapack reload) does not wrap twice. */
    private record HelpCommand(Command<CommandSourceStack> original) implements Command<CommandSourceStack> {
        @Override
        public int run(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            ServerPlayer player = context.getSource().getPlayer();
            if (player != null && tryRestrictedHelp(player, context.getInput())) {
                return 1;
            }
            return original.run(context);
        }
    }

    static void install(MinecraftServer server) {
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        int patched = 0;
        for (String rootName : ROOTS) {
            CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild(rootName);
            if (root == null) {
                continue;
            }
            CommandNode<CommandSourceStack> bind = root.getChild("bind");
            if (bind != null) {
                for (CommandNode<CommandSourceStack> child : bind.getChildren()) {
                    if (child instanceof ArgumentCommandNode<?, ?> argument) {
                        patched += setSuggestions(argument, false) ? 1 : 0;
                    }
                }
            }
            CommandNode<CommandSourceStack> attribute = root.getChild("attribute");
            if (attribute != null) {
                for (CommandNode<CommandSourceStack> child : attribute.getChildren()) {
                    if (child instanceof ArgumentCommandNode<?, ?> argument) {
                        patched += setSuggestions(argument, true) ? 1 : 0;
                    }
                }
            }
            CommandNode<CommandSourceStack> help = root.getChild("help");
            if (help != null) {
                patched += wrapHelp(help);
            }
        }
        if (patched == 0) {
            LOGGER.warn("ALERT: found no Bending bind/help commands to adjust; players will see unfiltered suggestions and help");
        } else {
            LOGGER.info("Adjusted {} Bending command node(s) (bind suggestions, help)", patched);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean setSuggestions(ArgumentCommandNode argument, boolean everyAbility) {
        try {
            Field field = ArgumentCommandNode.class.getDeclaredField("customSuggestions");
            field.setAccessible(true);
            SuggestionProvider<CommandSourceStack> provider = (context, builder) -> {
                ServerPlayer player = context.getSource().getPlayer();
                if (player != null) {
                    String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
                    Iterable<AbilityDescription> candidates = everyAbility ? visibleAbilities() : usableAbilities(player);
                    for (AbilityDescription ability : candidates) {
                        String name = ability.key().value();
                        if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            builder.suggest(name);
                        }
                    }
                }
                return builder.buildFuture();
            };
            field.set(argument, provider);
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOGGER.error("ALERT: could not install bind suggestions", failure);
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int wrapHelp(CommandNode<CommandSourceStack> node) {
        int count = 0;
        try {
            Field field = CommandNode.class.getDeclaredField("command");
            field.setAccessible(true);
            Command<CommandSourceStack> existing = (Command<CommandSourceStack>) field.get(node);
            if (existing != null && !(existing instanceof HelpCommand)) {
                field.set(node, new HelpCommand(existing));
                count++;
            }
            for (CommandNode<CommandSourceStack> child : node.getChildren()) {
                count += wrapHelp(child);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOGGER.error("ALERT: could not restrict Bending help", failure);
        }
        return count;
    }

    /** Every ability a staff member may ask about. */
    static List<AbilityDescription> visibleAbilities() {
        List<AbilityDescription> all = new ArrayList<>();
        for (AbilityDescription ability : Registries.ABILITIES) {
            if (!ability.hidden()) {
                all.add(ability);
            }
        }
        return all;
    }

    /** Abilities this player can bind right now: their elements, a learned ability, not hidden. */
    static List<AbilityDescription> usableAbilities(ServerPlayer player) {
        User user = Registries.BENDERS.get(player.getUUID());
        List<AbilityDescription> usable = new ArrayList<>();
        if (user == null) {
            return usable;
        }
        for (AbilityDescription ability : Registries.ABILITIES) {
            if (!ability.hidden() && ability.canBind() && user.hasElements(ability.elements()) && user.hasPermission(ability)) {
                usable.add(ability);
            }
        }
        return usable;
    }

    /** True when this help request was answered here. Only elements and abilities are restricted; other topics pass through. */
    private static boolean tryRestrictedHelp(ServerPlayer player, String input) {
        String[] words = input.trim().split("\\s+");
        if (words.length < 3) {
            return false;
        }
        String topic = words[words.length - 1].toLowerCase(Locale.ROOT);
        Realm realm = Realm.fromId(topic);
        if (realm != null) {
            sendElementHelp(player, realm);
            return true;
        }
        for (AbilityDescription ability : Registries.ABILITIES) {
            if (ability.key().value().equalsIgnoreCase(topic) && !ability.hidden()) {
                if (usableAbilities(player).contains(ability)) {
                    return false;
                }
                player.sendSystemMessage(Component.literal("You have not learned that yet. Open your skill tree to learn it.")
                    .withStyle(ChatFormatting.RED)
                    .append(openTree()));
                return true;
            }
        }
        return false;
    }

    private static void sendElementHelp(ServerPlayer player, Realm realm) {
        List<AbilityDescription> mine = new ArrayList<>();
        for (AbilityDescription ability : usableAbilities(player)) {
            if (ability.elements().stream().anyMatch(element -> element.name().equalsIgnoreCase(realm.name()))) {
                mine.add(ability);
            }
        }
        if (mine.isEmpty()) {
            player.sendSystemMessage(Component.literal("You have no " + realm.display() + " abilities yet.")
                .withStyle(ChatFormatting.GRAY).append(openTree()));
            return;
        }
        player.sendSystemMessage(Component.literal(realm.display() + " abilities you can use:").withStyle(ChatFormatting.GOLD));
        for (AbilityDescription ability : mine) {
            player.sendSystemMessage(Component.literal("  " + ability.key().value() + " ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("[details]").withStyle(style -> style.withColor(ChatFormatting.GRAY)
                    .withClickEvent(new ClickEvent.RunCommand("/bending help " + ability.key().value())))));
        }
        player.sendSystemMessage(Component.literal("Bind one to your hotbar slot with /bending bind <ability>.").withStyle(ChatFormatting.GRAY));
    }

    static Component openTree() {
        return Component.literal(" [Open skill tree]").withStyle(style -> style.withColor(ChatFormatting.GOLD).withUnderlined(true)
            .withClickEvent(new ClickEvent.RunCommand("/skills")));
    }
}
