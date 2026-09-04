package cn.bilicraft.bccyberware.item;

import cn.bilicraft.bccyberware.config.ConfigManager;
import cn.bilicraft.bccyberware.config.model.ItemDefinition;
import cn.bilicraft.bccyberware.text.TextService;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ItemService {
    private final ConfigManager configs;
    private final TextService text;
    private final NamespacedKey definitionKey;
    private final NamespacedKey instanceKey;
    private final NamespacedKey ownerUuidKey;
    private final NamespacedKey ownerNameKey;
    private final NamespacedKey schemaKey;

    public ItemService(Plugin plugin, ConfigManager configs, TextService text) {
        this.configs = configs;
        this.text = text;
        this.definitionKey = new NamespacedKey(plugin, "definition_id");
        this.instanceKey = new NamespacedKey(plugin, "instance_uuid");
        this.ownerUuidKey = new NamespacedKey(plugin, "original_owner_uuid");
        this.ownerNameKey = new NamespacedKey(plugin, "original_owner_name");
        this.schemaKey = new NamespacedKey(plugin, "item_schema");
    }

    public ItemStack create(String definitionId, OfflinePlayer originalOwner) {
        ItemDefinition definition = configs.current().items().get(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("未知部件 ID：" + definitionId);
        }
        if (definition.originalOrgan() && originalOwner == null) {
            throw new IllegalArgumentException("原生器官必须指定原始主人");
        }

        ItemStack item = new ItemStack(definition.material(), 1);
        ItemMeta meta = item.getItemMeta();
        UUID instanceId = UUID.randomUUID();
        String ownerName = originalOwner == null
                ? ""
                : originalOwner.getName() == null ? originalOwner.getUniqueId().toString() : originalOwner.getName();
        String ownerUuid = originalOwner == null ? "" : originalOwner.getUniqueId().toString();
        Map<String, Object> placeholders = text.placeholders(
                "owner", ownerName,
                "owner_uuid", ownerUuid,
                "capacity", definition.capacityCost(),
                "slot", definition.slotType()
        );

        meta.displayName(text.render(definition.displayName(), placeholders));
        List<Component> lore = new ArrayList<>();
        for (String line : definition.lore()) {
            lore.add(text.render(line, placeholders));
        }
        meta.lore(lore);
        meta.setMaxStackSize(1);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (!definition.itemModel().isEmpty()) {
            NamespacedKey model = NamespacedKey.fromString(definition.itemModel());
            if (model != null) {
                meta.setItemModel(model);
            }
        }
        if (definition.customModelData() != null && definition.customModelData() > 0) {
            var customModelData = meta.getCustomModelDataComponent();
            customModelData.setFloats(List.of(definition.customModelData().floatValue()));
            meta.setCustomModelDataComponent(customModelData);
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(definitionKey, PersistentDataType.STRING, definition.id());
        pdc.set(instanceKey, PersistentDataType.STRING, instanceId.toString());
        pdc.set(schemaKey, PersistentDataType.INTEGER, configs.current().schemaVersion());
        if (originalOwner != null) {
            pdc.set(ownerUuidKey, PersistentDataType.STRING, ownerUuid);
            pdc.set(ownerNameKey, PersistentDataType.STRING, ownerName);
        }
        item.setItemMeta(meta);
        return item;
    }

    public Optional<CyberwareIdentity> inspect(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String definitionId = pdc.get(definitionKey, PersistentDataType.STRING);
        String instance = pdc.get(instanceKey, PersistentDataType.STRING);
        if (definitionId == null || instance == null) {
            return Optional.empty();
        }
        try {
            UUID owner = parseUuid(pdc.get(ownerUuidKey, PersistentDataType.STRING));
            String ownerName = pdc.get(ownerNameKey, PersistentDataType.STRING);
            return Optional.of(new CyberwareIdentity(definitionId, UUID.fromString(instance), owner, ownerName));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Optional<ItemDefinition> definition(ItemStack item) {
        return inspect(item).map(CyberwareIdentity::definitionId).map(configs.current().items()::get);
    }

    public boolean sameInstance(ItemStack first, ItemStack second) {
        Optional<CyberwareIdentity> left = inspect(first);
        Optional<CyberwareIdentity> right = inspect(second);
        return left.isPresent() && right.isPresent() && left.get().instanceId().equals(right.get().instanceId());
    }

    public ItemStack displayCopy(ItemStack source) {
        ItemStack copy = source.clone();
        copy.setAmount(1);
        return copy;
    }

    private static UUID parseUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
