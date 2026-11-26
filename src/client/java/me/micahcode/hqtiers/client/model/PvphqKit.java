package me.micahcode.hqtiers.client.model;

import me.micahcode.hqtiers.client.config.HqTiersClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Locale;

import static me.micahcode.hqtiers.client.model.PvphqKit.Gear.*;

public final class PvphqKit {
    private static final Tracker tracker = new Tracker();

    private static final long ROUND_GRACE_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    private static ClientPacketListener connection;
    private static HqTiersLadder displayed;
    private static long lastSeen;

    public static HqTiersLadder current() {
        return displayed;
    }

    public static void update(Minecraft client) {
        var server = client.getCurrentServer();
        var player = client.player;
        var previous = displayed;
        var activeConnection = client.getConnection();
        boolean enabled = HqTiersClientConfig.displayMode == HqTiersClientConfig.DisplayMode.CURRENT_KIT
                && activeConnection != null && server != null && isPvphq(server.ip);
        if (!enabled || connection != activeConnection) {
            tracker.observe(null);
            displayed = null;
        }
        connection = enabled ? activeConnection : null;
        if (enabled) {
            var kit = tracker.observe(player != null && player.isAlive() && !player.isSpectator() ? player : null);
            long now = System.nanoTime();
            if (kit != null) {
                displayed = kit;
                lastSeen = now;
            } else if (now - lastSeen >= ROUND_GRACE_NANOS) {
                displayed = null;
            }
        }
        if (previous != displayed) HqTiersClientConfig.changed();
    }

    static boolean isPvphq(String address) {
        String host = ServerAddress.parseString(address).getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.equals("pvphq.com") || host.endsWith(".pvphq.com");
    }

    enum Gear {
        SWORD, AXE, BOW, SHIELD, LAVA, BUCKET, WEB, WOOD, POTION, PEARL, APPLE, TOTEM, PICK,
        CRYSTAL, CART, MACE, SPEAR, RAIL, FLINT, ANCHOR, GLOWSTONE
    }

