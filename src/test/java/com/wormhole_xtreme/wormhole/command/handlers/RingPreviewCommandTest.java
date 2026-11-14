package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
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
import com.wormhole_xtreme.wormhole.model.ring.RingPhase;
import com.wormhole_xtreme.wormhole.model.ring.RingPreview;
import com.wormhole_xtreme.wormhole.model.ring.RingPreviews;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * {@code /wormhole ring preview}: who may run it, which ring it acts on, and what a player is
 * told when a step does or does not make sense (#332).
 *
 * <p>The drawing and the steps are checked in {@code RingPreviewTest} and
 * {@code RingPreviewsTest}; what is left for the command is the decisions it makes before and
 * after them. The permission check is the one that matters most: a preview shows where a
 * private link is, so a stranger must be refused before anything is drawn, not after.
 */
class RingPreviewCommandTest
{
    private static final String WORLD = "world";
    private static final String OWNER = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    /** How long the preview timeout waits: five minutes of ticks. */
    private static final long TIMEOUT = 20L * 60L * 5L;

    private final HeldScheduler held = new HeldScheduler();
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
        final BlockData drawn = mock(BlockData.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenReturn(drawn);
        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingDeployTicks).thenReturn(Integer.valueOf(3));
        config.when(ConfigManager::getRingFlashTicks).thenReturn(Integer.valueOf(2));
        config.when(ConfigManager::getRingMaxCeilingDrop).thenReturn(Integer.valueOf(16));
        config.when(ConfigManager::getRingSoundRing).thenReturn("");
        config.when(ConfigManager::getRingSoundOpen).thenReturn("");
        config.when(ConfigManager::getRingSoundFlash).thenReturn("");
        config.when(ConfigManager::getRingSoundClose).thenReturn("");

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(Boolean.TRUE);
        when(world.getMinHeight()).thenReturn(Integer.valueOf(-64));
        when(world.getMaxHeight()).thenReturn(Integer.valueOf(320));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
        {
            final Block block = mock(Block.class);
            // Stone below the pads, open air from them up: a floor ring has its floor, and a
            // ceiling ring has none within reach.
            when(block.isPassable()).thenReturn(Boolean.valueOf(call.getArgument(1, Integer.class) >= 64));
            final BlockData real = mock(BlockData.class);
            when(block.getBlockData()).thenReturn(real);
            return block;
        });

        player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.fromString(OWNER));
        when(player.getName()).thenReturn("Justin");
        when(player.isOnline()).thenReturn(Boolean.TRUE);
        when(player.hasPermission(anyString())).thenReturn(false);
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

    private static Ring ring(final int x, final int z, final RingOrientation orientation)
    {
        final Ring made = new Ring(x, 64, z, RingPattern.ODD, orientation, Material.STONE_SLAB, Material.GLOWSTONE);
        made.setStyle(RingStyle.CONCURRENT);
        return made;
    }

    /** A registered pair, its A end at the origin and its B end 200 blocks away. */
    private static RingPair registeredPair(final String owner)
    {
        final RingPair pair = new RingPair("aaaa0001", WORLD, ring(0, 0, RingOrientation.FLOOR),
            ring(200, 200, RingOrientation.FLOOR));
        pair.setOwner(owner);
        pair.setOwnerName("Somebody");
        RingManager.addPair(pair, 4);
        return pair;
    }

    private boolean preview(final String... rest)
    {
        final String[] args = new String[rest.length + 2];
        args[0] = "ring";
        args[1] = "preview";
        System.arraycopy(rest, 0, args, 2, rest.length);
        return new RingCommand().execute(player, args);
    }

    /** Runs the animation until the stack stands, leaving the timeout alone. */
    private void standTheStack()
    {
        for (int guard = 0; (RingPreviews.of(player).getState() != RingPreview.State.DEPLOYED) && (guard < 100); guard++)
        {
            held.runNextOtherThan(TIMEOUT);
        }
        assertEquals(RingPreview.State.DEPLOYED, RingPreviews.of(player).getState());
    }

    /** With no ring underfoot and none named, there is nothing to look at. */
    @Test
    void withoutAPairThereIsNothingToPreview()
    {
        standAt(500, 65, 500);
        registeredPair(OWNER);

        assertTrue(preview());

        verify(player).sendMessage(plainContains("Stand in a ring, or name a pair by its id"));
        assertNull(RingPreviews.of(player));
    }

    /** The bare verb lights the pad of the ring underfoot, for the player alone. */
    @Test
    void thePlainVerbLightsThePadOfTheRingUnderfoot()
    {
        final RingPair pair = registeredPair(OWNER);

        assertTrue(preview());

        assertNotNull(RingPreviews.of(player));
        assertSame(pair.getEndA(), RingPreviews.of(player).getRing());
        verify(player).sendMessage(plainContains("is lit, for you alone"));
        verify(player).sendMessage(plainContains("aaaa0001"));
    }

    /** The pair's owner may preview it. */
    @Test
    void theOwnerMayPreviewTheirOwnRing()
    {
        registeredPair(OWNER);

        preview("deploy");

        assertEquals(RingPreview.State.DEPLOYING, RingPreviews.of(player).getState());
    }

    /** An admin may preview any pair, whoever owns it. */
    @Test
    void anAdminMayPreviewSomebodyElsesRing()
    {
        registeredPair(UUID.randomUUID().toString());
        when(player.hasPermission("wormhole.ring.admin")).thenReturn(true);

        preview("deploy");

        assertNotNull(RingPreviews.of(player));
    }

    /** Anybody else is refused, and nothing is drawn for them. */
    @Test
    void somebodyWhoDoesNotOwnTheRingIsRefusedBeforeAnythingIsDrawn()
    {
        registeredPair(UUID.randomUUID().toString());
        // Worked out first: building it reads the player, which verify would count.
        final String refusal = ChatText.noPermission(player);

        for (final String step : new String[] { "", "deploy" })
        {
            if (step.isEmpty())
            {
                preview();
            }
            else
            {
                preview(step);
            }
        }

        verify(player, times(2)).sendMessage(refusal);
        assertNull(RingPreviews.of(player));
        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /** The stack standing is not a licence to keep running it after the pair has changed hands. */
    @Test
    void lightAndCollapseAreRefusedOnceThePairIsNoLongerTheirs()
    {
        final RingPair pair = registeredPair(OWNER);
        preview("deploy");
        standTheStack();
        pair.setOwner(UUID.randomUUID().toString());
        final String refusal = ChatText.noPermission(player);

        preview("light");
        preview("collapse");

        verify(player, times(2)).sendMessage(refusal);
        assertEquals(RingPreview.State.DEPLOYED, RingPreviews.of(player).getState(), "neither step ran");
    }

    /** Each step on a stack that makes sense says what it is doing. */
    @Test
    void eachStepSaysWhatItIsDoing()
    {
        registeredPair(OWNER);

        preview("deploy");
        verify(player).sendMessage(plainContains("The stack rises"));
        standTheStack();

        preview("light");
        verify(player).sendMessage(plainContains("The light runs through the stack"));
        standTheStack();

        preview("collapse");
        verify(player).sendMessage(plainContains("The rings come home"));
    }

    /** Steps that make no sense in the current state say so. */
    @Test
    void stepsThatMakeNoSenseSaySoInsteadOfDoingSomethingOdd()
    {
        registeredPair(OWNER);

        preview("light");
        preview("collapse");
        verify(player, times(2)).sendMessage(plainContains("No stack is standing"));
        assertNull(RingPreviews.of(player), "asking for the light does not conjure a preview");

        preview("deploy");
        preview("light");
        verify(player).sendMessage(plainContains("Wait for the rings to stop moving"));
        standTheStack();

        preview("deploy");
        verify(player).sendMessage(plainContains("The stack is already standing"));

        preview();
        verify(player).sendMessage(plainContains("already lit for you"));
    }

    /** -clear takes it back, and says when there was nothing to take. */
    @Test
    void clearTakesThePreviewBackAndSaysWhenThereIsNone()
    {
        registeredPair(OWNER);

        preview("-clear");
        verify(player).sendMessage(plainContains("You have no ring preview showing"));

        preview("deploy");
        preview("-clear");

        verify(player).sendMessage(plainContains("Preview taken back"));
        assertNull(RingPreviews.of(player));
        assertEquals(0, held.pending());
    }

    /** Naming a pair works from anywhere, and shows whichever end is nearer. */
    @Test
    void namingAPairShowsTheEndNearerThePlayer()
    {
        final RingPair pair = registeredPair(OWNER);

        standAt(190, 65, 190);
        preview("aaaa0001", "deploy");
        assertSame(pair.getEndB(), RingPreviews.of(player).getRing(), "nearer the far end");

        standAt(10, 65, 10);
        preview("aaaa0001");
        assertSame(pair.getEndA(), RingPreviews.of(player).getRing(), "and nearer the first");
    }

    /** {@code cycle} lights the pad if it must, says it is playing the whole thing, and ends on the lit pad. */
    @Test
    void cycleLightsThePadPlaysTheWholeCycleAndEndsLit()
    {
        final RingPair pair = registeredPair(OWNER);

        assertTrue(preview("cycle"));

        verify(player).sendMessage(plainContains("A whole cycle plays, for you alone"));
        assertTrue(RingPreviews.of(player).isCycling());
        for (int guard = 0; RingPreviews.of(player).isCycling() && (guard < 200); guard++)
        {
            held.runNextOtherThan(TIMEOUT);
        }
        assertEquals(RingPreview.State.LIT, RingPreviews.of(player).getState());
        assertEquals(RingPhase.IDLE, pair.getPhase());
        assertEquals(0L, pair.getCooldownUntil());
    }

    /** With a stack standing, {@code cycle} says what to do and draws nothing. */
    @Test
    void cycleWithAStackStandingSaysToCollapseFirst()
    {
        registeredPair(OWNER);
        preview("deploy");
        standTheStack();

        preview("cycle");

        verify(player).sendMessage(plainContains("A stack is standing, and a cycle starts from the lit pad. Run collapse first."));
        assertFalse(RingPreviews.of(player).isCycling());
        assertEquals(RingPreview.State.DEPLOYED, RingPreviews.of(player).getState());
    }

    /** Another step during a cycle stops it and says so. */
    @Test
    void anotherStepDuringACycleStopsItAndSaysSo()
    {
        registeredPair(OWNER);
        preview("cycle");
        held.runNextOtherThan(TIMEOUT);

        preview("light");

        verify(player).sendMessage(plainContains("The cycle stopped and the preview is taken back"));
        assertNull(RingPreviews.of(player));
    }

    /** {@code cycle} is a step of the pair's owner or an admin, like the others. */
    @Test
    void cycleIsRefusedToSomebodyWhoMayNotPreviewThePair()
    {
        registeredPair("11111111-1111-1111-1111-111111111111");

        preview("cycle");

        assertNull(RingPreviews.of(player));
        verify(player, never()).sendMessage(plainContains("A whole cycle plays"));
    }

    /** A step that is not one, or too many words, is a usage line and draws nothing. */
    @Test
    void anUnknownStepOrExtraWordsGetTheUsageLine()
    {
        registeredPair(OWNER);

        preview("sparkle");
        preview("deploy", "twice");

        verify(player, times(2))
            .sendMessage(plainContains("/wormhole ring preview [id] [deploy|light|collapse|cycle|ring <slab>"));
        assertNull(RingPreviews.of(player));
    }

    /** A pair that is running is refused, with the reason. */
    @Test
    void aPairThatIsRunningIsRefusedWithTheReason()
    {
        final RingPair pair = registeredPair(OWNER);
        pair.setPhase(RingPhase.COUNTDOWN);

        preview();
        preview("deploy");

        verify(player, times(2)).sendMessage(plainContains("That pair is running"));
        assertNull(RingPreviews.of(player));
    }

    /** A ceiling ring with no room under it says what a build would say about it. */
    @Test
    void aCeilingRingWithNoRoomUnderItIsExplainedLikeABuildRefusal()
    {
        final RingPair pair = new RingPair("cccc0003", WORLD, ring(0, 0, RingOrientation.CEILING),
            ring(200, 200, RingOrientation.CEILING));
        pair.setOwner(OWNER);
        RingManager.addPair(pair, 4);

        preview("cccc0003", "deploy");

        verify(player).sendMessage(plainContains("no room between it and the floor"));
        assertEquals(RingPreview.State.LIT, RingPreviews.of(player).getState(), "nothing rose");
    }

    /** A pair in another world, or in a chunk that is not loaded, is refused with the reason. */
    @Test
    void aPairInAnotherWorldOrAnUnloadedChunkIsRefusedWithTheReason()
    {
        registeredPair(OWNER);
        final World nether = mock(World.class);
        when(nether.getName()).thenReturn("nether");
        when(player.getWorld()).thenReturn(nether);

        preview("aaaa0001", "deploy");
        verify(player).sendMessage(plainContains("That ring is in another world"));

        when(player.getWorld()).thenReturn(world);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(Boolean.FALSE);
        preview("aaaa0001", "deploy");
        verify(player).sendMessage(plainContains("its chunk is not loaded"));
        assertNull(RingPreviews.of(player));
    }

    /** The help lists the new verb. */
    @Test
    void theHelpListsPreview()
    {
        assertTrue(new RingCommand().execute(player, new String[] { "ring" }));

        verify(player).sendMessage(plainContains("/wormhole ring preview"));
    }
}
