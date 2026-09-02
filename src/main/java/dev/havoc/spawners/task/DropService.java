package dev.havoc.spawners.task;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.config.Messages;
import dev.havoc.spawners.spawner.ItemSig;
import dev.havoc.spawners.spawner.SpawnerData;
import dev.havoc.spawners.spawner.VirtualStorage;
import dev.havoc.spawners.util.Numbers;
import dev.havoc.spawners.util.Text;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Emptying storage without a TPS spike.
 * <p>
 * The naive implementation of "drop 40 pages" builds 1800 item stacks, then 1800 dropped entities, in
 * a single tick. This one converts a bounded number of virtual slots per tick, prefers the player's
 * inventory over loose entities, hard-caps how many entities a single request may create, and puts
 * anything that does not fit straight back into storage. Cost per tick is constant regardless of how
 * much was asked for.
 */
public final class DropService {

    /** One in-flight bulk operation. */
    public static final class Job {
        private final SpawnerData spawner;
        private final UUID player;
        private long remainingSlots;
        private final long fromSlot;
        private final AtomicLong deliveredItems = new AtomicLong();
        private final AtomicLong spawnedEntities = new AtomicLong();
        private final long startedAt = System.currentTimeMillis();
        private final boolean toInventory;
        private ScheduledTask task;
        private Runnable onFinish;

        Job(SpawnerData spawner, UUID player, long fromSlot, long slots, boolean toInventory) {
            this.spawner = spawner;
            this.player = player;
            this.fromSlot = fromSlot;
            this.remainingSlots = slots;
            this.toInventory = toInventory;
        }

        public long deliveredItems() {
            return deliveredItems.get();
        }

        public long elapsedMillis() {
            return System.currentTimeMillis() - startedAt;
        }
    }

    private final HavocSpawners plugin;
    private final Map<String, Job> running = new ConcurrentHashMap<>();

