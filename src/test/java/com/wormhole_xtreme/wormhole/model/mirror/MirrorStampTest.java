package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.DyeColor;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.banner.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Putting a look onto a banner.
 *
 * <p>What matters here is the layering. The sampled colours go on first and the preset's frame
 * over them, the whole thing stops at six, and indoors the commonest colour becomes the cloth
 * rather than a square on it -- because a brown square on brown cloth is not a square.
 *
 * <h2>Why half of this is behind an assumption</h2>
 *
 * <p>From 1.21 on, {@code PatternType} resolves through {@code Registry}, whose initialiser
 * needs a running server -- so in a unit test against a 1.21 jar no pattern can be built at
 * all, whatever the code does. Every test whose assertions are about the pattern <em>list</em>
 * therefore checks {@link #patternsAvailable()} first and skips where the API cannot be
 * exercised. The version matrix runs 1.20, 1.20.1, 1.20.4, 1.20.6, 1.21.1, 1.21.4 and 1.21.10,
 * so those assertions still run in full on four of the seven rows, the 1.20.4 compile target
 * among them.
 *
 * <p>What does <em>not</em> hide behind the assumption is the behaviour that matters when
 * patterns cannot be resolved: the banner is still dyed, and the stamp still reports success.
 * {@link #stampsTheClothWhenNoPatternResolves} asserts exactly that, on every version, and it
 * is the case the 1.21 rows were failing on before {@code LinkageError} was caught.
 */
class MirrorStampTest
{
    private Block block;
    private Banner banner;

    @BeforeEach
    void bannerBlock()
    {
        block = mock(Block.class);
        banner = mock(Banner.class);
        when(block.getState()).thenReturn(banner);
    }

    /**
     * Whether this server can build a pattern at all.
     *
     * <p>False on a 1.21 jar outside a running server, and true everywhere else. Asking through
     * {@link MirrorStamp#patternType} rather than touching {@code PatternType} directly, because
     * touching it is the thing that throws.
     *
     * @return true if pattern assertions can mean anything here
     */
    private static boolean patternsAvailable()
    {
        return MirrorStamp.patternType("BORDER") != null;
    }

    @Test
    @DisplayName("a preset alone dyes the banner and lays on its own layers")
    void presetAlone()
    {
        assumeTrue(patternsAvailable(),
            "this jar cannot build a pattern outside a running server");
        assertTrue(MirrorStamp.apply(block, preset("RED", "BLACK BORDER", "ORANGE CROSS")));

        verify(banner).setBaseColor(DyeColor.RED);
        final List<Pattern> patterns = captured();
        assertEquals(2, patterns.size());
        assertEquals(DyeColor.BLACK, patterns.get(0).getColor());
        assertEquals(DyeColor.ORANGE, patterns.get(1).getColor());
        verify(banner).update(anyBoolean());
    }

    @Test
    @DisplayName("the sampled squares go on under the preset's frame")
    void viewGoesUnderTheFrame()
    {
        assumeTrue(patternsAvailable(),
            "this jar cannot build a pattern outside a running server");
        final MirrorView view =
            new MirrorView("PLAINS", List.of(DyeColor.GREEN, DyeColor.BLUE), false);

        assertTrue(MirrorStamp.apply(block, preset("LIME", "BLACK BORDER"), view));

        verify(banner).setBaseColor(DyeColor.LIME);
        final List<Pattern> patterns = captured();
        assertEquals(3, patterns.size(), "two squares and the border");
        assertEquals(DyeColor.GREEN, patterns.get(0).getColor(), "commonest colour first");
        assertEquals(DyeColor.BLUE, patterns.get(1).getColor());
        assertEquals(DyeColor.BLACK, patterns.get(2).getColor(), "the frame goes on last");
    }

    @Test
    @DisplayName("indoors the commonest block becomes the cloth, not a square on it")
    void indoorsDyesTheCloth()
    {
        assumeTrue(patternsAvailable(),
            "this jar cannot build a pattern outside a running server");
        final MirrorView library =
            new MirrorView("PLAINS", List.of(DyeColor.BROWN, DyeColor.GRAY), true);

        MirrorStamp.apply(block, preset("YELLOW", "BLACK BORDER"), library);

        verify(banner).setBaseColor(DyeColor.BROWN);
        verify(banner, never()).setBaseColor(DyeColor.YELLOW);
        final List<Pattern> patterns = captured();
        assertEquals(2, patterns.size(), "the walls as a square, and the border");
        assertEquals(DyeColor.GRAY, patterns.get(0).getColor(),
            "brown is already the cloth, so the square is the next colour down");
        assertEquals(DyeColor.BLACK, patterns.get(1).getColor());
    }

    @Test
    @DisplayName("indoors with nothing sampled falls back to the preset's own colour")
    void indoorsWithNothingToSee()
    {
        assumeTrue(patternsAvailable(),
            "this jar cannot build a pattern outside a running server");
        MirrorStamp.apply(block, preset("YELLOW", "BLACK BORDER"),
            new MirrorView("PLAINS", List.of(), true));

        verify(banner).setBaseColor(DyeColor.YELLOW);
        assertEquals(1, captured().size());
    }

    @Test
    @DisplayName("never more than the six a banner can show")
    void stopsAtSix()
    {
        assumeTrue(patternsAvailable(),
            "this jar cannot build a pattern outside a running server");
        final MirrorView view = new MirrorView("PLAINS",
            List.of(DyeColor.RED, DyeColor.BLUE, DyeColor.LIME), false);

        MirrorStamp.apply(block, preset("WHITE", "BLACK BORDER", "GRAY CROSS",
            "PINK FLOWER", "CYAN SKULL", "PURPLE BRICKS"), view);

        assertEquals(6, captured().size(), "three squares and three of the five frame layers");
    }

    @Test
    @DisplayName("only three colours are drawn however many were sampled")
    void threeSquaresAtMost()
    {
        assumeTrue(patternsAvailable(),
            "this jar cannot build a pattern outside a running server");
        final MirrorView view = new MirrorView("PLAINS",
            List.of(DyeColor.RED, DyeColor.BLUE, DyeColor.LIME, DyeColor.PINK), false);

        MirrorStamp.apply(block, preset("WHITE"), view);

        assertEquals(3, captured().size());
    }

    @Test
    @DisplayName("a pattern this server does not have is skipped, not fatal")
    void skipsUnknownPatterns()
    {
        assumeTrue(patternsAvailable(),
            "this jar cannot build a pattern outside a running server");
        assertTrue(MirrorStamp.apply(block,
            preset("RED", "BLACK NO_SUCH_PATTERN", "ORANGE BORDER")));

        final List<Pattern> patterns = captured();
        assertEquals(1, patterns.size(), "the good layer should still be there");
        assertEquals(DyeColor.ORANGE, patterns.get(0).getColor());
    }

    /**
     * The case that broke three CI rows, and the reason {@code LinkageError} is caught.
     *
     * <p>No assumption on this one, deliberately -- it has to run on every version, because the
     * versions where no pattern resolves are exactly the ones it is about. Names that resolve
     * nowhere stand in for a whole API that resolves nothing: the banner still gets dyed, the
     * stamp still reports success, and nothing escapes to the command handler.
     */
    @Test
    @DisplayName("with no pattern resolvable at all, the cloth is still dyed and it still works")
    void stampsTheClothWhenNoPatternResolves()
    {
        assertTrue(MirrorStamp.apply(block,
            preset("RED", "BLACK NO_SUCH_PATTERN", "ORANGE ALSO_NOT_A_PATTERN")),
            "a banner with no patterns on it is still a stamped banner");

        verify(banner).setBaseColor(DyeColor.RED);
        verify(banner).update(anyBoolean());
        assertTrue(captured().isEmpty(), "nothing resolved, so nothing was laid on");
    }

    @Test
    @DisplayName("a block that is not a banner is left alone")
    void notABanner()
    {
        final BlockState notABanner = mock(BlockState.class);
        when(block.getState()).thenReturn(notABanner);

        assertFalse(MirrorStamp.apply(block, preset("RED", "BLACK BORDER")));
    }

    @Test
    @DisplayName("nothing to stamp, or nothing to stamp with, is a no")
    void nothingToDo()
    {
        assertFalse(MirrorStamp.apply(null, preset("RED")));
        assertFalse(MirrorStamp.apply(block, null));
        verify(banner, never()).update(anyBoolean());
    }

    @Test
    @DisplayName("pattern names resolve across the versions where PatternType changed kind")
    void resolvesPatternNames()
    {
        assumeTrue(patternsAvailable(),
            "this jar cannot build a pattern outside a running server");
        assertNotNull(MirrorStamp.patternType("BORDER"),
            "BORDER is present on every supported version");
        assertNull(MirrorStamp.patternType("NO_SUCH_PATTERN"));
        assertNull(MirrorStamp.patternType(null));
        assertNull(MirrorStamp.patternType(""));
        assertEquals(MirrorStamp.patternType("BORDER"), MirrorStamp.patternType("BORDER"),
            "a second lookup should come back from the cache with the same answer");
    }

    /** The patterns the banner was given. */
    private List<Pattern> captured()
    {
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<Pattern>> captor = ArgumentCaptor.forClass(List.class);
        verify(banner).setPatterns(captor.capture());
        return captor.getValue();
    }

    /** A preset with a base colour and any number of "COLOUR PATTERN" layers. */
    private static MirrorPreset preset(final String base, final String... layers)
    {
        final List<String> lines = new java.util.ArrayList<>();
        lines.add("Base=" + base);
        for (final String layer : layers)
        {
            lines.add("Layer=" + layer);
        }
        return MirrorPreset.parse("test", lines);
    }
}
