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

    /** The travelling slabs. Must be a slab: the rise animation is built out of slab halves. */
    private Material ringMaterial;

    /** What the perimeter shows during the countdown, before anything moves. */
    private Material lightMaterial;

    /** How this end's stack comes out. Its own, so two ends can look different. */
    private RingStyle style = RingStyle.CONCURRENT;

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
     * @param ringMaterial
     *            the travelling slabs; must be a slab
     * @param lightMaterial
     *            what the perimeter shows during the countdown
     */
    public Ring(final int anchorX, final int anchorY, final int anchorZ, final RingPattern pattern,
        final RingOrientation orientation, final Material ringMaterial, final Material lightMaterial)
    {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.pattern = pattern;
        this.orientation = orientation;
        this.ringMaterial = ringMaterial;
        this.lightMaterial = lightMaterial;
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

    /** @return the travelling slabs */
    public Material getRingMaterial()
    {
        return ringMaterial;
    }

    /**
     * Sets the travelling slab material.
     *
     * @param ringMaterial
     *            the new material; should satisfy {@link #isUsableAsRing(Material)}
     */
    public void setRingMaterial(final Material ringMaterial)
    {
        this.ringMaterial = ringMaterial;
    }

    /** @return what the perimeter shows during the countdown */
    public Material getLightMaterial()
    {
        return lightMaterial;
    }

    /**
     * Sets the countdown light material.
     *
     * @param lightMaterial
     *            the new material; anything placeable will do
     */
    public void setLightMaterial(final Material lightMaterial)
    {
        this.lightMaterial = lightMaterial;
    }

    /** @return how this end's stack comes out */
    public RingStyle getStyle()
    {
        return style;
    }

    /**
     * Sets how this end's stack comes out.
     *
     * <p>Per end rather than per pair. The two ends are never watched at once — a traveller
     * is at one of them, and by the time they see the other they have already arrived — so
     * there is nothing to be gained by forcing them to match, and a base can deploy
     * differently from the outpost it connects to.
     *
     * <p>They do have to finish together, but that is arranged by waiting for the longer of
     * the two rather than by making them the same.
     *
     * @param style
     *            the new style
     */
    public void setStyle(final RingStyle style)
    {
        this.style = style == null ? RingStyle.CONCURRENT : style;
    }

    /**
     * Whether a material can be used for the travelling ring.
     *
     * <p>It has to be a slab, and not for decoration. The rise is built out of slab halves —
     * a bottom slab fills the lower half of its block and a top slab the upper half — which
     * is the only way to get half-block resolution out of block placement. Anything else
     * would step a full block at a time and stop reading as rings rising.
     *
     * <p>Tested by name rather than through {@code Tag.SLABS} or {@code createBlockData()},
     * both of which need a live server registry. This is called from command validation,
     * which should be unit-testable, and every slab in the game ends this way.
     *
     * @param material
     *            the candidate material
     * @return true if it can be the travelling ring
     */
    public static boolean isUsableAsRing(final Material material)
    {
        return (material != null) && material.name().endsWith("_SLAB");
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
     * The layer the countdown lights sit in.
     *
     * <p>One block back against the direction the rings travel: into the floor beneath a
     * floor ring, into the ceiling above a ceiling one. The template slabs rest in the space
     * the rings rise <em>through</em>, so lighting that space would put the pattern hanging
     * in the air rather than set into the surface it belongs to.
     *
     * @return the block layer to light
     */
    public int lightPlaneY()
    {
        return anchorY - orientation.getTravel();
    }

    /**
     * The perimeter, placed in a given layer rather than the anchor's own.
     *
     * @param y
     *            the block layer to place them in
     * @return the perimeter blocks at that height, each as {@code {x, y, z}}
     */
    public List<int[]> perimeterBlocksAt(final int y)
    {
        return layer(pattern.getPerimeter(), y);
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
