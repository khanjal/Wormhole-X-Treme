/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   One end of a transport ring pair.
 */
package com.wormhole_xtreme.wormhole.model.ring;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * One end of a transport ring pair: a pad set into a floor or ceiling.
 *
 * <p>A ring is deliberately thin. It knows where it is, which of the two patterns it is,
 * which way it faces and what it is made of — and derives everything else. In particular it
 * does <em>not</em> store its own footprint: those twelve or sixteen block coordinates are a
 * pure function of the anchor, the pattern and the orientation, so keeping a copy would only
 * create something that can fall out of step with the fields it was computed from.
 *
 * <p>A ring on its own does nothing. It is always half of a {@link RingPair}, which is the
 * unit that is stored, addressed and fired.
 *
 * <p>Instances are immutable apart from the material, which an owner may change.
 */
public class Ring
{
    /** Anchor x. For an even ring this is the low-x block of the central 2x2. */
    private final int anchorX;

    /** Anchor y: the block layer the perimeter slabs occupy. */
    private final int anchorY;

    /** Anchor z. For an even ring this is the low-z block of the central 2x2. */
    private final int anchorZ;

    /** Which of the two footprints this ring is. */
    private final RingPattern pattern;

    /** Whether the ring is set into a floor or a ceiling. */
    private final RingOrientation orientation;

    /** What the perimeter becomes while the ring is deployed. */
    private Material material;

    /**
     * Instantiates a new ring.
     *
     * @param anchorX
     *            anchor x, the low-x block of the central 2x2 for an even ring
     * @param anchorY
     *            anchor y, the block layer the perimeter slabs occupy
     * @param anchorZ
     *            anchor z, the low-z block of the central 2x2 for an even ring
     * @param pattern
     *            which of the two footprints this ring is
     * @param orientation
     *            whether the ring is set into a floor or a ceiling
     * @param material
     *            what the perimeter becomes while deployed
     */
    public Ring(final int anchorX, final int anchorY, final int anchorZ, final RingPattern pattern,
        final RingOrientation orientation, final Material material)
    {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.pattern = pattern;
        this.orientation = orientation;
        this.material = material;
    }

    /** @return anchor x */
    public int getAnchorX()
    {
        return anchorX;
    }

    /** @return anchor y, the block layer the perimeter slabs occupy */
    public int getAnchorY()
    {
        return anchorY;
    }

    /** @return anchor z */
    public int getAnchorZ()
    {
        return anchorZ;
    }

    /** @return which of the two footprints this ring is */
    public RingPattern getPattern()
    {
        return pattern;
    }

    /** @return whether the ring is set into a floor or a ceiling */
    public RingOrientation getOrientation()
    {
        return orientation;
    }

    /** @return what the perimeter becomes while the ring is deployed */
    public Material getMaterial()
    {
        return material;
    }

    /**
     * Sets what the perimeter becomes while the ring is deployed.
     *
     * @param material
     *            the new material
     */
    public void setMaterial(final Material material)
    {
        this.material = material;
    }

    /**
     * The blocks the player laid in slabs, and the blocks that animate.
     *
     * <p>All in the anchor's own layer: a ring is flat, and only the travelling copies of it
     * during a cycle ever leave that plane.
     *
     * @return the perimeter blocks, each as {@code {x, y, z}}
     */
    public List<int[]> perimeterBlocks()
    {
        return layer(pattern.getPerimeter(), anchorY);
    }

    /**
     * The blocks enclosed by the ring, in the anchor's own layer.
     *
     * <p>This is the floor of the trigger volume rather than the whole of it. For the space a
     * passenger actually occupies, use {@link #triggerVolumeBlocks(int)}.
     *
     * @return the interior blocks in the ring plane, each as {@code {x, y, z}}
     */
    public List<int[]> interiorBlocks()
    {
        return layer(pattern.getInterior(), anchorY);
    }

