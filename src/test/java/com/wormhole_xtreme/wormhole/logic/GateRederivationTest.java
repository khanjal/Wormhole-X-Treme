package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateShapeLayer;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * Re-reading a gate's shape after the shape file has changed underneath it.
 *
 * <p>The bug this exists for: #28 added {@code [RD]} and {@code [RA]} markers to shipped sign
 * shapes, and every gate already standing kept the empty marker positions it was detected
 * with. Such a gate has no redstone dial block recorded, {@code setupRedstone} places blocks
 * only at positions that are already known, and {@code /wormhole regenerate} guards that call
 * with {@code isGateRedstonePowered()} -- which is false on exactly the gates that need
 * fixing. So no amount of wiring would ever fire them, and the command an admin reaches for
 * did nothing about it.
 *
 * <p>The world here is the one {@code GateDetectionTest} uses: a map from coordinate to
 * material, where anything not placed reads as AIR. Detection is run for real against a
 * shipped shape rather than stubbed, because the whole point is that re-derivation and
 * detection agree.
 */
class GateRederivationTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/shapes/gate");

    private final Map<String, Material> placed = new HashMap<>();
    private final Map<String, Block> blocks = new HashMap<>();
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));

        placed.clear();
        blocks.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn("test");
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
            blockAt(inv.getArgument(0, Integer.class).intValue(),
                    inv.getArgument(1, Integer.class).intValue(),
                    inv.getArgument(2, Integer.class).intValue()));
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
        org.mockito.Mockito.doAnswer(inv -> placed.put(key(x, y, z), inv.getArgument(0, Material.class)))
            .when(b).setType(any(Material.class), org.mockito.ArgumentMatchers.anyBoolean());
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
     * Builds the shape into the world and returns the block a player would click.
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

    /** Detects a gate of the named shape, standing at a fixed origin. */
    private Stargate detected(final String shapeName) throws Exception
    {
        final Stargate3DShape s = shape(shapeName);
        final Block clicked = build(s, BlockFace.SOUTH, 0, 64, 0);
        final Stargate gate = StargateHelper.checkStargate(clicked, BlockFace.SOUTH, s);
        assertNotNull(gate, shapeName + " should be detected from the blocks just placed");
        return gate;
    }

    /**
     * A gate built before its shape gained redstone markers gets them back.
     *
     * <p>This is #42's motivating case, staged the way it really happened: a gate detected
     * from a shape that had no {@code [RD]} or {@code [RA]}, then the shape file gains them.
     * The gate's own stored positions are what a pre-#28 gate has -- nothing -- and the whole
     * failure is that nothing ever re-reads the file to notice.
     */
    @Test
    void aGateBuiltBeforeItsShapeGainedRedstoneMarkersGetsThemBack() throws Exception
    {
        final Stargate gate = detected("MinimalSignDial");
        // Wind the gate back to what detection against the pre-#28 shape would have left.
        gate.setGateRedstoneDialActivationBlock(null);
        gate.setGateRedstoneGateActivatedBlock(null);
        gate.setGateRedstonePowered(false);

        final GateRederivation.Outcome outcome = GateRederivation.rederive(gate);

        assertEquals(GateRederivation.Result.REDERIVED, outcome.result());
        assertNotNull(gate.getGateRedstoneDialActivationBlock(),
            "without a dial input recorded, no redstone signal can ever fire this gate --"
                + " which is the bug, not a cosmetic gap");
        assertNotNull(gate.getGateRedstoneGateActivatedBlock(),
            "the gate-activated output is what a player wires their door to");
        assertTrue(gate.isGateRedstonePowered(),
            "regenerate guards its setupRedstone call with this flag, so a gate that has just"
                + " gained a dial input has to come back with it set or nothing gets placed");
        assertTrue(outcome.changes().contains("redstone dial input"),
            "the admin who ran the command should be told what moved; changes were: "
                + outcome.changes());
    }

    /**
     * Re-derivation puts the markers exactly where a fresh detection would.
     *
     * <p>The looser assertion above says a marker came back. This one says it came back in the
     * right place -- the point of re-running detection rather than guessing at an offset.
     */
    @Test
    void theMarkersComeBackWhereDetectionWouldHavePutThem() throws Exception
    {
        final Stargate reference = detected("MinimalSignDial");
        final Block expectedDial = reference.getGateRedstoneDialActivationBlock();
        final Block expectedOutput = reference.getGateRedstoneGateActivatedBlock();
        assertNotNull(expectedDial, "MinimalSignDial declares [RD], so detection must record one");

        reference.setGateRedstoneDialActivationBlock(null);
        reference.setGateRedstoneGateActivatedBlock(null);

        GateRederivation.rederive(reference);

        assertSame(expectedDial, reference.getGateRedstoneDialActivationBlock(),
            "the same world coordinate should resolve to the same block");
        assertSame(expectedOutput, reference.getGateRedstoneGateActivatedBlock());
    }

    /**
     * A gate that no longer matches its shape is left exactly as it was.
     *
     * <p>Re-derivation runs unattended from a command rather than from a player standing at
     * the gate, so the one thing it must never do is half-rewrite a gate whose frame somebody
     * has taken apart. Detection returning nothing has to mean nothing was touched.
     */
    @Test
    void aGateThatNoLongerMatchesItsShapeIsLeftAlone() throws Exception
    {
        final Stargate gate = detected("MinimalSignDial");
        gate.setGateRedstoneDialActivationBlock(null);
        // Knock a frame block out from under it, the way a WorldEdit //replace would.
        placed.remove(placed.keySet().iterator().next());

        final GateRederivation.Outcome outcome = GateRederivation.rederive(gate);

        assertEquals(GateRederivation.Result.NOT_DETECTED, outcome.result());
        assertNull(gate.getGateRedstoneDialActivationBlock(),
            "a gate whose frame no longer matches must come out of this untouched, not"
                + " partly rewritten");
        assertTrue(outcome.changes().isEmpty());
    }

    /**
     * A shape that declares no marker does not take away one the gate already has.
     *
     * <p>Re-derivation adds and moves; it never clears. The block is still standing in the
     * world, and quietly forgetting about it would leave redstone wire nobody can account for
     * and no message saying why. A shape that has lost a marker is a decision for whoever is
     * reading the report, not something to do silently on their behalf.
     */
    @Test
    void aShapeDeclaringNoMarkerDoesNotTakeAwayOneTheGateHas() throws Exception
    {
        // Standard has no [RD] at all, so a fresh detection finds nothing to record.
        final Stargate gate = detected("Standard");
        final Block wiredByHand = blockAt(5, 70, 5);
        gate.setGateRedstoneDialActivationBlock(wiredByHand);

        final GateRederivation.Outcome outcome = GateRederivation.rederive(gate);

        assertEquals(GateRederivation.Result.REDERIVED, outcome.result());
        assertSame(wiredByHand, gate.getGateRedstoneDialActivationBlock(),
            "a marker the shape does not declare is left where it is, not cleared");
    }

    /**
     * An iris lever the gate has lost is put back where the shape declares it.
     *
     * <p>Not hypothetical: 1.5.0 fixed three shipped sign shapes that carried no {@code :IA}
     * at all, so every gate built from one of them before that has no iris lever recorded and
     * cannot have an iris placed. Re-reading the shape is what reaches those gates.
     */
    @Test
    void anIrisLeverTheGateHasLostIsPutBack() throws Exception
    {
        final Stargate gate = detected("MinimalSignDial");
        assertNotNull(gate.getGateIrisLeverBlock(), "MinimalSignDial declares :IA");
        gate.setGateIrisLeverBlock(null);

        final GateRederivation.Outcome outcome = GateRederivation.rederive(gate);

        assertTrue(outcome.changes().contains("iris lever"),
            "changes were: " + outcome.changes());
        assertNotNull(gate.getGateIrisLeverBlock());
    }

    /**
     * The name sign holder is re-derived too.
     *
     * <p>{@code Standard} declares {@code :N} and {@code MinimalSignDial} does not, which is
     * why this one uses the other shape -- and is a reminder that these markers vary per
     * shape rather than being a fixed set every gate has.
     */
    @Test
    void aNameSignHolderIsReDerivedFromTheShape() throws Exception
    {
        final Stargate gate = detected("Standard");
        assertNotNull(gate.getGateNameBlockHolder(), "Standard declares :N");
        gate.setGateNameBlockHolder(null);

        final GateRederivation.Outcome outcome = GateRederivation.rederive(gate);

        assertTrue(outcome.changes().contains("name sign"),
            "changes were: " + outcome.changes());
        assertNotNull(gate.getGateNameBlockHolder());
    }

    /**
     * A marker held as a different World object of the same name has not moved.
     *
     * <p>This is why the comparison goes by world name rather than by reference. A gate
     * reloaded from disk holds blocks resolved through whatever {@code World} the server
     * handed the loader, and detection reads fresh ones out of the world now; comparing the
     * objects would call every marker on every loaded gate "moved", so every regenerate would
     * rewrite and re-save a gate that was perfectly correct.
     */
    @Test
    void aMarkerHeldAsADifferentWorldObjectOfTheSameNameHasNotMoved() throws Exception
    {
        final Stargate gate = detected("MinimalSignDial");
        final Block derived = gate.getGateIrisLeverBlock();
        assertNotNull(derived, "MinimalSignDial declares :IA");

        // Read the coordinates out first. Calling derived.getX() inside thenReturn() would
        // invoke one mock while the stubbing of another is still open, which Mockito refuses
        // as unfinished stubbing rather than quietly getting it wrong.
        final int x = derived.getX();
        final int y = derived.getY();
        final int z = derived.getZ();

        final World reloaded = mock(World.class);
        when(reloaded.getName()).thenReturn("test");
        final Block sameSpot = mock(Block.class);
        when(sameSpot.getX()).thenReturn(Integer.valueOf(x));
        when(sameSpot.getY()).thenReturn(Integer.valueOf(y));
        when(sameSpot.getZ()).thenReturn(Integer.valueOf(z));
        when(sameSpot.getWorld()).thenReturn(reloaded);
        gate.setGateIrisLeverBlock(sameSpot);

        final GateRederivation.Outcome outcome = GateRederivation.rederive(gate);

        assertFalse(outcome.changes().contains("iris lever"),
            "same coordinates and same world name is the same block, whatever World object"
                + " is holding it; changes were: " + outcome.changes());
    }

    /**
     * A gate whose shape could not be resolved is refused rather than re-derived.
     *
     * <p>{@code Stargate}'s constructor installs a placeholder shape that is not a
     * {@link Stargate3DShape}, and a gate whose real shape file has been renamed away still
     * carries it. Trying to detect against that placeholder would compare the gate to
     * something it was never built from.
     */
    @Test
    void aGateWithNoThreeDeeShapeIsRefused()
    {

        final GateRederivation.Outcome outcome = GateRederivation.rederive(new Stargate());

        assertEquals(GateRederivation.Result.NO_SHAPE, outcome.result());
        assertTrue(outcome.changes().isEmpty());
    }

    /**
     * A gate with no dial button recorded has nothing for detection to anchor on.
     *
     * <p>Detection derives the gate's entire coordinate origin from the clicked block, so
     * without one there is no way to work out where anything is. Saying so beats guessing.
     */
    @Test
    void aGateWithNoDialButtonIsRefused() throws Exception
    {
        final Stargate gate = new Stargate();
        gate.setGateShape(shape("MinimalSignDial"));
        gate.setGateFacing(BlockFace.SOUTH);

        final GateRederivation.Outcome outcome = GateRederivation.rederive(gate);

        assertEquals(GateRederivation.Result.NO_ANCHOR, outcome.result());
    }

    /**
     * Re-deriving a gate nothing has changed reports no changes.
     *
     * <p>{@code regenerate} only saves the gate when something moved, so a run that finds
     * everything already correct has to say so -- otherwise every regenerate writes a file
     * and the message says something moved when nothing did.
     */
    @Test
    void aGateAlreadyMatchingItsShapeReportsNothingMoved() throws Exception
    {
        final Stargate gate = detected("MinimalSignDial");

        final GateRederivation.Outcome outcome = GateRederivation.rederive(gate);

        assertEquals(GateRederivation.Result.REDERIVED, outcome.result());
        assertTrue(outcome.changes().isEmpty(),
            "nothing changed underneath this gate, so nothing should be reported as moved;"
                + " got: " + outcome.changes());
    }

    /** Each light wave as its blocks' coordinates, in wave order, so two gates' orders compare. */
    private static List<java.util.Set<String>> order(final Stargate gate)
    {
        final List<java.util.Set<String>> waves = new java.util.ArrayList<>();
        for (final List<Location> wave : gate.getGateLightBlocks())
        {
            final java.util.Set<String> keys = new java.util.HashSet<>();
            if (wave != null)
            {
                for (final Location l : wave)
                {
                    keys.add(key(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
                }
            }
            waves.add(keys);
        }
        return waves;
    }

    /** Swaps the first and last chevron, the way a shape renumbered since the gate was built leaves it. */
    private static void swapFirstAndLast(final Stargate gate)
    {
        final List<List<Location>> waves = gate.getGateLightBlocks();
        final int last = waves.size() - 1;
        final List<Location> first = waves.get(1);
        waves.set(1, waves.get(last));
        waves.set(last, first);
    }

    /**
     * A gate built before its shape was renumbered takes the shape's order (#350).
     *
     * <p>A gate saves its light order when it is built, and nothing re-read it, so the show's
     * order never reached a gate already standing.
     */
    @Test
    void aGateBuiltWithAnOlderLightOrderTakesTheShapesOrder() throws Exception
    {
        final Stargate gate = detected("Standard");
        final List<java.util.Set<String>> shapeOrder = order(gate);
        swapFirstAndLast(gate);
        assertNotEquals(shapeOrder, order(gate), "the swap should have changed the order");

        assertEquals(GateRederivation.LightResult.REBUILT, GateRederivation.rebuildLightOrder(gate));
        assertEquals(shapeOrder, order(gate));
    }

    /** A gate already lighting in its shape's order is reported unchanged, so it is not saved again. */
    @Test
    void aGateAlreadyLightingInItsShapesOrderIsUnchanged() throws Exception
    {
        final Stargate gate = detected("Standard");

        assertEquals(GateRederivation.LightResult.UNCHANGED, GateRederivation.rebuildLightOrder(gate));
    }

    /**
     * Rebuilding reads no blocks, which is what lets {@code regenerate -all} run it on gates in
     * chunks nobody has loaded.
     */
    @Test
    void rebuildingTheLightOrderReadsNoBlocks() throws Exception
    {
        final Stargate gate = detected("Standard");
        swapFirstAndLast(gate);
        clearInvocations(world);

        assertEquals(GateRederivation.LightResult.REBUILT, GateRederivation.rebuildLightOrder(gate));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    /** A gate part-way through dialling keeps its lights, or the ones already drawn would be stranded. */
    @Test
    void aGateThatIsDiallingKeepsItsLightOrder() throws Exception
    {
        final Stargate gate = detected("Standard");
        swapFirstAndLast(gate);
        final List<java.util.Set<String>> before = order(gate);
        gate.setGateLightsActive(true);

        assertEquals(GateRederivation.LightResult.BUSY, GateRederivation.rebuildLightOrder(gate));
        assertEquals(before, order(gate));
    }

    /**
     * Startup brings each standing gate's light order up to date and saves only the gates it
     * changed, so an upgrade needs no {@code regen -all}.
     */
    @Test
    void startupRebuildsAndSavesOnlyTheGatesLightingInAnOlderOrder() throws Exception
    {
        final Stargate old = detected("Standard");
        old.setGateName("Old");
        swapFirstAndLast(old);
        final Stargate current = detected("Standard");
        current.setGateName("Current");
        final List<java.util.Set<String>> shapeOrder = order(current);

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            assertEquals(1, LightOrderUpgrade.rebuildAll(List.of(old, current)));
            db.verify(() -> StargateDBManager.saveStargate(old));
            db.verify(() -> StargateDBManager.saveStargate(current), never());
        }
        assertEquals(shapeOrder, order(old));
    }

    /** A gate whose frame no longer fits its shape is left as it is, and named in the log. */
    @Test
    void startupNamesAGateItCouldNotRelight() throws Exception
    {
        final Stargate gate = detected("Standard");
        gate.setGateName("Abydos");
        gate.getGateStructureBlocks().clear();
        gate.getGateLightBlocks().clear();

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            assertEquals(0, LightOrderUpgrade.rebuildAll(List.of(gate)));
            db.verify(() -> StargateDBManager.saveStargate(gate), never());
        }
        verify(WormholeXTreme.getThisPlugin()).prettyLog(eq(Level.INFO), contains("Abydos"));
    }

    /** A shape lighting blocks the gate does not have leaves the gate alone: its frame has changed. */
    @Test
    void aShapeThatLightsBlocksOutsideTheFrameIsRefused() throws Exception
    {
        final Stargate gate = detected("Standard");
        gate.getGateStructureBlocks().clear();
        gate.getGateLightBlocks().clear();

        assertEquals(GateRederivation.LightResult.DOES_NOT_FIT, GateRederivation.rebuildLightOrder(gate));
        assertTrue(gate.getGateLightBlocks().isEmpty());
    }

    /** Runs with every shipped gate shape loaded, the way a server has them, and puts the registry back. */
    private static void withShippedShapes(final ThrowingRunnable body) throws Exception
    {
        final java.util.Map<String, com.wormhole_xtreme.wormhole.model.StargateShape> shapes =
            com.wormhole_xtreme.wormhole.model.StargateShapeRegistry.getStargateShapes();
        final Map<String, com.wormhole_xtreme.wormhole.model.StargateShape> saved = new HashMap<>(shapes);
        shapes.clear();
        try (java.util.stream.Stream<Path> files = Files.list(SHAPE_DIR))
        {
            for (final Path file : files.filter(f -> f.toString().endsWith(".shape")).toList())
            {
                final Stargate3DShape shape = new Stargate3DShape(Files.readAllLines(file).toArray(new String[0]));
                shapes.put(shape.getShapeName(), shape);
            }
        }
        try
        {
            body.run();
        }
        finally
        {
            shapes.clear();
            shapes.putAll(saved);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    /**
     * A gate recorded under a shape it is not takes the shape its frame is.
     *
     * <p>The legacy importer records every gate as {@code Standard}. A {@code Massive} gate
     * recorded that way could never be regenerated: its frame does not match {@code Standard},
     * so its markers and its light order were left alone however often it was asked.
     */
    @Test
    void aGateRecordedUnderTheWrongShapeTakesTheShapeItIs() throws Exception
    {
        final Stargate gate = detected("Massive");
        gate.setGateShape(shape("Standard"));

        withShippedShapes(() -> {
            final GateRederivation.Outcome outcome = GateRederivation.rederive(gate);

            assertEquals(GateRederivation.Result.REDERIVED, outcome.result());
            assertEquals("Massive", gate.getGateShapeName());
            assertTrue(outcome.changes().contains("shape (was Standard)"), "changes were: " + outcome.changes());
        });
        java.util.Collections.swap(gate.getGateLightBlocks(), 1, 2);
        assertEquals(GateRederivation.LightResult.REBUILT, GateRederivation.rebuildLightOrder(gate),
            "with its own shape back, the light order rebuilds");
    }

    /** A frame that matches no shape at all is still left alone, with every shape to try. */
    @Test
    void aFrameMatchingNoShapeIsLeftAloneEvenWithEveryShapeToTry() throws Exception
    {
        final Stargate gate = detected("Massive");
        knockOut(gate.getGateStructureBlocks().get(0));

        withShippedShapes(() -> {
            final GateRederivation.Outcome outcome = GateRederivation.rederive(gate);

            assertEquals(GateRederivation.Result.NOT_DETECTED, outcome.result());
            assertEquals("Massive", gate.getGateShapeName());
            assertTrue(outcome.changes().isEmpty());
        });
    }

    /**
     * A shape named for a gate it fits all but a block of is taken, and the gap is reported.
     *
     * <p>The case detection cannot reach: recorded as {@code Standard}, and a block short of the
     * {@code Massive} it is, so no shape matches it whole.
     */
    @Test
    void aNamedShapeTheFrameIsABlockShortOfIsTakenWithTheGapReported() throws Exception
    {
        final Stargate gate = detected("Massive");
        gate.setGateShape(shape("Standard"));
        knockOut(gate.getGateStructureBlocks().get(0));

        final GateRederivation.ShapeFit fit = GateRederivation.adoptShape(gate, shape("Massive"));

        assertTrue(fit.accepted());
        assertEquals(fit.expected() - 1, fit.present());
        assertEquals(1, fit.gaps().size(), "gaps were: " + fit.gaps());
        assertEquals(org.bukkit.Material.AIR, fit.gaps().get(0).found(), String.valueOf(fit.gaps().get(0)));
        assertEquals("Massive", gate.getGateShapeName());
    }

    /** A shape named for a gate it does not fit is refused, and the gate keeps the shape it had. */
    @Test
    void aNamedShapeTheFrameIsNotIsRefused() throws Exception
    {
        final Stargate gate = detected("Massive");
        final com.wormhole_xtreme.wormhole.model.StargateShape before = gate.getGateShape();

        final GateRederivation.ShapeFit fit = GateRederivation.adoptShape(gate, shape("Standard"));

        assertFalse(fit.accepted());
        assertTrue((fit.present() * 100) < (fit.expected() * GateRederivation.NAMED_SHAPE_MINIMUM_PERCENT));
        assertEquals("Massive", gate.getGateShapeName());
        assertSame(before, gate.getGateShape(), "the shape the gate animates and sounds from is put back too");
    }

    /** Takes one particular block out of the world, so a test does not depend on map order. */
    private void knockOut(final Location block)
    {
        assertNotNull(placed.remove(key(block.getBlockX(), block.getBlockY(), block.getBlockZ())), "no block there to take out");
    }

    /**
     * A gate whose DHD stands two blocks further out than its shape says is still laid where its
     * frame is: the layout follows the gate's own recorded blocks, not the button alone.
     *
     * <p>Found in-game on {@code Large} and {@code Grand} gates recorded as {@code Standard}:
     * naming the right shape found 2 of 26 and 2 of 464 frame blocks, every other one AIR,
     * because the shape was laid from the DHD and the ring was not where the DHD said.
     */
    @Test
    void aGateWithItsDhdOffIsLaidWhereItsFrameIs() throws Exception
    {
        final Stargate gate = detected("Large");
        final List<java.util.Set<String>> order = order(gate);
        final Block button = gate.getGateDialLeverBlock();
        final BlockFace out = gate.getGateFacing();
        gate.setGateDialLeverBlock(blockAt(button.getX() + (2 * out.getModX()), button.getY(),
            button.getZ() + (2 * out.getModZ())));
        gate.setGateShape(shape("Standard"));

        final GateRederivation.ShapeFit fit = GateRederivation.adoptShape(gate, shape("Large"));

        assertTrue(fit.accepted(), "found " + fit.present() + " of " + fit.expected());
        assertEquals(fit.expected(), fit.present());
        assertEquals(-2, fit.layout().along(), fit.layout().describe());
        java.util.Collections.swap(gate.getGateLightBlocks(), 1, 2);
        assertEquals(GateRederivation.LightResult.REBUILT, GateRederivation.rebuildLightOrder(gate));
        assertEquals(order, order(gate), "the light order is laid from the frame too");
    }

    /** A gate recorded facing the wrong way is laid by turning the facing round. */
    @Test
    void aGateRecordedFacingTheWrongWayIsLaidTurnedRound() throws Exception
    {
        final Stargate gate = detected("Standard");
        gate.setGateFacing(WorldUtils.getInverseDirection(gate.getGateFacing()));

        final GateRederivation.Layout layout = GateRederivation.layoutFor(gate, shape("Standard"));

        assertTrue(layout.reversed(), layout.describe());
        final GateRederivation.ShapeFit fit = GateRederivation.adoptShape(gate, shape("Standard"));
        assertEquals(fit.expected(), fit.present());
    }

    /** A gate built as its shape says is laid exactly where its DHD puts it. */
    @Test
    void aGateBuiltAsItsShapeSaysIsLaidWhereItsDhdPutsIt() throws Exception
    {
        for (final String name : new String[] { "Standard", "Large", "Grand", "Massive", "Horizontal" })
        {
            placed.clear();
            final Stargate gate = detected(name);
            assertFalse(GateRederivation.layoutFor(gate, shape(name)).moved(), name);
        }
    }

    /**
     * A {@code Massive} gate built under 1.7.0 has its name sign in the ring; regen takes it out.
     *
     * <p>1.7.0's shape put {@code :N} on the back ring, so the sign replaced a frame block of the
     * layer in front. Staged here on today's shape: the holder moved back two layers and a sign
     * standing in the frame cell between. Detection cannot match the frame with the sign in it,
     * so re-deriving fails until the block is back.
     */
    @Test
    void aNameSignStandingInTheFrameIsTakenOutAndTheHolderMovesToTheFront() throws Exception
    {
        final Stargate gate = detected("Massive");
        final Block front = gate.getGateNameBlockHolder();
        final BlockFace back = gate.getGateFacing().getOppositeFace();
        final Block signCell = front.getRelative(back);
        final Material frame = placed.get(key(signCell.getX(), signCell.getY(), signCell.getZ()));
        assertNotNull(frame, "the cell behind the front holder is frame");
        placed.put(key(signCell.getX(), signCell.getY(), signCell.getZ()), Material.OAK_WALL_SIGN);
        gate.setGateNameBlockHolder(signCell.getRelative(back));

        final List<Block> restored = GateRederivation.restoreFrameUnderSigns(gate);

        assertEquals(List.of(signCell), restored);
        assertEquals(frame, signCell.getType());
        final GateRederivation.Outcome outcome = GateRederivation.rederive(gate);
        assertEquals(GateRederivation.Result.REDERIVED, outcome.result());
        assertTrue(outcome.changes().contains("name sign"), "changes were: " + outcome.changes());
        assertSame(front, gate.getGateNameBlockHolder());
    }

    /** A name sign hanging where it should, in front of the ring, is left standing. */
    @Test
    void aNameSignInFrontOfTheFrameIsLeftAlone() throws Exception
    {
        final Stargate gate = detected("Massive");
        final Block sign = gate.getGateNameBlockHolder().getRelative(gate.getGateFacing());
        placed.put(key(sign.getX(), sign.getY(), sign.getZ()), Material.OAK_WALL_SIGN);

        assertEquals(List.of(), GateRederivation.restoreFrameUnderSigns(gate));
        assertEquals(Material.OAK_WALL_SIGN, sign.getType());
    }

    /**
     * A sign in the frame is found by the cell it stands in, not by the gate's recorded name holder.
     *
     * <p>Jericho, a {@code Massive} gate from 1.7.0, came up 459 of 460 with the sign reported in
     * the frame, and a cleanup that started from the holder did nothing for it.
     */
    @Test
    void aSignInTheFrameIsFoundWithoutTheNameHolder() throws Exception
    {
        final Stargate gate = detected("Massive");
        final Block signCell = gate.getGateNameBlockHolder().getRelative(gate.getGateFacing().getOppositeFace());
        final Material frame = signCell.getType();
        placed.put(key(signCell.getX(), signCell.getY(), signCell.getZ()), Material.OAK_WALL_SIGN);
        gate.setGateNameBlockHolder(null);

        assertEquals(List.of(signCell), GateRederivation.restoreFrameUnderSigns(gate));
        assertEquals(frame, signCell.getType());
    }
}
