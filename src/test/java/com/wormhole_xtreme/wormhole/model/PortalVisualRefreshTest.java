package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Redrawing the portal for a player who arrives after the gate opened.
 *
 * <p>The portal is not a block. The server keeps AIR in the gate so travellers do not drown
 * or burn standing in it, and every nearby client is sent a block change that makes the hole
 * look like water. That illusion lives only in the client's copy of the chunk, so the client
 * loses it the moment it is handed a fresh copy — walking out of range and back, relogging,
 * changing world, or arriving by teleport.
 *
 * <p>Teleport is the case that made this visible: the destination gate opens at dial time,
 * while the traveller is still standing at the source gate, far outside the range the portal
 * is drawn to. They were never sent it at all, so they stepped out into an empty frame.
 */
class PortalVisualRefreshTest
{
    @AfterEach
    void afterEach()
    {
        for (final Stargate gate : new java.util.ArrayList<Stargate>(StargateManager.getOpenGates()))
        {
            gate.setGateActive(false);
        }
    }

    private static Stargate openGateAt(final World world, final int x, final int y, final int z)
    {
        final Stargate gate = new Stargate();
        gate.setGateName("visual");
        gate.setGateWorld(world);
        gate.getGatePortalBlocks().add(new Location(world, x, y, z));
        gate.getGatePortalBlocks().add(new Location(world, x, y + 1, z));
        gate.setGateActive(true);
        return gate;
    }

    // -----------------------------------------------------------------------
    // The open-gate set
    // -----------------------------------------------------------------------

    @Test
    void openingAndClosingAGateTracksItInTheOpenSet()
    {
        // The refresh runs on every chunk boundary a player crosses, so it walks the open
        // gates rather than filtering all of them. That only works if the set follows the
        // flag exactly.
        final Stargate gate = new Stargate();
        assertFalse(StargateManager.getOpenGates().contains(gate), "a new gate is not open");

        gate.setGateActive(true);
        assertTrue(StargateManager.getOpenGates().contains(gate));

        gate.setGateActive(false);
        assertFalse(StargateManager.getOpenGates().contains(gate), "a closed gate must not be redrawn");
    }

    // -----------------------------------------------------------------------
    // Which gates get redrawn
    // -----------------------------------------------------------------------

    @Test
    void aNearbyOpenGateIsRedrawn()
    {
        final World world = mock(World.class);
        final Stargate gate = openGateAt(world, 100, 64, 100);

        assertTrue(StargateBlockSetup.shouldRedrawFor(gate, new Location(world, 105, 64, 100)));
    }

    @Test
    void aGateBeyondTheVisualRangeIsNotRedrawn()
    {
        final World world = mock(World.class);
        final Stargate gate = openGateAt(world, 100, 64, 100);

        // 64 is the range the open-time send uses, so the refresh has to agree with it or a
        // player would see the portal appear and disappear as they walked the boundary.
        assertTrue(StargateBlockSetup.shouldRedrawFor(gate, new Location(world, 160, 64, 100)));
        assertFalse(StargateBlockSetup.shouldRedrawFor(gate, new Location(world, 200, 64, 100)));
    }

    @Test
    void aGateInAnotherWorldIsNotRedrawn()
    {
        final World world = mock(World.class);
        final World elsewhere = mock(World.class);
        final Stargate gate = openGateAt(world, 100, 64, 100);

        assertFalse(StargateBlockSetup.shouldRedrawFor(gate, new Location(elsewhere, 100, 64, 100)),
            "same coordinates in a different world are not nearby");
    }

    @Test
    void aGateShowingAnIrisIsNotRedrawn()
    {
        // The iris is real blocks. Drawing the portal over it would show an open wormhole
        // where the gate is actually sealed, which is exactly backwards.
        final World world = mock(World.class);
        final Stargate gate = openGateAt(world, 100, 64, 100);
        gate.setGateIrisActive(true);

        assertFalse(StargateBlockSetup.shouldRedrawFor(gate, new Location(world, 101, 64, 100)));
    }

    @Test
    void aGateWithNoPortalBlocksIsNotRedrawn()
    {
        final World world = mock(World.class);
        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateActive(true);

        assertFalse(StargateBlockSetup.shouldRedrawFor(gate, new Location(world, 0, 64, 0)));
    }

    // -----------------------------------------------------------------------
    // Actually sending
    // -----------------------------------------------------------------------

    @Test
    void aNearbyOpenGateReachesTheSend()
    {
        // The send itself cannot be asserted directly here: it needs createBlockData(),
        // which goes through Bukkit.getServer(), and this project pins the subclass mock
        // maker so the static cannot be stubbed. What can be pinned is that the decision
        // hands off to the send at all — the first thing past it is that server call, which
        // fails in a specific, recognisable way with no server behind it.
        //
        // On its own that would be weak. The three tests below run the same method past the
        // same point and return cleanly, so the difference between "threw at the server
        // call" and "returned without sending" is what carries the meaning.
        final World world = mock(World.class);
        openGateAt(world, 100, 64, 100);

        final Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(world, 102, 64, 100));

        final NullPointerException thrown =
            assertThrows(NullPointerException.class, () -> StargateBlockSetup.refreshPortalVisuals(player));
        assertTrue(String.valueOf(thrown.getMessage()).contains("createBlockData"),
            "should have failed reaching the block data for the send, but got: " + thrown.getMessage());
    }

    @Test
    void aPlayerFarFromEveryGateIsSentNothing()
    {
        // The control for the test above: without it that one would still pass if the
        // range check were dropped entirely.
        final World world = mock(World.class);
        openGateAt(world, 100, 64, 100);

        final Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(world, 900, 64, 900));

        StargateBlockSetup.refreshPortalVisuals(player);

        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    @Test
    void anOfflinePlayerIsSentNothing()
    {
        final World world = mock(World.class);
        openGateAt(world, 100, 64, 100);

        final Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(false);

        StargateBlockSetup.refreshPortalVisuals(player);

        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    @Test
    void aClosedGateIsSentToNobodyEvenStandingInIt()
    {
        // Closing removes the gate from the open set, which is the only thing stopping the
        // refresh from painting a portal back over a gate that has just shut down.
        final World world = mock(World.class);
        final Stargate gate = openGateAt(world, 100, 64, 100);
        gate.setGateActive(false);

        final Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(world, 100, 64, 100));

        StargateBlockSetup.refreshPortalVisuals(player);

        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }
}
