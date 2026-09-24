package com.wormhole_xtreme.wormhole.plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Reports the blocks that building and taking down gates and rings place and remove to
 * CoreProtect (#238), so an admin can roll them back like anything else.
 *
 * <p>Only construction and removal. A running gate's water, levers and horizontal iris are left
 * out: a gate that wooshes twice a minute would drown a rollback log.
 *
 * <p>CoreProtect is reached by name, as Vault is, so nothing is compiled against it. Every failure
 * is swallowed: an optional integration never stops a gate being built.
 */
public final class CoreProtectLog
{
    /** Who a change is logged as when no player made it. {@code /co rollback u:#wormhole} finds it. */
    public static final String PLUGIN_USER = "#wormhole";

    /** The oldest CoreProtect API with {@code logPlacement} and {@code logRemoval} taking block data. */
    private static final int MIN_API = 9;

    /** Where a change goes, so tests can stand in for CoreProtect. */
    @FunctionalInterface
    public interface Sink
    {
        void log(boolean placed, String user, Location at, Material type, BlockData data);
    }

    /** The sink in use, or null until CoreProtect has been looked for. */
    private static volatile Sink sink;

    /** Whether CoreProtect has been looked for, so a server without it is only asked once. */
    private static volatile boolean looked;

    private CoreProtectLog()
    {
    }

    /**
     * Logs a block the plugin is about to place. Call before the write: CoreProtect reads what
     * stands there now as what was replaced, and logs its removal itself.
     *
     * @param user
     *            the player's name, or {@link #PLUGIN_USER}
     * @param block
     *            the block, before the change
     * @param type
     *            what is about to be placed
     * @param data
     *            its block data, or null for the type's default
     */
    public static void placing(final String user, final Block block, final Material type, final BlockData data)
    {
        if ((block == null) || (type == null) || (type == Material.AIR) || !ConfigManager.isCoreProtectEnabled())
        {
            return;
        }
        send(true, user, block.getLocation(), type, data);
    }

    /**
     * Logs a block the plugin is about to take away, leaving air. Call before changing it. A block
     * being replaced by another is logged by {@link #placing} alone.
     *
     * @param user
     *            the player's name, or {@link #PLUGIN_USER}
     * @param block
     *            the block, as it stands before the change
     */
    public static void removed(final String user, final Block block)
    {
        log(false, user, block);
    }

    private static void log(final boolean placed, final String user, final Block block)
    {
        if ((block == null) || (block.getType() == Material.AIR) || !ConfigManager.isCoreProtectEnabled())
        {
            return;
        }
        send(placed, user, block.getLocation(), block.getType(), block.getBlockData());
    }

    private static void send(final boolean placed, final String user, final Location at, final Material type,
        final BlockData data)
    {
        try
        {
            final Sink to = sink();
            if (to != null)
            {
                to.log(placed, user, at, type, data);
            }
        }
        catch (final Exception | LinkageError e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "CoreProtect did not take a block change", e);
        }
    }

    private static Sink sink()
    {
        if (!looked)
        {
            looked = true;
            sink = find();
        }
        return sink;
    }

    /** CoreProtect's API, if it is installed, enabled and new enough; else null, said once. */
    private static Sink find()
    {
        try
        {
            final Plugin plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
            if ((plugin == null) || !plugin.isEnabled())
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.INFO,
                    "coreprotect-enabled is set, but CoreProtect is not running; nothing is logged to it.");
                return null;
            }
            final Object api = plugin.getClass().getMethod("getAPI").invoke(plugin);
            final boolean enabled = (Boolean) api.getClass().getMethod("isEnabled").invoke(api);
            final int version = (Integer) api.getClass().getMethod("APIVersion").invoke(api);
            if (!enabled || (version < MIN_API))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.INFO,
                    "CoreProtect's API is off or older than version " + MIN_API + "; nothing is logged to it.");
                return null;
            }
            final Class<?>[] args = { String.class, Location.class, Material.class, BlockData.class };
            final Method placement = api.getClass().getMethod("logPlacement", args);
            final Method removal = api.getClass().getMethod("logRemoval", args);
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Logging gate and ring construction to CoreProtect.");
            return (placed, user, at, type, data) ->
            {
                try
                {
                    (placed ? placement : removal).invoke(api, user, at, type,
                        (data != null) ? data : type.createBlockData());
                }
                catch (final ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            };
        }
        catch (final Exception | LinkageError e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Could not reach CoreProtect; nothing is logged to it", e);
            return null;
        }
    }

    /**
     * Replaces CoreProtect, for tests. Not part of the plugin's API.
     *
     * @param replacement
     *            where changes go, or null to look for CoreProtect afresh
     */
    public static void setSinkForTest(final Sink replacement)
    {
        sink = replacement;
        looked = replacement != null;
    }
}
