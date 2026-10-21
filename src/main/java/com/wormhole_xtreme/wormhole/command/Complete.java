package com.wormhole_xtreme.wormhole.command;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.GateSpacing;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * The Class Complete.
 * 
 * @author alron
 */
public class Complete implements CommandExecutor, TabCompleter
{
    /** Drops a completion waiting for a DHD click, or a detected gate not yet named. */
    public static final String CANCEL = "-cancel";

    /** Says how the command is used. */
    public static final String HELP = "-help";

    /** Completions a player has been asked to confirm, holding their name, idc and network. */
    private static final ConcurrentHashMap<Player, String[]> pendingCompletions = new ConcurrentHashMap<>();

    public static void addPendingCompletion(final Player p, final String name, final String idc, final String network)
    {
        pendingCompletions.put(p, new String[] { name, idc, network });
    }

    public static String[] getPendingCompletion(final Player p)
    {
        return pendingCompletions.get(p);
    }

    public static void removePendingCompletion(final Player p)
    {
        pendingCompletions.remove(p);
    }

    /** What {@code /wormhole complete} takes. */
    private static final String USAGE = "/wormhole complete <name> [idc=<code>] [net=<network>]";

    /** The longest gate name there is room for. */
    private static final int LONGEST_NAME = 11;

    /**
     * Why a gate cannot be called this, or null if it can. Not whether the name is taken.
     *
     * @param sender
     *            who will read it
     * @param name
     *            the name asked for
     * @return the body of an error line, or null
     */
    public static String whyNotAName(final CommandSender sender, final String name)
    {
        // Words starting with a dash are options, so a gate may not be called one.
        if (name.startsWith("-"))
        {
            return Messages.get(sender, "gate.complete.name-dash", ChatText.command("-"), ChatText.command(CANCEL));
        }
        if (name.length() > LONGEST_NAME)
        {
            return Messages.get(sender, "gate.complete.name-too-long", ChatText.name(name),
                ChatText.value(String.valueOf(LONGEST_NAME)));
        }
        return null;
    }

    /**
     * Do complete.
     * 
     * @param args
     *            the gate's name, or {@code cancel}, then any {@code key=value} options
     */
    private static void doComplete(final Player player, final String[] args)
    {
        final String name = args[0].trim().replace("\n", "").replace("\r", "");
        if (CANCEL.equalsIgnoreCase(name))
        {
            final boolean waiting = (getPendingCompletion(player) != null)
                || (StargateManager.getIncompleteStargateName(player) != null);
            removePendingCompletion(player);
            StargateManager.removeIncompleteStargate(player);
            final String reply;
            if (waiting)
            {
                reply = Messages.get(player, "gate.complete.cancelled");
            }
            else
            {
                reply = Messages.get(player, "gate.complete.nothing-to-cancel");
            }
            player.sendMessage(ChatText.info(reply));
            return;
        }
        final String badName = whyNotAName(player, name);
        if (badName != null)
        {
            player.sendMessage(ChatText.error(badName));
            return;
        }

        final String[] options = parseOptions(args);
        final String idc = options[0];
        final String network = options[1];

        if (!WXPermissions.checkWXPermissions(player, network, PermissionType.BUILD))
        {
            player.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return;
        }
        if (StargateManager.getStargate(name) != null)
        {
            player.sendMessage(ChatText.error(Messages.get(player, "gate.complete.name-taken", ChatText.name(name))));
            return;
        }

        final String incompleteName = StargateManager.getIncompleteStargateName(player);
        if (incompleteName == null)
        {
            awaitDhdClick(player, name, idc, network);
            return;
        }
        finishBuiltGate(player, name, idc, network, incompleteName);
    }

    /**
     * Reads the optional {@code idc=} and {@code net=} arguments.
     *
     * <p>Tolerates a space after the equals, which is the common way to mistype these:
     * {@code idc= 1234} arrives as two arguments and means what {@code idc=1234} means.
     * Anything that is not a key=value pair is passed over rather than refused.
     *
     * @return the IDC and the network, in that order, each empty if not given
     */
    private static String[] parseOptions(final String[] args)
    {
        String idc = "";
        String network = "";
        // Walked with an explicit cursor: a key whose value was typed after the equals sign
        // consumes the argument that follows it, so the loop has to be able to skip one.
        int i = 1;
        while (i < args.length)
        {
            final String token = args[i];
            i++;
            // A null, an empty token and one without an equals sign are all the same thing
            // here: not a key=value pair, so not for us.
            final int eqPos = (token == null) ? -1 : token.indexOf('=');
            if (eqPos < 0)
            {
                continue;
            }
            final String key = token.substring(0, eqPos).trim();
            String value = token.substring(eqPos + 1).trim();
            if (value.isEmpty() && (i < args.length) && !args[i].contains("="))
            {
                value = args[i].trim();
                i++;
            }
            if ("idc".equalsIgnoreCase(key))
            {
                idc = value;
            }
            else if ("net".equalsIgnoreCase(key))
            {
                network = value;
            }
        }
        return new String[] {idc, network};
    }

