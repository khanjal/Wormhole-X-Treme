package com.wormhole_xtreme.wormhole.model.window;

import static com.wormhole_xtreme.wormhole.model.window.WindowStore.STATES;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.VIEWS;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.workSpent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPackets;

/**
 * Sending a viewer what a window draws: the difference since last time, a tick's worth at a time,
 * nearest the eye first (#430).
 *
 * <p>Moved out of {@link Windows} verbatim.
 */
final class WindowStream
{
    /** What {@link #freshChunks} gives when every chunk is fresh, told apart by identity from any real set. */
    static final Set<Long> ALL_CHUNKS = Collections.unmodifiableSet(new HashSet<>());

    /** Static helpers only. */
    private WindowStream()
    {
    }

    /**
     * Sends a viewer what changed since their last drawing, or all of it when asked to: a tick's
     * worth now ({@link Windows#Windows.streamPerTick}) and the rest a tick apart, nearest the eye first and a
     * chunk section at a time, so the client re-meshes each section once and no frame waits for
     * all of them. What was owed from an earlier redraw is owed no longer; this one says what is.
     *
     * @param fresh
     *            the chunks the client has just been handed, whose drawing is sent again whatever
     *            it was sent before; or {@link #ALL_CHUNKS}
     */
    static void send(final Player player, final ViewerDrawing view, final Map<Long, BlockData> wanted,
        final Location eye, final long now, final boolean full, final Set<Long> fresh)
    {
        final Map<Long, BlockData> owed = new HashMap<>();
        for (final Map.Entry<Long, BlockData> entry : wanted.entrySet())
        {
            final long cell = entry.getKey();
            if (full || (fresh == ALL_CHUNKS) || fresh.contains(chunkOfCell(cell))
                || !entry.getValue().equals(view.drawn.get(cell)))
            {
                owed.put(cell, entry.getValue());
            }
        }
        for (final Long cell : view.drawn.keySet())
        {
            if (!wanted.containsKey(cell))
            {
                owed.put(cell, null);
            }
        }
        view.pending.clear();
        view.undrawable = 0;
        for (final long cell : nearestFirst(owed.keySet(), eye))
        {
            view.pending.put(cell, owed.get(cell));
        }
        if (full)
        {
            view.fullAt = now;
        }
        stream(player, view, Windows.streamPerTick);
    }

    /** Sends the next of what a viewer is owed, up to a limit, and books the rest for the next tick. */
    static void stream(final Player player, final ViewerDrawing view, final int limit)
    {
        final List<BlockState> changes = new ArrayList<>();
        final List<TileState> restored = new ArrayList<>();
        final Iterator<Map.Entry<Long, BlockData>> next = view.pending.entrySet().iterator();
        while (next.hasNext() && (changes.size() < limit))
        {
            final Map.Entry<Long, BlockData> entry = next.next();
            next.remove();
            final long cell = entry.getKey();
            if (entry.getValue() == null)
            {
                truth(changes, restored, view.world, cell);
                view.drawn.remove(cell);
                continue;
            }
            final BlockState state = drawn(view.world, cell, entry.getValue());
            if (state == null)
            {
                view.undrawable++;
            }
            else
            {
                changes.add(state);
            }
            view.drawn.put(cell, entry.getValue());
        }
        if (!changes.isEmpty())
        {
            player.sendBlockChanges(changes);
            workSpent += changes.size();
        }
        // A block change carries no banner patterns or sign text, so those follow on their own.
        restored.forEach(tile -> MirrorPackets.send(player, tile.getLocation(), tile));
        if (!view.pending.isEmpty())
        {
            streamLater(player, view);
        }
    }

