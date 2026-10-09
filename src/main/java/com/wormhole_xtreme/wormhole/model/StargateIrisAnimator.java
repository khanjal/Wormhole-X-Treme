package com.wormhole_xtreme.wormhole.model;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Draws an iris arriving or drawing back a ring at a time.
 *
 * <h2>The blocks are not what sweeps</h2>
 *
 * <p>The iris is real blocks and stays real blocks, placed and removed in one go exactly as
 * before. Only the picture sweeps, sent to nearby clients with {@code sendBlockChange} the same
 * way the portal is drawn. That is not a shortcut, it is the point:
 *
 * <ul>
 * <li>{@link Stargate#isGateIrisActive()} is the one answer four separate call sites trust --
 * travel refusal for players and for vehicles, the portal redraw, and block protection.
 * Sweeping the real blocks would leave the flag describing something that is only partly
 * there for as long as the sweep ran, and a barrier with a hole in it is the one thing an
 * iris must never be.</li>
 * <li>Every real block placed raises a {@code BlockPhysicsEvent} for water, pistons and every
 * protection plugin on the server. A sweep would turn one toggle into a wave of them per
 * ring.</li>
 * <li>{@link StargateBlockSetup#clearIrisPath} moves anything living out of the opening before
 * the blocks land. Once, up front, not ring by ring as a closing sweep encloses somebody.</li>
 * </ul>
 *
 * <h2>The picture is always the more cautious of the two</h2>
 *
 * <p>Closing, the real blocks go in first and the rings are then revealed over them, so the
 * barrier exists before it looks like it does. Opening, the rings are cleared first and the
 * real blocks come out at the end, so the barrier outlasts the picture of it. Either way, at
 * every instant during a sweep the gate is at least as solid as it appears -- never less.
 */
public final class StargateIrisAnimator
{
    /** The sweep running on each gate, by name, so a second toggle can call the first off. */
    private static final ConcurrentHashMap<String, IrisSweepDriver<Location>> running = new ConcurrentHashMap<>();

    /** Static helpers only. */
    private StargateIrisAnimator()
    {
    }

    /**
     * Whether a gate's iris should sweep rather than arrive at once.
     *
     * @return true if there is a sweep worth running
     */
    static boolean sweeps(final Stargate gate)
    {
        return (gate != null)
            && ConfigManager.isIrisAnimated(gate.getEffectiveIrisAnimation())
            && (gate.getGateWorld() != null)
            && (gate.getGatePortalBlocks() != null)
            && (gate.getGatePortalBlocks().size() > 1)
            && (WormholeXTreme.getScheduler() != null)
            && stillRunning();
    }

    /**
     * Whether the plugin is still up enough to book a task.
     *
     * <p>Not a tidiness check. Bukkit sets a plugin disabled *before* it calls {@code onDisable},
     * and refuses {@code scheduleSyncDelayedTask} from a disabled plugin by throwing. Shutdown
     * closes the iris of every gate whose iris defaults closed, so without this a server
     * stopping with one dialled gate of that kind would start a sweep, throw out of
     * {@code shutdownStargate}, and land in the catch that wraps the whole save -- taking the
     * remaining gates, the rings, the beams, the mirrors and the database shutdown with it.
     *
     * <p>An animation must never cost the save. The same reasoning is written out at the top of
     * {@code onDisable} for the mirror restore, which learned it first.
     *
     * @return true if a sweep may be started
     */
    private static boolean stillRunning()
    {
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        return (plugin != null) && plugin.isEnabled();
    }

    /**
     * Sweeps a closed iris into view, over blocks that are already there.
     *
     * @param gate
     *            the gate, with its iris blocks already placed
     * @param under
     *            what the opening looked like before the iris closed, which the rings not yet
     *            arrived are drawn as
     */
    static void sweepClosed(final Stargate gate, final Material under)
    {
        sweepClosed(gate, under, null);
    }

    /**
     * The same, with something to do once the last ring has arrived.
     *
     * @param gate
     *            the gate, with its iris already standing
     * @param under
     *            what the opening looked like before the iris closed
     * @param afterwards
     *            run once the sweep finishes, or null; not run if it is called off
     */
    static void sweepClosed(final Stargate gate, final Material under, final Runnable afterwards)
    {
        final List<List<Location>> rings =
            IrisSweepDriver.rings(gate.getGatePortalBlocks(), gate.getEffectiveIrisAnimation(), true);
        // Everything is hidden first, so the client sees the opening as it was a moment ago
        // rather than the finished iris the server has just told it about.
        for (final List<Location> ring : rings)
        {
            StargateBlockSetup.sendCells(gate, ring, under);
        }
        // Then each ring is let through to the truth, which is the iris already standing there.
        // The far layer arrives with the ring that covers it, so a see-through iris has the
        // wormhole behind it from its very first ring rather than the landscape.
        new IrisSweepDriver<>(rings, new GateCanvas(gate, irisAsItStands(gate), true, afterwards)).start();
    }

    /**
     * What a closed iris's cells should be shown as once a ring is let through.
     *
     * <p>Null, for the block really there, when the iris is built. A drawn iris is air on the
     * server, so sending the real block uncovered the opening a ring at a time instead.
     *
     * @return the iris material if it is drawn, or null to send the real blocks
     */
    private static Material irisAsItStands(final Stargate gate)
    {
        return (gate.isGateIrisActive() && StargateBlockSetup.irisIsDrawn(gate))
            ? gate.getEffectiveIrisMaterial() : null;
    }

    /**
     * Sweeps an iris out of view, before the blocks behind it are taken away.
     *
     * @param gate
     *            the gate, with its iris blocks still in place
     * @param under
     *            what the opening should look like once the iris has gone
     * @param afterwards
     *            run once the last ring has cleared, to take the real blocks away
     */
    static void sweepOpen(final Stargate gate, final Material under, final Runnable afterwards)
    {
        // Drawn as the bare opening rather than as the truth: the iris blocks are still there
        // and stay there until the sweep ends, so sending what is really in the cell would
        // paint the iris back over itself and the open would not be seen to happen at all.
        new IrisSweepDriver<>(IrisSweepDriver.rings(gate.getGatePortalBlocks(), gate.getEffectiveIrisAnimation(), false),
            new GateCanvas(gate, under, false, afterwards)).start();
    }

    /**
     * A gate's sweep: block changes for the rings, the scheduler for the steps.
     *
     * @param draw
     *            what to draw each ring as, or null to send what is really in those cells
     * @param covering
     *            true while the iris closes, which brings the far layer in rather than out
     * @param afterwards
     *            run after the last ring, or null
     */
    private record GateCanvas(Stargate gate, Material draw, boolean covering, Runnable afterwards)
        implements IrisSweepDriver.Canvas<Location>
    {
        /** A gate can lose its world between two steps, on a server unloading one. */
        @Override
        public boolean stillValid()
        {
            return gate.getGateWorld() != null;
        }

        @Override
        public void drawRing(final List<Location> ring)
        {
            StargateBlockSetup.sendCells(gate, ring, draw);
        }

        @Override
        public void moveHorizon(final List<Location> ring)
        {
            StargateBlockSetup.horizonBehind(gate, ring, covering);
        }

        @Override
        public IrisSweepDriver.Booking later(final long ticks, final Runnable step)
        {
            final int task = WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), step,
                ticks);
            return () ->
            {
                if (WormholeXTreme.getScheduler() != null)
                {
                    WormholeXTreme.getScheduler().cancelTask(task);
                }
            };
        }

        @Override
        public void register(final IrisSweepDriver<Location> sweep)
        {
            running.put(key(gate), sweep);
        }

        @Override
        public void unregister()
        {
            running.remove(key(gate));
        }

        @Override
        public void settle()
        {
            if (afterwards != null)
            {
                afterwards.run();
            }
        }
    }

    /**
     * Calls off any sweep running on a gate, leaving the blocks as they truly are.
     *
     * <p>Called when the iris is toggled again before a sweep finishes. The half-drawn picture
     * is not unwound frame by frame -- the true blocks are simply sent, which is both the
     * shortest way back to honest and the state the next sweep expects to start from.
     */
    static void cancel(final Stargate gate)
    {
        final IrisSweepDriver<Location> sweep = running.remove(key(gate));
        if (sweep != null)
        {
            sweep.cancel();
        }
        if ((gate != null) && (gate.getGateWorld() != null))
        {
            // An opening sweep leaves a built iris standing until its last step, which is dropped
            // here: take it away now, to what the opening shows at this moment rather than what it
            // showed when the sweep began (#434).
            if ((sweep != null) && !gate.isGateIrisActive() && !StargateBlockSetup.irisIsDrawn(gate))
            {
                gate.fillGateInterior(gate.isGatePortalOpen()
                    ? GateViews.horizonOf(gate, gate.getEffectivePortalMaterial()) : Material.AIR);
            }
            StargateBlockSetup.sendCells(gate, gate.getGatePortalBlocks(), irisAsItStands(gate));
        }
    }

    /**
     * Calls off every sweep, for plugin shutdown.
     */
    public static void cancelAll()
    {
        for (final String name : running.keySet().toArray(new String[0]))
        {
            final IrisSweepDriver<Location> sweep = running.remove(name);
            if (sweep != null)
            {
                sweep.cancel();
            }
        }
    }

    /**
     * Whether a sweep is running on a gate.
     *
     * @return true if one is
     */
    static boolean isSweeping(final Stargate gate)
    {
        return running.containsKey(key(gate));
    }

    /**
     * The key a gate's sweep is held under.
     *
     * @return its name, or the empty string for a gate that has none
     */
    private static String key(final Stargate gate)
    {
        if ((gate == null) || (gate.getGateName() == null))
        {
            return "";
        }
        return gate.getGateName();
    }
}
