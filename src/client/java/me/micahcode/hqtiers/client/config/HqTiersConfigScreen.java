package me.micahcode.hqtiers.client.config;

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
                    case GLOBAL -> "Show each players overall leaderboard position.";
                }))
                .binding(DisplayMode.HIGHEST_TIER, () -> HqTiersClientConfig.displayMode, value -> HqTiersClientConfig.displayMode = value)
                .controller(option -> EnumControllerBuilder.create(option).enumClass(DisplayMode.class)
                        .formatValue(value -> Component.literal(switch (value) {
                            case HIGHEST_TIER -> "Highest tier";
                            case PREFERRED_LADDER -> "Preferred gamemode";
                            case GLOBAL -> "Global rank";
                        })))
                .build();
        var ladder = Option.<HqTiersLadder>createBuilder()
                .name(Component.literal("Preferred gamemode"))
                .description(description("Choose the ladder used by the Preferred gamemode display mode."))
                .binding(HqTiersLadder.SWORD,
                        () -> Objects.requireNonNullElse(HqTiersLadder.fromString(HqTiersClientConfig.preferredLadder), HqTiersLadder.SWORD),
                        value -> HqTiersClientConfig.preferredLadder = value.name())
                .controller(option -> EnumControllerBuilder.create(option).enumClass(HqTiersLadder.class)
                        .formatValue(value -> Component.empty().append(HqTiersFormatter.icon(value.name())).append(" " + value.displayName())))
                .build();
        var colors = new TierColorOptions();
        var config = YetAnotherConfigLib.createBuilder().title(Component.literal("HQTiers"))
                .category(display(mode, ladder))
                .category(new NametagLayoutOptions(mode, ladder, colors).category())
                .category(colors.category())
                .save(HqTiersClientConfig::save)
                .build();
        return config.generateScreen(parent);
    }

    private static ConfigCategory display(Option<DisplayMode> mode, Option<HqTiersLadder> ladder) {
        return ConfigCategory.createBuilder().name(Component.literal("Display"))
                .group(OptionGroup.createBuilder().name(Component.literal("Player ranking"))
                        .option(mode).option(ladder)
                        .option(toggle("Show unranked", "Show an Unranked label for players without a tier.", false,
                                () -> HqTiersClientConfig.showUnranked, value -> HqTiersClientConfig.showUnranked = value))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Nametags"))
                        .option(toggle("Show nametag stats", "Add PvPHQ stats to names above players.", true,
                                () -> HqTiersClientConfig.nametagEnabled, value -> HqTiersClientConfig.nametagEnabled = value))
                        .option(toggle("Skip duplicate tiers", "Hide the HQTiers stats when a nametag already contains a tier such as HT3 or LT2.", false,
                                () -> HqTiersClientConfig.suppressRankedDuplicates, value -> HqTiersClientConfig.suppressRankedDuplicates = value))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Tab list"))
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
