package me.micahcode.hqtiers.client.leaderboard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies the official v1 contract, including the distinct global score and page numbering. */
class LeaderboardContractTest {
    /** Overall ranks are already one-based and must display points rather than rating. */
    @Test
    void globalPage() {
        var page = HqTiersLeaderboardClient.parsePage("""
                {"entries":[{"rank":1,"uuid":"player-id","name":"Player","rating":2479,"tr":0,
                "points":165,"tiers":[{"gametype":"sword","tier":"HT3"},{"gametype":"axe","tier":"HT3"}]}],
                "page":{"page":0,"total":113763,"hasNext":true}}
                """);
        var player = page.entries().getFirst();
        assertEquals(1, player.position());
        assertEquals(165, player.points());
        assertEquals(0, player.elo());
        assertEquals(2, player.gamemodes());
        assertTrue(page.hasMore());
        assertEquals(113763, page.total());
    }

    /** Later ladder pages retain their absolute ranks and use the supplied TR field. */
    @Test
    void ladderPage() {
        var page = HqTiersLeaderboardClient.parsePage("""
                {"entries":[{"rank":51,"uuid":"player-id","name":"Player","rating":2449,"tr":399,
                "tier":"HT3","tierColor":"#DD7E46"}],"page":{"page":1,"total":51,"hasNext":false}}
                """);
        assertEquals(51, page.entries().getFirst().position());
        assertEquals(399, page.entries().getFirst().elo());
        assertFalse(page.hasMore());
    }

    /** Empty pages terminate pagination; malformed server responses are reported as errors. */
    @Test
    void emptyAndInvalidResponses() {
        assertFalse(HqTiersLeaderboardClient.parsePage("{\"entries\":[],\"page\":{\"hasNext\":true}}").hasMore());
        assertThrows(IllegalArgumentException.class, () -> HqTiersLeaderboardClient.parsePage("{}"));
        assertThrows(IllegalArgumentException.class, () -> HqTiersLeaderboardClient.parsePage("null"));
    }
}
