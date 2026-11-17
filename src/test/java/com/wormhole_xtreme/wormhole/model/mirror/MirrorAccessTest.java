package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.window.BlockPlace;
import com.wormhole_xtreme.wormhole.model.window.Place;

/**
 * Mirror networks (#280): named groups, and hidden and private mirrors.
 *
 * <p>A server with a museum wing and a survival hub wants the museum's mirrors to list only
 * museum worlds; a return mirror should be reachable by its start without being browsed; and a
 * mirror onto a staff world should be listed only for staff. Before this, a right-click listed
 * every mirror on the server for everybody, and these tests are what keeps each of those three
 * from quietly listing everything again. None of it may cost a mirror its start.
 */
class MirrorAccessTest
{
    private Player visitor;
    private Player staff;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.clear();
        MirrorManager.clear();
        MirrorNetwork.clear();
        visitor = mock(Player.class);
        staff = mock(Player.class);
        when(staff.hasPermission(MirrorNetwork.SEE_PRIVATE)).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorNetwork.clear();
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** A mirror storing its own room, with the given access, registered. */
    private static QuantumMirror mirror(final String name, final MirrorAccess access)
    {
        final int x = Math.abs(name.hashCode() % 1000);
        final QuantumMirror mirror = new QuantumMirror(name, new BlockPlace("world", x, 64, 10),
            new Place("world", x + 0.5, 63, 10.5, 180f, 0f)).withAccess(access);
        MirrorManager.add(mirror);
        return mirror;
    }

    private static List<String> names(final List<QuantumMirror> mirrors)
    {
        return mirrors.stream().map(QuantumMirror::name).toList();
    }

    /** Every mirror made before groups is on the public network and lists all the others, as it did. */
    @Test
    void ungroupedMirrorsListEveryOtherUngroupedOne()
    {
        final QuantumMirror hub = mirror("hub", MirrorAccess.OPEN);
        mirror("farm", MirrorAccess.OPEN);
        mirror("mine", MirrorAccess.OPEN);

        assertEquals(List.of("farm", "mine"), names(MirrorNetwork.order(hub, visitor)));
    }

    /**
     * A grouped mirror lists only its own group, ignoring case, and an ungrouped one none of them.
     *
     * <p>Both halves matter: the museum's mirrors must not list the hub, and the hub must not list
     * the museum's either, or a group would be a one-way door.
     */
    @Test
    void aGroupListsOnlyItselfAndThePublicNetworkListsNoGroup()
    {
        final QuantumMirror hub = mirror("hub", MirrorAccess.OPEN);
        mirror("farm", MirrorAccess.OPEN);
        final QuantumMirror lobby = mirror("lobby", new MirrorAccess("Museum", false, false));
        mirror("gallery", new MirrorAccess("museum", false, false));
        mirror("vault", new MirrorAccess("bank", false, false));

        assertEquals(List.of("gallery"), names(MirrorNetwork.order(lobby, visitor)),
            "the museum's lobby lists only the museum, whatever case the group was typed in");
        assertEquals(List.of("farm"), names(MirrorNetwork.order(hub, visitor)),
            "the public hub lists no grouped mirror");
    }

    /** A hidden mirror is in nobody's list, not even a holder of the private node's. */
    @Test
    void aHiddenMirrorIsInNobodysList()
    {
        final QuantumMirror hub = mirror("hub", MirrorAccess.OPEN);
        mirror("farm", MirrorAccess.OPEN);
        mirror("return", new MirrorAccess(null, true, false));

        assertEquals(List.of("farm"), names(MirrorNetwork.order(hub, visitor)));
        assertEquals(List.of("farm"), names(MirrorNetwork.order(hub, staff)));
    }

    /** A private mirror is listed for a holder of the node, and for nobody else. */
    @Test
    void aPrivateMirrorIsListedOnlyForAHolderOfTheNode()
    {
        final QuantumMirror hub = mirror("hub", MirrorAccess.OPEN);
        mirror("farm", MirrorAccess.OPEN);
        mirror("staff", new MirrorAccess(null, false, true));

        assertEquals(List.of("farm"), names(MirrorNetwork.order(hub, visitor)));
        assertEquals(List.of("farm"), names(MirrorNetwork.order(hub, null)), "nobody in particular sees none");
        assertEquals(List.of("farm", "staff"), names(MirrorNetwork.order(hub, staff)));
    }

    /**
     * A mirror's start is first whatever its group or flags: that is how a hidden return is reached.
     *
     * <p>The start is chosen by whoever runs the command, so listing it is their decision, not
     * the browser's.
     */
    @Test
    void aStartIsReachableWhateverItsGroupOrFlags()
    {
        mirror("farm", MirrorAccess.OPEN);
        mirror("return", new MirrorAccess("elsewhere", true, true));
        final QuantumMirror hub = mirror("hub", MirrorAccess.OPEN).withStart("return");
        MirrorManager.add(hub);

        assertEquals(List.of("return", "farm"), names(MirrorNetwork.order(hub, visitor)));
        MirrorNetwork.scroll(hub, false, visitor);
        assertTrue(MirrorNetwork.reachable(hub, visitor), "a punch may go to the start it opened onto");
    }

