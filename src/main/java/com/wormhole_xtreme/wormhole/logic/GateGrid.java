package com.wormhole_xtreme.wormhole.logic;

import java.util.List;

import org.bukkit.block.BlockFace;

import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeLayer;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * Where a gate's shape cells land in world coordinates, without a world.
 *
 * <p>The layer steps along the facing, the column along its perpendicular right, and the row is
 * height. Detection and the build preview both place cells through this, so they cannot disagree
 * about where a shape stands.
 *
 * @param ox
 *            the world x of layer 1, column 0
 * @param oy
 *            the world y of row 0
 * @param oz
 *            the world z of layer 1, column 0
 * @param facing
 *            the way the DHD's button faces, toward whoever presses it
 * @param right
 *            the facing's perpendicular right
 */
public record GateGrid(int ox, int oy, int oz, BlockFace facing, BlockFace right)
{
    /**
     * The grid of a shape whose activation cell is at a given block.
     *
     * @param hx
     *            the activation holder's x
     * @param hy
     *            the activation holder's y
     * @param hz
     *            the activation holder's z
     * @param facing
     *            the way the button on the holder faces
     * @return the grid, or null if the shape names no activation cell
     */
    public static GateGrid fromActivationHolder(final Stargate3DShape shape, final int hx, final int hy,
        final int hz, final BlockFace facing)
    {
        final int layerIdx = shape.getShapeActivationLayer();
        final List<StargateShapeLayer> layers = shape.getShapeLayers();
        if ((layerIdx < 1) || (layers == null) || (layers.size() <= layerIdx) || (layers.get(layerIdx) == null))
        {
            return null;
        }
        final int[] pos = layers.get(layerIdx).getLayerActivationPosition();
        if (pos.length < 3)
        {
            return null;
        }
        final BlockFace right = WorldUtils.getPerpendicularRightDirection(facing);
        return new GateGrid(
            hx - ((layerIdx - 1) * facing.getModX()) - (pos[2] * right.getModX()),
            hy - pos[1],
            hz - ((layerIdx - 1) * facing.getModZ()) - (pos[2] * right.getModZ()),
            facing, right);
    }

    /**
     * @param layerIdx
     *            the 1-based layer
     * @param col
     *            the column from the right, looking at the gate face
     * @return the world x of a cell
     */
    public int x(final int layerIdx, final int col)
    {
        return ox + ((layerIdx - 1) * facing.getModX()) + (col * right.getModX());
    }

    /**
     * @param row
     *            the row from the bottom
     * @return the world y of a cell
     */
    public int y(final int row)
    {
        return oy + row;
    }

    /**
     * @param layerIdx
     *            the 1-based layer
     * @param col
     *            the column from the right, looking at the gate face
     * @return the world z of a cell
     */
    public int z(final int layerIdx, final int col)
    {
        return oz + ((layerIdx - 1) * facing.getModZ()) + (col * right.getModZ());
    }
}
