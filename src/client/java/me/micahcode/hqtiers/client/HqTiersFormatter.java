package me.micahcode.hqtiers.client;

import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.model.HqTiersRankSystem;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.*;

public final class HqTiersFormatter {
	private HqTiersFormatter() {
	}

	public static Text compact(HqTiersStats stats) {
		HqTiersStats.LadderStats ladder = stats.displayLadder().orElse(null);

		if (ladder == null || !ladder.hasPlayedRanked()) {
			return Text.literal("Unranked").formatted(Formatting.GRAY);
		}

		return decorated(ladder);
	}

	public static Text previewCompact() {
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			HqTiersStats real =
					HqTiersClientState.cache()
							.getIfFresh(client.player.getUuid()).orElse(null);
			if (real != null) return compact(real);
		}
		HqTiersStats.LadderStats fake = HqTiersStats.LadderStats.minimal(
				HqTiersClientConfig.preferredLadder, 800, 10, 5, 10, "MT4", 123
		);
		return decorated(fake);
	}

	public static Text hud(HqTiersStats stats) {
		HqTiersStats.LadderStats ladder = stats.displayLadder().orElse(null);

		if (ladder == null) {
			return Text.literal("PvPHQ: Unranked").formatted(Formatting.GRAY);
		}

		return Text.literal("PvPHQ: ").formatted(Formatting.GRAY)
				.append(Text.literal(stats.name()).formatted(Formatting.WHITE))
				.append(Text.literal(" "))
				.append(decorated(ladder));
	}

	public static Text details(HqTiersStats stats) {
		return Text.literal("PvPHQ stats for ").formatted(Formatting.GRAY)
				.append(Text.literal(stats.name()).formatted(Formatting.WHITE))
				.append(Text.literal(":").formatted(Formatting.GRAY));
	}

	public static List<Text> ladderDetails(HqTiersStats stats) {
		List<Text> lines = new ArrayList<>();
		stats.ladder("GLOBAL").ifPresent(global -> lines.add(ladderDetailLine(global)));
		stats.ladders().values().stream()
				.filter(HqTiersStats.LadderStats::hasPlayedRanked)
				.filter(ladder -> !ladder.ladder().equals("GLOBAL"))
				.sorted(Comparator.comparingInt(HqTiersStats.LadderStats::totalRating).reversed())
				.forEach(ladder -> lines.add(ladderDetailLine(ladder)));
		return lines;
	}

	private static Text ladderDetailLine(HqTiersStats.LadderStats ladder) {
		return Text.literal("  ")
				.append(icon(ladder.ladder()))
				.append(Text.literal(" "))
				.append(Text.literal(displayName(ladder.ladder())).formatted(Formatting.AQUA))
				.append(Text.literal(": ").formatted(Formatting.GRAY))
				.append(Text.literal(ladder.tierLabel()).formatted(Formatting.GOLD))
				.append(Text.literal(" | ").formatted(Formatting.DARK_GRAY))
				.append(Text.literal(ratingText(ladder.totalRating())).setStyle(Style.EMPTY.withColor(ratingColor(ladder.totalRating()))))
				.append(Text.literal(" | ").formatted(Formatting.DARK_GRAY))
				.append(Text.literal(ladder.wins() + "W/" + ladder.losses() + "L").formatted(Formatting.WHITE))
				.append(positionDetails(ladder));
	}

	private static Text positionDetails(HqTiersStats.LadderStats ladder) {
		if (!ladder.hasPosition()) {
			return Text.empty();
		}

		return Text.literal(" | #").formatted(Formatting.GRAY)
				.append(Text.literal(Integer.toString(ladder.position())).formatted(Formatting.WHITE));
	}

	private static Text decorated(HqTiersStats.LadderStats ladder) {
		MutableText text = Text.empty();
		boolean wrotePart = false;

		for (HqTiersClientConfig.NametagComponent component : HqTiersClientConfig.nametagOrder) {
			switch (component) {
				case GAMEMODE_ICON -> {
					if (!HqTiersClientConfig.gamemodeIconEnabled) continue;
					if (wrotePart) text.append(Text.literal(" "));
					text.append(icon(ladder.ladder()));
					wrotePart = true;
				}
				case TIER -> {
					if (!HqTiersClientConfig.tierEnabled) continue;
					if (wrotePart) text.append(Text.literal(" "));
					if (HqTiersClientConfig.coloredTier) {
						text.append(Text.literal(tierLabel(ladder)).setStyle(Style.EMPTY.withColor(ladder.tierColorInt())));
					} else {
						text.append(Text.literal(tierLabel(ladder)).formatted(Formatting.WHITE));
					}
					wrotePart = true;
				}
				case SEPARATOR -> {
					if (!HqTiersClientConfig.separatorEnabled || !wrotePart) continue;
					text.append(Text.literal(" | ").formatted(Formatting.DARK_GRAY));
				}
				case ELO -> {
					if (!HqTiersClientConfig.eloEnabled) continue;
					Style eloStyle = Style.EMPTY.withColor(HqTiersClientConfig.coloredElo ? ratingColor(ladder.totalRating()) : 0xFFFFFF);
					text.append(Text.literal(Integer.toString(ladder.totalRating())).setStyle(eloStyle));
					if (HqTiersClientConfig.eloLabelEnabled)
						text.append(Text.literal(" " + HqTiersRankSystem.RATING_LABEL).setStyle(eloStyle));
					wrotePart = true;
				}
				case POSITION -> {
					if (!HqTiersClientConfig.positionEnabled || !ladder.hasPosition()) continue;
					int posColor = HqTiersClientConfig.coloredPosition ? ladder.tierColorInt() : 0xFFFFFF;
					if (HqTiersClientConfig.positionLabelEnabled)
						text.append(Text.literal("#").setStyle(Style.EMPTY.withColor(posColor)));
					text.append(Text.literal(Integer.toString(ladder.position())).setStyle(Style.EMPTY.withColor(posColor)));
					wrotePart = true;
				}
			}
		}
		return text;
	}

	private static String tierLabel(HqTiersStats.LadderStats ladder) {
		if (!HqTiersClientConfig.shortTierNames) {
			return ladder.tierLabel();
		}

		String tag = ladder.tierLabel();
		if (tag.matches("(?i)[LMH]T[1-5]")) {
			return tag.toUpperCase();
		}

		String[] words = tag.split("\\s+");
		if (words.length < 2) {
			return tag;
		}

		return words[0].substring(0, 1).toUpperCase() + shortDivision(words[1]);
	}

	private static String shortDivision(String division) {
		return switch (division.toUpperCase()) {
			case "I" -> "1";
			case "II" -> "2";
			case "III" -> "3";
			case "IV" -> "4";
			case "V" -> "5";
			default -> division;
		};
	}

	public static Text icon(String ladder) {
		return Text.literal(String.valueOf(iconGlyph(ladder)))
				.setStyle(HqTiersMinecraftCompat.fontStyle(Identifier.of("hqtiers", "default"))
						.withColor(0xFFFFFF));
	}

	public static String ratingText(int rating) {
		return rating + " " + HqTiersRankSystem.RATING_LABEL;
	}

	public static int ratingColor(int rating) {
		return HqTiersRankSystem.ratingColor(rating);
	}

	private static char iconGlyph(String ladder) {
		return switch (HqTiersClientConfig.normalizeLadder(ladder)) {
			case "GLOBAL" -> '\uE00A';
			case "SWORD" -> '\uE001';
			case "AXE" -> '\uE002';
			case "VANILLA", "CRYSTAL" -> '\uE003';
			case "UHC" -> '\uE004';
			case "MACE", "SPEAR_MACE", "SPEAR" -> '\uE005';
			case "NETHERITE_OP", "NETHERITE_POT" -> '\uE006';
			case "DIAMOND_POT", "POT" -> '\uE007';
			case "SMP", "NETHERITE_SMP" -> '\uE008';
			case "DIAMOND_SMP" -> '\uE009';
			case "CART" -> '\uE00A';
			default -> '\uE00A';
		};
	}

	public static String displayName(String ladder) {
		return switch (HqTiersClientConfig.normalizeLadder(ladder)) {
			case "GLOBAL" -> "Global";
			case "SWORD" -> "Sword";
			case "AXE" -> "Axe";
			case "UHC" -> "UHC";
			case "VANILLA", "CRYSTAL" -> "Vanilla";
			case "MACE" -> "Mace";
			case "SPEAR_MACE", "SPEAR" -> "Spear Mace";
			case "CART" -> "Cart";
			case "DIAMOND_POT" -> "Pot";
			case "NETHERITE_OP" -> "NethOP";
			case "SMP", "NETHERITE_SMP" -> "SMP";
			case "DIAMOND_SMP" -> "DiamondSMP";
			default -> HqTiersClientConfig.normalizeLadder(ladder);
		};
	}

	public static Optional<HqTiersStats.LadderStats> bestLadder(Map<String, HqTiersStats.LadderStats> ladders) {
		return ladders.values().stream()
				.filter(HqTiersStats.LadderStats::hasPlayedRanked)
				.filter(ladder -> !ladder.ladder().equals("GLOBAL"))
				.max(Comparator.comparingInt(HqTiersStats.LadderStats::totalRating));
	}
}