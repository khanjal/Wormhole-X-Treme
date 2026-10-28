package com.wormhole_xtreme.wormhole.model;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * How many gates one player may own, {@code max-gates-per-player} and {@code wormhole.gates.max.<n>} (#633).
 *
 * <p>The setting is the limit for everyone; a player holding {@code wormhole.gates.max.<n>} has the highest
 * such {@code n} instead, so a group can be given more, or fewer. {@code wormhole.gates.unlimited} lifts it,
 * and operators have that by default. Only gates that stand count, so a removed gate is not owned. Every way of
 * building a gate asks here before any fare is charged.
 */
public final class GateQuota
{
    /** Owning any number of gates. */
    public static final String UNLIMITED = "wormhole.gates.unlimited";

    /** What a group's limit begins with: {@code wormhole.gates.max.5} allows five. */
    static final String MAX_PREFIX = "wormhole.gates.max.";

    private GateQuota() {}

    /**
     * How many gates a player may own.
     *
     * @return the most, or 0 for any number
     */
    public static int limitFor(final Player player)
    {
        if (player.hasPermission(UNLIMITED))
        {
            return 0;
        }
        int granted = 0;
        for (final PermissionAttachmentInfo held : player.getEffectivePermissions())
        {
            if (held.getValue())
            {
                granted = Math.max(granted, groupLimit(held.getPermission()));
            }
        }
        return (granted > 0) ? granted : ConfigManager.getMaxGatesPerPlayer();
    }

    /** The limit a permission names, or 0 if it is not one or does not name a whole number above 0. */
    static int groupLimit(final String permission)
    {
        if (!permission.regionMatches(true, 0, MAX_PREFIX, 0, MAX_PREFIX.length()))
        {
            return 0;
        }
        try
        {
            return Math.max(0, Integer.parseInt(permission.substring(MAX_PREFIX.length())));
        }
        catch (final NumberFormatException notANumber)
        {
            return 0;
        }
    }

    /**
     * How many standing gates a player owns.
     *
     * @return the count
     */
    public static int ownedBy(final Player player)
    {
        int owned = 0;
        for (final Stargate gate : StargateManager.getAllGatesUnsorted())
        {
            if (gate.isOwner(player))
            {
                owned++;
            }
        }
        return owned;
    }

    /**
     * Why a player may not build another gate, or null if they may.
     *
     * @return the body of an error line, or null
     */
    public static String refusal(final Player player)
    {
        final int limit = limitFor(player);
        if ((limit <= 0) || (ownedBy(player) < limit))
        {
            return null;
        }
        final String shown = ChatText.value(String.valueOf(limit));
        if (limit == 1)
        {
            return Messages.get(player, "gate.quota.reached.one", shown);
        }
        return Messages.get(player, "gate.quota.reached.other", shown);
    }

    /**
     * What a player is told of their gates, or null if nothing limits them.
     *
     * @return a line, or null
     */
    public static String standing(final Player player)
    {
        final int limit = limitFor(player);
        if (limit <= 0)
        {
            return null;
        }
        return Messages.get(player, "gate.quota.count", ChatText.value(String.valueOf(ownedBy(player))),
            ChatText.value(String.valueOf(limit)));
    }
}
