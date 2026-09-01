/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   The single registry of /wormhole subcommands.
 */
package com.wormhole_xtreme.wormhole.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * The one place a {@code /wormhole} subcommand is declared.
 *
 * <p>Dispatch, tab completion and the help text all read this registry. They used to be
 * three hand-maintained lists — an if/else chain, a string array in the tab completer, and
 * two copies of a help sentence — and they had drifted badly: nine subcommands the
 * completer offered were dispatched by nothing at all, while {@code wooshdepth} and
 * {@code restrict} worked but were never suggested. Anything added here now appears in all
 * three automatically.
 */
public final class SubCommands
{
    /**
     * Supplies tab-completion candidates for a subcommand's arguments.
     */
    public interface ArgCompleter
    {
        /**
         * @param args
         *            the full argument array, including the subcommand at index 0
         * @return candidate completions for the argument being typed
         */
        List<String> complete(String[] args);
    }

    /** One subcommand: how to run it, what it is called, and how to complete its arguments. */
    public static final class Entry
    {
        private final String name;
        private final List<String> aliases;
        private final String usage;
        private final SubCommand handler;
        private final boolean dropSubcommandArg;
        private final ArgCompleter completer;

        Entry(final String name, final List<String> aliases, final String usage,
            final SubCommand handler, final boolean dropSubcommandArg, final ArgCompleter completer)
        {
            this.name = name;
            this.aliases = aliases;
            this.usage = usage;
            this.handler = handler;
            this.dropSubcommandArg = dropSubcommandArg;
            this.completer = completer;
        }

        public String getName() { return name; }

        public List<String> getAliases() { return aliases; }

        /** @return a one-line usage string for help output */
        public String getUsage() { return usage; }

        /**
         * Runs the subcommand.
         *
         * <p>Handlers come in two shapes. The {@code handlers} package expects the full
         * argument array with the subcommand still at index 0; the older
         * {@link org.bukkit.command.CommandExecutor} classes were written as standalone
         * commands and expect their own arguments to start at index 0. The registry knows
         * which is which so neither had to be rewritten.
         *
         * @param sender
         *            the command sender
         * @param args
         *            the full argument array, subcommand at index 0
         * @return true if the command was handled
         */
        public boolean run(final CommandSender sender, final String[] args)
        {
            final String[] handlerArgs = dropSubcommandArg && args.length > 0
                ? Arrays.copyOfRange(args, 1, args.length)
                : args;
            return handler.execute(sender, handlerArgs);
        }

        /**
         * @param args
         *            the full argument array
         * @return completion candidates for the argument currently being typed
         */
        public List<String> completeArgs(final String[] args)
        {
            return completer == null ? Collections.<String>emptyList() : completer.complete(args);
        }
    }

    private static final Map<String, Entry> BY_NAME = new LinkedHashMap<String, Entry>();
    private static final List<Entry> ORDERED = new ArrayList<Entry>();

    private SubCommands() {}

    // -----------------------------------------------------------------------
    // Shared argument completers
    // -----------------------------------------------------------------------

    /** Completes the name of an existing gate. */
    private static final ArgCompleter GATE_NAMES = args -> args.length == 2 ? gateNames(args[1]) : none();

    /** Completes a gate name, then true/false. */
    private static final ArgCompleter GATE_THEN_BOOLEAN = args ->
    {
        if (args.length == 2) return gateNames(args[1]);
        if (args.length == 3) return prefixed(args[2], "true", "false");
        return none();
    };

    /** Completes a gate name, then a free value the plugin cannot guess. */
    private static final ArgCompleter GATE_THEN_VALUE = args -> args.length == 2 ? gateNames(args[1]) : none();

