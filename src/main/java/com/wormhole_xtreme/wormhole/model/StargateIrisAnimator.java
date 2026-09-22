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
    private static final ConcurrentHashMap<String, Integer> running = new ConcurrentHashMap<>();

    /** Static helpers only. */
    private StargateIrisAnimator()
    {
    }

    /**
     * Whether a gate's iris should sweep rather than arrive at once.
     *
     * @param gate
     *            the gate
     * @return true if there is a sweep worth running
     */
    static boolean sweeps(final Stargate gate)
    {
        return ConfigManager.isGateIrisAnimated()
            && (gate != null)
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
        final List<List<Location>> rings =
            IrisSweep.closingOrder(gate.getGatePortalBlocks(), ConfigManager.getGateIrisStyle(),
                ConfigManager.getGateIrisMaxSteps());
        // Everything is hidden first, so the client sees the opening as it was a moment ago
        // rather than the finished iris the server has just told it about.
        for (final List<Location> ring : rings)
        {
            StargateBlockSetup.sendCells(gate, ring, under);
        }
        // Then each ring is let through to the truth, which is the iris already standing there.
        step(gate, rings, 0, null, null);
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
        step(gate, IrisSweep.openingOrder(gate.getGatePortalBlocks(), ConfigManager.getGateIrisStyle(),
            ConfigManager.getGateIrisMaxSteps()), 0, under, afterwards);
    }

    /**
     * Draws one ring and books the next.
     *
     * @param gate
     *            the gate
     * @param rings
     *            the rings, in the order they are drawn
     * @param index
     *            which ring
     * @param draw
     *            what to draw each ring as, or null to send what is really in those cells
     * @param afterwards
     *            run after the last ring, or null
     */
    private static void step(final Stargate gate, final List<List<Location>> rings, final int index,
        final Material draw, final Runnable afterwards)
    {
        if (index >= rings.size())
        {
            running.remove(key(gate));
            if (afterwards != null)
            {
                afterwards.run();
            }
            return;
        }
        // A gate can lose its world between two steps of a sweep, which is a handful of ticks
        // on a server that is unloading one. Nothing to draw on and nobody to draw it for.
        if (gate.getGateWorld() == null)
        {
            running.remove(key(gate));
            return;
        }
        StargateBlockSetup.sendCells(gate, rings.get(index), draw);
        final int task = WormholeXTreme.getScheduler().scheduleSyncDelayedTask(
            WormholeXTreme.getThisPlugin(),
            () -> step(gate, rings, index + 1, draw, afterwards),
            ConfigManager.getGateIrisStepTicks());
        running.put(key(gate), task);
    }

    /**
     * Calls off any sweep running on a gate, leaving the blocks as they truly are.
     *
     * <p>Called when the iris is toggled again before a sweep finishes. The half-drawn picture
     * is not unwound frame by frame -- the true blocks are simply sent, which is both the
     * shortest way back to honest and the state the next sweep expects to start from.
     *
     * @param gate
     *            the gate
     */
    static void cancel(final Stargate gate)
    {
        final Integer task = running.remove(key(gate));
        if ((task != null) && (WormholeXTreme.getScheduler() != null))
        {
            WormholeXTreme.getScheduler().cancelTask(task);
        }
        if ((gate != null) && (gate.getGateWorld() != null))
        {
            StargateBlockSetup.sendCells(gate, gate.getGatePortalBlocks(), null);
        }
    }

    /**
     * Calls off every sweep, for plugin shutdown.
     */
    public static void cancelAll()
    {
        for (final String name : running.keySet().toArray(new String[0]))
        {
            final Integer task = running.remove(name);
            if ((task != null) && (WormholeXTreme.getScheduler() != null))
            {
                WormholeXTreme.getScheduler().cancelTask(task);
            }
        }
    }

    /**
     * Whether a sweep is running on a gate.
     *
     * @param gate
     *            the gate
     * @return true if one is
     */
    static boolean isSweeping(final Stargate gate)
    {
        return running.containsKey(key(gate));
    }

    /**
     * The key a gate's sweep is held under.
     *
     * @param gate
     *            the gate
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
