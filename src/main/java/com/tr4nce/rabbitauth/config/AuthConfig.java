package com.tr4nce.rabbitauth.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public final class AuthConfig {
    private final Properties values = new Properties();
    private final List<Pattern> commandWhitelist;
    public AuthConfig(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("auth.properties");
        try (InputStream defaults = AuthConfig.class.getResourceAsStream("/auth-defaults.properties")) {
            if (defaults == null) throw new IOException("Missing default configuration");
            values.load(new InputStreamReader(defaults, StandardCharsets.UTF_8));
        }
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) { values.load(reader); }
        } else {
            try (Writer writer = Files.newBufferedWriter(file)) { values.store(writer, "RabbitAuth; restart server after changes"); }
        }
        number("login-timeout-seconds", 10, 3600);
        number("min-password-length", 6, number("max-password-length", 8, 120));
        number("max-login-attempts", 1, 100);
        number("reenter-cooldown-ticks", 0, 72000);
        number("max-registrations-per-ip", 0, 100000);
        number("max-connections-per-ip", 0, 100000);
        number("auto-login-timeout-minutes", 1, 10080);
        for (String key : List.of("before-login-allow-chat", "hide-inventory", "no-damage-before-login",
                "blindness-before-login", "spawn-enabled", "same-ip-auto-login", "loopback-auto-login")) flag(key);
        if (!List.of("sqlite", "mysql").contains(text("storage"))) throw new IllegalArgumentException("Invalid storage");
        if (!text("language").matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("Invalid language");
        if (!text("spawn-dimension").matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid spawn dimension");
        int coordinates = 0;
        for (String axis : List.of("x", "y", "z")) {
            String value = text("spawn-" + axis);
            if (!value.isEmpty()) {
                if (!Double.isFinite(Double.parseDouble(value))) throw new IllegalArgumentException("Non-finite spawn coordinate");
                coordinates++;
            }
        }
        if (coordinates != 0 && coordinates != 3) throw new IllegalArgumentException("Specify all three spawn coordinates or none");
        commandWhitelist = Arrays.stream(text("command-whitelist").split(";;"))
                .filter(s -> !s.isBlank()).map(Pattern::compile).toList();
    }
    public String text(String key) { return raw(key).trim(); }
    public String raw(String key) {
        String value = values.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Missing configuration: " + key);
        return value;
    }
    public boolean flag(String key) {
        String value = text(key);
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid boolean: " + key);
        return Boolean.parseBoolean(value);
    }
    public int number(String key, int min, int max) {
        int value = Integer.parseInt(text(key));
        if (value < min || value > max) throw new IllegalArgumentException("Invalid range: " + key);
        return value;
    }
    public List<Pattern> commandWhitelist() { return commandWhitelist; }
}
