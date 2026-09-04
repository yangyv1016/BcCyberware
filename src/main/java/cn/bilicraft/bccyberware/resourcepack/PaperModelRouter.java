package cn.bilicraft.bccyberware.resourcepack;

import cn.bilicraft.bccyberware.config.model.ItemDefinition;
import cn.bilicraft.bccyberware.item.ItemAppearance;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.TreeSet;

/** Adds our string selectors without discarding Oraxen's existing paper model tree. */
final class PaperModelRouter {
    static final String PATH = "assets/minecraft/items/paper.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PaperModelRouter() { }

    static void generate(Path root, Collection<ItemDefinition> items) throws IOException {
        TreeSet<String> models = new TreeSet<>();
        items.stream().map(ItemDefinition::itemModel).filter(model -> !model.isEmpty()).forEach(models::add);
        if (models.isEmpty()) {
            return;
        }
        JsonArray cases = new JsonArray();
        for (String model : models) {
            int colon = model.indexOf(':');
            String namespace = colon < 0 ? "minecraft" : model.substring(0, colon);
            String name = colon < 0 ? model : model.substring(colon + 1);
            Path file = root.resolve("assets/" + namespace + "/items/" + name + ".json").normalize();
            if (!file.startsWith(root)) {
                throw new IOException("物品模型路径越界：" + model);
            }
            // Missing optional art must still render paper, not a missing-model cube.
            JsonElement target = Files.isRegularFile(file)
                    ? parse(Files.readAllBytes(file)).get("model") : vanilla();
            if (target == null || !target.isJsonObject()) {
                throw new IOException("物品模型缺少 model 对象：" + model);
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("when", ItemAppearance.selector(model));
            entry.add("model", target);
            cases.add(entry);
        }
        Path output = root.resolve(PATH);
        JsonObject definition = Files.isRegularFile(output) ? parse(Files.readAllBytes(output)) : new JsonObject();
        definition.add("model", wrap(cases, baseModel(definition)));
        Files.createDirectories(output.getParent());
        Files.write(output, encode(definition));
    }

    static byte[] merge(byte[] incoming, byte[] existing) throws IOException {
        JsonObject additions = parse(incoming);
        JsonElement model = additions.get("model");
        if (!isOurRouter(model)) {
            throw new IOException("义体 paper.json 缺少专属字符串选择器，请重新生成义体资源包");
        }
        if (existing == null) {
            return incoming;
        }
        JsonObject preserved = parse(existing);
        preserved.add("model", wrap(model.getAsJsonObject().getAsJsonArray("cases"), baseModel(preserved)));
        return encode(preserved);
    }

    private static JsonElement baseModel(JsonObject definition) {
        JsonElement base = definition.get("model");
        // Reloading the same output must replace our routing layer, not nest it indefinitely.
        while (isOurRouter(base)) {
            base = base.getAsJsonObject().get("fallback");
        }
        return base == null ? vanilla() : base.deepCopy();
    }

    private static boolean isOurRouter(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return false;
        }
        JsonObject model = element.getAsJsonObject();
        if (!"minecraft:select".equals(string(model, "type"))
                || !"minecraft:custom_model_data".equals(string(model, "property"))
                || (model.has("index") && model.get("index").getAsInt() != 0)
                || !model.has("cases") || !model.get("cases").isJsonArray()) {
            return false;
        }
        JsonArray cases = model.getAsJsonArray("cases");
        return !cases.isEmpty() && cases.asList().stream().allMatch(entry -> entry.isJsonObject()
                && string(entry.getAsJsonObject(), "when").startsWith(ItemAppearance.SELECTOR_PREFIX));
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static JsonObject wrap(JsonArray cases, JsonElement fallback) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:select");
        model.addProperty("property", "minecraft:custom_model_data");
        model.addProperty("index", 0);
        model.add("cases", cases.deepCopy());
        model.add("fallback", fallback);
        return model;
    }

    private static JsonObject vanilla() {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", "minecraft:item/paper");
        return model;
    }

    private static JsonObject parse(byte[] bytes) throws IOException {
        try {
            JsonObject result = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!result.has("model") || !result.get("model").isJsonObject()) {
                throw new IOException("物品定义 JSON 缺少 model 对象");
            }
            return result;
        } catch (RuntimeException exception) {
            throw new IOException("无法解析物品定义 JSON", exception);
        }
    }

    private static byte[] encode(JsonObject object) {
        return (GSON.toJson(object) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
