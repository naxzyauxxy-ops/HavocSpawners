package dev.havoc.spawners.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.config.Settings;
import dev.havoc.spawners.spawner.BlockKey;
import dev.havoc.spawners.spawner.ItemSig;
import dev.havoc.spawners.spawner.SpawnerData;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * SQLite / MySQL persistence with a write-behind queue.
 * <p>
 * Nothing here ever runs on the main thread except {@link #queueSave(SpawnerData)}, which only marks
 * an id. The flush task batches every pending change into a single prepared-statement batch.
 */
public final class SqlStorage {

    private static final String TABLE = "havoc_spawners";
    private static final String NETWORK_TABLE = "havoc_networks";

    private static final String[] COLUMNS = {
            "server_name", "spawner_id", "world", "x", "y", "z",
            "entity_type", "item_material", "owner_uuid", "owner_name",
            "stack_size", "spawner_level", "stored_exp", "active", "stopped",
            "last_spawn", "created_at", "auto_sell", "auto_collect",
            "linked_container", "network_name", "filtered_items", "preferred_sort",
            "produced_items", "produced_exp", "earned_money", "inventory"
    };

    private final HavocSpawners plugin;
    private final Set<String> dirtyIds = ConcurrentHashMap.newKeySet();
    private final Set<String> deletedIds = ConcurrentHashMap.newKeySet();

    private HikariDataSource dataSource;
    private boolean mysql;
    private String upsertSql;

    public SqlStorage(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ setup

    public void connect() throws SQLException {
        Settings settings = plugin.settings();
        this.mysql = settings.storageMode == Settings.StorageMode.MYSQL;

        HikariConfig config = new HikariConfig();
        config.setPoolName("HavocSpawners-Pool");
        if (mysql) {
            // Resolved through the class object so shadow's relocation stays consistent.
            config.setDriverClassName(org.mariadb.jdbc.Driver.class.getName());
            config.setJdbcUrl("jdbc:mariadb://" + settings.mysqlHost + ":" + settings.mysqlPort
                    + "/" + settings.mysqlDatabase + "?" + settings.mysqlProperties);
            config.setUsername(settings.mysqlUser);
            config.setPassword(settings.mysqlPassword);
            config.setMaximumPoolSize(Math.max(1, settings.poolMax));
            config.setMinimumIdle(Math.max(1, settings.poolMinIdle));
            config.setMaxLifetime(settings.poolMaxLifetime);
            config.setIdleTimeout(settings.poolIdleTimeout);
            config.setKeepaliveTime(settings.poolKeepalive);
        } else {
            File file = new File(plugin.getDataFolder(), settings.sqliteFile);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create the data folder for SQLite.");
            }
            config.setDriverClassName(org.sqlite.JDBC.class.getName());
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            // SQLite is a single writer; more than one connection only creates lock contention.
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setMaxLifetime(0);
        }
        config.setConnectionTimeout(Math.max(1000L, settings.poolConnectionTimeout));
        this.dataSource = new HikariDataSource(config);

        createSchema();
        this.upsertSql = buildUpsert();
    }

    private void createSchema() throws SQLException {
        String text = mysql ? "MEDIUMTEXT" : "TEXT";
        String bool = mysql ? "BOOLEAN" : "INTEGER";
        String spawners = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "server_name VARCHAR(64) NOT NULL,"
                + "spawner_id VARCHAR(64) NOT NULL,"
                + "world VARCHAR(128) NOT NULL,"
                + "x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,"
                + "entity_type VARCHAR(64) DEFAULT NULL,"
                + "item_material VARCHAR(64) DEFAULT NULL,"
                + "owner_uuid VARCHAR(36) DEFAULT NULL,"
                + "owner_name VARCHAR(64) DEFAULT NULL,"
                + "stack_size INT NOT NULL DEFAULT 1,"
                + "spawner_level INT NOT NULL DEFAULT 1,"
                + "stored_exp BIGINT NOT NULL DEFAULT 0,"
                + "active " + bool + " NOT NULL DEFAULT 1,"
                + "stopped " + bool + " NOT NULL DEFAULT 0,"
                + "last_spawn BIGINT NOT NULL DEFAULT 0,"
                + "created_at BIGINT NOT NULL DEFAULT 0,"
                + "auto_sell " + bool + " NOT NULL DEFAULT 0,"
                + "auto_collect " + bool + " NOT NULL DEFAULT 0,"
                + "linked_container VARCHAR(200) DEFAULT NULL,"
                + "network_name VARCHAR(64) DEFAULT NULL,"
                + "filtered_items TEXT DEFAULT NULL,"
                + "preferred_sort VARCHAR(64) DEFAULT NULL,"
                + "produced_items BIGINT NOT NULL DEFAULT 0,"
                + "produced_exp BIGINT NOT NULL DEFAULT 0,"
                + "earned_money DOUBLE PRECISION NOT NULL DEFAULT 0,"
                + "inventory " + text + " DEFAULT NULL,"
                + "PRIMARY KEY (server_name, spawner_id)"
                + ")";
        String networks = "CREATE TABLE IF NOT EXISTS " + NETWORK_TABLE + " ("
                + "server_name VARCHAR(64) NOT NULL,"
                + "owner_uuid VARCHAR(36) NOT NULL,"
                + "network_name VARCHAR(64) NOT NULL,"
                + "created_at BIGINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (server_name, owner_uuid, network_name)"
                + ")";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(spawners);
            statement.executeUpdate(networks);
        }
        // Indexes are an optimisation; older MySQL rejects "IF NOT EXISTS" here, which is harmless.
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_havoc_world ON " + TABLE
                    + " (server_name, world)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_havoc_owner ON " + TABLE
                    + " (server_name, owner_uuid)");
        } catch (SQLException ignored) {
            // already present, or unsupported syntax on this server
        }
    }

    private String buildUpsert() {
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        StringBuilder updates = new StringBuilder();
        for (int i = 0; i < COLUMNS.length; i++) {
            String column = COLUMNS[i];
            if (i > 0) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append(column);
            values.append('?');
            if (i >= 2) {
                if (!updates.isEmpty()) {
                    updates.append(", ");
                }
                updates.append(column).append('=')
                        .append(mysql ? "VALUES(" + column + ")" : "excluded." + column);
            }
        }
        String base = "INSERT INTO " + TABLE + " (" + columns + ") VALUES (" + values + ") ";
        return base + (mysql
                ? "ON DUPLICATE KEY UPDATE " + updates
                : "ON CONFLICT(server_name, spawner_id) DO UPDATE SET " + updates);
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    // ------------------------------------------------------------------ queue

    public void queueSave(SpawnerData spawner) {
        spawner.markDirty();
        dirtyIds.add(spawner.id());
    }

    public void queueDelete(String id) {
        dirtyIds.remove(id);
        deletedIds.add(id);
    }

    public int pending() {
        return dirtyIds.size() + deletedIds.size();
    }

    /** Runs on the async scheduler. */
    public void flush(boolean drainEverything) {
        if (!isConnected()) {
            return;
        }
        int limit = drainEverything ? Integer.MAX_VALUE : plugin.settings().flushBatchSize;

        List<String> deletions = new ArrayList<>();
        for (String id : new HashSet<>(deletedIds)) {
            if (deletions.size() >= limit) {
                break;
            }
            deletions.add(id);
            deletedIds.remove(id);
        }

        List<SpawnerData> saves = new ArrayList<>();
        for (String id : new HashSet<>(dirtyIds)) {
            if (saves.size() >= limit) {
                break;
            }
            SpawnerData spawner = plugin.spawners().byId(id);
            dirtyIds.remove(id);
            if (spawner != null) {
                saves.add(spawner);
            }
        }

        if (deletions.isEmpty() && saves.isEmpty()) {
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (!deletions.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM " + TABLE + " WHERE server_name=? AND spawner_id=?")) {
                        for (String id : deletions) {
                            statement.setString(1, plugin.settings().serverName);
                            statement.setString(2, id);
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }
                if (!saves.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement(upsertSql)) {
                        for (SpawnerData spawner : saves) {
                            bind(statement, spawner);
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }
                connection.commit();
                for (SpawnerData spawner : saves) {
                    spawner.clearDirty();
                }
            } catch (SQLException ex) {
                connection.rollback();
                // Nothing was written; put the work back so the next flush retries it.
                for (SpawnerData spawner : saves) {
                    dirtyIds.add(spawner.id());
                }
                deletedIds.addAll(deletions);
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to flush spawner data", ex);
        }
    }

    private void bind(PreparedStatement statement, SpawnerData spawner) throws SQLException {
        BlockKey position = spawner.position();
        int i = 1;
        statement.setString(i++, plugin.settings().serverName);
        statement.setString(i++, spawner.id());
        statement.setString(i++, position.world());
        statement.setInt(i++, position.x());
        statement.setInt(i++, position.y());
        statement.setInt(i++, position.z());
        statement.setString(i++, spawner.entityType() == null ? null : spawner.entityType().name());
        statement.setString(i++, spawner.itemMaterial() == null ? null : spawner.itemMaterial().name());
        statement.setString(i++, spawner.owner() == null ? null : spawner.owner().toString());
        statement.setString(i++, spawner.ownerName());
        statement.setInt(i++, spawner.stackSize());
        statement.setInt(i++, spawner.level());
        statement.setLong(i++, spawner.storedExp());
        statement.setInt(i++, spawner.active() ? 1 : 0);
        statement.setInt(i++, spawner.stopped() ? 1 : 0);
        statement.setLong(i++, spawner.lastSpawnMillis());
        statement.setLong(i++, spawner.createdAt());
        statement.setInt(i++, spawner.autoSell() ? 1 : 0);
        statement.setInt(i++, spawner.autoCollect() ? 1 : 0);
        statement.setString(i++, spawner.linkedContainer() == null ? null : spawner.linkedContainer().serialize());
        statement.setString(i++, spawner.network());
        statement.setString(i++, InventoryCodec.encodeFilters(spawner.filtered()));
        statement.setString(i++, spawner.preferredSort() == null ? null : spawner.preferredSort().name());
        statement.setLong(i++, spawner.producedItems());
        statement.setLong(i++, spawner.producedExp());
        statement.setDouble(i++, spawner.earnedMoney());
        statement.setString(i, InventoryCodec.encode(spawner.storage().snapshot()));
    }

    // ------------------------------------------------------------------ load

    public List<SpawnerData> loadAll() {
        List<SpawnerData> out = new ArrayList<>();
        if (!isConnected()) {
            return out;
        }
        String sql = "SELECT * FROM " + TABLE + " WHERE server_name=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, plugin.settings().serverName);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    SpawnerData spawner = read(rs);
                    if (spawner != null) {
                        out.add(spawner);
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load spawners", ex);
        }
        return out;
    }

    private SpawnerData read(ResultSet rs) throws SQLException {
        String id = rs.getString("spawner_id");
        BlockKey position = new BlockKey(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
        SpawnerData spawner = new SpawnerData(id, position);

        String itemMaterial = rs.getString("item_material");
        if (itemMaterial != null && !itemMaterial.isBlank()) {
            Material material = Material.matchMaterial(itemMaterial.toUpperCase(Locale.ROOT));
            if (material != null) {
                spawner.itemMaterial(material);
            }
        } else {
            String entity = rs.getString("entity_type");
            if (entity != null && !entity.isBlank()) {
                try {
                    spawner.entityType(EntityType.valueOf(entity.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Spawner " + id + " has unknown entity type " + entity);
                }
            }
        }

        String ownerUuid = rs.getString("owner_uuid");
        if (ownerUuid != null && !ownerUuid.isBlank()) {
            try {
                spawner.owner(UUID.fromString(ownerUuid));
            } catch (IllegalArgumentException ignored) {
                // legacy rows may carry a name here
            }
        }
        spawner.ownerName(rs.getString("owner_name"));
        spawner.stackSize(rs.getInt("stack_size"));
        spawner.level(rs.getInt("spawner_level"));
        spawner.active(rs.getInt("active") != 0);
        spawner.stopped(rs.getInt("stopped") != 0);
        spawner.lastSpawnMillis(rs.getLong("last_spawn"));
        long created = rs.getLong("created_at");
        spawner.createdAt(created > 0 ? created : System.currentTimeMillis());
        spawner.autoSell(rs.getInt("auto_sell") != 0);
        spawner.autoCollect(rs.getInt("auto_collect") != 0);
        spawner.linkedContainer(BlockKey.deserialize(rs.getString("linked_container")));
        spawner.network(rs.getString("network_name"));
        InventoryCodec.decodeFilters(rs.getString("filtered_items"), spawner.filtered());
        String sort = rs.getString("preferred_sort");
        if (sort != null && !sort.isBlank()) {
            spawner.preferredSort(Material.matchMaterial(sort.toUpperCase(Locale.ROOT)));
        }
        spawner.producedItems(rs.getLong("produced_items"));
        spawner.producedExp(rs.getLong("produced_exp"));
        spawner.earnedMoney(rs.getDouble("earned_money"));

        spawner.recompute(plugin.settings(), plugin.upgrades());
        for (Map.Entry<ItemSig, Long> entry : InventoryCodec.decode(rs.getString("inventory")).entrySet()) {
            spawner.storage().addUnchecked(entry.getKey(), entry.getValue());
        }
        // storedExp is clamped by maxStoredExp, so it must be applied after recompute().
        spawner.storedExp(rs.getLong("stored_exp"));
        spawner.clearDirty();
        return spawner;
    }

    // ------------------------------------------------------------------ networks

    public void saveNetwork(UUID owner, String name) {
        if (!isConnected()) {
            return;
        }
        String sql = mysql
                ? "INSERT INTO " + NETWORK_TABLE + " (server_name, owner_uuid, network_name, created_at) "
                + "VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE created_at=VALUES(created_at)"
                : "INSERT INTO " + NETWORK_TABLE + " (server_name, owner_uuid, network_name, created_at) "
                + "VALUES (?,?,?,?) ON CONFLICT(server_name, owner_uuid, network_name) DO NOTHING";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, plugin.settings().serverName);
            statement.setString(2, owner.toString());
            statement.setString(3, name);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save network " + name, ex);
        }
    }

    public void deleteNetwork(UUID owner, String name) {
        if (!isConnected()) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + NETWORK_TABLE + " WHERE server_name=? AND owner_uuid=? AND network_name=?")) {
            statement.setString(1, plugin.settings().serverName);
            statement.setString(2, owner.toString());
            statement.setString(3, name);
            statement.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete network " + name, ex);
        }
    }

    public Collection<String[]> loadNetworks() {
        List<String[]> out = new ArrayList<>();
        if (!isConnected()) {
            return out;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT owner_uuid, network_name FROM " + NETWORK_TABLE + " WHERE server_name=?")) {
            statement.setString(1, plugin.settings().serverName);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(new String[]{rs.getString("owner_uuid"), rs.getString("network_name")});
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load networks", ex);
        }
        return out;
    }

    /** Requests an off-thread flush of everything currently queued. */
    public void flushSoon() {
        plugin.sched().async(() -> flush(true));
    }

    public void shutdown() {
        try {
            flush(true);
        } finally {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }
}
