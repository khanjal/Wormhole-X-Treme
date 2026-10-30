package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * The iris lever as it is placed and taken down, once {@code IrisLeverPlacementTest} has found it.
 *
 * <p>That test pins where the lever goes and that a non-lever is left alone on the way out. Three
 * mutations on what happens when it is placed all survived: never attaching it to the wall (so it
 * hangs on a floor face), dropping the facing, and not recording it as one of the gate's blocks,
 * which leaves it behind when the gate is removed.
 */
class IrisLeverBlockTest
{
    private Block lever;
    private Location leverAt;
    private Switch data;
    private Stargate gate;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        lever = mock(Block.class);
        leverAt = new Location(null, 3, 64, 5);
        when(lever.getLocation()).thenReturn(leverAt);
        data = mock(Switch.class);
        when(lever.getBlockData()).thenReturn(data);
        gate = new Stargate();
        gate.setGateName("irisLever");
        gate.setGateFacing(BlockFace.EAST);
        gate.setGateIrisLeverBlock(lever);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    @Test
    void theLeverHangsOnTheWallFacingTheWayTheGateFaces()
    {
        StargateBlockSetup.setupIrisLever(gate, true);

        verify(lever).setType(Material.LEVER);
        verify(data).setAttachedFace(FaceAttachable.AttachedFace.WALL);
        verify(data).setFacing(BlockFace.EAST);
        verify(lever).setBlockData(data);
    }

    @Test
    void thePlacedLeverIsOneOfTheGatesBlocks()
    {
        StargateBlockSetup.setupIrisLever(gate, true);

        assertTrue(gate.getGateStructureBlocks().contains(leverAt),
            "so removing the gate takes the lever down with it");
    }

    @Test
    void aLeverTakenDownIsNoLongerClaimed()
    {
        when(lever.getType()).thenReturn(Material.LEVER);
        gate.getGateStructureBlocks().add(leverAt);

        StargateBlockSetup.setupIrisLever(gate, false);

        verify(lever).setType(Material.AIR);
        assertFalse(gate.getGateStructureBlocks().contains(leverAt));
    }
}
