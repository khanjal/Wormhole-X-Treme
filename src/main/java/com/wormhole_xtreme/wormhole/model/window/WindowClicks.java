package com.wormhole_xtreme.wormhole.model.window;

import static com.wormhole_xtreme.wormhole.model.window.WindowStore.ACTIVE;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.VIEWS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * What the window drawing does about a click: sending a view again after one the server refused, and
 * saying which mirror, if any, a click through the view reaches (#430).
 *
 * <p>Moved out of {@link Windows} verbatim; public because the listeners and the mirror command call it.
 */
public final class WindowClicks
{
    /** Static helpers only. */
    private WindowClicks()
    {
    }

    /** How long after a click the server's own correction of the clicked blocks has gone out. */
    private static final long CORRECTION_MILLIS = 30L;

    /**
     * Sends the two blocks the server corrects after a click straight after its correction, and
     * the whole view a tick later.
     *
     * <p>A click the server refuses makes it send the clicked block and the one on the face
     * clicked as they really are, in the same tick and after every listener; the whole view a tick
     * later left the real banner showing for that tick -- "a very quick flicker of a banner when
     * right clicking". Those two are sent again as the view draws them from off the main thread a
     * few milliseconds on, which lands after the correction and before the tick is out. Nothing
     * here reads the world off the main thread: the blocks are looked up first.
     *
     * @param player
     *            who clicked
     * @param face
     *            the face of it clicked, or null if it is not known
     */
    public static void resend(final Player player, final Block clicked, final BlockFace face)
    {
        final ViewerDrawing view = VIEWS.get(player.getUniqueId());
        if (view == null)
        {
            return;
        }
        final List<Location> spots = new ArrayList<>();
        final List<BlockData> states = new ArrayList<>();
        final Block beside = (face == null) ? null : clicked.getRelative(face);
        for (final Block block : (beside == null) ? List.of(clicked) : List.of(clicked, beside))
        {
            final BlockData data = view.drawn.get(Cells.key(block.getX(), block.getY(), block.getZ()));
            if (data != null)
            {
                spots.add(new Location(player.getWorld(), block.getX(), block.getY(), block.getZ()));
                states.add(data);
            }
        }
        resend(player);
        if (spots.isEmpty())
        {
            return;
        }
        try
        {
            WormholeXTreme.getScheduler().runTaskAsynchronously(WormholeXTreme.getThisPlugin(), () ->
            {
                try
                {
                    Thread.sleep(CORRECTION_MILLIS);
                }
                catch (final InterruptedException interrupted)
                {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < spots.size(); i++)
                {
                    player.sendBlockChange(spots.get(i), states.get(i));
                }
            });
        }
        catch (final RuntimeException noScheduler)
        {
            // No scheduler yet, during startup or in tests: the whole view a tick later is enough.
        }
    }

    /**
     * Sends a player's whole view again a tick after they click a mirror.
     *
     * <p>A click the server refuses -- a punch that does not break, a right-click that places
     * nothing -- makes it send the clicked block, and the one beside it, as they really are once
     * the click is handled. Only marking the view left the real banner and wall showing until the
     * player moved or the sweep came round.
     *
     * @param player
     *            who clicked
     */
    public static void resend(final Player player)
    {
        final ViewerDrawing view = VIEWS.get(player.getUniqueId());
        if (view == null)
        {
            return;
        }
        view.fullAt = 0L;
        view.composedAt = 0L;
        view.eye = Long.MIN_VALUE;
        try
        {
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                () -> sendAgain(player), 1L);
        }
        catch (final RuntimeException noScheduler)
        {
            // No scheduler yet, during startup or in tests. The next move or sweep sends it.
        }
    }

    /**
     * Sends everything a view is meant to hold again: what it has been sent, with what it is still
     * owed applied.
     *
     * <p>Not what it has been sent alone. A click books this a tick after a redraw that may still be
     * streaming, and part-way through a stream what has gone out is half one room and half the next.
     * Taken as the truth, that threw the rest of the stream away and drew the half-finished room
     * again: part of the room somebody had just scrolled away from stayed up until the next sweep.
     */
    private static void sendAgain(final Player player)
    {
        final ViewerDrawing view = VIEWS.get(player.getUniqueId());
        if (view == null)
        {
            return;
        }
        final Map<Long, BlockData> meant = new HashMap<>(view.drawn);
        for (final Map.Entry<Long, BlockData> owed : view.pending.entrySet())
        {
            if (owed.getValue() == null)
            {
                meant.remove(owed.getKey());
            }
            else
            {
                meant.put(owed.getKey(), owed.getValue());
            }
        }
        WindowStream.send(player, view, meant, player.getEyeLocation(), Windows.now(), true, Set.of());
    }

    /**
     * Sends the whole view again, at the next sweep, to everybody looking into one mirror.
     *
     * <p>For after its banner is written to the world. Stamping sends every client the real banner,
     * which lands over the view where the banner is drawn away, and the view -- believing it sent
     * air there already -- would not send it again until its next whole resend, half a minute later.
     *
     * @param mirrorName
     *            the mirror whose banner changed
     */
    public static void resendFor(final String mirrorName)
    {
        for (final ViewerDrawing view : VIEWS.values())
        {
            if (view.mirrors.contains(mirrorName))
            {
                view.fullAt = 0L;
            }
        }
    }

    /**
     * The window mirror a player clicked through, if they clicked one they are looking into.
     *
     * <p>On every right-click of every block, so a player who is looking into no window is
     * answered from one map lookup, before the block is asked anything.
     *
     * @param player
     *            who clicked
     * @param block
     *            the real block behind what they clicked
     * @return the mirror, or null
     */
    public static QuantumMirror clicked(final Player player, final Block block)
    {
        final UUID id = (player == null) ? null : player.getUniqueId();
        final ViewerDrawing view = (id == null) ? null : VIEWS.get(id);
        if ((view == null) || (block == null))
        {
            return null;
        }
        final Spot at = new Spot(block.getX(), block.getY(), block.getZ());
        for (final String name : view.mirrors)
        {
            final WindowState window = ACTIVE.get(name);
            // A gate is walked through under its own rules; a punch at its view goes nowhere.
            if ((window != null) && !window.walkThrough() && window.open.contains(at)
                && window.anchor().getWorld().equals(block.getWorld()))
            {
                return window.source.punchTarget();
            }
        }
        // Anywhere else in a room drawn for them: the real block behind is not what they clicked.
        final boolean drawn = view.world.equals(block.getWorld()) && view.drawn.containsKey(Cells.key(at.x(), at.y(), at.z()));
        return drawn ? nearestWindow(view, block) : null;
    }

    /**
     * Whether a player is being shown this block as part of a view rather than as it is.
     *
     * <p>Acting on one acts on the real block behind it, unseen: a torch placed there hid under the
     * view until it was taken back. One map lookup for anybody looking into no window.
     *
     * @param player
     *            who is acting on the block
     * @param block
     *            the real block at that spot
     * @return true if the block is drawn over for this player
     */
    public static boolean drew(final Player player, final Block block)
    {
        final UUID id = (player == null) ? null : player.getUniqueId();
        final ViewerDrawing view = ((id == null) || (block == null)) ? null : VIEWS.get(id);
        return (view != null) && view.world.equals(block.getWorld())
            && view.drawn.containsKey(Cells.key(block.getX(), block.getY(), block.getZ()));
    }

    /** The mirror nearest a block, of the windows a view is looking into in its world. */
    private static QuantumMirror nearestWindow(final ViewerDrawing view, final Block block)
    {
        QuantumMirror nearest = null;
        long best = Long.MAX_VALUE;
        for (final String name : view.mirrors)
        {
            final WindowState window = ACTIVE.get(name);
            if ((window == null) || window.walkThrough() || !window.anchor().getWorld().equals(block.getWorld()))
            {
                continue;
            }
            final long dx = (long) window.anchor().getX() - block.getX();
            final long dy = (long) window.anchor().getY() - block.getY();
            final long dz = (long) window.anchor().getZ() - block.getZ();
            final long distance = (dx * dx) + (dy * dy) + (dz * dz);
            if (distance < best)
            {
                best = distance;
                nearest = window.source.punchTarget();
            }
        }
        return nearest;
    }
}
