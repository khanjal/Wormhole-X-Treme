package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.HeldScheduler;
import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingOrientation;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingPattern;
import com.wormhole_xtreme.wormhole.model.ring.RingPreview;
import com.wormhole_xtreme.wormhole.model.ring.RingPreviews;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * {@code /wormhole ring preview odd|even}: who may run it, what it tells the player, and the
 * steps that carry on from it (#332, step 4).
 *
 * <p>The drawing, the guide and every clean-up are checked in {@code RingPatternPreviewTest};
 * what is left for the command is the decisions it makes. There is no pair yet, so the only
 * permission that can matter is the one for building a ring, and it has to be checked for every
 * step and not only the first.
 */
class RingPatternPreviewCommandTest
{
    private static final String WORLD = "world";
    private static final String BUILD = "wormhole.ring.build";
    private static final long TIMEOUT = 20L * 60L * 5L;

    private final HeldScheduler held = new HeldScheduler();
    private final Set<String> slabsLaid = new HashSet<>();
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ConfigManager> config;
    private Player player;
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        held.install();
        RingManager.clear();
        RingPreviews.clearAll();

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call ->
            call.getArgument(0, Material.class).name().endsWith("_SLAB") ? mock(Slab.class) : mock(BlockData.class));
        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingDeployTicks).thenReturn(Integer.valueOf(3));
        config.when(ConfigManager::getRingFlashTicks).thenReturn(Integer.valueOf(2));
        config.when(ConfigManager::getRingSettleTicks).thenReturn(Integer.valueOf(7));
        config.when(ConfigManager::getRingHoldTicks).thenReturn(Integer.valueOf(11));
        config.when(ConfigManager::getRingLightsLingerTicks).thenReturn(Integer.valueOf(13));
        config.when(ConfigManager::getRingMaxCeilingDrop).thenReturn(Integer.valueOf(16));
        config.when(ConfigManager::getRingSoundRing).thenReturn("");
        config.when(ConfigManager::getRingSoundOpen).thenReturn("");
        config.when(ConfigManager::getRingSoundFlash).thenReturn("");
        config.when(ConfigManager::getRingSoundClose).thenReturn("");
        config.when(ConfigManager::getRingDefaultLight).thenReturn(Material.REDSTONE_LAMP);
        config.when(ConfigManager::getRingDefaultFlash).thenReturn(Material.GLOWSTONE);
        config.when(ConfigManager::getRingDefaultStyle).thenReturn(RingStyle.CONCURRENT);

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(Boolean.TRUE);
        when(world.getMinHeight()).thenReturn(Integer.valueOf(-64));
        when(world.getMaxHeight()).thenReturn(Integer.valueOf(320));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
        {
            final Block block = mock(Block.class);
            final int y = call.getArgument(1, Integer.class);
            when(block.isPassable()).thenReturn(Boolean.valueOf(y >= 65));
            if (slabsLaid.contains(call.getArgument(0) + "," + y + "," + call.getArgument(2)))
            {
                final Slab slab = mock(Slab.class);
                when(slab.getType()).thenReturn(Slab.Type.BOTTOM);
                when(block.getType()).thenReturn(Material.SMOOTH_STONE_SLAB);
                when(block.getBlockData()).thenReturn(slab);
            }
            else
            {
                when(block.getType()).thenReturn(Material.AIR);
                when(block.getBlockData()).thenReturn(mock(BlockData.class));
            }
            return block;
        });

        player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Justin");
        when(player.isOnline()).thenReturn(Boolean.TRUE);
        when(player.hasPermission(anyString())).thenReturn(false);
        when(player.hasPermission(BUILD)).thenReturn(true);
        standAt(0, 65, 0);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        RingPreviews.clearAll();
        RingManager.clear();
        config.close();
        bukkit.close();
        held.remove();
        PluginTestSupport.remove();
    }

    private void standAt(final int x, final int y, final int z)
    {
        when(player.getLocation()).thenReturn(new Location(world, x, y, z));
    }

    private boolean preview(final String... rest)
    {
        final String[] args = new String[rest.length + 2];
        args[0] = "ring";
        args[1] = "preview";
        System.arraycopy(rest, 0, args, 2, rest.length);
        return new RingCommand().execute(player, args);
    }

    private void settle(final RingPreview.State wanted)
    {
        for (int guard = 0; (RingPreviews.of(player).getState() != wanted) && (guard < 1000); guard++)
        {
            held.runNextOtherThan(TIMEOUT);
        }
        assertEquals(wanted, RingPreviews.of(player).getState());
    }

    /** Without the build permission nothing is drawn, and the player is told why. */
    @Test
    void withoutRingBuildNothingIsDrawn()
    {
        when(player.hasPermission(BUILD)).thenReturn(false);

        assertTrue(preview("odd"));

        final String refusal = ChatText.noPermission(player);
        verify(player).sendMessage(refusal);
        assertNull(RingPreviews.of(player));
        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /** The odd pattern is sixteen slabs and none are laid. */
    @Test
    void theOddPatternSaysSixteenSlabsAndNoneInPlace()
    {
        assertTrue(preview("odd"));

        verify(player).sendMessage(plainContains("odd ring pattern is drawn where you stand"));
        verify(player).sendMessage(plainContains("0 of 16 slabs in place"));
        assertTrue(RingPreviews.of(player).isPattern());
        assertEquals(RingPattern.ODD, RingPreviews.of(player).getRing().getPattern());
    }

    /** The even pattern is twelve. */
    @Test
    void theEvenPatternSaysTwelve()
    {
        assertTrue(preview("even"));

        verify(player).sendMessage(plainContains("0 of 12 slabs in place"));
        assertEquals(RingPattern.EVEN, RingPreviews.of(player).getRing().getPattern());
    }

    /** Slabs laid since are counted when it is run again where the player stands. */
    @Test
    void slabsAlreadyLaidAreCountedFromTheWorld()
    {
        slabsLaid.add("-1,65,-3");
        slabsLaid.add("3,65,0");
        slabsLaid.add("0,65,3");

        assertTrue(preview("odd"));

        verify(player).sendMessage(plainContains("3 of 16 slabs in place"));
        slabsLaid.add("1,65,-3");

        assertTrue(preview("odd"));

        verify(player).sendMessage(plainContains("4 of 16 slabs in place"));
    }

    /** A word after the pattern is a usage error, and draws nothing. */
    @Test
    void aWordAfterThePatternIsUsage()
    {
        assertTrue(preview("odd", "extra"));

        verify(player).sendMessage(plainContains("/wormhole ring preview"));
        assertNull(RingPreviews.of(player));
    }

    /** Deploy, light and collapse work on the pattern with no ring underfoot to find. */
    @Test
    void deployLightAndCollapseWorkOnThePattern()
    {
        preview("odd");

        assertTrue(preview("deploy"));
        settle(RingPreview.State.DEPLOYED);
        verify(player).sendMessage(plainContains("The stack rises"));

        assertTrue(preview("light"));
        verify(player).sendMessage(plainContains("The light runs through the stack"));
        settle(RingPreview.State.LIGHTING);
        settle(RingPreview.State.DEPLOYED);

        assertTrue(preview("collapse"));
        verify(player).sendMessage(plainContains("The rings come home"));
        settle(RingPreview.State.LIT);
    }

    /** A whole cycle plays on the pattern, and ends with the pad still lit. */
    @Test
    void cyclePlaysOnThePattern()
    {
        preview("even");

        assertTrue(preview("cycle"));

        verify(player).sendMessage(plainContains("A whole cycle plays"));
        for (int guard = 0; RingPreviews.of(player).isCycling() && (guard < 1000); guard++)
        {
            held.runNextOtherThan(TIMEOUT);
        }
        assertEquals(RingPreview.State.LIT, RingPreviews.of(player).getState());
    }

    /** With a built ring underfoot too, deploy still acts on the pattern the player is looking at. */
    @Test
    void aPatternStandingTakesTheStepsEvenOverABuiltRing()
    {
        final Ring built = new Ring(0, 64, 0, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB,
            Material.GLOWSTONE);
        final RingPair pair = new RingPair("aaaa0001", WORLD, built, built);
        pair.setOwner(UUID.randomUUID().toString());
        RingManager.addPair(pair, 4);
        preview("odd");
        final RingPreview pattern = RingPreviews.of(player);

        preview("deploy");

        assertSame(pattern, RingPreviews.of(player));
        assertTrue(pattern.isPattern());
        assertEquals(RingPreview.State.DEPLOYING, pattern.getState());
    }

    /** -save has nothing to save into, so it is refused and the pattern carries on. */
    @Test
    void saveIsRefusedOnAPattern()
    {
        preview("odd");
        preview("ring", "quartz_slab");

        assertTrue(preview("-save"));

        verify(player).sendMessage(plainContains("nothing to save into"));
        assertNotNull(RingPreviews.of(player));
        assertTrue(RingPreviews.of(player).hasTrial(), "the trial was not dropped by the refusal");
        assertTrue(RingManager.getAllPairs().isEmpty());
    }

    /** A trial value works on a pattern, and the guide judges against it. */
    @Test
    void aTrialSlabWorksOnAPattern()
    {
        preview("odd");

        assertTrue(preview("ring", "quartz_slab"));

        assertEquals(Material.QUARTZ_SLAB, RingPreviews.of(player).getTrialRing());
        verify(player).sendMessage(plainContains("Trying ring"));
    }

    /** Every step is checked against the build permission, so losing it stops the steps. */
    @Test
    void everyStepOnAPatternNeedsRingBuild()
    {
        preview("odd");
        preview("deploy");
        settle(RingPreview.State.DEPLOYED);
        when(player.hasPermission(BUILD)).thenReturn(false);

        preview("light");
        preview("collapse");
        preview("deploy");
        preview("cycle");
        preview("ring", "quartz_slab");

        final String refusal = ChatText.noPermission(player);
        verify(player, times(5)).sendMessage(refusal);
        assertEquals(RingPreview.State.DEPLOYED, RingPreviews.of(player).getState(), "nothing moved");
        assertNull(RingPreviews.of(player).getTrialRing());
    }

    /** -clear takes the pattern back. */
    @Test
    void clearTakesThePatternBack()
    {
        preview("odd");

        assertTrue(preview("-clear"));

        assertNull(RingPreviews.of(player));
        verify(player).sendMessage(plainContains("Preview taken back"));
    }
}
