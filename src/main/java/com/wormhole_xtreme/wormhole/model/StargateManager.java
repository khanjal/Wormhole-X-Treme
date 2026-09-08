package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable.ActionToTake;

/**
 * WormholeXtreme Stargate Manager.
 * 
 * @author Ben Echols (Lologarithm)
 */
public class StargateManager
{
    /** Static helpers only; never instantiated. */
    private StargateManager()
    {
    }

    // A list of all blocks contained by all stargates. Makes for easy indexing when a player is trying
    // to enter a gate or if water is trying to flow out, also will contain the stone buttons used to activate.
    /** The all_gate_blocks. */
    private static final ConcurrentHashMap<Location, Stargate> allGateBlocks = new ConcurrentHashMap<>();
    // List of All stargates indexed by name. Useful for dialing and such
    /** The stargate_list. */
    private static final ConcurrentHashMap<String, Stargate> stargateList = new ConcurrentHashMap<>();
    // List of stargates built but not named. Indexed by the player that built it.
    /** The incomplete_stargates. */
    private static final ConcurrentHashMap<Player, Stargate> incompleteStargates = new ConcurrentHashMap<>();
    // List of stargates that have been activated but not yet dialed. Only used for gates without public use sign.
    /** The activated_stargates. */
    private static final ConcurrentHashMap<Player, Stargate> activatedStargates = new ConcurrentHashMap<>();
    // List of networks indexed by their name
    /** The stargate_networks. */
    private static final ConcurrentHashMap<String, StargateNetwork> stargateNetworks = new ConcurrentHashMap<>();
    // List of players ready to build a stargate, with the shape they are trying to build.
    /** The player_builders. */
    private static final ConcurrentHashMap<Player, StargateShape> playerBuilders = new ConcurrentHashMap<>();

    // Gates whose portal is currently drawn, kept as a set rather than found by filtering
    // every gate. The portal is a client-side illusion that has to be redrawn whenever a
    // player arrives or reloads a chunk, so this is read on player movement across chunk
    // boundaries — a per-player, per-chunk event. Filtering the whole gate list there would
    // scale that work with the number of gates on the server; this scales with the number
    // of gates actually open, which is nearly always a handful.
    /** The gates currently showing a portal. */
    private static final java.util.Set<Stargate> openGates =
        java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    // List of blocks that are part of an active animation. Only use this to make sure water doesn't flow everywhere.
    /** The Constant opening_animation_blocks. */
    private static final ConcurrentHashMap<Location, Block> openingAnimationBlocks = new ConcurrentHashMap<>();
    // Keep the original material for each animated block so we can restore it after the woosh
    private static final ConcurrentHashMap<Location, Material> openingAnimationOriginalMaterials = new ConcurrentHashMap<>();

    /**
     * This method adds a stargate that has been activated but not dialed by a player.
     * 
     * @param p
     *            The player who has activated the gate
     * @param s
     *            The gate the player has activated.
     */
    public static void addActivatedStargate(final Player p, final Stargate s)
    {
        getActivatedStargates().put(p, s);
    }

