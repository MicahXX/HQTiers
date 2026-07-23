package me.micahcode.hqtiers.client.config;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class NametagLayoutScreen extends Screen {
    private final Screen parent;
    private static final int ROW_H = 24;
    private static final int START_Y = 55;

    private final List<HqTiersClientConfig.NametagComponent> order;

    public NametagLayoutScreen(Screen parent) {
        super(Text.literal("Nametag Layout"));
        this.parent = parent;
        this.order = new ArrayList<>(HqTiersClientConfig.nametagOrder);
    }

    @Override
    protected void init() {
        clearChildren();
        int cx = width / 2;
        int rowW = Math.min(420, width - 40);
        int left = cx - rowW / 2;

        for (int i = 0; i < order.size(); i++) {
            final int idx = i;
            HqTiersClientConfig.NametagComponent comp = order.get(i);
            int y = START_Y + i * ROW_H;

            if (i > 0) {
                addDrawableChild(ButtonWidget.builder(Text.literal("↑"), btn -> {
                    swap(idx - 1, idx);
                    init();
                }).dimensions(left + 190, y, 20, 18).build());
            }

            if (i < order.size() - 1) {
                addDrawableChild(ButtonWidget.builder(Text.literal("↓"), btn -> {
                    swap(idx, idx + 1);
                    init();
                }).dimensions(left + 214, y, 20, 18).build());
            }

            addDrawableChild(ButtonWidget.builder(
                    Text.literal(isEnabled(idx) ? "ON" : "OFF"),
                    btn -> {
                        toggle(idx);
                        btn.setMessage(Text.literal(isEnabled(idx) ? "ON" : "OFF"));
                    }
            ).dimensions(left + 238, y, 50, 18).build());

            if (comp == HqTiersClientConfig.NametagComponent.ELO) {
                addDrawableChild(ButtonWidget.builder(
                        Text.literal("Label: " + (HqTiersClientConfig.eloLabelEnabled ? "ON" : "OFF")),
                        btn -> {
                            HqTiersClientConfig.eloLabelEnabled = !HqTiersClientConfig.eloLabelEnabled;
                            btn.setMessage(Text.literal("Label: " + (HqTiersClientConfig.eloLabelEnabled ? "ON" : "OFF")));
                        }
                ).dimensions(left + 292, y, 80, 18).build());
            }

            if (comp == HqTiersClientConfig.NametagComponent.POSITION) {
                addDrawableChild(ButtonWidget.builder(
                        Text.literal("Label: " + (HqTiersClientConfig.positionLabelEnabled ? "ON" : "OFF")),
                        btn -> {
                            HqTiersClientConfig.positionLabelEnabled = !HqTiersClientConfig.positionLabelEnabled;
                            btn.setMessage(Text.literal("Label: " + (HqTiersClientConfig.positionLabelEnabled ? "ON" : "OFF")));
                        }
                ).dimensions(left + 292, y, 80, 18).build());
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> close())
                .dimensions(cx - 50, height - 28, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE0101420);
        super.render(context, mouseX, mouseY, delta);

        int cx = width / 2;
        int rowW = Math.min(420, width - 40);
        int left = cx - rowW / 2;

        context.drawCenteredTextWithShadow(textRenderer, "Nametag Layout", cx, 10, 0xFF00BFFF);
        context.drawCenteredTextWithShadow(textRenderer, "↑↓ reorder  •  toggle ON/OFF", cx, 22, 0xFF888888);

        context.drawTextWithShadow(textRenderer, "Component", left, START_Y - 14, 0xFFAAAAAA);
        context.drawTextWithShadow(textRenderer, "Move", left + 190, START_Y - 14, 0xFFAAAAAA);
        context.drawTextWithShadow(textRenderer, "Show", left + 244, START_Y - 14, 0xFFAAAAAA);
        context.drawTextWithShadow(textRenderer, "Label", left + 298, START_Y - 14, 0xFFAAAAAA);
        context.fill(left - 4, START_Y - 4, left + rowW + 4, START_Y - 3, 0xFF444444);

        for (int i = 0; i < order.size(); i++) {
            int y = START_Y + i * ROW_H;
            if (i % 2 == 0) {
                context.fill(left - 4, y - 2, left + rowW + 4, y + ROW_H - 4, 0x22FFFFFF);
            }
            context.drawTextWithShadow(textRenderer,
                    (i + 1) + ". " + componentName(i),
                    left, y + 4,
                    isEnabled(i) ? 0xFFFFFFFF : 0xFF777777);
        }

        int previewY = START_Y + order.size() * ROW_H + 14;
        context.fill(left - 4, previewY - 4, left + rowW + 4, previewY + 14, 0x33FFFFFF);
        context.drawTextWithShadow(textRenderer, "Preview:", left, previewY + 2, 0xFFAAAAAA);
        context.drawTextWithShadow(textRenderer, HqTiersFormatter.previewCompact(), left + 65, previewY + 2, 0xFFFFFFFF);
    }

    private void swap(int a, int b) {
        HqTiersClientConfig.NametagComponent compA = order.get(a);
        HqTiersClientConfig.NametagComponent compB = order.get(b);

        // If we're swapping two separators past each other, carry each one's
        // own enabled state along with it so a reorder doesn't silently flip
        // which separator is on/off.
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

    /** Which separator (0-based) the entry at `index` is, counting separators only. */
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
            case ELO -> "SR";
            case POSITION -> "Position";
        };
    }

    @Override
    public void close() {
        HqTiersClientConfig.nametagOrder = new ArrayList<>(order);
        HqTiersClientConfig.save();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}