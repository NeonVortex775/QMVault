package com.qingming.vault.data;

import com.qingming.vault.VaultPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** SQL backend shared by SQLite and MySQL. All JDBC calls go through one worker thread. */
public final class SqlStorage implements VaultStorage {

    private final VaultPlugin plugin;
    private final String kind;
    private final String prefix;
    private final String itemsTable;
    private final String usersTable;

    private String url;
    private String user = "";
    private String pass = "";
    private String label = "";

    private Connection conn;
    private final ExecutorService queue;

    public SqlStorage(VaultPlugin plugin, String kind) {
        this.plugin = plugin;
        this.kind = "mysql".equalsIgnoreCase(kind) ? "mysql" : "sqlite";

        FileConfiguration cfg = plugin.getConfig();
        String raw = cfg.getString("storage.table-prefix", "qm_");
        if (raw == null) {
            raw = "qm_";
        }
        this.prefix = raw.replaceAll("[^A-Za-z0-9_]", "");
        this.itemsTable = prefix + "vault_items";
        this.usersTable = prefix + "vault_users";

        this.queue = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "QingMingVault-DB");
                t.setDaemon(true);
                return t;
            }
        });
    }

    @Override
    public String describe() {
        return label.isEmpty() ? kind : label;
    }

    @Override
    public void init() throws Exception {
        FileConfiguration cfg = plugin.getConfig();
        if ("sqlite".equals(kind)) {
            Class.forName("org.sqlite.JDBC");
            String rel = cfg.getString("storage.file", "data/vault.db");
            File f = new File(plugin.getDataFolder(), rel);
            File dir = f.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                plugin.getLogger().warning("无法创建数据库目录: " + dir.getAbsolutePath());
            }
            url = "jdbc:sqlite:" + f.getAbsolutePath();
            label = "sqlite (" + f.getName() + ")";
        } else {
            loadMySqlDriver();
            String host = cfg.getString("storage.host", "127.0.0.1");
            int port = cfg.getInt("storage.port", 3306);
            String db = cfg.getString("storage.database", "minecraft");
            user = cfg.getString("storage.user", "root");
            pass = cfg.getString("storage.password", "");
            url = "jdbc:mysql://" + host + ":" + port + "/" + db
                    + "?useSSL=false&allowPublicKeyRetrieval=true"
                    + "&characterEncoding=utf8&useUnicode=true"
                    + "&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true";
            label = "mysql (" + host + ":" + port + "/" + db + ")";
        }

        final Connection c = connect();
        try {
            createTable(c, itemsSql());
            createTable(c, usersSql());
            conn = c;
        } catch (SQLException e) {
            closeQuietly(c);
            throw e;
        }
    }

    private String itemsSql() {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(itemsTable).append(" (");
        if ("mysql".equals(kind)) {
            sb.append("uuid VARCHAR(36) NOT NULL, idx INT NOT NULL,")
              .append(" item MEDIUMTEXT NOT NULL, amount BIGINT NOT NULL,");
        } else {
            sb.append("uuid TEXT NOT NULL, idx INTEGER NOT NULL,")
              .append(" item TEXT NOT NULL, amount INTEGER NOT NULL,");
        }
        sb.append(" PRIMARY KEY (uuid, idx))");
        if ("mysql".equals(kind)) {
            sb.append(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
        return sb.toString();
    }

    private String usersSql() {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(usersTable).append(" (");
        if ("mysql".equals(kind)) {
            sb.append("uuid VARCHAR(36) NOT NULL, name VARCHAR(32) NULL,")
              .append(" kinds INT NOT NULL DEFAULT 0, total BIGINT NOT NULL DEFAULT 0,")
              .append(" updated BIGINT NOT NULL, PRIMARY KEY (uuid))")
              .append(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } else {
            sb.append("uuid TEXT NOT NULL PRIMARY KEY, name TEXT NULL,")
              .append(" kinds INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL DEFAULT 0,")
              .append(" updated INTEGER NOT NULL)");
        }
        return sb.toString();
    }

    private void createTable(Connection c, String sql) throws SQLException {
        Statement st = c.createStatement();
        try {
            st.execute(sql);
        } finally {
            closeQuietly(st);
        }
    }

    private void loadMySqlDriver() throws Exception {

        for (String name : new String[]{"com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver"}) {
            try {
                Class.forName(name);
                return;
            } catch (Throwable ignored) {

            }
        }

        File dir = new File(plugin.getDataFolder(), "lib");
        File[] jars = dir.listFiles();
        if (jars != null && jars.length > 0) {
            List<URL> urls = new ArrayList<URL>();
            for (File f : jars) {
                if (f.getName().toLowerCase().endsWith(".jar")) {
                    urls.add(f.toURI().toURL());
                }
            }
            if (!urls.isEmpty()) {
                URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[urls.size()]),
                        getClass().getClassLoader());
                for (String name : new String[]{"com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver"}) {
                    try {
                        Class<?> cls = Class.forName(name, true, cl);
                        Driver d = (Driver) cls.newInstance();
                        DriverManager.registerDriver(new DriverShim(d));
                        plugin.getLogger().info("已从 " + dir.getName() + "/ 加载 MySQL 驱动: " + name);
                        return;
                    } catch (Throwable ignored) {

                    }
                }
            }
        }
        throw new Exception("找不到 MySQL 驱动。把 mysql-connector-java-x.x.x.jar 放到 "
                + dir.getAbsolutePath() + " 里再重启，或改用 storage.type: sqlite");
    }

    private static final class DriverShim implements Driver {
        private final Driver d;

        DriverShim(Driver d) {
            this.d = d;
        }

        @Override
        public boolean acceptsURL(String u) throws SQLException {
            return d.acceptsURL(u);
        }

        @Override
        public Connection connect(String u, java.util.Properties p) throws SQLException {
            return d.connect(u, p);
        }

        @Override
        public int getMajorVersion() {
            return d.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return d.getMinorVersion();
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String u, java.util.Properties p)
                throws SQLException {
            return d.getPropertyInfo(u, p);
        }

        @Override
        public boolean jdbcCompliant() {
            return d.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    private Connection conn() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = connect();
            return conn;
        }
        try {
            if (!conn.isValid(3)) {
                closeQuietly(conn);
                conn = connect();
            }
        } catch (Throwable t) {
            closeQuietly(conn);
            conn = connect();
        }
        return conn;
    }

    private static final class Loaded {
        String name = "";
        final List<Object[]> rows = new ArrayList<Object[]>();
    }

    @Override
    public void load(VaultData v) {
        final String uuid = v.owner().toString();
        Loaded data;
        try {
            data = call(new Callable<Loaded>() {
                @Override
                public Loaded call() throws Exception {
                    return query(uuid);
                }
            }, 8000L);
        } catch (Throwable t) {
            plugin.getLogger().warning("读取仓库失败 " + uuid + " -> " + t);
            return;
        }
        if (data == null) {
            return;
        }
        if (v.ownerName().isEmpty() && data.name != null) {
            v.ownerName(data.name);
        }
        for (Object[] r : data.rows) {
            ItemStackHolder h = new ItemStackHolder(r);
            if (h.item != null && h.amount > 0) {
                v.addEntry(h.item, h.amount);
            }
        }
    }

    private static final class ItemStackHolder {
        final org.bukkit.inventory.ItemStack item;
        final long amount;

        ItemStackHolder(Object[] row) {
            this.item = ItemCodec.decode((String) row[1]);
            this.amount = ((Number) row[2]).longValue();
        }
    }

    private Loaded query(String uuid) throws SQLException {
        Loaded out = new Loaded();
        Connection c = conn();

        PreparedStatement ps = c.prepareStatement(
                "SELECT name FROM " + usersTable + " WHERE uuid = ?");
        try {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            try {
                if (rs.next()) {
                    String n = rs.getString(1);
                    out.name = n == null ? "" : n;
                }
            } finally {
                closeQuietly(rs);
            }
        } finally {
            closeQuietly(ps);
        }

        PreparedStatement q = c.prepareStatement(
                "SELECT idx, item, amount FROM " + itemsTable + " WHERE uuid = ? ORDER BY idx");
        try {
            q.setString(1, uuid);
            ResultSet rs = q.executeQuery();
            try {
                while (rs.next()) {
                    out.rows.add(new Object[]{
                            Integer.valueOf(rs.getInt(1)),
                            rs.getString(2),
                            Long.valueOf(rs.getLong(3))
                    });
                }
            } finally {
                closeQuietly(rs);
            }
        } finally {
            closeQuietly(q);
        }
        return out;
    }

    @Override
    public void save(final VaultData v) {
        if (v == null) {
            return;
        }

        final String uuid = v.owner().toString();
        final String name = v.ownerName();
        final int kinds = v.size();
        final long total = v.totalCount();
        final List<Object[]> rows = new ArrayList<Object[]>();
        int i = 0;
        for (Entry e : v.entries()) {
            String blob = ItemCodec.encode(e.proto());
            if (blob.isEmpty()) {
                continue;
            }
            rows.add(new Object[]{Integer.valueOf(i++), blob, Long.valueOf(e.count())});
        }
        submitWrite(uuid, name, kinds, total, rows);
    }

    @Override
    public void saveNow(final VaultData v) {
        if (v == null) {
            return;
        }
        final String uuid = v.owner().toString();
        final String name = v.ownerName();
        final int kinds = v.size();
        final long total = v.totalCount();
        final List<Object[]> rows = new ArrayList<Object[]>();
        int i = 0;
        for (Entry e : v.entries()) {
            String blob = ItemCodec.encode(e.proto());
            if (blob.isEmpty()) {
                continue;
            }
            rows.add(new Object[]{Integer.valueOf(i++), blob, Long.valueOf(e.count())});
        }
        try {
            call(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    write(uuid, name, kinds, total, rows);
                    return Boolean.TRUE;
                }
            }, 15000L);
        } catch (Throwable t) {
            plugin.getLogger().warning("保存仓库失败 " + uuid + " -> " + t);
        }
    }

    private void submitWrite(final String uuid, final String name, final int kinds,
                             final long total, final List<Object[]> rows) {
        queue.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    write(uuid, name, kinds, total, rows);
                } catch (Throwable t) {
                    plugin.getLogger().warning("保存仓库失败 " + uuid + " -> " + t);
                }
            }
        });
    }

    private void write(String uuid, String name, int kinds, long total, List<Object[]> rows)
            throws SQLException {
        Connection c = conn();
        boolean auto;
        try {
            auto = c.getAutoCommit();
        } catch (SQLException e) {
            auto = true;
        }
        try {
            c.setAutoCommit(false);

            PreparedStatement del = c.prepareStatement(
                    "DELETE FROM " + itemsTable + " WHERE uuid = ?");
            try {
                del.setString(1, uuid);
                del.executeUpdate();
            } finally {
                closeQuietly(del);
            }

            if (!rows.isEmpty()) {
                PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO " + itemsTable + " (uuid, idx, item, amount) VALUES (?,?,?,?)");
                try {
                    for (Object[] r : rows) {
                        ins.setString(1, uuid);
                        ins.setInt(2, ((Integer) r[0]).intValue());
                        ins.setString(3, (String) r[1]);
                        ins.setLong(4, ((Long) r[2]).longValue());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                } finally {
                    closeQuietly(ins);
                }
            }

            PreparedStatement up = c.prepareStatement(
                    "REPLACE INTO " + usersTable + " (uuid, name, kinds, total, updated) VALUES (?,?,?,?,?)");
            try {
                up.setString(1, uuid);
                up.setString(2, name == null ? "" : name);
                up.setInt(3, kinds);
                up.setLong(4, total);
                up.setLong(5, System.currentTimeMillis());
                up.executeUpdate();
            } finally {
                closeQuietly(up);
            }

            c.commit();
        } catch (SQLException e) {
            try {
                c.rollback();
            } catch (Throwable ignored) {

            }
            throw e;
        } finally {
            try {
                c.setAutoCommit(auto);
            } catch (Throwable ignored) {
                closeQuietly(c);
                conn = null;
            }
        }
    }

    private <T> T call(Callable<T> task, long timeoutMs) throws Exception {
        Future<T> f = queue.submit(task);
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            f.cancel(true);
            throw new SQLException("数据库操作超时（" + timeoutMs + "ms）");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof Exception ? (Exception) cause : new SQLException(String.valueOf(cause));
        }
    }

    @Override
    public void close() {
        try {
            call(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    closeQuietly(conn);
                    conn = null;
                    return Boolean.TRUE;
                }
            }, 10000L);
        } catch (Throwable ignored) {

        }
        queue.shutdown();
        try {
            queue.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Throwable ignored) {

        }
    }

    private static void closeQuietly(Connection c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Throwable ignored) {

        }
    }
}
