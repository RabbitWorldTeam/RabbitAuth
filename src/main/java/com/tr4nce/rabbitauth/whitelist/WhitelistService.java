package com.tr4nce.rabbitauth.whitelist;

import com.google.gson.*;
import com.tr4nce.rabbitauth.RabbitAuth;
import com.tr4nce.rabbitauth.auth.AuthService;
import com.tr4nce.rabbitauth.i18n.MessageKey;
import com.tr4nce.rabbitauth.storage.Account;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class WhitelistService implements AutoCloseable {
    private final AuthService auth;
    private final WhitelistStore store;
    private volatile boolean closed;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "RabbitAuth-whitelist"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    public WhitelistService(AuthService auth) throws Exception {
        this.auth = auth; store = auth.store.whitelist();
        if (!store.initialized()) store.bootstrap(auth.server.getPlayerList().isUsingWhitelist(), vanillaNames());
    }
    public WhitelistStore store() { return store; }
    private List<String> vanillaNames() throws Exception {
        Path file = auth.server.getServerDirectory().resolve("whitelist.json");
        if (!Files.exists(file)) return List.of();
        if (Files.size(file) > 4 * 1024 * 1024) throw new IllegalArgumentException("Vanilla whitelist file is too large");
        try (var reader = Files.newBufferedReader(file)) {
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonArray()) throw new IllegalArgumentException("Invalid vanilla whitelist");
            List<String> names = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray()) {
                String name = WhitelistStore.name(element.getAsJsonObject().get("name").getAsString());
                Account account = auth.store.find(name);
                names.add(account == null ? name : account.name());
            }
            return names;
        }
    }
    public Component denial(String name) {
        if (!store.healthy()) return AuthService.message(MessageKey.STORAGE_UNAVAILABLE);
        var snapshot = store.snapshot();
        WhitelistStore.Entry entry = snapshot.entries().get(Account.key(name));
        if (entry != null && !entry.name().equals(name)) return AuthService.message(MessageKey.NAME_CASE, entry.name());
        if (!snapshot.enabled()) return null;
        if (entry == null || !entry.enabled()) return AuthService.message(MessageKey.WHITELIST_DENIED);
        if (!entry.name().equals(name)) return AuthService.message(MessageKey.NAME_CASE, entry.name());
        return null;
    }
    public void enforce(String removedName) {
        auth.revokeWhitelistAccess(removedName);
    }
    public CompletableFuture<Map<String, Object>> execute(Map<String, String> request) {
        Map<String, String> copy = Map.copyOf(request);
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        if (closed) return CompletableFuture.completedFuture(error("NOT_READY"));
        try {
            worker.execute(() -> {
                String action = copy.getOrDefault("action", "");
                String name = copy.getOrDefault("name", "");
                try {
                    if (copy.containsKey("deadline") && Long.parseLong(copy.get("deadline")) < System.currentTimeMillis()) {
                        future.complete(error("EXPIRED_REQUEST")); return;
                    }
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("ok", true); result.put("code", "OK");
                    String actor = copy.getOrDefault("actor", "api");
                    synchronized (auth.store) {
                        if (Set.of("add", "remove", "set", "info").contains(action)) {
                            WhitelistStore.name(name);
                            Account account = auth.store.find(name);
                            if (account != null) name = account.name();
                        }
                        String removed = null;
                        switch (action) {
                            case "add", "remove", "set" -> {
                                String value = copy.getOrDefault("enabled", "true");
                                if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("INVALID_STATE");
                                var entry = store.mutate(action, name, Boolean.parseBoolean(value), actor);
                                if (action.equals("remove")) { result.put("name", entry.name()); result.put("removed", true); }
                                else result.put("entry", entry(entry));
                                if (action.equals("remove") || (action.equals("set") && !entry.enabled())) removed = entry.name();
                            }
                            case "on", "off" -> store.enabled(action.equals("on"), actor);
                            case "import" -> result.put("imported", store.importNames(vanillaNames(), actor));
                            case "info" -> {
                                var entry = store.find(name);
                                if (entry == null) throw new IllegalArgumentException("NOT_FOUND");
                                if (action.equals("remove")) { result.put("name", entry.name()); result.put("removed", true); }
                                else result.put("entry", entry(entry));
                            }
                            case "list" -> {
                                int page = Integer.parseInt(copy.getOrDefault("page", "1"));
                                var entries = store.snapshot().entries().values().stream()
                                        .sorted(Comparator.comparing(e -> Account.key(e.name()))).toList();
                                int pages = Math.max(1, (entries.size() + 19) / 20);
                                if (page < 1 || page > pages) throw new IllegalArgumentException("INVALID_PAGE");
                                result.put("page", page); result.put("pages", pages); result.put("total", entries.size());
                                result.put("entries", entries.stream().skip((long) (page - 1) * 20).limit(20).map(WhitelistService::entry).toList());
                            }
                            case "status" -> {}
                            default -> throw new IllegalArgumentException("INVALID_ACTION");
                        }
                        result.put("enabled", store.snapshot().enabled());
                        result.put("message", render(action, result));
                        String target = removed;
                        auth.server.execute(() -> {
                            if (target != null || action.equals("on")) enforce(target);
                            future.complete(Map.copyOf(result));
                        });
                        }
                } catch (IllegalArgumentException exception) {
                    future.complete(error(exception instanceof NumberFormatException ? "INVALID_PAGE" : exception.getMessage()));
                } catch (Exception exception) {
                    if (exception instanceof java.sql.SQLException) store.failClosed();
                    RabbitAuth.LOGGER.error("Whitelist operation failed", exception);
                    auth.server.execute(() -> enforce(null));
                    future.complete(error("STORAGE_UNAVAILABLE"));
                }
            });
        } catch (RejectedExecutionException exception) { future.complete(error("BUSY")); }
        return future;
    }
    private String render(String action, Map<String, Object> result) {
        String text = auth.messages.format(MessageKey.WHITELIST_RESULT, action, result.get("enabled"));
        if (result.containsKey("entry")) text += " " + display(result.get("entry"));
        if (result.containsKey("name")) text += " " + result.get("name");
        if (result.containsKey("entries")) text += " " + result.get("page") + "/" + result.get("pages") + " " + ((List<?>) result.get("entries")).stream().map(this::display).collect(java.util.stream.Collectors.joining(", "));
        if (result.containsKey("imported")) text += " " + result.get("imported");
        return text;
    }
    private String display(Object value) {
        Map<?, ?> entry = (Map<?, ?>) value;
        return entry.get("name") + " (" + auth.messages.format(Boolean.TRUE.equals(entry.get("enabled"))
                ? MessageKey.WHITELIST_ACTIVE : MessageKey.WHITELIST_PAUSED) + ")";
    }
    private Map<String, Object> error(String code) {
        return Map.of("ok", false, "code", code == null ? "INVALID_REQUEST" : code,
                "message", auth.messages.format(MessageKey.WHITELIST_ERROR, code));
    }
    private static Map<String, Object> entry(WhitelistStore.Entry entry) {
        return Map.of("name", entry.name(), "enabled", entry.enabled(), "created_at", entry.createdAt(),
                "updated_at", entry.updatedAt(), "actor", entry.actor());
    }
    @Override public void close() throws InterruptedException {
        closed = true; worker.shutdown();
        if (!worker.awaitTermination(30, TimeUnit.SECONDS)) {
            worker.shutdownNow();
            if (!worker.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("Whitelist worker did not stop");
        }
    }
}
