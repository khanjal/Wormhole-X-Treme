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

    private final Map<String, Material> placed = new HashMap<>();
    private final Map<String, Block> blocks = new HashMap<>();
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
        final List<String> out = new ArrayList<>();
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
        final List<StargateShapeLayer> layers = s.getShapeLayers();
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
        final List<StargateShapeLayer> layers = s.getShapeLayers();

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
        for (final String at : new ArrayList<>(placed.keySet()))
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
        final List<StargateShapeLayer> layers = s.getShapeLayers();
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
        final List<StargateShapeLayer> layers = s.getShapeLayers();
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
        for (final java.util.List<Location> wave : found.getGateLightBlocks())
        {
            if (wave != null)
            {
                lit += wave.size();
            }
        }
        assertTrue(lit > 0, "the waves should hold the cells the shape marked for lighting");

        // The shift itself, which the assertions above do not reach: Standard numbers its
        // light waves L#1..L#7, and the runtime steps its counter to 1 before reading a
        // wave. Index 0 has to stay empty or wave 1 lands where nothing ever looks, and the
        // first chevron never lights.
        assertNull(found.getGateLightBlocks().get(0),
            "index 0 is the placeholder the lighting counter steps past");
        assertNotNull(found.getGateLightBlocks().get(1),
            "L#1 is recorded at index 1, not shifted down to 0");
        assertEquals(8, found.getGateLightBlocks().size(),
            "seven waves plus the placeholder");

        // Woosh waves are the other way round, and deliberately: nothing steps past a
        // placeholder there, so W#1 becomes index 0.
        assertNotNull(found.getGateWooshBlocks().get(0),
            "W#1 is recorded at index 0");
        assertEquals(3, found.getGateWooshBlocks().size(), "three woosh waves, no placeholder");
    }


    /**
     * A redstone-dialled gate gets blocks to watch for a power change.
     *
     * <p>Nothing places redstone dust for the player. The gate records the block below its
     * DHD holder and the two in front of that, and the listener treats power arriving at any
     * of them as a dial. A gate with none recorded cannot be dialled by redstone at all,
     * which is the whole point of the shape.
     */
    @Test
    void aRedstoneShapeRecordsBlocksToWatch() throws Exception
    {
        final Stargate3DShape s = shape("StandardSignDial");
        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);

        final Stargate found = StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s);
        assertNotNull(found);

        assertEquals(3, found.getGateRedstoneDialMonitorBlocks().size(),
            "the block below the holder, and the two in front of it");
        final Block holder = found.getGateDialLeverBlock();
        assertNotNull(holder, "the monitors are worked out from the holder");
        for (final Block m : found.getGateRedstoneDialMonitorBlocks())
        {
            assertTrue(m.getY() < holder.getY(),
                "every monitor sits below the holder, where a player would run dust");
        }
    }

    /**
     * The arrival steps out along every axis the facing has, not just one.
     *
     * <p>{@link #theArrivalPointSitsOutsideThePortal} builds facing SOUTH, whose x component
     * is zero -- so dropping the x term from the arrival changes nothing there and the test
     * passes anyway. Facing EAST is the other way round. Between them the two cover both
     * terms; either alone covers one and looks like it covers both.
     */
    @Test
    void theArrivalStepsOutAlongTheFacingsOtherAxis() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        final Block clicked = build(s, BlockFace.EAST, 0, 64, 0);

        final Stargate found = StargateHelper.checkStargate(clicked, BlockFace.EAST, s);
        assertNotNull(found);

        final Location arrival = found.getGatePlayerTeleportLocation();
        assertNotNull(arrival);
        // EAST is +X, so the arrival must sit a whole block further along x than the cell it
        // was derived from -- the half-block centring alone would leave it inside the portal.
        assertEquals(0.5, arrival.getX() - Math.floor(arrival.getX()), 0.001,
            "still centred in its block: " + arrival);
        assertTrue(arrival.getX() >= 1.0,
            "an EAST gate's arrival steps out along x, not only z: " + arrival);
    }

    /**
     * A minecart arrives half a block up, in the middle of its block.
     *
     * <p>Sunk to the block floor it spawns inside the ground and is pushed out somewhere the
     * gate did not choose. Nothing covered this at all.
     */
    @Test
    void aMinecartArrivesCentredInItsBlock() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);

        final Stargate found = StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s);
        assertNotNull(found);

        final Location cart = found.getGateMinecartTeleportLocation();
        assertNotNull(cart, "a Standard gate names an EM cell");
        // Fractional part via floor, not %: Java keeps the dividend's sign, so a gate built
        // at a negative coordinate would give -0.5 for a block that is centred just fine.
        assertEquals(0.5, cart.getX() - Math.floor(cart.getX()), 0.001, "centred on x: " + cart);
        assertEquals(0.5, cart.getY() - Math.floor(cart.getY()), 0.001, "and lifted half a block: " + cart);
        assertEquals(0.5, cart.getZ() - Math.floor(cart.getZ()), 0.001, "and centred on z: " + cart);
    }

    /**
     * The iris lever hangs on the gate's face, not inside the frame block.
     *
     * <p>Recorded on the block the shape names, the lever would be buried in the frame where
     * nobody can click it.
     */
    @Test
    void theIrisLeverSitsOnTheGatesFace() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);

        final Stargate found = StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s);
        assertNotNull(found);

        final Block lever = found.getGateIrisLeverBlock();
        assertNotNull(lever, "a Standard gate names an IA cell");
        // SOUTH is +Z, so the lever is one step further along z than any frame cell there.
        assertFalse(placed.containsKey(key(lever.getX(), lever.getY(), lever.getZ())),
            "the lever is on the face, not in a frame block: " + lever.getX() + "," + lever.getY()
                + "," + lever.getZ());
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
        final List<StargateShapeLayer> layers = s.getShapeLayers();
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

    /**
     * A chevron built from the chevron material is still a gate.
     *
     * <p>An {@code [S:L#n]} cell is a frame block that happens to sit in a lighting wave, and
     * a shape naming a CHEVRON_MATERIAL lets one be built from that instead -- so the gate
     * shows where its chevrons are before any of them light.
     *
     * <p>UnlitChevronTest describes this and tests the shape side of it. This is the
     * detection side: without the tolerance, a gate built the way the feature intends is not
     * recognised as a gate at all.
     */
    @Test
    void aChevronBuiltFromTheChevronMaterialIsStillFound() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        s.setShapeChevronMaterial(Material.GLOWSTONE);

        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);
        placeLitCellsAs(s, BlockFace.SOUTH, 0, 64, 0, Material.GLOWSTONE);

        assertNotNull(StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s),
            "a chevron in the chevron material belongs there");
    }

    /**
     * And a chevron built from the frame material is still a gate too.
     *
     * <p>Every gate standing in every world today was built that way, so re-detection has to
     * go on finding them. Both materials are accepted, not one or the other.
     */
    @Test
    void aChevronBuiltFromTheFrameMaterialIsStillFound() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        s.setShapeChevronMaterial(Material.GLOWSTONE);

        // build() puts the frame material everywhere, chevron positions included.
        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);

        assertNotNull(StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s),
            "an obsidian chevron is what every existing gate has");
    }


    /** Puts the given material in every cell the shape marks with a light order. */
    private void placeLitCellsAs(final Stargate3DShape s, final BlockFace facing,
                                 final int ox, final int oy, final int oz, final Material material)
    {
        final BlockFace right = WorldUtils.getPerpendicularRightDirection(facing);
        final List<StargateShapeLayer> layers = s.getShapeLayers();
        for (int layerIdx = 1; layerIdx < layers.size(); layerIdx++)
        {
            final StargateShapeLayer layer = layers.get(layerIdx);
            if (layer == null)
            {
                continue;
            }
            final List<List<Integer[]>> waves = layer.getLayerLightPositions();
            if (waves == null)
            {
                continue;
            }
            for (int waveIdx = 1; waveIdx < waves.size(); waveIdx++)
            {
                if (waves.get(waveIdx) == null)
                {
                    continue;
                }
                for (final Integer[] pos : waves.get(waveIdx))
                {
                    place(ox, oy, oz, facing, right, layerIdx, pos, material);
                }
            }
        }
    }

    /**
     * The chevron material is only accepted where a chevron belongs.
     *
     * <p>Accepting it anywhere in the frame would mean a gate with a stray glowstone block
     * in its wall still detected, and the whole point of the marker is that the shape says
     * which cells are chevrons.
     *
     * <p>It asserts the gate is found first, so the refusal afterwards is about the block
     * that changed rather than a setup that never detected anything.
     */
    @Test
    void theChevronMaterialIsNotAcceptedJustAnywhereInTheFrame() throws Exception
    {
        final Stargate3DShape s = shape("Standard");
        s.setShapeChevronMaterial(Material.GLOWSTONE);

        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);
        assertNotNull(StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s),
            "found before anything is spoiled, so the refusal below is about the glowstone");
        assertTrue(placeChevronMaterialAtAnUnlitFrameCell(s, BlockFace.SOUTH, 0, 64, 0),
            "the shape needs at least one frame cell that carries no light order");

        assertNull(StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s),
            "glowstone where the shape marked plain frame is not a chevron");
    }

    /**
     * Puts the chevron material in one frame cell the shape did not mark with a light order.
     *
     * @return true if such a cell was found
     */
    private boolean placeChevronMaterialAtAnUnlitFrameCell(final Stargate3DShape s,
                                                           final BlockFace facing,
                                                           final int ox, final int oy, final int oz)
    {
        final BlockFace right = WorldUtils.getPerpendicularRightDirection(facing);
        final List<StargateShapeLayer> layers = s.getShapeLayers();
        for (int layerIdx = 1; layerIdx < layers.size(); layerIdx++)
        {
            final StargateShapeLayer layer = layers.get(layerIdx);
            if (layer == null)
            {
                continue;
            }
            final java.util.Set<String> lit = new java.util.HashSet<String>();
            final List<List<Integer[]>> waves = layer.getLayerLightPositions();
            if (waves != null)
            {
                for (int w = 1; w < waves.size(); w++)
                {
                    if (waves.get(w) != null)
                    {
                        for (final Integer[] pos : waves.get(w))
                        {
                            lit.add(pos[0] + "," + pos[1] + "," + pos[2]);
                        }
                    }
                }
            }
            for (final Integer[] pos : layer.getLayerBlockPositions())
            {
                if (!lit.contains(pos[0] + "," + pos[1] + "," + pos[2]))
                {
                    place(ox, oy, oz, facing, right, layerIdx, pos, Material.GLOWSTONE);
                    return true;
                }
            }
        }
        return false;
    }
}
