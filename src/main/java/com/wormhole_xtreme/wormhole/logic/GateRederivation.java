package com.wormhole_xtreme.wormhole.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeLayer;

/**
 * Works a gate's furniture out from its shape again, for a gate whose shape file has changed
 * since it was built.
 *
 * <p>A gate records where its markers are once, at detection, and never re-reads the shape.
 * That is fine until the shape file gains a marker: #28 added {@code [RD]} and {@code [RA]} to
 * two shipped shapes, and every gate already standing kept the empty marker positions it was
 * detected with. No amount of wiring will fire such a gate, because
 * {@code StargateBlockSetup.setupRedstone} places blocks at positions that are already known
 * and derives none, and {@code /wormhole regenerate} guards that call with
 * {@code isGateRedstonePowered()} -- which is false on exactly the gates that need fixing.
 *
 * <p>So the missing step is re-derivation, and detection already knows how to do it. The gate
 * stores both of detection's inputs: the block a player clicked ({@code getGateDialLeverBlock})
 * and the direction it faces. Handing those back to {@link StargateHelper#checkStargate} with
 * the shape as it is <em>now</em> reproduces the detection that built the gate, against today's
 * file.
 *
 * <h2>What it copies, and what it deliberately does not</h2>
 *
 * <p>Only the furniture: the three redstone markers, the iris lever, the dial sign and the name
 * sign holder. Not the frame, the portal, the woosh waves or the arrival point. The light order
 * has its own rebuild, {@link #rebuildLightOrder}, which reads no blocks and so can run on
 * every gate.
 *
 * <p>That line is where it is because re-derivation runs unattended here, unlike
 * {@code /wormhole refresh}, which is always a player standing at one gate clicking its button.
 * Rewriting a gate's frame and portal lists is what {@code refresh} exists for and what it takes
 * a deliberate act to ask for; the arrival point has its own recomputation in
 * {@code RegenerateCommand} already. Markers are the part that a shape file changing actually
 * invalidates.
 *
 * <p>A gate recorded under a shape its frame does not match, as the legacy importer records every
 * gate as {@code Standard}, is detected against every shape, and takes the one it is. Its markers
 * and its light order can only come out right from its own shape.
 *
 * <p>And a marker is only ever added or moved, never cleared: a fresh detection that finds no
 * {@code :IA} leaves an iris lever a gate already has alone. A shape that has <em>lost</em> a
 * marker is a different question -- the block is still standing in the world, and taking it up
 * silently would be a surprise -- so this reports what it changed and leaves that decision to
 * whoever reads it.
 */
public final class GateRederivation
{
    /** How far re-derivation got. */
    public enum Result
    {
        /** The gate has no 3D shape to re-read -- either none was resolved, or it is 2D. */
        NO_SHAPE,

        /** No dial lever block or no facing, so detection has nothing to anchor on. */
        NO_ANCHOR,

        /** Detection ran and did not find the gate, so nothing was touched. */
        NOT_DETECTED,

        /** Detection ran and the gate's markers were brought up to date. */
        REDERIVED
    }

    /**
     * What re-derivation came to, and what it moved.
     *
     * @param result
     *            how far it got
     * @param changes
     *            one line per marker that was added or moved, empty when nothing changed
     */
    public record Outcome(Result result, List<String> changes)
    {
        /** @return true if anything actually moved */
        public boolean changedAnything()
        {
            return !changes.isEmpty();
        }
    }

    /** How rebuilding a gate's light order went. */
    public enum LightResult
    {
        /** The gate has no 3D shape to read the order from. */
        NO_SHAPE,

        /** No dial button, facing or world to place the shape by. */
        NO_ANCHOR,

        /** Lit or dialling; swapping the lights now would strand the ones already drawn. */
        BUSY,

        /** The shape lights a block that is not part of the gate's frame, so its frame has changed. */
        DOES_NOT_FIT,

        /** The gate already lights in the shape's order. */
        UNCHANGED,

