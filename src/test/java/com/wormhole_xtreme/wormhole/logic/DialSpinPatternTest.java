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
import java.util.Set;

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
        assertEquals(DialSpinPattern.TOP, DialSpinPattern.parse("true"), "the old switch on means the default");
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
                if ((pattern == DialSpinPattern.NONE) || (pattern == DialSpinPattern.UNIVERSE))
                {
                    // UNIVERSE lights its glyph as the chevron locks; see its own test.
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

    /**
     * CHASE laps anticlockwise from chevron 1 back to it for the first glyph, then runs from each
     * locked chevron to the next, clockwise to chevron 2 and alternating after.
     */
    @Test
    void chaseLapsToChevronOneThenRunsChevronToChevron() throws Exception
    {
        for (final String name : RINGS)
        {
            final DialSpin spin = spin(name);
            final List<Cell> first = spin.path(DialSpinPattern.CHASE, 1);
            assertEquals(spin.ring().size(), first.size(), name + ": the first glyph goes the whole way round");
            assertFalse(clockwise(spin, first), name + ": anticlockwise");
            for (int glyph = 2; glyph <= 7; glyph++)
            {
                final List<Cell> path = spin.path(DialSpinPattern.CHASE, glyph);
                assertEquals(glyph - 1, path.get(0).wave(), name + ": glyph " + glyph + " starts at the chevron before");
                assertEquals((glyph % 2) == 0, clockwise(spin, path), name + ": glyph " + glyph + " turns the other way");
            }
        }
    }

    /** OVERSHOOT runs on past its chevron, then backs onto it, the way the ring sometimes settles. */
    @Test
    void overshootRunsPastTheChevronAndBacksOntoIt() throws Exception
    {
        for (final String name : RINGS)
        {
            final DialSpin spin = spin(name);
            for (int glyph = 1; glyph <= 7; glyph++)
            {
                final List<Cell> plain = spin.path(DialSpinPattern.CHEVRON, glyph);
                final List<Cell> over = spin.path(DialSpinPattern.OVERSHOOT, glyph);
                final Cell chevron = plain.get(plain.size() - 1);
                assertEquals(plain, over.subList(0, plain.size()), name + " glyph " + glyph + ": the same way there");
                assertTrue(over.size() > plain.size(), name + " glyph " + glyph + ": and on past it");
                assertFalse(plain.contains(over.get(plain.size())), name + " glyph " + glyph + ": onto the ring beyond");
                assertEquals(chevron, over.get(over.size() - 1), name + " glyph " + glyph + ": and back onto the chevron");
            }
        }
    }

    /**
     * TOP rests on the top chevron after each lock before the ring turns again, so the lock reads
     * as the show's; every other pattern sets off at once.
     */
    @Test
    void topRestsOnTheTopAfterEachLock() throws Exception
    {
        final DialSpin spin = spin("Standard");
        final Set<Cell> top = spin.rest(DialSpinPattern.TOP, 1, 7);
        final List<Cell> path = spin.path(DialSpinPattern.TOP, 1);
        assertTrue(top.contains(path.get(path.size() - 1)), "the light stays where it landed");
        assertEquals(TICKS, spin.frames(DialSpinPattern.TOP, 1, TICKS), "nothing to rest after before the first");
        assertEquals(TICKS + DialSpin.TOP_HOLD_TICKS, spin.frames(DialSpinPattern.TOP, 2, TICKS));
        for (int frame = 0; frame < DialSpin.TOP_HOLD_TICKS; frame++)
        {
            assertEquals(top, spin.frame(DialSpinPattern.TOP, 2, frame, TICKS), "resting at frame " + frame);
        }
        assertEquals(spin.lit(DialSpinPattern.TOP, 2, 0, TICKS), spin.frame(DialSpinPattern.TOP, 2, DialSpin.TOP_HOLD_TICKS, TICKS),
            "then off round the ring");
        assertTrue(spin.rest(DialSpinPattern.TOP, 7, 7).isEmpty(), "the last lock is the top chevron's own light");
        for (final DialSpinPattern pattern : DialSpinPattern.values())
        {
            if ((pattern != DialSpinPattern.TOP) && (pattern != DialSpinPattern.NONE))
            {
                assertEquals(TICKS, spin.frames(pattern, 2, TICKS), pattern + " sets off at once");
            }
        }
    }

    /**
     * UNIVERSE turns the whole ring: each glyph lights at the top as it locks and rides round from
     * there, each at its own place, and a turn starts where the last left off.
     */
    @Test
    void universeCarriesEachLockedGlyphRound() throws Exception
    {
        for (final String name : RINGS)
        {
            final DialSpin spin = spin(name);
            final int width = Math.max(1, (int) Math.round(spin.ring().size() / 36.0));
            final Set<Cell> first = spin.frame(DialSpinPattern.UNIVERSE, 1, 0, TICKS);
            assertEquals(width, first.size(), name + ": the point of origin alone before any lock");
            assertFalse(first.equals(spin.frame(DialSpinPattern.UNIVERSE, 1, TICKS / 2, TICKS)), name + ": and it moves");
            for (int glyph = 1; glyph <= 7; glyph++)
            {
                final Set<Cell> locked = spin.rest(DialSpinPattern.UNIVERSE, glyph, 7);
                assertEquals((glyph + 1) * width, locked.size(), name + " glyph " + glyph + ": every lit glyph apart");
                final List<Cell> top = spin.path(DialSpinPattern.TOP, glyph);
                assertTrue(locked.contains(top.get(top.size() - 1)), name + " glyph " + glyph + ": the new one at the top");
                assertEquals(locked, spin.frame(DialSpinPattern.UNIVERSE, glyph + 1, 0, TICKS),
                    name + " glyph " + (glyph + 1) + " starts where " + glyph + " left off");
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
        assertEquals(DialSpinPattern.TOP, com.wormhole_xtreme.wormhole.config.ConfigManager.getGateDialSpinPattern(),
            "the default, not the CHEVRON it was");
    }

    /** A config with no gate-dial-spin at all turns the same default as a fresh one, not the old CHEVRON. */
    @Test
    void aMissingSettingTurnsTheDefault()
    {
        com.wormhole_xtreme.wormhole.config.ConfigTestSupport.clear();

        assertEquals(DialSpinPattern.TOP, com.wormhole_xtreme.wormhole.config.ConfigManager.getGateDialSpinPattern());
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
