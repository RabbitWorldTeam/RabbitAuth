package com.tr4nce.rabbitauth;

import com.tr4nce.rabbitauth.security.Crypt;
import com.tr4nce.rabbitauth.storage.*;
import java.nio.file.*;
import java.util.*;

public final class AuthRegressionTest {
    private static int checks;
    public static void main(String[] arguments) throws Exception {
        char[] password = "Correct-密码-123".toCharArray();
        String first = Crypt.hash(password), second = Crypt.hash(password);
        check(!first.equals(second), "random per-password salt");
        check(Crypt.verify(password, first), "correct password");
        check(!Crypt.verify("incorrect".toCharArray(), first), "wrong password");
        check(!Crypt.verify(password, "$argon2id$invalid"), "malformed hash rejected");
        Path directory = Files.createTempDirectory(Path.of("."), "auth-regression-");
        Path database = directory.resolve("accounts.db");
        try {
            try (AccountStore store = new AccountStore(database)) {
                check(store.register(new Account("Rabbit", first, "127.0.0.1"), 1), "account persisted");
                check(store.find("RABBIT").name().equals("Rabbit"), "case-insensitive index, original name");
                check(!store.register(new Account("rabbit", second, "other"), 0), "case duplicate rejected");
                check(!store.register(new Account("Second", second, "127.0.0.1"), 1), "registration IP limit");
                try {
                    store.changePassword("RABBIT", "incorrect-old-hash", second);
                    throw new AssertionError("Stale password update accepted");
                } catch (java.sql.SQLException expected) { check(store.find("Rabbit").passwordHash().equals(first), "stale password mutation cannot overwrite account"); }
                store.changePassword("RABBIT", first, second);
                UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
                SavedPosition position = new SavedPosition("minecraft:the_nether", 12.5, 70, -30, 90, 12);
                store.stagePosition(id, position);
                store.persistPosition(id, position);
            }
            try (AccountStore store = new AccountStore(database)) {
                check(store.find("rabbit").passwordHash().equals(second), "password survives reopen");
                SavedPosition position = store.position(UUID.fromString("00000000-0000-0000-0000-000000000001"));
                check(store.pending(UUID.fromString("00000000-0000-0000-0000-000000000001")), "pending return survives restart before authentication");
                check(position.dimension().equals("minecraft:the_nether") && position.x() == 12.5 && position.yaw() == 90, "cross-dimension return position survives reopen");
                try {
                    new SavedPosition("minecraft:overworld", Double.NaN, 0, 0, 0, 0);
                    throw new AssertionError("NaN position accepted");
                } catch (IllegalArgumentException expected) { checks++; }
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong();
        var tickets = new com.tr4nce.rabbitauth.auth.LoginTickets(clock::get);
        Account account = new Account("Rabbit", first, "127.0.0.1");
        check(!tickets.valid(account, "127.0.0.1"), "no ticket before authenticated logout");
        tickets.issue(account, "127.0.0.1", 1);
        check(tickets.valid(account, "127.0.0.1"), "same IP ticket");
        check(!tickets.valid(account, "127.0.0.2"), "changed IP rejected");
        check(!tickets.valid(new Account("Rabbit", second, "127.0.0.1"), "127.0.0.1"), "password change invalidates ticket");
        check(!tickets.valid(null, "127.0.0.1"), "unregistered account cannot skip login");
        clock.set(java.util.concurrent.TimeUnit.MINUTES.toNanos(1));
        check(!tickets.valid(account, "127.0.0.1"), "ticket expires at exact timeout");
        clock.set(0); tickets.issue(account, "127.0.0.1", 1); tickets.revoke("RABBIT");
        check(!tickets.valid(account, "127.0.0.1"), "case-insensitive explicit revocation");
        additionalChecks(first);
        whitelistChecks(first);
        Arrays.fill(password, '\0');
        System.out.println("RabbitAuth regression checks passed: " + checks);
    }

    private static void additionalChecks(String hash) throws Exception {
        java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong();
        var limiter = new com.tr4nce.rabbitauth.auth.FailureLimiter(time::get);
        limiter.fail("ip"); limiter.fail("ip");
        check(limiter.blocked("ip", 2), "failure limit persists independently of connection");
        check(!limiter.blocked("different-ip", 2), "failure limit isolates addresses");
        time.set(java.util.concurrent.TimeUnit.MINUTES.toNanos(5));
        check(!limiter.blocked("ip", 2), "failure cooldown expires");
        var rules = List.of(java.util.regex.Pattern.compile("/help(?: .*)?"));
        check(com.tr4nce.rabbitauth.auth.CommandPolicy.allowed("login secret", List.of()), "login always available");
        check(com.tr4nce.rabbitauth.auth.CommandPolicy.allowed("/register a a", List.of()), "registration always available");
        check(com.tr4nce.rabbitauth.auth.CommandPolicy.allowed("help page", rules), "regex whitelist");
        check(!com.tr4nce.rabbitauth.auth.CommandPolicy.allowed("minecraft:help", rules), "namespace cannot bypass whitelist");
        check(!com.tr4nce.rabbitauth.auth.CommandPolicy.allowed("op Rabbit", rules), "privileged command blocked");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            check(Account.key("RABBIT").equals("rabbit"), "account key independent of system locale");
        } finally { Locale.setDefault(previous); }
        Path directory = Files.createTempDirectory(Path.of("."), "auth-additional-");
        try {
            var config = new com.tr4nce.rabbitauth.config.AuthConfig(directory);
            check(!config.flag("loopback-auto-login") && !config.flag("same-ip-auto-login"), "automatic login disabled by default");
            var english = new com.tr4nce.rabbitauth.i18n.Messages(directory, "en-US");
            var chinese = new com.tr4nce.rabbitauth.i18n.Messages(directory, "zh-CN");
            check(chinese.format(com.tr4nce.rabbitauth.i18n.MessageKey.SUCCESS).contains("成功"), "Chinese UTF-8 messages");
            for (String language : List.of("en-US", "zh-CN")) {
                Properties bundled = new Properties();
                try (var input = AuthRegressionTest.class.getResourceAsStream("/languages/" + language + ".properties")) {
                    bundled.load(new java.io.InputStreamReader(Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8));
                }
                for (var key : com.tr4nce.rabbitauth.i18n.MessageKey.values()) check(bundled.containsKey(key.name()), language + " message completeness " + key);
            }
            Files.writeString(directory.resolve("languages/custom.properties"), "SUCCESS=自定义成功\nNAME_CASE=Name: {0}\n");
            var custom = new com.tr4nce.rabbitauth.i18n.Messages(directory, "custom");
            check(custom.format(com.tr4nce.rabbitauth.i18n.MessageKey.SUCCESS).equals("自定义成功"), "custom language override");
            check(custom.format(com.tr4nce.rabbitauth.i18n.MessageKey.LOGIN_FIRST).equals(english.format(com.tr4nce.rabbitauth.i18n.MessageKey.LOGIN_FIRST)), "missing custom message falls back to English");
            check(custom.format(com.tr4nce.rabbitauth.i18n.MessageKey.NAME_CASE, "{1}").equals("Name: {1}"), "message values do not inject new placeholders");
            try {
                new com.tr4nce.rabbitauth.i18n.Messages(directory, "../escape");
                throw new AssertionError("Language path traversal accepted");
            } catch (IllegalArgumentException expected) { checks++; }
            Path configFile = directory.resolve("auth.properties");
            String valid = Files.readString(configFile);
            Files.writeString(configFile, valid + "\nbefore-login-allow-chat=perhaps\n");
            try {
                new com.tr4nce.rabbitauth.config.AuthConfig(directory);
                throw new AssertionError("Invalid protection config accepted");
            } catch (IllegalArgumentException expected) { checks++; }
            Files.writeString(configFile, valid + "\ncommand-whitelist=[invalid\n");
            try {
                new com.tr4nce.rabbitauth.config.AuthConfig(directory);
                throw new AssertionError("Invalid whitelist regex accepted");
            } catch (IllegalArgumentException expected) { checks++; }
            Path database = directory.resolve("concurrent.db");
            UUID id = UUID.randomUUID();
            SavedPosition older = new SavedPosition("minecraft:overworld", 10, 64, 10, 0, 0);
            SavedPosition newer = new SavedPosition("minecraft:the_end", 20, 80, 20, 45, 10);
            try (AccountStore store = new AccountStore(database)) {
                var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
                try {
                    List<java.util.concurrent.Future<Boolean>> futures = new ArrayList<>();
                    for (int i = 0; i < 8; i++) {
                        int index = i;
                        futures.add(executor.submit(() -> store.register(new Account("Thread" + index, hash, "shared-ip"), 1)));
                    }
                    int successes = 0;
                    for (var future : futures) if (future.get()) successes++;
                    check(successes == 1, "concurrent registrations cannot exceed IP limit");
                } finally { executor.shutdownNow(); }
                store.stagePosition(id, older);
                store.stagePosition(id, newer, false);
                store.persistPosition(id, older);
                store.persistPosition(id, newer);
            }
            try (AccountStore store = new AccountStore(database)) {
                check(store.position(id).equals(newer), "stale async location write cannot overwrite newer position");
                check(!store.pending(id), "completed authentication marker survives reopen");
                try (var other = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
                     var statement = other.createStatement()) { statement.execute("DROP TABLE accounts"); }
                try {
                    store.register(new Account("Failure", hash, "new-ip"), 0);
                    throw new AssertionError("Storage failure was ignored");
                } catch (java.sql.SQLException expected) {
                    check(store.find("Failure") == null, "failed database insert cannot publish cached account");
                }
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }


    private static void whitelistChecks(String hash) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("."), "whitelist-regression-");
        Path database = directory.resolve("access.db");
        try {
            try (AccountStore accounts = new AccountStore(database)) {
                var whitelist = accounts.whitelist();
                whitelist.bootstrap(true, List.of("Rabbit"));
                check(whitelist.allows("Rabbit") && !whitelist.allows("rabbit"), "whitelist preserves canonical offline identity");
                check(!whitelist.allows("Unknown"), "unlisted identity denied");
                whitelist.mutate("add", "Unregistered", true, "console");
                check(whitelist.allows("Unregistered") && accounts.find("Unregistered") == null, "whitelist supports accounts before registration");
                try {
                    whitelist.mutate("add", "RABBIT", true, "console");
                    throw new AssertionError("Case duplicate whitelist accepted");
                } catch (IllegalArgumentException expected) { checks++; }
                whitelist.mutate("set", "RABBIT", false, "bridge:group:admin");
                check(!whitelist.allows("Rabbit") && whitelist.find("rabbit") != null, "pause retains whitelist identity and denies admission");
                check(whitelist.importNames(List.of("Rabbit", "Imported"), "console") == 1 && !whitelist.find("Rabbit").enabled(), "import cannot silently reactivate paused identity");
                accounts.register(new Account("Rabbit", hash, "ip"), 0);
                whitelist.mutate("remove", "RABBIT", true, "console");
                check(whitelist.find("Rabbit") == null && accounts.find("Rabbit") != null, "whitelist removal preserves authentication account");
                whitelist.enabled(false, "console");
                check(whitelist.allows("Unknown"), "global whitelist off permits entry");
                check(!accounts.register(new Account("UNREGISTERED", hash, "other-ip"), 0), "registration cannot contradict reserved whitelist spelling even when globally off");
                whitelist.enabled(true, "console");
                var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
                try {
                    List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
                    for (int i = 0; i < 8; i++) {
                        int index = i;
                        futures.add(executor.submit(() -> {
                            accounts.register(new Account("Player" + index, hash, "ip" + index), 0);
                            whitelist.mutate("add", "Player" + index, true, "console");
                            return null;
                        }));
                    }
                    for (var future : futures) future.get();
                    check(whitelist.allows("Player7") && accounts.find("Player7") != null, "whitelist and accounts serialize shared JDBC connection");
                } finally { executor.shutdownNow(); }
            }
            try (AccountStore accounts = new AccountStore(database)) {
                var whitelist = accounts.whitelist();
                whitelist.bootstrap(false, List.of("Ignored"));
                check(whitelist.snapshot().enabled() && whitelist.find("Ignored") == null, "bootstrap is one-time and whitelist enabled survives reopen");
                check(whitelist.find("Imported") != null && whitelist.find("Rabbit") == null, "whitelist membership survives reopen");
                try (var other = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
                     var sql = other.createStatement()) { sql.execute("DROP TABLE whitelist_audit"); }
                try {
                    whitelist.mutate("add", "RolledBack", true, "console");
                    throw new AssertionError("Unaudited mutation accepted");
                } catch (java.sql.SQLException expected) {
                    check(whitelist.find("RolledBack") == null && !whitelist.healthy(), "audit failure rolls back cache publication and fails closed");
                }
                try (var other = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
                     var sql = other.createStatement(); var rows = sql.executeQuery("SELECT COUNT(*) FROM access_whitelist WHERE name_key='rolledback'")) {
                    rows.next(); check(rows.getInt(1) == 0, "audit and whitelist writes share one transaction");
                }
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void check(boolean result, String description) {
        if (!result) throw new AssertionError(description);
        checks++;
    }
}