        /** The gate now lights in the shape's order. */
        REBUILT
    }

    /** Static helpers only. */
    private GateRederivation()
    {
    }

    /** The percentage of a named shape's frame that must be standing for a gate to take that shape. */
    public static final int NAMED_SHAPE_MINIMUM_PERCENT = 90;

    /**
     * How well a gate's frame fits a shape an admin named for it.
     *
     * @param accepted
     *            whether the gate took the shape
     * @param present
     *            frame and chevron blocks of the shape found standing
     * @param expected
     *            frame and chevron blocks the shape has
     * @param gaps
     *            each block that is missing or wrong
     */
    public record ShapeFit(boolean accepted, int present, int expected, List<Gap> gaps, Layout layout)
    {
    }

    /**
     * A frame block of a named shape that is not there.
     *
     * @param x
     *            where it should be
     * @param y
     *            where it should be
     * @param z
     *            where it should be
     * @param found
     *            what stands there instead
     */
    public record Gap(int x, int y, int z, org.bukkit.Material found)
    {
    }

    /** How far the layout search goes from the DHD's own layout: along the facing, up and down, across. */
    private static final int SEARCH_ALONG = 6;
    private static final int SEARCH_UP = 3;
    private static final int SEARCH_ACROSS = 3;

    /**
     * Where a shape sits on a gate, as an offset from where its DHD would put it.
     *
     * @param grid
     *            the grid the shape's cells are laid on
     * @param along
     *            blocks moved along the facing the layout was made for (positive toward the button's side)
     * @param up
     *            blocks moved up
     * @param across
     *            blocks moved to the right, looking at the gate face
     * @param reversed
     *            whether the gate's recorded facing had to be turned round
     */
    public record Layout(GateGrid grid, int along, int up, int across, boolean reversed)
    {
        /** @return true if the shape sits somewhere other than where the DHD puts it */
        public boolean moved()
        {
            return reversed || (along != 0) || (up != 0) || (across != 0);
        }

        /** @return the offset in words, empty when it has not moved */
        public String describe()
        {
            final List<String> parts = new ArrayList<>();
            if (reversed)
            {
                parts.add("facing the other way");
            }
            if (along != 0)
            {
                parts.add(Math.abs(along) + " toward " + ((along > 0) ? "the DHD" : "the back"));
            }
            if (up != 0)
            {
                parts.add(Math.abs(up) + ((up > 0) ? " up" : " down"));
            }
            if (across != 0)
            {
                parts.add(Math.abs(across) + ((across > 0) ? " right" : " left"));
            }
            return String.join(", ", parts);
        }
    }

    /**
     * Lays a shape on a gate where it best covers the gate's own recorded frame.
     *
     * <p>The DHD normally says where the ring is. A gate built by an older plugin, or with its
     * DHD a block off, or recorded facing the wrong way, still has its frame blocks recorded, so
     * the shape is tried near the DHD's layout and in the reversed facing, and the layout covering
     * most of those blocks wins. The DHD's own layout wins any tie, so a gate built as its shape
     * says is laid exactly as before. Nothing is read from the world.
     *
     * @param gate
     *            the gate
     * @param shape
     *            the shape to lay
     * @return the layout, or null with no button, facing or world to start from
     */
    public static Layout layoutFor(final Stargate gate, final Stargate3DShape shape)
    {
        final Block button = gate.getGateDialLeverBlock();
        final BlockFace facing = gate.getGateFacing();
        if ((button == null) || (facing == null) || (gate.getGateWorld() == null))
        {
            return null;
        }
        final Set<Long> own = ownBlocks(gate);
        final Layout[] best = { null };
        final int[] bestScore = { -1 };
        for (final boolean reversed : new boolean[] { false, true })
        {
            final BlockFace face = reversed ? com.wormhole_xtreme.wormhole.utils.WorldUtils.getInverseDirection(facing) : facing;
            final GateGrid base = GateGrid.fromActivationHolder(shape, button.getX() - face.getModX(),
                button.getY() - face.getModY(), button.getZ() - face.getModZ(), face);
            if ((base != null) && searchAround(shape, base, reversed, own, best, bestScore))
            {
                break;
            }
        }
        return best[0];
    }

