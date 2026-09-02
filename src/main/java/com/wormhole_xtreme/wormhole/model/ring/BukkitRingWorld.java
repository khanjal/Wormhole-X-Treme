/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   The ring subsystem's one point of contact with a real world.
 */
package com.wormhole_xtreme.wormhole.model.ring;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * Everything the ring subsystem does to a real world, in one place.
 *
 * <p>This class exists to be boring. All the decisions — what order things happen in, who
 * travels, what gets put back — live in {@link RingCycle} and {@link RingAnimator}, where
 * they can be checked without a server. What is left here is placing blocks, reading blocks,
 * finding entities and moving them, with no logic worth arguing about.
 *
 * <p>It is also the only class that knows a slab is a {@link Slab}, which is why the
 * half-block animation needs no special handling anywhere else.
 */
public class BukkitRingWorld implements RingCycle.Surroundings
{
    /** The world this operates in. Both ends of a pair are always in it. */
    private final World world;

    /** How deep a trigger volume runs, needed to work out where arrivals land. */
    private final int reach;

    /**
     * Instantiates a world adapter.
     *
     * @param world
     *            the world to operate in
     * @param reach
     *            how deep each trigger volume runs
     */
    public BukkitRingWorld(final World world, final int reach)
    {
        this.world = world;
        this.reach = reach;
    }

    /* (non-Javadoc)
     * @see RingCycle.Surroundings#materialAt(int, int, int)
     */
    @Override
    public Material materialAt(final int x, final int y, final int z)
    {
        return world.getBlockAt(x, y, z).getType();
    }

    /* (non-Javadoc)
     * @see RingCycle.Surroundings#setBlock(int, int, int, org.bukkit.Material)
     */
    @Override
    public void setBlock(final int x, final int y, final int z, final Material material)
    {
        // Physics off. A ring is scenery that exists for a second or two, and letting it
        // trigger falling sand, water flow or block updates would leave the world changed
        // after the rings had gone.
        world.getBlockAt(x, y, z).setType(material, false);
    }

    /* (non-Javadoc)
     * @see RingCycle.Surroundings#setSlab(int, int, int, org.bukkit.Material, boolean)
     */
    @Override
    public void setSlab(final int x, final int y, final int z, final Material material,
        final boolean top)
    {
        final Block block = world.getBlockAt(x, y, z);
        final BlockData data = material.createBlockData();
        if (data instanceof Slab)
        {
            final Slab slab = (Slab) data;
            slab.setType(top ? Slab.Type.TOP : Slab.Type.BOTTOM);
            block.setBlockData(slab, false);
            return;
        }
        // A ring material is validated as a slab when it is set, so this should not happen.
        // Placing it as a plain block still animates, just without half-block movement,
        // which is a better outcome than a cycle that throws halfway through and leaves
        // the rings standing.
        block.setType(material, false);
    }

    /* (non-Javadoc)
     * @see RingCycle.Surroundings#passengersIn(java.util.List)
     */
    @Override
    public List<RingPassenger> passengersIn(final List<int[]> blocks)
    {
        final List<RingPassenger> out = new ArrayList<RingPassenger>();
        if (blocks.isEmpty())
        {
            return out;
        }
        // One region query rather than one per block: a ring interior is a handful of
        // columns and asking the world about each of them separately would be the same
        // answer several dozen times over.
        for (final Entity entity : world.getNearbyEntities(boundsOf(blocks)))
        {
            if (standsInAny(entity, blocks))
            {
                out.add(new BukkitRingPassenger(entity));
            }
        }
        return out;
    }

    /**
     * A box enclosing every block of a volume.
     *
     * @param blocks
     *            the volume, each entry {@code {x, y, z}}
     * @return a box containing all of them
     */
    private static BoundingBox boundsOf(final List<int[]> blocks)
    {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (final int[] block : blocks)
        {
            minX = Math.min(minX, block[0]);
            minY = Math.min(minY, block[1]);
            minZ = Math.min(minZ, block[2]);
            maxX = Math.max(maxX, block[0]);
            maxY = Math.max(maxY, block[1]);
            maxZ = Math.max(maxZ, block[2]);
        }
        return new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    /**
     * Whether an entity is standing in one of these blocks.
     *
     * <p>The box above is the ring's bounding cube, which includes the cut corners the ring
     * itself does not occupy. Checking the actual blocks afterwards is what stops somebody
     * standing just outside a circle from being counted as inside it.
     *
     * @param entity
     *            the entity to place
     * @param blocks
     *            the volume, each entry {@code {x, y, z}}
     * @return true if it is in the volume
     */
    private static boolean standsInAny(final Entity entity, final List<int[]> blocks)
    {
        final Location at = entity.getLocation();
        final int x = at.getBlockX();
        final int y = at.getBlockY();
        final int z = at.getBlockZ();
        for (final int[] block : blocks)
        {
            if ((block[0] == x) && (block[1] == y) && (block[2] == z))
            {
                return true;
            }
        }
        return false;
    }

    /* (non-Javadoc)
     * @see RingCycle.Surroundings#deliver(RingPassenger, Ring)
     */
    @Override
    public void deliver(final RingPassenger passenger, final Ring destination)
    {
        if (!(passenger instanceof BukkitRingPassenger))
        {
            return;
        }
        final Entity entity = ((BukkitRingPassenger) passenger).getEntity();
        final Location arrival = arrivalPoint(destination);
        // Keep the way they were facing. Being spun round on arrival is disorienting, and
        // unlike a gate a ring has no direction of its own to face them in.
        arrival.setYaw(entity.getLocation().getYaw());
        arrival.setPitch(entity.getLocation().getPitch());
        if (entity instanceof Player)
        {
            ((Player) entity).setNoDamageTicks(5);
        }
        entity.teleport(arrival);
    }

    /**
     * Where travellers land at a ring.
     *
     * <p>The centre of the ring, standing on its floor. For a floor ring that is the ring
     * plane itself, which is where somebody standing in it already is. For a ceiling ring
     * the plane is above their heads, so the arrival is the bottom of the volume — the floor
     * the rings hang over — rather than the ring itself.
     *
     * @param ring
     *            the ring being arrived at
     * @return where to put a traveller
     */
    public Location arrivalPoint(final Ring ring)
    {
        final int y = (ring.getOrientation() == RingOrientation.FLOOR)
            ? ring.getAnchorY()
            : (ring.getAnchorY() - (reach - 1));
        // Half a block along x and z so they stand in the middle of a block rather than on
        // its corner. An even ring has no centre block, so this lands in one of the middle
        // four, which is as central as a ring with no centre gets.
        return new Location(world, ring.getAnchorX() + 0.5D, y, ring.getAnchorZ() + 0.5D);
    }
}
