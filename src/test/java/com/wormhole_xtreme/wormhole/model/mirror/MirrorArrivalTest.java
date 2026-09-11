package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.junit.jupiter.api.Test;

/**
 * Reading which way a banner faces, whichever of the two kinds it is.
 *
 * <p>This is the part of {@code mirror link} that can be tested without a live world, and it
 * is also the part most likely to be got wrong, because the two banner families keep their
 * facing in two different interfaces with no shared parent. A wall banner is
 * {@link Directional}; a freestanding one is {@link Rotatable}. Reading only the first would
 * work perfectly on every wall banner and silently fail on every banner on a post -- and a
 * museum corridor is mostly the latter.
 */
class MirrorArrivalTest
{
    /** A wall banner carries its facing as Directional. */
    @Test
    void aWallBannerFacesWhereItsDirectionalSays()
    {
        final Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(BlockFace.NORTH);

        assertEquals(BlockFace.NORTH, MirrorArrival.facingOf(data));
    }

    /**
     * A freestanding banner carries it as Rotatable, in sixteen directions.
     *
     * <p>The one that would be missed by checking only for {@code Directional}, and the
     * failure would be invisible on a test server built against a wall.
     */
    @Test
    void aFreestandingBannerFacesWhereItsRotatableSays()
    {
        final Rotatable data = mock(Rotatable.class);
        when(data.getRotation()).thenReturn(BlockFace.SOUTH_SOUTH_EAST);

        assertEquals(BlockFace.SOUTH_SOUTH_EAST, MirrorArrival.facingOf(data),
            "a banner on a post rotates in sixteen directions, not four");
    }

    /**
     * Anything that is not a banner has no facing to read.
     *
     * <p>Answering null rather than guessing a direction is what lets {@code mirror link}
     * refuse with "that is not a banner" instead of binding a mirror to a spot derived from
     * nothing.
     */
    @Test
    void aBlockThatIsNeitherKindHasNoFacing()
    {
        assertNull(MirrorArrival.facingOf(mock(BlockData.class)));
        assertNull(MirrorArrival.facingOf(null));
    }

    /**
     * Directional is preferred when a block data somehow implements both.
     *
     * <p>No vanilla banner does, but the check order is a decision rather than an accident and
     * a future block type could. Pinning it means a reordering of those two branches is a test
     * failure rather than a silent change of which face wins.
     */
    @Test
    void directionalWinsIfSomethingImplementsBoth()
    {
        final BlockData both = mock(BlockData.class,
            org.mockito.Mockito.withSettings().extraInterfaces(Directional.class, Rotatable.class));
        when(((Directional) both).getFacing()).thenReturn(BlockFace.EAST);
        when(((Rotatable) both).getRotation()).thenReturn(BlockFace.WEST);

        assertEquals(BlockFace.EAST, MirrorArrival.facingOf(both));
    }
}