    /**
     * This method adds an index mapping block location to stargate.
     * NOTE: This method does not verify that the block is part of the gate,
     * so it may not persist and won't be removed by removing the stargate. This can cause a gate to stay in memory!!!
     * 
     * @param b
     *            the b
     * @param s
     *            the s
     */
    public static void addBlockIndex(final Block b, final Stargate s)
    {
        if ((b != null) && (s != null))
        {
            final Location norm = normalizeBlockLocation(b.getLocation());
            getAllGateBlocks().put(norm, s);
            GateSpatialIndex.add(norm);
            try
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Indexed gate block: gate=" + s.getGateName() + " loc=" + b.getLocation().toString() + " type=" + b.getType().toString());
            }
            catch (final Exception e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Error logging indexed block", e);
            }
        }
    }

    /**
     * Adds the gate to network.
     * 
     * @param gate
     *            the gate
     * @param network
     *            the network
     */
    public static void addGateToNetwork(final Stargate gate, final String network)
    {
        if ( !getStargateNetworks().containsKey(network))
        {
            addStargateNetwork(network);
        }

        StargateNetwork net;
        if ((net = getStargateNetworks().get(network)) != null)
        {
            synchronized (net.getNetworkGateLock())
            {
                // Avoid adding the same gate multiple times.
                if (!net.getNetworkGateList().contains(gate))
                {
                    net.getNetworkGateList().add(gate);
                }
                if (gate.isGateSignPowered() && !net.getNetworkSignGateList().contains(gate))
                {
                    net.getNetworkSignGateList().add(gate);
                }
            }
        }
    }

    /**
     * Adds a gate indexed by the player that hasn't yet been named and completed.
     * 
     * @param p
     *            The player
     * @param s
     *            The Stargate
     */
    public static void addIncompleteStargate(final Player p, final Stargate s)
    {
        getIncompleteStargates().put(p, s);
    }

    /**
     * Adds the player builder shape.
     * 
     * @param p
     *            the p
     * @param shape
     *            the shape
     */
    public static void addPlayerBuilderShape(final Player p, final StargateShape shape)
    {
        getPlayerBuilders().put(p, shape);
    }

    /**
     * Adds the given stargate to the list of stargates. Also adds all its blocks to big block index.
     * 
     * @param s
     *            The Stargate you want added.
     */
    protected static void addStargate(final Stargate s)
    {
        getStargateList().put(normalizeGateName(s.getGateName()), s);
        for (final Location b : s.getGateStructureBlocks())
        {
            final Location norm = normalizeBlockLocation(b);
            getAllGateBlocks().put(norm, s);
            GateSpatialIndex.add(norm);
        }
        for (final Location b : s.getGatePortalBlocks())
        {
            final Location norm = normalizeBlockLocation(b);
            getAllGateBlocks().put(norm, s);
            GateSpatialIndex.add(norm);
        }
        // Index explicit activation-related blocks so player interactions find the gate.
        try
        {
            if (s.getGateDialLeverBlock() != null)
            {
                addBlockIndex(s.getGateDialLeverBlock(), s);
            }
            if (s.getGateIrisLeverBlock() != null)
            {
                addBlockIndex(s.getGateIrisLeverBlock(), s);
            }
            if (s.getGateDialSignBlock() != null)
            {
                addBlockIndex(s.getGateDialSignBlock(), s);
            }
            if (s.getGateRedstoneDialActivationBlock() != null)
            {
                addBlockIndex(s.getGateRedstoneDialActivationBlock(), s);
            }
            // The sign cycle block was the one activation block left out of the index. A
            // redstone event looks the gate up by the block it fired on, so without this
            // entry a pulse at [RS] only found the gate when it happened to land within the
            // fallback search radius of [RD], which it does only when a shape puts the two
            // close together and would not in a wider one. No shipped shape carries [RS]
            // now, but a server's custom shape still can.
            if (s.getGateRedstoneSignActivationBlock() != null)
            {
                addBlockIndex(s.getGateRedstoneSignActivationBlock(), s);
            }
            if (s.getGateRedstoneGateActivatedBlock() != null)
            {
                addBlockIndex(s.getGateRedstoneGateActivatedBlock(), s);
            }
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Error indexing gate activation blocks", e);
        }
    }

    /**
     * Public wrapper to register a stargate from other packages.
     * Delegates to protected addStargate.
     */
    public static void registerStargate(final Stargate s)
    {
        addStargate(s);
    }

    // Network functions
    /**
     * Adds the stargate network.
     * 
     * @param name
     *            the name
     * @return the stargate network
     */
    public static StargateNetwork addStargateNetwork(final String name)
    {
        if ( !getStargateNetworks().containsKey(name))
        {
            final StargateNetwork sn = new StargateNetwork();
            sn.setNetworkName(name);
            getStargateNetworks().put(name, sn);
            return sn;
        }
        else
        {
            return getStargateNetworks().get(name);
        }
    }

    /**
     * Complete stargate.
     * 
     * @param p
     *            the p
     * @param name
     *            the name
     * @param idc
     *            the idc
     * @param network
     *            the network
     * @return true, if successful
     */
    public static boolean completeStargate(final Player p, final String name, final String idc, final String network)
    {
        final Stargate complete = getIncompleteStargates().remove(p);
        if (complete == null)
        {
            return false;
        }

        joinNetwork(complete, network);
        complete.setGateOwner(p.getUniqueId().toString());
        complete.setGateOwnerName(p.getName());
        complete.completeGate(name, idc);
        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Player: " + p.getName() + " completed a wormhole: " + complete.getGateName());

        addStargate(complete);
        logGateBlocks(complete);
        StargateDBManager.saveStargate(complete);

        // Announced once the gate is registered and saved, so a listener can look it up by
        // name and find it already there.
        com.wormhole_xtreme.wormhole.events.GateEvents.fireCreated(complete, p);

        initialiseDialSign(complete);
        return true;
    }

    /**
     * Puts a gate on the named network, if one was named.
     *
     * @param gate
     *            the gate
     * @param network
     *            the network name, empty for none
     */
    private static void joinNetwork(final Stargate gate, final String network)
    {
        if (network.isEmpty())
        {
            return;
        }
        // addStargateNetwork returns the existing network when there is one, so this is
        // find-or-create and needs no check of its own.
        final StargateNetwork net = StargateManager.addStargateNetwork(network);
        StargateManager.addGateToNetwork(gate, network);
        gate.setGateNetwork(net);
    }

    /**
     * Logs which block ended up as which part of the gate.
     *
     * <p>At FINE rather than INFO: this is twelve fields about one gate, useful when a gate
     * came out wrong and noise on every gate that came out right.
     *
     * @param gate
     *            the completed gate
     */
    private static void logGateBlocks(final Stargate gate)
    {
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Gate debug: Name=" + gate.getGateName()
            + " Owner=" + gate.getGateOwner()
            + " DialLever=" + describe(gate.getGateDialLeverBlock())
            + " IrisLever=" + describe(gate.getGateIrisLeverBlock())
            + " DialSign=" + describe(gate.getGateDialSignBlock())
            + " RedstoneDial=" + describe(gate.getGateRedstoneDialActivationBlock())
            + " RedstoneGateActivated=" + describe(gate.getGateRedstoneGateActivatedBlock()));
    }

    /**
     * Where a block is and what it is, for a log line.
     *
     * @param block
     *            the block, which may be absent
     * @return its location and type, or "null"
     */
    private static String describe(final Block block)
    {
        if (block == null)
        {
            return "null";
        }
        return block.getLocation().toString() + " (" + block.getType() + ")";
    }

    /**
     * Cycles a sign-powered gate's dial sign to its first destination.
     *
     * <p>Otherwise the sign stands blank until somebody clicks it, which reads as a gate that
     * did not finish building.
     *
     * @param gate
     *            the completed gate
     */
    private static void initialiseDialSign(final Stargate gate)
    {
        if (!gate.isGateSignPowered() || (gate.getGateDialSignBlock() == null))
        {
            return;
        }
        try
        {
            StargateDialManager.teleportSignClicked(gate, true);
        }
        catch (final RuntimeException ignore)
        {
            // a sign that will not cycle is not worth failing the build over
        }
    }

    /**
     * Distance to closest stargate block.
     * 
     * @param self
     *            Location of the local object.
     * @param stargate
     *            Stargate to check blocks for distance.
     * @return square of distance to the closest stargate block.
     */
    public static double distanceSquaredToClosestGateBlock(final Location self, final Stargate stargate)
    {
        double distance = Double.MAX_VALUE;
        if ((stargate != null) && (self != null))
        {
            final List<Location> gateblocks = stargate.getGateStructureBlocks();
            for (final Location l : gateblocks)
            {
                final double blockdistance = getSquaredDistance(self, l);
                if (blockdistance < distance)
                {
                    distance = blockdistance;
                }
            }
        }
        return distance;
    }

    /**
     * Find the closest stargate.
     * 
     * @param self
     *            Location of the local object.
     * @return The closest stargate to the local object.
     */
    public static Stargate findClosestStargate(final Location self)
    {
        Stargate stargate = null;
        if (self != null)
        {
            final List<Stargate> gates = StargateManager.getAllGates();
            double man = Double.MAX_VALUE;
            for (final Stargate s : gates)
            {
                final Location t = s.getGatePlayerTeleportLocation();
                final double distance = getSquaredDistance(self, t);
                if (distance < man)
                {
                    man = distance;
                    stargate = s;
                }
            }
        }
        return stargate;
    }

    /**
     * Find a stargate by scanning nearby indexed gate blocks.
     * This is a local-area lookup that avoids iterating all gates and is
     * intended for event handlers that only need nearby gates.
     *
     * @param loc base location to search around
     * @param radiusXZ horizontal search radius in blocks
     * @param radiusY vertical search radius in blocks
     * @return a nearby Stargate if any indexed gate block is within the search box, otherwise null
     */
    public static Stargate findNearestGateByBlock(final Location loc, final int radiusXZ, final int radiusY)
    {
        if (loc == null)
        {
            return null;
        }

        final java.util.Set<Location> candidates = GateSpatialIndex.collectLocationsWithinRadius(loc, radiusXZ, radiusY);
        if (candidates == null || candidates.isEmpty())
        {
            return null;
        }

        Stargate best = null;
        double bestDist = Double.MAX_VALUE;
        for (final Location l : candidates)
        {
            final Stargate s = getAllGateBlocks().get(l);
            if (s == null)
            {
                continue;
            }
            final double d = getSquaredDistance(loc, l);
            if (d < bestDist)
            {
                bestDist = d;
                best = s;
            }
        }
        return best;
    }

    /**
     * Gets the activated stargates.
     * 
     * @return the activated stargates
     */
    private static ConcurrentHashMap<Player, Stargate> getActivatedStargates()
    {
        return activatedStargates;
    }

    /**
     * Gets the all gate blocks.
     * 
     * @return the all gate blocks
     */
    private static ConcurrentHashMap<Location, Stargate> getAllGateBlocks()
    {
        return allGateBlocks;
    }

    /**
     * Gets a live, unsorted view of every registered gate.
     *
     * <p>Use this for iteration. {@link #getAllGates()} copies every gate into a fresh
     * list and sorts it by name, which is right for anything shown to a player but pure
     * waste for a loop that just filters — and on a server with hundreds of gates, a
     * repeating task doing that every few ticks allocates and sorts continuously.
     *
     * <p>The returned collection is backed by the live gate map, so it must not be
     * modified and may change while being iterated.
     *
     * @return an unmodifiable view of the registered gates
     */
    public static java.util.Collection<Stargate> getAllGatesUnsorted()
    {
        return java.util.Collections.unmodifiableCollection(getStargateList().values());
    }

    /**
     * Records whether a gate is currently showing a portal.
     *
     * <p>Called from {@link Stargate#setGateActive(boolean)} so the set cannot drift from
     * the flag it mirrors.
     *
     * @param gate
     *            the gate
     * @param open
     *            whether its portal is drawn
     */
    static void setGateOpenState(final Stargate gate, final boolean open)
    {
        if (open)
        {
            openGates.add(gate);
        }
        else
        {
            openGates.remove(gate);
        }
    }

    /**
     * The gates currently showing a portal.
     *
     * <p>The portal is drawn on clients rather than placed in the world, so it has to be
     * redrawn for any player who was not nearby when it opened or whose client has since
     * reloaded the chunk. This is the set to walk for that.
     *
     * @return an unmodifiable view of the open gates
     */
    public static java.util.Set<Stargate> getOpenGates()
    {
        return java.util.Collections.unmodifiableSet(openGates);
    }

    /**
     * Shows a traveller a moment of water as they come out of a gate.
     *
     * @param player
     *            the traveller who has just arrived
     */
    public static void splashArrival(final Player player)
    {
        StargateBlockSetup.splashArrival(player);
    }

    /**
     * Redraws a gate's teleport sign.
     *
     * <p>Exists so {@code /wormhole gate regenerate} can ask for it directly. It used to
     * reach {@link StargateDialManager} through {@code Class.forName} and a reflective
     * lookup of a package-private method -- reflection into this plugin's own code, which
     * buys nothing and costs everything: a rename compiles clean, the lookup throws at
     * runtime, and the catch swallows it. The sign would simply stop being regenerated and
     * nobody would find out.
     *
     * @param gate
     *            the gate whose sign should be redrawn
     * @param forward
     *            true to step the sign's target forward
     */
    public static void refreshTeleportSign(final Stargate gate, final boolean forward)
    {
        StargateDialManager.teleportSignClicked(gate, forward);
    }

    /**
     * Forgets which portals a player was being shown.
     *
     * @param uuid
     *            the player who has gone
     */
    public static void forgetPortalVisuals(final java.util.UUID uuid)
    {
        StargateBlockSetup.forgetDrawn(uuid);
    }

    /**
     * Redraws every open gate's portal for one player, and takes back any they are still
     * being shown for a gate that has since closed.
     *
     * <p>The portal exists only in each nearby client's copy of the chunk, so it has to be
     * redrawn for anyone who arrives after the gate opened or whose client has reloaded the
     * chunk since — and un-drawn for anyone who kept a copy through a close they were not
     * near enough to be told about. See
     * {@link StargateBlockSetup#refreshPortalVisuals(Player)}.
     *
     * @param player
     *            the player to correct
     */
    public static void refreshPortalVisuals(final Player player)
    {
        StargateBlockSetup.refreshPortalVisuals(player);
    }

    /**
     * Get all gates, sorted by name.
     * This copies and sorts, so prefer {@link #getAllGatesUnsorted()} when iterating.
     *
     * @return the array list
     */
    public static List<Stargate> getAllGates()
    {
        final ArrayList<Stargate> gates = new ArrayList<>();

        final Enumeration<Stargate> keys = getStargateList().elements();

        while (keys.hasMoreElements())
        {
            gates.add(keys.nextElement());
        }

        java.util.Collections.sort(gates, (a, b) -> a.getGateName().compareToIgnoreCase(b.getGateName()));

        return gates;
    }

    /**
     * Gets the gate from block.
     * 
     * @param b
     *            the b
     * @return the gate from block
     */
    public static Stargate getGateFromBlock(final Block b)
    {
        final Location key = normalizeBlockLocation(b.getLocation());
        final boolean contains = getAllGateBlocks().containsKey(key);
        // Guarded because this is the most-called method in the plugin — every player
        // move, every vehicle move, and every tracked projectile every tick. Unguarded it
        // built two Location strings and a Material name per call and discarded them all.
        if (WormholeXTreme.getThisPlugin() != null && WormholeXTreme.getThisPlugin().isLoggable(Level.FINE))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Gate lookup: loc=" + b.getLocation() + " type=" + b.getType() + " indexed=" + contains);
        }
        if (contains)
        {
            final Stargate s = getAllGateBlocks().get(key);
            if (WormholeXTreme.getThisPlugin() != null && WormholeXTreme.getThisPlugin().isLoggable(Level.FINE))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Gate lookup hit: gate=" + (s != null ? s.getGateName() : "null") + " for loc=" + b.getLocation());
            }
            return s;
        }
        if (WormholeXTreme.getThisPlugin() != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Gate lookup miss for loc=" + b.getLocation().toString());
        }
        return null;
    }

    /**
     * Gets the incomplete stargates.
     * 
     * @return the incomplete stargates
     */
    private static ConcurrentHashMap<Player, Stargate> getIncompleteStargates()
    {
        return incompleteStargates;
    }

    /**
     * Returns the name of the incomplete stargate for a player, or null if none.
     * Useful for diagnostics when completion fails.
     *
     * @param p the player
     * @return gate name or null
     */
    public static String getIncompleteStargateName(final Player p)
    {
        final Stargate s = getIncompleteStargates().get(p);
        return s != null ? s.getGateName() : null;
    }

    /**
     * Gets the opening animation blocks.
     * 
     * @return the opening animation blocks
     */
    protected static ConcurrentHashMap<Location, Block> getOpeningAnimationBlocks()
    {
        return openingAnimationBlocks;
    }

    protected static ConcurrentHashMap<Location, Material> getOpeningAnimationOriginalMaterials()
    {
        return openingAnimationOriginalMaterials;
    }

    /**
     * Normalize a location to its block coordinates (integer XYZ) while preserving world.
     * Use this when storing/retrieving map keys that represent block positions.
     */
    protected static Location normalizeBlockLocation(final Location loc)
    {
        if (loc == null || loc.getWorld() == null)
        {
            return loc;
        }
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Gets the player builders.
     * 
     * @return the player builders
     */
    private static ConcurrentHashMap<Player, StargateShape> getPlayerBuilders()
    {
        return playerBuilders;
    }

    /**
     * Gets the player builder shape.
     * 
     * @param p
     *            the p
     * @return the stargate shape
     */
    public static StargateShape getPlayerBuilderShape(final Player p)
    {
        if (getPlayerBuilders().containsKey(p))
        {
            return getPlayerBuilders().remove(p);
        }
        else
        {
            return null;
        }
    }

    /**
     * Gets the square of the distance between self and target
     * which saves the costly call to {@link Math#sqrt(double)}.
     * 
     * @param self
     *            Location of the local object.
     * @param target
     *            Location of the target object.
     * @return square of distance to target object from local object.
     */
    private static double getSquaredDistance(final Location self, final Location target)
    {
        double distance = Double.MAX_VALUE;
        if ((self != null) && (target != null))
        {
            distance = Math.pow(self.getX() - target.getX(), 2) + Math.pow(self.getY() - target.getY(), 2) + Math.pow(self.getZ() - target.getZ(), 2);
        }
        return distance;
    }

    /**
     * Gets a stargate based on the name passed in. Returns null if there is no gate by that name.
     * 
     * @param name
     *            String name of the Stargate you want returned.
     * @return Stargate requested. Null if no stargate by that name.
     */
    public static Stargate getStargate(final String name)
    {
        if (name == null)
        {
            return null;
        }
        final String key = normalizeGateName(name);
        if (getStargateList().containsKey(key))
        {
            return getStargateList().get(key);
        }
        else
        {
            return null;
        }
    }

    /**
     * Gets the stargate list.
     * 
     * @return the stargate list
     */
    private static ConcurrentHashMap<String, Stargate> getStargateList()
    {
        return stargateList;
    }

    /**
     * Gets the stargate network.
     * 
     * @param name
     *            the name
     * @return the stargate network
     */
    public static StargateNetwork getStargateNetwork(final String name)
    {
        if (getStargateNetworks().containsKey(name))
        {
            return getStargateNetworks().get(name);
        }
        else
        {
            return null;
        }
    }

    /**
     * Gets the stargate networks.
     * 
     * @return the stargate networks
     */
    private static ConcurrentHashMap<String, StargateNetwork> getStargateNetworks()
    {
        return stargateNetworks;
    }

    // If block is a "gate" block this returns true.
    // This is useful to stop damage from being applied from an underpriveledged user.
    // Also used to stop flow of water, and prevent portal physics
    /**
     * Checks if is block in gate.
     * 
     * @param b
     *            the b
     * @return true, if is block in gate
     */
    public static boolean isBlockInGate(final Block b)
    {
        final Location key = normalizeBlockLocation(b.getLocation());
        return getAllGateBlocks().containsKey(key) || getOpeningAnimationBlocks().containsKey(key);
    }

    /**
     * Returns true if the given block location corresponds to a portal interior
     * block for its owning Stargate (not structure blocks). This checks the
     * gate's portal block list rather than the server-side block material so
     * it works when the server keeps the logical block as AIR and renders
     * visuals to clients.
     */
    public static boolean isPortalBlock(final Block b)
    {
        if (b == null || b.getWorld() == null)
        {
            return false;
        }
        final Location norm = normalizeBlockLocation(b.getLocation());
        final Stargate s = getAllGateBlocks().get(norm);
        if (s == null)
        {
            return false;
        }
        return s.isGatePortalBlockAt(norm.getBlockX(), norm.getBlockY(), norm.getBlockZ());
    }

    /**
     * Checks if is stargate.
     * 
     * @param name
     *            the name
     * @return true, if is stargate
     */
    public static boolean isStargate(final String name)
    {
        if (name == null)
        {
            return false;
        }
        return getStargateList().containsKey(normalizeGateName(name));
    }

    private static String normalizeGateName(final String name)
    {
        return name == null ? null : name.toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the stargate that has been activated by that player.
     * Returns null if that player has not activated a gate.
     * 
     * @param p
     *            The player
     * @return Stargate that the player has activated. Null if no active gate.
     */
    public static Stargate removeActivatedStargate(final Player p)
    {
        return getActivatedStargates().remove(p);
    }

    /**
     * Remove and return the player who activated the given stargate, if any.
     * This is used to force-clear stale activations when the gate is lit but the
     * activating player mapping is missing or the activator is offline.
     *
     * @param s the stargate
     * @return the Player who activated the gate (and was removed), or null if none
     */
    public static Player removeActivatorForStargate(final Stargate s)
    {
        if (s == null)
        {
            return null;
        }
        for (final java.util.Map.Entry<Player, Stargate> e : getActivatedStargates().entrySet())
        {
            if (e.getValue() == s)
            {
                final Player p = e.getKey();
                getActivatedStargates().remove(p);
                return p;
            }
        }
        return null;
    }

    /**
     * This method removes an index mapping block location to stargate.
     * NOTE: This method does not verify that the block has actually been removed from a gate
     * so it may not persist and can be readded when server is restarted.
     * 
     * @param b
     *            the b
     */
    public static void removeBlockIndex(final Block b)
    {
        if (b != null)
        {
            final Location norm = normalizeBlockLocation(b.getLocation());
            getAllGateBlocks().remove(norm);
            GateSpatialIndex.remove(norm);
        }
    }

    /**
     * Removes an incomplete stargate from the list.
     * 
     * @param p
     *            The player who created the gate.
     */
    public static void removeIncompleteStargate(final Player p)
    {
        getIncompleteStargates().remove(p);
    }

    /**
     * Removes the stargate from the list of stargates.
     * Also removes all block from this gate from the big list of all blocks.
     * 
     * @param s
     *            The gate you want removed.
     */
    public static void removeStargate(final Stargate s)
    {
        removeStargate(s, null, true);
    }

    /**
     * Removes a gate, naming the player who did it.
     *
     * @param s
     *            the gate to remove
     * @param remover
     *            the player removing it, or null if it was not a player
     */
    public static void removeStargate(final Stargate s, final Player remover)
    {
        removeStargate(s, remover, true);
    }

    /**
     * Removes a gate, optionally without telling anyone.
     *
     * <p>{@code announce} exists for re-registration. Refreshing a gate deregisters it and
     * registers it again with freshly detected geometry, which runs through this method but
     * is not a removal: the gate is still there afterwards. Announcing it would tell a
     * listener keeping its own records about that gate to throw them away, and it would do
     * so every time anybody ran a refresh.
     *
     * <p>The event fires before any teardown, so a listener acting on it can still read the
     * gate's name, owner, network, blocks and teleport location.
     *
     * @param s
     *            the gate to remove
     * @param remover
     *            the player removing it, or null if it was not a player
     * @param announce
     *            whether to raise {@link com.wormhole_xtreme.wormhole.events.StargateRemovedEvent}
     */
    public static void removeStargate(final Stargate s, final Player remover, final boolean announce)
    {
        if (announce)
        {
            com.wormhole_xtreme.wormhole.events.GateEvents.fireRemoved(s, remover);
        }
        getStargateList().remove(normalizeGateName(s.getGateName()));
        StargateDBManager.removeStargate(s);
        detachFromNetwork(s);
        unindexGateBlocks(s);
        unindexActivationBlocks(s);
    }

    /**
     * Takes a gate off its network, and off any sign that was naming it.
     *
     * <p>A dial sign belongs to a different gate entirely, and one pointed at this gate names
     * a destination that has just stopped existing -- so it is cleared, and moved to the
     * first of whatever is left if there is anything left to move to.
     *
     * @param s
     *            the gate being removed
     */
    private static void detachFromNetwork(final Stargate s)
    {
        if (s.getGateNetwork() == null)
        {
            return;
        }
        synchronized (s.getGateNetwork().getNetworkGateLock())
        {
            s.getGateNetwork().getNetworkGateList().remove(s);
            final List<Stargate> signGates = s.getGateNetwork().getNetworkSignGateList();
            if (s.isGateSignPowered())
            {
                signGates.remove(s);
            }
            for (final Stargate s2 : signGates)
            {
                if ((s2.getGateDialSignTarget() != null)
                    && (s2.getGateDialSignTarget().getGateId() == s.getGateId())
                    && s2.isGateSignPowered())
                {
                    clearDialSign(s2, signGates.size() > 1);
                }
            }
        }
    }

    /**
     * Points one sign somewhere else, now that what it named is gone.
     *
     * @param signGate
     *            the gate whose sign was naming the removed one
     * @param hasSomewhereElse
     *            whether the network still has another sign-powered gate to offer
     */
    private static void clearDialSign(final Stargate signGate, final boolean hasSomewhereElse)
    {
        signGate.setGateDialSignTarget(null);
        if (hasSomewhereElse)
        {
            signGate.setGateDialSignIndex(0);
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                new StargateUpdateRunnable(signGate, ActionToTake.DIAL_SIGN_CLICK));
        }
    }

    /**
     * Releases every block the gate's shape claimed.
     *
     * <p>A block left indexed still answers that it belongs to a gate, and names one that no
     * longer exists.
     *
     * @param s
     *            the gate being removed
     */
    private static void unindexGateBlocks(final Stargate s)
    {
        for (final Location b : s.getGateStructureBlocks())
        {
            getAllGateBlocks().remove(b);
            GateSpatialIndex.remove(b);
        }
        for (final Location b : s.getGatePortalBlocks())
        {
            getAllGateBlocks().remove(b);
            GateSpatialIndex.remove(b);
        }
    }

    /**
     * Releases the blocks that work the gate rather than make it up.
     *
     * <p>The dial lever, iris lever, dial sign and redstone activators are indexed separately
     * from the shape, so releasing the shape alone leaves them pointing at a gate that is
     * gone.
     *
     * @param s
     *            the gate being removed
     */
    private static void unindexActivationBlocks(final Stargate s)
    {
        try
        {
            removeBlockIndexIfPresent(s.getGateDialLeverBlock());
            removeBlockIndexIfPresent(s.getGateIrisLeverBlock());
            removeBlockIndexIfPresent(s.getGateDialSignBlock());
            removeBlockIndexIfPresent(s.getGateRedstoneDialActivationBlock());
            removeBlockIndexIfPresent(s.getGateRedstoneGateActivatedBlock());
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Error removing activation block indices", e);
        }
    }

    /**
     * Releases one block, if the gate had one.
     *
     * @param block
     *            the block, or null if this gate has none of that kind
     */
    private static void removeBlockIndexIfPresent(final Block block)
    {
        if (block != null)
        {
            removeBlockIndex(block);
        }
    }

}
