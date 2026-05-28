package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.storage.StorageMigrator;

/**
 * Handler for '/wormhole storage migrate' command.
 */
public class StorageCommand implements SubCommand
{

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if ((args.length >= 2) && args[1].equalsIgnoreCase("migrate"))
        {
            if (args.length < 3)
            {
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Usage: /wormhole storage migrate <to> [force] OR /wormhole storage migrate <from> <to> [force]");
                return true;
            }

            if (args.length == 3)
            {
                final boolean force = false;
                StorageMigrator.migrateTo(args[2], force, sender);
                return true;
            }

            if (args.length == 4)
            {
                if ("force".equalsIgnoreCase(args[3]))
                {
                    StorageMigrator.migrateTo(args[2], true, sender);
                    return true;
                }
                else
                {
                    StorageMigrator.migrateTo(args[2], args[3], false, sender);
                    return true;
                }
            }

            final boolean force = (args.length >= 5 && "force".equalsIgnoreCase(args[4]));
            StorageMigrator.migrateTo(args[2], args[3], force, sender);
            return true;
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.requestInvalid.toString() + ": " + args[0]);
            return true;
        }
    }

}
