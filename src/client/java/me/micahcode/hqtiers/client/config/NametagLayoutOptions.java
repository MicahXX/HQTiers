package me.micahcode.hqtiers.client.config;

import me.micahcode.hqtiers.client.nametag.StatPosition;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.gui.OptionListWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import java.util.function.Consumer;
import me.micahcode.hqtiers.client.config.HqTiersClientConfig.DisplayMode;
import me.micahcode.hqtiers.client.config.HqTiersClientConfig.NametagAlignment;
import me.micahcode.hqtiers.client.config.HqTiersClientConfig.NametagComponent;
import me.micahcode.hqtiers.client.model.HqTiersLadder;
import me.micahcode.hqtiers.client.model.HqTiersRanks;
import me.micahcode.hqtiers.client.nametag.HqTiersFormatter;
import me.micahcode.hqtiers.client.nametag.NametagComposer;
import me.micahcode.hqtiers.client.nametag.NametagParts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static me.micahcode.hqtiers.client.config.HqTiersClientConfig.NametagAlignment.LEFT;
import static me.micahcode.hqtiers.client.config.HqTiersClientConfig.NametagAlignment.RIGHT;
import static me.micahcode.hqtiers.client.config.HqTiersConfigScreen.toggle;

final class NametagLayoutOptions {
    record Row(NametagComponent kind, boolean shown, NametagAlignment side) {
        String name() {
            return switch (kind) {
                case GAMEMODE_ICON -> "Mode icon";
                case TIER -> "Tier";
                case ELO -> "Rating";
                case POSITION -> "Position";
                case GLOBAL_RANK -> "Global rank";
                case SEPARATOR -> "Divider";
            };
        }

        String placement() {
            return !shown ? "Hidden" : side == LEFT ? "Left" : "Right";
        }
    }

    private final Option<DisplayMode> mode;
    private final Option<HqTiersLadder> ladder;
    private final TierColorOptions colors;
    private final Option<IconSet> icons;
    private final Option<List<Row>> order;
    private final NametagPresetOptions presets;
    private final List<Option<?>> settings;
    private final Option<StatPosition> statPosition = Option.<StatPosition>createBuilder().name(Component.literal("Stat position"))
            .description(OptionDescription.of(Component.literal("Show stats beside the player name or on a separate line above or below it.")))
            .binding(StatPosition.INLINE, () -> HqTiersClientConfig.statPosition, value -> HqTiersClientConfig.statPosition = value)
            .controller(option -> EnumControllerBuilder.create(option).enumClass(StatPosition.class)
                    .formatValue(value -> Component.literal(value.label))).build();
    private final Option<Boolean> spacing = toggle("Icon spacing", "Leave a space between a gamemode icon and the next stat.", true,
            () -> HqTiersClientConfig.iconSpacing, v -> HqTiersClientConfig.iconSpacing = v);
    private final Option<Boolean> divider = toggle("Divider beside name", "Put a vertical bar between your name and the stats on each side.", true,
            () -> HqTiersClientConfig.nameSeparator, v -> HqTiersClientConfig.nameSeparator = v);
    private final Option<Boolean> trLabel = toggle("Rating suffix", "Show TR for tier rating and % for percentage progress.", false,
            () -> HqTiersClientConfig.eloLabelEnabled, v -> HqTiersClientConfig.eloLabelEnabled = v);
    private final Option<Boolean> negativeTr = toggle("Red negative TR", "Show negative tier ratings in red.", false,
            () -> HqTiersClientConfig.redNegativeTr, v -> HqTiersClientConfig.redNegativeTr = v);
    private final Option<Boolean> posLabel = toggle("Position prefix", "Show # before the gamemode position.", false,
            () -> HqTiersClientConfig.positionLabelEnabled, v -> HqTiersClientConfig.positionLabelEnabled = v);
    private final Option<Boolean> globalLabel = toggle("Global rank prefix", "Show # before the additional global rank.", true,
            () -> HqTiersClientConfig.globalRankLabelEnabled, v -> HqTiersClientConfig.globalRankLabelEnabled = v);

    NametagLayoutOptions(Option<DisplayMode> mode, Option<HqTiersLadder> ladder, TierColorOptions colors, Option<IconSet> icons) {
        this.mode = mode;
        this.ladder = ladder;
        this.colors = colors;
        this.icons = icons;
        var defaults = HqTiersClientConfig.defaultNametagOrder().stream()
                .map(kind -> new Row(kind, kind == NametagComponent.GAMEMODE_ICON || kind == NametagComponent.TIER, LEFT)).toList();
        order = Option.<List<Row>>createBuilder().name(Component.literal("Stat placement"))
                .description(OptionDescription.of(Component.literal("Drag a stat name or use Move up and Move down to reorder." +
                        " Click the placement button to cycle through Left, Right and Hidden.")))
                .binding(defaults, NametagLayoutOptions::loadRows, NametagLayoutOptions::saveRows)
                .customController(LayoutController::new)
                .build();
        settings = List.of(statPosition, order, spacing, divider, trLabel, negativeTr, posLabel, globalLabel);
        presets = new NametagPresetOptions(this::snapshot, this::apply, icons);
    }

