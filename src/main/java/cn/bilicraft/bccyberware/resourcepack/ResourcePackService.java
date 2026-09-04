package cn.bilicraft.bccyberware.resourcepack;

import cn.bilicraft.bccyberware.config.ConfigManager;
import cn.bilicraft.bccyberware.config.model.ResourcePackSpec;
import cn.bilicraft.bccyberware.text.TextService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ResourcePackService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final TextService text;

    public ResourcePackService(JavaPlugin plugin, ConfigManager configs, TextService text) {
        this.plugin = plugin;
        this.configs = configs;
        this.text = text;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!configs.current().resourcePacksEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                send(event.getPlayer());
            }
        }, configs.current().resourcePackDelayTicks());
    }

    public void send(Player player) {
        if (!configs.current().resourcePacksEnabled()) {
            return;
        }
        for (ResourcePackSpec pack : configs.current().resourcePacks()) {
            String prompt = PlainTextComponentSerializer.plainText().serialize(text.render(pack.prompt()));
            player.addResourcePack(pack.uuid(), pack.url(), pack.sha1(), prompt, pack.required());
        }
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        String status = event.getStatus().name();
        switch (status) {
            case "SUCCESSFULLY_LOADED" -> text.send(event.getPlayer(), "resource-pack-loaded");
            case "DECLINED" -> text.send(event.getPlayer(), "resource-pack-declined");
            case "FAILED_DOWNLOAD", "INVALID_URL", "FAILED_RELOAD", "DISCARDED" -> {
                text.send(event.getPlayer(), "resource-pack-failed");
                plugin.getLogger().warning("玩家 " + event.getPlayer().getName() + " 的资源包状态为 " + status);
            }
            default -> {
                // ACCEPTED 与 DOWNLOADED 是正常中间状态，不刷屏。
            }
        }
    }
}

