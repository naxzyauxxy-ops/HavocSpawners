package dev.havoc.spawners.spawner;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Slot-addressable item storage backed by {@code long} counters.
 * <p>
 * A page is a window of {@link #SLOTS_PER_PAGE} virtual slots. Slots are derived on demand from the
 * counters, so paging through - or emptying - a spawner holding tens of millions of items costs the
 * same as one holding ten. This is the piece that makes "drop many pages at once" cheap.
 */
public final class VirtualStorage {

    public static final int SLOTS_PER_PAGE = 45;

    private final LinkedHashMap<ItemSig, Long> counts = new LinkedHashMap<>();

    public synchronized boolean isEmpty() {
        return counts.isEmpty();
    }

    public synchronized void clear() {
        counts.clear();
    }

    public synchronized Map<ItemSig, Long> snapshot() {
        return new LinkedHashMap<>(counts);
    }

    public synchronized long countOf(ItemSig sig) {
        return counts.getOrDefault(sig, 0L);
    }

    public synchronized long totalItems() {
        long total = 0L;
        for (long value : counts.values()) {
            total += value;
        }
        return total;
    }

    /** Number of virtual slots currently occupied. */
    public synchronized long usedSlots() {
        long slots = 0L;
        for (Map.Entry<ItemSig, Long> entry : counts.entrySet()) {
            slots += slotsFor(entry.getValue(), entry.getKey().maxStack());
        }
        return slots;
    }

    public synchronized int pageCount() {
        long slots = usedSlots();
        if (slots <= 0L) {
            return 1;
        }
        long pages = (slots + SLOTS_PER_PAGE - 1L) / SLOTS_PER_PAGE;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, pages));
    }

    private static long slotsFor(long amount, int maxStack) {
        if (amount <= 0L) {
            return 0L;
        }
        return (amount + maxStack - 1L) / maxStack;
    }

    /**
     * Adds items, honouring a slot ceiling.
     *
     * @return the amount that did not fit
     */
    public synchronized long add(ItemSig sig, long amount, long slotLimit) {
        if (amount <= 0L) {
            return 0L;
        }
        long used = usedSlots();
        long existing = counts.getOrDefault(sig, 0L);
        int maxStack = sig.maxStack();
        long usedBySig = slotsFor(existing, maxStack);
        long freeSlots = Math.max(0L, slotLimit - (used - usedBySig));
        long capacityForSig = freeSlots * maxStack;
        long accepted = Math.min(amount, Math.max(0L, capacityForSig - existing));
        if (accepted > 0L) {
            counts.merge(sig, accepted, Long::sum);
        }
        return amount - accepted;
    }

    /** Unbounded add - used by the importer and by rollbacks, which must never lose items. */
    public synchronized void addUnchecked(ItemSig sig, long amount) {
        if (amount > 0L) {
            counts.merge(sig, amount, Long::sum);
        }
    }

    public synchronized long remove(ItemSig sig, long amount) {
        Long current = counts.get(sig);
        if (current == null || amount <= 0L) {
            return 0L;
        }
        long taken = Math.min(current, amount);
        long left = current - taken;
        if (left <= 0L) {
            counts.remove(sig);
        } else {
            counts.put(sig, left);
        }
        return taken;
    }

    /** Everything the storage holds, as real stacks. Only safe for small storages. */
    public synchronized List<ItemStack> page(int pageIndex) {
        return slice((long) pageIndex * SLOTS_PER_PAGE, (long) (pageIndex + 1) * SLOTS_PER_PAGE, false);
    }

    /**
     * Materialises the stacks living in {@code [fromSlot, toSlot)}.
     *
     * @param take when true the returned amounts are deducted from storage
     */
    public synchronized List<ItemStack> slice(long fromSlot, long toSlot, boolean take) {
        List<ItemStack> out = new ArrayList<>();
        if (toSlot <= fromSlot) {
            return out;
        }
        long cursor = 0L;
        List<Map.Entry<ItemSig, Long>> entries = new ArrayList<>(counts.entrySet());
        for (Map.Entry<ItemSig, Long> entry : entries) {
            ItemSig sig = entry.getKey();
            long amount = entry.getValue();
            int maxStack = sig.maxStack();
            long slots = slotsFor(amount, maxStack);
            long start = cursor;
            long end = cursor + slots;
            cursor = end;
            if (end <= fromSlot) {
                continue;
            }
            if (start >= toSlot) {
                break;
            }
            long overlapStart = Math.max(start, fromSlot);
            long overlapEnd = Math.min(end, toSlot);
            long overlapSlots = overlapEnd - overlapStart;
            if (overlapSlots <= 0L) {
                continue;
            }
            boolean includesTail = overlapEnd == end;
            long remainder = amount - (slots - 1L) * maxStack;
            long taken = includesTail
                    ? (overlapSlots - 1L) * maxStack + remainder
                    : overlapSlots * maxStack;
            taken = Math.min(taken, amount);
            emit(out, sig, taken, maxStack);
            if (take) {
                remove(sig, taken);
            }
        }
        return out;
    }

    private static void emit(List<ItemStack> out, ItemSig sig, long amount, int maxStack) {
        long left = amount;
        while (left > 0L) {
            int size = (int) Math.min(left, maxStack);
            out.add(sig.copy(size));
            left -= size;
        }
    }

    /**
     * Removes up to {@code maxStacks} stacks starting at slot 0 and returns them.
     * Used by the bulk drop service so it can meter work across ticks.
     */
    public synchronized List<ItemStack> takeStacks(int maxStacks) {
        if (maxStacks <= 0) {
            return Collections.emptyList();
        }
        return slice(0L, maxStacks, true);
    }

    /** Reorders storage so the given material is displayed first. */
    public synchronized void sortPreferring(Material preferred) {
        List<Map.Entry<ItemSig, Long>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator
                .comparingInt((Map.Entry<ItemSig, Long> e) -> e.getKey().material() == preferred ? 0 : 1)
                .thenComparing((Map.Entry<ItemSig, Long> e) -> -e.getValue())
                .thenComparing(e -> e.getKey().material().name()));
        counts.clear();
        for (Map.Entry<ItemSig, Long> entry : entries) {
            counts.put(entry.getKey(), entry.getValue());
        }
    }

    /** Distinct item types held. Always small, which is why the dialogs stay cheap. */
    public synchronized int distinctTypes() {
        return counts.size();
    }

    public synchronized List<Map.Entry<ItemSig, Long>> orderedEntries() {
        return new ArrayList<>(counts.entrySet());
    }
}
