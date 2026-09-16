package com.wormhole_xtreme.wormhole.model.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Palette;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role;
import com.wormhole_xtreme.wormhole.logic.GateGrid;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;

/**
 * One shape shown full size to the player who asked for it, and what it is doing.
 *
 * <p>Holds its blueprint as well as its displays, so a display the server dropped with its chunk
 * can be put back when the chunk returns, and so a change of material or state can be drawn
 * again from what each cell should now show.
 */
final class GatePreview
{
    private final World world;
    private final Stargate3DShape shape;
    private final GateGrid grid;
    private final List<Cell> cells;
    private final List<Cell> opening;
    /** In step with {@link #cells}; null where a display has not been, or could not be, spawned. */
    private final List<BlockDisplay> displays;
    /** In step with {@link #opening}; null wherever the opening is empty. */
    private final List<BlockDisplay> openingDisplays;
    private final int lastWave;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private Palette palette;
    private Interaction button;
    private BukkitTask dialling;
    private int litWaves;
    private boolean open;
    private boolean irisClosed;
    private boolean dhdHidden;
    private boolean plainChevrons;
    private long expiresAt;
    private long lastPressed;

    GatePreview(final World world, final Stargate3DShape shape, final GateGrid grid, final Palette palette,
        final List<Cell> cells, final List<Cell> opening)
    {
        this.world = world;
        this.shape = shape;
        this.grid = grid;
        this.palette = palette;
        this.cells = List.copyOf(cells);
        this.opening = List.copyOf(opening);
        this.displays = new ArrayList<>(Collections.nCopies(cells.size(), (BlockDisplay) null));
        this.openingDisplays = new ArrayList<>(Collections.nCopies(opening.size(), (BlockDisplay) null));
        lastWave = cells.stream().mapToInt(Cell::wave).max().orElse(0);
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

    Stargate3DShape shape()
    {
        return shape;
    }

    GateGrid grid()
    {
        return grid;
    }

    Palette palette()
    {
        return palette;
    }

    /** @return the palette as drawn: without chevron blocks while those are hidden */
    Palette drawnPalette()
    {
        return plainChevrons ? palette.with(Role.CHEVRON, null) : palette;
    }

    void palette(final Palette changed)
    {
        palette = changed;
    }

    List<Cell> cells()
    {
        return cells;
    }

    List<Cell> opening()
    {
        return opening;
    }

    List<BlockDisplay> displays()
    {
        return displays;
    }

    List<BlockDisplay> openingDisplays()
    {
        return openingDisplays;
    }

    /** @return every block this preview may show at once: its frame, and its opening filled */
    int size()
    {
        return cells.size() + opening.size();
    }

    /** @return the last chevron wave, 0 for a shape without chevrons */
    int lastWave()
    {
        return lastWave;
    }

    Interaction button()
    {
        return button;
    }

    void button(final Interaction entity)
    {
        button = entity;
    }

    BukkitTask dialling()
    {
        return dialling;
    }

    void dialling(final BukkitTask task)
    {
        dialling = task;
    }

    int litWaves()
    {
        return litWaves;
    }

    void litWaves(final int waves)
    {
        litWaves = waves;
    }

    boolean open()
    {
        return open;
    }

    void open(final boolean wormhole)
    {
        open = wormhole;
    }

    boolean irisClosed()
    {
        return irisClosed;
    }

    void irisClosed(final boolean closed)
    {
        irisClosed = closed;
    }

    boolean plainChevrons()
    {
        return plainChevrons;
    }

    void plainChevrons(final boolean plain)
    {
        plainChevrons = plain;
    }

    boolean dhdHidden()
    {
        return dhdHidden;
    }

    void dhdHidden(final boolean hidden)
    {
        dhdHidden = hidden;
    }

    long expiresAt()
    {
        return expiresAt;
    }

    void expiresAt(final long when)
    {
        expiresAt = when;
    }

    long lastPressed()
    {
        return lastPressed;
    }

    void lastPressed(final long when)
    {
        lastPressed = when;
    }

    /** @return whether a frame cell should be shown at all */
    boolean showing(final Cell cell)
    {
        return !(cell.dhd() && dhdHidden);
    }

    /** @return whether the opening shows anything */
    boolean openingFilled()
    {
        return open || irisClosed;
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

    /** @return the button cell, or null for a shape without one */
    Cell buttonCell()
    {
        return cells.stream().filter(c -> c.part() == Part.BUTTON).findFirst().orElse(null);
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

    /** Stops any dialling, and removes every entity this preview spawned. */
    void remove()
    {
        stopDialling();
        clear(displays);
        clear(openingDisplays);
        removeButton();
    }

    void stopDialling()
    {
        if (dialling != null)
        {
            dialling.cancel();
            dialling = null;
        }
    }

    void removeButton()
    {
        if (button != null)
        {
            button.remove();
            button = null;
        }
    }

    static void clear(final List<BlockDisplay> shown)
    {
        for (int i = 0; i < shown.size(); i++)
        {
            removeAt(shown, i);
        }
    }

    static void removeAt(final List<BlockDisplay> shown, final int i)
    {
        final BlockDisplay display = shown.get(i);
        if (display != null)
        {
            display.remove();
            shown.set(i, null);
        }
    }
}
