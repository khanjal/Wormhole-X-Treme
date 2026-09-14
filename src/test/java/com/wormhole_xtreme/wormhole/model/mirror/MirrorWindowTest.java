package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;

/**
 * The shape of a window mirror, and where each block drawn behind it comes from.
 *
 * <p>Everything a viewer sees depends on this arithmetic, and the mistakes it invites are ones
 * that still look like a view: a far side shown mirror-image, shown a layer too deep, drawn on
 * the viewer's own side where it would bury them, showing past the edges of the opening, or cut
 * short at the sides of a deep view.
 */
class MirrorWindowTest
{
    /** Where most of these travel: the arrival block is (100, 70, -21). */
    private static MirrorPoint far(final float yaw)
    {
        return new MirrorPoint("far", 100.5, 70.0, -20.5, yaw, 0.0f);
    }

    /** A banner at the origin hung on a wall, facing north, so its wall is the block to the south. */
    private static MirrorWindow northFacing(final float farYaw)
    {
        return MirrorWindow.of(new MirrorBlock("world", 0, 64, 0), BlockFace.NORTH, false,
            far(farYaw));
    }

    private static Set<Spot> opening(final MirrorWindow window)
    {
        final Set<Spot> opening = new HashSet<>();
        window.forEachOpening((x, y, z) -> opening.add(new Spot(x, y, z)));
        return opening;
    }

    /** Every candidate from an eye out to a radius, band by band, in the order handed out. */
    private static List<Spot> candidates(final MirrorWindow window, final double x,
        final double y, final double z, final double radius, final MirrorWindow.Limits limits)
    {
        final List<Spot> seen = new ArrayList<>();
        for (int band = 0; band < MirrorWindow.bands(); band++)
        {
            window.forEachCandidate(x, y, z, radius, band, limits, (cx, cy, cz) -> seen.add(new Spot(cx, cy, cz)));
        }
        return seen;
    }

    private static List<Spot> candidates(final MirrorWindow window, final double x,
        final double y, final double z, final double radius)
    {
        return candidates(window, x, y, z, radius, MirrorWindow.UNLIMITED);
    }


    /**
     * The opening is the banner's size, in the wall behind it, hanging down from its row.
     *
     * <p>One wide and two tall, the way a banner's cloth is. It began as a 3×3, which made a row
     * of banners a block apart into one long hole.
     */
    @Test
    void aWallBannersOpeningIsItsOwnColumnTwoTallInTheWall()
    {
        assertEquals(Set.of(new Spot(0, 63, 1), new Spot(0, 64, 1)), opening(northFacing(0.0f)));
    }

    @Test
    void aBannerFacingEastOpensInTheWallToItsWest()
    {
        assertEquals(Set.of(new Spot(-1, 63, 0), new Spot(-1, 64, 0)),
            opening(MirrorWindow.of(new MirrorBlock("world", 0, 64, 0), BlockFace.EAST, false,
                far(0.0f))));
    }

    /**
     * A freestanding banner stands up, so its opening runs up from where it stands.
     *
     * <p>A wall banner's opening hangs down from the banner's row. Using that for a standing one
     * would put the opening in the floor.
     */
    @Test
    void aStandingBannerOpensUpwardsFromItsOwnRow()
    {
        assertEquals(Set.of(new Spot(0, 64, 1), new Spot(0, 65, 1)),
            opening(MirrorWindow.of(new MirrorBlock("world", 0, 64, 0), BlockFace.NORTH, true,
                far(0.0f))));
    }

    @Test
    void aStandingBannerSnapsToTheNearestCardinal()
    {
        final MirrorBlock banner = new MirrorBlock("world", 0, 64, 0);

        assertEquals(new Spot(0, 0, 1),
            MirrorWindow.of(banner, BlockFace.NORTH_NORTH_EAST, true, far(0.0f)).into(), "north");
        assertEquals(new Spot(-1, 0, 0),
            MirrorWindow.of(banner, BlockFace.EAST_NORTH_EAST, true, far(0.0f)).into(), "east");
        assertEquals(new Spot(0, 0, 1),
            MirrorWindow.of(banner, BlockFace.NORTH_EAST, true, far(0.0f)).into(),
            "exactly between, north or south wins so the answer never changes");
    }

