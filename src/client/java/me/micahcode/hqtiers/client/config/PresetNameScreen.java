package me.micahcode.hqtiers.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

final class PresetNameScreen extends Screen {
    private final Screen parent;
    private final Predicate<String> available;
    private final Consumer<String> submit;
    private String draft;
    private Button done;
    private EditBox name;

    PresetNameScreen(Screen parent, boolean rename, String draft, Predicate<String> available, Consumer<String> submit) {
        super(Component.literal(rename ? "Rename preset" : "Save as preset"));
        this.parent = parent;
        this.draft = draft;
        this.available = available;
        this.submit = submit;
    }

    @Override protected void init() {
        int width = Math.min(this.width - 24, 220);
        int x = (this.width - width) / 2, y = height / 2 - 12;
        name = addRenderableWidget(new EditBox(font, x, y, width, 20, Component.literal("Preset name")));
        name.setMaxLength(24);
        name.setHint(Component.literal("Preset name"));
        name.setValue(draft);
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> accept())
                .bounds(x, y + 38, width / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(x + width / 2 + 2, y + 38, width / 2 - 2, 20).build());
        done.active = available.test(draft.strip());
        name.setResponder(value -> {
            draft = value;
            done.active = available.test(value.strip());
        });
        setInitialFocus(name);
    }

    private void accept() {
        if (done.active) {
            submit.accept(draft.strip());
            onClose();
        }
    }

    @Override public boolean keyPressed(KeyEvent key) {
        if (key.key() == GLFW.GLFW_KEY_ENTER || key.key() == GLFW.GLFW_KEY_KP_ENTER) {
            accept();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        g.drawCenteredString(font, title, width / 2, name.getY() - 18, 0xFFFFFFFF);
        if (!draft.isBlank() && !done.active)
            g.drawCenteredString(font, "That name is already in use", width / 2, name.getY() + 24, 0xFFFFAAAA);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
