package dev.havoc.spawners.config;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.util.Numbers;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Typed snapshot of config.yml. Rebuilt wholesale on reload so nothing reads a half-updated view. */
public final class Settings {

    public enum StorageMode {SQLITE, MYSQL}

    public enum PriceSource {SHOP_THEN_CUSTOM, CUSTOM_ONLY}

    public String language = "en_US";
    public boolean debug;

    // theme - drives every dialog and chat colour, reloadable without a rebuild
    public String themeAccent = "#ff2b3d";
    public String themeAccentDim = "#8f0f1c";
    public String themeGood = "#ffffff";
    public String themeWarn = "#ff8a95";
    public String themeBad = "#ff2b3d";
    public String themeInk = "#e8e8ea";
    public String themeFaint = "#9b9ba1";

    // bedrock (Geyser/Floodgate)
    public boolean bedrockEnabled = true;
    public boolean bedrockForceForms;
    public String bedrockAccent = "c";
    public String bedrockGood = "f";
    public String bedrockWarn = "6";
    public String bedrockBad = "4";
    public String bedrockInk = "f";
    public String bedrockFaint = "7";

    // storage
    public StorageMode storageMode = StorageMode.SQLITE;
    public String serverName = "server1";
    public String sqliteFile = "spawners.db";
    public String mysqlHost = "localhost";
    public int mysqlPort = 3306;
    public String mysqlDatabase = "havocspawners";
    public String mysqlUser = "root";
    public String mysqlPassword = "";
    public String mysqlProperties = "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    public int poolMax = 10;
    public int poolMinIdle = 2;
    public long poolConnectionTimeout = 10_000L;
    public long poolMaxLifetime = 1_800_000L;
    public long poolIdleTimeout = 600_000L;
    public long poolKeepalive = 30_000L;
    public int flushIntervalSeconds = 10;
    public int flushBatchSize = 500;

    // spawner defaults
    public int minMobs = 1;
    public int maxMobs = 4;
    public int activationRange = 16;
    public long delayTicks = 500L;
    public int pagesPerStack = 1;
    public long maxStoredExp = 1000L;
    public int maxStackSize = 1000;
    public boolean allowExpMending = true;
    public boolean protectFromExplosions = true;
    public boolean requirePlayerNearby = true;
    public long maxCatchupTicks;
    public boolean stopWhenFull = true;

    // breaking
    public boolean breakEnabled = true;
    public boolean directToInventory = true;
    public boolean dropStorageOnBreak = true;
    public Set<Material> requiredTools = EnumSet.noneOf(Material.class);
    public int durabilityLoss = 1;
    public boolean silkRequired;
    public int silkLevel = 1;

    // natural spawners
    public boolean naturalBreakable;
    public boolean naturalConvert = true;
    public boolean naturalSpawnMobs = true;
    public boolean naturalProtect;

    // bulk drop
    public int stacksPerTick = 24;
    public int maxItemEntities = 600;
    public boolean preferPlayerInventory = true;
    public boolean throwFromLook = true;
    public double throwStrength = 0.3D;
    public int pickupDelayTicks = 20;
    public boolean progressActionbar = true;
    public int maxPagesPerRequest = 4096;

    // economy
    public boolean economyEnabled = true;
    public PriceSource priceSource = PriceSource.SHOP_THEN_CUSTOM;
    public double defaultPrice;
    public double taxPercent;

    // automation
    public boolean automationEnabled = true;
    public int automationIntervalSeconds = 30;
    public boolean autoSellEnabled = true;
    public double autoSellMinValue = 1.0D;
    public boolean autoCollectEnabled = true;
    public int autoCollectRadius = 8;
    public int autoCollectStacks = 32;

    // upgrades
    public boolean upgradesEnabled = true;
    public double upgradeRefundPercent;

    // networks
    public boolean networksEnabled = true;
    public int maxNetworksPerPlayer = 5;
    public int maxSpawnersPerNetwork = 250;

    // analytics
    public boolean analyticsEnabled = true;
    public int analyticsHistoryHours = 24;
    public int leaderboardSize = 10;

    // cosmetics
    public boolean hologramEnabled;
    public double hologramOffsetY = 1.6D;
    public int hologramRefreshSeconds = 5;
    public boolean particleStack = true;
    public boolean particleActivate = true;
    public boolean particleLoot = true;

    // import
    public String importFolder = "plugins/SmartSpawner";
    public String importSourceServer = "server1";
    public boolean importSkipExisting = true;
    public String importHost = "localhost";
    public int importPort = 3306;
    public String importDatabase = "smartspawner";
    public String importUser = "root";
    public String importPassword = "";

