package com.wormhole_xtreme.wormhole.model.mirror;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.PluginLog;

/**
 * Pulling one viewer's own fog in to where a mirror's room ends.
 *
 * <p>A room reaches {@code mirror-view-depth} and nothing is drawn past it, but the room ending
 * is not the same as the world ending: the client goes on drawing this world beyond it, so the
 * hills behind a mirror show over the far edge of what it is showing. A flat wall the colour of
 * the sky stood there for a while and was taken back, because a flat colour where the far sky
 * should be reads as a wall however cheap it is.
 *
 * <p>The only thing that actually stops a client drawing past a distance is telling it not to
 * have the chunks. {@code Player.setSendViewDistance(int)} does exactly that, per player, and
 * it costs no blocks at all: set it to the room's depth on approach, put it back on leaving, and
 * the far edge is the client's own fog rather than anything this plugin drew.
 *
 * <p><strong>Paper only.</strong> That method, with {@code setViewDistance},
 * {@code setSimulationDistance} and {@code setNoTickViewDistance}, is on Paper's {@code Player}
 * and on no Spigot jar in the range this plugin supports -- read with {@code javap} from the API
 * jars themselves. So it is reached reflectively, the same as {@code sendBlockUpdate} is for
 * 1.20, and {@link #available()} is how the drawing asks. On Spigot it answers false and nothing
 * happens: this world shows past the room, which is what it does today.
 *
 * <p>It is a radius round the player, not a direction, so it pulls the fog in everywhere and not
 * only through the opening. That is why it is off by default ({@code mirror-fog-at-depth}) and
 * why it suits a shallow depth: at the default 160 there is nothing to gain, since the room
 * already reaches as far as a server usually sends.
 */
final class MirrorFog
{
    /**
     * The least a client's send distance is ever set to, in chunks.
     *
     * <p>Two, because a client sent less than the chunk it stands in and its neighbours has
     * nowhere to walk. A depth under a chunk and a half asks for less than this and gets this.
     */
    static final int LEAST_CHUNKS = 2;

    /** How a player's send distance is read and written: the server's own, or a test's. */
    interface SendDistance
    {
        /** @return the chunks this player is being sent now, or 0 if it cannot be read */
        int get(Player player);

        /** Sets how many chunks this player is sent. */
        void set(Player player, int chunks);
    }

    /** {@code Player.getSendViewDistance}, or null on a server without it. */
    private static final Method GET = find("getSendViewDistance");

    /** {@code Player.setSendViewDistance}, or null on a server without it. */
    private static final Method SET = find("setSendViewDistance", int.class);

    /** What one narrowed viewer was being sent before, and what a mirror asked for instead. */
    private record Narrowed(int before, int now)
    {
    }

    /** Every narrowed viewer, so each can be put back exactly and debug can say what happened. */
    private static final Map<UUID, Narrowed> BEFORE = new HashMap<>();

    /** The server's own, or whatever a test installed; null where the API is absent. */
    private static SendDistance sendDistance = reflective();

    /** Static state only. */
    private MirrorFog()
    {
    }

    /** @return true if this server can be told how far to send one player's chunks */
    static boolean available()
    {
        return sendDistance != null;
    }

    /**
     * Uses this instead of the server's own methods, for a test.
     *
     * <p>There is no Paper jar on the compile path, so the reflective lookup finds nothing here
     * and every decision below would be unreachable. Handing in a stand-in is the only way to
     * hold the arithmetic and the put-it-back with a test.
     *
     * @param stand
     *            what to read and write through, or null to go back to the server's own
     */
    static void sendDistanceWith(final SendDistance stand)
    {
        sendDistance = (stand == null) ? reflective() : stand;
        BEFORE.clear();
    }

