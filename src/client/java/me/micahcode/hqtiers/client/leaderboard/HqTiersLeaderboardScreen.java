package me.micahcode.hqtiers.client.leaderboard;

import java.util.List;
import java.util.ArrayList;
import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import net.minecraft.client.gui.components.Tooltip;
import java.util.Locale;
import java.util.UUID;

import me.micahcode.hqtiers.client.nametag.HqTiersFormatter;
import me.micahcode.hqtiers.client.model.HqTiersLadder;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import me.micahcode.hqtiers.client.fetching.MojangProfileResolver;
import me.micahcode.hqtiers.client.fetching.HqTiersCache;

public final class HqTiersLeaderboardScreen extends Screen {
    private static final List<HqTiersLadder> LADDERS = List.of(
            HqTiersLadder.GLOBAL, HqTiersLadder.SWORD, HqTiersLadder.AXE, HqTiersLadder.MACE, HqTiersLadder.SPEAR_MACE,
            HqTiersLadder.UHC, HqTiersLadder.VANILLA, HqTiersLadder.CART, HqTiersLadder.DIAMOND_POT,
            HqTiersLadder.NETHERITE_POT, HqTiersLadder.SMP, HqTiersLadder.DIAMOND_SMP
    );

    private static final int TAB_HEIGHT = 15;
    private static final int TAB_GAP = 3;
    private static final int TAB_ROW_GAP = 3;
    private static final int MIN_TAB_WIDTH = 52;
    private static final int MAX_TAB_COLUMNS = 6;
    private final List<HqTiersLeaderboardClient.Entry> compareSelection = new ArrayList<>();
    private boolean selectingPlayers;
    private static final int CLOSE_WIDTH = 54;

    private static final int GOLD = 0xFFFFD700;
    private static final int SILVER = 0xFFE3E6EA;
    private static final int BRONZE = 0xFFCD7F32;

    private final HqTiersLeaderboardClient leaderboardClient;
    private String ladder = initialLadder();
    private int scrollOffset;
    private EditBox searchField;
    private String searchQuery = "";
    private String searchStatus = "";
    private HqTiersLeaderboardClient.Entry resolvedSearchEntry;
    private String hoveredUuid = "";
    private long hoverSince;
    private long searchDue;
    private String pendingResolveName = "";

    public HqTiersLeaderboardScreen(HqTiersLeaderboardClient leaderboardClient) {
        super(Component.literal("HQTiers Leaderboard"));
        this.leaderboardClient = leaderboardClient;
    }

