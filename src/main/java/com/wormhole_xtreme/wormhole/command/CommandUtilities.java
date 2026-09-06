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
        StringBuilder tempString = new StringBuilder();
        boolean startQuoteFound = false;
        boolean endQuoteFound = false;

        final ArrayList<String> argsPartsList = new ArrayList<String>();

        for (final String part : args)
        {
            // First check to see if we have a starting or stopping quote
            if (part.contains("\"") && !startQuoteFound)
            {
                // Two quotes in same string = no spaces in quoted text;
                if ( !part.replaceFirst("\"", "").contains("\""))
                {
                    startQuoteFound = true;
                }
            }
            else if (part.contains("\"") && startQuoteFound)
            {
                endQuoteFound = true;
            }

            // If no quotes yet, we just append to list
            if ( !startQuoteFound)
            {
                argsPartsList.add(part);
            }

            // If we have quotes we should make sure to append the values
            // if we found the last quote we should stop adding.
            if (startQuoteFound)
            {
                tempString.append(part.replace("\"", ""));
                if (endQuoteFound)
                {
                    argsPartsList.add(tempString.toString());
                    startQuoteFound = false;
                    endQuoteFound = false;
                    tempString = new StringBuilder();
                }
                else
                {
                    tempString.append(" ");
                }
            }
        }
        return argsPartsList.toArray(new String[argsPartsList.size()]);
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
        if (stargate != null)
        {
            if (stargate.getGateNetwork() != null)
            {
                return stargate.getGateNetwork().getNetworkName();
            }
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
        if (sender instanceof Player)
        {
            return true;
        }
        else
        {
            return false;
        }
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
            if (playerCheck(sender))
            {
                ((Player) sender).sendMessage(com.wormhole_xtreme.wormhole.config.ConfigManager.MessageStrings.errorHeader.toString() + "An internal error occurred. Check server logs.");
            }
            else
            {
                sender.sendMessage(com.wormhole_xtreme.wormhole.config.ConfigManager.MessageStrings.errorHeader.toString() + "An internal error occurred. Check server logs.");
            }
            return true;
        }
    }
}
