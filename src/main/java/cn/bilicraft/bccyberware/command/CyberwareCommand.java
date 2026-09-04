package cn.bilicraft.bccyberware.command;

import cn.bilicraft.bccyberware.capacity.CapacityService;
import cn.bilicraft.bccyberware.config.ConfigManager;
import cn.bilicraft.bccyberware.config.model.ItemDefinition;
import cn.bilicraft.bccyberware.data.PlayerProfile;
import cn.bilicraft.bccyberware.data.ProfileService;
import cn.bilicraft.bccyberware.effect.EffectEngine;
import cn.bilicraft.bccyberware.gui.MenuService;
import cn.bilicraft.bccyberware.item.CyberwareIdentity;
import cn.bilicraft.bccyberware.item.ItemService;
import cn.bilicraft.bccyberware.resourcepack.ResourcePackService;
import cn.bilicraft.bccyberware.text.TextService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CyberwareCommand implements CommandExecutor, TabCompleter {
    private final ConfigManager configs;
    private final ProfileService profiles;
    private final ItemService items;
    private final CapacityService capacity;
    private final EffectEngine effects;
    private final MenuService menus;
    private final ResourcePackService resourcePacks;
    private final TextService text;

    public CyberwareCommand(
            ConfigManager configs,
            ProfileService profiles,
            ItemService items,
            CapacityService capacity,
            EffectEngine effects,
            MenuService menus,
            ResourcePackService resourcePacks,
            TextService text
    ) {
        this.configs = configs;
        this.profiles = profiles;
        this.items = items;
        this.capacity = capacity;
        this.effects = effects;
        this.menus = menus;
        this.resourcePacks = resourcePacks;
        this.text = text;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            return open(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "open" -> open(sender);
            case "give" -> give(sender, args);
            case "capacity" -> capacity(sender, args);
            case "reload" -> reload(sender);
            case "inspect" -> inspect(sender);
            case "pack", "packs" -> listPacks(sender);
            case "resourcepack" -> sendResourcePack(sender, args);
            default -> {
                text.send(sender, "usage");
                yield true;
            }
        };
    }

    private boolean open(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            text.send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("bccyberware.use")) {
            text.send(player, "no-permission");
            return true;
        }
        if (!configs.current().gui().commandOpenAnywhere() && !player.hasPermission("bccyberware.admin")) {
            text.send(player, "no-permission");
            return true;
        }
        menus.openMain(player);
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bccyberware.admin.give")) {
            text.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            text.send(sender, "usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            text.send(sender, "unknown-player", text.placeholders("player", args[1]));
            return true;
        }
        ItemDefinition definition = resolveItem(args[2]).orElse(null);
        if (definition == null) {
            text.send(sender, "unknown-cyberware", text.placeholders("id", args[2]));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                text.send(sender, "invalid-number", text.placeholders("value", args[3]));
                return true;
            }
        }
        amount = Math.max(1, Math.min(64, amount));
        if (freeStorageSlots(target) < amount) {
            text.send(sender, "inventory-full");
            return true;
        }
        for (int index = 0; index < amount; index++) {
            ItemStack item = items.create(definition.id(), definition.originalOrgan() ? target : null);
            target.getInventory().addItem(item);
        }
        text.send(sender, "item-given", text.placeholders(
                "player", target.getName(),
                "item", plainName(definition),
                "amount", amount
        ));
        return true;
    }

    private boolean capacity(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bccyberware.admin.capacity")) {
            text.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            text.send(sender, "usage");
            return true;
        }
        String operation = args[1].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            text.send(sender, "unknown-player", text.placeholders("player", args[2]));
            return true;
        }
        PlayerProfile profile = profiles.profile(target.getUniqueId()).orElse(null);
        if (profile == null) {
            text.send(sender, "profile-loading");
            return true;
        }
        if (operation.equals("get")) {
            text.send(sender, "capacity-result", text.placeholders(
                    "player", target.getName(),
                    "permanent", profile.permanentCapacity(),
                    "used", capacity.used(profile),
                    "total", capacity.maximum(target, profile)
            ));
            return true;
        }
        if (args.length < 4) {
            text.send(sender, "usage");
            return true;
        }
        double value;
        try {
            value = Double.parseDouble(args[3]);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("not finite");
            }
        } catch (NumberFormatException exception) {
            text.send(sender, "invalid-number", text.placeholders("value", args[3]));
            return true;
        }
        switch (operation) {
            case "set" -> profile.permanentCapacity(value);
            case "add" -> profile.permanentCapacity(profile.permanentCapacity() + value);
            default -> {
                text.send(sender, "usage");
                return true;
            }
        }
        capacity.invalidate(target.getUniqueId());
        profiles.requestSave(profile);
        text.send(sender, "capacity-updated", text.placeholders(
                "player", target.getName(), "value", profile.permanentCapacity()));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("bccyberware.admin.reload")) {
            text.send(sender, "no-permission");
            return true;
        }
        if (resourcePacks.isBusy()) {
            text.send(sender, "resource-pack-busy");
            return true;
        }
        if (!configs.reload()) {
            text.send(sender, "config-reload-failed");
            return true;
        }
        capacity.invalidateAll();
        menus.closeAllMenus();
        effects.reload();
        boolean scheduled = resourcePacks.reloadDeploymentAsync(success -> text.send(
                sender,
                success ? "config-reloaded" : "config-reloaded-resource-pack-kept"
        ));
        if (!scheduled) {
            text.send(sender, "config-reloaded-resource-pack-kept");
            return true;
        }
        text.send(sender, "config-reload-resource-pack-started");
        return true;
    }

    private boolean inspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            text.send(sender, "player-only");
            return true;
        }
        if (!sender.hasPermission("bccyberware.admin.inspect")) {
            text.send(sender, "no-permission");
            return true;
        }
        CyberwareIdentity identity = items.inspect(player.getInventory().getItemInMainHand()).orElse(null);
        if (identity == null) {
            text.send(player, "not-cyberware");
            return true;
        }
        ItemDefinition definition = configs.current().items().get(identity.definitionId());
        text.send(player, "inspect-result", text.placeholders(
                "id", identity.definitionId(),
                "slot", definition == null ? "UNKNOWN" : definition.slotType(),
                "instance", identity.instanceId(),
                "owner", identity.originalOwnerName() == null ? "无" : identity.originalOwnerName()
        ));
        return true;
    }

    private boolean listPacks(CommandSender sender) {
        if (!sender.hasPermission("bccyberware.admin")) {
            text.send(sender, "no-permission");
            return true;
        }
        sender.sendMessage(text.render("<dark_aqua>已加载内容 Pack <gray>(" + configs.current().packs().size() + ")："));
        configs.current().packs().values().stream()
                .sorted(java.util.Comparator.comparingInt(pack -> pack.priority()))
                .forEach(pack -> sender.sendMessage(text.render(
                        "<gray>- <aqua>" + pack.id() + " <dark_gray>[" + pack.namespace() + "] <white>"
                                + pack.displayName() + " <gray>v" + pack.version())));
        return true;
    }

    private boolean sendResourcePack(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bccyberware.admin.resourcepack")) {
            text.send(sender, "no-permission");
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("generate")) {
            boolean scheduled = resourcePacks.generateAsync(success -> text.send(
                    sender,
                    success ? "resource-pack-prepared" : "resource-pack-generate-failed"
            ));
            if (scheduled) {
                text.send(sender, "resource-pack-generation-started");
            } else {
                text.send(sender, "resource-pack-generate-failed");
            }
            return true;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player player ? player : null;
        if (target == null) {
            text.send(sender, "unknown-player", text.placeholders("player", args.length >= 2 ? args[1] : ""));
            return true;
        }
        resourcePacks.send(target);
        return true;
    }

    private Optional<ItemDefinition> resolveItem(String requested) {
        String id = requested.toLowerCase(Locale.ROOT);
        ItemDefinition direct = configs.current().items().get(id);
        if (direct != null) {
            return Optional.of(direct);
        }
        List<ItemDefinition> matches = configs.current().items().values().stream()
                .filter(item -> item.id().endsWith(":" + id))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private int freeStorageSlots(Player player) {
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                free++;
            }
        }
        return free;
    }

    private String plainName(ItemDefinition definition) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(text.render(definition.displayName().replace("<owner>", "原生主人")));
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        List<String> candidates = new ArrayList<>();
        if (args.length == 1) {
            candidates.addAll(List.of("open", "give", "capacity", "reload", "inspect", "pack", "resourcepack"));
        } else if (args.length == 2 && SetLike.contains(args[0], "give", "resourcepack")) {
            if (args[0].equalsIgnoreCase("resourcepack")) {
                candidates.add("generate");
            }
            Bukkit.getOnlinePlayers().forEach(player -> candidates.add(player.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("capacity")) {
            candidates.addAll(List.of("get", "set", "add"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("capacity")) {
            Bukkit.getOnlinePlayers().forEach(player -> candidates.add(player.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            candidates.addAll(configs.current().items().keySet());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    private static final class SetLike {
        private SetLike() {
        }

        static boolean contains(String value, String... candidates) {
            for (String candidate : candidates) {
                if (candidate.equalsIgnoreCase(value)) {
                    return true;
                }
            }
            return false;
        }
    }
}
