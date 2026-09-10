package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
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
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/GateShapes");

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
}
