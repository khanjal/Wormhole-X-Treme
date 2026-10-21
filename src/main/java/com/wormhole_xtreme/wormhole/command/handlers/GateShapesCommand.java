package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.Locale;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.logic.ShapeFileValidator;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * {@code /wormhole gate shapes <reload|validate|enable|disable> [name]} -- checking and reloading a
 * shape file from disk without restarting the server, and turning a shape off for new gates.
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
    private static final String ENABLE = "enable";
    private static final String DISABLE = "disable";

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

        // args: [0]=gate [1]=shapes [2]=<reload|validate|enable|disable> [3]=name (optional for reload)
        if (args.length < 3)
        {
            sender.sendMessage(ChatText.info(ChatText.usage("/wormhole gate shapes reload|validate|enable|disable [name]")));
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
                sender.sendMessage(ChatText.info(ChatText.usage("/wormhole gate shapes validate <name>")));
                return true;
            }
            report(sender, name, StargateShapeRegistry.validateShapeFile(fileName(name)), false);
            return true;
        }

        if (ENABLE.equals(action) || DISABLE.equals(action))
        {
            switchShape(sender, name, ENABLE.equals(action));
            return true;
        }

        sender.sendMessage(ChatText.error(Messages.get(sender, "gate.shapes.unknown", ChatText.command(action),
            ChatText.command("reload"), ChatText.command("validate"), ChatText.command(ENABLE),
            ChatText.command(DISABLE))));
        return true;
    }

    /** Turns a loaded shape on or off for new gates, whatever the capitals typed. */
    private static void switchShape(final CommandSender sender, final String name, final boolean enable)
    {
        final String verb = enable ? ENABLE : DISABLE;
        if (name == null)
        {
            sender.sendMessage(ChatText.info(ChatText.usage("/wormhole gate shapes " + verb + " <name>")));
            return;
        }
        final String shape = StargateShapeRegistry.getStargateShapes().keySet().stream()
            .filter(loaded -> loaded.equalsIgnoreCase(name)).findFirst().orElse(null);
        if (shape == null)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.shapes.no-shape", ChatText.name(name))));
            return;
        }
        final boolean saved = StargateShapeRegistry.setEnabled(shape, enable);
        final String line;
        if (enable)
        {
            line = Messages.get(sender, "gate.shapes.enabled", ChatText.name(shape));
        }
        else
        {
            line = Messages.get(sender, "gate.shapes.disabled", ChatText.name(shape));
        }
        sender.sendMessage(ChatText.info(line));
        if (!saved)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.shapes.not-saved")));
        }
    }

    private static void reload(final CommandSender sender, final String name)
    {
        if (name == null)
        {
            StargateShapeRegistry.reloadAllShapes();
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.shapes.reloaded-all")));
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
        final String file = ChatText.name(fileName(name));
        if (result.isValid())
        {
            final String ok = ChatText.good(Messages.get(sender, "gate.shapes.ok"));
            final String line;
            if (wasReload)
            {
                line = Messages.get(sender, "gate.shapes.valid-loaded", file, ok, ChatText.name(result.getShapeName()));
            }
            else
            {
                line = Messages.get(sender, "gate.shapes.valid", file, ok);
            }
            sender.sendMessage(ChatText.info(line));
            return;
        }

        final String problems = ChatText.bad(problemCount(sender, result.getProblems().size()));
        final String line;
        if (wasReload)
        {
            line = Messages.get(sender, "gate.shapes.invalid-kept", file, problems);
        }
        else
        {
            line = Messages.get(sender, "gate.shapes.invalid", file, problems);
        }
        sender.sendMessage(ChatText.error(line));
        for (final String problem : result.getProblems())
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.shapes.problem", problem)));
        }
    }

    /** @return {@code 1 problem} or {@code N problems} */
    private static String problemCount(final CommandSender sender, final int count)
    {
        if (count == 1)
        {
            return Messages.get(sender, "gate.shapes.problems.one", count);
        }
        return Messages.get(sender, "gate.shapes.problems.other", count);
    }
}