    static
    {
        // --- Gate lifecycle -------------------------------------------------
        register("list", aliases(), "/wormhole list [network]", new WXList(), true, args ->
            args.length == 2 ? networkNames(args[1]) : none());
        register("build", aliases(), "/wormhole build <shape>", new Build(), true, null);
        register("complete", aliases(), "/wormhole complete <name> [idc=IDC] [net=NET]", new Complete(), true, args ->
            // The name is new, so suggesting existing gate names would be actively wrong.
            args.length >= 3 ? prefixed(args[args.length - 1], "idc=", "net=") : none());
        register("remove", aliases("delete"), "/wormhole remove <gate>", new WXRemove(), true, GATE_NAMES);
        register("regenerate", aliases("regen"), "/wormhole regenerate <gate>",
            new com.wormhole_xtreme.wormhole.command.handlers.RegenerateCommand(), false, GATE_NAMES);
        register("refresh", aliases(), "/wormhole refresh", new Refresh(), true, null);

        // --- Travel ---------------------------------------------------------
        register("go", aliases(), "/wormhole go <gate>", new Go(), true, GATE_NAMES);
        register("compass", aliases(), "/wormhole compass", new Compass(), true, null);
        register("force", aliases(), "/wormhole force <gate>", new Force(), true, GATE_NAMES);

        // --- Per-gate settings ----------------------------------------------
        register("owner", aliases(), "/wormhole owner <gate> [player]",
            new com.wormhole_xtreme.wormhole.command.handlers.OwnerCommand(), false, GATE_THEN_VALUE);
        register("idc", aliases(), "/wormhole idc <gate> [code]", new WXIDC(), true, GATE_THEN_VALUE);
        register("redstone", aliases(), "/wormhole redstone <gate> [true|false]",
            new com.wormhole_xtreme.wormhole.command.handlers.RedstoneCommand(), false, GATE_THEN_BOOLEAN);
        register("custom", aliases(), "/wormhole custom <gate|-all|-clean> [true|false|confirm]",
            new com.wormhole_xtreme.wormhole.command.handlers.CustomCommand(), false, args ->
            {
                if (args.length == 2)
                {
                    final List<String> out = new ArrayList<String>(gateNames(args[1]));
                    out.addAll(prefixed(args[1], "-all", "-clean"));
                    return out;
                }
                if (args.length == 3)
                {
                    return "-clean".equalsIgnoreCase(args[1])
                        ? prefixed(args[2], "confirm")
                        : prefixed(args[2], "true", "false");
                }
                return none();
            });
        register("portalmaterial", aliases(), "/wormhole portalmaterial <gate> <material>",
            new com.wormhole_xtreme.wormhole.command.handlers.PortalMaterialCommand(), false, GATE_THEN_VALUE);
        register("irismaterial", aliases(), "/wormhole irismaterial <gate> <material>",
            new com.wormhole_xtreme.wormhole.command.handlers.IrisMaterialCommand(), false, GATE_THEN_VALUE);
        register("lightmaterial", aliases(), "/wormhole lightmaterial <gate> <material>",
            new com.wormhole_xtreme.wormhole.command.handlers.LightMaterialCommand(), false, GATE_THEN_VALUE);
        register("wooshdepth", aliases(), "/wormhole wooshdepth <gate> <depth>",
            new com.wormhole_xtreme.wormhole.command.handlers.WooshDepthCommand(), false, GATE_THEN_VALUE);

        // --- Server settings -------------------------------------------------
        register("perms", aliases("perm"), "/wormhole perms <player> <level>",
            new com.wormhole_xtreme.wormhole.command.handlers.PermsCommand(), false, null);
        register("shutdown_timeout", aliases("timeout"), "/wormhole shutdown_timeout <seconds>",
            new com.wormhole_xtreme.wormhole.command.handlers.TimeoutsCommand(), false, null);
        register("activate_timeout", aliases(), "/wormhole activate_timeout <seconds>",
            new com.wormhole_xtreme.wormhole.command.handlers.TimeoutsCommand(), false, null);
        register("cooldown", aliases(), "/wormhole cooldown <one|two|three|true|false> [time]",
            new com.wormhole_xtreme.wormhole.command.handlers.CooldownCommand(), false, args ->
                args.length == 2 ? prefixed(args[1], "one", "two", "three", "true", "false") : none());
        register("restrict", aliases(), "/wormhole restrict <player> [count]",
            new com.wormhole_xtreme.wormhole.command.handlers.RestrictCommand(), false, null);
        register("storage", aliases(), "/wormhole storage <backend|migrate> ...",
            new com.wormhole_xtreme.wormhole.command.handlers.StorageCommand(), false, args ->
            {
                if (args.length == 2) return prefixed(args[1], "backend", "migrate");
                final String last = args[args.length - 1];
                if ("backend".equalsIgnoreCase(args[1]) && args.length == 3)
                {
                    return prefixed(last, "file", "sqlite", "mysql", "postgres");
                }
                if ("migrate".equalsIgnoreCase(args[1]))
                {
                    // migrate <to> [force] and migrate <from> <to> [force] are both valid.
                    if (args.length == 3 || args.length == 4)
                    {
                        return prefixed(last, "file", "sqlite", "hsqldb", "mysql", "postgres");
                    }
                    if (args.length == 5) return prefixed(last, "force");
                }
                return none();
            });
    }

