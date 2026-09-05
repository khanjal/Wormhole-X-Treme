package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A closing iris must not place its blocks inside someone who is standing in the gate.
 *
 * <p>The iris is real, server-side blocks -- it has to be, or a traveller would walk through
 * a closed one. {@code fillGateIris} placed them over the whole portal opening without
 * looking at who was there, so every path that closes an iris could bury a player: the gate
 * shutting down onto an iris that defaults closed, an activation timing out, someone flipping
 * the lever, an IDC being cleared. The reported version needs no bad luck on the player's
 * part at all -- walk into the event horizon while the far gate's shutdown timer runs out and
 * the iris re-engages around you.
 *
 * <p>These pin {@link StargateBlockSetup#isInIrisPath}, the decision the fix turns on. The
 * loop around it -- asking the world for entities, teleporting them clear -- needs a live
 * server and is left to integration; this is the part that can be wrong.
 */
public class IrisDoesNotEntombTest
{
    /** Where the test gate's portal sits: a two-high opening at y=64 and y=65. */
    private static final int BX = 10;
    private static final int BY = 64;
    private static final int BZ = 20;

    private World world;
    private Stargate gate;

    @BeforeEach
    public void setUp()
    {
        world = mock(World.class);
        when(world.getName()).thenReturn("testworld");

        gate = new Stargate();
        gate.setGateName("TestGate");
        gate.setGateWorld(world);
        gate.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        gate.getGatePortalBlocks().add(new Location(world, BX, BY + 1, BZ));
    }

    @Test
    public void someoneStandingInTheOpeningIsInTheIrisPath()
    {
        assertTrue(StargateBlockSetup.isInIrisPath(gate, new Location(world, BX, BY, BZ)),
            "A player standing in a portal block is exactly who the iris would be placed inside");
    }

    @Test
    public void someoneWhoseHeadAloneIsInTheOpeningIsStillInTheIrisPath()
    {
        // Feet on the block below the portal, head in the portal's lowest block. Checking
        // only the feet -- which is all the entity sweep needs, since it decides who
        // travels -- would let the iris close into this player's face and call it clear.
        assertTrue(StargateBlockSetup.isInIrisPath(gate, new Location(world, BX, BY - 1, BZ)),
            "The head is the half that suffocates; feet below the opening must still count");
    }

    @Test
    public void someoneStandingOnTopOfTheGateIsNotInTheIrisPath()
    {
        // Feet at BY+2, head at BY+3: both clear of the two-block opening. Moving this
        // player would teleport bystanders off the gate frame every time an iris closed.
        assertFalse(StargateBlockSetup.isInIrisPath(gate, new Location(world, BX, BY + 2, BZ)),
            "Standing above the opening is not in the way and must not be moved");
    }

    @Test
    public void someoneBesideTheGateIsNotInTheIrisPath()
    {
        assertFalse(StargateBlockSetup.isInIrisPath(gate, new Location(world, BX + 1, BY, BZ)),
            "The bounding box query is wider than the opening, so off-ring candidates must be rejected");
    }

    @Test
    public void aGateWithNoPortalBlocksPutsNobodyInTheIrisPath()
    {
        final Stargate empty = new Stargate();
        empty.setGateWorld(world);

        assertFalse(StargateBlockSetup.isInIrisPath(empty, new Location(world, BX, BY, BZ)),
            "A gate with no recorded portal has no iris to close and nobody to move");
    }

    @Test
    public void aMissingGateOrLocationIsNotInTheIrisPath()
    {
        // Reached through a Bukkit entity whose location can be anything; a null here should
        // mean "leave them alone", not take down the iris closure for every other entity.
        assertFalse(StargateBlockSetup.isInIrisPath(null, new Location(world, BX, BY, BZ)));
        assertFalse(StargateBlockSetup.isInIrisPath(gate, null));
    }
}