    private static Gear gear(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.is(ItemTags.SWORDS)) return SWORD;
        if (stack.is(ItemTags.AXES)) return AXE;
        if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) return BOW;
        if (stack.is(Items.SHIELD)) return SHIELD;
        if (stack.is(Items.LAVA_BUCKET)) return LAVA;
        if (stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET)) return BUCKET;
        if (stack.is(Items.COBWEB)) return WEB;
        if (stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS)) return WOOD;
        if (stack.is(Items.SPLASH_POTION)) return POTION;
        if (stack.is(Items.ENDER_PEARL)) return PEARL;
        if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return APPLE;
        if (stack.is(Items.TOTEM_OF_UNDYING)) return TOTEM;
        if (stack.is(ItemTags.PICKAXES)) return PICK;
        if (stack.is(Items.END_CRYSTAL)) return CRYSTAL;
        if (stack.is(Items.TNT_MINECART)) return CART;
        if (stack.is(Items.MACE)) return MACE;
        if (stack.is(ItemTags.SPEARS)) return SPEAR;
        if (stack.is(ItemTags.RAILS)) return RAIL;
        if (stack.is(Items.FLINT_AND_STEEL)) return FLINT;
        if (stack.is(Items.RESPAWN_ANCHOR)) return ANCHOR;
        if (stack.is(Items.GLOWSTONE)) return GLOWSTONE;
        return null;
    }

    private record Loadout(ItemStack chest, ItemStack sword, EnumSet<Gear> items) {
        static Loadout read(Player player) {
            var items = EnumSet.noneOf(Gear.class);
            var sword = ItemStack.EMPTY;
            int swords = 0;
            var bag = player.getInventory();
            for (int i = 0; i < bag.getContainerSize(); i++) {
                var stack = bag.getItem(i);
                var kind = gear(stack);
                if (kind == null) continue;
                items.add(kind);
                if (kind == SWORD) {
                    sword = stack;
                    swords++;
                }
            }
            return new Loadout(player.getItemBySlot(EquipmentSlot.CHEST), swords == 1 ? sword : ItemStack.EMPTY, items);
        }

        boolean hasWeapon() {
            return items.contains(SWORD) || items.contains(AXE) || items.contains(BOW) || items.contains(MACE) || items.contains(SPEAR)
                    || items.contains(CRYSTAL) || items.contains(CART);
        }

        boolean conflicting() {
            boolean vanilla = items.contains(CRYSTAL) || items.contains(ANCHOR) && items.contains(GLOWSTONE);
            boolean cart = items.contains(CART) || items.contains(RAIL) && items.contains(FLINT);
            return vanilla && cart || (vanilla || cart) && (items.contains(MACE) || items.contains(SPEAR));
        }

        HqTiersLadder detect() {
            boolean diamond = chest.is(Items.DIAMOND_CHESTPLATE);
            boolean netherite = chest.is(Items.NETHERITE_CHESTPLATE);
            if ((!diamond && !netherite && !chest.is(Items.ELYTRA)) || conflicting()) return null;
            if (items.contains(CRYSTAL) || items.contains(ANCHOR) && items.contains(GLOWSTONE)) return HqTiersLadder.VANILLA;
            if (items.contains(CART) || items.contains(RAIL) && items.contains(FLINT)) return HqTiersLadder.CART;
            if (items.contains(SPEAR)) return HqTiersLadder.SPEAR_MACE;
            if (items.contains(MACE)) return HqTiersLadder.MACE;

            boolean melee = items.contains(SWORD) || items.contains(AXE);
            boolean building = items.contains(WOOD) || items.contains(WEB);
            boolean buckets = items.contains(BUCKET) || items.contains(LAVA);
            boolean pots = items.contains(POTION);
            if (diamond && melee && !pots && (items.contains(LAVA) || items.contains(BOW)
                    && (buckets && (building || items.contains(PICK) || items.contains(APPLE)) || building && items.contains(PICK)))) {
                return HqTiersLadder.UHC;
            }
            if ((diamond || netherite) && items.contains(SWORD)) {
                if (building && (pots || buckets && items.contains(AXE) || items.contains(PEARL))) {
                    return diamond ? HqTiersLadder.DIAMOND_SMP : HqTiersLadder.SMP;
                }
                if (pots && !building && !buckets) return diamond ? HqTiersLadder.DIAMOND_POT : HqTiersLadder.NETHERITE_POT;
            }
            if (diamond && items.contains(AXE) && items.contains(SHIELD) && items.contains(BOW) && !building && !buckets && !pots) {
                return HqTiersLadder.AXE;
            }
            if (diamond && items.size() == 1 && items.contains(SWORD)) return HqTiersLadder.SWORD;
            return null;
        }
    }

    static final class Tracker {
        private Player player;
        private HqTiersLadder current;
        private HqTiersLadder pending;
        private int invalidSamples;
        private final EnumSet<Gear> known = EnumSet.noneOf(Gear.class);
        private ItemStack lastChest = ItemStack.EMPTY;
        private ItemStack lastSword = ItemStack.EMPTY;

        HqTiersLadder observe(Player next) {
            if (next != player) {
                clear();
                player = next;
            }
            if (next == null) return null;

            var kit = Loadout.read(next);
            var detected = kit.detect();
            boolean unarmored = kit.chest().isEmpty() && next.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                    && next.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && next.getItemBySlot(EquipmentSlot.FEET).isEmpty();
            if (unarmored && !kit.hasWeapon()) {
                clear();
                return null;
            }
            boolean otherArmor = !kit.chest().isEmpty() && !kit.chest().is(Items.DIAMOND_CHESTPLATE)
                    && !kit.chest().is(Items.NETHERITE_CHESTPLATE) && !kit.chest().is(Items.ELYTRA);
            if (unarmored || otherArmor || kit.conflicting() || kit.items().isEmpty()) {
                pending = null;
                if (++invalidSamples >= 2) clear();
                return current;
            }
            invalidSamples = 0;
            boolean reequipped = changedGear(lastChest, kit.chest()) || changedGear(lastSword, kit.sword())
                    || repaired(lastChest, kit.chest()) && repaired(lastSword, kit.sword());
            if (reequipped && detected != current) clear();
            if (!kit.chest().isEmpty()) lastChest = kit.chest().copy();
            if (!kit.sword().isEmpty()) lastSword = kit.sword().copy();

            if (detected == null) {
                pending = null;
            } else if (detected == current) {
                remember(kit.items());
                pending = null;
            } else if (current != null && explainedByDepletion(kit, detected)) {
                pending = null;
            } else if (detected == pending) {
                current = detected;
                known.clear();
                remember(kit.items());
                pending = null;
            } else {
                pending = detected;
            }
            return current;
        }

        private boolean explainedByDepletion(Loadout kit, HqTiersLadder detected) {
            if (known.containsAll(kit.items())) return true;
            var remaining = kit.items().clone();
            remaining.retainAll(known);
            return new Loadout(kit.chest(), kit.sword(), remaining).detect() == detected;
        }

        private void remember(EnumSet<Gear> items) {
            known.addAll(items);
            if (items.contains(LAVA)) known.add(BUCKET);
        }

        private static boolean changedGear(ItemStack before, ItemStack after) {
            return !before.isEmpty() && !after.isEmpty()
                    && (!ItemStack.isSameItem(before, after) || !before.getEnchantments().equals(after.getEnchantments()));
        }

        private static boolean repaired(ItemStack before, ItemStack after) {
            return !before.isEmpty() && !after.isEmpty() && before.getDamageValue() > 0 && after.getDamageValue() == 0;
        }

        private void clear() {
            current = null;
            pending = null;
            known.clear();
            invalidSamples = 0;
            lastChest = ItemStack.EMPTY;
            lastSword = ItemStack.EMPTY;
        }
    }
}
