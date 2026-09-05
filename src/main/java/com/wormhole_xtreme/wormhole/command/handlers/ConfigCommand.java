package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.Arrays;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

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
 * read where they are used rather than cached at startup, so there is nothing to reload.
 */
// Command handlers return boolean because SubCommand/CommandExecutor say so; "always true" means handled.
@SuppressWarnings("java:S3516")
public class ConfigCommand implements SubCommand
{
    /** How many settings to list before telling them to narrow it down. */
    private static final int TOO_MANY_TO_LIST = 30;

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if ((sender instanceof Player)
            && !WXPermissions.checkWXPermissions((Player) sender, PermissionType.CONFIG))
        {
            sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }

        // args: config [name] [value...]
        if (args.length < 2)
        {
            return listMatching(sender, "");
        }
        final String name = args[1];
        if (args.length == 2)
        {
            final String described = ConfigManager.describeSetting(name);
            if (described == null)
            {
                // Not a setting, so treat what they typed as a search. Somebody hunting for
                // the ring cooldown is better served by the three settings with RING in the
                // name than by being told RING does not exist.
                return listMatching(sender, name);
            }
            sender.sendMessage(described);
            return true;
        }

        final String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        final String result = ConfigManager.applySetting(name, value);
        if (result == null)
        {
            sender.sendMessage("No setting called " + name + ".");
            return listMatching(sender, name);
        }
        sender.sendMessage(result);
        return true;
    }

    /**
     * Lists the settings whose names contain what was typed.
     *
     * @param sender
     *            who asked
     * @param needle
     *            the text to look for, empty for everything
     * @return true, the command was handled
     */
    private static boolean listMatching(final CommandSender sender, final String needle)
    {
        final List<String> names = ConfigManager.settingNamesMatching(needle);
        if (names.isEmpty())
        {
            sender.sendMessage("No setting matches \"" + needle + "\".");
            return true;
        }
        final StringBuilder found = new StringBuilder();
        for (int i = 0; (i < names.size()) && (i < TOO_MANY_TO_LIST); i++)
        {
            found.append(found.length() == 0 ? "" : ", ").append(names.get(i));
        }
        sender.sendMessage(found.toString());
        if (names.size() > TOO_MANY_TO_LIST)
        {
            sender.sendMessage("...and " + (names.size() - TOO_MANY_TO_LIST)
                + " more. Type part of a name to narrow it down.");
        }
        sender.sendMessage("/wormhole config <name> shows one, "
            + "/wormhole config <name> <value> changes it.");
        return true;
    }
}
