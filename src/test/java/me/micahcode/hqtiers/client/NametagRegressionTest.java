package me.micahcode.hqtiers.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.micahcode.hqtiers.client.HqTiersClientConfig.NametagAlignment;
import me.micahcode.hqtiers.client.HqTiersClientConfig.NametagComponent;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Checks observable tag behavior without a running game or a live API. */
class NametagRegressionTest {
    private static Path configDirectory;
    private final HqTiersStats.LadderStats sword = HqTiersStats.LadderStats.minimal("SWORD", 125, 10, 5, 10, "MT4", 123);

    /** Gives the loader a temporary config directory, leaving the user's settings untouched. */
    @BeforeAll
    static void initializeLoader() throws Exception {
        configDirectory = Files.createTempDirectory("hqtiers-test-");
        var field = FabricLoader.getInstance().getClass().getDeclaredField("configDir");
        field.setAccessible(true);
        field.set(FabricLoader.getInstance(), configDirectory);
    }

    /** Resets mutable preferences between independently executed scenarios. */
    @BeforeEach
    void reset() {
        HqTiersClientConfig.nametagOrder = HqTiersClientConfig.defaultNametagOrder();
        HqTiersClientConfig.componentSides = new EnumMap<>(NametagComponent.class);
        HqTiersClientConfig.separatorSides = new ArrayList<>();
        HqTiersClientConfig.nametagSeparatorStates = new ArrayList<>(List.of(false, false));
        HqTiersClientConfig.nametagAlignment = NametagAlignment.LEFT;
        HqTiersClientConfig.displayMode = HqTiersClientConfig.DisplayMode.HIGHEST_TIER;
        HqTiersClientConfig.showUnranked = false;
        HqTiersClientConfig.gamemodeIconEnabled = false;
        HqTiersClientConfig.tierEnabled = true;
        HqTiersClientConfig.eloEnabled = false;
        HqTiersClientConfig.positionEnabled = false;
        HqTiersClientConfig.eloLabelEnabled = false;
        HqTiersClientConfig.positionLabelEnabled = false;
        HqTiersClientConfig.shortTierNames = false;
        HqTiersClientConfig.iconSpacing = true;
        HqTiersClientConfig.nameSeparator = true;
        HqTiersClientConfig.changed();
    }

    /** New layouts place every element on the left and preserve the existing divider. */
    @Test
    void defaultLeft() {
        assertEquals("MT4 | Player", NametagComposer.compose(sword).around(Component.literal("Player")).getString());
        for (NametagComponent component : NametagComponent.values()) {
            assertEquals(NametagAlignment.LEFT, HqTiersClientConfig.side(component, 0));
        }
    }

    /** Mixed sides preserve styling and never decorate the same name twice. */
    @Test
    void independentSidesAndNameStyle() {
        HqTiersClientConfig.eloEnabled = true;
        HqTiersClientConfig.setSide(NametagComponent.ELO, -1, NametagAlignment.RIGHT);
        Component name = Component.literal("Player").withColor(0x123456);
        HqTiersStats stats = new HqTiersStats(UUID.randomUUID(), "Player", Map.of("SWORD", sword), 0);
        Component result = HqTiersFormatter.decorateName(stats, name);
        assertEquals("MT4 | Player | 125", result.getString());
        assertTrue(result.getSiblings().contains(name));
        assertEquals(result, HqTiersFormatter.decorateName(stats, result));
    }

    /** Separators only appear between visible elements, with one normal space otherwise. */
    @Test
    void noDanglingOrDoubledSeparators() {
        HqTiersClientConfig.nametagSeparatorStates = new ArrayList<>(List.of(true, true));
        assertEquals("MT4", NametagComposer.compose(sword).combined().getString());
        HqTiersClientConfig.positionEnabled = true;
        assertEquals("MT4 | 123", NametagComposer.compose(sword).combined().getString());
        HqTiersClientConfig.eloEnabled = true;
        assertEquals("MT4 | 125 | 123", NametagComposer.compose(sword).combined().getString());
    }

    /** Icon spacing can be removed on either side of an icon. */
    @Test
    void compactIcons() {
        HqTiersClientConfig.gamemodeIconEnabled = true;
        String glyph = HqTiersFormatter.icon("SWORD").getString();
        assertEquals(glyph + " MT4", NametagComposer.compose(sword).combined().getString());
        HqTiersClientConfig.iconSpacing = false;
        assertEquals(glyph + "MT4", NametagComposer.compose(sword).combined().getString());
        HqTiersClientConfig.nametagOrder = List.of(NametagComponent.TIER, NametagComponent.GAMEMODE_ICON);
        assertEquals("MT4" + glyph, NametagComposer.compose(sword).combined().getString());
    }

    /** Removing the name divider keeps a readable space and no orphaned boundary. */
    @Test
    void optionalNameDivider() {
        HqTiersClientConfig.nameSeparator = false;
        assertEquals("MT4 Player", NametagComposer.compose(sword).around(Component.literal("Player")).getString());
        HqTiersClientConfig.tierEnabled = false;
        assertEquals("Player", NametagComposer.compose(sword).around(Component.literal("Player")).getString());
    }

