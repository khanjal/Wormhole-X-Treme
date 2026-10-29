package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * Where a gate's travellers arrive, over a portal given as a list of blocks (#430).
 *
 * <p>Written for the move out of {@link Stargate}, on the conditions the older arrival tests left
 * unpinned: each test below fails when the one condition it is named for is taken out of
 * {@link PortalExit}.
 */
class PortalExitTest
{
    private World world;

    @BeforeEach
    void setUp()
    {
        world = mock(World.class);
    }

    private Location at(final double x, final double y, final double z)
    {
        return new Location(world, x, y, z);
    }

    private Location block(final int x, final int y, final int z)
    {
        return new Location(world, x, y, z);
    }

    private static PortalExit.Inside inside(final List<Location> portal)
    {
        return (x, y, z) -> portal.stream().anyMatch(block -> (block.getBlockX() == x) && (block.getBlockY() == y)
            && (block.getBlockZ() == z));
    }

    /** A portal one block wide along x and two high, {@code depth} blocks thick towards -z from z 5. */
    private List<Location> upright(final int depth)
    {
        final List<Location> portal = new ArrayList<>();
        for (int thick = 0; thick < depth; thick++)
        {
            portal.add(block(5, 64, 5 - thick));
            portal.add(block(5, 65, 5 - thick));
        }
        return portal;
    }

    // --- Whether a portal lies flat ---

    @Test
    void aPortalIsFlatOnlyWhenItHasBlocksAndAllShareOneHeight()
    {
        assertFalse(PortalExit.isFlat(List.of()), "no blocks, nothing lies flat");
        assertTrue(PortalExit.isFlat(List.of(block(1, 64, 1))), "one block");
        assertTrue(PortalExit.isFlat(List.of(block(1, 64, 1), block(2, 64, 1), block(2, 64, 2))), "one height");
        assertFalse(PortalExit.isFlat(List.of(block(1, 64, 1), block(1, 65, 1))), "two heights");
        assertFalse(PortalExit.isFlat(List.of(block(1, 64, 1), block(2, 64, 1), block(2, 65, 2))), "the last one differs");
    }

    // --- Moving an arrival point out of the portal ---

    @Test
    void anArrivalPointInsideAnUprightPortalIsMovedOneStepAlongTheFacingKeepingItsTurn()
    {
        final List<Location> portal = upright(1);
        final Location current = new Location(world, 5.5, 64.0, 5.5, 33.0f, 7.0f);

        final Location moved = PortalExit.normalized(current, BlockFace.NORTH, world, portal, inside(portal));

        assertNotNull(moved, "it was inside");
        assertEquals(5.5, moved.getX(), 1e-9, "x as it was");
        assertEquals(64.0, moved.getY(), 1e-9, "the height as it was");
        assertEquals(4.5, moved.getZ(), 1e-9, "one block along the facing, which is north");
        assertEquals(33.0f, moved.getYaw(), 1e-6, "turned as it was");
        assertEquals(7.0f, moved.getPitch(), 1e-6, "and pitched");
        assertSame(world, moved.getWorld(), "in the gate's world");
    }

    @Test
    void aDeeperPortalIsLeftAtTheFirstStepOutAndOneTooDeepIsNotLeftAtAll()
    {
        final Location current = new Location(world, 5.5, 64.0, 5.5);

        final List<Location> fourDeep = upright(4);
        final Location moved = PortalExit.normalized(current, BlockFace.NORTH, world, fourDeep, inside(fourDeep));
        assertNotNull(moved, "four blocks thick: out at the fourth step");
        assertEquals(1.5, moved.getZ(), 1e-9, "four blocks along");

        final List<Location> fiveDeep = upright(5);
        assertNull(PortalExit.normalized(current, BlockFace.NORTH, world, fiveDeep, inside(fiveDeep)),
            "five blocks thick: the traveller is not walked away for ever");
    }

    @Test
    void anArrivalPointInAFlatPortalIsMovedUpWhateverTheFacing()
    {
        final List<Location> portal = List.of(block(5, 64, 5), block(6, 64, 5), block(5, 64, 6));
        final Location current = new Location(world, 5.5, 64.0, 5.5);

        for (final BlockFace facing : List.of(BlockFace.NORTH, BlockFace.EAST))
        {
            final Location moved = PortalExit.normalized(current, facing, world, portal, inside(portal));

            assertNotNull(moved, "flat, facing " + facing);
            assertEquals(5.5, moved.getX(), 1e-9, "x as it was");
            assertEquals(65.0, moved.getY(), 1e-9, "one block up");
            assertEquals(5.5, moved.getZ(), 1e-9, "z as it was");
        }
    }

    @Test
    void anArrivalPointInAnUprightPortalFacingUpIsMovedUpPastTheBlocksOfThePortal()
    {
        final List<Location> portal = upright(1);
        final Location current = new Location(world, 5.5, 64.0, 5.5);

        // Straight up from (5, 64) goes into the second block of the portal, then out at 66.
        final Location moved = PortalExit.normalized(current, BlockFace.UP, world, portal, inside(portal));

        assertNotNull(moved, "an upright portal left along y");
        assertEquals(66.0, moved.getY(), 1e-9, "two steps up, the second block of the portal being in the way");
    }

