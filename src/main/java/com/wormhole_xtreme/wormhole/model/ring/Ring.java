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

    /**
     * The shallowest a ceiling ring can be and still have room for its whole stack.
     *
     * <p>Kept where the stack's geometry is, so the two cannot drift apart: how much room a
     * ceiling ring needs is a fact about how tall the stack is, and both change together.
     */
    public static final int MIN_CEILING_DROP = RingAnimator.MIN_CEILING_DROP;

    /** The travelling slabs. Must be a slab: the rise animation is built out of slab halves. */
    private Material ringMaterial;

    /** What the pad shows from the countdown until the rings are home. */
    private Material lightMaterial;

    /** What a ring turns to as the transport light passes through it. */
    private Material flashMaterial;

    /** How this end's stack comes out. Its own, so two ends can look different. */
    private RingStyle style = RingStyle.CONCURRENT;

    /** What this end is called, if anything. Empty means it has no name. */
    private String name = "";

    /**
     * How far below the plane the floor is, for a ceiling ring.
     *
     * <p>Runtime only, never stored: floors change, so this is measured when a cycle engages
     * rather than remembered from when the ring was built. Meaningless for a floor ring,
     * whose stack builds from the plane itself.
     */
    private int drop = 0;

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
     *            what the pad shows while the ring is working; also the initial flash
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
        // Starts matched, so a ring that nobody has fiddled with looks like one thing rather
        // than two. Setting them apart is what makes the transport read as its own moment.
        this.flashMaterial = lightMaterial;
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

    /** @return what a ring turns to as the transport light passes through it */
    public Material getFlashMaterial()
    {
        return flashMaterial;
    }

    /**
     * Sets what a ring turns to as the transport light passes through it.
     *
     * <p>Separate from the pad's own light because they are two different moments. The pad
     * lights to say the ring is working and stays lit throughout; the flash is the instant of
     * transport running through the stack. Left matched they read as one effect, which is a
     * fine default and a waste of the distinction.
     *
     * @param flashMaterial
     *            the new material
     */
    public void setFlashMaterial(final Material flashMaterial)
    {
        this.flashMaterial = flashMaterial;
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

    /** @return what this end is called, or empty if it has no name */
    public String getName()
    {
        return name;
    }

    /**
     * Sets what this end is called.
     *
     * <p>A name belongs to an end rather than to the pair because the useful thing to say is
     * where somebody is <em>going</em>, and that is a different answer at each end. Two names
     * also read better in a listing than one label ever did — "Base to Tower" says which two
     * places are joined, where "Mine Line" only says somebody named it.
     *
     * @param name
     *            the new name, or empty to clear it
     */
    public void setName(final String name)
    {
        this.name = (name == null) ? "" : name.trim();
    }

    /**
     * The game's own list of slabs, if this server can hand it over.
     *
     * <p>Resolved once and remembered, rather than asked for per material. Reading it needs a
     * live registry, so it comes back null in a unit test and on any server that has not
     * finished starting — and asking again for every material in the game would then throw
     * and be caught a thousand times per tab press.
     */
    private static final org.bukkit.Tag<Material> SLAB_TAG = resolveSlabTag();

    /**
     * Asks the server for its slab tag.
     *
     * @return the tag, or null if there is no registry to ask
     */
    private static org.bukkit.Tag<Material> resolveSlabTag()
    {
        try
        {
            return org.bukkit.Tag.SLABS;
        }
        // Catching Throwable because a registry that is not ready fails in class
        // initialisation, which surfaces as an Error rather than an exception.
        catch (final Throwable ignored)
        {
            return null;
        }
    }

    /**
     * How far below the plane this ring's stack is built.
     *
     * <p>Zero for a floor ring: its rings come out of the plane and stack up from there. For
     * a ceiling ring it is the distance down to the floor, because the rings drop all the way
     * to the ground and stack up around whoever is standing on it — a stack hanging from the
     * ceiling of a tall room would leave the traveller standing under it rather than in it.
     *
     * <p>Never smaller than {@link #MIN_CEILING_DROP}, so a ring that has not been measured
     * yet still describes a stack that fits rather than one folded through its own ceiling.
     *
     * @return the drop in blocks
     */
    public int getDrop()
    {
        return (orientation == RingOrientation.CEILING) ? Math.max(drop, MIN_CEILING_DROP) : 0;
    }

    /**
     * Records how far below the plane the floor was found.
     *
     * @param drop
     *            the distance in blocks
     */
    public void setDrop(final int drop)
    {
        this.drop = drop;
    }

    /**
     * The layer the stack is built up from.
     *
     * <p>The plane for a floor ring, and the floor itself for a ceiling one. Everything about
     * a settled stack is measured from here, which is what makes the two orientations produce
     * the same stack in the end and differ only in how the rings get to it.
     *
     * @return the block layer a traveller's feet occupy
     */
    public int stackBase()
    {
        return anchorY - getDrop();
    }

    /**
     * How deep this ring's trigger volume has to run.
     *
     * <p>A floor ring holds its passengers in the space just above it. A ceiling ring's stand
     * on the floor, which may be most of a room below the plane, so its volume has to reach
     * that far or nobody standing in the right place would ever set it off.
     *
     * @param reach
     *            how deep a floor ring's volume runs
     * @param maxDrop
     *            the furthest a ceiling ring will look for its floor
     * @return the number of block layers to cover
     */
    public int volumeDepth(final int reach, final int maxDrop)
    {
        // Two past the furthest floor: one for the feet standing on it, one for the head.
        return (orientation == RingOrientation.CEILING) ? (maxDrop + 2) : reach;
    }

    /**
     * Whether a material can be used for the travelling ring.
     *
     * <p>It has to be a slab, and not for decoration. The rise is built out of slab halves —
     * a bottom slab fills the lower half of its block and a top slab the upper half — which
     * is the only way to get half-block resolution out of block placement. Anything else
     * would step a full block at a time and stop reading as rings rising.
     *
     * <p>Answered from the game's own {@code minecraft:slabs} tag where there is a server to
     * ask, so a data pack that adds a slab gets one for free and nothing has to be kept in
     * step by hand.
     *
     * <p>Falls back to the name when there is no registry — in a unit test, or before the
     * server has finished starting. That is exact for every slab the game ships, so the
     * fallback is a worse source rather than a wrong answer.
     *
     * @param material
     *            the candidate material
     * @return true if it can be the travelling ring
     */
    public static boolean isUsableAsRing(final Material material)
    {
        if (material == null)
        {
            return false;
        }
        if (SLAB_TAG != null)
        {
            try
            {
                return SLAB_TAG.isTagged(material);
            }
            catch (final RuntimeException ignored)
            {
                // Fall through to the name.
            }
        }
        return material.name().endsWith("_SLAB");
    }

    /**
     * Solid blocks that read as glowing, for the countdown pattern.
     *
     * <p>Named rather than referenced, so a version that has never heard of copper bulbs
     * simply skips them instead of failing to load.
     */
    private static final String[] GLOWING = {
        "GLOWSTONE", "SEA_LANTERN", "SHROOMLIGHT", "JACK_O_LANTERN", "REDSTONE_LAMP",
        "MAGMA_BLOCK", "CRYING_OBSIDIAN", "BEACON", "SCULK_CATALYST", "AMETHYST_BLOCK",
        "OCHRE_FROGLIGHT", "VERDANT_FROGLIGHT", "PEARLESCENT_FROGLIGHT",
        "COPPER_BULB", "EXPOSED_COPPER_BULB", "WEATHERED_COPPER_BULB", "OXIDIZED_COPPER_BULB",
        "WAXED_COPPER_BULB", "WAXED_EXPOSED_COPPER_BULB", "WAXED_WEATHERED_COPPER_BULB",
        "WAXED_OXIDIZED_COPPER_BULB",
    };

    /** The above, resolved once against whatever this server actually has. */
    private static final List<Material> GLOWING_MATERIALS = resolveGlowing();

    /**
     * Works out which of the glowing blocks exist here.
     *
     * @return the ones this server has
     */
    private static List<Material> resolveGlowing()
    {
        final List<Material> found = new ArrayList<Material>();
        for (final String name : GLOWING)
        {
            final Material material = Material.matchMaterial(name);
            if ((material != null) && material.isBlock())
            {
                found.add(material);
            }
        }
        return java.util.Collections.unmodifiableList(found);
    }

    /**
     * The blocks worth offering for a ring's countdown pattern.
     *
     * <p>Solid blocks that look lit. Two things narrow this beyond "blocks that emit light".
     *
     * <p>The first is that <em>none</em> of them will actually light anything: a ring is
     * drawn to clients and the server's light data is never touched, so the pattern looks lit
     * and casts nothing. That makes the choice entirely about appearance.
     *
     * <p>The second is that the pattern is drawn <em>into</em> a floor or ceiling, so a torch
     * or a lantern would be a block that normally needs something to hang on, rendered where
     * it cannot hang. Full solid blocks are the only ones that read correctly there.
     *
     * @return the suggested light materials
     */
    public static List<Material> glowingMaterials()
    {
        return GLOWING_MATERIALS;
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
     * The layer that opens when the ring wakes: the surface itself.
     *
     * <p>One block back against the direction the rings travel — the floor beneath a floor
     * ring, the ceiling above a ceiling one. Shown as air while the ring is working, so the
     * pattern reads as the surface parting rather than as something painted on it.
     *
     * @return the block layer to open
     */
    public int openPlaneY()
    {
        return anchorY - orientation.getTravel();
    }

    /**
     * The layer the lights sit in, one below the surface that opens.
     *
     * <p>Under the opening rather than in it, so what a player sees is a lit recess with the
     * rings climbing out of it. Lighting the surface itself made the pattern look painted on
     * the floor; putting the light a block further down and taking the surface away makes the
     * same pattern look like somewhere the rings come from.
     *
     * @return the block layer to light
     */
    public int lightPlaneY()
    {
        return anchorY - (2 * orientation.getTravel());
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
