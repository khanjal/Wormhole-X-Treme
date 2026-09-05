package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * What a player is told when the ring they walked into will not fire.
 *
 * <p>The whole reason {@link RingBlockage} has more than one value is that these messages
 * differ. A ring that refuses and cannot say why is indistinguishable from a broken one, and
 * the thing to fix is often at the other end entirely — so each reason has to name a
 * different thing to go and do. A refusal that reported the wrong one would send somebody
 * hunting for a chest that was never there.
 *
 * <p>Chat rather than the action bar for exactly this reason, which is why these can be
 * captured at all: everything else in {@code RingMessages} goes through {@code player.spigot()},
 * which a bare mock cannot answer.
 */
public class RingMessagesTest
{
    /** Everything said to a player when one end turns them away. */
    private static List<String> refusalFor(final RingBlockage why, final String destination)
    {
        final Player player = mock(Player.class);
        RingMessages.cannotReceive(player, destination, why);
        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(player, times(2)).sendMessage(said.capture());
        return said.getAllValues();
    }

    /** The two lines joined, for the cases that only care that a phrase appears somewhere. */
    private static String refusalText(final RingBlockage why)
    {
        return String.join(" ", refusalFor(why, "Base"));
    }

    /**
     * A low ceiling is reported as a low ceiling, not as an obstruction.
     *
     * <p>The distinction this message exists for. Before the headroom rule, a ring in a short
     * room did not refuse at all; the risk in adding the rule was that it would refuse with
     * the old wording and send players looking for a block nobody had placed.
     */
    @Test
    public void aLowCeilingSaysSoRatherThanBlamingSomethingBuiltInTheRing()
    {
        final String text = refusalText(RingBlockage.NO_HEADROOM);
        assertTrue(text.contains("too low a ceiling"),
            "a short room should be reported as a short room: " + text);
        assertTrue(text.contains("4 blocks"),
            "the message should say how much room the rings actually need: " + text);
        assertFalse(text.contains("built"),
            "nothing was built in the ring, so nothing should say so: " + text);
    }

    /** The number in the message comes from the stack, so it cannot drift from the rule. */
    @Test
    public void theHeadroomMessageQuotesTheStacksOwnHeight()
    {
        assertTrue(refusalText(RingBlockage.NO_HEADROOM).contains(String.valueOf(Ring.STACK_HEIGHT)),
            "a hardcoded number here would go stale the moment the animation changed");
    }

    /** Something dropped in the ring is still reported as something dropped in the ring. */
    @Test
    public void somethingBuiltInsideAnEndSaysToClearTheInside()
    {
        final String text = refusalText(RingBlockage.OBSTRUCTED);
        assertTrue(text.contains("built inside it"), text);
        assertTrue(text.contains("What is built around it does not matter"),
            "the follow-up has to say only the inside counts, or players dig up their room: " + text);
    }

    /** A dug-out floor names the floor. */
    @Test
    public void aMissingFloorSaysThereIsAHoleInIt()
    {
        assertTrue(refusalText(RingBlockage.NO_GROUND).contains("hole in its floor"),
            "a hole is a different job from a chest, and gets its own words");
    }

    /** Both ceiling faults get the sentence explaining why a ceiling ring needs a floor. */
    @Test
    public void bothCeilingFaultsExplainWhatACeilingRingNeeds()
    {
        for (final RingBlockage why
            : new RingBlockage[] { RingBlockage.CEILING_TOO_HIGH, RingBlockage.CEILING_TOO_LOW })
        {
            final String text = refusalText(why);
            assertTrue(text.contains("drops its rings to the floor"),
                why + " should explain what a ceiling ring is doing: " + text);
            assertFalse(text.contains("Clear the inside"),
                why + " is not something to clear out of the way: " + text);
        }
    }

    /**
     * The named end is named, and an unnamed one is still pointed at.
     *
     * <p>A pair's two ends are usually nowhere near each other, so which one is broken is the
     * most useful thing the message carries. Naming ends is optional, and a pair where nobody
     * bothered still has to produce a sentence rather than "The  end".
     */
    @Test
    public void aRefusalNamesWhichEndIsAtFault()
    {
        assertTrue(refusalFor(RingBlockage.NO_GROUND, "Tower").get(0).contains("The Tower end"),
            "the far end is usually somewhere else entirely, so it has to be named");
        assertTrue(refusalFor(RingBlockage.NO_GROUND, "").get(0)
            .contains("The other end of these rings"),
            "an unnamed end still has to read as a sentence");
    }

    /** Every reason produces its own wording, so none of them is a copy of another. */
    @Test
    public void everyBlockageReasonReadsDifferently()
    {
        final java.util.Set<String> seen = new java.util.HashSet<String>();
        for (final RingBlockage why : RingBlockage.values())
        {
            seen.add(refusalText(why));
        }
        assertEquals(RingBlockage.values().length, seen.size(),
            "two reasons with the same words would make the enum pointless to a player");
    }
}
