package com.tr4nce.rabbitauth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.*;
import com.tr4nce.rabbitauth.RabbitAuth;
import com.tr4nce.rabbitauth.auth.AuthService;
import com.tr4nce.rabbitauth.i18n.MessageKey;
import java.util.*;
import net.minecraft.commands.*;

public final class WhitelistCommands {
    private WhitelistCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(tree("whitelist"));
        dispatcher.register(Commands.literal("rabbitauth").requires(s -> s.hasPermission(4)).then(tree("whitelist")));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        var root = Commands.literal(name).requires(source -> source.hasPermission(4));
        for (String action : List.of("on", "off", "status", "import"))
            root.then(Commands.literal(action).executes(c -> execute(c.getSource(), action, "", "", 1)));
        for (String action : List.of("add", "remove", "info"))
            root.then(Commands.literal(action).then(Commands.argument("player", StringArgumentType.word())
                    .executes(c -> execute(c.getSource(), action, StringArgumentType.getString(c, "player"), "", 1))));
        root.then(Commands.literal("list").executes(c -> execute(c.getSource(), "list", "", "", 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(c ->
                        execute(c.getSource(), "list", "", "", IntegerArgumentType.getInteger(c, "page")))));
        root.then(Commands.literal("set").then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.literal("enabled").then(Commands.argument("enabled", BoolArgumentType.bool())
                .executes(c -> execute(c.getSource(), "set", StringArgumentType.getString(c, "player"),
                        Boolean.toString(BoolArgumentType.getBool(c, "enabled")), 1))))));
        return root;
    }
    private static int execute(CommandSourceStack source, String action, String name, String enabled, int page) {
        var auth = RabbitAuth.service();
        if (!source.hasPermission(4) || (source.getPlayer() != null && !auth.authenticated(source.getPlayer()))) {
            source.sendFailure(AuthService.message(MessageKey.NO_PERMISSION)); return 0;
        }
        String actor = source.getEntity() == null ? "console" : "player:" + source.getEntity().getUUID();
        Map<String, String> request = new HashMap<>();
        request.put("action", action); request.put("name", name); request.put("actor", actor);
        request.put("page", Integer.toString(page));
        if (!enabled.isEmpty()) request.put("enabled", enabled);
        auth.whitelist().execute(request).thenAccept(result -> auth.server.execute(() -> {
            var response = net.minecraft.network.chat.Component.literal("[RabbitAuth] " + result.get("message"));
            if (Boolean.TRUE.equals(result.get("ok"))) source.sendSuccess(() -> response, false);
            else source.sendFailure(response);
        }));
        return 1;
    }
}
