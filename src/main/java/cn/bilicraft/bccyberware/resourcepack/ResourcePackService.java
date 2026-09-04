package cn.bilicraft.bccyberware.resourcepack;

import cn.bilicraft.bccyberware.config.ConfigManager;
import cn.bilicraft.bccyberware.config.model.ConfigSnapshot;
import cn.bilicraft.bccyberware.config.model.ResourcePackDeploymentSettings;
import cn.bilicraft.bccyberware.text.TextService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Generates BcCyberware assets and injects them into Oraxen's single final pack.
 * Oraxen remains the only component that zips, uploads and dispatches that pack.
 */
public final class ResourcePackService {
    private static final long ORAXEN_RETRY_DELAY_TICKS = 40L;
    private static final int MAX_ORAXEN_RELOAD_ATTEMPTS = 15;

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final TextService text;
    private final ResourcePackGenerator generator;
    private final OraxenApiBridge oraxen;
    private final AtomicBoolean taskRunning = new AtomicBoolean();
    private final AtomicBoolean retryScheduled = new AtomicBoolean();
    private final AtomicLong operationEpoch = new AtomicLong();

    private volatile Map<String, byte[]> activeAssets = Map.of();
    private volatile boolean reloadPending;
    private volatile int reloadAttempts;
    private volatile boolean closed;

