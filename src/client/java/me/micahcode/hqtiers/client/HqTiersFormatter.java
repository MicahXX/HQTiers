package me.micahcode.hqtiers.client;

import me.micahcode.hqtiers.client.leaderboard.HqTiersClientState;
import me.micahcode.hqtiers.client.model.HqTiersRankSystem;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import java.util.*;

public final class HqTiersFormatter {
	private HqTiersFormatter() {
	}

	public static Component compact(HqTiersStats stats) {

		HqTiersStats.LadderStats ladder;

		// use your config system
		if (HqTiersClientConfig.displayMode == HqTiersClientConfig.DisplayMode.GLOBAL) {

			ladder = stats.ladders().get("GLOBAL");

		} else if (HqTiersClientConfig.displayMode == HqTiersClientConfig.DisplayMode.HIGHEST_TIER) {

			ladder = stats.ladders()
					.values()
					.stream()
					.max(Comparator.comparingInt(HqTiersStats.LadderStats::totalRating))
					.orElse(null);

		} else {

			ladder = stats.ladders()
					.get(HqTiersClientConfig.normalizeLadder(
							HqTiersClientConfig.preferredLadder
					));

			if (ladder == null) {
				ladder = stats.ladders()
						.values()
						.stream()
						.filter(l -> !l.ladder().equals("GLOBAL"))
						.findFirst()
						.orElse(null);
			}
		}


		if (ladder == null) {
			return Component.literal("No Data")
					.withStyle(ChatFormatting.RED);
		}


		return decorated(ladder);
	}

	public static Component previewCompact() {
		net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
		if (client != null && client.player != null) {
			HqTiersStats real =
					HqTiersClientState.cache()
							.getIfFresh(client.player.getUUID()).orElse(null);
			if (real != null) return compact(real);
		}
		HqTiersStats.LadderStats fake = HqTiersStats.LadderStats.minimal(
				HqTiersClientConfig.preferredLadder, 800, 10, 5, 10, "MT4", 123
		);
		return decorated(fake);
	}

	public static Component nametag(HqTiersStats stats, Component currentName) {
		Component tier = compact(stats);

		if (tier.getString().isEmpty()) {
			return currentName;
		}

		if (currentName == null) {
			currentName = Component.empty();
		}

		if (HqTiersClientConfig.nametagAlignment ==
				HqTiersClientConfig.NametagAlignment.LEFT) {

			return tier.copy()
					.append(Component.literal(""))
					.append(currentName);

		} else {

			return currentName.copy()
					.append(Component.literal(""))
					.append(tier);
		}
	}

	public static Component hud(HqTiersStats stats) {
		HqTiersStats.LadderStats ladder = stats.displayLadder().orElse(null);

		if (ladder == null) {
			return Component.literal("PvPHQ: Unranked").withStyle(ChatFormatting.GRAY);
		}

		return Component.literal("PvPHQ: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(stats.name()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" "))
				.append(decorated(ladder));
	}

	public static Component details(HqTiersStats stats) {
		return Component.literal("PvPHQ stats for ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(stats.name()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(":").withStyle(ChatFormatting.GRAY));
	}

	public static List<Component> ladderDetails(HqTiersStats stats) {
		List<Component> lines = new ArrayList<>();
		stats.ladder("GLOBAL").ifPresent(global -> lines.add(ladderDetailLine(global)));
		stats.ladders().values().stream()
				.filter(HqTiersStats.LadderStats::hasPlayedRanked)
				.filter(ladder -> !ladder.ladder().equals("GLOBAL"))
				.sorted(Comparator.comparingInt(HqTiersStats.LadderStats::totalRating).reversed())
				.forEach(ladder -> lines.add(ladderDetailLine(ladder)));
		return lines;
	}

	private static Component ladderDetailLine(HqTiersStats.LadderStats ladder) {
		return Component.literal("  ")
				.append(icon(ladder.ladder()))
				.append(Component.literal(" "))
				.append(Component.literal(displayName(ladder.ladder())).withStyle(ChatFormatting.AQUA))
				.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(ladder.tierLabel()).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(ratingText(ladder.totalRating())).setStyle(Style.EMPTY.withColor(ratingColor(ladder.totalRating()))))
				.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(ladder.wins() + "W/" + ladder.losses() + "L").withStyle(ChatFormatting.WHITE))
				.append(positionDetails(ladder));
	}

