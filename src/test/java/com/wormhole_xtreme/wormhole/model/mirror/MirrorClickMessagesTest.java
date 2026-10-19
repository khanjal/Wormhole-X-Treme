package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.window.BlockPlace;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.utils.ActionBar;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What a click on a mirror says, read by key from {@code lang/} (#26).
 *
 * <p>Each line goes through {@link MirrorInteraction#handle} to an English player and is held to the
 * concatenation the code built before the move, copied from it, so extraction never rewords; then to a
 * {@code qq} player, so a call site that reads the wrong recipient, or none, fails here. The right-click
 * answers are built in {@link MirrorNetwork#scroll}, so its reader is checked the same way. {@link ActionBar}
 * is mocked: the real one goes through {@code player.spigot()} or Adventure, which a bare mock cannot answer.
 */
class MirrorClickMessagesTest
{
    private World world;

    private long now = 1_000_000L;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.clear();
        MirrorManager.clear();
        MirrorSettle.clear();
        MirrorNetwork.clear();
        MirrorNetwork.clock = () -> now;
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorSignpost.clear();
        MirrorManager.clear();
        MirrorSettle.clear();
        MirrorNetwork.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** A player whose client reads this language, who may use and configure mirrors. */
    private static Player playerReading(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOp()).thenReturn(true);
        when(player.getLocale()).thenReturn(locale);
        return player;
    }

    private Block banner(final int x)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(0);
        return block;
    }

    /** A mirror on this banner storing its own room, as a new mirror does. */
    private static void reflecting(final String name, final Block banner)
    {
        MirrorManager.add(new QuantumMirror(name, BlockPlace.of(banner),
            new Place("world", banner.getX() + 0.5, 63, 0.5, 180f, 0f)));
    }

    private static void punch(final Player player, final Block block)
    {
        assertTrue(MirrorInteraction.handle(new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, null, block,
            BlockFace.UP)), "a mirror claims its click");
    }

    private static void click(final Player player, final Block block)
    {
        assertTrue(MirrorInteraction.handle(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block,
            BlockFace.UP)), "a mirror claims its click");
    }

    /** Punches a mirror into a loaded world named {@code museum_world}. */
    private static void punchIntoMuseumWorld(final Player player, final Block block)
    {
        final World destination = mock(World.class);
        when(destination.getName()).thenReturn("museum_world");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("museum_world")).thenReturn(destination);
            punch(player, block);
        }
    }

    /** Punches a mirror whose far world is not loaded. */
    private static void punchIntoUnloadedWorld(final Player player, final Block block)
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("a_world_nobody_started")).thenReturn(null);
            punch(player, block);
        }
    }

    /** Somebody besides the clicker stands at the banner at x = 5, so the choice holds. */
    private void somebodyElseAtTheBanner()
    {
        final Player other = mock(Player.class);
        when(other.getLocation()).thenReturn(new Location(world, 5.5, 64.0, 1.5));
        when(world.getPlayers()).thenReturn(List.of(other));
    }

    /** The two punch answers above the hotbar, against the old expressions. */
    @Test
    void thePunchHintsReadAsTheyDidBefore()
    {
        final Player player = playerReading("en");
        final Block here = banner(5);
        reflecting("Museum", here);
        try (MockedStatic<ActionBar> bar = mockStatic(ActionBar.class))
        {
            punch(player, here);
            MirrorSettle.arrived(player);
            punch(player, here);

            bar.verify(() -> ActionBar.send(player, ChatText.status(MirrorText.quoted("Museum")
                + " is showing its own room. Right-click it" + " to choose another mirror.")));
            bar.verify(() -> ActionBar.send(player, ChatText.status("Mirrors settle for a moment after one puts"
                + " you down. Step away" + " from the banner and try again.")));
        }
    }

    /** Each right-click answer, against the old expressions. */
    @Test
    void theRightClickAnswersReadAsTheyDidBefore()
    {
        final Player player = playerReading("en");
        final Block here = banner(5);
        reflecting("Museum", here);
        try (MockedStatic<ActionBar> bar = mockStatic(ActionBar.class))
        {
            click(player, here);
            reflecting("Library", banner(9));
            reflecting("Archive", banner(13));
            now += 500L;
            click(player, here);
            somebodyElseAtTheBanner();
            now += 1_000L;
            click(player, here);

            bar.verify(() -> ActionBar.send(player, ChatText.status("No other mirrors found.")));
            bar.verify(() -> ActionBar.send(player, ChatText.status(MirrorText.quoted("Museum") + " opens onto "
                + MirrorText.quoted("Archive") + " (" + (0 + 1) + " of " + 2 + ").")));
            bar.verify(() -> ActionBar.send(player, ChatText.status("Somebody else is at this mirror. Give them a"
                + " moment before changing it.")));
        }
    }

    /** The chat refusals, both lines of each that has two, against the old expressions. */
    @Test
    void theChatRefusalsReadAsTheyDidBefore()
    {
        final Player player = playerReading("en");
        final Block unbound = banner(5);
        MirrorManager.add(new QuantumMirror("Museum", BlockPlace.of(unbound), null));
        final Block unloaded = banner(9);
        MirrorManager.add(new QuantumMirror("Archive", BlockPlace.of(unloaded),
            new Place("a_world_nobody_started", 0, 64, 0, 0, 0)));
        final Block refused = banner(13);
        MirrorManager.add(new QuantumMirror("Library", BlockPlace.of(refused),
            new Place("museum_world", 0, 64, 0, 0, 0)));
        when(player.teleport(any(Location.class))).thenReturn(false);

        punch(player, unbound);
        punchIntoUnloadedWorld(player, unloaded);
        punchIntoMuseumWorld(player, refused);

        verify(player).sendMessage(ChatText.info(MirrorText.quoted("Museum") + " does not open onto anywhere yet."));
        verify(player).sendMessage(ChatText.info("Look at it and run "
            + MirrorText.command("/wormhole mirror create", "Museum") + " to set it up again."));
        verify(player).sendMessage(ChatText.info("The far side of this mirror is in "
            + MirrorText.name("a_world_nobody_started") + ", which is not loaded."));
        verify(player).sendMessage(ChatText.info("Something else on this server would not let you into "
            + MirrorText.name("museum_world") + "."));
        verify(player).sendMessage(ChatText.info("A world-access or land-claim plugin is the usual reason -- check"
            + " that you" + " are allowed into that world."));
    }

    /** The punch hints reach a qq player in qq. */
    @Test
    void thePunchHintsAreInThePlayersLanguage()
    {
        final Player player = playerReading("qq");
        final Block here = banner(5);
        reflecting("Museum", here);
        try (MockedStatic<ActionBar> bar = mockStatic(ActionBar.class))
        {
            punch(player, here);
            MirrorSettle.arrived(player);
            punch(player, here);

            bar.verify(() -> ActionBar.send(player, ChatText.status("QQ <" + MirrorText.name("Museum") + "> reflecting.")));
            bar.verify(() -> ActionBar.send(player, ChatText.status("QQ settling.")));
        }
    }

    /** Each right-click answer reaches a qq player in qq, through the reader the click hands the scroll. */
    @Test
    void theRightClickAnswersAreInThePlayersLanguage()
    {
        final Player player = playerReading("qq");
        final Block here = banner(5);
        reflecting("Museum", here);
        try (MockedStatic<ActionBar> bar = mockStatic(ActionBar.class))
        {
            click(player, here);
            reflecting("Library", banner(9));
            reflecting("Archive", banner(13));
            now += 500L;
            click(player, here);
            somebodyElseAtTheBanner();
            now += 1_000L;
            click(player, here);

            bar.verify(() -> ActionBar.send(player, ChatText.status("QQ no others.")));
            bar.verify(() -> ActionBar.send(player, ChatText.status("QQ <" + MirrorText.name("Museum") + "> onto <"
                + MirrorText.name("Archive") + "> 1/2.")));
            bar.verify(() -> ActionBar.send(player, ChatText.status("QQ someone else.")));
        }
    }

    /** The chat refusals reach a qq player in qq. */
    @Test
    void theChatRefusalsAreInThePlayersLanguage()
    {
        final Player player = playerReading("qq");
        final Block unbound = banner(5);
        MirrorManager.add(new QuantumMirror("Museum", BlockPlace.of(unbound), null));
        final Block unloaded = banner(9);
        MirrorManager.add(new QuantumMirror("Archive", BlockPlace.of(unloaded),
            new Place("a_world_nobody_started", 0, 64, 0, 0, 0)));
        final Block refused = banner(13);
        MirrorManager.add(new QuantumMirror("Library", BlockPlace.of(refused),
            new Place("museum_world", 0, 64, 0, 0, 0)));
        when(player.teleport(any(Location.class))).thenReturn(false);

        punch(player, unbound);
        punchIntoUnloadedWorld(player, unloaded);
        punchIntoMuseumWorld(player, refused);

        verify(player).sendMessage(ChatText.info("QQ <" + MirrorText.name("Museum") + "> unbound."));
        verify(player).sendMessage(ChatText.info("QQ run " + MirrorText.command("/wormhole mirror create", "Museum")
            + "."));
        verify(player).sendMessage(ChatText.info("QQ unloaded " + MirrorText.name("a_world_nobody_started") + "."));
        verify(player).sendMessage(ChatText.info("QQ refused " + MirrorText.name("museum_world") + "."));
        verify(player).sendMessage(ChatText.info("QQ refused hint."));
    }
}
