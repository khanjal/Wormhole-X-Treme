package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.CommandUtilities;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.ChatText;


/**
 * Handler for '/wormhole custom'
 */
public class CustomCommand implements SubCommand
{

    // Bukkit reads the boolean as "handled"; every path here has handled it.
    @SuppressWarnings("java:S3516")
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        // Gate management was never actually gated: none of these commands checked a
        // permission at all, so any player able to run /wormhole could reconfigure or
        // reassign any gate on the server. wormhole.config is what an admin already needs
        // for /wormhole config, so it is reused here rather than inventing a second
        // admin-only node that would mean the same thing.
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return true;
        }

        if ((args.length != 2) && (args.length != 3))
        {
            sendUsage(sender);
            return true;
        }

        if (args[1].equalsIgnoreCase("-clean"))
        {
            cleanSnapshottedOverrides(sender, (args.length == 3) && "-confirm".equalsIgnoreCase(args[2]));
            return true;
        }
        if (args[1].equalsIgnoreCase("-all") && (args.length == 3) && CommandUtilities.isBoolean(args[2]))
        {
            setEveryGateCustom(sender, args[2]);
            return true;
        }
        if (!StargateManager.isStargate(args[1]))
        {
            sender.sendMessage(CommandUtilities.noGateCalled(args[1]));
            sendUsage(sender);
            return true;
        }

        final Stargate stargate = StargateManager.getStargate(args[1]);
        if (args.length == 2)
        {
            reportCustom(sender, stargate);
            sender.sendMessage(ChatText.info("Change it with " + ChatText.command("true") + " or "
                + ChatText.command("false") + "."));
            return true;
        }
        setOneGateCustom(sender, stargate, args[2]);
        return true;
    }

    /** Says whether a gate is in custom mode. */
    private static void reportCustom(final CommandSender sender, final Stargate stargate)
    {
        sender.sendMessage(ChatText.info(ChatText.name(stargate.getGateName()) + " custom mode: "
            + ChatText.value(String.valueOf(stargate.isGateCustom())) + "."));
    }

    /** Turns custom mode on or off for every gate that has a shape to base it on. */
    private static void setEveryGateCustom(final CommandSender sender, final String value)
    {
        for (final Stargate stargate : StargateManager.getAllGates())
        {
            CommandHandlerUtils.setGateCustomAll(stargate, Boolean.parseBoolean(value));
        }
        sender.sendMessage(ChatText.info("Custom mode set to " + ChatText.value(value)
            + " on every gate with a shape."));
    }

    /**
     * Turns custom mode on or off for one gate.
     *
     * <p>Refused without a shape, since custom mode copies from it: the gate would otherwise
     * be left claiming to be custom with nothing behind it. And a word that is neither true
     * nor false is refused rather than read as false, which would quietly turn the mode off
     * for an admin who mistyped the word they meant to turn it on with.
     */
    private static void setOneGateCustom(final CommandSender sender, final Stargate stargate,
        final String value)
    {
        if (!CommandUtilities.isBoolean(value))
        {
            sender.sendMessage(ChatText.error("Custom mode is " + ChatText.command("true") + " or "
                + ChatText.command("false") + ", not " + ChatText.command(value) + "."));
            sendUsage(sender);
            return;
        }
        if (stargate.getGateShape() == null)
        {
            sender.sendMessage(ChatText.error(ChatText.name(stargate.getGateName())
                + " has no gate shape to base custom data on. Make sure its shape file is loaded."));
            return;
        }
        CommandHandlerUtils.setGateCustomAll(stargate, Boolean.parseBoolean(value));
        reportCustom(sender, stargate);
    }

    /** How the command is used, written once. */
    private static void sendUsage(final CommandSender sender)
    {
        sender.sendMessage(ChatText.info(ChatText.usage("/wormhole custom <gate|-all|-clean> [true|false|-confirm]")));
    }


    /**
     * Clears material overrides that were snapshotted from a shape rather than chosen.
     *
     * <p>{@code /wormhole custom <gate> true} used to copy the shape's four materials into
     * the gate's own override fields. Those copies pin the gate to whatever the defaults
     * were at the time and stop it following its material group, which is rarely what the
     * admin wanted — they only asked to turn custom mode on.
     *
     * <p>A coincidental match is not enough to act on: someone who deliberately set an
     * iris to stone meant stone. Only a gate whose <em>whole set</em> of four overrides
     * equals the built-in defaults is treated as a snapshot, since a person choosing all
     * four to be exactly the defaults would have had no reason to set them at all.
     *
     * <p>Reports by default and only writes when told to, because it edits stored gates.
     *
     * @param sender
     *            who asked
     * @param confirmed
     *            true to apply the change, false to only report what would change
     */
    private static void cleanSnapshottedOverrides(final CommandSender sender, final boolean confirmed)
    {
        final List<Stargate> affected = StargateManager.getAllGatesUnsorted().stream()
            .filter(CustomCommand::hasDefaultSnapshotOverrides)
            .toList();

        if (affected.isEmpty())
        {
            sender.sendMessage(ChatText.info("No gates are carrying snapshotted material overrides."));
            return;
        }

        if (!confirmed)
        {
            sender.sendMessage(ChatText.info(ChatText.value(String.valueOf(affected.size()))
                + " gate(s) carry material overrides matching the built-in defaults:"));
            sender.sendMessage(ChatText.info(affected.stream().map(g -> ChatText.name(g.getGateName()))
                .collect(Collectors.joining(", "))));
            sender.sendMessage(ChatText.info("Clearing them lets those gates follow their material group."));
            sender.sendMessage(ChatText.info("Run " + ChatText.command("/wormhole custom -clean -confirm") + " to apply."));
            return;
        }

        for (final Stargate gate : affected)
        {
            gate.setGateCustomStructureMaterial(null);
            gate.setGateCustomPortalMaterial(null);
            gate.setGateCustomLightMaterial(null);
            gate.setGateCustomIrisMaterial(null);
            StargateDBManager.saveStargate(gate);
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO,
            "Cleared snapshotted material overrides from " + affected.size() + " gate(s): " + gateNames(affected));
        sender.sendMessage(ChatText.info("Cleared material overrides on " + ChatText.value(String.valueOf(affected.size()))
            + " gate(s); they now follow their material group."));
    }

    /**
     * Checks whether a gate's four material overrides are all set and all equal to the
     * built-in defaults — the signature of a snapshot rather than a deliberate choice.
     *
     * @param gate
     *            the gate to test
     * @return true if the overrides look snapshotted
     */
    private static boolean hasDefaultSnapshotOverrides(final Stargate gate)
    {
        return gate.getGateCustomStructureMaterial() == Material.OBSIDIAN
            && gate.getGateCustomPortalMaterial() == Material.WATER
            && gate.getGateCustomIrisMaterial() == Material.STONE
            && gate.getGateCustomLightMaterial() == Material.GLOWSTONE;
    }

    private static String gateNames(final List<Stargate> gates)
    {
        final StringBuilder sb = new StringBuilder();
        for (final Stargate g : gates)
        {
            if (!sb.isEmpty())
            {
                sb.append(", ");
            }
            sb.append(g.getGateName());
        }
        return sb.toString();
    }
}
