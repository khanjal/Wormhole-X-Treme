package com.wormhole_xtreme.wormhole.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * Whether a gate is still standing where it says it is.
 *
 * <p>The plugin protects its blocks with {@code BlockBreakEvent} and the physics handlers, and
 * nothing that goes through those can take a gate apart. WorldEdit does not go through those:
 * {@code //set}, {@code //replace} and {@code //cut} write blocks straight into the world and
 * fire none of them, so {@code //replace obsidian air} across a gate leaves it fully registered
 * with nothing standing. That is issue #53's counterpart and the older half of #54 -- the gate
 * does not break, it goes quietly dead.
 *
 * <p>There is no event to listen for and no Bukkit-visible hook WorldEdit offers, so this checks
 * at the moments that matter instead: when a dial sign is redrawn, and when a gate is dialled.
 *
 * <p><b>Never forces a chunk load.</b> Reading a block pulls its chunk in, and these run on the
 * dial path, so a gate in an unloaded chunk is reported as intact rather than checked. "Cannot
 * tell" has to read as "fine": guessing the other way would refuse to dial every gate whose far
 * end nobody has visited yet.
 */
public final class GateIntegrity
{
    private GateIntegrity() {}

    /**
     * How many of a gate's recorded frame blocks are now air.
     *
     * <p>Air specifically rather than "not the frame material", because a gate may legitimately
     * be built of anything and its material can be changed under it. Somebody replacing obsidian
     * with stone has not broken the gate; somebody replacing it with nothing has.
     *
     * @param gate
     *            the gate to look at
     * @return how many recorded frame blocks are missing, counting only blocks in loaded chunks
     */
    public static int missingStructureBlocks(final Stargate gate)
    {
        if (gate == null)
        {
            return 0;
        }
        int missing = 0;
        for (final Location location : gate.getGateStructureBlocks())
        {
            if (isMissing(location))
            {
                missing++;
            }
        }
        return missing;
    }

    /**
     * Whether a gate has lost enough of its frame that dialling it is pointless.
     *
     * <p>Any missing frame block at all, rather than a proportion. A gate with one block taken
     * out of its ring is not a gate any more -- the shape will not detect, the animation draws
     * against blocks that are not there, and a traveller arrives inside whatever replaced it.
     * There is no partial state worth being lenient about.
     *
     * @param gate
     *            the gate to look at
     * @return true if any recorded frame block in a loaded chunk is now air
     */
    public static boolean isStructureBroken(final Stargate gate)
    {
        return missingStructureBlocks(gate) > 0;
    }

    /**
     * Whether a gate's dial sign is recorded but no longer a sign.
     *
     * <p>The sharpest single case, and the one the 2011 report describes: the gate keeps
     * accepting incoming dials and keeps refusing to change target, because the sign it reads
     * the target off is not there to read.
     *
     * @param gate
     *            the gate to look at
     * @return true if the gate is sign-powered, has a dial sign block recorded, and that block
     *         is not a wall sign any more
     */
    public static boolean isDialSignMissing(final Stargate gate)
    {
        if ((gate == null) || !gate.isGateSignPowered())
        {
            return false;
        }
        final Block sign = gate.getGateDialSignBlock();
        if ((sign == null) || !isLoaded(sign.getWorld(), sign.getX(), sign.getZ()))
        {
            return false;
        }
        return !MaterialUtils.isWallSign(sign.getType());
    }

    /** A recorded block that is in a loaded chunk and is now air. */
    private static boolean isMissing(final Location location)
    {
        if ((location == null) || (location.getWorld() == null)
            || !isLoaded(location.getWorld(), location.getBlockX(), location.getBlockZ()))
        {
            return false;
        }
        return MaterialUtils.isAirMaterial(location.getBlock().getType());
    }

    /** Whether the chunk holding these block coordinates is already in memory. */
    private static boolean isLoaded(final World world, final int blockX, final int blockZ)
    {
        return (world != null) && world.isChunkLoaded(blockX >> 4, blockZ >> 4);
    }
}
