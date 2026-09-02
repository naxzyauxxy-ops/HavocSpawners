package dev.havoc.spawners.econ;

import dev.havoc.spawners.HavocSpawners;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

/** Optional Vault bridge. Everything degrades gracefully when Vault is missing. */
public final class EconomyHook {

    private Economy economy;

    public void reload(HavocSpawners plugin) {
        economy = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found - selling and upgrades are disabled.");
            return;
        }
        try {
            RegisteredServiceProvider<Economy> provider =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (provider != null) {
                economy = provider.getProvider();
                plugin.getLogger().info("Hooked into Vault economy: " + economy.getName());
            } else {
                plugin.getLogger().warning("Vault is installed but no economy provider is registered.");
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not hook Vault: " + ex.getMessage());
        }
    }

    public boolean available() {
        return economy != null;
    }

    public String currencyName() {
        if (economy == null) {
            return "";
        }
        try {
            return economy.currencyNamePlural();
        } catch (Throwable ex) {
            return "";
        }
    }

    public double balance(UUID uuid) {
        if (economy == null || uuid == null) {
            return 0.0D;
        }
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            return economy.getBalance(player);
        } catch (Throwable ex) {
            return 0.0D;
        }
    }

    public boolean deposit(UUID uuid, double amount) {
        if (economy == null || uuid == null || amount <= 0.0D) {
            return false;
        }
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            return economy.depositPlayer(player, amount).transactionSuccess();
        } catch (Throwable ex) {
            return false;
        }
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (economy == null || uuid == null || amount <= 0.0D) {
            return false;
        }
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            if (economy.getBalance(player) < amount) {
                return false;
            }
            return economy.withdrawPlayer(player, amount).transactionSuccess();
        } catch (Throwable ex) {
            return false;
        }
    }

    public String format(double amount) {
        if (economy == null) {
            return dev.havoc.spawners.util.Numbers.money(amount);
        }
        try {
            return economy.format(amount);
        } catch (Throwable ex) {
            return dev.havoc.spawners.util.Numbers.money(amount);
        }
    }
}
