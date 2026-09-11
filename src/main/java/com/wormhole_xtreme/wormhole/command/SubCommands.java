package com.wormhole_xtreme.wormhole.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * completer offered were dispatched by nothing at all, while {@code wooshdepth} worked but
 * was never suggested. Anything added here now appears in all three automatically.
 */
public final class SubCommands
{
    // Paired: Sonar only asks for FALSE, but one of the two spelled out and the
    // other named reads like a mistake at every call site.
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String BUILD = "build";
    private static final String REMOVE = "remove";
    private static final String REGENERATE = "regenerate";
    private static final String OWNER = "owner";
    private static final String REDSTONE = "redstone";
    private static final String LIGHT = "light";

    /**
     * Supplies tab-completion candidates for a subcommand's arguments.
     */
    public interface ArgCompleter
    {
        /**
         * @param sender
         *            who is completing. Bukkit hands this to every tab completer; it is
         *            carried through here so a completion can depend on who is asking --
         *            a player's own beam places being the case that needed it.
         * @param args
         *            the full argument array, including the subcommand at index 0
         * @return candidate completions for the argument being typed
         */
        List<String> complete(CommandSender sender, String[] args);
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
        private boolean checksOwnPermissions;

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

        /**
         * Whether this subcommand checks its own permissions, so {@code /wormhole} must not
         * apply the blanket {@code wormhole.config} gate before dispatching to it.
         *
         * <p>These are the player-facing subcommands: each has its own node, and each
         * verifies it -- per verb, and per gate or ring where ownership matters -- which the
         * one gate up front could only ever be coarser than.
         *
         * @return true if the handler does its own checking
         */
        public boolean checksOwnPermissions() { return checksOwnPermissions; }

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
        public List<String> completeArgs(final CommandSender sender, final String[] args)
        {
            return completer == null ? Collections.<String>emptyList() : completer.complete(sender, args);
        }
    }

    private static final Map<String, Entry> BY_NAME = new LinkedHashMap<>();
    private static final List<Entry> ORDERED = new ArrayList<>();

    private SubCommands() {}

    // -----------------------------------------------------------------------
    // Shared argument completers
    // -----------------------------------------------------------------------

    /** Completes the name of an existing gate. */
    private static final ArgCompleter GATE_NAMES = (sender, args) -> args.length == 2 ? gateNames(args[1]) : none();

    /** Completes a gate name, then true/false. */
    private static final ArgCompleter GATE_THEN_BOOLEAN = (sender, args) ->
    {
        if (args.length == 2) return gateNames(args[1]);
        if (args.length == 3) return prefixed(args[2], TRUE, FALSE);
        return none();
    };

    /** Completes a gate name, then a free value the plugin cannot guess. */
    private static final ArgCompleter GATE_THEN_VALUE = (sender, args) -> args.length == 2 ? gateNames(args[1]) : none();

