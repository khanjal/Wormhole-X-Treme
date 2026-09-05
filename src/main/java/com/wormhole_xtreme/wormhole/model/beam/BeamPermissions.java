package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.command.CommandSender;

/**
 * Permission checks for beaming.
 *
 * <p>Separate from {@link com.wormhole_xtreme.wormhole.permissions.WXPermissions}, the same
 * way {@link com.wormhole_xtreme.wormhole.model.ring.RingPermissions} is: that class is built
 * around gates, and beaming shares nothing with a gate beyond both being ways to travel.
 */
public final class BeamPermissions
{
    /** Travel to a public destination or one of your own places. */
    public static final String USE = "wormhole.beam.use";

    /** Create and manage your own private places. */
    public static final String PLACE = "wormhole.beam.place";

    /** Manage public destinations, and use or manage anyone's places. */
    public static final String ADMIN = "wormhole.beam.admin";

    /** Beam yourself or another player straight to a player or raw coordinates, from a
     * player, the console, or a command block. Kept as its own node rather than folded into
     * {@link #ADMIN}: curating the destination list and moving any player anywhere instantly
     * are different orders of power, and a beam-admin delegate shouldn't automatically
     * inherit the second just for holding the first. */
    public static final String ADMIN_TELEPORT = "wormhole.beam.admin.teleport";

    private BeamPermissions() {}

    /**
     * Whether a sender holds a node. Operators hold everything, matching how gate and ring
     * permissions already behave on this server. Takes a {@link CommandSender} rather than a
     * {@code Player} so the same check works for console and command-block senders, both of
     * which {@code admin goto}/{@code send} need to support -- {@code Permissible#isOp()} and
     * {@code #hasPermission(String)} are defined on {@link CommandSender} itself, not just on
     * {@code Player}.
     *
     * @param sender the sender
     * @param node the node to check
     * @return true if they hold it
     */
    public static boolean has(final CommandSender sender, final String node)
    {
        return sender.isOp() || sender.hasPermission(node);
    }
}
