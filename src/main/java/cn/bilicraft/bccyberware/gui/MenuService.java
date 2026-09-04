package cn.bilicraft.bccyberware.gui;

import cn.bilicraft.bccyberware.capacity.CapacityService;
import cn.bilicraft.bccyberware.config.ConfigManager;
import cn.bilicraft.bccyberware.config.model.ItemDefinition;
import cn.bilicraft.bccyberware.config.model.SlotDefinition;
import cn.bilicraft.bccyberware.config.model.TriggerType;
import cn.bilicraft.bccyberware.data.PlayerProfile;
import cn.bilicraft.bccyberware.data.ProfileService;
import cn.bilicraft.bccyberware.effect.EffectEngine;
import cn.bilicraft.bccyberware.item.ItemService;
import cn.bilicraft.bccyberware.text.TextService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MenuService implements Listener {
    private static final int PREVIOUS_PAGE = 45;
    private static final int BACK = 49;
    private static final int NEXT_PAGE = 53;
    private static final int REMOVE_CANCEL = 11;
    private static final int REMOVE_ITEM = 13;
    private static final int REMOVE_CONFIRM = 15;

    private final ConfigManager configs;
    private final ProfileService profiles;
    private final ItemService items;
    private final CapacityService capacity;
    private final EffectEngine effects;
    private final TextService text;

    public MenuService(
            ConfigManager configs,
            ProfileService profiles,
            ItemService items,
            CapacityService capacity,
            EffectEngine effects,
            TextService text
    ) {
        this.configs = configs;
        this.profiles = profiles;
        this.items = items;
        this.capacity = capacity;
        this.effects = effects;
        this.text = text;
    }

    public void openMain(Player player) {
        PlayerProfile profile = profiles.profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            text.send(player, "profile-loading");
            return;
        }
        int size = configs.current().gui().rows() * 9;
        MenuHolder holder = new MenuHolder(MenuHolder.Type.MAIN, player.getUniqueId(), "", 0);
        Component title = text.render(configs.current().gui().title(), text.placeholders(
                "player", player.getName(),
                "used", capacity.used(profile),
                "total", capacity.maximum(player, profile)
        ));
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.inventory(inventory);
        fill(inventory);

        for (SlotDefinition slot : configs.current().slots().values()) {
            ItemStack installed = profile.installedUnsafe(slot.id());
            inventory.setItem(slot.guiSlot(), installed == null ? emptySlot(slot) : installedSlot(slot, installed));
        }
        int infoSlot = findFreeFromEnd(size);
        if (infoSlot >= 0) {
            inventory.setItem(infoSlot, capacityInfo(player, profile));
        }
        player.openInventory(inventory);
    }

    public void closeAllMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof MenuHolder) {
                player.closeInventory();
            }
        }
    }

    private void openSelector(Player player, SlotDefinition slot, int requestedPage) {
        PlayerProfile profile = profiles.profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            text.send(player, "profile-loading");
            return;
        }
        List<Integer> candidates = compatibleInventorySlots(player, slot);
        if (candidates.isEmpty()) {
            text.send(player, "no-compatible-items", text.placeholders("slot", plain(slot.displayName())));
            return;
        }
        int pageSize = configs.current().gui().selectorPageSize();
        int pageCount = Math.max(1, (candidates.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        MenuHolder holder = new MenuHolder(MenuHolder.Type.SELECTOR, player.getUniqueId(), slot.id(), page);
        Component title = text.render(configs.current().gui().selectorTitle(), text.placeholders(
                "slot", plain(slot.displayName()), "page", page + 1, "pages", pageCount));
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.inventory(inventory);
        fill(inventory);

        int start = page * pageSize;
        int end = Math.min(candidates.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            int displaySlot = index - start;
            int playerSlot = candidates.get(index);
            holder.candidateInventorySlots().put(displaySlot, playerSlot);
            inventory.setItem(displaySlot, items.displayCopy(player.getInventory().getItem(playerSlot)));
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS_PAGE, button(Material.ARROW, "<yellow>上一页"));
        }
        inventory.setItem(BACK, button(Material.BARRIER, "<red>返回身体界面"));
        if (page + 1 < pageCount) {
            inventory.setItem(NEXT_PAGE, button(Material.ARROW, "<yellow>下一页"));
        }
        player.openInventory(inventory);
    }

    private void openRemoval(Player player, SlotDefinition slot) {
        PlayerProfile profile = profiles.profile(player.getUniqueId()).orElse(null);
        if (profile == null || profile.installedUnsafe(slot.id()) == null) {
            openMain(player);
            return;
        }
        MenuHolder holder = new MenuHolder(MenuHolder.Type.REMOVE_CONFIRM, player.getUniqueId(), slot.id(), 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, text.render("<dark_red>确认拆卸｜" + slot.displayName()));
        holder.inventory(inventory);
        fill(inventory);
        inventory.setItem(REMOVE_CANCEL, button(Material.LIME_DYE, "<green>取消并返回"));
        inventory.setItem(REMOVE_ITEM, items.displayCopy(profile.installedUnsafe(slot.id())));
        inventory.setItem(REMOVE_CONFIRM, button(Material.RED_DYE, "<red>确认拆卸并承受空部位效果",
                "<gray>该操作会把当前部件返还背包。",
                "<gray>此槽位共有 <white>" + slot.emptyEffects().size() + " <gray>条空缺规则。"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId().equals(player.getUniqueId())) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        switch (holder.type()) {
            case MAIN -> clickMain(player, event);
            case SELECTOR -> clickSelector(player, holder, event.getRawSlot());
            case REMOVE_CONFIRM -> clickRemoval(player, holder, event.getRawSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder)) {
            return;
        }
        // 受控菜单打开期间不允许任何拖拽，包括仅在玩家背包内拖动。
        event.setCancelled(true);
    }

    private void clickMain(Player player, InventoryClickEvent event) {
        SlotDefinition slot = slotAt(event.getRawSlot()).orElse(null);
        if (slot == null || !player.hasPermission("bccyberware.install")) {
            if (slot != null) {
                text.send(player, "no-permission");
            }
            return;
        }
        PlayerProfile profile = profiles.profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return;
        }
        if (event.isRightClick() && profile.installedUnsafe(slot.id()) != null) {
            openRemoval(player, slot);
        } else {
            openSelector(player, slot, 0);
        }
    }

    private void clickSelector(Player player, MenuHolder holder, int clickedSlot) {
        SlotDefinition slot = configs.current().slots().get(holder.slotId());
        if (slot == null) {
            openMain(player);
            return;
        }
        if (clickedSlot == BACK) {
            openMain(player);
            return;
        }
        if (clickedSlot == PREVIOUS_PAGE && holder.page() > 0) {
            openSelector(player, slot, holder.page() - 1);
            return;
        }
        if (clickedSlot == NEXT_PAGE) {
            openSelector(player, slot, holder.page() + 1);
            return;
        }
        Integer playerSlot = holder.candidateInventorySlots().get(clickedSlot);
        if (playerSlot == null) {
            return;
        }
        replaceFromInventory(player, slot, playerSlot);
    }

    private void clickRemoval(Player player, MenuHolder holder, int clickedSlot) {
        SlotDefinition slot = configs.current().slots().get(holder.slotId());
        if (slot == null || clickedSlot == REMOVE_CANCEL) {
            openMain(player);
            return;
        }
        if (clickedSlot != REMOVE_CONFIRM) {
            return;
        }
        PlayerProfile profile = profiles.profile(player.getUniqueId()).orElse(null);
        if (profile == null || profile.installedUnsafe(slot.id()) == null) {
            openMain(player);
            return;
        }
        int free = player.getInventory().firstEmpty();
        if (free < 0) {
            text.send(player, "inventory-full");
            return;
        }
        ItemStack removed = profile.remove(slot.id());
        player.getInventory().setItem(free, removed);
        effects.triggerItem(player, removed, TriggerType.UNEQUIP);
        effects.reconcilePassives(player);
        profiles.requestSave(profile);
        ItemDefinition definition = items.definition(removed).orElse(null);
        text.send(player, "removed", text.placeholders("item",
                definition == null ? "未知部件" : plain(definition.displayName())));
        openMain(player);
    }

    private void replaceFromInventory(Player player, SlotDefinition slot, int playerSlot) {
        PlayerProfile profile = profiles.profile(player.getUniqueId()).orElse(null);
        ItemStack source = player.getInventory().getItem(playerSlot);
        ItemDefinition candidate = items.definition(source).orElse(null);
        if (profile == null || source == null || candidate == null || !candidate.slotType().equals(slot.type())) {
            openSelector(player, slot, 0);
            return;
        }
        if (!capacity.canReplace(player, profile, slot.id(), candidate)) {
            double usedAfter = capacity.used(profile) + candidate.capacityCost();
            ItemStack current = profile.installedUnsafe(slot.id());
            if (current != null) {
                usedAfter -= items.definition(current).map(ItemDefinition::capacityCost).orElse(0.0);
            }
            text.send(player, "capacity-insufficient", text.placeholders(
                    "used", usedAfter, "total", capacity.maximum(player, profile)));
            return;
        }

        ItemStack installing = source.clone();
        installing.setAmount(1);
        ItemStack old = profile.installedUnsafe(slot.id());
        if (source.getAmount() == 1) {
            player.getInventory().setItem(playerSlot, old == null ? null : old.clone());
        } else {
            source.setAmount(source.getAmount() - 1);
            if (old != null && !player.getInventory().addItem(old.clone()).isEmpty()) {
                source.setAmount(source.getAmount() + 1);
                text.send(player, "inventory-full");
                return;
            }
        }
        profile.install(slot.id(), installing);
        if (old != null) {
            effects.triggerItem(player, old, TriggerType.UNEQUIP);
        }
        effects.triggerItem(player, installing, TriggerType.EQUIP);
        effects.reconcilePassives(player);
        profiles.requestSave(profile);
        text.send(player, old == null ? "installed" : "replaced", text.placeholders(
                "item", plain(candidate.displayName()),
                "old", old == null ? "" : items.definition(old).map(ItemDefinition::displayName).map(this::plain).orElse("未知部件"),
                "slot", plain(slot.displayName())
        ));
        openMain(player);
    }

    private List<Integer> compatibleInventorySlots(Player player, SlotDefinition slot) {
        List<Integer> result = new ArrayList<>();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int index = 0; index < storage.length; index++) {
            ItemDefinition definition = items.definition(storage[index]).orElse(null);
            if (definition != null && definition.slotType().equals(slot.type())) {
                result.add(index);
            }
        }
        result.sort(Comparator.comparing(index -> items.definition(storage[index]).map(ItemDefinition::id).orElse("")));
        return result;
    }

    private Optional<SlotDefinition> slotAt(int guiSlot) {
        return configs.current().slots().values().stream().filter(slot -> slot.guiSlot() == guiSlot).findFirst();
    }

    private ItemStack installedSlot(SlotDefinition slot, ItemStack installed) {
        ItemStack copy = items.displayCopy(installed);
        ItemMeta meta = copy.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(text.render("<aqua>部位：" + slot.displayName()));
        lore.add(text.render("<yellow>左键：选择替换部件"));
        lore.add(text.render("<red>右键：拆卸并留下空部位"));
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    private ItemStack emptySlot(SlotDefinition slot) {
        return button(Material.BARRIER, "<red>空缺｜" + slot.displayName(),
                "<gray>左键选择背包中的兼容部件。",
                "<dark_red>当前空缺规则：" + slot.emptyEffects().size() + " 条");
    }

    private ItemStack capacityInfo(Player player, PlayerProfile profile) {
        if (!configs.current().capacity().enabled()) {
            return button(Material.GRAY_DYE, "<gray>义体容量已关闭");
        }
        return button(Material.NETHER_STAR, "<aqua>义体容量",
                "<gray>已使用：<white>" + format(capacity.used(profile)),
                "<gray>总容量：<white>" + format(capacity.maximum(player, profile)),
                "<gray>负载率：<white>" + format(capacity.usedPercent(player, profile)) + "%");
    }

    private void fill(Inventory inventory) {
        ItemStack filler = button(configs.current().gui().fillerMaterial(), configs.current().gui().fillerName());
        for (int index = 0; index < inventory.getSize(); index++) {
            inventory.setItem(index, filler);
        }
    }

    private int findFreeFromEnd(int size) {
        Map<Integer, Boolean> occupied = new HashMap<>();
        configs.current().slots().values().forEach(slot -> occupied.put(slot.guiSlot(), true));
        for (int index = size - 1; index >= 0; index--) {
            if (!occupied.containsKey(index)) {
                return index;
            }
        }
        return -1;
    }

    private ItemStack button(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text.render(name));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(text.render(line));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String plain(String miniMessage) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(text.render(miniMessage));
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
