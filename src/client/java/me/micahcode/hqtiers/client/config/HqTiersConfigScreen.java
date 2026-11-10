package me.micahcode.hqtiers.client.config;

import java.util.Arrays;

import me.micahcode.hqtiers.client.nametag.HqTiersFormatter;
import me.micahcode.hqtiers.client.model.HqTiersLadder;
import me.micahcode.hqtiers.client.model.HqTiersRanks;
import me.micahcode.hqtiers.client.model.HqTiersRankSystem;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HqTiersConfigScreen {
    // all settings besides nametaglayout that are on modmenu
    private static final String[] LADDERS = Arrays.stream(HqTiersLadder.values())
            .map(Enum::name)
            .toArray(String[]::new);

    private HqTiersConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("HQTiers"));
        ConfigEntryBuilder entries = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        general.addEntry(entries.startEnumSelector(
                        Component.literal("Display mode"),
                        HqTiersClientConfig.DisplayMode.class,
                        HqTiersClientConfig.displayMode)
                .setDefaultValue(HqTiersClientConfig.DisplayMode.HIGHEST_TIER)
                .setSaveConsumer(value -> HqTiersClientConfig.displayMode = value)
                .build());
        general.addEntry(entries.startSelector(
                        Component.literal("Preferred gamemode"), LADDERS, HqTiersClientConfig.preferredLadder)
                .setDefaultValue("SWORD")
                .setNameProvider(value -> Component.literal(HqTiersFormatter.displayName(value)))
                .setSaveConsumer(value -> HqTiersClientConfig.preferredLadder = HqTiersClientConfig.normalizeLadder(value))
                .build());

        ConfigCategory overlay = builder.getOrCreateCategory(Component.literal("Nametag & Tab"));
        overlay.addEntry(new NametagLayoutButtonEntry(parent));
        overlay.addEntry(entries.startBooleanToggle(Component.literal("Show nametag stats"), HqTiersClientConfig.nametagEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(value -> HqTiersClientConfig.nametagEnabled = value)
                .build());
        overlay.addEntry(entries.startBooleanToggle(Component.literal("Hide HQTiers stats when a tier is already shown"), HqTiersClientConfig.suppressRankedDuplicates)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Skip HQTiers nametag stats when the existing name contains a tier label such as [HT3] or LT2. Your name stays visible."))
                .setSaveConsumer(value -> HqTiersClientConfig.suppressRankedDuplicates = value)
                .build());
        overlay.addEntry(entries.startBooleanToggle(Component.literal("Show tab list stats"), HqTiersClientConfig.tabListEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(value -> HqTiersClientConfig.tabListEnabled = value)
                .build());
        overlay.addEntry(entries.startBooleanToggle(Component.literal("Always place tab list stats on the left"), HqTiersClientConfig.tabListAlwaysLeft)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Turn off to follow the sides in your nametag layout."))
                .setSaveConsumer(value -> HqTiersClientConfig.tabListAlwaysLeft = value)
                .build());
        overlay.addEntry(entries.startBooleanToggle(Component.literal("Show chat stats"), HqTiersClientConfig.chatEnabled)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Show the configured nametag layout beside chat sender names. Toggle with U by default."))
                .setSaveConsumer(value -> HqTiersClientConfig.chatEnabled = value)
                .build());
        overlay.addEntry(entries.startBooleanToggle(Component.literal("Always place chat stats on the left"), HqTiersClientConfig.chatAlwaysLeft)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Turn off to follow the sides in your nametag layout."))
                .setSaveConsumer(value -> HqTiersClientConfig.chatAlwaysLeft = value)
                .build());
        overlay.addEntry(entries.startBooleanToggle(Component.literal("Colored tier in nametag"), HqTiersClientConfig.coloredTier)
                .setDefaultValue(true)
                .setSaveConsumer(value -> HqTiersClientConfig.coloredTier = value)
                .build());
        overlay.addEntry(entries.startBooleanToggle(Component.literal("Colored TR in nametag"), HqTiersClientConfig.coloredElo)
                .setDefaultValue(true)
                .setSaveConsumer(value -> HqTiersClientConfig.coloredElo = value)
                .build());
        overlay.addEntry(entries.startBooleanToggle(Component.literal("Colored position in nametag"), HqTiersClientConfig.coloredPosition)
                .setDefaultValue(true)
                .setSaveConsumer(value -> HqTiersClientConfig.coloredPosition = value)
                .build());
        overlay.addEntry(entries.startBooleanToggle(
                        Component.literal("Show Unranked"),
                        HqTiersClientConfig.showUnranked)
                .setDefaultValue(false)
                .setSaveConsumer(value -> HqTiersClientConfig.showUnranked = value)
                .build());

        ConfigCategory colors = builder.getOrCreateCategory(Component.literal("Tier colors"));
        colors.addEntry(entries.startBooleanToggle(Component.literal("Use custom tier colors"), HqTiersClientConfig.customTierColors)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Off uses PvPHQ colors. Turn on to apply your color overrides."))
                .setSaveConsumer(value -> HqTiersClientConfig.customTierColors = value)
                .build());
        for (HqTiersRanks tier : HqTiersRanks.values()) {
            int defaultColor = HqTiersRankSystem.tierColor(tier);
            colors.addEntry(entries.startColorField(Component.literal(tier.name()),
                            HqTiersClientConfig.tierColors.getOrDefault(tier, defaultColor))
                    .setDefaultValue(defaultColor)
                    .setTooltip(Component.literal("Reset to use the color supplied by PvPHQ."))
                    .setSaveConsumer(value -> {
                        int rgb = value & 0xFFFFFF;
                        if (rgb == defaultColor) HqTiersClientConfig.tierColors.remove(tier);
                        else HqTiersClientConfig.tierColors.put(tier, rgb);
                    }).build());
        }
        builder.setSavingRunnable(HqTiersClientConfig::save);
        return builder.build();
    }
}