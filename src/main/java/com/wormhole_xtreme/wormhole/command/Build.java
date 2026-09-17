package com.wormhole_xtreme.wormhole.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.preview.BuildGuide;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;
import com.wormhole_xtreme.wormhole.model.preview.PreviewPermissions;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * {@code /wormhole gate build <shape> [group]}, and options on the preview being looked at:
 * {@code -clear [-all]}, {@code -activate}, {@code -iris}, {@code -chevrons}, {@code -dhd},
 * {@code -material <group>|<role> <block>}, {@code -materials}, {@code -guide} and
 * {@code -layer [<n>|-next|-all]}.
 *
 * <p>Choosing a shape checks the next DHD button pressed against that shape alone. With
 * {@code wormhole.build.preview} it also stands the shape up full size in front of the player,
 * seen by them alone, to build by.
 */
public class Build implements CommandExecutor
{
    /** The word that takes previews away rather than naming a shape. */
    public static final String CLEAR = "-clear";

    /** After {@link #CLEAR}: every preview, not only the one looked at. */
    public static final String ALL = "-all";

    /** Dials the preview looked at, or shuts it down. */
    public static final String ACTIVATE = "-activate";

    /** Closes the preview's iris, or opens it. */
    public static final String IRIS = "-iris";

    /** Hides the preview's DHD, or shows it. */
    public static final String DHD = "-dhd";

    /** Draws the preview's chevrons as frame, or in the chevron material again. */
    public static final String CHEVRONS = "-chevrons";

    /** Redresses the preview in a group, or changes one of its materials. */
    public static final String MATERIAL = "-material";

    /** Lists what the preview takes to build, and what of it is still to place. */
    public static final String MATERIALS = "-materials";

    /** Marks on the preview what is still to place and what is wrong, or stops. */
    public static final String GUIDE = "-guide";

    /** Shows the preview's layers up to one, the next, or all. */
    public static final String LAYER = "-layer";

    /** After {@link #LAYER}: one more layer, or all again after the last. */
    public static final String NEXT = "-next";

    /** Every option, in the order they are offered. */
    public static final List<String> OPTIONS = List.of(CLEAR, ACTIVATE, IRIS, MATERIAL, MATERIALS, GUIDE, LAYER,
        CHEVRONS, DHD);

    /** How far away a placed DHD button can be looked at to stand a preview on it. */
    private static final int DHD_REACH = 6;

