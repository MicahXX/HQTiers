package me.micahcode.hqtiers.client.config;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Optional;

public class NametagLayoutButtonEntry extends TooltipListEntry<Void> {
    private final Button button;
    private final Screen configScreen;

    public NametagLayoutButtonEntry(Screen configScreen) {
        super(Component.literal("Nametag Layout"), null);
        this.configScreen = configScreen;
        this.button = Button.builder(
                Component.literal("Edit Nametag Layout..."),
                btn -> {
                    HqTiersClientConfig.save();
                    Minecraft.getInstance().setScreen(
                            new NametagLayoutScreen(Minecraft.getInstance().screen)
                    );
                }
        ).bounds(0, 0, 150, 20).build();
    }

    @Override
    public Void getValue() {
        return null;
    }

    @Override
    public Optional<Void> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public void save() {
    }

    @Override
    public boolean isEdited() {
        return false;
    }

    @Override
    public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
        return List.of(button);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(button);
    }

    @Override
    public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
        button.setX(x + entryWidth - 150);
        button.setY(y + 1);
        button.setWidth(150);
        context.drawString(
                Minecraft.getInstance().font,
                Component.literal("Nametag Layout"),
                x, y + 6, 0xFFFFFFFF
        );
        button.render(context, mouseX, mouseY, delta);
    }
}