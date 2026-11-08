package com.tr4nce.rabbitauth.auth;

import java.util.List;
import java.util.regex.Pattern;

public final class CommandPolicy {
    private CommandPolicy() {}
    public static boolean allowed(String command, List<Pattern> whitelist) {
        String input = command.startsWith("/") ? command.substring(1) : command;
        String root = input.split(" ", 2)[0];
        if (root.equals("login") || root.equals("register")) return true;
        return whitelist.stream().anyMatch(p -> p.matcher("/" + input).matches());
    }
}
