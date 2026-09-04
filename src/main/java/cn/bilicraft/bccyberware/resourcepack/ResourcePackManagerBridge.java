package cn.bilicraft.bccyberware.resourcepack;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

final class ResourcePackManagerBridge {
    private static final String MANAGER_NAME = "ResourcePackManager";
    private static final String API_CLASS = "com.magmaguy.resourcepackmanager.api.ResourcePackManagerAPI";
    private static final String RELOAD_COMMAND = "bccyberware resourcepack generate";

    private final JavaPlugin plugin;

    ResourcePackManagerBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void registerAndReload(Path resourcePack) throws IOException {
        Class<?> api = register(resourcePack);
        try {
            api.getMethod("reloadResourcePack").invoke(null);
        } catch (NoSuchMethodException exception) {
            throw new IOException("已安装的 ResourcePackManager 不包含重载 API，请升级该插件", exception);
        } catch (IllegalAccessException exception) {
            throw new IOException("无法访问 ResourcePackManager 的重载 API", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IOException("ResourcePackManager 合并或重载失败：" + cause.getMessage(), cause);
        }
    }

    Class<?> register(Path resourcePack) throws IOException {
        if (!Files.isRegularFile(resourcePack)) {
            throw new IOException("准备注册给 ResourcePackManager 的资源包不存在：" + resourcePack);
        }
        Plugin manager = Bukkit.getPluginManager().getPlugin(MANAGER_NAME);
        if (manager == null || !manager.isEnabled()) {
            throw new IOException("deployment.type=RESOURCE_PACK_MANAGER 需要安装并启用 ResourcePackManager");
        }

        String localPath = pluginRelativePath(plugin.getDataFolder().toPath().getParent(), resourcePack);
        try {
            Class<?> api = Class.forName(API_CLASS, true, manager.getClass().getClassLoader());
            Method register = api.getMethod(
                    "registerLocalResourcePack",
                    String.class,
                    String.class,
                    boolean.class,
                    boolean.class,
                    boolean.class,
                    String.class
            );
            register.invoke(null, plugin.getName(), localPath, false, false, true, RELOAD_COMMAND);
            return api;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            throw new IOException("已安装的 ResourcePackManager 不包含兼容的公开 API，请升级该插件", exception);
        } catch (IllegalAccessException exception) {
            throw new IOException("无法访问 ResourcePackManager 的公开 API", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IOException("ResourcePackManager 注册 BcCyberware 资源包失败：" + cause.getMessage(), cause);
        }
    }

    static String pluginRelativePath(Path pluginsDirectory, Path resourcePack) throws IOException {
        Path root = pluginsDirectory.toAbsolutePath().normalize();
        Path pack = resourcePack.toAbsolutePath().normalize();
        if (!pack.startsWith(root)) {
            throw new IOException("资源包必须位于服务器 plugins 目录内：" + pack);
        }
        String relative = root.relativize(pack).toString().replace('\\', '/');
        if (relative.isBlank()) {
            throw new IOException("资源包路径不能指向 plugins 目录本身");
        }
        return relative;
    }
}
