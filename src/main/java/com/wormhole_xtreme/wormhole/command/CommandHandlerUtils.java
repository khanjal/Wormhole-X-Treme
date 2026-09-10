package com.wormhole_xtreme.wormhole.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

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

    /**
     * Turns away a player who may not administer gates, and says so.
     *
     * <p>Sixteen handlers asked this question, and they asked it in four different ways: a
     * pattern {@code instanceof}, a {@code playerCheck} and a cast, an inverted block with the
     * refusal in its {@code else}, and a plain negation on an already-narrowed player. Four
     * spellings of one rule is how a handler ends up without it -- which is not hypothetical
     * here. Every one of these commands could once be run by any player at all, on any gate on
     * the server, and {@code gate import} was written later and inherited the same gap because
     * there was no single thing to reuse.
     *
     * <p>The console and command blocks are deliberately allowed through. They have no
     * permissions to check, and a server owner typing into the console is already past every
     * gate this could put in front of them.
     *
     * <p>The node is {@code wormhole.config} -- the one {@code /wormhole config} already
     * needs -- rather than a second admin-only node meaning the same thing.
     *
     * @param sender
     *            whoever typed the command
     * @return true if they were refused and told why, so the caller should stop
     */
    public static boolean lacksConfigPermission(final CommandSender sender)
    {
        if ((sender instanceof Player player)
            && !WXPermissions.checkWXPermissions(player, PermissionType.CONFIG))
        {
            sender.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return true;
        }
        return false;
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
