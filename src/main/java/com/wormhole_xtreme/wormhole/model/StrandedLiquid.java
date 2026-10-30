package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Real water and lava a closed gate's opening still holds from older versions (#430, #734).
 */
final class StrandedLiquid
{
    /** Static helpers only. */
    private StrandedLiquid()
    {
    }

    /**
     * Real water or lava still standing in a closed gate's opening or woosh.
     *
     * <p>The portal and the woosh are drawn to clients now, but older versions built them from
     * real blocks, and a dial that glitched part-way could leave some behind. Nothing standing
     * there belongs to the gate, though a gate built underwater has ordinary water in those cells,
     * which is why clearing it is asked for rather than done.
     *
     * @return the liquid blocks, none while the gate is open
     */
    static List<Block> strandedLiquid(final Stargate gate)
    {
        final List<Block> found = new ArrayList<>();
        if ((gate == null) || gate.isGateActive() || (gate.getGateWorld() == null))
        {
            return found;
        }
        final List<Location> cells = new ArrayList<>(gate.getGatePortalBlocks());
        for (int i = 0; i < StargateAnimator.wooshWaveCount(gate); i++)
        {
            final List<Location> wave = StargateAnimator.wooshWave(gate, i);
            if (wave != null)
            {
                cells.addAll(wave);
            }
        }
        final Set<String> seen = new HashSet<>();
        for (final Location cell : cells)
        {
            if (!seen.add(cell.getBlockX() + "," + cell.getBlockY() + "," + cell.getBlockZ()))
            {
                continue;
            }
            final Block block = gate.getGateWorld().getBlockAt(cell.getBlockX(), cell.getBlockY(), cell.getBlockZ());
            if ((block.getType() == Material.WATER) || (block.getType() == Material.LAVA))
            {
                found.add(block);
            }
        }
        return found;
    }

    /**
     * Clears the real water and lava standing in a closed gate's opening and woosh.
     *
     * @return how many blocks were cleared
     */
    static int clearStrandedLiquid(final Stargate gate)
    {
        final List<Block> liquid = strandedLiquid(gate);
        for (final Block block : liquid)
        {
            block.setType(Material.AIR);
        }
        return liquid.size();
    }
}
