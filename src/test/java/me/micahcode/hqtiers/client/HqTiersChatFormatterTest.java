package me.micahcode.hqtiers.client;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import me.micahcode.hqtiers.client.HqTiersClientConfig.NametagAlignment;
import me.micahcode.hqtiers.client.HqTiersClientConfig.NametagComponent;
import me.micahcode.hqtiers.client.model.HqTiersStats;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HqTiersChatFormatterTest {
    private final GameProfile player = new GameProfile(UUID.randomUUID(), "Player");
    private final GameProfile other = new GameProfile(UUID.randomUUID(), "PlayerTwo");
    private final List<GameProfile> profiles = List.of(player, other);
    private final HqTiersStats stats = new HqTiersStats(HqTiersMinecraftCompat.profileId(player), "Player", Map.of(
            "SWORD", HqTiersStats.LadderStats.minimal("SWORD", 125, 10, 5, 10, "MT4", 123),
            "GLOBAL", HqTiersStats.LadderStats.minimal("GLOBAL", 0, 10, 5, 0, null, 100000)), 0);

    @BeforeAll
    static void initializeMinecraft() throws Exception {
        NametagRegressionTest.initializeLoader();
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @BeforeEach
    void reset() {
        HqTiersClientConfig.chatEnabled = true;
        HqTiersClientConfig.nametagEnabled = false;
        HqTiersClientConfig.tabListEnabled = false;
        HqTiersClientConfig.nametagOrder = HqTiersClientConfig.defaultNametagOrder();
        HqTiersClientConfig.componentSides = new EnumMap<>(NametagComponent.class);
        HqTiersClientConfig.separatorSides = new ArrayList<>();
        HqTiersClientConfig.nametagSeparatorStates = new ArrayList<>(List.of(false, false));
        HqTiersClientConfig.nametagAlignment = NametagAlignment.LEFT;
        HqTiersClientConfig.displayMode = HqTiersClientConfig.DisplayMode.HIGHEST_TIER;
        HqTiersClientConfig.gamemodeIconEnabled = false;
        HqTiersClientConfig.tierEnabled = true;
        HqTiersClientConfig.eloEnabled = false;
        HqTiersClientConfig.positionEnabled = false;
        HqTiersClientConfig.eloLabelEnabled = false;
        HqTiersClientConfig.shortTierNames = false;
        HqTiersClientConfig.showUnranked = false;
        HqTiersClientConfig.nameSeparator = true;
        HqTiersClientConfig.changed();
    }

    private Component decorate(Component message) {
        return ChatMessageDecorator.decorate(message, profiles, uuid -> {
            assertEquals(HqTiersMinecraftCompat.profileId(player), uuid);
            return Optional.of(stats);
        });
    }

    @Test
    void disabledChatPreservesMessagesWithoutLookingUpStats() {
        HqTiersClientConfig.chatEnabled = false;
        var message = Component.literal("Player: hello");
        assertSame(message, HqTiersChatFormatter.decorate(message));
        assertSame(message, ChatMessageDecorator.decorate(message, profiles, uuid -> {
            fail("Disabled chat must not request stats");
            return Optional.empty();
        }));
    }

    @Test
    void vanillaChatDecoratesOnlyTheSenderAndPreservesTheBody() {
        var name = Component.literal("Player").withColor(0x123456);
        var body = Component.literal("Player mentioned PlayerTwo");
        var message = Component.translatable("chat.type.text", name, body);
        var result = decorate(message);
        var args = ((TranslatableContents) result.getContents()).getArgs();
        assertEquals("MT4 | Player", ((Component) args[0]).getString());
        assertTrue(((Component) args[0]).getSiblings().contains(name));
        assertSame(body, args[1]);
        assertSame(name, ((TranslatableContents) message.getContents()).getArgs()[0]);
    }

    @Test
    void serverChatRecognizesCommonSeparatorsAndPreservesTheBody() {
        for (String separator : List.of(":", " »", " ›", " >", " →", " ➜", " ➤", " ➥", " ⏵", " ❯", " |")) {
            var message = Component.literal("[VIP] Player" + separator + " PlayerTwo: hello Player");
            assertEquals("[VIP] MT4 | Player" + separator + " PlayerTwo: hello Player", decorate(message).getString());
            assertEquals("[VIP] Player" + separator + " PlayerTwo: hello Player", message.getString());
        }
        assertEquals("<MT4 | Player> hello", decorate(Component.literal("<Player> hello")).getString());
    }

    @Test
    void serverChatKeepsNameClicksColorsAndConfiguredSides() {
        HqTiersClientConfig.eloEnabled = true;
        HqTiersClientConfig.setSide(NametagComponent.ELO, -1, NametagAlignment.RIGHT);
        var click = new ClickEvent.SuggestCommand("/msg Player ");
        var nameStyle = Style.EMPTY.withColor(0x123456).withClickEvent(click);
        var bodyStyle = Style.EMPTY.withColor(0xABCDEF);
        var message = Component.literal("[VIP] ").withColor(0xFEDCBA)
                .append(Component.literal("Player").setStyle(nameStyle))
                .append(Component.literal(" » hello Player").setStyle(bodyStyle));
        var result = decorate(message);
        assertEquals("[VIP] MT4 | Player | 125 » hello Player", result.getString());
        result.visit((style, text) -> {
            if (text.equals("Player")) assertEquals(nameStyle, style);
            if (text.equals(" » hello Player")) assertEquals(bodyStyle, style);
            return Optional.empty();
        }, Style.EMPTY);
    }

    @Test
    void serverAnnouncementsAndNamesMentionedInBodiesStayUntouched() {
        for (String text : List.of("Player joined the game", "PlayerThree: hello", "Server: Player: hello",
                "Unknown » Player: hello", "Announcement\nPlayer: hello")) {
            var message = Component.literal(text);
            assertSame(message, decorate(message));
        }
    }

    @Test
    void firstMessageCanBeRedrawnAfterStatsArriveWithoutMutatingHistory() {
        var message = Component.literal("Player: hello");
        var loaded = new AtomicReference<HqTiersStats>();
        assertSame(message, ChatMessageDecorator.decorate(message, profiles, uuid -> Optional.ofNullable(loaded.get())));
        loaded.set(stats);
        assertEquals("MT4 | Player: hello", ChatMessageDecorator.decorate(
                message, profiles, uuid -> Optional.ofNullable(loaded.get())).getString());
        HqTiersClientConfig.chatEnabled = false;
        assertSame(message, decorate(message));
        assertEquals("Player: hello", message.getString());
    }

    @Test
    void globalPlaceholderStaysHiddenInChatAndTabNameFormatting() {
        HqTiersClientConfig.displayMode = HqTiersClientConfig.DisplayMode.GLOBAL;
        HqTiersClientConfig.changed();
        var message = Component.literal("Player: hello");
        assertSame(message, decorate(message));
        assertEquals("", HqTiersFormatter.compact(stats).getString());
        assertEquals("Player", HqTiersFormatter.decorateName(stats, Component.literal("Player")).getString());
    }

    @Test
    void teamChatAndWrappedTranslationsKeepOtherArguments() {
        var team = Component.literal("Team");
        var body = Component.literal("PlayerTwo: hello");
        var message = Component.translatable("chat.type.team.text", team, Component.literal("Player"), body);
        var result = decorate(message);
        var args = ((TranslatableContents) result.getContents()).getArgs();
        assertSame(team, args[0]);
        assertEquals("MT4 | Player", ((Component) args[1]).getString());
        assertSame(body, args[2]);
        var wrapper = Component.empty().withColor(0xABCDEF).append(message);
        var decoratedWrapper = decorate(wrapper);
        assertEquals(wrapper.getStyle(), decoratedWrapper.getStyle());
        assertEquals(result, decoratedWrapper.getSiblings().getFirst());
    }

    @Test
    void settingsChangesRequestOneChatRefresh() {
        HqTiersChatFormatter.needsRefresh();
        assertFalse(HqTiersChatFormatter.needsRefresh());
        HqTiersClientConfig.chatEnabled = false;
        HqTiersClientConfig.changed();
        assertTrue(HqTiersChatFormatter.needsRefresh());
        assertFalse(HqTiersChatFormatter.needsRefresh());
    }
}
