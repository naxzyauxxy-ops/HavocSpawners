package dev.havoc.spawners.migrate;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.spawner.BlockKey;
import dev.havoc.spawners.spawner.ItemSig;
import dev.havoc.spawners.spawner.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Reads a SmartSpawner installation and rebuilds it as HavocSpawners data.
 * <p>
 * All three of SmartSpawner's storage modes are supported: {@code spawners_data.yml}, its SQLite
 * database and a shared MySQL/MariaDB database. Positions, entity and item spawner types, stack
 * sizes, stored XP, filters, preferred sort order and the whole virtual inventory come across.
 * Behavioural numbers (delay, activation range, capacity) are deliberately re-derived from
 * HavocSpawners' own config so one file governs balance after the move.
 */
public final class SmartSpawnerImporter {

    private final HavocSpawners plugin;

    public SmartSpawnerImporter(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ YAML

    public ImportReport importYaml(File file) {
        ImportReport report = new ImportReport("spawners_data.yml");
        if (file == null || !file.isFile()) {
            report.warn("File not found: " + (file == null ? "null" : file.getAbsolutePath()));
            return report;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("spawners");
        if (root == null) {
            report.warn("No 'spawners' section in " + file.getName());
            return report;
        }
        for (String id : root.getKeys(false)) {
            report.countRead();
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                report.countFailed(id + ": not a section");
                continue;
            }
            try {
                Legacy legacy = new Legacy();
                legacy.id = id;
                legacy.location = section.getString("location");
                legacy.entityType = section.getString("entityType");
                legacy.itemMaterial = section.getString("itemSpawnerMaterial");
                legacy.settings = section.getString("settings");
                legacy.lastPlayer = section.getString("lastInteractedPlayer");
                legacy.preferredSort = section.getString("preferredSortItem");
                legacy.filtered = section.getString("filteredItems");
                legacy.inventory = LegacyItemCodec.parseAll(section.getStringList("inventory"));
                apply(legacy, report);
            } catch (Exception ex) {
                report.countFailed(id + ": " + ex.getMessage());
            }
        }
        return report;
    }

    // ------------------------------------------------------------------ SQL

    public ImportReport importSqlite(File file, String sourceServer) {
        ImportReport report = new ImportReport("SmartSpawner SQLite");
        if (file == null || !file.isFile()) {
            report.warn("Database not found: " + (file == null ? "null" : file.getAbsolutePath()));
            return report;
        }
        try {
            Driver driver = new org.sqlite.JDBC();
            try (Connection connection = driver.connect("jdbc:sqlite:" + file.getAbsolutePath(), new Properties())) {
                readTable(connection, sourceServer, report);
            }
        } catch (SQLException ex) {
            report.warn("SQLite error: " + ex.getMessage());
        }
        return report;
    }

    public ImportReport importMySql(String host, int port, String database, String user,
                                    String password, String sourceServer) {
        ImportReport report = new ImportReport("SmartSpawner MySQL");
        try {
            Driver driver = new org.mariadb.jdbc.Driver();
            String url = "jdbc:mariadb://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            Properties properties = new Properties();
            properties.setProperty("user", user);
            properties.setProperty("password", password == null ? "" : password);
            try (Connection connection = driver.connect(url, properties)) {
                if (connection == null) {
                    report.warn("Could not connect to " + host + ":" + port);
                    return report;
                }
                readTable(connection, sourceServer, report);
            }
        } catch (SQLException ex) {
            report.warn("MySQL error: " + ex.getMessage());
        }
        return report;
    }

    private void readTable(Connection connection, String sourceServer, ImportReport report) throws SQLException {
        String sql = "SELECT * FROM smart_spawners"
                + (sourceServer == null || sourceServer.isBlank() ? "" : " WHERE server_name = ?");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (sourceServer != null && !sourceServer.isBlank()) {
                statement.setString(1, sourceServer);
            }
            try (ResultSet rs = statement.executeQuery()) {
                Set<String> columns = columnsOf(rs);
                while (rs.next()) {
                    report.countRead();
                    try {
                        Legacy legacy = new Legacy();
                        legacy.id = str(rs, columns, "spawner_id");
                        String world = str(rs, columns, "world_name");
                        legacy.location = world + "," + rs.getInt("loc_x") + ","
                                + rs.getInt("loc_y") + "," + rs.getInt("loc_z");
                        legacy.entityType = str(rs, columns, "entity_type");
                        legacy.itemMaterial = str(rs, columns, "item_spawner_material");
                        legacy.lastPlayer = str(rs, columns, "last_interacted_player");
                        legacy.preferredSort = str(rs, columns, "preferred_sort_item");
                        legacy.filtered = str(rs, columns, "filtered_items");
                        legacy.inventory = LegacyItemCodec.parseBracketed(str(rs, columns, "inventory_data"));
                        legacy.exp = num(rs, columns, "spawner_exp");
                        legacy.stackSize = (int) num(rs, columns, "stack_size");
                        legacy.maxStackSize = (int) num(rs, columns, "max_stack_size");
                        legacy.lastSpawn = num(rs, columns, "last_spawn_time");
                        legacy.stopped = columns.contains("spawner_stop") && rs.getBoolean("spawner_stop");
                        apply(legacy, report);
                    } catch (Exception ex) {
                        report.countFailed("row: " + ex.getMessage());
                    }
                }
            }
        }
    }

