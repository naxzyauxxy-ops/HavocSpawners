package dev.havoc.spawners;

import dev.havoc.spawners.bedrock.BedrockBridge;
import dev.havoc.spawners.command.HavocCommand;
import dev.havoc.spawners.config.Messages;
import dev.havoc.spawners.config.Settings;
import dev.havoc.spawners.econ.EconomyHook;
import dev.havoc.spawners.econ.PriceBook;
import dev.havoc.spawners.econ.SellService;
import dev.havoc.spawners.feature.Analytics;
import dev.havoc.spawners.feature.AutomationService;
import dev.havoc.spawners.feature.NetworkService;
import dev.havoc.spawners.feature.UpgradeTree;
import dev.havoc.spawners.listener.BlockListener;
import dev.havoc.spawners.listener.InteractListener;
import dev.havoc.spawners.listener.PlayerListener;
import dev.havoc.spawners.loot.LootEngine;
import dev.havoc.spawners.loot.LootRegistry;
import dev.havoc.spawners.migrate.SmartSpawnerImporter;
import dev.havoc.spawners.spawner.SpawnerData;
import dev.havoc.spawners.spawner.SpawnerItems;
import dev.havoc.spawners.spawner.SpawnerManager;
import dev.havoc.spawners.storage.SqlStorage;
import dev.havoc.spawners.task.DropService;
import dev.havoc.spawners.ui.AdminUi;
import dev.havoc.spawners.ui.BedrockUi;
import dev.havoc.spawners.ui.SpawnerUi;
import dev.havoc.spawners.util.Sched;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * HavocSpawners - virtual, dialog-driven spawners for Paper 1.21.6+.
 * <p>
 * Requires the Paper Dialog API, which shipped in 1.21.6. The plugin refuses to enable on anything
 * older rather than half-working.
 */
public final class HavocSpawners extends JavaPlugin {

    private Sched sched;
    private Settings settings;
    private Messages messages;

    private SqlStorage storage;
    private SpawnerManager spawners;
    private SpawnerItems items;

    private LootRegistry lootRegistry;
    private LootEngine lootEngine;
    private UpgradeTree upgrades;

    private PriceBook prices;
    private EconomyHook economy;
    private SellService sell;

    private Analytics analytics;
    private NetworkService networks;
    private AutomationService automation;
    private DropService dropService;

    private SpawnerUi spawnerUi;
    private AdminUi adminUi;
    private BedrockBridge bedrock;
    private BedrockUi bedrockUi;
    private SmartSpawnerImporter importer;

    private final Map<UUID, String> pendingLinks = new ConcurrentHashMap<>();
    private ScheduledTask flushTask;

