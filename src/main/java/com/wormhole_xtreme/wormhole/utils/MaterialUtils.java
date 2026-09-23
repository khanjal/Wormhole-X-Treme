package com.wormhole_xtreme.wormhole.utils;

import org.bukkit.Material;

public final class MaterialUtils {
    private MaterialUtils() {}

    /**
     * Block data for showing a material to a client, switched on if it can be.
     *
     * <p>A redstone lamp and a copper bulb are lights that are <em>off</em> by default. Drawing
     * one straight from {@code createBlockData()} shows a dark lamp, which is a strange thing
     * for a ring pad or a chevron to light up as. Anything the game calls
     * {@link org.bukkit.block.data.Lightable} is switched on here, which covers those two and
     * whatever else arrives with the same idea.
     *
     * <p>Everything else passes through untouched -- a slab, a portal material and a barrier
     * have no state to set, so this is safe to use at every drawing site rather than only the
     * ones known to need it.
     *
     * @param material
     *            the material being drawn
     * @return its block data, lit where that means something
     */
    public static org.bukkit.block.data.BlockData drawnAs(final Material material) {
        return drawnAs(material.createBlockData());
    }

    /**
     * The same, for block data already made.
     *
     * @param data
     *            the block data, switched on in place where it can be
     * @return the same block data
     */
    public static org.bukkit.block.data.BlockData drawnAs(final org.bukkit.block.data.BlockData data) {
        if (data instanceof org.bukkit.block.data.Lightable lightable) {
            lightable.setLit(true);
        }
        return data;
    }

    /**
     * Block data for a material drawn across a gate's opening, turned to lie in it.
     *
     * <p>{@link #drawnAs(Material)} plus {@link #laidAcross}, which is what every cell of an
     * opening wants: the wormhole, the iris, the horizon behind one and the woosh.
     *
     * @param material
     *            the material being drawn
     * @param gateFacing
     *            the way the gate faces, may be null
     * @return its block data, lit and turned where either means something
     */
    public static org.bukkit.block.data.BlockData drawnAcross(final Material material,
        final org.bukkit.block.BlockFace gateFacing) {
        return laidAcross(drawnAs(material), gateFacing);
    }

    /**
     * Turns block data to lie in the plane of a gate's opening, where it has a plane to turn.
     *
     * <p>{@link org.bukkit.block.data.Orientable} blocks carry an axis, and
     * {@code createBlockData()} hands back whichever one the game defaults to. That is right for
     * half the gates in a world and ninety degrees out for the other half: a {@code NETHER_PORTAL}
     * wormhole drawn from the default shows a thin sliver edge-on instead of a sheet filling the
     * opening. Which half looks wrong depends only on which way the gate was built, so it stays
     * invisible until somebody builds the second one.
     *
     * <p>The axis is the gate's facing turned sideways, because the opening is the plane the
     * block has to fill: a gate facing north or south opens across X, one facing east or west
     * across Z. A horizontal gate gets nothing -- its opening is flat, and an axis names a
     * horizontal direction, so there is no value that would lie in it. Neither does a block whose
     * own {@code getAxes()} does not offer the one wanted: a nether portal has X and Z and no Y,
     * and a block with some other set is better left at its default than forced.
     *
     * <p>Written for {@code Orientable} rather than for the portal material, because a log, a
     * pillar or a bone block named as an iris has exactly the same problem. Only the opening,
     * though: a frame block stands upright on purpose, and laying the chevrons over sideways to
     * match the wormhole would be a fix for a bug nobody has.
     *
     * @param data
     *            the block data, turned in place where it can be; may be null
     * @param gateFacing
     *            the way the gate faces, may be null
     * @return the same block data
     */
    public static org.bukkit.block.data.BlockData laidAcross(final org.bukkit.block.data.BlockData data,
        final org.bukkit.block.BlockFace gateFacing) {
        final org.bukkit.Axis axis = openingAxis(gateFacing);
        if ((axis != null) && (data instanceof org.bukkit.block.data.Orientable orientable)
            && orientable.getAxes().contains(axis)) {
            orientable.setAxis(axis);
        }
        return data;
    }

    /**
     * The axis an opening runs along, for a gate facing this way.
     *
     * @param gateFacing
     *            the way the gate faces, may be null
     * @return the axis, or null for a horizontal gate and for anything not a cardinal direction
     */
    private static org.bukkit.Axis openingAxis(final org.bukkit.block.BlockFace gateFacing) {
        if (gateFacing == null) {
            return null;
        }
        switch (gateFacing) {
            case NORTH, SOUTH:
                return org.bukkit.Axis.X;
            case EAST, WEST:
                return org.bukkit.Axis.Z;
            default:
                return null;
        }
    }

