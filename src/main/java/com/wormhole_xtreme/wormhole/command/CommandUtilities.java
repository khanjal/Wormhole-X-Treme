package com.wormhole_xtreme.wormhole.command;

import java.util.ArrayList;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * WormholeXTreme Commands and command specific methods.
 * 
 * @author Dean Bailey (alron)
 * @author Ben Echols (Lologarithm)
 */
public class CommandUtilities
{
    /** A literal quote, stripped once per argument while splitting a command line. */
    private static final java.util.regex.Pattern QUOTE =
        java.util.regex.Pattern.compile("\"");

    /** Static helpers only; never instantiated. */
    private CommandUtilities()
    {
    }


    /**
     * Close gate.
     * 
     * @param stargate
     *            the stargate
     * @param iris
     *            the iris
     */
    static final void closeGate(final Stargate stargate, final boolean iris)
    {
        if (stargate != null)
        {
            if (stargate.isGateActive())
            {
                stargate.shutdownStargate(true);
                if (stargate.isGateActive())
                {
                    stargate.setGateActive(false);
                }
            }
            if (stargate.isGateLightsActive())
            {
                stargate.lightStargate(false);
                stargate.stopActivationTimer();
            }
            if (iris && stargate.isGateIrisActive())
            {
                stargate.toggleIrisActive(false);
            }
        }
    }

    /**
     * Command escaper.
     * Checks for " and escapes it.
     * 
     * @param args
     *            The String[] argument list to escape quotes on.
     * @return String[] with properly escaped quotes.
     */
    static String[] commandEscaper(final String[] args)
    {
        final Phrase phrase = new Phrase();
        final ArrayList<String> out = new ArrayList<>();
        for (final String part : args)
        {
            phrase.take(part, out);
        }
        // Deliberately not flushed: a phrase whose closing quote never arrived is dropped,
        // along with everything after it. See CommandEscaperTest, which records that rather
        // than endorsing it.
        return out.toArray(new String[out.size()]);
    }

    /**
     * The quoted phrase being rebuilt, if one is open.
     *
     * <p>Minecraft splits a command on spaces before the plugin sees it, so {@code "my gate"}
     * arrives as two arguments. This walks them one at a time and puts the phrase back
     * together.
     */
    private static final class Phrase
    {
        private final StringBuilder words = new StringBuilder();
        private boolean open;

        /**
         * Takes one argument, adding either it or a finished phrase to the result.
         *
         * @param part
         *            the argument as Minecraft split it
         * @param out
         *            the arguments to hand the command, appended to
         */
        void take(final String part, final ArrayList<String> out)
        {
            final boolean quoted = part.contains("\"");
            if (!open)
            {
                // A word carrying both quotes is not a phrase that needs rejoining, so it is
                // passed through as it came -- quotes included. Recorded in the tests.
                if (quoted && !QUOTE.matcher(part).replaceFirst("").contains("\""))
                {
                    open = true;
                    words.append(part.replace("\"", "")).append(" ");
                }
                else
                {
                    out.add(part);
                }
                return;
            }
            words.append(part.replace("\"", ""));
            if (quoted)
            {
                out.add(words.toString());
                words.setLength(0);
                open = false;
            }
            else
            {
                words.append(" ");
            }
        }
    }

    /**
     * Gate remove.
     * 
     * @param stargate
     *            the stargate
     * @param destroy
     *            true to destroy gate blocks
     */
    public static void gateRemove(final Stargate stargate, final boolean destroy)
    {
        gateRemove(stargate, destroy, true, null);
    }

    /**
     * Tears a gate down, optionally without announcing it.
     *
     * <p>Pass {@code announce} false when the gate is being deregistered in order to be
     * registered again, such as a refresh picking up freshly detected geometry. That is not
     * a removal and listeners should not be told it is one.
     *
     * @param stargate
     *            the gate
     * @param destroy
     *            whether to delete the gate's blocks as well as its registration
     * @param announce
     *            whether this is a real removal listeners should hear about
     */
    public static void gateRemove(final Stargate stargate, final boolean destroy, final boolean announce)
    {
        gateRemove(stargate, destroy, announce, null);
    }

    /**
     * Tears a gate down, naming the player responsible.
     *
     * @param stargate
     *            the gate
     * @param destroy
     *            whether to delete the gate's blocks as well as its registration
     * @param announce
     *            whether this is a real removal listeners should hear about
     * @param remover
     *            the player removing it, or null if it was not a player
     */
    public static void gateRemove(final Stargate stargate, final boolean destroy, final boolean announce,
                                  final org.bukkit.entity.Player remover)
    {
        // Ensure the gate is fully deactivated and cleaned up before removal.
        try
        {
            stargate.shutdownStargate(false);
        }
        catch (final Exception e)
        {
            // Be conservative: log and continue with removal to avoid leaving stale DB entries.
            com.wormhole_xtreme.wormhole.WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.WARNING, "Error shutting down gate before removal: " + e.getMessage());
        }
        // Remove any activator/player mapping referencing this stargate.
        try
        {
            com.wormhole_xtreme.wormhole.model.StargateManager.removeActivatorForStargate(stargate);
        }
        catch (final Exception e)
        {
            com.wormhole_xtreme.wormhole.WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.FINE, "No activator mapping to remove or error: " + e.getMessage());
        }

        stargate.setupGateSign(false);
        stargate.resetTeleportSign();
        if ( !stargate.getGateIrisDeactivationCode().equals(""))
        {
            if (stargate.isGateIrisActive())
            {
                stargate.toggleIrisActive(false);
            }
            stargate.setupIrisLever(false);
        }
        if (stargate.isGateRedstonePowered())
        {
            stargate.setupRedstone(false);
        }
        if (destroy)
        {
            stargate.deleteGateBlocks();
            stargate.deletePortalBlocks();
            stargate.deleteTeleportSign();
        }
        StargateManager.removeStargate(stargate, remover, announce);
    }

    /**
     * Gets the gate network.
     * 
     * @param stargate
     *            the stargate
     * @return the gate network
     */
    static String getGateNetwork(final Stargate stargate)
    {
        if ((stargate != null) && (stargate.getGateNetwork() != null))
        {
            return stargate.getGateNetwork().getNetworkName();
        }
        return "Public";
    }

    /**
     * Checks if is boolean.
     * 
     * @param booleanString
     *            the boolean string
     * @return true, if is boolean
     */
    public static boolean isBoolean(final String booleanString)
    {
        return booleanString.equalsIgnoreCase("true") || booleanString.equalsIgnoreCase("false");
    }

    /**
     * Player check.
     * 
     * @param sender
     *            the sender
     * @return true, if successful
     */
    public static boolean playerCheck(final CommandSender sender)
    {
        return sender instanceof Player;
    }

    /**
     * Run a command body safely, catching any Throwable and reporting a friendly
     * message to the command sender (and logging the error).
     *
     * @param sender   command sender
     * @param callable the command body to execute
     * @return the boolean result the callable returned, or true if an error occurred
     */
    public static boolean runCommandSafe(final CommandSender sender, final java.util.concurrent.Callable<Boolean> callable)
    {
        try
        {
            return callable.call();
        }
        catch (final Exception t)
        {
            com.wormhole_xtreme.wormhole.WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.WARNING, "Error executing command: " + t.getMessage());
            // Everyone is told the same thing, console included: the failure is logged
            // server-side, and neither a player nor an operator can act on more than that.
            sender.sendMessage(com.wormhole_xtreme.wormhole.config.ConfigManager.MessageStrings.ERROR_HEADER.toString() + "An internal error occurred. Check server logs.");
            return true;
        }
    }
}
