package com.wormhole_xtreme.wormhole.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Tab completer for the `/wormhole` command.
 */
public class WormholeTabCompleter implements TabCompleter
{
    private static final List<String> SUBCOMMANDS = Collections.unmodifiableList(Arrays.asList(
        "list", "build", "remove", "idc", "force", "compass", "complete", "go", "owner",
        "portalmaterial", "irismaterial", "lightmaterial", "redstone", "custom",
        "shutdown_timeout", "activate_timeout", "storage", "cooldown",
        "regenerate", "regen", "refresh"
    ));

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args)
    {
        final List<String> out = new ArrayList<>();
        if (args.length == 0)
        {
            return SUBCOMMANDS;
        }

        final String sub = args[0].toLowerCase();

        if (args.length == 1)
        {
            for (final String s : SUBCOMMANDS)
            {
                if (s.startsWith(sub))
                {
                    out.add(s);
                }
            }
            return out;
        }

        // args.length >= 2: provide context-aware completions
        switch (sub)
        {
            case "list":
                if (args.length == 2)
                {
                    final String netPfx = args[1].toLowerCase();
                    final java.util.LinkedHashSet<String> nets = new java.util.LinkedHashSet<String>();
                    nets.add("Public");
                    for (final Stargate g : StargateManager.getAllGates())
                    {
                        if (g.getGateNetwork() != null)
                        {
                            nets.add(g.getGateNetwork().getNetworkName());
                        }
                    }
                    for (final String n : nets)
                    {
                        if ((netPfx.length() == 0) || n.toLowerCase().startsWith(netPfx))
                        {
                            out.add(n);
                        }
                    }
                }
                return out;
            case "remove":
            case "idc":
            case "go":
            case "owner":
            case "portalmaterial":
            case "irismaterial":
            case "lightmaterial":
            case "redstone":
            case "custom":
            case "regenerate":
            case "regen":
            case "refresh":
                // suggest gate names for commands that operate on existing gates
                final String prefix = args[1].toLowerCase();
                for (final Stargate g : StargateManager.getAllGates())
                {
                    final String name = g.getGateName();
                    if ((prefix.length() == 0) || name.toLowerCase().startsWith(prefix))
                    {
                        out.add(name);
                    }
                }
                return out;
            case "complete":
                // `/wormhole complete` creates a NEW gate name — do NOT suggest existing gate names.
                // Suggest optional parameters for additional args: `idc=` and `net=`.
                if (args.length == 2)
                {
                    // First arg is the new gate name; don't suggest existing names or placeholders.
                    return out;
                }
                if (args.length >= 3)
                {
                    final String pfx = args[args.length - 1].toLowerCase();
                    if ("idc=".startsWith(pfx) || pfx.length() == 0)
                    {
                        out.add("idc=");
                    }
                    if ("net=".startsWith(pfx) || pfx.length() == 0)
                    {
                        out.add("net=Public");
                        out.add("net=Private");
                    }
                    return out;
                }
                return out;
            case "storage":
                if (args.length == 2)
                {
                    out.add("backend");
                    out.add("migrate");
                }
                else if (args.length >= 3 && args[1].equalsIgnoreCase("backend"))
                {
                    out.add("file");
                    out.add("sqlite");
                    out.add("mysql");
                    out.add("postgres");
                }
                else if (args.length >= 3 && args[1].equalsIgnoreCase("migrate"))
                {
                    // Suggest storage backends for both forms:
                    //   migrate <to> [force]
                    //   migrate <from> <to> [force]
                    final List<String> backends = Arrays.asList("file", "sqlite", "hsqldb", "mysql", "postgres");
                    // Determine which arg index the user is completing
                    final int completingIndex = args.length - 1;
                    final String migratePrefix = args[completingIndex].toLowerCase();
                    if ((completingIndex == 2) || (completingIndex == 3))
                    {
                        for (final String b : backends)
                        {
                            if ((migratePrefix.length() == 0) || b.startsWith(migratePrefix))
                            {
                                out.add(b);
                            }
                        }
                    }
                    else if (completingIndex == 4)
                    {
                        if ("force".startsWith(migratePrefix) || migratePrefix.length() == 0)
                        {
                            out.add("force");
                        }
                    }
                }
                return out;
            case "cooldown":
                if (args.length == 2)
                {
                    out.add("one");
                    out.add("two");
                    out.add("three");
                    out.add("true");
                    out.add("false");
                }
                return out;
            default:
                return out;
        }
    }
}