    /**
     * The same block switched on, where that means anything.
     *
     * <p>A redstone lamp built into a gate frame is a chevron waiting to light: it stands
     * there dark, and switching it on is a better thing to show than replacing it with a
     * different block entirely. That only works for a material with an off and an on --
     * {@link org.bukkit.block.data.Lightable} -- so this answers null for everything else,
     * which is the caller's cue to fall back to the gate's light material.
     *
     * <p>Deliberately not folded into {@link #drawnAs}: that one is called on the woosh's hot
     * path and always has an answer, whereas this one asks a question that can come back no.
     *
     * @param material
     *            the material standing there, may be null
     * @return its lit block data, or null if this material has no lit state
     */
    public static org.bukkit.block.data.BlockData litFormOf(final Material material) {
        return (material == null) ? null : litFormOf(material.createBlockData());
    }

    /**
     * The same, for block data already made.
     *
     * @param data
     *            the block data, switched on in place where it can be; may be null
     * @return it switched on, or null if it has no lit state
     */
    public static org.bukkit.block.data.BlockData litFormOf(final org.bukkit.block.data.BlockData data) {
        if (data instanceof org.bukkit.block.data.Lightable lightable) {
            lightable.setLit(true);
            return data;
        }
        return null;
    }

    /**
     * What one chevron position shows while it is lit: a chevron block with an on state switched on,
     * and the light material for everything else, so a gold chevron that cannot light still appears to.
     * A real gate and a build preview both light their chevrons by this.
     *
     * @param standing
     *            the material built at that position
     * @param chevronMaterial
     *            what an unlit chevron of this gate is built from, may be null
     * @param fixtureOn
     *            the chevron material switched on, or null if it has no lit state
     * @param lightData
     *            the gate's light material, used for everything else
     * @return what to draw there
     */
    public static org.bukkit.block.data.BlockData litChevron(final Material standing, final Material chevronMaterial,
        final org.bukkit.block.data.BlockData fixtureOn, final org.bukkit.block.data.BlockData lightData) {
        return ((fixtureOn != null) && (standing == chevronMaterial)) ? fixtureOn : lightData;
    }

    /**
     * Whether the registry can answer what is a block.
     *
     * <p>From 1.20.6 on, {@link Material#isBlock()} goes through the server's registry, which
     * is not there before the server has finished starting — and asking then throws rather
     * than returning false. Probed once, because the answer cannot change within a run and a
     * failed registry lookup is expensive to repeat for every material in the game.
     *
     * <p>Only a yes is remembered. A no means the registry was not ready when we asked, and
     * that changes: a single probe cached at class-init would have recorded "no registry" for
     * the life of the server. Asking again costs one call until the first time it works.
     */
    private static boolean blockCheckWorks = false;

    /** @return true if {@link Material#isBlock()} can be called without throwing */
    private static boolean probeBlockCheck() {
        try {
            Material.STONE.isBlock();
            return true;
        }
        // LinkageError as well as Exception: a registry that is not ready fails in class
        // initialisation, which arrives as an ExceptionInInitializerError.
        catch (final Exception | LinkageError ignored) {
            return false;
        }
    }

    /**
     * Whether a material is a block, as far as this server can say.
     *
     * <p>Without a registry to ask, everything is a block rather than nothing. Both callers
     * would rather be permissive than wrong: tab completion offering a few extra names is a
     * much smaller problem than an empty list, and refusing a perfectly good config value
     * because the server had not finished starting would be worse than accepting one that
     * turns out to be an item. On a running server — the only place either caller is reached
     * by a player — the registry is always there, so this is the strict check in practice.
     *
     * @param m
     *            the material to judge
     * @return true if it is a block, or if that cannot be determined
     */
    public static boolean isBlockOrUnknown(final Material m) {
        if (m == null) return false;
        if (!blockCheckWorks) {
            blockCheckWorks = probeBlockCheck();
        }
        return !blockCheckWorks || m.isBlock();
    }

    /**
     * Returns true for any button material — every wood type plus stone and polished
     * blackstone. These, and levers, are what a gate's DHD can be.
     *
     * <p>Matched by name rather than enumerated so a new wood type in a future Minecraft
     * release works without a code change, which is the convention the rest of this class
     * already follows. LEGACY_* constants are pre-1.13 compatibility entries and are not
     * real placeable blocks, so they are excluded.
     */
    public static boolean isButton(final Material m) {
        if (m == null) return false;
        final String name = m.name();
        return name.endsWith("_BUTTON") && !name.startsWith("LEGACY_");
    }

    /**
     * Returns true for any wall-sign material, matched by name so new wood types work
     * without a code change. LEGACY_* constants are excluded.
     */
    public static boolean isWallSign(final Material m) {
        if (m == null) return false;
        final String name = m.name();
        return name.endsWith("_WALL_SIGN") && !name.startsWith("LEGACY_");
    }

    /**
     * Whether a material is one of the three airs, without asking the registry.
     *
     * <p>{@code Material.isAir()} is the obvious way to write this and is what the rest of the
     * tree used. From Minecraft 1.20.6 it resolves through {@code org.bukkit.Registry}, which
     * needs a running server -- so on 1.20 and 1.20.1 it answers and from 1.20.6 on it throws
     * {@code NoClassDefFoundError} instead. On a live server it is fine either way; under test
     * it makes the calling code unloadable on seven of the ten versions this plugin supports.
     *
     * <p>Comparing the three constants is exactly as correct and asks nothing of the server.
     * {@code CAVE_AIR} and {@code VOID_AIR} are the reason not to write {@code == Material.AIR},
     * which is the trap the convention was written to avoid in the first place.
     *
     * @param m
     *            the material, may be null
     * @return true if it is AIR, CAVE_AIR or VOID_AIR
     */
    public static boolean isAirMaterial(final Material m) {
        return (m == Material.AIR) || (m == Material.CAVE_AIR) || (m == Material.VOID_AIR);
    }

