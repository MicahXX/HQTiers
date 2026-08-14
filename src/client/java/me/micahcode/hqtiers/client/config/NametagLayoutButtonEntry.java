package me.micahcode.hqtiers.client.config;

import me.micahcode.hqtiers.client.HqTiersClientConfig;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

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

                    Minecraft minecraft = Minecraft.getInstance();

                    minecraft.gui.setScreen(
                            new NametagLayoutScreen(minecraft.gui.screen())
                    );
                }
        ).width(150).build();
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
    public @NotNull List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
        return List.of(button);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(button);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
        button.setX(x + entryWidth - 154);
        button.setY(y + 1);
        button.setWidth(150);
        context.text(Minecraft.getInstance().font, Component.literal("Nametag Layout"), x, y + 6, 0xFFFFFFFF, true);

        int bx = button.getX();
        int by = button.getY();
        int bw = button.getWidth();
        int bh = button.getHeight();
        boolean buttonHovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        context.fill(bx, by, bx + bw, by + bh, buttonHovered ? 0xFF3B82F6 : 0xFF1F2937);
        context.fill(bx, by, bx + bw, by + 1, buttonHovered ? 0xFF93C5FD : 0xFF4B5563);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, buttonHovered ? 0xFF93C5FD : 0xFF4B5563);
        context.fill(bx, by, bx + 1, by + bh, buttonHovered ? 0xFF93C5FD : 0xFF4B5563);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, buttonHovered ? 0xFF93C5FD : 0xFF4B5563);
        context.centeredText(Minecraft.getInstance().font, button.getMessage(),
                bx + bw / 2, by + (bh - Minecraft.getInstance().font.lineHeight) / 2, 0xFFFFFFFF);
    }
}