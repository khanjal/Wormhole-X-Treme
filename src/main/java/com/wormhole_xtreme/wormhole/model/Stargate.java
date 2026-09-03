package com.wormhole_xtreme.wormhole.model;
import java.util.ArrayList;
import java.util.HashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

/**
 * WormholeXtreme Stargate Class/Instance.
 * 
 * @author Ben Echols (Lologarithm)
 * @author Dean Bailey (alron)
 * 
 */
public class Stargate
{

    /** The Loaded version, used to determine what version of parser to use. */
    private byte loadedVersion = -1;

    /** The Gate id. */
    private long gateId = -1;
    /** Name of this gate, used to index and target. */
    private String gateName = "";
    /** UUID string of the player who owns this gate. May be a legacy name string for gates built before the UUID migration. */
    private String gateOwner = null;
    /** Display name of the gate owner. Transient — not persisted directly; resolved from UUID or set at build time. */
    private String gateOwnerName = null;
    /** Network gate is connected to. */
    private StargateNetwork gateNetwork;
    /**
     * The gateshape that this gate uses.
     * This affects woosh depth and later materials
     */
    private StargateShape gateShape;
    /** The world this stargate is associated with. */
    private World gateWorld;
    /** Is this stargate already active? Can be active remotely and have no target of its own. */
    private boolean gateActive = false;

    /** Has this stargate been recently active?. */
    private boolean gateRecentlyActive = false;
    /** The direction that the stargate faces. */
    private BlockFace gateFacing;

    /** Is the stargate already lit up?. */
    private boolean gateLightsActive = false;
    /** Is activated through sign destination?. */
    private boolean gateSignPowered;
    /** The gate redstone powered. */
    private boolean gateRedstonePowered;
    /** The stargate that is being targeted by this gate. */
    private Stargate gateTarget = null;
    /** The current target on the sign, only used if gateSignPowered is true. */
    private Stargate gateDialSignTarget;
    /** Temp target id to store when loading gates. */
    private long gateTempSignTarget = -1;
    /** The network index the sign is pointing at. */
    private int gateDialSignIndex = 0;
    /** The temporary target stargate id. */
    private long gateTempTargetId = -1;
    /** The Iris deactivation code. */
    private String gateIrisDeactivationCode = "";
    /** Is the iris Active?. */
    private boolean gateIrisActive = false;
    /** The iris default setting. */
    private boolean gateIrisDefaultActive = false;
    /** The Teleport sign, used for selection of stargate target. */
    private Sign gateDialSign;
    /** The location to teleport players to. */
    private Location gatePlayerTeleportLocation;
    /** The location to teleport minecarts to. */
    private Location gateMinecartTeleportLocation;
    /** Location of the Button/Lever that activates this gate. */
    private Block gateDialLeverBlock;
    /** Location of the Button/Lever that activates the iris. */
    private Block gateIrisLeverBlock;
    /** The Teleport sign block. */
    private Block gateDialSignBlock;
    /** Block that toggle the activation state of the gate if nearby redstone is activated. */
    private Block gateRedstoneDialActivationBlock;
    /** Blocks to monitor for redstone input when auto-placing is disabled. */
    private final ArrayList<Block> gateRedstoneDialMonitorBlocks = new ArrayList<Block>();
    /** Block that will toggle sign target when redstone nearby is activated. */
    private Block gateRedstoneSignActivationBlock;
    /** The gate redstone gate activated block. */
    private Block gateRedstoneGateActivatedBlock;
    /** The Name block holder. Where we place the stargate name sign. */
    private Block gateNameBlockHolder;
    /** The gate activate scheduler task id. */
    private int gateActivateTaskId;
    /** The gate shutdown scheduler task id. */
    private int gateShutdownTaskId;

    /**
     * When this wormhole opened, in milliseconds, or 0 while closed.
     *
     * <p>Kept separate from the shutdown timer because that timer restarts every time the
     * gate is dialled. Something re-dialling on a schedule — a minecart crossing a detector
     * rail every few seconds — would otherwise hold a gate open indefinitely and lock
     * everyone else out of it.
     */
    private long gateOpenedAtMillis = 0L;

    /**
     * Gets when this wormhole opened.
     *
     * @return the open time in milliseconds, or 0 if the gate is closed
     */
    public long getGateOpenedAtMillis()
    {
        return gateOpenedAtMillis;
    }

    /**
     * Records that this wormhole has just opened, if it was not already.
     *
     * <p>Re-dialling an open gate deliberately does not restart this, so the maximum open
     * time is measured from when the wormhole first formed.
     */
    public void markGateOpened()
    {
        if (gateOpenedAtMillis == 0L)
        {
            gateOpenedAtMillis = System.currentTimeMillis();
        }
    }

    /**
     * Clears the open time, so the next dial starts a fresh maximum.
     */
    public void clearGateOpenedAt()
    {
        gateOpenedAtMillis = 0L;
    }

    /**
     * How much longer this wormhole may stay open before the maximum is reached.
     *
     * @param maxOpenSeconds
     *            the configured maximum, 0 for no limit
     * @return remaining milliseconds, or Long.MAX_VALUE when there is no limit
     */
    public long remainingOpenMillis(final int maxOpenSeconds)
    {
        if (maxOpenSeconds <= 0 || gateOpenedAtMillis == 0L)
        {
            return Long.MAX_VALUE;
        }
        return (gateOpenedAtMillis + (maxOpenSeconds * 1000L)) - System.currentTimeMillis();
    }
    /** The gate after shutdown scheduler task id. */
    private int gateAfterShutdownTaskId;
    /** The gate animation step 3d. */
    private int gateAnimationStep3D = 1;
    /** The gate animation step 2d. */
    private int gateAnimationStep2D = 0;
    /** The animation removing. */
    private boolean gateAnimationRemoving = false;
    /** The current_lighting_iteration. */
    private int gateLightingCurrentIteration = 0;
    /** List of all blocks contained in this stargate, including buttons and levers. */
    private final ArrayList<Location> gateStructureBlocks = new ArrayList<Location>();
    /** List of all blocks that that are part of the "portal". */
    private final ArrayList<Location> gatePortalBlocks = new ArrayList<Location>();
    /** List of all blocks that turn on when gate is active. */
    private final ArrayList<ArrayList<Location>> gateLightBlocks = new ArrayList<ArrayList<Location>>();
    /** List of all blocks that woosh in order when gate is active. */
    private final ArrayList<ArrayList<Location>> gateWooshBlocks = new ArrayList<ArrayList<Location>>();
    /** The Animated blocks. */
    private final ArrayList<Block> gateAnimatedBlocks = new ArrayList<Block>();
    /** The gate_order. */
    private final HashMap<Integer, Stargate> gateSignOrder = new HashMap<Integer, Stargate>();

    /** The gate custom. */
    private boolean gateCustom = false;
    /** The gate custom structure material. */
    private Material gateCustomStructureMaterial = null;
    /** The gate custom portal material. */
    private Material gateCustomPortalMaterial = null;
    /** The gate custom light material. */
    private Material gateCustomLightMaterial = null;
    /** The gate custom iris material. */
    private Material gateCustomIrisMaterial = null;
    /** The gate custom woosh ticks. */
    private int gateCustomWooshTicks = -1;
    /** The gate custom light ticks. */
    private int gateCustomLightTicks = -1;
    /** The gate custom woosh depth. */
    private int gateCustomWooshDepth = -1;
    /** The gate custom woosh depth squared. */
    private int gateCustomWooshDepthSquared = -1;

