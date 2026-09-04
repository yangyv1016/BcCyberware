package cn.bilicraft.bccyberware.data;

import cn.bilicraft.bccyberware.config.ConfigManager;
import cn.bilicraft.bccyberware.config.model.SlotDefinition;
import cn.bilicraft.bccyberware.item.ItemService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ProfileService {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final ItemService items;
    private final SqliteRepository repository;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> pendingSaves = new ConcurrentHashMap<>();
    private final List<Consumer<Player>> loadListeners = new ArrayList<>();

    public ProfileService(JavaPlugin plugin, ConfigManager configs, ItemService items, SqliteRepository repository) {
        this.plugin = plugin;
        this.configs = configs;
        this.items = items;
        this.repository = repository;
    }

    public Optional<PlayerProfile> profile(UUID playerId) {
        return Optional.ofNullable(profiles.get(playerId));
    }

    public void onLoaded(Consumer<Player> listener) {
        loadListeners.add(listener);
    }

    public void load(Player player) {
        UUID playerId = player.getUniqueId();
        if (!loading.add(playerId)) {
            return;
        }
        repository.load(playerId, player.getName()).whenComplete((stored, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    loading.remove(playerId);
                    if (failure != null) {
                        plugin.getLogger().severe("读取 " + player.getName() + " 的义体数据失败：" + failure.getMessage());
                        return;
                    }
                    PlayerProfile profile = materialize(stored, player);
                    if (!player.isOnline()) {
                        // 数据库回调到达前玩家可能已离线；不在内存中留下幽灵档案。
                        if (!stored.initialized()) {
                            saveSnapshot(profile);
                        }
                        return;
                    }
                    profiles.put(playerId, profile);
                    if (!stored.initialized()) {
                        requestSave(profile);
                    }
                    if (player.isOnline()) {
                        for (Consumer<Player> listener : loadListeners) {
                            listener.accept(player);
                        }
                    }
                }));
    }

    private PlayerProfile materialize(StoredProfile stored, Player player) {
        PlayerProfile profile = new PlayerProfile(
                stored.playerId(),
                player.getName(),
                stored.permanentCapacity(),
                stored.initialized()
        );
        for (Map.Entry<String, byte[]> entry : stored.installedItems().entrySet()) {
            try {
                ItemStack installed = ItemStack.deserializeBytes(entry.getValue());
                items.normalizeAppearance(installed);
                profile.loadInstalled(entry.getKey(), installed);
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("无法反序列化玩家 " + player.getName() + " 槽位 " + entry.getKey()
                        + " 的部件；原始数据库记录未被删除：" + exception.getMessage());
            }
        }
        if (!profile.initialized()) {
            if (configs.current().createDefaultOrgans()) {
                for (SlotDefinition slot : configs.current().slots().values()) {
                    if (!slot.defaultOrganId().isEmpty() && profile.installedUnsafe(slot.id()) == null) {
                        profile.install(slot.id(), items.create(slot.defaultOrganId(), player));
                    }
                }
            }
            profile.initialized(true);
        }
        return profile;
    }

    public void requestSave(PlayerProfile profile) {
        BukkitTask old = pendingSaves.remove(profile.playerId());
        if (old != null) {
            old.cancel();
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingSaves.remove(profile.playerId());
            saveSnapshot(profile);
        }, configs.current().saveDebounceTicks());
        pendingSaves.put(profile.playerId(), task);
    }

    public CompletableFuture<Void> flush(PlayerProfile profile) {
        BukkitTask old = pendingSaves.remove(profile.playerId());
        if (old != null) {
            old.cancel();
        }
        return saveSnapshot(profile);
    }

    private CompletableFuture<Void> saveSnapshot(PlayerProfile profile) {
        LinkedHashMap<String, byte[]> serialized = new LinkedHashMap<>();
        for (Map.Entry<String, ItemStack> entry : profile.installedView().entrySet()) {
            serialized.put(entry.getKey(), entry.getValue().serializeAsBytes());
        }
        return repository.save(
                profile.playerId(),
                profile.lastName(),
                profile.permanentCapacity(),
                profile.initialized(),
                serialized
        ).exceptionally(failure -> {
            plugin.getLogger().severe("保存玩家 " + profile.lastName() + " 的义体数据失败：" + failure.getMessage());
            return null;
        });
    }

    public void unload(Player player) {
        PlayerProfile profile = profiles.remove(player.getUniqueId());
        if (profile != null) {
            profile.lastName(player.getName());
            flush(profile);
        }
    }

    public void shutdown() {
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (PlayerProfile profile : profiles.values()) {
            saves.add(flush(profile));
        }
        CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).join();
        profiles.clear();
        repository.close();
    }
}
