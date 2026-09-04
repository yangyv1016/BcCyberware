package cn.bilicraft.bccyberware.resourcepack;

import cn.bilicraft.bccyberware.config.ConfigManager;
import cn.bilicraft.bccyberware.config.model.ConfigSnapshot;
import cn.bilicraft.bccyberware.config.model.ResourcePackDeploymentSettings;
import cn.bilicraft.bccyberware.config.model.ResourcePackDeploymentType;
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

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class ResourcePackService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final TextService text;
    private final ResourcePackGenerator generator;
    private final SelfHostedPackServer selfHost;
    private final AtomicBoolean taskRunning = new AtomicBoolean();
    private final AtomicLong operationEpoch = new AtomicLong();
    private volatile DeploymentState state = new DeploymentState(null, null, false);
    private volatile boolean closed;

    public ResourcePackService(JavaPlugin plugin, ConfigManager configs, TextService text) {
        this.plugin = plugin;
        this.configs = configs;
        this.text = text;
        this.generator = new ResourcePackGenerator(plugin.getDataFolder().toPath());
        this.selfHost = new SelfHostedPackServer(
                plugin.getLogger(),
                plugin.getDataFolder().toPath().resolve(".runtime/resourcepack-cache")
        );
    }

    public boolean start() {
        return scheduleDeployment(true, null);
    }

    public boolean reloadDeploymentAsync(Consumer<Boolean> completion) {
        return scheduleDeployment(true, completion);
    }

    public boolean isBusy() {
        return taskRunning.get();
    }

    private boolean scheduleDeployment(boolean notifyOnlinePlayers, Consumer<Boolean> completion) {
        if (closed || !taskRunning.compareAndSet(false, true)) {
            plugin.getLogger().warning("已有资源包生成或部署任务正在运行，本次请求已忽略。");
            return false;
        }
        long ticket = operationEpoch.incrementAndGet();
        ConfigSnapshot snapshot = configs.current();
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                PreparedDeployment prepared;
                try {
                    prepared = prepareDeployment(snapshot);
                } catch (IOException | RuntimeException exception) {
                    logFailure("资源包生成或部署准备失败", exception);
                    finishFailure(completion);
                    return;
                }
                queueDeploymentApply(prepared, ticket, notifyOnlinePlayers, completion);
            });
            return true;
        } catch (RuntimeException exception) {
            taskRunning.set(false);
            logFailure("无法调度资源包后台任务", exception);
            return false;
        }
    }

    private PreparedDeployment prepareDeployment(ConfigSnapshot snapshot) throws IOException {
        ResourcePackDeploymentSettings settings = snapshot.resourcePackDeployment();
        ResourcePackGenerator.GeneratedResourcePack candidate = null;
        if (settings.generationEnabled() && settings.generateOnStartup()) {
            candidate = generator.generate(snapshot);
        } else if (settings.deploymentEnabled() && settings.type() == ResourcePackDeploymentType.SELFHOST) {
            candidate = generator.inspectExisting(snapshot);
        }

        SelfHostedPackServer.PreparedPack selfHosted = null;
        if (settings.deploymentEnabled() && settings.type() == ResourcePackDeploymentType.SELFHOST) {
            if (candidate == null) {
                throw new IOException("SELFHOST 没有可部署的本地资源包");
            }
            selfHosted = selfHost.prepare(candidate);
        }
        return new PreparedDeployment(snapshot, settings, candidate, selfHosted);
    }

    private void queueDeploymentApply(
            PreparedDeployment prepared,
            long ticket,
            boolean notifyOnlinePlayers,
            Consumer<Boolean> completion
    ) {
        if (closed) {
            discard(prepared.selfHosted());
            taskRunning.set(false);
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean success = false;
                try {
                    if (closed || operationEpoch.get() != ticket || configs.current() != prepared.snapshot()) {
                        discard(prepared.selfHosted());
                        plugin.getLogger().warning("资源包后台处理期间配置已变化，结果未部署；请重新执行重载。");
                    } else {
                        success = applyPreparedDeployment(prepared, notifyOnlinePlayers);
                    }
                } catch (RuntimeException exception) {
                    discard(prepared.selfHosted());
                    logFailure("应用资源包部署状态失败", exception);
                }
                taskRunning.set(false);
                invokeCompletion(completion, success);
            });
        } catch (RuntimeException exception) {
            discard(prepared.selfHosted());
            taskRunning.set(false);
            logFailure("无法回到主线程应用资源包部署", exception);
        }
    }

    private boolean applyPreparedDeployment(PreparedDeployment prepared, boolean notifyOnlinePlayers) {
        ResourcePackDeploymentSettings settings = prepared.settings();
        DeploymentState previousState = state;
        ResourcePackDeploymentSettings previous = previousState.settings();
        ResourcePackGenerator.GeneratedResourcePack previousPack = previousState.pack();
        boolean wasReady = previousState.ready();

        try {
            if (settings.deploymentEnabled() && settings.type() == ResourcePackDeploymentType.SELFHOST) {
                selfHost.startPrepared(settings.bindAddress(), settings.port(), prepared.selfHosted());
                plugin.getLogger().info("资源包 SELFHOST 已监听 " + settings.bindAddress() + ":" + settings.port()
                        + "，文件 " + prepared.candidate().file().getFileName()
                        + "，SHA-1 " + prepared.candidate().sha1Hex());
            } else {
                selfHost.close();
            }
        } catch (IOException | RuntimeException exception) {
            logFailure("资源包部署失败", exception);
            plugin.getLogger().warning("资源包部署保留上一个可用状态，不会向玩家发送失败配置。");
            return false;
        }

        if (prepared.candidate() != null
                && (!settings.deploymentEnabled() || settings.type() != ResourcePackDeploymentType.SELFHOST)) {
            plugin.getLogger().info("资源包已生成：" + prepared.candidate().file()
                    + "，SHA-1 " + prepared.candidate().sha1Hex());
        }

        if (settings.deploymentEnabled() && settings.type() == ResourcePackDeploymentType.EXTERNAL
                && prepared.candidate() != null
                && !Arrays.equals(prepared.candidate().sha1(), settings.externalSha1())) {
            plugin.getLogger().warning("本地生成资源包的 SHA-1 与 EXTERNAL 已部署 SHA-1 不同；"
                    + "继续向玩家发送配置中的已上传版本。上传新 ZIP、更新 sha1 后执行重载即可切换。");
        }

        DeploymentState currentState = new DeploymentState(
                settings,
                prepared.candidate(),
                settings.deploymentEnabled()
        );
        state = currentState;
        if (notifyOnlinePlayers) {
            if (wasReady && previous != null
                    && (!currentState.ready() || !previous.uuid().equals(settings.uuid()))) {
                Bukkit.getOnlinePlayers().forEach(player -> player.removeResourcePack(previous.uuid()));
            }
            boolean changed = !wasReady
                    || previous == null
                    || previous.type() != settings.type()
                    || !previous.uuid().equals(settings.uuid())
                    || !previous.publicUrl().equals(settings.publicUrl())
                    || (!previous.autoSendEnabled() && settings.autoSendEnabled())
                    || (settings.type() == ResourcePackDeploymentType.SELFHOST
                    && (previousPack == null || prepared.candidate() == null
                    || !previousPack.sha1Hex().equals(prepared.candidate().sha1Hex())))
                    || (settings.type() == ResourcePackDeploymentType.EXTERNAL
                    && !Arrays.equals(previous.externalSha1(), settings.externalSha1()));
            boolean firstAvailableDeployment = !wasReady;
            if (currentState.ready() && settings.autoSendEnabled()
                    && (firstAvailableDeployment || (settings.sendOnUpdate() && changed))) {
                Bukkit.getOnlinePlayers().forEach(this::sendGenerated);
            }
        }
        return true;
    }

    public boolean generateAsync(Consumer<Boolean> completion) {
        ConfigSnapshot snapshot = configs.current();
        ResourcePackDeploymentSettings configured = snapshot.resourcePackDeployment();
        if (!configured.generationEnabled()) {
            plugin.getLogger().warning("resources.yml 中 generation.enabled=false，未生成资源包。");
            return false;
        }
        if (closed || !taskRunning.compareAndSet(false, true)) {
            plugin.getLogger().warning("已有资源包生成或部署任务正在运行，本次请求已忽略。");
            return false;
        }
        long ticket = operationEpoch.incrementAndGet();
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                GeneratedTaskResult prepared;
                try {
                    ResourcePackGenerator.GeneratedResourcePack candidate = generator.generate(snapshot);
                    DeploymentState currentState = state;
                    ResourcePackDeploymentSettings active = currentState.settings();
                    SelfHostedPackServer.PreparedPack selfHosted = null;
                    if (currentState.ready() && active != null
                            && active.type() == ResourcePackDeploymentType.SELFHOST) {
                        selfHosted = selfHost.prepare(candidate);
                    }
                    prepared = new GeneratedTaskResult(candidate, selfHosted, currentState);
                } catch (IOException | RuntimeException exception) {
                    logFailure("资源包生成失败", exception);
                    finishFailure(completion);
                    return;
                }
                queueGeneratedApply(snapshot, ticket, prepared, completion);
            });
            return true;
        } catch (RuntimeException exception) {
            taskRunning.set(false);
            logFailure("无法调度资源包生成任务", exception);
            return false;
        }
    }

    private void queueGeneratedApply(
            ConfigSnapshot snapshot,
            long ticket,
            GeneratedTaskResult prepared,
            Consumer<Boolean> completion
    ) {
        if (closed) {
            discard(prepared.selfHosted());
            taskRunning.set(false);
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean success = false;
                try {
                    if (closed || operationEpoch.get() != ticket || configs.current() != snapshot) {
                        discard(prepared.selfHosted());
                        plugin.getLogger().warning("资源包生成期间配置已变化，结果未部署；请重新执行生成命令。");
                    } else {
                        DeploymentState currentState = state;
                        ResourcePackDeploymentSettings active = currentState.settings();
                        if (currentState != prepared.stateAtPreparation()) {
                            discard(prepared.selfHosted());
                            plugin.getLogger().warning("资源包部署状态已变化，生成结果未发布；请重新执行生成命令。");
                        } else {
                            if (prepared.selfHosted() != null) {
                                if (!currentState.ready() || active == null
                                        || active.type() != ResourcePackDeploymentType.SELFHOST) {
                                    throw new IllegalStateException("SELFHOST 准备结果与当前部署状态不一致");
                                }
                                selfHost.publishPrepared(prepared.selfHosted());
                            }
                            state = new DeploymentState(active, prepared.candidate(), currentState.ready());
                            plugin.getLogger().info("资源包已生成：" + prepared.candidate().file()
                                    + "，SHA-1 " + prepared.candidate().sha1Hex());
                            if (currentState.ready() && active != null
                                    && active.type() == ResourcePackDeploymentType.SELFHOST
                                    && active.autoSendEnabled() && active.sendOnUpdate()) {
                                Bukkit.getOnlinePlayers().forEach(this::sendGenerated);
                            } else if (currentState.ready() && active != null
                                    && active.type() == ResourcePackDeploymentType.EXTERNAL) {
                                plugin.getLogger().info("EXTERNAL 模式只生成了本地 ZIP，未切换或推送远端资源包；"
                                        + "请先上传文件，再更新 resources.yml 的 sha1 并重载。");
                            }
                            success = true;
                        }
                    }
                } catch (IOException | RuntimeException exception) {
                    discard(prepared.selfHosted());
                    logFailure("应用新生成资源包失败", exception);
                }
                taskRunning.set(false);
                invokeCompletion(completion, success);
            });
        } catch (RuntimeException exception) {
            discard(prepared.selfHosted());
            taskRunning.set(false);
            logFailure("无法回到主线程应用生成结果", exception);
        }
    }

    private void finishFailure(Consumer<Boolean> completion) {
        if (closed || !plugin.isEnabled()) {
            taskRunning.set(false);
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                taskRunning.set(false);
                invokeCompletion(completion, false);
            });
        } catch (RuntimeException exception) {
            taskRunning.set(false);
            logFailure("无法回到主线程报告资源包任务失败", exception);
        }
    }

    private void discard(SelfHostedPackServer.PreparedPack prepared) {
        if (prepared != null) {
            selfHost.discardPrepared(prepared);
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
        state = new DeploymentState(null, null, false);
        selfHost.close();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        DeploymentState currentState = state;
        ResourcePackDeploymentSettings settings = currentState.settings();
        boolean generatedAutoSend = settings != null && currentState.ready() && settings.autoSendEnabled();
        boolean externalAutoSend = configs.current().resourcePacksEnabled();
        if (!generatedAutoSend && !externalAutoSend) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                DeploymentState delayedState = state;
                ResourcePackDeploymentSettings current = delayedState.settings();
                if (current != null && delayedState.ready() && current.autoSendEnabled()) {
                    sendGenerated(event.getPlayer());
                }
                if (configs.current().resourcePacksEnabled()) {
                    sendExternal(event.getPlayer());
                }
            }
        }, configs.current().resourcePackDelayTicks());
    }

    public void send(Player player) {
        sendGenerated(player);
        sendExternal(player);
    }

    private void sendGenerated(Player player) {
        DeploymentState currentState = state;
        ResourcePackDeploymentSettings settings = currentState.settings();
        ResourcePackGenerator.GeneratedResourcePack current = currentState.pack();
        if (currentState.ready() && settings != null
                && (settings.type() == ResourcePackDeploymentType.EXTERNAL || current != null)) {
            String url = generatedUrl(settings, current == null ? "" : current.sha1Hex());
            String prompt = PlainTextComponentSerializer.plainText().serialize(text.render(settings.prompt()));
            byte[] sha1 = settings.type() == ResourcePackDeploymentType.EXTERNAL
                    ? settings.externalSha1()
                    : current.sha1();
            player.addResourcePack(settings.uuid(), url, sha1, prompt, settings.required());
        }
    }

    private void sendExternal(Player player) {
        if (configs.current().resourcePacksEnabled()) {
            for (ResourcePackSpec pack : configs.current().resourcePacks()) {
                String prompt = PlainTextComponentSerializer.plainText().serialize(text.render(pack.prompt()));
                player.addResourcePack(pack.uuid(), pack.url(), pack.sha1(), prompt, pack.required());
            }
        }
    }

    private String generatedUrl(ResourcePackDeploymentSettings settings, String sha1) {
        String configured = settings.publicUrl().replaceAll("/+$", "");
        if (settings.type() == ResourcePackDeploymentType.SELFHOST) {
            return configured + SelfHostedPackServer.downloadPath(sha1);
        }
        return configured;
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        ResourcePackDeploymentSettings settings = state.settings();
        boolean generatedPackEvent = settings != null && event.getID().equals(settings.uuid());
        boolean externalPackEvent = configs.current().resourcePacks().stream()
                .anyMatch(pack -> event.getID().equals(pack.uuid()));
        if (!generatedPackEvent && !externalPackEvent) {
            return;
        }
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

    private record PreparedDeployment(
            ConfigSnapshot snapshot,
            ResourcePackDeploymentSettings settings,
            ResourcePackGenerator.GeneratedResourcePack candidate,
            SelfHostedPackServer.PreparedPack selfHosted
    ) {
    }

    private record GeneratedTaskResult(
            ResourcePackGenerator.GeneratedResourcePack candidate,
            SelfHostedPackServer.PreparedPack selfHosted,
            DeploymentState stateAtPreparation
    ) {
    }

    private record DeploymentState(
            ResourcePackDeploymentSettings settings,
            ResourcePackGenerator.GeneratedResourcePack pack,
            boolean ready
    ) {
    }
}
