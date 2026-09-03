package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamDestination;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamPermissions;
import com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager;

/**
 * Handler for {@code /wormhole beam}.
 *
 * <p>Groundwork only: a plain {@link Player#teleport(Location)} with no animation, no
 * cooldown and no claim-awareness. Those are follow-up work once the core mechanic — public,
 * admin-curated destinations and private, per-player places — is proven out.
 *
 * <pre>
 * /wormhole beam &lt;name&gt;              travel to a public destination
 * /wormhole beam list                 list public destinations
 * /wormhole beam admin set &lt;name&gt;    register a public destination at your location
 * /wormhole beam admin remove &lt;name&gt; remove a public destination
 * /wormhole beam place &lt;name&gt;        travel to one of your own places
 * /wormhole beam place list           list your own places
 * /wormhole beam place set &lt;name&gt;    save your current location as a place
 * /wormhole beam place remove &lt;name&gt; remove one of your own places
 * </pre>
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
                + "/wormhole beam <name>, beam list, beam admin set|remove <name>, "
                + "beam place [<name>|list|set <name>|remove <name>]");
            return true;
        }

        final String verb = args[1].toLowerCase();
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
        return travelPublic(player, args[1]);
    }

    private boolean travelPublic(final Player player, final String name)
    {
        if (!BeamPermissions.has(player, BeamPermissions.USE))
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }
        final BeamDestination destination = BeamManager.getPublicDestination(name);
        if (destination == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "No public beam destination named \"" + name + "\".");
            return true;
        }
        return teleport(player, destination);
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
        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + "/wormhole beam admin set|remove <name>");
        return true;
    }

    private boolean place(final Player player, final String[] args)
    {
        if (args.length < 3)
        {
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "/wormhole beam place <name>|list|set <name>|remove <name>");
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
        return travelPlace(player, args[2]);
    }

    private boolean travelPlace(final Player player, final String name)
    {
        if (!BeamPermissions.has(player, BeamPermissions.USE))
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }
        final BeamDestination place = BeamManager.getPlace(player.getUniqueId(), name);
        if (place == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "You have no place named \"" + name + "\".");
            return true;
        }
        return teleport(player, place);
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

    private boolean teleport(final Player player, final BeamDestination destination)
    {
        final Location location = destination.toLocation();
        if (location == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "That destination's world is not currently loaded.");
            return true;
        }
        player.teleport(location);
        player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Beamed to " + destination.getName() + ".");
        return true;
    }
}
