/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Permission nodes for transport rings.
 */
package com.wormhole_xtreme.wormhole.model.ring;

import org.bukkit.entity.Player;

/**
 * Permission checks for transport rings.
 *
 * <p>Separate from {@link com.wormhole_xtreme.wormhole.permissions.WXPermissions} on
 * purpose. That class is built around gates: its checks take a {@code Stargate}, consult the
 * gate's network, and fall through a chain of owner and network rules that has no meaning
 * here. Rings have no networks, no dialers and no signs, so passing them through it would
 * mean adding cases that ignore most of their own arguments.
 *
 * <p>What a ring needs is far simpler — four nodes and an operator bypass — so it is written
 * plainly rather than borrowed.
 */
public final class RingPermissions
{
    /** Build and pair rings. */
    public static final String BUILD = "wormhole.ring.build";

    /** Travel by a ring you are allowed on. */
    public static final String USE = "wormhole.ring.use";

    /** Use and manage any pair, whoever owns it and whatever its access is. */
    public static final String ADMIN = "wormhole.ring.admin";

    /** Own more pairs than the configured quota. */
    public static final String UNLIMITED = "wormhole.ring.unlimited";

    private RingPermissions() {}

    /**
     * Whether a player holds a node.
     *
     * <p>Operators hold everything, with or without a permissions plugin, which matches how
     * gate permissions already behave on this server.
     *
     * @param player
     *            the player
     * @param node
     *            the node to check
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

    /**
     * Whether a player may travel by this pair.
     *
     * <p>Two things have to be true: the server lets them use rings at all, and this
     * particular pair is one they are on. Administrators pass regardless, which is what
     * makes a private pair still fixable by staff when its owner is not around.
     *
     * @param player
     *            the player
     * @param pair
     *            the pair
     * @return true if they may travel by it
     */
    public static boolean mayUse(final Player player, final RingPair pair)
    {
        if ((player == null) || (pair == null))
        {
            return false;
        }
        if (has(player, ADMIN))
        {
            return true;
        }
        return has(player, USE) && pair.mayUse(player.getUniqueId().toString());
    }

    /**
     * Whether a player may change or remove this pair.
     *
     * <p>Being named on a private pair's allow list lets somebody travel by it. It does not
     * let them recolour it, rename it, hand it to somebody else or delete it — those stay
     * with the owner, and with staff.
     *
     * @param player
     *            the player
     * @param pair
     *            the pair
     * @return true if they may manage it
     */
    public static boolean mayManage(final Player player, final RingPair pair)
    {
        if ((player == null) || (pair == null))
        {
            return false;
        }
        return has(player, ADMIN) || pair.isOwnedBy(player.getUniqueId().toString());
    }
}
