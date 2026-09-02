package dev.havoc.spawners.util;

import dev.havoc.spawners.config.Settings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Throws a stack out of a player the way vanilla does when you press Q.
 * <p>
 * The item spawns just below eye level and is launched along the look vector with a little random
 * spread, so stacks arc out in front of the player instead of piling up on their feet.
 */
public final class ItemThrow {

    private ItemThrow() {
    }

    /**
     * @return the spawned item entity, or null if it could not be created
     */
    public static Item throwFromLook(Player player, ItemStack stack, Settings settings) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return null;
        }
        // Vanilla spawns the drop 0.3 blocks below the eye.
        Location spawn = eye.clone().subtract(0.0D, 0.3D, 0.0D);
        Item item = world.dropItem(spawn, stack);

        Vector velocity = eye.getDirection().normalize().multiply(settings.throwStrength);
        // Vanilla adds a small upward kick so the stack arcs rather than skids.
        velocity.setY(velocity.getY() + 0.1D);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double spread = 0.02D;
        velocity.add(new Vector(
                (random.nextDouble() - 0.5D) * spread,
                (random.nextDouble() - 0.5D) * spread,
                (random.nextDouble() - 0.5D) * spread));

        item.setVelocity(velocity);
        item.setPickupDelay(Math.max(0, settings.pickupDelayTicks));
        item.setThrower(player.getUniqueId());
        return item;
    }

    /** Plain drop at the player's feet, used when throw-from-look is turned off. */
    public static Item dropAtFeet(Player player, ItemStack stack, Settings settings) {
        World world = player.getWorld();
        Item item = world.dropItemNaturally(player.getLocation(), stack);
        item.setPickupDelay(Math.max(0, settings.pickupDelayTicks));
        item.setThrower(player.getUniqueId());
        return item;
    }

    public static Item deliver(Player player, ItemStack stack, Settings settings) {
        return settings.throwFromLook
                ? throwFromLook(player, stack, settings)
                : dropAtFeet(player, stack, settings);
    }
}
