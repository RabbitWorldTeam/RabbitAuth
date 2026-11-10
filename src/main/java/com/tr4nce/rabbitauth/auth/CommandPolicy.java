package com.tr4nce.rabbitauth.auth;

import java.util.List;
import java.util.regex.Pattern;

public final class CommandPolicy {
    private CommandPolicy() {}
    public static final String LOGIN_ALIAS = "l";
    public static final String REGISTER_ALIAS = "r";

    public static String canonicalRoot(String root) {
        return switch (root) {
            case LOGIN_ALIAS -> "login";
            case REGISTER_ALIAS -> "register";
            default -> root;
        };
    }

    public static boolean allowed(String command, List<Pattern> whitelist) {
        String input = command.startsWith("/") ? command.substring(1) : command;
        String root = canonicalRoot(input.split(" ", 2)[0]);
        if (root.equals("login") || root.equals("register")) return true;
        return whitelist.stream().anyMatch(p -> p.matcher("/" + input).matches());
    }
}
