package com.tr4nce.rabbitauth.i18n;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Messages {
    private static final Properties ENGLISH = resource("en-US");
    private final Properties values = new Properties();
    public Messages(Path directory, String language) throws IOException {
        if (!language.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("Invalid language filename");
        values.putAll(ENGLISH);
        values.putAll(resource(language));
        Path languages = directory.resolve("languages");
        Files.createDirectories(languages);
        for (String builtin : List.of("zh-CN", "en-US")) {
            Path target = languages.resolve(builtin + ".properties");
            if (!Files.exists(target)) {
                try (InputStream input = Messages.class.getResourceAsStream("/languages/" + builtin + ".properties")) {
                    if (input == null) throw new IOException("Missing built-in language");
                    Files.copy(input, target);
                }
            }
        }
        Path override = languages.resolve(language + ".properties");
        if (Files.exists(override)) {
            Properties custom = new Properties();
            try (Reader reader = Files.newBufferedReader(override)) { custom.load(reader); }
            values.putAll(custom);
        }
    }
    private static Properties resource(String language) {
        Properties properties = new Properties();
        try (InputStream input = Messages.class.getResourceAsStream("/languages/" + language + ".properties")) {
            if (input != null) properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    public String format(MessageKey key, Object... arguments) { return format(values, key, arguments); }
    public static String fallback(MessageKey key, Object... arguments) { return format(ENGLISH, key, arguments); }
    private static String format(Properties values, MessageKey key, Object[] arguments) {
        String text = values.getProperty(key.name(), ENGLISH.getProperty(key.name()));
        if (text == null) throw new IllegalStateException("Missing message: " + key);
        // Substitute in one pass so replacement values cannot introduce extra placeholders.
        var matcher = java.util.regex.Pattern.compile("\\{(\\d+)\\}").matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(index < arguments.length ? String.valueOf(arguments[index]) : matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
