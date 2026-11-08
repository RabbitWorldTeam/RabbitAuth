package com.tr4nce.rabbitauth.storage;

import com.tr4nce.rabbitauth.config.AuthConfig;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** One bounded connection, serialized writes, cache-only lookups on the game thread.
 * A database must belong to a single server process; cross-server cache sharing is unsupported.
 */
public final class AccountStore implements AutoCloseable {
    private record PositionState(SavedPosition position, boolean pending) {}
    private final Connection connection;
    private final com.tr4nce.rabbitauth.whitelist.WhitelistStore whitelist;
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<UUID, PositionState> positions = new ConcurrentHashMap<>();

    public AccountStore(Path database) throws Exception { this(openSqlite(database), true); }
    public AccountStore(AuthConfig config, Path directory) throws Exception {
        this(open(config, directory), config.text("storage").equals("sqlite"));
    }
    private static Connection openSqlite(Path path) throws Exception {
        Class.forName("org.sqlite.JDBC");
        return DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
    }
    private static Connection open(AuthConfig config, Path directory) throws Exception {
        return switch (config.text("storage")) {
            case "sqlite" -> {
                Path path = directory.resolve(config.text("sqlite-file")).normalize();
                if (!path.startsWith(directory.normalize())) throw new IllegalArgumentException("SQLite file must be inside RabbitAuth directory");
                java.nio.file.Files.createDirectories(path.getParent());
                yield openSqlite(path);
            }
            case "mysql" -> {
                Class.forName("com.mysql.cj.jdbc.Driver");
                String url = config.text("mysql-url");
                if (!url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException("Expected jdbc:mysql:// URL");
                Properties properties = new Properties();
                properties.setProperty("user", config.text("mysql-user"));
                properties.setProperty("password", config.raw("mysql-password"));
                properties.setProperty("connectTimeout", "5000");
                properties.setProperty("socketTimeout", "10000");
                yield DriverManager.getConnection(url, properties);
            }
            default -> throw new IllegalArgumentException("Storage must be sqlite or mysql");
        };
    }
    private AccountStore(Connection connection, boolean sqlite) throws SQLException {
        this.connection = connection;
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            if (sqlite) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=FULL");
                statement.execute("PRAGMA busy_timeout=5000");
            }
            statement.execute("CREATE TABLE IF NOT EXISTS auth_meta (id INTEGER PRIMARY KEY, version INTEGER NOT NULL)");
            try (ResultSet rows = statement.executeQuery("SELECT version FROM auth_meta WHERE id=1")) {
                if (rows.next() && rows.getInt(1) != 1) throw new SQLException("Unsupported authentication schema version");
            }
            statement.execute("CREATE TABLE IF NOT EXISTS accounts (name_key VARCHAR(16) PRIMARY KEY, name VARCHAR(16) NOT NULL, password_hash VARCHAR(255) NOT NULL, registration_ip VARCHAR(64) NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS positions (player_id VARCHAR(36) PRIMARY KEY, dimension VARCHAR(255) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, pending INTEGER NOT NULL DEFAULT 1)");
            try (ResultSet columns = connection.getMetaData().getColumns(null, null, "positions", "pending")) {
                if (!columns.next()) statement.execute("ALTER TABLE positions ADD COLUMN pending INTEGER NOT NULL DEFAULT 1");
            }
            try (ResultSet rows = statement.executeQuery("SELECT version FROM auth_meta WHERE id=1")) {
                if (!rows.next()) statement.executeUpdate("INSERT INTO auth_meta (id,version) VALUES (1,1)");
            }
            try (ResultSet rows = statement.executeQuery("SELECT name, password_hash, registration_ip FROM accounts")) {
                while (rows.next()) {
                    Account account = new Account(rows.getString(1), rows.getString(2), rows.getString(3));
                    if (!account.name().matches("[A-Za-z0-9_]{3,16}")) throw new SQLException("Invalid stored account name");
                    if (accounts.putIfAbsent(Account.key(account.name()), account) != null) throw new SQLException("Duplicate case-insensitive account");
                }
            }
            try (ResultSet rows = statement.executeQuery("SELECT player_id,dimension,x,y,z,yaw,pitch,pending FROM positions")) {
                while (rows.next()) positions.put(UUID.fromString(rows.getString(1)), new PositionState(
                        new SavedPosition(rows.getString(2), rows.getDouble(3), rows.getDouble(4), rows.getDouble(5), rows.getFloat(6), rows.getFloat(7)), rows.getInt(8) != 0));
            }
            whitelist = new com.tr4nce.rabbitauth.whitelist.WhitelistStore(connection, this);
        } catch (Exception exception) {
            try { connection.close(); } catch (SQLException close) { exception.addSuppressed(close); }
            if (exception instanceof SQLException sql) throw sql;
            throw new SQLException("Invalid authentication storage", exception);
        }
    }
    public com.tr4nce.rabbitauth.whitelist.WhitelistStore whitelist() { return whitelist; }
    public Account find(String name) { return accounts.get(Account.key(name)); }
    public SavedPosition position(UUID id) { PositionState state = positions.get(id); return state == null ? null : state.position(); }
    public boolean pending(UUID id) { PositionState state = positions.get(id); return state != null && state.pending(); }
    public void stagePosition(UUID id, SavedPosition position) { stagePosition(id, position, true); }
    public void stagePosition(UUID id, SavedPosition position, boolean pending) { positions.put(id, new PositionState(position, pending)); }
    public synchronized void verifyAvailable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (ResultSet rows = statement.executeQuery("SELECT name_key FROM accounts LIMIT 1")) {
                rows.next(); // Executing the query also verifies that the authentication table exists.
            }
        }
    }
    public synchronized void persistPosition(UUID id, SavedPosition position) throws SQLException {
        PositionState state = positions.get(id);
        if (state == null || state.position() != position) return;
        connection.setAutoCommit(false);
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM positions WHERE player_id=?");
             PreparedStatement insert = connection.prepareStatement("INSERT INTO positions (player_id,dimension,x,y,z,yaw,pitch,pending) VALUES (?,?,?,?,?,?,?,?)")) {
            delete.setQueryTimeout(10); insert.setQueryTimeout(10);
            delete.setString(1, id.toString()); delete.executeUpdate();
            insert.setString(1, id.toString()); insert.setString(2, position.dimension());
            insert.setDouble(3, position.x()); insert.setDouble(4, position.y()); insert.setDouble(5, position.z());
            insert.setFloat(6, position.yaw()); insert.setFloat(7, position.pitch()); insert.setInt(8, state.pending() ? 1 : 0); insert.executeUpdate();
            connection.commit();
        } catch (SQLException exception) { connection.rollback(); throw exception; }
        finally { connection.setAutoCommit(true); }
    }
    public synchronized boolean register(Account account, int ipLimit) throws SQLException {
        String key = Account.key(account.name());
        var approved = whitelist.find(account.name());
        if (approved != null && !approved.name().equals(account.name())) return false;
        if (accounts.containsKey(key)) return false;
        if (ipLimit > 0 && accounts.values().stream().filter(a -> a.registrationIp().equals(account.registrationIp())).count() >= ipLimit) return false;
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO accounts (name_key,name,password_hash,registration_ip) VALUES (?,?,?,?)")) {
            statement.setQueryTimeout(10);
            statement.setString(1, key); statement.setString(2, account.name());
            statement.setString(3, account.passwordHash()); statement.setString(4, account.registrationIp()); statement.executeUpdate();
        }
        accounts.put(key, account);
        return true;
    }
    public synchronized void changePassword(String name, String hash) throws SQLException { changePassword(name, null, hash); }
    public synchronized void changePassword(String name, String expectedHash, String hash) throws SQLException {
        Account old = find(name);
        if (old == null) throw new SQLException("Unknown account");
        try (PreparedStatement statement = connection.prepareStatement("UPDATE accounts SET password_hash=? WHERE name_key=?" + (expectedHash == null ? "" : " AND password_hash=?"))) {
            statement.setQueryTimeout(10);
            statement.setString(1, hash); statement.setString(2, Account.key(name));
            if (expectedHash != null) statement.setString(3, expectedHash);
            if (statement.executeUpdate() != 1) throw new SQLException("Account update failed");
        }
        accounts.put(Account.key(name), new Account(old.name(), hash, old.registrationIp()));
    }
    @Override public synchronized void close() throws SQLException { connection.close(); }
}
