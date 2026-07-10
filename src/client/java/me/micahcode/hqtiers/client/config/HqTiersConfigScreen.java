package me.micahcode.hqtiers.client.config;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class HqTiersConfigScreen {
	private static final String[] LADDERS = {
			"SWORD", "AXE", "UHC", "VANILLA", "MACE", "SPEAR_MACE", "CART",
			"DIAMOND_POT", "NETHERITE_OP", "SMP", "DIAMOND_SMP", "GLOBAL"
	};

	private HqTiersConfigScreen() {
	}

	public static Screen create(Screen parent) {
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.literal("HQTiers"));
		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));
		general.addEntry(entries.startEnumSelector(
						Text.literal("Display mode"),
						HqTiersClientConfig.DisplayMode.class,
						HqTiersClientConfig.displayMode)
				.setDefaultValue(HqTiersClientConfig.DisplayMode.PREFERRED_LADDER)
				.setSaveConsumer(value -> HqTiersClientConfig.displayMode = value)
				.build());
		general.addEntry(entries.startSelector(
						Text.literal("Preferred gamemode"), LADDERS, HqTiersClientConfig.preferredLadder)
				.setDefaultValue("SWORD")
				.setNameProvider(value -> Text.literal(HqTiersFormatter.displayName(value)))
				.setSaveConsumer(value -> HqTiersClientConfig.preferredLadder = HqTiersClientConfig.normalizeLadder(value))
				.build());

		ConfigCategory overlay = builder.getOrCreateCategory(Text.literal("Nametag & Tab"));
		overlay.addEntry(new NametagLayoutButtonEntry(parent));
		overlay.addEntry(entries.startBooleanToggle(Text.literal("Show nametag stats"), HqTiersClientConfig.nametagEnabled)
				.setDefaultValue(true)
				.setSaveConsumer(value -> HqTiersClientConfig.nametagEnabled = value)
				.build());
		overlay.addEntry(entries.startBooleanToggle(Text.literal("Hide nametag if Ranked System"), HqTiersClientConfig.suppressRankedDuplicates)
				.setDefaultValue(true)
				.setSaveConsumer(value -> HqTiersClientConfig.suppressRankedDuplicates = value)
				.build());
		overlay.addEntry(entries.startBooleanToggle(Text.literal("Show tab list stats"), HqTiersClientConfig.tabListEnabled)
				.setDefaultValue(true)
				.setSaveConsumer(value -> HqTiersClientConfig.tabListEnabled = value)
				.build());
		overlay.addEntry(entries.startEnumSelector(
						Text.literal("Stats position"),
						HqTiersClientConfig.NametagAlignment.class,
						HqTiersClientConfig.nametagAlignment)
				.setDefaultValue(HqTiersClientConfig.NametagAlignment.LEFT)
				.setSaveConsumer(value -> HqTiersClientConfig.nametagAlignment = value)
				.build());
		overlay.addEntry(entries.startBooleanToggle(Text.literal("Colored tier in nametag"), HqTiersClientConfig.coloredTier)
				.setDefaultValue(true)
				.setSaveConsumer(value -> HqTiersClientConfig.coloredTier = value)
				.build());
		overlay.addEntry(entries.startBooleanToggle(Text.literal("Colored SR in nametag"), HqTiersClientConfig.coloredElo)
				.setDefaultValue(true)
				.setSaveConsumer(value -> HqTiersClientConfig.coloredElo = value)
				.build());
		overlay.addEntry(entries.startBooleanToggle(Text.literal("Colored position in nametag"), HqTiersClientConfig.coloredPosition)
				.setDefaultValue(true)
				.setSaveConsumer(value -> HqTiersClientConfig.coloredPosition = value)
				.build());

		builder.setSavingRunnable(HqTiersClientConfig::save);
		return builder.build();
	}
}