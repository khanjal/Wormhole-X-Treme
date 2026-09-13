package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;

/**
 * The shape of a window mirror, and where each block drawn behind it comes from.
 *
 * <p>Everything a viewer sees depends on this arithmetic, and the mistakes it invites are ones
 * that still look like a view: a far side shown mirror-image, shown a layer too deep, or drawn
 * on the viewer's own side of the wall where it would bury them.
 */
class MirrorWindowTest
{
    /** Where most of these travel: the arrival block is (100, 70, -21). */
    private static MirrorPoint far(final float yaw)
    {
        return new MirrorPoint("far", 100.5, 70.0, -20.5, yaw, 0.0f);
    }

    /** A banner at the origin, facing north, so its wall is the block to the south. */
    private static MirrorWindow northFacing(final float farYaw)
    {
        return MirrorWindow.of(new MirrorBlock("world", 0, 64, 0), BlockFace.NORTH, far(farYaw));
    }

    /** Every block drawn behind a window, mapped to the far-side block it shows. */
    private static Map<Spot, Spot> shown(final MirrorWindow window)
    {
        final Map<Spot, Spot> shown = new HashMap<>();
        window.forEachShown((x, y, z, farX, farY, farZ) ->
            shown.put(new Spot(x, y, z), new Spot(farX, farY, farZ)));
        return shown;
    }

    @Test
    void theOpeningIsThreeByThreeInTheWallWithItsTopRowLevelWithTheBanner()
    {
        final Set<Spot> opening = new HashSet<>();
        northFacing(0.0f).forEachOpening((x, y, z) -> opening.add(new Spot(x, y, z)));

        final Set<Spot> expected = new HashSet<>();
        for (int x = -1; x <= 1; x++)
        {
            for (int y = 62; y <= 64; y++)
            {
                expected.add(new Spot(x, y, 1));
            }
        }
        assertEquals(expected, opening);
    }

    @Test
    void aBannerFacingEastOpensAlongZInTheWallToItsWest()
    {
        final Set<Spot> opening = new HashSet<>();
        MirrorWindow.of(new MirrorBlock("world", 0, 64, 0), BlockFace.EAST, far(0.0f))
            .forEachOpening((x, y, z) -> opening.add(new Spot(x, y, z)));

        assertTrue(opening.contains(new Spot(-1, 63, -1)));
        assertTrue(opening.contains(new Spot(-1, 63, 1)));
        assertEquals(9, opening.size());
    }

    /**
     * Straight through the middle of the bottom row is exactly where a traveller lands.
     *
     * <p>Whatever way the far side faces. Get this wrong by a layer and the view is of the block
     * in front of the arrival point, so stepping through never lands where it looked like.
     */
    @Test
    void justBehindTheMiddleOfTheBottomRowIsTheArrivalBlock()
    {
        for (final float yaw : new float[] { 0.0f, 90.0f, 180.0f, -90.0f })
        {
            assertEquals(new Spot(100, 70, -21), shown(northFacing(yaw)).get(new Spot(0, 62, 2)),
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
        assertEquals(new Spot(100, 70, -20), shown(northFacing(-90.0f)).get(new Spot(-1, 62, 2)));
    }

    @Test
    void deeperBehindTheWallIsFurtherAheadAtTheFarSide()
    {
        assertEquals(new Spot(104, 70, -21), shown(northFacing(-90.0f)).get(new Spot(0, 62, 6)));
    }

    @Test
    void higherInTheOpeningIsHigherAtTheFarSide()
    {
        assertEquals(new Spot(100, 73, -21), shown(northFacing(-90.0f)).get(new Spot(0, 65, 2)));
    }

    /**
     * Nothing is drawn on the viewer's side of the wall, or in the wall's own layer.
     *
     * <p>A drawn block on the viewer's side would stand where they are standing. The wall's own
     * layer is the opening, which is drawn as something else.
     */
    @Test
    void everythingShownIsBehindTheWall()
    {
        final Map<Spot, Spot> shown = shown(northFacing(0.0f));

        assertEquals((3 + 16) * (3 + 6 + 3) * 16, shown.size(), "the whole box is visited");
        for (final Spot spot : shown.keySet())
        {
            assertTrue(spot.z() >= 2, "drawn in front of the wall at " + spot);
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
    void theOpeningIsOnlyTheNineBlocksInTheWall()
    {
        final MirrorWindow window = northFacing(0.0f);

        assertTrue(window.isOpening(1, 63, 1));
        assertTrue(window.isOpening(-1, 62, 1));
        assertFalse(window.isOpening(2, 63, 1), "beside it");
        assertFalse(window.isOpening(0, 65, 1), "above it");
        assertFalse(window.isOpening(0, 61, 1), "below it");
        assertFalse(window.isOpening(0, 63, 2), "behind the wall");
        assertFalse(window.isOpening(0, 63, 0), "in front of the wall");
    }

    @Test
    void aBannerFacingNoCardinalOrAMirrorGoingNowhereMakesNoWindow()
    {
        final MirrorBlock banner = new MirrorBlock("world", 0, 64, 0);

        assertNull(MirrorWindow.of(banner, BlockFace.NORTH_EAST, far(0.0f)));
        assertNull(MirrorWindow.of(banner, null, far(0.0f)));
        assertNull(MirrorWindow.of(banner, BlockFace.NORTH, null));
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
