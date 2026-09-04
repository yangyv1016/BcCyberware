package cn.bilicraft.bccyberware.text;

import cn.bilicraft.bccyberware.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TextService {
    private static final DecimalFormat NUMBER = new DecimalFormat("0.##");

    private final ConfigManager configs;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public TextService(ConfigManager configs) {
        this.configs = configs;
    }

    public Component render(String template) {
        return miniMessage.deserialize(template == null ? "" : template);
    }

    public Component render(String template, Map<String, ?> placeholders) {
        String resolved = template == null ? "" : template;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            resolved = resolved.replace("<" + entry.getKey() + ">", escape(format(entry.getValue())));
        }
        return miniMessage.deserialize(resolved);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, ?> placeholders) {
        String prefix = configs.current().messages().getOrDefault("prefix", "");
        String message = configs.current().messages().getOrDefault(key, "<red>缺少消息：" + key);
        sender.sendMessage(render(prefix + message, placeholders));
    }

    public Map<String, Object> placeholders(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("占位符必须成对提供");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }

    private String escape(String value) {
        return miniMessage.escapeTags(value);
    }

    private static String format(Object value) {
        if (value instanceof Number number) {
            return NUMBER.format(number.doubleValue());
        }
        return String.valueOf(value);
    }
}

