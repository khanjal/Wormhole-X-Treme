package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainBody;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.beam.BeamDestination;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamPoint;
import com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@code /wormhole beam} says when it cannot do what was typed, and what it says it did.
 *
 * <p>Most of these name something -- a player, a destination, a world -- and the name is what the
 * reader acts on: a typo, a world to load, a player who is offline. Before #438 they were quoted
 * and headed by hand, and a few had no header at all. {@link Bukkit}'s static lookups are mocked
 * so a name can resolve to a player, a world, or nothing.
 *
 * <p>Only what is said before a beam starts: {@code BeamAnimation} cannot be loaded without a
 * server (its {@code TRAVELLER_EFFECTS} names {@code PotionEffectType}s), so what {@code send}
 * tells its sender once the beam starts, or once it finds one running, is left to the lab.
 */
class BeamCommandMessagesTest
{
    private static final String WORLD = "world";

    private Player admin;
    private World world;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<BeamYamlManager> yaml;

    @BeforeEach
    void setUp()
    {
        BeamManager.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        admin = mock(Player.class);
        when(admin.getName()).thenReturn("Justin");
        when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
        when(admin.getWorld()).thenReturn(world);
        when(admin.getLocation()).thenReturn(new Location(world, 0.5, 64.0, 0.5));
        when(admin.hasPermission(anyString())).thenReturn(Boolean.TRUE);

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(world);
        yaml = mockStatic(BeamYamlManager.class);
    }

    @AfterEach
    void tearDown()
    {
        yaml.close();
        bukkit.close();
        BeamManager.clear();
    }

    private static void run(final CommandSender sender, final String... args)
    {
        new BeamCommand().execute(sender, args);
    }

    private Player online(final String name)
    {
        final Player target = mock(Player.class);
        when(target.getName()).thenReturn(name);
        when(target.getWorld()).thenReturn(world);
        when(target.getLocation()).thenReturn(new Location(world, 5.5, 64.0, 5.5));
        bukkit.when(() -> Bukkit.getPlayerExact(name)).thenReturn(target);
        return target;
    }

    /** Travel is a player's; the console is told so rather than handed a usage line. */
    @Test
    void theConsoleIsToldBeamingIsAPlayers()
    {
        final CommandSender console = mock(CommandSender.class);

        run(console, "beam", "to", "Dock");

        verify(console).sendMessage(ChatText.error("Beaming is a player-only command."));
    }

    /** "to" with nothing after it shows what it takes, and a name that matches nothing says so. */
    @Test
    void toWithoutANameShowsTheUsageAndAnUnknownNameIsNamed()
    {
        run(admin, "beam", "to");
        run(admin, "beam", "to", "Nowhere");

        verify(admin).sendMessage(plainBody("Usage: /wormhole beam to <name>"));
        verify(admin).sendMessage(ChatText.ERROR_HEADER + "No destination named " + ChatText.NAME_COLOUR + "Nowhere"
            + ChatText.BODY_COLOUR + " among your places or the public list.");
    }

    /**
     * The public list names each destination in aqua, with its own price where it has one.
     *
     * <p>Zero reads as free; a destination with no price of its own says nothing, since it costs
     * the configured default like every other.
     */
    @Test
    void thePublicListNamesEachDestinationWithItsOwnPrice()
    {
        BeamManager.setPublicDestination(new BeamDestination("Dock", new BeamPoint(WORLD, 0, 64, 0, 0f, 0f), 0.0));

        run(admin, "beam", "list");

        verify(admin).sendMessage(ChatText.HEADER + "Beam destinations: " + ChatText.NAME_COLOUR + "Dock"
            + ChatText.BODY_COLOUR + " (free)");

        BeamManager.setPublicDestination(new BeamDestination("Mine", new BeamPoint(WORLD, 0, 64, 0, 0f, 0f), 2.5));
        BeamManager.setPublicDestination(new BeamDestination("Spawn", new BeamPoint(WORLD, 0, 64, 0, 0f, 0f), null));
        run(admin, "beam", "list");

        // The registry's order is not this test's business, so each entry is looked for on its own.
        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(admin, times(2)).sendMessage(said.capture());
        final String listed = ChatText.plain(said.getAllValues().get(1));
        assertTrue(listed.startsWith(":: Beam destinations: "), listed);
        assertTrue(listed.contains("Dock (free)") && listed.contains("Mine (2.5)"), listed);
        assertTrue(listed.contains("Spawn") && !listed.contains("Spawn ("), "no price of its own, so none shown: " + listed);
    }

    /** An empty public list says so. */
    @Test
    void anEmptyPublicListSaysSo()
    {
        run(admin, "beam", "list");

        verify(admin).sendMessage(plainBody("No public beam destinations are set."));
    }

    /** goto and send with too little after them show their own usage. */
    @Test
    void gotoAndSendWithTooLittleShowTheirUsage()
    {
        run(admin, "beam", "admin", "goto");
        run(admin, "beam", "admin", "send", "Probe2");

        verify(admin).sendMessage(plainBody("Usage: /wormhole beam admin goto <player|destination>|<x> <y> <z> [world]"));
        verify(admin).sendMessage(plainBody("Usage: /wormhole beam admin send <player> <player|destination>|<x> <y> <z> [world]"));
    }

    /** Sending somebody who is not online names them. */
    @Test
    void sendingSomebodyOfflineNamesThem()
    {
        run(admin, "beam", "admin", "send", "Nobody", "Dock");

        verify(admin).sendMessage(plainBody("No online player named Nobody."));
    }

    /** A destination name that is neither an online player nor a public destination is named back. */
    @Test
    void aDestinationThatIsNeitherAPlayerNorPublicIsNamed()
    {
        online("Probe2");

        run(admin, "beam", "admin", "send", "Probe2", "Atlantis");

        verify(admin).sendMessage(plainBody("No online player or public beam destination named Atlantis."));
    }

    /** A public destination whose world is not loaded names the world, so nobody hunts a typo. */
    @Test
    void aDestinationInAnUnloadedWorldNamesTheWorld()
    {
        online("Probe2");
        BeamManager.setPublicDestination(new BeamDestination("Fort", new BeamPoint("nether", 0, 64, 0, 0f, 0f), null));

        run(admin, "beam", "admin", "send", "Probe2", "Fort");

        verify(admin).sendMessage(plainBody("Beam destination Fort is in world nether, which is not loaded."));
    }

    /** Coordinates in a world that does not exist name the world. */
    @Test
    void coordinatesInAnUnknownWorldNameTheWorld()
    {
        online("Probe2");

        run(admin, "beam", "admin", "send", "Probe2", "1", "64", "2", "Narnia");

        verify(admin).sendMessage(plainBody("No world named Narnia."));
    }

    /** "place" with nothing after it, and "place remove" with no name, show what they take. */
    @Test
    void placeWithTooLittleShowsTheUsage()
    {
        run(admin, "beam", "place");
        run(admin, "beam", "place", "remove");

        verify(admin).sendMessage(plainBody("Usage: /wormhole beam place <list|set|remove> [name]"));
        verify(admin).sendMessage(plainBody("Usage: /wormhole beam place remove <name>"));
    }
}
