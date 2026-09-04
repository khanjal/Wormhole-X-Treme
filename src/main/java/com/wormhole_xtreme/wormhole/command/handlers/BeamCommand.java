package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamAnimation;
import com.wormhole_xtreme.wormhole.model.beam.BeamDestination;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamPermissions;
import com.wormhole_xtreme.wormhole.model.beam.BeamTravel;
import com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

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
 * /wormhole beam admin goto &lt;x&gt; &lt;y&gt; &lt;z&gt; [world]   beam yourself to raw coordinates
 * /wormhole beam admin send &lt;target&gt; &lt;player&gt;             beam a player to another player
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
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (args.length < 2)
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "/wormhole beam to <name>, beam list, beam admin set|remove|cost|goto|send, "
                + "beam place [list|set <name>|remove <name>]");
            return true;
        }

        final String verb = args[1].toLowerCase();
        if ("admin".equals(verb))
        {
            // admin has its own sender handling: goto/send accept console and command
            // blocks, set/remove/cost stay player-only below.
            return admin(sender, args);
        }

        if (!(sender instanceof Player))
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Beaming is a player-only command.");
            return true;
        }
        final Player player = (Player) sender;

        if ("to".equals(verb))
        {
            if (args.length < 3)
            {
                player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "/wormhole beam to <name>");
                return true;
            }
            return travelTo(player, args[2]);
        }
        if ("list".equals(verb))
        {
            return listPublic(player);
        }
        if ("place".equals(verb))
        {
            return place(player, args);
        }
        player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
            + "Unknown beam command. Try /wormhole beam to <name>.");
        return true;
    }

    /**
     * Travels to a destination by name. The actual resolution and travel is shared with
     * {@code /wormhole go}, via {@link BeamTravel}, so the two commands can never disagree
     * about what a name means.
     *
     * @param player the traveller
     * @param name the destination name
     * @return true, always — command handled
     */
    private boolean travelTo(final Player player, final String name)
    {
        if (!BeamTravel.travelTo(player, name))
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "No destination named \"" + name + "\" among your places or the public list.");
        }
        return true;
    }

    private boolean listPublic(final Player player)
    {
        final StringBuilder names = new StringBuilder();
        for (final BeamDestination destination : BeamManager.getAllPublicDestinations())
        {
            if (names.length() > 0)
            {
                names.append(", ");
            }
            names.append(destination.getName());
            // Only an override is worth saying anything about -- the default cost is
            // already visible via /wormhole config BEAM_ECONOMY_USE_COST, and repeating it
            // next to every destination that hasn't been given one of its own would just be
            // noise the reader has to filter back out.
            final Double cost = destination.getCost();
            if (cost != null)
            {
                names.append(cost <= 0 ? " (free)" : " (" + cost + ")");
            }
        }
        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + (names.length() == 0 ? "No public beam destinations are set." : "Beam destinations: " + names));
        return true;
    }

    private boolean admin(final CommandSender sender, final String[] args)
    {
        if (args.length < 3)
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "/wormhole beam admin set|remove|cost <name>, goto <player>|<x> <y> <z> [world], "
                + "send <player> <player>|<x> <y> <z> [world]");
            return true;
        }
        final String action = args[2].toLowerCase();

        if ("goto".equals(action))
        {
            return adminGoto(sender, args);
        }
        if ("send".equals(action))
        {
            return adminSend(sender, args);
        }

        // Below here records or reads the sender's own location (set) or is otherwise a
        // destination-list edit that has always been player-only; goto/send are the only
        // admin actions a non-player sender can reach.
        if (!(sender instanceof Player))
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "/wormhole beam admin set|remove|cost is player-only.");
            return true;
        }
        final Player player = (Player) sender;
        if (!BeamPermissions.has(player, BeamPermissions.ADMIN))
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }
        if (args.length < 4)
        {
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "/wormhole beam admin set|remove|cost <name>");
            return true;
        }
        final String name = args[3];
        if ("set".equals(action))
        {
            BeamManager.setPublicDestination(BeamDestination.fromLocation(name, player.getLocation()));
            BeamYamlManager.saveAll();
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "Public beam destination \"" + name + "\" set to your current location.");
            return true;
        }
        if ("remove".equals(action))
        {
            final boolean removed = BeamManager.removePublicDestination(name);
            if (removed)
            {
                BeamYamlManager.saveAll();
            }
            player.sendMessage((removed ? ConfigManager.MessageStrings.normalHeader : ConfigManager.MessageStrings.errorHeader).toString()
                + (removed ? "Removed public beam destination \"" + name + "\"." : "No public beam destination named \"" + name + "\"."));
            return true;
        }
        if ("cost".equals(action))
        {
            return setCost(player, args, name);
        }
        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + "/wormhole beam admin set|remove|cost <name>");
        return true;
    }

    /**
     * Sets, or clears, one public destination's own cost override.
     *
     * @param player the admin
     * @param args the full argument array -- the amount is at index 4, one past the name
     * @param name the destination's name
     * @return true, always — command handled
     */
    private boolean setCost(final Player player, final String[] args, final String name)
    {
        if (args.length < 5)
        {
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "/wormhole beam admin cost <name> <amount|default>");
            return true;
        }
        final BeamDestination existing = BeamManager.getPublicDestination(name);
        if (existing == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "No public beam destination named \"" + name + "\".");
            return true;
        }
        final String raw = args[4];
        final Double newCost;
        if ("default".equalsIgnoreCase(raw))
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
                player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                    + "\"" + raw + "\" is not a number, or \"default\".");
                return true;
            }
            if (newCost < 0)
            {
                player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Cost cannot be negative.");
                return true;
            }
        }
        BeamManager.setPublicDestination(existing.withCost(newCost));
        BeamYamlManager.saveAll();
        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + (newCost == null
                ? "\"" + name + "\" now uses the configured default beam cost."
                : "\"" + name + "\" now costs " + newCost + " to beam to."));
        return true;
    }

    /**
     * Beams the sender to a player or raw coordinates. Player-only -- there is nowhere for
     * console or a command block to beam <em>from</em> -- unlike {@link #adminSend}, which
     * moves someone else and has no such problem.
     *
     * @param sender the command sender
     * @param args the full argument array; the target starts at index 3
     * @return true, always — command handled
     */
    private boolean adminGoto(final CommandSender sender, final String[] args)
    {
        if (!(sender instanceof Player))
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "goto moves you -- there's nowhere for console or a command block to beam from. "
                + "Use send to move a player instead.");
            return true;
        }
        final Player player = (Player) sender;
        if (!BeamPermissions.has(player, BeamPermissions.ADMIN_TELEPORT))
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }
        if (args.length < 4)
        {
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "/wormhole beam admin goto <player>|<x> <y> <z> [world]");
            return true;
        }
        final Location destination = resolveDestination(player, args, 3,
            player.getWorld(), player.getLocation().getYaw(), player.getLocation().getPitch());
        if (destination == null)
        {
            // resolveDestination has already sent the specific reason.
            return true;
        }
        // BeamAnimation.start already refuses (and messages the player) if they're mid-beam,
        // and sends its own "Beaming to X..."/"Beamed to X." messages either way -- nothing
        // further to say here regardless of which way it goes.
        BeamAnimation.start(player, WorldUtils.findSafePlayerLocation(destination), describeDestination(args, 3));
        return true;
    }

    /**
     * Beams a named, online player to another player or raw coordinates. The only place this
     * command accepts console or a command block as the sender, since neither is the one
     * being moved.
     *
     * @param sender the command sender -- a player, console, or a command block
     * @param args the full argument array; the player being moved is at index 3, their
     *            destination starts at index 4
     * @return true, always — command handled
     */
    private boolean adminSend(final CommandSender sender, final String[] args)
    {
        if (!BeamPermissions.has(sender, BeamPermissions.ADMIN_TELEPORT))
        {
            sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }
        if (args.length < 5)
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "/wormhole beam admin send <player> <player>|<x> <y> <z> [world]");
            return true;
        }
        final Player target = Bukkit.getPlayerExact(args[3]);
        if (target == null)
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "No online player named \"" + args[3] + "\".");
            return true;
        }
        final Location destination = resolveDestination(sender, args, 4,
            target.getWorld(), target.getLocation().getYaw(), target.getLocation().getPitch());
        if (destination == null)
        {
            // resolveDestination has already sent the specific reason.
            return true;
        }
        final String label = describeDestination(args, 4);
        final boolean started = BeamAnimation.start(target, WorldUtils.findSafePlayerLocation(destination), label);
        // The target already hears BeamAnimation's own messages; this is for whoever sent
        // them, who is very often not the same person (console, a command block, or a
        // different admin) and would otherwise have no idea whether it worked.
        sender.sendMessage((started ? ConfigManager.MessageStrings.normalHeader : ConfigManager.MessageStrings.errorHeader).toString()
            + (started
                ? "Beaming " + target.getName() + " to " + label + "."
                : target.getName() + " is already beaming somewhere."));
        return true;
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
            final Player target = Bukkit.getPlayerExact(args[start]);
            if (target == null)
            {
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                    + "No online player named \"" + args[start] + "\".");
                return null;
            }
            return target.getLocation();
        }
        if ((remaining == 3) || (remaining == 4))
        {
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
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                        + "No world named \"" + args[start + 3] + "\".");
                    return null;
                }
            }
            else
            {
                world = defaultWorld;
            }
            return new Location(world, x, y, z, defaultYaw, defaultPitch);
        }
        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + "Expected a player name, or <x> <y> <z> [world].");
        return null;
    }

    Double parseCoordinate(final CommandSender sender, final String raw)
    {
        try
        {
            return Double.valueOf(raw);
        }
        catch (final NumberFormatException e)
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "\"" + raw + "\" is not a valid coordinate.");
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

    private boolean place(final Player player, final String[] args)
    {
        if (args.length < 3)
        {
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "/wormhole beam place list|set <name>|remove <name>");
            return true;
        }
        final String sub = args[2].toLowerCase();
        if ("list".equals(sub))
        {
            return listPlaces(player);
        }
        if ("set".equals(sub))
        {
            return setPlace(player, args);
        }
        if ("remove".equals(sub))
        {
            return removePlace(player, args);
        }
        // Travel used to be reachable here too ("beam place <name>"), but that meant the same
        // destination could be reached two different ways depending on whether it was public
        // or private. "to" is the one place travel happens now, whatever the destination is.
        player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
            + "Unknown. Try /wormhole beam place list|set <name>|remove <name>, "
            + "or /wormhole beam to <name> to travel.");
        return true;
    }

    private boolean listPlaces(final Player player)
    {
        final StringBuilder names = new StringBuilder();
        for (final BeamDestination place : BeamManager.getPlaces(player.getUniqueId()))
        {
            if (names.length() > 0)
            {
                names.append(", ");
            }
            names.append(place.getName());
        }
        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + (names.length() == 0 ? "You have no places set." : "Your places: " + names));
        return true;
    }

    private boolean setPlace(final Player player, final String[] args)
    {
        if (!BeamPermissions.has(player, BeamPermissions.PLACE))
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }
        if (args.length < 4)
        {
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "/wormhole beam place set <name>");
            return true;
        }
        final String name = args[3];
        BeamManager.setPlace(player.getUniqueId(), BeamDestination.fromLocation(name, player.getLocation()));
        BeamYamlManager.saveAll();
        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + "Place \"" + name + "\" set to your current location.");
        return true;
    }

    private boolean removePlace(final Player player, final String[] args)
    {
        if (args.length < 4)
        {
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "/wormhole beam place remove <name>");
            return true;
        }
        final String name = args[3];
        final boolean removed = BeamManager.removePlace(player.getUniqueId(), name);
        if (removed)
        {
            BeamYamlManager.saveAll();
        }
        player.sendMessage((removed ? ConfigManager.MessageStrings.normalHeader : ConfigManager.MessageStrings.errorHeader).toString()
            + (removed ? "Removed place \"" + name + "\"." : "You have no place named \"" + name + "\"."));
        return true;
    }
}
