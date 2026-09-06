package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Every marker a shape layer understands, and where each one lands.
 *
 * <p>The nine single-position markers are read by one long chain of string comparisons, and
 * each is asserted in a different test file, mostly once. Nothing checks them together, so a
 * marker wired to the wrong setter would still leave the suite green as long as its own
 * shape file did not happen to use it. This puts all nine on one line and reads each back.
 */
class ShapeLayerMarkerTest
{
    @BeforeEach
    void installPlugin() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));
    }

    /**
     * A marker's column is counted from the right, so the first on a line is the last column.
     *
     * @param index
     *            how many markers precede it on the line
     * @param width
     *            the layer width
     */
    private static int[] at(final int index, final int width)
    {
        return new int[] { 0, 0, width - 1 - index };
    }

    @Test
    void everySinglePositionMarkerLandsInItsOwnSetter()
    {
        final String[] lines = { "[N][EP][EM][A][D][IA][RA][RD][RS]" };

        final StargateShapeLayer layer = new StargateShapeLayer(lines, 1, 9);

        assertArrayEquals(at(0, 9), layer.getLayerNameSignPosition(), "N is the name sign");
        assertArrayEquals(at(1, 9), layer.getLayerPlayerExitPosition(), "EP is the player exit");
        assertArrayEquals(at(2, 9), layer.getLayerMinecartExitPosition(), "EM is the minecart exit");
        assertArrayEquals(at(3, 9), layer.getLayerActivationPosition(), "A is the activation block");
        assertArrayEquals(at(4, 9), layer.getLayerDialSignPosition(), "D is the dial sign");
        assertArrayEquals(at(5, 9), layer.getLayerIrisActivationPosition(), "IA is the iris lever");
        assertArrayEquals(at(6, 9), layer.getLayerRedstoneGateActivatedPosition(), "RA is gate-activated");
        assertArrayEquals(at(7, 9), layer.getLayerRedstoneDialActivationPosition(), "RD is dial activation");
        assertArrayEquals(at(8, 9), layer.getLayerRedstoneSignActivationPosition(), "RS is sign activation");
    }

    /** Structure, portal and chevron markers collect rather than replace. */
    @Test
    void theCollectingMarkersEachGatherTheirOwnBlocks()
    {
        final String[] lines = { "[S][S][P][C]" };

        final StargateShapeLayer layer = new StargateShapeLayer(lines, 1, 4);

        assertEquals(2, layer.getLayerBlockPositions().size(), "both S markers are kept");
        assertEquals(1, layer.getLayerPortalPositions().size());
        assertEquals(1, layer.getLayerChevronPositions().size());
    }

    /**
     * A marker carrying several roles applies all of them to the same block.
     *
     * <p>This is how a shipped shape says a frame block is also part of light wave one.
     */
    @Test
    void oneBlockCanCarrySeveralRoles()
    {
        final String[] lines = { "[S:L#1]" };

        final StargateShapeLayer layer = new StargateShapeLayer(lines, 1, 1);

        assertEquals(1, layer.getLayerBlockPositions().size(), "still a structure block");
        assertEquals(1, layer.getLayerLightPositions().get(1).size(), "and a light in wave one");
    }

    /**
     * Wave numbers index the wave list directly, and an unnumbered marker means wave one.
     *
     * <p>The list is padded with nulls up to the highest number seen, so wave 3 alone leaves
     * 0, 1 and 2 empty rather than shifting everything down.
     */
    @Test
    void waveNumbersIndexTheListAndDefaultToOne()
    {
        final String[] lines = { "[L][W][L#3][W#2]" };

        final StargateShapeLayer layer = new StargateShapeLayer(lines, 1, 4);

        assertEquals(1, layer.getLayerLightPositions().get(1).size(), "a bare L is wave one");
        assertEquals(1, layer.getLayerLightPositions().get(3).size(), "L#3 is wave three");
        assertEquals(4, layer.getLayerLightPositions().size(), "padded to hold index 3");
        assertEquals(1, layer.getLayerWooshPositions().get(1).size(), "a bare W is wave one");
        assertEquals(1, layer.getLayerWooshPositions().get(2).size(), "W#2 is wave two");
    }
}
