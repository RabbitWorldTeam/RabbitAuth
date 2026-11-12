package com.tr4nce.rabbitauth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tr4nce.rabbitauth.RabbitAuth;
import com.tr4nce.rabbitauth.auth.CommandPolicy;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;

public final class AuthCommands {
    private AuthCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rabbitauth").requires(source -> source.hasPermission(4))
                .then(Commands.literal("forcelogin").then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> RabbitAuth.service().forceLogin(context.getSource(),
                                EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("resetpassword").then(Commands.argument("name", StringArgumentType.word())
                .then(Commands.argument("password", StringArgumentType.word()).executes(c -> {
                    RabbitAuth.service().resetPassword(c.getSource(), StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "password"));
                    return 1;
                })))));
        for (String root : List.of("login", CommandPolicy.LOGIN_ALIAS)) {
            dispatcher.register(Commands.literal(root).then(Commands.argument("password", StringArgumentType.word())
                    .executes(context -> {
                        RabbitAuth.service().submit(context.getSource().getPlayerOrException(), "login",
                                StringArgumentType.getString(context, "password"), "");
                        return 1;
                    })));
        }
        for (String root : List.of("register", CommandPolicy.REGISTER_ALIAS)) {
            dispatcher.register(Commands.literal(root).then(Commands.argument("password", StringArgumentType.word())
                    .then(Commands.argument("confirm", StringArgumentType.word()).executes(context -> {
                        RabbitAuth.service().submit(context.getSource().getPlayerOrException(), "register",
                                StringArgumentType.getString(context, "password"),
                                StringArgumentType.getString(context, "confirm"));
                        return 1;
                    }))));
        }
        dispatcher.register(Commands.literal("changepassword").then(Commands.argument("old", StringArgumentType.word())
                .then(Commands.argument("new", StringArgumentType.word())
                .executes(c -> { RabbitAuth.service().submit(c.getSource().getPlayerOrException(), "change", StringArgumentType.getString(c, "old"), StringArgumentType.getString(c, "new")); return 1; }))));
    }
}
