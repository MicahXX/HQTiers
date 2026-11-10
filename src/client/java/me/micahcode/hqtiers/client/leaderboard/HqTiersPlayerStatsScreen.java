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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HqTiersPlayerStatsScreen extends Screen {
    private static final DateTimeFormatter HOVER_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");
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
    private static final int MIN_PANEL_WIDTH = 260;
    private static final int MAX_PANEL_WIDTH = 540;
    private static final int SIDE_MARGIN = 16;
    private static final int COMPACT_WIDTH = 360;

    private static final int FOOTER_HEIGHT = 26;
    private static final int COLUMN_HEADER_HEIGHT = 16;
    private static final int MIN_ROW_HEIGHT = 11;

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
    private List<RatingHistoryClient.Point> plotted = List.of();
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

    private int panelWidth() {
        int target = Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, (int) (width * 0.72)));
        return Math.min(target, Math.max(1, width - SIDE_MARGIN * 2));
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
        return 8;
    }

    private int headerBottom() {
        if (selectedLadder == null && displayedStats != null && displayedStats.ladders().containsKey("GLOBAL")) {
            return headerTop() + 60;
        }
        return compact() ? 46 : 56;
    }

    private int tableTop() {
        return headerBottom() + 4;
    }

    private int tableBottom() {
        return height - FOOTER_HEIGHT;
    }

    private int rowBoundary(int index) {
        int top = tableTop() + 4 + COLUMN_HEADER_HEIGHT;
        return top + (tableBottom() - top) * index / Math.max(1, visibleLadders.size());
    }

    @Override
    protected void init() {
        clearWidgets();

        String backLabel = selectedLadder != null ? "Back" : "Close";
        int backWidth = compact() ? 56 : 78;
        addRenderableWidget(Button.builder(Component.literal(backLabel), btn -> {
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
        }).bounds(panelRight() - backWidth - 4, height - FOOTER_HEIGHT + 4, backWidth, 18).build());

        HqTiersClientState.cache().fetchVisible(uuid);
        displayedStats = HqTiersClientState.cache().get(uuid).orElse(null);
        if (selectedLadder == null && displayedStats != null) addLadderButtons(displayedStats);

        if (selectedLadder != null) addRangeButtons(backWidth);
        if (selectedLadder != null && historyFailed) {
            addRenderableWidget(Button.builder(Component.literal("Retry history"), button -> {
                loadHistory();
                init();
            }).bounds(width / 2 - 42, (tableTop() + tableBottom()) / 2 + 14, 84, 18).build());
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
            Button button = Button.builder(Component.literal(range.label), b -> {
                historyRange = range;
                loadHistory();
                init();
            }).bounds(x, height - FOOTER_HEIGHT + 4, right - x - 1, 18).build();
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
        Button button = Button.builder(Component.literal(label), b -> {
            tablePage += step;
            init();
        }).bounds(x, headerTop() + 6, 20, 16).build();
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
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        int pl = panelLeft(), pr = panelRight();
        int ht = headerTop(), hb = headerBottom();
        int tt = tableTop(), tb = tableBottom();

        ctx.fill(0, 0, width, height, BG_BASE);
        ctx.fill(0, 0, width / 4, height, 0x08FFFFFF);

        ctx.fillGradient(pl - 3, ht - 2, pr + 3, tb + 3, 0x552A1E0C, 0x00000000);

        ctx.fill(pl, tt, pr, height, BG_PANEL);
        ctx.fill(pl, tt, pr, tt + 1, BORDER);
        ctx.fill(pl, tb - 1, pr, tb, BORDER);
        ctx.fill(pl, tt, pl + 1, height, BORDER);
        ctx.fill(pr - 1, tt, pr, height, BORDER);

        ctx.fillGradient(pl, ht, pr, hb, 0xEE33260F, BG_HEADER);
        ctx.fill(pl, hb - 1, pr, hb, ACCENT_DIM);
        ctx.fill(pl, ht, pr, ht + 1, ACCENT_GOLD);

        ctx.drawCenteredString(font, "HQTIERS  STATS", width / 2, ht + 6, TEXT_TITLE);
        ctx.drawCenteredString(font, trim(fallbackName, compact() ? 20 : 40), width / 2, ht + (compact() ? 17 : 20), TEXT_WHITE);

        super.render(ctx, mx, my, delta);

        var statsOpt = HqTiersClientState.cache().get(uuid);
        if (statsOpt.isEmpty()) {
            HqTiersCache.Status status = HqTiersClientState.cache().status(uuid);
            String msg = switch (status) {
                case MISSING -> "No ranked stats found for this player.";
                case FAILED -> "Could not load stats. Retrying…";
                default -> "Loading…";
            };
            int col = status == HqTiersCache.Status.LOADING ? TEXT_DIM : 0xFFFFD166;
            ctx.drawCenteredString(font, msg, width / 2, tt + 40, col);
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

    private void renderGlobalStandings(GuiGraphics ctx, HqTiersStats.LadderStats global,
                                       int left, int right, int bottom) {
        int width = right - left;
        int top = bottom - 28;
        int rankX = left + 10;
        int recordX = left + width * 43 / 100;
        int rateX = left + width * 76 / 100;
        ctx.fill(left, top, right, bottom - 1, BG_PANEL);
        ctx.fill(left, top, left + 2, bottom - 1, ACCENT_GOLD);
        ctx.fill(recordX - 6, top + 4, recordX - 5, bottom - 5, BORDER);
        ctx.fill(rateX - 6, top + 4, rateX - 5, bottom - 5, BORDER);

        String rank = global.hasPosition() ? "#" + global.position() : "Unranked";
        String record = global.wins() + "W / " + global.losses() + "L";
        ctx.drawString(font, "Global standings", rankX, top + 3, TEXT_HEADER);
        ctx.drawString(font, HqTiersFormatter.icon("GLOBAL"), rankX, top + 15, TEXT_WHITE);
        ctx.drawString(font, rank, rankX + 13, top + 15, TEXT_TITLE);
        ctx.drawString(font, "Record", recordX, top + 3, TEXT_HEADER);
        ctx.drawString(font, font.plainSubstrByWidth(record, rateX - recordX - 9), recordX, top + 15, TEXT_WHITE);
        ctx.drawString(font, "Win rate", rateX, top + 3, TEXT_HEADER);
        ctx.drawString(font, winRate(global), rateX, top + 15, wlColor(global.wins(), global.losses()));
    }

    private void renderTable(GuiGraphics ctx, HqTiersStats stats,
                             int pl, int pr, int tt, int tb, int mx, int my) {
        int pw = pr - pl;
        boolean compact = compact();

        List<HqTiersStats.LadderStats> ladders = visibleLadders;
        var best = stats.bestLadder().orElse(null);

        int hy = tt + 4;
        ctx.fill(pl + 2, hy, pr - 2, hy + COLUMN_HEADER_HEIGHT - 2, BG_HEADER);
        ctx.fill(pl + 2, hy + COLUMN_HEADER_HEIGHT - 2, pr - 2, hy + COLUMN_HEADER_HEIGHT - 1, ACCENT_DIM);

        ctx.drawString(font, "LADDER", pl + col(pw, 0, compact), hy + 4, TEXT_HEADER);
        ctx.drawString(font, "TIER", pl + col(pw, 1, compact), hy + 4, TEXT_HEADER);
        ctx.drawString(font, "TR / %", pl + col(pw, 2, compact), hy + 4, TEXT_HEADER);
        ctx.drawString(font, "RANK", pl + col(pw, 3, compact), hy + 4, TEXT_HEADER);
        if (!compact) {
            ctx.drawString(font, "W / L", pl + col(pw, 4, false), hy + 4, TEXT_HEADER);
            ctx.drawString(font, "WIN %", pl + col(pw, 5, false), hy + 4, TEXT_HEADER);
        }

        if (ladders.isEmpty()) {
            ctx.drawCenteredString(font, "No ranked data.", width / 2, tt + 50, TEXT_DIM);
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
            ctx.drawString(font, HqTiersFormatter.icon(l.ladder()), nameX, textY, TEXT_WHITE);
            ctx.drawString(font, font.plainSubstrByWidth(name, tierX - nameX - 15), nameX + 12, textY, TEXT_WHITE);
            int color = unranked ? TEXT_DIM : 0xFF000000 | l.tierColorInt();
            String tier = font.plainSubstrByWidth(unranked ? "Unranked" : rawTierLabel, trX - tierX - 6);
            if (isBest) {
                int right = tierX + font.width(tier) + 2;
                ctx.fill(tierX - 3, textY - 2, right + 1, textY + font.lineHeight + 1, 0x44FFD86B);
                ctx.fill(tierX - 2, textY - 1, right, textY + font.lineHeight, 0xFF57421C);
            }
            ctx.drawString(font, tier, tierX, textY, isBest ? TEXT_TITLE : color);
            String rating = unranked ? "—" : l.progressText(!compact);
            ctx.drawString(font, rating, trX, textY, color);

            String rankStr = l.hasPosition() ? "#" + l.position() : "—";
            int rankCol = l.hasPosition() ? 0xFFFFD700 : TEXT_DIM;
            ctx.drawString(font, rankStr, pl + col(pw, 3, compact), textY, rankCol);

            if (!compact) {
                ctx.drawString(font, l.wins() + " / " + l.losses(),
                        pl + col(pw, 4, false), textY, wlColor(l.wins(), l.losses()));
                ctx.drawString(font, winRate(l),
                        pl + col(pw, 5, false), textY, wlColor(l.wins(), l.losses()));
            }

        }

        String hint = compact ? "Select a ladder for history" : "Select a ladder to view its rating history";
        ctx.drawString(font, font.plainSubstrByWidth(hint, pw - (compact ? 56 : 78) - 18), pl + 8, tb + 9, TEXT_HEADER);
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
            case 3 -> pw * 52 / 100;
            case 4 -> pw * 66 / 100;
            case 5 -> pw * 84 / 100;
            default -> 10;
        };
    }

    private void renderGraph(GuiGraphics ctx, HqTiersStats stats,
                             int pl, int pr, int tt, int tb, int mx, int my) {
        boolean compact = compact();
        HqTiersStats.LadderStats ladder = stats.ladders().get(
                selectedLadder.toUpperCase()
        );

        ctx.drawCenteredString(font,
                trim(HqTiersFormatter.displayName(selectedLadder), compact ? 12 : 30) + "  ·  Rating History",
                width / 2, tt + 5, TEXT_HEADER);

        if (ladder != null) {
            String rawTierLabel = ladder.tierLabel();
            boolean unranked = rawTierLabel.isEmpty() || rawTierLabel.equalsIgnoreCase("Unranked");
            String tierText = unranked ? "Unranked" : rawTierLabel;
            int tierCol = unranked ? TEXT_DIM : (0xFF000000 | ladder.tierColorInt());

            String summary = tierText
                    + "   " + (unranked ? "—" : ladder.progressText(true))
                    + "   " + ladder.wins() + "W / " + ladder.losses() + "L";
            ctx.drawCenteredString(font, summary, width / 2, tt + 17, tierCol);
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
            ctx.drawCenteredString(font, "Loading history…", width / 2, gt + gh / 2 - 4, TEXT_DIM);
            return;
        }
        if (historyPoints == null || historyPoints.isEmpty()) {
            ctx.drawCenteredString(font, historyFailed ? "Could not load history." : historyRange == HistoryRange.ALL_TIME ? "No history data." : "No matches in this period.", width / 2, gt + gh / 2 - 4, TEXT_DIM);
            return;
        }

        if (plottedSource != historyPoints || plotWidth != gw) {
            plotWidth = gw;
            plottedSource = historyPoints;
            plotted = RatingHistoryClient.plotPoints(historyPoints, Math.max(1, gw));
            minRating = plotted.stream().mapToInt(RatingHistoryClient.Point::elo).min().orElse(0);
            maxRating = plotted.stream().mapToInt(RatingHistoryClient.Point::elo).max().orElse(1);
        }
        int n = plotted.size();
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
            ctx.drawString(font, label,
                    gl - font.width(label) - 3, labelY, TEXT_DIM);
        }

        if (graphXPositions == null || graphXPositions.length != n) {
            graphXPositions = new int[n];
            graphYPositions = new int[n];
        }
        long start = plotted.getFirst().timestamp();
        long span = plotted.getLast().timestamp() - start;
        for (int i = 0; i < n; i++) {
            graphXPositions[i] = gl + (span <= 0 ? gw / 2 : (int) ((plotted.get(i).timestamp() - start) * (double) gw / span));
            graphYPositions[i] = gbt - (plotted.get(i).elo() - minElo) * gh / eloRange;
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
            drawThickLine(ctx, x1, y1, x2, y2, 0xFF000000 | plotted.get(i).color());
        }

        for (int i = 0; i < n; i++) {
            int x = graphXPositions[i], y = graphYPositions[i];
            int dotCol = 0xFF000000 | plotted.get(i).color();
            ctx.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000);
            ctx.fill(x - 1, y - 1, x + 2, y + 2, dotCol);
        }

        ctx.drawString(font, dateLabel(plotted.getFirst().timestamp()),
                gl, gbt + 4, TEXT_DIM);
        String lastDate = dateLabel(plotted.get(n - 1).timestamp());
        ctx.drawString(font, lastDate,
                gr - font.width(lastDate), gbt + 4, TEXT_DIM);

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

    private void renderTooltip(GuiGraphics ctx, int idx, int gl, int gr, int gt, int gbt) {
        var point = plotted.get(idx);
        String date = HOVER_DATE.format(Instant.ofEpochMilli(HistoryRange.epochMillis(point.timestamp())).atZone(ZoneId.systemDefault()));
        String rating = "Rating " + point.elo();
        if (point.delta() != null) rating += " (" + signed(point.delta()) + ")";
        String tier = point.tier() == null ? "" : point.tier() + "  " + point.progressText();
        if (point.trDelta() != null && !tier.isEmpty()) tier += " (" + signed(point.trDelta()) + ")";
        int lines = tier.isEmpty() ? 2 : 3;
        int tw = Math.max(font.width(date), Math.max(font.width(rating), font.width(tier))) + 10;
        int th = 6 + lines * 10;
        ctx.fill(graphXPositions[idx], gt, graphXPositions[idx] + 1, gbt, ACCENT_DIM);
        int tx = Math.min(graphXPositions[idx] + 6, gr - tw - 2);
        tx = Math.max(gl + 2, tx);
        int ty = Math.max(gt + 2, graphYPositions[idx] - th - 6);

        ctx.fill(tx - 2, ty - 2, tx + tw + 2, ty + th + 2, 0xF0050810);
        ctx.fill(tx - 2, ty - 2, tx + tw + 2, ty - 1, ACCENT_GOLD);
        ctx.fill(tx - 2, ty - 2, tx - 1, ty + th + 2, ACCENT_DIM);

        ctx.drawString(font, date, tx + 2, ty + 2, TEXT_HEADER);
        ctx.drawString(font, rating, tx + 2, ty + 12, TEXT_WHITE);
        if (!tier.isEmpty()) ctx.drawString(font, tier, tx + 2, ty + 22, 0xFF000000 | point.color());

        ctx.fill(graphXPositions[idx] - 3, graphYPositions[idx] - 3,
                graphXPositions[idx] + 4, graphYPositions[idx] + 4, 0xFFFFFFFF);
        ctx.fill(graphXPositions[idx] - 2, graphYPositions[idx] - 2,
                graphXPositions[idx] + 3, graphYPositions[idx] + 3, ACCENT_GOLD);
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private static void drawThickLine(GuiGraphics ctx, int x1, int y1, int x2, int y2, int color) {
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

    private static void fillTrapezoid(GuiGraphics ctx, int x1, int y1, int x2, int y2, int baseline, int color) {
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

    private static String dateLabel(long ts) {
        if (ts <= 0) return "";
        long ms = HistoryRange.epochMillis(ts);
        return GRAPH_DATE.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}