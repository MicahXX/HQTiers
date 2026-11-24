package me.micahcode.hqtiers.client.config;

import com.google.common.collect.ImmutableList;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.CustomTabProvider;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

import java.util.function.BiFunction;

record SettingsCategory(ConfigCategory options, BiFunction<YACLScreen, ScreenRectangle, Tab> factory)
        implements ConfigCategory, CustomTabProvider {
    @Override public Component name() { return options.name(); }
    @Override public Component tooltip() { return options.tooltip(); }
    @Override public ImmutableList<OptionGroup> groups() { return options.groups(); }
    @Override public Tab createTab(YACLScreen screen, ScreenRectangle area) { return factory.apply(screen, area); }
}