    /**
     * Whether every cell is one the gate recorded as its frame or its lights.
     *
     * @param gate
     *            the gate
     * @param cells
     *            cells laid from its shape
     * @return true if none of them falls outside the gate
     */
    public static boolean liesOnGate(final Stargate gate, final java.util.Collection<GateBlueprint.Cell> cells)
    {
        final Set<Long> own = ownBlocks(gate);
        for (final GateBlueprint.Cell c : cells)
        {
            if (!own.contains(packed(c.x(), c.y(), c.z())))
            {
                return false;
            }
        }
        return true;
    }

    /** Every block the gate recorded as its frame or its lights. */
    private static Set<Long> ownBlocks(final Stargate gate)
    {
        final Set<Long> own = new HashSet<>();
        for (final Location l : gate.getGateStructureBlocks())
        {
            own.add(packed(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
        }
        for (final List<Location> wave : gate.getGateLightBlocks())
        {
            if (wave == null)
            {
                continue;
            }
            for (final Location l : wave)
            {
                own.add(packed(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
            }
        }
        return own;
    }

    /**
     * Tries a shape at every offset around one layout, nearest first, keeping the best.
     *
     * @return true once a layout covers every frame cell, so nothing further can beat it
     */
    private static boolean searchAround(final Stargate3DShape shape, final GateGrid base, final boolean reversed,
        final Set<Long> own, final Layout[] best, final int[] bestScore)
    {
        final List<GateBlueprint.Cell> frame = frameCells(shape, base);
        final BlockFace face = base.facing();
        final BlockFace right = base.right();
        for (final int[] offset : SEARCH_OFFSETS)
        {
            final int along = offset[0];
            final int up = offset[1];
            final int across = offset[2];
            final int dx = (along * face.getModX()) + (across * right.getModX());
            final int dz = (along * face.getModZ()) + (across * right.getModZ());
            final int score = covered(frame, own, dx, up, dz);
            if (score > bestScore[0])
            {
                bestScore[0] = score;
                best[0] = new Layout(new GateGrid(base.ox() + dx, base.oy() + up, base.oz() + dz, face, right),
                    along, up, across, reversed);
                if (score == frame.size())
                {
                    return true;
                }
            }
        }
        return false;
    }

    /** Every offset the search tries, as {along, up, across}, nearer ones first so they win ties. */
    private static final List<int[]> SEARCH_OFFSETS = searchOffsets();

    private static List<int[]> searchOffsets()
    {
        final List<int[]> offsets = new ArrayList<>();
        for (final int along : nearestFirst(SEARCH_ALONG))
        {
            for (final int up : nearestFirst(SEARCH_UP))
            {
                for (final int across : nearestFirst(SEARCH_ACROSS))
                {
                    offsets.add(new int[] { along, up, across });
                }
            }
        }
        offsets.sort(java.util.Comparator.comparingInt(o -> Math.abs(o[0]) + Math.abs(o[1]) + Math.abs(o[2])));
        return offsets;
    }

    /** 0, -1, 1, -2, 2 ... up to the reach. */
    private static int[] nearestFirst(final int reach)
    {
        final int[] order = new int[(reach * 2) + 1];
        for (int i = 1; i <= reach; i++)
        {
            order[(i * 2) - 1] = -i;
            order[i * 2] = i;
        }
        return order;
    }

    /** A shape's frame and chevron cells on a grid. */
    private static List<GateBlueprint.Cell> frameCells(final Stargate3DShape shape, final GateGrid grid)
    {
        final List<GateBlueprint.Cell> frame = new ArrayList<>();
        for (final GateBlueprint.Cell cell : GateBlueprint.of(shape, grid))
        {
            if ((cell.part() == GateBlueprint.Part.FRAME) || (cell.part() == GateBlueprint.Part.CHEVRON))
            {
                frame.add(cell);
            }
        }
        return frame;
    }

    /** How many of the cells, moved by the offset, land on the gate's own blocks. */
    private static int covered(final List<GateBlueprint.Cell> cells, final Set<Long> own, final int dx, final int dy,
        final int dz)
    {
        int score = 0;
        for (final GateBlueprint.Cell cell : cells)
        {
            if (own.contains(packed(cell.x() + dx, cell.y() + dy, cell.z() + dz)))
            {
                score++;
            }
        }
        return score;
    }

    /** A block position as one number, for set lookups in the layout search. */
    private static long packed(final int x, final int y, final int z)
    {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
    }

    /**
     * Records a named shape for a gate whose frame is close enough to it, for a gate detection
     * cannot place: recorded under the wrong shape and missing a block or two.
     *
     * <p>The shape is laid by the gate's stored button and facing, as detection would, and each
     * of its frame and chevron cells is read. Nothing is placed. At {@link #NAMED_SHAPE_MINIMUM_PERCENT}
     * or more of them standing, the gate takes the shape; below that it keeps the one it had.
     *
     * @param gate
     *            the gate
     * @param shape
     *            the shape named for it
     * @return how well it fits, and what is missing
     */
    public static ShapeFit adoptShape(final Stargate gate, final Stargate3DShape shape)
    {
        final World world = gate.getGateWorld();
        final Layout layout = layoutFor(gate, shape);
        if (layout == null)
        {
            return new ShapeFit(false, 0, 0, List.of(), null);
        }
        final GateGrid grid = layout.grid();
        final com.wormhole_xtreme.wormhole.model.StargateShape previous = gate.getGateShape();
        final String previousName = gate.getGateShapeName();
        // Taken first so the palette resolves as it would for this shape.
        gate.setGateShape(shape);
        final org.bukkit.Material frame = gate.getEffectiveStructureMaterial();
        final org.bukkit.Material chevron = gate.getEffectiveChevronMaterial();
        final List<Gap> gaps = new ArrayList<>();
        int expected = 0;
        for (final GateBlueprint.Cell cell : frameCells(shape, grid))
        {
            expected++;
            final org.bukkit.Material found = world.getBlockAt(cell.x(), cell.y(), cell.z()).getType();
            if ((found != frame) && ((chevron == null) || (found != chevron)))
            {
                gaps.add(new Gap(cell.x(), cell.y(), cell.z(), found));
            }
        }
        final int present = expected - gaps.size();
        // Whole numbers, so exactly 90% is always enough.
        final boolean accepted = (expected > 0) && ((present * 100) >= (expected * NAMED_SHAPE_MINIMUM_PERCENT));
        if (!accepted)
        {
            gate.setGateShape(previous);
            gate.setGateShapeName(previousName);
        }
        return new ShapeFit(accepted, present, expected, gaps, layout);
    }

    /**
     * Rebuilds which blocks light at each chevron step from the gate's shape as it is now.
     *
     * <p>A gate saves its light order at detection, so a shape renumbered since (#350) never
     * reached it. This places the shape by the gate's stored button and facing without reading
     * a block, so it is safe to run on every gate, loaded or not.
     *
     * @param gate
     *            the gate, changed only when the result is {@link LightResult#REBUILT}
     * @return what happened
     */
    public static LightResult rebuildLightOrder(final Stargate gate)
    {
        if (!(gate.getGateShape() instanceof Stargate3DShape shape))
        {
            return LightResult.NO_SHAPE;
        }
        final Block button = gate.getGateDialLeverBlock();
        final BlockFace facing = gate.getGateFacing();
        final World world = gate.getGateWorld();
        if ((button == null) || (facing == null) || (world == null))
        {
            return LightResult.NO_ANCHOR;
        }
        if (gate.isGateActive() || gate.isGateLightsActive())
        {
            return LightResult.BUSY;
        }
        final Layout layout = layoutFor(gate, shape);
        if (layout == null)
        {
            return LightResult.NO_ANCHOR;
        }
        final List<List<Location>> derived = lightWaves(shape, layout.grid(), world);
        if (!fitsFrame(gate, derived))
        {
            return LightResult.DOES_NOT_FIT;
        }
        if (keysOf(gate.getGateLightBlocks()).equals(keysOf(derived)))
        {
            return LightResult.UNCHANGED;
        }
        gate.getGateLightBlocks().clear();
        gate.getGateLightBlocks().addAll(derived);
        return LightResult.REBUILT;
    }

    /** The shape's light waves placed on the grid, indexed as detection indexes them (0 unused). */
    private static List<List<Location>> lightWaves(final Stargate3DShape shape, final GateGrid grid, final World world)
    {
        final List<List<Location>> waves = new ArrayList<>();
        final List<StargateShapeLayer> layers = shape.getShapeLayers();
        for (int layerIdx = 1; layerIdx < layers.size(); layerIdx++)
        {
            final StargateShapeLayer layer = layers.get(layerIdx);
            if ((layer == null) || (layer.getLayerLightPositions() == null))
            {
                continue;
            }
            final List<List<Integer[]>> lights = layer.getLayerLightPositions();
            for (int waveIdx = 1; waveIdx < lights.size(); waveIdx++)
            {
                if (lights.get(waveIdx) != null)
                {
                    addLights(wave(waves, waveIdx), lights.get(waveIdx), layerIdx, grid, world);
                }
            }
        }
        return waves;
    }

    /** The wave at an index, padding the list and creating the wave as needed. */
    private static List<Location> wave(final List<List<Location>> waves, final int waveIdx)
    {
        while (waves.size() <= waveIdx)
        {
            waves.add(null);
        }
        if (waves.get(waveIdx) == null)
        {
            waves.set(waveIdx, new ArrayList<>());
        }
        return waves.get(waveIdx);
    }

    /** Places one layer's cells of a wave on the grid. */
    private static void addLights(final List<Location> wave, final List<Integer[]> cells, final int layerIdx,
        final GateGrid grid, final World world)
    {
        for (final Integer[] pos : cells)
        {
            final int col = pos[2].intValue();
            wave.add(new Location(world, grid.x(layerIdx, col), grid.y(pos[1].intValue()), grid.z(layerIdx, col)));
        }
    }

    /** Whether every block the shape lights is one the gate already has as frame or light. */
    private static boolean fitsFrame(final Stargate gate, final List<List<Location>> derived)
    {
        final Set<String> frame = new HashSet<>();
        for (final Location l : gate.getGateStructureBlocks())
        {
            frame.add(key(l));
        }
        for (final Set<String> wave : keysOf(gate.getGateLightBlocks()))
        {
            frame.addAll(wave);
        }
        for (final Set<String> wave : keysOf(derived))
        {
            if (!frame.containsAll(wave))
            {
                return false;
            }
        }
        return true;
    }

    /** Each wave as a set of block keys, index 0 dropped and trailing empty waves trimmed. */
    private static List<Set<String>> keysOf(final List<List<Location>> waves)
    {
        final List<Set<String>> keys = new ArrayList<>();
        for (int i = 1; i < waves.size(); i++)
        {
            final Set<String> wave = new HashSet<>();
            if (waves.get(i) != null)
            {
                for (final Location l : waves.get(i))
                {
                    wave.add(key(l));
                }
            }
            keys.add(wave);
        }
        while (!keys.isEmpty() && keys.get(keys.size() - 1).isEmpty())
        {
            keys.remove(keys.size() - 1);
        }
        return keys;
    }

    /** A block position with its world by name, since a reload can hand out a new World object. */
    private static String key(final Location l)
    {
        return ((l.getWorld() == null) ? "" : l.getWorld().getName()) + ':' + l.getBlockX() + ',' + l.getBlockY()
            + ',' + l.getBlockZ();
    }

    /**
     * Re-reads the gate's shape and brings its markers up to date.
     *
     * @param gate
     *            the gate to re-derive, which is modified in place only on success
     * @return what happened, and what moved
     */
    public static Outcome rederive(final Stargate gate)
    {
        if (!(gate.getGateShape() instanceof Stargate3DShape shape))
        {
            return new Outcome(Result.NO_SHAPE, List.of());
        }
        final Block button = gate.getGateDialLeverBlock();
        final BlockFace facing = gate.getGateFacing();
        if ((button == null) || (facing == null))
        {
            return new Outcome(Result.NO_ANCHOR, List.of());
        }
        // Detection reads the world, so this needs the gate's chunk. That is acceptable
        // because regenerate is an admin naming one gate; it is why there is no -all form.
        Stargate fresh = StargateHelper.checkStargate(button, facing, shape);
        final List<String> changes = new ArrayList<>();
        if (fresh == null)
        {
            // Recorded under a shape it does not match, as every gate the legacy importer brings
            // in is recorded as Standard: take the shape the frame actually is, if any.
            fresh = StargateHelper.checkStargate(button, facing);
            if ((fresh == null) || !(fresh.getGateShape() instanceof Stargate3DShape))
            {
                return new Outcome(Result.NOT_DETECTED, List.of());
            }
            changes.add("shape (was " + shape.getShapeName() + ")");
            gate.setGateShape(fresh.getGateShape());
        }
        changes.addAll(copyMarkers(gate, fresh));
        return new Outcome(Result.REDERIVED, changes);
    }

    /**
     * Takes down every wall sign standing on a frame or chevron cell of the gate's shape, and puts
     * the block back.
     *
     * <p>1.7.0's {@code Massive} hung its name sign inside the ring, and detection cannot match a
     * frame with a sign in it, so this runs before the gate is re-detected. It goes by the cells
     * rather than the gate's recorded name holder, which a gate saved by an older version may not
     * have where the sign actually is.
     *
     * @param gate
     *            the gate
     * @return the blocks put back, empty if no sign stood in the frame
     */
    public static List<Block> restoreFrameUnderSigns(final Stargate gate)
    {
        final World world = gate.getGateWorld();
        if ((world == null) || !(gate.getGateShape() instanceof Stargate3DShape shape))
        {
            return List.of();
        }
        final Layout layout = layoutFor(gate, shape);
        if (layout == null)
        {
            return List.of();
        }
        final org.bukkit.Material chevron = gate.getEffectiveChevronMaterial();
        final List<Block> restored = new ArrayList<>();
        for (final GateBlueprint.Cell cell : frameCells(shape, layout.grid()))
        {
            final Block block = world.getBlockAt(cell.x(), cell.y(), cell.z());
            if (com.wormhole_xtreme.wormhole.utils.MaterialUtils.isWallSign(block.getType()))
            {
                block.setType(((cell.part() == GateBlueprint.Part.CHEVRON) && (chevron != null))
                    ? chevron : gate.getEffectiveStructureMaterial(), false);
                restored.add(block);
            }
        }
        return restored;
    }

    /**
     * Moves the freshly detected marker positions onto the gate that is really registered.
     *
     * <p>The fresh gate is a detached object detection just built; it is read for its marker
     * positions and thrown away. Copying markers rather than swapping the object is what keeps
     * the gate's identity, network, owner, IDC and open state exactly as they were -- none of
     * which detection knows anything about.
     *
     * @param gate
     *            the registered gate
     * @param fresh
     *            what detection found against the current shape
     * @return one line per marker that moved
     */
    private static List<String> copyMarkers(final Stargate gate, final Stargate fresh)
    {
        final List<String> changes = new ArrayList<>();

        if (moved(gate.getGateRedstoneDialActivationBlock(), fresh.getGateRedstoneDialActivationBlock()))
        {
            gate.setGateRedstoneDialActivationBlock(fresh.getGateRedstoneDialActivationBlock());
            changes.add("redstone dial input");
        }
        if (moved(gate.getGateRedstoneSignActivationBlock(), fresh.getGateRedstoneSignActivationBlock()))
        {
            gate.setGateRedstoneSignActivationBlock(fresh.getGateRedstoneSignActivationBlock());
            changes.add("redstone sign cycler");
        }
        if (moved(gate.getGateRedstoneGateActivatedBlock(), fresh.getGateRedstoneGateActivatedBlock()))
        {
            gate.setGateRedstoneGateActivatedBlock(fresh.getGateRedstoneGateActivatedBlock());
            changes.add("redstone gate-activated output");
        }
        // Set from the presence of a dial input, the same way detection sets it. A gate that
        // has just gained one is redstone powered now whether or not it was when it was built.
        if (fresh.isGateRedstonePowered() && !gate.isGateRedstonePowered())
        {
            gate.setGateRedstonePowered(true);
        }
        if (moved(gate.getGateIrisLeverBlock(), fresh.getGateIrisLeverBlock()))
        {
            gate.setGateIrisLeverBlock(fresh.getGateIrisLeverBlock());
            changes.add("iris lever");
        }
        if (moved(gate.getGateNameBlockHolder(), fresh.getGateNameBlockHolder()))
        {
            gate.setGateNameBlockHolder(fresh.getGateNameBlockHolder());
            changes.add("name sign");
        }
        copyDialSign(gate, fresh, changes);
        return changes;
    }

    /**
     * Reattaches the DHD sign, which is the case #54 asked for.
     *
     * <p>A sign that is standing but not bound to the gate -- because it was placed after the
     * gate was built, or because the gate was detected from a shape that had no {@code :D}
     * -- leaves the gate unable to choose a destination and saying nothing about why. Detection
     * only records a dial sign when a real wall sign is actually there, so a non-null result
     * here means one is standing now.
     *
     * @param gate
     *            the registered gate
     * @param fresh
     *            what detection found
     * @param changes
     *            collects a line if the sign moved or arrived
     */
    private static void copyDialSign(final Stargate gate, final Stargate fresh, final List<String> changes)
    {
        if (fresh.getGateDialSignBlock() == null)
        {
            return;
        }
        if (!moved(gate.getGateDialSignBlock(), fresh.getGateDialSignBlock()))
        {
            return;
        }
        gate.setGateDialSignBlock(fresh.getGateDialSignBlock());
        gate.setGateDialSign(fresh.getGateDialSign());
        gate.setGateSignPowered(true);
        changes.add("dial sign");
    }

    /**
     * Whether a freshly derived marker is somewhere the gate does not already have it.
     *
     * <p>False when detection found nothing, which is what makes this only ever add or move a
     * marker: a shape that no longer declares one leaves what the gate already has alone.
     *
     * @param current
     *            where the gate has the marker, or null if it has none
     * @param derived
     *            where detection puts it now, or null if the shape declares none
     * @return true if the gate should take the derived position
     */
    private static boolean moved(final Block current, final Block derived)
    {
        if (derived == null)
        {
            return false;
        }
        if (current == null)
        {
            return true;
        }
        return (current.getX() != derived.getX())
            || (current.getY() != derived.getY())
            || (current.getZ() != derived.getZ())
            || !sameWorld(current, derived);
    }

    /**
     * Whether two blocks are in the same world, by name.
     *
     * <p>By name rather than by reference because a gate reloaded from disk and a block read
     * fresh out of the server can be handed different {@code World} objects for the same world.
     *
     * @param current
     *            one block
     * @param derived
     *            the other
     * @return true if both name the same world, or neither has one
     */
    private static boolean sameWorld(final Block current, final Block derived)
    {
        if ((current.getWorld() == null) || (derived.getWorld() == null))
        {
            return current.getWorld() == derived.getWorld();
        }
        return current.getWorld().getName().equals(derived.getWorld().getName());
    }
}
