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
        private boolean hidden;

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

        /**
         * Whether this name is kept working but no longer advertised.
         *
         * <p>The flat gate commands moved under {@code /wormhole gate}, and the four
         * settings commands under {@code /wormhole config}. The old names still dispatch, so
         * nothing in a command block or a script broke, but they are left out of help and
         * tab completion -- otherwise the restructure would have made the list longer rather
         * than shorter.
         *
         * @return true if it is a legacy name
         */
        public boolean isHidden() { return hidden; }

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
        register("compass", aliases(), "/wormhole compass [reset]", new Compass(), true,
            args -> args.length == 2 ? prefixed(args[1], "reset") : none());
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

        // --- Transport rings --------------------------------------------------
        register("ring", aliases("rings"), "/wormhole ring <create|cancel|list|remove|edit|allow|deny|owner>",
            new com.wormhole_xtreme.wormhole.command.handlers.RingCommand(), false,
            SubCommands::completeRing);

        // --- Beaming ------------------------------------------------------------
        register("beam", aliases(), "/wormhole beam <name>|list|admin <set|remove>|place <name>|list|set|remove>",
            new com.wormhole_xtreme.wormhole.command.handlers.BeamCommand(), false,
            SubCommands::completeBeam);

        // --- Server settings -------------------------------------------------
        register("shutdown_timeout", aliases("timeout"), "/wormhole shutdown_timeout <seconds>",
            new com.wormhole_xtreme.wormhole.command.handlers.TimeoutsCommand(), false, null);
        register("activate_timeout", aliases(), "/wormhole activate_timeout <seconds>",
            new com.wormhole_xtreme.wormhole.command.handlers.TimeoutsCommand(), false, null);
        register("cooldown", aliases(), "/wormhole cooldown <one|two|three|true|false> [time]",
            new com.wormhole_xtreme.wormhole.command.handlers.CooldownCommand(), false, args ->
                args.length == 2 ? prefixed(args[1], "one", "two", "three", "true", "false") : none());
        register("restrict", aliases(), "/wormhole restrict <true|false>",
            new com.wormhole_xtreme.wormhole.command.handlers.RestrictCommand(), false, args ->
                args.length == 2 ? prefixed(args[1], "true", "false") : none());

        // --- The shape people actually type --------------------------------
        // Everything above stays registered and keeps working; it is just no longer what is
        // advertised. Four names now cover it: two nouns that behave alike, the settings, and
        // the one thing that is neither.
        register("gate", aliases("gates"),
            "/wormhole gate <" + String.join("|",
                com.wormhole_xtreme.wormhole.command.handlers.GateCommand.verbs()) + ">",
            new com.wormhole_xtreme.wormhole.command.handlers.GateCommand(), false,
            SubCommands::completeGate);
        register("config", aliases("set"), "/wormhole config <setting> [value]",
            new com.wormhole_xtreme.wormhole.command.handlers.ConfigCommand(), false, args ->
                args.length == 2 ? prefixed(args[1],
                    com.wormhole_xtreme.wormhole.config.ConfigManager.settingNames()
                        .toArray(new String[0])) : none());

        hide("list", "build", "complete", "remove", "regenerate", "refresh", "go", "force",
            "owner", "idc", "redstone", "custom", "portalmaterial", "irismaterial",
            "lightmaterial", "wooshdepth", "shutdown_timeout", "activate_timeout",
            "cooldown", "restrict");
    }

    /**
     * Completes the arguments of {@code /wormhole gate}.
     *
     * @param args
     *            the full argument array
     * @return the candidates for the argument being typed
     */
    private static List<String> completeGate(final String[] args)
    {
        if (args.length == 2)
        {
            return prefixed(args[1],
                com.wormhole_xtreme.wormhole.command.handlers.GateCommand.verbs()
                    .toArray(new String[0]));
        }
        final String verb = args[1].toLowerCase();
        if ("edit".equals(verb))
        {
            // gate edit <gate> <field> [value]
            if (args.length == 3) return gateNames(args[2]);
            if (args.length == 4)
            {
                return prefixed(args[3],
                    com.wormhole_xtreme.wormhole.command.handlers.GateEditCommand.fieldNames()
                        .toArray(new String[0]));
            }
            if (args.length == 5)
            {
                final String field = args[3].toLowerCase();
                if ("group".equals(field))
                {
                    return prefixed(args[4],
                        com.wormhole_xtreme.wormhole.command.handlers.GateEditCommand.groupNames()
                            .toArray(new String[0]));
                }
                if ("redstone".equals(field)) return prefixed(args[4], "true", "false");
                if ("portal".equals(field) || "iris".equals(field) || "light".equals(field))
                {
                    return materialNames(args[4], false);
                }
            }
            return none();
        }
        if ("build".equals(verb))
        {
            return args.length == 3 ? shapeNames(args[2]) : none();
        }
        if (("regenerate".equals(verb) || "regen".equals(verb)) && (args.length == 3))
        {
            // -all fixes every gate's arrival point in one pass; alongside gate names so
            // either is offered without knowing in advance which the admin wants.
            final java.util.List<String> out = new ArrayList<String>(gateNames(args[2]));
            out.addAll(prefixed(args[2], "-all"));
            return out;
        }
        // Every other verb takes a gate name first, and nothing after it worth guessing at.
        return args.length == 3 ? gateNames(args[2]) : none();
    }

    /**
     * Marks the named subcommands as kept-working but unadvertised.
     *
     * @param names
     *            the legacy names
     */
    private static void hide(final String... names)
    {
        for (final String name : names)
        {
            final Entry e = BY_NAME.get(name);
            if (e != null)
            {
                e.hidden = true;
            }
        }
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
            if (!e.isHidden() && e.getName().startsWith(p))
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
            if (e.isHidden())
            {
                continue;
            }
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

    /** The fields {@code /wormhole ring edit} understands. */
    private static final String[] RING_FIELDS = { "ring", "light", "flash", "built", "name", "access", "style", "reset" };

    /**
     * Completions for {@code /wormhole ring}.
     *
     * <p>{@code edit} comes in two forms — with a pair id and without — so the field can be
     * at either of two positions and the value at either of two more. Rather than guess from
     * the argument count alone, this looks at whether the word before the one being typed is
     * a field name, which tells the two forms apart wherever they are.
     *
     * @param args
     *            the full argument array, {@code ring} at index 0
     * @return the candidates
     */
    private static List<String> completeRing(final String[] args)
    {
        if (args.length == 2)
        {
            return prefixed(args[1], "create", "cancel", "list", "remove", "edit",
                "allow", "deny", "owner");
        }
        if (!"edit".equalsIgnoreCase(args[1]))
        {
            return none();
        }
        // Typing the word straight after "edit": either a field, or an id with the field
        // still to come. Ids cannot be offered because a completer is not told who is asking,
        // and listing every pair on the server would say more than it should.
        if (args.length == 3)
        {
            return prefixed(args[2], RING_FIELDS);
        }
        // Otherwise the previous word decides: a field means a value goes here, and anything
        // else means that word was an id and the field goes here instead.
        final String previous = args[args.length - 2];
        final String typed = args[args.length - 1];
        return isRingField(previous) ? ringFieldValues(previous, typed) : prefixed(typed, RING_FIELDS);
    }

    /**
     * Completions for {@code /wormhole beam}.
     *
     * @param args
     *            the full argument array, {@code beam} at index 0
     * @return the candidates
     */
    private static List<String> completeBeam(final String[] args)
    {
        if (args.length == 2)
        {
            final List<String> out = new ArrayList<String>(prefixed(args[1], "list", "admin", "place"));
            out.addAll(publicBeamNames(args[1]));
            return out;
        }
        final String noun = args[1].toLowerCase();
        if ("admin".equals(noun))
        {
            if (args.length == 3) return prefixed(args[2], "set", "remove");
            if (args.length == 4 && "remove".equals(args[2].toLowerCase())) return publicBeamNames(args[3]);
            return none();
        }
        if ("place".equals(noun))
        {
            // A place name is not completed even for "remove": a tab completer is not handed
            // the CommandSender, only the argument array, so there is no "the player asking"
            // to look place names up for.
            if (args.length == 3) return prefixed(args[2], "list", "set", "remove");
            return none();
        }
        return none();
    }

    private static List<String> publicBeamNames(final String typed)
    {
        final String p = typed == null ? "" : typed.toLowerCase();
        final List<String> out = new ArrayList<String>();
        for (final com.wormhole_xtreme.wormhole.model.beam.BeamDestination destination
            : com.wormhole_xtreme.wormhole.model.beam.BeamManager.getAllPublicDestinations())
        {
            if (destination.getName().toLowerCase().startsWith(p))
            {
                out.add(destination.getName());
            }
        }
        return out;
    }

    /**
     * Whether a word is one of the editable fields.
     *
     * @param word
     *            the word to test
     * @return true if it names a field
     */
    private static boolean isRingField(final String word)
    {
        for (final String field : RING_FIELDS)
        {
            if (field.equalsIgnoreCase(word))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * What can go in a given field.
     *
     * <p>The material fields are the reason this exists. There are around sixty slabs and
     * several hundred blocks in the game, and nobody remembers how {@code polished_deepslate}
     * is spelled — so the ring field offers only what it will actually accept, and the light
     * field offers anything placeable.
     *
     * @param field
     *            which field is being set
     * @param typed
     *            what has been typed of the value so far
     * @return the candidates
     */
    private static List<String> ringFieldValues(final String field, final String typed)
    {
        if ("access".equalsIgnoreCase(field))
        {
            return prefixed(typed, "public", "private");
        }
        if ("style".equalsIgnoreCase(field))
        {
            return prefixed(typed, "fast", "slow", "concurrent", "sequential");
        }
        if ("ring".equalsIgnoreCase(field) || "built".equalsIgnoreCase(field))
        {
            // Only slabs, because only a slab can move half a block at a time, which is the
            // whole of the rise animation. Offering anything else would be offering something
            // the command is about to refuse. built shares the constraint: it names the same
            // kind of slab, just recorded rather than currently worn.
            return materialNames(typed, true);
        }
        if ("light".equalsIgnoreCase(field) || "flash".equalsIgnoreCase(field))
        {
            // Solid blocks that read as glowing. Offering all several hundred blocks was a
            // list nobody could use, and most of them look wrong set into a floor.
            return glowingNames(typed);
        }
        // A name is whatever the player wants, and reset takes no value at all.
        return none();
    }

    /**
     * Glowing block names matching what has been typed.
     *
     * @param typed
     *            what has been typed so far
     * @return the matching names, lower case
     */
    private static List<String> glowingNames(final String typed)
    {
        final String p = typed == null ? "" : typed.toLowerCase();
        final List<String> out = new ArrayList<String>();
        for (final org.bukkit.Material material
            : com.wormhole_xtreme.wormhole.model.ring.Ring.glowingMaterials())
        {
            final String name = material.name().toLowerCase();
            if (name.startsWith(p))
            {
                out.add(name);
            }
        }
        Collections.sort(out);
        return out;
    }

    /**
     * Material names matching what has been typed.
     *
     * @param typed
     *            what has been typed so far
     * @param slabsOnly
     *            true to offer only slabs
     * @return the matching material names, lower case
     */
    /**
     * Whether the registry can answer what is a block.
     *
     * <p>From 1.20.6 on, {@link org.bukkit.Material#isBlock()} goes through the server's
     * registry, which is not there before the server has finished starting -- and asking then
     * throws rather than returning false. Probed once, because the answer cannot change
     * within a run and a failed registry lookup is expensive to repeat for every material in
     * the game on every tab press.
     */
    private static boolean blockCheckWorks = false;

    /**
     * Asks whether the block check is usable.
     *
     * @return true if it answered
     */
    private static boolean probeBlockCheck()
    {
        try
        {
            org.bukkit.Material.STONE.isBlock();
            return true;
        }
        // Throwable rather than Exception: a registry that is not ready fails in class
        // initialisation, which arrives as an Error.
        catch (final Throwable ignored)
        {
            return false;
        }
    }

    /**
     * Whether a material is a block, as far as this server can say.
     *
     * <p>Without a registry to ask, everything is offered rather than nothing. A completion
     * list with a few items in it is a much smaller problem than an empty one, and on a
     * running server -- which is the only place a player can press tab -- the registry is
     * always there.
     *
     * @param material
     *            the material to judge
     * @return true if it is a block, or if that cannot be determined
     */
    private static boolean isBlock(final org.bukkit.Material material)
    {
        // Only a yes is remembered. A no means the registry was not ready when we asked,
        // and that changes: this class is loaded while the plugin is still enabling, so a
        // single probe cached at class-init would have recorded "no registry" for the life of
        // the server and gone on offering every material in the game for ever. Asking again
        // costs one call until the first time it works.
        if (!blockCheckWorks)
        {
            blockCheckWorks = probeBlockCheck();
        }
        return !blockCheckWorks || material.isBlock();
    }

    private static List<String> materialNames(final String typed, final boolean slabsOnly)
    {
        final String p = typed == null ? "" : typed.toLowerCase();
        final List<String> out = new ArrayList<String>();
        for (final org.bukkit.Material material : org.bukkit.Material.values())
        {
            // Legacy materials are duplicates of real ones under old names, and offering
            // them would double the list with things nobody should be typing.
            if (material.isLegacy() || !isBlock(material))
            {
                continue;
            }
            if (slabsOnly && !com.wormhole_xtreme.wormhole.model.ring.Ring.isUsableAsRing(material))
            {
                continue;
            }
            final String name = material.name().toLowerCase();
            if (name.startsWith(p))
            {
                out.add(name);
            }
        }
        Collections.sort(out);
        return out;
    }

    /**
     * Completes the name of a gate shape.
     *
     * <p>{@code build} never offered these, which meant the one argument it takes had to be
     * remembered or read out of the shapes directory.
     *
     * @param typed
     *            what has been typed so far
     * @return the matching shape names
     */
    private static List<String> shapeNames(final String typed)
    {
        final String p = typed == null ? "" : typed.toLowerCase();
        final List<String> out = new ArrayList<String>();
        for (final String name
            : com.wormhole_xtreme.wormhole.model.StargateShapeRegistry.getStargateShapes().keySet())
        {
            if (name.toLowerCase().startsWith(p))
            {
                out.add(name);
            }
        }
        Collections.sort(out);
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