    @Override
    protected void init() {
        clearWidgets();
        int panelLeft = panelLeft();
        int panelRight = panelRight();
        int startX = panelLeft + 8;
        int availableWidth = (panelRight - 8) - startX;
        int columns = tabColumns(availableWidth);
        int tabWidth = (availableWidth - (columns - 1) * TAB_GAP) / columns;

        for (int i = 0; i < LADDERS.size(); i++) {
            HqTiersLadder tabLadder = LADDERS.get(i);
            String tabLadderName = tabLadder.name();
            int row = i / columns;
            int col = i % columns;
            int x = startX + col * (tabWidth + TAB_GAP);
            int y = topInset() + 8 + row * (TAB_HEIGHT + TAB_ROW_GAP);
            Button tab = LeaderboardButton.create(tabButtonLabel(tabLadder), x, y, tabWidth, TAB_HEIGHT, tabLadderName.equals(ladder), () -> {
                ladder = tabLadderName;
                scrollOffset = 0;
                resolvedSearchEntry = null;
                pendingResolveName = "";
                init();
                resolveSearchIfNeeded(searchQuery);
            });
            tab.active = !tabLadderName.equals(ladder);
            addRenderableWidget(tab);
        }

        int searchY = searchRowY();
        int searchAreaWidth = (panelRight - 8) - (panelLeft + 8);
        int buttonWidth = 54;
        int gap = 8;
        int searchWidth = Math.max(90, searchAreaWidth - 2 * (buttonWidth + gap));

        searchField = new EditBox(font, panelLeft + 8, searchY, searchWidth, 18, Component.literal("Search player"));
        searchField.setMaxLength(32);
        searchField.setHint(Component.literal("Search player..."));
        searchField.setValue(searchQuery);
        searchField.setResponder(value -> {
            searchQuery = value.trim();
            searchStatus = "";
            resolvedSearchEntry = null;
            pendingResolveName = "";
            scrollOffset = 0;
            searchDue = System.nanoTime() + 250_000_000L;
        });
        addRenderableWidget(searchField);
        addRenderableWidget(LeaderboardButton.create("Search", panelLeft + 8 + searchWidth + gap,
                searchY, buttonWidth, 18, false, this::searchPlayer));

        addRenderableWidget(LeaderboardButton.create("Refresh", panelLeft + 8 + searchWidth + 2 * gap + buttonWidth, searchY, buttonWidth, 18, false, () -> {
            scrollOffset = 0;
            leaderboardClient.refresh(ladder);
        }));

        addRenderableWidget(LeaderboardButton.create(selectingPlayers ? "Compare " + compareSelection.size() + "/2" : "Compare",
                panelRight - CLOSE_WIDTH - 94, panelBottom() - 23, 90, 18, false, () -> {
            if (selectingPlayers) openComparison();
            else {
                selectingPlayers = true;
                compareSelection.clear();
                init();
            }
        })).setTooltip(Tooltip.create(Component.literal("Select two rows to compare. Click again to enter names manually. Escape cancels selection.")));
        addRenderableWidget(LeaderboardButton.create(selectingPlayers ? "Cancel" : "Close", panelRight - CLOSE_WIDTH,
                panelBottom() - 23, CLOSE_WIDTH, 18, false, this::onClose));

        leaderboardClient.load(ladder);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (scrollOffset == 0 && searchText().isBlank()) leaderboardClient.load(ladder);

        RankedScreenStyle.frame(context, font, width, height, panelLeft(), panelRight(),
                HqTiersFormatter.displayName(ladder) + " leaderboard");
        super.extractRenderState(context, mouseX, mouseY, delta);

        HqTiersLeaderboardClient.PageState state = leaderboardClient.state(ladder);
        List<HqTiersLeaderboardClient.Entry> entries = state.entries();
        List<HqTiersLeaderboardClient.Entry> visibleEntries = visibleEntries(entries);
        int panelLeft = panelLeft();
        int panelRight = panelRight();
        int top = tableTop();
        int bottom = panelBottom() - 28;
        int rowHeight = rowHeight();
        int tierColX = tierColX(panelLeft, panelRight);
        int nameColX = panelLeft + (HqTiersClientConfig.leaderboardHeads ? 60 : 46);
        boolean global = ladder.equals("GLOBAL");

        context.fill(panelLeft - 2, top - 20, panelRight + 2, bottom + 2, 0x662A1E0C);
        context.fill(panelLeft, top - 18, panelRight, bottom, 0xCC1A1408);
        context.fill(panelLeft, top - 18, panelRight, top - 2, 0xEE33260F);
        context.fill(panelLeft, top - 3, panelRight, top - 2, 0xFFD4AF37);

        context.text(font, "Rank", panelLeft + 8, top - 14, 0xFFCABD9F, true);
        context.text(font, "Player", nameColX, top - 14, 0xFFFFE7A3, true);
        context.text(font, global ? "Modes" : "Tier", tierColX, top - 14, 0xFFFFE7A3, true);
        String scoreHeader = global ? "Points" : "Progress";
        context.text(font, scoreHeader, panelRight - 12 - font.width(scoreHeader), top - 14, 0xFFFFE7A3, true);

        if (visibleEntries.isEmpty()) {
            String message;
            int color;
            if (state.error() != null) {
                message = state.error();
                color = 0xFFAAAAAA;
            } else if (state.loading()) {
                message = "Loading...";
                color = 0xFFAAAAAA;
            } else {
                message = "No leaderboard data.";
                color = 0xFFAAAAAA;
            }

            if (!entries.isEmpty() && !searchText().isBlank()) {
                message = resolvedSearchEntry == null ? "No loaded rows match. Resolving player..." : "Press Search to open found player.";
                color = 0xFFAAAAAA;
            }

            if (!searchStatus.isBlank()) message = searchStatus;

            context.centeredText(font, Component.literal(font.plainSubstrByWidth(message, panelRight - panelLeft - 16)), width / 2, top + 28, color);
            return;
        }

        int maxScroll = Math.max(0, visibleEntries.size() * rowHeight - (bottom - top));
        scrollOffset = Math.min(scrollOffset, maxScroll);

        int nameMaxWidth = tierColX - nameColX - 12;

        context.enableScissor(panelLeft, top, panelRight, bottom);
        int firstRow = scrollOffset / rowHeight;
        int endRow = Math.min(visibleEntries.size(), (scrollOffset + bottom - top) / rowHeight + 1);
        String hoveredPlayer = "";
        for (int i = firstRow; i < endRow; i++) {
            HqTiersLeaderboardClient.Entry entry = visibleEntries.get(i);
            int y = top + i * rowHeight - scrollOffset;

            boolean hovered = mouseX >= panelLeft && mouseX <= panelRight && mouseY >= Math.max(top, y) && mouseY < Math.min(bottom, y + rowHeight);
            if (hovered) hoveredPlayer = entry.uuid();
            int textY = y + (rowHeight - 8) / 2;
            int rank = entry.position();
            boolean isPodium = rank >= 1 && rank <= 3;

            if (isPodium) {
                int accent = podiumColor(rank);
                context.fill(panelLeft + 2, y - 1, panelRight - 2, y + rowHeight - 1, withAlpha(accent, hovered ? 0x38 : 0x18));
                context.text(font, Integer.toString(rank), panelLeft + 10, textY, accent, true);
            } else if (hovered) {
                context.fill(panelLeft + 2, y - 1, panelRight - 2, y + rowHeight - 1, 0x55D4AF37);
                context.text(font, rank > 0 ? Integer.toString(rank) : "-", panelLeft + 10, textY, rankColor(rank), true);
            } else {
                if (i % 2 == 0) {
                    context.fill(panelLeft + 2, y - 1, panelRight - 2, y + rowHeight - 1, 0x22000000);
                }
                context.text(font, rank > 0 ? Integer.toString(rank) : "-", panelLeft + 10, textY, rankColor(rank), true);
            }

            if (compareSelection.stream().anyMatch(selected -> selected.uuid().equals(entry.uuid()))) {
                context.fill(panelLeft + 4, y, panelRight - 8, y + rowHeight - 1, 0x334ADE80);
                context.fill(panelLeft + 2, y, panelLeft + 4, y + rowHeight - 1, 0xFF4ADE80);
            }
            if (HqTiersClientConfig.leaderboardHeads) {
                LeaderboardHeads.draw(context, entry.uuid(), nameColX - 14, y + (rowHeight - 10) / 2, 10);
            }
            int nameColor = isPodium ? podiumColor(rank) : nameColor(rank);
            context.text(font, font.plainSubstrByWidth(entry.name(), nameMaxWidth), nameColX, textY, nameColor, true);

            String rawTierLabel = entry.tierLabel();
            boolean unranked = rawTierLabel.isEmpty();
            String tierText = global ? Integer.toString(entry.gamemodes()) : trim(unranked ? "Unranked" : rawTierLabel, 12);
            int tierColor = global ? 0xFFFFD86B : unranked ? 0xFF5C5138 : (0xFF000000 | entry.tierColorInt());
            context.text(font, tierText, tierColX, textY, tierColor, true);

            String score = global ? (entry.points() < 0 ? "—" : Integer.toString(entry.points())) : entry.progressText();
            context.text(font, score, panelRight - 12 - font.width(score), textY, tierColor, true);
            context.fill(panelLeft + 6, y + rowHeight - 2, panelRight - 8, y + rowHeight - 1, 0x225C503A);
        }
        context.disableScissor();
        warmHoveredPlayer(hoveredPlayer);
        if (maxScroll > 0) {
            int track = bottom - top;
            int thumb = Math.max(12, track * track / (visibleEntries.size() * rowHeight));
            int thumbY = top + scrollOffset * (track - thumb) / maxScroll;
            context.fill(panelRight - 3, top, panelRight - 1, bottom, 0xFF30291B);
            context.fill(panelRight - 3, thumbY, panelRight - 1, thumbY + thumb, 0xFFBDA367);
        }

        String footer = (firstRow + 1) + "–" + Math.min(visibleEntries.size(), (scrollOffset + bottom - top + rowHeight - 1) / rowHeight)
                + " of " + state.total() + " · Scroll for more";
        int footerColor = 0xFF7C8BA1;
        if (state.error() != null) {
            footer = state.error();
            footerColor = 0xFFFF9999;
        } else if (state.loading()) {
            footer = "Loading more...";
            footerColor = 0xFFB99842;
        }
        if (selectingPlayers) footer = "Select two players · " + compareSelection.size() + "/2";
        else if (!searchStatus.isBlank()) footer = searchStatus;
        else if (resolvedSearchEntry != null) footer = "Found: " + resolvedSearchEntry.name();
        String status = font.plainSubstrByWidth(footer, panelRight - panelLeft - CLOSE_WIDTH - 102);
        context.text(font, status, panelLeft, panelBottom() - 18, footerColor, true);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) (verticalAmount * 18);
        scrollOffset = Math.max(0, scrollOffset);
        HqTiersLeaderboardClient.PageState state = leaderboardClient.state(ladder);
        int visibleRows = Math.max(1, (panelBottom() - tableTop() - 40) / rowHeight());
        if (searchText().isBlank() && scrollOffset > Math.max(0, state.entries().size() - visibleRows - 4) * rowHeight()) {
            leaderboardClient.loadMore(ladder);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean focused) {
        if (click.button() == 0) {
            HqTiersLeaderboardClient.Entry entry = rowAt(click.x(), click.y());
            if (entry != null && minecraft != null) {
                if (selectingPlayers) {
                    if (!compareSelection.removeIf(selected -> selected.uuid().equals(entry.uuid()))) compareSelection.add(entry);
                    if (compareSelection.size() == 2) openComparison();
                    else init();
                    return true;
                }
                minecraft.gui.setScreen(new HqTiersPlayerStatsScreen(this, entry.uuid(), entry.name(), ladder));
                return true;
            }
        }
        return super.mouseClicked(click, focused);
    }

