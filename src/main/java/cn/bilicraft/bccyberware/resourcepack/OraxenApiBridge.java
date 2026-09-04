package cn.bilicraft.bccyberware.resourcepack;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Calls Oraxen's public API through Oraxen's own class loader.
 *
 * <p>Some Paper forks isolate plugin class paths even when the Bukkit dependency
 * order is correct. Keeping all Oraxen types behind this boundary avoids direct
 * linkage from BcCyberware while still invoking the public Oraxen API.</p>
 */
final class OraxenApiBridge {
    private static final String PLUGIN_NAME = "Oraxen";

    private final Plugin provider;
    private final ReflectionAccess access;

    private OraxenApiBridge(Plugin provider, ReflectionAccess access) {
        this.provider = provider;
        this.access = access;
    }

    static OraxenApiBridge connect(
            JavaPlugin owner,
            Consumer<Object> generatedHandler,
            Runnable uploadedHandler
    ) throws IntegrationException {
        Plugin provider = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (provider == null) {
            throw new IntegrationException("Paper 未找到名为 Oraxen 的插件");
        }
        if (!provider.isEnabled()) {
            throw new IntegrationException("Oraxen 已安装但尚未启用");
        }

        ReflectionAccess access = ReflectionAccess.resolve(provider.getClass().getClassLoader());
        Listener listener = new Listener() {
        };
        Bukkit.getPluginManager().registerEvent(
                access.generatedEventType(),
                listener,
                EventPriority.HIGH,
                (ignored, event) -> generatedHandler.accept(event),
                owner,
                true
        );
        Bukkit.getPluginManager().registerEvent(
                access.uploadedEventType(),
                listener,
                EventPriority.MONITOR,
                (ignored, event) -> uploadedHandler.run(),
                owner,
                true
        );
        return new OraxenApiBridge(provider, access);
    }

    String version() {
        return provider.getPluginMeta().getVersion();
    }

    void reloadPack() throws IntegrationException {
        access.reloadPack();
    }

    void injectAssets(Object generatedEvent, Map<String, byte[]> assets) throws IntegrationException {
        access.injectAssets(generatedEvent, assets);
    }

    static final class ReflectionAccess {
        private static final String PACK_API = "io.th0rgal.oraxen.api.OraxenPack";
        private static final String GENERATED_EVENT =
                "io.th0rgal.oraxen.api.events.OraxenPackGeneratedEvent";
        private static final String UPLOADED_EVENT =
                "io.th0rgal.oraxen.api.events.OraxenPackUploadEvent";
        private static final String VIRTUAL_FILE = "io.th0rgal.oraxen.utils.VirtualFile";

        private final Method reloadPack;
        private final Class<? extends Event> generatedEventType;
        private final Class<? extends Event> uploadedEventType;
        private final Method generatedOutput;
        private final Constructor<?> virtualFileConstructor;
        private final Method virtualFilePath;

        private ReflectionAccess(
                Method reloadPack,
                Class<? extends Event> generatedEventType,
                Class<? extends Event> uploadedEventType,
                Method generatedOutput,
                Constructor<?> virtualFileConstructor,
                Method virtualFilePath
        ) {
            this.reloadPack = reloadPack;
            this.generatedEventType = generatedEventType;
            this.uploadedEventType = uploadedEventType;
            this.generatedOutput = generatedOutput;
            this.virtualFileConstructor = virtualFileConstructor;
            this.virtualFilePath = virtualFilePath;
        }

