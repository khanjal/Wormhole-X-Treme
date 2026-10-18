package com.wormhole_xtreme.wormhole;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.wormhole_xtreme.wormhole.command.Refresh;
import com.wormhole_xtreme.wormhole.model.GateViews;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorInteraction;
import com.wormhole_xtreme.wormhole.model.window.Windows;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;

/**
 * WormholeXtreme Player Listener.
 *
 * <p><b>On the {@code catch (RuntimeException ignore)} blocks in this class and in {@link GateWalkThrough}.</b> A gate
 * teleport is a sequence of independent effects on the player and whatever they are
 * riding — position, velocity, fall distance, air, passenger seating, chunk loading. Most
 * are cosmetic settling, and one of them failing is not a reason to abandon the rest and
 * leave the player half-moved. Those calls are individually guarded and deliberately
 * silent.
 *
 * <p>That is the only thing a silent guard may cover. Anything that changes plugin state —
 * a cooldown, an arrival marker, a gate registration — logs when it fails, because
 * swallowing those hides real bugs and, in the cooldown's case, is exploitable. Nothing
 * here catches {@link Throwable}: an {@link Error} is never something this plugin should
 * absorb.
 *
 * @author Ben Echols (Lologarithm)
 * @author Dean Bailey (alron)
 */
class WormholeXTremePlayerListener implements Listener
{
    /** Part of the debug line naming what a click was. */
    private static final String ACTION_TYPE = "\" Action Type: \"";

    

