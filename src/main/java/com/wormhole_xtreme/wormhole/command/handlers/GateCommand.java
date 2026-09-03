package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.Build;
import com.wormhole_xtreme.wormhole.command.Complete;
import com.wormhole_xtreme.wormhole.command.Force;
import com.wormhole_xtreme.wormhole.command.Go;
import com.wormhole_xtreme.wormhole.command.Refresh;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.command.WXList;
import com.wormhole_xtreme.wormhole.command.WXRemove;

/**
 * Everything you do to a gate, under one name.
 *
 * <p>Gates had fifteen top-level commands while the rings had one with verbs under it. This
 * is the gates catching up: {@code /wormhole gate <verb>} reads the same way
 * {@code /wormhole ring <verb>} already did, and a new gate verb costs a line here instead
 * of another name at the top level.
 *
 * <p>Every verb hands straight off to the handler that already owned it, so nothing about
 * what these commands <em>do</em> has changed. The old flat names still work too -- they are
 * registered as hidden entries -- so nothing in a command block or a script breaks.
 */
public class GateCommand implements SubCommand
{
    /** The verbs, in the order they are offered. */
    private static final List<String> VERBS = Arrays.asList(
        "build", "complete", "list", "remove", "edit", "regenerate", "refresh", "go", "force");

    /**
     * The verbs, for tab completion and help.
     *
     * @return the verb names
     */
    public static List<String> verbs()
    {
        return new ArrayList<String>(VERBS);
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (args.length < 2)
        {
            sender.sendMessage("/wormhole gate <" + String.join("|", VERBS) + ">");
            return true;
        }
        final String verb = args[1].toLowerCase();
        // What the verb's own handler expects. The older ones were written as standalone
        // commands and read their arguments from index zero; the newer ones take the whole
        // array with the subcommand still in front. Both shapes are fed what they expect
        // rather than being rewritten.
        final String[] rest = Arrays.copyOfRange(args, 2, args.length);

        if ("edit".equals(verb))
        {
            return new GateEditCommand().execute(sender, args);
        }
        if ("regenerate".equals(verb) || "regen".equals(verb))
        {
            final String[] forHandler = new String[rest.length + 1];
            forHandler[0] = "regenerate";
            System.arraycopy(rest, 0, forHandler, 1, rest.length);
            return new RegenerateCommand().execute(sender, forHandler);
        }
        if ("build".equals(verb))
        {
            return new Build().onCommand(sender, null, verb, rest);
        }
        if ("complete".equals(verb))
        {
            return new Complete().onCommand(sender, null, verb, rest);
        }
        if ("list".equals(verb))
        {
            return new WXList().onCommand(sender, null, verb, rest);
        }
        if ("remove".equals(verb) || "delete".equals(verb))
        {
            return new WXRemove().onCommand(sender, null, verb, rest);
        }
        if ("refresh".equals(verb))
        {
            return new Refresh().onCommand(sender, null, verb, rest);
        }
        if ("go".equals(verb))
        {
            return new Go().onCommand(sender, null, verb, rest);
        }
        if ("force".equals(verb))
        {
            return new Force().onCommand(sender, null, verb, rest);
        }

        sender.sendMessage("No such gate command: " + args[1] + ". Try one of: "
            + String.join(", ", VERBS) + ".");
        return true;
    }
}
