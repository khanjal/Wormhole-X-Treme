package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamAnimation;
import com.wormhole_xtreme.wormhole.model.beam.BeamDestination;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamPermissions;
import com.wormhole_xtreme.wormhole.model.beam.BeamTravel;
import com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * Handler for {@code /wormhole beam}.
 *
 * <p>Actual travel is delegated to {@link BeamTravel}, shared with {@code /wormhole go} so the
 * two commands resolve a name the same way.
 *
 * <p>Travel goes through one verb, {@code to}, regardless of whether the name resolves to a
 * public destination or one of the player's own places. Earlier this took a bare name
 * (`/wormhole beam &lt;name&gt;`), which sat in the same argument slot as `list`, `admin` and
 * `place` and read as one more subcommand rather than as "the thing you are travelling to" --
 * confusing in a way `to` fixes outright, since a name that happens to collide with a verb is
 * no longer ambiguous once there is a word in front of it saying "what follows is a
 * destination."
 *
 * <pre>
 * /wormhole beam to &lt;name&gt;                travel -- checks your own places first, then public
 * /wormhole beam list                      list public destinations
 * /wormhole beam admin set &lt;name&gt;         register a public destination at your location
 * /wormhole beam admin remove &lt;name&gt;      remove a public destination
 * /wormhole beam admin cost &lt;name&gt; &lt;amt&gt;  set what a public destination costs to use
 * /wormhole beam admin cost &lt;name&gt; default clear the override; use the configured default
 * /wormhole beam admin goto &lt;player&gt;      beam yourself to a player
 * /wormhole beam admin goto &lt;destination&gt;  beam yourself to a public destination
 * /wormhole beam admin goto &lt;x&gt; &lt;y&gt; &lt;z&gt; [world]   beam yourself to raw coordinates
 * /wormhole beam admin send &lt;target&gt; &lt;player&gt;             beam a player to another player
 * /wormhole beam admin send &lt;target&gt; &lt;destination&gt;        beam a player to a public destination
 * /wormhole beam admin send &lt;target&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; [world]  beam a player to raw coordinates
 * /wormhole beam place list                list your own places
 * /wormhole beam place set &lt;name&gt;         save your current location as a place
 * /wormhole beam place remove &lt;name&gt;      remove one of your own places
 * </pre>
 *
 * <p>Only public destinations can have their own cost -- there is no {@code place cost}, on
 * purpose. A place is only ever reachable by the player who made it, so letting them set its
 * cost would just be them choosing what to pay themselves.
 *
 * <p>{@code goto} and {@code send} are the one place this command accepts a non-player
 * {@link CommandSender}: gated behind {@link BeamPermissions#ADMIN_TELEPORT}, not
 * {@link BeamPermissions#ADMIN}, since freely relocating any player is a different order of
 * power than curating the destination list, and console or a command block chaining several
 * of these has no location of its own to beam <em>from</em> in the first place -- only
 * {@code send}, never a bare {@code goto}, makes sense for either.
 */
public class BeamCommand implements SubCommand
{
    /** @return how to travel, in the reader's language */
    private static String toUsage(final CommandSender recipient)
    {
        return ChatText.info(ChatText.usage(recipient, "/wormhole beam to <name>"));
    }

    /** @return how to set, remove or price a public destination, in the reader's language */
    private static String editUsage(final CommandSender recipient)
    {
        return ChatText.info(ChatText.usage(recipient, "/wormhole beam admin set|remove|cost <name>"));
    }

    /** @return how an admin moves themselves, in the reader's language */
    private static String gotoUsage(final CommandSender recipient)
    {
        return ChatText.info(ChatText.usage(recipient,
            "/wormhole beam admin goto <player|destination>|<x> <y> <z> [world]"));
    }

    /** @return how an admin moves somebody else, in the reader's language */
    private static String sendUsage(final CommandSender recipient)
    {
        return ChatText.info(ChatText.usage(recipient,
            "/wormhole beam admin send <player> <player|destination>|<x> <y> <z> [world]"));
    }

    /** @return how to list, set or remove the player's own places, in the reader's language */
    private static String placeUsage(final CommandSender recipient)
    {
        return ChatText.info(ChatText.usage(recipient, "/wormhole beam place <list|set|remove> [name]"));
    }

    // Bukkit reads the boolean as "handled"; every path here has handled it.
    @SuppressWarnings("java:S3516")
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (args.length < 2)
        {
            sender.sendMessage(toUsage(sender));
            sender.sendMessage(ChatText.info(Messages.get(sender, "usage.beam.or",
                ChatText.usageBody("/wormhole beam list|place|admin"))));
            return true;
        }

        final String verb = args[1].toLowerCase(Locale.ROOT);
        if ("admin".equals(verb))
        {
            // admin has its own sender handling: goto/send accept console and command
            // blocks, set/remove/cost stay player-only below.
            admin(sender, args);
            return true;
        }

        if (!(sender instanceof Player))
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "beam.cmd.player-only")));
            return true;
        }
        final Player player = (Player) sender;

        if ("to".equals(verb))
        {
            if (args.length < 3)
            {
                player.sendMessage(toUsage(player));
                return true;
            }
            travelTo(player, args[2]);
            return true;
        }
        if ("list".equals(verb))
        {
            listPublic(player);
            return true;
        }
        if ("place".equals(verb))
        {
            place(player, args);
            return true;
        }
        player.sendMessage(ChatText.error(Messages.get(player, "beam.cmd.unknown")));
        player.sendMessage(toUsage(player));
        return true;
    }

    /**
     * Travels to a destination by name. The actual resolution and travel is shared with
     * {@code /wormhole go}, via {@link BeamTravel}, so the two commands can never disagree
     * about what a name means.
     *
     * @param player the traveller
     * @param name the destination name
     */
    private void travelTo(final Player player, final String name)
    {
        if (!BeamTravel.travelTo(player, name))
        {
            player.sendMessage(ChatText.error(Messages.get(player, "beam.cmd.to.none", ChatText.name(name))));
        }
    }

    private void listPublic(final Player player)
    {
        // Behind USE, not ungated: a player who cannot travel to any of these has no use for
        // the list, and the node is meant to cover the whole player-facing beam surface.
        if (!BeamPermissions.has(player, BeamPermissions.USE))
        {
            player.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return;
        }
        final List<String> names = new ArrayList<>();
        for (final BeamDestination destination : BeamManager.getAllPublicDestinations())
        {
            final String named = ChatText.name(destination.name());
            // Only an override is worth saying anything about -- the default cost is
            // already visible via /wormhole config BEAM_ECONOMY_USE_COST, and repeating it
            // next to every destination that hasn't been given one of its own would just be
            // noise the reader has to filter back out.
            final Double cost = destination.cost();
            if (cost == null)
            {
                names.add(named);
            }
            else if (cost <= 0)
            {
                names.add(Messages.get(player, "beam.cmd.list.free", named));
            }
            else
            {
                names.add(Messages.get(player, "beam.cmd.list.priced", named, ChatText.value(String.valueOf(cost))));
            }
        }
        if (names.isEmpty())
        {
            player.sendMessage(ChatText.info(Messages.get(player, "beam.cmd.list.none")));
            return;
        }
        player.sendMessage(ChatText.info(Messages.get(player, "beam.cmd.list.public", joined(player, names))));
    }

    /** @return the names in a list line, joined as {@code player} reads a list */
    private static String joined(final Player player, final List<String> names)
    {
        return String.join(Messages.get(player, "beam.cmd.list.separator"), names);
    }

    private void admin(final CommandSender sender, final String[] args)
    {
        if (args.length < 3)
        {
            sender.sendMessage(editUsage(sender));
            sender.sendMessage(gotoUsage(sender));
            sender.sendMessage(sendUsage(sender));
            return;
        }
        final String action = args[2].toLowerCase(Locale.ROOT);

        if ("goto".equals(action))
        {
            adminGoto(sender, args);
            return;
        }
        if ("send".equals(action))
        {
            adminSend(sender, args);
            return;
        }

        // Below here records or reads the sender's own location (set) or is otherwise a
        // destination-list edit that has always been player-only; goto/send are the only
        // admin actions a non-player sender can reach.
        if (!(sender instanceof Player))
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "beam.cmd.admin.player-only",
                ChatText.command("/wormhole beam admin set|remove|cost"))));
            return;
        }
        final Player player = (Player) sender;
        if (!BeamPermissions.has(player, BeamPermissions.ADMIN))
        {
            player.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return;
        }
        if (args.length < 4)
        {
            player.sendMessage(editUsage(player));
            return;
        }
        final String name = args[3];
        // create as well, the same alias gate and mirror take. Both halves of beam name a place
        // you are standing in, so a server owner who learned one should not find the other
        // refusing the word.
        if (CommandHandlerUtils.verbIs(action, "set", "create"))
        {
            setPublic(player, name);
            return;
        }
        if ("remove".equals(action))
        {
            final boolean removed = BeamManager.removePublicDestination(name);
            if (removed)
            {
                BeamYamlManager.saveAll();
            }
            final String said;
            if (removed)
            {
                said = ChatText.info(Messages.get(player, "beam.cmd.admin.removed", ChatText.name(name)));
            }
            else
            {
                said = noPublic(player, name);
            }
            player.sendMessage(said);
            return;
        }
        if ("cost".equals(action))
        {
            setCost(player, args, name);
            return;
        }
        player.sendMessage(editUsage(player));
    }

    /** @return the error, for {@code player} to read, for a public destination name that matches none */
    private static String noPublic(final Player player, final String name)
    {
        return ChatText.error(Messages.get(player, "beam.cmd.admin.no-public", ChatText.name(name)));
    }

    /**
     * Sets, or clears, one public destination's own cost override.
     *
     * @param player the admin
     * @param args the full argument array -- the amount is at index 4, one past the name
     * @param name the destination's name
     */
    private void setCost(final Player player, final String[] args, final String name)
    {
        if (args.length < 5)
        {
            player.sendMessage(ChatText.info(ChatText.usage(player, "/wormhole beam admin cost <name> <amount|-default>")));
            return;
        }
        final BeamDestination existing = BeamManager.getPublicDestination(name);
        if (existing == null)
        {
            player.sendMessage(noPublic(player, name));
            return;
        }
        final String raw = args[4];
        final Double newCost;
        if ("-default".equalsIgnoreCase(raw))
        {
            newCost = null;
        }
        else
        {
            try
            {
                newCost = Double.valueOf(raw);
            }
            catch (final NumberFormatException e)
            {
                player.sendMessage(ChatText.error(Messages.get(player, "beam.cmd.cost.not-number", ChatText.bad(raw),
                    ChatText.command("-default"))));
                return;
            }
            if (newCost < 0)
            {
                player.sendMessage(ChatText.error(Messages.get(player, "beam.cmd.cost.negative")));
                return;
            }
        }
        BeamManager.setPublicDestination(existing.withCost(newCost));
        BeamYamlManager.saveAll();
        final String line;
        if (newCost == null)
        {
            line = Messages.get(player, "beam.cmd.cost.default", ChatText.name(name));
        }
        else
        {
            line = Messages.get(player, "beam.cmd.cost.set", ChatText.name(name), ChatText.value(String.valueOf(newCost)));
        }
        player.sendMessage(ChatText.info(line));
    }

    /**
     * Beams the sender to a player or raw coordinates. Player-only -- there is nowhere for
     * console or a command block to beam <em>from</em> -- unlike {@link #adminSend}, which
     * moves someone else and has no such problem.
     *
     * @param args the full argument array; the target starts at index 3
     */
    private void adminGoto(final CommandSender sender, final String[] args)
    {
        if (!(sender instanceof Player))
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "beam.cmd.goto.not-a-player", ChatText.command("goto"))));
            sender.sendMessage(sendUsage(sender));
            return;
        }
        final Player player = (Player) sender;
        if (!BeamPermissions.has(player, BeamPermissions.ADMIN_TELEPORT))
        {
            player.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return;
        }
        if (args.length < 4)
        {
            player.sendMessage(gotoUsage(player));
            return;
        }
        final Location destination = resolveDestination(player, args, 3,
            player.getWorld(), player.getLocation().getYaw(), player.getLocation().getPitch());
        if (destination == null)
        {
            // resolveDestination has already sent the specific reason.
            return;
        }
        // BeamAnimation.start already refuses (and messages the player) if they're mid-beam,
        // and sends its own "Beaming to X..."/"Beamed to X." messages either way -- nothing
        // further to say here regardless of which way it goes.
        BeamAnimation.start(player, destination, describeDestination(args, 3));
    }

    /**
     * Beams a named, online player to another player or raw coordinates. The only place this
     * command accepts console or a command block as the sender, since neither is the one
     * being moved.
     *
     * @param sender the command sender -- a player, console, or a command block
     * @param args the full argument array; the player being moved is at index 3, their
     *            destination starts at index 4
     */
    private void adminSend(final CommandSender sender, final String[] args)
    {
        if (!BeamPermissions.has(sender, BeamPermissions.ADMIN_TELEPORT))
        {
            sender.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return;
        }
        if (args.length < 5)
        {
            sender.sendMessage(sendUsage(sender));
            return;
        }
        final Player target = Bukkit.getPlayerExact(args[3]);
        if (target == null)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "beam.cmd.send.not-online", ChatText.name(args[3]))));
            return;
        }
        final Location destination = resolveDestination(sender, args, 4,
            target.getWorld(), target.getLocation().getYaw(), target.getLocation().getPitch());
        if (destination == null)
        {
            // resolveDestination has already sent the specific reason.
            return;
        }
        final String label = describeDestination(args, 4);
        final boolean started = BeamAnimation.start(target, destination, label);
        // The target already hears BeamAnimation's own messages; this is for whoever sent
        // them, who is very often not the same person (console, a command block, or a
        // different admin) and would otherwise have no idea whether it worked.
        final String said = sent(sender, started, target.getName(), label);
        sender.sendMessage(said);
    }

    /**
     * What {@code send} tells its sender once it has asked for the beam.
     *
     * <p>Package-private for the tests: the beam itself runs through {@link BeamAnimation}, which a test
     * cannot load.
     *
     * @param sender who sent the command, and reads this
     * @param started whether the beam started, or the target was already beaming
     * @param target the name of the player being moved
     * @param label where to, as {@link #describeDestination} put it
     * @return the line to send
     */
    static String sent(final CommandSender sender, final boolean started, final String target, final String label)
    {
        if (started)
        {
            return ChatText.info(Messages.get(sender, "beam.cmd.send.started", ChatText.name(target),
                ChatText.name(label)));
        }
        return ChatText.error(Messages.get(sender, "beam.cmd.send.busy", ChatText.name(target)));
    }

    /**
     * Reads a "player, or raw coordinates" argument group -- what both {@code goto} and
     * {@code send} take as their destination, and {@code send} also takes as the player being
     * moved. One token is a player name; three or four are {@code x y z [world]}, the world
     * defaulting to {@code defaultWorld} when omitted.
     *
     * @param sender who to message if the arguments don't resolve to anything
     * @param args the full argument array
     * @param start the index the group starts at
     * @param defaultWorld the world to use when coordinates are given without one
     * @param defaultYaw facing to use for coordinates, which carry none of their own
     * @param defaultPitch pitch to use for coordinates, which carry none of their own
     * @return the resolved location, or null if a message explaining why has already been sent
     */
    // Package-private, not private: the wrong-argument-count branch is exercised directly by
    // BeamCommandTest without needing a live server, the same reason
    // BeamYamlManager.readDestination/writeDestination are package-private. The two branches
    // above it call Bukkit.getPlayerExact/getWorld and stay covered by manual testing only,
    // the same as the rest of this class always has been -- this codebase has no precedent
    // for mocking Bukkit's static accessors, and this is not the place to start.
    Location resolveDestination(final CommandSender sender, final String[] args, final int start,
        final World defaultWorld, final float defaultYaw, final float defaultPitch)
    {
        final int remaining = args.length - start;
        if (remaining == 1)
        {
            return resolveNamedDestination(sender, args[start]);
        }
        // Both arms below refuse by returning null after saying why, so the only thing this
        // method decides is which question is being asked. Four of the six ways it can be
        // got wrong are still unpinned -- they run through Bukkit.getPlayerExact and
        // Bukkit.getWorld, which this suite does not mock. See BeamCommandTest.
        if ((remaining == 3) || (remaining == 4))
        {
            return resolveCoordinates(sender, args, start, defaultWorld, defaultYaw, defaultPitch);
        }
        sender.sendMessage(ChatText.error(Messages.get(sender, "beam.cmd.destination.expected",
            ChatText.usageBody("<x> <y> <z> [world]"))));
        return null;
    }

    /**
     * One token: an online player, or a public beam destination.
     *
     * @param sender
     *            who asked, for the refusals
     * @param name
     *            the player or destination name as typed
     * @return where to beam to, or null if it was neither
     */
    private Location resolveNamedDestination(final CommandSender sender, final String name)
    {
        final Player target = Bukkit.getPlayerExact(name);
        if (target != null)
        {
            return target.getLocation();
        }
        // Not an online player, so try the public destination list before giving up --
        // "send someone to spawn" is the obvious thing to reach for, and having to look
        // up spawn's coordinates by hand to express it was a gap rather than a decision.
        //
        // Public destinations only, deliberately: a private place belongs to whoever set
        // it, and for "send" that would be the target rather than the sender, so a name
        // could silently mean something the sender cannot see and never chose. An admin
        // move should not be routed through another player's private list. Players reach
        // their own places through "beam to", which checks them first by design.
        final BeamDestination destination = BeamManager.getPublicDestination(name);
        if (destination == null)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "beam.cmd.destination.none", ChatText.name(name))));
            return null;
        }
        final Location located = destination.toLocation();
        if (located == null)
        {
            // The destination outlived the world it was recorded in, or that world just
            // isn't loaded right now. Saying so beats "no such destination", which would
            // send someone looking for a typo in a name that is actually fine.
            sender.sendMessage(ChatText.error(Messages.get(sender, "beam.cmd.destination.not-loaded",
                ChatText.name(destination.name()), ChatText.name(destination.point().worldName()))));
            return null;
        }
        return located;
    }

    /**
     * Three or four tokens: coordinates, optionally in a named world.
     *
     * @param sender
     *            who asked, for the refusals
     * @param args
     *            the full argument array
     * @param start
     *            the index the destination group starts at
     * @param defaultWorld
     *            the world to use when none is named
     * @param defaultYaw
     *            the facing to carry through
     * @param defaultPitch
     *            the pitch to carry through
     * @return where to beam to, or null if a coordinate or world would not read
     */
    private Location resolveCoordinates(final CommandSender sender, final String[] args, final int start,
        final World defaultWorld, final float defaultYaw, final float defaultPitch)
    {
        final int remaining = args.length - start;
        final Double x = parseCoordinate(sender, args[start]);
        final Double y = parseCoordinate(sender, args[start + 1]);
        final Double z = parseCoordinate(sender, args[start + 2]);
        if ((x == null) || (y == null) || (z == null))
        {
            return null;
        }
        final World world;
        if (remaining == 4)
        {
            world = Bukkit.getWorld(args[start + 3]);
            if (world == null)
            {
                sender.sendMessage(ChatText.error(Messages.get(sender, "beam.cmd.destination.no-world",
                    ChatText.name(args[start + 3]))));
                return null;
            }
        }
        else
        {
            world = defaultWorld;
        }
        return new Location(world, x, y, z, defaultYaw, defaultPitch);
    }

    Double parseCoordinate(final CommandSender sender, final String raw)
    {
        try
        {
            return Double.valueOf(raw);
        }
        catch (final NumberFormatException e)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "beam.cmd.destination.bad-coordinate",
                ChatText.bad(raw))));
            return null;
        }
    }

    /**
     * A short label for what {@link #resolveDestination} just resolved, for the "Beaming to
     * X..." messages {@link BeamAnimation} sends.
     *
     * @param args the full argument array
     * @param start the index the destination group starts at, matching the call to
     *            {@link #resolveDestination}
     * @return a player's name, or the coordinates as typed
     */
    static String describeDestination(final String[] args, final int start)
    {
        return (args.length - start == 1)
            ? args[start]
            : args[start] + ", " + args[start + 1] + ", " + args[start + 2];
    }

    private void place(final Player player, final String[] args)
    {
        if (args.length < 3)
        {
            player.sendMessage(placeUsage(player));
            return;
        }
        final String sub = args[2].toLowerCase(Locale.ROOT);
        if ("list".equals(sub))
        {
            listPlaces(player);
            return;
        }
        if (CommandHandlerUtils.verbIs(sub, "set", "create"))
        {
            setPlace(player, args);
            return;
        }
        if ("remove".equals(sub))
        {
            removePlace(player, args);
            return;
        }
        // Travel used to be reachable here too ("beam place <name>"), but that meant the same
        // destination could be reached two different ways depending on whether it was public
        // or private. "to" is the one place travel happens now, whatever the destination is.
        player.sendMessage(ChatText.error(Messages.get(player, "beam.cmd.place.unknown",
            ChatText.usageBody("/wormhole beam to <name>"))));
        player.sendMessage(placeUsage(player));
    }

    private static void setPublic(final Player player, final String name)
    {
        if (dashed(player, name))
        {
            return;
        }
        BeamManager.setPublicDestination(BeamDestination.fromLocation(name, player.getLocation()));
        BeamYamlManager.saveAll();
        player.sendMessage(ChatText.info(Messages.get(player, "beam.cmd.admin.set", ChatText.name(name))));
    }

    /** Refuses a new name starting with a dash, since words that do are options. */
    private static boolean dashed(final Player player, final String name)
    {
        if (!name.startsWith("-"))
        {
            return false;
        }
        player.sendMessage(ChatText.error(Messages.get(player, "beam.cmd.dashed", ChatText.bad("-"))));
        return true;
    }

    private void listPlaces(final Player player)
    {
        final List<String> names = BeamManager.getPlaces(player.getUniqueId()).stream()
            .map(place -> ChatText.name(place.name())).toList();
        if (names.isEmpty())
        {
            player.sendMessage(ChatText.info(Messages.get(player, "beam.cmd.place.list.none")));
            return;
        }
        player.sendMessage(ChatText.info(Messages.get(player, "beam.cmd.place.list.some", joined(player, names))));
    }

    private void setPlace(final Player player, final String[] args)
    {
        if (!BeamPermissions.has(player, BeamPermissions.PLACE))
        {
            player.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return;
        }
        if (args.length < 4)
        {
            player.sendMessage(ChatText.info(ChatText.usage(player, "/wormhole beam place set <name>")));
            return;
        }
        final String name = args[3];
        if (dashed(player, name))
        {
            return;
        }
        BeamManager.setPlace(player.getUniqueId(), BeamDestination.fromLocation(name, player.getLocation()));
        BeamYamlManager.saveAll();
        player.sendMessage(ChatText.info(Messages.get(player, "beam.cmd.place.set", ChatText.name(name))));
    }

    private void removePlace(final Player player, final String[] args)
    {
        if (args.length < 4)
        {
            player.sendMessage(ChatText.info(ChatText.usage(player, "/wormhole beam place remove <name>")));
            return;
        }
        final String name = args[3];
        final boolean removed = BeamManager.removePlace(player.getUniqueId(), name);
        if (removed)
        {
            BeamYamlManager.saveAll();
        }
        final String said;
        if (removed)
        {
            said = ChatText.info(Messages.get(player, "beam.cmd.place.removed", ChatText.name(name)));
        }
        else
        {
            said = ChatText.error(Messages.get(player, "beam.cmd.place.none", ChatText.name(name)));
        }
        player.sendMessage(said);
    }
}
