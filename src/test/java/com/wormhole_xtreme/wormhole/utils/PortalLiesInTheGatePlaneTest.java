package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;

import org.bukkit.Axis;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.junit.jupiter.api.Test;

/**
 * Which way a block drawn across a gate's opening is turned.
 *
 * <p>A {@code NETHER_PORTAL} carries an axis saying which way its sheet runs, and
 * {@code createBlockData()} hands back whichever one the game defaults to. Nothing here ever set
 * it, so the wormhole was right on half the gates in a world and ninety degrees out on the other
 * half -- a thin sliver seen edge-on instead of a sheet filling the opening. Which half looked
 * wrong depended only on which way the gate was built, which is why it survived so long: a world
 * with one nether-portal gate in it looks perfect.
 *
 * <p>The axis is the facing turned sideways, because the opening is the plane the block has to
 * fill. Both directions of each pair matter: north and south are the same plane, and a rule
 * written from one gate would have got a facing test right by accident.
 */
class PortalLiesInTheGatePlaneTest
{
    /** An Orientable that accepts both horizontal axes, as a nether portal does. */
    private static Orientable portal()
    {
        final Orientable portal = mock(Orientable.class);
        when(portal.getAxes()).thenReturn(EnumSet.of(Axis.X, Axis.Z));
        return portal;
    }

    /**
     * A gate facing north or south opens across X.
     *
     * <p>Both, and each asserts the axis it is <em>not</em> given as well: a rule that answered X
     * for everything would pass a test that only ever asked about north.
     */
    @Test
    void aGateFacingNorthOrSouthLaysItsWormholeAlongX()
    {
        final Orientable north = portal();
        MaterialUtils.laidAcross(north, BlockFace.NORTH);
        verify(north).setAxis(Axis.X);
        verify(north, never()).setAxis(Axis.Z);

        final Orientable south = portal();
        MaterialUtils.laidAcross(south, BlockFace.SOUTH);
        verify(south).setAxis(Axis.X);
        verify(south, never()).setAxis(Axis.Z);
    }

    /** And one facing east or west opens across Z, which is the half that was drawn wrong. */
    @Test
    void aGateFacingEastOrWestLaysItsWormholeAlongZ()
    {
        final Orientable east = portal();
        MaterialUtils.laidAcross(east, BlockFace.EAST);
        verify(east).setAxis(Axis.Z);
        verify(east, never()).setAxis(Axis.X);

        final Orientable west = portal();
        MaterialUtils.laidAcross(west, BlockFace.WEST);
        verify(west).setAxis(Axis.Z);
        verify(west, never()).setAxis(Axis.X);
    }

    /**
     * A horizontal gate is left alone.
     *
     * <p>Its opening is flat and an axis names a horizontal direction, so there is no value that
     * would lie in it. Better the game's default than a guess that stands the wormhole on edge in
     * a gate somebody walks over.
     */
    @Test
    void aHorizontalGateIsLeftAtTheGamesDefault()
    {
        final Orientable up = portal();
        MaterialUtils.laidAcross(up, BlockFace.UP);
        verify(up, never()).setAxis(any());

        final Orientable down = portal();
        MaterialUtils.laidAcross(down, BlockFace.DOWN);
        verify(down, never()).setAxis(any());
    }

    /** A gate whose facing has not been read yet asks for nothing, rather than throwing. */
    @Test
    void noFacingTurnsNothing()
    {
        final Orientable unplaced = portal();
        MaterialUtils.laidAcross(unplaced, null);
        verify(unplaced, never()).setAxis(any());

        assertNull(MaterialUtils.laidAcross(null, BlockFace.NORTH),
            "and null block data comes back as it went in");
    }

    /**
     * A block that does not offer the axis wanted keeps its own.
     *
     * <p>{@code Orientable} covers more than the portal: a log has three axes, a nether portal
     * two, and some future block may have one. Forcing an axis a block does not have is an
     * {@code IllegalArgumentException} from the server, in the middle of drawing a gate.
     */
    @Test
    void aBlockWithoutThatAxisIsNotForcedIntoIt()
    {
        final Orientable onlyY = mock(Orientable.class);
        when(onlyY.getAxes()).thenReturn(EnumSet.of(Axis.Y));

        MaterialUtils.laidAcross(onlyY, BlockFace.NORTH);

        verify(onlyY, never()).setAxis(any());
    }

    /**
     * Everything else passes through untouched, which is what makes this safe to call at every
     * cell of every opening rather than only the ones known to need it.
     */
    @Test
    void aBlockWithNoAxisAtAllIsHandedBackAsItIs()
    {
        final BlockData plain = mock(BlockData.class);

        assertSame(plain, MaterialUtils.laidAcross(plain, BlockFace.EAST),
            "the same block data, not a copy and not null");
    }
}
