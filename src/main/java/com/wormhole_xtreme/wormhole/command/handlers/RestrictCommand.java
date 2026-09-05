package com.wormhole_xtreme.wormhole.command.handlers;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Handler for '/wormhole restrict', which no longer restricts anything.
 *
 * <p>Build restriction was removed some time ago and left a shell behind: the permission
 * check {@code StargateRestrictions.isPlayerBuildRestricted} was cut down to
 * {@code return false}, so every call site was a constant and the "you are at your max
 * number of built gates" message was unreachable. The command itself kept running, and kept
 * answering "build count restrictions set to: true" -- but {@code BUILD_RESTRICTION_ENABLED}
 * was never registered in {@code DefaultSettings}, so {@code setConfigValue} discarded the
 * write, nothing read the value back, and the setting never reached {@code config.yml}
 * either. Three separate layers of doing nothing, each of which looked fine on its own.
 *
 * <p>The name is kept rather than removed because that is this registry's standing rule --
 * anyone with a subcommand in a command block or a script keeps getting a sensible response
 * instead of an unknown-command error. What changed is that the response is now true. Gate
 * building is governed by the {@code wormhole.build} permission; a server that wants to cap
 * how many gates someone owns should do it through its permissions plugin.
 */
public class RestrictCommand implements SubCommand
{

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
            + "Build restriction was removed and this command no longer does anything.");
        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + "Gate building is controlled by the wormhole.build permission instead.");
        return true;
    }

}
