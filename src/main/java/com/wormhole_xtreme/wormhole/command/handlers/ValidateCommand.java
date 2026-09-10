package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.GateIntegrity;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;

/**
 * Handler for '/wormhole gate validate' -- the third of the four things #54 asked for.
 *
 * <p>WorldEdit writes blocks straight into the world and fires nothing this plugin protects
 * itself with, so a gate it tears apart stays fully registered with nothing standing. Dialling
 * and redrawing a dial sign already notice that themselves, the moment it matters. This is the
 * same check ({@link GateIntegrity}) run on demand, for a gate nobody has clicked or dialled in
 * a while -- an admin asking "is this one actually still there" without waiting for a player
 * to find out the hard way.
 */
public class ValidateCommand implements SubCommand
{
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (refusedForPermissions(sender))
        {
            return true;
        }
        if (args.length < 2)
        {
            sender.sendMessage(ConfigManager.MessageStrings.GATE_NOT_SPECIFIED.toString());
            return false;
        }
        if ("-all".equalsIgnoreCase(args[1]))
        {
            return validateAll(sender);
        }

        final Stargate gate = StargateManager.getStargate(args[1]);
        if (gate == null)
        {
            sender.sendMessage(ConfigManager.MessageStrings.CONSTRUCT_NAME_INVALID.toString()
                + "\"" + args[1] + "\"");
            return true;
        }
        report(sender, gate, true);
        return true;
    }

    /**
     * Whether this sender may not validate gates.
     *
     * <p>Reuses {@code wormhole.config}, the same node {@code regenerate} checks: it is what an
     * admin already needs for gate maintenance, and a second node meaning the same thing would
     * only be one more permission to remember to grant.
     *
     * @param sender
     *            who is asking
     * @return true if they were refused and told so
     */
    private static boolean refusedForPermissions(final CommandSender sender)
    {
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return true;
        }
        return false;
    }

    /**
     * Checks every gate on the server, and names only the ones with something wrong.
     *
     * <p>A clean gate is not listed individually -- the summary line already says how many
     * there were -- so this stays useful on a server with hundreds of gates and nothing broken.
     *
     * @param sender
     *            who asked
     * @return true, the command was handled
     */
    private static boolean validateAll(final CommandSender sender)
    {
        int checked = 0;
        final List<Stargate> broken = new ArrayList<>();
        for (final Stargate gate : StargateManager.getAllGates())
        {
            checked++;
            if (isBroken(gate))
            {
                broken.add(gate);
            }
        }
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
            + "Checked " + checked + " gate" + (checked == 1 ? "" : "s") + ". "
            + broken.size() + " " + (broken.size() == 1 ? "has" : "have") + " something missing.");
        for (final Stargate gate : broken)
        {
            report(sender, gate, false);
        }
        return true;
    }

    /** Whether {@link GateIntegrity} would flag anything about this gate. */
    private static boolean isBroken(final Stargate gate)
    {
        return (GateIntegrity.missingStructureBlocks(gate) > 0) || GateIntegrity.isDialSignMissing(gate);
    }

    /**
     * Tells {@code sender} what, if anything, is wrong with one gate.
     *
     * @param sender
     *            who to tell
     * @param gate
     *            the gate that was checked
     * @param sayIfClean
     *            whether to say so when nothing is wrong; the {@code -all} sweep already gives
     *            a clean count and does not need every clean gate named as well
     */
    private static void report(final CommandSender sender, final Stargate gate, final boolean sayIfClean)
    {
        final int missing = GateIntegrity.missingStructureBlocks(gate);
        final boolean signMissing = GateIntegrity.isDialSignMissing(gate);
        if ((missing == 0) && !signMissing)
        {
            if (sayIfClean)
            {
                sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                    + gate.getGateName() + " is standing where it says it is.");
            }
            return;
        }
        final StringBuilder problems = new StringBuilder();
        if (missing > 0)
        {
            problems.append(missing).append(" frame block").append(missing == 1 ? "" : "s")
                .append(" missing");
        }
        if (signMissing)
        {
            if (!problems.isEmpty())
            {
                problems.append("; ");
            }
            problems.append("dial sign missing");
        }
        sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
            + gate.getGateName() + ": " + problems + ".");
    }
}
