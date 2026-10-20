package com.wormhole_xtreme.wormhole.command;

import static com.wormhole_xtreme.wormhole.utils.ChatText.bad;
import static com.wormhole_xtreme.wormhole.utils.ChatText.error;
import static com.wormhole_xtreme.wormhole.utils.ChatText.good;
import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static com.wormhole_xtreme.wormhole.utils.ChatText.material;
import static com.wormhole_xtreme.wormhole.utils.ChatText.name;
import static com.wormhole_xtreme.wormhole.utils.ChatText.value;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.GateInteractionHandler;
import com.wormhole_xtreme.wormhole.command.handlers.RegenerateCommand;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.preview.BuildGuide;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What the preview's {@code place}, {@code needs}, {@code layer}, {@code clear}, {@code share} and
 * {@code material} say, read by key from {@code lang/} (#26).
 *
 * <p>Every line goes through the real command to an English builder, against the text the code built
 * before the move, copied from it, and to a builder whose client reads the test language qq. Each
 * conditional has its variants: every place outcome, a list of what is in the way empty, of one and of
 * many, a layer and layers, a count of one and of none, and who sees a preview nobody, everyone,
 * somebody and both.
 */
class BuildPreviewMessagesTest extends BuildPreviewCase
{
    private static final String ALEX = "Alex";
    private static final String GUIDE = "guide";
    private static final String LITHIUM = "Lithium";
    private static final String SHARE_ALL = "share -all";

    private Stargate gate()
    {
        final Stargate gate = mock(Stargate.class);
        when(gate.getGateShape()).thenReturn(standard);
        when(gate.getGateName()).thenReturn(LITHIUM);
        return gate;
    }

    private static GatePreviews.Placed placed(final GatePreviews.Outcome outcome, final String... inTheWay)
    {
        return new GatePreviews.Placed(outcome, List.of(inTheWay), null, null);
    }

    /** Runs {@code place} once for each outcome, in the order they are listed in the tests below. */
    private void placeEach(final Player player)
    {
        final Stargate gate = gate();
        final Block button = mock(Block.class);
        final GatePreviews.Placed[] answers = {
            placed(GatePreviews.Outcome.NOT_FINDABLE), placed(GatePreviews.Outcome.NOT_LOADED),
            placed(GatePreviews.Outcome.OUTSIDE_BORDER), placed(GatePreviews.Outcome.TOO_CLOSE, "near"),
            placed(GatePreviews.Outcome.SHAPE_OFF),
            placed(GatePreviews.Outcome.IN_THE_WAY, "stone at 1 2 3", "4 more"),
            placed(GatePreviews.Outcome.IN_THE_WAY, "dirt"), placed(GatePreviews.Outcome.IN_THE_WAY),
            placed(GatePreviews.Outcome.NOT_FOUND),
            new GatePreviews.Placed(GatePreviews.Outcome.PLACED, List.of(), gate, button),
            new GatePreviews.Placed(GatePreviews.Outcome.REPAIRED, List.of(), gate, button)};
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class);
            MockedStatic<RegenerateCommand> regen = mockStatic(RegenerateCommand.class);
            MockedStatic<GateInteractionHandler> handler = mockStatic(GateInteractionHandler.class))
        {
            previews.when(() -> GatePreviews.place(any(), anyBoolean())).thenReturn(answers[0],
                Arrays.copyOfRange(answers, 1, answers.length));
            for (int i = 0; i < answers.length; i++)
            {
                run(player, "gate", "preview", "place");
            }
        }
    }

    /** Each outcome of placing, with the in-the-way list empty, one and many, as before. */
    @Test
    void thePlaceOutcomesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        placeEach(player);

        verify(player).sendMessage(error("No material group uses that frame block, so the gate would not be found. "
            + "Nothing placed."));
        verify(player).sendMessage(error("Part of it is in an unloaded chunk. Move closer. Nothing placed."));
        verify(player).sendMessage(error("Part of it is outside the world border. Nothing placed."));
        verify(player).sendMessage(error("near Nothing placed."));
        verify(player).sendMessage(error("That shape is turned off on this server. Nothing placed."));
        verify(player).sendMessage(error("Nothing placed. In the way: " + bad("stone at 1 2 3, 4 more") + ". ")
            + hint(GUIDE) + " marks them.");
        verify(player).sendMessage(error("Nothing placed. In the way: " + bad("dirt") + ". ") + hint(GUIDE) + " marks them.");
        verify(player).sendMessage(error("Nothing placed. In the way: " + bad("") + ". ") + hint(GUIDE) + " marks them.");
        verify(player).sendMessage(error("Placed, but no gate was found in it. ") + hint(GUIDE) + " shows what is wrong.");
        verify(player).sendMessage(info(good("Placed Standard.")));
        verify(player).sendMessage(info(good("Filled in " + LITHIUM + "'s missing blocks.") + " Regenerating it."));
    }

    /** The same lines reach a qq builder in qq, the names in the way joined as qq joins a list. */
    @Test
    void thePlaceOutcomesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        placeEach(player);

        verify(player).sendMessage(error("QQ not findable."));
        verify(player).sendMessage(error("QQ not loaded."));
        verify(player).sendMessage(error("QQ border."));
        verify(player).sendMessage(error("QQ near too close."));
        verify(player).sendMessage(error("QQ shape off."));
        verify(player).sendMessage(error("QQ in the way " + bad("stone at 1 2 3 | 4 more") + " ") + hint(GUIDE) + " QQ marks.");
        verify(player).sendMessage(error("QQ in the way " + bad("dirt") + " ") + hint(GUIDE) + " QQ marks.");
        verify(player).sendMessage(error("QQ in the way " + bad("") + " ") + hint(GUIDE) + " QQ marks.");
        verify(player).sendMessage(error("QQ not found ") + hint(GUIDE) + " QQ wrong.");
        verify(player).sendMessage(info(good("QQ placed Standard.")));
        verify(player).sendMessage(info(good("QQ filled " + LITHIUM + ".") + " QQ regen."));
    }

    /** A full list, then one with nothing to place, nothing in the way and a frame some group finds. */
    private void needsTwice(final Player player)
    {
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.materials(any())).thenReturn(
                new GatePreviews.Materials("Standard", List.of(new BuildGuide.Need("obsidian", 18, 4),
                    new BuildGuide.Need("button or lever", 1, 0)), 2, false, Material.GOLD_BLOCK),
                new GatePreviews.Materials("Standard", List.of(), 0, true, Material.GOLD_BLOCK));
            run(player, "gate", "preview", "needs");
            run(player, "gate", "preview", "needs");
        }
    }

    /** The needs list as it read: a header, a line each, then what is in the way and a frame no group finds. */
    @Test
    void theNeedsListReadsAsItDidBefore()
    {
        final Player player = reading("en");
        needsTwice(player);

        verify(player, times(2)).sendMessage(info(name("Standard") + " needs:"));
        verify(player).sendMessage(info("  " + value("18") + " " + material("obsidian") + " - " + value("4") + " left"));
        verify(player).sendMessage(info("  " + value("1") + " " + material("button or lever") + " - " + good("done")));
        verify(player).sendMessage(info("  " + bad("2 in the way") + " in the opening"));
        verify(player).sendMessage(error("No material group uses " + material("gold_block")
            + " for a frame, so this gate would not be found."));
    }

    /** The same lines reach a qq builder in qq. */
    @Test
    void theNeedsListIsInTheReadersLanguage()
    {
        final Player player = reading("qq");
        needsTwice(player);

        verify(player, times(2)).sendMessage(info("QQ " + name("Standard") + " needs"));
        verify(player).sendMessage(info("QQ " + value("18") + " " + material("obsidian") + " = QQ" + value("4") + "left"));
        verify(player).sendMessage(info("QQ " + value("1") + " " + material("button or lever") + " = " + good("QQdone")));
        verify(player).sendMessage(info("QQ " + bad("QQ2blocked") + " opening"));
        verify(player).sendMessage(error("QQ no group " + material("gold_block") + "."));
    }

    /** Layer 1, layers 1-3, all of them, more than there are, one layer only and none. */
    private void layersEach(final Player player)
    {
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.layers(any(), eq(GatePreviews.NEXT_LAYER)))
                .thenReturn(new GatePreviews.Layers(1, 4, true), new GatePreviews.Layers(3, 4, true));
            previews.when(() -> GatePreviews.layers(any(), eq(GatePreviews.ALL_LAYERS)))
                .thenReturn(new GatePreviews.Layers(GatePreviews.ALL_LAYERS, 4, true));
            previews.when(() -> GatePreviews.layers(any(), eq(9))).thenReturn(new GatePreviews.Layers(3, 4, false));
            previews.when(() -> GatePreviews.layers(any(), eq(5))).thenReturn(new GatePreviews.Layers(1, 1, false));
            previews.when(() -> GatePreviews.layers(any(), eq(7))).thenReturn(new GatePreviews.Layers(0, 0, false));
            run(player, "gate", "preview", "layer");
            run(player, "gate", "preview", "layer");
            run(player, "gate", "preview", "layer", "-all");
            run(player, "gate", "preview", "layer", "9");
            run(player, "gate", "preview", "layer", "5");
            run(player, "gate", "preview", "layer", "7");
        }
    }

    /** The layer lines as before, and "layer" singular only for one. */
    @Test
    void theLayerLinesReadAsTheyDidBeforeAndPluralOnlyAwayFromOne()
    {
        final Player player = reading("en");
        layersEach(player);

        verify(player).sendMessage(info("Showing layer " + value("1") + " of " + value("4") + ". " + hint("layer")
            + " for the next."));
        verify(player).sendMessage(info("Showing layer " + value("1-3") + " of " + value("4") + ". " + hint("layer")
            + " for the next."));
        verify(player).sendMessage(info("Showing all " + value("4") + " layers."));
        verify(player).sendMessage(error("It has only " + value("4") + " layers."));
        verify(player).sendMessage(error("It has only " + value("1") + " layer."));
        verify(player).sendMessage(error("It has only " + value("0") + " layers."));
    }

    /** The same lines reach a qq builder in qq, with its own singular and plural. */
    @Test
    void theLayerLinesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        layersEach(player);

        verify(player).sendMessage(info("QQ layer " + value("1") + " of " + value("4") + " " + hint("layer") + "."));
        verify(player).sendMessage(info("QQ layer " + value("1-3") + " of " + value("4") + " " + hint("layer") + "."));
        verify(player).sendMessage(info("QQ all " + value("4") + "."));
        verify(player).sendMessage(error("QQ only " + value("4") + " other."));
        verify(player).sendMessage(error("QQ only " + value("1") + " one."));
        verify(player).sendMessage(error("QQ only " + value("0") + " other."));
    }

    /** One preview, three, none; then the one looked at; then nothing looked at. */
    private void clearEach(final Player player)
    {
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.clearAll(any())).thenReturn(1, 3, 0);
            previews.when(() -> GatePreviews.clearLookedAt(any())).thenReturn(true, false);
            previews.when(() -> GatePreviews.countOf(any(UUID.class))).thenReturn(2);
            run(player, "gate", "preview", "clear", "-all");
            run(player, "gate", "preview", "clear", "-all");
            run(player, "gate", "preview", "clear", "-all");
            run(player, "gate", "preview", "clear");
            run(player, "gate", "preview", "clear");
        }
    }

    /** The clear lines as before; a count of one is the only singular. */
    @Test
    void theClearLinesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        clearEach(player);

        verify(player).sendMessage(info("Cleared " + value("1") + " preview."));
        verify(player).sendMessage(info("Cleared " + value("3") + " previews."));
        verify(player).sendMessage(info("Cleared " + value("0") + " previews."));
        verify(player).sendMessage(info("Cleared. " + value("2") + " left."));
        verify(player).sendMessage(error("Look at a preview to clear it, or use ") + hint("clear -all") + ".");
    }

    /** The same lines reach a qq builder in qq. */
    @Test
    void theClearLinesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        clearEach(player);

        verify(player).sendMessage(info("QQ cleared " + value("1") + " one."));
        verify(player).sendMessage(info("QQ cleared " + value("3") + " other."));
        verify(player).sendMessage(info("QQ cleared " + value("0") + " other."));
        verify(player).sendMessage(info("QQ cleared " + value("2") + " left."));
        verify(player).sendMessage(error("QQ look to clear ") + hint("clear -all") + ".");
    }

    private static Player named(final String locale, final String playerName)
    {
        final Player other = mock(Player.class);
        when(other.getLocale()).thenReturn(locale);
        when(other.getName()).thenReturn(playerName);
        return other;
    }

    /** Who sees it four ways, then shares with Alex, stops, is told they already do, and shares and stops for all. */
    private void shareEach(final Player player, final Player alex)
    {
        final Server server = mock(Server.class);
        when(player.getServer()).thenReturn(server);
        when(server.getPlayerExact(ALEX)).thenReturn(alex);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.audience(any())).thenReturn(
                new GatePreviews.Audience(false, List.of()), new GatePreviews.Audience(true, List.of(ALEX)),
                new GatePreviews.Audience(false, List.of(ALEX, "Bea")), new GatePreviews.Audience(true, List.of()));
            previews.when(() -> GatePreviews.share(any(), any())).thenReturn(GatePreviews.Shared.SHARED,
                GatePreviews.Shared.UNSHARED, GatePreviews.Shared.SELF);
            previews.when(() -> GatePreviews.shareAll(any())).thenReturn(GatePreviews.Shared.SHARED_ALL,
                GatePreviews.Shared.UNSHARED_ALL);
            for (int i = 0; i < 4; i++)
            {
                run(player, "gate", "preview", "share");
            }
            for (int i = 0; i < 3; i++)
            {
                run(player, "gate", "preview", "share", ALEX);
            }
            run(player, "gate", "preview", "share", "-all");
            run(player, "gate", "preview", "share", "-all");
            run(player, "gate", "preview", "share", "Nobody");
        }
    }

    /** Who sees the preview and each share reply as before, and the player shared with is told. */
    @Test
    void theShareLinesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        final Player alex = named("en", ALEX);
        shareEach(player, alex);

        verify(player).sendMessage(info("Only you see it. " + hint("share <player>") + " or " + hint(SHARE_ALL)
            + " shows it."));
        verify(player).sendMessage(info("Shown to everyone in this world, " + name(ALEX) + "."));
        verify(player).sendMessage(info("Shown to " + name(ALEX) + ", " + name("Bea") + "."));
        verify(player).sendMessage(info("Shown to everyone in this world."));
        verify(player).sendMessage(info("Showing it to " + name(ALEX) + ". " + hint("share " + ALEX) + " again stops."));
        verify(alex).sendMessage(info(name("builder") + " is showing you a gate preview."));
        verify(player).sendMessage(info("Stopped showing it to " + name(ALEX) + "."));
        verify(player).sendMessage(error("You already see it."));
        verify(player).sendMessage(info("Showing it to everyone in this world. " + hint(SHARE_ALL) + " again stops."));
        verify(player).sendMessage(info("Stopped showing it to everyone."));
        verify(player).sendMessage(error("No player called " + name("Nobody") + " is online."));
    }

    /** The same lines reach a qq builder in qq, who is shown to joined as qq joins a list. */
    @Test
    void theShareLinesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        final Player alex = named("qq", ALEX);
        shareEach(player, alex);

        verify(player).sendMessage(info("QQ only you " + hint("share <player>") + " " + hint(SHARE_ALL) + "."));
        verify(player).sendMessage(info("QQ shown QQeveryone | " + name(ALEX) + "."));
        verify(player).sendMessage(info("QQ shown " + name(ALEX) + " | " + name("Bea") + "."));
        verify(player).sendMessage(info("QQ shown QQeveryone."));
        verify(player).sendMessage(info("QQ started " + name(ALEX) + " " + hint("share " + ALEX) + "."));
        verify(alex).sendMessage(info("QQ " + name("builder") + " shows you."));
        verify(player).sendMessage(info("QQ stopped " + name(ALEX) + "."));
        verify(player).sendMessage(error("QQ self."));
        verify(player).sendMessage(info("QQ started all " + hint(SHARE_ALL) + "."));
        verify(player).sendMessage(info("QQ stopped all."));
        verify(player).sendMessage(error("QQ no player " + name("Nobody") + "."));
    }

    /** The one told that a preview is shown to them reads it in their own language, not the sharer's. */
    @Test
    void theNoticeToThePlayerSharedWithIsInTheirLanguageNotTheSharers()
    {
        final Player english = reading("en");
        final Player qq = named("qq", ALEX);
        final Server server = mock(Server.class);
        when(english.getServer()).thenReturn(server);
        when(server.getPlayerExact(ALEX)).thenReturn(qq);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.share(any(), any())).thenReturn(GatePreviews.Shared.SHARED);
            run(english, "gate", "preview", "share", ALEX);
        }

        verify(qq).sendMessage(info("QQ " + name("builder") + " shows you."));
        verify(english).sendMessage(info("Showing it to " + name(ALEX) + ". " + hint("share " + ALEX) + " again stops."));
    }

    /** What {@code material} takes, with the roles in a list. */
    @Test
    void theMaterialUsageReadsAsItDidBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qq = reading("qq");
        final List<String> roles = Arrays.stream(GateBlueprint.Role.values()).map(GateBlueprint.Role::option)
            .map(ChatText::command).toList();
        run(english, "gate", "preview", "material", "sparkly");
        run(qq, "gate", "preview", "material", "sparkly");

        verify(english).sendMessage(info("Usage: ") + hint("material <group>") + " or "
            + hint("material -<role> <block>") + ". Roles: " + roles.stream().collect(Collectors.joining(", ")) + ".");
        verify(qq).sendMessage(info("Usage: ") + hint("material <group>") + " QQor "
            + hint("material -<role> <block>") + " QQroles " + String.join(" | ", roles) + ".");
    }
}
