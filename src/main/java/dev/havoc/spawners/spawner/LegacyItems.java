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

import java.util.HashMap;
import java.util.LinkedHashMap;
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
 *   <li>any foreign persistent-data key whose value names an entity type - this is namespace-agnostic
 *       on purpose, so it works for SmartSpawner and for plugins we have never seen;</li>
 *   <li>the display name, and then the lore, normalised out of small-caps and legacy colour codes.</li>
 * </ol>
 * Only when all three come back empty does the caller fall back to a configured default.
 * <p>
 * <b>Item spawners are held to a higher standard than mob spawners.</b> A material name can appear on
 * a mob spawner for entirely innocent reasons - "Contains: Rotten Flesh" in the lore, a loot preview,
 * a foreign key holding an icon - and reading one of those as the spawner's identity is how a zombie
 * spawner became a bone-block spawner. So a material is only accepted from a key that says it holds
 * one, or from the words immediately in front of "SPAWNER" in the item's name. An entity type is
 * accepted from anywhere, because a mob name on a spawner item is never an accident.
 * <p>
 * The exception is an item that <em>declares</em> itself. SmartSpawner records an item spawner as the
 * literal token {@code ITEM} where a mob spawner would name a mob, with the material stored beside it
 * ({@code entityType: ITEM, itemSpawnerMaterial: BONE_BLOCK}). Once that token is present there is
 * nothing left to confuse a material with, so the caution is dropped and a material is taken from any
 * key - and a declared item spawner is never turned back into a mob spawner by a stray mob name.
 */
public final class LegacyItems {

