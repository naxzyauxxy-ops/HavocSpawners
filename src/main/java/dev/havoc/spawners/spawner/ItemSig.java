package dev.havoc.spawners.spawner;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

/**
 * Identity of a stored item type, independent of amount.
 * <p>
 * Two item stacks belong to the same signature when their serialized form (material + components)
 * matches. Storage then only keeps a {@code long} counter per signature, which is what lets a single
 * spawner hold millions of items without ever creating millions of {@link ItemStack} objects.
 */
public final class ItemSig {

    private final ItemStack template;
    private final byte[] key;
    private final int hash;

    private ItemSig(ItemStack template, byte[] key) {
        this.template = template;
        this.key = key;
        this.hash = Arrays.hashCode(key);
    }

    public static ItemSig of(ItemStack stack) {
        ItemStack template = stack.clone();
        template.setAmount(1);
        return new ItemSig(template, template.serializeAsBytes());
    }

    public static ItemSig of(Material material) {
        return of(new ItemStack(material, 1));
    }

    /** Never hand this out for mutation - always clone first. */
    public ItemStack template() {
        return template;
    }

    public ItemStack copy(int amount) {
        ItemStack copy = template.clone();
        copy.setAmount(Math.max(1, amount));
        return copy;
    }

    public Material material() {
        return template.getType();
    }

    public int maxStack() {
        int max = template.getMaxStackSize();
        return max <= 0 ? 64 : max;
    }

    public byte[] key() {
        return key;
    }

    public String encodeKey() {
        return java.util.Base64.getEncoder().encodeToString(key);
    }

    public static ItemSig decodeKey(String encoded) {
        return of(ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(encoded)));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ItemSig sig && this.hash == sig.hash && Arrays.equals(this.key, sig.key);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "ItemSig(" + template.getType() + ")";
    }
}
