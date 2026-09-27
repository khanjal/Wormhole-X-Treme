package com.wormhole_xtreme.wormhole.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.World;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Keeps chunks loaded for as long as anything in the plugin still needs them.
 *
 * <p>Bukkit gives a plugin one ticket per chunk, not a count, so a ring letting go of its end
 * would also let go of a pet waiting to follow from the same chunk. Every holder goes through
 * here, and the ticket goes only when the last of them has let go.
 */
public final class ChunkTickets
{
    /** A chunk by world and position; Bukkit hands out a new Chunk object for the same chunk. */
    private record Key(UUID world, int x, int z) {}

    /** How many holders each ticketed chunk has. Main thread only. */
    private static final Map<Key, Integer> HOLDERS = new HashMap<>();

    private ChunkTickets() {}

    /**
     * Holds a chunk loaded, taking the plugin's ticket if nothing else of ours holds it.
     *
     * @param chunk
     *            the chunk
     */
    public static void hold(final Chunk chunk)
    {
        final Key key = keyOf(chunk);
        final int holders = HOLDERS.getOrDefault(key, Integer.valueOf(0)).intValue();
        if (holders == 0)
        {
            chunk.addPluginChunkTicket(WormholeXTreme.getThisPlugin());
        }
        HOLDERS.put(key, Integer.valueOf(holders + 1));
    }

    /**
     * Lets go of one hold on a chunk, and of the ticket once nothing holds it.
     *
     * @param chunk
     *            a chunk {@link #hold} was given
     */
    public static void release(final Chunk chunk)
    {
        final Key key = keyOf(chunk);
        final Integer holders = HOLDERS.get(key);
        if (holders == null)
        {
            return;
        }
        if (holders.intValue() > 1)
        {
            HOLDERS.put(key, Integer.valueOf(holders.intValue() - 1));
            return;
        }
        HOLDERS.remove(key);
        chunk.removePluginChunkTicket(WormholeXTreme.getThisPlugin());
    }

    /**
     * Forgets every hold, for shutdown; Bukkit drops a disabled plugin's tickets itself.
     */
    public static void clear()
    {
        HOLDERS.clear();
    }

    /**
     * Which chunk this is.
     *
     * @param chunk
     *            the chunk
     * @return its key
     */
    private static Key keyOf(final Chunk chunk)
    {
        final World world = chunk.getWorld();
        return new Key((world == null) ? null : world.getUID(), chunk.getX(), chunk.getZ());
    }
}