	private static Component positionDetails(HqTiersStats.LadderStats ladder) {
		if (!ladder.hasPosition()) {
			return Component.empty();
		}

		return Component.literal(" | #").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(Integer.toString(ladder.position())).withStyle(ChatFormatting.WHITE));
	}

	private static Component decorated(HqTiersStats.LadderStats ladder) {
		MutableComponent text = Component.empty();
		boolean wrotePart = false;
		int separatorOccurrence = -1;

		for (HqTiersClientConfig.NametagComponent component : HqTiersClientConfig.nametagOrder) {
			switch (component) {
				case GAMEMODE_ICON -> {
					if (!HqTiersClientConfig.gamemodeIconEnabled) continue;
					if (wrotePart) text.append(Component.literal(" "));
					text.append(icon(ladder.ladder()));
					wrotePart = true;
				}
				case TIER -> {
					if (!HqTiersClientConfig.tierEnabled) continue;
					if (wrotePart) text.append(Component.literal(" "));
					if (HqTiersClientConfig.coloredTier) {
						text.append(Component.literal(tierLabel(ladder)).setStyle(Style.EMPTY.withColor(ladder.tierColorInt())));
					} else {
						text.append(Component.literal(tierLabel(ladder)).withStyle(ChatFormatting.WHITE));
					}
					wrotePart = true;
				}
				case SEPARATOR -> {
					separatorOccurrence++;
					if (!HqTiersClientConfig.isSeparatorEnabled(separatorOccurrence) || !wrotePart) continue;
					text.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
				}
				case ELO -> {
					if (!HqTiersClientConfig.eloEnabled) continue;
					Style eloStyle = Style.EMPTY.withColor(HqTiersClientConfig.coloredElo ? ratingColor(ladder.totalRating()) : 0xFFFFFF);
					text.append(Component.literal(Integer.toString(ladder.totalRating())).setStyle(eloStyle));
					if (HqTiersClientConfig.eloLabelEnabled)
						text.append(Component.literal(" " + HqTiersRankSystem.RATING_LABEL).setStyle(eloStyle));
					wrotePart = true;
				}
				case POSITION -> {
					if (!HqTiersClientConfig.positionEnabled || !ladder.hasPosition()) continue;
					int posColor = HqTiersClientConfig.coloredPosition ? ladder.tierColorInt() : 0xFFFFFF;
					if (HqTiersClientConfig.positionLabelEnabled)
						text.append(Component.literal("#").setStyle(Style.EMPTY.withColor(posColor)));
					text.append(Component.literal(Integer.toString(ladder.position())).setStyle(Style.EMPTY.withColor(posColor)));
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

	public static Component icon(String ladder) {
		return Component.literal(String.valueOf(iconGlyph(ladder)))
				.setStyle(HqTiersMinecraftCompat.fontStyle(Identifier.fromNamespaceAndPath("hqtiers", "default"))
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
			case "SWORD" -> '\uE001';
			case "AXE" -> '\uE002';
			case "VANILLA", "CRYSTAL" -> '\uE003';
			case "UHC" -> '\uE004';
			case "MACE" -> '\uE005';
			case "NETHERITE_POT", "NETHERITE_OP" -> '\uE006';
			case "DIAMOND_POT", "POT" -> '\uE007';
			case "SMP", "NETHERITE_SMP" -> '\uE008';
			case "DIAMOND_SMP" -> '\uE009';
            case "GLOBAL" -> '\uE00A';
            case "SPEAR_MACE" -> '\uE00B';
			case "CART" -> '\uE00C';
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
			case "NETHERITE_POT", "NETHERITE_OP" -> "NethOP";
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