package com.wormhole_xtreme.wormhole.command;

import org.bukkit.command.CommandSender;

/**
 * Simple subcommand functional interface for splitting the large Wormhole command
 * into focused handler classes.
 */
public interface SubCommand
{

    boolean execute(final CommandSender sender, final String[] args);

}
