package com.wormhole_xtreme.wormhole;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

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
        PortalPresence.updatePortalFlightExemption(event.getPlayer(), event.getTo());

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
        if (PortalPresence.hasChangedChunk(event.getFrom(), event.getTo()))
        {
            // Crossing into a chunk the client has not held before means the client is
            // about to be sent that chunk's real contents, which wipes any portal drawn
            // over it. Redrawing on the crossing covers walking up to a gate from out of
            // range, and covers coming back to one after being away.
            PortalPresence.refreshPortalVisualsFor(event.getPlayer());
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
            PortalPresence.refreshPortalVisualsFor(event.getPlayer());
            // Arriving lands the traveller in the ring, where the client immediately starts
            // floating them on water the server does not have. Waiting for their first move
            // event to grant the exemption would be waiting until after the rise that gets
            // them kicked for it.
            PortalPresence.updatePortalFlightExemption(event.getPlayer(), event.getTo());
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
        PortalPresence.forgetFlight(event.getPlayer().getUniqueId());
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
        PortalPresence.refreshPortalVisualsFor(event.getPlayer());
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
        PortalPresence.refreshPortalVisualsFor(event.getPlayer());
        GatePreviews.forget(event.getPlayer().getUniqueId());
    }

    /**
     * Redraws open portals for a player who has just respawned.
     */
    @EventHandler
    public void onPlayerRespawn(final PlayerRespawnEvent event)
    {
        PortalPresence.refreshPortalVisualsFor(event.getPlayer());
    }
}

