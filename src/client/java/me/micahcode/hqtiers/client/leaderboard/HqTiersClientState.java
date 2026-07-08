package me.micahcode.hqtiers.client.leaderboard;

import me.micahcode.hqtiers.client.HqTiersCache;
import me.micahcode.hqtiers.client.MojangProfileResolver;

public final class HqTiersClientState {
	private static final HqTiersCache CACHE = new HqTiersCache();
	private static final MojangProfileResolver PROFILE_RESOLVER = new MojangProfileResolver();
	private static final me.micahcode.hqtiers.client.leaderboard.HqTiersLeaderboardClient LEADERBOARD_CLIENT =
			new me.micahcode.hqtiers.client.leaderboard.HqTiersLeaderboardClient();

	private HqTiersClientState() {}

	public static HqTiersCache cache() {
		return CACHE;
	}

	public static MojangProfileResolver profileResolver() {
		return PROFILE_RESOLVER;
	}

	public static me.micahcode.hqtiers.client.leaderboard.HqTiersLeaderboardClient leaderboardClient() {
		return LEADERBOARD_CLIENT;
	}
}