package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Noticing that a gate is not standing where it says it is.
 *
 * <p>Nothing fires an event when WorldEdit writes blocks, so there is no moment to react to --
 * {@code //replace obsidian air} across a gate leaves it fully registered with nothing there.
 * This is the check that runs at the moments that do exist: redrawing a dial sign, and dialling.
 */
class GateIntegrityTest
{
    private World world;

    @BeforeEach
    void setUp()
    {
        world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(Boolean.TRUE);
    }

    /** A block at these coordinates made of this material, wired into the mock world. */
    private Location blockAt(final int x, final int y, final int z, final Material material)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getZ()).thenReturn(z);
        final Location location = new Location(world, x, y, z);
        when(world.getBlockAt(x, y, z)).thenReturn(block);
        when(world.getBlockAt(location)).thenReturn(block);
        return location;
    }

    private static Stargate gateWith(final List<Location> structure)
    {
        final Stargate gate = mock(Stargate.class);
        when(gate.getGateStructureBlocks()).thenReturn(structure);
        return gate;
    }

    @Test
    void aGateStillStandingHasNothingMissing()
    {
        final Stargate gate = gateWith(Arrays.asList(
            blockAt(0, 64, 0, Material.OBSIDIAN),
            blockAt(1, 64, 0, Material.OBSIDIAN)));

        assertEquals(0, GateIntegrity.missingStructureBlocks(gate));
        assertFalse(GateIntegrity.isStructureBroken(gate));
    }

    @Test
    void everyFrameBlockTurnedToAirIsCounted()
    {
        final Stargate gate = gateWith(Arrays.asList(
            blockAt(0, 64, 0, Material.AIR),
            blockAt(1, 64, 0, Material.OBSIDIAN),
            blockAt(2, 64, 0, Material.AIR)));

        assertEquals(2, GateIntegrity.missingStructureBlocks(gate));
        assertTrue(GateIntegrity.isStructureBroken(gate));
    }

    /**
     * One block is enough.
     *
     * <p>No proportion, no threshold: a ring with a hole in it will not detect as a shape, the
     * animation draws against blocks that are not there, and a traveller arrives inside whatever
     * replaced it. There is no partial state worth being lenient about.
     */
    @Test
    void oneMissingBlockIsAlreadyBroken()
    {
        final List<Location> structure = new ArrayList<>();
        for (int x = 0; x < 20; x++)
        {
            structure.add(blockAt(x, 64, 0, x == 7 ? Material.AIR : Material.OBSIDIAN));
        }

        assertTrue(GateIntegrity.isStructureBroken(gateWith(structure)));
        assertEquals(1, GateIntegrity.missingStructureBlocks(gateWith(structure)));
    }

    /**
     * A gate whose material somebody changed is not a broken gate.
     *
     * <p>Only air counts. Gates are built of whatever the shape allows and the material can be
     * changed under one deliberately, so "not obsidian any more" would refuse to dial gates that
     * are perfectly fine.
     */
    @Test
    void aFrameRebuiltInAnotherMaterialIsStillAFrame()
    {
        final Stargate gate = gateWith(Arrays.asList(
            blockAt(0, 64, 0, Material.STONE),
            blockAt(1, 64, 0, Material.GLASS)));

        assertFalse(GateIntegrity.isStructureBroken(gate));
    }

    /**
     * An unloaded chunk reads as intact, and is never touched.
     *
     * <p>Reading a block pulls its chunk into memory, and this runs on the dial path. So the
     * check has to stop at the chunk boundary rather than find out -- and "cannot tell" has to
     * mean "fine", or every gate whose far end nobody has visited would refuse to dial.
     */
    @Test
    void anUnloadedChunkIsLeftAloneRatherThanLoadedToCheck()
    {
        final Location far = blockAt(500, 64, 500, Material.AIR);
        when(world.isChunkLoaded(500 >> 4, 500 >> 4)).thenReturn(Boolean.FALSE);

        assertEquals(0, GateIntegrity.missingStructureBlocks(gateWith(Collections.singletonList(far))),
            "an unloaded chunk cannot be judged, so it is not");
        verify(world, org.mockito.Mockito.never()).getBlockAt(any(Location.class));
    }

    @Test
    void aGateWithNoRecordedBlocksIsNotBroken()
    {
        assertFalse(GateIntegrity.isStructureBroken(gateWith(Collections.emptyList())));
        assertEquals(0, GateIntegrity.missingStructureBlocks(null));
        assertFalse(GateIntegrity.isStructureBroken(null));
    }

    // ---- the dial sign ----

    private Stargate signGate(final Material signMaterial, final boolean signPowered)
    {
        final Stargate gate = mock(Stargate.class);
        when(gate.isGateSignPowered()).thenReturn(signPowered);
        final Block sign = mock(Block.class);
        when(sign.getType()).thenReturn(signMaterial);
        when(sign.getWorld()).thenReturn(world);
        when(sign.getX()).thenReturn(3);
        when(sign.getZ()).thenReturn(4);
        when(gate.getGateDialSignBlock()).thenReturn(sign);
        return gate;
    }

    @Test
    void aDialSignStillThereIsNotMissing()
    {
        assertFalse(GateIntegrity.isDialSignMissing(signGate(Material.OAK_WALL_SIGN, true)));
    }

    /** The 2011 report exactly: the gate keeps working except that no target can be chosen. */
    @Test
    void aDialSignReplacedByAirIsMissing()
    {
        assertTrue(GateIntegrity.isDialSignMissing(signGate(Material.AIR, true)));
    }

    /** A gate that was never sign-powered has no sign to lose. */
    @Test
    void aGateThatIsNotSignPoweredIsNotMissingASign()
    {
        assertFalse(GateIntegrity.isDialSignMissing(signGate(Material.AIR, false)));
    }

    @Test
    void aDialSignInAnUnloadedChunkIsNotJudged()
    {
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(Boolean.FALSE);

        assertFalse(GateIntegrity.isDialSignMissing(signGate(Material.AIR, true)));
    }
}
