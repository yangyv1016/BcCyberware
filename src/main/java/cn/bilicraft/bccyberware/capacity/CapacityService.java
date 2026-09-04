package cn.bilicraft.bccyberware.capacity;

import cn.bilicraft.bccyberware.config.ConfigManager;
import cn.bilicraft.bccyberware.config.model.ItemDefinition;
import cn.bilicraft.bccyberware.config.model.ValueSourceSpec;
import cn.bilicraft.bccyberware.data.PlayerProfile;
import cn.bilicraft.bccyberware.item.ItemService;
import cn.bilicraft.bccyberware.util.NumericFormula;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CapacityService {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final ItemService items;
    private final Map<CacheKey, CachedValue> cache = new ConcurrentHashMap<>();
    private final Set<String> warnedFailures = ConcurrentHashMap.newKeySet();

    public CapacityService(JavaPlugin plugin, ConfigManager configs, ItemService items) {
        this.plugin = plugin;
        this.configs = configs;
        this.items = items;
    }

    public double maximum(Player player, PlayerProfile profile) {
        double result = configs.current().capacity().base();
        if (configs.current().capacity().includePlayerPermanent()) {
            result += profile.permanentCapacity();
        }
        for (ValueSourceSpec source : configs.current().capacity().sources()) {
            if (!source.enabled()) {
                continue;
            }
            double contribution = cachedValue(player, profile, source);
            result = switch (source.operation()) {
                case "ADD" -> result + contribution;
                case "SUBTRACT" -> result - contribution;
                case "SET" -> contribution;
                case "MIN" -> Math.min(result, contribution);
                case "MAX" -> Math.max(result, contribution);
                default -> result;
            };
        }
        return Math.max(0.0, result);
    }

    public double used(PlayerProfile profile) {
        double result = 0.0;
        for (var item : profile.installedView().values()) {
            ItemDefinition definition = items.definition(item).orElse(null);
            if (definition != null) {
                result += definition.capacityCost();
            }
        }
        return result;
    }

    public int installedCyberwareCount(PlayerProfile profile) {
        int result = 0;
        for (var item : profile.installedView().values()) {
            ItemDefinition definition = items.definition(item).orElse(null);
            if (definition != null && !definition.originalOrgan()) {
                result++;
            }
        }
        return result;
    }

    public boolean canReplace(Player player, PlayerProfile profile, String slotId, ItemDefinition candidate) {
        if (!configs.current().capacity().enabled()) {
            return true;
        }
        double after = used(profile) + candidate.capacityCost();
        var current = profile.installedUnsafe(slotId);
        if (current != null) {
            ItemDefinition old = items.definition(current).orElse(null);
            if (old != null) {
                after -= old.capacityCost();
            }
        }
        return after <= maximum(player, profile) + 0.000_001;
    }

    public double usedPercent(Player player, PlayerProfile profile) {
        double maximum = maximum(player, profile);
        double used = used(profile);
        if (maximum <= 0.0) {
            return used <= 0.0 ? 0.0 : 100.0;
        }
        return used / maximum * 100.0;
    }

    public void invalidate(UUID playerId) {
        cache.keySet().removeIf(key -> key.playerId.equals(playerId));
    }

    public void invalidateAll() {
        cache.clear();
        warnedFailures.clear();
    }

    private double cachedValue(Player player, PlayerProfile profile, ValueSourceSpec source) {
        CacheKey key = new CacheKey(player.getUniqueId(), source.id());
        long now = System.nanoTime();
        CachedValue existing = cache.get(key);
        if (existing != null && existing.expiresAtNanos > now) {
            return existing.value;
        }
        double value;
        try {
            double raw = readRaw(player, profile, source);
            value = NumericFormula.evaluate(source.formula(), raw);
            value = Math.max(source.min(), Math.min(source.max(), value));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            value = source.fallback();
            warnOnce(source, exception);
        }
        long lifetime = Math.max(1L, source.refreshTicks()) * 50_000_000L;
        cache.put(key, new CachedValue(value, now + lifetime));
        return value;
    }

    private double readRaw(Player player, PlayerProfile profile, ValueSourceSpec source)
            throws ReflectiveOperationException {
        return switch (source.type()) {
            case "FIXED" -> number(source.values(), "value", 0.0);
            case "PLAYER_DATA" -> profile.permanentCapacity();
            case "PERMISSION" -> permissionValue(player, source.values().get("permissions"));
            case "MCMO_POWER_LEVEL" -> mcMmoPowerLevel(player);
            case "MCMO_SKILL_LEVEL" -> mcMmoSkillLevel(player, string(source.values(), "skill", "MINING"));
            case "PLACEHOLDER" -> placeholderValue(player, string(source.values(), "placeholder", ""));
            case "SCOREBOARD" -> scoreboardValue(player, string(source.values(), "objective", ""), source.fallback());
            default -> throw new IllegalArgumentException("不支持的数值源：" + source.type());
        };
    }

    private double permissionValue(Player player, Object rawPermissions) {
        if (!(rawPermissions instanceof Map<?, ?> permissions)) {
            return 0.0;
        }
        double best = 0.0;
        for (Map.Entry<?, ?> entry : permissions.entrySet()) {
            if (player.hasPermission(String.valueOf(entry.getKey()))) {
                best = Math.max(best, Double.parseDouble(String.valueOf(entry.getValue())));
            }
        }
        return best;
    }

    private double mcMmoPowerLevel(Player player) throws ReflectiveOperationException {
        requirePlugin("mcMMO");
        Class<?> api = Class.forName("com.gmail.nossr50.api.ExperienceAPI");
        Method method = api.getMethod("getPowerLevel", Player.class);
        return ((Number) method.invoke(null, player)).doubleValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private double mcMmoSkillLevel(Player player, String skill) throws ReflectiveOperationException {
        requirePlugin("mcMMO");
        Class<?> api = Class.forName("com.gmail.nossr50.api.ExperienceAPI");
        Class<? extends Enum> skillType = (Class<? extends Enum>) Class.forName(
                "com.gmail.nossr50.datatypes.skills.PrimarySkillType");
        Enum<?> enumValue = Enum.valueOf(skillType, skill.toUpperCase(Locale.ROOT));
        Method method = api.getMethod("getLevel", Player.class, skillType);
        return ((Number) method.invoke(null, player, enumValue)).doubleValue();
    }

    private double placeholderValue(Player player, String placeholder) throws ReflectiveOperationException {
        requirePlugin("PlaceholderAPI");
        Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
        Method method = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        String parsed = String.valueOf(method.invoke(null, player, placeholder));
        if (parsed.equals(placeholder)) {
            throw new IllegalArgumentException("占位符未被解析，可能缺少对应 Expansion：" + placeholder);
        }
        return Double.parseDouble(parsed.replace(",", "").trim());
    }

    private double scoreboardValue(Player player, String objectiveName, double fallback) {
        Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(objectiveName);
        if (objective == null) {
            return fallback;
        }
        Score score = objective.getScore(player.getName());
        return score.isScoreSet() ? score.getScore() : fallback;
    }

    private void requirePlugin(String name) {
        if (!Bukkit.getPluginManager().isPluginEnabled(name)) {
            throw new IllegalStateException("插件未安装或未启用：" + name);
        }
    }

    private void warnOnce(ValueSourceSpec source, Exception failure) {
        if (!configs.current().warnOnSourceFailure() || !warnedFailures.add(source.id())) {
            return;
        }
        plugin.getLogger().warning("外部数值源 " + source.id() + " 暂时失效，已使用 fallback="
                + source.fallback() + "。原因：" + failure.getMessage());
    }

    private static String string(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static double number(Map<String, Object> values, String key, double fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    private record CacheKey(UUID playerId, String sourceId) {
    }

    private record CachedValue(double value, long expiresAtNanos) {
    }
}

