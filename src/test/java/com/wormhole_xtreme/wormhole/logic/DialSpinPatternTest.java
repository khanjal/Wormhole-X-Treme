package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;

/** The ring light's patterns: where each lands, and that each fits the chevron's own interval. */
class DialSpinPatternTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/shapes/gate");
    private static final String[] RINGS = { "Standard", "Large", "Grand", "Massive", "Horizontal" };
    private static final int TICKS = 10;

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

    private static DialSpin spin(final String name) throws Exception
    {
        final Stargate3DShape shape = new Stargate3DShape(
            Files.readAllLines(SHAPE_DIR.resolve(name + ".shape")).toArray(new String[0]));
        final GateGrid grid = GateBlueprint.inFrontOf(shape, 0, 64, 0, BlockFace.NORTH);
        return DialSpin.of(GateBlueprint.of(shape, grid), grid);
    }

    /** Names in any capitals, and the setting's old true and false. */
    @Test
    void patternsAreReadByNameAndTheOldSwitchStillReads()
    {
        assertEquals(DialSpinPattern.LAP, DialSpinPattern.parse("lap"));
        assertEquals(DialSpinPattern.PEGASUS, DialSpinPattern.parse(" Pegasus "));
        assertEquals(DialSpinPattern.CHEVRON, DialSpinPattern.parse("true"));
        assertEquals(DialSpinPattern.NONE, DialSpinPattern.parse("FALSE"));
        assertEquals(DialSpinPattern.NONE, DialSpinPattern.parse("off"));
        assertEquals(DialSpinPattern.NONE, DialSpinPattern.parse("none"));
        assertNull(DialSpinPattern.parse("banana"));
        assertNull(DialSpinPattern.parse(null));
    }

    /**
     * Every pattern's light lands where it should on the last tick of the chevron's interval, and
     * is somewhere else on the first: its chevron, or the top for TOP.
     */
    @Test
    void everyPatternLandsOnTheLastTick() throws Exception
    {
        for (final String name : RINGS)
        {
            final DialSpin spin = spin(name);
            for (final DialSpinPattern pattern : DialSpinPattern.values())
            {
                if (pattern == DialSpinPattern.NONE)
                {
                    continue;
                }
                for (int glyph = 1; glyph <= 7; glyph++)
                {
                    final List<Cell> path = spin.path(pattern, glyph);
                    final Cell end = path.get(path.size() - 1);
                    final int lands = (pattern == DialSpinPattern.TOP) ? 7 : glyph;
                    final String what = name + " " + pattern + " glyph " + glyph;
                    assertEquals(lands, end.wave(), what + " lands on chevron " + lands);
                    assertTrue(spin.lit(pattern, glyph, TICKS - 1, TICKS).contains(end), what + ": there on the last tick");
                    assertFalse(spin.lit(pattern, glyph, 0, TICKS).contains(end), what + ": not there on the first");
                }
            }
        }
    }

    /** Whether a path's first quarter heads clockwise. */
    private static boolean clockwise(final DialSpin spin, final List<Cell> path)
    {
        final double d = spin.angleOf(path.get(Math.max(1, path.size() / 4))) - spin.angleOf(path.get(0));
        return Math.floorMod((long) Math.round(Math.toDegrees(d)), 360) < 180;
    }

    /** LAP goes the whole way round clockwise for every glyph: round and round. */
    @Test
    void aLapGoesRoundAndRound() throws Exception
    {
        for (final String name : RINGS)
        {
            final DialSpin spin = spin(name);
            for (int glyph = 1; glyph <= 2; glyph++)
            {
                final List<Cell> lap = spin.path(DialSpinPattern.LAP, glyph);
                assertEquals(spin.ring().size(), lap.size(), name + " LAP glyph " + glyph);
                assertTrue(clockwise(spin, lap), name + ": LAP glyph " + glyph + " turns clockwise");
            }
        }
    }

    /**
     * PEGASUS goes as an Atlantis gate dials: the first glyph from the top anticlockwise, and each
     * after from the chevron last locked, turning the other way from the one before.
     */
    @Test
    void pegasusRunsFromTheLastChevronToTheNextAlternating() throws Exception
    {
        for (final String name : RINGS)
        {
            final DialSpin spin = spin(name);
            final List<Cell> first = spin.path(DialSpinPattern.PEGASUS, 1);
            assertEquals(7, first.get(0).wave(), name + ": the first glyph starts at the top");
            assertFalse(clockwise(spin, first), name + ": and turns anticlockwise");
            for (int glyph = 2; glyph <= 7; glyph++)
            {
                final List<Cell> path = spin.path(DialSpinPattern.PEGASUS, glyph);
                assertEquals(glyph - 1, path.get(0).wave(), name + ": glyph " + glyph + " starts at the chevron before");
                assertEquals((glyph % 2) == 0, clockwise(spin, path), name + ": glyph " + glyph + " turns the other way");
            }
        }
    }

    /** In-game, `/wormhole config gate-dial-spin ` offers every pattern, and a switch offers true and false. */
    @Test
    void theConfigCommandOffersThePatterns()
    {
        com.wormhole_xtreme.wormhole.config.ConfigTestSupport.loadDefaults();
        final List<String> patterns = com.wormhole_xtreme.wormhole.config.ConfigManager.valuesFor("gate-dial-spin");
        for (final DialSpinPattern pattern : DialSpinPattern.values())
        {
            assertTrue(patterns.contains(pattern.name().toLowerCase(java.util.Locale.ROOT)), pattern.name());
        }
        assertEquals(List.of("true", "false"), com.wormhole_xtreme.wormhole.config.ConfigManager.valuesFor("PETS_FOLLOW_OWNER"));
        assertTrue(com.wormhole_xtreme.wormhole.config.ConfigManager.valuesFor("not-a-setting").isEmpty());
        assertEquals(DialSpinPattern.CHEVRON, com.wormhole_xtreme.wormhole.config.ConfigManager.getGateDialSpinPattern(),
            "the default");
    }

    /** FILL leaves the light behind it lit, so it only ever grows. */
    @Test
    void fillOnlyGrows() throws Exception
    {
        final DialSpin spin = spin("Standard");
        int previous = 0;
        for (int tick = 0; tick < TICKS; tick++)
        {
            final int lit = spin.lit(DialSpinPattern.FILL, 3, tick, TICKS).size();
            assertTrue(lit >= previous, "tick " + tick + " lit " + lit + " after " + previous);
            previous = lit;
        }
        assertEquals(spin.path(DialSpinPattern.FILL, 3).size(), previous, "the whole path by the end");
    }
}
