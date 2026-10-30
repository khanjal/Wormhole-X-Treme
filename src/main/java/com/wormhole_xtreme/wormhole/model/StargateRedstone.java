package com.wormhole_xtreme.wormhole.model;

import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Powerable;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.plugin.CoreProtectLog;
import com.wormhole_xtreme.wormhole.utils.GateRedstoneWrite;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * The redstone a gate carries: its dial and sign wires, the gate-activated lever, and the dial
 * lever's state (#430, #734).
 */
final class StargateRedstone
{
    /** Static helpers only. */
    private StargateRedstone()
    {
    }

    /**
     * Places or removes all redstone components (dial wire, sign wire, gate-
     * activated lever) in a single call.
     *
     * @param create {@code true} to place; {@code false} to remove
     */
    static void setupRedstone(final Stargate gate, final boolean create)
    {
        if (gate.isGateSignPowered())
        {
            setupRedstoneDialWire(gate, create);
            setupRedstoneSignDialWire(gate, create);
        }
        setupRedstoneGateActivatedLever(gate, create);
    }

    /** Places or removes the dial activation redstone wire. */
    static void setupRedstoneDialWire(final Stargate gate, final boolean create)
    {
        setupRedstoneWire(gate, gate.getGateRedstoneDialActivationBlock(), "RD", create);
    }

    /** Places or removes the sign-dial redstone wire. */
    static void setupRedstoneSignDialWire(final Stargate gate, final boolean create)
    {
        setupRedstoneWire(gate, gate.getGateRedstoneSignActivationBlock(), "RS", create);
    }