    @Test
    void anArrivalPointIsLeftAloneWhenItIsNotInThePortalOrThereIsNoFacingOrNoPoint()
    {
        final List<Location> portal = upright(1);

        assertNull(PortalExit.normalized(new Location(world, 9.5, 64.0, 9.5), BlockFace.NORTH, world, portal, inside(portal)),
            "outside the portal already");
        assertNull(PortalExit.normalized(new Location(world, 5.5, 64.0, 5.5), null, world, portal, inside(portal)),
            "no facing");
        assertNull(PortalExit.normalized(null, BlockFace.NORTH, world, portal, inside(portal)), "no arrival point");
    }

    // --- Working the arrival point out again ---

    @Test
    void theArrivalPointIsTheMiddleOfTheBottomRowOneStepAlongTheFacingWithTheFacingsYaw()
    {
        // Bottom row at height 64 (x 4, 4, 5, z 6, 7, 7), and a higher block that must not count.
        final List<Location> portal = List.of(block(4, 64, 6), block(4, 64, 7), block(5, 64, 7), block(9, 66, 9));

        final Location fixed = PortalExit.recomputed(world, BlockFace.EAST, portal);

        assertNotNull(fixed);
        // x: 13 / 3 is 4.33, which rounds to 4; the centre of that block is 4.5; one step east is 5.5.
        assertEquals(5.5, fixed.getX(), 1e-9, "x rounded, centred, a step east");
        // z: 20 / 3 is 6.67, which rounds to 7; the centre of that block is 7.5; east is not along z.
        assertEquals(7.5, fixed.getZ(), 1e-9, "z rounded, centred");
        assertEquals(64.0, fixed.getY(), 1e-9, "at the floor of the bottom row");
        assertEquals(WorldUtils.getDegreesFromBlockFace(BlockFace.EAST), fixed.getYaw(), 1e-6, "turned the way it faces");
        assertEquals(0.0f, fixed.getPitch(), 1e-6, "level");
    }

    @Test
    void theAverageOfTheBottomRowIsRoundedNotFlooredOrCeiled()
    {
        // x: 4, 5, 5 is 4.67, which rounds to 5 (floored it is 4). z: 6, 6, 7 is 6.33, which rounds to 6 (ceiled, 7).
        final List<Location> portal = List.of(block(4, 64, 6), block(5, 64, 6), block(5, 64, 7), block(6, 66, 6));

        final Location fixed = PortalExit.recomputed(world, BlockFace.NORTH, portal);

        assertNotNull(fixed);
        assertEquals(5.5, fixed.getX(), 1e-9, "4.67 rounds up");
        assertEquals(5.5, fixed.getZ(), 1e-9, "6.33 rounds down to 6, centred at 6.5, a step north");
    }

    @Test
    void aFlatPortalIsLeftUpwardAndNotAlongItsFacing()
    {
        final List<Location> portal = List.of(block(4, 64, 6), block(5, 64, 6), block(5, 64, 7), block(4, 64, 7));

        final Location north = PortalExit.recomputed(world, BlockFace.NORTH, portal);
        final Location east = PortalExit.recomputed(world, BlockFace.EAST, portal);

        assertNotNull(north);
        assertNotNull(east);
        assertEquals(65.0, north.getY(), 1e-9, "one up");
        assertEquals(5.5, north.getX(), 1e-9, "x as the middle: 18 / 4 rounds to 5, centred at 5.5 -- not stepped");
        assertEquals(7.5, north.getZ(), 1e-9, "z as the middle: 26 / 4 rounds to 7, centred at 7.5 -- not stepped");
        assertEquals(east.getX(), north.getX(), 1e-9, "the same whatever the facing: x");
        assertEquals(east.getZ(), north.getZ(), 1e-9, "and z");
    }

    @Test
    void anUprightPortalIsLeftAlongItsFacingAtTheFloor()
    {
        final List<Location> portal = List.of(block(5, 64, 5), block(5, 65, 5));

        final Location fixed = PortalExit.recomputed(world, BlockFace.NORTH, portal);

        assertNotNull(fixed);
        assertEquals(5.5, fixed.getX(), 1e-9, "x centred");
        assertEquals(64.0, fixed.getY(), 1e-9, "not raised");
        assertEquals(4.5, fixed.getZ(), 1e-9, "a block north of the centre of z 5");
    }

    @Test
    void noWorldNoFacingNoPortalOrNoDirectionWorksNothingOut()
    {
        final List<Location> portal = List.of(block(5, 64, 5), block(5, 65, 5));

        assertNull(PortalExit.recomputed(null, BlockFace.NORTH, portal), "no world");
        assertNull(PortalExit.recomputed(world, null, portal), "no facing");
        assertNull(PortalExit.recomputed(world, BlockFace.NORTH, null), "no portal");
        assertNull(PortalExit.recomputed(world, BlockFace.NORTH, List.of()), "an empty portal");
        assertNull(PortalExit.recomputed(world, BlockFace.SELF, portal), "a facing with no direction");
    }
}