    /** Global placement is visible by default and is not repeated by the position toggle. */
    @Test
    void globalRankInsteadOfTier() {
        var global = HqTiersStats.LadderStats.minimal("GLOBAL", 0, 10, 5, 0, null, 1);
        HqTiersClientConfig.positionEnabled = true;
        HqTiersClientConfig.eloEnabled = true;
        assertEquals("#1", NametagComposer.compose(global).combined().getString());
        HqTiersClientConfig.tierEnabled = false;
        assertEquals("#1", NametagComposer.compose(global).combined().getString());
    }

    /** Empty accounts stay hidden globally, even with Show Unranked enabled. */
    @Test
    void unplayedGlobalIsAlwaysHidden() {
        HqTiersClientConfig.displayMode = HqTiersClientConfig.DisplayMode.GLOBAL;
        HqTiersClientConfig.showUnranked = true;
        var stats = HqTiersApiClient.parseRanked(UUID.randomUUID(), """
                {"data":{},"rank":"HT3","globalPosition":0}
                """);
        assertFalse(stats.ladders().get("GLOBAL").hasPlayedRanked());
        assertEquals("Player", HqTiersFormatter.decorateName(stats, Component.literal("Player")).getString());
    }

    /** The legacy profile API's zero-based rank is converted exactly once. */
    @Test
    void profileRankAndParticipation() {
        var stats = HqTiersApiClient.parseRanked(UUID.randomUUID(), """
                {"globalPosition":0,"rank":"HT3","data":{"SWORD":{
                  "wins":3,"losses":2,"gamesPlayed":5,"placementGames":5,"placementTarget":5,
                  "rating":1800,"tr":75,"grantedTier":"HT3","leaderboardPosition":24}}}
                """);
        assertEquals(1, stats.ladder("GLOBAL").orElseThrow().position());
        assertEquals(5, stats.ladder("GLOBAL").orElseThrow().gamesPlayed());
        assertEquals(25, stats.ladder("SWORD").orElseThrow().position());
        assertEquals(75, stats.ladder("SWORD").orElseThrow().tr());
        HqTiersClientConfig.displayMode = HqTiersClientConfig.DisplayMode.GLOBAL;
        assertEquals("#1", HqTiersFormatter.compact(stats).getString());
    }

    /** Highest Tier compares tiers before within-tier TR progress. */
    @Test
    void highestTierUsesTierStrength() {
        var low = HqTiersStats.LadderStats.minimal("SWORD", 500, 10, 5, 10, "LT4", 10);
        var high = HqTiersStats.LadderStats.minimal("AXE", 10, 10, 5, 10, "HT3", 50);
        assertEquals(high, HqTiersFormatter.bestLadder(Map.of("SWORD", low, "AXE", high)).orElseThrow());
    }

    /** Layout preferences survive save/load and old all-right configurations migrate. */
    @Test
    void configRoundTripAndMigration() throws Exception {
        HqTiersClientConfig.iconSpacing = false;
        HqTiersClientConfig.nameSeparator = false;
        HqTiersClientConfig.setSide(NametagComponent.TIER, -1, NametagAlignment.RIGHT);
        HqTiersClientConfig.setSide(NametagComponent.SEPARATOR, 1, NametagAlignment.RIGHT);
        HqTiersClientConfig.save();
        reset();
        HqTiersClientConfig.load();
        assertFalse(HqTiersClientConfig.iconSpacing);
        assertFalse(HqTiersClientConfig.nameSeparator);
        assertEquals(NametagAlignment.RIGHT, HqTiersClientConfig.side(NametagComponent.TIER, -1));
        assertEquals(NametagAlignment.RIGHT, HqTiersClientConfig.side(NametagComponent.SEPARATOR, 1));
        Files.writeString(configDirectory.resolve("hqtiers.json"), "{\"nametagAlignment\":\"RIGHT\"}");
        HqTiersClientConfig.load();
        assertTrue(HqTiersClientConfig.iconSpacing);
        assertTrue(HqTiersClientConfig.nameSeparator);
        assertEquals(NametagAlignment.RIGHT, HqTiersClientConfig.side(NametagComponent.ELO, -1));
    }

    /** Unknown values and damaged order lists cannot crash the config screen. */
    @Test
    void damagedConfig() throws Exception {
        Files.writeString(configDirectory.resolve("hqtiers.json"), """
                {"nametagAlignment":"INVALID","nametagOrder":["TIER","TIER",null],"nametagSeparatorStates":[null]}
                """);
        assertDoesNotThrow(HqTiersClientConfig::load);
        assertEquals(6, HqTiersClientConfig.nametagOrder.size());
        assertFalse(HqTiersClientConfig.isSeparatorEnabled(0));
        assertEquals(NametagAlignment.LEFT, HqTiersClientConfig.side(NametagComponent.TIER, -1));
    }
}
