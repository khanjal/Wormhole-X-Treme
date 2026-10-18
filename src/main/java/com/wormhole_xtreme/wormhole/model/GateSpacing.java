package com.wormhole_xtreme.wormhole.model;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * The least distance between two gates, {@code min-gate-distance} (#635).
 *
 * <p>Gates only, in the same world, measured between the middles of their openings. Rings and
 * beams are not counted. Every way of building a gate asks here before any fare is charged.
 */
public final class GateSpacing
{
    /** Building closer to another gate than the setting allows. */
    public static final String BYPASS = "wormhole.build.mindistance.bypass";

    private GateSpacing() {}

    /**
     * Why a player may not stand a gate where its opening is, or null if they may.
     *
     * @param opening
     *            the blocks of the gate's opening
     * @return the body of an error line, or null
     */
    public static String refusal(final Player player, final World world, final List<Location> opening)
    {
        final int min = ConfigManager.getMinGateDistance();
        if ((min <= 0) || opening.isEmpty() || exempt(player))
        {
            return null;
        }
        final double[] at = centreOf(opening);
        return refusal(player, min, world, at[0], at[1], at[2], StargateManager.getAllGatesUnsorted());
    }

    /** {@link #refusal(Player, World, List)} for a gate that has been found but not yet registered. */
    public static String refusal(final Player player, final Stargate gate)
    {
        return refusal(player, gate.getGateWorld(), gate.getGatePortalBlocks());
    }

    /** Why a gate whose opening is centred here is too close to one of these, in the server's language, or null. */
    static String refusal(final int min, final World world, final double x, final double y, final double z,
        final Collection<Stargate> gates)
    {
        return refusal(null, min, world, x, y, z, gates);
    }

    /** Why a gate whose opening is centred here is too close to one of these, in the player's language, or null. */
    static String refusal(final Player player, final int min, final World world, final double x, final double y,
        final double z, final Collection<Stargate> gates)
    {
        Stargate nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (final Stargate other : gates)
        {
            final List<Location> portal = other.getGatePortalBlocks();
            if (portal.isEmpty() || !world.equals(other.getGateWorld()))
            {
                continue;
            }
            final double[] at = centreOf(portal);
            final double distance = Math.sqrt(square(at[0] - x) + square(at[1] - y) + square(at[2] - z));
            if (distance < nearestDistance)
            {
                nearest = other;
                nearestDistance = distance;
            }
        }
        if ((nearest == null) || (nearestDistance >= min))
        {
            return null;
        }
        return Messages.get(player, "gate.spacing.too-close", ChatText.name(nearest.getGateName()),
            ChatText.value(String.format(Locale.ROOT, "%.1f", nearestDistance)), ChatText.value(String.valueOf(min)));
    }

    private static boolean exempt(final Player player)
    {
        return player.hasPermission(BYPASS);
    }

    private static double[] centreOf(final List<Location> blocks)
    {
        double x = 0;
        double y = 0;
        double z = 0;
        for (final Location block : blocks)
        {
            x += block.getBlockX();
            y += block.getBlockY();
            z += block.getBlockZ();
        }
        return new double[] {x / blocks.size(), y / blocks.size(), z / blocks.size()};
    }

    private static double square(final double d)
    {
        return d * d;
    }
}
