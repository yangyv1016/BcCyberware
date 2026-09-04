package cn.bilicraft.bccyberware.item;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Vanilla always knows this model; the resource pack adds the optional selector. */
public final class ItemAppearance {
    public static final String SELECTOR_PREFIX = "bccyberware/";
    private static final NamespacedKey PAPER = NamespacedKey.minecraft("paper");

    private ItemAppearance() { }

    public static String selector(String model) {
        return SELECTOR_PREFIX + model;
    }

    public static boolean apply(ItemMeta meta, String model, Material material) {
        // Paper strips redundant item_model overrides equal to the material default.
        // Use no override for PAPER, an explicit vanilla paper model for other carriers.
        NamespacedKey base = material == Material.PAPER ? null : PAPER;
        boolean changed = !Objects.equals(base, meta.getItemModel());
        if (changed) {
            meta.setItemModel(base);
        }
        var component = meta.getCustomModelDataComponent();
        List<String> strings = new ArrayList<>(component.getStrings());
        String value = model.isEmpty() ? "" : selector(model);
        if (!value.isEmpty() && (strings.isEmpty() || !value.equals(strings.getFirst()))) {
            if (strings.isEmpty()) {
                strings.add(value);
            } else {
                strings.set(0, value);
            }
            component.setStrings(strings);
            meta.setCustomModelDataComponent(component);
            changed = true;
        } else if (value.isEmpty() && !strings.isEmpty() && strings.getFirst().startsWith(SELECTOR_PREFIX)) {
            // Keep later string indices and all numeric/boolean/color data untouched.
            strings.set(0, "");
            component.setStrings(strings);
            meta.setCustomModelDataComponent(component);
            changed = true;
        }
        return changed;
    }
}