    /**
     * Waits for the player to click the DHD, having nothing part-built to finish.
     *
     * <p>This is how a gate built without {@code /wormhole build} gets completed: the name
     * comes first and the click says which gate it belongs to.
     */
    private static void awaitDhdClick(final Player player, final String name, final String idc,
        final String network)
    {
        addPendingCompletion(player, name, idc, network);
        player.sendMessage(ChatText.info(Messages.get(player, "gate.complete.click-dhd")));
        player.sendMessage(ChatText.info(Messages.get(player, "gate.complete.options-hint",
            ChatText.command("idc=<code>"), ChatText.command("net=<network>"), ChatText.name(name))));
        player.sendMessage(ChatText.info(Messages.get(player, "gate.complete.cancel-hint",
            ChatText.command("/wormhole gate complete " + CANCEL))));
    }

    /** Finishes the gate this player already has part-built, charging for it if the server does. */
    private static void finishBuiltGate(final Player player, final String name, final String idc,
        final String network, final String incompleteName)
    {
        rereadDesign(player);
        completeAndCharge(player, name, idc, network,
            Messages.get(player, "gate.complete.failed", ChatText.name(incompleteName)),
            "/wormhole complete failed for player " + player.getName() + " - incomplete gate exists: " + incompleteName);
    }

    /**
     * Detects the part-built gate again, since a dial sign hung after it was first detected is
     * otherwise never taken up; a frame that no longer reads, or a shape that throws, keeps the
     * gate already held.
     *
     * <p>Without a sign a plain shape can win over its sign twin -- Horizontal and
     * HorizontalSignDial share a frame -- so a sign found by any shape wins over the held one.
     */
    private static void rereadDesign(final Player player)
    {
        final Stargate held = StargateManager.getIncompleteStargate(player);
        if (held == null)
        {
            return;
        }
        Stargate fresh;
        try
        {
            fresh = StargateHelper.checkStargate(held.getGateDialLeverBlock(),
                held.getGateFacing(), held.getGateShape());
            if ((fresh == null) || !fresh.isGateSignPowered())
            {
                final Stargate signed = StargateHelper.checkNewStargate(held.getGateDialLeverBlock(),
                    held.getGateFacing());
                if ((signed != null) && signed.isGateSignPowered())
                {
                    fresh = signed;
                }
            }
        }
        catch (final RuntimeException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Re-reading " + held.getGateName() + " failed; completing it as first detected", e);
            return;
        }
        if (fresh != null)
        {
            StargateManager.addIncompleteStargate(player, fresh);
        }
    }

    /**
     * Completes a gate the player has named, charging for it if the server does.
     *
     * <p>Shared with the interactive route in GateInteractionHandler, which reaches this same
     * point from a click on the DHD rather than from the command line. The two differ only in
     * what they can say when the gate will not complete: this one can name the half-built gate
     * it found, and the click cannot, because the click is what found the gate.
     *
     * @param onRefused
     *            what to tell the player if the gate will not complete, as the body of an error line
     * @param refusedLog
     *            what to log alongside it, or null to log nothing
     * @return true if the gate was completed
     */
    public static boolean completeAndCharge(final Player player, final String name, final String idc,
        final String network, final String onRefused, final String refusedLog)
    {
        final Stargate held = StargateManager.getIncompleteStargate(player);
        final String tooClose = (held == null) ? null : GateSpacing.refusal(player, held);
        if (tooClose != null)
        {
            player.sendMessage(ChatText.error(tooClose));
            return false;
        }
        final double buildCost = (ConfigManager.isEconomyEnabled() && EconomySupport.isAvailable())
            ? ConfigManager.getEconomyBuildCost()
            : 0.0;
        if ((buildCost > 0) && !EconomySupport.canAfford(player, buildCost))
        {
            player.sendMessage(ConfigManager.MessageStrings.ECONOMY_INSUFFICIENT_FUNDS.toString());
            return false;
        }
        if (!StargateManager.completeStargate(player, name, idc, network))
        {
            player.sendMessage(ChatText.error(onRefused));
            if (refusedLog != null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, refusedLog);
            }
            return false;
        }
        player.sendMessage(ConfigManager.MessageStrings.CONSTRUCT_SUCCESS.toString());
        if (buildCost > 0)
        {
            EconomySupport.charge(player, buildCost);
            player.sendMessage(ChatText.info(Messages.get(player, "gate.complete.charged",
                ChatText.value(buildCost + " " + EconomySupport.currencyName(buildCost)))));
        }
        return true;
    }

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        final String[] arguments = CommandUtilities.commandEscaper(args);
        if ((arguments.length <= 3) && (arguments.length > 0))
        {
            if (arguments[0].equalsIgnoreCase(HELP))
            {
                if (CommandUtilities.playerCheck(sender))
                {
                    final Player player = (Player) sender;
                    player.sendMessage(ChatText.info(ChatText.usage(USAGE)));
                    player.sendMessage(ChatText.info(Messages.get(player, "gate.complete.example",
                        ChatText.command("/wormhole complete MyGate idc=1234 net=Private"),
                        ChatText.command("/wx complete"))));
                }
                return true;
            }
            if (CommandUtilities.playerCheck(sender))
            {
                try
                {
                    doComplete((Player) sender, arguments);
                    return true;
                }
                catch (final Exception e)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Error executing /wormhole complete", e);
                    final Player player = (Player) sender;
                    player.sendMessage(ChatText.error(Messages.get(player, "gate.complete.bad-arguments",
                        ChatText.usage(USAGE))));
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args)
    {
        // Disable Bukkit's default player-name autocompletion for /wxcomplete.
        // Return an empty list so the client receives no suggestions.
        return Collections.emptyList();
    }

}
