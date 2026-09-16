package com.wormhole_xtreme.wormhole.model.preview;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The nodes for gate build previews.
 */
public final class PreviewPermissions
{
    /** Showing a shape in front of you under {@code /wormhole gate build}. */
    public static final String PREVIEW = "wormhole.build.preview";

    private PreviewPermissions() {}

    /**
     * Whether a sender may preview gate shapes. Only a player can, since a preview stands in
     * front of somebody.
     *
     * @param sender
     *            whoever typed the command
     * @return true for an operator or a player holding the node
     */
    public static boolean mayPreview(final CommandSender sender)
    {
        return (sender instanceof Player player) && (player.isOp() || player.hasPermission(PREVIEW));
    }
}
