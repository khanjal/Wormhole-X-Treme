package com.wormhole_xtreme.wormhole.model.mirror;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The sweep that offers every mirror to {@link MirrorWindows}, which draws the ones hung on a wall.
 *
 * <p>It runs forever, on a timer, so the order of the checks is the design. Before anything
 * touches a block it has already ruled out mirrors going nowhere, worlds that are not loaded and
 * chunks that are not loaded.
 */
public final class MirrorProximity
{
    /** Static state only. */
    private MirrorProximity()
    {
    }

    /** @return the sweep, for the scheduler */
    public static Runnable createTicker()
    {
        return MirrorProximity::tick;
    }

    /** Forgets every view, for a test or a reload. */
    public static void clear()
    {
        MirrorWindows.clear();
    }

    /**
     * Takes one mirror's view back from whoever is looking through it.
     *
     * @param mirror
     *            the mirror no longer being drawn
     */
    public static void release(final QuantumMirror mirror)
    {
        MirrorWindows.release(mirror);
    }

    /**
     * Releases a mirror and forgets everything else about it, for one being removed.
     *
     * <p>Left behind, its capture and its place on the network would be handed to whatever is next
     * given that name.
     *
     * @param mirror
     *            the mirror being removed
     */
    public static void forget(final QuantumMirror mirror)
    {
        release(mirror);
        MirrorCaptures.forget(mirror);
        MirrorNetwork.forget(mirror.name());
    }

    /** Takes every view back, so the world is what everybody sees, as the plugin stops. */
    public static void restoreAll()
    {
        MirrorWindows.restoreAll();
    }

    /**
     * One pass over every mirror, offering each to {@link MirrorWindows}.
     *
     * <p>Nothing marks a window in the file, so every mirror with somewhere to go has its banner
     * read to find out.
     */
    static void tick()
    {
        for (final QuantumMirror mirror : MirrorManager.all())
        {
            // A name whose banner is indexed under another mirror is not clicked through, so it is
            // not drawn either: two of them drew two far sides through one opening.
            final QuantumMirror onItsBanner = MirrorManager.at(mirror.banner());
            if ((onItsBanner != null) && !onItsBanner.name().equalsIgnoreCase(mirror.name()))
            {
                continue;
            }
            offerWindow(mirror);
        }
        // Windows share walls, so they are drawn together once every one has been found. A
        // window not offered this sweep -- broken, taken down, re-hung on a post -- drops out.
        MirrorWindows.finish();
    }

    /**
     * Offers a mirror to {@link MirrorWindows}, which takes it if its banner hangs on a wall.
     *
     * @param mirror
     *            the mirror
     */
    private static void offerWindow(final QuantumMirror mirror)
    {
        // A mirror going nowhere has no view, and its banner need not be read to learn that.
        final Block block = (mirror.destination() == null) ? null : bannerOf(mirror);
        if (block == null)
        {
            return;
        }
        // Nobody at it any more: back to its own room.
        MirrorNetwork.settle(mirror, MirrorNetwork.anybodyNear(block.getWorld(), mirror.banner(), null));
        MirrorWindows.offer(mirror, block);
    }

    /** The live banner block, or null if it cannot be reached or is no longer a banner. */
    private static Block bannerOf(final QuantumMirror mirror)
    {
        final MirrorBlock at = mirror.banner();
        final World world = Bukkit.getWorld(at.worldName());
        if (world == null)
        {
            return null;
        }
        // Before getBlockAt, which would load the chunk. A mirror in a corner of the map
        // nobody has walked to must not be the thing keeping that corner resident.
        if (!world.isChunkLoaded(at.x() >> 4, at.z() >> 4))
        {
            return null;
        }
        final Block block = world.getBlockAt(at.x(), at.y(), at.z());
        return block.getType().name().endsWith("BANNER") ? block : null;
    }
}
