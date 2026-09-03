package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamAnimation;
import com.wormhole_xtreme.wormhole.model.beam.BeamDestination;
import com.wormhole_xtreme.wormhole.model.beam.BeamFreeze;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamPermissions;
import com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager;

/**
 * Handler for {@code /wormhole beam}.
 *
 * <p>Travel runs the charge/beam-up/teleport/beam-down sequence in {@link BeamAnimation}
 * rather than a plain {@link Player#teleport(Location)} — still no cooldown and no
 * claim-awareness, which remain follow-up work once the core mechanic is proven out.
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
 * /wormhole beam to &lt;name&gt;           travel — checks your own places first, then public
 * /wormhole beam list                 list public destinations
 * /wormhole beam admin set &lt;name&gt;    register a public destination at your location
 * /wormhole beam admin remove &lt;name&gt; remove a public destination
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
                + "/wormhole beam to <name>, beam list, beam admin set|remove <name>, "
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
     * Travels to a destination by name, checking the player's own places before the public
     * list. A private place is a deliberate, personal choice, so it is the one that wins if a
     * player happens to have named one the same as something public.
     *
     * @param player the traveller
     * @param name the destination name
     * @return true, always — command handled
     */
    private boolean travelTo(final Player player, final String name)
    {
        if (!BeamPermissions.has(player, BeamPermissions.USE))
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }
        BeamDestination destination = BeamManager.getPlace(player.getUniqueId(), name);
        if (destination == null)
        {
            destination = BeamManager.getPublicDestination(name);
        }
        if (destination == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "No destination named \"" + name + "\" among your places or the public list.");
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

    private boolean teleport(final Player player, final BeamDestination destination)
    {
        if (BeamFreeze.isFrozen(player))
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "You're already beaming somewhere.");
            return true;
        }
        final Location location = destination.toLocation();
        if (location == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "That destination's world is not currently loaded.");
            return true;
        }
        BeamAnimation.start(player, location, destination.getName());
        return true;
    }
}
