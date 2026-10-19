package com.wormhole_xtreme.wormhole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Minecart;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerMoveEvent;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.events.GateEvents;
import com.wormhole_xtreme.wormhole.events.StargateShutdownEvent;
import com.wormhole_xtreme.wormhole.model.HorizonRipple;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.EntityUtils;
import com.wormhole_xtreme.wormhole.utils.Messages;
import com.wormhole_xtreme.wormhole.utils.PassengerReattach;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * What happens when a player walks into a gate: whether the gate lets them in, and if so the trip.
 *
 * <p>Split out of {@link WormholeXTremePlayerListener}, which keeps the event handlers and calls
 * {@link #handlePlayerMoveEvent} from {@code onPlayerMove}. The note there on the silent
 * {@code catch (RuntimeException ignore)} blocks applies to the ones here: each guards one cosmetic
 * effect of the teleport, never a change to plugin state.
 */
final class GateWalkThrough
{
    private GateWalkThrough() {}

    /**
     * The one-way-gate refusal a player was most recently told about, and when.
     *
     * <p>Holding forward against a gate that cannot be entered from outside cancels the
     * move every tick, and each cancelled move returns the player to the exact spot they
     * tried to leave -- so the next tick looks identical to this one, and without this a
     * player who simply keeps walking gets the same chat line every tick until they let go.
     * Remembered per player rather than gated on movement, since a cancelled move by
     * definition never goes anywhere different to notice.
     */
    private static final Map<UUID, RecentGateRefusal> recentGateRefusals =
        new ConcurrentHashMap<>();

    /** How long a refusal stays remembered before the same gate is worth mentioning again. */
    private static final long GATE_REFUSAL_REMINDER_MILLIS = 2000L;

    /** Which gate a player was told they could not enter, and when. */
    private static final class RecentGateRefusal
    {
        private final String gateName;
        private final long atMillis;

        RecentGateRefusal(final String gateName, final long atMillis)
        {
            this.gateName = gateName;
            this.atMillis = atMillis;
        }
    }

    /** Forgets what a player who has left was last told. */
    static void forget(final UUID id)
    {
        recentGateRefusals.remove(id);
    }

    static boolean hasChangedBlockCoordinates(final Location fromLoc, final Location toLoc) {
        return fromLoc.getBlockX() != toLoc.getBlockX()
                || fromLoc.getBlockY() != toLoc.getBlockY()
                || fromLoc.getBlockZ() != toLoc.getBlockZ();
    }

    /**
     * Returns true if {@code ridden} is a living mount this listener is responsible for.
     *
     * <p>Note that {@code instanceof Vehicle} does not make this distinction: in Bukkit
     * {@code Pig} and {@code AbstractHorse} — so horses, camels, donkeys, mules and
     * llamas, the whole point of the feature — all extend {@link Vehicle}. What actually
     * separates the two cases is which event fires. Minecarts and boats raise
     * {@code VehicleMoveEvent} and are handled by {@link WormholeXTremeVehicleListener};
     * living mounts never raise it, so they can only be caught here off the rider's move.
     * This predicate deliberately mirrors that listener's own filter so the two partition
     * the space with no gap and no double-handling.
     *
     * @param ridden
     *            the entity the player is riding, may be null
     * @return true if this listener should handle it as a mount
     */
    private static boolean isLivingMount(final Entity ridden)
    {
        return ridden != null && !WormholeXTremeVehicleListener.handlesMovementOf(ridden);
    }

    /**
     * Finds a portal block of an active gate that {@code mount} is standing in.
     *
     * <p>Scans the blocks the mount's bounding box overlaps rather than a fixed cube
     * around it, so a wide or tall mount is covered exactly and a small one costs
     * only the handful of lookups it actually occupies.
     *
     * <p>Only portal blocks count. Matching any gate block would let a structure
     * block win the search, and the caller requires a portal block — so the mount
     * would be silently ignored even with its legs in the open wormhole.
     *
     * @param mount
     *            the entity being ridden
     * @return a portal block of an active gate, or null if the mount is not in one
     */
    // Never null on a server; the box check is for mock mounts that stub none.
    @SuppressWarnings("java:S2589")
    private static Block findActiveGatePortalBlockAtMount(final Entity mount)
    {
        final Location ml = mount.getLocation();
        if (ml.getWorld() == null)
        {
            return null;
        }
        final World world = ml.getWorld();

        // Default to the mount's own block and the one above it, covering the common
        // case when no bounding box is available.
        int minX = ml.getBlockX();
        int maxX = minX;
        int minY = ml.getBlockY();
        int maxY = minY + 1;
        int minZ = ml.getBlockZ();
        int maxZ = minZ;
        final BoundingBox box = mount.getBoundingBox();
        if (box != null)
        {
            minX = (int) Math.floor(box.getMinX());
            maxX = (int) Math.floor(box.getMaxX());
            minY = (int) Math.floor(box.getMinY());
            maxY = (int) Math.floor(box.getMaxY());
            minZ = (int) Math.floor(box.getMinZ());
            maxZ = (int) Math.floor(box.getMaxZ());
        }

        for (int bx = minX; bx <= maxX; bx++)
        {
            for (int by = minY; by <= maxY; by++)
            {
                for (int bz = minZ; bz <= maxZ; bz++)
                {
                    final Block b = world.getBlockAt(bx, by, bz);
                    final Stargate s = StargateManager.getGateFromBlock(b);
                    if (s != null && s.isGatePortalOpen() && StargateManager.isPortalBlock(b))
                    {
                        return b;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Teleports a player through a gate on their own, with no mount involved.
     *
     * @param safeTarget
     *            the vetted arrival location
     * @return false if the teleport was refused and they are still here
     */
    private static boolean teleportPlayerAlone(final Player player, final Location safeTarget)
    {
        // Safety net: ensure destination chunk is loaded even if it unloaded since dial time.
        try
        {
            WorldUtils.forceLoadDestinationChunks(safeTarget);
        }
        catch (final RuntimeException ignore) { /* best effort */ }
        if (!player.teleport(safeTarget))
        {
            return false;
        }
        try
        {
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0);
        }
        catch (final RuntimeException ignore) { /* settling the arrival is cosmetic */ }
        return true;
    }

    /**
     * Refuses a player's step into a gate they may not enter, and tells them why.
     *
     * <p>Cancelling the move event is the entire job. Bukkit returns the player to
     * {@code event.getFrom()} and the client corrects itself smoothly, which is the
     * idiomatic way to refuse movement.
     *
     * <p>This used to do three things at once: rewrite the event's from and to, fire a
     * {@code player.teleport()}, and then cancel — three mechanisms competing within one
     * tick, which rubber-banded the player. It also teleported them to
     * {@code getGatePlayerTeleportLocation()}, the gate's <em>arrival</em> point, so
     * refusing entry actually pulled them further into the ring rather than holding them
     * out of it. It set no-damage ticks too, on a path where nothing deals damage.
     *
     * @param player
     *            the player to hold back
     * @param stargate
     *            the gate they may not enter
     * @return true, so the caller cancels the move event
     */
    private static boolean refuseGateEntry(final Player player, final Stargate stargate)
    {
        return refuseWithReminder(player, stargate,
            ConfigManager.MessageStrings.PLAYER_RECENT_ARRIVAL.toString());
    }

    /**
     * Refuses a step into a gate, saying why no more often than it is worth saying.
     *
     * @param player
     *            the player to hold back
     * @param stargate
     *            the gate they may not enter
     * @param message
     *            what to tell them
     * @return true, so the caller cancels the move event
     */
    private static boolean refuseWithReminder(final Player player, final Stargate stargate,
        final String message)
    {
        if (remindOfRefusal(player, stargate))
        {
            player.sendMessage(message);
        }
        return true;
    }

    /**
     * Notes a refused step and says whether to tell the player again, so a line is built only when sent.
     *
     * <p>The move is cancelled either way: only the chat line is skipped, and only when it would just be
     * saying the same thing again a moment later.
     *
     * @return true if the player should be told
     */
    private static boolean remindOfRefusal(final Player player, final Stargate stargate)
    {
        final UUID id = player.getUniqueId();
        final String gateName = stargate.getGateName();
        final long now = System.currentTimeMillis();
        final RecentGateRefusal last = recentGateRefusals.get(id);
        recentGateRefusals.put(id, new RecentGateRefusal(gateName, now));
        return (last == null) || !last.gateName.equals(gateName)
            || ((now - last.atMillis) > GATE_REFUSAL_REMINDER_MILLIS);
    }

    /**
     * Holds a player out of a gate whose own iris is shut.
     *
     * <p>Nothing used to need this: the iris was real blocks, and a wall does not have to be
     * told to keep somebody out. A vertical gate's iris is a drawing now, and a drawing is
     * only as solid as the client that believes in it -- which is enough for an honest player
     * walking into it, and nothing at all for one who is lagging, riding something, or running
     * a client that does not draw the iris it was sent.
     *
     * <p>A horizontal gate's iris is still real blocks, so this leaves those alone: the wall
     * refuses them, the same as it always did.
     *
     * @param event
     *            the move being considered
     * @param player
     *            the player walking in
     * @param stargate
     *            the gate they are stepping into
     * @return true if the move should be cancelled
     */
    private static boolean refusedByOwnIris(final PlayerMoveEvent event, final Player player,
        final Stargate stargate)
    {
        if (!stargate.isGateIrisActive() || !stargate.isGateIrisDrawn())
        {
            return false;
        }
        // Somebody already standing in the opening -- the iris shut around them, or they got
        // in before it did -- is left free to walk out. Refusing their every move is what
        // would trap them there, which is the mistake holdBackCancelledTraveller exists to
        // remember.
        if (!holdBackCancelledTraveller(event, stargate))
        {
            return false;
        }
        if (remindOfRefusal(player, stargate))
        {
            player.sendMessage(ChatText.error(Messages.get(player, "gate.walk.iris-closed")));
        }
        return true;
    }

    /**
     * Holds back a player whose trip a listener cancelled, without trapping them.
     *
     * <p>Cancelling a move event returns the player to where the move started, so what it
     * does depends entirely on where that was. Someone walking into the gate is returned to
     * the block outside it, which is the intended effect. Someone already standing in the
     * portal is returned into the portal — and since they are still in it on their next
     * move, that move is cancelled too, and every one after it. They cannot walk out, and
     * the only way the server ends it is by dropping them.
     *
     * <p>That is not hypothetical: the same shape of mistake, in the check that keeps
     * players out of the exit end of a wormhole, did exactly that.
     *
     * <p>So a cancelled trip stops the travel and nothing else. Only a player arriving from
     * outside is physically held; one already inside is left free to walk away, having
     * simply not been sent anywhere.
     *
     * @param event
     *            the move being considered
     * @param stargate
     *            the gate the player is entering
     * @return true if the move should be cancelled
     */
    private static boolean holdBackCancelledTraveller(final PlayerMoveEvent event, final Stargate stargate)
    {
        final Location from = event.getFrom();
        // Somebody already in the portal does not travel: refusing this move, and every
        // following one, is what would trap them.
        return !stargate.isGatePortalBlockAt(from.getBlockX(), from.getBlockY(), from.getBlockZ());
    }


    /**
     * Handle player move event.
     *
     * @return true, if successful
     */
    static boolean handlePlayerMoveEvent(final PlayerMoveEvent event)
    {
        if (!hasChangedBlockCoordinates(event.getFrom(), event.getTo()))
        {
            return false;
        }
        final Player player = event.getPlayer();
        logCrossing(event, player);

        final Location toLocFinal = event.getTo();
        Block gateBlockFinal = toLocFinal.getWorld().getBlockAt(toLocFinal.getBlockX(), toLocFinal.getBlockY(), toLocFinal.getBlockZ());
        Stargate stargate = StargateManager.getGateFromBlock(gateBlockFinal);

        // A rider's own block is not a reliable trigger: a camel is tall enough that the
        // rider clears the portal entirely while the camel stands in it. When the player's
        // block is not a gate, look for one under their mount instead so the mount-first
        // teleport still fires.
        if (stargate == null)
        {
            final Block mountBlock = gatePortalBlockUnderMount(player);
            if (mountBlock != null)
            {
                gateBlockFinal = mountBlock;
                stargate = StargateManager.getGateFromBlock(mountBlock);
            }
        }

        // Asked before the gate is asked to be open, because a shut iris keeps people out of
        // an idle gate too -- and an idle gate is exactly what the checks below walk away
        // from.
        if ((stargate != null) && StargateManager.isPortalBlock(gateBlockFinal)
            && refusedByOwnIris(event, player, stargate))
        {
            return true;
        }

        // Everything past here is about what to do about the gate the player stepped into,
        // and each answer is its own method. This reads as the order they are asked in.
        if ((stargate == null) || !stargate.isGatePortalOpen() || !StargateManager.isPortalBlock(gateBlockFinal))
        {
            if (stargate != null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                    "Player entered a gate that is not open, or a block of it that is not the portal.");
            }
            return false;
        }

        // A gate holding no target of its own is the far end of somebody else's wormhole, or
        // one that was lit and walked away from. Either way there is nowhere to send anyone
        // from here.
        if (stargate.getGateTarget() == null)
        {
            return handleMoveAtArrivalGate(event, player, stargate);
        }

        return travelThroughGate(event, player, stargate, gateBlockFinal);
    }

    /**
     * Says what the player just walked between, if anybody is listening.
     *
     * <p>Every player crossing a block boundary reaches here, so the line is built only when
     * it would actually be printed. It used to call {@code Player.toString()} and two extra
     * {@code getBlockAt} lookups on every crossing and throw all of it away.
     *
     * @param player
     *            who moved
     */
    private static void logCrossing(final PlayerMoveEvent event, final Player player)
    {
        // FINER, not FINE: one line per block walked would bury every other FINE line.
        if (!WormholeXTreme.getThisPlugin().isLoggable(Level.FINER))
        {
            return;
        }
        try
        {
            final Location from = event.getFrom();
            final Location to = event.getTo();
            final Block fromBlock = from.getWorld().getBlockAt(from.getBlockX(), from.getBlockY(), from.getBlockZ());
            final Block toBlock = to.getWorld().getBlockAt(to.getBlockX(), to.getBlockY(), to.getBlockZ());
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINER, "PlayerMove: " + player.getName()
                + " from=" + fromBlock.getType() + " to=" + toBlock.getType() + " y=" + to.getY());
        }
        // Diagnostics only, and on the move path, so never let it disturb the event.
        catch (final RuntimeException ignore) { /* best effort */ }
    }

    /**
     * The gate portal block under whatever the player is riding, if any.
     *
     * @param player
     *            the player, who may be riding something
     * @return the portal block their mount is standing in, or null
     */
    private static Block gatePortalBlockUnderMount(final Player player)
    {
        final Entity ridden = player.getVehicle();
        if (!isLivingMount(ridden))
        {
            return null;
        }
        final Block mountBlock = findActiveGatePortalBlockAtMount(ridden);
        if (mountBlock != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Detected mount-based gate entry for player=" + player.getName() + " via mount=" + ridden + " at block=" + mountBlock.getLocation());
        }
        return mountBlock;
    }

    /**
     * What a move means at a gate that holds no target of its own.
     *
     * <p>Two cases share this. A gate something is dialling <em>into</em> is an exit, and
     * walking in from outside is refused so a wormhole cannot be used as a door in both
     * directions. A gate activated but never dialled has nowhere to send anybody, so its
     * ring is just a ring and a player may walk through it.
     *
     * @param player
     *            the player moving
     * @param stargate
     *            the gate they are standing in
     * @return true if the move should be cancelled
     */
    private static boolean handleMoveAtArrivalGate(final PlayerMoveEvent event, final Player player,
                                                   final Stargate stargate)
    {
        // Gate is active but has no local target: check whether it's the destination of an
        // active incoming connection. Only an open gate can be dialled into this one, so the
        // question is asked of those rather than of a freshly copied and sorted list of every
        // gate on the server -- which is what this did, on every block boundary somebody
        // crossed while standing in an arrival gate.
        boolean incomingActive = false;
        try
        {
            incomingActive = StargateManager.hasIncomingConnection(stargate, null);
        }
        catch (final RuntimeException ignore) { /* treat an unreadable gate as not incoming */ }

        if (incomingActive)
        {
            // Refusing a move means cancelling it, which holds the player where
            // they already are. That is the right answer for someone stepping in
            // from outside, and a trap for someone already standing inside: the
            // traveller who just came out of this wormhole gets every move
            // cancelled, cannot walk clear of the ring, and is told they may not
            // enter an incoming wormhole once per move until the client gives up
            // and drops the connection.
            //
            // So only hold back the ones on their way in. Anyone already in the
            // portal is on their way out, which is exactly what should happen.
            final Location fromLoc = event.getFrom();
            if (!stargate.isGatePortalBlockAt(fromLoc.getBlockX(), fromLoc.getBlockY(), fromLoc.getBlockZ()))
            {
                return refuseGateEntry(player, stargate);
            }
            return false;
        }
        // Gate is active but has neither an outgoing target nor an incoming
        // wormhole — an activated-but-undialed gate. There is nowhere to send
        // the player, so let them walk through the empty ring untouched.
        // Everything below this point dereferences getGateTarget().
        return false;
    }

    /**
     * Takes a player through a wormhole, or explains why they are not going.
     *
     * <p>Reads as the order the questions are asked in: is the far end in a world they may
     * reach, may they use this gate, have they only just come out of it, are they on cooldown,
     * can they pay, is the far end sealed. Only once all of that has passed does anything
     * move, and even then a listener may still say no.
     *
     * @param event
     *            the move that carried them in
     * @param player
     *            the traveller
     * @param stargate
     *            the gate they entered, which holds the target
     * @param gateBlockFinal
     *            the portal block they are standing in
     * @return true if the move should be cancelled
     */
    private static boolean travelThroughGate(final PlayerMoveEvent event, final Player player,
                                             final Stargate stargate, final Block gateBlockFinal)
    {
        // Suppress solo teleport if this player was just ejected by a vehicle that VehicleListener
        // already teleported — the player must exit riding the vehicle, not as a solo traveller.
        if (WormholeXTremeVehicleListener.isPlayerRecentlyTeleportedByVehicle(player.getUniqueId()))
        {
            return false;
        }
        logTravelAttempt(stargate);

        // Refill the player's air while they stand in the portal so a water-material
        // gate does not drown them. Cosmetic, so a failure is not worth reporting.
        try
        {
            player.setRemainingAir(player.getMaximumAir());
        }
        catch (final RuntimeException ignore) { /* best effort */ }

        final Location target = stargate.getGateTarget().getGatePlayerTeleportLocation();
        // First, as the cart path asks it: a trip that can never happen is refused for that
        // reason, not for an iris, a cooldown or a fare that would not have mattered.
        if (crossesWorldsRefused(gateBlockFinal, target))
        {
            return refuseCrossWorld(event, player, stargate);
        }
        if (refusedBeforeTravel(player, stargate))
        {
            return false;
        }
        // Its own answer, because being turned away at the door is a cancelled move rather
        // than a permitted one that went nowhere.
        if (StargateRestrictions.isPlayerRecentArrivalFrom(player, stargate))
        {
            return refuseGateEntry(player, stargate);
        }

        // Affordability is checked here so the player is turned away for the right reason
        // and in the right order, but the money does not move until the trip is certain.
        final double pendingUseCost = GateFare.affordable(player);
        if (pendingUseCost < 0)
        {
            return false;
        }

        if (stargate.getGateTarget().isGateIrisActive())
        {
            return bounceOffRemoteIris(event, player, stargate);
        }

        // A far gate with no arrival point has nowhere to put anybody, and teleport(null) throws.
        if (target == null)
        {
            return false;
        }
        final Location safeTarget = WorldUtils.findSafePlayerLocation(target);

        // Every check this plugin makes has passed and nothing has moved yet, which is
        // the only honest point to let another plugin object.
        if (!GateEvents.firePlayerTravel(
                stargate, player, stargate.getGateTarget(), safeTarget))
        {
            return holdBackCancelledTraveller(event, stargate);
        }

        return performGateTeleport(event, player, stargate, target, safeTarget, pendingUseCost);
    }

    /**
     * Notes who walked into what, for anyone reading the log after the fact.
     *
     * @param stargate
     *            the gate they entered
     */
    private static void logTravelAttempt(final Stargate stargate)
    {
        final String gatenetwork = (stargate.getGateNetwork() != null)
            ? stargate.getGateNetwork().getNetworkName() : "Public";
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Player in gate:" + stargate.getGateName()
            + " gate Active: " + stargate.isGateActive()
            + " Target Gate: " + stargate.getGateTarget().getGateName()
            + " Network: " + gatenetwork);
    }

    /**
     * Whether this player may not use this gate right now, having been told why.
     *
     * <p>The permission asked for depends on how the gate is dialled: a sign-powered gate
     * belongs to whoever may use signs, anything else to whoever may use a dialler.
     *
     * @param player
     *            the traveller
     * @param stargate
     *            the gate they entered
     * @return true if they were refused and told so
     */
    private static boolean refusedBeforeTravel(final Player player, final Stargate stargate)
    {
        if (ConfigManager.getWormholeUseIsTeleport()
            && ((stargate.isGateSignPowered() && !WXPermissions.checkWXPermissions(player, stargate, PermissionType.SIGN))
                || (!stargate.isGateSignPowered() && !WXPermissions.checkWXPermissions(player, stargate, PermissionType.DIALER))))
        {
            player.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return true;
        }
        if (ConfigManager.isUseCooldownEnabled() && StargateRestrictions.isPlayerUseCooldown(player))
        {
            player.sendMessage(StargateRestrictions.cooldownRefusal(player));
            return true;
        }
        // The cooldown is not spent here. It is set once the traveller has actually gone,
        // further down: spending it at the check charged a player for a trip that had not
        // happened yet and might still not.
        return false;
    }

    /**
     * Puts a traveller back where they started, because the far end is shut.
     *
     * <p>An iris is the one thing a gate owner has to keep somebody out, so a closed one has
     * to stop the trip rather than let it through.
     *
     * @param event
     *            the move that carried them in
     * @param player
     *            the traveller
     * @param stargate
     *            the gate they entered
     * @return true, the move is cancelled
     */
    private static boolean bounceOffRemoteIris(final PlayerMoveEvent event, final Player player,
                                               final Stargate stargate)
    {
        player.sendMessage(ConfigManager.MessageStrings.REMOTE_IRIS_CLOSED.toString());
        player.setNoDamageTicks(5);
        event.setFrom(stargate.getGatePlayerTeleportLocation());
        event.setTo(stargate.getGatePlayerTeleportLocation());
        player.teleport(stargate.getGatePlayerTeleportLocation());
        return true;
    }

    /**
     * Whether this trip crosses worlds on a server that does not allow it.
     *
     * @param gateBlockFinal
     *            the portal block they are standing in
     * @param target
     *            where they would arrive, null if nowhere
     * @return true if the server forbids it
     */
    private static boolean crossesWorldsRefused(final Block gateBlockFinal, final Location target)
    {
        return StargateRestrictions.isCrossWorldRefused(gateBlockFinal.getWorld(), target);
    }

    /**
     * Refuses a trip to another world, saying so once per approach rather than at every block.
     *
     * <p>Someone stepping in is held out, as at any refused gate. Someone already standing in the
     * portal is let walk on: cancelling their every move would trap them in it.
     *
     * @param event
     *            the move that carried them in
     * @param player
     *            the traveller
     * @param stargate
     *            the gate they entered
     * @return true if the move should be cancelled
     */
    private static boolean refuseCrossWorld(final PlayerMoveEvent event, final Player player,
                                            final Stargate stargate)
    {
        refuseWithReminder(player, stargate, ConfigManager.MessageStrings.CROSS_WORLD_DISABLED.toString());
        final Location from = event.getFrom();
        return !stargate.isGatePortalBlockAt(from.getBlockX(), from.getBlockY(), from.getBlockZ());
    }

    /**
     * Moves the traveller, and records what follows from their having gone.
     *
     * <p>Reached only once travel is settled: every check has passed and no listener objected.
     * The fare is taken only once somebody has actually moved. What is left is the awkward
     * part — a rider has to travel with whatever is carrying them, and the client has to be
     * told about both in an order it will accept.
     *
     * @param event
     *            the move that carried them in
     * @param player
     *            the traveller
     * @param stargate
     *            the gate they entered
     * @param target
     *            the far gate's arrival point, before the safe-location search
     * @param safeTarget
     *            where they will actually land
     * @param fare
     *            what the trip costs, 0 for nothing
     * @return true if the move should be cancelled
     */
    private static boolean performGateTeleport(final PlayerMoveEvent event, final Player player,
                                               final Stargate stargate, final Location target,
                                               final Location safeTarget, final double fare)
    {
        logTeleportTarget(player, stargate, target);
        player.setNoDamageTicks(5);
        // Captured before any event or teleport manipulation moves it.
        final Location playerCurrentLoc = event.getFrom().clone();
        final Location playerHeadingTo = event.getTo().clone();
        // Whatever the player is riding: boat, horse, camel, pig, strider. Minecarts
        // are the one exception -- they raise VehicleMoveEvent, so the vehicle listener
        // owns them and teleports them in place with passenger state preserved.
        final Entity ridden = player.getVehicle();
        if (ridden instanceof Minecart)
        {
            // Not charged here: the cart has not moved yet, and may not. Its listener charges.
            return false;
        }
        // Read while the player still stands at the origin; the event changes below move it.
        final List<Entity> pets = PetEscort.gather(player);
        // For every other flow, mark the event position to the safe target and continue.
        event.setFrom(safeTarget);
        event.setTo(safeTarget);
        final boolean vehiclePathUsed = ridden != null;
        boolean moved = false;
        try
        {
            moved = vehiclePathUsed
                ? sendRiderWithMount(event, player, stargate, safeTarget, ridden)
                : teleportPlayerAlone(player, safeTarget);
        }
        catch (final Exception e)
        {
            if (WormholeXTreme.getThisPlugin() != null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Exception while teleporting " + player.getName()
                    + " to " + (target == null ? "null" : target.toString()), e);
            }
        }

        if (!moved)
        {
            // Nobody went anywhere, so nothing is charged, marked or logged as a trip.
            event.setFrom(playerCurrentLoc);
            event.setTo(playerHeadingTo);
            return holdBackCancelledTraveller(event, stargate);
        }
        GateFare.charge(player, fare);
        PetEscort.follow(pets, player);
        markTripTaken(player, stargate);
        HorizonRipple.crossed(stargate, stargate.getGateTarget());
        GateWelcome.greet(player, stargate.getGateTarget());
        scheduleArrivalSettle(player, target, vehiclePathUsed);

        if (target != stargate.getGatePlayerTeleportLocation())
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, player.getName() + " used wormhole: "
                + stargate.getGateName() + " to go to: " + stargate.getGateTarget().getGateName());
        }
        if (ConfigManager.getTimeoutShutdown() == 0)
        {
            stargate.shutdownStargate(true, StargateShutdownEvent.Reason.TIMEOUT);
        }
        return true;
    }

    /** Says where the traveller is going, or why the destination looks wrong. */
    private static void logTeleportTarget(final Player player, final Stargate stargate, final Location target)
    {
        if (WormholeXTreme.getThisPlugin() == null)
        {
            return;
        }
        final String destination = stargate.getGateTarget().getGateName();
        if (target == null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Teleport target is null for gate: " + destination);
        }
        else if (target.getWorld() == null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Teleport target world is null for gate: " + destination + " loc: " + target.toString());
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Teleporting " + player.getName() + " to " + destination + " @ " + target.toString());
        }
    }

    /**
     * Sends a mounted traveller through with whatever is carrying them.
     *
     * @return true if the mount moved and its riders were sent after it; false if it would
     *         not move, and everyone is still aboard at this end
     */
    private static boolean sendRiderWithMount(final PlayerMoveEvent event, final Player player,
                                              final Stargate stargate, final Location safeTarget,
                                              final Entity ridden)
    {
        final BlockFace exitFacing = stargate.getGateTarget().getGateFacing();
        final Location riddenTarget = WormholeXTremeVehicleListener.forwardAndUp(safeTarget, exitFacing, 1.0, 1.0);
        // forwardAndUp hands a null straight back; the caller has already refused one.
        if (riddenTarget == null)
        {
            return false;
        }
        final Vector exitVelocity = aimMountAtExit(riddenTarget, exitFacing, ridden);

        // Safety net: ensure destination chunk is loaded even if it unloaded since dial time.
        try
        {
            WorldUtils.forceLoadDestinationChunks(riddenTarget);
        }
        catch (final RuntimeException ignore) { /* best effort */ }
        // Mark before the teleport so a VehicleMoveEvent in the same tick is
        // suppressed and does not double-process this entry, zeroing the exit velocity.
        try
        {
            WormholeXTremeVehicleListener.markVehicleRecentlyTeleported(ridden.getUniqueId());
        }
        catch (final RuntimeException ignore) { /* best effort */ }

        final List<Entity> parents = new ArrayList<>();
        final List<Entity> children = new ArrayList<>();
        EntityUtils.collectPassengerPairs(ridden, parents, children);
        // The one rider known for certain, even if the mount's own list has not caught up.
        if (!children.contains(player))
        {
            parents.add(ridden);
            children.add(player);
        }
        boolean moved;
        try
        {
            moved = RiddenTeleport.move(ridden, riddenTarget, parents, children);
        }
        catch (final RuntimeException tt)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Failed to teleport what " + player.getName() + " was riding", tt);
            moved = false;
        }
        if (!moved)
        {
            // Not split from the mount: sending the player on alone would leave it here.
            player.sendMessage(ChatText.error(Messages.get(player, "gate.walk.ride-failed")));
            return false;
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "PlayerTeleport: teleported "
            + ridden.getType().name() + " " + ridden.getUniqueId() + " for player " + player.getName());
        // The player is at the far end now, so a cancelled move must hold them there.
        event.setFrom(riddenTarget);
        event.setTo(riddenTarget);
        // Five ticks, as for a cart: the riders were teleported, and a client withholds the
        // seat packet until it has acknowledged that.
        PassengerReattach.schedule(ridden, parents, children, exitVelocity, 5L);
        return true;
    }

    /**
     * Turns the mount's arrival point to face the way it is travelling, so the client does
     * not render it spinning to its new yaw after it lands.
     *
     * @return the exit velocity, or null if it could not be worked out
     */
    private static Vector aimMountAtExit(final Location riddenTarget, final BlockFace exitFacing, final Entity ridden)
    {
        try
        {
            final Vector exitVelocity =
                WormholeXTremeVehicleListener.computeExitVelocity(exitFacing, ridden.getVelocity(), 5.0);
            if (riddenTarget != null)
            {
                final double dx = exitVelocity.getX();
                final double dz = exitVelocity.getZ();
                riddenTarget.setYaw((Math.abs(dx) > 0.0001 || Math.abs(dz) > 0.0001)
                    ? (float) Math.toDegrees(Math.atan2( -dx, dz))
                    : WorldUtils.getDegreesFromBlockFace(exitFacing));
                riddenTarget.setPitch(0f);
            }
            return exitVelocity;
        }
        catch (final RuntimeException ignore) { /* exit facing is cosmetic */ }
        return null;
    }

    /** Starts the use cooldown and marks the arrival, so they cannot walk straight back in. */
    private static void markTripTaken(final Player player, final Stargate stargate)
    {
        try
        {
            StargateRestrictions.addPlayerUseCooldown(player);
        }
        catch (final RuntimeException e)
        {
            // Not fatal to the teleport that already happened, but a silently skipped
            // cooldown lets a player re-enter immediately, so say so.
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Failed to apply use cooldown for " + player.getName(), e);
        }
        try
        {
            if (stargate.getGateTarget() != null)
            {
                StargateRestrictions.addPlayerRecentArrival(player, stargate.getGateTarget());
            }
        }
        catch (final RuntimeException e)
        {
            // Without this marker the player can walk straight back into the gate they
            // just arrived from, so a failure is worth a line in the log.
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Failed to mark recent arrival for " + player.getName(), e);
        }
    }

    /**
     * Settles the traveller a tick after arrival. The position teleport is skipped on the
     * vehicle path, where the client is repositioned by addPassenger instead.
     */
    private static void scheduleArrivalSettle(final Player player, final Location target, final boolean skipTeleport)
    {
        try
        {
            Bukkit.getScheduler().runTaskLater(WormholeXTreme.getThisPlugin(), () ->
            {
                if ((player == null) || (target == null))
                {
                    return;
                }
                // Each call is independently best-effort: none of them failing is
                // worth aborting the others.
                try
                {
                    player.setVelocity(new Vector(0, 0, 0));
                }
                catch (final RuntimeException ignore) { /* best effort */ }
                try
                {
                    player.setFallDistance(0);
                }
                catch (final RuntimeException ignore) { /* best effort */ }
                if (!skipTeleport)
                {
                    try
                    {
                        player.teleport(target);
                    }
                    catch (final RuntimeException ignore) { /* best effort */ }
                }
                // A moment of water, as though they had just surfaced out of the event
                // horizon. Sent here rather than at teleport time so it lands after the
                // client has been put where it is going.
                StargateManager.splashArrival(player);
            }, 1L);
        }
        catch (final RuntimeException ignore) { /* the arrival already happened; only the follow-up tick is lost */ }
    }
}
