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

    /** Returns true for any button material. Delegates to LegacyCompat for legacy mappings. */
    public static boolean isButton(final Material m) {
        if (m == null) return false;
        switch (m) {
            case OAK_BUTTON:
            case SPRUCE_BUTTON:
            case BIRCH_BUTTON:
            case JUNGLE_BUTTON:
            case ACACIA_BUTTON:
            case DARK_OAK_BUTTON:
            case MANGROVE_BUTTON:
            case CHERRY_BUTTON:
            case BAMBOO_BUTTON:
            case CRIMSON_BUTTON:
            case WARPED_BUTTON:
            case STONE_BUTTON:
            case POLISHED_BLACKSTONE_BUTTON:
                return true;
            default:
                return false;
        }
    }

    /** Returns true for any wall-sign material. Delegates to LegacyCompat for legacy mappings. */
    public static boolean isWallSign(final Material m) {
        if (m == null) return false;
        switch (m) {
            case OAK_WALL_SIGN:
            case SPRUCE_WALL_SIGN:
            case BIRCH_WALL_SIGN:
            case JUNGLE_WALL_SIGN:
            case ACACIA_WALL_SIGN:
            case DARK_OAK_WALL_SIGN:
            case CRIMSON_WALL_SIGN:
            case WARPED_WALL_SIGN:
            case MANGROVE_WALL_SIGN:
            case CHERRY_WALL_SIGN:
            case BAMBOO_WALL_SIGN:
                return true;
            default:
                return false;
        }
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
