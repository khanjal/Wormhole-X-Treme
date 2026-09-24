package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.Build;
import com.wormhole_xtreme.wormhole.command.Complete;
import com.wormhole_xtreme.wormhole.command.Force;
import com.wormhole_xtreme.wormhole.command.Go;
import com.wormhole_xtreme.wormhole.command.Refresh;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.command.WXList;
import com.wormhole_xtreme.wormhole.command.WXRemove;
import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;

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
    private static final String REGENERATE = "regenerate";
    /** What regenerate is offered as: short to type. The long name still works. */
    private static final String REGEN = "regen";
    private static final String VALIDATE = "validate";

    /** The verbs, in the order they are offered: building, using, looking after, then shapes and imports. */
    private static final List<String> VERBS = Arrays.asList(
        "build", "preview", "complete",
        "list", "go", "force",
        "edit", "remove", REGEN, VALIDATE,
        "shapes", "import");

    /**
     * The verbs, for tab completion and help.
     *
     * @return the verb names
     */
    public static List<String> verbs()
    {
        return new ArrayList<>(VERBS);
    }

    // Bukkit reads the boolean as "handled"; every path here has handled it.
    @SuppressWarnings("java:S3516")
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (args.length < 2)
        {
            sendVerbList(sender);
            return true;
        }
        final String verb = args[1].toLowerCase(Locale.ROOT);
        // A verb's handler answers false for a line it could not use; say that verb's usage here
        // rather than letting the dispatcher say gate's, or Bukkit plugin.yml's (#325).
        if (!dispatch(sender, args, verb) && USAGES.containsKey(verb))
        {
            sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + ChatText.usage(USAGES.get(verb)));
        }
        return true;
    }

    /** Each verb's usage, aliases included, as the guide's command table gives them. */
    private static final java.util.Map<String, String> USAGES = new java.util.HashMap<>();

    static
    {
        usage("/wormhole gate build <shape> [group]", "build");
        usage("/wormhole gate preview <action>", "preview");
        usage("/wormhole gate complete <name> [idc=IDC] [net=NET]", "complete", "create");
        usage("/wormhole gate list [network]", "list");
        usage("/wormhole gate go <gate>", "go");
        usage("/wormhole gate force <gate>", "force");
        usage("/wormhole gate edit <gate> <field> [value]", "edit");
        usage("/wormhole gate remove <gate> [-destroy]", "remove", "delete");
        usage("/wormhole gate regen <gate> [-shape <shape>] [-fill] [-water]", REGEN, REGENERATE);
        usage("/wormhole gate validate <gate|-all>", VALIDATE);
        usage("/wormhole gate shapes <reload|validate> [name]", "shapes");
    }

    private static void usage(final String line, final String... verbs)
    {
        for (final String verb : verbs)
        {
            USAGES.put(verb, line);
        }
    }

    /**
     * The usage line for a verb, for tests and help.
     *
     * @param verb
     *            the verb, lower case
     * @return its usage, or null for a verb with none
     */
    public static String usageOf(final String verb)
    {
        return USAGES.get(verb);
    }

    /** The verbs under the job they are for, as the guide groups them. */
    private static final String[][] JOBS = {
        { "Building", "build", "preview", "complete" },
        { "Using gates", "list", "go", "force" },
        { "Looking after gates", "edit", "remove", REGEN, VALIDATE },
        { "Shapes and imports", "shapes", "import" } };

    /** {@code /wormhole gate} alone: the verbs, grouped by job. */
    private static void sendVerbList(final CommandSender sender)
    {
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + ChatText.usage("/wormhole gate <verb> ..."));
        for (final String[] job : JOBS)
        {
            final List<String> verbs = new ArrayList<>();
            for (int i = 1; i < job.length; i++)
            {
                verbs.add(ChatText.command(job[i]));
            }
            sender.sendMessage("  " + ChatText.heading(job[0] + ":") + " " + String.join(", ", verbs));
        }
    }

    /** Runs one verb; false when its handler could not use the line. */
    private static boolean dispatch(final CommandSender sender, final String[] args, final String verb)
    {
        // What the verb's own handler expects. The older ones were written as standalone
        // commands and read their arguments from index zero; the newer ones take the whole
        // array with the subcommand still in front. Both shapes are fed what they expect
        // rather than being rewritten.
        final String[] rest = Arrays.copyOfRange(args, 2, args.length);

        if ("edit".equals(verb))
        {
            return new GateEditCommand().execute(sender, args);
        }
        if (CommandHandlerUtils.verbIs(verb, REGEN, REGENERATE))
        {
            final String[] forHandler = new String[rest.length + 1];
            forHandler[0] = REGENERATE;
            System.arraycopy(rest, 0, forHandler, 1, rest.length);
            return new RegenerateCommand().execute(sender, forHandler);
        }
        if ("import".equals(verb))
        {
            importLegacy(sender);
            return true;
        }
        if ("shapes".equals(verb))
        {
            return new GateShapesCommand().execute(sender, args);
        }
        if (VALIDATE.equals(verb))
        {
            final String[] forHandler = new String[rest.length + 1];
            forHandler[0] = VALIDATE;
            System.arraycopy(rest, 0, forHandler, 1, rest.length);
            return new ValidateCommand().execute(sender, forHandler);
        }
        if ("build".equals(verb))
        {
            return new Build().onCommand(sender, null, verb, rest);
        }
        if ("preview".equals(verb))
        {
            return Build.previewAction(sender, rest);
        }
        // create is what somebody tries first, because it is what the rest of the ecosystem
        // uses for "register the thing I just built" -- /mv create, /npc create. complete is
        // this plugin's own word from 2011 and stays the documented one, since it is the
        // second half of build-then-complete rather than a creation on its own.
        if (CommandHandlerUtils.verbIs(verb, "complete", "create"))
        {
            return new Complete().onCommand(sender, null, verb, rest);
        }
        if ("list".equals(verb))
        {
            return new WXList().onCommand(sender, null, verb, rest);
        }
        if (CommandHandlerUtils.verbIs(verb, "remove", "delete"))
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

    /**
     * Brings gates in from an older Wormhole X-Treme's database.
     *
     * <p>Reports what came across and what did not, per gate. A gate can be skipped for
     * reasons that are nobody's fault -- a world that is not loaded, a name already taken --
     * and saying which is more use than a count on its own.
     *
     * @param sender
     *            who asked
     */
    private static void importLegacy(final CommandSender sender)
    {
        // Written fresh this session and given the same gap the rest of gate management
        // had: no permission check at all. Fixed at the same time as the others, on the
        // same node.
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return;
        }
        final com.wormhole_xtreme.wormhole.model.LegacyDatabaseImporter.Result result =
            com.wormhole_xtreme.wormhole.model.LegacyDatabaseImporter.importGates();
        if (result.getProblem() != null)
        {
            sender.sendMessage(result.getProblem());
            return;
        }
        sender.sendMessage("Imported " + result.getImported() + " gate"
            + (result.getImported() == 1 ? "" : "s") + ". The old database is untouched.");
        if (result.getMovedExits() > 0)
        {
            sender.sendMessage(result.getMovedExits() + " of those had an arrival point old "
                + "enough to sit inside the portal, and were moved clear of it.");
        }
        for (final String skipped : result.getSkipped())
        {
            sender.sendMessage("  skipped " + skipped);
        }
    }
}
