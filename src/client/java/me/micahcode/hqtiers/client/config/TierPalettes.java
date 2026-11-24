package me.micahcode.hqtiers.client.config;

import me.micahcode.hqtiers.client.model.HqTiersRanks;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static me.micahcode.hqtiers.client.model.HqTiersRanks.*;

public record TierPalettes(List<Preset> presets, int active) {
    // Selector order: PvPHQ, named presets, Custom.
    private static final List<HqTiersRanks> CHART_ORDER = List.of(
            HT1, MT1, LT1, HT2, MT2, LT2, HT3, MT3, LT3, HT4, MT4, LT4, HT5, MT5, LT5);
    static final TierPalettes DEFAULTS = new TierPalettes(List.of(
            chart("Preset 1", 0x9EEFFE, 0x56CDDF, 0x39A9CC, 0xA359DA, 0x9146C6, 0x8534BF,
                    0x55D483, 0x36BC67, 0x1C9D4B, 0xFFCA45, 0xE0B03A, 0xC29830, 0xDB8B6E, 0xB4684D, 0x8C4E38),
            chart("Preset 2", 0xFFCA45, 0xE0B03A, 0xC29830, 0xBDE2FF, 0x9DBCF0, 0x7E96C6,
                    0xE08249, 0xC27040, 0xA15C33, 0x90979B, 0x747A7E, 0x585C5F, 0x977145, 0x735635, 0x4F3A23),
            chart("Preset 3", 0xC78DF2, 0x9A5CC6, 0x744796, 0x9EEFFE, 0x56CDDF, 0x39A9CC,
                    0x5CF293, 0x17DD62, 0x11A047, 0xE04F49, 0xD34B45, 0xC0433D, 0xDB8B6E, 0xB4684D, 0x8C4E38)
    ), 0);

    public record Preset(String name, Map<HqTiersRanks, Integer> colors) {
        public Preset {
            name = name == null || name.isBlank() ? "Untitled" : name.strip();
            var valid = new EnumMap<HqTiersRanks, Integer>(HqTiersRanks.class);
            if (colors != null) colors.forEach((tier, rgb) -> {
                if (tier != null && rgb != null && rgb >= 0 && rgb <= 0xFFFFFF) valid.put(tier, rgb);
            });
            colors = Map.copyOf(valid);
        }
    }

    public TierPalettes {
        presets = presets == null ? List.of() : presets.stream().filter(java.util.Objects::nonNull).toList();
        if (active < 0 || active > presets.size() + 1) active = 0;
    }

    private static Preset chart(String name, int... rgb) {
        if (rgb.length != CHART_ORDER.size()) throw new IllegalArgumentException(name);
        var colors = new EnumMap<HqTiersRanks, Integer>(HqTiersRanks.class);
        for (int i = 0; i < rgb.length; i++) colors.put(CHART_ORDER.get(i), rgb[i]);
        return new Preset(name, colors);
    }

    boolean isCustom() { return active == presets.size() + 1; }
    boolean isPreset() { return active > 0 && !isCustom(); }
    String name() { return active == 0 ? "PvPHQ" : isCustom() ? "Custom" : presets.get(active - 1).name(); }
    TierPalettes select(int index) { return new TierPalettes(presets, index); }
    TierPalettes selectCustom() { return select(presets.size() + 1); }

    public int rgb(HqTiersRanks tier, int base, int custom) {
        if (tier == null) return base;
        if (isCustom()) return custom;
        return isPreset() ? presets.get(active - 1).colors().getOrDefault(tier, base) : base;
    }

    boolean nameAvailable(String name, boolean rename) {
        if (name.isBlank() || name.equalsIgnoreCase("PvPHQ") || name.equalsIgnoreCase("Custom")) return false;
        for (int i = 0; i < presets.size(); i++) {
            if ((!rename || i + 1 != active) && presets.get(i).name().equalsIgnoreCase(name)) return false;
        }
        return true;
    }

    TierPalettes add(String name, Map<HqTiersRanks, Integer> colors) {
        var next = new ArrayList<>(presets);
        next.add(new Preset(name, colors));
        return new TierPalettes(next, next.size());
    }

    private TierPalettes replace(Preset preset) {
        var next = new ArrayList<>(presets);
        next.set(active - 1, preset);
        return new TierPalettes(next, active);
    }

    TierPalettes rename(String name) { return replace(new Preset(name, presets.get(active - 1).colors())); }

    TierPalettes recolor(HqTiersRanks rank, int rgb) {
        var colors = new EnumMap<HqTiersRanks, Integer>(HqTiersRanks.class);
        colors.putAll(presets.get(active - 1).colors());
        colors.put(rank, rgb);
        return replace(new Preset(name(), colors));
    }

    TierPalettes delete() {
        var next = new ArrayList<>(presets);
        next.remove(active - 1);
        return new TierPalettes(next, 0);
    }
}
