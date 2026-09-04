package cn.bilicraft.bccyberware.data;

import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerProfile {
    private final UUID playerId;
    private String lastName;
    private double permanentCapacity;
    private boolean initialized;
    private final LinkedHashMap<String, ItemStack> installed = new LinkedHashMap<>();

    public PlayerProfile(UUID playerId, String lastName, double permanentCapacity, boolean initialized) {
        this.playerId = playerId;
        this.lastName = lastName;
        this.permanentCapacity = permanentCapacity;
        this.initialized = initialized;
    }

    public UUID playerId() {
        return playerId;
    }

    public String lastName() {
        return lastName;
    }

    public void lastName(String lastName) {
        this.lastName = lastName;
    }

    public double permanentCapacity() {
        return permanentCapacity;
    }

    public void permanentCapacity(double permanentCapacity) {
        this.permanentCapacity = permanentCapacity;
    }

    public boolean initialized() {
        return initialized;
    }

    public void initialized(boolean initialized) {
        this.initialized = initialized;
    }

    public Map<String, ItemStack> installedView() {
        return Map.copyOf(installed);
    }

    public Optional<ItemStack> installed(String slotId) {
        ItemStack item = installed.get(slotId);
        return Optional.ofNullable(item == null ? null : item.clone());
    }

    public ItemStack installedUnsafe(String slotId) {
        return installed.get(slotId);
    }

    public void install(String slotId, ItemStack item) {
        installed.put(slotId, item.clone());
    }

    public ItemStack remove(String slotId) {
        ItemStack removed = installed.remove(slotId);
        return removed == null ? null : removed.clone();
    }

    public void loadInstalled(String slotId, ItemStack item) {
        installed.put(slotId, item);
    }
}

