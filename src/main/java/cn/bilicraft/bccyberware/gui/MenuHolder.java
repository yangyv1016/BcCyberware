package cn.bilicraft.bccyberware.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class MenuHolder implements InventoryHolder {
    public enum Type {
        MAIN,
        SELECTOR,
        REMOVE_CONFIRM
    }

    private final Type type;
    private final UUID playerId;
    private final String slotId;
    private final int page;
    private final Map<Integer, Integer> candidateInventorySlots = new LinkedHashMap<>();
    private Inventory inventory;

    public MenuHolder(Type type, UUID playerId, String slotId, int page) {
        this.type = type;
        this.playerId = playerId;
        this.slotId = slotId;
        this.page = page;
    }

    public Type type() {
        return type;
    }

    public UUID playerId() {
        return playerId;
    }

    public String slotId() {
        return slotId;
    }

    public int page() {
        return page;
    }

    public Map<Integer, Integer> candidateInventorySlots() {
        return candidateInventorySlots;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

