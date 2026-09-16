package com.wormhole_xtreme.wormhole.model.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Vector;

import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Palette;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.logic.GateGrid;

/**
 * One shape shown full size to the player who asked for it.
 *
 * <p>Holds its blueprint as well as its displays, so a display the server dropped with its chunk
 * can be put back when the chunk returns.
 */
final class GatePreview
{
    private final World world;
    private final GateGrid grid;
    private final Palette palette;
    private final List<Cell> cells;
    /** In step with {@link #cells}; null where a display has not been, or could not be, spawned. */
    private final List<BlockDisplay> displays;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private long expiresAt;

    GatePreview(final World world, final GateGrid grid, final Palette palette, final List<Cell> cells)
    {
        this.world = world;
        this.grid = grid;
        this.palette = palette;
        this.cells = List.copyOf(cells);
        this.displays = new ArrayList<>(Collections.nCopies(cells.size(), (BlockDisplay) null));
        minX = cells.stream().mapToInt(Cell::x).min().orElse(0);
        minY = cells.stream().mapToInt(Cell::y).min().orElse(0);
        minZ = cells.stream().mapToInt(Cell::z).min().orElse(0);
        maxX = cells.stream().mapToInt(Cell::x).max().orElse(0);
        maxY = cells.stream().mapToInt(Cell::y).max().orElse(0);
        maxZ = cells.stream().mapToInt(Cell::z).max().orElse(0);
    }

    World world()
    {
        return world;
    }

    GateGrid grid()
    {
        return grid;
    }

    Palette palette()
    {
        return palette;
    }

    List<Cell> cells()
    {
        return cells;
    }

    List<BlockDisplay> displays()
    {
        return displays;
    }

    long expiresAt()
    {
        return expiresAt;
    }

    void expiresAt(final long when)
    {
        expiresAt = when;
    }

    /**
     * Whether this preview's button is at a block, where a gate built to it is pressed.
     *
     * @return true if the button cell is there
     */
    boolean hasButtonAt(final World at, final int x, final int y, final int z)
    {
        return world.equals(at) && cells.stream()
            .anyMatch(c -> (c.part() == Part.BUTTON) && (c.x() == x) && (c.y() == y) && (c.z() == z));
    }

    /**
     * How far along a line of sight this preview's bounding box starts, the way a player looking
     * at it would pick it out.
     *
     * @param eye
     *            where the line starts
     * @param direction
     *            which way it goes, of length one
     * @param reach
     *            how far to look
     * @return the distance to the box, 0 from inside it, or -1 if the line misses it within reach
     */
    double distanceAlong(final Vector eye, final Vector direction, final double reach)
    {
        double near = 0.0;
        double far = reach;
        final double[] from = { eye.getX(), eye.getY(), eye.getZ() };
        final double[] step = { direction.getX(), direction.getY(), direction.getZ() };
        final double[] low = { minX, minY, minZ };
        final double[] high = { maxX + 1.0, maxY + 1.0, maxZ + 1.0 };
        for (int axis = 0; axis < 3; axis++)
        {
            if (Math.abs(step[axis]) < 1.0e-9)
            {
                if ((from[axis] < low[axis]) || (from[axis] > high[axis]))
                {
                    return -1.0;
                }
                continue;
            }
            final double a = (low[axis] - from[axis]) / step[axis];
            final double b = (high[axis] - from[axis]) / step[axis];
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
            if (near > far)
            {
                return -1.0;
            }
        }
        return near;
    }

    /** Removes every display this preview spawned. */
    void remove()
    {
        for (int i = 0; i < displays.size(); i++)
        {
            final BlockDisplay display = displays.get(i);
            if (display != null)
            {
                display.remove();
                displays.set(i, null);
            }
        }
    }
}