    public static Settings load(HavocSpawners plugin) {
        FileConfiguration c = plugin.getConfig();
        Settings s = new Settings();

        s.language = c.getString("language", "en_US");
        s.debug = c.getBoolean("debug", false);

        s.themeAccent = colour(c.getString("theme.accent"), s.themeAccent);
        s.themeAccentDim = colour(c.getString("theme.accent-dim"), s.themeAccentDim);
        s.themeGood = colour(c.getString("theme.good"), s.themeGood);
        s.themeWarn = colour(c.getString("theme.warn"), s.themeWarn);
        s.themeBad = colour(c.getString("theme.bad"), s.themeBad);
        s.themeInk = colour(c.getString("theme.ink"), s.themeInk);
        s.themeFaint = colour(c.getString("theme.faint"), s.themeFaint);

        s.bedrockEnabled = c.getBoolean("bedrock.enabled", true);
        s.bedrockForceForms = c.getBoolean("bedrock.force-forms-for-java", false);
        s.bedrockAccent = legacyCode(c.getString("bedrock.colors.accent"), s.bedrockAccent);
        s.bedrockGood = legacyCode(c.getString("bedrock.colors.good"), s.bedrockGood);
        s.bedrockWarn = legacyCode(c.getString("bedrock.colors.warn"), s.bedrockWarn);
        s.bedrockBad = legacyCode(c.getString("bedrock.colors.bad"), s.bedrockBad);
        s.bedrockInk = legacyCode(c.getString("bedrock.colors.ink"), s.bedrockInk);
        s.bedrockFaint = legacyCode(c.getString("bedrock.colors.faint"), s.bedrockFaint);

        s.storageMode = enumOf(StorageMode.class, c.getString("storage.mode", "SQLITE"), StorageMode.SQLITE);
        s.serverName = c.getString("storage.server-name", "server1");
        s.sqliteFile = c.getString("storage.sqlite.file", "spawners.db");
        s.mysqlHost = c.getString("storage.mysql.host", "localhost");
        s.mysqlPort = c.getInt("storage.mysql.port", 3306);
        s.mysqlDatabase = c.getString("storage.mysql.database", "havocspawners");
        s.mysqlUser = c.getString("storage.mysql.username", "root");
        s.mysqlPassword = c.getString("storage.mysql.password", "");
        s.mysqlProperties = c.getString("storage.mysql.properties", s.mysqlProperties);
        s.poolMax = c.getInt("storage.pool.maximum-size", 10);
        s.poolMinIdle = c.getInt("storage.pool.minimum-idle", 2);
        s.poolConnectionTimeout = c.getLong("storage.pool.connection-timeout", 10_000L);
        s.poolMaxLifetime = c.getLong("storage.pool.max-lifetime", 1_800_000L);
        s.poolIdleTimeout = c.getLong("storage.pool.idle-timeout", 600_000L);
        s.poolKeepalive = c.getLong("storage.pool.keepalive-time", 30_000L);
        s.flushIntervalSeconds = Math.max(1, c.getInt("storage.flush-interval-seconds", 10));
        s.flushBatchSize = Math.max(1, c.getInt("storage.flush-batch-size", 500));

        s.minMobs = Math.max(0, c.getInt("spawner.defaults.min-mobs", 1));
        s.maxMobs = Math.max(s.minMobs, c.getInt("spawner.defaults.max-mobs", 4));
        s.activationRange = Math.max(1, c.getInt("spawner.defaults.activation-range", 16));
        s.delayTicks = Math.max(1L, Numbers.parseTicks(c.getString("spawner.defaults.delay", "25s"), 500L));
        s.pagesPerStack = Math.max(1, c.getInt("spawner.defaults.pages-per-stack", 1));
        s.maxStoredExp = Math.max(0L, c.getLong("spawner.defaults.max-stored-exp", 1000L));
        s.maxStackSize = Math.max(1, c.getInt("spawner.defaults.max-stack-size", 1000));
        s.allowExpMending = c.getBoolean("spawner.allow-exp-mending", true);
        s.protectFromExplosions = c.getBoolean("spawner.protect-from-explosions", true);
        s.requirePlayerNearby = c.getBoolean("spawner.require-player-nearby", true);
        s.maxCatchupTicks = Numbers.parseTicks(c.getString("spawner.max-catchup", "0s"), 0L);
        s.stopWhenFull = c.getBoolean("spawner.stop-when-full", true);

        s.breakEnabled = c.getBoolean("breaking.enabled", true);
        s.directToInventory = c.getBoolean("breaking.direct-to-inventory", true);
        s.dropStorageOnBreak = c.getBoolean("breaking.drop-storage-on-break", true);
        s.requiredTools = EnumSet.noneOf(Material.class);
        for (String raw : c.getStringList("breaking.required-tools")) {
            Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
            if (material != null) {
                s.requiredTools.add(material);
            }
        }
        s.durabilityLoss = Math.max(0, c.getInt("breaking.durability-loss", 1));
        s.silkRequired = c.getBoolean("breaking.silk-touch.required", false);
        s.silkLevel = Math.max(1, c.getInt("breaking.silk-touch.level", 1));

        s.naturalBreakable = c.getBoolean("natural-spawners.breakable", false);
        s.naturalConvert = c.getBoolean("natural-spawners.convert-on-break", true);
        s.naturalSpawnMobs = c.getBoolean("natural-spawners.spawn-mobs", true);
        s.naturalProtect = c.getBoolean("natural-spawners.protect-from-explosions", false);

        s.stacksPerTick = Numbers.clamp(c.getInt("bulk-drop.stacks-per-tick", 24), 1, 512);
        s.maxItemEntities = Numbers.clamp(c.getInt("bulk-drop.max-item-entities", 600), 1, 20_000);
        s.preferPlayerInventory = c.getBoolean("bulk-drop.prefer-player-inventory", false);
        s.throwFromLook = c.getBoolean("bulk-drop.throw-from-look", true);
        s.throwStrength = Math.max(0.0D, Math.min(2.0D, c.getDouble("bulk-drop.throw-strength", 0.3D)));
        s.pickupDelayTicks = Numbers.clamp(c.getInt("bulk-drop.pickup-delay-ticks", 20), 0, 32767);
        s.progressActionbar = c.getBoolean("bulk-drop.progress-actionbar", true);
        s.maxPagesPerRequest = Numbers.clamp(c.getInt("bulk-drop.max-pages-per-request", 4096), 1, 1_000_000);

        s.economyEnabled = c.getBoolean("economy.enabled", true);
        s.priceSource = enumOf(PriceSource.class, c.getString("economy.price-source", "SHOP_THEN_CUSTOM"),
                PriceSource.SHOP_THEN_CUSTOM);
        s.defaultPrice = c.getDouble("economy.default-price", 0.0D);
        s.taxPercent = Math.max(0.0D, Math.min(100.0D, c.getDouble("economy.tax-percent", 0.0D)));

        s.automationEnabled = c.getBoolean("automation.enabled", true);
        s.automationIntervalSeconds = Math.max(5, c.getInt("automation.interval-seconds", 30));
        s.autoSellEnabled = c.getBoolean("automation.auto-sell.enabled", true);
        s.autoSellMinValue = c.getDouble("automation.auto-sell.min-value", 1.0D);
        s.autoCollectEnabled = c.getBoolean("automation.auto-collect.enabled", true);
        s.autoCollectRadius = Numbers.clamp(c.getInt("automation.auto-collect.link-radius", 8), 1, 64);
        s.autoCollectStacks = Numbers.clamp(c.getInt("automation.auto-collect.stacks-per-run", 32), 1, 512);

        s.upgradesEnabled = c.getBoolean("upgrades.enabled", true);
        s.upgradeRefundPercent = c.getDouble("upgrades.refund-percent", 0.0D);

        s.networksEnabled = c.getBoolean("networks.enabled", true);
        s.maxNetworksPerPlayer = Math.max(1, c.getInt("networks.max-per-player", 5));
        s.maxSpawnersPerNetwork = Math.max(1, c.getInt("networks.max-spawners-per-network", 250));

        s.analyticsEnabled = c.getBoolean("analytics.enabled", true);
        s.analyticsHistoryHours = Numbers.clamp(c.getInt("analytics.history-hours", 24), 1, 168);
        s.leaderboardSize = Numbers.clamp(c.getInt("analytics.leaderboard-size", 10), 1, 50);

        s.hologramEnabled = c.getBoolean("hologram.enabled", false);
        s.hologramOffsetY = c.getDouble("hologram.offset-y", 1.6D);
        s.hologramRefreshSeconds = Math.max(1, c.getInt("hologram.refresh-seconds", 5));
        s.particleStack = c.getBoolean("particles.stack", true);
        s.particleActivate = c.getBoolean("particles.activate", true);
        s.particleLoot = c.getBoolean("particles.loot", true);

        s.importFolder = c.getString("import.smartspawner-folder", "plugins/SmartSpawner");
        s.importSourceServer = c.getString("import.source-server-name", "server1");
        s.importSkipExisting = c.getBoolean("import.skip-existing", true);
        s.importHost = c.getString("import.mysql.host", "localhost");
        s.importPort = c.getInt("import.mysql.port", 3306);
        s.importDatabase = c.getString("import.mysql.database", "smartspawner");
        s.importUser = c.getString("import.mysql.username", "root");
        s.importPassword = c.getString("import.mysql.password", "");

        if (s.requiredTools.isEmpty()) {
            List<Material> fallback = List.of(Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE,
                    Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE);
            s.requiredTools.addAll(fallback);
        }
        return s;
    }

    /** Accepts "#rrggbb" (with or without the hash); anything else keeps the built-in colour. */
    private static String colour(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim();
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        return value.matches("#[0-9a-fA-F]{6}") ? value.toLowerCase(Locale.ROOT) : fallback;
    }

    /** A single legacy colour character (0-9, a-f); anything else keeps the default. */
    private static String legacyCode(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim().replace("&", "").replace("§", "").toLowerCase(Locale.ROOT);
        return value.length() == 1 && "0123456789abcdef".indexOf(value.charAt(0)) >= 0 ? value : fallback;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String raw, E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
