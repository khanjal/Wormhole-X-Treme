package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * How long an iris takes to cross a big gate.
 *
 * <p>A sweep takes a step per distinct distance from the centre, and every step waits
 * {@code gate-iris-step-ticks}. On the shipped {@code Grand} that was sixty-one steps: six
 * seconds at the default pace, and about ten in a preview, which redraws itself on each one.
 * The guide said {@code Massive} took about a second. Nothing measured it.
 *
 * <p>The pace cannot go below a tick, so the fix caps the steps instead:
 * {@code gate-iris-sweep-ticks} is a budget for the whole crossing, and a gate with more
 * steps than fit crosses several at once. A gate that already fits is left exactly as it was.
 *
 * <p>Built from the real shape files, because the number that went wrong was a property of
 * the shipped shapes, and a hand-made opening would pin arithmetic nobody doubted.
 */
class IrisSweepBudgetTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/shapes/gate");

    @BeforeEach
    void setUp() throws Exception
    {
        // Parsing a shape logs through the plugin.
        PluginTestSupport.install(mock(WormholeXTreme.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /**
     * A shipped shape's portal cells, as the coordinates a sweep reads.
     *
     * <p>Column across, row up, layer back: the sweep only compares distances, so which way
     * round the gate faces makes no difference to how many rings it has.
     */
    private static List<Location> openingOf(final String name) throws Exception
    {
        final List<String> lines = Files.readAllLines(SHAPE_DIR.resolve(name + ".shape"));
        final Stargate3DShape shape = new Stargate3DShape(lines.toArray(new String[0]));
        final List<Location> cells = new ArrayList<>();
        final List<StargateShapeLayer> layers = shape.getShapeLayers();
        for (int layer = 0; layer < layers.size(); layer++)
        {
            if (layers.get(layer) == null)
            {
                continue;
            }
            for (final Integer[] p : layers.get(layer).getLayerPortalPositions())
            {
                cells.add(new Location(null, p[2], p[1], layer));
            }
        }
        return cells;
    }

    /** Every cell the frames draw, each exactly once. */
    private static Set<List<Integer>> coveredOnce(final List<List<Location>> frames, final int expectedCells)
    {
        final Set<List<Integer>> seen = new HashSet<>();
        int drawn = 0;
        for (final List<Location> frame : frames)
        {
            for (final Location at : frame)
            {
                seen.add(List.of(at.getBlockX(), at.getBlockY(), at.getBlockZ()));
                drawn++;
            }
        }
        assertEquals(expectedCells, drawn, "every cell is drawn once, not dropped or doubled by the folding");
        assertEquals(expectedCells, seen.size(), "and no cell turns up in two frames");
        return seen;
    }

    /**
     * {@code Grand} crosses in the budget's ten frames, where it took sixty-one steps.
     *
     * <p>The sixty-one is asserted too, so this goes on saying why the budget exists: if the
     * shape ever changed to need ten steps or fewer, the first half would fail rather than the
     * second passing for a reason that has nothing to do with the budget.
     */
    @Test
    void aGrandCrossesInTheBudgetRatherThanSixtyOneSteps() throws Exception
    {
        final List<Location> grand = openingOf("Grand");

        assertEquals(61, IrisSweep.closingOrder(grand, IrisSweep.Style.SWEEP).size(),
            "Grand has a step per distinct distance from its centre");
        final List<List<Location>> frames = IrisSweep.closingOrder(grand, IrisSweep.Style.SWEEP, 10);

        assertEquals(10, frames.size(), "the ten frames the default budget allows");
        coveredOnce(frames, grand.size());
    }

    /**
     * A gate that already fits is left exactly as it was.
     *
     * <p>{@code Standard} is five steps and was never slow. Folding it anyway -- into fewer,
     * fatter steps, say -- would change how the common gate looks to fix a gate most servers
     * never build.
     */
    @Test
    void aStandardThatAlreadyFitsIsUntouched() throws Exception
    {
        final List<Location> standard = openingOf("Standard");
        final List<List<Location>> unfolded = IrisSweep.closingOrder(standard, IrisSweep.Style.SWEEP);

        final List<List<Location>> frames = IrisSweep.closingOrder(standard, IrisSweep.Style.SWEEP, 10);

        assertEquals(5, unfolded.size(), "Standard is five steps");
        assertEquals(unfolded, frames, "and with room for ten it keeps all five, ring for ring");
    }

    /**
     * The rim still goes first and the middle last, however many rings share a frame.
     *
     * <p>Merging neighbours keeps their order. Merging them any other way -- round-robin, say,
     * which evens the frame sizes out better -- would put the middle of the iris in the first
     * frame and draw it from the centre and the rim at once.
     */
    @Test
    void foldingKeepsTheRimFirstAndTheMiddleLast() throws Exception
    {
        final List<Location> grand = openingOf("Grand");
        final List<List<Location>> rings = IrisSweep.closingOrder(grand, IrisSweep.Style.SWEEP);

        final List<List<Location>> frames = IrisSweep.closingOrder(grand, IrisSweep.Style.SWEEP, 10);

        assertTrue(frames.get(0).containsAll(rings.get(0)), "the outermost ring is in the first frame");
        assertTrue(frames.get(frames.size() - 1).containsAll(rings.get(rings.size() - 1)),
            "the middle is in the last");
    }

    /**
     * Opening is closing run backwards, frame for frame.
     *
     * <p>Folded before reversing so the two agree. Folding the closing order and then the
     * opening order separately would cut them at different places, and an iris would open
     * through frames it never closed through.
     */
    @Test
    void openingIsClosingBackwardsFrameForFrame() throws Exception
    {
        final List<Location> grand = openingOf("Grand");

        final List<List<Location>> closing = IrisSweep.closingOrder(grand, IrisSweep.Style.SWEEP, 10);
        final List<List<Location>> opening = IrisSweep.openingOrder(grand, IrisSweep.Style.SWEEP, 10);

        assertEquals(closing.size(), opening.size());
        for (int i = 0; i < closing.size(); i++)
        {
            assertEquals(closing.get(i), opening.get(opening.size() - 1 - i),
                "closing frame " + i + " is opening frame " + (opening.size() - 1 - i));
        }
    }

    /**
     * The spiral takes the same budget, and still turns.
     */
    @Test
    void aSpiralOnAGrandFitsTheSameBudget() throws Exception
    {
        final List<Location> grand = openingOf("Grand");

        final List<List<Location>> frames = IrisSweep.closingOrder(grand, IrisSweep.Style.SPIRAL, 10);

        assertEquals(10, frames.size(), "every style crosses in the same number of frames");
        coveredOnce(frames, grand.size());
    }

    /**
     * A budget below one frame is still a frame, not an iris that never arrives.
     */
    @Test
    void aBudgetOfNothingStillDrawsTheIris() throws Exception
    {
        final List<Location> grand = openingOf("Grand");

        final List<List<Location>> frames = IrisSweep.closingOrder(grand, IrisSweep.Style.SWEEP, 0);

        assertEquals(1, frames.size(), "one frame, the whole iris");
        coveredOnce(frames, grand.size());
    }

    /**
     * The budget is the whole crossing, divided by the pace.
     *
     * <p>At the defaults -- twenty ticks across, two a step -- that is ten frames, a second on
     * every gate big enough to need it.
     */
    @Test
    void theDefaultsAllowTenFrames()
    {
        ConfigTestSupport.loadDefaults();

        assertEquals(10, ConfigManager.getGateIrisSweepFrames(), "20 ticks across at 2 a step");
    }

    /**
     * A slower pace leaves room for fewer frames, so the whole crossing stays inside its budget.
     */
    @Test
    void aSlowerPaceGetsFewerFramesInTheSameTime()
    {
        ConfigTestSupport.loadDefaults();
        ConfigTestSupport.set(ConfigManager.ConfigKeys.GATE_IRIS_STEP_TICKS, 5);
        ConfigTestSupport.set(ConfigManager.ConfigKeys.GATE_IRIS_SWEEP_TICKS, 40);

        assertEquals(8, ConfigManager.getGateIrisSweepFrames(), "40 ticks across at 5 a step");
    }

    /**
     * A budget shorter than one step is still one frame.
     */
    @Test
    void aBudgetShorterThanAStepIsOneFrame()
    {
        ConfigTestSupport.loadDefaults();
        ConfigTestSupport.set(ConfigManager.ConfigKeys.GATE_IRIS_STEP_TICKS, 10);
        ConfigTestSupport.set(ConfigManager.ConfigKeys.GATE_IRIS_SWEEP_TICKS, 5);

        assertEquals(1, ConfigManager.getGateIrisSweepFrames(), "not zero frames, which would never draw");
    }

    /** Folding never hands back a different list for a sweep that fits. */
    @Test
    void aSweepThatFitsIsReturnedAsIs()
    {
        final List<List<Location>> steps = new ArrayList<>();
        steps.add(List.of(new Location(null, 0, 0, 0)));

        assertSame(steps, IrisSweep.fitTo(steps, 10), "nothing to fold, nothing copied");
    }
}
