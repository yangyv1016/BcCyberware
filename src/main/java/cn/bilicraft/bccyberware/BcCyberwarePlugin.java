package cn.bilicraft.bccyberware;

import cn.bilicraft.bccyberware.api.BcCyberwareApi;
import cn.bilicraft.bccyberware.api.BcCyberwareApiImpl;
import cn.bilicraft.bccyberware.capacity.CapacityService;
import cn.bilicraft.bccyberware.command.CyberwareCommand;
import cn.bilicraft.bccyberware.config.ConfigManager;
import cn.bilicraft.bccyberware.data.ProfileService;
import cn.bilicraft.bccyberware.data.SqliteRepository;
import cn.bilicraft.bccyberware.effect.CyberwareEventListener;
import cn.bilicraft.bccyberware.effect.EffectEngine;
import cn.bilicraft.bccyberware.gui.MenuService;
import cn.bilicraft.bccyberware.item.ItemService;
import cn.bilicraft.bccyberware.resourcepack.ResourcePackService;
import cn.bilicraft.bccyberware.text.TextService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.sql.SQLException;

public final class BcCyberwarePlugin extends JavaPlugin {
    private ConfigManager configs;
    private ProfileService profiles;
    private EffectEngine effects;
    private ResourcePackService resourcePacks;
    private BcCyberwareApi api;

    @Override
    public void onEnable() {
        configs = new ConfigManager(this);
        configs.installDefaults();
        if (!configs.reload()) {
            getLogger().severe("默认配置也未能通过校验，插件将安全停用。请修正上方错误后重启服务器。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        TextService text = new TextService(configs);
        ItemService items = new ItemService(this, configs, text);
        Path databasePath = getDataFolder().toPath().resolve(configs.current().databaseFile()).normalize();
        SqliteRepository repository = new SqliteRepository(databasePath);
        try {
            repository.initialize();
        } catch (SQLException exception) {
            getLogger().severe("SQLite 初始化失败，插件将安全停用：" + exception.getMessage());
            exception.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        profiles = new ProfileService(this, configs, items, repository);
        CapacityService capacity = new CapacityService(this, configs, items);
        effects = new EffectEngine(this, configs, profiles, capacity, items, text);
        MenuService menus = new MenuService(configs, profiles, items, capacity, effects, text);
        resourcePacks = new ResourcePackService(this, configs, text);
        if (!resourcePacks.start()) {
            getLogger().warning("义体功能继续运行，但资源包生成或部署当前不可用。请检查 resources.yml 和端口占用。 ");
        }
        CyberwareEventListener events = new CyberwareEventListener(this, profiles, capacity, effects);

        Bukkit.getPluginManager().registerEvents(events, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(resourcePacks, this);
        profiles.onLoaded(effects::reconcilePassives);

        CyberwareCommand executor = new CyberwareCommand(
                configs, profiles, items, capacity, effects, menus, resourcePacks, text);
        PluginCommand command = getCommand("bccyberware");
        if (command == null) {
            getLogger().severe("plugin.yml 未注册 bccyberware 命令，插件将安全停用。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        api = new BcCyberwareApiImpl(configs, profiles, items, capacity, menus);
        Bukkit.getServicesManager().register(BcCyberwareApi.class, api, this, ServicePriority.Normal);

        effects.start();
        Bukkit.getOnlinePlayers().forEach(profiles::load);
        getLogger().info("BcCyberware 已启用：Paper 1.21.11 单服模式，SQLite 数据库。 ");
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        if (effects != null) {
            effects.stop();
        }
        if (resourcePacks != null) {
            resourcePacks.close();
        }
        if (profiles != null) {
            profiles.shutdown();
        }
    }

    /**
     * 供已显式依赖 BcCyberware 的插件直接调用。对于软依赖，建议使用 ServicesManager。
     */
    public BcCyberwareApi getApi() {
        if (api == null) {
            throw new IllegalStateException("BcCyberware API 尚未就绪");
        }
        return api;
    }
}
