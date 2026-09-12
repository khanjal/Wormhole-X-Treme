package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.bukkit.DyeColor;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Whether being enclosed over there means the far side is a room.
 *
 * <p>The sampler can only say that more than half of what surrounds a destination is solid.
 * Three separate decisions took that as meaning "a building" -- which look to wear, whether to
 * replace the look's colour with the commonest block, and whether to skip that block's square
 * -- and a fourth said it in words to the operator. Only the first of the four asked whether
 * the place is enclosed <em>by its nature</em>, so a mirror onto the Nether was dressed as
 * somebody's library by every path except one.
 *
 * <p>Built from hand-made views rather than sampled ones on purpose. Reading a real biome means
 * touching {@code Biome}, which is an enum through 1.21.1 and a registry-backed interface from
 * 1.21.4, whose constants on the later versions need a running server to initialise. A test
 * that named one would pass here and fail three rows of the version matrix -- the same trap
 * {@code MirrorViewTest} documents avoiding.
 */
class MirrorShelteredTest
{
    /** A look for somewhere enclosed by its nature, like the Nether or a cave. */
    private static final MirrorPreset SHELTERED =
        new MirrorPreset("nether", DyeColor.RED, List.of(), Set.of("NETHER_WASTES"), true);

    /** A look for somewhere that is normally open to the sky. */
    private static final MirrorPreset OPEN =
        new MirrorPreset("forest", DyeColor.GREEN, List.of(), Set.of("FOREST"), false);

    private Block block;
    private Banner banner;

    @BeforeEach
    void bannerBlock()
    {
        block = mock(Block.class);
        banner = mock(Banner.class);
        when(block.getState()).thenReturn(banner);
    }

    /** Solid all round, and the commonest thing there is brown. */
    private static MirrorView enclosed(final String biome)
    {
        return new MirrorView(biome, List.of(DyeColor.BROWN, DyeColor.GRAY), true);
    }

    /** Open sky, same colours. */
    private static MirrorView open(final String biome)
    {
        return new MirrorView(biome, List.of(DyeColor.BROWN, DyeColor.GRAY), false);
    }

    /**
     * The case the whole flag exists for.
     *
     * <p>The Nether is solid rock with a ceiling on it, so every mirror ever pointed there is
     * sampled as enclosed. Reading that as "a room" would mean no mirror into the Nether could
     * ever wear the Nether's look.
     */
    @Test
    void somewhereEnclosedByItsNatureIsNotARoom()
    {
        assertFalse(SHELTERED.readsAsARoom(enclosed("NETHER_WASTES")),
            "the Nether is enclosed whoever points at it, so that says nothing");
    }

    /** A building in a forest is a room, which is the case the rule was written for. */
    @Test
    void somewhereEnclosedThatHasNoBusinessBeingIsARoom()
    {
        assertTrue(OPEN.readsAsARoom(enclosed("FOREST")),
            "a roof in a forest is somebody's building");
    }

    /** Out of doors is never a room, sheltered look or not. */
    @Test
    void openSkyIsNeverARoom()
    {
        assertFalse(OPEN.readsAsARoom(open("FOREST")));
        assertFalse(SHELTERED.readsAsARoom(open("NETHER_WASTES")));
    }

    /** A look chosen by name carries no view, and must not be read as a room on a null. */
    @Test
    void aLookWithNothingSampledIsNotARoom()
    {
        assertFalse(OPEN.readsAsARoom(null),
            "a named look has no view, and null must not throw or read as indoors");
    }

    /**
     * The banner keeps a sheltered look's own colour.
     *
     * <p>This is the half that was wrong on <em>every</em> path, the otherwise-correct one
     * included. The look was picked with the rule and the cloth was then coloured without it,
     * so a mirror onto the Nether wore the Nether's frame over whatever netherrack averaged to
     * in that sample -- the red the preset exists to be, thrown away at the last step.
     */
    @Test
    void aShelteredLookKeepsItsOwnColourRatherThanTheCommonestBlock()
    {
        MirrorStamp.apply(block, SHELTERED, enclosed("NETHER_WASTES"));

        verify(banner).setBaseColor(DyeColor.RED);
    }

    /** A real room still reads by its contents, which is what the indoor rule is for. */
    @Test
    void aRoomStillTakesItsColourFromWhatIsInIt()
    {
        MirrorStamp.apply(block, OPEN, enclosed("FOREST"));

        verify(banner).setBaseColor(DyeColor.BROWN);
    }

    /** Out of doors the look's own colour stands, which never changed. */
    @Test
    void openSkyKeepsTheLooksOwnColour()
    {
        MirrorStamp.apply(block, OPEN, open("FOREST"));

        verify(banner).setBaseColor(DyeColor.GREEN);
    }

}
