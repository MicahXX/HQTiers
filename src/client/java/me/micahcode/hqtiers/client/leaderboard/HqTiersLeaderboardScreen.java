package me.micahcode.hqtiers.client.leaderboard;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import me.micahcode.hqtiers.client.model.HqTiersRankSystem;
import me.micahcode.hqtiers.client.model.HqTiersRanks;
import me.micahcode.hqtiers.client.HqTiersFormatter;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import me.micahcode.hqtiers.client.MojangProfileResolver;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class HqTiersLeaderboardScreen extends Screen {
    private static final String[] LADDERS = {
            "GLOBAL", "SWORD", "AXE", "UHC", "VANILLA",
            "MACE", "DIAMOND_POT", "NETHERITE_OP", "SMP", "DIAMOND_SMP"
    };

    private final HqTiersLeaderboardClient leaderboardClient;
    private String ladder = initialLadder();
    private int scrollOffset;
    private TextFieldWidget searchField;
    private String searchQuery = "";
    private String searchStatus = "";
    private HqTiersLeaderboardClient.Entry resolvedSearchEntry;
    private String pendingResolveName = "";

    public HqTiersLeaderboardScreen(HqTiersLeaderboardClient leaderboardClient) {
        super(Text.literal("HQTiers Leaderboard"));
        this.leaderboardClient = leaderboardClient;
    }

    @Override
    protected void init() {
        clearChildren();
        int tabWidth = 68;
        int tabHeight = 18;
        int gap = 6;
        int panelLeft = panelLeft();
        int startX = panelLeft + 8;

        for (int i = 0; i < LADDERS.length; i++) {
            String tabLadder = LADDERS[i];
            int row = i / 5;
            int col = i % 5;
            int x = startX + col * (tabWidth + gap);
            int y = 22 + row * (tabHeight + 4);
            ButtonWidget tab = ButtonWidget.builder(Text.literal((tabLadder.equals(ladder) ? "> " : "") + tabButtonLabel(tabLadder)), button -> {
                ladder = tabLadder;
                scrollOffset = 0;
                leaderboardClient.load(ladder);
                init();
            }).dimensions(x, y, tabWidth, tabHeight).build();
            tab.active = !tabLadder.equals(ladder);
            addDrawableChild(tab);
        }

        searchField = new TextFieldWidget(textRenderer, panelLeft + 8, 72, 220, 18, Text.literal("Search player"));
        searchField.setMaxLength(32);
        searchField.setPlaceholder(Text.literal("Search player..."));
        searchField.setText(searchQuery);
        searchField.setChangedListener(value -> {
            searchQuery = value.trim();
            searchStatus = "";
            resolvedSearchEntry = null;
            resolveSearchIfNeeded(searchQuery);
        });
        addDrawableChild(searchField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Search"), button -> searchPlayer())
                .dimensions(panelLeft + 234, 72, 68, 18)
                .build());
        leaderboardClient.load(ladder);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xF0100C05);
        super.render(context, mouseX, mouseY, delta);

        HqTiersLeaderboardClient.PageState state = leaderboardClient.state(ladder);
        List<HqTiersLeaderboardClient.Entry> entries = state.entries();
        List<HqTiersLeaderboardClient.Entry> visibleEntries = visibleEntries(entries);
        int panelLeft = panelLeft();
        int panelRight = panelRight();
        int top = tableTop();
        int bottom = height - 28;
        int rowHeight = 16;

        context.fill(panelLeft, top - 18, panelRight, bottom, 0xCC1A1408);
        context.fill(panelLeft, top - 18, panelRight, top - 2, 0xDD2A1E0C);
        context.drawTextWithShadow(textRenderer, "#", panelLeft + 10, top - 14, 0xFFFFE7A3);
        context.drawTextWithShadow(textRenderer, "Player", panelLeft + 46, top - 14, 0xFFFFE7A3);
        context.drawTextWithShadow(textRenderer, "Tier", panelRight - 132, top - 14, 0xFFFFE7A3);
        context.drawTextWithShadow(textRenderer, "SR", panelRight - 54, top - 14, 0xFFFFE7A3);

        if (resolvedSearchEntry != null) {
            context.drawTextWithShadow(textRenderer, "Found: " + resolvedSearchEntry.name(), panelLeft + 310, 77, 0xFF55FF55);
        } else if (searchStatus != null && !searchStatus.isBlank()) {
            context.drawTextWithShadow(textRenderer, searchStatus, panelLeft + 310, 77, 0xFF7C8BA1);
        }

        if (visibleEntries.isEmpty()) {
            String message = state.error() != null ? state.error() : state.loading() ? "Loading..." : "No leaderboard data.";
            if (!entries.isEmpty() && !searchText().isBlank()) {
                message = resolvedSearchEntry == null ? "No loaded rows match. Resolving player..." : "Press Search to open found player.";
            }
            context.drawCenteredTextWithShadow(textRenderer, message, width / 2, top + 28, 0xFFAAAAAA);
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
            context.drawTextWithShadow(textRenderer, entry.position() > 0 ? Integer.toString(entry.position()) : "-", panelLeft + 10, y + 3, rankColor(entry.position()));
            context.drawTextWithShadow(textRenderer, trim(entry.name(), 18), panelLeft + 46, y + 3, nameColor(entry.position()));
            context.drawTextWithShadow(textRenderer, trim(rowStats.tierLabel(), 12), panelRight - 132, y + 3, tierColor(rowStats.tier()));
            context.drawTextWithShadow(textRenderer, entry.elo() + " SR", panelRight - 54, y + 3, eloColor(entry.elo()));
        }
        context.disableScissor();

        if (state.loading()) {
            context.drawCenteredTextWithShadow(textRenderer, "Loading more...", width / 2, height - 18, 0xFFB99842);
        } else {
            context.drawTextWithShadow(textRenderer, entries.size() + " players | page " + Math.max(1, state.page()), panelLeft, height - 18, 0xFF7C8BA1);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) (verticalAmount * 18);
        scrollOffset = Math.max(0, scrollOffset);
        HqTiersLeaderboardClient.PageState state = leaderboardClient.state(ladder);
        int visibleRows = Math.max(1, (height - 110) / 16);
        if (searchText().isBlank() && scrollOffset > Math.max(0, state.entries().size() - visibleRows - 4) * 16) {
            leaderboardClient.loadMore(ladder);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean focused) {
        if (click.button() == 0) {
            HqTiersLeaderboardClient.Entry entry = rowAt(click.x(), click.y());
            if (entry != null && client != null) {
                String autoLadder = ladder.equals("GLOBAL") ? null : ladder;
                client.setScreen(new HqTiersPlayerStatsScreen(this, entry.uuid(), entry.name(), autoLadder));
                return true;
            }
        }
        return super.mouseClicked(click, focused);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static String initialLadder() {
        return "GLOBAL";
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

    private int tableTop() {
        return 124;
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
        return searchField == null ? searchQuery : searchField.getText().trim();
    }

    private void searchPlayer() {
        String query = searchText();
        if (query.isBlank() || client == null) return;
        String autoLadder = ladder.equals("GLOBAL") ? null : ladder;

        if (resolvedSearchEntry != null && resolvedSearchEntry.name().equalsIgnoreCase(query)) {
            client.setScreen(new HqTiersPlayerStatsScreen(this, resolvedSearchEntry.uuid(), resolvedSearchEntry.name(), autoLadder));
            return;
        }

        for (HqTiersLeaderboardClient.Entry entry : leaderboardClient.state(ladder).entries()) {
            if (entry.name().equalsIgnoreCase(query)) {
                client.setScreen(new HqTiersPlayerStatsScreen(this, entry.uuid(), entry.name(), autoLadder));
                return;
            }
        }

        searchStatus = "Searching...";
        try {
            UUID uuid = parseUuid(query);
            client.setScreen(new HqTiersPlayerStatsScreen(this, uuid.toString(), query, autoLadder));
            return;
        } catch (IllegalArgumentException ignored) {
        }

        HqTiersClientState.profileResolver().resolve(query).thenAccept(result -> {
            if (client == null) return;
            client.execute(() -> {
                if (result.status() == MojangProfileResolver.Status.FOUND) {
                    client.setScreen(new HqTiersPlayerStatsScreen(this, result.profile().uuid().toString(), result.profile().name(), autoLadder));
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
            if (client == null) return;
            client.execute(() -> {
                if (!query.equals(searchText())) return;
                if (result.status() == MojangProfileResolver.Status.FOUND) {
                    searchStatus = "Fetching stats...";
                    HqTiersClientState.cache().fetch(result.profile().uuid()).thenAccept(stats -> {
                        if (client == null) return;
                        client.execute(() -> {
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
            case "GLOBAL" -> "Global";
            case "DIAMOND_POT" -> "Pot";
            case "NETHERITE_OP" -> "NethOP";
            case "DIAMOND_SMP" -> "D.SMP";
            default -> HqTiersFormatter.displayName(ladder);
        };
    }

    private static HqTiersStats.LadderStats ladderStatsFor(HqTiersLeaderboardClient.Entry entry) {
        return new HqTiersStats.LadderStats("LEADERBOARD", entry.elo(), 1, 0, 0, 1, null, entry.position());
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

    private static int tierColor(HqTiersRanks tier) {
        int color = HqTiersRankSystem.tierColor(tier);
        return color == 0xFFFFFF ? 0xFFAAAAAA : 0xFF000000 | color;
    }

    private static int eloColor(int elo) {
        return 0xFF000000 | HqTiersRankSystem.ratingColor(elo);
    }
}