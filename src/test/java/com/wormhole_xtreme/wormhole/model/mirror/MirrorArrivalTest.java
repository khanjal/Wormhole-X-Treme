package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
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
     * The arrival is the block in front, not two blocks away and diagonal.
     *
     * <p>The trap here is Bukkit's own geometry. Its sixteen-point faces are built by adding
     * two cardinals together, so {@code NORTH_NORTH_EAST} carries modX 1 and modZ -2 --
     * {@code getRelative} on one lands a knight's move away rather than in front of the
     * banner. Each axis is reduced to its sign, which is always an adjacent block.
     */
    /**
     * A sixteen-point facing still yields a sensible yaw, and no neighbour is consulted.
     *
     * <p>Arriving is the banner's own block now, so the old hazard -- Bukkit's sixteen-point
     * faces are built by adding two cardinals, so NORTH_NORTH_EAST carries modX 1 and modZ -2,
     * and getRelative on one lands two blocks away diagonally -- cannot arise at all. The yaw
     * still has to answer for all sixteen, which is what this pins.
     */
    @Test
    void aSixteenPointFacingGivesItsOwnYawAndConsultsNoNeighbour()
    {
        final Rotatable data = mock(Rotatable.class);
        when(data.getRotation()).thenReturn(BlockFace.NORTH_NORTH_EAST);

        final World world = mock(World.class);
        final Block banner = mock(Block.class);
        when(banner.getBlockData()).thenReturn(data);
        when(banner.getLocation()).thenReturn(new Location(world, 1.0, 64.0, 1.0));

        final Location arrival = MirrorArrival.atTheBanner(banner);

        assertNotNull(arrival, "a banner with a readable facing is somewhere to arrive");
        verify(banner, never()).getRelative(anyInt(), anyInt(), anyInt());
        assertEquals(202.5f, arrival.getYaw(), 0.01f,
            "and faces the way the banner does, not due south");
        assertEquals(0.0f, arrival.getPitch(), 0.01f, "looking level, not at the floor");
    }

    /**
     * Arriving is the banner's own block, centred in it.
     *
     * <p>Not the block in front, which reads the same in an open room and badly everywhere
     * else: one block of clearance the builder did not choose can be a wall, a drop or the far
     * side of a doorway. The banner's own block is the one place somebody deliberately put
     * something, so it is the one known to be clear -- and a banner is passable, so a player
     * can stand in it.
     */
    @Test
    void arrivalIsTheBannersOwnBlockCentredInIt()
    {
        final Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(BlockFace.SOUTH);

        final World world = mock(World.class);
        final Block banner = mock(Block.class);
        when(banner.getBlockData()).thenReturn(data);
        when(banner.getLocation()).thenReturn(new Location(world, 10.0, 64.0, 10.0));

        final Location arrival = MirrorArrival.atTheBanner(banner);

        assertEquals(10.5, arrival.getX(), 0.001, "standing in the middle of the block, not its edge");
        assertEquals(64.0, arrival.getY(), 0.001, "and at the banner's own height");
        assertEquals(10.5, arrival.getZ(), 0.001);
        assertEquals(0.0f, arrival.getYaw(), 0.01f,
            "looking out the way the banner faces, not back at the cloth");
    }

    /** A block with no facing is nowhere to arrive, and says so by answering null. */
    @Test
    void aBlockWithNoFacingHasNoArrival()
    {
        final BlockData plainBlock = mock(BlockData.class);
        final Block notABanner = mock(Block.class);
        when(notABanner.getBlockData()).thenReturn(plainBlock);

        assertNull(MirrorArrival.atTheBanner(notABanner));
        assertNull(MirrorArrival.atTheBanner(null));
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
