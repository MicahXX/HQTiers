package me.micahcode.hqtiers.client.config;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersClientConfig.NametagAlignment;
import me.micahcode.hqtiers.client.HqTiersClientConfig.NametagComponent;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Edits nametag elements with a live preview and pages that fit small GUI sizes. */
public class NametagLayoutScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_TOP = 84;
    private final Screen parent;
    private final List<NametagComponent> order;
    private int page;

    /** Copies the saved order while sharing live display preferences with the preview. */
    public NametagLayoutScreen(Screen parent) {
        super(Component.literal("Nametag Layout"));
        this.parent = parent;
        HqTiersClientConfig.normalizeNametagOrder();
        this.order = new ArrayList<>(HqTiersClientConfig.nametagOrder);
    }

    /** Reserves room for the preview, spacing controls, and navigation. */
    private int visibleRows() {
        return Math.max(1, Math.min(order.size(), (height - ROW_TOP - 92) / ROW_HEIGHT));
    }

    /** Sizes the panel to the available GUI width. */
    private int panelWidth() {
        return Math.min(480, width - 16);
    }

    /** Creates only the visible rows so every control remains reachable. */
    @Override
    protected void init() {
        clearWidgets();
        int rows = visibleRows();
        page = Math.min(page, Math.max(0, (order.size() - 1) / rows));
        int left = (width - panelWidth()) / 2;
        int right = left + panelWidth();
        for (int i = page * rows; i < Math.min(order.size(), (page + 1) * rows); i++) {
            final int index = i;
            NametagComponent component = order.get(i);
            int y = ROW_TOP + (i % rows) * ROW_HEIGHT;
            Button up = button("↑", right - 198, y, 20, () -> swap(index, index - 1));
            up.active = i > 0;
            up.setTooltip(Tooltip.create(Component.literal("Move earlier")));
            Button down = button("↓", right - 176, y, 20, () -> swap(index, index + 1));
            down.active = i < order.size() - 1;
            down.setTooltip(Tooltip.create(Component.literal("Move later")));
            button(isEnabled(i) ? "On" : "Off", right - 152, y, 38, () -> toggle(index));
            NametagAlignment side = HqTiersClientConfig.side(component, separatorOccurrence(i));
            button(side == NametagAlignment.LEFT ? "Left" : "Right", right - 110, y, 48, () -> {
                HqTiersClientConfig.setSide(component, separatorOccurrence(index),
                        side == NametagAlignment.LEFT ? NametagAlignment.RIGHT : NametagAlignment.LEFT);
            }).setTooltip(Tooltip.create(Component.literal("Side of the player name")));
            if (component == NametagComponent.ELO || component == NametagComponent.POSITION) {
                boolean label = component == NametagComponent.ELO
                        ? HqTiersClientConfig.eloLabelEnabled : HqTiersClientConfig.positionLabelEnabled;
                button("Label " + (label ? "+" : "-"), right - 58, y, 58, () -> {
                    if (component == NametagComponent.ELO) HqTiersClientConfig.eloLabelEnabled = !label;
                    else HqTiersClientConfig.positionLabelEnabled = !label;
                }).setTooltip(Tooltip.create(Component.literal("Show TR or # beside the value")));
            }
        }
        int half = (panelWidth() - 4) / 2;
        button("Icon space: " + (HqTiersClientConfig.iconSpacing ? "On" : "Off"), left, height - 72, half,
                () -> HqTiersClientConfig.iconSpacing = !HqTiersClientConfig.iconSpacing);
        button("Name divider: " + (HqTiersClientConfig.nameSeparator ? "On" : "Off"), left + half + 4, height - 72, half,
                () -> HqTiersClientConfig.nameSeparator = !HqTiersClientConfig.nameSeparator)
                .setTooltip(Tooltip.create(Component.literal("Show | between your stats and the player name")));
        Button previous = button("Previous", left, height - 28, 70, () -> page--);
        previous.active = page > 0;
        addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> onClose())
                .bounds(width / 2 - 42, height - 28, 84, 20).build());
        Button next = button("Next", right - 70, height - 28, 70, () -> page++);
        next.active = (page + 1) * rows < order.size();
    }

    /** Applies an edit and invalidates the cached in-world preview immediately. */
    private Button button(String label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), ignored -> {
            action.run();
            HqTiersClientConfig.changed();
            init();
        }).bounds(x, y, width, 20).build());
    }

    /** Draws panel backgrounds before widgets so row highlights never cover controls. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int left = (width - panelWidth()) / 2;
        int right = left + panelWidth();
        context.fill(0, 0, width, height, 0xF0101420);
        context.centeredText(font, title, width / 2, 10, 0xFFFFD86B);
        context.centeredText(font, Component.literal("Arrange your stats around the player name"), width / 2, 24, 0xFFA7B4C8);
        context.fill(left, 40, right, 64, 0xFF202B3B);
        context.enableScissor(left + 4, 40, right - 4, 64);
        context.centeredText(font, HqTiersFormatter.previewCompact(), width / 2, 48, 0xFFFFFFFF);
        context.disableScissor();
        context.text(font, "Element", left + 6, 72, 0xFFA7B4C8, true);
        context.text(font, "Order", right - 196, 72, 0xFFA7B4C8, true);
        context.text(font, "Show", right - 152, 72, 0xFFA7B4C8, true);
        context.text(font, "Side", right - 106, 72, 0xFFA7B4C8, true);
        int rows = visibleRows();
        for (int i = page * rows; i < Math.min(order.size(), (page + 1) * rows); i++) {
            int y = ROW_TOP + (i % rows) * ROW_HEIGHT;
            context.fill(left, y - 1, right, y + 21, i % 2 == 0 ? 0x80303C50 : 0x80303C44);
            context.text(font, font.plainSubstrByWidth(componentName(i), panelWidth() - 208), left + 6, y + 6, isEnabled(i) ? 0xFFFFFFFF : 0xFF8390A4, true);
        }
        context.centeredText(font, Component.literal("Page " + (page + 1) + " / " + ((order.size() + rows - 1) / rows)), width / 2, height - 44, 0xFFA7B4C8);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    /** Moves element settings together, including independently configured separators. */
    private void swap(int a, int b) {
        if (b < 0 || b >= order.size()) return;
        NametagComponent first = order.get(a);
        NametagComponent second = order.get(b);
        if (first == NametagComponent.SEPARATOR && second == NametagComponent.SEPARATOR) {
            int sa = separatorOccurrence(a), sb = separatorOccurrence(b);
            boolean enabled = HqTiersClientConfig.isSeparatorEnabled(sa);
            NametagAlignment side = HqTiersClientConfig.side(first, sa);
            HqTiersClientConfig.setSeparatorEnabled(sa, HqTiersClientConfig.isSeparatorEnabled(sb));
            HqTiersClientConfig.setSeparatorEnabled(sb, enabled);
            HqTiersClientConfig.setSide(first, sa, HqTiersClientConfig.side(second, sb));
            HqTiersClientConfig.setSide(second, sb, side);
        }
        order.set(a, second);
        order.set(b, first);
        HqTiersClientConfig.nametagOrder = new ArrayList<>(order);
        page = b / visibleRows();
    }

    /** Finds the independent state associated with a separator row. */
    private int separatorOccurrence(int index) {
        int occurrence = -1;
        for (int i = 0; i <= index; i++) if (order.get(i) == NametagComponent.SEPARATOR) occurrence++;
        return occurrence;
    }

    /** Reads visibility for the selected element. */
    private boolean isEnabled(int index) {
        return switch (order.get(index)) {
            case GAMEMODE_ICON -> HqTiersClientConfig.gamemodeIconEnabled;
            case TIER -> HqTiersClientConfig.tierEnabled;
            case SEPARATOR -> HqTiersClientConfig.isSeparatorEnabled(separatorOccurrence(index));
            case ELO -> HqTiersClientConfig.eloEnabled;
            case POSITION -> HqTiersClientConfig.positionEnabled;
        };
    }

    /** Toggles visibility without altering order or placement. */
    private void toggle(int index) {
        switch (order.get(index)) {
            case GAMEMODE_ICON -> HqTiersClientConfig.gamemodeIconEnabled = !HqTiersClientConfig.gamemodeIconEnabled;
            case TIER -> HqTiersClientConfig.tierEnabled = !HqTiersClientConfig.tierEnabled;
            case SEPARATOR -> HqTiersClientConfig.setSeparatorEnabled(separatorOccurrence(index), !isEnabled(index));
            case ELO -> HqTiersClientConfig.eloEnabled = !HqTiersClientConfig.eloEnabled;
            case POSITION -> HqTiersClientConfig.positionEnabled = !HqTiersClientConfig.positionEnabled;
        }
    }

    /** Labels the tier slot according to its global-mode behavior. */
    private String componentName(int index) {
        return switch (order.get(index)) {
            case GAMEMODE_ICON -> "Mode icon";
            case TIER -> HqTiersClientConfig.displayMode == HqTiersClientConfig.DisplayMode.GLOBAL ? "Global rank" : "Tier";
            case SEPARATOR -> "Divider " + (separatorOccurrence(index) + 1);
            case ELO -> "TR";
            case POSITION -> "Position";
        };
    }

    /** Persists changes for both Done and Escape. */
    @Override
    public void onClose() {
        HqTiersClientConfig.nametagOrder = new ArrayList<>(order);
        HqTiersClientConfig.save();
        minecraft.setScreen(parent);
    }

    /** Allows an open world to keep updating behind the editor. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
