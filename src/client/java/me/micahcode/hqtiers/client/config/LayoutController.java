package me.micahcode.hqtiers.client.config;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.OptionListWidget;
import me.micahcode.hqtiers.client.config.HqTiersClientConfig.NametagComponent;
import me.micahcode.hqtiers.client.config.NametagLayoutOptions.Row;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static me.micahcode.hqtiers.client.config.HqTiersClientConfig.NametagAlignment.*;

record LayoutController(Option<List<Row>> option) implements Controller<List<Row>> {
    @Override public Component formatValue() { return option.name(); }

    @Override
    public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> area) {
        return new Table(screen, area);
    }

    private enum Action {
        UP("Up", "Move up"), DOWN("Down", "Move down"), PLACEMENT("Placement", "Change placement"), REMOVE("×", "Remove from layout");

        final String label, help;
        Action(String label, String help) { this.label = label; this.help = help; }
    }

    private static final Action[] ACTIONS = Action.values();
    private static final int ROW = 20;
    private static final int HEADER = 16;

    private final class Table extends AbstractWidget {
        private boolean focused;
        private int focusRow;
        private int draggedRow = -1;
        private int focusColumn = Action.PLACEMENT.ordinal();

        private final YACLScreen screen;
        private boolean adding;
        private List<NametagComponent> choices = List.of();

        Table(YACLScreen screen, Dimension<Integer> area) {
            super(area.withHeight(HEADER + (option.pendingValue().size() + 1) * ROW));
            this.screen = screen;
            option.addEventListener((opt, event) -> {
                adding = false;
                resize();
            });
        }

        private void resize() {
            int rows = option.pendingValue().size();
            focusRow = Math.clamp(focusRow, 0, rows + (adding ? choices.size() : 0));
            int height = HEADER + (rows + 1 + (adding ? choices.size() : 0)) * ROW;
            setDimension(getDimension().withHeight(height));
            for (var child : screen.children()) {
                if (!(child instanceof OptionListWidget list)) continue;
                for (var entry : list.children()) {
                    if (entry instanceof OptionListWidget.OptionEntry row && row.widget == this) {
                        row.setHeight(height + 2);
                        list.setScrollAmount(list.scrollAmount());
                    }
                }
            }
        }

        private void revealFocus() {
            for (var child : screen.children()) {
                if (!(child instanceof OptionListWidget list)) continue;
                int y = top(focusRow);
                int offset = y < list.getY() ? y - list.getY() : Math.max(0, y + ROW - list.getBottom());
                if (offset != 0) list.setScrollAmount(list.scrollAmount() + offset);
            }
        }

        private void addAction(int rowIndex) {
            var rows = new ArrayList<>(option.pendingValue());
            if (rowIndex == rows.size()) {
                choices = java.util.Arrays.stream(NametagComponent.values())
                        .filter(kind -> kind == NametagComponent.SEPARATOR || rows.stream().noneMatch(row -> row.kind() == kind))
                        .toList();
                adding = !adding;
                resize();
            } else {
                rows.add(new Row(choices.get(rowIndex - rows.size() - 1), true, LEFT));
                focusRow = rows.size() - 1;
                focusColumn = Action.PLACEMENT.ordinal();
                option.requestSet(List.copyOf(rows));
            }
            playDownSound();
            revealFocus();
        }

        private boolean compact() { return getDimension().width() < 230; }

        private int width(int column) {
            if (column < 2) return textRenderer.width(moveLabel(column)) + 9;
            if (column == Action.PLACEMENT.ordinal()) return compact() ? 49 : 66;
            return compact() ? 16 : 20;
        }
        private String moveLabel(int column) {
            return compact() ? ACTIONS[column].label : ACTIONS[column].help;
        }

        private int x(int column) {
            int x = getDimension().xLimit();
            for (int i = ACTIONS.length - 1; i >= column; i--) x -= width(i) + 1;
            return x;
        }
        private int top(int row) { return getDimension().y() + HEADER + row * ROW; }
        private int rowAt(double y) { return (int)Math.floor((y - top(0)) / ROW); }

        private boolean enabled(int row, int col) {
            return (col != 0 || row > 0) && (col != 1 || row + 1 < option.pendingValue().size());
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float delta) {
            var rows = option.pendingValue();
            int start = getDimension().x();
            g.drawString(textRenderer, "Stat", start + 4, getDimension().y() + 4, 0xFFCCCCCC);
            g.drawString(textRenderer, "Move", x(0) + 2, getDimension().y() + 4, 0xFFCCCCCC);
            g.drawString(textRenderer, "Placement", x(2) + 3, getDimension().y() + 4, 0xFFCCCCCC);
            int dividers = 0;
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                int y = top(i);
                g.fill(start, y, getDimension().xLimit(), y + ROW - 1, 0x70000000);
                boolean grip = !compact();
                int nameX = start + (grip ? 11 : 4);
                if (grip) {
                    for (int dot = 0; dot < 3; dot++) g.fill(start + 4, y + 4 + 3 * dot, start + 6, y + 6 + 3 * dot, 0xFFAAAAAA);
                }
                String fullName = row.kind() == NametagComponent.SEPARATOR ? "Divider " + (++dividers) : row.name();
                int nameWidth = Math.max(1, x(0) - nameX - 3);
                String name = textRenderer.width(fullName) <= nameWidth ? fullName
                        : textRenderer.plainSubstrByWidth(fullName, Math.max(1, nameWidth - textRenderer.width("…"))) + "…";
                if (!name.equals(fullName) && mx >= start && mx < x(0) && my >= y && my < y + ROW)
                    g.setTooltipForNextFrame(Component.literal(fullName), mx, my);
                g.drawString(textRenderer, name, nameX, y + 6, row.shown() ? 0xFFFFFFFF : 0xFFAAAAAA);
                if (mx >= start && mx < x(0) && rowAt(my) == i) g.requestCursor(CursorTypes.POINTING_HAND);
                for (int col = 0; col < ACTIONS.length; col++) {
                    int left = x(col), right = left + width(col) - 1;
                    boolean hover = mx >= left && mx < right && my >= y + 1 && my < y + ROW - 1;
                    boolean active = enabled(i, col);
                    boolean highlight = hover || focused && i == focusRow && col == focusColumn;
                    drawButtonRect(g, left, y + 1, right, y + ROW - 2, highlight, active);
                    String label = switch (ACTIONS[col]) {
                        case UP, DOWN -> moveLabel(col);
                        case PLACEMENT -> row.placement();
                        case REMOVE -> ACTIONS[col].label;
                    };
                    g.drawCenteredString(textRenderer, label, (left + right) / 2, y + 5,
                            active ? 0xFFFFFFFF : inactiveColor);
                    if (hover && active) {
                        g.requestCursor(CursorTypes.POINTING_HAND);
                        g.setTooltipForNextFrame(Component.literal(ACTIONS[col].help + " (" + row.name() + ")"), mx, my);
                    }
                }
            }
            if (draggedRow >= 0 && isMouseOver(mx, my) && !rows.isEmpty()) {
                int target = Math.clamp(rowAt(my), 0, rows.size() - 1);
                int line = top(target) + (target > draggedRow ? ROW - 2 : 0);
                g.fill(start, line, getDimension().xLimit(), line + 2, 0xFFFFFFFF);
            }
            for (int idx = 0; idx <= (adding ? choices.size() : 0); idx++) {
                int rowIndex = rows.size() + idx;
                int y = top(rowIndex);
                boolean hover = isMouseOver(mx, my) && rowAt(my) == rowIndex;
                String label = idx == 0 ? (adding ? "Close choices" : "+ Add stat") :
                        new Row(choices.get(idx - 1), true, LEFT).name();
                drawButtonRect(g, start, y, getDimension().xLimit(), y + ROW - 2, hover || focused && focusRow == rowIndex, true);
                g.drawCenteredString(textRenderer, label, (start + getDimension().xLimit()) / 2, y + 5, -1);
                if (hover) g.requestCursor(CursorTypes.POINTING_HAND);
            }
        }

        @Override
        public boolean onMouseClicked(double mx, double my, int button) {
            draggedRow = -1;
            int row = rowAt(my);
            if (button != 0 || !isMouseOver(mx, my) || row < 0 || row > option.pendingValue().size() + (adding ? choices.size() : 0)) return false;
            if (row >= option.pendingValue().size()) {
                focusRow = row;
                addAction(row);
                return true;
            }
            if (mx < x(0)) {
                draggedRow = row;
                focusRow = row;
                return true;
            }
            for (int col = 0; col < ACTIONS.length; col++) {
                if (mx >= x(col) && mx < x(col) + width(col) - 1 && enabled(row, col)) {
                    focusRow = row;
                    focusColumn = col;
                    activate();
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onMouseDragged(double mx, double my, int button, double dx, double dy) {
            return button == 0 && draggedRow >= 0;
        }

        @Override
        public boolean onMouseReleased(double mx, double my, int button) {
            if (button != 0 || draggedRow < 0) return false;
            var rows = new ArrayList<>(option.pendingValue());
            int source = draggedRow;
            draggedRow = -1;
            if (isMouseOver(mx, my) && source < rows.size()) {
                int target = Math.clamp(rowAt(my), 0, rows.size() - 1);
                if (target != source) {
                    rows.add(target, rows.remove(source));
                    focusRow = target;
                    option.requestSet(List.copyOf(rows));
                    playDownSound();
                }
            }
            return true;
        }

        private void activate() {
            if (focusRow >= option.pendingValue().size()) {
                addAction(focusRow);
                return;
            }
            if (!enabled(focusRow, focusColumn)) return;
            var rows = new ArrayList<>(option.pendingValue());
            var row = rows.get(focusRow);
            switch (ACTIONS[focusColumn]) {
                case UP, DOWN -> {
                    int target = focusRow + (focusColumn == 0 ? -1 : 1);
                    Collections.swap(rows, focusRow, target);
                    focusRow = target;
                }
                case PLACEMENT -> {
                    if (!row.shown()) rows.set(focusRow, new Row(row.kind(), true, LEFT));
                    else if (row.side() == LEFT) rows.set(focusRow, new Row(row.kind(), true, RIGHT));
                    else rows.set(focusRow, new Row(row.kind(), false, RIGHT));
                }
                case REMOVE -> rows.remove(focusRow);
            }
            option.requestSet(List.copyOf(rows));
            playDownSound();
        }

        @Override
        public boolean onKeyPressed(int key, int scan, int mods) {
            if (!focused) return false;
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> focusColumn = Math.max(0, focusColumn - 1);
                case GLFW.GLFW_KEY_RIGHT -> focusColumn = Math.min(ACTIONS.length - 1, focusColumn + 1);
                case GLFW.GLFW_KEY_UP -> focusRow = Math.max(0, focusRow - 1);
                case GLFW.GLFW_KEY_DOWN -> focusRow = Math.min(option.pendingValue().size() + (adding ? choices.size() : 0), focusRow + 1);
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> activate();
                default -> { return false; }
            }
            revealFocus();
            return true;
        }

        @Override public boolean isFocused() { return focused; }
        @Override public void setFocused(boolean value) { focused = value; }
        @Override public void unfocus() { focused = false; draggedRow = -1; }
        @Override public ComponentPath nextFocusPath(FocusNavigationEvent event) { return focused ? null : ComponentPath.leaf(this); }
        @Override public NarrationPriority narrationPriority() { return focused ? NarrationPriority.FOCUSED : NarrationPriority.NONE; }

        @Override
        public void updateNarration(NarrationElementOutput out) {
            var rows = option.pendingValue();
            if (focusRow >= rows.size()) {
                String label = focusRow == rows.size() ? "Add stat" : new Row(choices.get(focusRow - rows.size() - 1), true, LEFT).name();
                out.add(NarratedElementType.TITLE, Component.literal(label));
                return;
            }
            var row = rows.get(focusRow);
            out.add(NarratedElementType.TITLE, Component.literal(row.name() + ", " + row.placement() + ". " + ACTIONS[focusColumn].help));
            out.add(NarratedElementType.USAGE, Component.literal("Use arrows to select a control. Press Enter to apply."));
        }

        @Override
        public boolean matchesSearch(String query) {
            String lower = query.toLowerCase(Locale.ROOT);
            return option.name().getString().toLowerCase(Locale.ROOT).contains(lower)
                    || option.pendingValue().stream().anyMatch(row -> row.name().toLowerCase(Locale.ROOT).contains(lower));
        }
    }
}
