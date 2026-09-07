package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.beam.BeamDestination;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;

/**
 * Tab completion for {@code /wormhole beam}, which is the deepest completion tree here.
 *
 * <p>It dispatches on a noun, then on an action, then on how many words have been typed --
 * and the position that matters differs between actions, because {@code send} carries an
 * extra token (the player being moved) ahead of its own destination. Nothing covered any of
 * it, so these pin what each position offers today.
 *
 * <p>The trailing {@code [world]} slots are the fiddliest part: goto's sits at index 6 and
 * send's at index 7, and getting those one apart is the whole reason they are worth a test.
 */
class BeamTabCompletionTest
{
    private static List<String> complete(final String... args)
    {
        return SubCommands.find("beam").completeArgs(args);
    }

    /** The nouns the command understands, and nothing else. */
    @Test
    void theNounsAreOffered()
    {
        final List<String> nouns = complete("beam", "");

        assertTrue(nouns.contains("to"));
        assertTrue(nouns.contains("list"));
        assertTrue(nouns.contains("admin"));
        assertTrue(nouns.contains("place"));
        assertEquals(4, nouns.size(), "no other noun is offered: " + nouns);
    }

    /** What has been typed narrows them. */
    @Test
    void aTypedPrefixNarrowsTheNouns()
    {
        assertEquals(Collections.singletonList("admin"), complete("beam", "a"));
        assertTrue(complete("beam", "p").contains("place"));
        assertFalse(complete("beam", "p").contains("admin"));
    }

    /** The admin actions. */
    @Test
    void theAdminActionsAreOffered()
    {
        final List<String> actions = complete("beam", "admin", "");

        assertTrue(actions.containsAll(Arrays.asList("set", "remove", "cost", "goto", "send")),
            "got " + actions);
        assertEquals(5, actions.size(), "no other admin action is offered: " + actions);
    }

    /** The place actions. */
    @Test
    void thePlaceActionsAreOffered()
    {
        final List<String> actions = complete("beam", "place", "");

        assertTrue(actions.containsAll(Arrays.asList("list", "set", "remove")), "got " + actions);
        assertEquals(3, actions.size(), "no other place action is offered: " + actions);
    }

    /** Past its action, place offers nothing -- a player's own places are never listed. */
    @Test
    void placeOffersNothingBeyondItsAction()
    {
        assertTrue(complete("beam", "place", "remove", "").isEmpty(),
            "a completer cannot see who is asking, so it cannot look up their places");
    }

    /** {@code cost}'s second argument is the one word that is not a number. */
    @Test
    void costOffersDefaultInItsValueSlot()
    {
        assertEquals(Collections.singletonList("default"),
            complete("beam", "admin", "cost", "somewhere", ""));
    }

    /** A noun the command does not know completes to nothing rather than guessing. */
    @Test
    void anUnknownNounOffersNothing()
    {
        assertTrue(complete("beam", "nonsense", "").isEmpty());
        assertTrue(complete("beam", "list", "").isEmpty(), "list takes no arguments");
    }

    /** {@code to} offers destinations in its one slot and nothing after it. */
    @Test
    void toOffersNothingPastItsDestination()
    {
        assertTrue(complete("beam", "to", "somewhere", "").isEmpty());
    }

    /**
     * The trailing world slots, which sit one apart.
     *
     * <p>goto's destination is a single token, so its optional [world] lands at index 6.
     * send's is preceded by the player being moved, so its world lands at index 7. Offering
     * either at the other's position would be offering a world where a coordinate goes.
     */
    @Test
    void theWorldSlotsSitOneApartForGotoAndSend()
    {
        final World world = mock(World.class);
        when(world.getName()).thenReturn("nether");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::getWorlds).thenReturn(Collections.singletonList(world));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Collections.emptyList());

            // goto: beam admin goto <dest> <x> <y> <z> -- world at index 6
            assertEquals(Collections.singletonList("nether"),
                complete("beam", "admin", "goto", "1", "2", "3", ""));
            assertTrue(complete("beam", "admin", "goto", "1", "2", "3", "4", "").isEmpty(),
                "goto has no slot at index 7");

            // send: beam admin send <player> <dest> <x> <y> <z> -- world at index 7
            assertEquals(Collections.singletonList("nether"),
                complete("beam", "admin", "send", "bob", "1", "2", "3", ""));
        }
    }

    /** {@code remove} and {@code cost} name an existing public destination. */
    @Test
    void removeAndCostOfferPublicDestinations()
    {
        final BeamDestination spawn = mock(BeamDestination.class);
        when(spawn.getName()).thenReturn("spawn");
        final BeamDestination market = mock(BeamDestination.class);
        when(market.getName()).thenReturn("market");

        try (MockedStatic<BeamManager> beams = mockStatic(BeamManager.class))
        {
            beams.when(BeamManager::getAllPublicDestinations)
                .thenReturn(Arrays.asList(spawn, market));

            assertEquals(Arrays.asList("spawn", "market"), complete("beam", "admin", "remove", ""));
            assertEquals(Collections.singletonList("market"),
                complete("beam", "admin", "cost", "m"));
            assertEquals(Arrays.asList("spawn", "market"), complete("beam", "to", ""),
                "to offers the same public names");
        }
    }

    /**
     * send names a player to move; goto names somewhere to go.
     *
     * <p>The two look alike -- both take one word in the slot right after the action -- but
     * they are different in kind. A destination name in send's slot would be meaningless,
     * because there is nobody called "spawn" to pick up and move. goto has no such problem,
     * so it offers both players and places.
     */
    @Test
    void sendNamesAPlayerWhereGotoNamesAnywhere()
    {
        final org.bukkit.entity.Player bob = mock(org.bukkit.entity.Player.class);
        when(bob.getName()).thenReturn("bob");
        final BeamDestination spawn = mock(BeamDestination.class);
        when(spawn.getName()).thenReturn("spawn");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<BeamManager> beams = mockStatic(BeamManager.class))
        {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Collections.singletonList(bob));
            beams.when(BeamManager::getAllPublicDestinations)
                .thenReturn(Collections.singletonList(spawn));

            final List<String> forSend = complete("beam", "admin", "send", "");
            assertEquals(Collections.singletonList("bob"), forSend,
                "only somebody who can be moved belongs in send's first slot");

            final List<String> forGoto = complete("beam", "admin", "goto", "");
            assertTrue(forGoto.contains("bob"));
            assertTrue(forGoto.contains("spawn"), "goto may name a place as well as a player");
        }
    }
}
