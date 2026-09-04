package cn.bilicraft.bccyberware.api;

import cn.bilicraft.bccyberware.capacity.CapacityService;
import cn.bilicraft.bccyberware.config.ConfigManager;
import cn.bilicraft.bccyberware.data.PlayerProfile;
import cn.bilicraft.bccyberware.data.ProfileService;
import cn.bilicraft.bccyberware.gui.MenuService;
import cn.bilicraft.bccyberware.item.CyberwareIdentity;
import cn.bilicraft.bccyberware.item.ItemService;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BcCyberwareApiImpl implements BcCyberwareApi {
    private final ConfigManager configs;
    private final ProfileService profiles;
    private final ItemService items;
    private final CapacityService capacity;
    private final MenuService menus;

    public BcCyberwareApiImpl(
            ConfigManager configs,
            ProfileService profiles,
            ItemService items,
            CapacityService capacity,
            MenuService menus
    ) {
        this.configs = configs;
        this.profiles = profiles;
        this.items = items;
        this.capacity = capacity;
        this.menus = menus;
    }

    @Override
    public boolean openMenu(Player player) {
        if (profiles.profile(player.getUniqueId()).isEmpty()) {
            return false;
        }
        menus.openMain(player);
        return true;
    }

    @Override
    public Optional<ItemStack> createPart(String definitionId, OfflinePlayer originalOwner) {
        if (!configs.current().items().containsKey(definitionId)) {
            return Optional.empty();
        }
        return Optional.of(items.create(definitionId, originalOwner));
    }

    @Override
    public Optional<CyberwareIdentity> inspect(ItemStack item) {
        return items.inspect(item);
    }

    @Override
    public Optional<Map<String, ItemStack>> installedParts(UUID playerId) {
        return profiles.profile(playerId).map(profile -> {
            Map<String, ItemStack> copy = new LinkedHashMap<>();
            profile.installedView().forEach((slot, item) -> copy.put(slot, item.clone()));
            return Map.copyOf(copy);
        });
    }

    @Override
    public Optional<CapacityView> capacity(Player player) {
        return profiles.profile(player.getUniqueId()).map(profile -> new CapacityView(
                configs.current().capacity().enabled(),
                capacity.used(profile),
                capacity.maximum(player, profile),
                capacity.usedPercent(player, profile),
                capacity.installedCyberwareCount(profile)
        ));
    }

    @Override
    public boolean setPermanentCapacity(UUID playerId, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("永久容量必须是有限数值");
        }
        PlayerProfile profile = profiles.profile(playerId).orElse(null);
        if (profile == null) {
            return false;
        }
        profile.permanentCapacity(value);
        capacity.invalidate(playerId);
        profiles.requestSave(profile);
        return true;
    }

    @Override
    public boolean addPermanentCapacity(UUID playerId, double delta) {
        if (!Double.isFinite(delta)) {
            throw new IllegalArgumentException("永久容量变化值必须是有限数值");
        }
        PlayerProfile profile = profiles.profile(playerId).orElse(null);
        return profile != null && setPermanentCapacity(playerId, profile.permanentCapacity() + delta);
    }
}
