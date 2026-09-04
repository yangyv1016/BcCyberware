package cn.bilicraft.bccyberware.config.model;

import org.bukkit.Material;

public record GuiSettings(
        boolean commandOpenAnywhere,
        int rows,
        String title,
        String selectorTitle,
        int selectorPageSize,
        Material fillerMaterial,
        String fillerName
) {
}

