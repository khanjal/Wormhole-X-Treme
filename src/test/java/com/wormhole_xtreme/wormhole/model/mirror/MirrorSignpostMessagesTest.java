package com.wormhole_xtreme.wormhole.model.mirror;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.window.BlockPlace;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.utils.ActionBar;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * The line a mirror shows whoever looks at it, read by key from {@code lang/} (#26), and what that costs.
 *
 * <p>Each variant -- opening onto a world, onto another mirror, or showing its own room -- goes through a
 * real sweep to an English player against the concatenation {@link MirrorText} built before the move,
 * and to a {@code qq} player in qq. The sweep runs every second for every player at a mirror, so the
 * lookup is held to one per line actually sent: a player a click has held costs none at all.
 */
class MirrorSignpostMessagesTest
{
    /** Where saves go, so no test writes a mirror file into the repository. */
    @TempDir
    File dataFolder;

    private World world;

    private Block banner;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        MirrorManager.clear();
        MirrorNetwork.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        banner = bannerAt(10);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorSignpost.clear();
        MirrorNetwork.clear();
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private Block bannerAt(final int x)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(10);
        return block;
    }

    /** A player reading this language, four blocks out from the banner and looking at it. */
    private Player playerAtTheBanner(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocale()).thenReturn(locale);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 10.5, 64.0, 14.5));
        when(player.getTargetBlockExact(ArgumentMatchers.anyInt())).thenReturn(banner);
        return player;
    }

    /** A mirror on the banner opening onto another world. */
    private void intoTheNether()
    {
        MirrorManager.add(new QuantumMirror("museum", BlockPlace.of(banner), new Place("nether", 0, 64, 0, 0f, 0f)));
    }

    /** A mirror on the banner showing its own room. */
    private QuantumMirror reflecting()
    {
        final QuantumMirror museum = new QuantumMirror("museum", BlockPlace.of(banner),
            new Place("world", 10.5, 63, 10.5, 180f, 0f));
        MirrorManager.add(museum);
        return museum;
    }

    /** The banner's mirror, turned on to open onto a second one called library. */
    private void ontoTheLibrary()
    {
        final QuantumMirror museum = reflecting();
        MirrorManager.add(new QuantumMirror("library", new BlockPlace("world", 30, 64, 10),
            new Place("world", 30.5, 63, 10.5, 180f, 0f)));
        MirrorNetwork.scroll(museum, false);
    }

    /** One pass, with a server that has this player online. */
    private static void sweep(final Player player)
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            MirrorSignpost.tick();
        }
    }

    /** The line for a mirror opening onto a world, against the old expression. */
    @Test
    void theApproachLineToAWorldReadsAsItDidBefore()
    {
        intoTheNether();
        final Player player = playerAtTheBanner("en");
        try (MockedStatic<ActionBar> bar = mockStatic(ActionBar.class))
        {
            sweep(player);

            bar.verify(() -> ActionBar.send(player, ChatText.status(MirrorText.NAME_COLOUR + "museum"
                + MirrorText.BODY_COLOUR + " -- punch to travel to " + MirrorText.NAME_COLOUR + "nether"
                + MirrorText.BODY_COLOUR + ", right-click for another.")));
        }
    }

    /** The line for a mirror turned on to open onto another mirror names that mirror, as before. */
    @Test
    void theApproachLineToAnotherMirrorReadsAsItDidBefore()
    {
        ontoTheLibrary();
        final Player player = playerAtTheBanner("en");
        try (MockedStatic<ActionBar> bar = mockStatic(ActionBar.class))
        {
            sweep(player);

            bar.verify(() -> ActionBar.send(player, ChatText.status(MirrorText.NAME_COLOUR + "museum"
                + MirrorText.BODY_COLOUR + " -- punch to travel to " + MirrorText.NAME_COLOUR + "library"
                + MirrorText.BODY_COLOUR + ", right-click for another.")));
        }
    }

    /** The line for a mirror showing its own room, against the old expression. */
    @Test
    void theReflectionLineReadsAsItDidBefore()
    {
        reflecting();
        final Player player = playerAtTheBanner("en");
        try (MockedStatic<ActionBar> bar = mockStatic(ActionBar.class))
        {
            sweep(player);

            bar.verify(() -> ActionBar.send(player, ChatText.status(MirrorText.NAME_COLOUR + "museum"
                + MirrorText.BODY_COLOUR + " -- right-click to choose a mirror.")));
        }
    }

    /** Each variant reaches a qq player in qq. */
    @Test
    void eachLineIsInTheLookersLanguage()
    {
        final Player player = playerAtTheBanner("qq");
        try (MockedStatic<ActionBar> bar = mockStatic(ActionBar.class))
        {
            intoTheNether();
            sweep(player);
            MirrorManager.clear();
            reflecting();
            sweep(player);
            MirrorManager.clear();
            ontoTheLibrary();
            sweep(player);

            bar.verify(() -> ActionBar.send(player, ChatText.status("QQ " + ChatText.name("museum") + " punch "
                + ChatText.name("nether") + ".")));
            bar.verify(() -> ActionBar.send(player, ChatText.status("QQ " + ChatText.name("museum") + " right-click.")));
            bar.verify(() -> ActionBar.send(player, ChatText.status("QQ " + ChatText.name("museum") + " punch "
                + ChatText.name("library") + ".")));
        }
    }

    /**
     * Sweeps while a click holds the player build nothing; the rest look up once per line sent.
     *
     * <p>The lookup reads the player's client language, so it is the one cost the move to {@code lang/}
     * adds to a pass that runs every second for everyone at a mirror. Five held sweeps must read the
     * language and look up a message no times, and three free ones exactly three times each, not twice.
     */
    @Test
    void aHeldSweepLooksNothingUpAndAFreeOneLooksUpOncePerLine()
    {
        intoTheNether();
        final Player player = playerAtTheBanner("qq");
        try (MockedStatic<ActionBar> bar = mockStatic(ActionBar.class);
            MockedStatic<Messages> messages = mockStatic(Messages.class, CALLS_REAL_METHODS))
        {
            MirrorSignpost.hold(player);
            for (int i = 0; i < 5; i++)
            {
                sweep(player);
            }
            bar.verify(() -> ActionBar.send(any(), anyString()), never());
            messages.verify(() -> Messages.get(any(CommandSender.class), anyString(), any(Object[].class)), never());
            verify(player, never()).getLocale();

            MirrorSignpost.clear();
            for (int i = 0; i < 3; i++)
            {
                sweep(player);
            }
            bar.verify(() -> ActionBar.send(any(), anyString()), times(3));
            messages.verify(() -> Messages.get(any(CommandSender.class), anyString(), any(Object[].class)), times(3));
        }
        verify(player, times(3)).getLocale();
    }
}
