package dev.havoc.spawners.spawner;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Works out what a spawner item from *another* plugin actually is.
 * <p>
 * A spawner item minted by SmartSpawner - or by an older build of this plugin, or by a crate, a
 * shop, a /give command - carries none of our persistent data. Placing one used to fall straight
 * through to a hard-coded {@link EntityType#PIG}, which is why every old spawner in a player's ender
 * chest turned into a pig spawner the moment it was placed.
 * <p>
 * Instead of guessing, this reads every place a spawner item can plausibly record its type, in
 * descending order of trust:
 * <ol>
 *   <li>the item's own block-entity data ({@code BlockStateMeta}) - where vanilla keeps it;</li>
 *   <li>any foreign persistent-data key whose value names an entity type or a material - this is
 *       namespace-agnostic on purpose, so it works for SmartSpawner and for plugins we have never
 *       seen;</li>
 *   <li>the display name and lore, normalised out of small-caps and legacy colour codes.</li>
 * </ol>
 * Only when all three come back empty does the caller fall back to a configured default.
 */
public final class LegacyItems {

    /** What we managed to work out. Both types null means "no idea". */
    public record Guess(EntityType entityType, Material itemMaterial, int stackSize, String source) {

        public static final Guess EMPTY = new Guess(null, null, 1, "unknown");

        public boolean found() {
            return entityType != null || itemMaterial != null;
        }
    }

    /** Old names that no longer exist as entity types. */
    private static final Map<String, EntityType> ALIASES = new HashMap<>();

    /**
     * Small-caps and full-width letters folded back to ASCII, so a name written
     * "ᴢᴏᴍʙɪᴇ ꜱᴘᴀᴡɴᴇʀ" still reads as ZOMBIE.
     */
    private static final String FANCY = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘqʀꜱᴛᴜᴠᴡxʏᴢǫѕʀ";
    private static final String PLAIN = "abcdefghijklmnopqrstuvwxyzqsr";

    static {
        alias("ZOMBIE_PIGMAN", "ZOMBIFIED_PIGLIN");
        alias("PIG_ZOMBIE", "ZOMBIFIED_PIGLIN");
        alias("PIGMAN", "ZOMBIFIED_PIGLIN");
        alias("MUSHROOM_COW", "MOOSHROOM");
        alias("SNOWMAN", "SNOW_GOLEM");
        alias("VILLAGER_GOLEM", "IRON_GOLEM");
        alias("ENDER_DRAGON", "ENDER_DRAGON");
        alias("CAVESPIDER", "CAVE_SPIDER");
        alias("MAGMACUBE", "MAGMA_CUBE");
        alias("IRONGOLEM", "IRON_GOLEM");
        alias("WITHERSKELETON", "WITHER_SKELETON");
    }

    private static void alias(String from, String to) {
        try {
            ALIASES.put(from, EntityType.valueOf(to));
        } catch (IllegalArgumentException ignored) {
            // The target does not exist on this server version - the alias simply does not apply.
        }
    }

    private LegacyItems() {
    }

    /** Reads a foreign spawner item for everything it is willing to tell us. */
    public static Guess resolve(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return Guess.EMPTY;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Guess.EMPTY;
        }
        int stack = 1;

        // 1. The item's own block-entity data. Vanilla, WorldEdit and most plugins set this, and it
        //    is the only source that cannot be a coincidence, so it wins outright.
        if (meta instanceof BlockStateMeta blockStateMeta) {
            try {
                if (blockStateMeta.hasBlockState()
                        && blockStateMeta.getBlockState() instanceof CreatureSpawner cage) {
                    EntityType type = cage.getSpawnedType();
                    if (type != null) {
                        return new Guess(type, null, stack, "item block data");
                    }
                }
            } catch (Throwable ignored) {
                // A malformed block state is just another empty source.
            }
        }

        // 2. Foreign persistent data, scanned by value rather than by key name so we do not have to
        //    keep a list of other plugins' namespaces up to date.
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        EntityType entity = null;
        Material material = null;
        try {
            for (NamespacedKey key : pdc.getKeys()) {
                String name = key.getKey().toLowerCase(Locale.ROOT);
                Integer number = readInt(pdc, key);
                if (number != null && number > 1 && (name.contains("stack")
                        || name.contains("amount") || name.contains("quantity") || name.contains("size"))) {
                    stack = Math.max(stack, number);
                    continue;
                }
                String value = readString(pdc, key);
                if (value == null || value.isBlank()) {
                    continue;
                }
                EntityType candidate = entityOf(value);
                if (candidate != null) {
                    if (entity == null) {
                        entity = candidate;
                    }
                    continue;
                }
                if (material == null) {
                    Material block = materialOf(value);
                    if (block != null) {
                        material = block;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Never let an unreadable container stop a placement.
        }
        if (entity != null) {
            return new Guess(entity, null, stack, "item data");
        }
        if (material != null) {
            return new Guess(null, material, stack, "item data");
        }

        // 3. The name and lore. Least trustworthy, so it runs last - but it is what saves an item
        //    whose only remaining clue is that it says "Zombie Spawner" on the tin.
        List<String> text = new ArrayList<>();
        addText(text, meta.hasDisplayName() ? meta.displayName() : null);
        if (meta.hasLore() && meta.lore() != null) {
            for (Component line : meta.lore()) {
                addText(text, line);
            }
        }
        for (String line : text) {
            Guess guess = fromText(line, stack);
            if (guess.found()) {
                return guess;
            }
        }
        return new Guess(null, null, stack, "unknown");
    }

    private static void addText(List<String> into, Component component) {
        if (component == null) {
            return;
        }
        try {
            String plain = PlainTextComponentSerializer.plainText().serialize(component);
            if (plain != null && !plain.isBlank()) {
                into.add(plain);
            }
        } catch (Throwable ignored) {
            // A component we cannot flatten is simply not a clue.
        }
    }

    /** Pulls a type out of a line like "&c§lZᴏᴍʙɪᴇ Sᴘᴀᴡɴᴇʀ &7(x4)". */
    static Guess fromText(String raw, int stack) {
        String cleaned = normalise(raw);
        if (cleaned.isEmpty()) {
            return Guess.EMPTY;
        }
        // "SPAWNER" appears in every one of these names and never carries information.
        String stripped = cleaned.replace("SPAWNER", " ").trim();

        Guess whole = match(stripped.replace(' ', '_'), stack);
        if (whole.found()) {
            return whole;
        }
        String[] words = stripped.split("\\s+");
        // Longest run of words first, so "CAVE SPIDER" beats "SPIDER".
        for (int size = words.length; size >= 1; size--) {
            for (int start = 0; start + size <= words.length; start++) {
                StringBuilder builder = new StringBuilder();
                for (int i = start; i < start + size; i++) {
                    if (words[i].isEmpty()) {
                        continue;
                    }
                    if (!builder.isEmpty()) {
                        builder.append('_');
                    }
                    builder.append(words[i]);
                }
                Guess guess = match(builder.toString(), stack);
                if (guess.found()) {
                    return guess;
                }
            }
        }
        return Guess.EMPTY;
    }

    private static Guess match(String token, int stack) {
        EntityType entity = entityOf(token);
        if (entity != null) {
            return new Guess(entity, null, stack, "item name");
        }
        Material material = materialOf(token);
        if (material != null) {
            return new Guess(null, material, stack, "item name");
        }
        return Guess.EMPTY;
    }

    /**
     * Uppercases, folds small caps to ASCII, drops colour codes and turns anything that is not a
     * letter into a separator.
     */
    static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§' || c == '&') {
                // "&#f40d0d" - a hex colour. Eat the whole thing so its digits do not become words.
                if (i + 7 < raw.length() && raw.charAt(i + 1) == '#' && isHex(raw, i + 2, 6)) {
                    i += 7;
                    continue;
                }
                // A legacy colour code: skip it and the character it applies to.
                if (i + 1 < raw.length() && isCode(raw.charAt(i + 1))) {
                    i++;
                    continue;
                }
            }
            int fancy = FANCY.indexOf(Character.toLowerCase(c));
            if (fancy >= 0) {
                builder.append(Character.toUpperCase(PLAIN.charAt(fancy)));
            } else if (Character.isLetter(c)) {
                builder.append(Character.toUpperCase(c));
            } else {
                builder.append('_');
            }
        }
        // Collapse the separators so the word splitter has an easy job.
        return builder.toString().replaceAll("_+", "_").replaceAll("^_|_$", "").replace('_', ' ').trim();
    }

    private static boolean isHex(String raw, int from, int length) {
        if (from + length > raw.length()) {
            return false;
        }
        for (int i = from; i < from + length; i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                || "klmnorxKLMNORX#".indexOf(c) >= 0;
    }

    /** An entity type, or null. Never returns a type for something that is only a material. */
    static EntityType entityOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String token = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (token.isEmpty() || token.equals("SPAWNER") || token.equals("ITEM")) {
            return null;
        }
        if (token.indexOf(':') >= 0) {
            token = token.substring(token.indexOf(':') + 1);
        }
        EntityType alias = ALIASES.get(token);
        if (alias != null) {
            return alias;
        }
        try {
            EntityType type = EntityType.valueOf(token);
            // PLAYER and UNKNOWN are real constants but never valid spawner types.
            return type == EntityType.PLAYER || type == EntityType.UNKNOWN ? null : type;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** A placeable material for an item spawner, or null. */
    static Material materialOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String token = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (token.isEmpty() || token.equals("SPAWNER") || token.equals("AIR")) {
            return null;
        }
        Material material = Material.matchMaterial(token);
        if (material == null || material == Material.AIR || material == Material.SPAWNER) {
            return null;
        }
        return material.isItem() ? material : null;
    }

    private static String readString(PersistentDataContainer pdc, NamespacedKey key) {
        try {
            return pdc.get(key, PersistentDataType.STRING);
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Integer readInt(PersistentDataContainer pdc, NamespacedKey key) {
        try {
            return pdc.get(key, PersistentDataType.INTEGER);
        } catch (Throwable ex) {
            return null;
        }
    }
}