    private static void doBuild(final Player player, final String[] args)
    {
        final boolean mayPreview = PreviewPermissions.mayPreview(player);
        if (!mayPreview && CommandHandlerUtils.lacksConfigPermission(player))
        {
            return;
        }
        if (args[0].startsWith("-"))
        {
            option(player, args, mayPreview);
            return;
        }
        if (args.length > 2)
        {
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                + "Usage: /wormhole gate build <shape> [group]");
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
        final Block looked = player.getTargetBlockExact(DHD_REACH);
        final BlockFace dhdFacing = (looked == null) ? null : wallFacing(looked);
        final GatePreviews.Shown shown = (dhdFacing == null) ? GatePreviews.show(player, shape, group)
            : GatePreviews.showOn(player, shape, group, looked, dhdFacing);
        switch (shown)
        {
            case SHOWN -> player.sendMessage(header + "Previewing " + shape.getShapeName()
                + ((group == null) ? "" : " in " + group.getName())
                + ((dhdFacing == null)
                    ? ". Right-click its button to dial it. Build it where it stands, then place a real button where "
                        + "its button is and press that."
                    : " on the DHD you are looking at. Finish building it, then press that button.")
                + " Look at it and use " + String.join(", ", OPTIONS) + ".");
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

    /**
     * The way a button or lever on the side of a block faces, as a DHD's does.
     *
     * @param block
     *            what the player looks at
     * @return its facing, or null for anything else, or one on a floor or ceiling
     */
    static BlockFace wallFacing(final Block block)
    {
        final Material type = block.getType();
        final boolean dhdSwitch = MaterialUtils.isButton(type) || (type == Material.LEVER);
        return (dhdSwitch && (block.getBlockData() instanceof FaceAttachable attached)
            && (attached.getAttachedFace() == FaceAttachable.AttachedFace.WALL)
            && (block.getBlockData() instanceof Directional directional))
            ? directional.getFacing()
            : null;
    }

    /** Runs an option on the preview the player looks at. */
    private static void option(final Player player, final String[] args, final boolean mayPreview)
    {
        final String option = args[0].toLowerCase(Locale.ROOT);
        if (!OPTIONS.contains(option))
        {
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "No such option: " + args[0]
                + ". Try " + String.join(", ", OPTIONS) + ".");
            return;
        }
        if (CLEAR.equals(option))
        {
            clear(player, args);
            return;
        }
        if (!mayPreview)
        {
            player.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return;
        }
        final GatePreviews.Control done = switch (option)
        {
            case ACTIVATE -> GatePreviews.activate(player);
            case IRIS -> GatePreviews.iris(player);
            case DHD -> GatePreviews.toggleDhd(player);
            case CHEVRONS -> GatePreviews.toggleChevrons(player);
            case GUIDE -> GatePreviews.guide(player);
            case MATERIALS -> listMaterials(player);
            case LAYER -> layers(player, args);
            default -> material(player, args);
        };
        if (done != null)
        {
            tell(player, done);
        }
    }

    /** {@code -material <group>} or {@code -material <role> <block>}; null once it has answered itself. */
    private static GatePreviews.Control material(final Player player, final String[] args)
    {
        final String error = ConfigManager.MessageStrings.ERROR_HEADER.toString();
        final String roles = Arrays.stream(Role.values()).map(Role::word).collect(Collectors.joining("|"));
        if (args.length == 2)
        {
            final MaterialGroup group = MaterialGroupRegistry.getGroup(args[1]);
            if (group != null)
            {
                return GatePreviews.material(player, group);
            }
        }
        final Role role = (args.length == 3) ? Role.named(args[1]) : null;
        if (role == null)
        {
            player.sendMessage(error + "Usage: /wormhole gate build " + MATERIAL + " <group>, or " + MATERIAL
                + " <" + roles + "> <block>");
            return null;
        }
        final Material block = Material.matchMaterial(args[2]);
        return (block == null) ? GatePreviews.Control.NOT_A_BLOCK : GatePreviews.material(player, role, block);
    }

    /** {@code -layer [<n>|-next|-all]}; null once it has answered itself. */
    private static GatePreviews.Control layers(final Player player, final String[] args)
    {
        final String error = ConfigManager.MessageStrings.ERROR_HEADER.toString();
        final int asked = layerAsked(args);
        if (asked < GatePreviews.NEXT_LAYER)
        {
            player.sendMessage(error + "Usage: /wormhole gate build " + LAYER + " [<number>|" + NEXT + "|" + ALL + "]");
            return null;
        }
        final GatePreviews.Layers layers = GatePreviews.layers(player, asked);
        if (layers == null)
        {
            return GatePreviews.Control.NOT_LOOKING;
        }
        if (!layers.valid())
        {
            player.sendMessage(error + "It has " + layers.of() + " layer" + ((layers.of() == 1) ? "" : "s") + ".");
        }
        else if (layers.shown() == GatePreviews.ALL_LAYERS)
        {
            player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + "Showing all " + layers.of()
                + " layers.");
        }
        else
        {
            player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + "Showing "
                + ((layers.shown() == 1) ? "layer 1" : "layers 1 to " + layers.shown()) + " of " + layers.of()
                + ". " + LAYER + " again shows the next.");
        }
        return null;
    }

    /** What {@code -layer} asked for, or less than {@link GatePreviews#NEXT_LAYER} if it makes no sense. */
    private static int layerAsked(final String[] args)
    {
        if ((args.length == 1) || NEXT.equalsIgnoreCase(args[1]))
        {
            return GatePreviews.NEXT_LAYER;
        }
        if (ALL.equalsIgnoreCase(args[1]))
        {
            return GatePreviews.ALL_LAYERS;
        }
        try
        {
            final int n = Integer.parseInt(args[1]);
            return (n >= 1) ? n : Integer.MIN_VALUE;
        }
        catch (final NumberFormatException notANumber)
        {
            return Integer.MIN_VALUE;
        }
    }

    /** {@code -materials}; null once it has answered itself. */
    private static GatePreviews.Control listMaterials(final Player player)
    {
        final GatePreviews.Materials list = GatePreviews.materials(player);
        if (list == null)
        {
            return GatePreviews.Control.NOT_LOOKING;
        }
        final String header = ConfigManager.MessageStrings.NORMAL_HEADER.toString();
        player.sendMessage(header + list.shape() + " takes:");
        for (final BuildGuide.Need need : list.needs())
        {
            player.sendMessage(header + "  " + need.count() + " " + need.name()
                + ((need.toPlace() == 0) ? ", all in place" : ", " + need.toPlace() + " still to place"));
        }
        if (list.blocked() > 0)
        {
            player.sendMessage(header + "  and " + list.blocked() + " block" + ((list.blocked() == 1) ? "" : "s")
                + " cleared from its opening");
        }
        if (!list.detectable())
        {
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "No material group has a "
                + list.frame().name().toLowerCase(Locale.ROOT) + " frame, so a gate built like this will not be found.");
        }
        return null;
    }

    private static void tell(final Player player, final GatePreviews.Control done)
    {
        final boolean refused = switch (done)
        {
            case NOT_LOOKING, NOT_A_BLOCK, NOT_IN_GROUP -> true;
            default -> false;
        };
        final String text = switch (done)
        {
            case NOT_LOOKING -> "Look at one of your previews first.";
            case DIALLING -> "Dialling. " + ACTIVATE + " again, or its button, shuts it down.";
            case SHUT_DOWN -> "Shut down.";
            case IRIS_CLOSED -> "Iris closed. " + IRIS + " again opens it.";
            case IRIS_OPENED -> "Iris open.";
            case CHANGED -> "Materials changed.";
            case NOT_A_BLOCK -> "That is not a block that can be shown.";
            case NOT_IN_GROUP -> "This shape is not built in that group.";
            case DHD_HIDDEN -> "DHD hidden. " + DHD + " again shows it.";
            case DHD_SHOWN -> "DHD shown.";
            case CHEVRONS_PLAIN -> "Chevrons drawn as frame, as a gate built without chevron blocks. " + CHEVRONS
                + " again shows them.";
            case CHEVRONS_SHOWN -> "Chevrons drawn in their own material.";
            case GUIDE_ON -> "Guide on: blocks still to place are drawn small, wrong blocks glow red, and placed "
                + "blocks disappear. " + GUIDE + " again turns it off.";
            case GUIDE_OFF -> "Guide off.";
        };
        player.sendMessage((refused ? ConfigManager.MessageStrings.ERROR_HEADER : ConfigManager.MessageStrings.NORMAL_HEADER)
            .toString() + text);
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
            + "Look at the preview to clear, or use /wormhole gate build " + CLEAR + " " + ALL + ".");
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
            if ((arguments.length < 1) || (arguments.length > 3))
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