    /* (non-Javadoc)
     * @see org.bukkit.event.player.PlayerListener#onPlayerBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent)
     */
    @EventHandler
    public void onPlayerBucketEmpty(final PlayerBucketEmptyEvent event)
    {
        if ( !event.isCancelled())
        {
            final Stargate stargate = StargateManager.getGateFromBlock(event.getBlockClicked());
            if ((stargate != null) || StargateManager.isBlockInGate(event.getBlockClicked()))
            {
                event.setCancelled(true);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.player.PlayerListener#onPlayerBucketFill(org.bukkit.event.player.PlayerBucketFillEvent)
     */
    @EventHandler
    public void onPlayerBucketFill(final PlayerBucketFillEvent event)
    {
        if ( !event.isCancelled())
        {
            final Stargate stargate = StargateManager.getGateFromBlock(event.getBlockClicked());
            if ((stargate != null) || StargateManager.isBlockInGate(event.getBlockClicked()))
            {
                event.setCancelled(true);
            }
        }
    }

    /** How long a player's swing holds off the next iris redraw, in milliseconds. */
    private static final long IRIS_REDRAW_THROTTLE_MILLIS = 500L;

    /** When each player last had a drawn iris put back after swinging at one. */
    private static final Map<UUID, Long> recentIrisRedraws =
        new ConcurrentHashMap<>();

    /**
     * Puts a drawn iris back after somebody takes a swing at it.
     *
     * <p>Mining at a block the server does not have gets the client the truth about that cell,
     * which is air, and the iris comes off their screen in a hole. No event fires for the
     * break itself -- there is nothing there to break -- so the swing is what there is to
     * listen for.
     *
     * <p>Throttled, and asked only of players standing near a gate whose iris is drawn: an arm
     * swing is also every punch, every attack and every left click anybody makes.
     *
     * @param event
     *            the swing
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerAnimation(final PlayerAnimationEvent event)
    {
        final Player player = event.getPlayer();
        if (!StargateManager.nearDrawnIris(player.getLocation()))
        {
            return;
        }
        final long now = System.currentTimeMillis();
        final Long last = recentIrisRedraws.get(player.getUniqueId());
        if ((last != null) && ((now - last) < IRIS_REDRAW_THROTTLE_MILLIS))
        {
            return;
        }
        recentIrisRedraws.put(player.getUniqueId(), now);
        StargateManager.redrawPortalVisualsSoon(player);
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.player.PlayerListener#onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent)
     */
    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event)
    {
        if (event.getClickedBlock() == null)
        {
            logClick(event, "Caught and ignored Player: \"");
            return;
        }
        logClick(event, "Caught Player: \"");
        if (GateInteractionHandler.handlePlayerInteractEvent(event))
        {
            event.setCancelled(true);
            logClick(event, "Cancelled Player: \"");
            return;
        }
        // Mirrors after gates, and only if the gate handler did not claim the click. The two
        // never want the same block -- a dial sign is not a banner -- but asking in a fixed
        // order means that if one ever did, the older mechanic keeps the behaviour it has.
        if (MirrorInteraction.handle(event))
        {
            event.setCancelled(true);
            logClick(event, "Cancelled Player: \"");
        }
    }

    /**
     * Says what was clicked, if anybody is listening.
     *
     * <p>Built only when FINE is actually enabled. This line used to be assembled at the call
     * site -- five calls and nine joins, including {@code Block.toString()} and
     * {@code World.toString()} -- and handed to a method that then decided whether to print
     * it. {@code PlayerInteractEvent} fires for both buttons on both blocks and air, per
     * player, and left-clicking air repeats for as long as somebody holds the button down, so
     * on any server not logging at FINE all of that was built and discarded.
     *
     * @param event
     *            the click
     * @param opening
     *            how the line starts, which is what tells the three cases apart
     */
    private static void logClick(final PlayerInteractEvent event, final String opening)
    {
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        if ((plugin == null) || !plugin.isLoggable(Level.FINE))
        {
            return;
        }
        final Block clicked = event.getClickedBlock();
        if (clicked == null)
        {
            plugin.prettyLog(Level.FINE,
                opening + event.getPlayer().getName() + ACTION_TYPE + event.getAction() + "\"");
            return;
        }
        plugin.prettyLog(Level.FINE, opening + event.getPlayer().getName() + ACTION_TYPE
            + event.getAction() + "\" Event Block Type: \"" + clicked.getType()
            + "\" Event World: \"" + clicked.getWorld() + "\" Event Block: \"" + clicked + "\"");
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.player.PlayerListener#onPlayerMove(org.bukkit.event.player.PlayerMoveEvent)
     */
    @EventHandler
    public void onPlayerMove(final PlayerMoveEvent event)
    {
        // Before the cancel check, so a player held at the mouth of a gate is still let
        // out of the flight exemption when they step away.
        updatePortalFlightExemption(event.getPlayer(), event.getTo());

        if (GateWalkThrough.handlePlayerMoveEvent(event))
        {
            event.setCancelled(true);
            return;
        }
        // Rings are asked only once no gate has claimed this move, so gates keep priority
        // and the two can never both act on one step. Ring creation refuses any footprint
        // touching gate blocks, so in practice they never contend for the same block at all.
        RingArming.handleRingMoveEvent(event);
        // A window mirror's view depends on where the eye is, so it follows the step.
        Windows.moved(event.getPlayer(),
            event.getTo());
        // A shut iris over an open wormhole looks different from each side, so crossing a
        // gate's plane restacks it. Only on a change of block: a plane is crossed at one.
        if (GateWalkThrough.hasChangedBlockCoordinates(event.getFrom(), event.getTo()))
        {
            StargateManager.relayerFor(event.getPlayer(), event.getTo());
            // A preview redraws itself every five seconds, which cannot follow somebody walking
            // round one, so its stacking follows the step as a gate's does.
            GatePreviews.moved(event.getPlayer(), event.getTo());
        }
        if (hasChangedChunk(event.getFrom(), event.getTo()))
        {
            // Crossing into a chunk the client has not held before means the client is
            // about to be sent that chunk's real contents, which wipes any portal drawn
            // over it. Redrawing on the crossing covers walking up to a gate from out of
            // range, and covers coming back to one after being away.
            refreshPortalVisualsFor(event.getPlayer());
        }
    }

    /**
     * Whether a move crossed a chunk boundary.
     *
     * @param from
     *            where the player was
     * @param to
     *            where the player is
     * @return true if the two are in different chunks
     */
    private static boolean hasChangedChunk(final Location from, final Location to)
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
    private static void updatePortalFlightExemption(final Player player, final Location at)
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
    private static void refreshPortalVisualsFor(final Player player)
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

    /**
     * Redraws open portals for a player arriving by teleport.
     *
     * <p>This is the case behind "the water is gone at the other end": the destination gate
     * opened while the traveller was still standing at the source, far outside the range
     * the portal is drawn to, so they were never sent it in the first place.
     */
    @EventHandler
    public void onPlayerTeleport(final PlayerTeleportEvent event)
    {
        if (!event.isCancelled())
        {
            refreshPortalVisualsFor(event.getPlayer());
            // Arriving lands the traveller in the ring, where the client immediately starts
            // floating them on water the server does not have. Waiting for their first move
            // event to grant the exemption would be waiting until after the rise that gets
            // them kicked for it.
            updatePortalFlightExemption(event.getPlayer(), event.getTo());
        }
    }

    /**
     * Drops any portal flight exemption held by a player who has left.
     *
     * <p>Ids of players who never come back would otherwise sit in the set forever.
     */
    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event)
    {
        portalFlightGranted.remove(event.getPlayer().getUniqueId());
        GateWalkThrough.forget(event.getPlayer().getUniqueId());
        recentIrisRedraws.remove(event.getPlayer().getUniqueId());
        // A client that has gone takes its drawings with it, and the next one to log in on
        // that account gets fresh chunks anyway.
        StargateManager.forgetPortalVisuals(
            event.getPlayer().getUniqueId());
        // The Player-keyed state, which the id-keyed lines above are not. Nothing was
        // clearing it, so every person who had ever half-built or activated a gate stayed
        // in memory — with their entity, inventory and world — until the server stopped.
        StargateManager.forgetPlayer(event.getPlayer());
        StargateRestrictions.forgetPlayer(event.getPlayer());
        Refresh.removePendingRefresh(event.getPlayer());
        GatePreviews.forget(event.getPlayer().getUniqueId());
    }

    /**
     * Dials a build preview whose button was right-clicked.
     *
     * @param event
     *            the click
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(final PlayerInteractEntityEvent event)
    {
        // The off hand repeats the click; only the main hand's counts.
        if ((event.getHand() == EquipmentSlot.HAND)
            && GatePreviews.pressed(event.getPlayer(), event.getRightClicked()))
        {
            event.setCancelled(true);
        }
    }

    /**
     * Redraws open portals for a player who has just joined.
     */
    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event)
    {
        refreshPortalVisualsFor(event.getPlayer());
    }

    /**
     * Redraws open portals for a player who has changed world, and takes away the previews they
     * left behind.
     *
     * @param event
     *            the world change
     */
    @EventHandler
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event)
    {
        // Their client has the new world's chunks, so no gate's opening is cleared for them any more.
        GateViews.forgetViewer(event.getPlayer().getUniqueId());
        StargateManager.forgetSweptLayers(event.getPlayer().getUniqueId());
        refreshPortalVisualsFor(event.getPlayer());
        GatePreviews.forget(event.getPlayer().getUniqueId());
    }

    /**
     * Redraws open portals for a player who has just respawned.
     */
    @EventHandler
    public void onPlayerRespawn(final PlayerRespawnEvent event)
    {
        refreshPortalVisualsFor(event.getPlayer());
    }
}

