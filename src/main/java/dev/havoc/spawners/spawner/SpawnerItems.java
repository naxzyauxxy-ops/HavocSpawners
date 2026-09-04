package dev.havoc.spawners.spawner;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.config.Messages;
import dev.havoc.spawners.util.Numbers;
import dev.havoc.spawners.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds and reads the physical spawner item. */
public final class SpawnerItems {

    private final HavocSpawners plugin;
    private final NamespacedKey keyType;
    private final NamespacedKey keyItemMaterial;
    private final NamespacedKey keyStack;
    private final NamespacedKey keyLevel;
    private final NamespacedKey keyStorage;
    private final NamespacedKey keyStoredExp;

    public SpawnerItems(HavocSpawners plugin) {
        this.plugin = plugin;
        this.keyType = new NamespacedKey(plugin, "spawner_type");
        this.keyItemMaterial = new NamespacedKey(plugin, "item_material");
        this.keyStack = new NamespacedKey(plugin, "stack_size");
        this.keyLevel = new NamespacedKey(plugin, "level");
        this.keyStorage = new NamespacedKey(plugin, "storage");
        this.keyStoredExp = new NamespacedKey(plugin, "stored_exp");
    }

    public ItemStack create(EntityType entityType, Material itemMaterial, int stackSize, int level, int amount) {
        return create(entityType, itemMaterial, stackSize, level, amount, null, 0L);
    }

    /**
     * Builds a spawner item, optionally carrying a whole virtual storage inside it.
     * <p>
     * Carrying the contents on the item is what lets a spawner holding millions of items be broken
     * without dropping a single entity: the counters ride along in the item's persistent data and are
     * restored when it is placed again.
     */
    public ItemStack create(EntityType entityType, Material itemMaterial, int stackSize, int level,
                            int amount, String storagePayload, long storedExp) {
        ItemStack item = new ItemStack(Material.SPAWNER, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (meta instanceof BlockStateMeta blockStateMeta) {
            try {
                if (blockStateMeta.getBlockState() instanceof CreatureSpawner spawnerState) {
                    // null for an item spawner: an empty cage in the item preview beats a stray pig.
                    spawnerState.setSpawnedType(itemMaterial != null ? null : entityType);
                    blockStateMeta.setBlockState(spawnerState);
                }
            } catch (Throwable ignored) {
                // Not every type round-trips through an item's block state - the block itself is
                // corrected on placement by SpawnerBlocks, so this is only a nicety.
            }
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (itemMaterial != null) {
            pdc.set(keyType, PersistentDataType.STRING, "ITEM");
            pdc.set(keyItemMaterial, PersistentDataType.STRING, itemMaterial.name());
        } else {
            pdc.set(keyType, PersistentDataType.STRING,
                    entityType == null ? EntityType.PIG.name() : entityType.name());
        }
        pdc.set(keyStack, PersistentDataType.INTEGER, Math.max(1, stackSize));
        pdc.set(keyLevel, PersistentDataType.INTEGER, Math.max(1, level));
        if (storagePayload != null && !storagePayload.isBlank()) {
            pdc.set(keyStorage, PersistentDataType.STRING, storagePayload);
        }
        if (storedExp > 0L) {
            pdc.set(keyStoredExp, PersistentDataType.LONG, storedExp);
        }

        String typeName = pretty(itemMaterial != null ? itemMaterial.name()
                : (entityType == null ? "PIG" : entityType.name()));
        meta.displayName(Text.mm(plugin.messages().raw("item.spawner-name"),
                Messages.of("type", typeName, "stack", Numbers.plain(stackSize), "level", String.valueOf(level))));

        List<Component> lore = new ArrayList<>();
        for (String line : plugin.messages().raw("item.spawner-lore").split("\\n")) {
            lore.add(Text.mm(line, Messages.of(
                    "type", typeName,
                    "stack", Numbers.plain(stackSize),
                    "level", String.valueOf(level))));
        }
        long carried = countOf(storagePayload);
        if (carried > 0L || storedExp > 0L) {
            for (String line : plugin.messages().raw("item.spawner-contents").split("\\n")) {
                lore.add(Text.mm(line, Messages.of(
                        "items", Numbers.plain(carried),
                        "exp", Numbers.plain(storedExp))));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isHavocSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(keyType, PersistentDataType.STRING);
    }

    public EntityType readEntityType(ItemStack item) {
        String raw = readString(item, keyType);
        if (raw == null || raw.equals("ITEM")) {
            return null;
        }
        try {
            return EntityType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Material readItemMaterial(ItemStack item) {
        String raw = readString(item, keyType);
        if (raw == null || !raw.equals("ITEM")) {
            return null;
        }
        String material = readString(item, keyItemMaterial);
        return material == null ? null : Material.matchMaterial(material.toUpperCase(Locale.ROOT));
    }

    public int readStackSize(ItemStack item) {
        Integer value = readInt(item, keyStack);
        return value == null ? 1 : Math.max(1, value);
    }

    /** Storage the item is carrying, empty when it holds none. */
    public java.util.Map<ItemSig, Long> readStorage(ItemStack item) {
        String payload = readString(item, keyStorage);
        return payload == null
                ? new java.util.LinkedHashMap<>()
                : dev.havoc.spawners.storage.InventoryCodec.decode(payload);
    }

    public long readStoredExp(ItemStack item) {
        if (item == null) {
            return 0L;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0L;
        }
        Long value = meta.getPersistentDataContainer().get(keyStoredExp, PersistentDataType.LONG);
        return value == null ? 0L : Math.max(0L, value);
    }

    private static long countOf(String payload) {
        if (payload == null || payload.isBlank()) {
            return 0L;
        }
        long total = 0L;
        for (java.util.Map.Entry<ItemSig, Long> entry
                : dev.havoc.spawners.storage.InventoryCodec.decode(payload).entrySet()) {
            total += entry.getValue();
        }
        return total;
    }

    public int readLevel(ItemStack item) {
        Integer value = readInt(item, keyLevel);
        return value == null ? 1 : Math.max(1, value);
    }

    private String readString(ItemStack item, NamespacedKey key) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private Integer readInt(ItemStack item, NamespacedKey key) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
    }

    public static String pretty(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "Unknown";
        }
        String[] words = raw.toLowerCase(Locale.ROOT).split("_");
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
}
