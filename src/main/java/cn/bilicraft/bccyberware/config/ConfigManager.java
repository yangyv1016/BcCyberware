package cn.bilicraft.bccyberware.config;

import cn.bilicraft.bccyberware.config.model.ConfigSnapshot;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        installDefaultPackAssets();
        warnIfLegacyResourceConfig();
    }

    private void warnIfLegacyResourceConfig() {
        File resourceConfig = new File(plugin.getDataFolder(), "resources.yml");
        if (!resourceConfig.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(resourceConfig);
        if (yaml.isConfigurationSection("deployment")) {
            plugin.getLogger().warning("检测到 v0.0.5 的 deployment 配置：v0.0.6 起该节不再使用。"
                    + "BcCyberware 资源改由 Oraxen API 注入，请参照最新 resources.yml 的 oraxen 节。");
        }
        if (!yaml.isConfigurationSection("generation") && !yaml.isConfigurationSection("deployment")) {
            plugin.getLogger().warning("检测到 v0.0.1 格式的 resources.yml：旧版逐包 URL 下发已移除。"
                    + "请把资源放入 Pack Assets 或 Generation/merge，并由 Oraxen 统一生成和下发。");
        }
    }

    private void installDefaultPackAssets() {
        Path targetRoot = plugin.getDataFolder().toPath().resolve("packs/core/Assets").normalize();
        if (Files.isRegularFile(targetRoot.resolve("pack.mcmeta"))) {
            return;
        }
        try (InputStream bundled = plugin.getResource("bundled-resourcepacks/BcCyberware-Example-Pack.zip")) {
            if (bundled == null) {
                plugin.getLogger().severe("插件 JAR 中缺少默认资源包，无法释放 core/Assets。");
                return;
            }
            Files.createDirectories(targetRoot);
            try (ZipInputStream zip = new ZipInputStream(bundled)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    Path target = targetRoot.resolve(entry.getName()).normalize();
                    if (!target.startsWith(targetRoot)) {
                        throw new IOException("默认资源包包含越界路径：" + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else if (!Files.exists(target)) {
                        Files.createDirectories(target.getParent());
                        Files.copy(zip, target);
                    }
                    zip.closeEntry();
                }
            }
            plugin.getLogger().info("已释放默认 Pack 资源到 plugins/BcCyberware/packs/core/Assets。");
        } catch (IOException exception) {
            plugin.getLogger().severe("释放默认 Pack 资源失败：" + exception.getMessage());
        }
    }

    public boolean reload() {
        try {
            ConfigSnapshot candidate = new ConfigLoader(plugin.getDataFolder().toPath()).load()
                    .withMessageFallbacks(loadBundledMessageFallbacks());
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

    private Map<String, String> loadBundledMessageFallbacks() {
        try (InputStream bundled = plugin.getResource("messages.yml")) {
            if (bundled == null) {
                plugin.getLogger().warning("插件 JAR 中缺少 messages.yml，无法为旧版消息配置提供回退文本。");
                return Map.of();
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundled, StandardCharsets.UTF_8)
            );
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            for (String key : yaml.getKeys(true)) {
                if (yaml.isString(key)) {
                    result.put(key, yaml.getString(key, ""));
                }
            }
            return result;
        } catch (IOException exception) {
            plugin.getLogger().warning("读取 JAR 内置消息回退失败：" + exception.getMessage());
            return Map.of();
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
