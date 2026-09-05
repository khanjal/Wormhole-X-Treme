package com.wormhole_xtreme.wormhole.utils;

import org.bukkit.Material;

public final class MaterialUtils {
    private MaterialUtils() {}

    /**
     * Returns true for any button material — every wood type plus stone and polished
     * blackstone. These, and levers, are what a gate's DHD can be.
     *
     * <p>Matched by name rather than enumerated so a new wood type in a future Minecraft
     * release works without a code change, which is the convention the rest of this class
     * already follows. LEGACY_* constants are pre-1.13 compatibility entries and are not
     * real placeable blocks, so they are excluded.
     */
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
        final org.bukkit.block.data.BlockData data = material.createBlockData();
        if (data instanceof org.bukkit.block.data.Lightable) {
            ((org.bukkit.block.data.Lightable) data).setLit(true);
        }
        return data;
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
        if (material == null) {
            return null;
        }
        final org.bukkit.block.data.BlockData data = material.createBlockData();
        if (data instanceof org.bukkit.block.data.Lightable) {
            ((org.bukkit.block.data.Lightable) data).setLit(true);
            return data;
        }
        return null;
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
        // Throwable rather than Exception: a registry that is not ready fails in class
        // initialisation, which arrives as an Error.
        catch (final Throwable ignored) {
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

    /** Returns true if the material represents ice we care about. */
    public static boolean isIce(final Material m) {
        if (m == null) {
            return false;
        }
        switch (m) {
            case ICE:
            case PACKED_ICE:
            case BLUE_ICE:
            case FROSTED_ICE:
                return true;
            default:
                return false;
        }
    }

    /** Returns true for any rail material (rails, powered, detector, activator). */
    public static boolean isRail(final Material m) {
        if (m == null) return false;
        final String name = m.name();
        return "RAIL".equals(name) || name.endsWith("_RAIL");
    }

    /** Returns true for doors and trapdoors. Uses name-based checks to be forward-compatible. */
    public static boolean isDoor(final Material m) {
        if (m == null) return false;
        final String name = m.name();
        return name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR");
    }

    /** Returns true for fluid blocks (water or lava). */
    public static boolean isLiquid(final Material m) {
        if (m == null) return false;
        return m == Material.WATER || m == Material.LAVA || m.name().contains("WATER") || m.name().contains("LAVA");
    }

    /** Convenience checks. */
    public static boolean isWater(final Material m) { return m == Material.WATER; }
    public static boolean isLava(final Material m) { return m == Material.LAVA; }

    /** Returns true for any sign (wall or standing). */
    public static boolean isSign(final Material m) {
        if (m == null) return false;
        final String name = m.name();
        return name.endsWith("_SIGN") || name.equals("SIGN");
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
            case REDSTONE_WIRE:
            case REPEATER:
            case COMPARATOR:
            case REDSTONE_BLOCK:
            case REDSTONE_TORCH:
            case REDSTONE_WALL_TORCH:
            case LEVER:
            case DETECTOR_RAIL:
            case ACTIVATOR_RAIL:
            case POWERED_RAIL:
            case TRIPWIRE_HOOK:
            case OBSERVER:
            case DAYLIGHT_DETECTOR:
            case TARGET:
            case SCULK_SENSOR:
            case CALIBRATED_SCULK_SENSOR:
            case LIGHTNING_ROD:
            case TRAPPED_CHEST:
                return true;
            default:
                return false;
        }
    }
}