    public ResourcePackService(JavaPlugin plugin, ConfigManager configs, TextService text) {
        this.plugin = plugin;
        this.configs = configs;
        this.text = text;
        this.generator = new ResourcePackGenerator(plugin.getDataFolder().toPath());
        OraxenApiBridge connected = null;
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Oraxen")) {
                connected = OraxenApiBridge.connect(plugin, this::onOraxenPackGenerated, this::onOraxenPackUploaded);
                plugin.getLogger().info("已连接 Oraxen " + connected.version()
                        + " 公共资源包 API（兼容隔离类加载器）。");
            } else {
                plugin.getLogger().info("Oraxen 未安装或未启用：义体以纸张外观运行，不下发额外资源包。");
            }
        } catch (OraxenApiBridge.IntegrationException | LinkageError | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "无法连接 Oraxen 公共资源包 API；义体以纸张外观继续运行，资源不会注入："
                            + exception.getMessage(), exception);
        }
        this.oraxen = connected;
    }

    public boolean start() {
        ResourcePackDeploymentSettings settings = configs.current().resourcePackDeployment();
        return scheduleBuild(settings.generateOnStartup(), null);
    }

    public boolean reloadDeploymentAsync(Consumer<Boolean> completion) {
        ResourcePackDeploymentSettings settings = configs.current().resourcePackDeployment();
        return scheduleBuild(settings.generateOnStartup(), completion);
    }

    public boolean generateAsync(Consumer<Boolean> completion) {
        return scheduleBuild(true, completion);
    }

    public boolean isBusy() {
        return taskRunning.get();
    }

    private boolean scheduleBuild(boolean forceGeneration, Consumer<Boolean> completion) {
        if (closed || !taskRunning.compareAndSet(false, true)) {
            plugin.getLogger().warning("已有资源包生成或 Oraxen 注入任务正在运行，本次请求已忽略。");
            return false;
        }

        ConfigSnapshot snapshot = configs.current();
        ResourcePackDeploymentSettings settings = snapshot.resourcePackDeployment();
        if (forceGeneration && !settings.generationEnabled()) {
            taskRunning.set(false);
            plugin.getLogger().warning("resources.yml 中 generation.enabled=false，未生成资源包。");
            return false;
        }

        long ticket = operationEpoch.incrementAndGet();
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                PreparedAssets prepared;
                try {
                    ResourcePackGenerator.GeneratedResourcePack pack = forceGeneration
                            ? generator.generate(snapshot)
                            : generator.inspectExisting(snapshot);
                    prepared = new PreparedAssets(pack, OraxenPackAssets.read(pack.file()));
                } catch (IOException | RuntimeException exception) {
                    logFailure("生成或读取 BcCyberware 资源失败", exception);
                    finish(completion, false);
                    return;
                }
                applyPrepared(snapshot, settings, prepared, ticket, completion);
            });
            return true;
        } catch (RuntimeException exception) {
            taskRunning.set(false);
            logFailure("无法调度资源包后台任务", exception);
            return false;
        }
    }

    private void applyPrepared(
            ConfigSnapshot snapshot,
            ResourcePackDeploymentSettings settings,
            PreparedAssets prepared,
            long ticket,
            Consumer<Boolean> completion
    ) {
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean success = false;
                try {
                    if (closed || operationEpoch.get() != ticket || configs.current() != snapshot) {
                        plugin.getLogger().warning("资源包生成期间配置已变化，结果未注入 Oraxen；请重新执行重载。");
                    } else {
                        activeAssets = settings.oraxenIntegrationEnabled() && oraxen != null
                                ? immutableCopy(prepared.assets())
                                : Map.of();
                        plugin.getLogger().info("BcCyberware 资源已准备：" + prepared.pack().file()
                                + "，SHA-1 " + prepared.pack().sha1Hex()
                                + "，可注入文件 " + activeAssets.size() + " 个。");
                        if (oraxen != null && settings.reloadOraxenAfterGeneration()) {
                            requestOraxenReload();
                        }
                        success = true;
                    }
                } catch (RuntimeException exception) {
                    logFailure("应用 Oraxen 资源注入状态失败", exception);
                } finally {
                    taskRunning.set(false);
                    invokeCompletion(completion, success);
                }
            });
        } catch (RuntimeException exception) {
            taskRunning.set(false);
            logFailure("无法回到主线程应用 Oraxen 资源注入", exception);
        }
    }

    private Map<String, byte[]> immutableCopy(Map<String, byte[]> source) {
        LinkedHashMap<String, byte[]> copy = new LinkedHashMap<>();
        source.forEach((path, bytes) -> copy.put(path, bytes.clone()));
        return java.util.Collections.unmodifiableMap(copy);
    }

    private void requestOraxenReload() {
        reloadPending = true;
        reloadAttempts = 0;
        scheduleOraxenReloadRetry();
    }

    private void tryOraxenReload() {
        if (closed || !reloadPending) {
            return;
        }
        if (oraxen == null) {
            reloadPending = false;
            plugin.getLogger().severe("Oraxen 公共 API 不可用，无法重建并注入义体资源。"
                    + "请检查启动阶段更早的 Oraxen API 诊断。");
            return;
        }
        reloadAttempts++;
        try {
            oraxen.reloadPack();
        } catch (OraxenApiBridge.IntegrationException | LinkageError exception) {
            reloadPending = false;
            plugin.getLogger().log(Level.SEVERE,
                    "调用 OraxenPack.reloadPack() 失败；已停止重试，避免定时任务反复报错："
                            + exception.getMessage(), exception);
            return;
        }
        if (reloadPending && reloadAttempts < MAX_ORAXEN_RELOAD_ATTEMPTS) {
            scheduleOraxenReloadRetry();
        } else if (reloadPending) {
            plugin.getLogger().severe("Oraxen 连续未进入资源包生成事件；BcCyberware 资源尚未注入。"
                    + "请检查 Oraxen Pack.generation.generate，并执行 /bccyberware resourcepack generate 重试。");
        }
    }

    private void scheduleOraxenReloadRetry() {
        if (!closed && reloadPending && retryScheduled.compareAndSet(false, true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                retryScheduled.set(false);
                tryOraxenReload();
            }, ORAXEN_RETRY_DELAY_TICKS);
        }
    }

    private void onOraxenPackGenerated(Object event) {
        if (closed) {
            return;
        }
        if (!configs.current().resourcePackDeployment().oraxenIntegrationEnabled()) {
            reloadPending = false;
            return;
        }
        Map<String, byte[]> assets = activeAssets;
        if (assets.isEmpty()) {
            plugin.getLogger().warning("Oraxen 正在生成资源包，但 BcCyberware 尚无可注入资源。");
            return;
        }

        if (oraxen == null) {
            return;
        }
        try {
            oraxen.injectAssets(event, assets);
        } catch (OraxenApiBridge.IntegrationException | LinkageError exception) {
            reloadPending = false;
            plugin.getLogger().log(Level.SEVERE,
                    "向 OraxenPackGeneratedEvent 注入资源失败：" + exception.getMessage(), exception);
            return;
        }
        reloadPending = false;
        plugin.getLogger().info("已通过 OraxenPackGeneratedEvent 注入 " + assets.size()
                + " 个 BcCyberware 资源；最终 ZIP、上传和玩家下发由 Oraxen 统一完成。");
    }

    private void onOraxenPackUploaded() {
        if (reloadPending) {
            Bukkit.getScheduler().runTask(plugin, this::tryOraxenReload);
        }
    }

    public void send(Player player) {
        text.send(player, oraxen != null && configs.current().resourcePackDeployment().oraxenIntegrationEnabled()
                ? "resource-pack-managed-by-oraxen" : "resource-pack-paper-mode");
    }

    private void finish(Consumer<Boolean> completion, boolean success) {
        if (closed || !plugin.isEnabled()) {
            taskRunning.set(false);
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                taskRunning.set(false);
                invokeCompletion(completion, success);
            });
        } catch (RuntimeException exception) {
            taskRunning.set(false);
            logFailure("无法回到主线程报告资源包任务结果", exception);
        }
    }

    private void invokeCompletion(Consumer<Boolean> completion, boolean success) {
        if (completion == null) {
            return;
        }
        try {
            completion.accept(success);
        } catch (RuntimeException exception) {
            logFailure("资源包任务完成回调失败", exception);
        }
    }

    private void logFailure(String context, Throwable exception) {
        if (exception instanceof IOException) {
            plugin.getLogger().severe(context + "：" + exception.getMessage());
        } else {
            plugin.getLogger().log(Level.SEVERE, context + "：" + exception.getMessage(), exception);
        }
    }

    public void close() {
        closed = true;
        operationEpoch.incrementAndGet();
        taskRunning.set(false);
        reloadPending = false;
        activeAssets = Map.of();
    }

    private record PreparedAssets(
            ResourcePackGenerator.GeneratedResourcePack pack,
            Map<String, byte[]> assets
    ) {
    }
}
