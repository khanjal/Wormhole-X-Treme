package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Handler for '/wormhole portalmaterial'
 */
public class PortalMaterialCommand implements SubCommand
{

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if ((args.length == 3) || (args.length == 2))
        {
            if (StargateManager.isStargate(args[1]))
            {
                final Stargate stargate = StargateManager.getStargate(args[1]);
                if (stargate.isGateCustom())
                {
                    if (args.length == 3)
                    {
                        Material m = null;
                        try
                        {
                            m = Material.valueOf(args[2].trim().toUpperCase());
                        }
                        catch (final Exception e)
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Caught Exception on portal material" + e.getMessage());
                        }

                        if ((m != null) && ((m == Material.LAVA) || (m == Material.WATER) || (m == Material.AIR) || (m == Material.NETHER_PORTAL)))
                        {
                            stargate.setGateCustomPortalMaterial(m);
                            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " portal material set to: " + stargate.getGateCustomPortalMaterial());
                        }
                        else
                        {
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid Portal Material: " + args[2]);
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: WATER, LAVA, AIR, NETHER_PORTAL");
                        }
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " portal material is currently: " + stargate.getGateCustomPortalMaterial());
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid materials are: WATER, LAVA, AIR, NETHER_PORTAL");
                    }
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Stargate is not in custom mode. Set it with the '/wormhole custom' command");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole portalmaterial [stargate] <material>");
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: WATER, LAVA, AIR, NETHER_PORTAL");
            }
            return true;
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole portalmaterial [stargate] <material>");
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: WATER, LAVA, AIR, NETHER_PORTAL");
            return false;
        }
    }

}
