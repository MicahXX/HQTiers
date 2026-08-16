package me.micahcode.hqtiers.client.leaderboard;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.MojangProfileResolver;
import me.micahcode.hqtiers.client.model.HqTiersLadder;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HqTiersLeaderboardScreen extends Screen {
    private static final List<HqTiersLadder> LADDERS = List.of(
            HqTiersLadder.GLOBAL, HqTiersLadder.SWORD, HqTiersLadder.AXE, HqTiersLadder.MACE, HqTiersLadder.SPEAR_MACE,
            HqTiersLadder.UHC, HqTiersLadder.VANILLA, HqTiersLadder.CART, HqTiersLadder.DIAMOND_POT,
            HqTiersLadder.NETHERITE_POT, HqTiersLadder.SMP, HqTiersLadder.DIAMOND_SMP
    );

    private static final int TAB_HEIGHT = 15;
    private static final int TAB_GAP = 3;
    private static final int TAB_ROW_GAP = 3;
    private static final int MIN_TAB_WIDTH = 58;
    private static final int MAX_TAB_COLUMNS = 6;
    private static final int MIN_PANEL_WIDTH = 320;
    private static final int MAX_PANEL_WIDTH = 480;
    private static final int ROW_HEIGHT = 16;

    private static final long LEADERBOARD_REFRESH_INTERVAL_MS = 10_000;

    private static final int GOLD = 0xFFFFD700;
    private static final int SILVER = 0xFFE3E6EA;
    private static final int BRONZE = 0xFFCD7F32;

    private static final int TIER_DIM = 0xFF5C5138;

    private final HqTiersLeaderboardClient leaderboardClient;
    private String ladder = initialLadder();
    private int scrollOffset;
    private EditBox searchField;
    private String searchQuery = "";
    private String searchStatus = "";
    private HqTiersLeaderboardClient.Entry resolvedSearchEntry;
    private String pendingResolveName = "";
    private final Map<String, Long> lastLeaderboardRefreshAt = new HashMap<>();
    private boolean firstInit = true;

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
            int y = 20 + row * (TAB_HEIGHT + TAB_ROW_GAP);
            String prefix = tabLadderName.equals(ladder) ? "> " : "";
            Button tab = Button.builder(Component.literal(prefix + tabButtonLabel(tabLadder)), button -> {
                ladder = tabLadderName;
                scrollOffset = 0;
                leaderboardClient.load(ladder);
                lastLeaderboardRefreshAt.put(ladder, System.currentTimeMillis());
                init();
            }).bounds(x, y, tabWidth, TAB_HEIGHT).build();
            tab.active = !tabLadderName.equals(ladder);
            addRenderableWidget(tab);
        }

        int searchY = searchRowY();
        int searchAreaWidth = (panelRight - 8) - (panelLeft + 8);
        int buttonWidth = 64;
        int gap = 8;
        int searchWidth = Math.max(90, searchAreaWidth - buttonWidth - gap);

        searchField = new EditBox(font, panelLeft + 8, searchY, searchWidth, 18, Component.literal("Search player"));
        searchField.setMaxLength(32);
        searchField.setHint(Component.literal("Search player..."));
        searchField.setValue(searchQuery);
        searchField.setResponder(value -> {
            searchQuery = value.trim();
            searchStatus = "";
            resolvedSearchEntry = null;
            resolveSearchIfNeeded(searchQuery);
        });
        addRenderableWidget(searchField);
        addRenderableWidget(Button.builder(Component.literal("Search"), button -> searchPlayer())
                .bounds(panelLeft + 8 + searchWidth + gap, searchY, buttonWidth, 18)
                .build());

        if (firstInit) {
            firstInit = false;
            leaderboardClient.refresh(ladder);
            lastLeaderboardRefreshAt.put(ladder, System.currentTimeMillis());
        } else {
            leaderboardClient.load(ladder);
            lastLeaderboardRefreshAt.putIfAbsent(ladder, System.currentTimeMillis());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        maybeRefreshLeaderboard();

        context.fill(0, 0, width, height, 0xF0100C05);
        super.extractRenderState(context, mouseX, mouseY, delta);

        HqTiersLeaderboardClient.PageState state = leaderboardClient.state(ladder);
        List<HqTiersLeaderboardClient.Entry> entries = state.entries();
        List<HqTiersLeaderboardClient.Entry> visibleEntries = visibleEntries(entries);
        int panelLeft = panelLeft();
        int panelRight = panelRight();
        int top = tableTop();
        int bottom = height - 28;
        int rowHeight = ROW_HEIGHT;
        int searchY = searchRowY();

        int tierColX = tierColX(panelLeft, panelRight);
        int eloColX = eloColX(panelRight);
        int nameColX = panelLeft + 46;

        context.fill(panelLeft, top - 18, panelRight, bottom, 0xCC1A1408);
        context.fill(panelLeft, top - 18, panelRight, top - 2, 0xDD2A1E0C);
        context.fill(panelLeft, top - 3, panelRight, top - 2, 0xFFD4AF37);

        context.text(font, "#", panelLeft + 10, top - 14, 0xFFFFE7A3, true);
        context.text(font, "Player", nameColX, top - 14, 0xFFFFE7A3, true);
        context.text(font, "Tier", tierColX, top - 14, 0xFFFFE7A3, true);
        context.text(font, "TR", eloColX, top - 14, 0xFFFFE7A3, true);

        context.text(font, "Click a player to view full stats", panelLeft + 8, legendY() + 1, 0xFF6B5D3A, true);

        if (resolvedSearchEntry != null) {
            context.text(font, "Found: " + resolvedSearchEntry.name(), panelLeft + 310, searchY + 5, 0xFF55FF55, true);
        } else if (searchStatus != null && !searchStatus.isBlank()) {
            context.text(font, searchStatus, panelLeft + 310, searchY + 5, 0xFF7C8BA1, true);
        }

        if (visibleEntries.isEmpty()) {
            String message;
            int color;
            if (state.unsupported()) {
                message = HqTiersFormatter.displayName(ladder) + " leaderboard is coming soon to PvPHQ.";
                color = 0xFFD4AF37;
            } else if (state.error() != null) {
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

            context.centeredText(font, Component.literal(message), width / 2, top + 28, color);
            return;
        }

        int maxScroll = Math.max(0, visibleEntries.size() * rowHeight - (bottom - top));
        scrollOffset = Math.min(scrollOffset, maxScroll);

        int nameMaxChars = Math.max(6, (tierColX - nameColX - 6) / 6);

        context.enableScissor(panelLeft, top, panelRight, bottom);
        for (int i = 0; i < visibleEntries.size(); i++) {
            HqTiersLeaderboardClient.Entry entry = visibleEntries.get(i);
            int y = top + i * rowHeight - scrollOffset;
            if (y + rowHeight < top || y > bottom) {
                continue;
            }

            boolean hovered = mouseX >= panelLeft && mouseX <= panelRight && mouseY >= y && mouseY < y + rowHeight;
            int rank = entry.position();
            boolean isPodium = rank >= 1 && rank <= 3;

            if (isPodium) {
                int accent = podiumColor(rank);
                context.fill(panelLeft + 2, y - 1, panelRight - 2, y + rowHeight - 1, withAlpha(accent, hovered ? 0x40 : 0x22));
                context.fill(panelLeft + 2, y - 1, panelLeft + 4, y + rowHeight - 1, withAlpha(accent, 0xFF));
                context.text(font, String.valueOf(rank), panelLeft + 10, y + 3, accent, true);
            } else if (hovered) {
                context.fill(panelLeft + 2, y - 1, panelRight - 2, y + rowHeight - 1, 0x55D4AF37);
                context.text(font, rank > 0 ? Integer.toString(rank) : "-", panelLeft + 10, y + 3, rankColor(rank), true);
            } else {
                if (i % 2 == 0) {
                    context.fill(panelLeft + 2, y - 1, panelRight - 2, y + rowHeight - 1, 0x22000000);
                }
                context.text(font, rank > 0 ? Integer.toString(rank) : "-", panelLeft + 10, y + 3, rankColor(rank), true);
            }

            int nameColor = isPodium ? podiumColor(rank) : nameColor(rank);
            context.text(font, trim(entry.name(), nameMaxChars), nameColX, y + 3, nameColor, true);

            String rawTierLabel = entry.tierLabel();
            boolean unranked = rawTierLabel.isEmpty();
            String tierText = trim(unranked ? "Unranked" : rawTierLabel, 12);
            int tierColor = unranked ? TIER_DIM : (0xFF000000 | entry.tierColorInt());
            context.text(font, tierText, tierColX, y + 3, tierColor, true);

            context.text(font, entry.elo() + " TR", eloColX, y + 3, tierColor, true);
        }
        context.disableScissor();

        if (state.loading()) {
            context.centeredText(font, Component.literal("Loading more..."), width / 2, height - 18, 0xFFB99842);
        } else {
            context.text(font, entries.size() + " players | page " + Math.max(1, state.page()), panelLeft, height - 18, 0xFF7C8BA1, true);
        }
    }

    private void maybeRefreshLeaderboard() {
        HqTiersLeaderboardClient.PageState state = leaderboardClient.state(ladder);
        if (state.loading() || !searchText().isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        long last = lastLeaderboardRefreshAt.getOrDefault(ladder, 0L);
        if (now - last < LEADERBOARD_REFRESH_INTERVAL_MS) {
            return;
        }

        lastLeaderboardRefreshAt.put(ladder, now);
        leaderboardClient.refresh(ladder);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) (verticalAmount * 18);
        scrollOffset = Math.max(0, scrollOffset);
        HqTiersLeaderboardClient.PageState state = leaderboardClient.state(ladder);
        int visibleRows = Math.max(1, (height - tableTop() - 40) / ROW_HEIGHT);
        if (searchText().isBlank() && scrollOffset > Math.max(0, state.entries().size() - visibleRows - 4) * ROW_HEIGHT) {
            leaderboardClient.loadMore(ladder);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        if (event.button() == 0) {
            HqTiersLeaderboardClient.Entry entry = rowAt(event.x(), event.y());
            if (entry != null && minecraft != null) {
                minecraft.gui.setScreen(new HqTiersPlayerStatsScreen(this, entry.uuid(), entry.name(), ladder));
                return true;
            }
        }
        return super.mouseClicked(event, focused);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // todo: make this the global ladder later
    private static String initialLadder() {
        return "SWORD";
    }

    private HqTiersLeaderboardClient.Entry rowAt(double mouseX, double mouseY) {
        int panelLeft = panelLeft();
        int panelRight = panelRight();
        int top = tableTop();
        int bottom = height - 28;
        int rowHeight = ROW_HEIGHT;
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

    private int panelWidth() {
        int target = Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, (int) (width * 0.6)));
        return Math.min(target, Math.max(MIN_PANEL_WIDTH, width - 40));
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

    private int eloColX(int panelRight) {
        return panelRight - 54;
    }

    private int tabRows() {
        int panelLeft = panelLeft();
        int panelRight = panelRight();
        int availableWidth = (panelRight - 8) - (panelLeft + 8);
        int columns = tabColumns(availableWidth);
        return (LADDERS.size() + columns - 1) / columns;
    }

    private int legendY() {
        return 20 + tabRows() * (TAB_HEIGHT + TAB_ROW_GAP);
    }

    private int searchRowY() {
        return legendY() + 12;
    }

    private int tableTop() {
        return searchRowY() + TAB_HEIGHT + 34;
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
        searchStatus = "Resolving...";
        HqTiersClientState.profileResolver().resolve(query).thenAccept(result -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                if (!query.equals(searchText())) return;
                if (result.status() == MojangProfileResolver.Status.FOUND) {
                    searchStatus = "Fetching stats...";
                    HqTiersClientState.cache().fetch(result.profile().uuid()).thenAccept(stats -> {
                        if (minecraft == null) return;
                        minecraft.execute(() -> {
                            if (!query.equals(searchText())) return;
                            int elo = 0;
                            int position = 0;
                            String tierName = null;
                            String tierColorHex = null;
                            if (stats != null) {
                                HqTiersStats.LadderStats ladderStats = stats.ladder(ladder)
                                        .or(() -> stats.displayLadder())
                                        .orElse(null);
                                if (ladderStats != null) {
                                    elo = ladderStats.totalRating();
                                    position = ladderStats.position();
                                    tierName = ladderStats.tierName();
                                    tierColorHex = ladderStats.tierColorHex();
                                }
                            }
                            if (stats == null) {
                                resolvedSearchEntry = null;
                                searchStatus = "Player has not played PvPHQ ranked.";
                                return;
                            }
                            resolvedSearchEntry = new HqTiersLeaderboardClient.Entry(
                                    position, result.profile().uuid().toString(), result.profile().name(), elo, tierName, tierColorHex);
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

    private static int nameColor(int rank) {
        return 0xFFFFFFFF;
    }
}