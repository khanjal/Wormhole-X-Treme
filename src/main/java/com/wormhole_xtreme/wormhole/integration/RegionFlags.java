package com.wormhole_xtreme.wormhole.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * Where a region lets a player build and use gates, through WorldGuard's {@code wormhole-build}
 * and {@code wormhole-use} flags (#240).
 *
 * <p>A flag only ever takes away. Permission nodes decide who may build or use a gate, the region
 * decides where, and both must say yes; callers ask the permission first, so a player without the
 * node is told that rather than about the region. Gate owners and operators get no pass here:
 * WorldGuard's own region bypass is what exempts somebody.
 *
 * <p>Names no WorldGuard type, so it loads on a server without WorldGuard; {@link WorldGuardRegions}
 * is only touched once WorldGuard is known to be there. Off, or failing, it allows everything.
 */
public final class RegionFlags
{
    /** Told to a player a region refuses a gate's use. */
    public static final String USE_REFUSED =
        ConfigManager.MessageStrings.ERROR_HEADER.toString() + "This region does not allow using gates.";

    /** Told to a player a region refuses a gate's building. */
    public static final String BUILD_REFUSED =
        ConfigManager.MessageStrings.ERROR_HEADER.toString() + "This region does not allow building gates.";

    /** What a region can refuse, each its own WorldGuard flag. */
    public enum Action
    {
        /** Building a gate, and completing one. */
        BUILD("wormhole-build"),
        /** Dialling a gate, and travelling through one. */
        USE("wormhole-use");

        private final String flagName;

        Action(final String flagName)
        {
            this.flagName = flagName;
        }

        /**
         * The WorldGuard flag's name.
         *
         * @return what region owners type in {@code /rg flag}
         */
        public String flagName()
        {
            return flagName;
        }
    }

    /** Asks the regions at one spot; {@link WorldGuardRegions} is the real one. */
    @FunctionalInterface
    public interface Check
    {
        /**
         * Whether the regions at this spot allow the player this action.
         *
         * @param player
         *            who is asking
         * @param at
         *            where
         * @param action
         *            what for
         * @return true if allowed
         */
        boolean allows(Player player, Location at, Action action);
    }

    /** The region check in use, or null when regions restrict nothing. */
    // A function reference, not a container: volatile is the whole synchronisation it needs.
    @SuppressWarnings("java:S3077")
    private static volatile Check check = null;

    /** Whether a failing region check has been logged at WARNING yet this run. */
    private static volatile boolean warnedOfFailure = false;

    /** Static helpers only. */
    private RegionFlags()
    {
    }

    /**
     * Registers the two flags with WorldGuard, when the config asks and WorldGuard is installed.
     *
     * <p>Called from {@code onLoad}: WorldGuard's flag registry refuses new flags once plugins
     * start enabling.
     */
    public static void register()
    {
        if (!ConfigManager.isWorldGuardEnabled())
        {
            return;
        }
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        try
        {
            if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null)
            {
                plugin.prettyLog(Level.INFO,
                    "worldguard-enabled is set, but WorldGuard is not installed; regions do not restrict gates.");
                return;
            }
            check = WorldGuardRegions.register();
            plugin.prettyLog(Level.INFO, "Registered WorldGuard flags " + Action.BUILD.flagName() + " and "
                + Action.USE.flagName() + ".");
        }
        catch (final Exception | LinkageError e)
        {
            // A WorldGuard too old or new for these calls leaves regions out of it, not the plugin.
            check = null;
            plugin.prettyLog(Level.WARNING, "Could not register the WorldGuard flags; regions do not restrict gates.",
                e);
        }
    }

    /**
     * Starts refusing travel through gates in regions that deny their use, if the flags registered.
     *
     * @param plugin
     *            this plugin, to register the listener under
     */
    public static void listen(final Plugin plugin)
    {
        if (check == null)
        {
            return;
        }
        try
        {
            plugin.getServer().getPluginManager().registerEvents(new RegionTravelListener(), plugin);
        }
        catch (final Exception | LinkageError e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Could not listen for gate travel; WorldGuard regions will not stop it.", e);
        }
    }

    /**
     * Whether regions let this player use this gate, asked at its DHD and where travellers arrive.
     *
     * <p>Both, because a region drawn tightly round the gate can leave either one just outside it.
     *
     * @param player
     *            who is dialling or travelling
     * @param stargate
     *            the gate
     * @return true if allowed at both, and when there is nothing to ask about
     */
    public static boolean mayUse(final Player player, final Stargate stargate)
    {
        if (stargate == null)
        {
            return true;
        }
        final Block lever = stargate.getGateDialLeverBlock();
        return allows(player, (lever == null) ? null : lever.getLocation(), Action.USE)
            && allows(player, stargate.getGatePlayerTeleportLocation(), Action.USE);
    }

    /**
     * Whether regions let this player build this gate, at every block it takes.
     *
     * @param player
     *            the builder
     * @param stargate
     *            the gate
     * @return true if allowed everywhere it stands
     */
    public static boolean mayBuild(final Player player, final Stargate stargate)
    {
        if (stargate == null)
        {
            return true;
        }
        final List<Location> at = new ArrayList<>();
        for (final List<Location> part : Arrays.asList(stargate.getGateStructureBlocks(),
            stargate.getGatePortalBlocks()))
        {
            if (part != null)
            {
                at.addAll(part);
            }
        }
        final Block lever = stargate.getGateDialLeverBlock();
        if (lever != null)
        {
            at.add(lever.getLocation());
        }
        return mayBuild(player, at);
    }

    /**
     * Whether regions let this player build at every one of these spots.
     *
     * @param player
     *            the builder
     * @param at
     *            where the gate would stand
     * @return true if every spot allows it
     */
    public static boolean mayBuild(final Player player, final Collection<Location> at)
    {
        if ((check == null) || (at == null))
        {
            return true;
        }
        for (final Location spot : at)
        {
            if (!allows(player, spot, Action.BUILD))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Refuses a gate's use where a region denies it, telling the player.
     *
     * @param player
     *            who is dialling
     * @param stargate
     *            the gate
     * @return true if refused
     */
    public static boolean refusesUse(final Player player, final Stargate stargate)
    {
        if (mayUse(player, stargate))
        {
            return false;
        }
        player.sendMessage(USE_REFUSED);
        return true;
    }

    /**
     * Refuses a gate's building where a region denies it, telling the player.
     *
     * @param player
     *            the builder
     * @param stargate
     *            the gate
     * @return true if refused
     */
    public static boolean refusesBuild(final Player player, final Stargate stargate)
    {
        if (mayBuild(player, stargate))
        {
            return false;
        }
        player.sendMessage(BUILD_REFUSED);
        return true;
    }

    /** One spot asked, where a check that fails lets the player through. */
    private static boolean allows(final Player player, final Location at, final Action action)
    {
        final Check current = check;
        if ((current == null) || (player == null) || (at == null))
        {
            return true;
        }
        try
        {
            return current.allows(player, at, action);
        }
        catch (final Exception | LinkageError e)
        {
            // The first failure is loud: a check that always fails means regions restrict nothing.
            final Level level = warnedOfFailure ? Level.FINE : Level.WARNING;
            warnedOfFailure = true;
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            if (plugin != null)
            {
                plugin.prettyLog(level, "WorldGuard region check failed; allowing " + action.flagName(), e);
            }
            return true;
        }
    }

    /**
     * Replaces the region check, for tests.
     *
     * <p>Not part of the plugin's API: production never calls it.
     *
     * @param replacement
     *            the check to use, or null for regions to restrict nothing
     */
    public static void setCheckForTest(final Check replacement)
    {
        check = replacement;
        warnedOfFailure = false;
    }
}