        static ReflectionAccess resolve(ClassLoader loader) throws IntegrationException {
            Objects.requireNonNull(loader, "loader");
            try {
                Class<?> packApi = Class.forName(PACK_API, false, loader);
                Class<? extends Event> generatedEvent = eventClass(loader, GENERATED_EVENT);
                Class<? extends Event> uploadedEvent = eventClass(loader, UPLOADED_EVENT);
                Class<?> virtualFile = Class.forName(VIRTUAL_FILE, false, loader);
                return new ReflectionAccess(
                        packApi.getMethod("reloadPack"),
                        generatedEvent,
                        uploadedEvent,
                        generatedEvent.getMethod("getOutput"),
                        virtualFile.getConstructor(String.class, String.class, InputStream.class),
                        virtualFile.getMethod("getPath")
                );
            } catch (ClassNotFoundException exception) {
                throw new IntegrationException("当前 Oraxen 缺少公共 API 类 " + exception.getMessage()
                        + "；请更新到兼容版本", exception);
            } catch (NoSuchMethodException exception) {
                throw new IntegrationException("当前 Oraxen 公共 API 签名不兼容：" + exception.getMessage(),
                        exception);
            } catch (LinkageError error) {
                throw new IntegrationException("加载 Oraxen 公共 API 时缺少其运行依赖：" + error.getMessage(),
                        error);
            }
        }

        private static Class<? extends Event> eventClass(ClassLoader loader, String name)
                throws ClassNotFoundException, IntegrationException {
            Class<?> type = Class.forName(name, false, loader);
            if (!Event.class.isAssignableFrom(type)) {
                throw new IntegrationException(name + " 不是 Bukkit Event");
            }
            return type.asSubclass(Event.class);
        }

        Class<? extends Event> generatedEventType() {
            return generatedEventType;
        }

        Class<? extends Event> uploadedEventType() {
            return uploadedEventType;
        }

        void reloadPack() throws IntegrationException {
            invoke(reloadPack, null);
        }

        void injectAssets(Object generatedEvent, Map<String, byte[]> assets) throws IntegrationException {
            if (!generatedEventType.isInstance(generatedEvent)) {
                throw new IntegrationException("收到的事件不是当前 OraxenPackGeneratedEvent 实例");
            }
            Object rawOutput = invoke(generatedOutput, generatedEvent);
            if (!(rawOutput instanceof List<?> rawList)) {
                throw new IntegrationException("OraxenPackGeneratedEvent.getOutput() 未返回 List");
            }

            @SuppressWarnings("unchecked")
            List<Object> output = (List<Object>) rawList;
            Iterator<Object> iterator = output.iterator();
            while (iterator.hasNext()) {
                Object virtualFile = iterator.next();
                Object rawPath = invoke(virtualFilePath, virtualFile);
                if (rawPath instanceof String path && assets.containsKey(path)) {
                    iterator.remove();
                }
            }

            for (Map.Entry<String, byte[]> asset : assets.entrySet()) {
                String path = asset.getKey();
                int separator = path.lastIndexOf('/');
                String parent = separator < 0 ? "" : path.substring(0, separator);
                String name = separator < 0 ? path : path.substring(separator + 1);
                try {
                    output.add(virtualFileConstructor.newInstance(
                            parent,
                            name,
                            new ByteArrayInputStream(asset.getValue())
                    ));
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
                    throw reflectionFailure("创建 Oraxen VirtualFile", exception);
                }
            }
        }

        private static Object invoke(Method method, Object target, Object... arguments)
                throws IntegrationException {
            try {
                return method.invoke(target, arguments);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw reflectionFailure("调用 " + method.getDeclaringClass().getSimpleName()
                        + "." + method.getName(), exception);
            }
        }

        private static IntegrationException reflectionFailure(String action, ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    ? invocation.getTargetException() : exception;
            return new IntegrationException(action + " 失败：" + readableMessage(cause), cause);
        }

        private static String readableMessage(Throwable throwable) {
            String message = throwable.getMessage();
            return message == null || message.isBlank()
                    ? throwable.getClass().getName()
                    : throwable.getClass().getName() + ": " + message;
        }
    }

    static final class IntegrationException extends Exception {
        private static final long serialVersionUID = 1L;

        IntegrationException(String message) {
            super(message);
        }

        IntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
