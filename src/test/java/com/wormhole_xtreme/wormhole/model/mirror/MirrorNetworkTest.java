package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * Every mirror is on the network: it shows its own room until somebody at it chooses another.
 *
 * <p>A right-click walks the mirrors in a fixed order -- its own room first, then the others by
 * name -- so a player learns where one press takes them. Alone, you can click through them as
 * fast as you like; with somebody else at the mirror, what it shows stays up a few seconds before
 * it can change, so nobody is swapped out from under a trip they were about to take. When nobody
 * is at it any more, it goes back to its own room.
 */
class MirrorNetworkTest
{
    private long now = 1_000_000L;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(com.wormhole_xtreme.wormhole.WormholeXTreme.class));
        ConfigTestSupport.clear();
        MirrorManager.clear();
        MirrorNetwork.clear();
        MirrorNetwork.clock = () -> now;
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorNetwork.clear();
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** A mirror storing its own room, in a world of its own, as a new mirror does. */
    private static QuantumMirror mirror(final String name, final String world)
    {
        final QuantumMirror mirror = new QuantumMirror(name, new MirrorBlock(world, 10, 64, 10),
            new MirrorPoint(world, 10.5, 63, 10.5, 180f, 0f));
        MirrorManager.add(mirror);
        return mirror;
    }

    /** A wall banner facing north stores the block in front of it, at the opening's bottom, facing out. */
    @Test
    void aBannersRoomIsInFrontOfItLevelWithTheBottomOfItsOpening()
    {
        final World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        final Block banner = mock(Block.class);
        final Directional facing = mock(Directional.class);
        when(facing.getFacing()).thenReturn(BlockFace.EAST);
        when(banner.getBlockData()).thenReturn(facing);
        when(banner.getWorld()).thenReturn(world);
        when(banner.getX()).thenReturn(4);
        when(banner.getY()).thenReturn(70);
        when(banner.getZ()).thenReturn(-3);

        assertEquals(new MirrorPoint("world", 4.5, 69, -2.5, 270f, 0f), MirrorNetwork.roomOf(banner));
    }

    /** A new mirror shows its own room, and a mirror from before the network does not. */
    @Test
    void aMirrorStoringItsOwnRoomReflectsAndOnePointedElsewhereDoesNot()
    {
        final QuantumMirror library = mirror("library", "world");
        final QuantumMirror old = new QuantumMirror("old", new MirrorBlock("world", 40, 64, 40),
            new MirrorPoint("world_nether", 0, 64, 0, 0f, 0f));

        assertTrue(MirrorNetwork.reflects(library));
        assertFalse(MirrorNetwork.reflects(old), "an old mirror still opens where it was pointed");
    }

    /** The only mirror has nowhere else to go. */
    @Test
    void theOnlyMirrorSaysThereAreNoOthers()
    {
        final QuantumMirror library = mirror("library", "world");

        assertEquals("No other mirrors found.", MirrorNetwork.scroll(library, false));
        assertTrue(MirrorNetwork.reflects(library));
    }

    /** Right-clicks walk the others by name, then come back to the mirror's own room. */
    @Test
    void rightClicksWalkTheOthersByNameAndComeBackToItsOwnRoom()
    {
        final QuantumMirror library = mirror("library", "world");
        mirror("nether", "world_nether");
        mirror("End", "world_the_end");

        MirrorNetwork.scroll(library, false);
        assertEquals("End", MirrorNetwork.chosen(library).name(), "by name, whatever the case");
        now += 500L;
        MirrorNetwork.scroll(library, false);
        assertEquals("nether", MirrorNetwork.chosen(library).name());
        now += 500L;
        MirrorNetwork.scroll(library, false);
        assertEquals("library", MirrorNetwork.chosen(library).name(), "and round to its own room");
        assertTrue(MirrorNetwork.reflects(library));
    }

    /** One press arriving twice moves the mirror once. */
    @Test
    void aSecondClickOnTheSamePressDoesNotSkipAMirror()
    {
        final QuantumMirror library = mirror("library", "world");
        mirror("nether", "world_nether");
        mirror("end", "world_the_end");

        MirrorNetwork.scroll(library, false);
        assertNull(MirrorNetwork.scroll(library, false), "nothing to say for the same press");

        assertEquals("end", MirrorNetwork.chosen(library).name());
    }

    /** Alone at a mirror you can click on at once; with somebody else there, the choice holds. */
    @Test
    void withSomebodyElseAtTheMirrorTheChoiceHoldsAFewSeconds()
    {
        final QuantumMirror library = mirror("library", "world");
        mirror("nether", "world_nether");
        mirror("end", "world_the_end");

        MirrorNetwork.scroll(library, true);
        now += 1_000L;
        final String said = MirrorNetwork.scroll(library, true);

        assertTrue(said.contains("Somebody else"), "should say why it did not change: " + said);
        assertEquals("end", MirrorNetwork.chosen(library).name(), "still what the first click chose");

        now += MirrorNetwork.HOLD_MILLIS;
        MirrorNetwork.scroll(library, true);
        assertEquals("nether", MirrorNetwork.chosen(library).name(), "after the hold it moves on");
    }

    /** When nobody is at a mirror any more it goes back to its own room. */
    @Test
    void nobodyNearSendsAMirrorBackToItsOwnRoom()
    {
        final QuantumMirror library = mirror("library", "world");
        mirror("nether", "world_nether");
        MirrorNetwork.scroll(library, false);

        MirrorNetwork.settle(library, true);
        assertEquals("nether", MirrorNetwork.chosen(library).name(), "somebody is still there");

        MirrorNetwork.settle(library, false);
        assertTrue(MirrorNetwork.reflects(library), "nobody is, so it shows its own room again");
    }

    /** A chosen mirror that is removed leaves the mirror showing its own room. */
    @Test
    void aChosenMirrorThatIsRemovedLeavesItsOwnRoom()
    {
        final QuantumMirror library = mirror("library", "world");
        mirror("nether", "world_nether");
        MirrorNetwork.scroll(library, false);

        MirrorManager.remove("nether");

        assertTrue(MirrorNetwork.reflects(library));
    }

    /** Somebody is near a mirror inside the proximity radius, and not past it; the clicker does not count. */
    @Test
    void whoIsNearIsMeasuredFromTheBannerAndLeavesOutTheClicker()
    {
        final World world = mock(World.class);
        final Player clicker = mock(Player.class);
        when(clicker.getLocation()).thenReturn(new Location(world, 10.5, 63, 12.5));
        final Player far = mock(Player.class);
        when(far.getLocation()).thenReturn(new Location(world, 10.5, 63, 40.5));
        when(world.getPlayers()).thenReturn(List.of(clicker, far));
        final MirrorBlock banner = new MirrorBlock("world", 10, 64, 10);

        assertFalse(MirrorNetwork.anybodyNear(world, banner, clicker), "thirty blocks away is not at it");
        assertTrue(MirrorNetwork.anybodyNear(world, banner, null), "the clicker is, when counted");
    }
}
