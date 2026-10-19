package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamDestination;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamPoint;
import com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * What {@code /wormhole beam} says, read by key from {@code lang/} (#26).
 *
 * <p>Each line is sent through the real command twice. Once to an English player, against the
 * concatenation the code built before the move, copied from it, so a reworded template or a swapped
 * argument shows as a changed message. Once to a player whose client reads the test language qq, so a
 * call site that reads the server's language instead of the player's shows as English. The lines only
 * the console can reach are read with {@code server-language} set to qq.
 */
class BeamCommandLanguageTest
{
    private static final String WORLD = "world";
    private static final String DOCK = "Dock";
    private static final String HOME = "Home";
    private static final String MINE = "Mine";
    private static final String NOWHERE = "Nowhere";
    private static final String PROBE = "Probe2";
    private static final String TO_NAME = "/wormhole beam to <name>";
    private static final String COORDINATES = "<x> <y> <z> [world]";
    private static final String DEFAULT = "-default";

    private World world;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<BeamYamlManager> yaml;

    @BeforeEach
    void setUp()
    {
        BeamManager.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
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

    /** An admin with every beam permission, whose client reads {@code locale}. */
    private Player reading(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        when(player.getName()).thenReturn("Justin");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0.5, 64.0, 0.5));
        when(player.hasPermission(anyString())).thenReturn(Boolean.TRUE);
        return player;
    }

    private void online(final String name)
    {
        final Player target = mock(Player.class);
        when(target.getName()).thenReturn(name);
        when(target.getWorld()).thenReturn(world);
        when(target.getLocation()).thenReturn(new Location(world, 5.5, 64.0, 5.5));
        bukkit.when(() -> Bukkit.getPlayerExact(name)).thenReturn(target);
    }

    private static void publicAt(final String name, final String worldName, final Double cost)
    {
        BeamManager.setPublicDestination(new BeamDestination(name, new BeamPoint(worldName, 0, 64, 0, 0f, 0f), cost));
    }

    private static void beam(final CommandSender sender, final String... rest)
    {
        final String[] args = new String[rest.length + 1];
        args[0] = "beam";
        System.arraycopy(rest, 0, args, 1, rest.length);
        new BeamCommand().execute(sender, args);
    }

    private static void admin(final CommandSender sender, final String... rest)
    {
        final String[] args = new String[rest.length + 1];
        args[0] = "admin";
        System.arraycopy(rest, 0, args, 1, rest.length);
        beam(sender, args);
    }

    /** Each way a public destination is set, removed or priced, sent by {@code player}. */
    private static void editPublic(final Player player)
    {
        admin(player, "set", DOCK);
        admin(player, "set", "-dock");
        admin(player, "cost", DOCK, "abc");
        admin(player, "cost", DOCK, "-1");
        admin(player, "cost", DOCK, "2.5");
        admin(player, "cost", DOCK, DEFAULT);
        admin(player, "cost", NOWHERE, "1");
        admin(player, "remove", DOCK);
        admin(player, "remove", DOCK);
    }

    /** Each way a destination for goto or send is refused, sent by {@code player}. */
    private void refuseDestinations(final Player player)
    {
        online(PROBE);
        publicAt("Fort", "nether", null);
        admin(player, "send", NOWHERE, DOCK);
        admin(player, "send", PROBE, "1", "2");
        admin(player, "send", PROBE, "Atlantis");
        admin(player, "send", PROBE, "Fort");
        admin(player, "send", PROBE, "1", "64", "2", "Narnia");
        admin(player, "goto", "x", "64", "2");
    }

    /** Each place command's reply, sent by {@code player}. */
    private static void usePlaces(final Player player)
    {
        beam(player, "place", "list");
        beam(player, "place", "fly");
        beam(player, "place", "set", HOME);
        beam(player, "place", "list");
        beam(player, "place", "remove", HOME);
        beam(player, "place", "remove", HOME);
    }

    /** The unknown verb, an unknown destination and the public list, empty and with each kind of price. */
    @Test
    void travelAndThePublicListReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        beam(player, "fly");
        beam(player, "to", NOWHERE);
        beam(player, "list");
        publicAt(DOCK, WORLD, 0.0);
        beam(player, "list");
        BeamManager.clear();
        publicAt(MINE, WORLD, 2.5);
        beam(player, "list");
        publicAt(DOCK, WORLD, null);
        beam(player, "list");

        verify(player).sendMessage(ChatText.error("Unknown beam command."));
        verify(player).sendMessage(ChatText.error("No destination named " + ChatText.name(NOWHERE)
            + " among your places or the public list."));
        verify(player).sendMessage(ChatText.info("No public beam destinations are set."));
        verify(player).sendMessage(ChatText.info("Beam destinations: " + ChatText.name(DOCK) + " (free)"));
        verify(player).sendMessage(ChatText.info("Beam destinations: " + ChatText.name(MINE) + " ("
            + ChatText.value(String.valueOf(2.5)) + ")"));
        verify(player).sendMessage(ChatText.info("Beam destinations: " + oldList()));
    }

    /** The public list as the code built it before the move, in the registry's own order. */
    private static String oldList()
    {
        final StringBuilder names = new StringBuilder();
        for (final BeamDestination destination : BeamManager.getAllPublicDestinations())
        {
            if (!names.isEmpty())
            {
                names.append(", ");
            }
            names.append(ChatText.name(destination.name()));
            final Double cost = destination.cost();
            if (cost != null)
            {
                names.append(cost <= 0 ? " (free)" : " (" + ChatText.value(String.valueOf(cost)) + ")");
            }
        }
        return names.toString();
    }

    /** The same lines reach a qq player in qq, the list joined and priced as qq reads it. */
    @Test
    void travelAndThePublicListAreInThePlayersLanguage()
    {
        final Player player = reading("qq");
        beam(player, "fly");
        beam(player, "to", NOWHERE);
        beam(player, "list");
        publicAt(DOCK, WORLD, 0.0);
        publicAt(MINE, WORLD, 2.5);
        publicAt(HOME, WORLD, null);
        beam(player, "list");

        verify(player).sendMessage(ChatText.error("QQ unknown beam."));
        verify(player).sendMessage(ChatText.error("QQ no destination " + ChatText.name(NOWHERE) + "."));
        verify(player).sendMessage(ChatText.info("QQ no destinations."));
        final List<String> entries = BeamManager.getAllPublicDestinations().stream().map(d -> switch (d.name())
        {
            case DOCK -> ChatText.name(DOCK) + " QQfree";
            case MINE -> ChatText.name(MINE) + " QQ" + ChatText.value("2.5");
            default -> ChatText.name(d.name());
        }).toList();
        verify(player).sendMessage(ChatText.info("QQ destinations " + String.join(" | ", entries)));
    }

    /** Setting, pricing and removing a public destination read as they did before. */
    @Test
    void editingAPublicDestinationReadsAsItDidBefore()
    {
        final Player player = reading("en");
        editPublic(player);

        verify(player).sendMessage(ChatText.info("Public beam destination " + ChatText.name(DOCK)
            + " set to your current location."));
        verify(player).sendMessage(ChatText.error("A beam destination's name cannot start with " + ChatText.bad("-")
            + "; words that do are options."));
        verify(player).sendMessage(ChatText.error(ChatText.bad("abc") + " is not a number. Give an amount, or "
            + ChatText.command(DEFAULT) + "."));
        verify(player).sendMessage(ChatText.error("Cost cannot be negative."));
        verify(player).sendMessage(ChatText.info(ChatText.name(DOCK) + " now costs "
            + ChatText.value(String.valueOf(2.5)) + " to beam to."));
        verify(player).sendMessage(ChatText.info(ChatText.name(DOCK) + " now uses the configured default beam cost."));
        verify(player).sendMessage(ChatText.error("No public beam destination named " + ChatText.name(NOWHERE) + "."));
        verify(player).sendMessage(ChatText.info("Removed public beam destination " + ChatText.name(DOCK) + "."));
        verify(player).sendMessage(ChatText.error("No public beam destination named " + ChatText.name(DOCK) + "."));
    }

    /** The same lines reach a qq player in qq, a removal told apart from a name that matched none. */
    @Test
    void editingAPublicDestinationIsInThePlayersLanguage()
    {
        final Player player = reading("qq");
        editPublic(player);

        verify(player).sendMessage(ChatText.info("QQ public set " + ChatText.name(DOCK) + "."));
        verify(player).sendMessage(ChatText.error("QQ dashed " + ChatText.bad("-") + "."));
        verify(player).sendMessage(ChatText.error("QQ " + ChatText.bad("abc") + " not number "
            + ChatText.command(DEFAULT) + "."));
        verify(player).sendMessage(ChatText.error("QQ negative."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(DOCK) + " costs " + ChatText.value("2.5") + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(DOCK) + " default cost."));
        verify(player).sendMessage(ChatText.error("QQ no public " + ChatText.name(NOWHERE) + "."));
        verify(player).sendMessage(ChatText.info("QQ public removed " + ChatText.name(DOCK) + "."));
        verify(player).sendMessage(ChatText.error("QQ no public " + ChatText.name(DOCK) + "."));
    }

    /** Each refused goto or send destination reads as it did before. */
    @Test
    void refusedDestinationsReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        refuseDestinations(player);

        verify(player).sendMessage(ChatText.error("No online player named " + ChatText.name(NOWHERE) + "."));
        verify(player).sendMessage(ChatText.error("Expected a player name, a public destination name, or "
            + ChatText.usageBody(COORDINATES) + "."));
        verify(player).sendMessage(ChatText.error("No online player or public beam destination named "
            + ChatText.name("Atlantis") + "."));
        verify(player).sendMessage(ChatText.error("Beam destination " + ChatText.name("Fort") + " is in world "
            + ChatText.name("nether") + ", which is not loaded."));
        verify(player).sendMessage(ChatText.error("No world named " + ChatText.name("Narnia") + "."));
        verify(player).sendMessage(ChatText.error(ChatText.bad("x") + " is not a valid coordinate."));
    }

    /** The same refusals reach a qq player in qq. */
    @Test
    void refusedDestinationsAreInThePlayersLanguage()
    {
        final Player player = reading("qq");
        refuseDestinations(player);

        verify(player).sendMessage(ChatText.error("QQ not online " + ChatText.name(NOWHERE) + "."));
        verify(player).sendMessage(ChatText.error("QQ expected " + ChatText.usageBody(COORDINATES) + "."));
        verify(player).sendMessage(ChatText.error("QQ no player or destination " + ChatText.name("Atlantis") + "."));
        verify(player).sendMessage(ChatText.error("QQ destination " + ChatText.name("Fort") + " unloaded "
            + ChatText.name("nether") + "."));
        verify(player).sendMessage(ChatText.error("QQ no world " + ChatText.name("Narnia") + "."));
        verify(player).sendMessage(ChatText.error("QQ bad coordinate " + ChatText.bad("x") + "."));
    }

    /** What send tells its sender, started or not, as before and in qq. */
    @Test
    void whatSendTellsItsSenderReadsAsBeforeAndInTheirLanguage()
    {
        final Player english = reading("en");
        final Player qq = reading("qq");

        assertEquals(List.of(ChatText.info("Beaming " + ChatText.name(PROBE) + " to " + ChatText.name(DOCK) + "."),
            ChatText.error(ChatText.name(PROBE) + " is already beaming somewhere.")),
            List.of(BeamCommand.sent(english, true, PROBE, DOCK), BeamCommand.sent(english, false, PROBE, DOCK)));
        assertEquals(List.of(ChatText.info("QQ sending " + ChatText.name(PROBE) + " " + ChatText.name(DOCK) + "."),
            ChatText.error("QQ " + ChatText.name(PROBE) + " busy.")),
            List.of(BeamCommand.sent(qq, true, PROBE, DOCK), BeamCommand.sent(qq, false, PROBE, DOCK)));
    }

    /** The place commands read as they did before: an empty list, a set, a list, a removal and a miss. */
    @Test
    void placesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        usePlaces(player);

        verify(player).sendMessage(ChatText.info("You have no places set."));
        verify(player).sendMessage(ChatText.error("Unknown place command. To travel, use "
            + ChatText.usageBody(TO_NAME) + "."));
        verify(player).sendMessage(ChatText.info("Place " + ChatText.name(HOME) + " set to your current location."));
        verify(player).sendMessage(ChatText.info("Your places: " + ChatText.name(HOME)));
        verify(player).sendMessage(ChatText.info("Removed place " + ChatText.name(HOME) + "."));
        verify(player).sendMessage(ChatText.error("You have no place named " + ChatText.name(HOME) + "."));
    }

    /** The same lines reach a qq player in qq, and two places are joined as qq joins a list. */
    @Test
    void placesAreInThePlayersLanguage()
    {
        final Player player = reading("qq");
        usePlaces(player);
        beam(player, "place", "set", DOCK);
        beam(player, "place", "set", MINE);
        beam(player, "place", "list");

        verify(player).sendMessage(ChatText.info("QQ no places."));
        verify(player).sendMessage(ChatText.error("QQ unknown place " + ChatText.usageBody(TO_NAME) + "."));
        verify(player).sendMessage(ChatText.info("QQ place set " + ChatText.name(HOME) + "."));
        verify(player).sendMessage(ChatText.info("QQ places " + ChatText.name(HOME)));
        verify(player).sendMessage(ChatText.info("QQ place removed " + ChatText.name(HOME) + "."));
        verify(player).sendMessage(ChatText.error("QQ no place " + ChatText.name(HOME) + "."));
        final List<String> names = BeamManager.getPlaces(player.getUniqueId()).stream()
            .map(place -> ChatText.name(place.name())).toList();
        verify(player).sendMessage(ChatText.info("QQ places " + String.join(" | ", names)));
    }

    /**
     * What only the console can be told: beaming, editing the public list and goto are a player's.
     * It has no client language, so it reads {@code server-language}: English, then qq once set.
     */
    @Test
    void theConsoleReadsTheServersLanguage()
    {
        final CommandSender english = mock(ConsoleCommandSender.class);
        final CommandSender qq = mock(ConsoleCommandSender.class);
        when(qq.hasPermission(anyString())).thenReturn(Boolean.TRUE);
        when(english.hasPermission(anyString())).thenReturn(Boolean.TRUE);
        consoleTries(english);
        try (MockedStatic<ConfigManager> config = mockStatic(ConfigManager.class))
        {
            config.when(ConfigManager::getServerLanguage).thenReturn("qq");
            Messages.load();
            try
            {
                consoleTries(qq);
            }
            finally
            {
                config.when(ConfigManager::getServerLanguage).thenReturn(Messages.DEFAULT_LANGUAGE);
                Messages.load();
            }
        }

        verify(english).sendMessage(ChatText.error("Beaming is a player-only command."));
        verify(english).sendMessage(ChatText.error(ChatText.command("/wormhole beam admin set|remove|cost")
            + " is player-only."));
        verify(english).sendMessage(ChatText.error(ChatText.command("goto") + " moves you, and there is nowhere for "
            + "console or a command block to beam from."));
        verify(qq).sendMessage(ChatText.error("QQ beam player only."));
        verify(qq).sendMessage(ChatText.error("QQ " + ChatText.command("/wormhole beam admin set|remove|cost")
            + " player only."));
        verify(qq).sendMessage(ChatText.error("QQ " + ChatText.command("goto") + " not a player."));
    }

    private static void consoleTries(final CommandSender console)
    {
        beam(console, "to", DOCK);
        admin(console, "set", DOCK);
        admin(console, "goto", DOCK);
    }
}
