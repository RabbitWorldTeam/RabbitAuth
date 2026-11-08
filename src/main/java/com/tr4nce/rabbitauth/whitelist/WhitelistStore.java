package com.tr4nce.rabbitauth.whitelist;

import com.tr4nce.rabbitauth.storage.Account;
import java.sql.*;
import java.util.*;

/** Shares the account connection's lock; no SQL is performed by cache readers. */
public final class WhitelistStore {
    public record Entry(String name, boolean enabled, long createdAt, long updatedAt, String actor) {}
    public record Snapshot(boolean enabled, Map<String, Entry> entries) {}
    private final Connection connection;
    private final Object lock;
    private volatile Snapshot snapshot;
    private boolean initialized;
    private volatile boolean healthy = true;
    public boolean healthy() { return healthy; }
    public void failClosed() { healthy = false; }
    public WhitelistStore(Connection connection, Object lock) throws SQLException {
        this.connection = connection; this.lock = lock;
        try (Statement sql = connection.createStatement()) {
            sql.setQueryTimeout(10);
            sql.execute("CREATE TABLE IF NOT EXISTS whitelist_settings (id INTEGER PRIMARY KEY, enabled INTEGER NOT NULL)");
            sql.execute("CREATE TABLE IF NOT EXISTS access_whitelist (name_key VARCHAR(16) PRIMARY KEY, name VARCHAR(16) NOT NULL, enabled INTEGER NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, actor VARCHAR(384) NOT NULL)");
            sql.execute("CREATE TABLE IF NOT EXISTS whitelist_audit (event_id VARCHAR(36) PRIMARY KEY, at_ms BIGINT NOT NULL, actor VARCHAR(384) NOT NULL, action VARCHAR(32) NOT NULL, player VARCHAR(16) NOT NULL)");
            boolean enabled = false;
            try (ResultSet rows = sql.executeQuery("SELECT enabled FROM whitelist_settings WHERE id=1")) {
                if (rows.next()) { initialized = true; enabled = rows.getInt(1) != 0; }
            }
            Map<String, Entry> entries = new HashMap<>();
            try (ResultSet rows = sql.executeQuery("SELECT name,enabled,created_at,updated_at,actor FROM access_whitelist")) {
                while (rows.next()) {
                    String name = name(rows.getString(1));
                    if (entries.putIfAbsent(Account.key(name), new Entry(name, rows.getInt(2) != 0, rows.getLong(3), rows.getLong(4), rows.getString(5))) != null)
                        throw new SQLException("Duplicate whitelist identity");
                }
            }
            snapshot = new Snapshot(enabled, Map.copyOf(entries));
        }
    }
    public static String name(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("INVALID_NAME");
        return name;
    }
    public boolean initialized() { return initialized; }
    public Snapshot snapshot() { return snapshot; }
    public Entry find(String name) { return snapshot.entries().get(Account.key(name)); }
    public boolean allows(String name) {
        if (!healthy) return false;
        Snapshot current = snapshot;
        Entry entry = current.entries().get(Account.key(name));
        return !current.enabled() || (entry != null && entry.enabled() && entry.name().equals(name));
    }
    public void bootstrap(boolean enabled, Collection<String> names) throws SQLException {
        synchronized (lock) {
            if (initialized) return;
            Map<String, Entry> next = new HashMap<>();
            long now = System.currentTimeMillis();
            for (String value : names) {
                String name = name(value);
                next.putIfAbsent(Account.key(name), new Entry(name, true, now, now, "vanilla-import"));
            }
            transaction(() -> {
                try (PreparedStatement sql = prepare("INSERT INTO whitelist_settings (id,enabled) VALUES (1,?)")) {
                    sql.setInt(1, enabled ? 1 : 0); sql.executeUpdate();
                }
                for (Entry entry : next.values()) insert(entry);
                audit("vanilla-import", "bootstrap", "");
            });
            snapshot = new Snapshot(enabled, Map.copyOf(next)); initialized = true;
        }
    }
    public Entry mutate(String operation, String value, boolean enabled, String actor) throws SQLException {
        String name = name(value);
        validateActor(actor);
        synchronized (lock) {
            Snapshot old = snapshot;
            Entry before = old.entries().get(Account.key(name));
            if (operation.equals("add") && before != null) throw new IllegalArgumentException("ALREADY_EXISTS");
            if (!operation.equals("add") && before == null) throw new IllegalArgumentException("NOT_FOUND");
            if (!Set.of("add", "remove", "set").contains(operation)) throw new IllegalArgumentException("INVALID_ACTION");
            long now = System.currentTimeMillis();
            Entry after = new Entry(before == null ? name : before.name(), operation.equals("add") || enabled,
                    before == null ? now : before.createdAt(), now, actor);
            transaction(() -> {
                if (before != null) {
                    try (PreparedStatement sql = prepare("DELETE FROM access_whitelist WHERE name_key=?")) {
                        sql.setString(1, Account.key(name)); sql.executeUpdate();
                    }
                }
                if (!operation.equals("remove")) insert(after);
                audit(actor, operation.equals("set") ? enabled ? "enable" : "pause" : operation, after.name());
            });
            Map<String, Entry> next = new HashMap<>(old.entries());
            if (operation.equals("remove")) next.remove(Account.key(name)); else next.put(Account.key(name), after);
            snapshot = new Snapshot(old.enabled(), Map.copyOf(next));
            return after;
        }
    }
    public void enabled(boolean enabled, String actor) throws SQLException {
        validateActor(actor);
        synchronized (lock) {
            transaction(() -> {
                try (PreparedStatement sql = prepare("UPDATE whitelist_settings SET enabled=? WHERE id=1")) {
                    sql.setInt(1, enabled ? 1 : 0);
                    if (sql.executeUpdate() != 1) throw new SQLException("Whitelist is not initialized");
                }
                audit(actor, enabled ? "on" : "off", "");
            });
            snapshot = new Snapshot(enabled, snapshot.entries());
        }
    }
    public int importNames(Collection<String> names, String actor) throws SQLException {
        validateActor(actor);
        synchronized (lock) {
            Map<String, Entry> next = new HashMap<>(snapshot.entries());
            List<Entry> additions = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (String value : names) {
                String name = name(value);
                if (!next.containsKey(Account.key(name))) {
                    Entry entry = new Entry(name, true, now, now, actor);
                    next.put(Account.key(name), entry); additions.add(entry);
                }
            }
            transaction(() -> { for (Entry entry : additions) { insert(entry); audit(actor, "import", entry.name()); } });
            snapshot = new Snapshot(snapshot.enabled(), Map.copyOf(next));
            return additions.size();
        }
    }
    private void insert(Entry entry) throws SQLException {
        try (PreparedStatement sql = prepare("INSERT INTO access_whitelist (name_key,name,enabled,created_at,updated_at,actor) VALUES (?,?,?,?,?,?)")) {
            sql.setString(1, Account.key(entry.name())); sql.setString(2, entry.name()); sql.setInt(3, entry.enabled() ? 1 : 0);
            sql.setLong(4, entry.createdAt()); sql.setLong(5, entry.updatedAt()); sql.setString(6, entry.actor()); sql.executeUpdate();
        }
    }
    private void audit(String actor, String operation, String name) throws SQLException {
        try (PreparedStatement sql = prepare("INSERT INTO whitelist_audit (event_id,at_ms,actor,action,player) VALUES (?,?,?,?,?)")) {
            sql.setString(1, UUID.randomUUID().toString()); sql.setLong(2, System.currentTimeMillis()); sql.setString(3, actor);
            sql.setString(4, operation); sql.setString(5, name); sql.executeUpdate();
        }
    }
    private static void validateActor(String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 384 || actor.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("INVALID_ACTOR");
    }
    private PreparedStatement prepare(String text) throws SQLException {
        PreparedStatement sql = connection.prepareStatement(text); sql.setQueryTimeout(10); return sql;
    }
    private interface Write { void run() throws SQLException; }
    private void transaction(Write write) throws SQLException {
        connection.setAutoCommit(false);
        try { write.run(); connection.commit(); }
        catch (SQLException exception) { healthy = false; connection.rollback(); throw exception; }
        catch (RuntimeException exception) { connection.rollback(); throw exception; }
        finally { connection.setAutoCommit(true); }
    }
}
