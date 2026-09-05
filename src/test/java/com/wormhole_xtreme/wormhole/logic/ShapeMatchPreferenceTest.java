package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeLayer;

/**
 * Which shape wins when more than one of them matches the same build.
 *
 * <p>More than one always does. Detection reads frame and portal cells and nothing else, and
 * a sign-dial shape puts its DHD in cells the plain twin marks {@code [I]} — so a gate built
 * as {@code StandardSignDial} satisfies {@code Standard} too. {@code Horizontal} and
 * {@code HorizontalSignDial} go further and are byte-identical once markers are stripped.
 *
 * <p>The tie used to be broken by {@code REDSTONE_ACTIVATED} and, failing that, by whichever
 * shape the registry's {@code ConcurrentHashMap} happened to return first. That is how
 * {@code HorizontalSignDial} became impossible to build: {@code Horizontal} comes back first,
 * both declare {@code REDSTONE_ACTIVATED=FALSE}, so {@code Horizontal} always won — and since
 * it marks the DHD cell {@code :N}, completing the gate then dropped a name sign on top of the
 * dial sign the player had just hung there. {@code MinimalSignDial} beat {@code Minimal} only
 * because of where the two names happened to hash, which a single custom shape on the server
 * would have been enough to change.
 */
class ShapeMatchPreferenceTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/GateShapes");

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);
    }

    private static Stargate3DShape load(final String name) throws Exception
    {
        final List<String> lines = Files.readAllLines(SHAPE_DIR.resolve(name + ".shape"));
        return new Stargate3DShape(lines.toArray(new String[0]));
    }

    /**
     * Stands in for what {@code check3DShape} hands back for this shape.
     *
     * <p>Only two things off the gate are ranked — whether a dial sign was found, and how many
     * frame blocks the shape accounted for — so the frame count is taken from the shape itself,
     * counted over the same layers detection walks (index 0 is the unused slot before
     * {@code Layer#1}). Making the number up would let the test agree with a ranking that had
     * stopped matching the shapes actually shipped.
     */
    private static Stargate matchOf(final Stargate3DShape shape, final boolean dialSignFound)
    {
        final Stargate gate = new Stargate();
        gate.setGateShape(shape);
        gate.setGateSignPowered(dialSignFound);

        final ArrayList<StargateShapeLayer> layers = shape.getShapeLayers();
        for (int layerIdx = 1; layerIdx < layers.size(); layerIdx++)
        {
            final StargateShapeLayer layer = layers.get(layerIdx);
            if (layer == null)
            {
                continue;
            }
            for (final Integer[] pos : layer.getLayerBlockPositions())
            {
                // A null world is fine: nothing here reads the location back, it is only counted.
                gate.getGateStructureBlocks().add(new Location(null, layerIdx, pos[1], pos[2]));
            }
        }
        return gate;
    }

    /** The four shipped rings that have both a plain and a sign-dial spelling. */
    private static String[][] shippedPairs()
    {
        return new String[][] {
            { "Standard", "StandardSignDial" },
            { "Even", "EvenSignDial" },
            { "Minimal", "MinimalSignDial" },
            { "Horizontal", "HorizontalSignDial" },
        };
    }

    /**
     * The bug this was written for: a player hangs a dial sign, and the shape that found it wins.
     *
     * <p>Only a shape carrying {@code :D} ever looks for a sign, so finding one is proof the
     * player built a sign gate rather than the plain twin that happens to fit inside it. Before
     * this ranking existed {@code Horizontal} beat {@code HorizontalSignDial} here and the sign
     * was ignored, then overwritten.
     */
    @Test
    void aFoundDialSignBeatsAShapeThatNeverLooksForOne() throws Exception
    {
        for (final String[] pair : shippedPairs())
        {
            final Stargate3DShape plain = load(pair[0]);
            final Stargate3DShape signDial = load(pair[1]);

            assertTrue(
                StargateHelper.beatsBestMatch(matchOf(signDial, true), signDial, matchOf(plain, false), plain),
                pair[1] + " found the player's dial sign and " + pair[0] + " cannot, so it has to win"
                    + " -- losing here means the sign is ignored and then painted over");
            assertFalse(
                StargateHelper.beatsBestMatch(matchOf(plain, false), plain, matchOf(signDial, true), signDial),
                pair[0] + " must never take a build from " + pair[1] + " once the dial sign is up,"
                    + " whichever order the registry hands the two shapes back in");
        }
    }

    /**
     * With no sign hung there is nothing in the world telling the two apart, so the answer has
     * to come from somewhere stable rather than from hash order.
     *
     * <p>Asserted as "exactly one of them wins" rather than naming the winner: which one it is
     * does not matter to a player, because a gate with no dial sign is a plain {@code /dial}
     * gate either way. What matters is that the same build does not resolve differently on two
     * servers, or on the same server after someone drops in an unrelated custom shape and
     * resizes the registry's table.
     */
    @Test
    void aBuildWithNoSignStillResolvesTheSameWayEveryTime() throws Exception
    {
        for (final String[] pair : shippedPairs())
        {
            final Stargate3DShape plain = load(pair[0]);
            final Stargate3DShape signDial = load(pair[1]);
            final Stargate plainMatch = matchOf(plain, false);
            final Stargate signDialMatch = matchOf(signDial, false);

            final boolean signDialWins =
                StargateHelper.beatsBestMatch(signDialMatch, signDial, plainMatch, plain);
            final boolean plainWins =
                StargateHelper.beatsBestMatch(plainMatch, plain, signDialMatch, signDial);

            assertNotEquals(signDialWins, plainWins,
                pair[0] + " and " + pair[1] + " disagree about which of them wins, so the shape a"
                    + " player ends up with depends on the order the registry iterates in");
        }
    }

    /**
     * Where the frames differ, the shape that accounts for more of what was built wins.
     *
     * <p>{@code MinimalSignDial} builds two frame blocks {@code Minimal} does not ask for, so it
     * is the better description of that build even with no sign on it. Previously the two agreed
     * only by accident of hashing.
     */
    @Test
    void theShapeThatAccountsForMoreOfTheFrameWins() throws Exception
    {
        final Stargate3DShape plain = load("Minimal");
        final Stargate3DShape signDial = load("MinimalSignDial");

        assertTrue(matchOf(signDial, false).getGateStructureBlocks().size()
            > matchOf(plain, false).getGateStructureBlocks().size(),
            "MinimalSignDial is supposed to be the larger frame; if that stops being true this"
                + " test is no longer exercising the frame-size rule");

        assertTrue(
            StargateHelper.beatsBestMatch(matchOf(signDial, false), signDial, matchOf(plain, false), plain),
            "MinimalSignDial builds blocks Minimal never asks for, so it describes the build better");
    }

    /**
     * The one preference that predates this ranking, kept because a server's own shapes can
     * still be written as redstone twins even though no shipped pair needs it now.
     *
     * <p>It sits below the dial sign and above frame size, so this is checked with neither
     * shape holding a sign — where the flag is the first thing that separates them.
     */
    @Test
    void aRedstoneShapeStillOutranksItsPlainTwin() throws Exception
    {
        final Stargate3DShape plain = load("Standard");
        final Stargate3DShape redstone = load("StandardSignDial");

        assertFalse(plain.isShapeRedstoneActivated(), "Standard is the non-redstone side of this pair");
        assertTrue(redstone.isShapeRedstoneActivated(),
            "StandardSignDial is what makes this pair the fixture for the redstone rule; if it stops"
                + " declaring REDSTONE_ACTIVATED=TRUE this test covers nothing");

        assertTrue(
            StargateHelper.beatsBestMatch(matchOf(redstone, false), redstone, matchOf(plain, false), plain),
            "a redstone shape has to outrank the plain twin its frame fits inside");
    }

    /**
     * No shape can outrank itself, at any step of the ranking.
     *
     * <p>A ranking that says a shape beats an equal one is not an ordering, and the winner then
     * depends on how many other shapes happened to be compared along the way.
     */
    @Test
    void noShippedShapeBeatsAnEqualCopyOfItself() throws Exception
    {
        int checked = 0;
        try (java.util.stream.Stream<Path> listing = Files.list(SHAPE_DIR))
        {
            for (final Path p : listing.toList())
            {
                final String file = p.getFileName().toString();
                if (!file.endsWith(".shape"))
                {
                    continue;
                }
                final Stargate3DShape shape = load(file.substring(0, file.length() - ".shape".length()));
                for (final boolean signFound : new boolean[] { false, true })
                {
                    checked++;
                    assertFalse(
                        StargateHelper.beatsBestMatch(matchOf(shape, signFound), shape,
                            matchOf(shape, signFound), shape),
                        shape.getShapeName() + " outranks itself, so the ranking is not an ordering"
                            + " and the winner depends on what else was compared first");
                }
            }
        }
        assertTrue(checked > 0, "no shapes were found to check, so this proved nothing");
    }
}
