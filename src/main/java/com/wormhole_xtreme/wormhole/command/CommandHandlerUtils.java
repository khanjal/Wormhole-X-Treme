package com.wormhole_xtreme.wormhole.command;

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

    public static void setGateCustomAll(final Stargate stargate, final boolean customEnabled)
    {
        if (stargate.getGateShape() != null)
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
            stargate.setGateCustom(customEnabled);
        }
        else
        {
            com.wormhole_xtreme.wormhole.WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, stargate.getGateName() + " has no valid shape file. Unable to enable custom.");
        }
    }

}
