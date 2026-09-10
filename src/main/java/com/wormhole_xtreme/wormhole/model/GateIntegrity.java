package com.wormhole_xtreme.wormhole.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

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
     * <p>A count rather than a predicate because the caller needs the number for its message,
     * and any of them being missing is already enough: a gate with one block out of its ring
     * will not detect as a shape, and a traveller arrives inside whatever replaced it. There is
     * no proportion worth being lenient about, so callers branch on {@code > 0}.
     *
     * @param gate
     *            the gate to look at
     * @return how many recorded frame blocks are missing, counting only blocks in loaded chunks
     */
    public static int missingStructureBlocks(final Stargate gate)
    {
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

    /**
     * Whether this gate's dial sign has been taken out from under it.
     *
     * <p>{@code updateDialSign} already notices this the moment it matters -- a click, a load,
     * a refresh -- and logs it there. This is the same question asked from the outside, for
     * {@code /wormhole gate validate}, which may be checking a gate nobody has approached in a
     * while. Guarded the same way {@link #missingStructureBlocks} is: only in a chunk already
     * loaded, so asking never pulls one in just to answer.
     *
     * <p>A gate with no dial sign recorded at all -- one dialled only by lever, button or
     * redstone -- has nothing to lose here, so it reports as not missing.
     *
     * @param gate
     *            the gate to check
     * @return true if a dial sign is recorded, its chunk is loaded, and the block is no longer
     *         a sign
     */
    public static boolean isDialSignMissing(final Stargate gate)
    {
        final Block signBlock = gate.getGateDialSignBlock();
        if (signBlock == null)
        {
            return false;
        }
        if (!isLoaded(signBlock.getWorld(), signBlock.getX(), signBlock.getZ()))
        {
            return false;
        }
        return !(signBlock.getState() instanceof Sign);
    }
}
