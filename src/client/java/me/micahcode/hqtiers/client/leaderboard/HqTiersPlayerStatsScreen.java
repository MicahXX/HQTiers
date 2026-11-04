package me.micahcode.hqtiers.client.leaderboard;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
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
    private HistoryRange historyRange = HistoryRange.ALL_TIME;
    private List<HqTiersLeaderboardClient.HistoryPoint> fullHistory = List.of();
    private List<HqTiersLeaderboardClient.HistoryPoint> historyPoints = null;
    private boolean historyLoading = false;
    private int historyRequest;
    private boolean historyRequested;
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
                minecraft.gui.setScreen(parent);
            }
        }).bounds(panelRight() - backWidth - 4, height - FOOTER_HEIGHT + 4, backWidth, 18).build());

        displayedStats = HqTiersClientState.cache().get(uuid).orElse(null);
        if (selectedLadder == null && displayedStats != null) addLadderButtons(displayedStats);

        if (selectedLadder != null) addRangeButtons(backWidth);
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
        HqTiersClientState.leaderboardClient().fetchHistory(uuid.toString(), ladder)
                .thenAccept(points -> minecraft.execute(() -> {
                    if (request != historyRequest || !ladder.equals(selectedLadder)) return;
                    fullHistory = points;
                    applyHistoryRange();
                    historyLoading = false;
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
                applyHistoryRange();
                init();
            }).bounds(x, height - FOOTER_HEIGHT + 4, right - x - 1, 18).build();
            button.active = range != historyRange;
            addRenderableWidget(button);
        }
    }

    private void applyHistoryRange() {
        historyPoints = historyRange.filter(fullHistory, System.currentTimeMillis());
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
            if (id.equals("GLOBAL")) continue;
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
        historyRange = HistoryRange.ALL_TIME;
        fullHistory = List.of();
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

        ctx.fill(0, 0, width, height, BG_BASE);
        ctx.fill(0, 0, width / 4, height, 0x08FFFFFF);

        ctx.fill(pl - 3, ht - 2, pr + 3, tb + 3, 0x552A1E0C);

        ctx.fill(pl, tt, pr, height, BG_PANEL);
        ctx.fill(pl, tt, pr, tt + 1, BORDER);
        ctx.fill(pl, tb - 1, pr, tb, BORDER);
        ctx.fill(pl, tt, pl + 1, height, BORDER);
        ctx.fill(pr - 1, tt, pr, height, BORDER);

        ctx.fill(pl, ht, pr, hb, 0xEE33260F);
        ctx.fill(pl, hb - 1, pr, hb, ACCENT_DIM);
        ctx.fill(pl, ht, pr, ht + 1, ACCENT_GOLD);

        ctx.centeredText(font, Component.literal("HQTIERS  STATS"), width / 2, ht + 6, TEXT_TITLE);
        ctx.centeredText(font, Component.literal(trim(fallbackName, compact() ? 20 : 40)), width / 2, ht + (compact() ? 17 : 20), TEXT_WHITE);

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
            renderTable(ctx, playerStats, pl, pr, tt, tb, mx, my);
        }
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

        ctx.text(font, "LADDER", pl + col(pw, 0, compact), hy + 4, TEXT_HEADER, true);
        ctx.text(font, "TIER", pl + col(pw, 1, compact), hy + 4, TEXT_HEADER, true);
        ctx.text(font, "TR", pl + col(pw, 2, compact), hy + 4, TEXT_HEADER, true);
        ctx.text(font, "RANK", pl + col(pw, 3, compact), hy + 4, TEXT_HEADER, true);
        if (!compact) {
            ctx.text(font, "W / L", pl + col(pw, 4, false), hy + 4, TEXT_HEADER, true);
            ctx.text(font, "WIN %", pl + col(pw, 5, false), hy + 4, TEXT_HEADER, true);
        }

        if (ladders.isEmpty()) {
            ctx.centeredText(font, Component.literal("No ranked data."), width / 2, tt + 50, TEXT_DIM);
            return;
        }

        for (int i = 0; i < ladders.size(); i++) {
            HqTiersStats.LadderStats l = ladders.get(i);
            boolean isGlobal = l.ladder().equals("GLOBAL");
            int y = rowBoundary(i);
            int bottom = rowBoundary(i + 1);
            int textY = (y + bottom - font.lineHeight) / 2;
            boolean isBest = l.equals(best);
            boolean hovered = !isGlobal && mx >= pl + 2 && mx <= pr - 2
                    && my >= y && my < bottom;

            if (hovered) ctx.fill(pl + 2, y, pr - 2, bottom, BG_ROW_HOVER);
            else if (i % 2 == 0) ctx.fill(pl + 2, y, pr - 2, bottom, BG_ROW_ALT);

            if (isGlobal) {
                ctx.fill(pl + 2, y, pr - 2, bottom, BG_HEADER);
                ctx.fill(pl + 2, bottom - 1, pr - 2, bottom, ACCENT_DIM);
            } else if (isBest) {
                ctx.fill(pl + 2, y, pr - 2, bottom, 0x25FFD86B);
                ctx.fill(pl + 2, y, pl + 4, bottom, ACCENT_GOLD);
            }

            String rawTierLabel = l.tierLabel();
            boolean unranked = rawTierLabel.isEmpty() || rawTierLabel.equalsIgnoreCase("Unranked");

            int nameX = pl + col(pw, 0, compact);
            int tierX = pl + col(pw, 1, compact);
            int trX = pl + col(pw, 2, compact);
            int nameEnd = isGlobal ? pl + col(pw, 3, compact) : tierX;
            String name = isGlobal ? "Global standings" : HqTiersFormatter.displayName(l.ladder());
            ctx.text(font, HqTiersFormatter.icon(l.ladder()), nameX, textY, TEXT_WHITE, true);
            ctx.text(font, font.plainSubstrByWidth(name, nameEnd - nameX - 15), nameX + 12, textY, isGlobal ? TEXT_TITLE : TEXT_WHITE, true);
            if (!isGlobal) {
                int color = unranked ? TEXT_DIM : 0xFF000000 | l.tierColorInt();
                String tier = font.plainSubstrByWidth(unranked ? "Unranked" : rawTierLabel, trX - tierX - 6);
                if (isBest) {
                    int right = tierX + font.width(tier) + 2;
                    ctx.fill(tierX - 3, textY - 2, right + 1, textY + font.lineHeight + 1, 0x44FFD86B);
                    ctx.fill(tierX - 2, textY - 1, right, textY + font.lineHeight, 0xFF57421C);
                }
                ctx.text(font, tier, tierX, textY, isBest ? TEXT_TITLE : color, true);
                String rating = unranked ? "—" : l.tr() + (compact ? "" : " TR");
                ctx.text(font, rating, trX, textY, color, true);
            }

            String rankStr = l.hasPosition() ? "#" + l.position() : "—";
            int rankCol = l.hasPosition() ? 0xFFFFD700 : TEXT_DIM;
            ctx.text(font, rankStr, pl + col(pw, 3, compact), textY, rankCol, true);

            if (!compact) {
                ctx.text(font, l.wins() + " / " + l.losses(), pl + col(pw, 4, false), textY, wlColor(l.wins(), l.losses()), true);
                ctx.text(font, l.wins() + l.losses() == 0 ? "—" : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * l.wins() / (l.wins() + l.losses())), pl + col(pw, 5, false), textY, wlColor(l.wins(), l.losses()), true);
            }

        }

        if (best != null) {
            String footer = "Best: " + HqTiersFormatter.displayName(best.ladder()) + "  " + best.tierLabel();
            ctx.text(font, font.plainSubstrByWidth(footer, pw - (compact ? 56 : 78) - 18), pl + 8, tb + 9, TEXT_TITLE, true);
        }
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

            String summary = tierText
                    + "   " + (unranked ? "—" : ladder.tr() + " TR")
                    + "   " + ladder.wins() + "W / " + ladder.losses() + "L";
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
            ctx.centeredText(font, Component.literal(historyRange == HistoryRange.ALL_TIME ? "No history data." : "No matches in this period."), width / 2, gt + gh / 2 - 4, TEXT_DIM);
            return;
        }

        int n = historyPoints.size();

        int minElo = historyPoints.stream().mapToInt(HqTiersLeaderboardClient.HistoryPoint::elo).min().orElse(0);
        int maxElo = historyPoints.stream().mapToInt(HqTiersLeaderboardClient.HistoryPoint::elo).max().orElse(1);
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
            graphXPositions[i] = gl + (n == 1 ? gw / 2 : i * gw / (n - 1));
            graphYPositions[i] = gbt - (historyPoints.get(i).elo() - minElo) * gh / eloRange;
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
            boolean up = historyPoints.get(i).elo() >= historyPoints.get(i - 1).elo();
            drawThickLine(ctx, x1, y1, x2, y2, up ? 0xCC4ADE80 : 0xCCF87171);
        }

        for (int i = 0; i < n; i++) {
            int x = graphXPositions[i], y = graphYPositions[i];
            boolean up = i == 0 || historyPoints.get(i).elo() >= historyPoints.get(i - 1).elo();
            int dotCol = up ? 0xFF4ADE80 : 0xFFF87171;
            ctx.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000);
            ctx.fill(x - 1, y - 1, x + 2, y + 2, dotCol);
        }

        ctx.text(font, dateLabel(historyPoints.getFirst().timestamp()), gl, gbt + 4, TEXT_DIM, true);
        String lastDate = dateLabel(historyPoints.get(n - 1).timestamp());
        ctx.text(font, lastDate, gr - font.width(lastDate), gbt + 4, TEXT_DIM, true);

        if (graphXPositions != null && my >= gt && my <= gbt) {
            int closest = -1, bestDist = 10;
            for (int i = 0; i < n; i++) {
                int d = Math.max(Math.abs(mx - graphXPositions[i]), Math.abs(my - graphYPositions[i]));
                if (d < bestDist) {
                    bestDist = d;
                    closest = i;
                }
            }
            if (closest >= 0) renderTooltip(ctx, closest, gl, gr, gt, gbt, tierRaw);
        }
    }

    private void renderTooltip(GuiGraphicsExtractor ctx, int idx, int gl, int gr, int gt, int gbt, int tierColorRaw) {
        int elo = historyPoints.get(idx).elo();
        int prev = idx > 0 ? historyPoints.get(idx - 1).elo() : elo;
        int delta = elo - prev;
        String deltaStr = idx == 0 ? "start" : (delta >= 0 ? "+" + delta : Integer.toString(delta));
        String date = dateLabel(historyPoints.get(idx).timestamp());
        int deltaColor = idx == 0 ? TEXT_DIM : (delta >= 0 ? 0xFF4ADE80 : 0xFFF87171);
        int eloTextColor = 0xFF000000 | (tierColorRaw & 0xFFFFFF);

        ctx.fill(graphXPositions[idx], gt, graphXPositions[idx] + 1, gbt, 0x553B82F6);

        int lines = date.isEmpty() ? 2 : 3;
        int tw = Math.max(68, font.width(date) + 12);
        int th = 8 + lines * 10;
        int tx = Math.min(graphXPositions[idx] + 6, gr - tw - 2);
        tx = Math.max(gl + 2, tx);
        int ty = Math.max(gt + 2, graphYPositions[idx] - th - 6);

        ctx.fill(tx - 2, ty - 2, tx + tw + 2, ty + th + 2, 0xF0050810);
        ctx.fill(tx - 2, ty - 2, tx + tw + 2, ty - 1, ACCENT_GOLD);
        ctx.fill(tx - 2, ty - 2, tx - 1, ty + th + 2, ACCENT_DIM);

        ctx.text(font, elo + " rating", tx + 2, ty + 2, eloTextColor, true);
        ctx.text(font, deltaStr, tx + 2, ty + 12, deltaColor, true);
        if (!date.isEmpty())
            ctx.text(font, date, tx + 2, ty + 22, TEXT_HEADER, true);

        ctx.fill(graphXPositions[idx] - 3, graphYPositions[idx] - 3,
                graphXPositions[idx] + 4, graphYPositions[idx] + 4, 0xFFFFFFFF);
        ctx.fill(graphXPositions[idx] - 2, graphYPositions[idx] - 2,
                graphXPositions[idx] + 3, graphYPositions[idx] + 3, ACCENT_GOLD);
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
                .sorted(Comparator
                        .comparingInt((HqTiersStats.LadderStats l) -> l.ladder().equals("GLOBAL") ? 0 : 1)
                        .thenComparing(Comparator.comparingInt(HqTiersStats.LadderStats::totalRating).reversed()))
                .toList();
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