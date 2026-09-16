package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Palette;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;

/**
 * Where the build preview puts a shape's blocks.
 *
 * <p>A preview is only worth following if a gate built to it is detected. The blueprint and
 * detection share {@link GateGrid}, but the blueprint also chooses which cells to show and where
 * the button goes, so every shipped shape is built from its blueprint here and handed to
 * {@link StargateHelper#checkStargate}.
 */
class GateBlueprintTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/shapes/gate");

    private static final BlockFace[] LOOKS = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };

    private final Map<String, Material> placed = new HashMap<>();
    private final Map<String, Block> blocks = new HashMap<>();
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        world = mock(World.class);
        when(world.getName()).thenReturn("test");
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> blockAt(
            inv.getArgument(0, Integer.class), inv.getArgument(1, Integer.class), inv.getArgument(2, Integer.class)));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private static String key(final int x, final int y, final int z)
    {
        return x + "," + y + "," + z;
    }

    private Block blockAt(final int x, final int y, final int z)
    {
        return blocks.computeIfAbsent(key(x, y, z), k ->
        {
            final Block b = mock(Block.class);
            when(b.getX()).thenReturn(x);
            when(b.getY()).thenReturn(y);
            when(b.getZ()).thenReturn(z);
            when(b.getWorld()).thenReturn(world);
            when(b.getLocation()).thenReturn(new Location(world, x, y, z));
            when(b.getType()).thenAnswer(inv -> placed.getOrDefault(k, Material.AIR));
            when(b.getRelative(any(BlockFace.class))).thenAnswer(inv ->
            {
                final BlockFace face = inv.getArgument(0, BlockFace.class);
                return blockAt(x + face.getModX(), y + face.getModY(), z + face.getModZ());
            });
            return b;
        });
    }

    private static Stargate3DShape shape(final String name) throws Exception
    {
        return new Stargate3DShape(Files.readAllLines(SHAPE_DIR.resolve(name + ".shape")).toArray(new String[0]));
    }

    private static List<String> shippedShapes() throws Exception
    {
        try (Stream<Path> files = Files.list(SHAPE_DIR))
        {
            return files.map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".shape"))
                .map(n -> n.substring(0, n.length() - ".shape".length()))
                .sorted()
                .toList();
        }
    }

    private static Cell only(final List<Cell> cells, final Part part)
    {
        final List<Cell> found = cells.stream().filter(c -> c.part() == part).toList();
        assertEquals(1, found.size(), "one " + part + " in " + cells.size() + " cells");
        return found.get(0);
    }

    /**
     * Every shipped shape, built exactly to its preview facing each way, is a gate.
     *
     * <p>A preview drawn one column over, a layer back or with its button on the wrong face would
     * look right and lead a builder to a frame nothing ever detects.
     */
    @Test
    void everyShippedShapeBuiltToItsPreviewIsDetected() throws Exception
    {
        final List<String> names = shippedShapes();
        assertEquals(9, names.size(), "the shapes read: " + names);
        for (final String name : names)
        {
            for (final BlockFace looking : LOOKS)
            {
                placed.clear();
                blocks.clear();
                final Stargate3DShape s = shape(name);
                final GateGrid grid = GateBlueprint.inFrontOf(s, 10, 64, -20, looking);
                final List<Cell> cells = GateBlueprint.of(s, grid);
                final Palette palette = Palette.of(s, null);
                for (final Cell cell : cells)
                {
                    if ((cell.part() == Part.FRAME) || (cell.part() == Part.CHEVRON))
                    {
                        placed.put(key(cell.x(), cell.y(), cell.z()), palette.materialOf(cell));
                    }
                }
                final Cell button = only(cells, Part.BUTTON);

                assertNotNull(StargateHelper.checkStargate(blockAt(button.x(), button.y(), button.z()),
                    grid.facing(), s), name + " built looking " + looking + " should be a gate");
            }
        }
    }

    /** And a blueprint missing any one of its frame blocks is not, so the test above can fail. */
    @Test
    void theSameFrameWithOneBlockLeftOutIsNotAGate() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        final GateGrid grid = GateBlueprint.inFrontOf(s, 0, 64, 0, BlockFace.NORTH);
        final List<Cell> cells = GateBlueprint.of(s, grid);
        cells.stream().filter(c -> c.part() == Part.FRAME).skip(1)
            .forEach(c -> placed.put(key(c.x(), c.y(), c.z()), Material.OBSIDIAN));
        final Cell button = only(cells, Part.BUTTON);

        assertNull(StargateHelper.checkStargate(blockAt(button.x(), button.y(), button.z()), grid.facing(), s));
    }

    /**
     * The preview stands in front of the player, with its button straight ahead and facing them.
     *
     * <p>The DHD in the very next block, as asked for in testing, with its button where the
     * player stands; its bottom row level with their feet, so it is built on the ground they
     * stand on. Massive's kawoosh reaches six layers past its DHD, and is not counted.
     */
    @Test
    void thePreviewStandsInFrontOfThePlayerWithItsButtonFacingThem() throws Exception
    {
        for (final String name : shippedShapes())
        {
            for (final BlockFace looking : LOOKS)
            {
                final Stargate3DShape s = shape(name);
                final GateGrid grid = GateBlueprint.inFrontOf(s, 10, 64, -20, looking);
                final List<Cell> cells = GateBlueprint.of(s, grid);
                final Cell button = only(cells, Part.BUTTON);
                final String where = name + " looking " + looking;

                assertEquals(looking.getOppositeFace(), grid.facing(), where + ": the button faces the player");
                assertEquals(0, ((button.x() - 10) * looking.getModZ()) - ((button.z() + 20) * looking.getModX()),
                    where + ": the button is straight ahead");
                assertEquals(0, ((button.x() - 10) * looking.getModX()) + ((button.z() + 20) * looking.getModZ()),
                    where + ": the button is in the block the player stands in");
                final int nearest = cells.stream().filter(c -> (c.part() != Part.BUTTON) && (c.part() != Part.DIAL_SIGN))
                    .mapToInt(c -> ((c.x() - 10) * looking.getModX()) + ((c.z() + 20) * looking.getModZ()))
                    .min().orElseThrow();
                assertEquals(1, nearest, where + ": the DHD in the block in front");
                assertEquals(64, cells.stream().mapToInt(Cell::y).min().orElseThrow(),
                    where + ": the bottom row is level with the player's feet");
            }
        }
    }

    /**
     * A shape with frame blocks nearer the player than its DHD stands back far enough to put
     * those in the block in front instead.
     *
     * <p>No shipped shape has one: each DHD is in its nearest built layer. So Standard is edited
     * here to hang its DHD two layers back.
     */
    @Test
    void aShapeWithFrameNearerThanItsDhdStandsFurtherBack() throws Exception
    {
        final List<String> lines = new java.util.ArrayList<>(Files.readAllLines(SHAPE_DIR.resolve("Standard.shape")));
        final int layer2 = lines.indexOf("Layer#2=");
        // The marker taken off layer 4, and put on the same cell of layer 2.
        lines.replaceAll(line -> line.startsWith("#") ? line : line.replace("[S:A]", "[S]"));
        lines.set(layer2 + 6, "[I][I][I:W#1][I:W#1][I:W#1][S:A][I]");
        final Stargate3DShape s = new Stargate3DShape(lines.toArray(new String[0]));
        assertEquals(2, s.getShapeActivationLayer(), "the edit moved the DHD");

        final List<Cell> cells = GateBlueprint.of(s, GateBlueprint.inFrontOf(s, 0, 64, 0, BlockFace.SOUTH));

        assertEquals(1, cells.stream().mapToInt(Cell::z).min().orElseThrow(),
            "layer 4's frame blocks are the nearest, in the block in front");
    }

    /** Each chevron carries the wave that lights it, so a test activation can light them in order. */
    @Test
    void theChevronsCarryTheWaveThatLightsThem() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        final List<Cell> cells = GateBlueprint.of(s, GateBlueprint.inFrontOf(s, 0, 64, 0, BlockFace.NORTH));

        final Set<Integer> waves = cells.stream().map(Cell::wave).filter(w -> w > 0)
            .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7), waves);
        assertEquals(7, cells.stream().filter(c -> c.wave() > 0).count(), "one block a wave in Standard");
    }

    /** A sign-dial shape shows its sign in front of the holder; a button-dial shape has none. */
    @Test
    void aSignDialShapeShowsItsSignAndAButtonShapeDoesNot() throws Exception
    {
        final Stargate3DShape dial = shape("StandardSignDial");
        final GateGrid grid = GateBlueprint.inFrontOf(dial, 0, 64, 0, BlockFace.WEST);
        final Cell sign = only(GateBlueprint.of(dial, grid), Part.DIAL_SIGN);

        final int[] holder = dial.getShapeLayers().get(4).getLayerDialSignPosition();
        assertEquals(grid.x(4, holder[2]) + grid.facing().getModX(), sign.x());
        assertEquals(grid.z(4, holder[2]) + grid.facing().getModZ(), sign.z());

        final Stargate3DShape standard = shape("Standard");
        assertTrue(GateBlueprint.of(standard, GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.WEST)).stream()
            .noneMatch(c -> c.part() == Part.DIAL_SIGN));
    }

    /** A lit frame cell is drawn in the chevron material where the gate has one, and frame where it has none. */
    @Test
    void aChevronIsDrawnInTheChevronMaterialOnlyWhereThereIsOne()
    {
        final Cell lit = new Cell(0, 0, 0, Part.FRAME, 3);
        final Cell plain = new Cell(0, 0, 0, Part.FRAME, 0);
        final Palette withChevrons = new Palette(Material.OBSIDIAN, Material.REDSTONE_LAMP, Material.OAK_WALL_SIGN);
        final Palette without = new Palette(Material.OBSIDIAN, null, Material.OAK_WALL_SIGN);

        assertEquals(Material.REDSTONE_LAMP, withChevrons.materialOf(lit));
        assertEquals(Material.OBSIDIAN, withChevrons.materialOf(plain));
        assertEquals(Material.OBSIDIAN, without.materialOf(lit));
        assertEquals(Material.OBSIDIAN, without.materialOf(new Cell(0, 0, 0, Part.CHEVRON, 0)));
    }

    /** The nearest cardinal direction to a yaw, as Minecraft counts it: 0 is south, 90 west. */
    @Test
    void aYawIsReadAsTheNearestCardinalDirection()
    {
        assertEquals(BlockFace.SOUTH, GateBlueprint.facingOf(0f));
        assertEquals(BlockFace.SOUTH, GateBlueprint.facingOf(44f));
        assertEquals(BlockFace.WEST, GateBlueprint.facingOf(46f));
        assertEquals(BlockFace.NORTH, GateBlueprint.facingOf(180f));
        assertEquals(BlockFace.NORTH, GateBlueprint.facingOf(-180f));
        assertEquals(BlockFace.EAST, GateBlueprint.facingOf(-90f));
        assertEquals(BlockFace.EAST, GateBlueprint.facingOf(270f));
    }
}
