package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamPermissions;
import com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager;

/**
 * A player's own beam places, which nobody else can see or reach.
 *
 * <p>Places are the private half of beaming: kept per player rather than on one shared list, so
 * two people may both have a place called "home" and neither can go to the other's. Nothing
 * covered any of it.
 *
 * <p>The three subcommands are deliberately not gated alike. Making a new place needs
 * {@code wormhole.beam.place}; listing and removing your own do not. Taking the node away should
 * stop somebody adding more, not strand them with places they can no longer tidy up.
 */
class BeamPlacesTest
{
    private static final String MINE = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String THEIRS = "11111111-2222-3333-4444-555555555555";

    private Player player;
    private Player other;
    private World world;
    private MockedStatic<BeamYamlManager> yaml;

    @BeforeEach
    void setUp()
    {
        BeamManager.clear();

        world = mock(World.class);
        when(world.getName()).thenReturn("world");

        player = playerAt(MINE, "Justin", 10.5, 64.0, 20.5);
        other = playerAt(THEIRS, "Grace", 90.5, 70.0, 80.5);

        yaml = mockStatic(BeamYamlManager.class);
    }

    @AfterEach
    void tearDown()
    {
        yaml.close();
        BeamManager.clear();
    }

    private Player playerAt(final String uuid, final String name,
        final double x, final double y, final double z)
    {
        final Player p = mock(Player.class);
        when(p.getName()).thenReturn(name);
        when(p.getUniqueId()).thenReturn(UUID.fromString(uuid));
        when(p.getWorld()).thenReturn(world);
        when(p.getLocation()).thenReturn(new Location(world, x, y, z));
        when(p.isOp()).thenReturn(Boolean.FALSE);
        when(p.hasPermission(anyString())).thenReturn(Boolean.FALSE);
        when(p.hasPermission(BeamPermissions.PLACE)).thenReturn(Boolean.TRUE);
        return p;
    }

    private static boolean place(final Player who, final String... rest)
    {
        final String[] args = new String[rest.length + 2];
        args[0] = "beam";
        args[1] = "place";
        System.arraycopy(rest, 0, args, 2, rest.length);
        return new BeamCommand().execute(who, args);
    }

    /** Setting a place records where the player is standing, under their own name for it. */
    @Test
    void settingAPlaceRecordsWhereThePlayerStands()
    {
        assertTrue(place(player, "set", "home"));

        final var saved = BeamManager.getPlace(UUID.fromString(MINE), "home");
        assertNotNull(saved, "the place is kept");
        assertEquals(10.5, saved.point().x(), 1.0e-9, "where they were standing");
        assertEquals(20.5, saved.point().z(), 1.0e-9);
        yaml.verify(BeamYamlManager::saveAll);
        verify(player).sendMessage(contains("Place \"home\" set"));
    }

    /**
     * Two players may each have a place of the same name, and neither reaches the other's.
     *
     * <p>The point of places being private. Kept on one shared list, the second person to save
     * a "home" would overwrite the first, and every player on the server would be one command
     * away from standing in somebody's bedroom.
     */
    @Test
    void twoPlayersMayEachHaveAHomeAndNeitherIsTheOthers()
    {
        place(player, "set", "home");
        place(other, "set", "home");

        assertEquals(10.5, BeamManager.getPlace(UUID.fromString(MINE), "home").point().x(), 1.0e-9,
            "mine is where I was standing");
        assertEquals(90.5, BeamManager.getPlace(UUID.fromString(THEIRS), "home").point().x(), 1.0e-9,
            "and theirs is where they were");
    }

    /** Listing shows your own places and says so plainly when there are none. */
    @Test
    void listingShowsYourOwnPlacesAndSaysSoWhenThereAreNone()
    {
        place(player, "list");
        verify(player).sendMessage(contains("no places set"));

        place(player, "set", "home");
        place(player, "set", "mine");
        place(player, "list");

        // The listing line specifically: contains("home") would also match the confirmation
        // that setting it sent a moment earlier. The order the two come back in is the
        // registry's, not this test's business, so both are looked for rather than a sequence.
        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(player, atLeastOnce()).sendMessage(said.capture());
        assertTrue(said.getAllValues().stream().anyMatch(line -> line.contains("Your places:")
            && line.contains("home") && line.contains("mine")),
            "the listing names both: " + said.getAllValues());
    }

    /** And it shows only yours, not everybody's. */
    @Test
    void listingShowsOnlyYourOwnPlaces()
    {
        place(other, "set", "secretbase");

        place(player, "list");

        verify(player).sendMessage(contains("no places set"));
        verify(player, never()).sendMessage(contains("secretbase"));
    }

    /** Removing takes it off and writes the change out. */
    @Test
    void removingAPlaceTakesItOff()
    {
        place(player, "set", "home");
        yaml.clearInvocations();

        assertTrue(place(player, "remove", "home"));

        assertNull(BeamManager.getPlace(UUID.fromString(MINE), "home"));
        yaml.verify(BeamYamlManager::saveAll);
        verify(player).sendMessage(contains("Removed place \"home\""));
    }

    /** Removing one you never had says so, and writes nothing. */
    @Test
    void removingAPlaceYouNeverHadWritesNothing()
    {
        assertTrue(place(player, "remove", "nosuchplace"));

        yaml.verify(BeamYamlManager::saveAll, never());
        verify(player).sendMessage(contains("no place named"));
    }

    /** Making a new place needs the node. */
    @Test
    void makingAPlaceNeedsTheNode()
    {
        when(player.hasPermission(BeamPermissions.PLACE)).thenReturn(Boolean.FALSE);

        assertTrue(place(player, "set", "home"));

        assertNull(BeamManager.getPlace(UUID.fromString(MINE), "home"), "nothing was kept");
        verify(player).sendMessage(contains("permission"));
    }

    /**
     * Tidying up after yourself does not.
     *
     * <p>Deliberately not gated the same way. Somebody whose {@code wormhole.beam.place} has
     * been taken away should be stopped from making more, not left holding places they can
     * neither reach nor remove.
     */
    @Test
    void tidyingUpAfterYourselfDoesNotNeedTheNode()
    {
        place(player, "set", "home");
        when(player.hasPermission(BeamPermissions.PLACE)).thenReturn(Boolean.FALSE);

        assertTrue(place(player, "remove", "home"));

        assertNull(BeamManager.getPlace(UUID.fromString(MINE), "home"),
            "a place already made is still theirs to remove");
    }

    /** Nor does looking at what you have. */
    @Test
    void lookingAtWhatYouHaveDoesNotNeedTheNode()
    {
        place(player, "set", "home");
        when(player.hasPermission(BeamPermissions.PLACE)).thenReturn(Boolean.FALSE);

        assertTrue(place(player, "list"));

        verify(player).sendMessage(contains("Your places: home"));
    }

    /** A subcommand nobody recognises points at the ones that exist, and at travel. */
    @Test
    void anUnknownSubcommandPointsAtTheOnesThatExist()
    {
        assertTrue(place(player, "teleport", "home"));

        verify(player).sendMessage(contains("list|set <name>|remove <name>"));
        verify(player).sendMessage(contains("beam to <name> to travel"));
    }

    /** Asking for a place with no name shows what the command takes. */
    @Test
    void settingAPlaceWithNoNameShowsTheUsage()
    {
        assertTrue(place(player, "set"));

        verify(player).sendMessage(contains("place set <name>"));
        yaml.verify(BeamYamlManager::saveAll, never());
    }
}
