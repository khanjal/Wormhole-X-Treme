package com.wormhole_xtreme.wormhole.logic;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;

import com.wormhole_xtreme.wormhole.model.GateSerializer;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateNetwork;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeLayer;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, trimmed Stargate helper. Responsibilities:
 * - Provide geometry utility used by unit tests
 * - Delegate serialization to GateSerializer
 * - Delegate shape loading/registry to StargateShapeRegistry
 * - Provide small stubs for legacy APIs still referenced elsewhere
 */
public final class StargateHelper
{
    private StargateHelper() { }

    /**
     * Compute the gate facing direction by analysing the spatial variance of
     * the gate's structure blocks while excluding the DHD/dial block location.
     * Returns null for indeterminate or invalid inputs.
     */
    public static BlockFace computeGateFacingFromGeometry(final Stargate gate)
    {
        if (gate == null)
        {
            return null;
        }
        final Block dhd = gate.getGateDialLeverBlock();
        if (dhd == null)
        {
            return null;
        }
        final List<Location> frame = frameBlocksExcludingDhd(gate.getGateStructureBlocks(), dhd);
        if (frame.isEmpty())
        {
            return null;
        }

        double sumX = 0.0;
        double sumZ = 0.0;
        for (final Location loc : frame)
        {
            sumX += loc.getBlockX();
            sumZ += loc.getBlockZ();
        }
        final double meanX = sumX / frame.size();
        final double meanZ = sumZ / frame.size();

        double varX = 0.0;
        double varZ = 0.0;
        for (final Location loc : frame)
        {
            final double dx = loc.getBlockX() - meanX;
            final double dz = loc.getBlockZ() - meanZ;
            varX += dx * dx;
            varZ += dz * dz;
        }
        // Population variance: only the relative magnitudes are compared.
        varX = varX / frame.size();
        varZ = varZ / frame.size();

        if (Math.abs(varX - varZ) < 1e-9)
        {
            return null; // indeterminate
        }
        if (varX > varZ)
        {
            // Spread mainly along X, so the gate faces north or south, whichever side the DHD is on.
            return dhd.getZ() > meanZ ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        // Spread mainly along Z, so east or west.
        return dhd.getX() > meanX ? BlockFace.EAST : BlockFace.WEST;
    }

    /**
     * The gate's frame blocks with nulls and the DHD's own cell removed.
     *
     * <p>The DHD sits in the structure list but is off to one side of the ring, so leaving it
     * in would drag the mean towards it and skew the spread the facing is read from.
     *
     * @param structure
     *            the gate's structure blocks, possibly null
     * @param dhd
     *            the dial lever block to exclude
     * @return the frame blocks to measure, never null
     */
    private static List<Location> frameBlocksExcludingDhd(final List<Location> structure, final Block dhd)
    {
        final List<Location> frame = new ArrayList<Location>();
        if (structure == null)
        {
            return frame;
        }
        for (final Location loc : structure)
        {
            if ((loc != null)
                && !((loc.getBlockX() == dhd.getX())
                    && (loc.getBlockY() == dhd.getY())
                    && (loc.getBlockZ() == dhd.getZ())))
            {
                frame.add(loc);
            }
        }
        return frame;
    }

    // ---------------------------------------------------------------------
    // Delegation helpers (thin wrappers)
    // ---------------------------------------------------------------------

    public static void loadShapes()
    {
        StargateShapeRegistry.loadShapes();
    }

    public static StargateShape getStargateShape(final String name)
    {
        return StargateShapeRegistry.getStargateShape(name);
    }

    public static boolean isStargateShape(final String name)
    {
        return StargateShapeRegistry.isStargateShape(name);
    }

    public static Stargate parseVersionedData(final byte[] gateData, final World w, final String name, final StargateNetwork network)
    {
        return GateSerializer.parseVersionedData(gateData, w, name, network);
    }

    public static byte[] stargatetoBinary(final Stargate s)
    {
        return GateSerializer.stargatetoBinary(s);
    }

    // ---------------------------------------------------------------------
    // Gate detection
    // ---------------------------------------------------------------------

    /**
     * Checks whether a block could be part of some gate's frame.
     *
     * <p>Both lookups are O(1), which makes this a cheap way to rule out a candidate
     * position before paying for a geometry scan against every registered shape.
     *
     * @param material
     *            the material to test
     * @return true if any loaded shape or configured material group builds frames from it
     */
    public static boolean isPossibleGateFrameMaterial(final org.bukkit.Material material)
    {
        if (material == null)
        {
            return false;
        }
        return StargateShapeRegistry.getKnownStructureMaterials().contains(material)
            || MaterialGroupRegistry.getGroupByStructureMaterial(material) != null;
    }

    /**
     * Attempts to find a valid stargate whose activation button/lever is
     * {@code clickedBlock} facing {@code direction}.  Iterates every loaded
     * 3-D shape and returns the best match, or {@code null} if none match.
     *
     * <p>More than one shape routinely matches the same build. Detection only reads frame
     * and portal cells, and a sign-dial shape puts its DHD in cells the plain twin marks
     * {@code [I]} -- so anything built as {@code StandardSignDial} satisfies {@code Standard}
     * as well, and {@code Horizontal} and {@code HorizontalSignDial} are byte-identical once
     * markers are stripped. Which one wins is decided by {@link #beatsBestMatch}, which used
     * to weigh only {@code REDSTONE_ACTIVATED} and fall back below that to whatever order a
     * {@code ConcurrentHashMap} happened to hand back.
     */
    public static Stargate checkStargate(final Block clickedBlock, final BlockFace direction)
    {
        if (clickedBlock == null || direction == null)
        {
            return null;
        }
        Stargate best = null;
        Stargate3DShape bestShape = null;
        for (final StargateShape shape : StargateShapeRegistry.getStargateShapes().values())
        {
            if (shape instanceof Stargate3DShape shape3D)
            {
                final Stargate result = check3DShape(clickedBlock, direction, shape3D);
                if ((result != null) && beatsBestMatch(result, shape3D, best, bestShape))
                {
                    best = result;
                    bestShape = shape3D;
                }
            }
        }
        return best;
    }

    /**
     * Ranks one matching shape against the best match so far, most significant test first.
     *
     * <ol>
     *   <li><b>A dial sign was actually found.</b> Only a shape carrying {@code :D} looks for
     *       one, and finding one is proof the player built a sign gate. Without this,
     *       {@code HorizontalSignDial} could never be detected at all: its frame is identical
     *       to {@code Horizontal}'s, {@code Horizontal} comes back first from the registry,
     *       and the player's dial sign was then overwritten by the name sign {@code Horizontal}
     *       puts on that same cell.</li>
     *   <li><b>{@code REDSTONE_ACTIVATED=TRUE}.</b> No shipped pair needs this any more, but a
     *       server's custom shapes can still be written as redstone twins, so it stays.</li>
     *   <li><b>More frame blocks.</b> The shape that accounts for more of what is actually
     *       built is the more specific description of it. This is what settles
     *       {@code MinimalSignDial} against {@code Minimal}, which previously agreed only
     *       because of where the two names happened to hash.</li>
     *   <li><b>Shape name.</b> Nothing left to tell them apart, so decide by something stable.
     *       Shapes are held in a {@code ConcurrentHashMap} keyed by name: iteration order is
     *       arbitrary, and adding a twelfth shape resizes the table and reshuffles all of it.
     *       A server should not get a different gate for adding an unrelated custom shape.</li>
     * </ol>
     *
     * @return true if {@code candidate} should replace {@code best}
     */
    static boolean beatsBestMatch(final Stargate candidate, final Stargate3DShape candidateShape,
        final Stargate best, final Stargate3DShape bestShape)
    {
        if (best == null)
        {
            return true;
        }
        if (candidate.isGateSignPowered() != best.isGateSignPowered())
        {
            return candidate.isGateSignPowered();
        }
        if (candidateShape.isShapeRedstoneActivated() != bestShape.isShapeRedstoneActivated())
        {
            return candidateShape.isShapeRedstoneActivated();
        }
        final int candidateBlocks = candidate.getGateStructureBlocks().size();
        final int bestBlocks = best.getGateStructureBlocks().size();
        if (candidateBlocks != bestBlocks)
        {
            return candidateBlocks > bestBlocks;
        }
        return String.valueOf(candidateShape.getShapeName())
            .compareTo(String.valueOf(bestShape.getShapeName())) < 0;
    }

    /**
     * Like {@link #checkStargate(Block, BlockFace)} but only tests the given
     * {@code shape}.
     */
    public static Stargate checkStargate(final Block clickedBlock, final BlockFace direction, final StargateShape shape)
    {
        if (clickedBlock == null || direction == null || shape == null)
        {
            return null;
        }
        if (shape instanceof Stargate3DShape shape3D)
        {
            return check3DShape(clickedBlock, direction, shape3D);
        }
        return null;
    }

    /**
     * Works out the world height a redstone marker's component belongs at.
     *
     * <p>The shapes use two conventions and both are valid. Written bare, as {@code [RA]},
     * the cell is not a frame block: it is an empty space sitting on top of one, and that
     * space is where the redstone goes. Written as {@code [S:RA]} the cell <em>is</em> the
     * frame block, so the redstone belongs on top of it, one block higher.
     *
     * <p>Every shape that ships today writes the bare form, but the other one is what the
     * older shapes used and is still accepted, so assuming either on its own is wrong. What
     * both forms are for is the same: landing the redstone on a cell nothing is built in.
     *
     * @param layer
     *            the layer the marker is on
     * @param markerPos
     *            the marker's grid position
     * @param baseY
     *            the world height of the marker cell
     * @return the world height the redstone component belongs at
     */
    static int redstoneComponentY(final StargateShapeLayer layer, final int[] markerPos, final int baseY)
    {
        for (final Integer[] block : layer.getLayerBlockPositions())
        {
            if ((block[1].intValue() == markerPos[1]) && (block[2].intValue() == markerPos[2]))
            {
                return baseY + 1; // [S:RA] — the marker is the frame block, so sit on top
            }
        }
        return baseY; // [RA] — the marker is already the empty cell above the frame
    }

    /**
     * Where a gate sits and which way it faces, so a shape cell can be turned into a world
     * block.
     *
     * <p>The mapping was written out seventeen times in this file before this existed: the
     * layer index steps along the facing, the column along its perpendicular right, and the
     * row is height. Getting one of those wrong is the mistake a detection refactor is most
     * likely to make, so it is written once.
     */
    private static final class GateFrame
    {
        private final World world;
        private final int ox;
        private final int oy;
        private final int oz;
        private final BlockFace facing;
        private final BlockFace right;

        GateFrame(final World world, final int ox, final int oy, final int oz, final BlockFace facing)
        {
            this.world = world;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.facing = facing;
            this.right = WorldUtils.getPerpendicularRightDirection(facing);
        }

        /**
         * The world block a shape cell maps to.
         *
         * @param layerIdx
         *            the 1-based layer, increasing away from the player
         * @param row
         *            the row from the bottom
         * @param col
         *            the column from the right, looking at the gate face
         * @return the block that cell names
         */
        Block blockAt(final int layerIdx, final int row, final int col)
        {
            return world.getBlockAt(
                ox + ((layerIdx - 1) * facing.getModX()) + (col * right.getModX()),
                oy + row,
                oz + ((layerIdx - 1) * facing.getModZ()) + (col * right.getModZ()));
        }

        Block blockAt(final int layerIdx, final Integer[] pos)
        {
            return blockAt(layerIdx, pos[1].intValue(), pos[2].intValue());
        }

        Block blockAt(final int layerIdx, final int[] pos)
        {
            return blockAt(layerIdx, pos[1], pos[2]);
        }

        BlockFace facing()
        {
            return facing;
        }

        World world()
        {
            return world;
        }
    }

    /**
     * Decides which material this gate's frame must be made of, by reading the first
     * frame position out of the world.
     *
     * <p>The shape's own {@code STARGATE_MATERIAL} always wins, so shapes that predate
     * material groups keep behaving exactly as before. Otherwise the block is looked up
     * in {@link MaterialGroupRegistry}: if it names a configured palette, and the shape
     * does not restrict itself to other palettes, that palette's frame material is what
     * the rest of the scan verifies against.
     *
     * @return the material every frame block must be, or null if the frame belongs to no
     *         palette this shape accepts
     */
    private static org.bukkit.Material resolveStructureMaterial(final GateFrame frame,
                                                                 final Stargate3DShape shape,
                                                                 final ArrayList<StargateShapeLayer> shapeLayers)
    {
        final org.bukkit.Material shapeMaterial = shape.getShapeStructureMaterial();
        for (int layerIdx = 1; layerIdx < shapeLayers.size(); layerIdx++)
        {
            final StargateShapeLayer layer = shapeLayers.get(layerIdx);
            if (layer == null || layer.getLayerBlockPositions().isEmpty())
            {
                continue;
            }
            final Integer[] pos = layer.getLayerBlockPositions().get(0);
            final Block cell = frame.blockAt(layerIdx, pos);
            final org.bukkit.Material found = cell.getType();

            if (found == shapeMaterial)
            {
                return shapeMaterial;
            }
            final MaterialGroup group = MaterialGroupRegistry.getGroupByStructureMaterial(found);
            if (group != null && shape.acceptsMaterialGroup(group.getName()))
            {
                return group.getStructureMaterial();
            }
            // First frame block read and it matched nothing — no point scanning the rest.
            return null;
        }
        return shapeMaterial;
    }

    /**
     * Which cells of a layer carry a light marker, keyed for lookup.
     *
     * <p>A chevron written the old way is an {@code [S:L#n]} cell — a frame block that also
     * appears in a lighting wave — so telling one from an ordinary frame block means asking
     * whether its position is in any wave. Built once per layer rather than searched per
     * block: detection runs up to 156 times on a single click, and the alternative is a scan
     * of every wave for every frame block of every candidate shape.
     *
     * @param layer
     *            the layer
     * @return the keys of its light-marked cells, empty if it has none
     */
    static java.util.Set<Long> lightCells(final StargateShapeLayer layer)
    {
        final java.util.Set<Long> cells = new java.util.HashSet<Long>();
        final ArrayList<ArrayList<Integer[]>> waves = layer.getLayerLightPositions();
        if (waves == null)
        {
            return cells;
        }
        for (final ArrayList<Integer[]> wave : waves)
        {
            if (wave == null)
            {
                continue; // index 0 is the placeholder the runtime lighting expects
            }
            for (final Integer[] pos : wave)
            {
                cells.add(cellKey(pos));
            }
        }
        return cells;
    }

    /**
     * A layer cell's row and column packed into one key.
     *
     * <p>Only the row and column identify a cell within a layer; the first element of a
     * shape position is always zero, the layer index being carried by the list the position
     * lives in.
     *
     * @param pos
     *            the shape position
     * @return the key
     */
    static Long cellKey(final Integer[] pos)
    {
        return Long.valueOf(((long) pos[1].intValue() << 32) ^ (pos[2].intValue() & 0xffffffffL));
    }

    /**
     * Core V2 (3-D) shape detection.
     *
     * <p>Coordinate system (per StargateShapeLayer):
     * <ul>
     *   <li>{@code L} – layer index (1-based; increases away from the player)
     *   <li>{@code R} – row from the bottom (0 = ground row)
     *   <li>{@code C} – column from the right (when looking at the gate face)
     * </ul>
     *
     * <p>Given gate-facing direction {@code F} and its perpendicular-right
     * direction {@code RIGHT}:
     * <pre>
     *   wx = ox + (L-1)*F.modX  + C*RIGHT.modX
     *   wy = oy + R
     *   wz = oz + (L-1)*F.modZ  + C*RIGHT.modZ
     * </pre>
     * where the origin is derived from the activation-holder position.
     */
    private static Stargate check3DShape(final Block clickedBlock,
                                          final BlockFace facing,
                                          final Stargate3DShape shape)
    {
        final int activationLayerIdx = shape.getShapeActivationLayer();
        if (activationLayerIdx < 1)
        {
            return null;
        }
        final ArrayList<StargateShapeLayer> shapeLayers = shape.getShapeLayers();
        if (shapeLayers == null || shapeLayers.size() <= activationLayerIdx)
        {
            return null;
        }
        final StargateShapeLayer actLayer = shapeLayers.get(activationLayerIdx);
        if (actLayer == null)
        {
            return null;
        }
        final int[] aPos = actLayer.getLayerActivationPosition();
        if (aPos.length < 3)
        {
            return null;
        }
        final int aRow = aPos[1]; // row from bottom
        final int aCol = aPos[2]; // col from right

        // The button/lever is mounted on the gate-facing face of the activation
        // holder block, so the holder is one step opposite to the facing direction.
        final Block holder = clickedBlock.getRelative(WorldUtils.getInverseDirection(facing));

        // Note: Shapes may declare an `ACTIVATION` metadata value, but we do
        // not enforce the activation block's material here. Players may place
        // buttons or levers freely; detection should succeed regardless of the
        // exact activation item used.

        // Derive the gate's coordinate-system origin.
        final BlockFace right = WorldUtils.getPerpendicularRightDirection(facing);
        final int ox = holder.getX() - (activationLayerIdx - 1) * facing.getModX() - aCol * right.getModX();
        final int oy = holder.getY() - aRow;
        final int oz = holder.getZ() - (activationLayerIdx - 1) * facing.getModZ() - aCol * right.getModZ();

        final World world = clickedBlock.getWorld();
        final GateFrame frame = new GateFrame(world, ox, oy, oz, facing);
        final int numLayers = shapeLayers.size();

        // Resolve the palette from what is actually standing in the world rather than
        // testing the one material the shape happens to name. Reading the first frame
        // block and looking it up is a single map lookup, so a server can offer any
        // number of palettes without making detection slower. Doing it the other way —
        // one .shape file per palette — costs a full extra geometry scan per palette on
        // every detection attempt, and detection already runs up to 156 times for a
        // single click on a directional block that is not a gate.
        final org.bukkit.Material structMat = resolveStructureMaterial(frame, shape, shapeLayers);
        if (structMat == null)
        {
            return null; // frame is not built from any palette this shape accepts
        }

        // Resolved once and reused: the palette decides the chevron material as well as the
        // portal, iris and light ones, and populating the gate below needs the same lookup.
        final MaterialGroup group = MaterialGroupRegistry.getGroupByStructureMaterial(structMat);

        // What an unlit chevron is built from, if this gate has them at all. Null is the
        // ordinary case and means neither shape nor palette named one: [S:L#n] then accepts
        // only the frame material, exactly as before, and a [C] cell means the same as [S].
        final org.bukkit.Material chevronMat = Stargate.resolveChevronMaterial(shape, group);

        if (!frameMatchesShape(frame, shapeLayers, numLayers, structMat, chevronMat))
        {
            return null;
        }

        final Stargate gate = populateGate(frame, clickedBlock, shape, shapeLayers, numLayers, group);
        applyRedstoneWiring(gate, frame, shape);
        return gate;
    }

    /**
     * Whether the blocks standing in the world match the shape.
     *
     * @return true if every frame, chevron and portal cell is what the shape asks for
     */
    private static boolean frameMatchesShape(final GateFrame frame,
                                            final ArrayList<StargateShapeLayer> shapeLayers,
                                            final int numLayers,
                                            final org.bukkit.Material structMat,
                                            final org.bukkit.Material chevronMat)
    {
        // Verify every structure (S) block has the expected material,
        // AND every portal (P) block is NOT the structure material.
        // The second check prevents false positives inside solid obsidian rooms
        // (or any room built from the gate's structure material) where the frame
        // outline happens to match a shape but the interior is not open space.
        for (int layerIdx = 1; layerIdx < numLayers; layerIdx++)
        {
            final StargateShapeLayer layer = shapeLayers.get(layerIdx);
            if (layer == null)
            {
                continue;
            }
            final java.util.Set<Long> litCells = lightCells(layer);
            for (final Integer[] pos : layer.getLayerBlockPositions())
            {
                final Block cell = frame.blockAt(layerIdx, pos);
                final org.bukkit.Material found = cell.getType();
                if (found == structMat)
                {
                    continue;
                }
                // An [S:L#n] cell is a chevron, and a shape with a chevron material lets one
                // be built from that instead, so the gate shows where its chevrons are before
                // any of them light. Both materials are accepted rather than only the chevron
                // one: every gate standing in every frame.world() today has frame material in those
                // positions, and re-detection has to go on finding them.
                if ((chevronMat != null) && (found == chevronMat) && litCells.contains(cellKey(pos)))
                {
                    continue;
                }
                return false; // structure block mismatch
            }
            for (final Integer[] pos : layer.getLayerChevronPositions())
            {
                final Block cell = frame.blockAt(layerIdx, pos);
                // A [C] cell is the strict form: the shape asked for a distinct block there,
                // so the frame material will not do. Unless the shape named no chevron
                // material at all, in which case [C] falls back to meaning [S] rather than
                // making the shape impossible to build.
                if (cell.getType() != ((chevronMat != null) ? chevronMat : structMat))
                {
                    return false; // chevron block mismatch
                }
            }
            for (final Integer[] pos : layer.getLayerPortalPositions())
            {
                final Block cell = frame.blockAt(layerIdx, pos);
                if (cell.getType() == structMat)
                {
                    return false; // portal interior is solid — not a real gate
                }
            }
        }

        return true;
    }

    /** Builds the gate and records every block the shape names. */
    private static Stargate populateGate(final GateFrame frame,
                                         final Block clickedBlock,
                                         final Stargate3DShape shape,
                                         final ArrayList<StargateShapeLayer> shapeLayers,
                                         final int numLayers,
                                         final MaterialGroup group)
    {
        // All structure blocks match.  Build and populate the Stargate object.
        final Stargate gate = new Stargate();
        // Record which palette matched so the gate's portal, iris and light materials
        // come from it rather than from the shape's own defaults.
        gate.setGateMaterialGroup(group);
        gate.setGateShape(shape);
        gate.setGateFacing(frame.facing());
        gate.setGateWorld(frame.world());
        gate.setGateDialLeverBlock(clickedBlock);

        boolean hasDialSign = false;

        for (int layerIdx = 1; layerIdx < numLayers; layerIdx++)
        {
            final StargateShapeLayer layer = shapeLayers.get(layerIdx);
            if (layer == null)
            {
                continue;
            }

            // Structure blocks
            for (final Integer[] pos : layer.getLayerBlockPositions())
            {
                final Block cell = frame.blockAt(layerIdx, pos);
                gate.getGateStructureBlocks().add(cell.getLocation());
            }

            // Chevron blocks are frame for every purpose except which material they have to
            // be: protected from breaking, indexed for lookup, and cleared when the gate is
            // removed. Only the verification above cares about the difference.
            for (final Integer[] pos : layer.getLayerChevronPositions())
            {
                final Block cell = frame.blockAt(layerIdx, pos);
                gate.getGateStructureBlocks().add(cell.getLocation());
            }

            // Portal blocks
            for (final Integer[] pos : layer.getLayerPortalPositions())
            {
                final Block cell = frame.blockAt(layerIdx, pos);
                gate.getGatePortalBlocks().add(cell.getLocation());
            }

            // Light blocks — shape uses 1-based wave indices; runtime lighting expects
            // a placeholder at index 0 and real waves starting at index 1.
            final ArrayList<ArrayList<Integer[]>> lightWaves = layer.getLayerLightPositions();
            if (lightWaves != null)
            {
                for (int waveIdx = 1; waveIdx < lightWaves.size(); waveIdx++)
                {
                    final ArrayList<Integer[]> wavePositions = lightWaves.get(waveIdx);
                    if (wavePositions == null)
                    {
                        continue;
                    }
                    final int gateWaveIdx = waveIdx; // keep index 1..N so index 0 stays as placeholder
                    while (gate.getGateLightBlocks().size() <= gateWaveIdx)
                    {
                        gate.getGateLightBlocks().add(null);
                    }
                    if (gate.getGateLightBlocks().get(gateWaveIdx) == null)
                    {
                        gate.getGateLightBlocks().set(gateWaveIdx, new ArrayList<Location>());
                    }
                    for (final Integer[] pos : wavePositions)
                    {
                        final Block cell = frame.blockAt(layerIdx, pos);
                        gate.getGateLightBlocks().get(gateWaveIdx).add(cell.getLocation());
                    }
                }
            }

            // Woosh blocks — same 1-based → 0-based shift.
            final ArrayList<ArrayList<Integer[]>> wooshWaves = layer.getLayerWooshPositions();
            if (wooshWaves != null)
            {
                for (int waveIdx = 1; waveIdx < wooshWaves.size(); waveIdx++)
                {
                    final ArrayList<Integer[]> wavePositions = wooshWaves.get(waveIdx);
                    if (wavePositions == null)
                    {
                        continue;
                    }
                    final int gateWaveIdx = waveIdx - 1;
                    while (gate.getGateWooshBlocks().size() <= gateWaveIdx)
                    {
                        gate.getGateWooshBlocks().add(new ArrayList<Location>());
                    }
                    for (final Integer[] pos : wavePositions)
                    {
                        final Block cell = frame.blockAt(layerIdx, pos);
                        gate.getGateWooshBlocks().get(gateWaveIdx).add(cell.getLocation());
                    }
                }
            }

            // Name sign holder (N)
            final int[] nPos = layer.getLayerNameSignPosition();
            if (nPos.length >= 3)
            {
                final Block cell = frame.blockAt(layerIdx, nPos);
                gate.setGateNameBlockHolder(cell);
            }

            // Player teleport exit (EP)
            final int[] epPos = layer.getLayerPlayerExitPosition();
            if (epPos.length >= 3)
            {
                final Block cell = frame.blockAt(layerIdx, epPos);
                // EP is the block the player's feet land on. Add 1.0 Y so feet are on
                // top of it, offset one block in the -frame.facing() direction to place the
                // player just outside the portal water, and face them in the gate's
                // frame.facing() direction with pitch zeroed.
                // Move one block in the gate's frame.facing() direction (outwards)
                // so the player appears just outside the portal rather than
                // being placed inside it. Use frame.facing()'s mod components directly.
                final Location tpLoc = new Location(frame.world(), cell.getX() + 0.5 + frame.facing().getModX(), cell.getY() + 1.0, cell.getZ() + 0.5 + frame.facing().getModZ());
                try { tpLoc.setYaw(WorldUtils.getDegreesFromBlockFace(frame.facing())); } catch (final Throwable ignore) { /* best effort */ }
                try { tpLoc.setPitch(0f); } catch (final Throwable ignore) { /* best effort */ }
                gate.setGatePlayerTeleportLocation(tpLoc);
            }

            // Minecart teleport exit (EM)
            final int[] emPos = layer.getLayerMinecartExitPosition();
            if (emPos.length >= 3)
            {
                final Block cell = frame.blockAt(layerIdx, emPos);
                // Use a half-block Y offset so minecarts spawn above the ground and do not sink into blocks.
                gate.setGateMinecartTeleportLocation(new Location(frame.world(), cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5));
            }

            // Dial-sign holder (D) — the sign sits on the gate-frame.facing() face of this block.
            final int[] dPos = layer.getLayerDialSignPosition();
            if (dPos.length >= 3)
            {
                final Block cell = frame.blockAt(layerIdx, dPos);
                final Block signBlock = cell.getRelative(frame.facing());
                if (com.wormhole_xtreme.wormhole.utils.MaterialUtils.isWallSign(signBlock.getType()))
                {
                    try
                    {
                        final Sign signState = (Sign) signBlock.getState();
                        gate.setGateDialSignBlock(signBlock);
                        gate.setGateDialSign(signState);
                        // Read the name the player wrote on line 0 of the sign.
                        // Stripped, because the plugin writes this same line itself once the
                        // gate is running. Re-detecting a styled sign would otherwise take the
                        // colour codes into the gate's name -- invisible characters in a name
                        // that has to be typed to dial it.
                        final String line0 = signState.getSide(Side.FRONT).getLine(0);
                        final String signName = com.wormhole_xtreme.wormhole.utils.SignStyle
                            .stripFormatting(line0).trim();
                        if (!signName.isEmpty())
                        {
                            gate.setGateName(signName);
                        }
                        hasDialSign = true;
                    }
                    catch (final Exception e)
                    {
                        // Sign state not available — treat as no sign.
                    }
                }
            }

            // Iris activation holder (IA) — iris lever is on the gate-frame.facing() face.
            final int[] iaPos = layer.getLayerIrisActivationPosition();
            if (iaPos.length >= 3)
            {
                final Block cell = frame.blockAt(layerIdx, iaPos);
                gate.setGateIrisLeverBlock(cell.getRelative(frame.facing()));
            }

            // Redstone dial activation (RD)
            final int[] rdPos = layer.getLayerRedstoneDialActivationPosition();
            if (rdPos.length >= 3)
            {
                final Block cell = frame.blockAt(layerIdx, rdPos);
                gate.setGateRedstoneDialActivationBlock(
                    frame.world().getBlockAt(cell.getX(), redstoneComponentY(layer, rdPos, cell.getY()), cell.getZ()));
                gate.setGateRedstonePowered(true);
            }

            // Redstone sign activation (RS)
            final int[] rsPos = layer.getLayerRedstoneSignActivationPosition();
            if (rsPos.length >= 3)
            {
                final Block cell = frame.blockAt(layerIdx, rsPos);
                gate.setGateRedstoneSignActivationBlock(
                    frame.world().getBlockAt(cell.getX(), redstoneComponentY(layer, rsPos, cell.getY()), cell.getZ()));
            }

            // Redstone gate-activated output (RA)
            final int[] raPos = layer.getLayerRedstoneGateActivatedPosition();
            if (raPos.length >= 3)
            {
                final Block cell = frame.blockAt(layerIdx, raPos);
                // Matters most here: the gate-activated output only fires when this block
                // is a lever, so getting the height wrong means the lever a player placed
                // is never found or toggled.
                gate.setGateRedstoneGateActivatedBlock(
                    frame.world().getBlockAt(cell.getX(), redstoneComponentY(layer, raPos, cell.getY()), cell.getZ()));
            }
        }


        gate.setGateSignPowered(hasDialSign);
        return gate;
    }

    /** Settles where the redstone markers go, once the gate itself is known. */
    private static void applyRedstoneWiring(final Stargate gate,
                                           final GateFrame frame,
                                           final Stargate3DShape shape)
    {
        // Prevent RD/RS from being side-by-side: if both assigned and adjacent,
        // prefer the dial activation (RD) and drop the sign-cycler (RS).
        if ((gate.getGateRedstoneDialActivationBlock() != null)
            && (gate.getGateRedstoneSignActivationBlock() != null)
            && WorldUtils.isAdjacent(gate.getGateRedstoneDialActivationBlock(), gate.getGateRedstoneSignActivationBlock()))
        {
            gate.setGateRedstoneSignActivationBlock(null);
        }

        // If the shape is redstone-enabled and defines a redstone-activated output (RA)
        // but does not explicitly define a redstone-dial activation block (RD),
        // choose a safe RD location in front of the RA block. Avoid placing RD on
        // the DHD/dial holder, the dial sign, the name holder, or any structure block.
        if ((gate.getGateRedstoneGateActivatedBlock() != null) && (gate.getGateRedstoneDialActivationBlock() == null)
            && (shape != null) && shape.isShapeRedstoneActivated())
        {
            try
            {
                final Block ra = gate.getGateRedstoneGateActivatedBlock();
                final Block dial = gate.getGateDialLeverBlock();
                final Block signBlock = gate.getGateDialSignBlock();
                final Block nameHolder = gate.getGateNameBlockHolder();

                final Block candidateFront = ra.getRelative(frame.facing());
                final Block candidateFrontUp = candidateFront.getRelative(BlockFace.UP);
                final Block candidateRight = candidateFront.getRelative(WorldUtils.getPerpendicularRightDirection(frame.facing()));
                final Block candidateRightUp = candidateRight.getRelative(BlockFace.UP);
                final Block candidateAboveRa = ra.getRelative(BlockFace.UP);

                final Block[] candidates = new Block[] { candidateFront, candidateFrontUp, candidateRight, candidateRightUp, candidateAboveRa };
                Block chosen = null;
                for (final Block c : candidates)
                {
                    if (c == null)
                    {
                        continue;
                    }
                    // Avoid colliding with player-placed activation holder or dial/sign/name holders
                    if ((dial != null) && WorldUtils.isSameBlock(c, dial))
                    {
                        continue;
                    }
                    if ((signBlock != null) && WorldUtils.isSameBlock(c, signBlock))
                    {
                        continue;
                    }
                    if ((nameHolder != null) && WorldUtils.isSameBlock(c, nameHolder))
                    {
                        continue;
                    }
                    // Avoid replacing any structure block
                    boolean collidesStructure = false;
                    for (final org.bukkit.Location loc : gate.getGateStructureBlocks())
                    {
                        if (loc == null)
                        {
                            continue;
                        }
                        if ((loc.getBlockX() == c.getX()) && (loc.getBlockY() == c.getY()) && (loc.getBlockZ() == c.getZ()))
                        {
                            collidesStructure = true;
                            break;
                        }
                    }
                    if (collidesStructure)
                    {
                        continue;
                    }
                    chosen = c;
                    break;
                }

                if (chosen == null)
                {
                    // Last-resort fallback: use block above RA (may overwrite non-structure blocks)
                    chosen = candidateAboveRa;
                }

                if (chosen != null)
                {
                    gate.setGateRedstoneDialActivationBlock(chosen);
                    gate.setGateRedstonePowered(true);
                }
            }
            catch (final Throwable ignore) { /* redstone wiring is optional; a gate without it still works */ }
        }

        // Instead of forcing placement of redstone dust in front of the activation
        // holder we record a small set of monitor blocks for redstone-enabled
        // shapes. The listener will treat power changes to these blocks as
        // activation triggers. Monitor targets are the blocks below the
        // activation holder (not the block the lever/button is attached to).
        try
        {
            gate.getGateRedstoneDialMonitorBlocks().clear();
            final Block dialHolder = gate.getGateDialLeverBlock();
            if (dialHolder != null)
            {
                // the block below the holder (where a player would place dust)
                final Block below = dialHolder.getRelative(BlockFace.DOWN);
                gate.getGateRedstoneDialMonitorBlocks().add(below);
                // also monitor the two blocks in front of that below-block
                final Block front = below.getRelative(frame.facing());
                gate.getGateRedstoneDialMonitorBlocks().add(front);
                final Block rightBlock = front.getRelative(WorldUtils.getPerpendicularRightDirection(frame.facing()));
                gate.getGateRedstoneDialMonitorBlocks().add(rightBlock);
            }
        }
        catch (final Throwable ignore) { /* monitor blocks are optional */ }

    }

}
