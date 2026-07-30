package me.micahcode.hqtiers.client.leaderboard;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import me.micahcode.hqtiers.client.model.HqTiersRankSystem;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import me.micahcode.hqtiers.client.MojangProfileResolver;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HqTiersLeaderboardScreen extends Screen {
    private static final String[] LADDERS = {
            "SWORD", "AXE", "MACE", "SPEAR_MACE", "UHC", "VANILLA",
            "CART", "DIAMOND_POT", "NETHERITE_OP", "SMP", "DIAMOND_SMP"
    };
    private static final Set<String> LADDERS_COMING_SOON = Set.of("CART", "SPEAR_MACE");

    private static final int TAB_HEIGHT = 18;
    private static final int TAB_GAP = 6;
    private static final int TAB_COLUMNS = 4;

    private final HqTiersLeaderboardClient leaderboardClient;
    private String ladder = initialLadder();
    private int scrollOffset;
    private EditBox searchField;
    private String searchQuery = "";
    private String searchStatus = "";
    private HqTiersLeaderboardClient.Entry resolvedSearchEntry;
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
        int tabWidth = (availableWidth - (TAB_COLUMNS - 1) * TAB_GAP) / TAB_COLUMNS;

        for (int i = 0; i < LADDERS.length; i++) {
            String tabLadder = LADDERS[i];
            int row = i / TAB_COLUMNS;
            int col = i % TAB_COLUMNS;
            int x = startX + col * (tabWidth + TAB_GAP);
            int y = 22 + row * (TAB_HEIGHT + 4);
            String prefix = tabLadder.equals(ladder) ? "> " : "";
            Button tab = Button.builder(Component.literal(prefix + tabButtonLabel(tabLadder)), button -> {
                ladder = tabLadder;
                scrollOffset = 0;
                leaderboardClient.load(ladder);
                init();
            }).bounds(x, y, tabWidth, TAB_HEIGHT).build();
            tab.active = !tabLadder.equals(ladder);
            addRenderableWidget(tab);
        }

        int searchY = searchRowY();
        searchField = new EditBox(font, panelLeft + 8, searchY, 220, 18, Component.literal("Search player"));
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
                .bounds(panelLeft + 234, searchY, 68, 18)
                .build());
        leaderboardClient.load(ladder);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xF0100C05);
        super.extractRenderState(context, mouseX, mouseY, delta);

        HqTiersLeaderboardClient.PageState state = leaderboardClient.state(ladder);
        List<HqTiersLeaderboardClient.Entry> entries = state.entries();
        List<HqTiersLeaderboardClient.Entry> visibleEntries = visibleEntries(entries);
        int panelLeft = panelLeft();
        int panelRight = panelRight();
        int top = tableTop();
        int bottom = height - 28;
        int rowHeight = 16;
        int searchY = searchRowY();

        context.fill(panelLeft, top - 18, panelRight, bottom, 0xCC1A1408);
        context.fill(panelLeft, top - 18, panelRight, top - 2, 0xDD2A1E0C);
        context.text(font, "#", panelLeft + 10, top - 14, 0xFFFFE7A3, true);
        context.text(font, "Player", panelLeft + 46, top - 14, 0xFFFFE7A3, true);
        context.text(font, "Tier", panelRight - 132, top - 14, 0xFFFFE7A3, true);
        context.text(font, "TR", panelRight - 54, top - 14, 0xFFFFE7A3, true);

        context.text(font, "* this as of now does not work", panelLeft + 8, legendY() + 1, 0xFF6B5D3A, true);

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
                color = 0xFFD4AF37; // gold accent - reads as "planned", not "broken"
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

        context.enableScissor(panelLeft, top, panelRight, bottom);
        for (int i = 0; i < visibleEntries.size(); i++) {
            HqTiersLeaderboardClient.Entry entry = visibleEntries.get(i);
            int y = top + i * rowHeight - scrollOffset;
            if (y + rowHeight < top || y > bottom) {
                continue;
            }

            boolean hovered = mouseX >= panelLeft && mouseX <= panelRight && mouseY >= y && mouseY < y + rowHeight;
            if (hovered) {
                context.fill(panelLeft + 2, y - 1, panelRight - 2, y + rowHeight - 1, 0x55D4AF37);
            } else if (i % 2 == 0) {
                context.fill(panelLeft + 2, y - 1, panelRight - 2, y + rowHeight - 1, 0x22000000);
            }

            HqTiersStats.LadderStats rowStats = ladderStatsFor(entry);
            context.text(font, entry.position() > 0 ? Integer.toString(entry.position()) : "-", panelLeft + 10, y + 3, rankColor(entry.position()), true);
            context.text(font, trim(entry.name(), 18), panelLeft + 46, y + 3, nameColor(entry.position()), true);
            context.text(font, trim(rowStats.tierLabel(), 12), panelRight - 132, y + 3, 0xFF000000 | rowStats.tierColorInt(), true);
            context.text(font, entry.elo() + " TR", panelRight - 54, y + 3, eloColor(entry.elo()), true);
        }
        context.disableScissor();

        if (state.loading()) {
            context.centeredText(font, Component.literal("Loading more..."), width / 2, height - 18, 0xFFB99842);
        } else {
            context.text(font, entries.size() + " players | page " + Math.max(1, state.page()), panelLeft, height - 18, 0xFF7C8BA1, true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) (verticalAmount * 18);
        scrollOffset = Math.max(0, scrollOffset);
        HqTiersLeaderboardClient.PageState state = leaderboardClient.state(ladder);
        int visibleRows = Math.max(1, (height - tableTop() - 40) / 16);
        if (searchText().isBlank() && scrollOffset > Math.max(0, state.entries().size() - visibleRows - 4) * 16) {
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

    private static String initialLadder() {
        // SWORD is used as the default because GLOBAL has no leaderboard
        // endpoint on the API at all - that tab used to load nothing forever.
        return "SWORD";
    }

    private HqTiersLeaderboardClient.Entry rowAt(double mouseX, double mouseY) {
        int panelLeft = panelLeft();
        int panelRight = panelRight();
        int top = tableTop();
        int bottom = height - 28;
        int rowHeight = 16;
        if (mouseX < panelLeft || mouseX > panelRight || mouseY < top || mouseY > bottom) {
            return null;
        }

        int index = ((int) mouseY - top + scrollOffset) / rowHeight;
        List<HqTiersLeaderboardClient.Entry> entries = visibleEntries(leaderboardClient.state(ladder).entries());
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private int panelLeft() {
        return Math.max(20, width / 2 - 190);
    }

    private int panelRight() {
        return Math.min(width - 20, width / 2 + 190);
    }

    private int tabRows() {
        return (LADDERS.length + TAB_COLUMNS - 1) / TAB_COLUMNS;
    }

    private int legendY() {
        return 22 + tabRows() * (TAB_HEIGHT + 4);
    }

    private int searchRowY() {
        return legendY() + 12;
    }

    private int tableTop() {
        return searchRowY() + TAB_HEIGHT + 34;
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
        String label = switch (ladder) {
            case "DIAMOND_POT" -> "Pot";
            case "NETHERITE_OP" -> "NethOP";
            case "DIAMOND_SMP" -> "D.SMP";
            case "SPEAR_MACE" -> "S.Mace";
            default -> HqTiersFormatter.displayName(ladder);
        };
        return LADDERS_COMING_SOON.contains(ladder) ? label + "*" : label;
    }

    private static HqTiersStats.LadderStats ladderStatsFor(HqTiersLeaderboardClient.Entry entry) {
        return HqTiersStats.LadderStats.minimal("LEADERBOARD", entry.elo(), 1, 0, 0, null, entry.position());
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "...";
    }

    private static int rankColor(int rank) {
        if (rank == 1) return 0xFFFFD700;
        if (rank == 2) return 0xFFC0C0C0;
        if (rank == 3) return 0xFFCD7F32;
        if (rank <= 10) return 0xFFFFFF88;
        return 0xFF9CA3AF;
    }

    private static int nameColor(int rank) {
        if (rank <= 3) return rankColor(rank);
        return 0xFFFFFFFF;
    }

    private static int eloColor(int elo) {
        return 0xFF000000 | HqTiersRankSystem.ratingColor(elo);
    }
}