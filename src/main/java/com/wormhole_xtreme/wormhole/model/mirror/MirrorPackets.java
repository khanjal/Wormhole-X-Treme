package com.wormhole_xtreme.wormhole.model.mirror;

import java.lang.reflect.Method;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.utils.PluginLog;

/**
 * Showing one player a block that is not what the world says it is.
 *
 * <p>{@code Player.sendBlockUpdate(Location, TileState)} is the whole mechanism behind a
 * proximity mirror. The banner in the world stays stamped -- banner patterns are vanilla data
 * and outlive this plugin -- so what gets sent is the <em>blank</em>, to whoever is too far
 * away to be shown the real thing. A copy nobody else receives and nothing persists.
 *
 * <p>Reached reflectively because it <strong>does not exist on plain 1.20</strong>, which this
 * plugin still supports -- present from 1.20.1 on, checked against the jars for all seven
 * versions the matrix builds. Calling it directly would compile here and throw
 * {@code NoSuchMethodError} on that one version, and it is not the kind of method whose absence
 * should be discovered at the moment a player walks down a corridor.
 *
 * <p>{@link #available()} is how the sweep asks. On 1.20 it answers false, and a proximity
 * mirror is treated as an ordinary one -- stamped into its block and always visible. A
 * cosmetic loss on the oldest supported version, rather than a mirror that is never visible
 * at all.
 */
final class MirrorPackets
{
    /** {@code Player.sendBlockUpdate}, or null on a server that has no such method. */
    private static final Method SEND_BLOCK_UPDATE = findSendBlockUpdate();

    /** Static helpers only. */
    private MirrorPackets()
    {
    }

    /** @return true if this server can show one player a block the others do not see */
    static boolean available()
    {
        return SEND_BLOCK_UPDATE != null;
    }

    /**
     * Sends one player a dressed copy of a block.
     *
     * <p>Failures are swallowed rather than logged per call. This runs on a sweep, once per
     * player per mirror, so a server that somehow refuses would otherwise fill the log at the
     * rate of the sweep -- and the visible consequence is only that a banner does not light up.
     *
     * @param player
     *            who to show it to
     * @param location
     *            where the block is
     * @param state
     *            the appearance to send
     * @return true if the packet was handed off
     */
    static boolean send(final Player player, final Location location, final TileState state)
    {
        if ((SEND_BLOCK_UPDATE == null) || (player == null) || (location == null)
            || (state == null))
        {
            return false;
        }
        try
        {
            SEND_BLOCK_UPDATE.invoke(player, location, state);
            return true;
        }
        catch (final ReflectiveOperationException | RuntimeException | LinkageError notSent)
        {
            return false;
        }
    }

    /** Looks the method up once, and says so in the log when it is not there. */
    private static Method findSendBlockUpdate()
    {
        try
        {
            return Player.class.getMethod("sendBlockUpdate", Location.class, TileState.class);
        }
        catch (final NoSuchMethodException absent)
        {
            PluginLog.log(Level.INFO, "This server has no Player.sendBlockUpdate, so quantum"
                + " mirrors set to 'proximity' will stay visible like ordinary ones."
                + " That method arrived in 1.20.1.");
            return null;
        }
        catch (final RuntimeException | LinkageError e)
        {
            PluginLog.log(Level.WARNING, "Could not look up Player.sendBlockUpdate; quantum"
                + " mirrors set to 'proximity' will stay visible like ordinary ones.", e);
            return null;
        }
    }
}
