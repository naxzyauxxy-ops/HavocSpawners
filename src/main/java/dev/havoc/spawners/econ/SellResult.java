package dev.havoc.spawners.econ;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

/** Outcome (or preview) of selling a spawner's storage. */
public final class SellResult {

    private final Map<Material, Long> soldByMaterial = new LinkedHashMap<>();
    private final Map<Material, Double> valueByMaterial = new LinkedHashMap<>();
    private long itemsSold;
    private double gross;
    private double tax;
    private long unsellableItems;

    public void add(Material material, long amount, double value) {
        soldByMaterial.merge(material, amount, Long::sum);
        valueByMaterial.merge(material, value, Double::sum);
        itemsSold += amount;
        gross += value;
    }

    public void addUnsellable(long amount) {
        unsellableItems += amount;
    }

    public void applyTax(double percent) {
        this.tax = gross * (percent / 100.0D);
    }

    public double gross() {
        return gross;
    }

    public double tax() {
        return tax;
    }

    public double net() {
        return Math.max(0.0D, gross - tax);
    }

    public long itemsSold() {
        return itemsSold;
    }

    public long unsellableItems() {
        return unsellableItems;
    }

    public boolean isEmpty() {
        return itemsSold <= 0L;
    }

    public Map<Material, Long> soldByMaterial() {
        return soldByMaterial;
    }

    public Map<Material, Double> valueByMaterial() {
        return valueByMaterial;
    }
}