    /**
     * Straight through the bottom of the opening is exactly where a traveller lands.
     *
     * <p>Whatever way the far side faces. Get this wrong by a layer and the view is of the block
     * in front of the arrival point, so stepping through never lands where it looked like.
     */
    @Test
    void justBehindTheBottomOfTheOpeningIsTheArrivalBlock()
    {
        for (final float yaw : new float[] { 0.0f, 90.0f, 180.0f, -90.0f })
        {
            assertEquals(new Spot(100, 70, -21), northFacing(yaw).farOf(0, 63, 2),
                "for a far side facing yaw " + yaw);
        }
    }

    /**
     * Right through the opening is right at the far side, when the two face different ways.
     *
     * <p>Looking in through a north-facing banner is looking south, so right is west. Arriving
     * facing east, right is south. A window that swapped those would be a reflection.
     */
    @Test
    void rightThroughTheOpeningIsRightAtTheFarSide()
    {
        assertEquals(new Spot(100, 70, -20), northFacing(-90.0f).farOf(-1, 63, 2));
    }

    @Test
    void deeperBehindTheOpeningIsFurtherAheadAtTheFarSide()
    {
        assertEquals(new Spot(104, 70, -21), northFacing(-90.0f).farOf(0, 63, 6));
    }

    @Test
    void higherBehindTheOpeningIsHigherAtTheFarSide()
    {
        assertEquals(new Spot(100, 73, -21), northFacing(-90.0f).farOf(0, 66, 2));
    }

    /**
     * Candidates are behind the opening, nearest layer first, out to the radius.
     *
     * <p>Nothing on the viewer's side, where a drawn block would stand where they are standing,
     * and no layer whose nearest face is further from the eye than the radius.
     */
    @Test
    void candidatesAreBehindTheOpeningNearestFirstAndOutToTheRadius()
    {
        final List<Spot> seen = candidates(northFacing(0.0f), 0.5, 64.0, -3.0, 20);

        assertEquals(2, seen.get(0).z(), "the first layer behind the opening comes first");
        assertTrue(seen.stream().allMatch(spot -> (spot.z() >= 2) && (spot.z() <= 17)),
            "all of them behind the opening, and none past the radius");
        assertTrue(seen.contains(new Spot(0, 63, 2)), "straight behind");
        assertTrue(seen.contains(new Spot(0, 63, 17)), "as far as the radius reaches");
    }

    /**
     * Only the cone through the opening is walked, and it widens with distance.
     *
     * <p>A fixed box around the opening cut a deep view short at its sides; walking a box big
     * enough not to would be most of the cost for blocks nobody can see.
     */
    @Test
    void candidatesAreTheConeThroughTheOpeningWideningWithDistance()
    {
        final List<Spot> seen = candidates(northFacing(0.0f), 0.5, 64.0, -3.0, 20);

        assertFalse(seen.contains(new Spot(3, 63, 2)), "no line through the opening reaches this");
        final long nearLayer = seen.stream().filter(spot -> spot.z() == 2).count();
        final long farLayer = seen.stream().filter(spot -> spot.z() == 12).count();
        assertTrue(farLayer > (3 * nearLayer), nearLayer + " near, " + farLayer + " far");
    }

    /**
     * The walk is bounded by distance from the eye, not depth, so it is bounded right up close.
     *
     * <p>From a few tenths of a block the cone is nearly half a sphere. Bounded by depth it was
     * hundreds of thousands of blocks; half a sphere of radius 16 is about nine thousand.
     */
    @Test
    void rightUpAgainstAMirrorTheWalkIsStillOnlyHalfASphere()
    {
        final List<Spot> seen = candidates(northFacing(0.0f), 0.5, 64.0, 0.6, 16);

        assertTrue(seen.size() < 12_000, seen.size() + " blocks walked");
        // The walk's own bound is a box around the sphere; the exact test is per block, after.
        assertTrue(seen.stream().allMatch(spot -> (spot.z() <= 16) && (distance(spot, 0.5, 64.0, 0.6) < 31.0)),
            "none past the radius along the axis, and none outside its box");
    }

