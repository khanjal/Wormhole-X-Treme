package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * the DHD, and walked half way round to the chevron about to lock, alternating direction each glyph.
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

    /**
     * Each glyph's light ends on its own chevron, the one about to lock, after half the ring. Found
     * in-game: ending at the top for every glyph made the chevron that then lit look random.
     */
    @Test
    void eachGlyphTravelsHalfTheRingToItsOwnChevron() throws Exception
    {
        for (final String name : RINGS)
        {
            final DialSpin spin = spin(name, BlockFace.NORTH);
            final int half = spin.ring().size() / 2;
            for (int glyph = 1; glyph <= 7; glyph++)
            {
                final List<Cell> path = spin.path(glyph);
                assertEquals(glyph, path.get(path.size() - 1).wave(), name + ": glyph " + glyph + " ends on its chevron");
                assertTrue(Math.abs(path.size() - half) <= 2, name + ": glyph " + glyph + " goes about half the ring, "
                    + path.size() + " of " + spin.ring().size());
            }
        }
    }

    /** Successive glyphs turn opposite ways, as the show's ring does. */
    @Test
    void eachGlyphTurnsTheOtherWay() throws Exception
    {
        for (final String name : RINGS)
        {
            final DialSpin spin = spin(name, BlockFace.NORTH);
            // A thick ring's neighbours can sit a hair either side, so it is the whole path's turn that counts.
            assertTrue(travel(spin, spin.path(1)) > 0, name + ": the first glyph turns clockwise");
            assertTrue(travel(spin, spin.path(2)) < 0, name + ": the second anticlockwise");
        }
    }

    /** The signed angle a path turns through, clockwise positive. */
    private static double travel(final DialSpin spin, final List<Cell> path)
    {
        double total = 0;
        for (int i = 1; i < path.size(); i++)
        {
            final double d = spin.angleOf(path.get(i)) - spin.angleOf(path.get(i - 1));
            total += (d > Math.PI) ? (d - (2 * Math.PI)) : ((d < -Math.PI) ? (d + (2 * Math.PI)) : d);
        }
        return total;
    }

    /** The light moves along the path over the spin and ends on the chevron. */
    @Test
    void theCometStartsOppositeAndArrivesOnTheChevron() throws Exception
    {
        final DialSpin spin = spin("Standard", BlockFace.NORTH);
        final List<Cell> path = spin.path(1);

        assertTrue(spin.comet(1, 0, 10).contains(path.get(0)), "it starts opposite the chevron");
        final Set<Cell> last = spin.comet(1, 9, 10);
        assertTrue(last.contains(path.get(path.size() - 1)), "it ends on the chevron");
        assertTrue(last.size() >= 2, "a run of cells, not one");
    }

    /**
     * A gate lit on two layers turns on the front one, nearest the DHD, as the show's ring is the
     * face you look at. Found in-game: Grand and Massive turned on the back layer.
     */
    @Test
    void aGateLitOnTwoLayersTurnsOnTheFrontOne() throws Exception
    {
        for (final String name : new String[] { "Grand", "Massive" })
        {
            final Stargate3DShape shape = new Stargate3DShape(
                Files.readAllLines(SHAPE_DIR.resolve(name + ".shape")).toArray(new String[0]));
            final GateGrid grid = GateBlueprint.inFrontOf(shape, 0, 64, 0, BlockFace.NORTH);
            final List<Cell> cells = GateBlueprint.of(shape, grid);
            final int front = cells.stream().filter(c -> c.wave() > 0).mapToInt(Cell::layer).max().orElseThrow();
            final int back = cells.stream().filter(c -> c.wave() > 0).mapToInt(Cell::layer).min().orElseThrow();
            assertTrue(front > back, name + " lights more than one layer");

            final DialSpin spin = DialSpin.of(cells, grid);

            assertTrue(spin.ring().stream().allMatch(c -> c.layer() == front), name + ": every ring cell on layer " + front);
        }
    }
}
