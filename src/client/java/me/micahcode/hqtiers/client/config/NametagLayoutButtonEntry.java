package me.micahcode.hqtiers.client.config;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

public class NametagLayoutButtonEntry extends TooltipListEntry<Void> {
    private final ButtonWidget button;
    private final Screen configScreen;

    public NametagLayoutButtonEntry(Screen configScreen) {
        super(Text.literal("Nametag Layout"), null);
        this.configScreen = configScreen;
        this.button = ButtonWidget.builder(
                Text.literal("Edit Nametag Layout..."),
                btn -> {
                    HqTiersClientConfig.save();
                    MinecraftClient.getInstance().setScreen(
                            new NametagLayoutScreen(MinecraftClient.getInstance().currentScreen)
                    );
                }
        ).dimensions(0, 0, 150, 20).build();
    }

    @Override
    public Void getValue() { return null; }

    @Override
    public Optional<Void> getDefaultValue() { return Optional.empty(); }

    @Override
    public void save() {}

    @Override
    public boolean isEdited() { return false; }

    @Override
    public List<? extends net.minecraft.client.gui.Element> children() {
        return List.of(button);
    }

    @Override
    public List<? extends Selectable> narratables() {
        return List.of(button);
    }

    @Override
    public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
        button.setX(x + entryWidth - 150);
        button.setY(y + 1);
        button.setWidth(150);
        context.drawTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                Text.literal("Nametag Layout"),
                x, y + 6, 0xFFFFFFFF
        );
        button.render(context, mouseX, mouseY, delta);
    }
}