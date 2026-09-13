package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        return MirrorWindow.of(new MirrorBlock("world", 0, 64, 0), BlockFace.NORTH, false,
            far(farYaw));
    }

    /** Every block that may be drawn behind a window, mapped to the far-side block it shows. */
    private static Map<Spot, Spot> shown(final MirrorWindow window)
    {
        final Map<Spot, Spot> shown = new HashMap<>();
        window.forEachShown((x, y, z, farX, farY, farZ) ->
            shown.put(new Spot(x, y, z), new Spot(farX, farY, farZ)));
        return shown;
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
            assertEquals(new Spot(100, 70, -21), shown(northFacing(yaw)).get(new Spot(0, 63, 2)),
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
        assertEquals(new Spot(100, 70, -20), shown(northFacing(-90.0f)).get(new Spot(-1, 63, 2)));
    }

    @Test
    void deeperBehindTheOpeningIsFurtherAheadAtTheFarSide()
    {
        assertEquals(new Spot(104, 70, -21), shown(northFacing(-90.0f)).get(new Spot(0, 63, 6)));
    }

    @Test
    void higherBehindTheOpeningIsHigherAtTheFarSide()
    {
        assertEquals(new Spot(100, 73, -21), shown(northFacing(-90.0f)).get(new Spot(0, 66, 2)));
    }

    /**
     * Nothing is drawn on the viewer's side of the opening, or in the opening's own layer.
     *
     * <p>A drawn block on the viewer's side would stand where they are standing. The opening's
     * own layer is the opening, which is drawn as something else.
     */
    @Test
    void everythingShownIsBehindTheOpening()
    {
        final Map<Spot, Spot> shown = shown(northFacing(0.0f));

        assertEquals((MirrorWindow.WIDTH + (2 * MirrorWindow.SIDE))
            * (MirrorWindow.BELOW + MirrorWindow.HEIGHT + MirrorWindow.ABOVE) * MirrorWindow.DEPTH,
            shown.size(), "the whole box is visited");
        for (final Spot spot : shown.keySet())
        {
            assertTrue(spot.z() >= 2, "drawn in front of the opening at " + spot);
        }
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
     * A block reaching past the opening's edge is kept only where something solid hides the rest.
     *
     * <p>In a wall, the wall hides it. A freestanding mirror in open air has nothing there, so the
     * same block would show in full beside the opening -- which is what a mirror on a glowstone
     * tower did, showing its far side well past its edges.
     */
    @Test
    void aBlockReachingPastTheEdgeIsCoveredOnlyWhereTheEdgeIsSolid()
    {
        final MirrorWindow window = northFacing(0.0f);
        final double[] inside = { 0.2, 0.8, 63.2, 64.8 };
        final double[] pastTheEdge = { 0.5, 1.5, 63.2, 63.8 };
        final MirrorWindow.Face openAir = (across, y) -> (across == 0) && ((y == 63) || (y == 64));
        final MirrorWindow.Face wall = (across, y) -> true;

        assertTrue(window.covered(inside, openAir), "all of it behind the opening");
        assertFalse(window.covered(pastTheEdge, openAir), "part of it beside the opening, in the air");
        assertTrue(window.covered(pastTheEdge, wall), "part of it beside the opening, in the wall");
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
