package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * What a trip through a gate costs: the gate's own, else its shape's, else {@code economy-use-cost} (#632).
 */
class UseCostPrecedenceTest
{
    private static final Path STANDARD = Paths.get("src/main/resources/shapes/gate/Standard.shape");

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** The shipped Standard shape with one more line in it, as an admin would add it. */
    private static Stargate3DShape standardWith(final String line) throws Exception
    {
        final List<String> lines = new ArrayList<>(Files.readAllLines(STANDARD));
        lines.add(1, line);
        return new Stargate3DShape(lines.toArray(new String[0]));
    }

    private static Stargate gateOf(final StargateShape shape)
    {
        final Stargate gate = new Stargate();
        gate.setGateShape(shape);
        return gate;
    }

    /** A shape file's USE_COST is read, with the spacing and trailing semicolon the shipped files use. */
    @Test
    void aShapeFileSaysWhatItsGatesCost() throws Exception
    {
        assertEquals(2.5, standardWith("USE_COST=2.5").getShapeUseCost());
        assertEquals(4.0, standardWith("USE_COST = 4;").getShapeUseCost(), "as LIGHT_TICKS = 2; is written");
        assertEquals(0.0, standardWith("USE_COST=0").getShapeUseCost(), "free is a cost, not unset");
    }

    /** A cost that is not a number from zero up leaves the shape following the server, and keeps the rest of the file. */
    @Test
    void aMistakeInTheFileLeavesTheShapeFollowingTheServer() throws Exception
    {
        assertNull(standardWith("USE_COST=lots").getShapeUseCost());
        assertNull(standardWith("USE_COST=-3").getShapeUseCost());
        assertNull(standardWith("USE_COST=").getShapeUseCost());
        assertNull(standardWith("# nothing here").getShapeUseCost(), "no line, no cost of its own");
        assertEquals("Standard", standardWith("USE_COST=lots").getShapeName(), "and the shape still loads");
    }

    /** The old file format reads it the same way. */
    @Test
    void theOlderShapeFormatReadsItToo()
    {
        final StargateShape shape = new StargateShape(new String[] {"Name=Legacy", "USE_COST=1.5", "GateShape=",
            "[O][O]", "[LO][E]", ""});

        assertEquals(1.5, shape.getShapeUseCost());
    }

    /** With nothing set anywhere the server's setting is the fare. */
    @Test
    void theServerSettingIsTheFareWhenNothingElseIsSet() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_USE_COST, 3.0);

        assertEquals(3.0, gateOf(standardWith("# none")).getEffectiveUseCost());
        assertEquals(3.0, new Stargate().getEffectiveUseCost(), "a gate with no shape follows the server too");
    }

    /** A shape's cost beats the server's, up or down, and 0 is free rather than unset. */
    @Test
    void aShapesCostBeatsTheServers() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_USE_COST, 3.0);

        assertEquals(10.0, gateOf(standardWith("USE_COST=10")).getEffectiveUseCost(), "dearer");
        assertEquals(1.0, gateOf(standardWith("USE_COST=1")).getEffectiveUseCost(), "cheaper");
        assertEquals(0.0, gateOf(standardWith("USE_COST=0")).getEffectiveUseCost(), "free");
    }

    /** A gate's own cost beats its shape's and the server's, and 0 is free, and clearing it falls back. */
    @Test
    void aGatesOwnCostBeatsEverythingAndClearingItFallsBack() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_USE_COST, 3.0);
        final Stargate gate = gateOf(standardWith("USE_COST=10"));

        gate.setGateUseCost(7.0);
        assertEquals(7.0, gate.getEffectiveUseCost(), "its own, over its shape's 10 and the server's 3");
        gate.setGateUseCost(0.0);
        assertEquals(0.0, gate.getEffectiveUseCost(), "its own free, over a shape that charges");
        gate.setGateUseCost(null);
        assertEquals(10.0, gate.getEffectiveUseCost(), "cleared: back to its shape's");
    }

    /** Only a plain amount is one: no exponent, suffix, sign or hex, which Double.parseDouble would take. */
    @Test
    void onlyAPlainAmountIsAnAmount()
    {
        assertEquals(2.0, StargateShape.parseCost("2"));
        assertEquals(0.25, StargateShape.parseCost(" .25 "));
        assertEquals(1.5, StargateShape.parseCost("1.5;"));
        for (final String bad : new String[] {"1e3", "2f", "2d", "0x1p3", "-0", "+2", "NaN", "Infinity", "2,5", "1.", "", " "})
        {
            assertNull(StargateShape.parseCost(bad), "'" + bad + "' is not an amount");
        }
        assertNull(StargateShape.parseCost(null));
    }

    /** The older format reads the key as the shipped files write it, and not out of a comment. */
    @Test
    void theOlderFormatReadsTheKeyAsWrittenAndNotFromAComment()
    {
        final String[] grid = {"GateShape=", "[O][O]", "[LO][E]", ""};
        assertEquals(2.0, new StargateShape(withLines(grid, "USE_COST = 2;")).getShapeUseCost());
        assertNull(new StargateShape(withLines(grid, "# USE_COST=5")).getShapeUseCost(), "a comment is not a setting");
        assertEquals(1.0, new StargateShape(withLines(grid, "USE_COST=1", "# USE_COST is what a trip costs"))
            .getShapeUseCost(), "and a comment after the line does not reset it");
    }

    private static String[] withLines(final String[] grid, final String... settings)
    {
        final List<String> lines = new ArrayList<>();
        lines.add("Name=Legacy");
        lines.addAll(List.of(settings));
        lines.addAll(List.of(grid));
        return lines.toArray(new String[0]);
    }

    /** A cost that is not an amount is said, naming the shape, and the shape follows the server. */
    @Test
    void aBadCostInAShapeFileIsSaid() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);

        assertNull(standardWith("USE_COST=2,5").getShapeUseCost());

        verify(plugin).prettyLog(eq(Level.WARNING), contains("USE_COST=2,5"));
        // A line with nothing after the key, and a file without it, are not mistakes worth a warning.
        standardWith("USE_COST=");
        standardWith("# none");
        verify(plugin, never()).prettyLog(eq(Level.WARNING), contains("USE_COST=,"));
        verify(plugin, times(1)).prettyLog(eq(Level.WARNING), contains("USE_COST"));
    }
}
