package me.micahcode.hqtiers.client.leaderboard;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class RankedScreenStyle {
    static final int GOLD = 0xFFD4AF37;

    private RankedScreenStyle() {}

    static int panelWidth(int width) {
        return Math.min(540, Math.max(1, width - 40));
    }

    static int top(int height) {
        return Math.max(10, (height - 360) / 2);
    }

    static int bottom(int height) {
        return height - top(height);
    }

    static void frame(GuiGraphicsExtractor ctx, Font font, int width, int height, int left, int right, String section) {
        ctx.fill(0, 0, width, height, 0xF0100C05);
        ctx.fill(0, 0, width / 4, height, 0x08FFFFFF);
    }
}
