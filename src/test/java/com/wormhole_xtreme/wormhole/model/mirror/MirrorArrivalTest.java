package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.utils.WorldUtils;

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
     * All sixteen rotations get their own yaw.
     *
     * <p>{@code WorldUtils.getDegreesFromBlockFace} answers for the four cardinals and returns
     * 0 -- due south -- for everything else. That is right for a gate, whose parts only ever
     * face a cardinal, and wrong for a banner on a post: twelve of its sixteen rotations would
     * turn an arriving player south regardless of which way they had just stepped out of.
     *
     * <p>Checked against the whole circle rather than a sample, because the failure is silent:
     * a wrong yaw looks like a player who happens to be facing an odd way.
     */
    @Test
    void everyRotationGetsItsOwnYaw()
    {
        final BlockFace[] clockwiseFromSouth = {
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST,
            BlockFace.WEST_SOUTH_WEST, BlockFace.WEST, BlockFace.WEST_NORTH_WEST,
            BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST, BlockFace.NORTH,
            BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST,
            BlockFace.SOUTH_SOUTH_EAST };

        final Set<Float> seen = new HashSet<>();
        for (int i = 0; i < clockwiseFromSouth.length; i++)
        {
            final float yaw = MirrorArrival.yawOf(clockwiseFromSouth[i]);
            assertEquals(i * 22.5f, yaw, 0.01f,
                clockwiseFromSouth[i] + " should be " + (i * 22.5f) + " degrees;"
                + " Minecraft yaw starts at south and increases clockwise");
            seen.add(yaw);
        }
        assertEquals(16, seen.size(), "sixteen rotations need sixteen distinct yaws");
    }

    /**
     * The four cardinals still agree with what gates have always used.
     *
     * <p>A mirror on a wall must not face a different way from everything else in the plugin
     * that reads the same BlockFace.
     */
    @Test
    void theCardinalsMatchWhatTheRestOfThePluginUses()
    {
        for (final BlockFace cardinal : new BlockFace[] {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST })
        {
            assertEquals(WorldUtils.getDegreesFromBlockFace(cardinal), MirrorArrival.yawOf(cardinal),
                0.01f, cardinal + " should agree with the gate-side helper");
        }
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