    /**
     * What we managed to work out. Both types null means "no idea".
     *
     * @param declaredItem the item said outright that it is an item spawner (SmartSpawner writes the
     *                     literal token {@code ITEM} where a mob spawner would name a mob). That
     *                     removes the ambiguity that makes us cautious about materials elsewhere.
     */
    public record Guess(EntityType entityType, Material itemMaterial, int stackSize, String source,
                        boolean declaredItem) {

        public static final Guess EMPTY = new Guess(null, null, 1, "unknown", false);

        public Guess(EntityType entityType, Material itemMaterial, int stackSize, String source) {
            this(entityType, itemMaterial, stackSize, source, itemMaterial != null);
        }

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

        // 2. Foreign persistent data, read in two passes.
        //
        //    SmartSpawner stores an item spawner as the literal token ITEM where a mob spawner names
        //    a mob, with the material alongside it ("entityType: ITEM, itemSpawnerMaterial:
        //    BONE_BLOCK"). That token is a *declaration*, and it is what the first pass looks for.
        //    Once an item has declared itself, the ambiguity that makes us cautious about materials
        //    is gone, so the second pass will take a material from any key at all.
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Map<String, String> data = new LinkedHashMap<>();
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
                if (value != null && !value.isBlank()) {
                    data.put(name, value);
                }
            }
        } catch (Throwable ignored) {
            // Never let an unreadable container stop a placement.
        }

        boolean declaredItem = false;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (isItemDeclaration(entry.getKey()) || isItemDeclaration(entry.getValue())) {
                declaredItem = true;
                break;
            }
        }

        EntityType entity = null;
        Material material = null;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String value = entry.getValue();
            if (entity == null) {
                entity = entityOf(value);
                if (entity != null) {
                    continue;
                }
            }
            if (material == null && (declaredItem || namesAMaterial(entry.getKey()))) {
                material = materialOf(value);
            }
        }
        // A declaration outranks a stray mob name: an item spawner that happens to carry the mob it
        // was converted from is still an item spawner.
        if (declaredItem && material != null) {
            return new Guess(null, material, stack, "item data (declared)", true);
        }
        if (entity != null) {
            return new Guess(entity, null, stack, "item data");
        }
        if (material != null) {
            return new Guess(null, material, stack, "item data");
        }
        if (declaredItem) {
            // It told us it is an item spawner but never said of what. Try the name for a material
            // without the usual anchoring, since there is nothing left to confuse it with.
            Material named = meta.hasDisplayName() ? materialIn(plain(meta.displayName())) : null;
            if (named != null) {
                return new Guess(null, named, stack, "item name (declared)", true);
            }
            return new Guess(null, null, stack, "item spawner of unknown material", true);
        }

        // 3. The name, then the lore. Least trustworthy, so it runs last - but it is what saves an
        //    item whose only remaining clue is that it says "Zombie Spawner" on the tin.
        //
        //    The two are NOT treated alike. A display name is about the item; lore is about its
        //    contents, its stack count, how to use it. Reading a material out of lore is how
        //    "Contains: Rotten Flesh" on a zombie spawner turned it into an item spawner, so lore is
        //    searched for entity types only.
        String name = null;
        if (meta.hasDisplayName()) {
            name = plain(meta.displayName());
        }
        if (name != null) {
            Guess guess = fromName(name, stack);
            if (guess.found()) {
                return guess;
            }
        }
        if (meta.hasLore() && meta.lore() != null) {
            for (Component line : meta.lore()) {
                EntityType type = entityInText(plain(line));
                if (type != null) {
                    return new Guess(type, null, stack, "item lore");
                }
            }
        }
        return new Guess(null, null, stack, "unknown");
    }

    /**
     * True for the token an item spawner identifies itself with.
     * <p>
     * SmartSpawner writes {@code entityType: ITEM} - the one place in the data where "ITEM" appears
     * as a type rather than as part of a longer word, which is why this matches whole tokens only.
     */
    private static boolean isItemDeclaration(String raw) {
        if (raw == null) {
            return false;
        }
        String token = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return token.equals("ITEM") || token.equals("ITEM_SPAWNER") || token.equals("ITEMSPAWNER")
                || token.equals("SPAWNER_ITEM");
    }

    /** Any material named anywhere in a line - only used once an item has declared itself. */
    private static Material materialIn(String raw) {
        if (raw == null) {
            return null;
        }
        String[] words = words(normalise(raw).replace("SPAWNER", " "));
        for (int size = Math.min(3, words.length); size >= 1; size--) {
            for (int start = 0; start + size <= words.length; start++) {
                Material material = materialOf(join(words, start, size));
                if (material != null) {
                    return material;
                }
            }
        }
        return null;
    }

    /** True when a persistent-data key is claiming to hold a material rather than anything else. */
    private static boolean namesAMaterial(String key) {
        return key.contains("item") || key.contains("material") || key.contains("mat")
                || key.contains("block") || key.contains("drop") || key.contains("loot");
    }

    private static String plain(Component component) {
        if (component == null) {
            return null;
        }
        try {
            String text = PlainTextComponentSerializer.plainText().serialize(component);
            return text == null || text.isBlank() ? null : text;
        } catch (Throwable ex) {
            // A component we cannot flatten is simply not a clue.
            return null;
        }
    }

    /**
     * Reads a display name like "&c§lZᴏᴍʙɪᴇ Sᴘᴀᴡɴᴇʀ &7(x4)".
     * <p>
     * Entity types are looked for anywhere in the name. A material has to earn it: only the words
     * immediately in front of the word "SPAWNER" count, so "Bone Block Spawner" is an item spawner
     * while "Zombie Spawner &7(Bones)" is not.
     */
    static Guess fromName(String raw, int stack) {
        String cleaned = normalise(raw);
        if (cleaned.isEmpty()) {
            return Guess.EMPTY;
        }
        EntityType entity = entityIn(words(cleaned.replace("SPAWNER", " ")));
        if (entity != null) {
            return new Guess(entity, null, stack, "item name");
        }
        Material material = materialBeforeSpawner(cleaned);
        if (material != null) {
            return new Guess(null, material, stack, "item name");
        }
        return Guess.EMPTY;
    }

    /** Entity types only - used for lore, where a material would be describing the contents. */
    static EntityType entityInText(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = normalise(raw);
        return cleaned.isEmpty() ? null : entityIn(words(cleaned.replace("SPAWNER", " ")));
    }

    private static String[] words(String cleaned) {
        String trimmed = cleaned.trim();
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }

    /** Longest run of words first, so "CAVE SPIDER" beats "SPIDER". */
    private static EntityType entityIn(String[] words) {
        for (int size = words.length; size >= 1; size--) {
            for (int start = 0; start + size <= words.length; start++) {
                EntityType type = entityOf(join(words, start, size));
                if (type != null) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * The material named by the words directly in front of "SPAWNER", longest run first.
     * <p>
     * Anchoring to that word is the whole point: it is what separates a name that says the item
     * <em>is</em> a bone block spawner from one that merely mentions bones.
     */
    private static Material materialBeforeSpawner(String cleaned) {
        String[] all = words(cleaned);
        int end = -1;
        for (int i = all.length - 1; i >= 0; i--) {
            if (all[i].equals("SPAWNER")) {
                end = i;
                break;
            }
        }
        if (end <= 0) {
            return null;
        }
        // A material name is at most three words ("light blue stained glass" is four, but those are
        // not spawner types anybody ships).
        int limit = Math.min(3, end);
        for (int size = limit; size >= 1; size--) {
            Material material = materialOf(join(all, end - size, size));
            if (material != null) {
                return material;
            }
        }
        return null;
    }

    private static String join(String[] words, int start, int size) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < start + size && i < words.length; i++) {
            if (words[i].isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('_');
            }
            builder.append(words[i]);
        }
        return builder.toString();
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
