package dev.havoc.spawners.econ;

import dev.havoc.spawners.HavocSpawners;
import dev.havoc.spawners.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sell prices.
 * <p>
 * Shop plugins are reached reflectively, so HavocSpawners compiles and runs without any of them
 * installed and never hard-fails when one changes its API.
 */
public final class PriceBook {

    private final Map<Material, Double> custom = new EnumMap<>(Material.class);
    private Method economyShopGui;
    private Method shopGuiPlus;
    private String shopName = "none";

    public void reload(HavocSpawners plugin) {
        custom.clear();
        File file = new File(plugin.getDataFolder(), "prices.yml");
        if (!file.exists()) {
            plugin.saveResource("prices.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("prices");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
                if (material == null) {
                    plugin.getLogger().warning("Unknown material '" + key + "' in prices.yml");
                    continue;
                }
                custom.put(material, section.getDouble(key, 0.0D));
            }
        }
        hookShops(plugin);
    }

    private void hookShops(HavocSpawners plugin) {
        economyShopGui = null;
        shopGuiPlus = null;
        shopName = "none";
        try {
            if (Bukkit.getPluginManager().getPlugin("EconomyShopGUI") != null
                    || Bukkit.getPluginManager().getPlugin("EconomyShopGUI-Premium") != null) {
                Class<?> hook = Class.forName("me.gypopo.economyshopgui.api.EconomyShopGUIHook");
                economyShopGui = hook.getMethod("getItemSellPrice", ItemStack.class);
                shopName = "EconomyShopGUI";
            }
        } catch (Throwable ignored) {
            economyShopGui = null;
        }
        try {
            if (economyShopGui == null && Bukkit.getPluginManager().getPlugin("ShopGUIPlus") != null) {
                Class<?> api = Class.forName("net.brcdev.shopgui.ShopGuiPlusApi");
                shopGuiPlus = api.getMethod("getItemStackPriceSell", ItemStack.class);
                shopName = "ShopGUIPlus";
            }
        } catch (Throwable ignored) {
            shopGuiPlus = null;
        }
        if (!"none".equals(shopName)) {
            plugin.getLogger().info("Sell prices will fall back to " + shopName + ".");
        }
    }

    public String shopName() {
        return shopName;
    }

    /** Unit price for one item of this stack's type. */
    public double priceOf(ItemStack template, Settings settings) {
        if (settings.priceSource == Settings.PriceSource.SHOP_THEN_CUSTOM) {
            Double shop = shopPrice(template);
            if (shop != null && shop > 0.0D) {
                return shop;
            }
        }
        Double own = custom.get(template.getType());
        if (own != null) {
            return own;
        }
        return settings.defaultPrice;
    }

    private Double shopPrice(ItemStack template) {
        try {
            if (economyShopGui != null) {
                Object value = economyShopGui.invoke(null, template);
                return value instanceof Number number ? number.doubleValue() : null;
            }
            if (shopGuiPlus != null) {
                Object value = shopGuiPlus.invoke(null, template);
                return value instanceof Number number ? number.doubleValue() : null;
            }
        } catch (Throwable ignored) {
            // A shop plugin failing must never break selling.
        }
        return null;
    }

    public Map<Material, Double> customPrices() {
        return Map.copyOf(custom);
    }
}
