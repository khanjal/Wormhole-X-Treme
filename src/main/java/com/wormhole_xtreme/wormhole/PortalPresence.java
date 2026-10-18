package com.wormhole_xtreme.wormhole;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * What a player standing in or arriving at an open portal needs: the flight exemption that keeps the
 * server from kicking them for floating, and the redraws that put the portal back on their screen.
 *
 * <p>Split out of {@link WormholeXTremePlayerListener}, which calls these from its move, teleport, join,
 * respawn and world-change handlers. {@link #updatePortalFlightExemption} and {@link #hasChangedChunk}
 * run on every player move, so they stay static calls with no allocation of their own.
 */
final class PortalPresence
{
    private PortalPresence() {}

    /**
     * Whether a move crossed a chunk boundary.
     *
     * @param from
     *            where the player was
     * @param to
     *            where the player is
     * @return true if the two are in different chunks
     */
    static boolean hasChangedChunk(final Location from, final Location to)
    {
        if ((from == null) || (to == null))
        {
            return false;
        }
        // Bit-shifting rather than getChunk(), which loads the chunk if it is not resident.
        return ((from.getBlockX() >> 4) != (to.getBlockX() >> 4))
            || ((from.getBlockZ() >> 4) != (to.getBlockZ() >> 4))
            || !Objects.equals(from.getWorld(), to.getWorld());
    }

    /**
     * When a redraw is retried after a player arrives somewhere, in ticks.
     *
     * <p>The portal is drawn over the client's copy of the chunk, so it only sticks once
     * the client actually holds that chunk. On arrival it usually does not yet, and a block
     * change that lands first is simply overwritten by the chunk that follows it.
     *
     * <p>How long that takes is not observable from the server and is not a fixed cost. A
     * short hop reuses chunks the client already has, while a trip across the world makes
     * it fetch and render everything from scratch — which is why a single redraw one tick
     * later worked for a gate nearby and did nothing at all for a distant one. Rather than
     * guess a delay, redraw a few times across the couple of seconds an arrival can take.
     * Each pass is a handful of block changes to one player, and a redundant pass costs
     * nothing visible: the client is being told what it is already showing.
     */
    private static final long[] ARRIVAL_REDRAW_TICKS = { 1L, 10L, 30L, 60L };

    /**
     * Players granted flight because they are standing in a portal, by id.
     *
     * <p>Only ones this class granted it to, so a creative player or someone another plugin
     * has given flight is never stripped of it on the way out of a gate.
     */
    private static final Set<UUID> portalFlightGranted =
        ConcurrentHashMap.newKeySet();

    /** Forgets a player who has left, so their id does not sit in the set for good. */
    static void forgetFlight(final UUID id)
    {
        portalFlightGranted.remove(id);
    }

    /**
     * Grants or withdraws the flight exemption a player needs to stand in a portal.
     *
     * <p>A portal is AIR on the server with the portal material drawn over it on the
     * client, so a traveller does not drown in a water gate or burn in a lava one. The cost
     * is that the two disagree about physics. The client is simulating water: it floats the
     * player upward and reports them rising. The server sees them climbing through open air
     * with nothing holding them up, decides that is flight, and kicks them.
     *
     * <p>Nothing here can make the server agree with the client — the whole point is that
     * the block is not really water. What it can do is stop the disagreement being fatal,
     * by allowing flight for exactly as long as the player is inside the portal.
     *
     * <p>Withdrawn on the way out, and only from players this granted it to. Someone in
     * creative, or with flight from another plugin, keeps what they came in with.
     *
     * @param player
     *            the player who moved
     * @param at
     *            where they moved to
     */
    static void updatePortalFlightExemption(final Player player, final Location at)
    {
        if ((player == null) || (at == null))
        {
            return;
        }
        try
        {
            final boolean inPortal = isInsideOpenPortal(at);
            // Every move event reaches here, including the rotation-only ones a player
            // generates just by looking around, and on a server where nobody is standing in
            // a wormhole both halves below are no-ops. Answering that before asking the
            // player for their id keeps the common case to one empty-set check.
            if (!inPortal && portalFlightGranted.isEmpty())
            {
                return;
            }
            final UUID id = player.getUniqueId();

            if (inPortal)
            {
                // Already able to fly, by game mode or by another plugin: leave it alone,
                // and do not record it, or the way out would take away what it did not give.
                if (!player.getAllowFlight())
                {
                    player.setAllowFlight(true);
                    portalFlightGranted.add(id);
                }
            }
            else if (portalFlightGranted.remove(id))
            {
                clearGrantedFlight(player);
            }
        }
        // On the move path, so a failure here must never disturb the event.
        catch (final RuntimeException ignore) { /* best effort */ }
    }

    /**
     * Takes back flight this class granted, unless the player's game mode provides it.
     *
     * @param player
     *            the player to withdraw from
     */
    private static void clearGrantedFlight(final Player player)
    {
        try
        {
            if ((player.getGameMode() == GameMode.CREATIVE)
                || (player.getGameMode() == GameMode.SPECTATOR))
            {
                return;
            }
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        catch (final RuntimeException ignore) { /* clearing granted flight is best effort */ }
    }

    /**
     * Whether a location is inside the portal of a gate that is currently open.
     *
     * <p>Walks the open gates rather than every gate, and the containment test behind it is
     * a hash lookup, so this stays cheap on a path that runs whenever a player crosses a
     * block boundary.
     *
     * @param at
     *            the location to test
     * @return true if an open gate's portal covers that block
     */
    private static boolean isInsideOpenPortal(final Location at)
    {
        for (final Stargate gate : StargateManager.getOpenGates())
        {
            if (gate.isGatePortalOpen() && (gate.getGateWorld() != null) && gate.getGateWorld().equals(at.getWorld())
                && gate.isGatePortalBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Redraws open portals for a player who has just arrived somewhere.
     *
     * @param player
     *            the player to redraw for
     */
    static void refreshPortalVisualsFor(final Player player)
    {
        for (final long delay : ARRIVAL_REDRAW_TICKS)
        {
            try
            {
                WormholeXTreme.getScheduler().scheduleSyncDelayedTask(
                    WormholeXTreme.getThisPlugin(),
                    () -> StargateManager.refreshPortalVisuals(player),
                    delay);
            }
            catch (final RuntimeException ignore)
            {
                // No scheduler yet, during startup or in tests. Nothing is drawn at that
                // point either, so there is nothing to restore.
                return;
            }
        }
    }
}
