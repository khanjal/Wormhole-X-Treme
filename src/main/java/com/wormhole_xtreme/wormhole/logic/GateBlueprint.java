package com.wormhole_xtreme.wormhole.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeLayer;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * Every block a player places to build a shape, in world coordinates, without a world.
 *
 * <p>Only what the builder puts down: the frame, the chevrons, the DHD's button and a sign-dial
 * shape's sign. The name sign, iris lever and redstone come from the plugin when the gate is
 * completed. The opening is listed apart, since it is air until the gate is dialled.
 */
public final class GateBlueprint
{
    /** What a blueprint cell is. */
    public enum Part
    {
        /** A frame block, or a chevron that may be built from the frame material. */
        FRAME,
        /** A {@code [C]} cell, which must be the chevron material when the gate has one. */
        CHEVRON,
        /** The button on the DHD, in front of its activation block. */
        BUTTON,
        /** The dial sign, in front of its holder. */
        DIAL_SIGN,
        /** A cell of the opening, where the wormhole or the iris stands. */
        PORTAL
    }

    /**
     * One block of a blueprint.
     *
     * @param x
     *            world x
     * @param y
     *            world y
     * @param z
     *            world z
     * @param part
     *            what it is
     * @param wave
     *            the chevron wave that lights it, or 0 for a block that does not light
     * @param dhd
     *            whether it belongs to the DHD rather than the ring
     */
    public record Cell(int x, int y, int z, Part part, int wave, boolean dhd)
    {
        /** A cell of the ring. */
        public Cell(final int x, final int y, final int z, final Part part, final int wave)
        {
            this(x, y, z, part, wave, false);
        }
    }

    /** What one material of a gate is for, as {@code gate build -material} names it. */
    public enum Role
    {
        FRAME, CHEVRON, LIGHT, PORTAL, IRIS, SIGN;

        /** @return the word a player types for it */
        public String word()
        {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * @param word
         *            what was typed
         * @return the role, or null for any other word
         */
        public static Role named(final String word)
        {
            for (final Role role : values())
            {
                if (role.word().equalsIgnoreCase(word))
                {
                    return role;
                }
            }
            return null;
        }
    }

    /**
     * The materials a blueprint is drawn in.
     *
     * @param structure
     *            the frame
     * @param chevron
     *            the chevrons, or null where the gate has none and they are frame
     * @param light
     *            what a lit chevron shows when it has no lit form of its own
     * @param portal
     *            the open wormhole
     * @param iris
     *            the closed iris
     * @param sign
     *            the dial sign
     */
    public record Palette(Material structure, Material chevron, Material light, Material portal, Material iris,
        Material sign)
    {
        /**
         * The materials a gate of this shape and group is built from, resolved the way a
         * completed gate resolves them.
         *
         * @param shape
         *            the shape
         * @param group
         *            the material group, or null for the shape's own defaults
         * @return the palette
         */
        public static Palette of(final Stargate3DShape shape, final MaterialGroup group)
        {
            final Stargate gate = new Stargate();
            gate.setGateShape(shape);
            gate.setGateMaterialGroup(group);
            return new Palette(gate.getEffectiveStructureMaterial(), gate.getEffectiveChevronMaterial(),
                gate.getEffectiveLightMaterial(), gate.getEffectivePortalMaterial(), gate.getEffectiveIrisMaterial(),
                gate.getEffectiveSignMaterial());
        }

        /**
         * @param role
         *            which material
         * @param material
         *            what it becomes
         * @return this palette with that one material changed
         */
        public Palette with(final Role role, final Material material)
        {
            return switch (role)
            {
                case FRAME -> new Palette(material, chevron, light, portal, iris, sign);
                case CHEVRON -> new Palette(structure, material, light, portal, iris, sign);
                case LIGHT -> new Palette(structure, chevron, material, portal, iris, sign);
                case PORTAL -> new Palette(structure, chevron, light, material, iris, sign);
                case IRIS -> new Palette(structure, chevron, light, portal, material, sign);
                case SIGN -> new Palette(structure, chevron, light, portal, iris, material);
            };
        }

        /**
         * @param cell
         *            a blueprint cell
         * @return what that cell is built from; an opening cell shows the wormhole
         */
        public Material materialOf(final Cell cell)
        {
            return switch (cell.part())
            {
                case FRAME -> ((cell.wave() > 0) && (chevron != null)) ? chevron : structure;
                case CHEVRON -> (chevron != null) ? chevron : structure;
                case BUTTON -> Material.STONE_BUTTON;
                case DIAL_SIGN -> sign;
                case PORTAL -> portal;
            };
        }
    }

