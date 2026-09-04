package cn.bilicraft.bccyberware.config;

import cn.bilicraft.bccyberware.config.model.ConfigSnapshot;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigManager {
    private static final List<String> DEFAULT_RESOURCES = List.of(
            "config.yml",
            "resources.yml",
            "messages.yml",
            "README-配置说明.md",
            "packs/README.yml",
            "packs/core/pack.yml",
            "packs/core/slots.yml",
            "packs/core/organs/default-organs.yml",
            "packs/core/cyberware/passive.yml",
            "packs/core/cyberware/triggered.yml",
            "packs/core/cyberware/active.yml"
    );

    private final JavaPlugin plugin;
    private final AtomicReference<ConfigSnapshot> snapshot = new AtomicReference<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void installDefaults() {
        for (String resource : DEFAULT_RESOURCES) {
            File target = new File(plugin.getDataFolder(), resource);
            if (!target.exists()) {
                plugin.saveResource(resource, false);
            }
        }
    }

    public boolean reload() {
        try {
            ConfigSnapshot candidate = new ConfigLoader(plugin.getDataFolder().toPath()).load();
            snapshot.set(candidate);
            plugin.getLogger().info("配置加载完成：" + candidate.packs().size() + " 个 Pack、"
                    + candidate.slots().size() + " 个槽位、" + candidate.items().size() + " 个部件。 ");
            return true;
        } catch (ConfigException exception) {
            plugin.getLogger().severe("BcCyberware 配置校验失败，当前运行配置未被替换：" + System.lineSeparator()
                    + exception.detailedMessage());
            return false;
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("BcCyberware 配置加载出现未预期错误，当前运行配置未被替换："
                    + exception.getMessage());
            exception.printStackTrace();
            return false;
        }
    }

    public ConfigSnapshot current() {
        ConfigSnapshot current = snapshot.get();
        if (current == null) {
            throw new IllegalStateException("配置尚未成功加载");
        }
        return current;
    }
}