    /** Portal block coordinates as a set, for O(1) containment. Built lazily. */
    private java.util.Set<Location> gatePortalBlockLookup = null;

    /** Bounding box enclosing every portal block, for one-shot entity queries. Built lazily. */
    private org.bukkit.util.BoundingBox gatePortalBounds = null;

    /** Portal block count the caches were built from, used to spot a changed gate. */
    private int gatePortalCacheSize = -1;

    /**
     * Rebuilds the portal block caches if the portal block list has changed size.
     *
     * <p>The list is exposed mutably via {@link #getGatePortalBlocks()}, so there is no
     * mutation hook to invalidate on. Size is a good enough signal in practice: portal
     * blocks are populated once during detection and cleared wholesale on teardown.
     */
    private void refreshPortalCaches()
    {
        if ((gatePortalBlockLookup != null) && (gatePortalCacheSize == gatePortalBlocks.size()))
        {
            return;
        }
        final java.util.Set<Location> lookup = new java.util.HashSet<Location>(Math.max(16, gatePortalBlocks.size() * 2));
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (final Location l : gatePortalBlocks)
        {
            if (l == null)
            {
                continue;
            }
            final int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            lookup.add(new Location(gateWorld, x, y, z));
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
        gatePortalBlockLookup = lookup;
        gatePortalBounds = lookup.isEmpty()
            ? null
            : new org.bukkit.util.BoundingBox(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        gatePortalCacheSize = gatePortalBlocks.size();
    }

    /**
     * Checks whether the given block coordinates are one of this gate's portal blocks.
     *
     * <p>Backed by a set rather than a scan of the portal block list. This is called on
     * every player move that lands on a gate block, and once per entity found near an
     * active gate, so the linear version showed up on both hot paths.
     *
     * @param x
     *            block x
     * @param y
     *            block y
     * @param z
     *            block z
     * @return true if this is a portal interior block of this gate
     */
    public boolean isGatePortalBlockAt(final int x, final int y, final int z)
    {
        refreshPortalCaches();
        return gatePortalBlockLookup.contains(new Location(gateWorld, x, y, z));
    }

    /**
     * Whether this gate's portal lies flat, every block of it at one height.
     *
     * <p>Distinguishes a gate standing upright from one lying flat in the ground without
     * having to know which shape built it, which matters because the two are left in
     * different directions.
     *
     * @return true if the gate has portal blocks and they all share one height
     */
    private boolean isPortalFlat()
    {
        if (gatePortalBlocks.isEmpty())
        {
            return false;
        }
        final int y = gatePortalBlocks.get(0).getBlockY();
        for (final Location block : gatePortalBlocks)
        {
            if (block.getBlockY() != y)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Moves this gate's arrival point out of its own portal if that is where it sits.
     *
     * <p>Travellers should step out of a wormhole, not stand in it. Arriving inside means
     * appearing waist-deep in the portal material, floating on water the server does not
     * have, and having to walk out of the ring before anything looks right.
     *
     * <p>Gates built today are already placed a block clear of the ring, but that offset
     * came after gates had been built and their arrival point written to disk, and loading
     * restores the stored value rather than recomputing it. So old gates keep landing
     * people inside, and no amount of fixing the build path reaches them.
     *
     * <p>Rather than key this off a format version, it asks the question directly: is the
     * arrival point one of my portal blocks? A gate that is already correct is left alone,
     * whatever wrote it, and one that is wrong is corrected however it got that way.
     *
     * @return true if the arrival point was moved
     */
    public boolean normalizeGatePlayerTeleportLocation()
    {
        if ((gatePlayerTeleportLocation == null) || (gateFacing == null))
        {
            return false;
        }
        if (!isGatePortalBlockAt(gatePlayerTeleportLocation.getBlockX(),
                                 gatePlayerTeleportLocation.getBlockY(),
                                 gatePlayerTeleportLocation.getBlockZ()))
        {
            return false;
        }
        // Which way is out depends on how the portal lies, and the portal blocks say that
        // without needing to know which shape built the gate. A gate standing upright is
        // left through its face, along the gate's facing. A gate lying flat is left
        // upward — stepping along a horizontal facing there would slide the traveller
        // across the portal and out through the frame ring instead of clear of it.
        final boolean portalIsFlat = isPortalFlat();
        final int stepX = portalIsFlat ? 0 : gateFacing.getModX();
        final int stepY = portalIsFlat ? 1 : gateFacing.getModY();
        final int stepZ = portalIsFlat ? 0 : gateFacing.getModZ();
        if ((stepX == 0) && (stepY == 0) && (stepZ == 0))
        {
            return false;
        }

        // One step does it for a portal one block thick; the loop covers a deeper one and
        // stops rather than walking away for ever if that direction cannot get out.
        final int maxSteps = 4;
        for (int step = 1; step <= maxSteps; step++)
        {
            final double x = gatePlayerTeleportLocation.getX() + (stepX * step);
            final double y = gatePlayerTeleportLocation.getY() + (stepY * step);
            final double z = gatePlayerTeleportLocation.getZ() + (stepZ * step);
            final Location candidate = new Location(gateWorld, x, y, z,
                gatePlayerTeleportLocation.getYaw(), gatePlayerTeleportLocation.getPitch());
            if (!isGatePortalBlockAt(candidate.getBlockX(), candidate.getBlockY(), candidate.getBlockZ()))
            {
                gatePlayerTeleportLocation = candidate;
                return true;
            }
        }
        return false;
    }

    /**
     * Works the arrival point out again from the gate itself.
     *
     * <p>For a gate whose stored exit is simply wrong -- travellers arriving at the side of
     * it, or facing into the frame. The exit is computed once when a gate is built, from the
     * shape's exit marker and the detected facing, and then stored; nothing recomputed it
     * afterwards, so a bad value stayed bad for the life of the gate.
     *
     * <p>Derived from the portal blocks rather than from the shape, which means it does not
     * matter whether the shape file is still around or has changed since. The bottom row of
     * the portal is where feet belong, its middle is where the traveller should stand, and
     * one step along the facing puts them clear of it. A gate lying flat is left upward
     * instead, for the same reason the normaliser does it: stepping along a horizontal facing
     * there would slide them across the portal and out through the frame.
     *
     * <p>What this cannot fix is a gate whose <em>facing</em> is wrong, because the facing is
     * the thing it trusts. If a gate behaves as though it faces the wrong way in every other
     * respect too -- the woosh, the sign -- then it needs rebuilding rather than this.
     *
     * @return true if an arrival point was worked out and set
     */
    public boolean recomputeGatePlayerTeleportLocation()
    {
        if ((gateWorld == null) || (gateFacing == null))
        {
            return false;
        }
        final java.util.List<Location> portal = getGatePortalBlocks();
        if ((portal == null) || portal.isEmpty())
        {
            return false;
        }

        int lowest = Integer.MAX_VALUE;
        for (final Location l : portal)
        {
            lowest = Math.min(lowest, l.getBlockY());
        }
        long sumX = 0;
        long sumZ = 0;
        int counted = 0;
        for (final Location l : portal)
        {
            if (l.getBlockY() == lowest)
            {
                sumX += l.getBlockX();
                sumZ += l.getBlockZ();
                counted++;
            }
        }
        if (counted == 0)
        {
            return false;
        }

        final boolean flat = isPortalFlat();
        final int stepX = flat ? 0 : gateFacing.getModX();
        final int stepY = flat ? 1 : 0;
        final int stepZ = flat ? 0 : gateFacing.getModZ();
        if ((stepX == 0) && (stepY == 0) && (stepZ == 0))
        {
            return false;
        }

        // Block centres, so a traveller lands in the middle of the block rather than on its
        // corner, and at its floor rather than inside it.
        final double x = Math.round((double) sumX / counted) + 0.5 + stepX;
        final double y = lowest + stepY;
        final double z = Math.round((double) sumZ / counted) + 0.5 + stepZ;
        final Location fixed = new Location(gateWorld, x, y, z);
        try
        {
            fixed.setYaw(com.wormhole_xtreme.wormhole.utils.WorldUtils
                .getDegreesFromBlockFace(gateFacing));
            fixed.setPitch(0f);
        }
        catch (final RuntimeException ignore)
        {
            // A yaw that cannot be worked out is not worth losing the position over.
        }
        gatePlayerTeleportLocation = fixed;
        return true;
    }

    /**
     * Gets a box enclosing every portal block of this gate.
     *
     * <p>Lets the periodic entity scan ask the world for entities near the whole gate in
     * one query instead of one query per portal block — 21 of them for a Standard gate.
     * The box is a cuboid and the gate interior is a ring, so callers must still confirm
     * a candidate with {@link #isGatePortalBlockAt(int, int, int)}.
     *
     * @return the bounding box, or null if the gate has no portal blocks
     */
    public org.bukkit.util.BoundingBox getGatePortalBounds()
    {
        refreshPortalCaches();
        return gatePortalBounds;
    }

    /**
     * The palette this gate is built from. Set during detection; for gates read back from
     * storage it stays null until {@link #getGateMaterialGroup()} resolves it lazily from
     * the frame material in the world.
     */
    private MaterialGroup gateMaterialGroup = null;

    /** Whether lazy group resolution has already run, so a genuine miss is not retried. */
    private boolean gateMaterialGroupResolved = false;

    /**
     * Gets the material palette this gate is built from.
     *
     * <p>Resolution is lazy and cached. Gates loaded from storage do not persist their
     * palette — it is recovered from the frame material in the world, which makes it
     * self-healing (rebuild a gate in lapis and it becomes an Atlantis gate) and avoids
     * touching the on-disk format. Doing it lazily rather than at load also keeps startup
     * from forcing a chunk load for every gate on the server, which matters once a world
     * has hundreds of them.
     *
     * @return the group, or null if the frame material matches no configured group
     */
    public MaterialGroup getGateMaterialGroup()
    {
        if (!gateMaterialGroupResolved)
        {
            gateMaterialGroupResolved = true;
            if (gateMaterialGroup == null)
            {
                gateMaterialGroup = resolveMaterialGroupFromWorld();
            }
        }
        return gateMaterialGroup;
    }

    /**
     * Sets the material palette this gate is built from, skipping lazy resolution.
     *
     * @param group
     *            the group, may be null
     */
    public void setGateMaterialGroup(final MaterialGroup group)
    {
        gateMaterialGroup = group;
        gateMaterialGroupResolved = true;
    }

    /**
     * Reads the gate's first frame block and looks its material up in the registry.
     *
     * @return the matching group, or null if the block is unreadable or unrecognised
     */
    private MaterialGroup resolveMaterialGroupFromWorld()
    {
        try
        {
            if (gateWorld == null || gateStructureBlocks.isEmpty())
            {
                return null;
            }
            final Location first = gateStructureBlocks.get(0);
            final Material frame = gateWorld.getBlockAt(first.getBlockX(), first.getBlockY(), first.getBlockZ()).getType();
            return MaterialGroupRegistry.getGroupByStructureMaterial(frame);
        }
        catch (final Throwable ignore)
        {
            return null;
        }
    }

    /**
     * Gets the material the gate interior shows while a wormhole is open.
     *
     * <p>Resolution order is the same for every effective-material accessor: an explicit
     * per-gate override set by an admin wins, then the gate's material palette, then the
     * shape's own default.
     *
     * @return the portal material, never null
     */
    public Material getEffectivePortalMaterial()
    {
        if (gateCustom && (gateCustomPortalMaterial != null))
        {
            return gateCustomPortalMaterial;
        }
        // A shape that names this material in its own file outranks the palette; the
        // palette only supplies what the shape left unsaid.
        if (gateShape != null && gateShape.hasExplicitPortalMaterial())
        {
            return gateShape.getShapePortalMaterial();
        }
        final MaterialGroup group = getGateMaterialGroup();
        if (group != null)
        {
            return group.getPortalMaterial();
        }
        return gateShape != null ? gateShape.getShapePortalMaterial() : Material.WATER;
    }

    /**
     * Gets the material the iris is made of when engaged.
     *
     * @return the iris material, never null
     */
    public Material getEffectiveIrisMaterial()
    {
        if (gateCustom && (gateCustomIrisMaterial != null))
        {
            return gateCustomIrisMaterial;
        }
        // A shape that names this material in its own file outranks the palette; the
        // palette only supplies what the shape left unsaid.
        if (gateShape != null && gateShape.hasExplicitIrisMaterial())
        {
            return gateShape.getShapeIrisMaterial();
        }
        final MaterialGroup group = getGateMaterialGroup();
        if (group != null)
        {
            return group.getIrisMaterial();
        }
        return gateShape != null ? gateShape.getShapeIrisMaterial() : Material.STONE;
    }

    /**
     * Gets the material the light blocks become while the gate is active.
     *
     * @return the light material, never null
     */
    public Material getEffectiveLightMaterial()
    {
        if (gateCustom && (gateCustomLightMaterial != null))
        {
            return gateCustomLightMaterial;
        }
        // A shape that names this material in its own file outranks the palette; the
        // palette only supplies what the shape left unsaid.
        if (gateShape != null && gateShape.hasExplicitLightMaterial())
        {
            return gateShape.getShapeLightMaterial();
        }
        final MaterialGroup group = getGateMaterialGroup();
        if (group != null)
        {
            return group.getLightMaterial();
        }
        return gateShape != null ? gateShape.getShapeLightMaterial() : Material.GLOWSTONE;
    }

    /**
     * Gets the wall-sign material for this gate's name sign.
     *
     * <p>There is no per-gate override for this one, so resolution is shape-then-palette.
     *
     * @return the sign material, never null
     */
    public Material getEffectiveSignMaterial()
    {
        if (gateShape != null && gateShape.hasExplicitSignMaterial())
        {
            return gateShape.getShapeSignMaterial();
        }
        final MaterialGroup group = getGateMaterialGroup();
        if (group != null)
        {
            return group.getSignMaterial();
        }
        return gateShape != null ? gateShape.getShapeSignMaterial() : Material.OAK_WALL_SIGN;
    }

    /**
     * Gets the material the gate frame is built from.
     *
     * @return the structure material, never null
     */
    public Material getEffectiveStructureMaterial()
    {
        if (gateCustom && (gateCustomStructureMaterial != null))
        {
            return gateCustomStructureMaterial;
        }
        // Unlike the other materials, the frame is not a styling choice the shape gets to
        // state: it is whatever the player actually built the gate out of, and that is
        // precisely what selected the palette. Preferring the shape's declaration here
        // reports OBSIDIAN for a gate made of lapis, and StargateAnimator uses this value
        // to restore light blocks after the lighting animation — so it would rebuild that
        // gate's chevrons in the wrong material.
        final MaterialGroup group = getGateMaterialGroup();
        if (group != null)
        {
            return group.getStructureMaterial();
        }
        return gateShape != null ? gateShape.getShapeStructureMaterial() : Material.OBSIDIAN;
    }

    /**
     * Gets the tick delay between woosh animation steps.
     *
     * @return the woosh tick delay
     */
    public int getEffectiveWooshTicks()
    {
        if (gateCustom && (gateCustomWooshTicks >= 0))
        {
            return gateCustomWooshTicks;
        }
        return gateShape != null ? gateShape.getShapeWooshTicks() : 3;
    }

    /**
     * Gets the tick delay between lighting animation steps.
     *
     * @return the light tick delay
     */
    public int getEffectiveLightTicks()
    {
        if (gateCustom && (gateCustomLightTicks >= 0))
        {
            return gateCustomLightTicks;
        }
        return gateShape != null ? gateShape.getShapeLightTicks() : 2;
    }

    /**
     * Gets the woosh depth used for splash effects.
     *
     * @return the woosh depth, 0 when the gate has none
     */
    public int getEffectiveWooshDepth()
    {
        if (gateCustom && (gateCustomWooshDepth >= 0))
        {
            return gateCustomWooshDepth;
        }
        return gateShape != null ? gateShape.getShapeWooshDepth() : 0;
    }

    /**
     * Gets the woosh depth used for splash effects, squared for distance comparisons.
     *
     * @return the squared woosh depth
     */
    public int getEffectiveWooshDepthSquared()
    {
        if (gateCustom && (gateCustomWooshDepthSquared >= 0))
        {
            return gateCustomWooshDepthSquared;
        }
        return gateShape != null ? gateShape.getShapeWooshDepthSquared() : 0;
    }

    /**
     * Instantiates a new stargate.
     */
    public Stargate()
    {
        // Ensure a default shape exists so code and tests can rely on shape defaults
        this.gateShape = new StargateShape();
    }


    /**
     * Animate opening.
     */
    public void animateOpening()
    {
        StargateAnimator.animateOpening(this);
    }

    /**
     * Complete gate.
     * 
     * @param name
     *            the name
     * @param idc
     *            the idc
     */
    public void completeGate(final String name, final String idc)
    {
        setGateName(name);

        // 1. Setup Name Sign
        if (getGateNameBlockHolder() != null)
        {
            setupGateSign(true);
        }
        // 2. Set up Iris stuff
        setIrisDeactivationCode(idc);

        if (isGateRedstonePowered())
        {
            setupRedstoneGateActivatedLever(true);
            if (isGateSignPowered())
            {
                setupRedstoneDialWire(true);
                setupRedstoneSignDialWire(true);
            }
        }
    }

    /**
     * Delete gate blocks.
     */
    public void deleteGateBlocks()
    {
        StargateBlockSetup.deleteGateBlocks(this);
    }

    /**
     * Delete portal blocks.
     */
    public void deletePortalBlocks()
    {
        StargateBlockSetup.deletePortalBlocks(this);
    }

    /**
     * Delete teleport sign.
     */
    public void deleteTeleportSign()
    {
        StargateBlockSetup.deleteTeleportSign(this);
    }

    /**
     * This method activates the current stargate as if it had just been dialed.
     * This includes filling the event horizon, canceling any other shutdown events,
     * scheduling the shutdown time and scheduling the WOOSH if enabled.
     * Failed task schedules will cause gate to not activate, fill, or animate.
     */
    void dialStargate()
    {
        StargateDialManager.dialStargate(this);
    }

    /**
     * This method takes in a remote stargate and dials it if it is not active.
     * 
     * @param target
     *            the target stargate
     * @param force
     *            true to force dial the stargate, false to properly check if target gate is not active.
     * @return True if successful, False if remote target is already Active or if there is a failure scheduling stargate
     *         shutdowns.
     */
    public boolean dialStargate(final Stargate target, final boolean force)
    {
        return StargateDialManager.dialStargate(this, target, force);
    }

    /**
     * Opens or clears the portal interior. The server-side blocks stay AIR;
     * {@code material} is the appearance shown to nearby clients.
     *
     * @param material
     *            the appearance to show clients, or {@link Material#AIR} to clear
     */
    public void fillGateInterior(final Material material)
    {
        StargateBlockSetup.fillGateInterior(this, material);
    }

    /**
     * Fills the portal interior with real, solid iris blocks. Unlike
     * {@link #fillGateInterior(Material)} this places actual server-side blocks,
     * because the iris has to physically stop travellers.
     *
     * @param material
     *            the iris material to place
     */
    public void fillGateIris(final Material material)
    {
        StargateBlockSetup.fillGateIris(this, material);
    }

    /**
     * Gets the gate activate task id.
     * 
     * @return the gate activate task id
     */
    int getGateActivateTaskId()
    {
        return gateActivateTaskId;
    }

    /**
     * Gets the gate after shutdown task id.
     * 
     * @return the gate after shutdown task id
     */
    int getGateAfterShutdownTaskId()
    {
        return gateAfterShutdownTaskId;
    }

    /**
     * Gets the gate animated blocks.
     * 
     * @return the gate animated blocks
     */
    ArrayList<Block> getGateAnimatedBlocks()
    {
        return gateAnimatedBlocks;
    }

    /**
     * Gets the gate animation step 2d.
     * 
     * @return the gate animation step 2d
     */
    public int getGateAnimationStep2D()
    {
        return gateAnimationStep2D;
    }

    /**
     * Gets the gate animation step.
     * 
     * @return the gate animation step
     */
    int getGateAnimationStep3D()
    {
        return gateAnimationStep3D;
    }

    /**
     * Gets the gate custom iris material.
     * 
     * @return the gate custom iris material
     */
    public Material getGateCustomIrisMaterial()
    {
        return gateCustomIrisMaterial;
    }

    /**
     * Gets the gate custom light material.
     * 
     * @return the gate custom light material
     */
    public Material getGateCustomLightMaterial()
    {
        return gateCustomLightMaterial;
    }

    /**
     * Gets the gate custom light ticks.
     * 
     * @return the gate custom light ticks
     */
    public int getGateCustomLightTicks()
    {
        return gateCustomLightTicks;
    }

    /**
     * Gets the gate custom portal material.
     * 
     * @return the gate custom portal material
     */
    public Material getGateCustomPortalMaterial()
    {
        return gateCustomPortalMaterial;
    }

    /**
     * Gets the gate custom structure material.
     * 
     * @return the gate custom structure material
     */
    public Material getGateCustomStructureMaterial()
    {
        return gateCustomStructureMaterial;
    }

    /**
     * Gets the gate custom woosh depth.
     * 
     * @return the gate custom woosh depth
     */
    public int getGateCustomWooshDepth()
    {
        return gateCustomWooshDepth;
    }

    /**
     * Gets the gate custom woosh depth squared.
     * 
     * @return the gate custom woosh depth squared
     */
    public int getGateCustomWooshDepthSquared()
    {
        return gateCustomWooshDepthSquared;
    }

    /**
     * Gets the gate custom woosh ticks.
     * 
     * @return the gate custom woosh ticks
     */
    public int getGateCustomWooshTicks()
    {
        return gateCustomWooshTicks;
    }

    /**
     * Gets the gate activation block.
     * 
     * @return the gate activation block
     */
    public Block getGateDialLeverBlock()
    {
        return gateDialLeverBlock;
    }

    /**
     * Gets the gate teleport sign.
     * 
     * @return the gate teleport sign
     */
    public synchronized Sign getGateDialSign()
    {
        return gateDialSign;
    }

    /**
     * Gets the gate teleport sign block.
     * 
     * @return the gate teleport sign block
     */
    public synchronized Block getGateDialSignBlock()
    {
        return gateDialSignBlock;
    }

    /**
     * Gets the gate sign index.
     * 
     * @return the gate sign index
     */
    public synchronized int getGateDialSignIndex()
    {
        return gateDialSignIndex;
    }

    /**
     * Gets the gate sign target.
     * 
     * @return the gate sign target
     */
    public Stargate getGateDialSignTarget()
    {
        return gateDialSignTarget;
    }

    /**
     * Gets the gate facing.
     * 
     * @return the gate facing
     */
    public BlockFace getGateFacing()
    {
        return gateFacing;
    }

    /**
     * Gets the gate id.
     * 
     * @return the gate id
     */
    public long getGateId()
    {
        return gateId;
    }

    /**
     * Gets the gate iris deactivation code.
     * 
     * @return the gate iris deactivation code
     */
    public String getGateIrisDeactivationCode()
    {
        return gateIrisDeactivationCode;
    }

    /**
     * Gets the gate iris activation block.
     * 
     * @return the gate iris activation block
     */
    public Block getGateIrisLeverBlock()
    {
        return gateIrisLeverBlock;
    }

    /**
     * Gets the gate light blocks.
     * 
     * @return the gate light blocks
     */
    public ArrayList<ArrayList<Location>> getGateLightBlocks()
    {
        return gateLightBlocks;
    }

    /**
     * Gets the gate lighting current iteration.
     * 
     * @return the gate lighting current iteration
     */
    int getGateLightingCurrentIteration()
    {
        return gateLightingCurrentIteration;
    }

    /**
     * Gets the gate minecart teleport location.
     * 
     * @return the gate minecart teleport location
     */
    public Location getGateMinecartTeleportLocation()
    {
        return gateMinecartTeleportLocation;
    }

    /**
     * Gets the gate name.
     * 
     * @return the gate name
     */
    public String getGateName()
    {
        return gateName;
    }

    /**
     * Gets the gate name block holder.
     * 
     * @return the gate name block holder
     */
    public Block getGateNameBlockHolder()
    {
        return gateNameBlockHolder;
    }

    /**
     * Gets the gate network.
     * 
     * @return the gate network
     */
    public StargateNetwork getGateNetwork()
    {
        return gateNetwork;
    }

    /**
     * Gets the gate owner identifier (UUID string for new gates, or legacy player name).
     * 
     * @return the gate owner identifier
     */
    public String getGateOwner()
    {
        return gateOwner;
    }

    /**
     * Gets the human-readable display name of the gate owner.
     * Falls back to the raw owner string (legacy name or UUID) if no display name is set.
     *
     * @return the gate owner display name, or null if no owner is set
     */
    public String getGateOwnerName()
    {
        if (gateOwnerName != null)
        {
            return gateOwnerName;
        }
        return gateOwner;
    }

    /**
     * Returns true if the given player is the owner of this gate.
     * Handles both UUID-based identifiers (new gates) and legacy name-based identifiers.
     *
     * @param player the player to test
     * @return true if player owns this gate
     */
    public boolean isOwner(final Player player)
    {
        if ((player == null) || (gateOwner == null))
        {
            return false;
        }
        // Try UUID comparison first (new format)
        try
        {
            java.util.UUID.fromString(gateOwner);
            return player.getUniqueId().toString().equals(gateOwner);
        }
        catch (final IllegalArgumentException e)
        {
            // Legacy: stored as player name
            return player.getName().equalsIgnoreCase(gateOwner);
        }
    }

    /**
     * Gets the gate teleport location.
     * 
     * @return the gate teleport location
     */
    public Location getGatePlayerTeleportLocation()
    {
        return gatePlayerTeleportLocation;
    }

    /**
     * Gets the gate portal blocks.
     * 
     * @return the gate portal blocks
     */
    public ArrayList<Location> getGatePortalBlocks()
    {
        return gatePortalBlocks;
    }

    /**
     * Gets the gate redstone activation block.
     * 
     * @return the gate redstone activation block
     */
    public Block getGateRedstoneDialActivationBlock()
    {
        return gateRedstoneDialActivationBlock;
    }

    /**
     * Gets the list of blocks that should be monitored for redstone input.
     * These are used for redstone-enabled shapes where the plugin does not
     * place redstone dust but detects player-placed dust in the monitor area.
     *
     * @return modifiable list of monitor blocks (may be empty)
     */
    public ArrayList<Block> getGateRedstoneDialMonitorBlocks()
    {
        return gateRedstoneDialMonitorBlocks;
    }

    /**
     * Gets the gate redstone gate activated block.
     * 
     * @return the gate redstone gate activated block
     */
    public Block getGateRedstoneGateActivatedBlock()
    {
        return gateRedstoneGateActivatedBlock;
    }

    /**
     * Gets the gate redstone dial change block.
     * 
     * @return the gate redstone dial change block
     */
    public Block getGateRedstoneSignActivationBlock()
    {
        return gateRedstoneSignActivationBlock;
    }

    /**
     * Gets the gate shape.
     * 
     * @return the gate shape
     */
    public StargateShape getGateShape()
    {
        return gateShape;
    }

    /**
     * Gets the gate shutdown task id.
     * 
     * @return the gate shutdown task id
     */
    int getGateShutdownTaskId()
    {
        return gateShutdownTaskId;
    }

    /**
     * Gets the gate sign order.
     * 
     * @return the gate sign order
     */
    HashMap<Integer, Stargate> getGateSignOrder()
    {
        return gateSignOrder;
    }

    /**
     * Gets the gate structure blocks.
     * 
     * @return the gate structure blocks
     */
    public ArrayList<Location> getGateStructureBlocks()
    {
        return gateStructureBlocks;
    }

    /**
     * Gets the gate target.
     * 
     * @return the gate target
     */
    public Stargate getGateTarget()
    {
        return gateTarget;
    }

    /**
     * Gets the gate temp sign target.
     * 
     * @return the gate temp sign target
     */
    long getGateTempSignTarget()
    {
        return gateTempSignTarget;
    }

    /**
     * Gets the gate temp target id.
     * 
     * @return the gate temp target id
     */
    long getGateTempTargetId()
    {
        return gateTempTargetId;
    }

    /**
     * Gets the gate woosh blocks.
     * 
     * @return the gate woosh blocks
     */
    public ArrayList<ArrayList<Location>> getGateWooshBlocks()
    {
        return gateWooshBlocks;
    }

    /**
     * Gets the gate world.
     * 
     * @return the gate world
     */
    public World getGateWorld()
    {
        return gateWorld;
    }

    /**
     * Gets the loaded version.
     * 
     * @return the loaded version
     */
    public byte getLoadedVersion()
    {
        return loadedVersion;
    }

    /**
     * Checks if is gate active.
     * 
     * @return true, if is gate active
     */
    public boolean isGateActive()
    {
        return gateActive;
    }

    /**
     * Checks if is gate animation removing.
     * 
     * @return true, if is gate animation removing
     */
    boolean isGateAnimationRemoving()
    {
        return gateAnimationRemoving;
    }

    /**
     * Checks if is gate custom.
     * 
     * @return true, if is gate custom
     */
    public boolean isGateCustom()
    {
        return gateCustom;
    }

    /**
     * Checks if is gate iris active.
     * 
     * @return true, if is gate iris active
     */
    public boolean isGateIrisActive()
    {
        return gateIrisActive;
    }

    /**
     * Checks if is gate iris default active.
     * 
     * @return true, if is gate iris default active
     */
    boolean isGateIrisDefaultActive()
    {
        return gateIrisDefaultActive;
    }

    /**
     * Checks if is gate lit.
     * 
     * @return true, if is gate lit
     */
    public boolean isGateLightsActive()
    {
        return gateLightsActive;
    }

    /**
     * Checks if is gate recently active.
     * 
     * @return true, if is gate recently active
     */
    public boolean isGateRecentlyActive()
    {
        return gateRecentlyActive;
    }

    /**
     * Checks if is gate redstone powered.
     * 
     * @return true, if is gate redstone powered
     */
    public boolean isGateRedstonePowered()
    {
        return gateRedstonePowered;
    }

    /**
     * Checks if is gate sign powered.
     * 
     * @return true, if is gate sign powered
     */
    public boolean isGateSignPowered()
    {
        return gateSignPowered;
    }

    /**
     * Light or darken stargate and kick off woosh animation on active stargates.
     * 
     * @param on
     *            true to light, false to darken.
     */
    public void lightStargate(final boolean on)
    {
        StargateAnimator.lightStargate(this, on);
    }

    /**
     * Reset sign.
     * 
     * @param teleportSign
     *            the teleport sign
     */
    public void resetSign(final boolean teleportSign)
    {
        StargateDialManager.resetSign(this, teleportSign);
    }

    /**
     * Reset teleport sign.
     */
    public void resetTeleportSign()
    {
        StargateDialManager.resetTeleportSign(this);
    }

    /**
     * Sets the gate activate task id.
     * 
     * @param gateActivateTaskId
     *            the new gate activate task id
     */
    void setGateActivateTaskId(final int gateActivateTaskId)
    {
        this.gateActivateTaskId = gateActivateTaskId;
    }

    /**
     * Sets the gate active.
     * 
     * @param gateActive
     *            the new gate active
     */
    public void setGateActive(final boolean gateActive)
    {
        this.gateActive = gateActive;
        StargateManager.setGateOpenState(this, gateActive);
    }

    /**
     * Sets the gate after shutdown task id.
     * 
     * @param gateAfterShutdownTaskId
     *            the new gate after shutdown task id
     */
    void setGateAfterShutdownTaskId(final int gateAfterShutdownTaskId)
    {
        this.gateAfterShutdownTaskId = gateAfterShutdownTaskId;
    }

    /**
     * Sets the gate animation removing.
     * 
     * @param gateAnimationRemoving
     *            the new gate animation removing
     */
    void setGateAnimationRemoving(final boolean gateAnimationRemoving)
    {
        this.gateAnimationRemoving = gateAnimationRemoving;
    }

    /**
     * Sets the gate animation step 2d.
     * 
     * @param gateAnimationStep2D
     *            the new gate animation step 2d
     */
    public void setGateAnimationStep2D(final int gateAnimationStep2D)
    {
        this.gateAnimationStep2D = gateAnimationStep2D;
    }

    /**
     * Sets the gate animation step.
     * 
     * @param gateAnimationStep
     *            the new gate animation step
     */
    void setGateAnimationStep3D(final int gateAnimationStep3D)
    {
        this.gateAnimationStep3D = gateAnimationStep3D;
    }

    /**
     * Sets the gate custom.
     * 
     * @param gateCustom
     *            the new gate custom
     */
    public void setGateCustom(final boolean gateCustom)
    {
        this.gateCustom = gateCustom;
    }

    /**
     * Sets the gate custom iris material.
     * 
     * @param gateCustomIrisMaterial
     *            the new gate custom iris material
     */
    public void setGateCustomIrisMaterial(final Material gateCustomIrisMaterial)
    {
        this.gateCustomIrisMaterial = gateCustomIrisMaterial;
    }

    /**
     * Sets the gate custom light material.
     * 
     * @param gateCustomLightMaterial
     *            the new gate custom light material
     */
    public void setGateCustomLightMaterial(final Material gateCustomLightMaterial)
    {
        this.gateCustomLightMaterial = gateCustomLightMaterial;
    }

    /**
     * Sets the gate custom light ticks.
     * 
     * @param gateCustomLightTicks
     *            the new gate custom light ticks
     */
    public void setGateCustomLightTicks(final int gateCustomLightTicks)
    {
        this.gateCustomLightTicks = gateCustomLightTicks;
    }

    /**
     * Sets the gate custom portal material.
     * 
     * @param gateCustomPortalMaterial
     *            the new gate custom portal material
     */
    public void setGateCustomPortalMaterial(final Material gateCustomPortalMaterial)
    {
        this.gateCustomPortalMaterial = gateCustomPortalMaterial;
    }

    /**
     * Sets the gate custom structure material.
     * 
     * @param gateCustomStructureMaterial
     *            the new gate custom structure material
     */
    public void setGateCustomStructureMaterial(final Material gateCustomStructureMaterial)
    {
        this.gateCustomStructureMaterial = gateCustomStructureMaterial;
    }

    /**
     * Sets the gate custom woosh depth.
     * 
     * @param gateCustomWooshDepth
     *            the new gate custom woosh depth
     */
    public void setGateCustomWooshDepth(final int gateCustomWooshDepth)
    {
        this.gateCustomWooshDepth = gateCustomWooshDepth;
    }

    /**
     * Sets the gate custom woosh depth squared.
     * 
     * @param gateCustomWooshDepthSquared
     *            the new gate custom woosh depth squared
     */
    public void setGateCustomWooshDepthSquared(final int gateCustomWooshDepthSquared)
    {
        this.gateCustomWooshDepthSquared = gateCustomWooshDepthSquared;
    }

    /**
     * Sets the gate custom woosh ticks.
     * 
     * @param gateCustomWooshTicks
     *            the new gate custom woosh ticks
     */
    public void setGateCustomWooshTicks(final int gateCustomWooshTicks)
    {
        this.gateCustomWooshTicks = gateCustomWooshTicks;
    }

    /**
     * Sets the gate activation block.
     * 
     * @param gateDialLeverBlock
     *            the new gate dial lever block
     */
    public void setGateDialLeverBlock(final Block gateDialLeverBlock)
    {
        this.gateDialLeverBlock = gateDialLeverBlock;
    }

    /**
     * Sets the gate teleport sign.
     * 
     * @param gateDialSign
     *            the new gate dial sign
     */
    public synchronized void setGateDialSign(final Sign gateDialSign)
    {
        this.gateDialSign = gateDialSign;
    }

    /**
     * Sets the gate teleport sign block.
     * 
     * @param gateDialSignBlock
     *            the new gate dial sign block
     */
    public synchronized void setGateDialSignBlock(final Block gateDialSignBlock)
    {
        this.gateDialSignBlock = gateDialSignBlock;
    }

    /**
     * Sets the gate sign index.
     * 
     * @param gateDialSignIndex
     *            the new gate dial sign index
     */
    public synchronized void setGateDialSignIndex(final int gateDialSignIndex)
    {
        this.gateDialSignIndex = gateDialSignIndex;
    }

    /**
     * Sets the gate sign target.
     * 
     * @param gateDialSignTarget
     *            the new gate dial sign target
     */
    protected void setGateDialSignTarget(final Stargate gateDialSignTarget)
    {
        this.gateDialSignTarget = gateDialSignTarget;
    }

    /**
     * Sets the gate facing.
     * 
     * @param gateFacing
     *            the new gate facing
     */
    public void setGateFacing(final BlockFace gateFacing)
    {
        this.gateFacing = gateFacing;
    }

    /**
     * Sets the gate id.
     * 
     * @param gateId
     *            the new gate id
     */
    void setGateId(final long gateId)
    {
        this.gateId = gateId;
    }

    /**
     * Sets the gate iris active.
     * 
     * @param gateIrisActive
     *            the new gate iris active
     */
    public void setGateIrisActive(final boolean gateIrisActive)
    {
        this.gateIrisActive = gateIrisActive;
    }

    /**
     * Sets the gate iris deactivation code.
     * 
     * @param gateIrisDeactivationCode
     *            the new gate iris deactivation code
     */
    public void setGateIrisDeactivationCode(final String gateIrisDeactivationCode)
    {
        this.gateIrisDeactivationCode = gateIrisDeactivationCode;
    }

    /**
     * Sets the gate iris default active.
     * 
     * @param gateIrisDefaultActive
     *            the new gate iris default active
     */
    public void setGateIrisDefaultActive(final boolean gateIrisDefaultActive)
    {
        this.gateIrisDefaultActive = gateIrisDefaultActive;
    }

    /**
     * Sets the gate iris activation block.
     * 
     * @param gateIrisLeverBlock
     *            the new gate iris lever block
     */
    public void setGateIrisLeverBlock(final Block gateIrisLeverBlock)
    {
        this.gateIrisLeverBlock = gateIrisLeverBlock;
    }

    /**
     * Sets the gate lighting current iteration.
     * 
     * @param gateLightingCurrentIteration
     *            the new gate lighting current iteration
     */
    void setGateLightingCurrentIteration(final int gateLightingCurrentIteration)
    {
        this.gateLightingCurrentIteration = gateLightingCurrentIteration;
    }

    /**
     * Sets the gate lit.
     * 
     * @param gateLightsActive
     *            the new gate lights active
     */
    public void setGateLightsActive(final boolean gateLightsActive)
    {
        this.gateLightsActive = gateLightsActive;
    }

    /**
     * Sets the gate minecart teleport location.
     * 
     * @param gateMinecartTeleportLocation
     *            the new gate minecart teleport location
     */
    public void setGateMinecartTeleportLocation(final Location gateMinecartTeleportLocation)
    {
        this.gateMinecartTeleportLocation = gateMinecartTeleportLocation;
    }

    /**
     * Sets the gate name.
     * 
     * @param gateName
     *            the new gate name
     */
    public void setGateName(final String gateName)
    {
        this.gateName = gateName;
    }

    /**
     * Sets the gate name block holder.
     * 
     * @param gateNameBlockHolder
     *            the new gate name block holder
     */
    public void setGateNameBlockHolder(final Block gateNameBlockHolder)
    {
        this.gateNameBlockHolder = gateNameBlockHolder;
    }

    /**
     * Sets the gate network.
     * 
     * @param gateNetwork
     *            the new gate network
     */
    public void setGateNetwork(final StargateNetwork gateNetwork)
    {
        this.gateNetwork = gateNetwork;
    }

    /**
     * Sets the gate owner identifier (UUID string for new gates, or legacy player name).
     * 
     * @param gateOwner
     *            the new gate owner identifier
     */
    public void setGateOwner(final String gateOwner)
    {
        this.gateOwner = gateOwner;
    }

    /**
     * Sets the display name of the gate owner (shown on sign and in commands).
     *
     * @param gateOwnerName the human-readable player name
     */
    public void setGateOwnerName(final String gateOwnerName)
    {
        this.gateOwnerName = gateOwnerName;
    }

    /**
     * Sets the gate teleport location.
     * 
     * @param gatePlayerTeleportLocation
     *            the new gate player teleport location
     */
    public void setGatePlayerTeleportLocation(final Location gatePlayerTeleportLocation)
    {
        this.gatePlayerTeleportLocation = gatePlayerTeleportLocation;
    }

    /**
     * Sets the gate recently active.
     * 
     * @param gateRecentlyActive
     *            the new gate recently active
     */
    void setGateRecentlyActive(final boolean gateRecentlyActive)
    {
        this.gateRecentlyActive = gateRecentlyActive;
    }

    /**
     * Sets the gate redstone activation block.
     * 
     * @param gateRedstoneDialActivationBlock
     *            the new gate redstone dial activation block
     */
    public void setGateRedstoneDialActivationBlock(final Block gateRedstoneDialActivationBlock)
    {
        this.gateRedstoneDialActivationBlock = gateRedstoneDialActivationBlock;
    }

    /**
     * Sets the gate redstone gate activated block.
     * 
     * @param gateRedstoneGateActivatedBlock
     *            the new gate redstone gate activated block
     */
    public void setGateRedstoneGateActivatedBlock(final Block gateRedstoneGateActivatedBlock)
    {
        this.gateRedstoneGateActivatedBlock = gateRedstoneGateActivatedBlock;
    }

    /**
     * Sets the gate redstone powered.
     * 
     * @param gateRedstonePowered
     *            the new gate redstone powered
     */
    public void setGateRedstonePowered(final boolean gateRedstonePowered)
    {
        this.gateRedstonePowered = gateRedstonePowered;
    }

    /**
     * Sets the gate redstone dial change block.
     * 
     * @param gateRedstoneSignActivationBlock
     *            the new gate redstone sign activation block
     */
    public void setGateRedstoneSignActivationBlock(final Block gateRedstoneSignActivationBlock)
    {
        this.gateRedstoneSignActivationBlock = gateRedstoneSignActivationBlock;
    }

    /**
     * Sets the gate shape.
     * 
     * @param gateShape
     *            the new gate shape
     */
    public void setGateShape(final StargateShape gateShape)
    {
        this.gateShape = gateShape;
    }

    /**
     * Sets the gate shutdown task id.
     * 
     * @param gateShutdownTaskId
     *            the new gate shutdown task id
     */
    void setGateShutdownTaskId(final int gateShutdownTaskId)
    {
        this.gateShutdownTaskId = gateShutdownTaskId;
    }

    /**
     * Sets the gate sign powered.
     * 
     * @param gateSignPowered
     *            the new gate sign powered
     */
    public void setGateSignPowered(final boolean gateSignPowered)
    {
        this.gateSignPowered = gateSignPowered;
    }

    /**
     * Sets the gate target.
     * 
     * @param gateTarget
     *            the new gate target
     */
    void setGateTarget(final Stargate gateTarget)
    {
        this.gateTarget = gateTarget;
    }

    /**
     * Sets the gate temp sign target.
     * 
     * @param gateTempSignTarget
     *            the new gate temp sign target
     */
    public void setGateTempSignTarget(final long gateTempSignTarget)
    {
        this.gateTempSignTarget = gateTempSignTarget;
    }

    /**
     * Sets the gate temp target id.
     * 
     * @param gateTempTargetId
     *            the new gate temp target id
     */
    public void setGateTempTargetId(final long gateTempTargetId)
    {
        this.gateTempTargetId = gateTempTargetId;
    }

    /**
     * Sets the gate world.
     * 
     * @param gateWorld
     *            the new gate world
     */
    public void setGateWorld(final World gateWorld)
    {
        this.gateWorld = gateWorld;
    }

    /**
     * Sets the iris deactivation code.
     * 
     * @param idc
     *            the idc
     */
    public void setIrisDeactivationCode(final String idc)
    {
        // If empty string make sure to make lever area air instead of lever.
        if ((idc != null) && !idc.equals(""))
        {
            setGateIrisDeactivationCode(idc);
            setupIrisLever(true);
        }
        else
        {
            setIrisState(false);
            setupIrisLever(false);
            setGateIrisDeactivationCode("");
        }
    }

    /**
     * This method sets the iris state and toggles the iris lever.
     * Smart enough to know if the gate is active and set the proper
     * material in its interior.
     * 
     * @param irisactive
     *            true for iris on, false for off.
     */
    void setIrisState(final boolean irisactive)
    {
        StargateLifecycle.setIrisState(this, irisactive);
    }

    /**
     * Sets the loaded version.
     * 
     * @param loadedVersion
     *            the new loaded version
     */
    public void setLoadedVersion(final byte loadedVersion)
    {
        this.loadedVersion = loadedVersion;
    }

    /**
     * Setup or remove gate name sign.
     * 
     * @param create
     *            true to create, false to destroy
     */
    public void setupGateSign(final boolean create)
    {
        StargateBlockSetup.setupGateSign(this, create);
    }

    /**
     * Setup or remove IRIS control lever.
     * 
     * @param create
     *            true for create, false for destroy.
     */
    public void setupIrisLever(final boolean create)
    {
        StargateBlockSetup.setupIrisLever(this, create);
    }

    /**
     * Sets the up redstone connections (create or delete).
     * 
     * @param create
     *            true to create redstone connections, false to delete.
     */
    public void setupRedstone(final boolean create)
    {
        StargateBlockSetup.setupRedstone(this, create);
    }

    /**
     * Sets the up redstone dial wire.
     * 
     * @param create
     *            the new redstone dial wire
     */
    private void setupRedstoneDialWire(final boolean create)
    {
        StargateBlockSetup.setupRedstoneDialWire(this, create);
    }

    /**
     * Sets the up redstone gate activated Lever.
     * 
     * @param create
     *            the new redstone gate activated lever
     */
    private void setupRedstoneGateActivatedLever(final boolean create)
    {
        StargateBlockSetup.setupRedstoneGateActivatedLever(this, create);
    }

    /**
     * Sets the up redstone sign dial wire.
     * 
     * @param create
     *            the new redstone sign dial wire
     */
    private void setupRedstoneSignDialWire(final boolean create)
    {
        StargateBlockSetup.setupRedstoneSignDialWire(this, create);
    }

    /**
     * Shutdown stargate.
     * 
     * @param timer
     *            true if we want to spawn after shutdown timer.
     */
    public void shutdownStargate(final boolean timer)
    {
        StargateLifecycle.shutdownStargate(this, timer);
    }

    /**
     * Start activation timer.
     * 
     * @param p
     *            the p
     */
    public void startActivationTimer(final Player p)
    {
        StargateLifecycle.startActivationTimer(this, p);
    }

    /**
     * Stop activation timer.
     * 
     */
    public void stopActivationTimer()
    {
        StargateLifecycle.stopActivationTimer(this);
    }

    /**
     * After shutdown stargate.
     */
    public void stopAfterShutdownTimer()
    {
        StargateLifecycle.stopAfterShutdownTimer(this);
    }

    /**
     * Teleport sign clicked.
     */
    public void teleportSignClicked()
    {
        StargateDialManager.teleportSignClicked(this, true);
    }

    /**
     * Teleport sign clicked with explicit navigation direction.
     *
     * @param forward {@code true} to advance to the next gate (right-click);
     *                {@code false} to go to the previous gate (left-click)
     */
    public void teleportSignClicked(final boolean forward)
    {
        StargateDialManager.teleportSignClicked(this, forward);
    }

    /**
     * Timeout stargate.
     * 
     * @param p
     *            the p
     */
    public void timeoutStargate(final Player p)
    {
        StargateLifecycle.timeoutStargate(this, p);
    }

    /**
     * Set the dial button and lever block state based on gate activation status.
     * 
     * @param regenerate
     *            true, to replace missing activation lever.
     */
    public void toggleDialLeverState(final boolean regenerate)
    {
        StargateBlockSetup.toggleDialLeverState(this, regenerate);
    }

    /**
     * Toggle the iris state.
     * 
     * @param setDefault
     *            true to set the toggled state as the default state.
     */
    public void toggleIrisActive(final boolean setDefault)
    {
        StargateLifecycle.toggleIrisActive(this, setDefault);
    }

    /**
     * Toggle redstone gate activated power.
     */
    void toggleRedstoneGateActivatedPower()
    {
        StargateBlockSetup.toggleRedstoneGateActivatedPower(this);
    }

    /**
     * Try click teleport sign. This is the same as {@link Stargate#tryClickTeleportSign(Block, Player)} with Player set
     * to null.
     * 
     * @param clicked
     *            the clicked
     * @return true, if successful
     */
    public boolean tryClickTeleportSign(final Block clicked)
    {
        return StargateDialManager.tryClickTeleportSign(this, clicked);
    }

    /**
     * Try click teleport sign.
     * 
     * @param clicked
     *            the clicked
     * @param player
     *            the player
     * @return true, if successful
     */
    public boolean tryClickTeleportSign(final Block clicked, final Player player)
    {
        return StargateDialManager.tryClickTeleportSign(this, clicked, player);
    }

    /**
     * Try click teleport sign with explicit navigation direction.
     *
     * @param clicked the block that was clicked
     * @param player  the player who clicked
     * @param forward {@code true} to advance to the next gate (right-click);
     *                {@code false} to go to the previous gate (left-click)
     * @return true, if successful
     */
    public boolean tryClickTeleportSign(final Block clicked, final Player player, final boolean forward)
    {
        return StargateDialManager.tryClickTeleportSign(this, clicked, player, forward);
    }
}
