package cn.bilicraft.bccyberware.resourcepack;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OraxenApiBridgeTest {
    @Test
    void mergesPaperDefinitionThroughRealVirtualFileAndCanRepeat() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        var access = OraxenApiBridge.ReflectionAccess.resolve(loader);
        Class<?> type = Class.forName("io.th0rgal.oraxen.utils.VirtualFile", false, loader);
        Constructor<?> constructor = type.getConstructor(String.class, String.class, InputStream.class);
        byte[] original = """
                {"hand_animation_on_swap":false,"model":{"type":"minecraft:model","model":"oraxen:item/kept"}}
                """.getBytes(StandardCharsets.UTF_8);
        byte[] incoming = """
                {"model":{"type":"minecraft:select","property":"minecraft:custom_model_data","index":0,
                "cases":[{"when":"bccyberware/test:heart","model":{"type":"minecraft:model","model":"test:item/heart"}}],
                "fallback":{"type":"minecraft:model","model":"minecraft:item/paper"}}}
                """.getBytes(StandardCharsets.UTF_8);
        List<Object> output = new ArrayList<>();
        output.add(constructor.newInstance("assets/minecraft/items", "paper.json",
                new java.io.ByteArrayInputStream(original)));
        Object event = access.generatedEventType().getConstructor(List.class).newInstance(output);
        Map<String, byte[]> assets = Map.of(PaperModelRouter.PATH, incoming);
        access.injectAssets(event, assets);
        access.injectAssets(event, assets);
        assertEquals(1, output.size());
        InputStream stream = (InputStream) type.getMethod("getInputStream").invoke(output.getFirst());
        assertArrayEquals(PaperModelRouter.merge(incoming, original), stream.readAllBytes());
    }

    @Test
    void invalidPaperMergeDoesNotRemoveExistingOutput() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        var access = OraxenApiBridge.ReflectionAccess.resolve(loader);
        Class<?> type = Class.forName("io.th0rgal.oraxen.utils.VirtualFile", false, loader);
        Object original = type.getConstructor(String.class, String.class, InputStream.class).newInstance(
                "assets/minecraft/items", "paper.json", new java.io.ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        List<Object> output = new ArrayList<>(List.of(original));
        Object event = access.generatedEventType().getConstructor(List.class).newInstance(output);
        assertThrows(OraxenApiBridge.IntegrationException.class,
                () -> access.injectAssets(event, Map.of(PaperModelRouter.PATH, new byte[0])));
        assertEquals(List.of(original), output);
        InputStream stream = (InputStream) type.getMethod("getInputStream").invoke(original);
        assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), stream.readAllBytes());
    }

    @Test
    void resolvesOfficialApiAndReplacesMatchingVirtualFiles() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        OraxenApiBridge.ReflectionAccess access = OraxenApiBridge.ReflectionAccess.resolve(loader);
        Class<?> virtualFileType = Class.forName("io.th0rgal.oraxen.utils.VirtualFile", false, loader);
        Constructor<?> virtualFileConstructor = virtualFileType.getConstructor(
                String.class,
                String.class,
                InputStream.class
        );

        List<Object> output = new ArrayList<>();
        output.add(virtualFileConstructor.newInstance(
                "assets/bccyberware/models/item",
                "heart.json",
                InputStream.nullInputStream()
        ));
        output.add(virtualFileConstructor.newInstance(
                "assets/oraxen/models/item",
                "kept.json",
                InputStream.nullInputStream()
        ));
        Object event = access.generatedEventType().getConstructor(List.class).newInstance(output);

        LinkedHashMap<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("assets/bccyberware/models/item/heart.json", "replacement".getBytes(StandardCharsets.UTF_8));
        assets.put("assets/bccyberware/textures/item/heart.png", new byte[]{1, 2, 3});
        access.injectAssets(event, assets);

        assertEquals(List.of(
                        "assets/oraxen/models/item/kept.json",
                        "assets/bccyberware/models/item/heart.json",
                        "assets/bccyberware/textures/item/heart.png"
                ), paths(output, virtualFileType));
        Method getInputStream = virtualFileType.getMethod("getInputStream");
        try (InputStream stream = (InputStream) getInputStream.invoke(output.get(1))) {
            assertArrayEquals("replacement".getBytes(StandardCharsets.UTF_8), stream.readAllBytes());
        }
    }

    @Test
    void reportsMissingOraxenApiWithoutLinkageError() {
        ClassLoader isolated = new ClassLoader(null) {
        };
        assertThrows(
                OraxenApiBridge.IntegrationException.class,
                () -> OraxenApiBridge.ReflectionAccess.resolve(isolated)
        );
    }

    private List<String> paths(List<Object> output, Class<?> virtualFileType) throws Exception {
        Method getPath = virtualFileType.getMethod("getPath");
        List<String> result = new ArrayList<>();
        for (Object virtualFile : output) {
            result.add((String) getPath.invoke(virtualFile));
        }
        return result;
    }
}
