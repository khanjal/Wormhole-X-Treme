package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateGrid;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * Which shipped shapes ripple a block in front of their horizon, laid out as a builder lays them (#579).
 *
 * <p>A ripple a block in front is only right where the frame closes that plane in, so it reads as
 * the horizon standing out of the ring rather than as a slab of water hanging in front of a flat
 * gate. The rule reads the gate's own blocks, so a shape edited to add or drop that front layer
 * changes the answer here, in each of the four ways a gate can face.
 */
class HorizonRippleShapesTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/shapes/gate");

    @AfterEach
    void tearDown() throws ReflectiveOperationException
    {
        PluginTestSupport.remove();
    }

    private static boolean deep(final String name, final BlockFace looking) throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        final Stargate3DShape shape =
            new Stargate3DShape(Files.readAllLines(SHAPE_DIR.resolve(name + ".shape")).toArray(new String[0]));
        final GateGrid grid = GateBlueprint.inFrontOf(shape, 0, 64, 0, looking);
        final List<Spot> opening = GateBlueprint.openingOf(shape, grid).stream()
            .map(cell -> new Spot(cell.x(), cell.y(), cell.z())).toList();
        final List<Spot> frame = GateBlueprint.of(shape, grid).stream()
            .map(cell -> new Spot(cell.x(), cell.y(), cell.z())).toList();
        return HorizonRipple.isDeep(grid.facing(), opening, frame);
    }

    @Test
    void grandAndMassiveHaveARingInFrontAndTheOthersDoNot() throws Exception
    {
        final Map<String, Boolean> expected = new TreeMap<>(Map.of("Grand", true, "Massive", true, "Standard", false,
            "StandardSignDial", false, "Large", false, "Minimal", false, "MinimalSignDial", false));
        for (final BlockFace looking : new BlockFace[] { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST })
        {
            final Map<String, Boolean> found = new TreeMap<>();
            for (final String name : expected.keySet())
            {
                found.put(name, deep(name, looking));
            }
            assertEquals(expected, found, "built looking " + looking);
        }
    }

    /** Pillars either side of an opening, a block in front, are not a ring in front of it: a lintel and a sill as well are. */
    @Test
    void pillarsEitherSideAreNotARingInFront()
    {
        final List<Spot> opening = List.of(new Spot(0, 64, 0), new Spot(0, 65, 0));
        final List<Spot> pillars = List.of(new Spot(-1, 64, 1), new Spot(-1, 65, 1), new Spot(1, 64, 1), new Spot(1, 65, 1));
        assertFalse(HorizonRipple.isDeep(BlockFace.SOUTH, opening, pillars), "nothing above or below");
        final List<Spot> ring = new ArrayList<>(pillars);
        ring.add(new Spot(0, 63, 1));
        ring.add(new Spot(0, 66, 1));
        assertTrue(HorizonRipple.isDeep(BlockFace.SOUTH, opening, ring), "closed round on all four sides");
    }

    /** Frame on four sides of a one-cell opening, which would count on an upright gate, does not on one lying flat. */
    @Test
    void aHorizontalGateIsNeverDeep()
    {
        final List<Spot> round = List.of(new Spot(-1, 64, 1), new Spot(1, 64, 1), new Spot(0, 63, 1), new Spot(0, 65, 1));
        assertTrue(HorizonRipple.isDeep(BlockFace.SOUTH, List.of(new Spot(0, 64, 0)), round), "upright, it counts");
        assertFalse(HorizonRipple.isDeep(BlockFace.UP, List.of(new Spot(0, 64, 0)), round), "lying flat, it does not");
    }
}
