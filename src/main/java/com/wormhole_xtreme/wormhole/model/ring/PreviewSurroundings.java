package com.wormhole_xtreme.wormhole.model.ring;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * {@link RingCycle.Surroundings} for a preview: one player sees the drawing, and the world is
 * left entirely alone.
 *
 * <p>{@link BukkitRingWorld} sends a block change to everyone near the pair. This sends it to
 * one viewer and carries nobody: no passengers are found, nothing may travel, and nothing is
 * delivered. A preview is a drawing and cannot be anything else, so every way it could touch
 * the world answers no.
 *
 * <p>Measuring a ceiling ring is the one thing it does for real, because the stack has to
 * know where the floor is; that walk reads blocks and writes only the drop on the ring, as the
 * real cycle's does.
 */
final class PreviewSurroundings implements RingCycle.Surroundings
{
    /** Who is shown the drawing. */
    private final Player viewer;

    /** The world the ring is in. */
    private final World world;

    /** The pair, which the survey reads the world through. */
    private final RingPair pair;

    /**
     * Instantiates the drawing for one viewer.
     *
     * @param viewer
     *            who is shown the drawing, and nobody else is
     * @param world
     *            the world the ring is in
     * @param pair
     *            the pair being previewed
     */
    PreviewSurroundings(final Player viewer, final World world, final RingPair pair)
    {
        this.viewer = viewer;
        this.world = world;
        this.pair = pair;
    }

    @Override
    public void showBlock(final int x, final int y, final int z, final Material material)
    {
        send(x, y, z, MaterialUtils.drawnAs(material));
    }

    @Override
    public void showSlab(final int x, final int y, final int z, final Material material,
        final boolean top)
    {
        send(x, y, z, BukkitRingWorld.slabData(material, top));
    }

    @Override
    public void reveal(final int x, final int y, final int z)
    {
        // A chunk nobody has loaded has nothing of ours in it, and asking for its block would
        // load it just to say so.
        if (!world.isChunkLoaded(x >> 4, z >> 4))
        {
            return;
        }
        send(x, y, z, world.getBlockAt(x, y, z).getBlockData());
    }

    /**
     * Sends one block change to the viewer, and only if it still means something to them.
     *
     * <p>Not once they have gone or changed world: coordinates are the same in every world, so
     * a block sent to somebody in the nether is drawn in the nether, over terrain it was never
     * about. The client throws the old world's drawing away when it changes world anyway.
     *
     * @param x
     *            block x
     * @param y
     *            block y
     * @param z
     *            block z
     * @param data
     *            what to show
     */
    private void send(final int x, final int y, final int z, final BlockData data)
    {
        if (!viewer.isOnline() || !world.equals(viewer.getWorld()))
        {
            return;
        }
        try
        {
            viewer.sendBlockChange(new Location(world, x, y, z), data);
        }
        // Purely a courtesy to one player. A client that cannot be sent it is not worth
        // stopping a clean-up over.
        catch (final RuntimeException ignored)
        {
            // deliberately silent
        }
    }

    @Override
    public List<RingPassenger> passengersIn(final List<int[]> blocks)
    {
        return new ArrayList<>();
    }

    @Override
    public boolean mayTravel(final RingPassenger passenger, final Ring from, final Ring to)
    {
        return false;
    }

    @Override
    public RingBlockage survey(final Ring ring)
    {
        return new BukkitRingWorld(world, pair).survey(ring);
    }

    @Override
    public void deliver(final RingPassenger passenger, final Ring destination)
    {
        // Nobody is carried by a drawing.
    }
}