    static
    {
        // --- Gate lifecycle -------------------------------------------------
        register("list", aliases(), "/wormhole list [network]", new WXList(), true, (sender, args) ->
            args.length == 2 ? networkNames(args[1]) : none());
        register(BUILD, aliases(), "/wormhole build <shape>", new Build(), true, null);
        register("complete", aliases(), "/wormhole complete <name> [idc=IDC] [net=NET]", new Complete(), true, (sender, args) ->
            // The name is new, so suggesting existing gate names would be actively wrong.
            args.length >= 3 ? prefixed(args[args.length - 1], "idc=", "net=") : none());
        register(REMOVE, aliases("delete"), "/wormhole remove <gate>", new WXRemove(), true, GATE_NAMES);
        register(REGENERATE, aliases("regen"), "/wormhole regenerate <gate>",
            new com.wormhole_xtreme.wormhole.command.handlers.RegenerateCommand(), false, GATE_NAMES);
        register("refresh", aliases(), "/wormhole refresh", new Refresh(), true, null);

        // --- Travel ---------------------------------------------------------
        // Tries a gate first, then a beam destination or place -- see Go's own class comment.
        // Completion offers all three, which is what the command accepts: gate names and public
        // destinations are nobody's secret, and a player's own places are their own to see.
        register("go", aliases(), "/wormhole go <gate|destination>", new Go(), true, (sender, args) ->
            args.length == 2
                ? combine(gateNames(args[1]), travelBeamNames(sender, args[1])) : none());
        register("compass", aliases(), "/wormhole compass [reset]", new Compass(), true,
            (sender, args) -> args.length == 2 ? prefixed(args[1], "reset") : none());
        register("force", aliases(), "/wormhole force <gate>", new Force(), true, GATE_NAMES);

        // --- Per-gate settings ----------------------------------------------
        register(OWNER, aliases(), "/wormhole owner <gate> [player]",
            new com.wormhole_xtreme.wormhole.command.handlers.OwnerCommand(), false, GATE_THEN_VALUE);
        register("idc", aliases(), "/wormhole idc <gate> [code]", new WXIDC(), true, GATE_THEN_VALUE);
        register(REDSTONE, aliases(), "/wormhole redstone <gate> [true|false]",
            new com.wormhole_xtreme.wormhole.command.handlers.RedstoneCommand(), false, GATE_THEN_BOOLEAN);
        register("custom", aliases(), "/wormhole custom <gate|-all|-clean> [true|false|confirm]",
            new com.wormhole_xtreme.wormhole.command.handlers.CustomCommand(), false, (sender, args) ->
            {
                if (args.length == 2)
                {
                    final List<String> out = new ArrayList<>(gateNames(args[1]));
                    out.addAll(prefixed(args[1], "-all", "-clean"));
                    return out;
                }
                if (args.length == 3)
                {
                    return "-clean".equalsIgnoreCase(args[1])
                        ? prefixed(args[2], "confirm")
                        : prefixed(args[2], TRUE, FALSE);
                }
                return none();
            });
        // The three material overrides differ only in which set of materials they accept, so
        // they share one handler and the completer offers that set at the value position.
        for (final com.wormhole_xtreme.wormhole.command.handlers.MaterialCommand.Kind kind
            : com.wormhole_xtreme.wormhole.command.handlers.MaterialCommand.Kind.values())
        {
            final String name = kind.command();
            register(name, aliases(), "/wormhole " + name + " <gate> <material>",
                new com.wormhole_xtreme.wormhole.command.handlers.MaterialCommand(kind), false, (sender, args) ->
                {
                    if (args.length == 2)
                    {
                        return gateNames(args[1]);
                    }
                    if (args.length == 3)
                    {
                        return prefixed(args[2], kind.allowedNames().toArray(new String[0]));
                    }
                    return none();
                });
        }
        register("wooshdepth", aliases(), "/wormhole wooshdepth <gate> <depth>",
            new com.wormhole_xtreme.wormhole.command.handlers.WooshDepthCommand(), false, GATE_THEN_VALUE);

        // --- Transport rings --------------------------------------------------
        register("ring", aliases("rings"), "/wormhole ring <create|cancel|list|remove|edit|allow|deny|owner>",
            new com.wormhole_xtreme.wormhole.command.handlers.RingCommand(), false,
            SubCommands::completeRing);

        // --- Beaming ------------------------------------------------------------
        register("beam", aliases(),
            "/wormhole beam <to <name>|list|admin <set|remove|cost|goto|send>|place <list|set|remove>>",
            new com.wormhole_xtreme.wormhole.command.handlers.BeamCommand(), false,
            SubCommands::completeBeam);

        // --- Server settings -------------------------------------------------
        register("shutdown_timeout", aliases("timeout"), "/wormhole shutdown_timeout <seconds>",
            new com.wormhole_xtreme.wormhole.command.handlers.TimeoutsCommand(), false, null);
        register("activate_timeout", aliases(), "/wormhole activate_timeout <seconds>",
            new com.wormhole_xtreme.wormhole.command.handlers.TimeoutsCommand(), false, null);
        register("cooldown", aliases(), "/wormhole cooldown <seconds> or <true|false>",
            new com.wormhole_xtreme.wormhole.command.handlers.CooldownCommand(), false, (sender, args) ->
                args.length == 2 ? prefixed(args[1], TRUE, FALSE) : none());
        // Kept dispatchable, but it reports that build restriction is gone rather than
        // pretending to set it. See RestrictCommand.
        register("restrict", aliases(), "/wormhole restrict (removed)",
            new com.wormhole_xtreme.wormhole.command.handlers.RestrictCommand(), false, null);

        // --- The shape people actually type --------------------------------
        // Everything above stays registered and keeps working; it is just no longer what is
        // advertised. Four names now cover it: two nouns that behave alike, the settings, and
        // the one thing that is neither.
        register("gate", aliases("gates"),
            "/wormhole gate <" + String.join("|",
                com.wormhole_xtreme.wormhole.command.handlers.GateCommand.verbs()) + ">",
            new com.wormhole_xtreme.wormhole.command.handlers.GateCommand(), false,
            SubCommands::completeGate);
        register("mirror", aliases("mirrors"),
            "/wormhole mirror <" + String.join("|",
                com.wormhole_xtreme.wormhole.command.handlers.MirrorCommand.verbs()) + ">",
            new com.wormhole_xtreme.wormhole.command.handlers.MirrorCommand(), false,
            SubCommands::completeMirror);
        register("config", aliases("set"), "/wormhole config <setting> [value]",
            new com.wormhole_xtreme.wormhole.command.handlers.ConfigCommand(), false, (sender, args) ->
            {
                if (args.length != 2)
                {
                    return none();
                }
                // Completing what is in config.yml: gate-sound- has to reach
                // GATE_SOUND_KAWOOSH, or the file's own spelling completes to nothing.
                final String typed = (args[1] == null) ? null : args[1].replace('-', '_');
                return prefixed(typed,
                    com.wormhole_xtreme.wormhole.config.ConfigManager.settingNames()
                        .toArray(new String[0]));
            });

        hide("list", BUILD, "complete", REMOVE, REGENERATE, "refresh", "go", "force",
            OWNER, "idc", REDSTONE, "custom", "portalmaterial", "irismaterial",
            "lightmaterial", "wooshdepth", "shutdown_timeout", "activate_timeout",
            "cooldown", "restrict");

        selfPermissioned("beam", "ring", "go", "list", "compass");
    }

