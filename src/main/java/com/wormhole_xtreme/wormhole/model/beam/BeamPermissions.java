package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.entity.Player;

/**
 * Permission checks for beaming.
 *
 * <p>Separate from {@link com.wormhole_xtreme.wormhole.permissions.WXPermissions}, the same
 * way {@link com.wormhole_xtreme.wormhole.model.ring.RingPermissions} is: that class is built
 * around gates, and beaming shares nothing with a gate beyond both being ways to travel. Three
 * nodes and an operator bypass is the whole requirement.
 */
public final class BeamPermissions
{
    /** Travel to a public destination or one of your own places. */
    public static final String USE = "wormhole.beam.use";

    /** Create and manage your own private places. */
    public static final String PLACE = "wormhole.beam.place";

    /** Manage public destinations, and use or manage anyone's places. */
    public static final String ADMIN = "wormhole.beam.admin";

    private BeamPermissions() {}

    /**
     * Whether a player holds a node. Operators hold everything, matching how gate and ring
     * permissions already behave on this server.
     *
     * @param player the player
     * @param node the node to check
     * @return true if they hold it
     */
    public static boolean has(final Player player, final String node)
    {
        if (player == null)
        {
            return false;
        }
        return player.isOp() || player.hasPermission(node);
    }
}
