/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Small material helper facade to centralize "isX" predicates and
 *   provide a single place to handle modern-material checks and legacy
 *   fallbacks.
 */
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
