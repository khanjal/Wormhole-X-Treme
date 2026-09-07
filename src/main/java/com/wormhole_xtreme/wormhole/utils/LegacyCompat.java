package com.wormhole_xtreme.wormhole.utils;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

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

    // CRIMSON_SIGN and WARPED_SIGN are standing signs rather than wall signs, but the legacy
    // mapping has always read them off the sign table, so they stay on it.
    private static final Set<Material> SIGN_FACINGS = Collections.unmodifiableSet(
        EnumSet.of(Material.OAK_WALL_SIGN, Material.SPRUCE_WALL_SIGN, Material.BIRCH_WALL_SIGN,
            Material.ACACIA_WALL_SIGN, Material.JUNGLE_WALL_SIGN, Material.DARK_OAK_WALL_SIGN,
            Material.CRIMSON_SIGN, Material.WARPED_SIGN));

    public static Material materialFromId(final int id) {
        switch (id) {
            case 0:
                return Material.AIR;
            case 8, 9:
                return Material.WATER;
            case 10, 11:
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
        } catch (final Exception | LinkageError t) {
            try { b.setType(m); } catch (final Exception | LinkageError t2) { /* ignore */ }
        }
    }

    public static void setData(final Block b, final byte data) {
        try {
            final Material m = b.getType();
            final BlockData bd = b.getBlockData();
            if (bd instanceof Directional d) {
                applyFacing(b, d, data, SIGN_FACINGS.contains(m));
            }
            if (bd instanceof Powerable p) {
                p.setPowered((data & 0x8) == 0x8);
                try { b.setBlockData(p); } catch (final Exception | LinkageError ignore) { /* best effort */ }
            }
            applyRedstonePower(b, bd, data);
        } catch (final Exception | LinkageError t) {
            // ignore mapping errors
        }
    }

    /** Turns a block to face wherever its legacy byte said, leaving it alone if the byte says nothing. */
    private static void applyFacing(final Block b, final Directional d, final byte data, final boolean sign) {
        final BlockFace face = sign ? signFacing(data) : genericFacing(data, d.getFacing());
        try { d.setFacing(face); b.setBlockData(d); } catch (final Exception | LinkageError ignore) { /* best effort */ }
    }

    private static BlockFace signFacing(final byte data) {
        switch (data) {
            case 2: return BlockFace.EAST;
            case 3: return BlockFace.WEST;
            case 4: return BlockFace.NORTH;
            default: return BlockFace.SOUTH;
        }
    }

    /**
     * Anything that is not a sign, which numbered the same four walls differently.
     *
     * <p>Only the low three bits are the facing. The fourth is the powered bit that
     * {@link #genericData} writes on top of it, so a powered lever facing east arrives here
     * as 12; reading the byte whole matched nothing and left the block facing wherever the
     * world had already put it.
     */
    private static BlockFace genericFacing(final byte data, final BlockFace current) {
        switch (data & 0x7) {
            case 1: return BlockFace.SOUTH;
            case 2: return BlockFace.NORTH;
            case 3: return BlockFace.WEST;
            case 4: return BlockFace.EAST;
            default: return current;
        }
    }

    public static byte getData(final Block b) {
        try {
            final Material m = b.getType();
            final BlockData bd = b.getBlockData();
            if (bd instanceof Directional d) {
                return SIGN_FACINGS.contains(m) ? signData(d.getFacing()) : genericData(bd, d.getFacing());
            }
            return redstonePower(bd);
        } catch (final Exception | LinkageError t) {
            // ignore
        }
        return 0;
    }

    private static byte signData(final BlockFace face) {
        switch (face) {
            case EAST: return 2;
            case WEST: return 3;
            case NORTH: return 4;
            default: return 5;
        }
    }

    /** The generic table, with the powered bit on top of it. */
    private static byte genericData(final BlockData bd, final BlockFace face) {
        byte base = 0;
        if (face == BlockFace.SOUTH) base = 1;
        else if (face == BlockFace.NORTH) base = 2;
        else if (face == BlockFace.WEST) base = 3;
        else if (face == BlockFace.EAST) base = 4;
        if ((bd instanceof Powerable p) && p.isPowered()) {
            base |= 0x8;
        }
        return base;
    }

    /**
     * Reads redstone wire's power level, which is its whole byte.
     *
     * <p>Reflection, because the accessor is not on any interface this plugin can compile
     * against across every server version it supports.
     */
    private static byte redstonePower(final BlockData bd) {
        if (!isRedstone(bd)) {
            return 0;
        }
        try {
            final Object val = bd.getClass().getMethod("getPower").invoke(bd);
            if (val instanceof Integer i) return i.byteValue();
        } catch (final Exception | LinkageError ignore) { /* best effort */ }
        return 0;
    }

    /** The other half of {@link #redstonePower}, and reflective for the same reason. */
    private static void applyRedstonePower(final Block b, final BlockData bd, final byte data) {
        if (!isRedstone(bd)) {
            return;
        }
        try {
            bd.getClass().getMethod("setPower", int.class).invoke(bd, data & 0xF);
            b.setBlockData(bd);
        } catch (final Exception | LinkageError ignore) { /* best effort */ }
    }

    private static boolean isRedstone(final BlockData bd) {
        return bd.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("redstone");
    }
}