    /**
     * Lays one marker's redstone wire, or takes it up again.
     *
     * <p>Both callers were the same thirty lines, differing only in which block they read and
     * two characters of a log line.
     *
     * <p>Neither direction touches a block it did not put there. On the way in, a cell
     * somebody has already built in is left alone and reported rather than overwritten; on
     * the way out, only an actual wire is cleared, because between laying and lifting a
     * player may have replaced it. The gate's structure list follows the same rule, so it
     * never claims a block it did not place or abandons one it did.
     *
     * @param gate
     *            the gate being wired
     * @param target
     *            the block the marker resolved to, or null if the shape has no such marker
     * @param marker
     *            the marker's name, for the log line when the cell is occupied
     * @param create
     *            true to lay the wire, false to take it up
     */
    private static void setupRedstoneWire(final Stargate gate, final Block target,
        final String marker, final boolean create)
    {
        if (target == null)
        {
            return;
        }
        if (create)
        {
            try
            {
                final Material current = target.getType();
                if ((current == Material.AIR) || (current == Material.REDSTONE_WIRE))
                {
                    gate.getGateStructureBlocks().add(target.getLocation());
                    // Wire laid again over its own wire is not a placement, so every regen does not log one.
                    if (current == Material.AIR)
                    {
                        CoreProtectLog.placing(CoreProtectLog.PLUGIN_USER, target, Material.REDSTONE_WIRE, null);
                    }
                    target.setType(Material.REDSTONE_WIRE);
                }
                else
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                        "Skipping " + marker + " placement; target occupied: " + current);
                }
            }
            catch (final RuntimeException ignore) { /* placing the marker is best effort */ }
        }
        else if (target.getType() == Material.REDSTONE_WIRE)
        {
            gate.getGateStructureBlocks().remove(target.getLocation());
            CoreProtectLog.removed(CoreProtectLog.PLUGIN_USER, target);
            target.setType(Material.AIR);
        }
    }

    /** Places or removes the gate-activated output lever. */
    static void setupRedstoneGateActivatedLever(final Stargate gate, final boolean create)
    {
        if (gate.getGateRedstoneGateActivatedBlock() != null)
        {
            if (create)
            {
                placeGateActivatedLever(gate, gate.getGateRedstoneGateActivatedBlock());
            }
            else
            {
                if (gate.getGateRedstoneGateActivatedBlock().getType() == Material.LEVER)
                {
                    gate.getGateStructureBlocks().remove(gate.getGateRedstoneGateActivatedBlock().getLocation());
                    CoreProtectLog.removed(CoreProtectLog.PLUGIN_USER, gate.getGateRedstoneGateActivatedBlock());
                    gate.getGateRedstoneGateActivatedBlock().setType(Material.AIR);
                }
            }
        }
    }

    /** Places the gate-activated lever, or claims one already there; anything else is left alone. */
    private static void placeGateActivatedLever(final Stargate gate, final Block ra)
    {
        try
        {
            final Material current = ra.getType();
            // A lever already there is the gate's own, as a wire is: a regenerated gate
            // finds the one it placed before, and has to claim it again (#440).
            if (current == Material.LEVER)
            {
                // Left as it stands, not reset: an open gate's lever is thrown.
                if (!gate.getGateStructureBlocks().contains(ra.getLocation()))
                {
                    gate.getGateStructureBlocks().add(ra.getLocation());
                }
            }
            else if (current == Material.AIR)
            {
                gate.getGateStructureBlocks().add(ra.getLocation());
                CoreProtectLog.placing(CoreProtectLog.PLUGIN_USER, ra, Material.LEVER, null);
                ra.setType(Material.LEVER);
            }
            else
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Skipping RA lever placement; target occupied: " + current);
            }
        }
        catch (final RuntimeException ignore) { /* placing the marker is best effort */ }
    }

    /**
     * Updates the DHD lever/button block to reflect the current gate activation
     * state. Automatically replaces buttons with levers so the lever can be
     * held in the "on" position.
     *
     * @param regenerate {@code true} to forcibly replace a missing lever
     */
    static void toggleDialLeverState(final Stargate gate, final boolean regenerate)
    {
        if (gate.getGateDialLeverBlock() != null)
        {
            if (gate.isGateActive())
            {
                WorldUtils.scheduleChunkLoad(gate.getGateDialLeverBlock());
            }
            Material mat = Material.AIR;
            try
            {
                mat = gate.getGateDialLeverBlock().getType();
            }
            catch (final RuntimeException t)
            {
                mat = Material.AIR;
            }

            // Only create a lever if the activation holder is empty. Preserve the player's
            // placed activation item (button/lever) otherwise.
            if (regenerate && (mat == Material.AIR))
            {
                CoreProtectLog.placing(CoreProtectLog.PLUGIN_USER, gate.getGateDialLeverBlock(), Material.LEVER, null);
                gate.getGateDialLeverBlock().setType(Material.LEVER);
                final Directional rld = (Directional) gate.getGateDialLeverBlock().getBlockData();
                rld.setFacing(gate.getGateFacing());
                gate.getGateDialLeverBlock().setBlockData(rld);
                mat = gate.getGateDialLeverBlock().getType();
            }

            // Preserve whatever activation the player placed.  If it's a lever,
            // update its powered state; do not convert buttons to levers.
            if (mat == Material.LEVER)
            {
                // Flipping this lever fires BlockRedstoneEvent straight back at the redstone
                // listener, for the lever and for everything it powers. Marked as ours so the
                // listener does not read the gate opening as somebody pressing the button --
                // which dialled a sign gate twice. See GateRedstoneWrite.
                GateRedstoneWrite.begin();
                try
                {
                    final Powerable llp = (Powerable) gate.getGateDialLeverBlock().getBlockData();
                    llp.setPowered(gate.isGateActive());
                    gate.getGateDialLeverBlock().setBlockData(llp);
                }
                catch (final RuntimeException ignore)
                {
                    // A lever that refuses the write leaves the gate's light wrong, which is
                    // cosmetic. Errors are left to propagate; the finally still clears the
                    // guard either way, so a failure here cannot wedge the redstone listener.
                }
                finally
                {
                    GateRedstoneWrite.end();
                }
            }
            if (!gate.isGateActive())
            {
                WorldUtils.scheduleChunkUnload(gate.getGateDialLeverBlock());
            }
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Dial Button Lever Gate: \"" + gate.getGateName() + "\" Material: \"" + mat.toString() + "\"");
        }
    }

    /**
     * Pulses the gate-activated redstone output lever to match the gate's
     * current activation state.
     */
    static void toggleRedstoneGateActivatedPower(final Stargate gate)
    {
        if (gate.isGateRedstonePowered()
            && (gate.getGateRedstoneGateActivatedBlock() != null)
            && (gate.getGateRedstoneGateActivatedBlock().getType() == Material.LEVER))
        {
            // The listener already refuses this lever as a trigger by position, but not the
            // conductors it powers, and on a small shape those can touch the DHD. Marked as
            // ours for the same reason the dial lever is.
            GateRedstoneWrite.begin();
            try
            {
                final Powerable rp = (Powerable) gate.getGateRedstoneGateActivatedBlock().getBlockData();
                rp.setPowered(gate.isGateActive());
                gate.getGateRedstoneGateActivatedBlock().setBlockData(rp);
            }
            finally
            {
                GateRedstoneWrite.end();
            }
        }
    }
}
