package me.micahcode.hqtiers.client.model;

import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.Locale;

public final class PvphqKit {
    private static HqTiersLadder current;

    public static HqTiersLadder current() {
        return current;
    }

    public static void update(Minecraft client) {
        var server = client.getCurrentServer();
        HqTiersLadder kit = null;
        if (HqTiersClientConfig.displayMode == HqTiersClientConfig.DisplayMode.CURRENT_KIT
                && client.player != null && !client.player.isSpectator() && server != null && isPvphq(server.ip)) {
            kit = detect(client.player);
        }
        if (kit != current) {
            current = kit;
            HqTiersClientConfig.changed();
        }
    }

    static boolean isPvphq(String address) {
        String host = ServerAddress.parseString(address).getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.equals("pvphq.com") || host.endsWith(".pvphq.com");
    }

    static HqTiersLadder detect(Player player) {
        var chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean diamond = chest.is(Items.DIAMOND_CHESTPLATE);
        boolean netherite = chest.is(Items.NETHERITE_CHESTPLATE);
        if (chest.isEmpty()) return null;

        var bag = player.getInventory();
        boolean crystals = bag.countItem(Items.END_CRYSTAL) > 0;
        boolean cart = bag.countItem(Items.TNT_MINECART) > 0;
        boolean mace = bag.countItem(Items.MACE) > 0;
        boolean spear = bag.contains(ItemTags.SPEARS);
        if ((crystals && cart) || ((crystals || cart) && (mace || spear))) return null;
        if (crystals) return HqTiersLadder.VANILLA;
        if (cart) return HqTiersLadder.CART;
        if (spear) return HqTiersLadder.SPEAR_MACE;
        if (mace) return HqTiersLadder.MACE;

        boolean sword = bag.contains(ItemTags.SWORDS);
        boolean axe = bag.contains(ItemTags.AXES);
        boolean bow = bag.countItem(Items.BOW) > 0 || bag.countItem(Items.CROSSBOW) > 0;
        boolean shield = bag.countItem(Items.SHIELD) > 0;
        boolean lava = bag.countItem(Items.LAVA_BUCKET) > 0;
        boolean water = bag.countItem(Items.WATER_BUCKET) > 0 || bag.countItem(Items.BUCKET) > 0;
        boolean web = bag.countItem(Items.COBWEB) > 0;
        boolean wood = bag.contains(ItemTags.PLANKS) || bag.contains(ItemTags.LOGS);
        boolean healing = bag.countItem(Items.SPLASH_POTION) > 0;
        boolean pearls = bag.countItem(Items.ENDER_PEARL) > 0;
        boolean gapples = bag.countItem(Items.GOLDEN_APPLE) > 0 || bag.countItem(Items.ENCHANTED_GOLDEN_APPLE) > 0;
        if (bag.contains(ItemTags.RAILS) && bag.countItem(Items.FLINT_AND_STEEL) > 0) return HqTiersLadder.CART;
        if (bag.countItem(Items.RESPAWN_ANCHOR) > 0 && bag.countItem(Items.GLOWSTONE) > 0) return HqTiersLadder.VANILLA;
        if (diamond && !healing && (lava || (bow && water && (wood || web))) && (sword || axe)) return HqTiersLadder.UHC;
        if ((diamond || netherite) && sword) {
            if ((wood || web) && (healing || (water && axe) || pearls)) {
                return diamond ? HqTiersLadder.DIAMOND_SMP : HqTiersLadder.SMP;
            }
            if (healing && !wood && !web && !water && !lava) {
                return diamond ? HqTiersLadder.DIAMOND_POT : HqTiersLadder.NETHERITE_POT;
            }
        }
        if (axe && shield && bow && !wood && !web && !water && !lava && !healing) return HqTiersLadder.AXE;
        if (sword && !gapples && !axe && !bow && !shield && !healing && !pearls && !wood && !web && !water && !lava
                && bag.countItem(Items.TOTEM_OF_UNDYING) == 0 && !bag.contains(ItemTags.PICKAXES)) return HqTiersLadder.SWORD;
        return null;
    }
}
