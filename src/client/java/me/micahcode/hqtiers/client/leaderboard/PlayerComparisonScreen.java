package me.micahcode.hqtiers.client.leaderboard;

import me.micahcode.hqtiers.client.fetching.HqTiersCache;
import me.micahcode.hqtiers.client.fetching.MojangProfileResolver;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import me.micahcode.hqtiers.client.nametag.HqTiersFormatter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class PlayerComparisonScreen extends Screen {
    private final Screen parent;
    private final Column first;
    private final Column second = new Column("");
    private final List<String> rows = new ArrayList<>();
    private static final int TABLE_TOP = 96;
    private boolean initialLookup;
    private ComparisonView view = ComparisonView.STANDINGS;
    private int scroll;
    private Button compare;

    PlayerComparisonScreen(Screen parent, String name) {
        this(parent, name, "");
    }

    PlayerComparisonScreen(Screen parent, String name, String other) {
        super(Component.literal("Compare players"));
        this.parent = parent;
        first = new Column(name);
        second.name = other;
        initialLookup = valid(name) && valid(other) && !name.equalsIgnoreCase(other);
        rows.add("GLOBAL");
        rows.addAll(HqTiersFormatter.KNOWN_LADDERS);
    }

    @Override
    protected void init() {
        int left = left();
        int half = panelWidth() / 2;
        field(first, "First player", left, half - 2);
        field(second, "Second player", left + half + 2, half - 2);
        int tabWidth = Math.min(78, (panelWidth() - 8) / ComparisonView.values().length);
        for (var tab : ComparisonView.values()) {
            var button = addRenderableWidget(Button.builder(Component.literal(tab.label), b -> {
                view = tab;
                scroll = 0;
                refreshCells();
                clearWidgets();
                init();
            }).bounds(left + tab.ordinal() * (tabWidth + 3), 46, tabWidth, 17).build());
            button.active = tab != view;
        }
        compare = addRenderableWidget(Button.builder(Component.literal("Compare"), b -> {
            scroll = 0;
            lookup(first);
            lookup(second);
        }).bounds(width / 2 - 58, height - 23, 58, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(width / 2 + 4, height - 23, 54, 18).build());
        updateButton();
        if (initialLookup) {
            initialLookup = false;
            lookup(first);
            lookup(second);
        }
    }

    private void field(Column column, String label, int x, int w) {
        var input = new EditBox(font, x, 24, w, 18, Component.literal(label));
        input.setHint(Component.literal(label));
        input.setMaxLength(16);
        input.setValue(column.name);
        input.setResponder(value -> {
            column.name = value.trim();
            column.request++;
            update(column, null);
            column.status = "";
            updateButton();
        });
        addRenderableWidget(input);
    }

    private void update(Column column, HqTiersStats stats) {
        column.stats = stats;
        refreshCells();
    }

    private void refreshCells() {
        first.update(first.stats, view, second.stats);
        second.update(second.stats, view, first.stats);
    }

    private void updateButton() {
        if (compare != null) compare.active = valid(first.name) && valid(second.name)
                && !first.name.equalsIgnoreCase(second.name);
    }

    private static boolean valid(String name) {
        return name.matches("[A-Za-z0-9_]{1,16}");
    }

    private void lookup(Column column) {
        int request = ++column.request;
        column.status = "Loading…";
        update(column, null);
        var known = HqTiersClientState.leaderboardClient().knownUuid(column.name);
        if (known != null) {
            loadStats(column, request, known);
            return;
        }
        HqTiersClientState.profileResolver().resolve(column.name).whenComplete((result, error) -> minecraft.execute(() -> {
            if (column.request != request) return;
            if (error != null || result.status() == MojangProfileResolver.Status.ERROR) column.status = "Lookup failed";
            else if (result.status() == MojangProfileResolver.Status.NOT_FOUND) column.status = "Player not found";
            else loadStats(column, request, result.profile().uuid());
        }));
    }

    private void loadStats(Column column, int request, UUID uuid) {
        var cache = HqTiersClientState.cache();
        var future = cache.fetchVisible(uuid);
        update(column, cache.get(uuid).orElse(null));
        future.whenComplete((stats, error) -> minecraft.execute(() -> {
            if (column.request != request) return;
            update(column, stats);
            column.status = stats != null ? "" : cache.status(uuid) == HqTiersCache.Status.MISSING
                    ? "No ranked stats" : "Stats unavailable";
        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        int left = left();
        int right = left + panelWidth();
        int labelWidth = Math.min(90, panelWidth() / 4);
        int columnWidth = (panelWidth() - labelWidth) / 2;
        ctx.fill(0, 0, width, height, 0xF0100C05);
        draw(ctx, "Compare players", left, 9, 0xFFFFD86B);
        super.extractRenderState(ctx, mx, my, delta);
        int x1 = left + labelWidth;
        int x2 = x1 + columnWidth;
        draw(ctx, "Ladder", left + 2, 68, 0xFFFFE7A3);
        draw(ctx, font.plainSubstrByWidth(first.name, columnWidth - 3), x1, 68, 0xFFFFE7A3);
        draw(ctx, font.plainSubstrByWidth(second.name, columnWidth - 3), x2, 68, 0xFFFFE7A3);
        draw(ctx, first.status, x1, 80, 0xFFE6C888);
        draw(ctx, second.status, x2, 80, 0xFFE6C888);
        if (first.stats != null && second.stats != null && first.status.isEmpty() && second.status.isEmpty()) {
            draw(ctx, "Green: ahead   Red: behind   Gray: tied / unavailable", left + 2, 80, 0xFFB9AA8B);
        }
        if (first.stats == null && second.stats == null) return;
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - capacity()));
        for (int index = scroll; index < Math.min(rows.size(), scroll + capacity()); index++) {
            int y = TABLE_TOP + (index - scroll) * view.rowHeight;
            if ((index & 1) == 0) ctx.fill(left, y - 2, right, y + view.rowHeight - 3, 0x332F2611);
            String ladder = rows.get(index);
            draw(ctx, font.plainSubstrByWidth(HqTiersFormatter.displayName(ladder), labelWidth - 4),
                    left + 2, y, 0xFFFFF8E8);
            drawStats(ctx, first, ladder, x1, y, columnWidth);
            drawStats(ctx, second, ladder, x2, y, columnWidth);
        }
        if (rows.size() > capacity()) {
            draw(ctx, (scroll + 1) + "–" + Math.min(rows.size(), scroll + capacity()) + " / " + rows.size(),
                    left, height - 18, 0xFFAA9875);
        }
    }

    private void drawStats(GuiGraphicsExtractor ctx, Column column, String ladder, int x, int y, int w) {
        var cell = column.cells.get(ladder);
        if (cell == null) {
            draw(ctx, "—", x, y, 0xFF96876A);
            return;
        }
        drawCell(ctx, cell.first(), x, y, w);
        if (!cell.second().getString().isEmpty()) drawCell(ctx, cell.second(), x, y + 11, w);
    }

    private void drawCell(GuiGraphicsExtractor ctx, Component text, int x, int y, int w) {
        ctx.enableScissor(x, y - 1, x + w - 3, y + 10);
        ctx.text(font, text, x, y, 0xFFFFFFFF, true);
        ctx.disableScissor();
    }

    private void draw(GuiGraphicsExtractor ctx, String text, int x, int y, int color) {
        ctx.text(font, text, x, y, color, true);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, rows.size() - capacity()));
        return true;
    }

    private int capacity() {
        return Math.max(1, (height - 26 - TABLE_TOP) / view.rowHeight);
    }

    private int panelWidth() {
        return Math.min(560, width - 16);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class Column {
        String name;
        String status = "";
        int request;
        HqTiersStats stats;
        final Map<String, ComparisonView.Cell> cells = new HashMap<>();

        void update(HqTiersStats value, ComparisonView view) {
            update(value, view, null);
        }

        void update(HqTiersStats value, ComparisonView view, HqTiersStats opponent) {
            stats = value;
            cells.clear();
            if (value == null) return;
            for (var ladder : value.ladders().values()) {
                if (ladder.hasPlayedRanked()) cells.put(ladder.ladder(), view.cell(ladder, opponent == null ? null : opponent.ladder(ladder.ladder()).orElse(null)));
            }
        }

        Column(String name) {
            this.name = name;
        }
    }
}