    /**
     * Whether a block drawn in this material would hide water drawn right behind it.
     *
     * <p>Java Edition draws water and these in the same translucent pass, and a face between
     * two of them is not drawn at all. A wormhole is a sheet one block thick, so behind a
     * stained-glass iris its near face is culled and its far face points away: nothing is left
     * to see, and the gate shows the landscape rather than the wormhole. Plain glass is a
     * different pass and does not do this, which is the difference that gave it away.
     *
     * <p>Only the materials an iris can plausibly be. Anything opaque hides water by simply
     * being opaque, which needs no help from here.
     *
     * @param m
     *            the material, may be null
     * @return true if water drawn against it would not be seen
     */
    public static boolean cullsWaterBehindIt(final Material m) {
        if (m == null) {
            return false;
        }
        switch (m) {
            // ICE and FROSTED_ICE only: packed and blue ice are solid, and solid blocks show
            // water behind them perfectly well.
            case ICE, FROSTED_ICE, TINTED_GLASS, SLIME_BLOCK, HONEY_BLOCK, WATER, BUBBLE_COLUMN:
                return true;
            default:
                // Every stained glass block and pane, which is what the shipped Atlantis and
                // Universe palettes give an iris.
                return m.name().endsWith("STAINED_GLASS") || m.name().endsWith("STAINED_GLASS_PANE");
        }
    }

    /**
     * What to draw a horizon as when it has to sit behind an iris that would hide the liquid.
     *
     * <p>Only the face between a fluid and a translucent block is skipped, not everything
     * behind one -- the world beyond a stained-glass iris is drawn perfectly well, and so is
     * glass behind water. So the horizon is drawn in something that looks like the liquid and
     * is not one, and the fluid rule stops applying.
     *
     * <p>The stand-in has to be solid, not merely a different translucent: blue glass behind a
     * yellow iris was tried in a world and is not drawn either. The two ices are the closest
     * solid things to water, and nothing can cull either.
     *
     * <p>Two of them because one is a flat sheet of a single colour, which reads as ice rather
     * than as water. Laid in a checkerboard they break each other up and pass for a surface.
     *
     * <p>Water and no other liquid. Lava is not drawn translucent -- the game says so plainly,
     * and it shows through a stained-glass iris exactly as it is -- so standing in for it would
     * replace a perfectly good picture with an imitation of it. A portal material that <em>is</em>
     * translucent and has no stand-in here, {@code NETHER_PORTAL} being the one that ships, is
     * hidden behind such an iris the same way water was; that wants its own look chosen rather
     * than a guess made here.
     *
     * @param horizon
     *            the material the horizon would be, may be null
     * @param alternate
     *            true for the other square of the checkerboard
     * @return the stand-in, or the material itself where it needs no standing in for
     */
    public static Material shownBehindGlassAs(final Material horizon, final boolean alternate) {
        if (horizon != Material.WATER) {
            return horizon;
        }
        return alternate ? Material.PACKED_ICE : Material.BLUE_ICE;
    }

    /** Returns true if the material represents ice we care about. */
    public static boolean isIce(final Material m) {
        if (m == null) {
            return false;
        }
        switch (m) {
            case ICE, PACKED_ICE, BLUE_ICE, FROSTED_ICE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns true for blocks that can put a redstone signal into a neighbouring block:
     * the wire and repeaters that carry one, and the components that emit one.
     *
     * <p>Used to decide whether a redstone change next to a gate's activation block
     * counts as someone powering the gate. Buttons and pressure plates are matched by
     * family rather than enumerated, so a new wood type does not silently stop working.
     *
     * <p>Powered rails are included alongside detector and activator rails. A powered
     * rail does not actually emit a signal, but a player running a track into a gate
     * reasonably expects it to trigger, and accepting it costs nothing — the event only
     * fires when that rail's own power changes.
     */
    public static boolean isRedstoneSource(final Material m) {
        if (m == null) return false;
        if (isButton(m)) return true;
        if (m.name().endsWith("_PRESSURE_PLATE")) return true;
        switch (m) {
            case REDSTONE_WIRE, REPEATER, COMPARATOR, REDSTONE_BLOCK,
                 REDSTONE_TORCH, REDSTONE_WALL_TORCH, LEVER,
                 DETECTOR_RAIL, ACTIVATOR_RAIL, POWERED_RAIL,
                 TRIPWIRE_HOOK, OBSERVER, DAYLIGHT_DETECTOR, TARGET,
                 SCULK_SENSOR, CALIBRATED_SCULK_SENSOR, LIGHTNING_ROD,
                 TRAPPED_CHEST:
                return true;
            default:
                return false;
        }
    }
}
