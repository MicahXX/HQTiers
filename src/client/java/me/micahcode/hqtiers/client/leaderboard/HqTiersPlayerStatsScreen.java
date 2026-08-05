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
import net.minecraft.client.gui.GuiGraphics;
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

    // Responsive breakpoints for the panel width, mirrored by COMPACT_WIDTH
    // below the point where column labels start truncating/overlapping.
    private static final int MIN_PANEL_WIDTH = 260;
    private static final int MAX_PANEL_WIDTH = 540;
    private static final int SIDE_MARGIN = 16;
    private static final int COMPACT_WIDTH = 360;

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
        super(Component.literal("HqTiers Player Stats"));
        this.parent = parent;
        this.uuid = UUID.fromString(uuid);
        this.fallbackName = fallbackName;
        this.selectedLadder = autoOpenLadder == null ? null : autoOpenLadder.toLowerCase();
        if (autoOpenLadder != null) this.historyLoading = true;
    }

    public HqTiersPlayerStatsScreen(Screen parent, String uuid, String fallbackName) {
        this(parent, uuid, fallbackName, null);
    }

    // --- Responsive layout ---

    private int panelWidth() {
        int target = Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, (int) (width * 0.72)));
        return Math.min(target, Math.max(MIN_PANEL_WIDTH, width - SIDE_MARGIN * 2));
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
        return height - 32;
    }

    private int rowH() {
        return compact() ? 15 : 17;
    }

    /**
     * Row height for the ladder table specifically. Shrinks below rowH()'s
     * base value when the full row count (header + every ladder) wouldn't
     * fit in the space above the footer, so the last row never renders past
     * the panel edge and the footer never has to overlap it.
     */
    private int tableRowH(int ladderCount) {
        int base = rowH();
        if (ladderCount <= 0) return base;
        int footerReserve = 14;
        int available = (tableBottom() - footerReserve) - (tableTop() + 4);
        int neededRows = ladderCount + 1; // +1 for the header row
        int needed = neededRows * base;
        if (needed <= available) return base;
        return Math.max(11, available / neededRows);
    }

    @Override
    protected void init() {
        clearWidgets();

        String backLabel = selectedLadder != null ? "Back" : "Close";
        int backWidth = compact() ? 56 : 78;
        addRenderableWidget(Button.builder(Component.literal(backLabel), btn -> {
            if (selectedLadder != null) {
                selectedLadder = null;
                historyPoints = null;
                graphXPositions = null;
                graphYPositions = null;
                init();
            } else {
                if (minecraft != null) minecraft.setScreen(parent);
            }
        }).bounds(panelLeft(), height - 26, backWidth, 18).build());

        if (selectedLadder == null) {
            var cached = HqTiersClientState.cache().getIfFresh(uuid);
            if (cached.isPresent()) addLadderButtons(cached.get());
        }

        HqTiersClientState.cache().fetch(uuid).thenAccept(stats -> {
            if (!loaded) {
                loaded = true;
                failed = stats == null;
                if (minecraft != null && selectedLadder == null) minecraft.execute(this::init);
            }
        });

        if (selectedLadder != null && historyPoints == null && historyLoading) {
            HqTiersClientState.leaderboardClient()
                    .fetchHistory(uuid.toString(), selectedLadder)
                    .thenAccept(pts -> {
                        if (minecraft != null) minecraft.execute(() -> {
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
        int rh = tableRowH(ladders.size());
        int y = tableTop() + rh + 4;

        for (HqTiersStats.LadderStats l : ladders) {
            if (!l.ladder().equals("GLOBAL")) {
                final String id = l.ladder();
                final int fy = y;
                Button btn = Button.builder(Component.empty(), b -> openLadder(id))
                        .bounds(pl, fy, pr - pl, rh).build();
                btn.setAlpha(0f);
                addRenderableWidget(btn);
            }
            y += rh;
        }
    }

    private void openLadder(String id) {
        selectedLadder = id.toLowerCase();
        historyPoints = null;
        graphXPositions = null;
        graphYPositions = null;
        historyLoading = true;
        init();
        HqTiersClientState.leaderboardClient()
                .fetchHistory(uuid.toString(), id)
                .thenAccept(pts -> {
                    if (minecraft != null) minecraft.execute(() -> {
                        historyPoints = pts;
                        historyLoading = false;
                    });
                });
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        int pl = panelLeft(), pr = panelRight();
        int ht = headerTop(), hb = headerBottom();
        int tt = tableTop(), tb = tableBottom();

        // Background vignette
        ctx.fill(0, 0, width, height, BG_BASE);
        ctx.fill(0, 0, width / 4, height, 0x08FFFFFF);

        // Outer glow around the whole panel for a bit of depth
        ctx.fillGradient(pl - 3, ht - 2, pr + 3, tb + 3, 0x552A1E0C, 0x00000000);

        // Panel body
        ctx.fill(pl, tt, pr, tb, BG_PANEL);
        ctx.fill(pl, tt, pr, tt + 1, BORDER);
        ctx.fill(pl, tb - 1, pr, tb, BORDER);
        ctx.fill(pl, tt, pl + 1, tb, BORDER);
        ctx.fill(pr - 1, tt, pr, tb, BORDER);

        // Header block
        ctx.fillGradient(pl, ht, pr, hb, 0xEE33260F, BG_HEADER);
        ctx.fill(pl, hb - 1, pr, hb, ACCENT_DIM);
        ctx.fill(pl, ht, pr, ht + 1, ACCENT_GOLD);

        ctx.drawCenteredString(font, "HQTIERS  STATS", width / 2, ht + 6, TEXT_TITLE);
        ctx.drawCenteredString(font, trim(fallbackName, compact() ? 20 : 40), width / 2, ht + (compact() ? 17 : 20), TEXT_WHITE);

        super.render(ctx, mx, my, delta);

        var statsOpt = HqTiersClientState.cache().getIfFresh(uuid);
        if (statsOpt.isEmpty()) {
            String msg = loaded || failed ? "No ranked stats found for this player." : "Loading…";
            int col = loaded || failed ? 0xFFFFD166 : TEXT_DIM;
            ctx.drawCenteredString(font, msg, width / 2, tt + 40, col);
            return;
        }

        HqTiersStats playerStats = statsOpt.get();

        if (selectedLadder != null) {
            renderGraph(ctx, playerStats, pl, pr, tt, tb, mx, my);
        } else {
            renderTable(ctx, playerStats, pl, pr, tt, tb, mx, my);
        }
    }

    private void renderTable(GuiGraphics ctx, HqTiersStats stats,
                             int pl, int pr, int tt, int tb, int mx, int my) {
        int pw = pr - pl;
        boolean compact = compact();

        List<HqTiersStats.LadderStats> ladders = sortedLadders(stats);
        // Must match addLadderButtons()'s row height exactly - otherwise the
        // invisible click hitboxes drift out of alignment with the rendered
        // rows, and the table can render taller than the space reserved for
        // it, pushing the footer text on top of the last row.
        int rh = tableRowH(ladders.size());

        // Column headers
        int hy = tt + 4;
        ctx.fill(pl + 2, hy, pr - 2, hy + rh - 2, BG_HEADER);
        ctx.fill(pl + 2, hy + rh - 2, pr - 2, hy + rh - 1, ACCENT_DIM);

        ctx.drawString(font, "LADDER", pl + col(pw, 0, compact), hy + 4, TEXT_HEADER);
        ctx.drawString(font, "TIER", pl + col(pw, 1, compact), hy + 4, TEXT_HEADER);
        ctx.drawString(font, "TR", pl + col(pw, 2, compact), hy + 4, TEXT_HEADER);
        ctx.drawString(font, "RANK", pl + col(pw, 3, compact), hy + 4, TEXT_HEADER);
        if (!compact) {
            ctx.drawString(font, "W / L", pl + col(pw, 4, compact), hy + 4, TEXT_HEADER);
        }

        if (ladders.isEmpty()) {
            ctx.drawCenteredString(font, "No ranked data.", width / 2, tt + 50, TEXT_DIM);
            return;
        }

        int y = tt + rh + 4;
        for (int i = 0; i < ladders.size(); i++) {
            HqTiersStats.LadderStats l = ladders.get(i);
            boolean isGlobal = l.ladder().equals("GLOBAL");
            boolean hovered = !isGlobal && mx >= pl + 2 && mx <= pr - 2
                    && my >= y && my < y + rh;

            if (hovered) ctx.fill(pl + 2, y, pr - 2, y + rh, BG_ROW_HOVER);
            else if (i % 2 == 0) ctx.fill(pl + 2, y, pr - 2, y + rh, BG_ROW_ALT);

            if (isGlobal) ctx.fill(pl + 2, y, pl + 4, y + rh, ACCENT_GOLD);

            // A ladder is "unranked" if the player hasn't finished placements /
            // has no tier data - tierLabel() may return "Unranked" or "" here
            // depending on config, so normalize both cases to one dimmed state
            // instead of falling back to a random rating-based tier color.
            String rawTierLabel = l.tierLabel();
            boolean unranked = rawTierLabel.isEmpty() || rawTierLabel.equalsIgnoreCase("Unranked");

            ctx.drawString(font, HqTiersFormatter.icon(l.ladder()), pl + col(pw, 0, compact), y + 4, TEXT_WHITE);
            ctx.drawString(font, HqTiersFormatter.displayName(l.ladder()),
                    pl + col(pw, 0, compact) + 12, y + 4, TEXT_WHITE);

            String tierText = unranked ? "Unranked" : rawTierLabel;
            int tierCol = unranked ? TEXT_DIM : (0xFF000000 | l.tierColorInt());
            ctx.drawString(font, trim(tierText, compact ? 8 : 14),
                    pl + col(pw, 1, compact), y + 4, tierCol);

            int tr = l.totalRating();
            String trStr = unranked ? "—" : (tr + (compact ? "" : " TR"));
            ctx.drawString(font, trStr, pl + col(pw, 2, compact), y + 4, unranked ? TEXT_DIM : eloColor(tr));

            String rankStr = l.hasPosition() ? "#" + l.position() : "—";
            int rankCol = l.hasPosition() ? 0xFFFFD700 : TEXT_DIM;
            ctx.drawString(font, rankStr, pl + col(pw, 3, compact), y + 4, rankCol);

            if (!compact) {
                ctx.drawString(font, l.wins() + " / " + l.losses(),
                        pl + col(pw, 4, compact), y + 4, wlColor(l.wins(), l.losses()));
            }

            if (hovered) ctx.drawString(font, "→", pr - 14, y + 4, ACCENT_GOLD);

            y += rh;
        }

        // Footer is clamped with Math.min so it can never render past the
        // panel's reserved bottom margin. Previously this used Math.max,
        // which pushed the footer further DOWN (below the panel) whenever
        // the ladder list was long enough to push finalY past tb - 16,
        // causing it to overlap/clip past the last ladder row.
        int finalY = y;
        stats.bestLadder().ifPresent(best -> {
            int fy = Math.min(tb - 16, finalY + 6);
            ctx.fill(pl + 2, fy - 4, pr - 2, fy - 3, ACCENT_DIM);
            String footer = "Best: " + HqTiersFormatter.displayName(best.ladder()) + "  " + best.tierLabel();
            ctx.drawString(font, trim(footer, compact ? 30 : 60), pl + 10, fy, 0xFFFFD700);
        });
    }

    // column x-offsets as fraction of panel width; compact mode drops W/L
    // entirely and widens the remaining four columns to use the space
    private static int col(int pw, int col, boolean compact) {
        if (compact) {
            return switch (col) {
                case 0 -> 8;
                case 1 -> pw * 40 / 100;
                case 2 -> pw * 62 / 100;
                case 3 -> pw * 82 / 100;
                default -> 8;
            };
        }
        return switch (col) {
            case 0 -> 10;
            case 1 -> pw * 32 / 100;
            case 2 -> pw * 48 / 100;
            case 3 -> pw * 62 / 100;
            case 4 -> pw * 78 / 100;
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
                trim(HqTiersFormatter.displayName(selectedLadder), compact ? 12 : 30) + "  ·  TR History",
                width / 2, tt + 5, TEXT_HEADER);

        if (ladder != null) {
            String rawTierLabel = ladder.tierLabel();
            boolean unranked = rawTierLabel.isEmpty() || rawTierLabel.equalsIgnoreCase("Unranked");
            String tierText = unranked ? "Unranked" : rawTierLabel;
            int tierCol = unranked ? TEXT_DIM : (0xFF000000 | ladder.tierColorInt());

            String summary = tierText
                    + "   " + (unranked ? "—" : ladder.totalRating() + " TR")
                    + "   " + ladder.wins() + "W / " + ladder.losses() + "L";
            ctx.drawCenteredString(font, summary, width / 2, tt + 17, tierCol);
        }

        // Graph bounds - Y-axis gutter shrinks in compact mode since labels are shorter
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
            ctx.drawCenteredString(font, "No history data.", width / 2, gt + gh / 2 - 4, TEXT_DIM);
            return;
        }

        int n = historyPoints.size();

        int minElo = historyPoints.stream().mapToInt(HqTiersLeaderboardClient.HistoryPoint::elo).min().orElse(0);
        int maxElo = historyPoints.stream().mapToInt(HqTiersLeaderboardClient.HistoryPoint::elo).max().orElse(1);
        int pad = Math.max(15, (maxElo - minElo) / 8);
        minElo -= pad;
        maxElo += pad;
        int eloRange = Math.max(1, maxElo - minElo);

        // Fewer gridlines when the panel is narrow so labels don't collide
        int gridLines = compact ? 3 : 4;
        for (int i = 0; i <= gridLines; i++) {
            int gridElo = minElo + eloRange * i / gridLines;
            int gy = gbt - (gridElo - minElo) * gh / eloRange;
            ctx.fill(gl, gy, gr, gy + 1, i == 0 ? 0x448EA7D2 : AXIS_LINE);
            String label = Integer.toString(gridElo);
            // Clamp so the label always sits fully above its gridline - the
            // bottom-most line (i == 0, gy == gbt) would otherwise draw the
            // label text overlapping/spilling past the grid box's bottom
            // border since gy - 4 isn't enough clearance for a ~9px-tall
            // string sitting right on the line.
            int labelY = Math.min(gy - 9, gbt - 9);
            ctx.drawString(font, label,
                    gl - font.width(label) - 3, labelY, TEXT_DIM);
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

        ctx.drawString(font, dateLabel(historyPoints.get(0).timestamp()),
                gl, gbt + 4, TEXT_DIM);
        String lastDate = dateLabel(historyPoints.get(n - 1).timestamp());
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
        int elo = historyPoints.get(idx).elo();
        int prev = idx > 0 ? historyPoints.get(idx - 1).elo() : elo;
        int delta = elo - prev;
        String deltaStr = idx == 0 ? "start" : (delta >= 0 ? "+" + delta : Integer.toString(delta));
        String date = dateLabel(historyPoints.get(idx).timestamp());
        int deltaColor = idx == 0 ? TEXT_DIM : (delta >= 0 ? 0xFF4ADE80 : 0xFFF87171);

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

        ctx.drawString(font, elo + " TR", tx + 2, ty + 2, eloColor(elo));
        ctx.drawString(font, deltaStr, tx + 2, ty + 12, deltaColor);
        if (!date.isEmpty())
            ctx.drawString(font, date, tx + 2, ty + 22, TEXT_HEADER);

        ctx.fill(graphXPositions[idx] - 3, graphYPositions[idx] - 3,
                graphXPositions[idx] + 4, graphYPositions[idx] + 4, 0xFFFFFFFF);
        ctx.fill(graphXPositions[idx] - 2, graphYPositions[idx] - 2,
                graphXPositions[idx] + 3, graphYPositions[idx] + 3, ACCENT_GOLD);
    }

    // ── drawing primitives ─────────────────────────────────────────────────
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

    /**
     * Every fetched ladder gets shown here, regardless of whether the player
     * has actually played games on it - this screen is meant to be a full
     * stat sheet, matching what the /hqtiers command's text output shows.
     * (Previously this filtered out anything with 0 wins/losses, which made
     * the K menu look empty for players who haven't played much yet even
     * though the data was already fetched and available.)
     */
    private static List<HqTiersStats.LadderStats> sortedLadders(HqTiersStats stats) {
        return stats.ladders().values().stream()
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

    private static int eloColor(int elo) {
        return 0xFF000000 | HqTiersRankSystem.ratingColor(elo);
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static String dateLabel(long ts) {
        if (ts <= 0) return "";
        long ms = ts < 10_000_000_000L ? ts * 1000L : ts;
        return GRAPH_DATE.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}