    @Override
    public void onEnable() {
        if (!hasDialogApi()) {
            getLogger().severe("HavocSpawners needs Paper 1.21.6 or newer (the Dialog API is missing).");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        saveResource("lang/en_US.yml", false);

        this.sched = new Sched(this);
        this.settings = Settings.load(this);
        this.messages = new Messages(this);
        this.messages.reload(settings.language);
        dev.havoc.spawners.ui.Ui.applyTheme(settings);

        this.upgrades = new UpgradeTree();
        this.upgrades.reload(this);
        this.lootRegistry = new LootRegistry();
        this.lootRegistry.reload(this);
        this.lootEngine = new LootEngine(lootRegistry);

        this.prices = new PriceBook();
        this.prices.reload(this);
        this.economy = new EconomyHook();
        this.economy.reload(this);
        this.sell = new SellService(this);

        this.items = new SpawnerItems(this);
        this.analytics = new Analytics(this);
        this.spawners = new SpawnerManager(this);
        this.storage = new SqlStorage(this);
        this.networks = new NetworkService(this);
        this.automation = new AutomationService(this);
        this.dropService = new DropService(this);
        this.spawnerUi = new SpawnerUi(this);
        this.adminUi = new AdminUi(this);
        this.bedrock = new BedrockBridge(this);
        this.bedrockUi = new BedrockUi(this);
        this.importer = new SmartSpawnerImporter(this);

        try {
            storage.connect();
        } catch (SQLException ex) {
            getLogger().log(Level.SEVERE, "Could not open the spawner database - disabling.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        spawners.loadAll(storage.loadAll());
        networks.load();

        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        PluginCommand command = getCommand("havocspawners");
        if (command != null) {
            HavocCommand executor = new HavocCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            spawners.tracker().update(player);
        }

        bedrock.reload();

        spawners.startTicking();
        automation.start();
        startFlushTask();

        getLogger().info("HavocSpawners enabled - " + spawners.size() + " spawners, storage="
                + settings.storageMode + ".");
    }

    @Override
    public void onDisable() {
        if (dropService != null) {
            dropService.cancelAll();
        }
        if (spawners != null) {
            spawners.stopTicking();
        }
        if (automation != null) {
            automation.stop();
        }
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (storage != null) {
            // Mark everything dirty so a clean shutdown always lands a full snapshot.
            for (SpawnerData spawner : spawners == null ? java.util.List.<SpawnerData>of() : spawners.all()) {
                storage.queueSave(spawner);
            }
            storage.shutdown();
        }
        getLogger().info("HavocSpawners disabled.");
    }

    private boolean hasDialogApi() {
        try {
            Class.forName("io.papermc.paper.registry.data.dialog.DialogBase");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private void startFlushTask() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        long seconds = Math.max(1, settings.flushIntervalSeconds);
        flushTask = sched.asyncTimer(() -> storage.flush(false), seconds, seconds, TimeUnit.SECONDS);
    }

    /** Full configuration reload; safe to call at runtime. */
    public void reloadEverything() {
        reloadConfig();
        this.settings = Settings.load(this);
        this.messages.reload(settings.language);
        dev.havoc.spawners.ui.Ui.applyTheme(settings);
        this.upgrades.reload(this);
        this.lootRegistry.reload(this);
        this.lootEngine = new LootEngine(lootRegistry);
        this.prices.reload(this);
        this.economy.reload(this);
        this.bedrock.reload();
        this.spawners.recomputeAll();
        this.spawners.startTicking();
        this.automation.start();
        startFlushTask();
    }

    // ------------------------------------------------------------------ linking

    public void beginLinking(Player player, SpawnerData spawner) {
        pendingLinks.put(player.getUniqueId(), spawner.id());
    }

    public SpawnerData pendingLink(Player player) {
        String id = pendingLinks.get(player.getUniqueId());
        return id == null ? null : spawners.byId(id);
    }

    public void clearLinking(Player player) {
        pendingLinks.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------ accessors

    public Sched sched() {
        return sched;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public SqlStorage storage() {
        return storage;
    }

    public SpawnerManager spawners() {
        return spawners;
    }

    public SpawnerItems items() {
        return items;
    }

    public LootRegistry loot() {
        return lootRegistry;
    }

    public LootEngine lootEngine() {
        return lootEngine;
    }

    public UpgradeTree upgrades() {
        return upgrades;
    }

    public PriceBook prices() {
        return prices;
    }

    public EconomyHook economy() {
        return economy;
    }

    public SellService sell() {
        return sell;
    }

    public Analytics analytics() {
        return analytics;
    }

    public NetworkService networks() {
        return networks;
    }

    public AutomationService automation() {
        return automation;
    }

    public DropService dropService() {
        return dropService;
    }

    public SpawnerUi spawnerUi() {
        return spawnerUi;
    }

    public AdminUi adminUi() {
        return adminUi;
    }

    public BedrockBridge bedrock() {
        return bedrock;
    }

    public BedrockUi bedrockUi() {
        return bedrockUi;
    }

    public SmartSpawnerImporter importer() {
        return importer;
    }
}
