package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.Arrays;
import java.util.List;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigReload;
import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * Reads and writes any setting, by name.
 *
 * <p>Four commands used to set one value each -- {@code shutdown_timeout},
 * {@code activate_timeout}, {@code cooldown} and {@code restrict} -- while the other forty
 * or so settings could only be changed by editing {@code config.yml} and restarting the
 * server. Every sound, every ring timing, every material default was on the wrong side of
 * that line.
 *
 * <p>This is the one command for all of them, and it takes effect immediately: settings are
 * read where they are used, and the few read once -- a sweep's period, an integration -- are
 * applied again by {@code ConfigManager.applySetting}.
 *
 * <p>{@code /wormhole config reload} takes a hand-edited or deployed config.yml the same way,
 * through {@link ConfigReload}, without a restart.
 */
public class ConfigCommand implements SubCommand
{
    /** How many settings to list before telling them to narrow it down. */
    private static final int TOO_MANY_TO_LIST = 30;

    /** The word that reloads config.yml rather than naming a setting. */
    public static final String RELOAD = "reload";

    // Bukkit reads the boolean as "handled"; every path here has handled it.
    @SuppressWarnings("java:S3516")
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return true;
        }

        // args: config [name] [value...]
        if (args.length < 2)
        {
            listMatching(sender, "");
            return true;
        }
        final String name = args[1];
        if ((args.length == 2) && RELOAD.equalsIgnoreCase(name))
        {
            reload(sender);
            return true;
        }
        if (args.length == 2)
        {
            final String described = ConfigManager.describeSetting(name);
            if (described == null)
            {
                // Not a setting, so treat what they typed as a search. Somebody hunting for
                // the ring cooldown is better served by the three settings with RING in the
                // name than by being told RING does not exist.
                listMatching(sender, name);
                return true;
            }
            // The name and value on one line, what it is for on the next.
            for (final String line : described.split(System.lineSeparator()))
            {
                sender.sendMessage(ChatText.info(line.strip()));
            }
            return true;
        }

        final String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        final String refused = ConfigManager.settingRefusal(name, value);
        if (refused != null)
        {
            sender.sendMessage(ChatText.error(refused));
            return true;
        }
        final String result = ConfigManager.applySetting(name, value);
        if (result == null)
        {
            sender.sendMessage(ChatText.error("No setting called " + ChatText.command(name) + "."));
            listMatching(sender, name);
            return true;
        }
        sender.sendMessage(ChatText.info(result));
        return true;
    }

    /**
     * Reloads config.yml and says what changed and what was refused.
     *
     * @param sender
     *            who asked
     */
    private static void reload(final CommandSender sender)
    {
        final ConfigReload.Report report = ConfigReload.reload();
        if (report.unreadable() != null)
        {
            sender.sendMessage(ChatText.error(report.unreadable() + "; no setting changed."));
            return;
        }
        for (final ConfigReload.Change change : report.changes())
        {
            sender.sendMessage(ChatText.info(ChatText.command(change.key().name()) + ": "
                + ChatText.value(String.valueOf(change.from())) + " -> " + ChatText.value(String.valueOf(change.to()))
                + ((change.unapplied() == null) ? "."
                    : ", but it could not be applied now: " + change.unapplied() + ". It applies at the next restart.")));
        }
        for (final ConfigReload.Rejection rejection : report.rejections())
        {
            sender.sendMessage(ChatText.error(rejection.refusal() + " Kept "
                + ChatText.value(String.valueOf(rejection.kept())) + "."));
        }
        if (report.changes().isEmpty() && report.rejections().isEmpty())
        {
            sender.sendMessage(ChatText.info("Reloaded config.yml: no setting changed."));
        }
    }

    /**
     * Lists the settings whose names contain what was typed.
     *
     * @param sender
     *            who asked
     * @param needle
     *            the text to look for, empty for everything
     */
    private static void listMatching(final CommandSender sender, final String needle)
    {
        final List<String> names = ConfigManager.settingNamesMatching(needle);
        if (names.isEmpty())
        {
            sender.sendMessage(ChatText.error("No setting matches " + ChatText.command(needle) + "."));
            return;
        }
        final StringBuilder found = new StringBuilder();
        for (int i = 0; (i < names.size()) && (i < TOO_MANY_TO_LIST); i++)
        {
            found.append(found.isEmpty() ? "" : ", ").append(ChatText.command(names.get(i)));
        }
        sender.sendMessage(ChatText.info(found.toString()));
        if (names.size() > TOO_MANY_TO_LIST)
        {
            sender.sendMessage(ChatText.info("...and " + ChatText.value(String.valueOf(names.size() - TOO_MANY_TO_LIST))
                + " more. Type part of a name to narrow it down."));
        }
        sender.sendMessage(ChatText.info(ChatText.usageBody("/wormhole config <name>") + " shows one, "
            + ChatText.usageBody("/wormhole config <name> <value>") + " changes it."));
    }
}
