package cn.bilicraft.bccyberware.resourcepack;

import cn.bilicraft.bccyberware.config.model.ItemDefinition;
import cn.bilicraft.bccyberware.item.ItemAppearance;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaperModelRouterTest {
    @TempDir Path root;

    @Test
    void mapsConfiguredModelAndFallsBackToVanillaPaper() throws Exception {
        write("assets/test/items/heart.json", """
                {"model":{"type":"minecraft:model","model":"test:item/heart"}}
                """);
        PaperModelRouter.generate(root, List.of(item("test:heart")));
        JsonObject router = result().getAsJsonObject("model");
        assertEquals("minecraft:select", router.get("type").getAsString());
        assertEquals("minecraft:custom_model_data", router.get("property").getAsString());
        assertEquals(0, router.get("index").getAsInt());
        JsonObject entry = router.getAsJsonArray("cases").get(0).getAsJsonObject();
        assertEquals(ItemAppearance.selector("test:heart"), entry.get("when").getAsString());
        assertEquals("test:item/heart", entry.getAsJsonObject("model").get("model").getAsString());
        assertEquals("minecraft:item/paper", router.getAsJsonObject("fallback").get("model").getAsString());
    }

    @Test
    void missingOptionalItemDefinitionUsesPaperInsteadOfMissingModel() throws Exception {
        PaperModelRouter.generate(root, List.of(item("test:missing")));
        JsonObject branch = result().getAsJsonObject("model").getAsJsonArray("cases")
                .get(0).getAsJsonObject().getAsJsonObject("model");
        assertEquals("minecraft:item/paper", branch.get("model").getAsString());
    }

    @Test
    void preservesOraxenNumericModelsAndTopLevelSettingsAcrossRepeatedMerges() throws Exception {
        PaperModelRouter.generate(root, List.of(item("test:heart")));
        byte[] incoming = Files.readAllBytes(root.resolve(PaperModelRouter.PATH));
        byte[] original = """
                {"hand_animation_on_swap":false,"model":{
                  "type":"minecraft:range_dispatch","property":"minecraft:custom_model_data",
                  "entries":[{"threshold":42,"model":{"type":"minecraft:model","model":"oraxen:item/old"}}],
                  "fallback":{"type":"minecraft:model","model":"minecraft:item/paper"}}}
                """.getBytes(StandardCharsets.UTF_8);
        byte[] merged = PaperModelRouter.merge(incoming, original);
        JsonObject definition = JsonParser.parseString(new String(merged, StandardCharsets.UTF_8)).getAsJsonObject();
        assertFalse(definition.get("hand_animation_on_swap").getAsBoolean());
        assertEquals(JsonParser.parseString(new String(original, StandardCharsets.UTF_8)).getAsJsonObject().get("model"),
                definition.getAsJsonObject("model").get("fallback"));
        assertArrayEquals(merged, PaperModelRouter.merge(incoming, merged));
    }

    @Test
    void generationReplacesStaleBundledRoutingAndIsDeterministic() throws Exception {
        PaperModelRouter.generate(root, List.of(item("test:old")));
        PaperModelRouter.generate(root, List.of(item("test:new"), item("test:new")));
        byte[] once = Files.readAllBytes(root.resolve(PaperModelRouter.PATH));
        assertEquals(1, result().getAsJsonObject("model").getAsJsonArray("cases").size());
        assertFalse(new String(once, StandardCharsets.UTF_8).contains("test:old"));
        PaperModelRouter.generate(root, List.of(item("test:new")));
        assertArrayEquals(once, Files.readAllBytes(root.resolve(PaperModelRouter.PATH)));
    }

    @Test
    void malformedAssetFailsRatherThanPublishingBrokenPack() throws Exception {
        write("assets/test/items/heart.json", "{}");
        assertThrows(IOException.class, () -> PaperModelRouter.generate(root, List.of(item("test:heart"))));
    }

    private JsonObject result() throws IOException {
        return JsonParser.parseString(Files.readString(root.resolve(PaperModelRouter.PATH))).getAsJsonObject();
    }

    private ItemDefinition item(String model) {
        return new ItemDefinition("test:heart", "heart", Material.PAPER, model, null,
                0, false, "Heart", List.of(), List.of());
    }

    private void write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
