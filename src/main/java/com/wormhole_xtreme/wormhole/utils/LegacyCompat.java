package com.wormhole_xtreme.wormhole.utils;

import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Small compatibility helpers for legacy numeric material IDs.
 */
public final class LegacyCompat {
    private LegacyCompat() {}

    public static Material materialFromId(final int id) {
        switch (id) {
            case 0:
                return Material.AIR;
            case 8:
            case 9:
                return Material.WATER;
            case 10:
            case 11:
                return Material.LAVA;
            case 55:
                return Material.REDSTONE_WIRE;
            case 68:
                return Material.OAK_WALL_SIGN;
            case 69:
                return Material.LEVER;
            case 77:
                return Material.STONE_BUTTON;
            default:
                return Material.STONE;
        }
    }

    public static void setTypeId(final Block b, final int id) {
        b.setType(materialFromId(id));
    }

    public static void setTypeIdAndData(final Block b, final int id, final byte data, final boolean applyPhysics) {
        b.setType(materialFromId(id));
        // legacy data handling is ignored; BlockData conversion is complex
    }

    public static void setData(final Block b, final byte data) {
        try {
            // best-effort: do nothing (modern BlockData requires mapping)
        }
        catch (final Throwable t) {
            // ignore
        }
    }
}
