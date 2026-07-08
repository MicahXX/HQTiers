package me.micahcode.hqtiers.client.model;

public final class HqTiersRankSystem {
	public static final String RATING_LABEL = "SR";

	private HqTiersRankSystem() {
	}

	public static String fallbackTierLabel(int rating) {
		// todo: will be different
		if (rating >= 2175) return "HT1";
		if (rating >= 1900) return "MT1";
		if (rating >= 1770) return "LT1";
		if (rating >= 1650) return "HT2";
		if (rating >= 1525) return "MT2";
		if (rating >= 1400) return "LT2";
		if (rating >= 1275) return "HT3";
		if (rating >= 1125) return "MT3";
		if (rating >= 1025) return "LT3";
		if (rating >= 900) return "HT4";
		if (rating >= 800) return "MT4";
		if (rating >= 700) return "LT4";
		if (rating >= 600) return "HT5";
		if (rating >= 400) return "MT5";
		return "LT5";
	}

	public static String normalizeRankLabel(String rank) {
		if (rank == null || rank.isBlank()) {
			return rank;
		}

		String compact = rank.trim()
				.toUpperCase()
				.replace('-', '_')
				.replace(' ', '_');
		compact = compact.replace("LOW_TIER_", "LT")
				.replace("MID_TIER_", "MT")
				.replace("HIGH_TIER_", "HT")
				.replace("LOWTIER_", "LT")
				.replace("MIDTIER_", "MT")
				.replace("HIGHTIER_", "HT")
				.replace("_", "");

		return compact;
	}

	public static int tierColor(String tier, int position) {
		HqTiersRanks rank = HqTiersRanks.fromString(normalizeRankLabel(tier));
		if (rank == null) return 0xFFFFFF;
		return hexToColor(rank.getHexColor());
	}

	public static int ratingColor(int rating) {
		return tierColor(fallbackTierLabel(rating), 0);
	}

	private static int hexToColor(String hex) {
		if (hex == null || hex.isBlank()) return 0xFFFFFF;
		String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
		try {
			return Integer.parseInt(cleaned, 16);
		} catch (NumberFormatException e) {
			return 0xFFFFFF;
		}
	}

	private static String titleCaseTier(String rank) {
		String[] words = rank.replace('_', ' ').toLowerCase().split("\\s+");
		StringBuilder builder = new StringBuilder();
		for (String word : words) {
			if (word.isBlank()) {
				continue;
			}

			if (!builder.isEmpty()) {
				builder.append(' ');
			}

			builder.append(titleCaseTierWord(word));
		}

		return builder.toString();
	}

	private static String titleCaseTierWord(String word) {
		return switch (word.toUpperCase()) {
			case "I", "II", "III", "IV", "V" -> word.toUpperCase();
			default -> Character.toUpperCase(word.charAt(0)) + word.substring(1);
		};
	}
}