    /**
     * Completes the arguments of {@code /wormhole gate}.
     *
     * @param args
     *            the full argument array
     * @return the candidates for the argument being typed
     */
    private static List<String> completeGate(final CommandSender sender, final String[] args)
    {
        if (args.length == 2)
        {
            return prefixed(args[1],
                com.wormhole_xtreme.wormhole.command.handlers.GateCommand.verbs()
                    .toArray(new String[0]));
        }
        final String verb = args[1].toLowerCase(Locale.ROOT);
        if ("edit".equals(verb))
        {
            return completeGateEdit(args);
        }
        if (BUILD.equals(verb))
        {
            return args.length == 3 ? shapeNames(args[2]) : none();
        }
        if ("shapes".equals(verb))
        {
            return completeGateShapes(args);
        }
        if (REGENERATE.equals(verb) || "regen".equals(verb) || "validate".equals(verb))
        {
            // Same shape as regenerate: a specific gate, or -all to sweep every one of them.
            return completeGateRegenerate(args);
        }
        // Every other verb takes a gate name first, and nothing after it worth guessing at.
        return args.length == 3 ? gateNames(args[2]) : none();
    }

    /**
     * Completions for {@code /wormhole gate edit <gate> <field> [value]}.
     *
     * @param args
     *            the full argument array
     * @return the candidates
     */
    private static List<String> completeGateEdit(final String[] args)
    {
        if (args.length == 3)
        {
            return gateNames(args[2]);
        }
        if (args.length == 4)
        {
            return prefixed(args[3],
                com.wormhole_xtreme.wormhole.command.handlers.GateEditCommand.fieldNames()
                    .toArray(new String[0]));
        }
        if (args.length == 5)
        {
            return completeGateEditValue(args[3].toLowerCase(Locale.ROOT), args[4]);
        }
        return none();
    }

    /**
     * The value slot of {@code gate edit}, whose candidates depend on the field named.
     *
     * <p>Offering a block material where a true/false belongs, or the other way round, is
     * offering the player a mistake the command is about to refuse. Fields with nothing
     * worth guessing at -- a gate's new name, say -- offer nothing.
     *
     * @param field
     *            the field being edited, lower-cased
     * @param typed
     *            what has been typed in the value slot
     * @return the candidates
     */
    private static List<String> completeGateEditValue(final String field, final String typed)
    {
        if ("group".equals(field))
        {
            return prefixed(typed,
                com.wormhole_xtreme.wormhole.command.handlers.GateEditCommand.groupNames()
                    .toArray(new String[0]));
        }
        if (REDSTONE.equals(field))
        {
            return prefixed(typed, TRUE, FALSE);
        }
        if ("portal".equals(field) || "iris".equals(field) || LIGHT.equals(field))
        {
            return materialNames(typed, false);
        }
        return none();
    }

