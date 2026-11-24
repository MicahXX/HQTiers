package me.micahcode.hqtiers.client.config;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionEventListener;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.OptionDescriptionWidget;
import dev.isxander.yacl3.gui.OptionListWidget;
import dev.isxander.yacl3.gui.SearchFieldWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ColorController;
import dev.isxander.yacl3.gui.controllers.ColorPickerWidget;
import me.micahcode.hqtiers.client.model.HqTiersRankSystem;
import me.micahcode.hqtiers.client.model.HqTiersRanks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;

import static me.micahcode.hqtiers.client.config.HqTiersConfigScreen.toggle;
import static me.micahcode.hqtiers.client.model.HqTiersRanks.*;

final class TierColorOptions {
    private static final List<HqTiersRanks> RANKS = List.of(
            LT1, MT1, HT1, LT2, MT2, HT2, LT3, MT3, HT3, LT4, MT4, HT4, LT5, MT5, HT5);
    private final EnumMap<HqTiersRanks, Option<Color>> colors = new EnumMap<>(HqTiersRanks.class);
    private final Option<Boolean> custom;
    final Option<Boolean> tier = toggle("Tier labels", "Tint the tier beside a player name.", true,
            () -> HqTiersClientConfig.coloredTier, value -> HqTiersClientConfig.coloredTier = value);
    final Option<Boolean> rating = toggle("Tier rating", "Tint rating and progress values.", true,
            () -> HqTiersClientConfig.coloredElo, value -> HqTiersClientConfig.coloredElo = value);
    final Option<Boolean> position = toggle("Positions", "Tint leaderboard placement numbers.", true,
            () -> HqTiersClientConfig.coloredPosition, value -> HqTiersClientConfig.coloredPosition = value);

    TierColorOptions() {
        custom = Option.<Boolean>createBuilder().name(Component.literal("Active palette"))
                .description(OptionDescription.of(Component.literal("Switch back to PvPHQ at any time. Your custom colors are saved.")))
                .binding(false, () -> HqTiersClientConfig.customTierColors, value -> HqTiersClientConfig.customTierColors = value)
                .controller(o -> BooleanControllerBuilder.create(o).formatValue(v -> Component.literal(v ? "Custom" : "PvPHQ")))
                .build();
        for (var rank : RANKS) colors.put(rank, colorOption(rank));
    }

    ConfigCategory category() {
        var builder = ConfigCategory.createBuilder().name(Component.literal("Colors")).option(custom)
                .group(OptionGroup.createBuilder().name(Component.literal("Apply rank colors to"))
                        .option(tier).option(rating).option(position).build());
        var visible = builder.build();

        var bindings = ConfigCategory.createBuilder().name(visible.name()).groups(visible.groups())
                .group(OptionGroup.createBuilder().name(Component.literal("Tier palette")).options(colors.values()).build()).build();
        return new SettingsCategory(bindings, (screen, area) -> new PaletteTab(screen, visible, area));
    }

    int previewRgb(HqTiersRanks rank) {
        return custom.pendingValue() ? colors.get(rank).pendingValue().getRGB() & 0xFFFFFF : HqTiersRankSystem.tierColor(rank);
    }

    private Option<Color> colorOption(HqTiersRanks rank) {
        int base = HqTiersRankSystem.tierColor(rank);
        return Option.<Color>createBuilder().name(Component.literal(rank.name()))
                .binding(new Color(base), () -> new Color(HqTiersClientConfig.tierColors.getOrDefault(rank, base)), color -> {
                    int rgb = color.getRGB() & 0xFFFFFF;
                    if (rgb == base) HqTiersClientConfig.tierColors.remove(rank);
                    else HqTiersClientConfig.tierColors.put(rank, rgb);
                })
                .controller(ColorControllerBuilder::create).build();
    }

    private final class PaletteTab extends YACLScreen.CategoryTab {
        private final YACLScreen screen;
        private final EnumMap<HqTiersRanks, Button> buttons = new EnumMap<>(HqTiersRanks.class);
        private final Button reset;
        private HqTiersRanks selected;
        private ColorController.ColorControllerElement editor;
        private int left, top, width, cellHeight;

        PaletteTab(YACLScreen screen, ConfigCategory category, ScreenRectangle area) {
            super(screen, category, area);
            this.screen = screen;
            for (var rank : RANKS) buttons.put(rank, new Swatch(rank));
            selected = RANKS.stream().filter(rank -> !colors.get(rank).isPendingValueDefault()).findFirst().orElse(LT1);
            reset = Button.builder(Component.literal("Reset " + selected.name()), button -> colors.get(selected).requestSetDefault()).build();
            reset.setTooltip(Tooltip.create(Component.literal("Restore this tiers PvPHQ color in your custom palette.")));
            reset.active = !colors.get(selected).isPendingValueDefault();
        }

        @Override public void visitChildren(Consumer<AbstractWidget> visitor) {
            super.visitChildren(widget -> {
                if (!(widget instanceof OptionDescriptionWidget || widget instanceof SearchFieldWidget)) visitor.accept(widget);
            });
            RANKS.forEach(rank -> visitor.accept(buttons.get(rank)));
            visitor.accept(reset);
        }

