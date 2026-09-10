package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.Locale;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.ShapeFileValidator;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;

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
public class GateShapesCommand implements SubCommand
{
    // Bukkit reads the boolean as "handled"; every path here has handled it.
    @SuppressWarnings("java:S3516")
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        // Same node the rest of gate management already requires -- this reaches into the
        // shapes/gate directory and changes what every future gate on the server can be built
        // from, not something to leave open to any player who can run /wormhole.
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
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
            reload(sender, name);
            return true;
        }
        if ("validate".equals(action))
        {
            if (name == null)
            {
                sender.sendMessage("/wormhole gate shapes validate <name>");
                return true;
            }
            report(sender, name, StargateShapeRegistry.validateShapeFile(fileName(name)), false);
            return true;
        }

        sender.sendMessage("No such shapes command: " + action + ". Try reload or validate.");
        return true;
    }

    private static void reload(final CommandSender sender, final String name)
    {
        if (name == null)
        {
            StargateShapeRegistry.reloadAllShapes();
            sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                + "Reloaded every shape in the shapes/gate directory.");
            return;
        }
        report(sender, name, StargateShapeRegistry.reloadShapeFile(fileName(name)), true);
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

    private static void report(final CommandSender sender, final String name,
        final ShapeFileValidator.Result result, final boolean wasReload)
    {
        if (result.isValid())
        {
            sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                + fileName(name) + ": OK" + (wasReload ? " -- loaded as \"" + result.getShapeName() + "\"." : "."));
            return;
        }

        sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
            + fileName(name) + ": " + result.getProblems().size() + " problem"
            + (result.getProblems().size() == 1 ? "" : "s") + " found"
            + (wasReload ? " -- the previously loaded version is unchanged." : "."));
        for (final String problem : result.getProblems())
        {
            sender.sendMessage("  - " + problem);
        }
    }
}
