package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Getting off a mount while standing in a gate's opening.
 *
 * <p>Refused inside a wormhole, where the rider would be left standing in the portal; allowed in a
 * gate still dialling, which is only an empty frame.
 */
class GateDismountTest
{
    private static final int BX = 10, BY = 64, BZ = 20;
    private Stargate gate;
    private Player rider;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        PluginTestSupport.install(mock(WormholeXTreme.class));
        final World world = mock(World.class);
        when(world.getName()).thenReturn("w");
        final Block portal = mock(Block.class);
        when(portal.getLocation()).thenReturn(new Location(world, BX, BY, BZ));
        when(portal.getX()).thenReturn(Integer.valueOf(BX));
        when(portal.getY()).thenReturn(Integer.valueOf(BY));
        when(portal.getZ()).thenReturn(Integer.valueOf(BZ));
        when(portal.getWorld()).thenReturn(world);
        when(portal.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(portal);

        gate = new Stargate();
        gate.setGateName("mount-gate");
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGateActive(true);
        gate.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        StargateManager.addBlockIndex(portal, gate);
        StargateManager.registerStargate(gate);

        rider = mock(Player.class);
        when(rider.getLocation()).thenReturn(new Location(world, BX + 0.5, BY, BZ + 0.5));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateManager.removeStargate(gate);
        gate.setGateActive(false);
        GateSpatialIndex.clear();
        PluginTestSupport.remove();
    }

    @Test
    void gettingOffInsideAWormholeIsRefused()
    {
        gate.setGatePortalOpen(true);

        assertTrue(GateDismount.shouldRefuse(rider));
    }

    @Test
    void gettingOffInAGateStillDiallingIsAllowed()
    {
        assertFalse(GateDismount.shouldRefuse(rider), "no wormhole has formed, so there is only a frame");
    }
}