        @Override public void doLayout(ScreenRectangle area) {
            super.doLayout(area);
            width = Math.min(180, saveFinishedButton.getWidth());
            left = saveFinishedButton.getX() + (saveFinishedButton.getWidth() - width) / 2;
            top = area.top() + 7;
            int gridTop = top + 15;
            cellHeight = Math.clamp((undoButton.getY() - gridTop - 24) / 5, 13, 22);
            for (int i = 0; i < RANKS.size(); i++) {
                int col = i % 3;
                int x = left + col * width / 3;
                buttons.get(RANKS.get(i)).setRectangle(left + (col + 1) * width / 3 - x - 2, cellHeight - 2,
                        x, gridTop + (i / 3) * cellHeight);
            }
            reset.setRectangle(width - 2, 16, left, gridTop + 5 * cellHeight + 2);
        }

        private void openPicker(HqTiersRanks rank) {
            selected = rank;
            reset.setMessage(Component.literal("Reset " + rank.name()));
            int listWidth = screen.tabArea.width() * 2 / 3;
            int fieldWidth = Math.min(172, listWidth - 20);
            var anchor = Dimension.ofInt((listWidth - fieldWidth) / 2, screen.tabArea.bottom() - 24, fieldWidth, 17);
            Color initial = new Color(previewRgb(rank));

            var draft = Option.<Color>createBuilder().name(Component.literal(rank.name()))
                    .binding(initial, () -> initial, ignored -> {})
                    .controller(ColorControllerBuilder::create)
                    .addListener((option, event) -> {
                        if (event == OptionEventListener.Event.STATE_CHANGE) {
                            colors.get(rank).requestSet(option.pendingValue());
                            custom.requestSet(true);
                        }
                    }).build();
            var controller = new ColorController(draft);
            editor = new ColorController.ColorControllerElement(controller, screen, anchor) {
                @Override public void removeColorPicker() {
                    super.removeColorPicker();
                    PaletteTab.super.doLayout(screen.tabArea);
                }
                @Override public ColorPickerWidget createColorPicker() {

                    var bounds = Dimension.ofInt(anchor.x() - fieldWidth + 20, anchor.y(), 2 * fieldWidth - 20, anchor.height());
                    return new ColorPickerWidget(controller, screen, bounds, this) {
                        @Override public boolean isMouseOver(double x, double y) {
                            return super.isMouseOver(x, y) || editor.isMouseOver(x, y) || reset.isMouseOver(x, y)
                                    || buttons.values().stream().anyMatch(swatch -> swatch.isMouseOver(x, y));
                        }

                        @Override public boolean onMouseClicked(double x, double y, int button) {
                            if (button == 0) {
                                if (reset.isMouseOver(x, y)) {
                                    if (!reset.active) return true;
                                    Minecraft.getInstance().screen.onClose();
                                    colors.get(rank).requestSetDefault();
                                    reset.playDownSound(Minecraft.getInstance().getSoundManager());
                                    return true;
                                }
                                for (var next : RANKS) {
                                    if (buttons.get(next).isMouseOver(x, y)) {
                                        if (next != rank) {
                                            Minecraft.getInstance().screen.onClose();
                                            openPicker(next);
                                        }
                                        return true;
                                    }
                                }
                            }
                            if (editor.isMouseOver(x, y)) return editor.onMouseClicked(x, y, button);
                            return super.onMouseClicked(x, y, button);
                        }
                    };
                }
            };
            super.visitChildren(widget -> {
                if (widget instanceof OptionListWidget list) {
                    list.setHeight(Math.max(25, anchor.y() - 58 - list.getY()));
                    list.refreshScrollAmount();
                }
            });
            editor.setDimension(anchor);
            editor.setFocused(true);
            editor.onMouseClicked(anchor.x() + 2, anchor.centerY(), 0);
            editor.createOrRemoveColorPicker();
        }

        @Override public void renderBackground(GuiGraphics graphics) {
            super.renderBackground(graphics);
            graphics.drawString(Minecraft.getInstance().font, "Choose a color", left, top, 0xFFFFFFFF);
            boolean editing = editor != null && editor.colorPickerVisible();
            reset.active = !colors.get(selected).isPendingValueDefault();
            if (editing) {
                editor.setDimension(editor.getDimension());
                editor.render(graphics, -1, -1, 0);
            }
        }

        private final class Swatch extends Button {
            private final HqTiersRanks rank;
            Swatch(HqTiersRanks rank) {
                super(0, 0, 0, 0, Component.literal(rank.name()), b -> openPicker(rank), DEFAULT_NARRATION);
                this.rank = rank;
                setTooltip(Tooltip.create(Component.literal("Choose a color for " + rank.name())));
            }
            @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
                var color = new Color(previewRgb(rank));
                int x = getX(), y = getY(), right = x + getWidth(), bottom = y + getHeight();
                g.fill(x, y, right, bottom, isHoveredOrFocused() || rank == selected ? 0xFFFFFFFF : 0xFF697079);
                g.fill(x + 1, y + 1, right - 1, bottom - 1, color.getRGB());
                if (isHovered()) g.requestCursor(CursorTypes.POINTING_HAND);
                var font = Minecraft.getInstance().font;
                int lightness = 299 * color.getRed() + 587 * color.getGreen() + 114 * color.getBlue();
                g.drawString(font, rank.name(), x + (getWidth() - font.width(rank.name())) / 2,
                        y + (getHeight() - font.lineHeight + 1) / 2, lightness >= 147000 ? 0xFF111111 : 0xFFFFFFFF, false);
            }
        }
    }
}
