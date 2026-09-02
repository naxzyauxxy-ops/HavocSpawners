package dev.havoc.spawners.spawner;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.config.Settings;
import dev.havoc.spawners.loot.LootEngine;
import dev.havoc.spawners.loot.LootResult;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Owns every loaded spawner and drives the generation tick. */
public final class SpawnerManager {

    private final HavocSpawners plugin;
    private final Map<String, SpawnerData> byId = new ConcurrentHashMap<>();
    private final Map<BlockKey, SpawnerData> byPosition = new ConcurrentHashMap<>();
    private final PlayerTracker tracker = new PlayerTracker();

    private ScheduledTask tickTask;

    public SpawnerManager(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    public PlayerTracker tracker() {
        return tracker;
    }

    // ------------------------------------------------------------------ registry

    public void loadAll(Collection<SpawnerData> spawners) {
        byId.clear();
        byPosition.clear();
        for (SpawnerData spawner : spawners) {
            byId.put(spawner.id(), spawner);
            byPosition.put(spawner.position(), spawner);
        }
        plugin.getLogger().info("Loaded " + byId.size() + " spawners.");
    }

    public SpawnerData byId(String id) {
        return id == null ? null : byId.get(id);
    }

    public SpawnerData at(BlockKey key) {
        return key == null ? null : byPosition.get(key);
    }

    public Collection<SpawnerData> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }

    public List<SpawnerData> ownedBy(UUID owner) {
        List<SpawnerData> out = new ArrayList<>();
        if (owner == null) {
            return out;
        }
        for (SpawnerData spawner : byId.values()) {
            if (owner.equals(spawner.owner())) {
                out.add(spawner);
            }
        }
        return out;
    }

    public List<SpawnerData> inNetwork(UUID owner, String network) {
        List<SpawnerData> out = new ArrayList<>();
        if (network == null) {
            return out;
        }
        for (SpawnerData spawner : byId.values()) {
            if (network.equalsIgnoreCase(spawner.network())
                    && (owner == null || owner.equals(spawner.owner()))) {
                out.add(spawner);
            }
        }
        return out;
    }

    public SpawnerData create(BlockKey position, EntityType type, Material itemMaterial, Player owner, int stackSize) {
        SpawnerData spawner = new SpawnerData(newId(), position);
        if (itemMaterial != null) {
            spawner.itemMaterial(itemMaterial);
        } else {
            spawner.entityType(type == null ? EntityType.PIG : type);
        }
        if (owner != null) {
            spawner.owner(owner.getUniqueId());
            spawner.ownerName(owner.getName());
        }
        spawner.stackSize(Math.max(1, stackSize));
        spawner.lastSpawnMillis(System.currentTimeMillis());
        spawner.recompute(plugin.settings(), plugin.upgrades());
        register(spawner);
        return spawner;
    }

    public void register(SpawnerData spawner) {
        byId.put(spawner.id(), spawner);
        byPosition.put(spawner.position(), spawner);
        plugin.storage().queueSave(spawner);
    }

    public void remove(SpawnerData spawner) {
        byId.remove(spawner.id());
        byPosition.remove(spawner.position());
        plugin.storage().queueDelete(spawner.id());
    }

    /**
     * Frees the block position while keeping the spawner alive.
     * Used when a broken spawner is still handing its contents back to the player.
     */
    public void detachPosition(SpawnerData spawner) {
        byPosition.remove(spawner.position());
    }

    private String newId() {
        String id;
        do {
            id = Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFL);
        } while (byId.containsKey(id));
        return id;
    }

    /** Drops spawners whose world no longer exists or whose block is gone. */
    public int purgeGhosts() {
        List<SpawnerData> doomed = new ArrayList<>();
        for (SpawnerData spawner : byId.values()) {
            BlockKey key = spawner.position();
            if (org.bukkit.Bukkit.getWorld(key.world()) == null) {
                doomed.add(spawner);
            }
        }
        for (SpawnerData spawner : doomed) {
            remove(spawner);
        }
        return doomed.size();
    }

    public void recomputeAll() {
        for (SpawnerData spawner : byId.values()) {
            spawner.recompute(plugin.settings(), plugin.upgrades());
        }
    }

    // ------------------------------------------------------------------ tick

    public void startTicking() {
        stopTicking();
        // One second resolution is plenty: spawn delays are measured in seconds, and this keeps the
        // per-tick cost of thousands of spawners in the microsecond range.
        tickTask = plugin.sched().globalTimer(this::tick, 40L, 20L);
    }

    public void stopTicking() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void tick() {
        Settings settings = plugin.settings();
        LootEngine engine = plugin.lootEngine();
        long now = System.currentTimeMillis();

        for (SpawnerData spawner : byId.values()) {
            if (spawner.stopped() || spawner.isBusy()) {
                continue;
            }
            boolean nearby = !settings.requirePlayerNearby
                    || tracker.anyWithin(spawner.position(), spawner.activationRange());
            if (spawner.active() != nearby) {
                spawner.active(nearby);
            }
            if (!nearby) {
                // A sleeping spawner must not bank time it never spent awake.
                spawner.lastSpawnMillis(now);
                continue;
            }
            int cycles = LootEngine.cyclesSince(spawner, now, settings.maxCatchupTicks);
            if (cycles <= 0) {
                continue;
            }
            spawner.lastSpawnMillis(now);

            if (settings.stopWhenFull && spawner.storage().usedSlots() >= spawner.maxSlots()
                    && spawner.storedExp() >= spawner.maxStoredExp()) {
                spawner.atCapacity(true);
                continue;
            }

            LootResult result = engine.generate(spawner, cycles);
            if (result.isEmpty()) {
                continue;
            }
            applyLoot(spawner, result);
        }
    }

    private void applyLoot(SpawnerData spawner, LootResult result) {
        boolean overflow = false;
        long stored = 0L;
        for (Map.Entry<ItemSig, Long> entry : result.items().entrySet()) {
            ItemSig sig = entry.getKey();
            if (spawner.filtered().contains(sig.material())) {
                continue;
            }
            long leftover = spawner.storage().add(sig, entry.getValue(), spawner.maxSlots());
            stored += entry.getValue() - leftover;
            if (leftover > 0L) {
                overflow = true;
            }
        }
        if (result.exp() > 0L) {
            long before = spawner.storedExp();
            spawner.addExp(result.exp());
            spawner.addProducedExp(spawner.storedExp() - before);
            if (spawner.storedExp() >= spawner.maxStoredExp()) {
                overflow = true;
            }
        }
        if (stored > 0L) {
            spawner.addProducedItems(stored);
            plugin.analytics().recordItems(spawner, stored);
        }
        spawner.atCapacity(overflow);
        plugin.storage().queueSave(spawner);
    }
}
