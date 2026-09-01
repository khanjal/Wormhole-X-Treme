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
 * Handler for '/wormhole lightmaterial'
 */
public class LightMaterialCommand implements SubCommand
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
                            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Caught Exception on light material" + e.getMessage());
                        }

                        if ((m != null) && ((m == Material.GLOWSTONE) || (m == Material.REDSTONE_ORE)))
                        {
                            stargate.setGateCustomLightMaterial(m);
                            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " light material set to: " + stargate.getGateCustomLightMaterial());
                        }
                        else
                        {
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid Light Material: " + args[2]);
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: GLOWSTONE, REDSTONE_ORE");
                        }
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " light material is currently: " + stargate.getGateCustomLightMaterial());
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid materials are: GLOWSTONE, REDSTONE_ORE");
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
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole lightmaterial [stargate] <material>");
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: GLOWSTONE, REDSTONE_ORE");
            }
            return true;
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole lightmaterial [stargate] <material>");
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: GLOWSTONE, REDSTONE_ORE");
            return false;
        }
    }

}
