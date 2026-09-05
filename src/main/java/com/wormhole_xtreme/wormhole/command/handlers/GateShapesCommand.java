package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.Locale;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.ShapeFileValidator;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * {@code /wormhole gate shapes <reload|validate> [name]} -- checking and reloading a shape
 * file from disk without restarting the server.
 *
 * <p>Shapes only ever loaded once, at startup ({@link StargateShapeRegistry#loadShapes()}),
 * whose own "name already exists" rule keeps whichever version loaded first -- exactly wrong
 * for someone iterating on a shape file, where every edit after the first would silently do
 * nothing. {@code reload} uses {@link StargateShapeRegistry#reloadShapeFile} instead, which
 * replaces the existing entry on a valid reload rather than refusing to touch it.
 *
 * <p>{@code validate} runs the same checks without changing anything loaded -- for looking a
 * shape over before it is worth loading at all, or confirming a fix landed without disturbing
 * whatever a gate is already standing on.
 */
// Command handlers return boolean because SubCommand/CommandExecutor say so; "always true" means handled.
@SuppressWarnings("java:S3516")
public class GateShapesCommand implements SubCommand
{
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        // Same node the rest of gate management already requires -- this reaches into the
        // GateShapes directory and changes what every future gate on the server can be built
        // from, not something to leave open to any player who can run /wormhole.
        if ((sender instanceof Player)
            && !WXPermissions.checkWXPermissions((Player) sender, PermissionType.CONFIG))
        {
            sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }

        // args: [0]=gate [1]=shapes [2]=<reload|validate> [3]=name (optional for reload)
        if (args.length < 3)
        {
            sender.sendMessage("/wormhole gate shapes <reload [name]|validate <name>>");
            return true;
        }

        final String action = args[2].toLowerCase(Locale.ROOT);
        final String name = (args.length >= 4) ? args[3] : null;

        if ("reload".equals(action))
        {
            return reload(sender, name);
        }
        if ("validate".equals(action))
        {
            if (name == null)
            {
                sender.sendMessage("/wormhole gate shapes validate <name>");
                return true;
            }
            return report(sender, name, StargateShapeRegistry.validateShapeFile(fileName(name)), false);
        }

        sender.sendMessage("No such shapes command: " + action + ". Try reload or validate.");
        return true;
    }

    private static boolean reload(final CommandSender sender, final String name)
    {
        if (name == null)
        {
            StargateShapeRegistry.reloadAllShapes();
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "Reloaded every shape in the GateShapes directory.");
            return true;
        }
        return report(sender, name, StargateShapeRegistry.reloadShapeFile(fileName(name)), true);
    }

    /**
     * @param name
     *            what the player typed, with or without the {@code .shape} extension
     * @return the file name to actually look for
     */
    private static String fileName(final String name)
    {
        return name.endsWith(".shape") ? name : (name + ".shape");
    }

    private static boolean report(final CommandSender sender, final String name,
        final ShapeFileValidator.Result result, final boolean wasReload)
    {
        if (result.isValid())
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + fileName(name) + ": OK" + (wasReload ? " -- loaded as \"" + result.getShapeName() + "\"." : "."));
            return true;
        }

        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
            + fileName(name) + ": " + result.getProblems().size() + " problem"
            + (result.getProblems().size() == 1 ? "" : "s") + " found"
            + (wasReload ? " -- the previously loaded version is unchanged." : "."));
        for (final String problem : result.getProblems())
        {
            sender.sendMessage("  - " + problem);
        }
        return true;
    }
}
