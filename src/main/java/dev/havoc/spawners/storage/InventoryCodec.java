package dev.havoc.spawners.storage;

import dev.havoc.spawners.spawner.ItemSig;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Serialises virtual storage as {@code base64(itemBytes)*count;base64(itemBytes)*count}.
 * <p>
 * The item bytes come from {@link org.bukkit.inventory.ItemStack#serializeAsBytes()}, so enchantments,
 * potion data, custom names and any other data component survive a restart - unlike the legacy
 * "MATERIAL:amount" format, which silently dropped them.
 */
public final class InventoryCodec {

    private InventoryCodec() {
    }

    public static String encode(Map<ItemSig, Long> counts) {
        if (counts.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(counts.size() * 48);
        for (Map.Entry<ItemSig, Long> entry : counts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(entry.getKey().encodeKey()).append('*').append(entry.getValue().longValue());
        }
        return builder.toString();
    }

    public static Map<ItemSig, Long> decode(String raw) {
        Map<ItemSig, Long> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split(";")) {
            if (part.isBlank()) {
                continue;
            }
            int split = part.lastIndexOf('*');
            if (split <= 0) {
                continue;
            }
            try {
                long amount = Long.parseLong(part.substring(split + 1).trim());
                if (amount <= 0L) {
                    continue;
                }
                ItemSig sig = ItemSig.decodeKey(part.substring(0, split));
                out.merge(sig, amount, Long::sum);
            } catch (Exception ignored) {
                // A single unreadable entry must not cost the player the rest of the storage.
            }
        }
        return out;
    }

    public static String encodeFilters(Set<Material> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (Material material : filters) {
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(material.name());
        }
        return builder.toString();
    }

    public static void decodeFilters(String raw, Set<Material> target) {
        target.clear();
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            Material material = Material.matchMaterial(part.trim().toUpperCase(Locale.ROOT));
            if (material != null) {
                target.add(material);
            }
        }
    }
}
