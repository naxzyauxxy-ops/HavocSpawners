package dev.havoc.spawners.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Thin wrapper over Paper's region schedulers.
 * <p>
 * Paper implements the whole {@code io.papermc.paper.threadedregions.scheduler} family even when it is
 * not running Folia, so using it unconditionally keeps a single code path that is correct on both.
 */
public final class Sched {

    private final Plugin plugin;

    public Sched(Plugin plugin) {
        this.plugin = plugin;
    }

    private static Consumer<ScheduledTask> wrap(Runnable runnable) {
        return task -> runnable.run();
    }

    /** Runs on the global region (world time, plugin-wide state). */
    public ScheduledTask global(Runnable runnable) {
        return Bukkit.getGlobalRegionScheduler().run(plugin, wrap(runnable));
    }

    public ScheduledTask globalLater(Runnable runnable, long delayTicks) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, wrap(runnable), Math.max(1L, delayTicks));
    }

    public ScheduledTask globalTimer(Runnable runnable, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, wrap(runnable), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    /** Runs on the region that owns {@code location}. Safe for block and world access. */
    public ScheduledTask region(Location location, Runnable runnable) {
        return Bukkit.getRegionScheduler().run(plugin, location, wrap(runnable));
    }

    public ScheduledTask regionLater(Location location, Runnable runnable, long delayTicks) {
        return Bukkit.getRegionScheduler().runDelayed(plugin, location, wrap(runnable), Math.max(1L, delayTicks));
    }

    public ScheduledTask regionTimer(Location location, Runnable runnable, long delayTicks, long periodTicks) {
        return Bukkit.getRegionScheduler()
                .runAtFixedRate(plugin, location, wrap(runnable), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    /** Fire and forget on the async pool. Never touch the world from here. */
    public ScheduledTask async(Runnable runnable) {
        return Bukkit.getAsyncScheduler().runNow(plugin, wrap(runnable));
    }

    public ScheduledTask asyncLater(Runnable runnable, long delay, TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runDelayed(plugin, wrap(runnable), delay, unit);
    }

    public ScheduledTask asyncTimer(Runnable runnable, long delay, long period, TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, wrap(runnable), delay, period, unit);
    }

    public static void cancel(ScheduledTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
