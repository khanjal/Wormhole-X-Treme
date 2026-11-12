package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.Map;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.LegacyDatabaseExporter;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * {@code /wormhole export sqlite}: writes the gates out as a database the older plugins read (#719).
 *
 * <p>The way back out, beside {@code gate import} coming in. It writes a new folder under
 * {@code exports/} and touches nothing else, and says what the format could not carry rather than
 * leaving it out silently.
 */
public class ExportCommand implements SubCommand
{
    /** Who may export; op by default. The console always may. */
    public static final String PERMISSION = "wormhole.export";

    private static final String SQLITE = "sqlite";

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (!sender.hasPermission(PERMISSION))
        {
            sender.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return true;
        }
        if (args.length != 2)
        {
            return false;
        }
        if (!SQLITE.equalsIgnoreCase(args[1]))
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "export.unknown-format",
                ChatText.value(args[1]), ChatText.command("/wormhole export " + SQLITE))));
            return true;
        }
        final LegacyDatabaseExporter.Result result = LegacyDatabaseExporter.export();
        if (result.getProblem() != null)
        {
            sender.sendMessage(ChatText.error(problem(sender, result)));
            return true;
        }
        report(sender, result);
        return true;
    }

    private static void report(final CommandSender sender, final LegacyDatabaseExporter.Result result)
    {
        final String count = ChatText.value(String.valueOf(result.getExported()));
        final String where = ChatText.value(result.getFile().getAbsolutePath());
        final String done;
        if (result.getExported() == 1)
        {
            done = Messages.get(sender, "export.done.one", count, where);
        }
        else
        {
            done = Messages.get(sender, "export.done.other", count, where);
        }
        sender.sendMessage(ChatText.info(done));

        final String skippedWord = ChatText.bad(Messages.get(sender, "export.skipped-word"));
        for (final String skipped : result.getSkipped())
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "export.skipped", skippedWord, skipped)));
        }
        if (!result.getDropped().isEmpty())
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "export.dropped.header")));
            for (final Map.Entry<LegacyDatabaseExporter.Dropped, Integer> kind : result.getDropped().entrySet())
            {
                final String label = label(sender, kind.getKey());
                sender.sendMessage(ChatText.info(Messages.get(sender, "export.dropped.line", label,
                    ChatText.value(String.valueOf(kind.getValue())))));
            }
        }
        sender.sendMessage(ChatText.info(Messages.get(sender, "export.not-carried")));
        sender.sendMessage(ChatText.info(Messages.get(sender, "export.ordinals")));
    }

    /** Each key is written out whole, as below. */
    private static String problem(final CommandSender sender, final LegacyDatabaseExporter.Result result)
    {
        final Object[] args = result.getProblemArgs();
        return switch (result.getProblem())
        {
            case NO_DRIVER -> Messages.get(sender, "export.no-driver");
            case NO_FOLDER -> Messages.get(sender, "export.no-folder", args);
            case WRITE_FAILED -> Messages.get(sender, "export.write-failed", args);
        };
    }

    /** Each key is written out whole, so a missing one is found by searching for it. */
    private static String label(final CommandSender sender, final LegacyDatabaseExporter.Dropped kind)
    {
        return switch (kind)
        {
            case USE_COST -> Messages.get(sender, "export.dropped.kind.use-cost");
            case DIAL_SPIN -> Messages.get(sender, "export.dropped.kind.dial-spin");
            case IRIS_ANIMATION -> Messages.get(sender, "export.dropped.kind.iris-animation");
            case VIEW -> Messages.get(sender, "export.dropped.kind.view");
            case MATERIAL_GROUP -> Messages.get(sender, "export.dropped.kind.material-group");
            case FRAME_MATERIAL -> Messages.get(sender, "export.dropped.kind.frame-material");
            case DIAL_SIGN_TARGET -> Messages.get(sender, "export.dropped.kind.dial-sign-target");
            case OWNER_UUID -> Messages.get(sender, "export.dropped.kind.owner-uuid");
        };
    }
}