    /** Minecraft's yaw quarters, from 0: south, west, north, east. */
    private static final BlockFace[] YAW_FACES = { BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST };

    /** How far in front of the player the DHD, or any frame block nearer than it, stands. */
    private static final int CLEARANCE = 1;

    private GateBlueprint() {}

    /**
     * The cardinal direction a yaw is nearest.
     *
     * @param yaw
     *            a player's yaw, in degrees
     * @return north, south, east or west
     */
    public static BlockFace facingOf(final float yaw)
    {
        return YAW_FACES[Math.floorMod(Math.round(yaw / 90.0f), 4)];
    }

    /**
     * Where a shape stands when previewed in front of a player: its DHD in the block straight
     * ahead, its button facing them in the block they stand in, its bottom row level with their
     * feet, and no frame block nearer than the DHD's.
     *
     * @param shape
     *            the shape
     * @param feetX
     *            the block x the player stands in
     * @param feetY
     *            the block y the player stands in
     * @param feetZ
     *            the block z the player stands in
     * @param looking
     *            the cardinal direction the player looks
     * @return the grid, or null if the shape names no activation cell
     */
    public static GateGrid inFrontOf(final Stargate3DShape shape, final int feetX, final int feetY, final int feetZ,
        final BlockFace looking)
    {
        final List<StargateShapeLayer> layers = shape.getShapeLayers();
        final int act = shape.getShapeActivationLayer();
        if ((layers == null) || (act < 1) || (layers.size() <= act) || (layers.get(act) == null))
        {
            return null;
        }
        final int[] pos = layers.get(act).getLayerActivationPosition();
        if (pos.length < 3)
        {
            return null;
        }
        // Blocks in layers past the DHD's stand between it and the player, so it steps back for them.
        final int ahead = CLEARANCE + Math.max(0, lastBuiltLayer(layers) - act);
        return GateGrid.fromActivationHolder(shape, feetX + (ahead * looking.getModX()), feetY + pos[1],
            feetZ + (ahead * looking.getModZ()), WorldUtils.getInverseDirection(looking));
    }

    /**
     * Every block a builder places for a shape standing on a grid.
     *
     * <p>A frame block in a layer with no opening and no chevron in it is the DHD's, as are the
     * button and the dial sign. Where the DHD shares a layer with the ring, as a horizontal gate's
     * does, only the button and sign are counted, so hiding the DHD never opens a hole in the ring.
     *
     * @param shape
     *            the shape
     * @param grid
     *            where it stands
     * @return the cells, frame and chevrons layer by layer, then the button and dial sign
     */
    public static List<Cell> of(final Stargate3DShape shape, final GateGrid grid)
    {
        final List<Cell> cells = new ArrayList<>();
        final List<Cell> fronts = new ArrayList<>();
        final List<StargateShapeLayer> layers = shape.getShapeLayers();
        for (int layerIdx = 1; layerIdx < layers.size(); layerIdx++)
        {
            final StargateShapeLayer layer = layers.get(layerIdx);
            if (layer == null)
            {
                continue;
            }
            final Map<Long, Integer> waves = wavesOf(layer);
            final boolean dhdLayer = waves.isEmpty() && layer.getLayerPortalPositions().isEmpty();
            addAll(cells, grid, layerIdx, layer.getLayerBlockPositions(), Part.FRAME, waves, dhdLayer);
            addAll(cells, grid, layerIdx, layer.getLayerChevronPositions(), Part.CHEVRON, waves, dhdLayer);
            if (layerIdx == shape.getShapeActivationLayer())
            {
                addInFront(fronts, grid, layerIdx, layer.getLayerActivationPosition(), Part.BUTTON);
            }
            addInFront(fronts, grid, layerIdx, layer.getLayerDialSignPosition(), Part.DIAL_SIGN);
        }
        cells.addAll(fronts);
        return cells;
    }

