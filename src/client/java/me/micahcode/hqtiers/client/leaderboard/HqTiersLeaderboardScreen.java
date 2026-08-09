package me.micahcode.hqtiers.client.leaderboard;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import me.micahcode.hqtiers.client.model.HqTiersRankSystem;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import me.micahcode.hqtiers.client.MojangProfileResolver;

public final class HqTiersLeaderboardScreen extends Screen {
    private static final String[] LADDERS = {
            "SWORD", "AXE", "MACE", "SPEAR_MACE", "UHC", "VANILLA",
            "CART", "DIAMOND_POT", "NETHERITE_OP", "SMP", "DIAMOND_SMP"
    };

    private static final int TAB_HEIGHT = 15;
    private static final int TAB_GAP = 3;
    private static final int TAB_ROW_GAP = 3;
    private static final int MIN_TAB_WIDTH = 58;
    private static final int MAX_TAB_COLUMNS = 6;
    private static final int MIN_PANEL_WIDTH = 320;
    private static final int MAX_PANEL_WIDTH = 480;
    private static final int ROW_HEIGHT = 16;


    private static final long LEADERBOARD_REFRESH_INTERVAL_MS = 10_000;
    private static final long TIER_REFETCH_COOLDOWN_MS = 5_000;

    // Podium accent colors (ARGB)
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
    private String pendingResolveName = "";
    private final Map<String, Long> lastLeaderboardRefreshAt = new HashMap<>();
    private final Map<String, Long> tierRequestedAt = new HashMap<>();
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

        for (int i = 0; i < LADDERS.length; i++) {
            String tabLadder = LADDERS[i];
            int row = i / columns;
            int col = i % columns;
            int x = startX + col * (tabWidth + TAB_GAP);
            int y = 20 + row * (TAB_HEIGHT + TAB_ROW_GAP);
            String prefix = tabLadder.equals(ladder) ? "> " : "";
            Button tab = Button.builder(Component.literal(prefix + tabButtonLabel(tabLadder)), button -> {
                ladder = tabLadder;
                scrollOffset = 0;
                leaderboardClient.load(ladder);
                lastLeaderboardRefreshAt.put(ladder, System.currentTimeMillis());
                init();
            }).bounds(x, y, tabWidth, TAB_HEIGHT).build();
            tab.active = !tabLadder.equals(ladder);
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
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        maybeRefreshLeaderboard();

        context.fill(0, 0, width, height, 0xF0100C05);
        super.render(context, mouseX, mouseY, delta);

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

        context.fillGradient(panelLeft - 2, top - 20, panelRight + 2, bottom + 2, 0x662A1E0C, 0x00000000);
        context.fill(panelLeft, top - 18, panelRight, bottom, 0xCC1A1408);
        context.fillGradient(panelLeft, top - 18, panelRight, top - 2, 0xEE33260F, 0xCC2A1E0C);
        context.fill(panelLeft, top - 3, panelRight, top - 2, 0xFFD4AF37);

        context.drawString(font, "#", panelLeft + 10, top - 14, 0xFFFFE7A3);
        context.drawString(font, "Player", nameColX, top - 14, 0xFFFFE7A3);
        context.drawString(font, "Tier", tierColX, top - 14, 0xFFFFE7A3);
        context.drawString(font, "TR", eloColX, top - 14, 0xFFFFE7A3);

        context.drawString(font, "Click a player to view full stats", panelLeft + 8, legendY() + 1, 0xFF6B5D3A);

        if (resolvedSearchEntry != null) {
            context.drawString(font, "Found: " + resolvedSearchEntry.name(), panelLeft + 310, searchY + 5, 0xFF55FF55);
        } else if (searchStatus != null && !searchStatus.isBlank()) {
            context.drawString(font, searchStatus, panelLeft + 310, searchY + 5, 0xFF7C8BA1);
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

            context.drawCenteredString(font, message, width / 2, top + 28, color);
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
                context.fillGradient(panelLeft + 2, y - 1, panelRight - 2, y + rowHeight - 1,
                        withAlpha(accent, hovered ? 0x50 : 0x30), 0x00000000);
                context.fill(panelLeft + 2, y - 1, panelLeft + 4, y + rowHeight - 1, withAlpha(accent, 0xFF));
                context.drawString(font, String.valueOf(rank), panelLeft + 10, y + 3, accent);
            } else if (hovered) {
                context.fill(panelLeft + 2, y - 1, panelRight - 2, y + rowHeight - 1, 0x55D4AF37);
                context.drawString(font, rank > 0 ? Integer.toString(rank) : "-", panelLeft + 10, y + 3, rankColor(rank));
            } else {
                if (i % 2 == 0) {
                    context.fill(panelLeft + 2, y - 1, panelRight - 2, y + rowHeight - 1, 0x22000000);
                }
                context.drawString(font, rank > 0 ? Integer.toString(rank) : "-", panelLeft + 10, y + 3, rankColor(rank));
            }

            int nameColor = isPodium ? podiumColor(rank) : nameColor(rank);
            context.drawString(font, trim(entry.name(), nameMaxChars), nameColX, y + 3, nameColor);

            TierLookup tier = tierFor(entry);
            String tierText = tier.loaded() ? trim(tier.label(), 12) : "···";
            int tierColor = tier.loaded() ? (0xFF000000 | tier.colorInt()) : 0xFF5C5138;
            context.drawString(font, tierText, tierColX, y + 3, tierColor);

            context.drawString(font, entry.elo() + " TR", eloColX, y + 3, eloColor(entry.elo()));
        }
        context.disableScissor();