    /**
     * The space a passenger has to be in for this ring to take them.
     *
     * <p>The interior columns, extended {@code reach} blocks out of the ring plane and into
     * the room the ring serves — upward from a floor ring, downward from a ceiling one. The
     * plane layer itself is included, because for a floor ring that is exactly where a
     * standing player's feet are.
     *
     * <p>Reach is a parameter rather than a config read so this class stays testable without
     * a running server, and so the caller decides once rather than every ring looking it up
     * separately. It matters most for ceiling rings: a floor further below the ring than the
     * reach means people standing on it are not in the volume and will not travel.
     *
     * @param reach
     *            how many block layers deep the volume runs, including the ring plane
     * @return every block of the trigger volume, each as {@code {x, y, z}}
     */
    public List<int[]> triggerVolumeBlocks(final int reach)
    {
        final List<RingPattern.Offset> interior = pattern.getInterior();
        final List<int[]> out = new ArrayList<int[]>(interior.size() * Math.max(reach, 1));
        for (int step = 0; step < reach; step++)
        {
            out.addAll(layer(interior, anchorY + (orientation.getTravel() * step)));
        }
        return out;
    }

    /**
     * Places a set of offsets at one y.
     *
     * <p>The single place anchor and offset are added together, so perimeter, interior and
     * every layer of the trigger volume cannot disagree about where the ring is.
     *
     * @param offsets
     *            the pattern offsets to place
     * @param y
     *            the block layer to place them in
     * @return the blocks, each as {@code {x, y, z}}
     */
    private List<int[]> layer(final List<RingPattern.Offset> offsets, final int y)
    {
        final List<int[]> out = new ArrayList<int[]>(offsets.size());
        for (final RingPattern.Offset offset : offsets)
        {
            out.add(new int[] { anchorX + offset.getDx(), y, anchorZ + offset.getDz() });
        }
        return out;
    }

    /**
     * The perimeter as Bukkit locations, for the code that actually places blocks.
     *
     * @param world
     *            the world the ring is in
     * @return the perimeter block locations
     */
    public List<Location> perimeterLocations(final World world)
    {
        return locations(perimeterBlocks(), world);
    }

    /**
     * The interior plane as Bukkit locations.
     *
     * @param world
     *            the world the ring is in
     * @return the interior block locations in the ring plane
     */
    public List<Location> interiorLocations(final World world)
    {
        return locations(interiorBlocks(), world);
    }

    /**
     * Attaches a world to a list of raw block coordinates.
     *
     * @param blocks
     *            blocks as {@code {x, y, z}}
     * @param world
     *            the world to attach
     * @return the same blocks as locations
     */
    private static List<Location> locations(final List<int[]> blocks, final World world)
    {
        final List<Location> out = new ArrayList<Location>(blocks.size());
        for (final int[] block : blocks)
        {
            out.add(new Location(world, block[0], block[1], block[2]));
        }
        return out;
    }

    /**
     * Whether a block column falls inside this ring's footprint, perimeter or interior.
     *
     * <p>Ignores y deliberately. This answers "would these two rings collide if built here",
     * and two rings sharing a column at different heights still means one of them animating
     * through the other's passengers.
     *
     * @param x
     *            block x
     * @param z
     *            block z
     * @return true if the column is part of this ring
     */
    public boolean coversColumn(final int x, final int z)
    {
        return coversColumn(pattern.getPerimeter(), x, z) || coversColumn(pattern.getInterior(), x, z);
    }

    /**
     * Whether any offset in the given list lands on the given column.
     *
     * @param offsets
     *            the offsets to test
     * @param x
     *            block x
     * @param z
     *            block z
     * @return true if one of them covers the column
     */
    private boolean coversColumn(final List<RingPattern.Offset> offsets, final int x, final int z)
    {
        for (final RingPattern.Offset offset : offsets)
        {
            if (((anchorX + offset.getDx()) == x) && ((anchorZ + offset.getDz()) == z))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Distance between this ring's anchor and another's, on the horizontal plane.
     *
     * <p>Squared, so the minimum-separation check never needs a square root.
     *
     * @param other
     *            the other ring
     * @return the squared horizontal distance between anchors
     */
    public long anchorDistanceSquared(final Ring other)
    {
        final long dx = anchorX - other.anchorX;
        final long dz = anchorZ - other.anchorZ;
        return (dx * dx) + (dz * dz);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString()
    {
        return pattern + " " + orientation + " ring at " + anchorX + "," + anchorY + "," + anchorZ;
    }
}