    /**
     * The cells of a shape's opening, where the wormhole and the iris are drawn.
     *
     * @param shape
     *            the shape
     * @param grid
     *            where it stands
     * @return the opening's cells
     */
    public static List<Cell> openingOf(final Stargate3DShape shape, final GateGrid grid)
    {
        final List<Cell> cells = new ArrayList<>();
        final List<StargateShapeLayer> layers = shape.getShapeLayers();
        for (int layerIdx = 1; layerIdx < layers.size(); layerIdx++)
        {
            final StargateShapeLayer layer = layers.get(layerIdx);
            if (layer != null)
            {
                addAll(cells, grid, layerIdx, layer.getLayerPortalPositions(), Part.PORTAL, Map.of(), false);
            }
        }
        return cells;
    }

    /**
     * The cells the kawoosh reaches, each carrying the step of the woosh it shows at.
     *
     * @param shape
     *            the shape
     * @param grid
     *            where it stands
     * @return the woosh's cells, in step order within each layer
     */
    public static List<Cell> wooshOf(final Stargate3DShape shape, final GateGrid grid)
    {
        final List<Cell> cells = new ArrayList<>();
        final List<StargateShapeLayer> layers = shape.getShapeLayers();
        for (int layerIdx = 1; layerIdx < layers.size(); layerIdx++)
        {
            final StargateShapeLayer layer = layers.get(layerIdx);
            final List<List<Integer[]>> steps = (layer == null) ? null : layer.getLayerWooshPositions();
            if (steps == null)
            {
                continue;
            }
            for (int step = 1; step < steps.size(); step++)
            {
                if (steps.get(step) == null)
                {
                    continue;
                }
                for (final Integer[] pos : steps.get(step))
                {
                    cells.add(new Cell(grid.x(layerIdx, pos[2]), grid.y(pos[1]), grid.z(layerIdx, pos[2]), Part.PORTAL,
                        step, false));
                }
            }
        }
        return cells;
    }

    /** The highest layer with a frame or chevron block; the kawoosh may reach past it. */
    private static int lastBuiltLayer(final List<StargateShapeLayer> layers)
    {
        for (int layerIdx = layers.size() - 1; layerIdx > 0; layerIdx--)
        {
            final StargateShapeLayer layer = layers.get(layerIdx);
            if ((layer != null)
                && (!layer.getLayerBlockPositions().isEmpty() || !layer.getLayerChevronPositions().isEmpty()))
            {
                return layerIdx;
            }
        }
        return 0;
    }

    /** Which wave lights each cell of a layer, keyed as {@link StargateHelper#cellKey}. */
    private static Map<Long, Integer> wavesOf(final StargateShapeLayer layer)
    {
        final Map<Long, Integer> waves = new HashMap<>();
        final List<List<Integer[]>> lights = layer.getLayerLightPositions();
        if (lights == null)
        {
            return waves;
        }
        for (int wave = 1; wave < lights.size(); wave++)
        {
            if (lights.get(wave) == null)
            {
                continue;
            }
            for (final Integer[] pos : lights.get(wave))
            {
                waves.put(StargateHelper.cellKey(pos), wave);
            }
        }
        return waves;
    }

    private static void addAll(final List<Cell> cells, final GateGrid grid, final int layerIdx,
        final List<Integer[]> positions, final Part part, final Map<Long, Integer> waves, final boolean dhd)
    {
        for (final Integer[] pos : positions)
        {
            final int row = pos[1].intValue();
            final int col = pos[2].intValue();
            cells.add(new Cell(grid.x(layerIdx, col), grid.y(row), grid.z(layerIdx, col), part,
                waves.getOrDefault(StargateHelper.cellKey(pos), 0), dhd));
        }
    }

    private static void addInFront(final List<Cell> cells, final GateGrid grid, final int layerIdx,
        final int[] pos, final Part part)
    {
        if (pos.length < 3)
        {
            return;
        }
        final BlockFace facing = grid.facing();
        cells.add(new Cell(grid.x(layerIdx, pos[2]) + facing.getModX(), grid.y(pos[1]),
            grid.z(layerIdx, pos[2]) + facing.getModZ(), part, 0, true));
    }
}
