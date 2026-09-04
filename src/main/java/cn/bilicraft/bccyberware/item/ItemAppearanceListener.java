package cn.bilicraft.bccyberware.item;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Lazy, permanent migration for old physical items, independent of pack acceptance. */
public final class ItemAppearanceListener implements Listener {
    private final ItemService items;

    public ItemAppearanceListener(ItemService items) {
        this.items = items;
    }

    public void normalize(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (items.normalizeAppearance(item)) {
                inventory.setItem(slot, item);
            }
        }
    }

    public void normalize(Entity entity) {
        if (entity instanceof Item dropped) {
            ItemStack stack = dropped.getItemStack();
            if (items.normalizeAppearance(stack)) {
                dropped.setItemStack(stack);
            }
        } else if (entity instanceof ItemFrame frame) {
            ItemStack stack = frame.getItem();
            if (items.normalizeAppearance(stack)) {
                frame.setItem(stack, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        normalize(event.getPlayer().getInventory());
        normalize(event.getPlayer().getEnderChest());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        normalize(event.getInventory());
        normalize(event.getPlayer().getInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        normalize(event.getItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        normalize(event.getItemDrop());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            normalize(entity);
        }
    }
}
