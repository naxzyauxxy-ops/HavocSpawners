package dev.havoc.spawners.spawner;

import dev.havoc.spawners.config.Settings;
import dev.havoc.spawners.feature.UpgradeTier;
import dev.havoc.spawners.feature.UpgradeTree;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One virtual spawner.
 * <p>
 * Derived numbers (delay, capacity, mob counts) are recomputed from {@link Settings} and the upgrade
 * tier whenever the stack size or level changes, so they can be read without a lookup on hot paths.
 */
public final class SpawnerData {

    private final String id;
    private BlockKey position;

    private EntityType entityType;
    private Material itemMaterial;

    private UUID owner;
    private String ownerName;

    private int stackSize = 1;
    private int level = 1;
    private long storedExp;

    private boolean active = true;
    private boolean stopped;
    private boolean atCapacity;
    private long lastSpawnMillis = System.currentTimeMillis();
    private long createdAt = System.currentTimeMillis();

    private final VirtualStorage storage = new VirtualStorage();
    private final Set<Material> filtered = EnumSet.noneOf(Material.class);
    private Material preferredSort;

    private boolean autoSell;
    private boolean autoCollect;
    private BlockKey linkedContainer;
    private String network;

    private long producedItems;
    private long producedExp;
    private double earnedMoney;

    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final AtomicBoolean busy = new AtomicBoolean(false);

    // derived
    private long spawnDelayTicks = 500L;
    private int minMobs = 1;
    private int maxMobs = 4;
    private int activationRange = 16;
    private long maxStoredExp = 1000L;
    private long maxSlots = 45L;
    private int maxStackSize = 1000;
    private double lootMultiplier = 1.0D;

    public SpawnerData(String id, BlockKey position) {
        this.id = id;
        this.position = position;
    }

    public void recompute(Settings settings, UpgradeTree upgrades) {
        UpgradeTier tier = upgrades == null ? UpgradeTier.base() : upgrades.tier(level);
        this.spawnDelayTicks = Math.max(1L, Math.round(settings.delayTicks * tier.delayMultiplier()));
        this.minMobs = Math.max(0, settings.minMobs * stackSize);
        this.maxMobs = Math.max(this.minMobs, settings.maxMobs * stackSize);
        this.activationRange = Math.max(1, settings.activationRange + tier.bonusRange());
        this.maxStoredExp = Math.max(0L, (long) settings.maxStoredExp * stackSize + tier.bonusExpCapacity());
        long pages = (long) settings.pagesPerStack * stackSize + tier.bonusPages();
        this.maxSlots = Math.max(VirtualStorage.SLOTS_PER_PAGE, pages * VirtualStorage.SLOTS_PER_PAGE);
        this.maxStackSize = settings.maxStackSize;
        this.lootMultiplier = tier.lootMultiplier();
    }

    // ---------------------------------------------------------------- identity

    public String id() {
        return id;
    }

    public BlockKey position() {
        return position;
    }

    public void position(BlockKey position) {
        this.position = position;
        markDirty();
    }

    public boolean isItemSpawner() {
        return itemMaterial != null;
    }

    public EntityType entityType() {
        return entityType;
    }

    public void entityType(EntityType entityType) {
        this.entityType = entityType;
        this.itemMaterial = null;
        markDirty();
    }

    public Material itemMaterial() {
        return itemMaterial;
    }

    public void itemMaterial(Material material) {
        this.itemMaterial = material;
        markDirty();
    }

    /** Human readable spawner type, e.g. "Iron Golem" or "Bone Block". */
    public String typeKey() {
        if (itemMaterial != null) {
            return itemMaterial.name();
        }
        return entityType == null ? "UNKNOWN" : entityType.name();
    }

