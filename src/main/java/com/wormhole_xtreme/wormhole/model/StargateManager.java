package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
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
    // A list of all blocks contained by all stargates. Makes for easy indexing when a player is trying
    // to enter a gate or if water is trying to flow out, also will contain the stone buttons used to activate.
    /** The all_gate_blocks. */
    private final static ConcurrentHashMap<Location, Stargate> allGateBlocks = new ConcurrentHashMap<Location, Stargate>();
    // List of All stargates indexed by name. Useful for dialing and such
    /** The stargate_list. */
    private final static ConcurrentHashMap<String, Stargate> stargateList = new ConcurrentHashMap<String, Stargate>();
    // List of stargates built but not named. Indexed by the player that built it.
    /** The incomplete_stargates. */
    private final static ConcurrentHashMap<Player, Stargate> incompleteStargates = new ConcurrentHashMap<Player, Stargate>();
    // List of stargates that have been activated but not yet dialed. Only used for gates without public use sign.
    /** The activated_stargates. */
    private final static ConcurrentHashMap<Player, Stargate> activatedStargates = new ConcurrentHashMap<Player, Stargate>();
    // List of networks indexed by their name
    /** The stargate_networks. */
    private final static ConcurrentHashMap<String, StargateNetwork> stargateNetworks = new ConcurrentHashMap<String, StargateNetwork>();
    // List of players ready to build a stargate, with the shape they are trying to build.
    /** The player_builders. */
    private final static ConcurrentHashMap<Player, StargateShape> playerBuilders = new ConcurrentHashMap<Player, StargateShape>();

    // List of blocks that are part of an active animation. Only use this to make sure water doesn't flow everywhere.
    /** The Constant opening_animation_blocks. */
    private static final ConcurrentHashMap<Location, Block> openingAnimationBlocks = new ConcurrentHashMap<Location, Block>();
    // Keep the original material for each animated block so we can restore it after the woosh
    private static final ConcurrentHashMap<Location, Material> openingAnimationOriginalMaterials = new ConcurrentHashMap<Location, Material>();

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
        // s.ActivateStargate();
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
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Indexed gate block: gate=" + s.getGateName() + " loc=" + b.getLocation().toString() + " type=" + b.getType().toString());
            }
            catch (final Exception e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Error logging indexed block: " + e.getMessage());
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
                if (gate.isGateSignPowered())
                {
                    if (!net.getNetworkSignGateList().contains(gate))
                    {
                        net.getNetworkSignGateList().add(gate);
                    }
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
            if (s.getGateRedstoneGateActivatedBlock() != null)
            {
                addBlockIndex(s.getGateRedstoneGateActivatedBlock(), s);
            }
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Error indexing gate activation blocks: " + e.getMessage());
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

        if (complete != null)
        {
            if ( !network.equals(""))
            {
                StargateNetwork net = StargateManager.getStargateNetwork(network);
                if (net == null)
                {
                    net = StargateManager.addStargateNetwork(network);
                }
                StargateManager.addGateToNetwork(complete, network);
                complete.setGateNetwork(net);
            }

            complete.setGateOwner(p.getUniqueId().toString());
            complete.setGateOwnerName(p.getName());
            complete.completeGate(name, idc);
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Player: " + p.getName() + " completed a wormhole: " + complete.getGateName());
            addStargate(complete);
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Gate debug: Name=" + complete.getGateName()
                        + " Owner=" + complete.getGateOwner()
                        + " DialLever=" + (complete.getGateDialLeverBlock() != null ? complete.getGateDialLeverBlock().getLocation().toString() : "null")
                        + " DialLeverType=" + (complete.getGateDialLeverBlock() != null ? complete.getGateDialLeverBlock().getType().toString() : "null")
                        + " IrisLever=" + (complete.getGateIrisLeverBlock() != null ? complete.getGateIrisLeverBlock().getLocation().toString() : "null")
                        + " IrisLeverType=" + (complete.getGateIrisLeverBlock() != null ? complete.getGateIrisLeverBlock().getType().toString() : "null")
                        + " DialSignBlock=" + (complete.getGateDialSignBlock() != null ? complete.getGateDialSignBlock().getLocation().toString() : "null")
                        + " DialSignType=" + (complete.getGateDialSignBlock() != null ? complete.getGateDialSignBlock().getType().toString() : "null")
                        + " RedstoneDial=" + (complete.getGateRedstoneDialActivationBlock() != null ? complete.getGateRedstoneDialActivationBlock().getLocation().toString() : "null")
                        + " RedstoneDialType=" + (complete.getGateRedstoneDialActivationBlock() != null ? complete.getGateRedstoneDialActivationBlock().getType().toString() : "null")
                        + " RedstoneGateActivated=" + (complete.getGateRedstoneGateActivatedBlock() != null ? complete.getGateRedstoneGateActivatedBlock().getLocation().toString() : "null")
                        + " RedstoneGateActivatedType=" + (complete.getGateRedstoneGateActivatedBlock() != null ? complete.getGateRedstoneGateActivatedBlock().getType().toString() : "null")
                    );
            StargateDBManager.saveStargate(complete);

            // For sign-powered gates, initialize the DHD sign by cycling to the first available target.
            if (complete.isGateSignPowered() && complete.getGateDialSignBlock() != null)
            {
                try
                {
                    StargateDialManager.teleportSignClicked(complete, true);
                }
                catch (final Throwable ignore) {}
            }

            return true;
        }

        return false;
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
            final ArrayList<Location> gateblocks = stargate.getGateStructureBlocks();
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
            final ArrayList<Stargate> gates = StargateManager.getAllGates();
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
     * Get all gates, sorted by name.
     * This copies and sorts, so prefer {@link #getAllGatesUnsorted()} when iterating.
     *
     * @return the array list
     */
    public static ArrayList<Stargate> getAllGates()
    {
        final ArrayList<Stargate> gates = new ArrayList<Stargate>();

        final Enumeration<Stargate> keys = getStargateList().elements();

        while (keys.hasMoreElements())
        {
            gates.add(keys.nextElement());
        }

        java.util.Collections.sort(gates, new java.util.Comparator<Stargate>()
        {
            @Override
            public int compare(final Stargate a, final Stargate b)
            {
                return a.getGateName().compareToIgnoreCase(b.getGateName());
            }
        });

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
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Gate lookup: loc=" + b.getLocation() + " type=" + b.getType() + " indexed=" + contains);
        }
        if (contains)
        {
            final Stargate s = getAllGateBlocks().get(key);
            if (WormholeXTreme.getThisPlugin() != null && WormholeXTreme.getThisPlugin().isLoggable(Level.FINE))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Gate lookup hit: gate=" + (s != null ? s.getGateName() : "null") + " for loc=" + b.getLocation());
            }
            return s;
        }
        if (WormholeXTreme.getThisPlugin() != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Gate lookup miss for loc=" + b.getLocation().toString());
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
        final Stargate s = getActivatedStargates().remove(p);
        //	if ( s != null )
        //		s.DeActivateStargate();
        return s;
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
        getStargateList().remove(normalizeGateName(s.getGateName()));
        StargateDBManager.removeStargate(s);
        if (s.getGateNetwork() != null)
        {
            synchronized (s.getGateNetwork().getNetworkGateLock())
            {
                s.getGateNetwork().getNetworkGateList().remove(s);
                if (s.isGateSignPowered())
                {
                    s.getGateNetwork().getNetworkSignGateList().remove(s);
                }

                for (final Stargate s2 : s.getGateNetwork().getNetworkSignGateList())
                {
                    if ((s2.getGateDialSignTarget() != null) && (s2.getGateDialSignTarget().getGateId() == s.getGateId()) && s2.isGateSignPowered())
                    {
                        s2.setGateDialSignTarget(null);
                        if (s.getGateNetwork().getNetworkSignGateList().size() > 1)
                        {
                            s2.setGateDialSignIndex(0);
                            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(s2, ActionToTake.DIAL_SIGN_CLICK));
                            // s2.teleportSignClicked();
                        }
                    }
                }
            }
        }

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
        // Also remove any explicit activation-related blocks (dial lever, iris lever, dial sign, redstone activators)
        try
        {
            if (s.getGateDialLeverBlock() != null)
            {
                removeBlockIndex(s.getGateDialLeverBlock());
            }
            if (s.getGateIrisLeverBlock() != null)
            {
                removeBlockIndex(s.getGateIrisLeverBlock());
            }
            if (s.getGateDialSignBlock() != null)
            {
                removeBlockIndex(s.getGateDialSignBlock());
            }
            if (s.getGateRedstoneDialActivationBlock() != null)
            {
                removeBlockIndex(s.getGateRedstoneDialActivationBlock());
            }
            if (s.getGateRedstoneGateActivatedBlock() != null)
            {
                removeBlockIndex(s.getGateRedstoneGateActivatedBlock());
            }
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Error removing activation block indices: " + e.getMessage());
        }
    }

}
