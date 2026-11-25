package me.micahcode.hqtiers.client.config;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public enum IconSet {
    ORIGINAL("Original", "default"), PVPHQ("PvPHQ", "pvphq"), CUSTOM("Custom", "custom");

    public final String label;
    public final Style style;

    IconSet(String label, String font) {
        this.label = label;
        this.style = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("hqtiers", font))).withColor(0xFFFFFF);
    }
}
