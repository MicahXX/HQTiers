package me.micahcode.hqtiers.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import me.micahcode.hqtiers.Hqtiers;
import net.fabricmc.loader.api.FabricLoader;

public final class HqTiersClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("hqtiers.json");

    public static boolean nametagEnabled = true;
    public static boolean tabListEnabled = true;
    public static boolean versionCheckEnabled = true;
    public static String preferredLadder = "SWORD";
    public static DisplayMode displayMode = DisplayMode.PREFERRED_LADDER;
    public static boolean rankSectionEnabled = true;
    public static boolean shortTierNames = false;
    public static boolean coloredElo = true;
    public static NametagAlignment nametagAlignment = NametagAlignment.LEFT;
    public static boolean gamemodeIconEnabled = true;
    public static boolean tierEnabled = true;
    public static boolean eloEnabled = false;
    public static boolean eloLabelEnabled = false;
    public static boolean positionEnabled = false;
    public static boolean positionLabelEnabled = false;
    public static boolean suppressRankedDuplicates = true;
    public static boolean coloredTier = true;
    public static boolean coloredPosition = true;
    public static List<NametagComponent> nametagOrder = defaultNametagOrder();

    public static List<Boolean> nametagSeparatorStates = new ArrayList<>(defaultSeparatorStates());

    private HqTiersClientConfig() {}

    private static final Map<String, String> INTERNAL_TO_API = Map.of(
            "DIAMOND_POT", "POT",
            "NETHERITE_OP", "NETHERITE_POT"
    );
    private static final Map<String, String> API_TO_INTERNAL = Map.of(
            "POT", "DIAMOND_POT",
            "NETHERITE_POT", "NETHERITE_OP"
    );
    // Ladders the mod knows about (or that a person could type into a config/
    // command) that have no backing endpoint on the real API at all.
    private static final Set<String> UNSUPPORTED_BY_API = Set.of("GLOBAL", "CART", "SPEAR_MACE");

    /**
     * Translates an internal ladder key to the key the PvPHQ API expects.
     * Returns empty if this ladder has no leaderboard/history/ranked-stats
     * support on the real API (e.g. GLOBAL, CART, SPEAR_MACE).
     */
    public static Optional<String> toApiLadder(String internalLadder) {
        String normalized = normalizeLadder(internalLadder);
        if (UNSUPPORTED_BY_API.contains(normalized)) {
            return Optional.empty();
        }
        return Optional.of(INTERNAL_TO_API.getOrDefault(normalized, normalized));
    }
    public static String fromApiLadder(String apiLadder) {
        String normalized = normalizeLadder(apiLadder);
        return API_TO_INTERNAL.getOrDefault(normalized, normalized);
    }

    public static List<NametagComponent> defaultNametagOrder() {
        return new ArrayList<>(List.of(
                NametagComponent.GAMEMODE_ICON, NametagComponent.TIER, NametagComponent.SEPARATOR,
                NametagComponent.ELO, NametagComponent.SEPARATOR, NametagComponent.POSITION
        ));
    }

    private static List<Boolean> defaultSeparatorStates() {
        // Matches the two SEPARATOR entries in defaultNametagOrder().
        return List.of(true, true);
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;

            nametagEnabled = data.nametagEnabled;
            tabListEnabled = data.tabListEnabled;
            versionCheckEnabled = data.versionCheckEnabled == null || data.versionCheckEnabled;
            preferredLadder = normalizeLadder(data.preferredLadder == null ? "SWORD" : data.preferredLadder);
            displayMode = DisplayMode.fromName(data.displayMode);
            rankSectionEnabled = data.rankSectionEnabled;
            gamemodeIconEnabled = data.gamemodeIconEnabled;
            tierEnabled = data.tierEnabled;
            if (!rankSectionEnabled) {
                gamemodeIconEnabled = false;
                tierEnabled = false;
            }
            coloredTier = data.coloredTier;
            coloredPosition = data.coloredPosition;
            shortTierNames = data.shortTierNames;
            eloEnabled = data.eloEnabled;
            eloLabelEnabled = data.eloLabelEnabled;
            coloredElo = data.coloredElo;
            positionEnabled = data.positionEnabled;
            positionLabelEnabled = data.positionLabelEnabled;
            nametagAlignment = data.nametagAlignment == null ? NametagAlignment.LEFT :
                    NametagAlignment.valueOf(data.nametagAlignment.toUpperCase());
            suppressRankedDuplicates = data.suppressRankedDuplicates;
            if (data.nametagOrder != null && !data.nametagOrder.isEmpty()) {
                nametagOrder = new ArrayList<>();
                for (String s : data.nametagOrder) {
                    try { nametagOrder.add(NametagComponent.valueOf(s)); } catch (Exception ignored) {}
                }
                if (nametagOrder.isEmpty()) nametagOrder = defaultNametagOrder();
            } else {
                nametagOrder = defaultNametagOrder();
            }
            nametagSeparatorStates = data.nametagSeparatorStates != null
                    ? new ArrayList<>(data.nametagSeparatorStates)
                    : new ArrayList<>();
            normalizeNametagOrder();
        } catch (IOException exception) {
            Hqtiers.logger.warn("Failed to load HqTiers config.", exception);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(Data.fromCurrent(), writer);
            }
        } catch (IOException exception) {
            Hqtiers.logger.warn("Failed to save HqTiers config.", exception);
        }
    }

    public static String normalizeLadder(String ladder) {
        String normalized = ladder.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "SPEARMACE", "SPEAR_MACE", "SPEAR" -> "SPEAR_MACE";
            case "CARTS", "MINECART", "MINECARTS" -> "CART";
            default -> normalized;
        };
    }

    public enum DisplayMode {
        PREFERRED_LADDER, HIGHEST_TIER, GLOBAL;

        public static DisplayMode fromName(String name) {
            if (name == null) return PREFERRED_LADDER;
            try {
                return DisplayMode.valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return PREFERRED_LADDER;
            }
        }
    }

    public enum NametagAlignment {
        LEFT, RIGHT;

        public NametagAlignment next() {
            return this == LEFT ? RIGHT : LEFT;
        }
    }

    public enum NametagComponent {
        GAMEMODE_ICON, TIER, SEPARATOR, ELO, POSITION
    }

    public static void normalizeNametagOrder() {
        nametagOrder = hasNametagPart(nametagOrder) ? new ArrayList<>(nametagOrder) : defaultNametagOrder();
        ensureSeparatorComponents();
        ensureSeparatorStatesSize();
    }

    private static boolean hasNametagPart(List<NametagComponent> components) {
        for (NametagComponent component : components) {
            if (component != NametagComponent.SEPARATOR) return true;
        }
        return false;
    }

    private static void ensureSeparatorComponents() {
        while (separatorCount() < 2) {
            int positionIndex = nametagOrder.indexOf(NametagComponent.POSITION);
            if (positionIndex > 0 && nametagOrder.get(positionIndex - 1) != NametagComponent.SEPARATOR) {
                nametagOrder.add(positionIndex, NametagComponent.SEPARATOR);
                continue;
            }

            int tierIndex = nametagOrder.indexOf(NametagComponent.TIER);
            if (tierIndex >= 0 && tierIndex < nametagOrder.size() - 1
                    && nametagOrder.get(tierIndex + 1) != NametagComponent.SEPARATOR) {
                nametagOrder.add(tierIndex + 1, NametagComponent.SEPARATOR);
            } else if (nametagOrder.isEmpty() || nametagOrder.get(0) != NametagComponent.SEPARATOR) {
                nametagOrder.add(0, NametagComponent.SEPARATOR);
            } else {
                nametagOrder.add(NametagComponent.SEPARATOR);
            }
        }
    }

    private static int separatorCount() {
        int count = 0;
        for (NametagComponent component : nametagOrder) {
            if (component == NametagComponent.SEPARATOR) count++;
        }
        return count;
    }

    /**
     * Pads or trims nametagSeparatorStates so it has exactly one entry per
     * SEPARATOR currently in nametagOrder. New separators default to enabled.
     */
    private static void ensureSeparatorStatesSize() {
        int needed = separatorCount();
        while (nametagSeparatorStates.size() < needed) {
            nametagSeparatorStates.add(true);
        }
        while (nametagSeparatorStates.size() > needed) {
            nametagSeparatorStates.remove(nametagSeparatorStates.size() - 1);
        }
    }

    /** Whether the Nth separator (0-based, in list order) is enabled. */
    public static boolean isSeparatorEnabled(int occurrenceIndex) {
        if (occurrenceIndex < 0 || occurrenceIndex >= nametagSeparatorStates.size()) {
            return true;
        }
        return nametagSeparatorStates.get(occurrenceIndex);
    }

    /** Sets whether the Nth separator (0-based, in list order) is enabled. */
    public static void setSeparatorEnabled(int occurrenceIndex, boolean enabled) {
        ensureSeparatorStatesSize();
        if (occurrenceIndex >= 0 && occurrenceIndex < nametagSeparatorStates.size()) {
            nametagSeparatorStates.set(occurrenceIndex, enabled);
        }
    }

    private static final class Data {
        boolean nametagEnabled = true;
        boolean tabListEnabled = true;
        Boolean versionCheckEnabled = true;
        String preferredLadder = "SWORD";
        String displayMode = DisplayMode.PREFERRED_LADDER.name();
        boolean rankSectionEnabled = true;
        boolean shortTierNames = false;
        boolean gamemodeIconEnabled = true;
        boolean tierEnabled = false;
        boolean eloEnabled = false;
        boolean eloLabelEnabled = false;
        boolean coloredElo = true;
        boolean positionEnabled = false;
        boolean positionLabelEnabled = false;
        String nametagAlignment = NametagAlignment.LEFT.name();
        List<String> nametagOrder = null;
        List<Boolean> nametagSeparatorStates = null;
        boolean suppressRankedDuplicates = true;
        boolean coloredTier = true;
        boolean coloredPosition = false;

        static Data fromCurrent() {
            Data data = new Data();
            data.nametagEnabled = HqTiersClientConfig.nametagEnabled;
            data.tabListEnabled = HqTiersClientConfig.tabListEnabled;
            data.versionCheckEnabled = HqTiersClientConfig.versionCheckEnabled;
            data.preferredLadder = HqTiersClientConfig.preferredLadder;
            data.displayMode = HqTiersClientConfig.displayMode.name();
            data.rankSectionEnabled = HqTiersClientConfig.gamemodeIconEnabled || HqTiersClientConfig.tierEnabled;
            data.gamemodeIconEnabled = HqTiersClientConfig.gamemodeIconEnabled;
            data.tierEnabled = HqTiersClientConfig.tierEnabled;
            data.shortTierNames = HqTiersClientConfig.shortTierNames;
            data.eloEnabled = HqTiersClientConfig.eloEnabled;
            data.eloLabelEnabled = HqTiersClientConfig.eloLabelEnabled;
            data.coloredElo = HqTiersClientConfig.coloredElo;
            data.positionEnabled = HqTiersClientConfig.positionEnabled;
            data.positionLabelEnabled = HqTiersClientConfig.positionLabelEnabled;
            data.nametagAlignment = HqTiersClientConfig.nametagAlignment.name();
            data.nametagOrder = HqTiersClientConfig.nametagOrder.stream()
                    .map(Enum::name).collect(Collectors.toList());
            data.nametagSeparatorStates = new ArrayList<>(HqTiersClientConfig.nametagSeparatorStates);
            data.suppressRankedDuplicates = HqTiersClientConfig.suppressRankedDuplicates;
            data.coloredTier = HqTiersClientConfig.coloredTier;
            data.coloredPosition = HqTiersClientConfig.coloredPosition;
            return data;
        }
    }
}