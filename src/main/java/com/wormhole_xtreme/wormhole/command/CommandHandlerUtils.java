package com.wormhole_xtreme.wormhole.command;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;

import java.util.logging.Level;

/**
 * Shared helper methods used by individual command handlers.
 */
public final class CommandHandlerUtils
{

    private CommandHandlerUtils()
    {
        // utility class
    }

    public static boolean isValidGroupName(final String groupName)
    {
        return groupName.equalsIgnoreCase("one") || groupName.equalsIgnoreCase("two") || groupName.equalsIgnoreCase("three");
    }

    public static int doCooldownGroup(final String groupName, final boolean set, final int timeoutValue)
    {
        int group = 0;
        int oldValue = 0;
        if (groupName.equalsIgnoreCase("one"))
        {
            group = 1;
        }
        else if (groupName.equalsIgnoreCase("two"))
        {
            group = 2;
        }
        else if (groupName.equalsIgnoreCase("three"))
        {
            group = 3;
        }
        switch (group)
        {
            case 1 :
                if (set)
                {
                    oldValue = ConfigManager.getUseCooldownGroupOne();
                    ConfigManager.setUseCooldownGroupOne(timeoutValue);
                }
                return set
                    ? oldValue
                    : ConfigManager.getUseCooldownGroupOne();
            case 2 :
                if (set)
                {
                    oldValue = ConfigManager.getUseCooldownGroupTwo();
                    ConfigManager.setUseCooldownGroupTwo(timeoutValue);
                }
                return set
                    ? oldValue
                    : ConfigManager.getUseCooldownGroupTwo();
            case 3 :
                if (set)
                {
                    oldValue = ConfigManager.getUseCooldownGroupThree();
                    ConfigManager.setUseCooldownGroupThree(timeoutValue);
                }
                return set
                    ? oldValue
                    : ConfigManager.getUseCooldownGroupThree();
            default :
                return -1;
        }
    }

    public static void setGateCustomAll(final Stargate stargate, final boolean customEnabled)
    {
        if (stargate.getGateShape() != null)
        {
            if (customEnabled)
            {
                // Nothing is copied out of the shape here. Custom mode means "this gate
                // may carry its own overrides", and an override that has not been set is
                // left null so the gate keeps resolving through its shape and material
                // group. Snapshotting the shape's values used to be necessary because the
                // old inline ternaries returned the custom field unconditionally; the
                // effective-material accessors fall through instead.
                //
                // It is also actively harmful now that shapes declare no materials: the
                // snapshot would capture the built-in defaults and pin the gate to them,
                // permanently opting it out of every palette — and `custom -all true`
                // would do that to every gate on the server at once.
                stargate.setGateCustom(true);
            }
            else
            {
                stargate.setGateCustom(false);
            }
        }
        else
        {
            com.wormhole_xtreme.wormhole.WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, stargate.getGateName() + " has no valid shape file. Unable to enable custom.");
        }
    }

}
