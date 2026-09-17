package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * the viewer's own side where it would bury them, or showing past the edges of the opening.
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
        return MirrorWindow.of(new MirrorBlock("world", 0, 64, 0), BlockFace.NORTH, far(farYaw));
    }

    private static Set<Spot> opening(final MirrorWindow window)
    {
        final Set<Spot> opening = new HashSet<>();
        window.forEachOpening((x, y, z) -> opening.add(new Spot(x, y, z)));
        return opening;
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
            opening(MirrorWindow.of(new MirrorBlock("world", 0, 64, 0), BlockFace.EAST, far(0.0f))));
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

    /**
     * A mirror two banners wide opens two columns, the second to the right looking at the wall.
     *
     * <p>The banner faces north, so the wall is to its south and, looking at the wall, right is
     * west: the second column is at x - 1.
     */
    @Test
    void aMirrorTwoWideOpensTwoColumnsToItsRight()
    {
        final MirrorWindow wide = MirrorWindow.of(new MirrorBlock("world", 0, 64, 0), BlockFace.NORTH,
            far(0.0f), false, 2);

        assertEquals(Set.of(new Spot(0, 63, 1), new Spot(0, 64, 1), new Spot(-1, 63, 1), new Spot(-1, 64, 1)),
            opening(wide), "two columns, two tall");
        assertTrue(wide.isOpening(-1, 63, 1), "the second column is the opening");
        assertFalse(wide.isOpening(1, 63, 1), "and nothing to the left of the first");
        assertEquals(new Spot(-1, 63, 0).x() + 1, wide.base().x(), "the window is still held by the left banner");
    }

    /**
     * A reflection shows the room in front of the wall flipped across it, not turned round.
     *
     * <p>The banner faces north, so its own room lies to the north and the view lies behind the
     * wall to the south. One block along to the east behind the wall shows one block along to the
     * east in front of it, as a mirror does; a window onto the same room would show the west.
     */
    @Test
    void aReflectionKeepsEachBlockOnItsOwnSideOfTheRoom()
    {
        final MirrorPoint room = new MirrorPoint("world", 0.5, 63, 0.5, 180.0f, 0.0f);
        final MirrorWindow reflection = MirrorWindow.of(new MirrorBlock("world", 0, 64, 0), BlockFace.NORTH,
            room, true);
        final MirrorWindow window = MirrorWindow.of(new MirrorBlock("world", 0, 64, 0), BlockFace.NORTH, room);

        assertEquals(new Spot(0, 63, 0), reflection.farOf(0, 63, 2),
            "the first block behind the wall shows the banner's own block, in front of it");
        assertEquals(new Spot(1, 63, -1), reflection.farOf(1, 63, 3), "east behind the wall shows east in front");
        assertEquals(new Spot(-1, 63, -1), window.farOf(1, 63, 3), "where a window turned round shows the west");
        assertEquals(new Spot(1, 63, 3), reflection.hereOf(1, 63, -1), "and back again");
        assertEquals(0, reflection.quarterTurns(), "flipped, so no turn on top");
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

        assertNull(MirrorWindow.of(banner, BlockFace.NORTH_EAST, far(0.0f)),
            "a wall banner only ever faces a cardinal, so anything else is not one");
        assertNull(MirrorWindow.of(banner, null, far(0.0f)));
        assertNull(MirrorWindow.of(banner, BlockFace.UP, far(0.0f)));
        assertNull(MirrorWindow.of(banner, BlockFace.NORTH, null));
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
        assertTrue((behind != MirrorWindow.UNSEEN) && window.overlaps(behind, open));

        final double[] offToTheSide = window.projected(0.5, 64.0, -3.0, 12, 63, 2);
        assertTrue((offToTheSide != MirrorWindow.UNSEEN) && !window.overlaps(offToTheSide, open),
            "behind the wall, but not through the opening from there");

        assertSame(MirrorWindow.UNSEEN, window.projected(0.5, 64.0, 6.0, 0, 63, 3),
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
        assertTrue((shadow != MirrorWindow.UNSEEN) && (shadow[0] > 1.0) && ((shadow[1] - shadow[0]) > 1.5),
            "off to the right and magnified: " + java.util.Arrays.toString(shadow));
        assertSame(MirrorWindow.UNSEEN, window.shadow(0.5, 64.0, -3.0, 0, 63, 3), "behind the face");
        assertSame(MirrorWindow.UNSEEN, window.shadow(0.5, 64.0, -3.0, 0, 63, -3), "at the eye");
    }

    @Test
    void windowsInTheSameWallShareAFaceAndOthersDoNot()
    {
        final MirrorWindow one = northFacing(0.0f);
        final MirrorWindow along = MirrorWindow.of(new MirrorBlock("world", 2, 64, 0), BlockFace.NORTH, far(0.0f));
        final MirrorWindow deeper = MirrorWindow.of(new MirrorBlock("world", 0, 64, 3), BlockFace.NORTH, far(0.0f));
        final MirrorWindow facingEast = MirrorWindow.of(new MirrorBlock("world", 0, 64, 0), BlockFace.EAST, far(0.0f));

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
