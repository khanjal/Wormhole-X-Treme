package com.wormhole_xtreme.wormhole.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Tab completer for the `/dial` command to suggest gate names.
 */
public class DialTabCompleter implements TabCompleter
{
    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args)
    {
        final List<String> out = new ArrayList<>();
        if (args.length == 0)
        {
            return out;
        }
        if (args.length == 1)
        {
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            for (final Stargate g : StargateManager.getAllGates())
            {
                final String name = g.getGateName();
                if ((prefix.length() == 0) || name.toLowerCase(Locale.ROOT).startsWith(prefix))
                {
                    out.add(name);
                }
            }
        }
        return out;
    }
}
