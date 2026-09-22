package com.wormhole_xtreme.wormhole.logic;

import java.util.Collection;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * Clears the real blocks older versions left standing in a drawn iris's cells.
 *
 * <p>A vertical gate's iris used to be real blocks in the opening, and is now drawn on clients
 * over air. A world saved by an older version therefore has a gate's worth of stone or glass
 * sitting in every opening whose iris was shut at the time, and nothing would take it out: the
 * gate looks right, because the drawing and the blocks say the same thing, but the barrier is
 * still there to be broken, mined or left behind by the next crash.
 *
 * <p>Only cells holding that gate's own iris material are touched. Anything else in the opening
 * belongs to whoever put it there.
 */
public final class BuiltIrisUpgrade
{
    private BuiltIrisUpgrade() {}

    /**
     * Clears what it can reach of every loaded gate's built iris.
     *
     * <p>Run once, after gates load. Cells in chunks that are not loaded are left for
     * {@link #clearLeftover(Stargate)}, which the draw path calls when somebody walks up to
     * the gate -- loading a chunk per gate on every server start, for a one-off tidy-up, would
     * cost far more than it saves.
     *
     * @param gates
     *            the gates just loaded
     * @return how many gates had blocks taken out of their opening
     */
    public static int clearAll(final Collection<Stargate> gates)
    {
        int cleared = 0;
        for (final Stargate gate : gates)
        {
            try
            {
                if (clearLeftover(gate) > 0)
                {
                    cleared++;
                }
            }
            // One odd gate must not stop the rest, or the server starting.
            catch (final RuntimeException e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                    "Could not clear the built iris of gate " + gate.getGateName(), e);
            }
        }
        if (cleared > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Took the blocks out of "
                + cleared + ((cleared == 1) ? " gate's" : " gates'")
                + " iris; it is drawn on clients now.");
        }
        return cleared;
    }

    /**
     * Clears one gate's built iris, as far as its chunks are loaded.
     *
     * <p>Safe to call repeatedly: once the cells are air there is nothing left to match.
     *
     * @param gate
     *            the gate
     * @return how many cells were cleared
     */
    public static int clearLeftover(final Stargate gate)
    {
        if ((gate == null) || (gate.getGateWorld() == null)
            || !gate.isGateIrisActive() || !gate.isGateIrisDrawn())
        {
            return 0;
        }
        final Material iris = gate.getEffectiveIrisMaterial();
        if ((iris == null) || (iris == Material.AIR))
        {
            return 0;
        }
        final World world = gate.getGateWorld();
        int cleared = 0;
        for (final Location bc : gate.getGatePortalBlocks())
        {
            if (!world.isChunkLoaded(bc.getBlockX() >> 4, bc.getBlockZ() >> 4))
            {
                continue;
            }
            final Block b = world.getBlockAt(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            if (b.getType() == iris)
            {
                b.setType(Material.AIR);
                cleared++;
            }
        }
        return cleared;
    }
}
