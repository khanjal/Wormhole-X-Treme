package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * Taking out the real iris blocks a world saved by an older version still holds.
 *
 * <p>A vertical gate's iris used to be real blocks in the opening and is now drawn on clients.
 * A world that last saved under the old behaviour therefore has a gate's worth of stone or
 * glass standing in every opening that was shut at the time, and nothing else would ever take
 * it out: the drawing and the blocks say the same thing, so it looks right while the barrier is
 * still there to be mined, and still there to be left behind by the next crash.
 *
 * <p>What it must not do is tidy away somebody's property. A player can build in a gate's
 * opening -- that is what #243 was about -- so only a cell holding that gate's own iris
 * material is cleared.
 */
class BuiltIrisUpgradeTest
{
    /** Held: a Location keeps its World weakly, so an inline mock can be collected mid-test. */
    private World world;
    private Stargate gate;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);

        gate = new Stargate();
        gate.setGateName("OldGate");
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGateIrisActive(true);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    /**
     * Puts a cell in the gate's opening with the given block really standing in it.
     *
     * @param y
     *            the cell's height, which is all that distinguishes them here
     * @param standing
     *            what is really in that cell
     * @return the block, to verify against
     */
    private Block cellHolding(final int y, final Material standing)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(standing);
        gate.getGatePortalBlocks().add(new Location(world, 10, y, 20));
        when(world.getBlockAt(10, y, 20)).thenReturn(block);
        return block;
    }

    /**
     * The blocks an old save left in the opening are taken out.
     */
    @Test
    void anIrisLeftStandingByAnOlderVersionIsCleared()
    {
        final Block left = cellHolding(64, Material.STONE);

        final int cleared = BuiltIrisUpgrade.clearLeftover(gate);

        assertEquals(1, cleared, "the cell holding the gate's own iris material is its to clear");
        verify(left).setType(Material.AIR);
    }

    /**
     * Anything that is not the gate's iris material belongs to whoever put it there.
     *
     * <p>A player can build in a gate's opening, and what they built is not the plugin's to
     * remove on a version upgrade. Clearing the cells wholesale would eat it.
     */
    @Test
    void aBlockThatIsNotTheIrisMaterialIsLeftWhereItIs()
    {
        final Block someonesChest = cellHolding(64, Material.CHEST);

        final int cleared = BuiltIrisUpgrade.clearLeftover(gate);

        assertEquals(0, cleared, "a chest in the opening is somebody's, not a leftover iris");
        verify(someonesChest, never()).setType(Material.AIR);
    }

    /**
     * A cell in an unloaded chunk is left for later rather than loading the chunk.
     *
     * <p>This runs over every gate on the server at start-up. Reading a block loads its chunk,
     * so doing it unconditionally would pull a chunk into memory for every gate on the server
     * to perform a tidy-up that the draw path can just as well do when somebody walks up.
     */
    @Test
    void aCellInAnUnloadedChunkIsNotTouched()
    {
        final Block far = cellHolding(64, Material.STONE);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

        final int cleared = BuiltIrisUpgrade.clearLeftover(gate);

        assertEquals(0, cleared, "nothing in an unloaded chunk is read at start-up");
        verify(far, never()).setType(Material.AIR);
    }

    /**
     * A horizontal gate's iris really is supposed to be there.
     *
     * <p>It is still real blocks, on purpose: it is a floor. Clearing it would drop whoever
     * was standing on it and leave the gate open through the ground.
     */
    @Test
    void aHorizontalGatesIrisIsNotALeftover()
    {
        gate.setGateFacing(BlockFace.UP);
        final Block floor = cellHolding(64, Material.STONE);

        final int cleared = BuiltIrisUpgrade.clearLeftover(gate);

        assertEquals(0, cleared, "a horizontal gate's iris is real on purpose");
        verify(floor, never()).setType(Material.AIR);
    }

    /**
     * A gate whose iris is open has nothing standing in it to clear.
     */
    @Test
    void aGateWithItsIrisOpenIsLeftAlone()
    {
        gate.setGateIrisActive(false);
        final Block whateverIsThere = cellHolding(64, Material.STONE);

        final int cleared = BuiltIrisUpgrade.clearLeftover(gate);

        assertEquals(0, cleared, "an open iris means anything in the opening is not iris");
        verify(whateverIsThere, never()).setType(Material.AIR);
    }
}