    /** Books the next tick's worth of a viewer's stream, if it is not booked already. */
    private static void streamLater(final Player player, final ViewerDrawing view)
    {
        if (view.streamQueued)
        {
            return;
        }
        try
        {
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                () -> streamOn(player), 1L);
            view.streamQueued = true;
        }
        catch (final RuntimeException noScheduler)
        {
            // No scheduler yet, during startup or in tests. The next redraw works out what is owed again.
        }
    }

    /** A tick on: the next of what a viewer is owed, and the end of their view once nothing is left of it. */
    private static void streamOn(final Player player)
    {
        final UUID id = player.getUniqueId();
        final ViewerDrawing view = VIEWS.get(id);
        if (view == null)
        {
            return;
        }
        view.streamQueued = false;
        if (!player.isOnline() || !player.getWorld().equals(view.world))
        {
            // Gone, or in a world whose chunks have replaced everything drawn: nothing is owed.
            Windows.endView(id, player);
            return;
        }
        stream(player, view, Windows.streamPerTick);
        if (view.pending.isEmpty() && view.drawn.isEmpty())
        {
            Windows.endView(id, player);
        }
    }

    /**
     * Cells in the order to send them: nearest the eye first, sixteen blocks at a time, and
     * within that a chunk section at a time, so a batch touches as few sections as it can.
     */
    static long[] nearestFirst(final Set<Long> cells, final Location eye)
    {
        final int count = cells.size();
        final long[] plain = new long[count];
        final long[] keyed = new long[count];
        int index = 0;
        for (final long cell : cells)
        {
            plain[index] = cell;
            keyed[index] = (orderOf(cell, eye) << 20) | index;
            index++;
        }
        if (count >= (1 << 20))
        {
            // More than a million: the index no longer fits beside the order, and a view this big is not sorted.
            return plain;
        }
        Arrays.sort(keyed);
        final long[] ordered = new long[count];
        for (int at = 0; at < count; at++)
        {
            ordered[at] = plain[(int) (keyed[at] & 0xFFFFF)];
        }
        return ordered;
    }

    /** A cell's place in the order to send it: its sixteen-block band from the eye, then its chunk section. */
    private static long orderOf(final long cell, final Location eye)
    {
        final int x = Cells.unpackX(cell);
        final int y = Cells.unpackY(cell);
        final int z = Cells.unpackZ(cell);
        final double dx = (x + 0.5) - eye.getX();
        final double dy = (y + 0.5) - eye.getY();
        final double dz = (z + 0.5) - eye.getZ();
        final long band = Math.min(0x7FF, (long) (Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)) / 16.0));
        return (band << 32) | (((long) (x >> 4) & 0xFFF) << 20) | (((long) (z >> 4) & 0xFFF) << 8) | ((long) (y >> 4) & 0xFF);
    }

    /**
     * The chunks a viewer's client is handed on crossing from one chunk to another: those within
     * its reach of the new chunk and not of the old, and a chunk over for the edge. Everything
     * drawn in them has to be sent again, since a chunk arrives as the world has it.
     *
     * @return the chunks: none when the eye has stayed in its chunk, or {@link #ALL_CHUNKS} on a
     *         first drawing or a reach the server does not say
     */
    static Set<Long> freshChunks(final Player player, final long from, final long to)
    {
        if (from == to)
        {
            return Set.of();
        }
        // What the server sends is the shorter of its own distance and the client's.
        final int reach = Math.min(player.getWorld().getViewDistance(), player.getClientViewDistance()) + 1;
        if ((from == Long.MIN_VALUE) || (reach <= 1))
        {
            return ALL_CHUNKS;
        }
        final int toX = (int) (to >> 32);
        final int toZ = (int) to;
        final int fromX = (int) (from >> 32);
        final int fromZ = (int) from;
        final Set<Long> fresh = new HashSet<>();
        for (int x = toX - reach; x <= (toX + reach); x++)
        {
            for (int z = toZ - reach; z <= (toZ + reach); z++)
            {
                if ((Math.abs(x - fromX) > reach) || (Math.abs(z - fromZ) > reach))
                {
                    fresh.add(Cells.chunkKey(x, z));
                }
            }
        }
        return fresh;
    }

    /** The chunk a cell is in, as a key. */
    static long chunkOfCell(final long cell)
    {
        return Cells.chunkKey(Cells.unpackX(cell) >> 4, Cells.unpackZ(cell) >> 4);
    }

    /** One block, drawn as something else, or null if this world cannot draw it. */
    private static BlockState drawn(final World here, final long cell, final BlockData data)
    {
        final int x = Cells.unpackX(cell);
        final int z = Cells.unpackZ(cell);
        if (!here.isChunkLoaded(x >> 4, z >> 4))
        {
            return null;
        }
        // Reused rather than read again: a redraw as somebody walks would otherwise snapshot
        // every changed block from the world, only to overwrite what it read.
        final Map<Long, BlockState> states = STATES.computeIfAbsent(here.getName(),
            name -> new HashMap<>());
        final BlockState state = states.computeIfAbsent(cell,
            key -> here.getBlockAt(x, Cells.unpackY(key), z).getState());
        try
        {
            state.setBlockData(data);
        }
        catch (final IllegalArgumentException refused)
        {
            // A block entity's state will not always take another block's data. That one block
            // shows as it really is.
            return null;
        }
        return state;
    }

    /** Adds one block as the world really has it. */
    private static void truth(final List<BlockState> changes, final List<TileState> restored,
        final World here, final long cell)
    {
        final int x = Cells.unpackX(cell);
        final int z = Cells.unpackZ(cell);
        // A chunk that has unloaded is one the client dropped too, and it gets a fresh copy.
        if (!here.isChunkLoaded(x >> 4, z >> 4))
        {
            return;
        }
        final BlockState state = here.getBlockAt(x, Cells.unpackY(cell), z).getState();
        changes.add(state);
        if (state instanceof TileState tile)
        {
            restored.add(tile);
        }
    }
}