    /**
     * A mirror turned to a private one is not reachable by a player who could not list it.
     *
     * <p>The choice is shared by everybody at the mirror, so the punch has to ask again.
     */
    @Test
    void aChoiceSomebodyElseMadeIsReachableOnlyIfListedForThePuncher()
    {
        final QuantumMirror hub = mirror("hub", MirrorAccess.OPEN);
        mirror("staff", new MirrorAccess(null, false, true));
        assertTrue(MirrorNetwork.reachable(hub, visitor), "its own room is always reachable");

        MirrorNetwork.scroll(hub, false, staff);

        assertEquals("staff", MirrorNetwork.chosen(hub).name());
        assertTrue(MirrorNetwork.reachable(hub, staff));
        assertFalse(MirrorNetwork.reachable(hub, visitor));
    }

    /**
     * A right-click skips a private mirror while somebody without the node is at the mirror.
     *
     * <p>The view is drawn for everybody in range, so a staff member choosing the staff room
     * beside a visitor would show the visitor that room.
     */
    @Test
    void aRightClickSkipsAPrivateMirrorWhileSomebodyWithoutTheNodeIsAtIt()
    {
        final QuantumMirror hub = mirror("hub", MirrorAccess.OPEN);
        mirror("staff", new MirrorAccess(null, false, true));
        mirror("zoo", MirrorAccess.OPEN);

        MirrorNetwork.scroll(hub, List.of(visitor), staff);
        assertEquals("zoo", MirrorNetwork.chosen(hub).name(), "staff is skipped with a visitor beside");

        MirrorNetwork.clear();
        MirrorNetwork.scroll(hub, List.of(staff), staff);
        assertEquals("staff", MirrorNetwork.chosen(hub).name(), "listed when everybody there holds the node");
    }

    /** A start is still chosen with a visitor beside: whoever set it chose it for everybody. */
    @Test
    void aPrivateStartIsChosenWhoeverIsAtTheMirror()
    {
        mirror("staff", new MirrorAccess(null, false, true));
        final QuantumMirror hub = mirror("hub", MirrorAccess.OPEN).withStart("staff");
        MirrorManager.add(hub);

        MirrorNetwork.scroll(hub, List.of(visitor), visitor);

        assertEquals("staff", MirrorNetwork.chosen(hub).name());
    }

    /**
     * A mirror turned to a private room goes back to its own when somebody without the node walks up.
     *
     * <p>Checked by the sweep before the view is drawn, which is what stops a late arrival seeing
     * a room they could not choose.
     */
    @Test
    void aPrivateChoiceIsDroppedWhenSomebodyWithoutTheNodeComesNear()
    {
        final QuantumMirror hub = mirror("hub", MirrorAccess.OPEN);
        mirror("staff", new MirrorAccess(null, false, true));
        MirrorNetwork.scroll(hub, false, staff);
        final World world = mock(World.class);
        nearHub(hub, world, staff);

        assertFalse(MirrorNetwork.keepToWhatAllMaySee(hub, world), "only the staff member is near");
        assertEquals("staff", MirrorNetwork.chosen(hub).name());

        nearHub(hub, world, staff, visitor);

        assertTrue(MirrorNetwork.keepToWhatAllMaySee(hub, world));
        assertEquals("hub", MirrorNetwork.chosen(hub).name(), "back to its own room");
    }

    /** Puts these players a couple of blocks in front of the hub's banner. */
    private static void nearHub(final QuantumMirror hub, final World world, final Player... players)
    {
        for (final Player player : players)
        {
            when(player.getLocation()).thenReturn(new Location(world, hub.banner().x() + 0.5, 64, 12.5));
        }
        when(world.getPlayers()).thenReturn(List.of(players));
    }

    /** With nothing else listed for the clicker, a right-click says there are no others. */
    @Test
    void aMirrorWhoseOthersAreAllHiddenSaysThereAreNoOthers()
    {
        final QuantumMirror hub = mirror("hub", MirrorAccess.OPEN);
        mirror("return", new MirrorAccess(null, true, false));

        assertEquals("No other mirrors found.", MirrorNetwork.scroll(hub, false, visitor));
        assertTrue(MirrorNetwork.reflects(hub));
    }

    /**
     * The access is written only when set, and an older file reads as the public network.
     *
     * <p>A file of ordinary mirrors must read as it did before groups existed, both ways round.
     */
    @Test
    void theAccessIsStoredOnlyWhenItIsNotTheDefault()
    {
        final QuantumMirror plain = mirror("hub", MirrorAccess.OPEN);
        final Map<String, Object> written = MirrorYamlManager.writeMirror(plain);
        assertFalse(written.containsKey("Group") || written.containsKey("Hidden") || written.containsKey("Private"),
            "an ordinary mirror's entry is as it was: " + written);
        assertEquals(MirrorAccess.OPEN, MirrorYamlManager.readMirror("hub", written).access());

        // One flag on and the other off each way round, so neither can be read from the other's key.
        for (final MirrorAccess access : List.of(new MirrorAccess("Museum", false, true),
            new MirrorAccess(null, true, false)))
        {
            final QuantumMirror back = MirrorYamlManager.readMirror("hub",
                MirrorYamlManager.writeMirror(plain.withAccess(access)));
            assertEquals(access, back.access(), "read back as written");
        }
    }

    /** A blank group is no group, so a hand-edited empty one does not make a group of its own. */
    @Test
    void aBlankGroupIsThePublicNetwork()
    {
        assertNull(new MirrorAccess("  ", false, false).group());
        assertEquals(MirrorAccess.OPEN, new QuantumMirror("hub", new BlockPlace("world", 1, 64, 1), null,
            null, null, 1, false, null).access(), "no access at all is the public network");
    }
}