    public String displayType() {
        String key = typeKey();
        String[] words = key.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    // ---------------------------------------------------------------- state

    public UUID owner() {
        return owner;
    }

    public void owner(UUID owner) {
        this.owner = owner;
        markDirty();
    }

    public String ownerName() {
        return ownerName;
    }

    public void ownerName(String ownerName) {
        this.ownerName = ownerName;
        markDirty();
    }

    public int stackSize() {
        return stackSize;
    }

    public void stackSize(int stackSize) {
        this.stackSize = Math.max(1, stackSize);
        markDirty();
    }

    public int level() {
        return level;
    }

    public void level(int level) {
        this.level = Math.max(1, level);
        markDirty();
    }

    public long storedExp() {
        return storedExp;
    }

    public void storedExp(long storedExp) {
        this.storedExp = Math.max(0L, Math.min(storedExp, maxStoredExp));
        markDirty();
    }

    public void addExp(long amount) {
        storedExp(this.storedExp + amount);
    }

    public boolean active() {
        return active;
    }

    public void active(boolean active) {
        this.active = active;
        markDirty();
    }

    public boolean stopped() {
        return stopped;
    }

    public void stopped(boolean stopped) {
        this.stopped = stopped;
        markDirty();
    }

    public boolean atCapacity() {
        return atCapacity;
    }

    public void atCapacity(boolean atCapacity) {
        this.atCapacity = atCapacity;
    }

    public long lastSpawnMillis() {
        return lastSpawnMillis;
    }

    public void lastSpawnMillis(long lastSpawnMillis) {
        this.lastSpawnMillis = lastSpawnMillis;
    }

    public long createdAt() {
        return createdAt;
    }

    public void createdAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public VirtualStorage storage() {
        return storage;
    }

    public Set<Material> filtered() {
        return filtered;
    }

    public Material preferredSort() {
        return preferredSort;
    }

    public void preferredSort(Material preferredSort) {
        this.preferredSort = preferredSort;
        markDirty();
    }

    public boolean autoSell() {
        return autoSell;
    }

    public void autoSell(boolean autoSell) {
        this.autoSell = autoSell;
        markDirty();
    }

    public boolean autoCollect() {
        return autoCollect;
    }

    public void autoCollect(boolean autoCollect) {
        this.autoCollect = autoCollect;
        markDirty();
    }

    public BlockKey linkedContainer() {
        return linkedContainer;
    }

    public void linkedContainer(BlockKey linkedContainer) {
        this.linkedContainer = linkedContainer;
        markDirty();
    }

    public String network() {
        return network;
    }

    public void network(String network) {
        this.network = network;
        markDirty();
    }

    public long producedItems() {
        return producedItems;
    }

    public void producedItems(long producedItems) {
        this.producedItems = producedItems;
    }

    public void addProducedItems(long amount) {
        this.producedItems += amount;
    }

    public long producedExp() {
        return producedExp;
    }

    public void producedExp(long producedExp) {
        this.producedExp = producedExp;
    }

    public void addProducedExp(long amount) {
        this.producedExp += amount;
    }

    public double earnedMoney() {
        return earnedMoney;
    }

    public void earnedMoney(double earnedMoney) {
        this.earnedMoney = earnedMoney;
    }

    public void addEarnedMoney(double amount) {
        this.earnedMoney += amount;
    }

    // ---------------------------------------------------------------- derived

    public long spawnDelayTicks() {
        return spawnDelayTicks;
    }

    public int minMobs() {
        return minMobs;
    }

    public int maxMobs() {
        return maxMobs;
    }

    public int activationRange() {
        return activationRange;
    }

    public long maxStoredExp() {
        return maxStoredExp;
    }

    public long maxSlots() {
        return maxSlots;
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    public double lootMultiplier() {
        return lootMultiplier;
    }

    public int maxPages() {
        return (int) Math.max(1L, maxSlots / VirtualStorage.SLOTS_PER_PAGE);
    }

    public double fillRatio() {
        long slots = storage.usedSlots();
        return maxSlots <= 0 ? 0.0D : Math.min(1.0D, (double) slots / (double) maxSlots);
    }

    // ---------------------------------------------------------------- flags

    public boolean isDirty() {
        return dirty.get();
    }

    public void markDirty() {
        dirty.set(true);
    }

    public void clearDirty() {
        dirty.set(false);
    }

    /** Guards long running bulk operations so two of them never race on the same spawner. */
    public boolean tryLock() {
        return busy.compareAndSet(false, true);
    }

    public void unlock() {
        busy.set(false);
    }

    public boolean isBusy() {
        return busy.get();
    }
}
