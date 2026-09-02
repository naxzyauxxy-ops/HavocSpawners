package dev.havoc.spawners.migrate;

import dev.havoc.spawners.spawner.ItemSig;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses SmartSpawner's item encoding.
 * <p>
 * Three shapes exist in the wild:
 * <pre>
 *   IRON_INGOT:158142            plain material
 *   DIAMOND_SWORD;120:3          material with a damage value
 *   TIPPED_ARROW#STRENGTH:64     tipped arrow with a base potion type
 * </pre>
 */
public final class LegacyItemCodec {

    private LegacyItemCodec() {
    }

    /** Parses one token; returns null when the material no longer exists. */
    public static Map.Entry<ItemSig, Long> parse(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int colon = trimmed.lastIndexOf(':');
        if (colon <= 0) {
            return null;
        }
        long amount;
        try {
            amount = Long.parseLong(trimmed.substring(colon + 1).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        if (amount <= 0L) {
            return null;
        }
        String descriptor = trimmed.substring(0, colon).trim();

        ItemStack stack;
        if (descriptor.startsWith("TIPPED_ARROW#")) {
            stack = new ItemStack(Material.TIPPED_ARROW);
            String potion = descriptor.substring("TIPPED_ARROW#".length()).trim();
            ItemMeta meta = stack.getItemMeta();
            if (meta instanceof PotionMeta potionMeta) {
                try {
                    potionMeta.setBasePotionType(PotionType.valueOf(potion.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    potionMeta.setBasePotionType(PotionType.WATER);
                }
                stack.setItemMeta(potionMeta);
            }
        } else {
            int semi = descriptor.indexOf(';');
            String materialName = semi < 0 ? descriptor : descriptor.substring(0, semi);
            Material material = Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
            if (material == null || material.isAir()) {
                return null;
            }
            stack = new ItemStack(material);
            if (semi >= 0) {
                try {
                    int damage = Integer.parseInt(descriptor.substring(semi + 1).trim());
                    ItemMeta meta = stack.getItemMeta();
                    if (meta instanceof Damageable damageable && damage > 0) {
                        damageable.setDamage(damage);
                        stack.setItemMeta(meta);
                    }
                } catch (NumberFormatException ignored) {
                    // keep the undamaged item
                }
            }
        }
        return Map.entry(ItemSig.of(stack), amount);
    }

    /** Parses a whole inventory list, tolerating the {@code [a,b,c]} database wrapper. */
    public static Map<ItemSig, Long> parseAll(Iterable<String> tokens) {
        Map<ItemSig, Long> out = new LinkedHashMap<>();
        for (String token : tokens) {
            Map.Entry<ItemSig, Long> entry = parse(token);
            if (entry != null) {
                out.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }
        return out;
    }

    public static Map<ItemSig, Long> parseBracketed(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        String body = raw.trim();
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.isBlank()) {
            return new LinkedHashMap<>();
        }
        return parseAll(java.util.Arrays.asList(body.split(",")));
    }
}