    private void openComparison() {
        String first = compareSelection.isEmpty() ? searchText() : compareSelection.getFirst().name();
        String second = compareSelection.size() < 2 ? "" : compareSelection.get(1).name();
        selectingPlayers = false;
        compareSelection.clear();
        minecraft.gui.setScreen(new PlayerComparisonScreen(this, first, second));
    }

    @Override
    public void onClose() {
        if (selectingPlayers) {
            selectingPlayers = false;
            compareSelection.clear();
            init();
        } else super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String initialLadder() {
        return "GLOBAL";
    }

    private HqTiersLeaderboardClient.Entry rowAt(double mouseX, double mouseY) {
        int panelLeft = panelLeft();
        int panelRight = panelRight();
        int top = tableTop();
        int bottom = panelBottom() - 28;
        int rowHeight = rowHeight();
        if (mouseX < panelLeft || mouseX > panelRight || mouseY < top || mouseY > bottom) {
            return null;
        }

        int index = ((int) mouseY - top + scrollOffset) / rowHeight;
        List<HqTiersLeaderboardClient.Entry> entries = visibleEntries(leaderboardClient.state(ladder).entries());
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private int panelLeft() {
        return width / 2 - panelWidth() / 2;
    }

    private int panelRight() {
        return width / 2 + panelWidth() / 2;
    }

    private int rowHeight() {
        return HqTiersClientConfig.compactLeaderboardRows ? 16 : 21;
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

    private int tabColumns(int availableWidth) {
        int fit = availableWidth / (MIN_TAB_WIDTH + TAB_GAP);
        return Math.max(3, Math.min(MAX_TAB_COLUMNS, fit));
    }

    private int tierColX(int panelLeft, int panelRight) {
        int panelWidth = panelRight - panelLeft;
        int fromRight = Math.max(110, Math.min(150, panelWidth / 3));
        return panelRight - fromRight;
    }

    private int tabRows() {
        int panelLeft = panelLeft();
        int panelRight = panelRight();
        int availableWidth = (panelRight - 8) - (panelLeft + 8);
        int columns = tabColumns(availableWidth);
        return (LADDERS.size() + columns - 1) / columns;
    }

    private int legendY() {
        return topInset() + 8 + tabRows() * (TAB_HEIGHT + TAB_ROW_GAP);
    }

    private int searchRowY() {
        return legendY() + 4;
    }

    private int tableTop() {
        return searchRowY() + 40;
    }

    private static int podiumColor(int rank) {
        return switch (rank) {
            case 1 -> GOLD;
            case 2 -> SILVER;
            case 3 -> BRONZE;
            default -> 0xFFFFFFFF;
        };
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private List<HqTiersLeaderboardClient.Entry> visibleEntries(List<HqTiersLeaderboardClient.Entry> entries) {
        String query = searchText();
        if (query.isBlank()) {
            return entries;
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<HqTiersLeaderboardClient.Entry> filtered = entries.stream()
                .filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(lowerQuery))
                .toList();
        if (!filtered.isEmpty() || resolvedSearchEntry == null) {
            return filtered;
        }

        return List.of(resolvedSearchEntry);
    }

    private String searchText() {
        return searchField == null ? searchQuery : searchField.getValue().trim();
    }

    private void searchPlayer() {
        String query = searchText();
        if (query.isBlank() || minecraft == null) return;

        if (resolvedSearchEntry != null && resolvedSearchEntry.name().equalsIgnoreCase(query)) {
            minecraft.gui.setScreen(new HqTiersPlayerStatsScreen(this, resolvedSearchEntry.uuid(), resolvedSearchEntry.name(), ladder));
            return;
        }

        for (HqTiersLeaderboardClient.Entry entry : leaderboardClient.state(ladder).entries()) {
            if (entry.name().equalsIgnoreCase(query)) {
                minecraft.gui.setScreen(new HqTiersPlayerStatsScreen(this, entry.uuid(), entry.name(), ladder));
                return;
            }
        }

        searchStatus = "Searching...";
        try {
            UUID uuid = parseUuid(query);
            minecraft.gui.setScreen(new HqTiersPlayerStatsScreen(this, uuid.toString(), query, ladder));
            return;
        } catch (IllegalArgumentException ignored) {
        }

        HqTiersClientState.profileResolver().resolve(query).thenAccept(result -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                if (minecraft.gui.screen() != this || !query.equals(searchText())) return;
                if (result.status() == MojangProfileResolver.Status.FOUND) {
                    minecraft.gui.setScreen(new HqTiersPlayerStatsScreen(this, result.profile().uuid().toString(), result.profile().name(), ladder));
                } else if (result.status() == MojangProfileResolver.Status.NOT_FOUND) {
                    searchStatus = "Player not found.";
                } else {
                    searchStatus = "Search failed.";
                }
            });
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (searchDue != 0 && System.nanoTime() >= searchDue) {
            searchDue = 0;
            resolveSearchIfNeeded(searchQuery);
        }
    }

    private void warmHoveredPlayer(String uuid) {
        long now = System.nanoTime();
        if (!hoveredUuid.equals(uuid)) {
            hoveredUuid = uuid;
            hoverSince = now;
        } else if (!uuid.isEmpty() && hoverSince != 0 && now - hoverSince >= 180_000_000L) {
            HqTiersClientState.cache().get(UUID.fromString(uuid));
            hoverSince = 0;
        }
    }

    private void resolveSearchIfNeeded(String query) {
        if (query.length() < 3 || query.equalsIgnoreCase(pendingResolveName)) return;

        for (HqTiersLeaderboardClient.Entry entry : leaderboardClient.state(ladder).entries()) {
            if (entry.name().equalsIgnoreCase(query)) {
                resolvedSearchEntry = entry;
                searchStatus = "";
                return;
            }
        }

        pendingResolveName = query;
        String requestedLadder = ladder;
        searchStatus = "Resolving...";
        HqTiersClientState.profileResolver().resolve(query).thenAccept(result -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                if (!query.equals(searchText()) || !requestedLadder.equals(ladder)) return;
                if (result.status() == MojangProfileResolver.Status.FOUND) {
                    searchStatus = "Fetching stats...";
                    HqTiersClientState.cache().fetchVisible(result.profile().uuid()).thenAccept(stats -> {
                        if (minecraft == null) return;
                        minecraft.execute(() -> {
                            if (!query.equals(searchText()) || !requestedLadder.equals(ladder)) return;
                            String progress = "—";
                            int position = 0;
                            String tierName = null;
                            String tierColorHex = null;
                            if (stats != null) {
                                HqTiersStats.LadderStats ladderStats = stats.ladder(requestedLadder)
                                        .orElse(null);
                                if (ladderStats != null) {
                                    progress = ladderStats.progressText(false);
                                    position = ladderStats.position();
                                    tierName = ladderStats.tierName();
                                    tierColorHex = ladderStats.tierColorHex();
                                }
                            }
                            if (stats == null) {
                                resolvedSearchEntry = null;
                                searchStatus = HqTiersClientState.cache().status(result.profile().uuid()) == HqTiersCache.Status.MISSING
                                        ? "Player has not played PvPHQ ranked." : "Could not load stats. Please try again.";
                                return;
                            }
                            resolvedSearchEntry = new HqTiersLeaderboardClient.Entry(
                                    position, result.profile().uuid().toString(), result.profile().name(), progress, tierName, tierColorHex,
                                    -1, (int) stats.ladders().values().stream()
                                            .filter(value -> !value.ladder().equals("GLOBAL") && value.hasPlayedRanked()).count());
                            searchStatus = "";
                        });
                    });
                } else if (result.status() == MojangProfileResolver.Status.NOT_FOUND) {
                    searchStatus = "Player not found.";
                } else {
                    searchStatus = "Search failed.";
                }
            });
        });
    }

    private static UUID parseUuid(String value) {
        if (value.length() != 32) {
            return UUID.fromString(value);
        }

        return UUID.fromString(value.replaceFirst(
                "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                "$1-$2-$3-$4-$5"
        ));
    }

    private static String tabButtonLabel(HqTiersLadder ladder) {
        return switch (ladder) {
            case SPEAR_MACE -> "S.Mace";
            case DIAMOND_SMP -> "D.SMP";
            default -> ladder.displayName();
        };
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "...";
    }

    private static int rankColor(int rank) {
        if (rank <= 10 && rank > 0) return 0xFFFFFF88;
        return 0xFF9CA3AF;
    }

    // this could be deleted
    private static int nameColor(int rank) {
        return 0xFFFFFFFF;
    }
}
