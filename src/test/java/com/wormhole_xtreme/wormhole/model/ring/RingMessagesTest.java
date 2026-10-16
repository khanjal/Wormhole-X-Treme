package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.utils.ActionBar;
import com.wormhole_xtreme.wormhole.utils.ChatText;

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
class RingMessagesTest
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

    /** The two lines joined as a player reads them, for the cases that only care that a phrase appears somewhere. */
    private static String refusalText(final RingBlockage why)
    {
        return ChatText.plain(String.join(" ", refusalFor(why, "Base")));
    }

    /**
     * A low ceiling is reported as a low ceiling, not as an obstruction.
     *
     * <p>The distinction this message exists for. Before the headroom rule, a ring in a short
     * room did not refuse at all; the risk in adding the rule was that it would refuse with
     * the old wording and send players looking for a block nobody had placed.
     */
    @Test
    void aLowCeilingSaysSoRatherThanBlamingSomethingBuiltInTheRing()
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
    void theHeadroomMessageQuotesTheStacksOwnHeight()
    {
        assertTrue(refusalText(RingBlockage.NO_HEADROOM).contains(String.valueOf(Ring.STACK_HEIGHT)),
            "a hardcoded number here would go stale the moment the animation changed");
    }

    /** Something dropped in the ring is still reported as something dropped in the ring. */
    @Test
    void somethingBuiltInsideAnEndSaysToClearTheInside()
    {
        final String text = refusalText(RingBlockage.OBSTRUCTED);
        assertTrue(text.contains("built inside it"), text);
        assertTrue(text.contains("What is built around it does not matter"),
            "the follow-up has to say only the inside counts, or players dig up their room: " + text);
    }

    /** A dug-out floor names the floor. */
    @Test
    void aMissingFloorSaysThereIsAHoleInIt()
    {
        assertTrue(refusalText(RingBlockage.NO_GROUND).contains("hole in its floor"),
            "a hole is a different job from a chest, and gets its own words");
    }

    /** Both ceiling faults get the sentence explaining why a ceiling ring needs a floor. */
    @Test
    void bothCeilingFaultsExplainWhatACeilingRingNeeds()
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
    void aRefusalNamesWhichEndIsAtFault()
    {
        assertTrue(ChatText.plain(refusalFor(RingBlockage.NO_GROUND, "Tower").get(0)).contains("The Tower end"),
            "the far end is usually somewhere else entirely, so it has to be named");
        assertTrue(refusalFor(RingBlockage.NO_GROUND, "").get(0)
            .contains("The other end of these rings"),
            "an unnamed end still has to read as a sentence");
    }

    /** The end at fault is a name, in the palette's aqua, and the line goes back to grey after it. */
    @Test
    void theEndAtFaultIsColouredAsAName()
    {
        assertEquals(ChatText.ERROR_HEADER + "The " + ChatText.NAME_COLOUR + "Tower" + ChatText.BODY_COLOUR
            + " end has a hole in its floor.", refusalFor(RingBlockage.NO_GROUND, "Tower").get(0));
    }

    /** Every reason produces its own wording, so none of them is a copy of another. */
    @Test
    void everyBlockageReasonReadsDifferently()
    {
        final Set<String> seen = new HashSet<String>();
        for (final RingBlockage why : RingBlockage.values())
        {
            seen.add(refusalText(why));
        }
        assertEquals(RingBlockage.values().length, seen.size(),
            "two reasons with the same words would make the enum pointless to a player");
    }

    /**
     * A ring refusal opens with the same {@code :: error ::} header a gate refusal does.
     *
     * <p>It used to be {@code ::} and then dark purple body text, so the same kind of message
     * looked different depending on what refused.
     */
    @Test
    void everyRefusalOpensWithTheGateErrorHeader()
    {
        for (final RingBlockage why : RingBlockage.values())
        {
            for (final String line : refusalFor(why, "Base"))
            {
                assertTrue(line.startsWith(ChatText.ERROR_HEADER),
                    why + " should open with the gate error header, not " + line);
                assertFalse(ChatText.plain(line).isEmpty());
            }
        }
    }

    /**
     * The action-bar lines carry the plugin's header, and the destination and seconds in their
     * palette colours.
     *
     * <p>These replace themselves above the hotbar, so the header is the only thing marking them
     * as the plugin's rather than the server's. {@link ActionBar} is mocked: the real one goes
     * through {@code player.spigot()}, which a bare mock cannot answer.
     */
    @Test
    void theActionBarLinesOpenWithTheHeaderAndColourTheirParts()
    {
        final Player player = mock(Player.class);
        try (MockedStatic<ActionBar> bar = mockStatic(ActionBar.class))
        {
            RingMessages.engaged(player, "Tower");
            RingMessages.engaged(player, "");
            RingMessages.stoodDown(player);
            RingMessages.committed(List.of(new BukkitRingPassenger(player)));
            RingMessages.recharging(player, 2500L);
            RingMessages.recharging(player, 400L);
            RingMessages.busy(player);

            bar.verify(() -> ActionBar.send(player, ChatText.HEADER + "Transport rings engaging — travelling to "
                + ChatText.NAME_COLOUR + "Tower" + ChatText.BODY_COLOUR + ". Step clear to cancel."));
            bar.verify(() -> ActionBar.send(player, ChatText.info("Transport rings engaging. Step clear to cancel.")));
            bar.verify(() -> ActionBar.send(player, ChatText.info("Transport rings powering down.")));
            bar.verify(() -> ActionBar.send(player, ChatText.HEADING_COLOUR + ":: " + ChatText.NAME_COLOUR
                + "Rings deploying. Hold still."));
            bar.verify(() -> ActionBar.send(player, ChatText.HEADER + "Rings recharging. Ready in "
                + ChatText.COMMAND_COLOUR + "3" + ChatText.BODY_COLOUR + " seconds."));
            bar.verify(() -> ActionBar.send(player, ChatText.info("Rings recharging. Ready in "
                + ChatText.value("1") + " second.")));
            bar.verify(() -> ActionBar.send(player, ChatText.info("Rings are already in use.")));
        }
    }

    /** The private-ring refusal goes to chat under the same error header. */
    @Test
    void aPrivateRingRefusalOpensWithTheErrorHeaderToo()
    {
        final Player player = mock(Player.class);
        RingMessages.notYours(player);
        verify(player).sendMessage(ChatText.error("These transport rings are private."));
    }
}