        if (state.loading()) {
            context.drawCenteredString(font, "Loading more...", width / 2, height - 18, 0xFFB99842);
        } else {
            context.drawString(font, entries.size() + " players | page " + Math.max(1, state.page()), panelLeft, height - 18, 0xFF7C8BA1);
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
    public boolean mouseClicked(MouseButtonEvent click, boolean focused) {
        if (click.button() == 0) {
            HqTiersLeaderboardClient.Entry entry = rowAt(click.x(), click.y());
            if (entry != null && minecraft != null) {
                minecraft.setScreen(new HqTiersPlayerStatsScreen(this, entry.uuid(), entry.name(), ladder));
                return true;
            }
        }
        return super.mouseClicked(click, focused);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // todo: make this global
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
        return (LADDERS.length + columns - 1) / columns;
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
            minecraft.setScreen(new HqTiersPlayerStatsScreen(this, resolvedSearchEntry.uuid(), resolvedSearchEntry.name(), ladder));
            return;
        }

        for (HqTiersLeaderboardClient.Entry entry : leaderboardClient.state(ladder).entries()) {
            if (entry.name().equalsIgnoreCase(query)) {
                minecraft.setScreen(new HqTiersPlayerStatsScreen(this, entry.uuid(), entry.name(), ladder));
                return;
            }
        }

        searchStatus = "Searching...";
        try {
            UUID uuid = parseUuid(query);
            minecraft.setScreen(new HqTiersPlayerStatsScreen(this, uuid.toString(), query, ladder));
            return;
        } catch (IllegalArgumentException ignored) {
        }

        HqTiersClientState.profileResolver().resolve(query).thenAccept(result -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                if (result.status() == MojangProfileResolver.Status.FOUND) {
                    minecraft.setScreen(new HqTiersPlayerStatsScreen(this, result.profile().uuid().toString(), result.profile().name(), ladder));
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
                            if (stats != null) {
                                HqTiersStats.LadderStats ladderStats = stats.ladder(ladder)
                                        .or(() -> stats.displayLadder())
                                        .orElse(null);
                                if (ladderStats != null) {
                                    elo = ladderStats.totalRating();
                                    position = ladderStats.position();
                                }
                            }
                            if (stats == null) {
                                resolvedSearchEntry = null;
                                searchStatus = "Player has not played PvPHQ ranked.";
                                return;
                            }
                            resolvedSearchEntry = new HqTiersLeaderboardClient.Entry(position, result.profile().uuid().toString(), result.profile().name(), elo);
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

    private static String tabButtonLabel(String ladder) {
        return switch (ladder) {
            case "DIAMOND_POT" -> "Pot";
            case "NETHERITE_OP" -> "NethOP";
            case "DIAMOND_SMP" -> "D.SMP";
            case "SPEAR_MACE" -> "S.Mace";
            default -> HqTiersFormatter.displayName(ladder);
        };
    }

    private record TierLookup(boolean loaded, String label, int colorInt) {
        static TierLookup unloaded() {
            return new TierLookup(false, "", 0);
        }
    }

    private TierLookup tierFor(HqTiersLeaderboardClient.Entry entry) {
        UUID uuid;
        try {
            uuid = UUID.fromString(entry.uuid());
        } catch (IllegalArgumentException invalid) {
            return TierLookup.unloaded();
        }

        Optional<HqTiersStats> cached = HqTiersClientState.cache().getIfFresh(uuid);
        if (cached.isPresent()) {
            Optional<HqTiersStats.LadderStats> real = cached.get().ladder(ladder);
            if (real.isPresent()) {
                HqTiersStats.LadderStats l = real.get();
                return new TierLookup(true, l.tierLabel(), l.tierColorInt());
            }
            return new TierLookup(true, "", 0);
        }

        long now = System.currentTimeMillis();
        long last = tierRequestedAt.getOrDefault(entry.uuid(), 0L);
        if (now - last >= TIER_REFETCH_COOLDOWN_MS) {
            tierRequestedAt.put(entry.uuid(), now);
            HqTiersClientState.cache().fetch(uuid);
        }
        return TierLookup.unloaded();
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

    private static int eloColor(int elo) {
        return 0xFF000000 | HqTiersRankSystem.ratingColor(elo);
    }
}