    /**
     * Completions for {@code /wormhole mirror <verb> [name] [name]}.
     *
     * <p>{@code set} is not completed from existing mirrors: naming a new one is the common
     * case, and offering the existing names there would invite rebinding one by accident.
     * Every other verb names a mirror that already exists, {@code link} names two, and
     * {@code stamp} takes a mirror and then a preset.
     *
     * @param args
     *            the full argument array
     * @return the candidates
     */
    private static List<String> completeMirror(final CommandSender sender, final String[] args)
    {
        if (args.length == 2)
        {
            return prefixed(args[1],
                com.wormhole_xtreme.wormhole.command.handlers.MirrorCommand.verbs());
        }
        final String verb = (args.length > 1) ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        // Named rather than excluded. Falling through for anything that is not set or list
        // meant a verb nobody has -- a typo, most likely -- still offered the mirror names,
        // which reads as though the typo were a real command.
        final boolean takesOneName = "target".equals(verb) || REMOVE.equals(verb)
            || "display".equals(verb) || "mode".equals(verb);
        final boolean takesTwoNames = "link".equals(verb);
        final boolean stamp = "stamp".equals(verb);
        if ((args.length == 3) && (takesOneName || takesTwoNames || stamp))
        {
            return prefixed(args[2], mirrorNames());
        }
        if ((args.length == 4) && takesTwoNames)
        {
            return prefixed(args[3], mirrorNames());
        }
        // Presets, not mirrors, and the empty offer is the point: leaving it blank is what
        // makes stamp read the far side rather than apply a look somebody picked.
        if ((args.length == 4) && stamp)
        {
            return prefixed(args[3],
                com.wormhole_xtreme.wormhole.model.mirror.MirrorPresetRegistry.names());
        }
        if (args.length == 4)
        {
            return prefixed(args[3], settingsFor(verb));
        }
        return none();
    }

    /**
     * What the fourth word can be, for the two verbs that take a setting.
     *
     * @param verb
     *            the mirror verb typed
     * @return the values it accepts, or nothing
     */
    private static String[] settingsFor(final String verb)
    {
        if ("display".equals(verb))
        {
            return new String[] { "always", "proximity" };
        }
        if ("mode".equals(verb))
        {
            return new String[] { "static", "dynamic" };
        }
        return new String[0];
    }

    /** @return every registered mirror's name */
    private static String[] mirrorNames()
    {
        return com.wormhole_xtreme.wormhole.model.mirror.MirrorManager.all().stream()
            .map(com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror::name)
            .toArray(String[]::new);
    }

    /**
     * Completions for {@code /wormhole gate shapes <reload|validate> [shape]}.
     *
     * @param args
     *            the full argument array
     * @return the candidates
     */
    private static List<String> completeGateShapes(final String[] args)
    {
        if (args.length == 3)
        {
            return prefixed(args[2], "reload", "validate");
        }
        // Completes from names already loaded -- a brand new file not loaded yet has to be
        // typed out in full, the same limit gate build's own completion already has.
        return args.length == 4 ? shapeNames(args[3]) : none();
    }

