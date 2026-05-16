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
}
