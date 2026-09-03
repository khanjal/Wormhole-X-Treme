package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Handler for '/wormhole regenerate' (regen)
 */
public class RegenerateCommand implements SubCommand
{

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (args.length >= 2)
        {
            final Stargate s = StargateManager.getStargate(args[1]);
            if (s != null)
            {
                if ((s.getGateShape() != null) && StargateHelper.isStargateShape(s.getGateShape().getShapeName()))
                {
                    // Shape format (2D/3D) is determined at load time; no runtime upgrade needed.
                }
                // The exit is worked out once when a gate is built and then stored, so a
                // gate that landed travellers at its side kept doing it for ever. This is
                // the command people already reach for when a gate is misbehaving, so it is
                // where the fix belongs.
                if (s.recomputeGatePlayerTeleportLocation())
                {
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                        + "Arrival point recomputed for " + s.getGateName() + ".");
                }
                s.toggleDialLeverState(true);
                if ((s.getGateIrisDeactivationCode() != null) && (s.getGateIrisDeactivationCode().length() > 0))
                {
                    s.setupIrisLever(true);
                }
                if (s.isGateRedstonePowered())
                {
                    s.setupRedstone(true);
                }
                s.setupGateSign(true);
                if (s.isGateSignPowered() && s.getGateDialSignBlock() != null)
                {
                    StargateManager.refreshTeleportSign(s, true);
                }
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Regenerating Gate: " + s.getGateName());
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.constructNameInvalid.toString() + "\"" + args[1] + "\"");
            }
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.gateNotSpecified.toString());
            return false;
        }
        return true;
    }

}