    /**
     * Completions for {@code /wormhole gate regenerate <gate|-all>} and
     * {@code /wormhole gate validate <gate|-all>} -- both take exactly one gate name, or
     * {@code -all} to sweep every gate on the server in one pass.
     *
     * @param args
     *            the full argument array
     * @return the candidates
     */
    private static List<String> completeGateRegenerate(final String[] args)
    {
        if (args.length != 3)
        {
            return none();
        }
        final List<String> out = new ArrayList<>(gateNames(args[2]));
        out.addAll(prefixed(args[2], "-all"));
        return out;
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

    /**
     * Marks the named subcommands as doing their own permission checking.
     *
     * <p>Written as an explicit opt-in list rather than inferred: forgetting to mark a new
     * subcommand leaves it behind {@code wormhole.config}, which is the safe way to be wrong.
     *
     * @param names
     *            the self-permissioned names
     */
    private static void selfPermissioned(final String... names)
    {
        for (final String name : names)
        {
            final Entry e = BY_NAME.get(name);
            if (e != null)
            {
                e.checksOwnPermissions = true;
            }
        }
    }

    private static void register(final String name, final List<String> aliases, final String usage,
        final Object handler, final boolean dropSubcommandArg, final ArgCompleter completer)
    {
        final SubCommand adapted = handler instanceof SubCommand sub
            ? sub
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
        return name == null ? null : BY_NAME.get(name.toLowerCase(Locale.ROOT));
    }

    /** @return every subcommand, in the order declared above */
    public static Collection<Entry> all()
    {
        return Collections.unmodifiableList(ORDERED);
    }

    /** @return canonical subcommand names matching the given prefix, for tab completion */
    public static List<String> namesMatching(final String prefix)
    {
        final String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<>();
        for (final Entry e : ORDERED)
        {
            if (!e.isHidden() && e.getName().startsWith(p))
            {
                out.add(e.getName());
            }
        }
        return out;
    }

    /**
     * The help message's command list, optionally narrowed to the subcommands that carry
     * their own nodes -- everything a player without {@code wormhole.config} is not refused
     * by the dispatcher for. It is a filter on the registry, not on the sender: whether they
     * hold {@code wormhole.beam.use} is the beam handler's question, asked when they run it.
     * Listing the admin subcommands to someone the dispatcher will refuse outright is noise,
     * and tells them the server's layout besides.
     *
     * @param selfPermissionedOnly
     *            true to list only the subcommands that check their own permissions
     * @return a comma-separated list of subcommand names
     */
    public static String nameList(final boolean selfPermissionedOnly)
    {
        final StringBuilder sb = new StringBuilder();
        for (final Entry e : ORDERED)
        {
            if (e.isHidden() || (selfPermissionedOnly && !e.checksOwnPermissions()))
            {
                continue;
            }
            if (!sb.isEmpty())
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

    private static List<String> combine(final List<String> first, final List<String> second)
    {
        final List<String> out = new ArrayList<>(first);
        out.addAll(second);
        return out;
    }

    private static List<String> prefixed(final String typed, final String... candidates)
    {
        final String p = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<>();
        for (final String c : candidates)
        {
            if (c.toLowerCase(Locale.ROOT).startsWith(p))
            {
                out.add(c);
            }
        }
        return out;
    }

    /** The fields {@code /wormhole ring edit} understands. */
    private static final String[] RING_FIELDS = { "ring", LIGHT, "flash", "built", "name", "access", "style", "reset" };

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
    private static List<String> completeRing(final CommandSender sender, final String[] args)
    {
        if (args.length == 2)
        {
            return prefixed(args[1], "create", "cancel", "list", REMOVE, "edit",
                "allow", "deny", OWNER);
        }
        if (!"edit".equalsIgnoreCase(args[1]))
        {
            return none();
        }
        // Typing the word straight after "edit": either a field, or an id with the field
        // still to come. Ids are still not offered: the completer is told who is asking now,
        // but a pair is not addressed by its owner -- an id names a pair anywhere on the
        // server, so completing them would list pairs that are none of the asker's business.
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
    private static List<String> completeBeam(final CommandSender sender, final String[] args)
    {
        if (args.length == 2)
        {
            return prefixed(args[1], "to", "list", "admin", "place");
        }
        final String noun = args[1].toLowerCase(Locale.ROOT);
        if ("to".equals(noun))
        {
            // Public destinations and the asking player's own places, which is exactly what
            // "beam to" resolves -- places first, then public. Offering only the public half
            // meant a player could travel to a place they could not tab-complete.
            return args.length == 3 ? travelBeamNames(sender, args[2]) : none();
        }
        if ("admin".equals(noun))
        {
            return completeBeamAdmin(args);
        }
        if ("place".equals(noun))
        {
            return args.length == 3 ? prefixed(args[2], "list", "set", REMOVE) : none();
        }
        return none();
    }

    /**
     * Completions for {@code /wormhole beam admin <action> ...}.
     *
     * @param args
     *            the full argument array, {@code beam} at index 0
     * @return the candidates
     */
    private static List<String> completeBeamAdmin(final String[] args)
    {
        if (args.length == 3)
        {
            return prefixed(args[2], "set", REMOVE, "cost", "goto", "send");
        }
        final String action = args[2].toLowerCase(Locale.ROOT);
        if (args.length == 4)
        {
            return beamAdminFirstArgument(action, args[3]);
        }
        if (args.length == 5)
        {
            if ("cost".equals(action))
            {
                return prefixed(args[4], "default");
            }
            // send's destination, one token in -- same shape as goto's first argument.
            if ("send".equals(action))
            {
                return playerOrDestinationNames(args[4]);
            }
        }
        return beamAdminWorldSlot(action, args);
    }

    /**
     * The one word directly after an admin action.
     *
     * @param action
     *            the admin action, lower-cased
     * @param typed
     *            what has been typed in that slot
     * @return the candidates
     */
    private static List<String> beamAdminFirstArgument(final String action, final String typed)
    {
        if (REMOVE.equals(action) || "cost".equals(action))
        {
            return publicBeamNames(typed);
        }
        // goto's only argument is a destination: a player, a public destination name, or the
        // first of three coordinates -- coordinates would not match a name prefix anyway, so
        // offering names here does no harm on the numeric path.
        if ("goto".equals(action))
        {
            return playerOrDestinationNames(typed);
        }
        // send's first argument is different in kind: the player being *moved*, who has to be
        // an actual online player. A destination name would be meaningless in this slot, so
        // only players are offered.
        if ("send".equals(action))
        {
            return playerNames(typed);
        }
        return none();
    }

    /**
     * The trailing {@code [world]} slot after a full set of raw coordinates.
     *
     * <p>goto's sits one token earlier than send's, since send has an extra token -- the
     * player being moved -- ahead of its own destination. Offered on position alone rather
     * than only once the earlier tokens are confirmed numeric: the same lightweight approach
     * completion already takes everywhere else here, not a full parse of what was typed.
     *
     * @param action
     *            the admin action, lower-cased
     * @param args
     *            the full argument array
     * @return the world names, or nothing if this is not that slot
     */
    private static List<String> beamAdminWorldSlot(final String action, final String[] args)
    {
        if ("goto".equals(action) && (args.length == 7))
        {
            return worldNames(args[6]);
        }
        if ("send".equals(action) && (args.length == 8))
        {
            return worldNames(args[7]);
        }
        return none();
    }

    /**
     * Online player names matching what has been typed, for {@code beam admin goto}/{@code
     * send}. A player's own places are still not offered anywhere, same limitation as {@code
     * to} above, but a target player's name is nobody's secret in the same way.
     *
     * @param typed what has been typed so far
     * @return the matching online player names
     */
    private static List<String> playerNames(final String typed)
    {
        final String p = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<>();
        for (final org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers())
        {
            final String name = player.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(p))
            {
                out.add(name);
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /**
     * Online player names and public destination names together, for the one argument slot
     * that genuinely accepts either: {@code goto}'s destination, and {@code send}'s.
     *
     * <p>Deliberately not the slot naming the player {@code send} is moving -- that one has
     * to be a real online player, and offering destination names there would suggest a
     * command that does not exist.
     *
     * <p>A public destination sharing a name with an online player is offered once, not
     * twice: the resolver checks players first, so the completion would be describing two
     * different outcomes with one identical string. Sorted together so the list does not
     * betray which kind a given name is -- by the time it matters, the resolver has already
     * decided.
     *
     * @param typed what has been typed so far
     * @return the matching names, players and public destinations combined
     */
    private static List<String> playerOrDestinationNames(final String typed)
    {
        final List<String> out = playerNames(typed);
        for (final String name : publicBeamNames(typed))
        {
            boolean already = false;
            for (final String existing : out)
            {
                // Case-insensitively: the destination list is keyed lowercase, so "Spawn" and
                // "spawn" are one destination, and a player differing only in case from one
                // would still resolve to the player either way.
                if (existing.equalsIgnoreCase(name))
                {
                    already = true;
                    break;
                }
            }
            if (!already)
            {
                out.add(name);
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /**
     * Loaded world names matching what has been typed, for the trailing {@code [world]} slot
     * on {@code beam admin goto}/{@code send}'s raw-coordinate form.
     *
     * @param typed what has been typed so far
     * @return the matching loaded world names
     */
    private static List<String> worldNames(final String typed)
    {
        final String p = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<>();
        for (final org.bukkit.World world : org.bukkit.Bukkit.getWorlds())
        {
            final String name = world.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(p))
            {
                out.add(name);
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /**
     * Public destinations plus, when a player is asking, their own places.
     *
     * <p>What {@code BeamTravel.travelTo} accepts: it looks a name up in the asking player's
     * places first and falls back to the public list, so completion that offered only the
     * public half was hiding half of what the command would have taken. A place shadows a
     * public destination of the same name there, so a shared name is offered once.
     *
     * @param sender
     *            who is completing; a console has no places
     * @param typed
     *            what has been typed in that slot
     * @return the matching names
     */
    private static List<String> travelBeamNames(final CommandSender sender, final String typed)
    {
        final List<String> out = publicBeamNames(typed);
        if (!(sender instanceof org.bukkit.entity.Player player))
        {
            return out;
        }
        final String p = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        for (final com.wormhole_xtreme.wormhole.model.beam.BeamDestination place
            : com.wormhole_xtreme.wormhole.model.beam.BeamManager.getPlaces(player.getUniqueId()))
        {
            final String name = place.name();
            if (!name.toLowerCase(Locale.ROOT).startsWith(p))
            {
                continue;
            }
            // Replaced rather than skipped when the names match case-insensitively: travelTo
            // resolves this name to the place, so the place's own spelling is what belongs
            // under the cursor.
            int shadowed = -1;
            for (int i = 0; i < out.size(); i++)
            {
                if (out.get(i).equalsIgnoreCase(name))
                {
                    shadowed = i;
                    break;
                }
            }
            if (shadowed < 0)
            {
                out.add(name);
            }
            else
            {
                out.set(shadowed, name);
            }
        }
        return out;
    }

    private static List<String> publicBeamNames(final String typed)
    {
        final String p = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<>();
        for (final com.wormhole_xtreme.wormhole.model.beam.BeamDestination destination
            : com.wormhole_xtreme.wormhole.model.beam.BeamManager.getAllPublicDestinations())
        {
            if (destination.name().toLowerCase(Locale.ROOT).startsWith(p))
            {
                out.add(destination.name());
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
        if (LIGHT.equalsIgnoreCase(field) || "flash".equalsIgnoreCase(field))
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
        final String p = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<>();
        for (final org.bukkit.Material material
            : com.wormhole_xtreme.wormhole.model.ring.Ring.glowingMaterials())
        {
            final String name = material.name().toLowerCase(Locale.ROOT);
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
    private static List<String> materialNames(final String typed, final boolean slabsOnly)
    {
        final String p = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<>();
        for (final org.bukkit.Material material : org.bukkit.Material.values())
        {
            final String name = material.name().toLowerCase(Locale.ROOT);
            if (worthOffering(material, slabsOnly) && name.startsWith(p))
            {
                out.add(name);
            }
        }
        Collections.sort(out);
        return out;
    }

    /**
     * Whether a material is worth putting in front of somebody typing.
     *
     * <p>Legacy materials are duplicates of real ones under old names, and offering them
     * would double the list with things nobody should be typing.
     *
     * @param material
     *            the material to consider
     * @param slabsOnly
     *            true when only what can make a ring should be offered
     * @return true if it should appear in the completions
     */
    private static boolean worthOffering(final org.bukkit.Material material, final boolean slabsOnly)
    {
        // isLegacy() is deprecated because the materials it identifies are, which is exactly
        // what makes it the thing to ask: there is no undeprecated way to spot one, and a
        // completion list offering LEGACY_ variants would be offering names nothing accepts.
        if (material.isLegacy()
            || !com.wormhole_xtreme.wormhole.utils.MaterialUtils.isBlockOrUnknown(material))
        {
            return false;
        }
        return !slabsOnly || com.wormhole_xtreme.wormhole.model.ring.Ring.isUsableAsRing(material);
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
        final String p = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<>();
        for (final String name
            : com.wormhole_xtreme.wormhole.model.StargateShapeRegistry.getStargateShapes().keySet())
        {
            if (name.toLowerCase(Locale.ROOT).startsWith(p))
            {
                out.add(name);
            }
        }
        Collections.sort(out);
        return out;
    }

    private static List<String> gateNames(final String typed)
    {
        final String p = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<>();
        for (final Stargate g : StargateManager.getAllGatesUnsorted())
        {
            final String name = g.getGateName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(p))
            {
                out.add(name);
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static List<String> networkNames(final String typed)
    {
        final String p = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        final java.util.LinkedHashSet<String> nets = new java.util.LinkedHashSet<String>();
        nets.add("Public");
        for (final Stargate g : StargateManager.getAllGatesUnsorted())
        {
            if (g.getGateNetwork() != null)
            {
                nets.add(g.getGateNetwork().getNetworkName());
            }
        }
        final List<String> out = new ArrayList<>();
        for (final String n : nets)
        {
            if (n.toLowerCase(Locale.ROOT).startsWith(p))
            {
                out.add(n);
            }
        }
        return out;
    }
}
