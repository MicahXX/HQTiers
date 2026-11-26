package me.micahcode.hqtiers.client.config;

import dev.isxander.yacl3.api.Binding;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.cycling.ICyclingController;
import dev.isxander.yacl3.impl.SimpleStateManager;
import me.micahcode.hqtiers.client.config.NametagPresets.Entry;
import me.micahcode.hqtiers.client.config.NametagPresets.Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class NametagPresetOptions {
    final Option<NametagPresets> selector;
    private final Supplier<Layout> snapshot;
    private final Option<IconSet> icons;
    private final BiConsumer<Layout, TierPalettes.Preset> apply;

    NametagPresetOptions(Supplier<Layout> snapshot, BiConsumer<Layout, TierPalettes.Preset> apply, Option<IconSet> icons) {
        this.snapshot = snapshot;
        this.apply = apply;
        this.icons = icons;
        var binding = Binding.generic(NametagPresets.EMPTY, () -> HqTiersClientConfig.nametagPresets,
                value -> HqTiersClientConfig.nametagPresets = value);
        var state = new SimpleStateManager<NametagPresets>(binding) {
            @Override public void resetToDefault(ResetAction action) { reset(); }
            @Override public boolean isDefault() { return "Default".equals(get().active(snapshot.get())); }
        };
        selector = Option.<NametagPresets>createBuilder().name(Component.literal("Layout preset"))
                .description(OptionDescription.of(Component.literal("Custom keeps your personal layout and colors. PvPHQ uses those colors with PvPHQ icons.")))
                .stateManager(state).customController(Selector::new).build();
    }

    void reset() {
        select(0, true);
    }

    private void select(int index, boolean reset) {
        var library = selector.pendingValue();
        var current = snapshot.get();
        var active = library.active(current);
        var target = library.entries().get(index);
        var previous = library.rememberPalette(current);
        var previousIcons = library.rememberIcons(icons.pendingValue());
        var custom = active.equals("Custom") ? current : library.custom();
        if (reset) custom = null;
        var layout = index == 0 && custom != null ? custom : target.layout();
        apply.accept(layout, NametagPresets.usesDefaultPalette(target.name()) ? previous : target.layout().palette());
        icons.requestSet(target.name().equals("PvPHQ") ? IconSet.PVPHQ : previousIcons);
        selector.requestSet(new NametagPresets(library.saved(), target.name(), custom, previous, previousIcons));
    }

    private final class Selector implements ICyclingController<NametagPresets> {
        private final Option<NametagPresets> option;
        Selector(Option<NametagPresets> option) { this.option = option; }
        @Override public Option<NametagPresets> option() { return option; }
        @Override public Component formatValue() {
            var active = option.pendingValue().active(snapshot.get());
            return Component.literal("Default".equals(active) ? "Custom" : active);
        }
        @Override public int getPendingValue() {
            var name = option.pendingValue().active(snapshot.get());
            var entries = option.pendingValue().entries();
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).name().equals(name)) return i;
            return 0;
        }
        @Override public int getCycleLength() { return option.pendingValue().entries().size(); }
        @Override public void setPendingValue(int index) { select(index, false); }
    }

    private static ButtonOption action(String name, Consumer<YACLScreen> callback) {
        return ButtonOption.createBuilder().name(Component.literal(name)).text(Component.literal("\u2026"))
                .action((screen, option) -> callback.accept(screen)).build();
    }

    OptionGroup management(List<Option<?>> settings, TierColorOptions colors) {
        var create = action("Save as preset", screen -> name(screen, false));
        var rename = action("Rename preset", screen -> name(screen, true));
        var delete = action("Delete preset", screen -> {
            var library = selector.pendingValue();
            var active = library.active(snapshot.get());
            if (!isSaved(library, active)) return;
            Minecraft.getInstance().setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    var saved = library.saved().stream().filter(entry -> !entry.name().equals(active)).toList();
                    selector.requestSet(new NametagPresets(saved, "Custom", snapshot.get(), library.defaultPalette(), library.defaultIcons()));
                }
                Minecraft.getInstance().setScreen(screen);
            }, Component.literal("Delete " + active + "?"), Component.literal("Your current layout and colors will stay applied."),
                    Component.literal("Delete"), Component.literal("Cancel")));
        });
        Runnable availability = () -> {
            var library = selector.pendingValue();
            boolean editable = isSaved(library, library.active(snapshot.get()));
            rename.setAvailable(editable);
            delete.setAvailable(editable);
        };
        availability.run();
        selector.addEventListener((option, event) -> availability.run());
        for (var setting : settings) setting.addEventListener((option, event) -> availability.run());
        colors.onPaletteChange(availability);
        return OptionGroup.createBuilder().name(Component.literal("Saved layouts")).option(create).option(rename).option(delete).build();
    }

    private static boolean isSaved(NametagPresets library, String name) {
        return library.saved().stream().anyMatch(e -> e.name().equals(name));
    }

    private void name(YACLScreen screen, boolean rename) {
        var library = selector.pendingValue();
        var current = snapshot.get();
        var active = library.active(current);
        if (rename && !isSaved(library, active)) return;
        Minecraft.getInstance().setScreen(new PresetNameScreen(screen, rename, rename ? active : "",
                text -> library.available(text, rename ? active : null), text -> {
                    var saved = new ArrayList<>(library.saved());
                    if (rename) saved.replaceAll(e -> e.name().equals(active) ? new Entry(text, current) : e);
                    else saved.add(new Entry(text, current));
                    var previousIcons = library.rememberIcons(icons.pendingValue());
                    icons.requestSet(previousIcons);
                    selector.requestSet(new NametagPresets(saved, text, "Custom".equals(active) ? current : library.custom(), library.rememberPalette(current), previousIcons));
                }));
    }
}