    /**
     * The turn a far-side block's facing needs follows the arrival yaw.
     *
     * <p>A far side turned round showed glass panes that did not join: their connections are
     * compass directions, and positions were turned but the blocks at them were not.
     */
    @Test
    void theQuarterTurnsFromTheFarSideToThisOneFollowTheArrivalYaw()
    {
        assertEquals(0, northFacing(0.0f).quarterTurns(), "arriving facing south, the way a viewer looks in");
        assertEquals(2, northFacing(180.0f).quarterTurns(), "arriving facing north: turned right round");
        assertEquals(3, northFacing(90.0f).quarterTurns(), "arriving facing west: a quarter turn anticlockwise");
        assertEquals(1, northFacing(-90.0f).quarterTurns(), "arriving facing east: a quarter turn clockwise");
    }

    /** A direction through the opening turns the way the block mapping turns. */
    @Test
    void aDirectionThroughTheOpeningTurnsWithTheFarSide()
    {
        final MirrorWindow window = northFacing(-90.0f);

        assertArrayEquals(new double[] { 1.0, 0.0, 0.0 }, window.farDirection(0.0, 0.0, 1.0), 1.0e-9);
        assertArrayEquals(new double[] { 0.0, 0.0, 1.0 }, window.farDirection(-1.0, 0.0, 0.0), 1.0e-9);
        assertArrayEquals(new double[] { 0.0, -0.5, 0.0 }, window.farDirection(0.0, -0.5, 0.0), 1.0e-9);
    }

    private static double distance(final Spot spot, final double x, final double y, final double z)
    {
        final double dx = (spot.x() + 0.5) - x;
        final double dy = (spot.y() + 0.5) - y;
        final double dz = (spot.z() + 0.5) - z;
        return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    }

    /**
     * Above a column's top, nothing is offered.
     *
     * <p>Sky over sky changes nothing on the client and outdoors is most of the cone, so a walk
     * that offered it spent half its budget on nothing.
     */
    @Test
    void aboveAColumnsTopNothingIsOffered()
    {
        final double ex = 0.5;
        final double ey = 64.0;
        final double ez = -3.0;
        final int radius = 8;
        final int top = 63;
        final MirrorWindow.Limits skyAbove = new MirrorWindow.Limits()
        {
            @Override
            public int deepest()
            {
                return Integer.MAX_VALUE;
            }

            @Override
            public int top(final int x, final int z)
            {
                return top;
            }
        };
        final List<Spot> seen = candidates(northFacing(0.0f), ex, ey, ez, radius, skyAbove);
        final List<Spot> sky = seen.stream().filter(spot -> spot.y() > top).toList();
        final List<Spot> all = candidates(northFacing(0.0f), ex, ey, ez, radius);

        assertTrue(sky.isEmpty(), sky.size() + " offered above the top");
        assertTrue(all.stream().anyMatch(spot -> spot.y() > top), "which the full walk does offer");
        assertTrue(seen.stream().anyMatch(spot -> spot.y() <= top), "and the column below it still is");
        assertTrue(all.containsAll(seen), "without offering anything the full walk did not");
    }

    @Test
    void aCandidateWalkStopsWhenAsked()
    {
        final List<Spot> seen = new ArrayList<>();
        final boolean finished = northFacing(0.0f).forEachCandidate(0.5, 64.0, -3.0, 20, 0,
            MirrorWindow.UNLIMITED, (x, y, z) ->
            {
                seen.add(new Spot(x, y, z));
                return false;
            });

        assertEquals(1, seen.size());
        assertFalse(finished, "and says it was stopped, so the next band is not walked either");
    }

