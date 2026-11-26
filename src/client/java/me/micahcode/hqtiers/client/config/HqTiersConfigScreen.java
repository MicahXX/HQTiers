package me.micahcode.hqtiers.client.config;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import me.micahcode.hqtiers.client.config.HqTiersClientConfig.DisplayMode;
import me.micahcode.hqtiers.client.model.HqTiersLadder;
import me.micahcode.hqtiers.client.nametag.HqTiersFormatter;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class HqTiersConfigScreen {
    private HqTiersConfigScreen() {}

    public static Screen create(Screen parent) {
        var mode = Option.<DisplayMode>createBuilder()
                .name(Component.literal("Display mode"))
                .description(value -> description(switch (value) {
                    case HIGHEST_TIER -> "Show each players strongest ranked gamemode.";
                    case PREFERRED_LADDER -> "Use your chosen gamemode. If the player has no completed placement there, use their strongest gamemode.";
                    case CURRENT_KIT -> "On PvPHQ, use your inventory to identify the current kit. Fall back to each player’s highest tier when the kit or their tier is unknown.";
                    case GLOBAL -> "Show each players overall leaderboard position.";
                }))
                .binding(DisplayMode.HIGHEST_TIER, () -> HqTiersClientConfig.displayMode, value -> HqTiersClientConfig.displayMode = value)
                .controller(option -> EnumControllerBuilder.create(option).enumClass(DisplayMode.class)
                        .formatValue(value -> Component.literal(switch (value) {
                            case HIGHEST_TIER -> "Highest tier";
                            case PREFERRED_LADDER -> "Preferred gamemode";
                            case CURRENT_KIT -> "Current PvPHQ kit";
                            case GLOBAL -> "Global rank";
                        })))
                .build();
        var icons = Option.<IconSet>createBuilder().name(Component.literal("Gamemode icons"))
                .description(description("Choose the icons used for gamemodes throughout HQTiers."))
                .binding(IconSet.ORIGINAL, () -> HqTiersClientConfig.iconSet, value -> HqTiersClientConfig.iconSet = value)
                .controller(option -> EnumControllerBuilder.create(option).enumClass(IconSet.class)
                        .formatValue(value -> Component.literal(value.label))).build();
        var ladder = Option.<HqTiersLadder>createBuilder()
                .name(Component.literal("Preferred gamemode"))
                .description(description("Choose the ladder used by the Preferred gamemode display mode."))
                .binding(HqTiersLadder.SWORD,
                        () -> Objects.requireNonNullElse(HqTiersLadder.fromString(HqTiersClientConfig.preferredLadder), HqTiersLadder.SWORD),
                        value -> HqTiersClientConfig.preferredLadder = value.name())
                .controller(option -> EnumControllerBuilder.create(option).enumClass(HqTiersLadder.class)
                        .formatValue(value -> Component.empty().append(HqTiersFormatter.icon(value.name(), icons.pendingValue())).append(" " + value.displayName())))
                .build();
        var colors = new TierColorOptions();
        var config = YetAnotherConfigLib.createBuilder().title(Component.literal("HQTiers"))
                .category(display(mode, ladder, icons))
                .category(new NametagLayoutOptions(mode, ladder, colors, icons).category())
                .category(colors.category())
                .save(() -> {
                    HqTiersClientConfig.save();
                    if (HqTiersClientConfig.iconSet == IconSet.CUSTOM) CustomIcons.enable();
                })
                .build();
        return new YACLScreen(config, parent) {
            @Override protected void init() {
                super.init();
                removeWidget(tabNavigationBar);
                addWidget(tabNavigationBar);
            }

            @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                int headerHeight = tabArea.top() + 1;
                graphics.enableScissor(0, headerHeight, width, height);
                super.render(graphics, mouseX, mouseY, delta);
                graphics.disableScissor();
                graphics.fill(0, 0, width, headerHeight, 0xFF080808);
                graphics.enableScissor(0, 0, width, headerHeight);
                tabNavigationBar.render(graphics, mouseX, mouseY, delta);
                graphics.disableScissor();
            }
        };
    }

    private static ConfigCategory display(Option<DisplayMode> mode, Option<HqTiersLadder> ladder, Option<IconSet> icons) {
        var display = ConfigCategory.createBuilder().name(Component.literal("Display"))
                .group(OptionGroup.createBuilder().name(Component.literal("Player ranking"))
                        .option(mode).option(ladder)
                        .option(icons)
                        .option(ButtonOption.createBuilder().name(Component.literal("Custom icon folder")).text(Component.literal("Open"))
                                .description(description("Replace the PNGs, select Custom and save. Use F3 + T to reload later edits."))
                                .action((screen, option) -> CustomIcons.openFolder()).build())
                        .option(toggle("Show unranked", "Show an Unranked label for players without a tier.", false,
                                () -> HqTiersClientConfig.showUnranked, value -> HqTiersClientConfig.showUnranked = value))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Nametags"))
                        .option(toggle("Show nametag stats", "Add PvPHQ stats to names above players.", true,
                                () -> HqTiersClientConfig.nametagEnabled, value -> HqTiersClientConfig.nametagEnabled = value))
                        .option(toggle("Skip duplicate tiers", "Hide the HQTiers stats when a nametag already contains a tier such as HT3 or LT2.", false,
                                () -> HqTiersClientConfig.suppressRankedDuplicates, value -> HqTiersClientConfig.suppressRankedDuplicates = value))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Tablist"))
                        .option(toggle("Show tablist stats", "Add PvPHQ stats beside names in the tablist.", false,
                                () -> HqTiersClientConfig.tabListEnabled, value -> HqTiersClientConfig.tabListEnabled = value))
                        .option(toggle("Keep tablist stats on the left", "Place all stats before the name. Turn off to follow your nametag layout.", true,
                                () -> HqTiersClientConfig.tabListAlwaysLeft, value -> HqTiersClientConfig.tabListAlwaysLeft = value))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Chat"))
                        .option(toggle("Show chat stats", "Add PvPHQ stats beside the sender's name in chat.", true,
                                () -> HqTiersClientConfig.chatEnabled, value -> HqTiersClientConfig.chatEnabled = value))
                        .option(toggle("Keep chat stats on the left", "Place all stats before the sender's name. Turn off to follow your nametag layout.", true,
                                () -> HqTiersClientConfig.chatAlwaysLeft, value -> HqTiersClientConfig.chatAlwaysLeft = value))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Leaderboard"))
                        .option(toggle("Show player heads", "Load a skin portrait beside each visible leaderboard entry.", false,
                                () -> HqTiersClientConfig.leaderboardHeads, value -> HqTiersClientConfig.leaderboardHeads = value))
                        .build())
                .build();
        return new SettingsCategory(display, (screen, area) -> new SettingsCategory.AlignedTab(screen, display, area));
    }

    static Option<Boolean> toggle(String name, String help, boolean defaultValue, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder().name(Component.literal(name)).description(description(help))
                .binding(defaultValue, getter, setter)
                .controller(option -> BooleanControllerBuilder.create(option).onOffFormatter().coloured(true))
                .build();
    }

    private static OptionDescription description(String text) {
        return OptionDescription.of(Component.literal(text));
    }
}
