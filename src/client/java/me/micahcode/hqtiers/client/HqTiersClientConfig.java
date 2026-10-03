package me.micahcode.hqtiers.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import me.micahcode.hqtiers.Hqtiers;
import net.fabricmc.loader.api.FabricLoader;

/** Persists client display preferences and migrates older nametag layouts. */
public final class HqTiersClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("hqtiers.json");

    public static boolean nametagEnabled = true;
    public static boolean tabListEnabled = false;
    public static boolean versionCheckEnabled = true;
    public static String preferredLadder = "SWORD";
    public static DisplayMode displayMode = DisplayMode.HIGHEST_TIER;
    public static boolean rankSectionEnabled = true;
    public static boolean shortTierNames = false;
    public static boolean coloredElo = true;
    public static NametagAlignment nametagAlignment = NametagAlignment.LEFT;
    public static Map<NametagComponent, NametagAlignment> componentSides = new EnumMap<>(NametagComponent.class);
    public static List<NametagAlignment> separatorSides = new ArrayList<>();
    public static boolean iconSpacing = true;
    public static boolean nameSeparator = true;
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
    public static boolean showUnranked = false;

    public static List<Boolean> nametagSeparatorStates = new ArrayList<>(defaultSeparatorStates());

    private static final AtomicInteger configVersion = new AtomicInteger(0);

    private HqTiersClientConfig() {}

    private static final Map<String, String> INTERNAL_TO_API = Map.of(
            "DIAMOND_POT", "POT",
            "CART", "HT_CART"
    );

    private static final Map<String, String> API_TO_INTERNAL = Map.of(
            "POT", "DIAMOND_POT",
            "HT_CART", "CART"
    );

    private static final Set<String> UNSUPPORTED_BY_API = Set.of("GLOBAL");

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
        return List.of(false, false);
    }

    public static int configVersion() {
        return configVersion.get();
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
            showUnranked = data.showUnranked;
            coloredTier = data.coloredTier;
            coloredPosition = data.coloredPosition;
            shortTierNames = data.shortTierNames;
            eloEnabled = data.eloEnabled;
            eloLabelEnabled = data.eloLabelEnabled;
            coloredElo = data.coloredElo;
            positionEnabled = data.positionEnabled;
            positionLabelEnabled = data.positionLabelEnabled;
            nametagAlignment = data.nametagAlignment == null ? NametagAlignment.LEFT :
                    parseSide(data.nametagAlignment);
            componentSides.clear();
            if (data.componentSides != null) {
                data.componentSides.forEach((key, value) -> {
                    try { componentSides.put(NametagComponent.valueOf(key), parseSide(value)); }
                    catch (IllegalArgumentException ignored) { /* Ignore unknown future components. */ }
                });
            }
            separatorSides = new ArrayList<>();
            if (data.separatorSides != null) data.separatorSides.forEach(side -> separatorSides.add(parseSide(side)));
            iconSpacing = data.iconSpacing;
            nameSeparator = data.nameSeparator;
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
            configVersion.incrementAndGet();
        } catch (IOException | com.google.gson.JsonParseException | IllegalArgumentException exception) {
            Hqtiers.logger.warn("Failed to load HqTiers config.", exception);
        }
    }

    public static void save() {
        configVersion.incrementAndGet();
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
        String normalized = ladder.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "SPEARMACE", "SPEAR_MACE", "SPEAR" -> "SPEAR_MACE";
            case "CARTS", "MINECART", "MINECARTS" -> "CART";
            default -> normalized;
        };
    }

    public enum DisplayMode {
        PREFERRED_LADDER, HIGHEST_TIER, GLOBAL;

        public static DisplayMode fromName(String name) {
            if (name == null) return HIGHEST_TIER;
            try {
                return DisplayMode.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return HIGHEST_TIER;
            }
        }
    }

    public enum NametagAlignment {
        LEFT, RIGHT
    }

    public enum NametagComponent {
        GAMEMODE_ICON, TIER, SEPARATOR, ELO, POSITION
    }

    public static void normalizeNametagOrder() {
        List<NametagComponent> normalized = new ArrayList<>();
        Set<NametagComponent> seen = java.util.EnumSet.noneOf(NametagComponent.class);
        int separators = 0;
        if (nametagOrder != null) {
            for (NametagComponent component : nametagOrder) {
                if (component == null) continue;
                if (component == NametagComponent.SEPARATOR) {
                    if (separators++ < 2) normalized.add(component);
                } else if (seen.add(component)) normalized.add(component);
            }
        }
        for (NametagComponent component : NametagComponent.values()) {
            if (component != NametagComponent.SEPARATOR && seen.add(component)) normalized.add(component);
        }
        nametagOrder = normalized;
        ensureSeparatorComponents();
        ensureSeparatorStatesSize();
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
            } else if (nametagOrder.isEmpty() || nametagOrder.getFirst() != NametagComponent.SEPARATOR) {
                nametagOrder.addFirst(NametagComponent.SEPARATOR);
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

    private static void ensureSeparatorStatesSize() {
        int needed = separatorCount();
        while (nametagSeparatorStates.size() < needed) {
            nametagSeparatorStates.add(false);
        }
        while (nametagSeparatorStates.size() > needed) {
            nametagSeparatorStates.removeLast();
        }
    }

    public static boolean isSeparatorEnabled(int occurrenceIndex) {
        if (occurrenceIndex < 0 || occurrenceIndex >= nametagSeparatorStates.size()) {
            return true;
        }
        return Boolean.TRUE.equals(nametagSeparatorStates.get(occurrenceIndex));
    }

    public static void setSeparatorEnabled(int occurrenceIndex, boolean enabled) {
        ensureSeparatorStatesSize();
        if (occurrenceIndex >= 0 && occurrenceIndex < nametagSeparatorStates.size()) {
            nametagSeparatorStates.set(occurrenceIndex, enabled);
        }
    }

    /** Reads a side safely, including malformed or missing legacy values. */
    private static NametagAlignment parseSide(String value) {
        try { return NametagAlignment.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException | NullPointerException ignored) { return NametagAlignment.LEFT; }
    }

    /** Returns the selected side, preserving the whole-tag setting from old configs. */
    public static NametagAlignment side(NametagComponent component, int separator) {
        if (component == NametagComponent.SEPARATOR) {
            return separator >= 0 && separator < separatorSides.size() ? separatorSides.get(separator) : nametagAlignment;
        }
        return componentSides.getOrDefault(component, nametagAlignment);
    }

    /** Changes one element's side without moving the other elements. */
    public static void setSide(NametagComponent component, int separator, NametagAlignment side) {
        if (component == NametagComponent.SEPARATOR) {
            while (separatorSides.size() <= separator) separatorSides.add(nametagAlignment);
            separatorSides.set(separator, side);
        } else componentSides.put(component, side);
        changed();
    }

    /** Invalidates formatted nametags immediately while editing a live preview. */
    public static void changed() {
        configVersion.incrementAndGet();
    }

    private static final class Data {
        boolean nametagEnabled = true;
        boolean tabListEnabled = false;
        Boolean versionCheckEnabled = true;
        String preferredLadder = "SWORD";
        String displayMode = DisplayMode.HIGHEST_TIER.name();
        boolean rankSectionEnabled = true;
        boolean shortTierNames = false;
        boolean gamemodeIconEnabled = true;
        boolean tierEnabled = true;
        boolean eloEnabled = false;
        boolean eloLabelEnabled = false;
        boolean coloredElo = true;
        boolean positionEnabled = false;
        boolean positionLabelEnabled = false;
        String nametagAlignment = NametagAlignment.LEFT.name();
        Map<String, String> componentSides;
        List<String> separatorSides;
        boolean iconSpacing = true;
        boolean nameSeparator = true;
        List<String> nametagOrder = null;
        List<Boolean> nametagSeparatorStates = null;
        boolean suppressRankedDuplicates = true;
        boolean coloredTier = true;
        boolean coloredPosition = true;
        boolean showUnranked = false;

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
            data.componentSides = new java.util.HashMap<>();
            HqTiersClientConfig.componentSides.forEach((key, value) -> data.componentSides.put(key.name(), value.name()));
            data.separatorSides = HqTiersClientConfig.separatorSides.stream().map(Enum::name).toList();
            data.iconSpacing = HqTiersClientConfig.iconSpacing;
            data.nameSeparator = HqTiersClientConfig.nameSeparator;
            data.nametagOrder = HqTiersClientConfig.nametagOrder.stream()
                    .map(Enum::name).collect(Collectors.toList());
            data.nametagSeparatorStates = new ArrayList<>(HqTiersClientConfig.nametagSeparatorStates);
            data.suppressRankedDuplicates = HqTiersClientConfig.suppressRankedDuplicates;
            data.coloredTier = HqTiersClientConfig.coloredTier;
            data.coloredPosition = HqTiersClientConfig.coloredPosition;
            data.showUnranked = HqTiersClientConfig.showUnranked;
            return data;
        }
    }
}