    /**
     * Right up against a mirror, the middle of the view is walked to full depth before its edges.
     *
     * <p>From there the cone is hundreds of thousands of blocks and a redraw runs out of budget.
     * Walked a layer at a time, running out cut the view short in every direction, and the real
     * world showed straight through the middle a few blocks back.
     */
    @Test
    void rightUpAgainstAMirrorTheMiddleIsWalkedToFullDepthBeforeTheEdges()
    {
        final List<Spot> seen = candidates(northFacing(0.0f), 0.5, 64.0, 0.6, 48);

        final int deepMiddle = seen.indexOf(new Spot(0, 64, 48));
        final int nearEdge = seen.indexOf(new Spot(2, 64, 2));
        assertTrue((deepMiddle >= 0) && (nearEdge >= 0), "both are in the view");
        assertTrue(deepMiddle < nearEdge, "the deep middle first: " + deepMiddle + " vs " + nearEdge);
        assertEquals(seen.size(), new HashSet<>(seen).size(), "and nothing is walked twice");
    }

    /** Nothing deeper than the deepest layer allowed is walked. */
    @Test
    void nothingDeeperThanAllowedIsWalked()
    {
        final List<Spot> seen = candidates(northFacing(0.0f), 0.5, 64.0, -3.0, 20, () -> 5);

        assertTrue(seen.stream().allMatch(spot -> spot.z() <= 6));
        assertTrue(seen.contains(new Spot(0, 63, 6)), "down to that layer itself");
    }

    @Test
    void onlyTheBannersSideOfTheWallIsInFront()
    {
        final MirrorWindow window = northFacing(0.0f);

        assertTrue(window.inFront(0.5, -3.0), "out in the room");
        assertTrue(window.inFront(0.5, 0.9), "standing in the banner's own block");
        assertFalse(window.inFront(0.5, 1.2), "inside the wall");
        assertFalse(window.inFront(0.5, 4.0), "behind it");
    }

    @Test
    void theOpeningIsOnlyTheTwoBlocksBehindTheBanner()
    {
        final MirrorWindow window = northFacing(0.0f);

        assertTrue(window.isOpening(0, 63, 1));
        assertTrue(window.isOpening(0, 64, 1));
        assertFalse(window.isOpening(1, 63, 1), "beside it");
        assertFalse(window.isOpening(0, 65, 1), "above it");
        assertFalse(window.isOpening(0, 62, 1), "below it");
        assertFalse(window.isOpening(0, 63, 2), "behind the wall");
        assertFalse(window.isOpening(0, 63, 0), "in front of the wall");
    }

    @Test
    void aBannerFacingNoUsableWayOrAMirrorGoingNowhereMakesNoWindow()
    {
        final MirrorBlock banner = new MirrorBlock("world", 0, 64, 0);

        assertNull(MirrorWindow.of(banner, BlockFace.NORTH_EAST, false, far(0.0f)),
            "a wall banner only ever faces a cardinal, so anything else is not one");
        assertNull(MirrorWindow.of(banner, null, false, far(0.0f)));
        assertNull(MirrorWindow.of(banner, BlockFace.UP, true, far(0.0f)));
        assertNull(MirrorWindow.of(banner, BlockFace.NORTH, false, null));
    }

    /**
     * A block straight behind the opening is seen through it, and one behind the viewer is not.
     *
     * <p>This is what decides which blocks are drawn at all, and which of two neighbouring windows
     * a block belongs to.
     */
    @Test
    void aBlockStraightBehindTheOpeningIsSeenThroughIt()
    {
        final MirrorWindow window = northFacing(0.0f);
        final List<Spot> open = List.of(new Spot(0, 63, 1), new Spot(0, 64, 1));

        final double[] behind = window.projected(0.5, 64.0, -3.0, 0, 63, 4);
        assertTrue((behind != null) && window.overlaps(behind, open));

        final double[] offToTheSide = window.projected(0.5, 64.0, -3.0, 12, 63, 2);
        assertTrue((offToTheSide != null) && !window.overlaps(offToTheSide, open),
            "behind the wall, but not through the opening from there");

        assertNull(window.projected(0.5, 64.0, 6.0, 0, 63, 3),
            "from behind the wall nothing is behind the opening");
    }

