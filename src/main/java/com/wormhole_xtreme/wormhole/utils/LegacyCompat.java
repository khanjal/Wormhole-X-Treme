package com.wormhole_xtreme.wormhole.utils;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.BlockFace;

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
        final Material m = materialFromId(id);
        try {
            b.setType(m, applyPhysics);
            // apply data mapping where possible
            setData(b, data);
        } catch (final Throwable t) {
            try { b.setType(m); } catch (final Throwable t2) { /* ignore */ }
        }
    }

    public static void setData(final Block b, final byte data) {
        try {
            final Material m = b.getType();
            final BlockData bd = b.getBlockData();
            // Generic Directional + Powerable handling for levers/buttons/signs
            if (bd instanceof Directional) {
                final Directional d = (Directional) bd;
                BlockFace face = d.getFacing();
                if (m == Material.OAK_WALL_SIGN || m == Material.SPRUCE_WALL_SIGN || m == Material.BIRCH_WALL_SIGN || m == Material.ACACIA_WALL_SIGN || m == Material.JUNGLE_WALL_SIGN || m == Material.DARK_OAK_WALL_SIGN || m == Material.CRIMSON_SIGN || m == Material.WARPED_SIGN) {
                    switch (data) {
                        case 2: face = BlockFace.EAST; break;
                        case 3: face = BlockFace.WEST; break;
                        case 4: face = BlockFace.NORTH; break;
                        case 5: 
                        default: face = BlockFace.SOUTH; break;
                    }
                } else {
                    switch (data) {
                        case 1: face = BlockFace.SOUTH; break;
                        case 2: face = BlockFace.NORTH; break;
                        case 3: face = BlockFace.WEST; break;
                        case 4: face = BlockFace.EAST; break;
                        default: break;
                    }
                }
                try { d.setFacing(face); b.setBlockData(d); } catch (final Throwable ignore) {}
            }
            if (bd instanceof Powerable) {
                final Powerable p = (Powerable) bd;
                p.setPowered((data & 0x8) == 0x8);
                try { b.setBlockData(p); } catch (final Throwable ignore) {}
            }
            // Redstone wire: try reflection to set power if present
            try {
                final Class<?> cls = bd.getClass();
                if (cls.getSimpleName().toLowerCase().contains("redstone")) {
                    final java.lang.reflect.Method setPower = cls.getMethod("setPower", int.class);
                    int power = data & 0xF;
                    if (power < 0) power = 0;
                    setPower.invoke(bd, power);
                    b.setBlockData(bd);
                    return;
                }
            } catch (final Throwable ignore) {}
        } catch (final Throwable t) {
            // ignore mapping errors
        }
    }

    public static byte getData(final Block b) {
        try {
            final Material m = b.getType();
            final BlockData bd = b.getBlockData();
            if (bd instanceof Directional) {
                final Directional d = (Directional) bd;
                final BlockFace face = d.getFacing();
                byte base = 0;
                if (face == BlockFace.SOUTH) base = 1;
                else if (face == BlockFace.NORTH) base = 2;
                else if (face == BlockFace.WEST) base = 3;
                else if (face == BlockFace.EAST) base = 4;
                if (m == Material.OAK_WALL_SIGN || m == Material.SPRUCE_WALL_SIGN || m == Material.BIRCH_WALL_SIGN || m == Material.ACACIA_WALL_SIGN || m == Material.JUNGLE_WALL_SIGN || m == Material.DARK_OAK_WALL_SIGN || m == Material.CRIMSON_SIGN || m == Material.WARPED_SIGN) {
                    switch (face) {
                        case EAST: return 2;
                        case WEST: return 3;
                        case NORTH: return 4;
                        case SOUTH: default: return 5;
                    }
                }
                // include powered bit if applicable
                if (bd instanceof Powerable) {
                    final Powerable p = (Powerable) bd;
                    if (p.isPowered()) base |= 0x8;
                }
                return base;
            }
            // Redstone wire: reflection-based getter
            try {
                final Class<?> cls = bd.getClass();
                if (cls.getSimpleName().toLowerCase().contains("redstone")) {
                    final java.lang.reflect.Method getPower = cls.getMethod("getPower");
                    final Object val = getPower.invoke(bd);
                    if (val instanceof Integer) return ((Integer) val).byteValue();
                }
            } catch (final Throwable ignore) {}
        } catch (final Throwable t) {
            // ignore
        }
        return 0;
    }
}
