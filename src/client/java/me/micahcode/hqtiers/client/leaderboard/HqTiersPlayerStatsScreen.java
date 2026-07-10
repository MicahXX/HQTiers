package me.micahcode.hqtiers.client.leaderboard;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.model.HqTiersRankSystem;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class HqTiersPlayerStatsScreen extends Screen {
    private static final DateTimeFormatter GRAPH_DATE = DateTimeFormatter.ofPattern("MMM d");

    private static final int BG_BASE = 0xF0100C05;
    private static final int BG_PANEL = 0xCC1A1408;
    private static final int BG_HEADER = 0xDD2A1E0C;
    private static final int BG_ROW_ALT = 0x18FFF4CC;
    private static final int BG_ROW_HOVER = 0x44D4AF37;
    private static final int BG_GRAPH = 0x33000000;

    private static final int ACCENT_GOLD = 0xFFD4AF37;
    private static final int ACCENT_DIM = 0x66D4AF37;

    private static final int BORDER = 0x446B5520;

    private static final int TEXT_TITLE = 0xFFFFD86B;
    private static final int TEXT_HEADER = 0xFFFFE7A3;
    private static final int TEXT_DIM = 0xFF8F7A42;
    private static final int TEXT_WHITE = 0xFFFFF8E8;

    private static final int AXIS_LINE = 0x33FFE7A3;
    private static final int GRAPH_LINE = 0xCCD4AF37;

    private final Screen parent;
    private final UUID uuid;
    private final String fallbackName;
    private boolean loaded;
    private boolean failed;

    private String selectedLadder = null;
    private List<HqTiersLeaderboardClient.HistoryPoint> historyPoints = null;
    private boolean historyLoading = false;
    private int[] graphXPositions = null;
    private int[] graphYPositions = null;

    public HqTiersPlayerStatsScreen(Screen parent, String uuid, String fallbackName, String autoOpenLadder) {
        super(Text.literal("HqTiers Player Stats"));
        this.parent = parent;
        this.uuid = UUID.fromString(uuid);
        this.fallbackName = fallbackName;
        this.selectedLadder = autoOpenLadder;
        if (autoOpenLadder != null) this.historyLoading = true;
    }

    public HqTiersPlayerStatsScreen(Screen parent, String uuid, String fallbackName) {
        this(parent, uuid, fallbackName, null);
    }

    private int panelLeft() {
        return Math.max(16, width / 2 - 270);
    }

    private int panelRight() {
        return Math.min(width - 16, width / 2 + 270);
    }

    private int panelWidth() {
        return panelRight() - panelLeft();
    }

    private int headerTop() {
        return 8;
    }

    private int headerBottom() {
        return 56;
    }

    private int tableTop() {
        return headerBottom() + 4;
    }

    private int tableBottom() {
        return height - 32;
    }

    private int rowH() {
        return 17;
    }

    @Override
    protected void init() {
        clearChildren();

        String backLabel = selectedLadder != null ? "Back" : "Close";
        addDrawableChild(ButtonWidget.builder(Text.literal(backLabel), btn -> {
            if (selectedLadder != null) {
                selectedLadder = null;
                historyPoints = null;
                graphXPositions = null;
                graphYPositions = null;
                init();
            } else {
                if (client != null) client.setScreen(parent);
            }
        }).dimensions(panelLeft(), height - 26, 78, 18).build());

        // Invisible ladder-row hit-boxes
        if (selectedLadder == null) {
            var cached = HqTiersClientState.cache().getIfFresh(uuid);
            if (cached.isPresent()) addLadderButtons(cached.get());
        }

        // Async data fetch
        HqTiersClientState.cache().fetch(uuid).thenAccept(stats -> {
            if (!loaded) {
                loaded = true;
                failed = stats == null;
                if (client != null && selectedLadder == null) client.execute(this::init);
            }
        });

        // History fetch
        if (selectedLadder != null && historyPoints == null && historyLoading) {
            HqTiersClientState.leaderboardClient()
                    .fetchHistory(uuid.toString(), selectedLadder)
                    .thenAccept(pts -> {
                        if (client != null) client.execute(() -> {
                            historyPoints = pts;
                            historyLoading = false;
                        });
                    });
        }
    }

    private void addLadderButtons(HqTiersStats stats) {
        List<HqTiersStats.LadderStats> ladders = sortedLadders(stats);
        int pl = panelLeft() + 2;
        int pr = panelRight() - 2;
        // Must match renderTable: first data row starts at tt + rowH() + 4
        int y = tableTop() + rowH() + 4;

        for (HqTiersStats.LadderStats l : ladders) {
            if (!l.ladder().equals("GLOBAL")) {
                final String id = l.ladder();
                final int fy = y;
                ButtonWidget btn = ButtonWidget.builder(Text.empty(), b -> openLadder(id))
                        .dimensions(pl, fy, pr - pl, rowH()).build();
                btn.setAlpha(0f);
                addDrawableChild(btn);
            }
            y += rowH(); // always increment, matching renderTable
        }
    }

    private void openLadder(String id) {
        selectedLadder = id;
        historyPoints = null;
        graphXPositions = null;
        graphYPositions = null;
        historyLoading = true;
        init();
        HqTiersClientState.leaderboardClient()
                .fetchHistory(uuid.toString(), id)
                .thenAccept(pts -> {
                    if (client != null) client.execute(() -> {
                        historyPoints = pts;
                        historyLoading = false;
                    });
                });
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int pl = panelLeft(), pr = panelRight();
        int ht = headerTop(), hb = headerBottom();
        int tt = tableTop(), tb = tableBottom();

        // Background vignette
        ctx.fill(0, 0, width, height, BG_BASE);
        // Subtle vertical gradient strips for depth
        ctx.fill(0, 0, width / 4, height, 0x08FFFFFF);

        // Panel body
        ctx.fill(pl, tt, pr, tb, BG_PANEL);
        // Panel border
        ctx.fill(pl, tt, pr, tt + 1, BORDER);
        ctx.fill(pl, tb - 1, pr, tb, BORDER);
        ctx.fill(pl, tt, pl + 1, tb, BORDER);
        ctx.fill(pr - 1, tt, pr, tb, BORDER);

        // Header block
        ctx.fill(pl, ht, pr, hb, BG_HEADER);
        ctx.fill(pl, hb - 1, pr, hb, ACCENT_DIM); // accent bottom border

        // Header text
        ctx.drawCenteredTextWithShadow(textRenderer, "HQTIERS  STATS", width / 2, ht + 6, TEXT_TITLE);
        ctx.drawCenteredTextWithShadow(textRenderer, fallbackName, width / 2, ht + 20, TEXT_WHITE);

        super.render(ctx, mx, my, delta);

        var statsOpt = HqTiersClientState.cache().getIfFresh(uuid);
        if (statsOpt.isEmpty()) {
            String msg = loaded || failed ? "No ranked stats found for this player." : "Loading…";
            int col = loaded || failed ? 0xFFFFD166 : TEXT_DIM;
            ctx.drawCenteredTextWithShadow(textRenderer, msg, width / 2, tt + 40, col);
            return;
        }

        HqTiersStats playerStats = statsOpt.get();

        if (selectedLadder != null) {
            renderGraph(ctx, playerStats, pl, pr, tt, tb, mx, my);
        } else {
            renderTable(ctx, playerStats, pl, pr, tt, tb, mx, my);
        }
    }

    private void renderTable(DrawContext ctx, HqTiersStats stats,
                             int pl, int pr, int tt, int tb, int mx, int my) {
        int pw = pr - pl;

        // Column headers
        int hy = tt + 4;
        ctx.fill(pl + 2, hy, pr - 2, hy + rowH() - 2, BG_HEADER);
        ctx.fill(pl + 2, hy + rowH() - 2, pr - 2, hy + rowH() - 1, ACCENT_DIM);

        ctx.drawTextWithShadow(textRenderer, "LADDER", pl + col(pw, 0), hy + 4, TEXT_HEADER);
        ctx.drawTextWithShadow(textRenderer, "TIER", pl + col(pw, 1), hy + 4, TEXT_HEADER);
        ctx.drawTextWithShadow(textRenderer, "SR", pl + col(pw, 2), hy + 4, TEXT_HEADER);
        ctx.drawTextWithShadow(textRenderer, "RANK", pl + col(pw, 3), hy + 4, TEXT_HEADER);
        ctx.drawTextWithShadow(textRenderer, "W / L", pl + col(pw, 4), hy + 4, TEXT_HEADER);
        ctx.drawTextWithShadow(textRenderer, "STREAK", pl + col(pw, 5), hy + 4, TEXT_HEADER);

        List<HqTiersStats.LadderStats> ladders = sortedLadders(stats);
        if (ladders.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No ranked data.", width / 2, tt + 50, TEXT_DIM);
            return;
        }

        int y = tt + rowH() + 4;
        for (int i = 0; i < ladders.size(); i++) {
            HqTiersStats.LadderStats l = ladders.get(i);
            boolean isGlobal = l.ladder().equals("GLOBAL");
            boolean hovered = !isGlobal && mx >= pl + 2 && mx <= pr - 2
                    && my >= y && my < y + rowH();

            // Row background
            if (hovered) ctx.fill(pl + 2, y, pr - 2, y + rowH(), BG_ROW_HOVER);
            else if (i % 2 == 0) ctx.fill(pl + 2, y, pr - 2, y + rowH(), BG_ROW_ALT);

            // Accent left strip for GLOBAL
            if (isGlobal) ctx.fill(pl + 2, y, pl + 4, y + rowH(), ACCENT_GOLD);

            // Icon + name
            ctx.drawTextWithShadow(textRenderer, HqTiersFormatter.icon(l.ladder()), pl + col(pw, 0), y + 4, TEXT_WHITE);
            ctx.drawTextWithShadow(textRenderer, HqTiersFormatter.displayName(l.ladder()), pl + col(pw, 0) + 12, y + 4, TEXT_WHITE);

            // Tier
            ctx.drawTextWithShadow(textRenderer, l.tierLabel(),
                    pl + col(pw, 1), y + 4, tierColor(l.tierLabel(), l.position()));

            // SR with mini-bar
            int sr = l.totalRating();
            ctx.drawTextWithShadow(textRenderer, sr + " SR", pl + col(pw, 2), y + 4, eloColor(sr));

            // Rank
            String rankStr = l.hasPosition() ? "#" + l.position() : "—";
            int rankCol = l.hasPosition() ? 0xFFFFD700 : TEXT_DIM;
            ctx.drawTextWithShadow(textRenderer, rankStr, pl + col(pw, 3), y + 4, rankCol);

            // W/L
            ctx.drawTextWithShadow(textRenderer, l.wins() + " / " + l.losses(),
                    pl + col(pw, 4), y + 4, wlColor(l.wins(), l.losses()));

            // Streak
            ctx.drawTextWithShadow(textRenderer, streakStr(l.currentStreak()),
                    pl + col(pw, 5), y + 4, streakColor(l.currentStreak()));

            // Hover arrow hint
            if (hovered) ctx.drawTextWithShadow(textRenderer, "→", pr - 14, y + 4, ACCENT_GOLD);

            y += rowH();
        }

        // Footer: best ladder
        int finalY = y;
        stats.bestLadder().ifPresent(best -> {
            int fy = Math.max(tb - 16, finalY + 6);
            ctx.drawTextWithShadow(textRenderer,
                    "Best: " + HqTiersFormatter.displayName(best.ladder()) + "  " + best.tierLabel(),
                    pl + 10, fy, 0xFFFFD700);
        });
    }

    // column x-offsets as fraction of panel width
    private static int col(int pw, int col) {
        return switch (col) {
            case 0 -> 10;
            case 1 -> pw * 32 / 100;
            case 2 -> pw * 48 / 100;
            case 3 -> pw * 62 / 100;
            case 4 -> pw * 74 / 100;
            case 5 -> pw * 89 / 100;
            default -> 10;
        };
    }

    private void renderGraph(DrawContext ctx, HqTiersStats stats,
                             int pl, int pr, int tt, int tb, int mx, int my) {
        // selectedLadder is the raw map key (e.g. "SWORD"), always use it directly
        HqTiersStats.LadderStats ladder = stats.ladders().get(selectedLadder);

        // Ladder title row
        ctx.drawCenteredTextWithShadow(textRenderer,
                HqTiersFormatter.displayName(selectedLadder) + "  ·  SR History",
                width / 2, tt + 5, TEXT_HEADER);

        if (ladder != null) {
            String summary = ladder.tierLabel()
                    + "   " + ladder.totalRating() + " SR"
                    + "   " + ladder.wins() + "W / " + ladder.losses() + "L";
            ctx.drawCenteredTextWithShadow(textRenderer, summary, width / 2, tt + 17,
                    tierColor(ladder.tierLabel(), ladder.position()));
        }

        // Graph bounds
        int gl = pl + 44;
        int gr = pr - 14;
        int gt = tt + 32;
        int gbt = tb - 18;
        int gw = gr - gl;
        int gh = gbt - gt;

        // Graph background
        ctx.fill(gl, gt, gr, gbt, BG_GRAPH);
        // Axis lines
        ctx.fill(gl - 1, gt, gl, gbt + 1, 0x884C7BA7);
        ctx.fill(gl, gbt, gr, gbt + 1, 0x884C7BA7);

        if (historyLoading) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Loading history…", width / 2, gt + gh / 2 - 4, TEXT_DIM);
            return;
        }
        if (historyPoints == null || historyPoints.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No history data.", width / 2, gt + gh / 2 - 4, TEXT_DIM);
            return;
        }

        int n = historyPoints.size();

        // ELO range with padding
        int minElo = historyPoints.stream().mapToInt(HqTiersLeaderboardClient.HistoryPoint::elo).min().orElse(0);
        int maxElo = historyPoints.stream().mapToInt(HqTiersLeaderboardClient.HistoryPoint::elo).max().orElse(1);
        int pad = Math.max(15, (maxElo - minElo) / 8);
        minElo -= pad;
        maxElo += pad;
        int eloRange = Math.max(1, maxElo - minElo);

        // Grid lines + Y labels
        for (int i = 0; i <= 4; i++) {
            int gridElo = minElo + eloRange * i / 4;
            int gy = gbt - (gridElo - minElo) * gh / eloRange;
            ctx.fill(gl, gy, gr, gy + 1, i == 0 ? 0x448EA7D2 : AXIS_LINE);
            String label = Integer.toString(gridElo);
            ctx.drawTextWithShadow(textRenderer, label,
                    gl - textRenderer.getWidth(label) - 3, gy - 4, TEXT_DIM);
        }

        // Build point arrays
        if (graphXPositions == null || graphXPositions.length != n) {
            graphXPositions = new int[n];
            graphYPositions = new int[n];
        }
        for (int i = 0; i < n; i++) {
            graphXPositions[i] = gl + (n == 1 ? gw / 2 : i * gw / (n - 1));
            graphYPositions[i] = gbt - (historyPoints.get(i).elo() - minElo) * gh / eloRange;
        }

        // Derive fill color from tier (gold for gold, purple for T1, etc.)
        int tierRaw = ladder != null ? HqTiersRankSystem.tierColor(ladder.tierLabel(), ladder.position()) : 0x3B82F6;
        int fillColor = (0x22 << 24) | (tierRaw & 0xFFFFFF);
        int lineColor = (0xCC << 24) | (tierRaw & 0xFFFFFF);

        // Filled area — single solid tier color underneath the whole line
        for (int i = 1; i < n; i++) {
            int x1 = graphXPositions[i - 1], y1 = graphYPositions[i - 1];
            int x2 = graphXPositions[i], y2 = graphYPositions[i];
            fillTrapezoid(ctx, x1, y1, x2, y2, gbt, fillColor);
        }

        // Line segments — green when rising, red when falling
        for (int i = 1; i < n; i++) {
            int x1 = graphXPositions[i - 1], y1 = graphYPositions[i - 1];
            int x2 = graphXPositions[i], y2 = graphYPositions[i];
            boolean up = historyPoints.get(i).elo() >= historyPoints.get(i - 1).elo();
            drawThickLine(ctx, x1, y1, x2, y2, up ? 0xCC4ADE80 : 0xCCF87171);
        }

        // Dots — tier color
        for (int i = 0; i < n; i++) {
            int x = graphXPositions[i], y = graphYPositions[i];
            boolean up = i == 0 || historyPoints.get(i).elo() >= historyPoints.get(i - 1).elo();
            int dotCol = up ? 0xFF4ADE80 : 0xFFF87171;
            ctx.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000);
            ctx.fill(x - 1, y - 1, x + 2, y + 2, dotCol);
        }

        // Date labels
        ctx.drawTextWithShadow(textRenderer, dateLabel(historyPoints.get(0).timestamp()),
                gl, gbt + 4, TEXT_DIM);
        String lastDate = dateLabel(historyPoints.get(n - 1).timestamp());
        ctx.drawTextWithShadow(textRenderer, lastDate,
                gr - textRenderer.getWidth(lastDate), gbt + 4, TEXT_DIM);

        // Tooltip on hover
        if (graphXPositions != null && my >= gt && my <= gbt) {
            int closest = -1, bestDist = 10;
            for (int i = 0; i < n; i++) {
                int d = Math.max(Math.abs(mx - graphXPositions[i]), Math.abs(my - graphYPositions[i]));
                if (d < bestDist) {
                    bestDist = d;
                    closest = i;
                }
            }
            if (closest >= 0) renderTooltip(ctx, closest, gl, gr, gt, gbt);
        }
    }

    private void renderTooltip(DrawContext ctx, int idx, int gl, int gr, int gt, int gbt) {
        int elo = historyPoints.get(idx).elo();
        int prev = idx > 0 ? historyPoints.get(idx - 1).elo() : elo;
        int delta = elo - prev;
        String deltaStr = idx == 0 ? "start" : (delta >= 0 ? "+" + delta : Integer.toString(delta));
        String date = dateLabel(historyPoints.get(idx).timestamp());
        int deltaColor = idx == 0 ? TEXT_DIM : (delta >= 0 ? 0xFF4ADE80 : 0xFFF87171);

        // Vertical crosshair
        ctx.fill(graphXPositions[idx], gt, graphXPositions[idx] + 1, gbt, 0x553B82F6);

        // Tooltip box
        int lines = date.isEmpty() ? 2 : 3;
        int tw = Math.max(68, textRenderer.getWidth(date) + 12);
        int th = 8 + lines * 10;
        int tx = Math.min(graphXPositions[idx] + 6, gr - tw - 2);
        int ty = Math.max(gt + 2, graphYPositions[idx] - th - 6);

        ctx.fill(tx - 2, ty - 2, tx + tw + 2, ty + th + 2, 0xF0050810);
        ctx.fill(tx - 2, ty - 2, tx + tw + 2, ty - 1, ACCENT_GOLD);
        ctx.fill(tx - 2, ty - 2, tx - 1, ty + th + 2, ACCENT_DIM);

        ctx.drawTextWithShadow(textRenderer, elo + " SR", tx + 2, ty + 2, eloColor(elo));
        ctx.drawTextWithShadow(textRenderer, deltaStr, tx + 2, ty + 12, deltaColor);
        if (!date.isEmpty())
            ctx.drawTextWithShadow(textRenderer, date, tx + 2, ty + 22, TEXT_HEADER);

        // Highlight dot
        ctx.fill(graphXPositions[idx] - 3, graphYPositions[idx] - 3,
                graphXPositions[idx] + 4, graphYPositions[idx] + 4, 0xFFFFFFFF);
        ctx.fill(graphXPositions[idx] - 2, graphYPositions[idx] - 2,
                graphXPositions[idx] + 3, graphYPositions[idx] + 3, ACCENT_GOLD);
    }

    // ── drawing primitives ─────────────────────────────────────────────────
    private static void drawThickLine(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            ctx.fill(x1 - 1, y1 - 1, x1 + 2, y1 + 2, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            ctx.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    /**
     * Fills the trapezoid between two line endpoints and the bottom baseline.
     */
    private static void fillTrapezoid(DrawContext ctx, int x1, int y1, int x2, int y2, int baseline, int color) {
        if (x2 <= x1) return;
        for (int x = x1; x < x2; x++) {
            // linear interpolate Y on the line at this x
            int lineY = y1 + (y2 - y1) * (x - x1) / Math.max(1, x2 - x1);
            if (lineY < baseline)
                ctx.fill(x, lineY, x + 1, baseline, color);
        }
    }

    private static List<HqTiersStats.LadderStats> sortedLadders(HqTiersStats stats) {
        return stats.ladders().values().stream()
                .filter(l -> l.ladder().equals("GLOBAL")
                        ? (l.wins() > 0 || l.losses() > 0)
                        : (l.wins() > 0 || l.losses() > 0))
                .sorted(Comparator
                        .comparingInt((HqTiersStats.LadderStats l) -> l.ladder().equals("GLOBAL") ? 0 : 1)
                        .thenComparing(Comparator.comparingInt(HqTiersStats.LadderStats::totalRating).reversed()))
                .toList();
    }

    private static String streakStr(int s) {
        if (s > 0) return "+" + s;
        if (s < 0) return Integer.toString(s);
        return "—";
    }

    private static int wlColor(int w, int l) {
        int t = w + l;
        if (t == 0) return TEXT_DIM;
        double r = (double) w / t;
        if (r >= 0.55) return 0xFF4ADE80;
        if (r >= 0.45) return 0xFFB0B8CC;
        return 0xFFF87171;
    }

    private static int streakColor(int s) {
        if (s > 2) return 0xFF4ADE80;
        if (s > 0) return 0xFF86EFAC;
        if (s < -2) return 0xFFF87171;
        if (s < 0) return 0xFFFCA5A5;
        return TEXT_DIM;
    }

    private static int tierColor(String tier, int pos) {
        int c = HqTiersRankSystem.tierColor(tier, pos);
        return c == 0xFFFFFF ? 0xFFB0B8CC : 0xFF000000 | c;
    }

    private static int eloColor(int elo) {
        return 0xFF000000 | HqTiersRankSystem.ratingColor(elo);
    }

    private static String dateLabel(long ts) {
        if (ts <= 0) return "";
        long ms = ts < 10_000_000_000L ? ts * 1000L : ts;
        return GRAPH_DATE.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}