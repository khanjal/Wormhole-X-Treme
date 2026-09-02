package com.wormhole_xtreme.wormhole;

import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;
import org.bukkit.Bukkit;
import org.bukkit.event.block.Action;
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

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * WormholeXtreme Player Listener.
 *
 * <p><b>On the {@code catch (RuntimeException ignore)} blocks in this class.</b> A gate
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
    

    private static boolean hasChangedBlockCoordinates(final Location fromLoc, final Location toLoc) {
        if (fromLoc.getBlockX() == toLoc.getBlockX()
                && fromLoc.getBlockY() == toLoc.getBlockY()
                && fromLoc.getBlockZ() == toLoc.getBlockZ()) {
            return false;
        }
        return true;
    }

    private static Location findSafePlayerLocation(final Location preferred)
    {
        if (preferred == null || preferred.getWorld() == null)
        {
            return preferred;
        }
        final org.bukkit.World w = preferred.getWorld();
        final int x = preferred.getBlockX();
        final int z = preferred.getBlockZ();
        final int baseY = preferred.getBlockY();

        // Prefer the exact stored location if it is safe, then search upward, then down.
        for (int dy = 0; dy <= 3; dy++)
        {
            if (isStandableAt(w, x, baseY + dy, z))
            {
                return new Location(w, x + 0.5, baseY + dy, z + 0.5, preferred.getYaw(), preferred.getPitch());
            }
        }

        for (int dy = 1; dy <= 3; dy++)
        {
            final int y = baseY - dy;
            if (y < w.getMinHeight())
            {
                break;
            }
            if (isStandableAt(w, x, y, z))
            {
                return new Location(w, x + 0.5, y, z + 0.5, preferred.getYaw(), preferred.getPitch());
            }
        }

        // Fallback to the original preferred location
        return preferred.clone();
    }

    /**
     * Checks whether a player can stand at the given block: head and feet clear, solid
     * ground underneath.
     *
     * <p>The blocks are null-checked rather than wrapped in a catch. A world can return
     * null for an unloaded or out-of-range column, and "no block there" is an ordinary
     * answer meaning not standable — not an error worth swallowing.
     *
     * @param w
     *            the world
     * @param x
     *            block x
     * @param y
     *            block y of the player's feet
     * @param z
     *            block z
     * @return true if a player can stand there
     */
    private static boolean isStandableAt(final org.bukkit.World w, final int x, final int y, final int z)
    {
        final org.bukkit.block.Block feet = w.getBlockAt(x, y, z);
        final org.bukkit.block.Block head = w.getBlockAt(x, y + 1, z);
        final org.bukkit.block.Block below = w.getBlockAt(x, y - 1, z);
        if (feet == null || head == null || below == null)
        {
            return false;
        }
        return feet.isPassable() && head.isPassable() && !below.isPassable();
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
    private static Block findActiveGatePortalBlockAtMount(final Entity mount)
    {
        final Location ml = mount.getLocation();
        if (ml == null || ml.getWorld() == null)
        {
            return null;
        }
        final org.bukkit.World world = ml.getWorld();

        // Default to the mount's own block and the one above it, covering the common
        // case when no bounding box is available.
        int minX = ml.getBlockX(), maxX = minX;
        int minY = ml.getBlockY(), maxY = minY + 1;
        int minZ = ml.getBlockZ(), maxZ = minZ;
        final org.bukkit.util.BoundingBox box = mount.getBoundingBox();
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
                    if (s != null && s.isGateActive() && StargateManager.isPortalBlock(b))
                    {
                        return b;
                    }
                }
            }
        }
        return null;
    }

    /** Maximum re-seat attempts before a passenger is given up on. */
    private static final int MAX_REATTACH_ATTEMPTS = 12;

    /**
     * Teleports a player through a gate on their own, with no mount involved.
     *
     * @param player
     *            the player
     * @param safeTarget
     *            the vetted arrival location
     */
    private static void teleportPlayerAlone(final Player player, final Location safeTarget)
    {
        // Safety net: ensure destination chunk is loaded even if it unloaded since dial time.
        try { WorldUtils.forceLoadDestinationChunks(safeTarget); } catch (final RuntimeException ignore) {}
        player.teleport(safeTarget);
        try
        {
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0);
        }
        catch (final RuntimeException ignore) {}
    }

    /**
     * Seats {@code passenger} on {@code parent}, retrying once via a position sync.
     *
     * @param parent
     *            the entity to ride
     * @param passenger
     *            the entity to seat
     * @return true if the passenger ends up aboard
     */
    private static boolean attachPassenger(final Entity parent, final Entity passenger)
    {
        try
        {
            if (parent.addPassenger(passenger))
            {
                return true;
            }
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "addPassenger failed: " + t.getMessage());
        }
        // An earlier attempt may already have succeeded without reporting it.
        try
        {
            if (parent.getPassengers().contains(passenger))
            {
                return true;
            }
        }
        catch (final RuntimeException ignore) {}
        // A passenger too far from its parent is refused, so close the gap and retry.
        try
        {
            passenger.teleport(parent.getLocation());
            return parent.addPassenger(passenger);
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "addPassenger after position sync failed: " + t.getMessage());
        }
        return false;
    }

    /**
     * Re-seats every passenger of {@code ridden} after it has been teleported through a
     * gate, then applies its exit velocity once the whole stack is aboard.
     *
     * <p>Teleporting an entity detaches its passengers, and the client does not reliably
     * accept the re-seat on the first try, so this retries on an exponential backoff.
     * Boats additionally need a position re-sync or the client keeps drawing them at the
     * departure point.
     *
     * @param ridden
     *            the boat or mount that was just teleported
     * @param player
     *            the moving player, re-seated even when the teleport already detached them
     * @param exitVelocity
     *            velocity to apply once everyone is aboard, may be null
     */
    private static void schedulePassengerReattach(final Entity ridden, final Player player, final Vector exitVelocity)
    {
        final java.util.List<Entity> parents = new java.util.ArrayList<Entity>();
        final java.util.List<Entity> children = new java.util.ArrayList<Entity>();
        WormholeXTremeVehicleListener.collectPassengerPairs(ridden, parents, children);
        // The teleport has usually already detached the moving player, so they will not
        // appear in the collected tree — put them back explicitly.
        if (!children.contains(player))
        {
            parents.add(ridden);
            children.add(player);
        }

        final int[] attempts = new int[] { 0 };
        final boolean[] attached = new boolean[children.size()];
        final Runnable[] taskHolder = new Runnable[1];
        taskHolder[0] = new Runnable()
        {
            @Override
            public void run()
            {
                attempts[0]++;
                try
                {
                    if (!ridden.isValid())
                    {
                        return;
                    }
                    int remaining = 0;
                    for (int i = 0; i < children.size(); i++)
                    {
                        if (attached[i])
                        {
                            continue;
                        }
                        final Entity psg = children.get(i);
                        try
                        {
                            if (!psg.isValid())
                            {
                                continue;
                            }
                            if (attachPassenger(parents.get(i), psg))
                            {
                                attached[i] = true;
                            }
                            else
                            {
                                remaining++;
                            }
                        }
                        catch (final RuntimeException t)
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Exception during passenger reattach: " + t.getMessage());
                            remaining++;
                        }
                    }

                    if (remaining == 0)
                    {
                        try
                        {
                            ridden.setVelocity(exitVelocity != null ? exitVelocity : new Vector(0, 0, 0));
                            ridden.setFireTicks(0);
                        }
                        catch (final RuntimeException ignore) {}
                        if (ridden instanceof Boat)
                        {
                            final Location resyncLoc = ridden.getLocation();
                            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    try { if (ridden.isValid()) { ridden.teleport(resyncLoc); } } catch (final RuntimeException ignore) {}
                                }
                            }, 3L);
                        }
                    }
                    else if (attempts[0] < MAX_REATTACH_ATTEMPTS)
                    {
                        final long backoff = Math.min(1L << Math.max(0, attempts[0] - 1), 20L);
                        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), taskHolder[0], backoff);
                    }
                    else
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to reattach passengers to " + ridden.getUniqueId() + " after " + attempts[0] + " attempts");
                    }
                }
                catch (final RuntimeException t)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Exception during passenger reattach: " + t.getMessage());
                }
            }
        };
        // 2-tick delay: there is no teleport-ack to wait for, the client re-seats immediately.
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), taskHolder[0], 2);
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
     * @return true, so the caller cancels the move event
     */
    private static boolean refuseGateEntry(final Player player)
    {
        player.sendMessage(ConfigManager.MessageStrings.playerRecentArrival.toString());
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
        if (stargate.isGatePortalBlockAt(from.getBlockX(), from.getBlockY(), from.getBlockZ()))
        {
            // Already in the portal: refusing this move, and every following one, is what
            // would trap them. They simply do not travel.
            return false;
        }
        return true;
    }


    /**
     * Handle player move event.
     *
     * @param event
     *            the event
     * @return true, if successful
     */
    private static boolean handlePlayerMoveEvent(final PlayerMoveEvent event)
    {
        if (!hasChangedBlockCoordinates(event.getFrom(), event.getTo()))
        {
            return false;
        }
        final Player player = event.getPlayer();
        if (player == null) {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "handlePlayerMoveEvent: event player is null, ignoring event.");
            return false;
        }
        final Location toLocFinal = event.getTo();
        // Every player crossing a block boundary reaches here, so the diagnostic is built
        // only when it would actually be printed. It used to call Player.toString() and
        // two extra getBlockAt() lookups on every crossing and throw all of it away.
        if (WormholeXTreme.getThisPlugin().isLoggable(Level.FINE))
        {
            try
            {
                final Block fromBlock = event.getFrom().getWorld().getBlockAt(event.getFrom().getBlockX(), event.getFrom().getBlockY(), event.getFrom().getBlockZ());
                final Block toBlock = toLocFinal.getWorld().getBlockAt(toLocFinal.getBlockX(), toLocFinal.getBlockY(), toLocFinal.getBlockZ());
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "PlayerMove: " + player.getName()
                    + " from=" + fromBlock.getType() + " to=" + toBlock.getType() + " y=" + toLocFinal.getY());
            }
            // Diagnostics only, and on the move path, so never let it disturb the event.
            catch (final RuntimeException ignore) {}
        }
        Block gateBlockFinal = toLocFinal.getWorld().getBlockAt(toLocFinal.getBlockX(), toLocFinal.getBlockY(), toLocFinal.getBlockZ());
        Stargate stargate = StargateManager.getGateFromBlock(gateBlockFinal);

        // A rider's own block is not a reliable trigger: a camel is tall enough that
        // the rider clears the portal entirely while the camel stands in it. When the
        // player's block is not a gate, look for one under their mount instead so the
        // mount-first teleport still fires.
        if (stargate == null)
        {
            final Entity ridden = player.getVehicle();
            if (isLivingMount(ridden))
            {
                final Block mountBlock = findActiveGatePortalBlockAtMount(ridden);
                if (mountBlock != null)
                {
                    gateBlockFinal = mountBlock;
                    stargate = StargateManager.getGateFromBlock(mountBlock);
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Detected mount-based gate entry for player=" + player.getName() + " via mount=" + ridden + " at block=" + mountBlock.getLocation());
                }
            }
        }

        // Everything past here is about what to do about the gate the player stepped into,
        // and each answer is its own method. This reads as the order they are asked in.
        if ((stargate == null) || !stargate.isGateActive() || !StargateManager.isPortalBlock(gateBlockFinal))
        {
            if (stargate != null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                    "Player entered a gate that is not open, or a block of it that is not the portal.");
            }
            return false;
        }

        // A gate holding no target of its own is the far end of somebody else's wormhole,
        // or one that was lit and walked away from. Either way there is nowhere to send
        // anyone from here.
        if (stargate.getGateTarget() == null)
        {
            return handleMoveAtArrivalGate(event, player, stargate);
        }

        return travelThroughGate(event, player, stargate, gateBlockFinal);
    }

    /**
     * What a move means at a gate that holds no target of its own.
     *
     * <p>Two cases share this. A gate something is dialling <em>into</em> is an exit, and
     * walking in from outside is refused so a wormhole cannot be used as a door in both
     * directions. A gate activated but never dialled has nowhere to send anybody, so its
     * ring is just a ring and a player may walk through it.
     *
     * @param event
     *            the move
     * @param player
     *            the player moving
     * @param stargate
     *            the gate they are standing in
     * @return true if the move should be cancelled
     */
    private static boolean handleMoveAtArrivalGate(final PlayerMoveEvent event, final Player player,
                                                   final Stargate stargate)
    {
        // Gate is active but has no local target: check whether it's the destination of an active incoming connection.
        boolean incomingActive = false;
        try
        {
            for (final Stargate s : StargateManager.getAllGates())
            {
                if ((s != null) && (s.getGateTarget() != null) && (s.getGateTarget() == stargate) && s.isGateActive())
                {
                    incomingActive = true;
                    break;
                }
            }
        }
        catch (final RuntimeException ignore) {}

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
                return refuseGateEntry(player);
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
     * <p>Reads as the order the questions are asked in: may they use this gate, have they
     * only just come out of it, are they on cooldown, can they pay, is the far end sealed,
     * is it even in this world. Only once all of that has passed does anything move, and
     * even then a listener may still say no.
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
        String gatenetwork;
        if (stargate.getGateNetwork() != null)
        {
            gatenetwork = stargate.getGateNetwork().getNetworkName();
        }
        else
        {
            gatenetwork = "Public";
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Player in gate:" + stargate.getGateName() + " gate Active: " + stargate.isGateActive() + " Target Gate: " + stargate.getGateTarget().getGateName() + " Network: " + gatenetwork);

        // Refill the player's air while they stand in the portal so a water-material
        // gate does not drown them. Cosmetic, so a failure is not worth reporting.
        try { player.setRemainingAir(player.getMaximumAir()); } catch (final RuntimeException ignore) {}

        if (ConfigManager.getWormholeUseIsTeleport() && ((stargate.isGateSignPowered() && !WXPermissions.checkWXPermissions(player, stargate, PermissionType.SIGN)) || ( !stargate.isGateSignPowered() && !WXPermissions.checkWXPermissions(player, stargate, PermissionType.DIALER))))
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return false;
        }

        // Prevent immediate re-entry to the gate the player just exited from.
        if (com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.isPlayerRecentArrivalFrom(player, stargate))
        {
            return refuseGateEntry(player);
        }

        if (ConfigManager.isUseCooldownEnabled())
        {
            if (StargateRestrictions.isPlayerUseCooldown(player))
            {
                player.sendMessage(ConfigManager.MessageStrings.playerUseCooldownRestricted.toString());
                player.sendMessage(ConfigManager.MessageStrings.playerUseCooldownWaitTime.toString() + StargateRestrictions.checkPlayerUseCooldownRemaining(player));
                return false;
            }
            // Not applied here: the cooldown is set once the traveller has actually
            // gone, further down. Setting it at the check as well spent the player's
            // cooldown on a trip that had not happened yet and might still not.
        }

        // Affordability is checked here so the player is turned away for the right
        // reason and in the right order, but the money does not move until the trip is
        // certain: a listener may still stop it, and charging for a journey that never
        // happened is the one outcome nobody can argue is correct.
        double pendingUseCost = 0.0;
        if (ConfigManager.isEconomyEnabled() && com.wormhole_xtreme.wormhole.plugin.EconomySupport.isAvailable())
        {
            final double useCost = ConfigManager.getEconomyUseCost();
            if (useCost > 0)
            {
                if (!com.wormhole_xtreme.wormhole.plugin.EconomySupport.canAfford(player, useCost))
                {
                    player.sendMessage(ConfigManager.MessageStrings.economyInsufficientFunds.toString());
                    return false;
                }
                pendingUseCost = useCost;
            }
        }

        if (stargate.getGateTarget().isGateIrisActive())
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Remote Iris is locked!");
            player.setNoDamageTicks(5);
            event.setFrom(stargate.getGatePlayerTeleportLocation());
            event.setTo(stargate.getGatePlayerTeleportLocation());
            player.teleport(stargate.getGatePlayerTeleportLocation());
            return true;
        }

        final Location target = stargate.getGateTarget().getGatePlayerTeleportLocation();

        if (ConfigManager.isSameWorldOnly())
        {
            final org.bukkit.World targetWorld = (target != null) ? target.getWorld() : null;
            if (targetWorld != null && !gateBlockFinal.getWorld().equals(targetWorld))
            {
                player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Cross-world travel is disabled on this server.");
                player.setNoDamageTicks(5);
                return false;
            }
        }

        final Location safeTarget = findSafePlayerLocation(target);

        // Every check this plugin makes has passed and nothing has moved yet, which is
        // the only honest point to let another plugin object.
        if (!com.wormhole_xtreme.wormhole.events.GateEvents.firePlayerTravel(
                stargate, player, stargate.getGateTarget(), safeTarget))
        {
            return holdBackCancelledTraveller(event, stargate);
        }

        // Travel is settled, so the fare can be taken.
        if (pendingUseCost > 0)
        {
            com.wormhole_xtreme.wormhole.plugin.EconomySupport.charge(player, pendingUseCost);
            player.sendMessage(ConfigManager.MessageStrings.economyCharged.toString()
                + pendingUseCost + " " + com.wormhole_xtreme.wormhole.plugin.EconomySupport.currencyName(pendingUseCost));
        }
        return performGateTeleport(event, player, stargate, target, safeTarget);
    }

    /**
     * Moves the traveller, and records what follows from their having gone.
     *
     * <p>Reached only once travel is certain: every check has passed, no listener objected,
     * and the fare is paid. What is left is the awkward part — a rider has to travel with
     * whatever is carrying them, and the client has to be told about both in an order it
     * will accept.
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
     * @return true if the move should be cancelled
     */
    private static boolean performGateTeleport(final PlayerMoveEvent event, final Player player,
                                               final Stargate stargate, final Location target,
                                               final Location safeTarget)
    {
        // Diagnostic logging for teleport issues
        if (WormholeXTreme.getThisPlugin() != null)
        {
            if (target == null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Teleport target is null for gate: " + stargate.getGateTarget().getGateName());
            }
            else if (target.getWorld() == null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Teleport target world is null for gate: " + stargate.getGateTarget().getGateName() + " loc: " + target.toString());
            }
            else
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Teleporting " + player.getName() + " to " + stargate.getGateTarget().getGateName() + " @ " + target.toString());
            }
        }
        player.setNoDamageTicks(5);
        // Capture the player's current position before any event/teleport manipulation.
        final Location playerCurrentLoc = event.getFrom().clone();
        // Track whether the vehicle-only path was taken (no explicit player teleport).
        final boolean[] vehiclePathUsed = { false };
        // Whatever the player is riding: boat, horse, camel, pig, strider. Minecarts
        // are the one exception — they raise VehicleMoveEvent, so the vehicle listener
        // owns them and teleports them in place with passenger state preserved.
        final Entity ridden = player.getVehicle();
        if (ridden instanceof Minecart)
        {
            return false;
        }
        // For every other flow, mark the event position to the safe target and continue.
        event.setFrom(safeTarget);
        event.setTo(safeTarget);
        try
        {
            if (ridden != null)
            {
                final BlockFace exitFacing = stargate.getGateTarget().getGateFacing();
                final Location riddenTarget = WormholeXTremeVehicleListener.forwardAndUp(safeTarget, exitFacing, 1.0, 1.0);
                Vector exitVelocity = null;
                try
                {
                    exitVelocity = WormholeXTremeVehicleListener.computeExitVelocity(exitFacing, ridden.getVelocity(), 5.0);
                    if (riddenTarget != null && exitVelocity != null)
                    {
                        // Face the direction of travel so the client does not render the
                        // mount spinning to its new yaw after arrival.
                        final double dx = exitVelocity.getX();
                        final double dz = exitVelocity.getZ();
                        riddenTarget.setYaw((Math.abs(dx) > 0.0001 || Math.abs(dz) > 0.0001)
                            ? (float) Math.toDegrees(Math.atan2( -dx, dz))
                            : WorldUtils.getDegreesFromBlockFace(exitFacing));
                        riddenTarget.setPitch(0f);
                    }
                }
                catch (final RuntimeException ignore) {}

                // Safety net: ensure destination chunk is loaded even if it unloaded since dial time.
                try { WorldUtils.forceLoadDestinationChunks(riddenTarget); } catch (final RuntimeException ignore) {}
                // Mark before the teleport so a VehicleMoveEvent in the same tick is
                // suppressed and does not double-process this entry, zeroing the exit velocity.
                try { WormholeXTremeVehicleListener.markVehicleRecentlyTeleported(ridden.getUniqueId()); } catch (final RuntimeException ignore) {}

                boolean riddenTeleported = false;
                try
                {
                    ridden.teleport(riddenTarget);
                    riddenTeleported = true;
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "PlayerTeleport: teleported " + ridden.getType().name() + " " + ridden.getUniqueId() + " for player " + player.getName());
                }
                catch (final RuntimeException tt)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to teleport what " + player.getName() + " was riding: " + tt.getMessage());
                }

                if (riddenTeleported)
                {
                    // Ride-first: no player.teleport() at all, so there is no teleport-ack
                    // race when the client processes the follow-up set-passengers packet.
                    vehiclePathUsed[0] = true;
                    event.setFrom(playerCurrentLoc);
                    event.setTo(playerCurrentLoc);
                    schedulePassengerReattach(ridden, player, exitVelocity);
                }
                else
                {
                    // Could not move what they were riding; send the player through alone
                    // rather than stranding them on the source side.
                    teleportPlayerAlone(player, safeTarget);
                }
            }
            else
            {
                teleportPlayerAlone(player, safeTarget);
            }
        }
        catch (final Exception e)
        {
            if (WormholeXTreme.getThisPlugin() != null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Exception while teleporting " + player.getName() + " to " + (target == null ? "null" : target.toString()) + ": " + e.getMessage());
            }
        }
        try
        {
            com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.addPlayerUseCooldown(player);
        }
        catch (final RuntimeException e)
        {
            // Not fatal to the teleport that already happened, but a silently skipped
            // cooldown lets a player re-enter immediately, so say so.
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                "Failed to apply use cooldown for " + player.getName() + ": " + e.getMessage());
        }
        // Mark player as having just arrived from this gate to prevent immediate re-entry
        try
        {
            if (stargate.getGateTarget() != null)
            {
                com.wormhole_xtreme.wormhole.permissions.StargateRestrictions.addPlayerRecentArrival(player, stargate.getGateTarget());
            }
        }
        catch (final RuntimeException e)
        {
            // Without this marker the player can walk straight back into the gate they
            // just arrived from, so a failure is worth a line in the log.
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                "Failed to mark recent arrival for " + player.getName() + ": " + e.getMessage());
        }

        // Schedule a short delayed task to re-apply zero velocity.
        // Skip the position teleport for the vehicle path — the client is repositioned by addPassenger.
        try {
            final Location finalTarget = target;
            final boolean skipTeleport = vehiclePathUsed[0];
            Bukkit.getScheduler().runTaskLater(WormholeXTreme.getThisPlugin(), new Runnable()
            {
                @Override
                public void run()
                {
                    if ((player == null) || (finalTarget == null))
                    {
                        return;
                    }
                    // Settling the player after arrival. Each call is independently
                    // best-effort: none of them failing is worth aborting the others.
                    try { player.setVelocity(new Vector(0, 0, 0)); } catch (final RuntimeException ignore) {}
                    try { player.setFallDistance(0); } catch (final RuntimeException ignore) {}
                    if (!skipTeleport)
                    {
                        try { player.teleport(finalTarget); } catch (final RuntimeException ignore) {}
                    }
                }
            }, 1L);
        }
        catch (final RuntimeException ignore) {}
        if (target != stargate.getGatePlayerTeleportLocation())
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, player.getName() + " used wormhole: " + stargate.getGateName() + " to go to: " + stargate.getGateTarget().getGateName());
        }
        if (ConfigManager.getTimeoutShutdown() == 0)
        {
            stargate.shutdownStargate(true);
        }
        return true;
    }

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

    /* (non-Javadoc)
     * @see org.bukkit.event.player.PlayerListener#onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent)
     */
    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event)
    {
        if (event.getClickedBlock() != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Caught Player: \"" + event.getPlayer().getName() + "\" Action Type: \"" + event.getAction().toString() + "\" Event Block Type: \"" + event.getClickedBlock().getType().toString() + "\" Event World: \"" + event.getClickedBlock().getWorld().toString() + "\" Event Block: " + event.getClickedBlock().toString() + "\"");
            if (GateInteractionHandler.handlePlayerInteractEvent(event))
            {
                event.setCancelled(true);
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Cancelled Player: \"" + event.getPlayer().getName() + "\" Action Type: \"" + event.getAction().toString() + "\" Event Block Type: \"" + event.getClickedBlock().getType().toString() + "\" Event World: \"" + event.getClickedBlock().getWorld().toString() + "\" Event Block: " + event.getClickedBlock().toString() + "\"");
            }
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Caught and ignored Player: \"" + event.getPlayer().getName() + "\" Action Type: \"" + event.getAction().toString() + "\"");
        }
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

        if (handlePlayerMoveEvent(event))
        {
            event.setCancelled(true);
            return;
        }
        // Rings are asked only once no gate has claimed this move, so gates keep priority
        // and the two can never both act on one step. Ring creation refuses any footprint
        // touching gate blocks, so in practice they never contend for the same block at all.
        handleRingMoveEvent(event);
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
     * Arms a transport ring if this move took the player into one.
     *
     * <p>The whole of ring detection on the move path, and it is deliberately three lines of
     * work: a hash lookup for the block, a question to the pair about whether it will fire,
     * and a permission check. Every player crossing every block boundary runs this, so it
     * cannot afford to be anything more.
     *
     * <p>Only the interior arms a ring. Standing on the edge is crossing a threshold rather
     * than standing in the thing, and firing on it would take people who were walking past.
     *
     * <p>There is no arrival guard here and none is needed. The cooldown is shared by both
     * ends of a pair, so somebody who has just landed cannot re-fire the ring they landed in
     * — the settle-move after a teleport arrives long inside the cooldown that same cycle
     * started. Somebody still standing there and still moving a full cooldown later does
     * fire it again, and travels back, which is the intended behaviour rather than a bug.
     *
     * @param event
     *            the move being considered
     */
    private static void handleRingMoveEvent(final PlayerMoveEvent event)
    {
        final Location to = event.getTo();
        if ((to == null) || (to.getWorld() == null))
        {
            return;
        }
        final com.wormhole_xtreme.wormhole.model.ring.RingIndex.RingEnd end =
            com.wormhole_xtreme.wormhole.model.ring.RingIndex.volumeAt(
                to.getWorld().getName(), to.getBlockX(), to.getBlockY(), to.getBlockZ());
        if (end == null)
        {
            return;
        }
        final com.wormhole_xtreme.wormhole.model.ring.RingPair pair = end.getPair();
        final Player player = event.getPlayer();

        // Whether this step took them into the ring or merely around inside it. Messages are
        // for arriving somewhere, and this path runs on every block boundary crossed, so a
        // player wandering about on a pad that is recharging would otherwise be told about it
        // several times a second. Arming still happens on any move inside, which is what lets
        // somebody who stays put after a trip be carried back once the cooldown passes.
        final Location from = event.getFrom();
        final boolean justEntered = (from.getWorld() == null)
            || (com.wormhole_xtreme.wormhole.model.ring.RingIndex.volumeAt(
                from.getWorld().getName(), from.getBlockX(), from.getBlockY(), from.getBlockZ()) != end);

        // Arming is a use of the ring, so the same permission governs it as governs being
        // carried. Somebody who cannot travel by a pair should not be able to set it off
        // for everybody else either.
        if (!com.wormhole_xtreme.wormhole.model.ring.RingPermissions.mayUse(player, pair))
        {
            if (justEntered)
            {
                com.wormhole_xtreme.wormhole.model.ring.RingMessages.notYours(player);
            }
            return;
        }
        final long now = System.currentTimeMillis();
        if (!pair.canFire(now))
        {
            if (justEntered)
            {
                // Two different reasons to refuse, and a player standing on a silent pad
                // deserves to know which: one of them ends by itself and the other does not.
                if (pair.getCooldownUntil() > now)
                {
                    com.wormhole_xtreme.wormhole.model.ring.RingMessages.recharging(
                        player, pair.getCooldownUntil() - now);
                    // A recharging ring is invisible, so being told it is not ready leaves
                    // somebody standing on ground that looks like any other. Show them where
                    // it is. Not done for a ring that is mid-cycle: that pad is already lit,
                    // so there is nothing to point out and the outline would put those lights
                    // out when it expired.
                    com.wormhole_xtreme.wormhole.model.ring.RingOutline.flash(
                        player, pair, end.getRing());
                }
                else
                {
                    com.wormhole_xtreme.wormhole.model.ring.RingMessages.busy(player);
                }
            }
            return;
        }
        if (com.wormhole_xtreme.wormhole.model.ring.RingTransit.start(pair))
        {
            final com.wormhole_xtreme.wormhole.model.ring.Ring far = pair.opposite(end.getRing());
            com.wormhole_xtreme.wormhole.model.ring.RingMessages.engaged(
                player, far == null ? "" : far.getName());
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
            || !java.util.Objects.equals(from.getWorld(), to.getWorld());
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
    private static final java.util.Set<java.util.UUID> portalFlightGranted =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

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
            final java.util.UUID id = player.getUniqueId();

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
        catch (final RuntimeException ignore) {}
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
            if ((player.getGameMode() == org.bukkit.GameMode.CREATIVE)
                || (player.getGameMode() == org.bukkit.GameMode.SPECTATOR))
            {
                return;
            }
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        catch (final RuntimeException ignore) {}
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
            if ((gate.getGateWorld() != null) && gate.getGateWorld().equals(at.getWorld())
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
                    new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            StargateManager.refreshPortalVisuals(player);
                        }
                    },
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
     *
     * @param event
     *            the teleport
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
     *
     * @param event
     *            the quit
     */
    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event)
    {
        portalFlightGranted.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Redraws open portals for a player who has just joined.
     *
     * @param event
     *            the join
     */
    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event)
    {
        refreshPortalVisualsFor(event.getPlayer());
    }

    /**
     * Redraws open portals for a player who has changed world.
     *
     * @param event
     *            the world change
     */
    @EventHandler
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event)
    {
        refreshPortalVisualsFor(event.getPlayer());
    }

    /**
     * Redraws open portals for a player who has just respawned.
     *
     * @param event
     *            the respawn
     */
    @EventHandler
    public void onPlayerRespawn(final PlayerRespawnEvent event)
    {
        refreshPortalVisualsFor(event.getPlayer());
    }
}

