package me.micahcode.hqtiers.client.leaderboard;

import me.micahcode.hqtiers.client.fetching.HqTiersCache;
import me.micahcode.hqtiers.client.fetching.MojangProfileResolver;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import me.micahcode.hqtiers.client.nametag.HqTiersFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class PlayerComparisonScreen extends Screen {
    private final Screen parent;
    private final Column first;
    private final Column second = new Column("");
    private final List<String> rows = new ArrayList<>();
    private int scroll;
    private Button compare;

    PlayerComparisonScreen(Screen parent, String name) {
        super(Component.literal("Compare players"));
        this.parent = parent;
        first = new Column(name);
        rows.add("GLOBAL");
        rows.addAll(HqTiersFormatter.KNOWN_LADDERS);
    }

    @Override
    protected void init() {
        int left = left();
        int half = panelWidth() / 2;
        field(first, "First player", left, half - 2);
        field(second, "Second player", left + half + 2, half - 2);
        compare = addRenderableWidget(Button.builder(Component.literal("Compare"), b -> {
            scroll = 0;
            lookup(first);
            lookup(second);
        }).bounds(width / 2 - 58, height - 23, 58, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(width / 2 + 4, height - 23, 54, 18).build());
        updateButton();
    }

    private void field(Column column, String label, int x, int w) {
        var input = new EditBox(font, x, 24, w, 18, Component.literal(label));
        input.setHint(Component.literal(label));
        input.setMaxLength(16);
        input.setValue(column.name);
        input.setResponder(value -> {
            column.name = value.trim();
            column.request++;
            column.stats = null;
            column.status = "";
            updateButton();
        });
        addRenderableWidget(input);
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
        column.stats = null;
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
        column.stats = cache.get(uuid).orElse(null);
        future.whenComplete((stats, error) -> minecraft.execute(() -> {
            if (column.request != request) return;
            column.stats = stats;
            column.status = stats != null ? "" : cache.status(uuid) == HqTiersCache.Status.MISSING
                    ? "No ranked stats" : "Stats unavailable";
        }));
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        int left = left();
        int right = left + panelWidth();
        int labelWidth = Math.min(90, panelWidth() / 4);
        int columnWidth = (panelWidth() - labelWidth) / 2;
        ctx.fill(0, 0, width, height, 0xF0100C05);
        draw(ctx, "Compare players", left, 9, 0xFFFFD86B);
        super.render(ctx, mx, my, delta);
        int x1 = left + labelWidth;
        int x2 = x1 + columnWidth;
        draw(ctx, "Ladder", left + 2, 49, 0xFFFFE7A3);
        draw(ctx, first.name, x1, 49, 0xFFFFE7A3);
        draw(ctx, second.name, x2, 49, 0xFFFFE7A3);
        draw(ctx, first.status, x1, 60, 0xFFE6C888);
        draw(ctx, second.status, x2, 60, 0xFFE6C888);
        if (first.stats == null && second.stats == null) return;
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - capacity()));
        for (int index = scroll; index < Math.min(rows.size(), scroll + capacity()); index++) {
            int y = 74 + (index - scroll) * 15;
            if ((index & 1) == 0) ctx.fill(left, y - 2, right, y + 12, 0x332F2611);
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

    private void drawStats(GuiGraphics ctx, Column column, String ladder, int x, int y, int w) {
        var stats = column.stats == null ? null : column.stats.ladder(ladder).orElse(null);
        String value = "—";
        int color = 0xFF96876A;
        if (stats != null) {
            String rank = stats.hasPosition() ? "#" + stats.position() : "—";
            if (ladder.equals("GLOBAL")) {
                value = rank + "  " + stats.wins() + "W / " + stats.losses() + "L";
                color = 0xFFFFD86B;
            } else if (stats.tier() != null && stats.placementGames() >= stats.placementTarget()) {
                value = stats.tierName() + "  " + stats.progressText(true) + "  " + rank;
                color = 0xFF000000 | stats.tierColorInt();
            }
        }
        draw(ctx, font.plainSubstrByWidth(value, w - 3), x, y, color);
    }

    private void draw(GuiGraphics ctx, String text, int x, int y, int color) {
        ctx.drawString(font, text, x, y, color);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, rows.size() - capacity()));
        return true;
    }

    private int capacity() {
        return Math.max(1, (height - 101) / 15);
    }

    private int panelWidth() {
        return Math.min(560, width - 16);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
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

        Column(String name) {
            this.name = name;
        }
    }
}
