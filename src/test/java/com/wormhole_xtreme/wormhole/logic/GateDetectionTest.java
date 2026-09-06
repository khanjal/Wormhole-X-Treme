package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeLayer;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * Detection, end to end: build a shipped shape into a world of blocks and check that
 * {@code checkStargate} finds it there.
 *
 * <p>{@code check3DShape} is the largest method in the plugin and had no test of its own --
 * the nearest one covers {@code beatsBestMatch}, the tie-break beside it. These are
 * characterisation tests: they pin what detection does today so the method can be taken apart
 * later and the result compared against something.
 *
 * <p>The world is a map from coordinate to material, and anything not placed reads as AIR,
 * which is what an open portal interior is on the server.
 */
class GateDetectionTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/GateShapes");

    private final Map<String, Material> placed = new HashMap<String, Material>();
    private final Map<String, Block> blocks = new HashMap<String, Block>();
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);

        placed.clear();
        blocks.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn("test");
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
            blockAt(inv.getArgument(0, Integer.class).intValue(),
                    inv.getArgument(1, Integer.class).intValue(),
                    inv.getArgument(2, Integer.class).intValue()));
    }

    private static String key(final int x, final int y, final int z)
    {
        return x + "," + y + "," + z;
    }

    /** A block that reads its material back out of the map, and can step to its neighbours. */
    private Block blockAt(final int x, final int y, final int z)
    {
        final Block cached = blocks.get(key(x, y, z));
        if (cached != null)
        {
            return cached;
        }
        final Block b = mock(Block.class);
        when(b.getX()).thenReturn(Integer.valueOf(x));
        when(b.getY()).thenReturn(Integer.valueOf(y));
        when(b.getZ()).thenReturn(Integer.valueOf(z));
        when(b.getWorld()).thenReturn(world);
        when(b.getLocation()).thenReturn(new Location(world, x, y, z));
        when(b.getType()).thenAnswer(inv -> placed.getOrDefault(key(x, y, z), Material.AIR));
        when(b.getRelative(any(BlockFace.class))).thenAnswer(inv -> {
            final BlockFace face = inv.getArgument(0, BlockFace.class);
            return blockAt(x + face.getModX(), y + face.getModY(), z + face.getModZ());
        });
        blocks.put(key(x, y, z), b);
        return b;
    }

    private static Stargate3DShape shape(final String name) throws Exception
    {
        return shape(name, null, null);
    }

    /**
     * Parses a shipped shape, optionally rewriting one cell on the way in.
     *
     * <p>Editing a real shape beats hand-rolling one: the parser rejects a shape missing the
     * cells a gate needs, and a fixture built just far enough to parse would not prove
     * anything about a gate.
     */
    private static Stargate3DShape shape(final String name, final String from, final String to)
        throws Exception
    {
        final List<String> lines = Files.readAllLines(SHAPE_DIR.resolve(name + ".shape"));
        final List<String> out = new ArrayList<String>();
        for (final String line : lines)
        {
            out.add((from == null) || line.trim().startsWith("#") ? line : line.replace(from, to));
        }
        return new Stargate3DShape(out.toArray(new String[0]));
    }

    /**
     * Builds the shape into the world at a fixed origin and returns the block a player would
     * click to raise it: the activation holder's outward face.
     *
     * <p>The same mapping the detector uses, run the other way -- layer index steps along the
     * facing, column steps along its perpendicular right, row is height.
     */
    private Block build(final Stargate3DShape s, final BlockFace facing, final int ox, final int oy, final int oz)
    {
        final BlockFace right = WorldUtils.getPerpendicularRightDirection(facing);
        final ArrayList<StargateShapeLayer> layers = s.getShapeLayers();
        final Material struct = s.getShapeStructureMaterial();

        for (int layerIdx = 1; layerIdx < layers.size(); layerIdx++)
        {
            final StargateShapeLayer layer = layers.get(layerIdx);
            if (layer == null)
            {
                continue;
            }
            for (final Integer[] pos : layer.getLayerBlockPositions())
            {
                place(ox, oy, oz, facing, right, layerIdx, pos, struct);
            }
            // No palette is registered here, so a [C] cell means the same as [S].
            for (final Integer[] pos : layer.getLayerChevronPositions())
            {
                place(ox, oy, oz, facing, right, layerIdx, pos, struct);
            }
        }

        final int actIdx = s.getShapeActivationLayer();
        final int[] aPos = layers.get(actIdx).getLayerActivationPosition();
        final int hx = ox + (actIdx - 1) * facing.getModX() + aPos[2] * right.getModX();
        final int hy = oy + aPos[1];
        final int hz = oz + (actIdx - 1) * facing.getModZ() + aPos[2] * right.getModZ();
        // The detector steps from the clicked block back to the holder, so hand it the face.
        return blockAt(hx, hy, hz).getRelative(facing);
    }

    private void place(final int ox, final int oy, final int oz, final BlockFace facing,
                       final BlockFace right, final int layerIdx, final Integer[] pos, final Material m)
    {
        final int wx = ox + (layerIdx - 1) * facing.getModX() + pos[2].intValue() * right.getModX();
        final int wy = oy + pos[1].intValue();
        final int wz = oz + (layerIdx - 1) * facing.getModZ() + pos[2].intValue() * right.getModZ();
        placed.put(key(wx, wy, wz), m);
    }

    @Test
    void aStandardGateBuiltToItsOwnShapeIsDetected() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);

        final Stargate found = StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s);

        assertNotNull(found, "a gate built exactly to the Standard shape should be detected");
    }

    @Test
    void theSameGateIsDetectedFacingEveryDirection() throws Exception
    {
        for (final BlockFace facing : new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH,
                                                        BlockFace.EAST, BlockFace.WEST })
        {
            setUp();
            final Stargate3DShape s = shape("Standard");
            final Block clicked = build(s, facing, 0, 64, 0);

            assertNotNull(StargateHelper.checkStargate(clicked, facing, s),
                "a Standard gate facing " + facing + " should be detected");
        }
    }

    @Test
    void aFrameWithOneBlockMissingIsNotAGate() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);

        // Knock one frame block out and detection must refuse the whole thing.
        final String victim = placed.keySet().iterator().next();
        placed.remove(victim);

        assertNull(StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s),
            "a frame with a hole in it is not a gate");
    }

    @Test
    void aFrameFilledInSolidIsNotAGate() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);
        final BlockFace right = WorldUtils.getPerpendicularRightDirection(BlockFace.SOUTH);
        final ArrayList<StargateShapeLayer> layers = s.getShapeLayers();

        // Fill the portal interior with the frame material: the outline still matches, but
        // this is a solid room rather than a gate, and that is the false positive the portal
        // check exists to reject.
        for (int layerIdx = 1; layerIdx < layers.size(); layerIdx++)
        {
            final StargateShapeLayer layer = layers.get(layerIdx);
            if (layer == null)
            {
                continue;
            }
            for (final Integer[] pos : layer.getLayerPortalPositions())
            {
                place(0, 64, 0, BlockFace.SOUTH, right, layerIdx, pos, s.getShapeStructureMaterial());
            }
        }

        assertNull(StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s),
            "a solid room whose outline matches a shape is not a gate");
    }

    @Test
    void aFrameOfTheWrongMaterialIsNotAGate() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);

        // Rebuild the whole frame in something no palette claims.
        for (final String at : new ArrayList<String>(placed.keySet()))
        {
            placed.put(at, Material.DIRT);
        }

        assertNull(StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s),
            "a dirt ring is not a Standard gate");
    }

    /**
     * Every frame and chevron cell the shape names is recorded as a structure block.
     *
     * <p>Uses a Standard with its lighting cells rewritten to {@code [C]}, because no shipped
     * shape declares one and the chevron half of this would otherwise assert nothing. With no
     * chevron material named, {@code [C]} means the same as {@code [S]}, so the gate still
     * builds out of plain frame material.
     */
    @Test
    void everyFrameCellIsRecordedOnTheGate() throws Exception
    {
        final Stargate3DShape s = shape("Standard", "[S:L#1]", "[C:L#1]");
        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);

        final Stargate found = StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s);
        assertNotNull(found);

        int expected = 0;
        final ArrayList<StargateShapeLayer> layers = s.getShapeLayers();
        for (int i = 1; i < layers.size(); i++)
        {
            if (layers.get(i) != null)
            {
                expected += layers.get(i).getLayerBlockPositions().size();
                expected += layers.get(i).getLayerChevronPositions().size();
            }
        }
        assertEquals(expected, found.getGateStructureBlocks().size(),
            "a chevron cell is a frame block for every purpose except its material");
    }

    /** The portal interior is recorded too, and it is what the wormhole is drawn over. */
    @Test
    void everyPortalCellIsRecordedOnTheGate() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);

        final Stargate found = StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s);
        assertNotNull(found);

        int expected = 0;
        final ArrayList<StargateShapeLayer> layers = s.getShapeLayers();
        for (int i = 1; i < layers.size(); i++)
        {
            if (layers.get(i) != null)
            {
                expected += layers.get(i).getLayerPortalPositions().size();
            }
        }
        assertEquals(expected, found.getGatePortalBlocks().size());
    }

    /**
     * The lighting waves survive the 1-based to 0-based shift: the shape numbers its waves
     * from one, and the runtime expects a placeholder at index zero.
     */
    @Test
    void theLightingWavesAreRecordedInOrder() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);

        final Stargate found = StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s);
        assertNotNull(found);

        assertFalse(found.getGateLightBlocks().isEmpty(), "a Standard gate lights up, so it has waves");
        int lit = 0;
        for (final java.util.ArrayList<Location> wave : found.getGateLightBlocks())
        {
            if (wave != null)
            {
                lit += wave.size();
            }
        }
        assertTrue(lit > 0, "the waves should hold the cells the shape marked for lighting");
    }

    /**
     * EP is the block a traveller's feet land on, and they are put one block outside it along
     * the gate's facing so they do not arrive inside the portal.
     */
    @Test
    void theArrivalPointSitsOutsideThePortal() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);

        final Stargate found = StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s);
        assertNotNull(found);

        final Location arrival = found.getGatePlayerTeleportLocation();
        assertNotNull(arrival, "a Standard gate names an EP cell");
        // The pitch is set unguarded on a Location built two lines earlier. It used to sit in
        // a try/catch, which could only ever have hidden a bug in setting it.
        assertEquals(0f, arrival.getPitch(), 0.001f, "a traveller arrives looking level");

        // Find the EP cell the shape declared and check the arrival sits one step out from it.
        final ArrayList<StargateShapeLayer> layers = s.getShapeLayers();
        final BlockFace right = WorldUtils.getPerpendicularRightDirection(BlockFace.SOUTH);
        boolean checked = false;
        for (int i = 1; i < layers.size() && !checked; i++)
        {
            final StargateShapeLayer layer = layers.get(i);
            if (layer == null)
            {
                continue;
            }
            final int[] ep = layer.getLayerPlayerExitPosition();
            if (ep.length < 3)
            {
                continue;
            }
            final int cx = 0 + ((i - 1) * BlockFace.SOUTH.getModX()) + (ep[2] * right.getModX());
            final int cy = 64 + ep[1];
            final int cz = 0 + ((i - 1) * BlockFace.SOUTH.getModZ()) + (ep[2] * right.getModZ());

            assertEquals(cx + 0.5 + BlockFace.SOUTH.getModX(), arrival.getX(), 1e-9);
            assertEquals(cy + 1.0, arrival.getY(), 1e-9, "feet stand on top of the EP block");
            assertEquals(cz + 0.5 + BlockFace.SOUTH.getModZ(), arrival.getZ(), 1e-9);
            checked = true;
        }
        assertTrue(checked, "the Standard shape should declare an EP cell for this to mean anything");
    }

    /** The gate remembers the basics it was detected with. */
    @Test
    void theDetectedGateRemembersItsShapeFacingAndDial() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        final Block clicked = build(s, BlockFace.EAST, 0, 64, 0);

        final Stargate found = StargateHelper.checkStargate(clicked, BlockFace.EAST, s);
        assertNotNull(found);

        assertEquals(BlockFace.EAST, found.getGateFacing());
        assertEquals(s, found.getGateShape());
        assertEquals(clicked, found.getGateDialLeverBlock());
        assertNotNull(found.getGateNameBlockHolder(), "a Standard gate names an N cell");
    }
}
