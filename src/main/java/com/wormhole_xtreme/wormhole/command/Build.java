package com.wormhole_xtreme.wormhole.command;

import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;
import com.wormhole_xtreme.wormhole.model.preview.PreviewPermissions;

/**
 * {@code /wormhole gate build <shape> [group]} and {@code /wormhole gate build clear [all]}.
 *
 * <p>Choosing a shape checks the next DHD button pressed against that shape alone. With
 * {@code wormhole.build.preview} it also stands the shape up full size in front of the player,
 * seen by them alone, to build by.
 */
public class Build implements CommandExecutor
{
    /** The word that takes previews away rather than naming a shape. */
    public static final String CLEAR = "clear";

    /** After {@link #CLEAR}: every preview, not only the one looked at. */
    public static final String ALL = "all";

    private static void doBuild(final Player player, final String[] args)
    {
        final boolean mayPreview = PreviewPermissions.mayPreview(player);
        if (!mayPreview && CommandHandlerUtils.lacksConfigPermission(player))
        {
            return;
        }
        if (CLEAR.equalsIgnoreCase(args[0]))
        {
            clear(player, args);
            return;
        }
        if (!StargateHelper.isStargateShape(args[0]))
        {
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "Invalid shape: " + args[0]);
            return;
        }
        final StargateShape shape = StargateHelper.getStargateShape(args[0]);
        MaterialGroup group = MaterialGroupRegistry.getDefaultGroup();
        if (args.length == 2)
        {
            group = MaterialGroupRegistry.getGroup(args[1]);
            if ((group == null) || !shape.acceptsMaterialGroup(group.getName()))
            {
                player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + args[0]
                    + " is not built in a group called " + args[1] + ". Try one of: " + groupsFor(shape) + ".");
                return;
            }
        }
        else if ((group != null) && !shape.acceptsMaterialGroup(group.getName()))
        {
            group = null;
        }
        StargateManager.addPlayerBuilderShape(player, shape);
        if (!mayPreview || !(shape instanceof Stargate3DShape shape3d))
        {
            player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                + "Press the button on your new DHD to check it against the shape " + args[0] + ".");
            return;
        }
        preview(player, shape3d, group);
    }

    private static void preview(final Player player, final Stargate3DShape shape, final MaterialGroup group)
    {
        final String header = ConfigManager.MessageStrings.NORMAL_HEADER.toString();
        switch (GatePreviews.show(player, shape, group))
        {
            case SHOWN -> player.sendMessage(header + "Previewing " + shape.getShapeName()
                + ((group == null) ? "" : " in " + group.getName())
                + ". Build it where it stands, then place a real button where its button is and press that. "
                + "/wormhole gate build clear takes away the one you look at.");
            case OVER_LIMIT -> player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                + ((ConfigManager.getGatePreviewMaxBlocks() == 0)
                    ? "Previews are turned off on this server (gate-preview-max-blocks is 0). "
                        + "The shape is still chosen."
                    : "That would show more than " + ConfigManager.getGatePreviewMaxBlocks()
                        + " preview blocks on the server. The shape is still chosen; clear a preview to show it."));
            case NO_DHD -> player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                + shape.getShapeName() + " has no DHD to stand it by, so it cannot be previewed.");
        }
    }

    private static void clear(final Player player, final String[] args)
    {
        final String header = ConfigManager.MessageStrings.NORMAL_HEADER.toString();
        if ((args.length == 2) && ALL.equalsIgnoreCase(args[1]))
        {
            final int cleared = GatePreviews.clearAll(player);
            player.sendMessage(header + "Cleared " + cleared + " preview" + ((cleared == 1) ? "" : "s") + ".");
            return;
        }
        if (GatePreviews.clearLookedAt(player))
        {
            player.sendMessage(header + "Cleared that preview. " + GatePreviews.countOf(player.getUniqueId())
                + " left.");
            return;
        }
        player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
            + "Look at the preview to clear, or use /wormhole gate build clear all.");
    }

    /** The group names a shape may be built in, for an error message. */
    private static String groupsFor(final StargateShape shape)
    {
        return MaterialGroupRegistry.getGroups().stream()
            .map(MaterialGroup::getName)
            .filter(shape::acceptsMaterialGroup)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.joining(", "));
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        return CommandUtilities.runCommandSafe(sender, () ->
        {
            if (!CommandUtilities.playerCheck(sender))
            {
                return true;
            }
            final String[] arguments = CommandUtilities.commandEscaper(args);
            if ((arguments.length < 1) || (arguments.length > 2))
            {
                return false;
            }
            doBuild((Player) sender, arguments);
            return true;
        });
    }

    /**
     * Whether a sender may run {@code gate build} without {@code wormhole.config}.
     *
     * @param sender
     *            whoever typed it
     * @param args
     *            the full argument array, {@code gate} at index 0
     * @return true for {@code gate build} typed by somebody who may preview
     */
    public static boolean admitsWithoutConfig(final CommandSender sender, final String[] args)
    {
        return (args.length > 1) && "build".equals(args[1].toLowerCase(Locale.ROOT))
            && PreviewPermissions.mayPreview(sender);
    }
}