    /**
     * A block reaching past the opening's edge is kept only where something solid hides most of
     * the rest.
     *
     * <p>In a wall, the wall hides it. A freestanding mirror in open air has nothing there, so the
     * same block would show partly beside the opening -- which is what a mirror on a glowstone
     * tower did, showing its far side well past its edges. Most rather than all, since a block
     * straddling the edge of a hut's wall is either a sliver of far scenery round the corner or a
     * hole in the view, and the hole is worse.
     */
    @Test
    void aBlockReachingPastTheEdgeInOpenAirIsCoveredOnlyIfNearlyAllOfItIsBehindTheOpening()
    {
        final MirrorWindow window = northFacing(0.0f);
        final double[] inside = { 0.2, 0.8, 63.2, 64.8 };
        final double[] mostlyPast = { 0.6, 1.5, 63.2, 63.8 };
        final double[] mostlyInside = { 0.2, 1.4, 63.2, 63.8 };
        final double[] justTouching = { 0.2, 1.02, 63.2, 63.8 };
        final MirrorWindow.Face openAir = (across, y) -> (across == 0) && ((y == 63) || (y == 64));
        final MirrorWindow.Face wall = (across, y) -> true;

        assertTrue(window.covered(inside, openAir), "all of it behind the opening");
        assertFalse(window.covered(mostlyPast, openAir), "most of it beside the opening, in the air");
        assertFalse(window.covered(mostlyInside, openAir),
            "a third of it beside the opening, which used to be drawn and showed past small mirrors");
        assertTrue(window.covered(justTouching, openAir), "a fortieth over the edge is rounding");
        assertTrue(window.covered(mostlyPast, wall), "beside the opening, but in the wall");
    }

    /**
     * A real block between the eye and the wall throws a shadow on the wall, wider than itself.
     *
     * <p>A corridor's wall a block from the eye hides a swathe of the face plane four blocks
     * away. A block behind the face, or one at the eye, throws none.
     */
    @Test
    void aBlockInFrontOfTheWallThrowsAShadowOnItFromTheEye()
    {
        final MirrorWindow window = northFacing(0.0f);

        final double[] shadow = window.shadow(0.5, 64.0, -3.0, 2, 63, -2);
        assertTrue((shadow != null) && (shadow[0] > 1.0) && ((shadow[1] - shadow[0]) > 1.5),
            "off to the right and magnified: " + java.util.Arrays.toString(shadow));
        assertNull(window.shadow(0.5, 64.0, -3.0, 0, 63, 3), "behind the face");
        assertNull(window.shadow(0.5, 64.0, -3.0, 0, 63, -3), "at the eye");
    }

    @Test
    void windowsInTheSameWallShareAFaceAndOthersDoNot()
    {
        final MirrorWindow one = northFacing(0.0f);
        final MirrorWindow along = MirrorWindow.of(new MirrorBlock("world", 2, 64, 0),
            BlockFace.NORTH, false, far(0.0f));
        final MirrorWindow deeper = MirrorWindow.of(new MirrorBlock("world", 0, 64, 3),
            BlockFace.NORTH, false, far(0.0f));
        final MirrorWindow facingEast = MirrorWindow.of(new MirrorBlock("world", 0, 64, 0),
            BlockFace.EAST, false, far(0.0f));

        assertTrue(one.sharesFace(along));
        assertFalse(one.sharesFace(deeper));
        assertFalse(one.sharesFace(facingEast));
    }

    @Test
    void aYawFacesTheNearestCardinal()
    {
        assertEquals(new Spot(0, 0, 1), MirrorWindow.aheadOf(0.0f), "south");
        assertEquals(new Spot(-1, 0, 0), MirrorWindow.aheadOf(90.0f), "west");
        assertEquals(new Spot(0, 0, -1), MirrorWindow.aheadOf(180.0f), "north");
        assertEquals(new Spot(1, 0, 0), MirrorWindow.aheadOf(-90.0f), "east, however it is written");
        assertEquals(new Spot(1, 0, 0), MirrorWindow.aheadOf(270.0f), "east");
        assertEquals(new Spot(0, 0, 1), MirrorWindow.aheadOf(44.0f), "still nearer south");
        assertEquals(new Spot(-1, 0, 0), MirrorWindow.aheadOf(46.0f), "now nearer west");
    }
}
