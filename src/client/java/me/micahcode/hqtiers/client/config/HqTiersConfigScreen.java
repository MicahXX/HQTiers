package me.micahcode.hqtiers.client.config;

import java.util.Arrays;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.model.HqTiersLadder;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HqTiersConfigScreen {

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
                .setDefaultValue(HqTiersClientConfig.DisplayMode.PREFERRED_LADDER)
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
        overlay.addEntry(entries.startBooleanToggle(Component.literal("Hide nametag if Ranked System"), HqTiersClientConfig.suppressRankedDuplicates)
                .setDefaultValue(true)
                .setSaveConsumer(value -> HqTiersClientConfig.suppressRankedDuplicates = value)
                .build());
        overlay.addEntry(entries.startBooleanToggle(Component.literal("Show tab list stats"), HqTiersClientConfig.tabListEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(value -> HqTiersClientConfig.tabListEnabled = value)
                .build());
        overlay.addEntry(entries.startEnumSelector(
                        Component.literal("Stats position"),
                        HqTiersClientConfig.NametagAlignment.class,
                        HqTiersClientConfig.nametagAlignment)
                .setDefaultValue(HqTiersClientConfig.NametagAlignment.LEFT)
                .setSaveConsumer(value -> HqTiersClientConfig.nametagAlignment = value)
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

        builder.setSavingRunnable(HqTiersClientConfig::save);
        return builder.build();
    }
}