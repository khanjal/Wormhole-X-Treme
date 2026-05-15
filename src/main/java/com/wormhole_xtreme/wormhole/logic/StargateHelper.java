/*
 *   Wormhole X-Treme Plugin for Bukkit
 *   Trimmed helper: delegates serialization and shape loading,
 *   and exposes a small set of utility methods used across the codebase.
 */
package com.wormhole_xtreme.wormhole.logic;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.wormhole_xtreme.wormhole.model.GateSerializer;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateNetwork;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;

import java.util.List;

/**
 * Lightweight, trimmed Stargate helper. Responsibilities:
 * - Provide geometry utility used by unit tests
 * - Delegate serialization to GateSerializer
 * - Delegate shape loading/registry to StargateShapeRegistry
 * - Provide small stubs for legacy APIs still referenced elsewhere
 */
public final class StargateHelper
{
    private StargateHelper() { }

    /**
     * Compute the gate facing direction by analysing the spatial variance of
     * the gate's structure blocks while excluding the DHD/dial block location.
     * Returns null for indeterminate or invalid inputs.
     */
    public static BlockFace computeGateFacingFromGeometry(final Stargate gate)
    {
        if (gate == null)
        {
            return null;
        }
        final Block dhd = gate.getGateDialLeverBlock();
        if (dhd == null)
        {
            return null;
        }

        final List<Location> structure = gate.getGateStructureBlocks();
        if (structure == null || structure.isEmpty())
        {
            return null;
        }

        // Accumulate excluding any structure entry equal to the DHD coordinates
        double sumX = 0.0, sumZ = 0.0;
        int count = 0;
        for (final Location loc : structure)
        {
            if (loc == null)
            {
                continue;
            }
            final int lx = loc.getBlockX();
            final int ly = loc.getBlockY();
            final int lz = loc.getBlockZ();
            if (lx == dhd.getX() && ly == dhd.getY() && lz == dhd.getZ())
            {
                continue; // exclude DHD coordinate
            }
            sumX += lx;
            sumZ += lz;
            count++;
        }

        if (count == 0)
        {
            return null;
        }

        final double meanX = sumX / count;
        final double meanZ = sumZ / count;

        double varX = 0.0, varZ = 0.0;
        for (final Location loc : structure)
        {
            if (loc == null) continue;
            final int lx = loc.getBlockX();
            final int ly = loc.getBlockY();
            final int lz = loc.getBlockZ();
            if (lx == dhd.getX() && ly == dhd.getY() && lz == dhd.getZ()) continue;
            final double dx = lx - meanX;
            final double dz = lz - meanZ;
            varX += dx * dx;
            varZ += dz * dz;
        }

        // Use population variance (divide by count). For comparison only relative magnitudes matter.
        varX = varX / count;
        varZ = varZ / count;

        final double eps = 1e-9;
        if (Math.abs(varX - varZ) < eps)
        {
            return null; // indeterminate
        }

        if (varX > varZ)
        {
            // Frame spread mainly along X → gate faces NORTH/SOUTH depending on DHD Z
            return dhd.getZ() > meanZ ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        else
        {
            // Frame spread mainly along Z → gate faces EAST/WEST depending on DHD X
            return dhd.getX() > meanX ? BlockFace.EAST : BlockFace.WEST;
        }
    }

    // ---------------------------------------------------------------------
    // Delegation helpers (thin wrappers)
    // ---------------------------------------------------------------------

    public static void loadShapes()
    {
        StargateShapeRegistry.loadShapes();
    }

    public static StargateShape getStargateShape(final String name)
    {
        return StargateShapeRegistry.getStargateShape(name);
    }

    public static boolean isStargateShape(final String name)
    {
        return StargateShapeRegistry.isStargateShape(name);
    }

    public static Stargate parseVersionedData(final byte[] gate_data, final World w, final String name, final StargateNetwork network)
    {
        return GateSerializer.parseVersionedData(gate_data, w, name, network);
    }

    public static byte[] stargatetoBinary(final Stargate s)
    {
        return GateSerializer.stargatetoBinary(s);
    }

    // ---------------------------------------------------------------------
    // Backwards-compatible stubs for legacy callers. These return null / no-op
    // so the trimmed helper is safe to compile and run where full detection
    // logic is not required for unit-tests.
    // ---------------------------------------------------------------------

    public static Stargate checkStargate(final Block clickedBlock, final BlockFace direction)
    {
        return null;
    }

    public static Stargate checkStargate(final Block clickedBlock, final BlockFace direction, final StargateShape shape)
    {
        return null;
    }

    public static void debugShapeMatch(final Block buttonBlock, final BlockFace facing, final StargateShape shape)
    {
        // Intentionally no-op in trimmed helper
    }
}
