package com.wormhole_xtreme.wormhole.model.window;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;

/**
 * Blocks that throw particles, drawn without them where a small window's view meets its edge (#309).
 *
 * <p>A particle belongs to the client and the server cannot take it back, so the flames and smoke
 * of a torch that has just gone out of sight hang past the opening for up to a second. Beside a
 * one or two block opening there is no wall to hide them. A block in the ring around the opening is
 * drawn unlit instead; the ones in the middle of the view are left as they are.
 */
final class EdgeEffects
{
    private EdgeEffects()
    {
    }

    /**
     * A block of a room as it is drawn through a window: quieted if the window is small and the
     * block is beside its opening, as it is otherwise.
     *
     * @param openCells
     *            how many blocks the window's opening has
     * @param shape
     *            the window
     * @param x
     *            the block's x
     * @param y
     *            the block's y
     * @param z
     *            the block's z
     * @param data
     *            the block as the room has it
     * @return the block to draw
     */
    static BlockData drawn(final int openCells, final WindowShape shape, final int x, final int y, final int z,
        final BlockData data)
    {
        return ((openCells > 0) && (openCells <= Windows.SMALL_OPEN) && onEdge(shape, x, y, z)) ? quieted(data) : data;
    }

    /**
     * Whether a block behind the opening lines up with the ring of blocks one outside its edge.
     *
     * @param shape
     *            the window
     * @param x
     *            the block's x
     * @param y
     *            the block's y
     * @param z
     *            the block's z
     * @return true if it is behind the wall, and one column or row beside the opening
     */
    static boolean onEdge(final WindowShape shape, final int x, final int y, final int z)
    {
        if (shape.layerOf(x, z) < 1)
        {
            return false;
        }
        final int across = shape.acrossOf(x, z);
        final int up = y - shape.base().y();
        final boolean beside = (across == -1) || (across == shape.width());
        final boolean above = (up == -1) || (up == shape.height());
        final boolean withinAcross = (across >= -1) && (across <= shape.width());
        final boolean withinUp = (up >= -1) && (up <= shape.height());
        return withinAcross && withinUp && (beside || above);
    }

    /**
     * The same block without the particles it throws.
     *
     * <p>A torch becomes an unlit redstone torch, a candle or campfire the unlit one, and fire air.
     * Chosen by name so a block newer than the build, a copper torch, is covered where the server
     * has it.
     *
     * @param data
     *            a block as the room has it
     * @return the block to draw, the same one if it throws no particles
     */
    static BlockData quieted(final BlockData data)
    {
        final Material material = data.getMaterial();
        if (material == null)
        {
            return data;
        }
        final String name = material.name();
        if (name.endsWith("FIRE") && !name.endsWith("CAMPFIRE"))
        {
            return Bukkit.createBlockData(Material.AIR);
        }
        if (name.endsWith("TORCH") && !name.startsWith("REDSTONE_"))
        {
            return unlitTorch(data, name.contains("WALL_"));
        }
        if (burns(name) && (data instanceof Lightable lightable)
            && lightable.isLit())
        {
            final Lightable out = (Lightable) data.clone();
            out.setLit(false);
            return out;
        }
        return data;
    }

    /** Whether a block of this name throws particles while it is lit: a candle, a campfire or a redstone torch. */
    private static boolean burns(final String name)
    {
        return name.endsWith("CANDLE") || name.endsWith("CANDLE_CAKE") || name.endsWith("CAMPFIRE")
            || name.equals("REDSTONE_TORCH") || name.equals("REDSTONE_WALL_TORCH");
    }

    /** A redstone torch, unlit, standing or on a wall facing the way the torch did. */
    private static BlockData unlitTorch(final BlockData torch, final boolean wall)
    {
        final BlockData out = Bukkit.createBlockData(wall ? Material.REDSTONE_WALL_TORCH : Material.REDSTONE_TORCH);
        if (wall && (out instanceof Directional facing) && (torch instanceof Directional was))
        {
            facing.setFacing(was.getFacing());
        }
        ((Lightable) out).setLit(false);
        return out;
    }
}
