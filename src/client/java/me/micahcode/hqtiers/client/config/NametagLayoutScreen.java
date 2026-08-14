package me.micahcode.hqtiers.client.config;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

public class NametagLayoutScreen extends Screen {
    private final Screen parent;
    private static final int ROW_H = 24;
    private static final int START_Y = 55;

    private final List<HqTiersClientConfig.NametagComponent> order;

    public NametagLayoutScreen(Screen parent) {
        super(Component.literal("Nametag Layout"));
        this.parent = parent;
        this.order = new ArrayList<>(HqTiersClientConfig.nametagOrder);
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2;
        int rowW = Math.min(420, width - 40);
        int left = cx - rowW / 2;

        for (int i = 0; i < order.size(); i++) {
            final int idx = i;
            HqTiersClientConfig.NametagComponent comp = order.get(i);
            int y = START_Y + i * ROW_H;

            if (i > 0) {
                addRenderableWidget(Button.builder(Component.literal("↑"), btn -> {
                    swap(idx - 1, idx);
                    init();
                }).bounds(left + 190, y, 20, 18).build());
            }

            if (i < order.size() - 1) {
                addRenderableWidget(Button.builder(Component.literal("↓"), btn -> {
                    swap(idx, idx + 1);
                    init();
                }).bounds(left + 214, y, 20, 18).build());
            }

            addRenderableWidget(Button.builder(
                    Component.literal(isEnabled(idx) ? "ON" : "OFF"),
                    btn -> {
                        toggle(idx);
                        btn.setMessage(Component.literal(isEnabled(idx) ? "ON" : "OFF"));
                    }
            ).bounds(left + 238, y, 50, 18).build());

            if (comp == HqTiersClientConfig.NametagComponent.ELO) {
                addRenderableWidget(Button.builder(
                        Component.literal("Label: " + (HqTiersClientConfig.eloLabelEnabled ? "ON" : "OFF")),
                        btn -> {
                            HqTiersClientConfig.eloLabelEnabled = !HqTiersClientConfig.eloLabelEnabled;
                            btn.setMessage(Component.literal("Label: " + (HqTiersClientConfig.eloLabelEnabled ? "ON" : "OFF")));
                        }
                ).bounds(left + 292, y, 80, 18).build());
            }

            if (comp == HqTiersClientConfig.NametagComponent.POSITION) {
                addRenderableWidget(Button.builder(
                        Component.literal("Label: " + (HqTiersClientConfig.positionLabelEnabled ? "ON" : "OFF")),
                        btn -> {
                            HqTiersClientConfig.positionLabelEnabled = !HqTiersClientConfig.positionLabelEnabled;
                            btn.setMessage(Component.literal("Label: " + (HqTiersClientConfig.positionLabelEnabled ? "ON" : "OFF")));
                        }
                ).bounds(left + 292, y, 80, 18).build());
            }
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(cx - 50, height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0101420);
        super.render(context, mouseX, mouseY, delta);

        int cx = width / 2;
        int rowW = Math.min(420, width - 40);
        int left = cx - rowW / 2;

        context.drawCenteredString(font, "Nametag Layout", cx, 10, 0xFF00BFFF);
        context.drawCenteredString(font, "↑↓ reorder  •  toggle ON/OFF", cx, 22, 0xFF888888);

        context.drawString(font, "Component", left, START_Y - 14, 0xFFAAAAAA);
        context.drawString(font, "Move", left + 190, START_Y - 14, 0xFFAAAAAA);
        context.drawString(font, "Show", left + 244, START_Y - 14, 0xFFAAAAAA);
        context.drawString(font, "Label", left + 298, START_Y - 14, 0xFFAAAAAA);
        context.fill(left - 4, START_Y - 4, left + rowW + 4, START_Y - 3, 0xFF444444);

        for (int i = 0; i < order.size(); i++) {
            int y = START_Y + i * ROW_H;
            if (i % 2 == 0) {
                context.fill(left - 4, y - 2, left + rowW + 4, y + ROW_H - 4, 0x22FFFFFF);
            }
            context.drawString(font,
                    (i + 1) + ". " + componentName(i),
                    left, y + 4,
                    isEnabled(i) ? 0xFFFFFFFF : 0xFF777777);
        }

        int previewY = START_Y + order.size() * ROW_H + 14;
        context.fill(left - 4, previewY - 4, left + rowW + 4, previewY + 14, 0x33FFFFFF);
        context.drawString(font, "Preview:", left, previewY + 2, 0xFFAAAAAA);
        context.drawString(font, HqTiersFormatter.previewCompact(), left + 65, previewY + 2, 0xFFFFFFFF);
    }

    private void swap(int a, int b) {
        HqTiersClientConfig.NametagComponent compA = order.get(a);
        HqTiersClientConfig.NametagComponent compB = order.get(b);

        if (compA == HqTiersClientConfig.NametagComponent.SEPARATOR
                && compB == HqTiersClientConfig.NametagComponent.SEPARATOR) {
            int occA = separatorOccurrence(a);
            int occB = separatorOccurrence(b);
            boolean stateA = HqTiersClientConfig.isSeparatorEnabled(occA);
            boolean stateB = HqTiersClientConfig.isSeparatorEnabled(occB);
            HqTiersClientConfig.setSeparatorEnabled(occA, stateB);
            HqTiersClientConfig.setSeparatorEnabled(occB, stateA);
        }

        order.set(a, compB);
        order.set(b, compA);
        HqTiersClientConfig.nametagOrder = new ArrayList<>(order);
    }

    private int separatorOccurrence(int index) {
        int occurrence = -1;
        for (int i = 0; i <= index; i++) {
            if (order.get(i) == HqTiersClientConfig.NametagComponent.SEPARATOR) occurrence++;
        }
        return occurrence;
    }

    private boolean isEnabled(int index) {
        HqTiersClientConfig.NametagComponent comp = order.get(index);
        return switch (comp) {
            case GAMEMODE_ICON -> HqTiersClientConfig.gamemodeIconEnabled;
            case TIER -> HqTiersClientConfig.tierEnabled;
            case SEPARATOR -> HqTiersClientConfig.isSeparatorEnabled(separatorOccurrence(index));
            case ELO -> HqTiersClientConfig.eloEnabled;
            case POSITION -> HqTiersClientConfig.positionEnabled;
        };
    }

    private void toggle(int index) {
        HqTiersClientConfig.NametagComponent comp = order.get(index);
        switch (comp) {
            case GAMEMODE_ICON -> HqTiersClientConfig.gamemodeIconEnabled = !HqTiersClientConfig.gamemodeIconEnabled;
            case TIER -> HqTiersClientConfig.tierEnabled = !HqTiersClientConfig.tierEnabled;
            case SEPARATOR -> {
                int occurrence = separatorOccurrence(index);
                HqTiersClientConfig.setSeparatorEnabled(occurrence, !HqTiersClientConfig.isSeparatorEnabled(occurrence));
            }
            case ELO -> HqTiersClientConfig.eloEnabled = !HqTiersClientConfig.eloEnabled;
            case POSITION -> HqTiersClientConfig.positionEnabled = !HqTiersClientConfig.positionEnabled;
        }
    }

    private String componentName(int index) {
        HqTiersClientConfig.NametagComponent comp = order.get(index);
        return switch (comp) {
            case GAMEMODE_ICON -> "Gamemode Icon";
            case TIER -> "Tier";
            case SEPARATOR -> "Separator " + (separatorOccurrence(index) + 1);
            case ELO -> "TR";
            case POSITION -> "Position";
        };
    }

    @Override
    public void onClose() {
        HqTiersClientConfig.nametagOrder = new ArrayList<>(order);
        HqTiersClientConfig.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}