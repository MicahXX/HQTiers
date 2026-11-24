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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

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
        UP("↑", "Move earlier"), DOWN("↓", "Move later"), LEFT_SIDE("Left", "Before the name"),
        HIDE("Hidden", "Hide this stat"), RIGHT_SIDE("Right", "After the name"), REMOVE("×", "Remove from layout");

        final String label, help;
        Action(String label, String help) { this.label = label; this.help = help; }
    }

    private static final Action[] ACTIONS = Action.values();
    private static final int ROW = 18;
    private static final int HEADER = 15;

    private final class Table extends AbstractWidget {
        private boolean focused;
        private int focusRow;
        private int draggedRow = -1;
        private int focusColumn = Action.LEFT_SIDE.ordinal();

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
                focusColumn = Action.LEFT_SIDE.ordinal();
                option.requestSet(List.copyOf(rows));
            }
            playDownSound();
            revealFocus();
        }

        private boolean compact() { return getDimension().width() < 230; }

        private int width(int column) {
            if (column < 2 || column == 5) return compact() ? 14 : 16;
            if (!compact()) return Math.min(41, (getDimension().width() - 87) / 4);
            return textRenderer.width(label(column)) + 6;
        }
        private String label(int column) {
            return compact() && column == Action.HIDE.ordinal() ? "Off" : ACTIONS[column].label;
        }
        private int x(int column) {
            int x = getDimension().xLimit();
            for (int i = ACTIONS.length - 1; i >= column; i--) {
                x -= width(i);
                if (i == 1 || i == 4) x -= 2;
            }
            return x;
        }
        private int top(int row) { return getDimension().y() + HEADER + row * ROW; }
        private int rowAt(double y) { return (int)Math.floor((y - top(0)) / ROW); }

        private boolean enabled(int row, int col) {
            return (col != 0 || row > 0) && (col != 1 || row + 1 < option.pendingValue().size());
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            var rows = option.pendingValue();
            int start = getDimension().x();
            g.text(textRenderer, "Stat", start + 4, getDimension().y() + 4, 0xFFBCC5D0);
            g.text(textRenderer, "Move", x(0) + 2, getDimension().y() + 4, 0xFFBCC5D0);
            g.text(textRenderer, "Placement", x(2) + 3, getDimension().y() + 4, 0xFFBCC5D0);
            int dividers = 0;
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                int y = top(i);
                g.fill(start, y, getDimension().xLimit(), y + ROW - 1, (i % 2 == 0) ? 0xCF202933 : 0xCF181F28);
                boolean grip = !compact();
                int nameX = start + (grip ? 11 : 4);
                if (grip) {
                    for (int dot = 0; dot < 3; dot++) g.fill(start + 4, y + 4 + 3 * dot, start + 6, y + 6 + 3 * dot, 0xFFA4B3BD);
                }
                String fullName = row.kind() == NametagComponent.SEPARATOR ? "Divider " + (++dividers) : row.name();
                int nameWidth = Math.max(1, x(0) - nameX - 3);
                String name = textRenderer.width(fullName) <= nameWidth ? fullName
                        : textRenderer.plainSubstrByWidth(fullName, Math.max(1, nameWidth - textRenderer.width("…"))) + "…";
                if (!name.equals(fullName) && mx >= start && mx < x(0) && my >= y && my < y + ROW)
                    g.setTooltipForNextFrame(Component.literal(fullName), mx, my);
                g.text(textRenderer, name, nameX, y + 6, row.shown() ? 0xFFFFFFFF : 0xFFBDC5CE);
                if (mx >= start && mx < x(0) && rowAt(my) == i) g.requestCursor(CursorTypes.POINTING_HAND);
                int selected = !row.shown() ? 3 : row.side() == LEFT ? 2 : 4;
                for (int col = 0; col < ACTIONS.length; col++) {
                    int left = x(col), right = left + width(col) - 1;
                    boolean hover = mx >= left && mx < right && my >= y + 1 && my < y + ROW - 1;
                    boolean active = enabled(i, col);
                    boolean chosen = col == selected;
                    int fill = chosen ? (row.shown() ? 0xFF21545B : 0xFF49515C) : 0xFF2C3540;
                    g.fill(left, y + 1, right, y + ROW - 2, fill);
                    if (chosen) g.fill(left, y + ROW - 4, right, y + ROW - 2, row.shown() ? 0xFF76E7DD : 0xFFD1D6DB);
                    if ((hover && active) || (focused && i == focusRow && col == focusColumn)) {
                        g.outline(left, y + 1, right - left, ROW - 3, 0xFFFFFFFF);
                    }
                    g.centeredText(textRenderer, label(col), (left + right) / 2, y + 5,
                            !active ? 0xFF75808C : chosen ? 0xFFFFFFFF : 0xFFCAD3DC);
                    if (hover && active) {
                        g.requestCursor(CursorTypes.POINTING_HAND);
                        if (col < 2 || col == 5) g.setTooltipForNextFrame(Component.literal(ACTIONS[col].help + " (" + row.name() + ")"), mx, my);
                    }
                }
            }
            if (draggedRow >= 0 && isMouseOver(mx, my) && !rows.isEmpty()) {
                int target = Math.clamp(rowAt(my), 0, rows.size() - 1);
                int line = top(target) + (target > draggedRow ? ROW - 2 : 0);
                g.fill(start, line, getDimension().xLimit(), line + 2, 0xFF76E7DD);
            }
            for (int idx = 0; idx <= (adding ? choices.size() : 0); idx++) {
                int rowIndex = rows.size() + idx;
                int y = top(rowIndex);
                boolean hover = isMouseOver(mx, my) && rowAt(my) == rowIndex;
                String label = idx == 0 ? (adding ? "Close choices" : "+ Add stat") :
                        new Row(choices.get(idx - 1), true, LEFT).name();
                g.fill(start, y, getDimension().xLimit(), y + ROW - 2, idx == 0 ? 0xFF243C40 : 0xFF29313B);
                if (hover || focused && focusRow == rowIndex) g.outline(start, y, getDimension().width(), ROW - 2, -1);
                g.centeredText(textRenderer, label, (start + getDimension().xLimit()) / 2, y + 5, -1);
                if (hover) g.requestCursor(CursorTypes.POINTING_HAND);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mx = event.x(), my = event.y();
            draggedRow = -1;
            int row = rowAt(my);
            if (event.button() != 0 || !isMouseOver(mx, my) || row < 0 || row > option.pendingValue().size() + (adding ? choices.size() : 0)) return false;
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
        public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
            return event.button() == 0 && draggedRow >= 0;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            double mx = event.x(), my = event.y();
            if (event.button() != 0 || draggedRow < 0) return false;
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
                case LEFT_SIDE -> rows.set(focusRow, new Row(row.kind(), true, LEFT));
                case RIGHT_SIDE -> rows.set(focusRow, new Row(row.kind(), true, RIGHT));
                case REMOVE -> rows.remove(focusRow);
                case HIDE -> rows.set(focusRow, new Row(row.kind(), false, row.side()));
            }
            option.requestSet(List.copyOf(rows));
            playDownSound();
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (!focused) return false;
            switch (event.key()) {
                case InputConstants.KEY_LEFT -> focusColumn = Math.max(0, focusColumn - 1);
                case InputConstants.KEY_RIGHT -> focusColumn = Math.min(ACTIONS.length - 1, focusColumn + 1);
                case InputConstants.KEY_UP -> focusRow = Math.max(0, focusRow - 1);
                case InputConstants.KEY_DOWN -> focusRow = Math.min(option.pendingValue().size() + (adding ? choices.size() : 0), focusRow + 1);
                case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> activate();
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
