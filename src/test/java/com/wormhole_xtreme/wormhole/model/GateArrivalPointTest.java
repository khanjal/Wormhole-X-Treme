package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

/**
 * Travellers step out of a wormhole rather than standing in it.
 *
 * <p>Arriving inside the ring means appearing waist deep in the portal material, floating on
 * water the server does not actually have, and having to walk clear before anything looks
 * right. Gates built today already place the arrival point a block outside, but that offset
 * arrived after gates had been built and their arrival point written to disk — and loading
 * restores what was stored rather than recomputing it, so fixing the build path never
 * reached the gates that already existed.
 *
 * <p>The correction asks the question directly rather than keying off a format version: is
 * the arrival point one of my own portal blocks? A gate that is already right is left alone
 * whatever wrote it, and a wrong one is fixed however it got that way.
 */
public class GateArrivalPointTest
{
    private static final int BX = 10, BY = 64, BZ = 20;

    /** A gate facing south with a two-block-tall portal at BX,BZ. */
    private static Stargate gateFacingSouth(final World world)
    {
        final Stargate gate = new Stargate();
        gate.setGateName("arrival");
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.SOUTH);
        gate.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        gate.getGatePortalBlocks().add(new Location(world, BX, BY + 1, BZ));
        return gate;
    }

    @Test
    public void anArrivalPointInsideThePortalIsMovedOut()
    {
        final World world = mock(World.class);
        final Stargate gate = gateFacingSouth(world);
        gate.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ + 0.5));

        assertTrue(gate.normalizeGatePlayerTeleportLocation(), "should report that it moved the point");

        final Location moved = gate.getGatePlayerTeleportLocation();
        assertFalse(gate.isGatePortalBlockAt(moved.getBlockX(), moved.getBlockY(), moved.getBlockZ()),
            "the whole point is that the arrival block is no longer part of the portal");
        // SOUTH is +Z, so stepping out of the ring lands one block further along Z.
        assertEquals(BZ + 1.5, moved.getZ(), 1e-9);
        assertEquals(BX + 0.5, moved.getX(), 1e-9, "sideways position should be untouched");
        assertEquals(BY, moved.getY(), 1e-9, "height should be untouched");
    }

    @Test
    public void anArrivalPointAlreadyOutsideIsLeftAlone()
    {
        final World world = mock(World.class);
        final Stargate gate = gateFacingSouth(world);
        final Location outside = new Location(world, BX + 0.5, BY, BZ + 1.5);
        gate.setGatePlayerTeleportLocation(outside);

        assertFalse(gate.normalizeGatePlayerTeleportLocation(), "nothing to correct");
        assertEquals(BZ + 1.5, gate.getGatePlayerTeleportLocation().getZ(), 1e-9);
    }

    @Test
    public void theDirectionTheTravellerFacesIsPreserved()
    {
        // Arriving should still look out of the gate the way it always did; only the
        // standing position changes.
        final World world = mock(World.class);
        final Stargate gate = gateFacingSouth(world);
        final Location inside = new Location(world, BX + 0.5, BY, BZ + 0.5);
        inside.setYaw(123.0f);
        inside.setPitch(-7.0f);
        gate.setGatePlayerTeleportLocation(inside);

        gate.normalizeGatePlayerTeleportLocation();

        assertEquals(123.0f, gate.getGatePlayerTeleportLocation().getYaw(), 1e-6);
        assertEquals(-7.0f, gate.getGatePlayerTeleportLocation().getPitch(), 1e-6);
    }

    @Test
    public void aDeeperPortalIsSteppedAllTheWayOut()
    {
        // A portal more than one block thick needs more than one step, and stopping after
        // one would leave the traveller still inside it.
        final World world = mock(World.class);
        final Stargate gate = gateFacingSouth(world);
        gate.getGatePortalBlocks().add(new Location(world, BX, BY, BZ + 1));
        gate.getGatePortalBlocks().add(new Location(world, BX, BY, BZ + 2));
        gate.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ + 0.5));

        assertTrue(gate.normalizeGatePlayerTeleportLocation());

        final Location moved = gate.getGatePlayerTeleportLocation();
        assertFalse(gate.isGatePortalBlockAt(moved.getBlockX(), moved.getBlockY(), moved.getBlockZ()));
        assertEquals(BZ + 3.5, moved.getZ(), 1e-9);
    }

    @Test
    public void eachFacingStepsOutAlongItsOwnAxis()
    {
        for (final BlockFace facing : new BlockFace[] {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST })
        {
            final World world = mock(World.class);
            final Stargate gate = new Stargate();
            gate.setGateWorld(world);
            gate.setGateFacing(facing);
            gate.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
            gate.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ + 0.5));

            assertTrue(gate.normalizeGatePlayerTeleportLocation(), facing + " should have moved");

            final Location moved = gate.getGatePlayerTeleportLocation();
            assertEquals(BX + 0.5 + facing.getModX(), moved.getX(), 1e-9, facing + " x");
            assertEquals(BZ + 0.5 + facing.getModZ(), moved.getZ(), 1e-9, facing + " z");
        }
    }

    @Test
    public void aGateWithNoFacingOrNoArrivalPointIsNotTouched()
    {
        final World world = mock(World.class);

        final Stargate noFacing = gateFacingSouth(world);
        noFacing.setGateFacing(null);
        noFacing.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ + 0.5));
        assertFalse(noFacing.normalizeGatePlayerTeleportLocation());

        final Stargate noPoint = gateFacingSouth(world);
        noPoint.setGatePlayerTeleportLocation(null);
        assertFalse(noPoint.normalizeGatePlayerTeleportLocation());
    }

    @Test
    public void aPortalTheFacingCannotEscapeGivesUpRatherThanWalkingAway()
    {
        // Defensive: if stepping along the facing never leaves the portal, the traveller is
        // better off where they were than teleported somewhere arbitrarily far off.
        final World world = mock(World.class);
        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.SOUTH);
        for (int z = 0; z < 20; z++)
        {
            gate.getGatePortalBlocks().add(new Location(world, BX, BY, BZ + z));
        }
        final Location inside = new Location(world, BX + 0.5, BY, BZ + 0.5);
        gate.setGatePlayerTeleportLocation(inside);

        assertFalse(gate.normalizeGatePlayerTeleportLocation(), "should report it could not fix this");
        assertEquals(BZ + 0.5, gate.getGatePlayerTeleportLocation().getZ(), 1e-9, "and leave it untouched");
    }
}
