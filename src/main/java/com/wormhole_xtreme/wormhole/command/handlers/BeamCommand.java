package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamDestination;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamPermissions;
import com.wormhole_xtreme.wormhole.model.beam.BeamTravel;
import com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager;

/**
 * Handler for {@code /wormhole beam}.
 *
 * <p>Actual travel is delegated to {@link BeamTravel}, shared with {@code /wormhole go} so the
 * two commands resolve a name the same way — still no cooldown and no claim-awareness, which
 * remain follow-up work once the core mechanic is proven out.
 *
 * <p>Travel goes through one verb, {@code to}, regardless of whether the name resolves to a
 * public destination or one of the player's own places. Earlier this took a bare name
 * (`/wormhole beam &lt;name&gt;`), which sat in the same argument slot as `list`, `admin` and
 * `place` and read as one more subcommand rather than as "the thing you are travelling to" —
 * confusing in a way `to` fixes outright, since a name that happens to collide with a verb is
 * no longer ambiguous once there is a word in front of it saying "what follows is a
 * destination."
 *
 * <pre>
 * /wormhole beam to &lt;name&gt;                travel — checks your own places first, then public
 * /wormhole beam list                      list public destinations
 * /wormhole beam admin set &lt;name&gt;         register a public destination at your location
 * /wormhole beam admin remove &lt;name&gt;      remove a public destination
 * /wormhole beam admin cost &lt;name&gt; &lt;amt&gt;  set what a public destination costs to use
 * /wormhole beam admin cost &lt;name&gt; default clear the override; use the configured default
 * /wormhole beam place list                list your own places
 * /wormhole beam place set &lt;name&gt;         save your current location as a place
 * /wormhole beam place remove &lt;name&gt;      remove one of your own places
 * </pre>
 *
 * <p>Only public destinations can have their own cost -- there is no {@code place cost}, on
 * purpose. A place is only ever reachable by the player who made it, so letting them set its
 * cost would just be them choosing what to pay themselves.
 */
public class BeamCommand implements SubCommand
{
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (!(sender instanceof Player))
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Beaming is a player-only command.");
            return true;
        }
        final Player player = (Player) sender;

        if (args.length < 2)
        {
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "/wormhole beam to <name>, beam list, beam admin set|remove|cost <name>, "
                + "beam place [list|set <name>|remove <name>]");
            return true;
        }

        final String verb = args[1].toLowerCase();
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
        if ("admin".equals(verb))
        {
            return admin(player, args);
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

    private boolean admin(final Player player, final String[] args)
    {
        if (!BeamPermissions.has(player, BeamPermissions.ADMIN))
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }
        if (args.length < 4)
        {
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "/wormhole beam admin set|remove <name>");
            return true;
        }
        final String action = args[2].toLowerCase();
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
