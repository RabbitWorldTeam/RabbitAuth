package com.tr4nce.rabbitauth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tr4nce.rabbitauth.RabbitAuth;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class AuthCommands {
    private AuthCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rabbitauth").requires(source -> source.hasPermission(4))
                .then(Commands.literal("resetpassword").then(Commands.argument("name", StringArgumentType.word())
                .then(Commands.argument("password", StringArgumentType.word()).executes(c -> {
                    RabbitAuth.service().resetPassword(c.getSource(), StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "password"));
                    return 1;
                })))));
        dispatcher.register(Commands.literal("login").then(Commands.argument("password", StringArgumentType.word())
                .executes(c -> { RabbitAuth.service().submit(c.getSource().getPlayerOrException(), "login", StringArgumentType.getString(c, "password"), ""); return 1; })));
        dispatcher.register(Commands.literal("register").then(Commands.argument("password", StringArgumentType.word())
                .then(Commands.argument("confirm", StringArgumentType.word())
                .executes(c -> { RabbitAuth.service().submit(c.getSource().getPlayerOrException(), "register", StringArgumentType.getString(c, "password"), StringArgumentType.getString(c, "confirm")); return 1; }))));
        dispatcher.register(Commands.literal("changepassword").then(Commands.argument("old", StringArgumentType.word())
                .then(Commands.argument("new", StringArgumentType.word())
                .executes(c -> { RabbitAuth.service().submit(c.getSource().getPlayerOrException(), "change", StringArgumentType.getString(c, "old"), StringArgumentType.getString(c, "new")); return 1; }))));
    }
}
