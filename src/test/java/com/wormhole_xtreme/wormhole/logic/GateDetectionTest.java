package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        final List<String> lines = Files.readAllLines(SHAPE_DIR.resolve(name + ".shape"));
        return new Stargate3DShape(lines.toArray(new String[0]));
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
}
