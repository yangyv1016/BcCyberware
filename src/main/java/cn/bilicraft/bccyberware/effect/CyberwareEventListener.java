package cn.bilicraft.bccyberware.effect;

import cn.bilicraft.bccyberware.capacity.CapacityService;
import cn.bilicraft.bccyberware.config.model.TriggerType;
import cn.bilicraft.bccyberware.data.ProfileService;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

public final class CyberwareEventListener implements Listener {
    private final JavaPlugin plugin;
    private final ProfileService profiles;
    private final CapacityService capacity;
    private final EffectEngine effects;

    public CyberwareEventListener(
            JavaPlugin plugin,
            ProfileService profiles,
            CapacityService capacity,
            EffectEngine effects
    ) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.capacity = capacity;
        this.effects = effects;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        profiles.load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        capacity.invalidate(event.getPlayer().getUniqueId());
        profiles.unload(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> effects.reconcilePassives(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !effects.isInternalDamage(player.getUniqueId())) {
            effects.trigger(player, TriggerType.DAMAGED,
                    event instanceof EntityDamageByEntityEvent byEntity ? attacker(byEntity) : null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player player = attackingPlayer(event);
        if (player != null && event.getEntity() instanceof LivingEntity target) {
            effects.trigger(player, TriggerType.ATTACK, target);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            effects.trigger(killer, TriggerType.KILL, event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        effects.trigger(event.getPlayer(), TriggerType.RIGHT_CLICK, null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking() && effects.trigger(event.getPlayer(), TriggerType.SNEAK_SWAP, null)) {
            event.setCancelled(true);
        }
    }

    private static Player attackingPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private static LivingEntity attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }
}