    public DropService(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning(SpawnerData spawner) {
        return running.containsKey(spawner.id());
    }

    public int activeJobs() {
        return running.size();
    }

    /**
     * Drains the given page window into the player.
     *
     * @param firstPage inclusive, zero based
     * @param lastPage  inclusive, zero based
     * @return false when another bulk job already owns this spawner
     */
    public boolean dropPages(Player player, SpawnerData spawner, int firstPage, int lastPage) {
        return dropPages(player, spawner, firstPage, lastPage, plugin.settings().preferPlayerInventory);
    }

    /**
     * @param toInventory true fills the player's inventory first; false drops loose stacks at their
     *                    feet, which is the behaviour the old plugin had
     */
    public boolean dropPages(Player player, SpawnerData spawner, int firstPage, int lastPage,
                             boolean toInventory) {
        int from = Math.max(0, Math.min(firstPage, lastPage));
        int to = Math.max(0, Math.max(firstPage, lastPage));
        int pages = Math.min(to - from + 1, plugin.settings().maxPagesPerRequest);
        long fromSlot = (long) from * VirtualStorage.SLOTS_PER_PAGE;
        long slots = (long) pages * VirtualStorage.SLOTS_PER_PAGE;
        return start(player, spawner, fromSlot, slots, null, toInventory);
    }

    /** Drains everything the spawner holds. */
    public boolean dropAll(Player player, SpawnerData spawner) {
        return dropAll(player, spawner, plugin.settings().preferPlayerInventory);
    }

    public boolean dropAll(Player player, SpawnerData spawner, boolean toInventory) {
        return start(player, spawner, 0L, spawner.storage().usedSlots(), null, toInventory);
    }

    /**
     * Drops every stack of one item type at the player's feet.
     * <p>
     * The item is sorted to the front of storage first, so the existing slot-window machinery can
     * drain exactly that material without a second code path.
     */
    public boolean dropItemToGround(Player player, SpawnerData spawner, ItemSig sig) {
        long amount = spawner.storage().countOf(sig);
        if (amount <= 0L) {
            return false;
        }
        spawner.storage().sortPreferring(sig.material());
        long stacks = (amount + sig.maxStack() - 1L) / sig.maxStack();
        return start(player, spawner, 0L, stacks, null, false);
    }

    /**
     * Drains everything, then runs {@code onFinish} on the job's thread.
     * Used when breaking a spawner: the data row may only disappear once its contents are handed over.
     */
    public boolean dropAllThen(Player player, SpawnerData spawner, Runnable onFinish) {
        return start(player, spawner, 0L, spawner.storage().usedSlots(), onFinish,
                plugin.settings().directToInventory);
    }

    private boolean start(Player player, SpawnerData spawner, long fromSlot, long slots,
                          Runnable onFinish, boolean toInventory) {
        if (slots <= 0L) {
            return false;
        }
        if (running.containsKey(spawner.id()) || !spawner.tryLock()) {
            return false;
        }
        Job job = new Job(spawner, player.getUniqueId(), fromSlot, slots, toInventory);
        job.onFinish = onFinish;
        running.put(spawner.id(), job);

        Location anchor = player.getLocation();
        job.task = plugin.sched().regionTimer(anchor, () -> pump(job), 1L, 1L);
        return true;
    }

    private void pump(Job job) {
        SpawnerData spawner = job.spawner;
        Player player = plugin.getServer().getPlayer(job.player);

        if (player == null || !player.isOnline()) {
            finish(job, null);
            return;
        }
        if (job.remainingSlots <= 0L || job.spawnedEntities.get() >= plugin.settings().maxItemEntities) {
            finish(job, player);
            return;
        }

        int batch = (int) Math.min(plugin.settings().stacksPerTick, job.remainingSlots);
        List<ItemStack> stacks = spawner.storage().slice(job.fromSlot, job.fromSlot + batch, true);
        if (stacks.isEmpty()) {
            finish(job, player);
            return;
        }
        job.remainingSlots -= stacks.size();

        List<ItemStack> overflow = deliver(player, stacks, job);
        if (!overflow.isEmpty()) {
            // Never destroy items: whatever could not be handed over goes back where it came from.
            for (ItemStack stack : overflow) {
                spawner.storage().addUnchecked(ItemSig.of(stack), stack.getAmount());
            }
            finish(job, player);
            return;
        }

        plugin.storage().queueSave(spawner);
        if (plugin.settings().progressActionbar) {
            player.sendActionBar(Text.mm(plugin.messages().raw("bulk-drop.progress"), Messages.of(
                    "items", Numbers.compact(job.deliveredItems()),
                    "pages", Numbers.plain(Math.max(0L, job.remainingSlots / VirtualStorage.SLOTS_PER_PAGE)))));
        }
    }

    /**
     * Hands stacks to the player, then to the ground.
     *
     * @return stacks that could not be delivered at all
     */
    private List<ItemStack> deliver(Player player, List<ItemStack> stacks, Job job) {
        List<ItemStack> pending = new ArrayList<>(stacks);
        long delivered = 0L;

        if (job.toInventory) {
            Map<Integer, ItemStack> leftovers =
                    player.getInventory().addItem(pending.toArray(new ItemStack[0]));
            long before = totalOf(pending);
            pending = new ArrayList<>(leftovers.values());
            delivered += before - totalOf(pending);
        }

        List<ItemStack> undelivered = new ArrayList<>();
        if (!pending.isEmpty()) {
            Location where = player.getLocation();
            int cap = plugin.settings().maxItemEntities;
            for (ItemStack stack : pending) {
                if (stack == null || stack.getAmount() <= 0) {
                    continue;
                }
                if (job.spawnedEntities.get() >= cap) {
                    undelivered.add(stack);
                    continue;
                }
                if (where.getWorld() == null) {
                    undelivered.add(stack);
                    continue;
                }
                if (plugin.settings().mergeGroundStacks) {
                    where.getWorld().dropItem(where, stack);
                } else {
                    where.getWorld().dropItemNaturally(where, stack);
                }
                job.spawnedEntities.incrementAndGet();
                delivered += stack.getAmount();
            }
        }
        job.deliveredItems.addAndGet(delivered);
        return undelivered;
    }

    private static long totalOf(List<ItemStack> stacks) {
        long total = 0L;
        for (ItemStack stack : stacks) {
            if (stack != null) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void finish(Job job, Player player) {
        if (job.task != null) {
            job.task.cancel();
            job.task = null;
        }
        running.remove(job.spawner.id());
        job.spawner.unlock();
        plugin.storage().queueSave(job.spawner);
        if (player != null && player.isOnline()) {
            plugin.messages().send(player, "bulk-drop.done", Messages.of(
                    "items", Numbers.plain(job.deliveredItems()),
                    "seconds", Numbers.duration(job.elapsedMillis())));
        }
        // Runs last so any queued save happens before a caller deletes the row.
        if (job.onFinish != null) {
            Runnable callback = job.onFinish;
            job.onFinish = null;
            try {
                callback.run();
            } catch (Exception ex) {
                plugin.getLogger().warning("Bulk drop callback failed: " + ex.getMessage());
            }
        }
    }

    /**
     * Stops every in-flight job, e.g. on shutdown.
     * Completion callbacks are dropped on purpose: a half-finished handover must leave the spawner
     * (and whatever is still inside it) in the database rather than deleting it.
     */
    public void cancelAll() {
        for (Job job : new ArrayList<>(running.values())) {
            job.onFinish = null;
            finish(job, null);
        }
    }
}
