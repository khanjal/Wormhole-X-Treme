package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingBlockage;

/**
 * A room that cannot work is refused when the ring is laid, not when somebody stands in it.
 *
 * <p>The survey always existed, but only ran at use time. So a builder could lay a ceiling
 * ring over a room too deep for it, pair it, and hear nothing wrong until somebody walked
 * underneath -- at which point the rings fire, carry nobody, and fire again, and the person
 * seeing it is not the person who built it and cannot fix the room from inside the cycle.
 * {@code RingCommand.create} now runs the same survey before it accepts the circle.
 *
 * <p>These cover the wording rather than the geometry, which
 * {@code RingSurveyTest} already pins against a map of blocks. A refusal that does not say
 * which of the five things is wrong sends the builder to dig in the wrong place, and all five
 * are things a person fixes differently.
 */
class RingBuildCheckTest
{
    /**
     * The four refusals that are facts about the rings rather than about config.
     *
     * <p>{@link RingBlockage#CEILING_TOO_HIGH} is deliberately not here: its message quotes
     * {@code ring.max-ceiling-drop}, so it reads config and cannot be built without a loaded
     * server. The branch is reached on a server and nowhere else, which is the trade for
     * naming the actual limit in the text.
     */
    private static final RingBlockage[] CONFIG_FREE = {
        RingBlockage.CEILING_TOO_LOW,
        RingBlockage.NO_HEADROOM,
        RingBlockage.NO_GROUND,
        RingBlockage.OBSTRUCTED,
    };

    /** Every refusal says something, and none of them says nothing. */
    @Test
    void everyRefusalExplainsItself()
    {
        for (final RingBlockage blockage : CONFIG_FREE)
        {
            final String said = RingCommand.explain(blockage);

            assertTrue((said != null) && (said.length() > 30),
                blockage + " was refused with nothing useful: " + said);
            assertTrue(said.endsWith(".") || said.endsWith("matter."),
                blockage + " should be a finished sentence, got: " + said);
        }
    }

    /**
     * The five refusals are told apart.
     *
     * <p>The point of surveying at build time is that the builder knows what to change. Two
     * blockages sharing one message would mean digging out a ring that was fine and leaving a
     * ceiling that was not.
     */
    @Test
    void noTwoRefusalsGiveTheSameAdvice()
    {
        final Set<String> seen = new HashSet<>();
        final List<String> repeats = new ArrayList<>();
        for (final RingBlockage blockage : CONFIG_FREE)
        {
            if (!seen.add(RingCommand.explain(blockage)))
            {
                repeats.add(blockage.name());
            }
        }

        assertTrue(repeats.isEmpty(),
            "these refusals repeat another one's wording, so the builder cannot tell which "
                + "thing to fix: " + repeats);
        assertNotEquals(0, seen.size(), "nothing was explained, so this proved nothing");
    }

    /**
     * The two ceiling refusals quote the numbers a builder has to build to.
     *
     * <p>"Too low" without saying how low is the complaint that sends somebody to the wiki.
     */
    @Test
    void theCeilingRefusalsQuoteTheRealMeasurements()
    {
        final String tooLow = RingCommand.explain(RingBlockage.CEILING_TOO_LOW);
        assertTrue(tooLow.contains(String.valueOf(Ring.MIN_CEILING_DROP)),
            "should name the " + Ring.MIN_CEILING_DROP + " blocks a ceiling ring needs below "
                + "it, got: " + tooLow);

        final String noRoom = RingCommand.explain(RingBlockage.NO_HEADROOM);
        assertTrue(noRoom.contains(String.valueOf(Ring.STACK_HEIGHT)),
            "should name the " + Ring.STACK_HEIGHT + " blocks the stack stands, got: " + noRoom);
    }
}