    /**
     * Pulls a viewer's fog in to a room this deep, if that is nearer than what they are sent.
     *
     * <p>A chunk over the depth, the same allowance {@code freshChunks} makes, so the room's own
     * far edge is inside what the client has rather than exactly at its limit.
     *
     * <p>Once per viewer: the second call while they are still narrowed does nothing, so what is
     * remembered is always what they had before any mirror touched it.
     *
     * @param player
     *            the viewer being drawn a room
     * @param depth
     *            how far the room reaches, in blocks
     */
    static void narrow(final Player player, final int depth)
    {
        if ((sendDistance == null) || (player == null) || !ConfigManager.isMirrorFogAtDepth())
        {
            return;
        }
        final UUID id = player.getUniqueId();
        if ((id == null) || BEFORE.containsKey(id))
        {
            return;
        }
        final int wanted = Math.max(LEAST_CHUNKS, ((depth + 15) / 16) + 1);
        final int sent = sendDistance.get(player);
        // Nothing to gain where the room already reaches as far as the client is being sent,
        // which is the default depth on an ordinary server.
        if ((sent <= 0) || (wanted >= sent))
        {
            return;
        }
        BEFORE.put(id, new Narrowed(sent, wanted));
        sendDistance.set(player, wanted);
    }

    /**
     * Puts back what a viewer was being sent before a mirror narrowed it.
     *
     * <p>Quiet for a viewer who was never narrowed, which is most of them: the setting is off by
     * default, the API is absent on Spigot, and a room at the default depth gains nothing.
     *
     * @param player
     *            the viewer who is no longer being drawn a room
     */
    static void restore(final Player player)
    {
        if ((sendDistance == null) || (player == null))
        {
            return;
        }
        final UUID id = player.getUniqueId();
        final Narrowed was = (id == null) ? null : BEFORE.remove(id);
        if (was != null)
        {
            sendDistance.set(player, was.before());
        }
    }

    /**
     * Forgets every remembered distance without putting any back.
     *
     * <p>For a reload or a test. Nothing is sent, because the caller either has no players left
     * to send to or has already put them back one at a time.
     */
    static void clear()
    {
        BEFORE.clear();
    }

    /** @return true if this viewer is narrowed right now */
    static boolean narrowed(final UUID id)
    {
        return BEFORE.containsKey(id);
    }

    /** @return the chunks a mirror asked for on this viewer's behalf, or 0 if none did */
    static int narrowedTo(final UUID id)
    {
        final Narrowed was = BEFORE.get(id);
        return (was == null) ? 0 : was.now();
    }

    /** @return the chunks this viewer was being sent before a mirror narrowed it, or 0 */
    static int wasSent(final UUID id)
    {
        final Narrowed was = BEFORE.get(id);
        return (was == null) ? 0 : was.before();
    }

    /** The server's own methods, or null where this server has none. */
    private static SendDistance reflective()
    {
        if ((GET == null) || (SET == null))
        {
            return null;
        }
        return new SendDistance()
        {
            @Override
            public int get(final Player player)
            {
                try
                {
                    return (int) GET.invoke(player);
                }
                catch (final ReflectiveOperationException | RuntimeException | LinkageError unreadable)
                {
                    return 0;
                }
            }

            @Override
            public void set(final Player player, final int chunks)
            {
                try
                {
                    SET.invoke(player, chunks);
                }
                catch (final ReflectiveOperationException | RuntimeException | LinkageError notSet)
                {
                    // One viewer keeps the fog they had. Not logged per call: this runs on a
                    // sweep, and the visible consequence is only that a room's far edge shows
                    // this world beyond it, which is what Spigot does anyway.
                    BEFORE.remove(player.getUniqueId());
                }
            }
        };
    }

    /** Looks one method up once, saying so in the log only for the one that decides the feature. */
    private static Method find(final String name, final Class<?>... parameters)
    {
        try
        {
            return Player.class.getMethod(name, parameters);
        }
        catch (final NoSuchMethodException absent)
        {
            if ("setSendViewDistance".equals(name))
            {
                PluginLog.log(Level.INFO, "This server has no Player.setSendViewDistance, so"
                    + " mirror-fog-at-depth does nothing: a mirror's room ends where it ends and"
                    + " this world shows past it. That method is Paper's.");
            }
            return null;
        }
        catch (final RuntimeException | LinkageError e)
        {
            PluginLog.log(Level.WARNING, "Could not look up Player." + name + "; a mirror's room"
                + " will show this world past its far edge.", e);
            return null;
        }
    }
}
