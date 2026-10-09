package com.wormhole_xtreme.wormhole.model.window;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorArrival;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorNetwork;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorSource;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;

/**
 * The sweep that offers every mirror to {@link Windows}, which draws the ones hung on a wall.
 *
 * <p>It runs forever, on a timer, so the order of the checks is the design. Before anything
 * touches a block it has already ruled out mirrors going nowhere, worlds that are not loaded and
 * chunks that are not loaded.
 */
public final class WindowSweep
{
    /** Static state only. */
    private WindowSweep()
    {
    }

    /** Offers whatever else is drawn as a window, before the sweep settles them: open gates (#516). */
    private static Runnable alsoOffer = () ->
    {
    };

    /**
     * Sets what else each sweep offers as a window, alongside the mirrors.
     *
     * @param offer
     *            run once a sweep, after the mirrors and before the windows are settled; null for nothing
     */
    public static void alsoOffer(final Runnable offer)
    {
        alsoOffer = (offer == null) ? () ->
        {
        } : offer;
    }

    /** @return the sweep, for the scheduler */
    public static Runnable createTicker()
    {
        return WindowSweep::tick;
    }

    /** Forgets every view, for a test or a reload. */
    public static void clear()
    {
        Windows.clear();
    }

    /**
     * Takes one mirror's view back from whoever is looking through it.
     *
     * @param mirror
     *            the mirror no longer being drawn
     */
    public static void release(final QuantumMirror mirror)
    {
        Windows.release(mirror);
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
        Captures.forget(mirror);
        MirrorNetwork.forget(mirror.name());
    }

    /** Takes every view back, so the world is what everybody sees, as the plugin stops. */
    public static void restoreAll()
    {
        Windows.restoreAll();
    }

    /**
     * One pass over every mirror, offering each to {@link Windows}.
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
        try
        {
            alsoOffer.run();
        }
        catch (final Exception | LinkageError e)
        {
            // An experiment's failure must not freeze every mirror's view, which finish() is what moves on.
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Could not offer gate views to the mirror sweep", e);
        }
        // Windows share walls, so they are drawn together once every one has been found. A
        // window not offered this sweep -- broken, taken down, re-hung on a post -- drops out.
        Windows.finish();
    }

    /**
     * Offers a mirror to {@link Windows}, which takes it if its banner hangs on a wall.
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
        offerMirror(mirror, block);
    }

    /**
     * Offers a mirror to the sweep in progress, if its banner hangs on a wall.
     *
     * <p>A mirror whose far side has not been captured yet is not a window until it has; the
     * capture is asked for, and the mirror stays a banner meanwhile. A mirror whose capture has
     * been outgrown asks for a fresh one, and keeps showing the old until it arrives.
     *
     * @param mirror
     *            a mirror with somewhere to go
     * @param banner
     *            its loaded banner block
     * @return true if it is a window
     */
    static boolean offerMirror(final QuantumMirror mirror, final Block banner)
    {
        final BlockData data = banner.getBlockData();
        // A banner on a post is no window: nothing hides its room past its edges, and create refuses one.
        if (!(data instanceof Directional))
        {
            return false;
        }
        // The room of the mirror chosen at it, or its own, shown as a reflection.
        final QuantumMirror chosen = MirrorNetwork.chosen(mirror);
        final QuantumMirror showing = (chosen == mirror) ? mirror : mirror.withDestination(chosen.destination());
        final WindowShape shape = WindowShape.of(mirror.banner(), MirrorArrival.facingOf(data),
            showing.destination(), MirrorNetwork.reflects(mirror), mirror.width());
        if (shape == null)
        {
            return false;
        }
        final Capture capture = Captures.get(showing);
        if (capture == null)
        {
            Captures.request(showing);
            return false;
        }
        if (Captures.outgrown(showing, capture))
        {
            Captures.request(showing);
        }
        Windows.offer(new MirrorSource(showing, banner, shape, Windows.openCells(shape, banner.getWorld())), capture);
        return true;
    }

    /** The live banner block, or null if it cannot be reached or is no longer a banner. */
    private static Block bannerOf(final QuantumMirror mirror)
    {
        final BlockPlace at = mirror.banner();
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