    private static void register(final String name, final List<String> aliases, final String usage,
        final Object handler, final boolean dropSubcommandArg, final ArgCompleter completer)
    {
        final SubCommand adapted = handler instanceof SubCommand
            ? (SubCommand) handler
            : adapt((org.bukkit.command.CommandExecutor) handler);
        final Entry entry = new Entry(name, aliases, usage, adapted, dropSubcommandArg, completer);
        ORDERED.add(entry);
        BY_NAME.put(name, entry);
        for (final String alias : aliases)
        {
            BY_NAME.put(alias, entry);
        }
    }

    /**
     * Wraps a legacy {@link org.bukkit.command.CommandExecutor} as a {@link SubCommand}.
     * These were written when each was its own top-level command; consolidating them under
     * {@code /wormhole} left the classes usable as-is.
     */
    private static SubCommand adapt(final org.bukkit.command.CommandExecutor executor)
    {
        return (sender, args) -> executor.onCommand(sender, null, "wormhole", args);
    }

    private static List<String> aliases(final String... names)
    {
        return Collections.unmodifiableList(Arrays.asList(names));
    }

    // -----------------------------------------------------------------------
    // Lookup
    // -----------------------------------------------------------------------

    /**
     * Finds a subcommand by name or alias.
     *
     * @param name
     *            the typed subcommand, case-insensitive
     * @return the entry, or null if there is no such subcommand
     */
    public static Entry find(final String name)
    {
        return name == null ? null : BY_NAME.get(name.toLowerCase());
    }

    /** @return every subcommand, in the order declared above */
    public static Collection<Entry> all()
    {
        return Collections.unmodifiableList(ORDERED);
    }

    /** @return canonical subcommand names matching the given prefix, for tab completion */
    public static List<String> namesMatching(final String prefix)
    {
        final String p = prefix == null ? "" : prefix.toLowerCase();
        final List<String> out = new ArrayList<String>();
        for (final Entry e : ORDERED)
        {
            if (e.getName().startsWith(p))
            {
                out.add(e.getName());
            }
        }
        return out;
    }

    /** @return a comma-separated list of subcommand names, for the help message */
    public static String nameList()
    {
        final StringBuilder sb = new StringBuilder();
        for (final Entry e : ORDERED)
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            sb.append(e.getName());
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Completion helpers
    // -----------------------------------------------------------------------

    private static List<String> none()
    {
        return Collections.emptyList();
    }

    private static List<String> prefixed(final String typed, final String... candidates)
    {
        final String p = typed == null ? "" : typed.toLowerCase();
        final List<String> out = new ArrayList<String>();
        for (final String c : candidates)
        {
            if (c.toLowerCase().startsWith(p))
            {
                out.add(c);
            }
        }
        return out;
    }

    private static List<String> gateNames(final String typed)
    {
        final String p = typed == null ? "" : typed.toLowerCase();
        final List<String> out = new ArrayList<String>();
        for (final Stargate g : StargateManager.getAllGatesUnsorted())
        {
            final String name = g.getGateName();
            if (name != null && name.toLowerCase().startsWith(p))
            {
                out.add(name);
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static List<String> networkNames(final String typed)
    {
        final String p = typed == null ? "" : typed.toLowerCase();
        final java.util.LinkedHashSet<String> nets = new java.util.LinkedHashSet<String>();
        nets.add("Public");
        for (final Stargate g : StargateManager.getAllGatesUnsorted())
        {
            if (g.getGateNetwork() != null)
            {
                nets.add(g.getGateNetwork().getNetworkName());
            }
        }
        final List<String> out = new ArrayList<String>();
        for (final String n : nets)
        {
            if (n.toLowerCase().startsWith(p))
            {
                out.add(n);
            }
        }
        return out;
    }
}