    private NametagPresets.Layout snapshot() {
        return new NametagPresets.Layout(order.pendingValue(), statPosition.pendingValue(), spacing.pendingValue(),
                divider.pendingValue(), trLabel.pendingValue(), negativeTr.pendingValue(), posLabel.pendingValue(), globalLabel.pendingValue(), colors.paletteSnapshot());
    }

    private void apply(NametagPresets.Layout layout, TierPalettes.Preset palette) {
        order.requestSet(layout.rows());
        statPosition.requestSet(layout.position());
        spacing.requestSet(layout.spacing());
        divider.requestSet(layout.divider());
        trLabel.requestSet(layout.ratingSuffix());
        negativeTr.requestSet(layout.redNegativeTr());
        posLabel.requestSet(layout.positionPrefix());
        globalLabel.requestSet(layout.globalPrefix());
        colors.selectSnapshot(palette);
    }

    ConfigCategory category() {
        var visible = ConfigCategory.createBuilder().name(Component.literal("Nametag layout")).option(presets.selector).option(order)
                .group(OptionGroup.createBuilder().name(Component.literal("Spacing and labels"))
                        .option(spacing).option(divider).option(trLabel).option(negativeTr).option(posLabel).option(globalLabel).build())
                .group(presets.management(settings, colors))
                .build();
        var bindings = ConfigCategory.createBuilder().name(visible.name()).groups(visible.groups())
                .group(OptionGroup.createBuilder().name(statPosition.name()).option(statPosition).build()).build();
        return new SettingsCategory(bindings, (screen, area) -> new LayoutTab(screen, visible, area));
    }

    private final class LayoutTab extends SettingsCategory.AlignedTab {
        private final CycleButton<StatPosition> position;
        private final Button reset;
        private boolean controlsBelow;
        private ScreenRectangle panel;

        LayoutTab(YACLScreen screen, ConfigCategory category, ScreenRectangle area) {
            super(screen, category, area);
            position = CycleButton.<StatPosition>builder(value -> Component.literal(value.label), statPosition.pendingValue())
                    .withValues(StatPosition.values()).displayOnlyValue()
                    .create(statPosition.name(), (button, value) -> statPosition.requestSet(value));
            position.setTooltip(Tooltip.create(Component.literal("Show stats beside, above, or below the player name.")));
            reset = Button.builder(Component.literal("Reset layout"), button -> presets.reset())
                    .size(70, 18).build();
            reset.setTooltip(Tooltip.create(Component.literal("Restore the default order, placement, spacing and labels.")));
        }

        @Override public void visitChildren(Consumer<AbstractWidget> visitor) {
            super.visitChildren(visitor);
            visitor.accept(position);
            visitor.accept(reset);
        }

        @Override public void doLayout(ScreenRectangle area) {
            super.doLayout(area);
            super.visitChildren(widget -> {
                if (widget instanceof OptionListWidget list) {
                    int controlWidth = 52;
                    controlsBelow = list.getWidth() < controlWidth + reset.getWidth() + 100;
                    int height = Math.clamp(area.height() / 5, 32, 44) + (controlsBelow ? 22 : 0);
                    panel = new ScreenRectangle(list.getX(), list.getY(), list.getWidth(), height);
                    list.setHeight(list.getHeight() - height);
                    list.setY(panel.bottom());
                    list.refreshScrollAmount();
                    int y = controlsBelow ? panel.bottom() - reset.getHeight() - 3
                            : panel.top() + (panel.height() - reset.getHeight()) / 2;
                    reset.setPosition(panel.right() - reset.getWidth() - 5, y);
                    position.setRectangle(controlWidth, reset.getHeight(), reset.getX() - controlWidth - 3, y);
                }
            });
        }

