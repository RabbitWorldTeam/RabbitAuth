package com.tr4nce.rabbitauth.auth;

import com.mojang.authlib.GameProfile;
import com.tr4nce.rabbitauth.i18n.*;
import static com.tr4nce.rabbitauth.i18n.MessageKey.*;
import com.tr4nce.rabbitauth.RabbitAuth;
import com.tr4nce.rabbitauth.api.PlayerAuthenticatedEvent;
import com.tr4nce.rabbitauth.config.AuthConfig;
import com.tr4nce.rabbitauth.security.Crypt;
import com.tr4nce.rabbitauth.storage.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

public final class AuthService implements AutoCloseable {
    public final MinecraftServer server;
    public final AuthConfig config;
    public final AccountStore store;
    public final Messages messages;
    private final com.tr4nce.rabbitauth.whitelist.WhitelistService whitelist;
    private final Map<UUID, AuthSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Connection> admissions = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final LoginTickets tickets = new LoginTickets();
    private final FailureLimiter limiter = new FailureLimiter();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), runnable -> {
                Thread thread = new Thread(runnable, "RabbitAuth-worker");
                thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public AuthService(MinecraftServer server, AuthConfig config, AccountStore store, Messages messages) throws Exception {
        this.server = server; this.config = config; this.store = store; this.messages = messages;
        whitelist = new com.tr4nce.rabbitauth.whitelist.WhitelistService(this);
    }
    public com.tr4nce.rabbitauth.whitelist.WhitelistService whitelist() { return whitelist; }
    public void revokeWhitelistAccess(String target) {
        for (AuthSession session : sessions.values()) {
            String name = session.player.getGameProfile().getName();
            if ((target != null && name.equalsIgnoreCase(target)) || (target == null && whitelist.denial(name) != null)) {
                session.rememberAllowed = false;
                tickets.revoke(name);
                session.player.connection.disconnect(message(WHITELIST_DENIED));
            }
        }
        for (var entry : admissions.entrySet()) {
            String name = entry.getKey();
            // Keys are lowercase; use the approved spelling when checking only eligibility.
            var approved = whitelist.store().find(name);
            boolean denied = !whitelist.store().healthy() || (whitelist.store().snapshot().enabled() && (approved == null || !approved.enabled()));
            if ((target != null && name.equalsIgnoreCase(target)) || (target == null && denied)) {
                tickets.revoke(name);
                var listener = entry.getValue().getPacketListener();
                if (listener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl common) common.disconnect(message(WHITELIST_DENIED));
                else if (listener instanceof net.minecraft.server.network.ServerLoginPacketListenerImpl login) login.disconnect(message(WHITELIST_DENIED));
                else entry.getValue().disconnect(message(WHITELIST_DENIED));
            }
        }
        if (target != null) tickets.revoke(target);
    }
    public synchronized Component admission(Connection connection, GameProfile profile) {
        String name = profile.getName();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) return message(INVALID_NAME);
        Component accessDenied = whitelist.denial(name);
        if (accessDenied != null) return accessDenied;
        Account account = store.find(name);
        if (account != null && !account.name().equals(name)) return message(NAME_CASE, account.name());
        long tick = server.getTickCount();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= tick);
        if (cooldowns.getOrDefault(Account.key(name), 0L) > tick) return message(REENTER_WAIT);
        admissions.entrySet().removeIf(entry -> !entry.getValue().isConnected());
        int ipLimit = config.number("max-connections-per-ip", 0, 100000);
        if (ipLimit > 0 && admissions.values().stream().filter(c -> c != connection && ip(c.getRemoteAddress()).equals(ip(connection.getRemoteAddress()))).count() >= ipLimit)
            return message(IP_CONNECTION_LIMIT);
        Connection previous = admissions.putIfAbsent(Account.key(name), connection);
        if (previous != null && previous != connection) return message(DUPLICATE_CONNECTION);
        return null;
    }
    public static String ip(SocketAddress address) {
        return address instanceof InetSocketAddress inet && inet.getAddress() != null ? inet.getAddress().getHostAddress() : "";
    }
    public void join(ServerPlayer player) {
        Component denied = whitelist.denial(player.getGameProfile().getName());
        if (denied != null) { player.connection.disconnect(denied); return; }
        AuthSession session = new AuthSession(player, ip(player.connection.getRemoteAddress()),
                System.nanoTime() + TimeUnit.SECONDS.toNanos(config.number("login-timeout-seconds", 10, 3600)));
        session.returnPosition = store.pending(player.getUUID()) ? store.position(player.getUUID()) : null;
        if (session.returnPosition == null) session.returnPosition = com.tr4nce.rabbitauth.protection.Protection.capture(player);
        sessions.put(player.getUUID(), session);
        store.stagePosition(player.getUUID(), session.returnPosition);
        try {
            workers.execute(() -> {
                try {
                    store.persistPosition(player.getUUID(), session.returnPosition);
                    Account account = store.find(player.getGameProfile().getName());
                    boolean loopback = player.connection.getRemoteAddress() instanceof InetSocketAddress address
                            && address.getAddress() != null && address.getAddress().isLoopbackAddress();
                    boolean auto = account != null && ((config.flag("same-ip-auto-login") && tickets.valid(account, session.ip))
                            || (config.flag("loopback-auto-login") && loopback));
                    if (auto && current(session)) {
                        store.stagePosition(player.getUUID(), session.returnPosition, false);
                        store.persistPosition(player.getUUID(), session.returnPosition);
                    }
                    server.execute(() -> {
                        if (!current(session)) return;
                        com.tr4nce.rabbitauth.protection.Protection.enter(session);
                        session.protectionReady = true;
                        boolean ticketStillValid = account != null && ((config.flag("same-ip-auto-login") && tickets.valid(store.find(account.name()), session.ip))
                                || (config.flag("loopback-auto-login") && loopback));
                        if (auto && ticketStillValid && store.find(account.name()).passwordHash().equals(account.passwordHash())) authenticate(session);
                        else prompt(session);
                    });
                } catch (Exception exception) {
                    RabbitAuth.LOGGER.error("Unable to persist return position", exception);
                    server.execute(() -> { if (current(session)) player.connection.disconnect(message(STORAGE_UNAVAILABLE)); });
                }
            });
        } catch (RejectedExecutionException exception) { player.connection.disconnect(message(BUSY)); }
    }
    public void leave(ServerPlayer player) {
        AuthSession session = session(player);
        if (session == null) return;
        session.disconnected = true;
        cooldowns.put(Account.key(player.getGameProfile().getName()),
                (long) server.getTickCount() + config.number("reenter-cooldown-ticks", 0, 72000));
        if (session.authenticated) {
            if (session.rememberAllowed && config.flag("same-ip-auto-login")) tickets.issue(store.find(player.getGameProfile().getName()), session.ip,
                    config.number("auto-login-timeout-minutes", 1, 10080));
            SavedPosition position = com.tr4nce.rabbitauth.protection.Protection.capture(player);
            store.stagePosition(player.getUUID(), position, false);
            try { workers.execute(() -> {
                try { store.persistPosition(player.getUUID(), position); }
                catch (Exception exception) { RabbitAuth.LOGGER.error("Unable to save logout position", exception); }
            }); } catch (RejectedExecutionException exception) { RabbitAuth.LOGGER.error("Position queue full; vanilla player data remains available"); }
        }
    }
    public void respawn(ServerPlayer player) {
        AuthSession old = sessions.get(player.getUUID());
        if (old == null || !old.authenticated || old.player.connection != player.connection) {
            player.connection.disconnect(message(LOGIN_FIRST)); return;
        }
        AuthSession replacement = new AuthSession(player, old.ip, old.deadline);
        replacement.authenticated = true;
        replacement.protectionReady = true;
        replacement.returnPosition = old.returnPosition;
        sessions.put(player.getUUID(), replacement);
        // Respawn's initial inventory packets were masked before the new entity inherited the session.
        player.inventoryMenu.sendAllDataToRemote();
        player.containerMenu.sendAllDataToRemote();
    }
    public boolean authenticated(ServerPlayer player) {
        AuthSession session = sessions.get(player.getUUID());
        return session != null && session.player == player && session.authenticated;
    }
    public AuthSession session(ServerPlayer player) {
        AuthSession session = sessions.get(player.getUUID());
        return session != null && session.player == player ? session : null;
    }
    public void tick() {
        long now = System.nanoTime();
        if (server.getTickCount() % 1200 == 0) {
            tickets.purge(); limiter.purge();
            admissions.entrySet().removeIf(e -> !e.getValue().isConnected());
            cooldowns.entrySet().removeIf(e -> e.getValue() <= server.getTickCount());
        }
        sessions.entrySet().removeIf(entry -> entry.getValue().disconnected);
        for (AuthSession session : sessions.values()) {
            if (!session.authenticated) com.tr4nce.rabbitauth.protection.Protection.tick(session);
            if (!session.authenticated && now >= session.deadline) session.player.connection.disconnect(message(LOGIN_TIMEOUT));
            else if (!session.authenticated && server.getTickCount() % 200 == 0) prompt(session);
        }
    }
    private void prompt(AuthSession session) {
        send(session.player, store.find(session.player.getGameProfile().getName()) == null
                ? REGISTER_PROMPT : LOGIN_PROMPT);
    }
    private boolean current(AuthSession session) {
        return session(session.player) == session && session.player.connection.isAcceptingMessages()
                && (session.authenticated || System.nanoTime() < session.deadline);
    }
    public void authenticate(AuthSession session) {
        if (!current(session)) return;
        Component denied = whitelist.denial(session.player.getGameProfile().getName());
        if (denied != null) { session.rememberAllowed = false; session.player.connection.disconnect(denied); return; }
        session.authenticated = true;
        com.tr4nce.rabbitauth.protection.Protection.exit(session);
        send(session.player, SUCCESS);
        NeoForge.EVENT_BUS.post(new PlayerAuthenticatedEvent(session.player));
    }
    public void submit(ServerPlayer player, String operation, String first, String second) {
        AuthSession session = session(player);
        if (session == null) return;
        if (!session.protectionReady) { send(player, WAIT); return; }
        boolean change = operation.equals("change");
        if (change != session.authenticated) { send(player, change ? LOGIN_FIRST : ALREADY_AUTHENTICATED); return; }
        if (limiter.blocked(session.ip, config.number("max-login-attempts", 1, 100))) {
            player.connection.disconnect(message(TOO_MANY_FAILURES)); return;
        }
        if (session.busy || System.nanoTime() < session.nextAttempt) { send(player, WAIT); return; }
        Account account = store.find(player.getGameProfile().getName());
        if (operation.equals("register") && account != null) { send(player, ALREADY_REGISTERED); return; }
        if (!operation.equals("register") && account == null) { send(player, REGISTER_FIRST); return; }
        if (operation.equals("register") && !first.equals(second)) { send(player, PASSWORD_MISMATCH); return; }
        String newPassword = change ? second : first;
        int length = newPassword.length();
        if (first.length() > config.number("max-password-length", 8, 120) || length > config.number("max-password-length", 8, 120) || (!operation.equals("login") && length < config.number("min-password-length", 6, 256))) {
            send(player, PASSWORD_LENGTH); return;
        }
        char[] password = first.toCharArray();
        char[] replacement = change ? second.toCharArray() : new char[0];
        session.busy = true;
        session.nextAttempt = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        try {
            workers.execute(() -> {
                try {
                    if (!current(session)) return;
                    store.verifyAvailable();
                    boolean success;
                    if (operation.equals("register")) {
                        String hash = Crypt.hash(password);
                        if (!current(session)) return;
                        success = store.register(new Account(player.getGameProfile().getName(), hash, session.ip),
                                config.number("max-registrations-per-ip", 0, 100000));
                    } else {
                        success = Crypt.verify(password, account.passwordHash());
                        if (success && change) {
                            String hash = Crypt.hash(replacement);
                            if (!current(session)) return;
                            store.changePassword(account.name(), account.passwordHash(), hash);
                        }
                    }
                    if (!success) limiter.fail(session.ip);
                    if (success && !change && current(session)) {
                        store.stagePosition(player.getUUID(), session.returnPosition, false);
                        store.persistPosition(player.getUUID(), session.returnPosition);
                    }
                    server.execute(() -> {
                        if (!current(session)) return;
                        session.busy = false;
                        if (success && !change && account != null
                                && !store.find(account.name()).passwordHash().equals(account.passwordHash())) {
                            send(player, AUTH_FAILED); return;
                        }
                        if (success) {
                            if (change) { tickets.revoke(account.name()); send(player, PASSWORD_CHANGED); }
                            else authenticate(session);
                        } else {
                            if (++session.failures >= config.number("max-login-attempts", 1, 100)) player.connection.disconnect(message(TOO_MANY_FAILURES));
                            else send(player, AUTH_FAILED);
                        }
                    });
                } catch (Exception exception) {
                    RabbitAuth.LOGGER.error("Authentication storage operation failed", exception);
                    server.execute(() -> { if (current(session)) { session.busy = false; player.connection.disconnect(message(STORAGE_UNAVAILABLE)); } });
                } finally { Arrays.fill(password, '\0'); Arrays.fill(replacement, '\0'); }
            });
        } catch (RejectedExecutionException exception) {
            session.busy = false; Arrays.fill(password, '\0'); Arrays.fill(replacement, '\0');
            send(player, BUSY);
        }
    }
    public void resetPassword(net.minecraft.commands.CommandSourceStack source, String name, String value) {
        ServerPlayer actor = source.getPlayer();
        if (!source.hasPermission(4) || (actor != null && !authenticated(actor))) {
            source.sendFailure(message(NO_PERMISSION)); return;
        }
        Account account = store.find(name);
        if (account == null) { source.sendFailure(message(UNKNOWN_ACCOUNT)); return; }
        if (value.length() < config.number("min-password-length", 6, 120) || value.length() > config.number("max-password-length", 8, 120)) {
            source.sendFailure(message(PASSWORD_LENGTH)); return;
        }
        char[] password = value.toCharArray();
        try {
            workers.execute(() -> {
                try {
                    store.changePassword(account.name(), Crypt.hash(password));
                    tickets.revoke(account.name());
                    server.execute(() -> {
                        // Revoke again after concurrent logout callbacks could have issued a new ticket.
                        tickets.revoke(account.name());
                        ServerPlayer online = server.getPlayerList().getPlayerByName(account.name());
                        if (online != null) {
                            AuthSession session = session(online);
                            if (session != null) session.rememberAllowed = false;
                            online.connection.disconnect(message(LOGIN_FIRST));
                        }
                        source.sendSuccess(() -> message(RESET_SUCCESS, account.name()), false);
                    });
                } catch (Exception exception) {
                    RabbitAuth.LOGGER.error("Password reset storage operation failed", exception);
                    server.execute(() -> source.sendFailure(message(STORAGE_UNAVAILABLE)));
                } finally { Arrays.fill(password, '\0'); }
            });
        } catch (RejectedExecutionException exception) {
            Arrays.fill(password, '\0'); source.sendFailure(message(BUSY));
        }
    }
    public static Component message(MessageKey key, Object... arguments) {
        AuthService service = RabbitAuth.serviceOrNull();
        return Component.literal(service == null ? Messages.fallback(PREFIX) + Messages.fallback(key, arguments)
                : service.messages.format(PREFIX) + service.messages.format(key, arguments));
    }
    public static void send(ServerPlayer player, MessageKey key, Object... arguments) { player.sendSystemMessage(message(key, arguments)); }
    @Override public void close() throws Exception {
        workers.shutdown();
        if (!workers.awaitTermination(30, TimeUnit.SECONDS)) {
            workers.shutdownNow();
            if (!workers.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("Authentication workers did not stop");
        }
        whitelist.close();
        sessions.clear(); admissions.clear(); store.close();
    }
}
