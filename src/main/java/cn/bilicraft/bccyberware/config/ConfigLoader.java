package cn.bilicraft.bccyberware.config;

import cn.bilicraft.bccyberware.config.model.ActionSpec;
import cn.bilicraft.bccyberware.config.model.CapacitySettings;
import cn.bilicraft.bccyberware.config.model.ConditionSpec;
import cn.bilicraft.bccyberware.config.model.ConfigSnapshot;
import cn.bilicraft.bccyberware.config.model.GuiSettings;
import cn.bilicraft.bccyberware.config.model.ItemDefinition;
import cn.bilicraft.bccyberware.config.model.PackDefinition;
import cn.bilicraft.bccyberware.config.model.ResourcePackSpec;
import cn.bilicraft.bccyberware.config.model.SlotDefinition;
import cn.bilicraft.bccyberware.config.model.ThresholdRule;
import cn.bilicraft.bccyberware.config.model.TriggerSpec;
import cn.bilicraft.bccyberware.config.model.TriggerType;
import cn.bilicraft.bccyberware.config.model.ValueSourceSpec;
import cn.bilicraft.bccyberware.util.Comparison;
import cn.bilicraft.bccyberware.util.NumericFormula;
import cn.bilicraft.bccyberware.util.TimeParser;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class ConfigLoader {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]*");
    private static final Pattern MODEL_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Set<String> ACTION_TYPES = Set.of(
            "POTION", "DAMAGE", "HEAL", "MESSAGE", "ACTION_BAR", "TITLE", "SOUND", "PARTICLE",
            "COMMAND", "ATTRIBUTE", "DAMAGE_NEARBY"
    );
    private static final Set<String> CONDITION_TYPES = Set.of(
            "HEALTH_PERCENT", "SNEAKING", "PERMISSION", "WORLD", "TARGET_TYPE"
    );

    private final Path dataDirectory;

    ConfigLoader(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    ConfigSnapshot load() throws ConfigException {
        YamlConfiguration main = yaml(dataDirectory.resolve("config.yml"), "config.yml");
        int schemaVersion = integer(main, "config.yml", "schema-version", 1, 1, Integer.MAX_VALUE);

        boolean createDefaultOrgans = main.getBoolean("general.create-default-organs", true);
        long packDelay = ticks(main, "config.yml", "general.resource-pack-delay", "1s", true);
        long engineTick = ticks(main, "config.yml", "general.effect-engine-tick", "10t", false);
        long saveDebounce = ticks(main, "config.yml", "general.save-debounce", "2s", false);

        GuiSettings gui = loadGui(main);
        String databaseType = main.getString("database.type", "SQLITE").toUpperCase(Locale.ROOT);
        if (!databaseType.equals("SQLITE")) {
            throw error("config.yml", "database.type", "首版只支持 SQLITE");
        }
        String databaseFile = requiredString(main, "config.yml", "database.file");
        Path resolvedDatabase = dataDirectory.resolve(databaseFile).normalize();
        if (!resolvedDatabase.startsWith(dataDirectory.normalize())) {
            throw error("config.yml", "database.file", "数据库文件必须位于插件数据目录内");
        }

        List<ValueSourceSpec> sources = loadSources(main);
        List<ThresholdRule> thresholds = new ArrayList<>(loadThresholds(main, "config.yml", "capacity.thresholds", "global"));

        LinkedHashMap<String, PackDefinition> packs = loadPackMetadata();
        List<PackDefinition> orderedPacks = orderPacks(packs);
        LinkedHashMap<String, SlotDefinition> slots = new LinkedHashMap<>();
        LinkedHashMap<String, ItemDefinition> items = new LinkedHashMap<>();
        Set<Integer> occupiedGuiSlots = new HashSet<>();
        for (PackDefinition pack : orderedPacks) {
            Path packDirectory = dataDirectory.resolve("packs").resolve(pack.id());
            loadSlots(pack, packDirectory, gui.rows(), occupiedGuiSlots, slots);
            loadItems(pack, packDirectory.resolve("organs"), items);
            loadItems(pack, packDirectory.resolve("cyberware"), items);
            thresholds.addAll(loadPackThresholds(pack, packDirectory));
        }
        validateReferences(slots, items);

        CapacitySettings capacity = new CapacitySettings(
                main.getBoolean("capacity.enabled", true),
                finiteNumber(main, "config.yml", "capacity.base", 30.0),
                main.getBoolean("capacity.include-player-permanent", true),
                sources,
                thresholds
        );

        ResourcePackLoadResult resourcePacks = activeResourcePacks(loadResourcePacks(), orderedPacks);
        Map<String, String> messages = loadMessages();

        return new ConfigSnapshot(
                schemaVersion,
                createDefaultOrgans,
                packDelay,
                engineTick,
                saveDebounce,
                databaseFile,
                main.getBoolean("logging.warn-on-source-failure", true),
                main.getBoolean("logging.debug-effects", false),
                gui,
                capacity,
                packs,
                slots,
                items,
                resourcePacks.enabled,
                resourcePacks.packs,
                messages
        );
    }

    private GuiSettings loadGui(YamlConfiguration main) throws ConfigException {
        int rows = integer(main, "config.yml", "gui.rows", 6, 1, 6);
        int pageSize = integer(main, "config.yml", "gui.selector-page-size", 45, 1, 45);
        Material filler = Material.matchMaterial(main.getString("gui.filler.material", "BLACK_STAINED_GLASS_PANE"));
        if (filler == null || filler == Material.AIR) {
            throw error("config.yml", "gui.filler.material", "必须是有效的非空气物品材质");
        }
        return new GuiSettings(
                main.getBoolean("gui.command-open-anywhere", true),
                rows,
                main.getString("gui.title", "<dark_aqua>义体系统"),
                main.getString("gui.selector-title", "<dark_aqua>选择部件"),
                pageSize,
                filler,
                main.getString("gui.filler.name", "<black>.")
        );
    }

    private List<ValueSourceSpec> loadSources(YamlConfiguration main) throws ConfigException {
        List<ValueSourceSpec> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        List<Map<?, ?>> maps = main.getMapList("capacity.sources");
        for (int index = 0; index < maps.size(); index++) {
            String path = "capacity.sources[" + index + "]";
            Map<String, Object> values = stringMap(maps.get(index));
            String id = mapString(values, "id", null);
            validateLocalId("config.yml", path + ".id", id);
            if (!ids.add(id)) {
                throw error("config.yml", path + ".id", "数值源 ID 重复：" + id);
            }
            String type = mapString(values, "type", "FIXED").toUpperCase(Locale.ROOT);
            if (!Set.of("FIXED", "PLAYER_DATA", "PERMISSION", "MCMO_POWER_LEVEL", "MCMO_SKILL_LEVEL",
                    "PLACEHOLDER", "SCOREBOARD").contains(type)) {
                throw error("config.yml", path + ".type", "不支持的数值源类型：" + type);
            }
            String operation = mapString(values, "operation", "ADD").toUpperCase(Locale.ROOT);
            if (!Set.of("ADD", "SUBTRACT", "SET", "MIN", "MAX").contains(operation)) {
                throw error("config.yml", path + ".operation", "合法值：ADD、SUBTRACT、SET、MIN、MAX");
            }
            String formula = mapString(values, "formula", "value");
            try {
                NumericFormula.evaluate(formula, 12.5);
            } catch (IllegalArgumentException exception) {
                throw new ConfigException("config.yml", path + ".formula", exception.getMessage(), exception);
            }
            double min = mapNumber(values, "min", -Double.MAX_VALUE);
            double max = mapNumber(values, "max", Double.MAX_VALUE);
            if (min > max) {
                throw error("config.yml", path, "min 不能大于 max");
            }
            long refresh = mapTicks(values, "refresh-interval", "5s", "config.yml", path, false);
            result.add(new ValueSourceSpec(
                    id,
                    mapBoolean(values, "enabled", true),
                    type,
                    operation,
                    formula,
                    min,
                    max,
                    mapNumber(values, "fallback", 0.0),
                    refresh,
                    values
            ));
        }
        return result;
    }

    private List<ThresholdRule> loadThresholds(
            YamlConfiguration yaml,
            String file,
            String path,
            String namespace
    ) throws ConfigException {
        List<ThresholdRule> result = new ArrayList<>();
        List<Map<?, ?>> maps = yaml.getMapList(path);
        for (int index = 0; index < maps.size(); index++) {
            String itemPath = path + "[" + index + "]";
            Map<String, Object> values = stringMap(maps.get(index));
            String localId = mapString(values, "id", null);
            validateLocalId(file, itemPath + ".id", localId);
            String metric = mapString(values, "metric", "USED_PERCENT").toUpperCase(Locale.ROOT);
            if (!Set.of("INSTALLED_COUNT", "USED_CAPACITY", "USED_PERCENT").contains(metric)) {
                throw error(file, itemPath + ".metric", "合法值：INSTALLED_COUNT、USED_CAPACITY、USED_PERCENT");
            }
            Comparison comparison = enumValue(Comparison.class, mapString(values, "comparison", "GTE"), file,
                    itemPath + ".comparison");
            long interval = mapTicks(values, "interval", "10s", file, itemPath, false);
            result.add(new ThresholdRule(
                    namespace + ":" + localId,
                    mapBoolean(values, "enabled", true),
                    metric,
                    comparison,
                    mapNumber(values, "value", 0.0),
                    interval,
                    parseActions(values.get("actions"), file, itemPath + ".actions")
            ));
        }
        return result;
    }

    private LinkedHashMap<String, PackDefinition> loadPackMetadata() throws ConfigException {
        Path root = dataDirectory.resolve("packs");
        LinkedHashMap<String, PackDefinition> result = new LinkedHashMap<>();
        if (!Files.isDirectory(root)) {
            throw error("packs", "packs", "Pack 目录不存在");
        }
        List<Path> directories;
        try (var stream = Files.list(root)) {
            directories = stream.filter(Files::isDirectory).sorted().toList();
        } catch (IOException exception) {
            throw new ConfigException("packs", "packs", "无法读取 Pack 目录", exception);
        }
        for (Path directory : directories) {
            Path manifest = directory.resolve("pack.yml");
            if (!Files.isRegularFile(manifest)) {
                continue;
            }
            String relative = relative(manifest);
            YamlConfiguration yaml = yaml(manifest, relative);
            if (!yaml.getBoolean("enabled", true)) {
                continue;
            }
            String id = requiredString(yaml, relative, "id");
            validateLocalId(relative, "id", id);
            if (!directory.getFileName().toString().equals(id)) {
                throw error(relative, "id", "Pack ID 必须与目录名一致：" + directory.getFileName());
            }
            String namespace = yaml.getString("namespace", id);
            validateLocalId(relative, "namespace", namespace);
            PackDefinition pack = new PackDefinition(
                    id,
                    namespace,
                    yaml.getString("display-name", id),
                    yaml.getString("version", "1.0.0"),
                    yaml.getString("description", ""),
                    yaml.getInt("priority", 0),
                    lowerList(yaml.getStringList("depends")),
                    lowerList(yaml.getStringList("soft-depends")),
                    yaml.getBoolean("allow-overrides", false),
                    lowerList(yaml.getStringList("resource-packs"))
            );
            if (result.putIfAbsent(id, pack) != null) {
                throw error(relative, "id", "Pack ID 重复：" + id);
            }
        }
        if (result.isEmpty()) {
            throw error("packs", "packs", "没有任何启用且包含 pack.yml 的 Pack");
        }
        return result;
    }

    private List<PackDefinition> orderPacks(Map<String, PackDefinition> packs) throws ConfigException {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Set<String>> outgoing = new HashMap<>();
        for (PackDefinition pack : packs.values()) {
            indegree.put(pack.id(), 0);
            outgoing.put(pack.id(), new LinkedHashSet<>());
        }
        for (PackDefinition pack : packs.values()) {
            for (String dependency : pack.depends()) {
                if (!packs.containsKey(dependency)) {
                    throw error("packs/" + pack.id() + "/pack.yml", "depends", "缺少启用的硬依赖 Pack：" + dependency);
                }
                addEdge(dependency, pack.id(), indegree, outgoing);
            }
            for (String dependency : pack.softDepends()) {
                if (packs.containsKey(dependency)) {
                    addEdge(dependency, pack.id(), indegree, outgoing);
                }
            }
        }
        Comparator<PackDefinition> comparator = Comparator.comparingInt(PackDefinition::priority)
                .thenComparing(PackDefinition::id);
        PriorityQueue<PackDefinition> ready = new PriorityQueue<>(comparator);
        packs.values().stream().filter(pack -> indegree.get(pack.id()) == 0).forEach(ready::add);
        List<PackDefinition> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            PackDefinition pack = ready.remove();
            result.add(pack);
            for (String target : outgoing.get(pack.id())) {
                int next = indegree.merge(target, -1, Integer::sum);
                if (next == 0) {
                    ready.add(packs.get(target));
                }
            }
        }
        if (result.size() != packs.size()) {
            throw error("packs", "depends", "Pack 依赖关系中存在循环");
        }
        return result;
    }

    private void loadSlots(
            PackDefinition pack,
            Path packDirectory,
            int rows,
            Set<Integer> occupied,
            Map<String, SlotDefinition> target
    ) throws ConfigException {
        Path path = packDirectory.resolve("slots.yml");
        if (!Files.isRegularFile(path)) {
            return;
        }
        String file = relative(path);
        YamlConfiguration yaml = yaml(path, file);
        for (String localId : yaml.getKeys(false)) {
            validateLocalId(file, localId, localId);
            String fullId = qualify(pack.namespace(), localId);
            ConfigurationSection section = yaml.getConfigurationSection(localId);
            if (section == null) {
                throw error(file, localId, "槽位必须是 YAML 对象");
            }
            String type = requiredString(section, file, localId + ".type").toUpperCase(Locale.ROOT);
            int guiSlot;
            if (section.contains("gui-slot")) {
                guiSlot = integer(section, file, localId + ".gui-slot", 0, 0, rows * 9 - 1);
            } else {
                guiSlot = firstFree(occupied, rows * 9);
                if (guiSlot < 0) {
                    throw error(file, localId + ".gui-slot", "没有可用的默认 GUI 格子，请显式配置并扩大 gui.rows");
                }
            }
            if (!occupied.add(guiSlot)) {
                throw error(file, localId + ".gui-slot", "GUI 格子 " + guiSlot + " 已被其他槽位占用");
            }
            String defaultOrgan = section.getString("default-organ", "").trim();
            if (!defaultOrgan.isEmpty()) {
                defaultOrgan = qualify(pack.namespace(), defaultOrgan);
            }
            SlotDefinition definition = new SlotDefinition(
                    fullId,
                    type,
                    section.getString("display-name", localId),
                    guiSlot,
                    defaultOrgan,
                    parseTriggers(section.getMapList("empty-effects"), file, localId + ".empty-effects", fullId + "/empty")
            );
            putDefinition(target, fullId, definition, pack, file, localId);
        }
    }

    private void loadItems(PackDefinition pack, Path directory, Map<String, ItemDefinition> target) throws ConfigException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        List<Path> files;
        try (var stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new ConfigException(relative(directory), "items", "无法扫描定义目录", exception);
        }
        for (Path path : files) {
            String file = relative(path);
            YamlConfiguration yaml = yaml(path, file);
            ConfigurationSection items = yaml.getConfigurationSection("items");
            if (items == null) {
                throw error(file, "items", "文件必须包含 items 对象");
            }
            for (String localId : items.getKeys(false)) {
                validateLocalId(file, "items." + localId, localId);
                String fullId = qualify(pack.namespace(), localId);
                String basePath = "items." + localId;
                ConfigurationSection section = items.getConfigurationSection(localId);
                if (section == null) {
                    throw error(file, basePath, "部件必须是 YAML 对象");
                }
                String slotType = requiredString(section, file, basePath + ".slot-type").toUpperCase(Locale.ROOT);
                String materialName = section.getString("material", "PAPER");
                Material material = Material.matchMaterial(materialName);
                if (material == null || material == Material.AIR) {
                    throw error(file, basePath + ".material", "不是有效的非空气材质：" + materialName);
                }
                String itemModel = section.getString("item-model", "").trim();
                if (!itemModel.isEmpty() && !MODEL_KEY.matcher(itemModel).matches()) {
                    throw error(file, basePath + ".item-model", "需要 namespace:path 格式且只能使用小写字符");
                }
                Integer customModelData = section.contains("custom-model-data")
                        ? integer(section, file, basePath + ".custom-model-data", 0, 0, Integer.MAX_VALUE)
                        : null;
                double capacity = finiteNumber(section, file, basePath + ".capacity-cost", 0.0);
                if (capacity < 0) {
                    throw error(file, basePath + ".capacity-cost", "容量消耗不能为负数");
                }
                ItemDefinition definition = new ItemDefinition(
                        fullId,
                        slotType,
                        material,
                        itemModel,
                        customModelData,
                        capacity,
                        section.getBoolean("original-organ", false),
                        section.getString("display-name", "<white>" + localId),
                        section.getStringList("lore"),
                        parseTriggers(section.getMapList("triggers"), file, basePath + ".triggers", fullId)
                );
                putDefinition(target, fullId, definition, pack, file, basePath);
            }
        }
    }

    private List<ThresholdRule> loadPackThresholds(PackDefinition pack, Path packDirectory) throws ConfigException {
        Path directory = packDirectory.resolve("thresholds");
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<ThresholdRule> result = new ArrayList<>();
        List<Path> files;
        try (var stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted().toList();
        } catch (IOException exception) {
            throw new ConfigException(relative(directory), "thresholds", "无法扫描阈值目录", exception);
        }
        for (Path path : files) {
            String file = relative(path);
            result.addAll(loadThresholds(yaml(path, file), file, "thresholds", pack.namespace()));
        }
        return result;
    }

    private void validateReferences(Map<String, SlotDefinition> slots, Map<String, ItemDefinition> items) throws ConfigException {
        Set<String> slotTypes = new HashSet<>();
        for (SlotDefinition slot : slots.values()) {
            if (!slotTypes.add(slot.type())) {
                throw error("packs", "slots." + slot.id() + ".type",
                        "每种槽位类型只能对应一个身体位置，重复类型：" + slot.type());
            }
        }
        for (ItemDefinition item : items.values()) {
            if (!slotTypes.contains(item.slotType())) {
                throw error("packs", "items." + item.id() + ".slot-type",
                        "没有任何槽位接受类型 " + item.slotType());
            }
        }
        for (SlotDefinition slot : slots.values()) {
            if (slot.defaultOrganId().isEmpty()) {
                continue;
            }
            ItemDefinition item = items.get(slot.defaultOrganId());
            if (item == null) {
                throw error("packs", "slots." + slot.id() + ".default-organ",
                        "找不到部件 " + slot.defaultOrganId());
            }
            if (!item.originalOrgan()) {
                throw error("packs", "slots." + slot.id() + ".default-organ",
                        "默认器官必须设置 original-organ: true");
            }
            if (!item.slotType().equals(slot.type())) {
                throw error("packs", "slots." + slot.id() + ".default-organ",
                        "默认器官类型 " + item.slotType() + " 与槽位类型 " + slot.type() + " 不一致");
            }
        }
    }

    private ResourcePackLoadResult loadResourcePacks() throws ConfigException {
        String file = "resources.yml";
        YamlConfiguration yaml = yaml(dataDirectory.resolve(file), file);
        boolean enabled = yaml.getBoolean("enabled", false);
        List<ResourcePackSpec> packs = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        List<Map<?, ?>> maps = yaml.getMapList("packs");
        for (int index = 0; index < maps.size(); index++) {
            String path = "packs[" + index + "]";
            Map<String, Object> values = stringMap(maps.get(index));
            String id = mapString(values, "id", null);
            validateLocalId(file, path + ".id", id);
            if (!ids.add(id)) {
                throw error(file, path + ".id", "资源包 ID 重复：" + id);
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(mapString(values, "uuid", ""));
            } catch (IllegalArgumentException exception) {
                throw new ConfigException(file, path + ".uuid", "需要标准 UUID", exception);
            }
            String url = mapString(values, "url", "");
            if (!url.matches("https?://.+")) {
                throw error(file, path + ".url", "需要客户端可直接下载的 HTTP 或 HTTPS URL");
            }
            String hash = mapString(values, "sha1", "");
            if (!hash.matches("[0-9a-fA-F]{40}")) {
                throw error(file, path + ".sha1", "需要 40 位十六进制 SHA-1");
            }
            packs.add(new ResourcePackSpec(
                    id,
                    uuid,
                    url,
                    hex(hash),
                    mapBoolean(values, "required", false),
                    mapString(values, "prompt", "")
            ));
        }
        if (enabled && packs.isEmpty()) {
            throw error(file, "packs", "enabled=true 时至少需要一个资源包");
        }
        return new ResourcePackLoadResult(enabled, packs);
    }

    private ResourcePackLoadResult activeResourcePacks(
            ResourcePackLoadResult configured,
            List<PackDefinition> loadedPacks
    ) throws ConfigException {
        if (!configured.enabled) {
            return configured;
        }
        Set<String> requested = new LinkedHashSet<>();
        loadedPacks.forEach(pack -> requested.addAll(pack.resourcePacks()));
        if (requested.isEmpty()) {
            return configured;
        }
        Set<String> available = new HashSet<>();
        configured.packs.forEach(pack -> available.add(pack.id()));
        for (String id : requested) {
            if (!available.contains(id)) {
                throw error("resources.yml", "packs", "已启用的内容 Pack 引用了未声明的资源包：" + id);
            }
        }
        List<ResourcePackSpec> selected = configured.packs.stream()
                .filter(pack -> requested.contains(pack.id()))
                .toList();
        return new ResourcePackLoadResult(true, selected);
    }

    private Map<String, String> loadMessages() throws ConfigException {
        String file = "messages.yml";
        YamlConfiguration yaml = yaml(dataDirectory.resolve(file), file);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                result.put(key, yaml.getString(key, ""));
            }
        }
        if (!result.containsKey("prefix")) {
            throw error(file, "prefix", "缺少消息前缀");
        }
        return result;
    }

    private List<TriggerSpec> parseTriggers(
            List<Map<?, ?>> maps,
            String file,
            String path,
            String keyPrefix
    ) throws ConfigException {
        List<TriggerSpec> result = new ArrayList<>();
        for (int index = 0; index < maps.size(); index++) {
            String triggerPath = path + "[" + index + "]";
            Map<String, Object> values = stringMap(maps.get(index));
            String configuredType = values.containsKey("type")
                    ? mapString(values, "type", "PERIODIC")
                    : mapString(values, "trigger", "PERIODIC");
            TriggerType type = enumValue(TriggerType.class, configuredType, file,
                    triggerPath + ".type");
            double chance = mapNumber(values, "chance", 1.0);
            if (chance < 0 || chance > 1) {
                throw error(file, triggerPath + ".chance", "概率范围必须是 0～1");
            }
            long interval = type == TriggerType.PERIODIC
                    ? mapTicks(values, "interval", "1s", file, triggerPath, false)
                    : mapTicks(values, "interval", "0t", file, triggerPath, true);
            long cooldown = mapTicks(values, "cooldown", "0t", file, triggerPath, true);
            List<ConditionSpec> conditions = parseConditions(values.get("conditions"), file, triggerPath + ".conditions");
            List<ActionSpec> actions = parseActions(values.get("actions"), file, triggerPath + ".actions");
            if (actions.isEmpty()) {
                throw error(file, triggerPath + ".actions", "触发器至少需要一个动作");
            }
            result.add(new TriggerSpec(keyPrefix + "/" + index, type, interval, chance, cooldown, conditions, actions));
        }
        return result;
    }

    private List<ConditionSpec> parseConditions(Object raw, String file, String path) throws ConfigException {
        List<Map<String, Object>> maps = listOfMaps(raw, file, path);
        List<ConditionSpec> result = new ArrayList<>();
        for (int index = 0; index < maps.size(); index++) {
            Map<String, Object> values = maps.get(index);
            String type = mapString(values, "type", "").toUpperCase(Locale.ROOT);
            if (!CONDITION_TYPES.contains(type)) {
                throw error(file, path + "[" + index + "].type", "不支持的条件类型：" + type);
            }
            result.add(new ConditionSpec(type, values));
        }
        return result;
    }

    private List<ActionSpec> parseActions(Object raw, String file, String path) throws ConfigException {
        List<Map<String, Object>> maps = listOfMaps(raw, file, path);
        List<ActionSpec> result = new ArrayList<>();
        for (int index = 0; index < maps.size(); index++) {
            Map<String, Object> values = maps.get(index);
            String type = mapString(values, "type", "").toUpperCase(Locale.ROOT);
            if (!ACTION_TYPES.contains(type)) {
                throw error(file, path + "[" + index + "].type", "不支持的动作类型：" + type);
            }
            double chance = mapNumber(values, "chance", 1.0);
            if (chance < 0 || chance > 1) {
                throw error(file, path + "[" + index + "].chance", "动作概率范围必须是 0～1");
            }
            result.add(new ActionSpec(type, values));
        }
        return result;
    }

    private List<Map<String, Object>> listOfMaps(Object raw, String file, String path) throws ConfigException {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw error(file, path, "需要 YAML 列表");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> map)) {
                throw error(file, path, "列表中的每一项都必须是 YAML 对象");
            }
            result.add(stringMap(map));
        }
        return result;
    }

    private YamlConfiguration yaml(Path path, String file) throws ConfigException {
        if (!Files.isRegularFile(path)) {
            throw error(file, "<file>", "文件不存在");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(path.toFile());
            return yaml;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new ConfigException(file, "<yaml>", "YAML 无法解析：" + exception.getMessage(), exception);
        }
    }

    private static void addEdge(
            String source,
            String target,
            Map<String, Integer> indegree,
            Map<String, Set<String>> outgoing
    ) {
        if (outgoing.get(source).add(target)) {
            indegree.merge(target, 1, Integer::sum);
        }
    }

    private static <T> void putDefinition(
            Map<String, T> target,
            String id,
            T definition,
            PackDefinition pack,
            String file,
            String path
    ) throws ConfigException {
        if (target.containsKey(id) && !pack.allowOverrides()) {
            throw error(file, path, "定义 ID 已存在且当前 Pack 未开启 allow-overrides：" + id);
        }
        target.put(id, definition);
    }

    private static int firstFree(Set<Integer> occupied, int size) {
        for (int index = 0; index < size; index++) {
            if (!occupied.contains(index)) {
                return index;
            }
        }
        return -1;
    }

    private static String qualify(String namespace, String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        return normalized.contains(":") ? normalized : namespace + ":" + normalized;
    }

    private static void validateLocalId(String file, String path, String id) throws ConfigException {
        if (id == null || !ID.matcher(id).matches()) {
            throw error(file, path, "ID 只能包含小写字母、数字、下划线和短横线，且必须以字母或数字开头");
        }
    }

    private static String requiredString(ConfigurationSection section, String file, String path) throws ConfigException {
        String localPath = localPath(section, path);
        String value = section.getString(localPath);
        if (value == null || value.isBlank()) {
            throw error(file, path, "不能为空");
        }
        return value.trim();
    }

    private static String localPath(ConfigurationSection section, String absolutePath) {
        String current = section.getCurrentPath();
        if (current == null || current.isEmpty()) {
            return absolutePath;
        }
        String prefix = current + ".";
        return absolutePath.startsWith(prefix) ? absolutePath.substring(prefix.length()) : absolutePath;
    }

    private static int integer(
            ConfigurationSection section,
            String file,
            String path,
            int fallback,
            int min,
            int max
    ) throws ConfigException {
        String localPath = localPath(section, path);
        Object raw = section.get(localPath);
        int value = raw == null ? fallback : raw instanceof Number number
                ? number.intValue() : parseInteger(String.valueOf(raw), file, path);
        if (value < min || value > max) {
            throw error(file, path, "合法范围是 " + min + "～" + max);
        }
        return value;
    }

    private static int parseInteger(String value, String file, String path) throws ConfigException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new ConfigException(file, path, "需要整数", exception);
        }
    }

    private static double finiteNumber(ConfigurationSection section, String file, String path, double fallback)
            throws ConfigException {
        String localPath = localPath(section, path);
        Object raw = section.get(localPath);
        double value;
        try {
            value = raw == null ? fallback : raw instanceof Number number
                    ? number.doubleValue() : Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException exception) {
            throw new ConfigException(file, path, "需要数字", exception);
        }
        if (!Double.isFinite(value)) {
            throw error(file, path, "需要有限数字");
        }
        return value;
    }

    private static long ticks(
            ConfigurationSection section,
            String file,
            String path,
            String fallback,
            boolean allowZero
    ) throws ConfigException {
        String localPath = localPath(section, path);
        String value = section.getString(localPath, fallback);
        try {
            long ticks = TimeParser.parseTicks(value);
            if (!allowZero && ticks == 0) {
                throw new IllegalArgumentException("时间必须大于 0t");
            }
            return ticks;
        } catch (IllegalArgumentException exception) {
            throw new ConfigException(file, path, exception.getMessage(), exception);
        }
    }

    private static long mapTicks(
            Map<String, Object> values,
            String key,
            String fallback,
            String file,
            String path,
            boolean allowZero
    ) throws ConfigException {
        String value = mapString(values, key, fallback);
        try {
            long ticks = TimeParser.parseTicks(value);
            if (!allowZero && ticks == 0) {
                throw new IllegalArgumentException("时间必须大于 0t");
            }
            return ticks;
        } catch (IllegalArgumentException exception) {
            throw new ConfigException(file, path + "." + key, exception.getMessage(), exception);
        }
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String mapString(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private static double mapNumber(Map<String, Object> map, String key, double fallback) throws ConfigException {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            double result = value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
            if (!Double.isFinite(result)) {
                throw new NumberFormatException("not finite");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new ConfigException("config", key, "需要有限数字", exception);
        }
    }

    private static boolean mapBoolean(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String file, String path)
            throws ConfigException {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ConfigException(file, path, "不支持的值：" + value, exception);
        }
    }

    private static List<String> lowerList(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }

    private static byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        }
        return bytes;
    }

    private String relative(Path path) {
        return dataDirectory.relativize(path).toString().replace('\\', '/');
    }

    private static ConfigException error(String file, String path, String message) {
        return new ConfigException(file, path, message);
    }

    private record ResourcePackLoadResult(boolean enabled, List<ResourcePackSpec> packs) {
    }
}
