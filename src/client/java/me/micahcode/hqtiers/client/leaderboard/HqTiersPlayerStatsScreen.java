package me.micahcode.hqtiers.client.leaderboard;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.micahcode.hqtiers.client.nametag.HqTiersFormatter;
import me.micahcode.hqtiers.client.fetching.HqTiersCache;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HqTiersPlayerStatsScreen extends Screen {
    private static final DateTimeFormatter HOVER_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

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
    private static final int COMPACT_WIDTH = 360;

    private static final int FOOTER_HEIGHT = 26;
    private static final int COLUMN_HEADER_HEIGHT = 16;
    private static final int MIN_ROW_HEIGHT = 16;

    private final Screen parent;
    private final UUID uuid;
    private final String fallbackName;
    private HqTiersStats displayedStats;

    private int tablePage;
    private List<HqTiersStats.LadderStats> visibleLadders = List.of();
    private String selectedLadder;
    private HistoryRange historyRange = HistoryRange.MONTH;
    private boolean historyFailed;
    private List<RatingHistoryClient.Point> historyPoints = null;
    private boolean historyLoading = false;
    private int historyRequest;
    private boolean historyRequested;
    private List<RatingHistoryClient.Point> plottedSource;
    private MatchPlot plot;
    private int plotWidth;
    private int minRating;
    private int maxRating;
    private int[] graphXPositions = null;
    private int[] graphYPositions = null;

    public HqTiersPlayerStatsScreen(Screen parent, String uuid, String fallbackName, String autoOpenLadder) {
        super(Component.literal("HqTiers Player Stats"));
        this.parent = parent;
        this.uuid = UUID.fromString(uuid);
        this.fallbackName = fallbackName;
        this.selectedLadder = autoOpenLadder == null || autoOpenLadder.equalsIgnoreCase("GLOBAL")
                ? null : autoOpenLadder.toLowerCase(java.util.Locale.ROOT);
        if (selectedLadder != null) this.historyLoading = true;
    }

    public HqTiersPlayerStatsScreen(Screen parent, String uuid, String fallbackName) {
        this(parent, uuid, fallbackName, null);
    }

    private int topInset() {
        return RankedScreenStyle.top(height);
    }

    private int panelBottom() {
        return RankedScreenStyle.bottom(height);
    }

    private int panelWidth() {
        return RankedScreenStyle.panelWidth(width);
    }

    private boolean compact() {
        return panelWidth() < COMPACT_WIDTH;
    }

    private int panelLeft() {
        return width / 2 - panelWidth() / 2;
    }

    private int panelRight() {
        return width / 2 + panelWidth() / 2;
    }

    private int headerTop() {
        return topInset() + 4;
    }

    private int headerBottom() {
        if (selectedLadder == null && displayedStats != null && displayedStats.ladders().containsKey("GLOBAL")) {
            return headerTop() + 43;
        }
        return headerTop() + 18;
    }

    private int tableTop() {
        return headerBottom() + 4;
    }

    private int tableBottom() {
        return panelBottom() - FOOTER_HEIGHT;
    }

    private int rowBoundary(int index) {
        int top = tableTop() + 4 + COLUMN_HEADER_HEIGHT;
        return top + (tableBottom() - top) * index / Math.max(1, visibleLadders.size());
    }

    @Override
    protected void init() {
        clearWidgets();

        int backWidth = compact() ? 56 : 78;
        addRenderableWidget(LeaderboardButton.create("Back", panelRight() - backWidth - 4,
                panelBottom() - FOOTER_HEIGHT + 4, backWidth, 18, false, this::onClose));

        HqTiersClientState.cache().fetchVisible(uuid);
        displayedStats = HqTiersClientState.cache().get(uuid).orElse(null);
        if (selectedLadder == null && displayedStats != null) addLadderButtons(displayedStats);

        if (selectedLadder != null) addRangeButtons(backWidth);
        if (selectedLadder != null && historyFailed) {
            addRenderableWidget(LeaderboardButton.create("Retry history", width / 2 - 42, (tableTop() + tableBottom()) / 2 + 14, 84, 18, false, () -> {
                loadHistory();
                init();
            }));
        }
        if (selectedLadder != null && historyPoints == null && !historyRequested) {
            loadHistory();
        }
    }

    @Override
    public void tick() {
        super.tick();
        HqTiersStats stats = HqTiersClientState.cache().get(uuid).orElse(null);
        if (stats != displayedStats) {
            displayedStats = stats;
            if (selectedLadder == null) init();
        }
    }

    private void loadHistory() {
        historyRequested = true;
        historyLoading = true;
        int request = ++historyRequest;
        String ladder = selectedLadder;
        historyFailed = false;
        HqTiersClientState.leaderboardClient().history().load(uuid, ladder, historyRange)
                .whenComplete((points, error) -> minecraft.execute(() -> {
                    if (request != historyRequest || !ladder.equals(selectedLadder)) return;
                    historyLoading = false;
                    historyFailed = error != null;
                    historyPoints = historyFailed ? List.of() : points;
                    if (historyFailed) init();
                }));
    }

    private void addRangeButtons(int backWidth) {
        int left = panelLeft() + 4;
        int available = Math.min(240, panelWidth() - backWidth - 14);
        HistoryRange[] ranges = HistoryRange.values();
        for (int i = 0; i < ranges.length; i++) {
            HistoryRange range = ranges[i];
            int x = left + available * i / (ranges.length + 1);
            int right = range == HistoryRange.ALL_TIME ? left + available
                    : left + available * (i + 1) / (ranges.length + 1);
            Button button = LeaderboardButton.create(range.label, x, panelBottom() - FOOTER_HEIGHT + 4, right - x - 2, 18, range == historyRange, () -> {
                historyRange = range;
                loadHistory();
                init();
            });
            button.active = range != historyRange;
            addRenderableWidget(button);
        }
    }

    private void addLadderButtons(HqTiersStats stats) {
        List<HqTiersStats.LadderStats> ladders = sortedLadders(stats);
        int capacity = Math.max(1, (tableBottom() - tableTop() - 4 - COLUMN_HEADER_HEIGHT) / MIN_ROW_HEIGHT);
        int lastPage = Math.max(0, (ladders.size() - 1) / capacity);
        tablePage = Math.min(tablePage, lastPage);
        int pageSize = Math.ceilDiv(ladders.size(), lastPage + 1);
        int first = tablePage * pageSize;
        visibleLadders = ladders.subList(first, Math.min(ladders.size(), first + pageSize));
        if (lastPage > 0) {
            addPageButton("<", panelLeft() + 4, -1, tablePage > 0);
            addPageButton(">", panelRight() - 24, 1, tablePage < lastPage);
        }
        for (int i = 0; i < visibleLadders.size(); i++) {
            String id = visibleLadders.get(i).ladder();
            int top = rowBoundary(i);
            Button button = Button.builder(Component.literal(HqTiersFormatter.displayName(id)), b -> openLadder(id))
                    .bounds(panelLeft() + 2, top, panelWidth() - 4, rowBoundary(i + 1) - top).build();
            addWidget(button);
        }
    }

    private void addPageButton(String label, int x, int step, boolean enabled) {
        Button button = LeaderboardButton.create(label, x, headerTop() + 1, 20, 16, false, () -> {
            tablePage += step;
            init();
        });
        button.active = enabled;
        addRenderableWidget(button);
    }

    private void openLadder(String id) {
        historyRange = HistoryRange.MONTH;
        historyFailed = false;
        selectedLadder = id.toLowerCase(java.util.Locale.ROOT);
        historyRequest++;
        historyRequested = false;
        historyPoints = null;
        graphXPositions = null;
        graphYPositions = null;
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        int pl = panelLeft(), pr = panelRight();
        int ht = headerTop(), hb = headerBottom();
        int tt = tableTop(), tb = tableBottom();

        RankedScreenStyle.frame(ctx, font, width, height, pl, pr, "");
        ctx.fill(pl, ht, pr, hb, BG_HEADER);
        ctx.fill(pl, ht, pr, ht + 1, ACCENT_GOLD);
        ctx.fill(pl, hb - 1, pr, hb, ACCENT_DIM);
        ctx.fill(pl, tt, pr, panelBottom(), BG_PANEL);
        ctx.fill(pl, tt, pr, tt + 1, BORDER);
        ctx.fill(pl, tb - 1, pr, tb, BORDER);
        ctx.fill(pl, tt, pl + 1, panelBottom(), BORDER);
        ctx.fill(pr - 1, tt, pr, panelBottom(), BORDER);
        ctx.centeredText(font, Component.literal(fallbackName), width / 2, ht + 3, TEXT_WHITE);

        super.extractRenderState(ctx, mx, my, delta);

        var statsOpt = HqTiersClientState.cache().get(uuid);
        if (statsOpt.isEmpty()) {
            HqTiersCache.Status status = HqTiersClientState.cache().status(uuid);
            String msg = switch (status) {
                case MISSING -> "No ranked stats found for this player.";
                case FAILED -> "Could not load stats. Retrying…";
                default -> "Loading…";
            };
            int col = status == HqTiersCache.Status.LOADING ? TEXT_DIM : 0xFFFFD166;
            ctx.centeredText(font, Component.literal(msg), width / 2, tt + 40, col);
            return;
        }

        HqTiersStats playerStats = statsOpt.get();

        if (selectedLadder != null) {
            renderGraph(ctx, playerStats, pl, pr, tt, tb, mx, my);
        } else {
            if (displayedStats != null) {
                var global = displayedStats.ladders().get("GLOBAL");
                if (global != null) renderGlobalStandings(ctx, global, pl, pr, hb);
            }
            renderTable(ctx, playerStats, pl, pr, tt, tb, mx, my);
        }
    }

    private void renderGlobalStandings(GuiGraphicsExtractor ctx, HqTiersStats.LadderStats global,
                                       int left, int right, int bottom) {
        int width = right - left;
        int top = bottom - 28;
        int rankX = left + 10;
        int recordX = left + width * 43 / 100;
        int rateX = left + width * 76 / 100;
        ctx.fill(left, top, right, bottom - 1, BG_PANEL);
        ctx.fill(recordX - 6, top + 4, recordX - 5, bottom - 5, BORDER);
        ctx.fill(rateX - 6, top + 4, rateX - 5, bottom - 5, BORDER);

        String rank = global.hasPosition() ? "#" + global.position() : "Unranked";
        String record = global.wins() + "W / " + global.losses() + "L";
        ctx.text(font, "Global standings", rankX, top + 3, TEXT_HEADER, true);
        ctx.text(font, HqTiersFormatter.icon("GLOBAL"), rankX, top + 15, TEXT_WHITE, true);
        ctx.text(font, rank, rankX + 13, top + 15, TEXT_TITLE, true);
        ctx.text(font, "Record", recordX, top + 3, TEXT_HEADER, true);
        ctx.text(font, font.plainSubstrByWidth(record, rateX - recordX - 9), recordX, top + 15, TEXT_WHITE, true);
        ctx.text(font, "Win rate", rateX, top + 3, TEXT_HEADER, true);
        ctx.text(font, winRate(global), rateX, top + 15, wlColor(global.wins(), global.losses()), true);
    }

    private void renderTable(GuiGraphicsExtractor ctx, HqTiersStats stats,
                             int pl, int pr, int tt, int tb, int mx, int my) {
        int pw = pr - pl;
        boolean compact = compact();

        List<HqTiersStats.LadderStats> ladders = visibleLadders;
        var best = stats.bestLadder().orElse(null);

        int hy = tt + 4;
        ctx.fill(pl + 2, hy, pr - 2, hy + COLUMN_HEADER_HEIGHT - 2, BG_HEADER);
        ctx.fill(pl + 2, hy + COLUMN_HEADER_HEIGHT - 2, pr - 2, hy + COLUMN_HEADER_HEIGHT - 1, ACCENT_DIM);

        ctx.text(font, "Ladder", pl + col(pw, 0, compact), hy + 4, TEXT_HEADER, true);
        ctx.text(font, "Tier", pl + col(pw, 1, compact), hy + 4, TEXT_HEADER, true);
        ctx.text(font, compact ? "TR / %" : "Progress", pl + col(pw, 2, compact), hy + 4, TEXT_HEADER, true);
        ctx.text(font, "Rank", pl + col(pw, 3, compact), hy + 4, TEXT_HEADER, true);
        if (!compact) {
            ctx.text(font, "W / L", pl + col(pw, 4, false), hy + 4, TEXT_HEADER, true);
            ctx.text(font, "Win %", pl + col(pw, 5, false), hy + 4, TEXT_HEADER, true);
        }

        if (ladders.isEmpty()) {
            ctx.centeredText(font, Component.literal("No ranked data."), width / 2, tt + 50, TEXT_DIM);
            return;
        }

        for (int i = 0; i < ladders.size(); i++) {
            HqTiersStats.LadderStats l = ladders.get(i);
            int y = rowBoundary(i);
            int bottom = rowBoundary(i + 1);
            int textY = (y + bottom - font.lineHeight) / 2;
            boolean isBest = l.equals(best);
            boolean hovered = mx >= pl + 2 && mx <= pr - 2
                    && my >= y && my < bottom;

            if (hovered) ctx.fill(pl + 2, y, pr - 2, bottom, BG_ROW_HOVER);
            else if (i % 2 == 0) ctx.fill(pl + 2, y, pr - 2, bottom, BG_ROW_ALT);

            if (isBest) {
                ctx.fill(pl + 2, y, pr - 2, bottom, 0x25FFD86B);
                ctx.fill(pl + 2, y, pl + 4, bottom, ACCENT_GOLD);
            }

            String rawTierLabel = l.tierLabel();
            boolean unranked = rawTierLabel.isEmpty() || rawTierLabel.equalsIgnoreCase("Unranked");

            int nameX = pl + col(pw, 0, compact);
            int tierX = pl + col(pw, 1, compact);
            int trX = pl + col(pw, 2, compact);
            String name = HqTiersFormatter.displayName(l.ladder());
            ctx.text(font, HqTiersFormatter.icon(l.ladder()), nameX, textY, TEXT_WHITE, true);
            ctx.text(font, font.plainSubstrByWidth(name, tierX - nameX - 15), nameX + 12, textY, TEXT_WHITE, true);
            int color = unranked ? TEXT_DIM : 0xFF000000 | l.tierColorInt();
            String tier = font.plainSubstrByWidth(unranked ? "—" : rawTierLabel, trX - tierX - 6);
            if (isBest) {
                int right = tierX + font.width(tier) + 2;
                ctx.fill(tierX - 3, textY - 2, right + 1, textY + font.lineHeight + 1, 0x44FFD86B);
                ctx.fill(tierX - 2, textY - 1, right, textY + font.lineHeight, 0xFF57421C);
            }
            ctx.text(font, tier, tierX, textY, isBest ? TEXT_TITLE : color, true);
            String rating = unranked ? "—" : l.progressText(!compact);
            ctx.text(font, rating, trX, textY, color, true);

            String rankStr = l.hasPosition() ? "#" + l.position() : "—";
            int rankCol = l.hasPosition() ? 0xFFFFD700 : TEXT_DIM;
            ctx.text(font, rankStr, pl + col(pw, 3, compact), textY, rankCol, true);

            if (!compact) {
                ctx.text(font, l.wins() + " / " + l.losses(), pl + col(pw, 4, false), textY, wlColor(l.wins(), l.losses()), true);
                ctx.text(font, winRate(l), pl + col(pw, 5, false), textY, wlColor(l.wins(), l.losses()), true);
            }

        }

        String hint = compact ? "Select a ladder for history" : "Select a ladder to view its rating history";
        ctx.text(font, font.plainSubstrByWidth(hint, pw - (compact ? 56 : 78) - 18), pl + 8, tb + 9, TEXT_HEADER, true);
    }

    private static int col(int pw, int col, boolean compact) {
        if (compact) {
            return switch (col) {
                case 1 -> pw * 40 / 100;
                case 2 -> pw * 62 / 100;
                case 3 -> pw * 82 / 100;
                default -> 8;
            };
        }
        return switch (col) {
            case 1 -> pw * 26 / 100;
            case 2 -> pw * 40 / 100;
            case 3 -> pw * 56 / 100;
            case 4 -> pw * 70 / 100;
            case 5 -> pw * 86 / 100;
            default -> 10;
        };
    }

    private void renderGraph(GuiGraphicsExtractor ctx, HqTiersStats stats,
                             int pl, int pr, int tt, int tb, int mx, int my) {
        boolean compact = compact();
        HqTiersStats.LadderStats ladder = stats.ladders().get(
                selectedLadder.toUpperCase()
        );

        ctx.centeredText(font, Component.literal(trim(HqTiersFormatter.displayName(selectedLadder), compact ? 12 : 30) + "  ·  Rating History"), width / 2, tt + 5, TEXT_HEADER);

        if (ladder != null) {
            String rawTierLabel = ladder.tierLabel();
            boolean unranked = rawTierLabel.isEmpty() || rawTierLabel.equalsIgnoreCase("Unranked");
            String tierText = unranked ? "Unranked" : rawTierLabel;
            int tierCol = unranked ? TEXT_DIM : (0xFF000000 | ladder.tierColorInt());

            String summary = "Current: " + tierText
                    + "   " + (unranked ? "—" : ladder.progressText(true))
                    + "   " + ladder.wins() + "W / " + ladder.losses() + "L"
                    + (ladder.hasPosition() ? "   #" + ladder.position() : "");
            ctx.centeredText(font, Component.literal(summary), width / 2, tt + 17, tierCol);
        }

        int gl = pl + (compact ? 34 : 44);
        int gr = pr - (compact ? 10 : 14);
        int gt = tt + 32;
        int gbt = tb - 18;
        int gw = gr - gl;
        int gh = gbt - gt;

        ctx.fill(gl, gt, gr, gbt, BG_GRAPH);
        ctx.fill(gl - 1, gt, gl, gbt + 1, 0x884C7BA7);
        ctx.fill(gl, gbt, gr, gbt + 1, 0x884C7BA7);

        if (historyLoading) {
            ctx.centeredText(font, Component.literal("Loading history…"), width / 2, gt + gh / 2 - 4, TEXT_DIM);
            return;
        }
        if (historyPoints == null || historyPoints.isEmpty()) {
            ctx.centeredText(font, Component.literal(historyFailed ? "Could not load history." : historyRange == HistoryRange.ALL_TIME ? "No history data." : "No matches in this period."), width / 2, gt + gh / 2 - 4, TEXT_DIM);
            return;
        }

        if (plottedSource != historyPoints || plotWidth != gw) {
            plotWidth = gw;
            plottedSource = historyPoints;
            plot = MatchPlot.of(historyPoints, gw);
            minRating = historyPoints.stream().mapToInt(RatingHistoryClient.Point::elo).min().orElse(0);
            maxRating = historyPoints.stream().mapToInt(RatingHistoryClient.Point::elo).max().orElse(1);
        }
        int n = plot.indices().length;
        int minElo = minRating;
        int maxElo = maxRating;
        int pad = Math.max(15, (maxElo - minElo) / 8);
        minElo -= pad;
        maxElo += pad;
        int eloRange = Math.max(1, maxElo - minElo);

        int gridLines = compact ? 3 : 4;
        for (int i = 0; i <= gridLines; i++) {
            int gridElo = minElo + eloRange * i / gridLines;
            int gy = gbt - (gridElo - minElo) * gh / eloRange;
            ctx.fill(gl, gy, gr, gy + 1, i == 0 ? 0x448EA7D2 : AXIS_LINE);
            String label = Integer.toString(gridElo);
            int labelY = Math.min(gy - 9, gbt - 9);
            ctx.text(font, label, gl - font.width(label) - 3, labelY, TEXT_DIM, true);
        }

        if (graphXPositions == null || graphXPositions.length != n) {
            graphXPositions = new int[n];
            graphYPositions = new int[n];
        }
        for (int i = 0; i < n; i++) {
            graphXPositions[i] = gl + plot.x(i, gw);
            graphYPositions[i] = gbt - (historyPoints.get(plot.indices()[i]).elo() - minElo) * gh / eloRange;
        }

        int tierRaw = ladder != null ? ladder.tierColorInt() : 0x3B82F6;
        int fillColor = (0x22 << 24) | (tierRaw & 0xFFFFFF);

        for (int i = 1; i < n; i++) {
            int x1 = graphXPositions[i - 1], y1 = graphYPositions[i - 1];
            int x2 = graphXPositions[i], y2 = graphYPositions[i];
            fillTrapezoid(ctx, x1, y1, x2, y2, gbt, fillColor);
        }

        for (int i = 1; i < n; i++) {
            int x1 = graphXPositions[i - 1], y1 = graphYPositions[i - 1];
            int x2 = graphXPositions[i], y2 = graphYPositions[i];
            drawThickLine(ctx, x1, y1, x2, y2, 0xFF000000 | historyPoints.get(plot.indices()[i]).color());
        }

        if (n == 1 || gw / (n - 1) >= 7) {
            for (int i = 0; i < n; i++) {
                int x = graphXPositions[i], y = graphYPositions[i];
                int dotCol = 0xFF000000 | historyPoints.get(plot.indices()[i]).color();
                ctx.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000);
                ctx.fill(x - 1, y - 1, x + 2, y + 2, dotCol);
            }
        }

        String label = "Match 1";
        int x = n == 1 ? gl + (gw - font.width(label)) / 2 : gl;
        ctx.text(font, label, x, gbt + 4, TEXT_DIM, true);
        if (n > 1) {
            label = "Match " + plot.matchCount();
            x = gr - font.width(label);
            ctx.text(font, label, x, gbt + 4, TEXT_DIM, true);
        }
        if (mx >= gl && mx <= gr && my >= gt && my <= gbt) {
            int closest = 0;
            for (int i = 1; i < n; i++) {
                int dx = Math.abs(mx - graphXPositions[i]);
                int bestDx = Math.abs(mx - graphXPositions[closest]);
                if (dx < bestDx || (dx == bestDx && Math.abs(my - graphYPositions[i]) < Math.abs(my - graphYPositions[closest]))) {
                    closest = i;
                }
            }
            renderTooltip(ctx, closest, gl, gr, gt, gbt);
        }
    }

    private void renderTooltip(GuiGraphicsExtractor ctx, int idx, int gl, int gr, int gt, int gbt) {
        int match = plot.indices()[idx];
        var point = historyPoints.get(match);
        String date = HOVER_DATE.format(Instant.ofEpochMilli(point.timestamp()).atZone(ZoneId.systemDefault()));
        String ratingDelta = point.delta() == null ? "" : signed(point.delta());
        String progressDelta = point.trDelta() == null ? "" : signed(point.trDelta())
                + (me.micahcode.hqtiers.client.model.HqTiersRankSystem.usesPercent(point.tier()) ? " pp" : "");
        int ratingWidth = font.width("Rating") + font.width(Integer.toString(point.elo())) + font.width(ratingDelta) + 50;
        int progressWidth = font.width("Progress") + font.width(point.progressText()) + font.width(progressDelta) + 50;
        int tw = Math.min(width - 12, Math.max(184, Math.max(font.width(date) + 16, Math.max(ratingWidth, progressWidth))));
        int th = point.tier() == null ? 50 : 78;
        int tx = graphXPositions[idx] + 10;
        if (tx + tw > width - 6) tx = graphXPositions[idx] - tw - 10;
        tx = Math.clamp(tx, 6, Math.max(6, width - tw - 6));
        boolean fitsGraph = gbt - gt >= th + 4;
        int minY = fitsGraph ? gt + 2 : topInset() + 2;
        int maxY = fitsGraph ? gbt - th - 2 : Math.max(minY, panelBottom() - th - 6);
        int ty = Math.clamp(graphYPositions[idx] - th - 8, minY, maxY);
        ctx.fill(graphXPositions[idx], gt, graphXPositions[idx] + 1, gbt, ACCENT_DIM);
        ctx.fill(tx - 1, ty - 1, tx + tw + 1, ty + th + 1, 0xFF6B5520);
        ctx.fill(tx, ty, tx + tw, ty + th, 0xFF15120D);
        ctx.fill(tx, ty, tx + tw, ty + 2, ACCENT_GOLD);
        ctx.text(font, "Match " + (match + 1), tx + 8, ty + 7, TEXT_TITLE, true);
        ctx.text(font, date, tx + 8, ty + 20, 0xFFB9AA8B, true);
        ctx.fill(tx + 8, ty + 32, tx + tw - 8, ty + 33, BORDER);
        tooltipRow(ctx, tx, ty + 37, tw, "Rating", Integer.toString(point.elo()), ratingDelta, point.delta(), TEXT_WHITE);
        if (point.tier() != null) {
            tooltipRow(ctx, tx, ty + 50, tw, "Recorded tier", point.tier(), "", null, 0xFF000000 | point.color());
            tooltipRow(ctx, tx, ty + 63, tw, "Progress", point.progressText(), progressDelta, point.trDelta(), TEXT_WHITE);
        }
        ctx.fill(graphXPositions[idx] - 3, graphYPositions[idx] - 3,
                graphXPositions[idx] + 4, graphYPositions[idx] + 4, 0xFFFFFFFF);
        ctx.fill(graphXPositions[idx] - 2, graphYPositions[idx] - 2,
                graphXPositions[idx] + 3, graphYPositions[idx] + 3, ACCENT_GOLD);
    }

    private void tooltipRow(GuiGraphicsExtractor ctx, int x, int y, int w, String label, String value, String delta, Integer change, int color) {
        ctx.text(font, label, x + 8, y, 0xFFB9AA8B, true);
        int right = x + w - 8;
        int deltaWidth = font.width(delta);
        int valueWidth = Math.max(1, right - deltaWidth - 8 - (x + 83));
        ctx.text(font, font.plainSubstrByWidth(value, valueWidth), x + 83, y, color, true);
        int deltaColor = change == null || change == 0 ? TEXT_WHITE : change > 0 ? 0xFF4ADE80 : 0xFFF87171;
        ctx.text(font, delta, right - deltaWidth, y, deltaColor, true);
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private static void drawThickLine(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
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

    private static void fillTrapezoid(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int baseline, int color) {
        if (x2 <= x1) return;
        for (int x = x1; x < x2; x++) {
            int lineY = y1 + (y2 - y1) * (x - x1) / Math.max(1, x2 - x1);
            if (lineY < baseline)
                ctx.fill(x, lineY, x + 1, baseline, color);
        }
    }

    private static List<HqTiersStats.LadderStats> sortedLadders(HqTiersStats stats) {
        Map<String, HqTiersStats.LadderStats> ladders = new HashMap<>(stats.ladders());
        for (String known : HqTiersFormatter.KNOWN_LADDERS) {
            ladders.putIfAbsent(known, HqTiersStats.LadderStats.minimal(known, 0, 0, 0, 0, null, 0));
        }

        return ladders.values().stream()
                .filter(ladder -> !ladder.ladder().equals("GLOBAL"))
                .sorted(HqTiersStats.LadderStats.STANDINGS_ORDER)
                .toList();
    }

    private static String winRate(HqTiersStats.LadderStats ladder) {
        long matches = (long) ladder.wins() + ladder.losses();
        return matches == 0 ? "—" : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * ladder.wins() / matches);
    }

    private static int wlColor(int w, int l) {
        int t = w + l;
        if (t == 0) return TEXT_DIM;
        double r = (double) w / t;
        if (r >= 0.55) return 0xFF4ADE80;
        if (r >= 0.45) return 0xFFB0B8CC;
        return 0xFFF87171;
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    @Override
    public void onClose() {
        if (selectedLadder != null) {
            selectedLadder = null;
            historyRequest++;
            historyRequested = false;
            historyLoading = false;
            historyPoints = null;
            graphXPositions = null;
            graphYPositions = null;
            init();
        } else {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
