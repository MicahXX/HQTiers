package me.micahcode.hqtiers.client.config;

import me.micahcode.hqtiers.client.config.NametagLayoutOptions.Row;
import me.micahcode.hqtiers.client.nametag.StatPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.micahcode.hqtiers.client.config.HqTiersClientConfig.NametagComponent;

import static me.micahcode.hqtiers.client.config.HqTiersClientConfig.NametagAlignment.*;
import static me.micahcode.hqtiers.client.config.HqTiersClientConfig.NametagComponent.*;

record NametagPresets(List<Entry> saved, String selected, Layout custom, TierPalettes.Preset defaultPalette, IconSet defaultIcons) {
    record Layout(List<Row> rows, StatPosition position, boolean spacing, boolean divider,
                  boolean ratingSuffix, boolean redNegativeTr, boolean positionPrefix, boolean globalPrefix, TierPalettes.Preset palette) {
        Layout {
            rows = rows == null ? List.of() : rows.stream().filter(row -> row != null && row.kind() != null && row.side() != null).toList();
            if (palette == null) palette = new TierPalettes.Preset("PvPHQ", Map.of());
            if (position == null) position = StatPosition.INLINE;
        }
        boolean matches(Layout other, boolean matchPalette) {
            return rows.equals(other.rows) && position == other.position && spacing == other.spacing && divider == other.divider
                    && ratingSuffix == other.ratingSuffix && redNegativeTr == other.redNegativeTr && positionPrefix == other.positionPrefix && globalPrefix == other.globalPrefix
                    && (!matchPalette || palette.colors().equals(other.palette.colors()));
        }
    }

    record Entry(String name, Layout layout) {}

    static final NametagPresets EMPTY = new NametagPresets(List.of(), "Default", null, null, null);
    static final List<Entry> BUILT_INS = List.of(
            template("Default", StatPosition.INLINE, 0, GAMEMODE_ICON, TIER),
            template("PvPHQ", StatPosition.ABOVE, 0, GAMEMODE_ICON, TIER, SEPARATOR, ELO),
            template("PvPHQ 2", StatPosition.ABOVE, 0, TIER, SEPARATOR, ELO),
            template("Creator Pick", StatPosition.INLINE, 1, GAMEMODE_ICON, TIER),
            template("Compact", StatPosition.INLINE, 4, TIER),
            template("Leaderboard", StatPosition.INLINE, 1, GAMEMODE_ICON, TIER, POSITION));

    NametagPresets {
        saved = saved == null ? List.of() : saved.stream().filter(entry -> entry != null && entry.name() != null
                && !entry.name().isBlank() && entry.layout() != null).toList();
        if (saved.stream().anyMatch(e -> isNewBuiltin(e.name()))) {
            var names = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            saved.forEach(e -> names.add(e.name()));
            var migrated = new ArrayList<Entry>();
            for (var entry : saved) {
                if (isNewBuiltin(entry.name())) {
                    int suffix = 2;
                    String replacement;
                    do { replacement = entry.name() + " (" + suffix++ + ")"; } while (!names.add(replacement));
                    if (entry.name().equals(selected)) selected = replacement;
                    entry = new Entry(replacement, entry.layout());
                }
                migrated.add(entry);
            }
            saved = List.copyOf(migrated);
        }
    }

    private static boolean isNewBuiltin(String name) {
        return "Creator Pick".equalsIgnoreCase(name) || "PvPHQ 2".equalsIgnoreCase(name);
    }

    private static Entry template(String name, StatPosition position, int palette, NametagComponent... visible) {
        var shown = Set.of(visible);
        var rows = new ArrayList<Row>();
        boolean firstDivider = true;
        for (var kind : HqTiersClientConfig.defaultNametagOrder()) {
            boolean enabled = shown.contains(kind) && (kind != SEPARATOR || firstDivider);
            rows.add(new Row(kind, enabled, kind == POSITION && enabled ? RIGHT : LEFT));
            if (kind == SEPARATOR) firstDivider = false;
        }
        return new Entry(name, new Layout(rows, position, true, visible.length > 1, isPvphq(name), isPvphq(name), shown.contains(POSITION), true,
                palette == 0 ? new TierPalettes.Preset("PvPHQ", Map.of()) : TierPalettes.DEFAULTS.presets().get(palette - 1)));
    }

    List<Entry> entries() {
        var entries = new ArrayList<>(BUILT_INS);
        entries.addAll(saved);
        return entries;
    }

    static boolean isPvphq(String name) {
        return "PvPHQ".equals(name) || "PvPHQ 2".equals(name);
    }

    static boolean usesDefaultPalette(String name) {
        return "Default".equals(name) || isPvphq(name);
    }

    TierPalettes.Preset rememberPalette(Layout current) {
        if (defaultPalette == null || "Default".equals(selected)) return current.palette();
        if (usesDefaultPalette(selected) && defaultIcons != null) return current.palette();
        return defaultPalette;
    }

    IconSet rememberIcons(IconSet current) {
        return "PvPHQ".equals(selected) && current == IconSet.PVPHQ && defaultIcons != null ? defaultIcons : current;
    }

    String active(Layout current) {
        String match = null;
        for (var entry : entries()) {
            if (!entry.layout().matches(current, !usesDefaultPalette(entry.name()))) continue;
            if (entry.name().equals(selected)) return selected;
            if (match == null) match = entry.name();
        }
        return match == null ? "Custom" : match;
    }

    boolean available(String name, String replacing) {
        return !name.isBlank() && !name.equalsIgnoreCase("Custom") && entries().stream()
                .noneMatch(e -> !e.name().equals(replacing) && e.name().equalsIgnoreCase(name));
    }
}
