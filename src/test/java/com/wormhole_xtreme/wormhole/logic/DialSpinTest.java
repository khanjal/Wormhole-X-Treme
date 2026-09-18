package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;

/**
 * The ring a dial's light travels round (#357): ordered clockwise from the top chevron as seen from
 * the DHD, and walked half way round to the top, alternating direction each glyph.
 */
class DialSpinTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/shapes/gate");
    private static final String[] RINGS = { "Standard", "Large", "Grand", "Massive", "Horizontal" };

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private static DialSpin spin(final String name, final BlockFace facing) throws Exception
    {
        final Stargate3DShape shape = new Stargate3DShape(
            Files.readAllLines(SHAPE_DIR.resolve(name + ".shape")).toArray(new String[0]));
        final GateGrid grid = GateBlueprint.inFrontOf(shape, 0, 64, 0, facing);
        return DialSpin.of(GateBlueprint.of(shape, grid), grid);
    }

    /** The mean angle of one chevron's cells on the ring. */
    private static double angleOfChevron(final DialSpin spin, final int wave)
    {
        final List<Cell> cells = spin.ring().stream().filter(c -> c.wave() == wave).toList();
        assertTrue(!cells.isEmpty(), "chevron " + wave + " is on the ring");
        // Chevron 7 straddles 0, so measure it from -pi to pi.
        return cells.stream().mapToDouble(c -> {
            final double a = spin.angleOf(c);
            return ((wave == 7) && (a > Math.PI)) ? (a - (2 * Math.PI)) : a;
        }).average().orElseThrow();
    }

    /**
     * Chevrons 1 to 6 lie clockwise from the top in their own order, and 7 is the top, on every
     * ring and whichever way the gate faces. The right side is the viewer's, so this is where a
     * mirrored axis would show.
     */
    @Test
    void theChevronsLieClockwiseFromTheTopInTheirOrder() throws Exception
    {
        for (final String name : RINGS)
        {
            for (final BlockFace facing : new BlockFace[] { BlockFace.NORTH, BlockFace.EAST })
            {
                final DialSpin spin = spin(name, facing);
                assertNotNull(spin, name);
                assertEquals(0.0, angleOfChevron(spin, 7), 0.35, name + " " + facing + ": chevron 7 is the top");
                double previous = 0.0;
                for (int wave = 1; wave <= 6; wave++)
                {
                    final double angle = angleOfChevron(spin, wave);
                    assertTrue(angle > previous, name + " " + facing + ": chevron " + wave + " at " + angle
                        + " should be clockwise of " + previous);
                    previous = angle;
                }
            }
        }
    }

    /** A glyph's light goes half way round, from opposite the top to the top, turning back each glyph. */
    @Test
    void eachGlyphTravelsHalfTheRingToTheTopTurningBackEachTime() throws Exception
    {
        for (final String name : RINGS)
        {
            final DialSpin spin = spin(name, BlockFace.NORTH);
            final List<Cell> odd = spin.path(1);
            final List<Cell> even = spin.path(2);
            final int half = spin.ring().size() / 2;

            assertEquals(odd.get(odd.size() - 1), even.get(even.size() - 1), name + ": both end at the top");
            assertTrue(Math.abs(spin.angleOf(odd.get(odd.size() - 1))) < 0.35
                || Math.abs(spin.angleOf(odd.get(odd.size() - 1)) - (2 * Math.PI)) < 0.35, name + ": the top");
            assertTrue(Math.abs(odd.size() - half) <= 2, name + ": about half the ring, " + odd.size() + " of "
                + spin.ring().size());
            assertNotEquals(odd.get(1), even.get(1), name + ": the second glyph turns the other way");
        }
    }

    /** The light moves along the path over the spin and ends at the top. */
    @Test
    void theCometStartsOppositeAndArrivesAtTheTop() throws Exception
    {
        final DialSpin spin = spin("Standard", BlockFace.NORTH);
        final List<Cell> path = spin.path(1);

        assertTrue(spin.comet(1, 0, 10).contains(path.get(0)), "it starts opposite the top");
        final Set<Cell> last = spin.comet(1, 9, 10);
        assertTrue(last.contains(path.get(path.size() - 1)), "it ends at the top");
        assertTrue(last.size() >= 2, "a run of cells, not one");
    }
}
