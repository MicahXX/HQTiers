package me.micahcode.hqtiers.client.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.gui.OptionListWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
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
import net.minecraft.ChatFormatting;
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
    private final Option<List<Row>> order;
    private final Option<Boolean> spacing = toggle("Icon spacing", "Leave a space between a gamemode icon and the next stat.", true,
            () -> HqTiersClientConfig.iconSpacing, v -> HqTiersClientConfig.iconSpacing = v);
    private final Option<Boolean> divider = toggle("Divider beside name", "Put a vertical bar between your name and the stats on each side.", true,
            () -> HqTiersClientConfig.nameSeparator, v -> HqTiersClientConfig.nameSeparator = v);
    private final Option<Boolean> trLabel = toggle("Rating suffix", "Show TR after a numeric tier rating.", false,
            () -> HqTiersClientConfig.eloLabelEnabled, v -> HqTiersClientConfig.eloLabelEnabled = v);
    private final Option<Boolean> posLabel = toggle("Position prefix", "Show # before the gamemode position.", false,
            () -> HqTiersClientConfig.positionLabelEnabled, v -> HqTiersClientConfig.positionLabelEnabled = v);
    private final Option<Boolean> globalLabel = toggle("Global rank prefix", "Show # before the additional global rank.", true,
            () -> HqTiersClientConfig.globalRankLabelEnabled, v -> HqTiersClientConfig.globalRankLabelEnabled = v);

    NametagLayoutOptions(Option<DisplayMode> mode, Option<HqTiersLadder> ladder, TierColorOptions colors) {
        this.mode = mode;
        this.ladder = ladder;
        this.colors = colors;
        var defaults = HqTiersClientConfig.defaultNametagOrder().stream()
                .map(kind -> new Row(kind, kind == NametagComponent.GAMEMODE_ICON || kind == NametagComponent.TIER, LEFT)).toList();
        order = Option.<List<Row>>createBuilder().name(Component.literal("Stat placement"))
                .description(OptionDescription.of(Component.literal("Drag a stat name or use Move up and Move down to reorder." +
                        " Click the placement button to cycle through Left, Right and Hidden.")))
                .binding(defaults, NametagLayoutOptions::loadRows, NametagLayoutOptions::saveRows)
                .customController(LayoutController::new)
                .build();
    }

    ConfigCategory category() {
        var bindings = ConfigCategory.createBuilder().name(Component.literal("Nametag layout")).option(order)
                .group(OptionGroup.createBuilder().name(Component.literal("Spacing and labels"))
                        .option(spacing).option(divider).option(trLabel).option(posLabel).option(globalLabel).build())
                .build();
        return new SettingsCategory(bindings, (screen, area) -> new LayoutTab(screen, bindings, area));
    }

    private final class LayoutTab extends SettingsCategory.AlignedTab {
        private final List<Option<?>> settings = List.of(order, spacing, divider, trLabel, posLabel, globalLabel);
        private final Button reset;
        private ScreenRectangle panel;

        LayoutTab(YACLScreen screen, ConfigCategory category, ScreenRectangle area) {
            super(screen, category, area);
            reset = Button.builder(Component.literal("Reset layout"), button -> settings.forEach(Option::requestSetDefault))
                    .size(70, 18).build();
            reset.setTooltip(Tooltip.create(Component.literal("Restore the default order, placement, spacing and labels.")));
        }

        @Override public void visitChildren(Consumer<AbstractWidget> visitor) {
            super.visitChildren(visitor);
            visitor.accept(reset);
        }

        @Override public void doLayout(ScreenRectangle area) {
            super.doLayout(area);
            int height = Math.clamp(area.height() / 5, 32, 44);
            super.visitChildren(widget -> {
                if (widget instanceof OptionListWidget list) {
                    panel = new ScreenRectangle(list.getX(), list.getY(), list.getWidth(), height);
                    list.setHeight(list.getHeight() - height);
                    list.setY(panel.bottom());
                    list.refreshScrollAmount();
                    reset.setPosition(panel.right() - reset.getWidth() - 5,
                            panel.top() + (panel.height() - reset.getHeight()) / 2);
                }
            });
        }

        @Override public void renderBackground(GuiGraphics graphics) {
            super.renderBackground(graphics);
            if (panel == null) return;
            var font = Minecraft.getInstance().font;
            graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), 0x80000000);
            var sample = preview();
            int tagLeft = panel.left() + 6;
            int tagRight = reset.getX() - 6;
            float scale = Math.min(2.0f, (float) Math.max(1, tagRight - tagLeft) / Math.max(1, font.width(sample)));
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate((tagLeft + tagRight) / 2f, panel.top() + panel.height() / 2f);
            pose.scale(scale, scale);
            graphics.drawCenteredString(font, sample, 0, -font.lineHeight / 2, 0xFFFFFFFF);
            pose.popMatrix();
            reset.active = settings.stream().anyMatch(setting -> !setting.isPendingValueDefault());
        }
    }

    private static List<Row> loadRows() {
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

    private Component preview() {
        var rows = order.pendingValue();
        boolean global = mode.pendingValue() == DisplayMode.GLOBAL ||
                (mode.pendingValue() == DisplayMode.PREFERRED_LADDER && ladder.pendingValue() == HqTiersLadder.GLOBAL);
        String sampleLadder = global ? "GLOBAL" : mode.pendingValue() == DisplayMode.PREFERRED_LADDER ? ladder.pendingValue().name() : "SWORD";
        int rgb = colors.previewRgb(HqTiersRanks.HT3);
        boolean hasTier = rows.stream().anyMatch(row -> row.kind() == NametagComponent.TIER && row.shown());
        var left = new NametagComposer.Line(spacing.pendingValue());
        var right = new NametagComposer.Line(spacing.pendingValue());
        for (var row : rows) {
            if (!row.shown()) continue;
            var line = row.side() == LEFT ? left : right;
            if (row.kind() == NametagComponent.SEPARATOR) {
                line.divider();
                continue;
            }
            Component value = switch (row.kind()) {
                case GAMEMODE_ICON -> HqTiersFormatter.icon(sampleLadder);
                case TIER -> global ? Component.literal("#3299") : sample("HT3", colors.tier, rgb);
                case ELO -> global ? Component.empty() : sample(trLabel.pendingValue() ? "125 TR" : "125", colors.rating, rgb);
                case POSITION -> global && hasTier ? Component.empty() : global ? Component.literal("#3299")
                        : sample(posLabel.pendingValue() ? "#123" : "123", colors.position, rgb);
                case GLOBAL_RANK -> global ? Component.empty() : Component.empty().append(HqTiersFormatter.icon("GLOBAL"))
                        .append(globalLabel.pendingValue() ? " #3299" : " 3299");
                case SEPARATOR -> throw new IllegalStateException("Divider already handled");
            };
            line.append(row.kind(), value);
        }
        var name = Component.literal(Minecraft.getInstance().getUser().getName());
        return new NametagParts(left.text(), right.text()).around(name, divider.pendingValue()).copy().withStyle(ChatFormatting.WHITE);
    }

    private static Component sample(String value, Option<Boolean> colored, int rgb) {
        return Component.literal(value).withColor(colored.pendingValue() ? rgb : 0xFFFFFF);
    }
}
