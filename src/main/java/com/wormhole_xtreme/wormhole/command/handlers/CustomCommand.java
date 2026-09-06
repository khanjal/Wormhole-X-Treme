package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * Handler for '/wormhole custom'
 */
// Command handlers return boolean because SubCommand/CommandExecutor say so; "always true" means handled.
@SuppressWarnings("java:S3516")
public class CustomCommand implements SubCommand
{

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        // Gate management was never actually gated: none of these commands checked a
        // permission at all, so any player able to run /wormhole could reconfigure or
        // reassign any gate on the server. wormhole.config is what an admin already needs
        // for /wormhole config, so it is reused here rather than inventing a second
        // admin-only node that would mean the same thing.
        if ((sender instanceof Player player)
            && !WXPermissions.checkWXPermissions(player, PermissionType.CONFIG))
        {
            sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }

        if ((args.length == 2) || (args.length == 3))
        {
            if (args[1].equalsIgnoreCase("-clean"))
            {
                return cleanSnapshottedOverrides(sender, (args.length == 3) && "confirm".equalsIgnoreCase(args[2]));
            }
            else if (args[1].equalsIgnoreCase("-all") && (args.length == 3) && com.wormhole_xtreme.wormhole.command.CommandUtilities.isBoolean(args[2]))
            {
                for (final Stargate stargate : StargateManager.getAllGates())
                {
                    CommandHandlerUtils.setGateCustomAll(stargate, args[2].equalsIgnoreCase("true")
                        ? true
                        : false);
                }
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "All stargates with valid shapes have been set to custom mode: " + args[2]);
                return true;
            }
            else if (StargateManager.isStargate(args[1]))
            {
                final Stargate stargate = StargateManager.getStargate(args[1]);
                if (args.length == 3)
                {
                    if (com.wormhole_xtreme.wormhole.command.CommandUtilities.isBoolean(args[2]))
                    {
                        if (stargate.getGateShape() != null)
                        {
                            CommandHandlerUtils.setGateCustomAll(stargate, args[2].equalsIgnoreCase("true")
                                ? true
                                : false);
                            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Stargate is custom: " + stargate.isGateCustom());
                        }
                        else
                        {
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "No gate shape to base custom data off of!");
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Make sure the proper shape file is available!");
                        }
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid boolean option: " + args[2]);
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole custom [stargate|-all] <boolean>");
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid boolean options are: true and false");
                    }
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Stargate is custom: " + stargate.isGateCustom());
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid boolean options are: true and false");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole custom [stargate|-all] <boolean>");
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid boolean options are: true and false");
            }
            return true;
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole custom [stargate|-all] <boolean>");
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid boolean options are: true and false");
            return false;
        }

    }


    /**
     * Clears material overrides that were snapshotted from a shape rather than chosen.
     *
     * <p>{@code /wormhole custom <gate> true} used to copy the shape's four materials into
     * the gate's own override fields. Those copies pin the gate to whatever the defaults
     * were at the time and stop it following its material group, which is rarely what the
     * admin wanted — they only asked to turn custom mode on.
     *
     * <p>A coincidental match is not enough to act on: someone who deliberately set an
     * iris to stone meant stone. Only a gate whose <em>whole set</em> of four overrides
     * equals the built-in defaults is treated as a snapshot, since a person choosing all
     * four to be exactly the defaults would have had no reason to set them at all.
     *
     * <p>Reports by default and only writes when told to, because it edits stored gates.
     *
     * @param sender
     *            who asked
     * @param confirmed
     *            true to apply the change, false to only report what would change
     * @return true, the command was handled
     */
    private static boolean cleanSnapshottedOverrides(final CommandSender sender, final boolean confirmed)
    {
        final java.util.List<Stargate> affected = new java.util.ArrayList<Stargate>();
        for (final Stargate gate : StargateManager.getAllGatesUnsorted())
        {
            if (hasDefaultSnapshotOverrides(gate))
            {
                affected.add(gate);
            }
        }

        if (affected.isEmpty())
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "No gates are carrying snapshotted material overrides.");
            return true;
        }

        if (!confirmed)
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + affected.size() + " gate(s) carry material overrides matching the built-in defaults:");
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + gateNames(affected));
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "Clearing them lets those gates follow their material group.");
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "Run '/wormhole custom -clean confirm' to apply.");
            return true;
        }

        for (final Stargate gate : affected)
        {
            gate.setGateCustomStructureMaterial(null);
            gate.setGateCustomPortalMaterial(null);
            gate.setGateCustomLightMaterial(null);
            gate.setGateCustomIrisMaterial(null);
            StargateDBManager.saveStargate(gate);
        }
        WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.INFO,
            "Cleared snapshotted material overrides from " + affected.size() + " gate(s): " + gateNames(affected));
        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + "Cleared material overrides on " + affected.size() + " gate(s); they now follow their material group.");
        return true;
    }

    /**
     * Checks whether a gate's four material overrides are all set and all equal to the
     * built-in defaults — the signature of a snapshot rather than a deliberate choice.
     *
     * @param gate
     *            the gate to test
     * @return true if the overrides look snapshotted
     */
    private static boolean hasDefaultSnapshotOverrides(final Stargate gate)
    {
        return gate.getGateCustomStructureMaterial() == org.bukkit.Material.OBSIDIAN
            && gate.getGateCustomPortalMaterial() == org.bukkit.Material.WATER
            && gate.getGateCustomIrisMaterial() == org.bukkit.Material.STONE
            && gate.getGateCustomLightMaterial() == org.bukkit.Material.GLOWSTONE;
    }

    private static String gateNames(final java.util.List<Stargate> gates)
    {
        final StringBuilder sb = new StringBuilder();
        for (final Stargate g : gates)
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            sb.append(g.getGateName());
        }
        return sb.toString();
    }
}
