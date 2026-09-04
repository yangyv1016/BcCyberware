package cn.bilicraft.bccyberware.item;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemAppearanceTest {
    @Test
    void migratesCustomModelToVanillaKeyWithoutTouchingOtherMetadata() {
        MetaState state = new MetaState();
        assertTrue(ItemAppearance.apply(state.meta, "bccyberware:native_heart", Material.PAPER));
        assertNull(state.model);
        assertEquals(List.of("bccyberware/bccyberware:native_heart", "kept"), state.strings);
        assertEquals(List.of(123f), state.floats);
        assertFalse(ItemAppearance.apply(state.meta, "bccyberware:native_heart", Material.PAPER));
    }

    @Test
    void removingModelClearsOnlyOwnedSelectorAndPreservesOtherIndices() {
        MetaState state = new MetaState();
        ItemAppearance.apply(state.meta, "bccyberware:native_heart", Material.PAPER);
        assertTrue(ItemAppearance.apply(state.meta, "", Material.PAPER));
        assertEquals(List.of("", "kept"), state.strings);
        assertEquals(List.of(123f), state.floats);
        assertFalse(ItemAppearance.apply(state.meta, "", Material.PAPER));
    }

    @Test
    void nonPaperCarrierStillUsesVanillaPaperModel() {
        MetaState state = new MetaState();
        assertTrue(ItemAppearance.apply(state.meta, "test:heart", Material.STONE));
        assertEquals(NamespacedKey.minecraft("paper"), state.model);
        assertFalse(ItemAppearance.apply(state.meta, "test:heart", Material.STONE));
    }

    private static final class MetaState {
        NamespacedKey model = NamespacedKey.fromString("bccyberware:native_heart");
        List<String> strings = new ArrayList<>(List.of("legacy", "kept"));
        final List<Float> floats = List.of(123f);
        final CustomModelDataComponent component = (CustomModelDataComponent) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{CustomModelDataComponent.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getStrings" -> List.copyOf(strings);
                        case "setStrings" -> { strings = new ArrayList<>(((List<?>) args[0]).stream().map(String.class::cast).toList()); yield null; }
                        case "getFloats" -> floats;
                        default -> throw new AssertionError("Unexpected data mutation: " + method);
                    };
                });
        final ItemMeta meta = (ItemMeta) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ItemMeta.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getItemModel" -> model;
                        case "setItemModel" -> { model = (NamespacedKey) args[0]; yield null; }
                        case "getCustomModelDataComponent" -> component;
                        case "setCustomModelDataComponent" -> null;
                        // Names, lore, PDC, stack size and all other data must stay untouched.
                        default -> throw new AssertionError("Unexpected metadata mutation: " + method);
                    };
                });
    }
}
