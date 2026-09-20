package com.wormhole_xtreme.wormhole.model.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import com.wormhole_xtreme.wormhole.model.Stargate;
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
    private final List<Cell> woosh;
    /** Which cells a fake block has been sent at, to everyone watching, so it can be taken back. */
    private final Set<Long> sent = new HashSet<>();
    /** Who the owner has shared it with, by id, with the name they had then. */
    private final Map<UUID, String> sharedWith = new LinkedHashMap<>();
    /** Who besides the owner is being shown it now. */
    private final Set<UUID> shownTo = new LinkedHashSet<>();
    /** In step with {@link #cells}; null where a display has not been, or could not be, spawned. */
    private final List<BlockDisplay> displays;
    /** In step with {@link #opening}; null wherever the opening is empty. */
    private final List<BlockDisplay> openingDisplays;
    /** In step with {@link #opening}; the guide's marks where something is in the way. */
    private final List<BlockDisplay> blockedDisplays;
    private final int lastWave;
    /** The shape layers something is built in, front to back as the shape numbers them. */
    private final List<Integer> builtLayers;
    private final int lastWoosh;
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
    private final com.wormhole_xtreme.wormhole.logic.DialSpin spin;
    private int spinTick;
    private Set<Cell> spinCells = Set.of();
    private int wooshStage;
    private boolean open;
    private boolean irisClosed;

    /**
     * Which of the opening's cells are showing iris right now.
     *
     * <p>Not the same question as {@link #irisClosed}, which is where the iris is *going*. A
     * sweep moves this set a ring at a time, so between the two there is a moment where the
     * iris is logically shut and only half drawn -- which on a preview is exactly the point,
     * since there is nothing here for anybody to walk through.
     *
     * <p>With no sweep running the two agree: every index when closed, none when open.
     */
    private final java.util.Set<Integer> irisShown = new java.util.HashSet<>();
    private boolean dhdHidden;
    private boolean plainChevrons;
    private boolean guide;
    private boolean sharedWithAll;
    /** How many built layers are shown, from the first; 0 shows them all. */
    private int layersShown;
    private boolean finished;
    private boolean recheckQueued;
    private long expiresAt;
    private long lastPressed;

    GatePreview(final World world, final Stargate3DShape shape, final GateGrid grid, final Palette palette,
        final List<Cell> cells, final List<Cell> opening, final List<Cell> woosh)
    {
        this.world = world;
        this.shape = shape;
        this.grid = grid;
        this.palette = palette;
        this.cells = List.copyOf(cells);
        this.opening = List.copyOf(opening);
        this.woosh = List.copyOf(woosh);
        this.displays = new ArrayList<>(Collections.nCopies(cells.size(), (BlockDisplay) null));
        this.openingDisplays = new ArrayList<>(Collections.nCopies(opening.size(), (BlockDisplay) null));
        this.blockedDisplays = new ArrayList<>(Collections.nCopies(opening.size(), (BlockDisplay) null));
        // A preview dials nowhere, so its other-world chevron stays dark.
        lastWave = Math.min(cells.stream().mapToInt(Cell::wave).max().orElse(0), Stargate.LOCAL_CHEVRONS);
        builtLayers = cells.stream().map(Cell::layer).distinct().sorted().toList();
        lastWoosh = woosh.stream().mapToInt(Cell::wave).max().orElse(0);
        spin = com.wormhole_xtreme.wormhole.logic.DialSpin.of(this.cells, grid);
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

    List<Cell> woosh()
    {
        return woosh;
    }

    /** @return the last step of the kawoosh, 0 for a shape without one */
    int lastWoosh()
    {
        return lastWoosh;
    }

    /** @return the ring's turn while dialling, or null for a gate with no top chevron */
    com.wormhole_xtreme.wormhole.logic.DialSpin spin()
    {
        return spin;
    }

    int spinTick()
    {
        return spinTick;
    }

    void spinTick(final int tick)
    {
        spinTick = tick;
    }

    /** @return the cells the ring's light is on now */
    Set<Cell> spinCells()
    {
        return spinCells;
    }

    void spinCells(final Set<Cell> cells)
    {
        spinCells = Set.copyOf(cells);
    }

    int wooshStage()
    {
        return wooshStage;
    }

    void wooshStage(final int stage)
    {
        wooshStage = stage;
    }

    /** @return the positions a fake block has been sent to, packed as {@link #key} */
    Set<Long> sent()
    {
        return sent;
    }

    /** @return one number for a block position */
    static long key(final Cell cell)
    {
        return ((cell.x() & 0x3FFFFFFL) << 38) | ((cell.z() & 0x3FFFFFFL) << 12) | (cell.y() & 0xFFFL);
    }

    List<BlockDisplay> displays()
    {
        return displays;
    }

    List<BlockDisplay> openingDisplays()
    {
        return openingDisplays;
    }

    List<BlockDisplay> blockedDisplays()
    {
        return blockedDisplays;
    }

    Map<UUID, String> sharedWith()
    {
        return sharedWith;
    }

    Set<UUID> shownTo()
    {
        return shownTo;
    }

    boolean sharedWithAll()
    {
        return sharedWithAll;
    }

    void sharedWithAll(final boolean everyone)
    {
        sharedWithAll = everyone;
    }

    /** @return every block display standing now: frame, iris and the guide's marks */
    List<BlockDisplay> standingDisplays()
    {
        final List<BlockDisplay> all = new ArrayList<>();
        for (final List<BlockDisplay> shown : List.of(displays, openingDisplays, blockedDisplays))
        {
            shown.stream().filter(java.util.Objects::nonNull).forEach(all::add);
        }
        return all;
    }

    boolean guide()
    {
        return guide;
    }

    void guide(final boolean on)
    {
        guide = on;
    }

    /** @return whether the owner has been told the build is finished since it last was not */
    boolean finished()
    {
        return finished;
    }

    void finished(final boolean told)
    {
        finished = told;
    }

    boolean recheckQueued()
    {
        return recheckQueued;
    }

    void recheckQueued(final boolean queued)
    {
        recheckQueued = queued;
    }

    /** @return whether a block is inside this preview's bounds */
    boolean contains(final World at, final int x, final int y, final int z)
    {
        return world.equals(at) && (x >= minX) && (x <= maxX) && (y >= minY) && (y <= maxY) && (z >= minZ)
            && (z <= maxZ);
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
        return !(cell.dhd() && dhdHidden)
            && ((layersShown == 0) || (cell.layer() <= builtLayers.get(layersShown - 1)));
    }

    /** @return how many layers have something built in them */
    int layerCount()
    {
        return builtLayers.size();
    }

    int layersShown()
    {
        return layersShown;
    }

    void layersShown(final int layers)
    {
        layersShown = layers;
    }

    /**
     * Whether one opening cell is showing iris.
     *
     * <p>The opening's displays are the iris; the wormhole behind it is sent as blocks. Asked
     * per cell rather than for the whole opening so a sweep can draw part of one.
     *
     * @param cell
     *            the cell's index in {@link #opening()}
     * @return true if its display should stand
     */
    boolean irisShownAt(final int cell)
    {
        return irisShown.contains(cell);
    }

    /** @return the set of opening cells showing iris, for a sweep to move */
    java.util.Set<Integer> irisShown()
    {
        return irisShown;
    }

    /** Shows the iris over the whole opening, or none of it, with no sweep in between. */
    void irisShownEverywhere(final boolean everywhere)
    {
        irisShown.clear();
        if (everywhere)
        {
            for (int i = 0; i < opening().size(); i++)
            {
                irisShown.add(i);
            }
        }
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
        clear(blockedDisplays);
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
