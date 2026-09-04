package cn.bilicraft.bccyberware.effect;

import cn.bilicraft.bccyberware.capacity.CapacityService;
import cn.bilicraft.bccyberware.config.ConfigManager;
import cn.bilicraft.bccyberware.config.model.ActionSpec;
import cn.bilicraft.bccyberware.config.model.ConditionSpec;
import cn.bilicraft.bccyberware.config.model.ItemDefinition;
import cn.bilicraft.bccyberware.config.model.SlotDefinition;
import cn.bilicraft.bccyberware.config.model.ThresholdRule;
import cn.bilicraft.bccyberware.config.model.TriggerSpec;
import cn.bilicraft.bccyberware.config.model.TriggerType;
import cn.bilicraft.bccyberware.data.PlayerProfile;
import cn.bilicraft.bccyberware.data.ProfileService;
import cn.bilicraft.bccyberware.item.CyberwareIdentity;
import cn.bilicraft.bccyberware.item.ItemService;
import cn.bilicraft.bccyberware.text.TextService;
import cn.bilicraft.bccyberware.util.Comparison;
import cn.bilicraft.bccyberware.util.TimeParser;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class EffectEngine {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final ProfileService profiles;
    private final CapacityService capacity;
    private final ItemService items;
    private final TextService text;
    private final Map<RuntimeKey, Long> cooldowns = new HashMap<>();
    private final Map<RuntimeKey, Long> periodicRuns = new HashMap<>();
    private final Set<UUID> internalDamage = new HashSet<>();
    private BukkitTask task;

    public EffectEngine(
            JavaPlugin plugin,
            ConfigManager configs,
            ProfileService profiles,
            CapacityService capacity,
            ItemService items,
            TextService text
    ) {
        this.plugin = plugin;
        this.configs = configs;
        this.profiles = profiles;
        this.capacity = capacity;
        this.items = items;
        this.text = text;
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }
        long period = Math.max(1L, configs.current().effectEngineTick());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            removePluginAttributes(player);
        }
        cooldowns.clear();
        periodicRuns.clear();
    }

    public void reload() {
        cooldowns.clear();
        periodicRuns.clear();
        start();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (profiles.profile(player.getUniqueId()).isPresent()) {
                reconcilePassives(player);
            }
        }
    }

    public boolean trigger(Player player, TriggerType type, LivingEntity target) {
        PlayerProfile profile = profiles.profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return false;
        }
        boolean activated = false;
        for (ItemStack installed : profile.installedView().values()) {
            ItemDefinition definition = items.definition(installed).orElse(null);
            if (definition == null) {
                continue;
            }
            CyberwareIdentity identity = items.inspect(installed).orElse(null);
            if (identity == null) {
                continue;
            }
            for (TriggerSpec trigger : definition.triggers()) {
                if (trigger.type() == type && type != TriggerType.PASSIVE && type != TriggerType.PERIODIC) {
                    activated |= attempt(player, profile, target, trigger,
                            definition.id() + "/" + identity.instanceId(), false);
                }
            }
        }
        return activated;
    }

    public void triggerItem(Player player, ItemStack item, TriggerType type) {
        PlayerProfile profile = profiles.profile(player.getUniqueId()).orElse(null);
        ItemDefinition definition = items.definition(item).orElse(null);
        CyberwareIdentity identity = items.inspect(item).orElse(null);
        if (profile == null || definition == null || identity == null) {
            return;
        }
        for (TriggerSpec trigger : definition.triggers()) {
            if (trigger.type() == type) {
                attempt(player, profile, null, trigger, definition.id() + "/" + identity.instanceId(), false);
            }
        }
    }

    public void reconcilePassives(Player player) {
        removePluginAttributes(player);
        PlayerProfile profile = profiles.profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return;
        }
        for (ItemStack installed : profile.installedView().values()) {
            ItemDefinition definition = items.definition(installed).orElse(null);
            CyberwareIdentity identity = items.inspect(installed).orElse(null);
            if (definition == null || identity == null) {
                continue;
            }
            for (TriggerSpec trigger : definition.triggers()) {
                if (trigger.type() != TriggerType.PASSIVE || !conditionsPass(player, null, trigger.conditions())) {
                    continue;
                }
                int actionIndex = 0;
                for (ActionSpec action : trigger.actions()) {
                    if (action.type().equals("ATTRIBUTE")) {
                        addAttribute(player, action, identity.instanceId(), trigger.key(), actionIndex);
                    }
                    actionIndex++;
                }
            }
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }
    }

    public boolean isInternalDamage(UUID entityId) {
        return internalDamage.contains(entityId);
    }

    private void tick() {
        long now = System.nanoTime();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() + 60_000_000_000L < now);
        periodicRuns.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey().playerId) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = profiles.profile(player.getUniqueId()).orElse(null);
            if (profile == null || player.isDead()) {
                continue;
            }
            for (Map.Entry<String, ItemStack> entry : profile.installedView().entrySet()) {
                ItemDefinition definition = items.definition(entry.getValue()).orElse(null);
                CyberwareIdentity identity = items.inspect(entry.getValue()).orElse(null);
                if (definition == null || identity == null) {
                    continue;
                }
                for (TriggerSpec trigger : definition.triggers()) {
                    if (trigger.type() == TriggerType.PERIODIC) {
                        attempt(player, profile, null, trigger,
                                definition.id() + "/" + identity.instanceId(), true);
                    }
                }
            }
            for (SlotDefinition slot : configs.current().slots().values()) {
                if (profile.installedUnsafe(slot.id()) != null) {
                    continue;
                }
                for (TriggerSpec trigger : slot.emptyEffects()) {
                    if (trigger.type() == TriggerType.PERIODIC) {
                        attempt(player, profile, null, trigger, slot.id(), true);
                    }
                }
            }
            runThresholds(player, profile);
        }
    }

    private void runThresholds(Player player, PlayerProfile profile) {
        for (ThresholdRule rule : configs.current().capacity().thresholds()) {
            if (!rule.enabled()) {
                continue;
            }
            double actual = switch (rule.metric()) {
                case "INSTALLED_COUNT" -> capacity.installedCyberwareCount(profile);
                case "USED_CAPACITY" -> capacity.used(profile);
                case "USED_PERCENT" -> capacity.usedPercent(player, profile);
                default -> 0.0;
            };
            if (!rule.comparison().test(actual, rule.value())) {
                continue;
            }
            RuntimeKey key = new RuntimeKey(player.getUniqueId(), "threshold/" + rule.id());
            long now = System.nanoTime();
            if (periodicRuns.getOrDefault(key, 0L) > now) {
                continue;
            }
            periodicRuns.put(key, now + ticksToNanos(rule.intervalTicks()));
            for (ActionSpec action : rule.actions()) {
                executeAction(player, null, action);
            }
        }
    }

    private boolean attempt(
            Player player,
            PlayerProfile profile,
            LivingEntity target,
            TriggerSpec trigger,
            String context,
            boolean periodic
    ) {
        RuntimeKey key = new RuntimeKey(player.getUniqueId(), context + "/" + trigger.key());
        long now = System.nanoTime();
        if (periodic) {
            if (periodicRuns.getOrDefault(key, 0L) > now) {
                return false;
            }
            periodicRuns.put(key, now + ticksToNanos(trigger.intervalTicks()));
        }
        if (cooldowns.getOrDefault(key, 0L) > now
                || ThreadLocalRandom.current().nextDouble() > trigger.chance()
                || !conditionsPass(player, target, trigger.conditions())) {
            return false;
        }
        boolean executed = false;
        for (ActionSpec action : trigger.actions()) {
            if (!action.type().equals("ATTRIBUTE")) {
                executeAction(player, target, action);
                executed = true;
            }
        }
        if (executed && trigger.cooldownTicks() > 0) {
            cooldowns.put(key, now + ticksToNanos(trigger.cooldownTicks()));
        }
        return executed;
    }

    private boolean conditionsPass(Player player, LivingEntity target, List<ConditionSpec> conditions) {
        for (ConditionSpec condition : conditions) {
            boolean result = switch (condition.type()) {
                case "HEALTH_PERCENT" -> {
                    AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                    double maximum = maxHealth == null ? 20.0 : maxHealth.getValue();
                    Comparison comparison = parseComparison(condition.string("comparison", "GTE"));
                    yield comparison.test(player.getHealth() / maximum * 100.0, condition.number("value", 0.0));
                }
                case "SNEAKING" -> player.isSneaking() == condition.bool("value", true);
                case "PERMISSION" -> player.hasPermission(condition.string("permission", ""));
                case "WORLD" -> player.getWorld().getName().equalsIgnoreCase(condition.string("world", ""));
                case "TARGET_TYPE" -> target != null
                        && target.getType().name().equalsIgnoreCase(condition.string("entity", ""));
                default -> false;
            };
            if (!result) {
                return false;
            }
        }
        return true;
    }

    private void executeAction(Player player, LivingEntity eventTarget, ActionSpec action) {
        if (ThreadLocalRandom.current().nextDouble() > action.number("chance", 1.0)) {
            return;
        }
        LivingEntity recipient = action.string("target", "SELF").equalsIgnoreCase("TARGET")
                ? eventTarget : player;
        try {
            switch (action.type()) {
                case "POTION" -> potion(recipient, action);
                case "DAMAGE" -> damage(player, recipient, action.number("amount", 1.0));
                case "HEAL" -> heal(recipient, action.number("amount", 1.0));
                case "MESSAGE" -> player.sendMessage(text.render(action.string("text", "")));
                case "ACTION_BAR" -> player.sendActionBar(text.render(action.string("text", "")));
                case "TITLE" -> player.showTitle(Title.title(
                        text.render(action.string("title", "")),
                        text.render(action.string("subtitle", "")),
                        Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500))
                ));
                case "SOUND" -> sound(player, action);
                case "PARTICLE" -> particle(recipient == null ? player : recipient, action);
                case "COMMAND" -> command(player, eventTarget, action);
                case "DAMAGE_NEARBY" -> damageNearby(player, action);
                case "ATTRIBUTE" -> {
                    // ATTRIBUTE 由 reconcilePassives 统一维护，事件阶段不直接执行。
                }
                default -> plugin.getLogger().warning("运行时遇到不支持的动作：" + action.type());
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("执行动作 " + action.type() + " 失败：" + exception.getMessage());
        }
    }

    private void potion(LivingEntity recipient, ActionSpec action) {
        if (recipient == null) {
            return;
        }
        String name = action.string("effect", "").toUpperCase(Locale.ROOT);
        PotionEffectType effect = Registry.EFFECT.get(registryKey(name));
        if (effect == null) {
            throw new IllegalArgumentException("未知药水效果 " + name);
        }
        int duration = Math.toIntExact(Math.min(Integer.MAX_VALUE,
                TimeParser.parseTicks(action.string("duration", "1s"))));
        recipient.addPotionEffect(new PotionEffect(effect, duration, action.integer("amplifier", 0), true, true, true));
    }

    private void damage(Player source, LivingEntity recipient, double amount) {
        if (recipient == null || amount <= 0) {
            return;
        }
        internalDamage.add(recipient.getUniqueId());
        try {
            if (recipient == source) {
                recipient.damage(amount);
            } else {
                recipient.damage(amount, source);
            }
        } finally {
            internalDamage.remove(recipient.getUniqueId());
        }
    }

    private void heal(LivingEntity recipient, double amount) {
        if (recipient == null || amount <= 0) {
            return;
        }
        AttributeInstance maxHealth = recipient.getAttribute(Attribute.MAX_HEALTH);
        double maximum = maxHealth == null ? recipient.getHealth() : maxHealth.getValue();
        recipient.setHealth(Math.min(maximum, recipient.getHealth() + amount));
    }

    private void sound(Player player, ActionSpec action) {
        String name = action.string("sound", "").toUpperCase(Locale.ROOT);
        Sound sound = Registry.SOUNDS.get(registryKey(name));
        if (sound == null) {
            throw new IllegalArgumentException("未知声音 " + name);
        }
        player.playSound(player.getLocation(), sound, (float) action.number("volume", 1.0),
                (float) action.number("pitch", 1.0));
    }

    private void particle(LivingEntity recipient, ActionSpec action) {
        Particle particle = Particle.valueOf(action.string("particle", "").toUpperCase(Locale.ROOT));
        recipient.getWorld().spawnParticle(particle, recipient.getLocation().add(0, 1.0, 0),
                action.integer("count", 10), 0.45, 0.55, 0.45, 0.02);
    }

    private void command(Player player, LivingEntity target, ActionSpec action) {
        String command = action.string("command", "")
                .replace("<player>", player.getName())
                .replace("<target>", target == null ? player.getName() : target.getName());
        if (action.string("executor", "CONSOLE").equalsIgnoreCase("PLAYER")) {
            player.performCommand(stripSlash(command));
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(command));
        }
    }

    private void damageNearby(Player player, ActionSpec action) {
        double radius = Math.max(0.0, action.number("radius", 3.0));
        double amount = Math.max(0.0, action.number("amount", 1.0));
        String filter = action.string("entity-filter", "HOSTILE").toUpperCase(Locale.ROOT);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living == player) {
                continue;
            }
            if (filter.equals("HOSTILE") && !(living instanceof Enemy)) {
                continue;
            }
            if (filter.equals("NON_PLAYER") && living instanceof Player) {
                continue;
            }
            damage(player, living, amount);
        }
    }

    private void addAttribute(Player player, ActionSpec action, UUID instanceId, String triggerKey, int actionIndex) {
        Attribute attribute;
        AttributeModifier.Operation operation;
        try {
            String name = action.string("attribute", "").toUpperCase(Locale.ROOT);
            attribute = Registry.ATTRIBUTE.get(registryKey(name));
            if (attribute == null) {
                throw new IllegalArgumentException("未知属性 " + name);
            }
            operation = AttributeModifier.Operation.valueOf(action.string("operation", "ADD_NUMBER").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("被动属性配置无效：" + exception.getMessage());
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            plugin.getLogger().warning("玩家不支持属性 " + attribute);
            return;
        }
        String raw = instanceId + "/" + triggerKey + "/" + actionIndex;
        String keyPart = "passive_" + Integer.toUnsignedString(raw.hashCode(), 36);
        AttributeModifier modifier = new AttributeModifier(
                new NamespacedKey(plugin, keyPart),
                action.number("amount", 0.0),
                operation,
                EquipmentSlotGroup.ANY
        );
        instance.addModifier(modifier);
    }

    private void removePluginAttributes(Player player) {
        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            List<AttributeModifier> removal = new ArrayList<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (modifier.getKey().getNamespace().equalsIgnoreCase(plugin.getName())) {
                    removal.add(modifier);
                }
            }
            removal.forEach(instance::removeModifier);
        }
    }

    private static Comparison parseComparison(String value) {
        return Comparison.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private static long ticksToNanos(long ticks) {
        return Math.max(0L, ticks) * 50_000_000L;
    }

    private record RuntimeKey(UUID playerId, String key) {
    }

    private static NamespacedKey registryKey(String configuredName) {
        String normalized = configuredName.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(normalized);
        if (key == null) {
            throw new IllegalArgumentException("无效的注册表 ID：" + configuredName);
        }
        return key;
    }
}