    private static Set<String> columnsOf(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Set<String> columns = new HashSet<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            columns.add(meta.getColumnLabel(i).toLowerCase(Locale.ROOT));
        }
        return columns;
    }

    private static String str(ResultSet rs, Set<String> columns, String name) throws SQLException {
        return columns.contains(name) ? rs.getString(name) : null;
    }

    private static long num(ResultSet rs, Set<String> columns, String name) throws SQLException {
        return columns.contains(name) ? rs.getLong(name) : 0L;
    }

    // ------------------------------------------------------------------ mapping

    /** Raw legacy fields, whichever backend they came from. */
    private static final class Legacy {
        String id;
        String location;
        String entityType;
        String itemMaterial;
        String settings;
        String lastPlayer;
        String preferredSort;
        String filtered;
        Map<ItemSig, Long> inventory = Map.of();
        long exp;
        int stackSize = 1;
        int maxStackSize;
        long lastSpawn;
        boolean stopped;
    }

    private void apply(Legacy legacy, ImportReport report) {
        BlockKey position = BlockKey.deserialize(legacy.location);
        if (position == null) {
            report.countFailed(legacy.id + ": unreadable location '" + legacy.location + "'");
            return;
        }
        if (Bukkit.getWorld(position.world()) == null) {
            report.countFailed(legacy.id + ": world '" + position.world() + "' is not loaded");
            return;
        }

        SpawnerData existing = plugin.spawners().at(position);
        if (existing != null) {
            if (plugin.settings().importSkipExisting) {
                report.countSkipped();
                return;
            }
            plugin.spawners().remove(existing);
        }

        // "settings" is SmartSpawner's CSV:
        // exp,active,range,stop,delay,slots,maxExp,minMobs,maxMobs,stack,maxStack,time,atCapacity
        if (legacy.settings != null && !legacy.settings.isBlank()) {
            String[] parts = legacy.settings.split(",");
            legacy.exp = parseLong(parts, 0, legacy.exp);
            legacy.stopped = parseBool(parts, 3, legacy.stopped);
            legacy.stackSize = (int) parseLong(parts, 9, legacy.stackSize);
            legacy.maxStackSize = (int) parseLong(parts, 10, legacy.maxStackSize);
            legacy.lastSpawn = parseLong(parts, 11, legacy.lastSpawn);
        }

        String id = legacy.id == null || legacy.id.isBlank()
                ? Long.toHexString(System.nanoTime() & 0xFFFFFFFFL)
                : legacy.id;
        if (plugin.spawners().byId(id) != null) {
            id = id + "x";
        }
        SpawnerData spawner = new SpawnerData(id, position);

        Material itemMaterial = legacy.itemMaterial == null ? null
                : Material.matchMaterial(legacy.itemMaterial.toUpperCase(Locale.ROOT));
        boolean isItemSpawner = itemMaterial != null
                || (legacy.entityType != null && legacy.entityType.equalsIgnoreCase("ITEM"));
        if (isItemSpawner && itemMaterial != null) {
            spawner.itemMaterial(itemMaterial);
        } else {
            EntityType type = null;
            if (legacy.entityType != null && !legacy.entityType.equalsIgnoreCase("ITEM")) {
                try {
                    type = EntityType.valueOf(legacy.entityType.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    report.warn(id + ": unknown entity type '" + legacy.entityType + "'");
                }
            }
            if (type == null) {
                report.countFailed(id + ": no usable spawner type");
                return;
            }
            spawner.entityType(type);
        }

        spawner.stackSize(Math.max(1, legacy.stackSize));
        spawner.stopped(legacy.stopped);
        spawner.lastSpawnMillis(legacy.lastSpawn > 0 ? legacy.lastSpawn : System.currentTimeMillis());
        spawner.createdAt(System.currentTimeMillis());

        if (legacy.lastPlayer != null && !legacy.lastPlayer.isBlank()) {
            spawner.ownerName(legacy.lastPlayer);
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(legacy.lastPlayer);
            if (cached != null) {
                spawner.owner(cached.getUniqueId());
            }
        }
        if (legacy.preferredSort != null && !legacy.preferredSort.isBlank()) {
            spawner.preferredSort(Material.matchMaterial(legacy.preferredSort.toUpperCase(Locale.ROOT)));
        }
        if (legacy.filtered != null && !legacy.filtered.isBlank()) {
            for (String part : legacy.filtered.split(",")) {
                Material material = Material.matchMaterial(part.trim().toUpperCase(Locale.ROOT));
                if (material != null) {
                    spawner.filtered().add(material);
                }
            }
        }

        spawner.recompute(plugin.settings(), plugin.upgrades());

        long moved = 0L;
        for (Map.Entry<ItemSig, Long> entry : legacy.inventory.entrySet()) {
            spawner.storage().addUnchecked(entry.getKey(), entry.getValue());
            moved += entry.getValue();
        }
        if (spawner.preferredSort() != null) {
            spawner.storage().sortPreferring(spawner.preferredSort());
        }
        spawner.storedExp(legacy.exp);

        plugin.spawners().register(spawner);
        report.countImported(moved);
    }

    private static long parseLong(String[] parts, int index, long fallback) {
        if (index >= parts.length) {
            return fallback;
        }
        try {
            return Long.parseLong(parts[index].trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean parseBool(String[] parts, int index, boolean fallback) {
        if (index >= parts.length) {
            return fallback;
        }
        String value = parts[index].trim();
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        return fallback;
    }

    /** Convenience for the command: resolve the configured SmartSpawner folder. */
    public File folder() {
        File configured = new File(plugin.settings().importFolder);
        if (configured.isAbsolute()) {
            return configured;
        }
        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        File relative = new File(serverRoot, plugin.settings().importFolder);
        if (relative.isDirectory()) {
            return relative;
        }
        File sibling = new File(plugin.getDataFolder().getParentFile(), "SmartSpawner");
        return sibling.isDirectory() ? sibling : configured;
    }

    public List<String> describe(ImportReport report) {
        return List.of(
                "source=" + report.source(),
                "read=" + report.read(),
                "imported=" + report.imported(),
                "skipped=" + report.skipped(),
                "failed=" + report.failed(),
                "items=" + report.itemsMoved());
    }
}
