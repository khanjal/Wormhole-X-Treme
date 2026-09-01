package com.wormhole_xtreme.wormhole.command;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * Tab completer for the {@code /wormhole} command.
 *
 * <p>Both the subcommand names and their argument completions come from
 * {@link SubCommands}, the same registry the dispatcher runs from. Keeping a separate list
 * here is what let the completer drift into offering nine subcommands that no longer
 * existed while hiding two that did.
 */
public class WormholeTabCompleter implements TabCompleter
{
    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
        final String alias, final String[] args)
    {
        if (args.length <= 1)
        {
            return SubCommands.namesMatching(args.length == 0 ? "" : args[0]);
        }
        final SubCommands.Entry entry = SubCommands.find(args[0]);
        return entry == null ? java.util.Collections.<String>emptyList() : entry.completeArgs(args);
    }
}