        @Override public void renderBackground(GuiGraphics graphics) {
            super.renderBackground(graphics);
            if (panel == null) return;
            var font = Minecraft.getInstance().font;
            graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), 0x80000000);
            var lines = preview();
            int lineHeight = font.lineHeight + 1;
            int widest = lines.stream().mapToInt(font::width).max().orElse(1);
            int tagLeft = panel.left() + 6;
            int tagRight = (controlsBelow ? panel.right() : position.getX()) - 6;
            int previewHeight = controlsBelow ? position.getY() - panel.top() : panel.height();
            float scale = Math.min(2.0f, (float) Math.max(1, tagRight - tagLeft) / Math.max(1, widest));
            scale = Math.min(scale, (float) (previewHeight - 4) / (lineHeight * lines.size()));
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate((tagLeft + tagRight) / 2f, panel.top() + previewHeight / 2f);
            pose.scale(scale, scale);
            int y = -(lines.size() * lineHeight - 1) / 2;
            for (Component line : lines) {
                graphics.drawCenteredString(font, line, 0, y, 0xFFFFFFFF);
                y += lineHeight;
            }
            pose.popMatrix();
            if (position.getValue() != statPosition.pendingValue()) position.setValue(statPosition.pendingValue());
            reset.active = !presets.selector.isPendingValueDefault();
        }
    }

    static List<Row> loadRows() {
        List<Row> rows = new ArrayList<>();
        int separator = -1;
        for (var kind : HqTiersClientConfig.nametagOrder) {
            if (kind == NametagComponent.SEPARATOR) separator++;
            boolean shown = switch (kind) {
                case GAMEMODE_ICON -> HqTiersClientConfig.gamemodeIconEnabled;
                case TIER -> HqTiersClientConfig.tierEnabled;
                case SEPARATOR -> HqTiersClientConfig.isSeparatorEnabled(separator);
                case ELO -> HqTiersClientConfig.eloEnabled;
                case POSITION -> HqTiersClientConfig.positionEnabled;
                case GLOBAL_RANK -> HqTiersClientConfig.globalRankEnabled;
            };
            rows.add(new Row(kind, shown, HqTiersClientConfig.side(kind, separator)));
        }
        return rows;
    }

    private static void saveRows(List<Row> rows) {
        HqTiersClientConfig.nametagOrder = new ArrayList<>(rows.stream().map(Row::kind).toList());
        HqTiersClientConfig.separatorSides = new ArrayList<>();
        HqTiersClientConfig.nametagSeparatorStates = new ArrayList<>();
        HqTiersClientConfig.gamemodeIconEnabled = false;
        HqTiersClientConfig.tierEnabled = false;
        HqTiersClientConfig.eloEnabled = false;
        HqTiersClientConfig.positionEnabled = false;
        HqTiersClientConfig.globalRankEnabled = false;
        for (Row row : rows) {
            switch (row.kind()) {
                case GAMEMODE_ICON -> HqTiersClientConfig.gamemodeIconEnabled = row.shown();
                case TIER -> HqTiersClientConfig.tierEnabled = row.shown();
                case ELO -> HqTiersClientConfig.eloEnabled = row.shown();
                case POSITION -> HqTiersClientConfig.positionEnabled = row.shown();
                case GLOBAL_RANK -> HqTiersClientConfig.globalRankEnabled = row.shown();
                case SEPARATOR -> {
                    HqTiersClientConfig.nametagSeparatorStates.add(row.shown());
                    HqTiersClientConfig.separatorSides.add(row.side());
                }
            }
            if (row.kind() != NametagComponent.SEPARATOR) HqTiersClientConfig.componentSides.put(row.kind(), row.side());
        }
    }

    private List<Component> preview() {
        var rows = order.pendingValue();
        boolean global = mode.pendingValue() == DisplayMode.GLOBAL ||
                (mode.pendingValue() == DisplayMode.PREFERRED_LADDER && ladder.pendingValue() == HqTiersLadder.GLOBAL);
        String sampleLadder = global ? "GLOBAL" : mode.pendingValue() == DisplayMode.PREFERRED_LADDER ? ladder.pendingValue().name() : "SWORD";
        var sampleTier = HqTiersRanks.HT3;
        int rgb = colors.previewRgb(sampleTier);
        boolean hasTier = rows.stream().anyMatch(row -> row.kind() == NametagComponent.TIER && row.shown());
        var left = new NametagComposer.Line(spacing.pendingValue());
        var right = new NametagComposer.Line(spacing.pendingValue());
        for (var row : rows) {
            if (!row.shown()) continue;
            var line = statPosition.pendingValue() != StatPosition.INLINE || row.side() == LEFT ? left : right;
            if (row.kind() == NametagComponent.SEPARATOR) {
                line.divider();
                continue;
            }
            Component value = switch (row.kind()) {
                case GAMEMODE_ICON -> HqTiersFormatter.icon(sampleLadder, icons.pendingValue());
                case TIER -> global ? Component.literal("#3299") : sample(sampleTier.name(), colors.tier, rgb);
                case ELO -> global ? Component.empty() : NametagComposer.rating(-4, false, trLabel.pendingValue(),
                        negativeTr.pendingValue(), colors.rating.pendingValue() ? rgb : 0xFFFFFF);
                case POSITION -> global && hasTier ? Component.empty() : global ? Component.literal("#3299")
                        : sample(posLabel.pendingValue() ? "#123" : "123", colors.position, rgb);
                case GLOBAL_RANK -> global ? Component.empty() : Component.empty().append(HqTiersFormatter.icon("GLOBAL", icons.pendingValue()))
                        .append(globalLabel.pendingValue() ? " #3299" : " 3299");
                case SEPARATOR -> throw new IllegalStateException("Divider already handled");
            };
            line.append(row.kind(), value);
        }
        var name = Component.literal(Minecraft.getInstance().getUser().getName());
        return statPosition.pendingValue().lines(new NametagParts(left.text(), right.text()), name, divider.pendingValue());
    }

    private static Component sample(String value, Option<Boolean> colored, int rgb) {
        return Component.literal(value).withColor(colored.pendingValue() ? rgb : 0xFFFFFF);
    }
}
