package com.wormhole_xtreme.wormhole.command;

import static com.wormhole_xtreme.wormhole.model.preview.PreviewText.bad;
import static com.wormhole_xtreme.wormhole.model.preview.PreviewText.command;
import static com.wormhole_xtreme.wormhole.model.preview.PreviewText.commands;
import static com.wormhole_xtreme.wormhole.model.preview.PreviewText.good;
import static com.wormhole_xtreme.wormhole.model.preview.PreviewText.name;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.GateInteractionHandler;
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
import com.wormhole_xtreme.wormhole.model.preview.PreviewText;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * {@code /wormhole gate build <shape> [group]}, and {@code /wormhole gate preview <action>} on the
 * preview being looked at: {@code clear [-all]}, {@code activate}, {@code iris}, {@code chevrons},
 * {@code dhd}, {@code material <group>|-<role> <block>}, {@code needs}, {@code guide},
 * {@code layer [<n>|-next|-all]}, {@code share [<player>|-all]} and {@code place}.
 *
 * <p>Choosing a shape checks the next DHD button pressed against that shape alone. With
 * {@code wormhole.build.preview} it also stands the shape up full size in front of the player,
 * seen by them alone, to build by.
 */
public class Build implements CommandExecutor
{
    /** The command the preview actions follow, with the space before an action. */
    public static final String PREVIEW_COMMAND = "/wormhole gate preview ";

    /** Takes the preview looked at away, or with {@link #ALL} every one. */
    public static final String CLEAR = "clear";

    /** After {@link #CLEAR}: every preview, not only the one looked at. */
    public static final String ALL = "-all";

    /** Dials the preview looked at, or shuts it down. */
    public static final String ACTIVATE = "activate";

    /** Closes the preview's iris, or opens it. */
    public static final String IRIS = "iris";

    /** Hides the preview's DHD, or shows it. */
    public static final String DHD = "dhd";

    /** Draws the preview's chevrons as frame, or in the chevron material again. */
    public static final String CHEVRONS = "chevrons";

    /** Redresses the preview in a group, or changes one of its materials. */
    public static final String MATERIAL = "material";

    /** Lists what the preview takes to build, and what of it is still to place. */
    public static final String NEEDS = "needs";

    /**
     * What {@link #NEEDS} was called in 1.7.0, still accepted and no longer offered.
     *
     * <p>One letter from {@link #MATERIAL}, which does something else entirely -- redresses the
     * preview rather than counting what it would take to build. Two neighbouring rows of the
     * same table, told apart by an {@code s}, and a typo quietly did the other thing. The list
     * already called itself "needs" in its own first line, so the command now agrees with it.
     */
    public static final String MATERIALS = "materials";

    /** Marks on the preview what is still to place and what is wrong, or stops. */
    public static final String GUIDE = "guide";

    /** Shows the preview's layers up to one, the next, or all. */
    public static final String LAYER = "layer";

    /** After {@link #LAYER}: one more layer, or all again after the last. */
    public static final String NEXT = "-next";

    /** Builds the preview for real, with {@code wormhole.build.preview.place}. */
    public static final String PLACE = "place";

    /** Shows the preview to a player or everyone in the world, or stops, with {@code wormhole.build.preview.share}. */
    public static final String SHARE = "share";

    /** Every action, in the order they are offered. */
    // MATERIALS is deliberately absent: it is still accepted, it is just not offered.
    public static final List<String> ACTIONS = List.of(CLEAR, ACTIVATE, IRIS, MATERIAL, NEEDS, GUIDE, LAYER,
        CHEVRONS, DHD, SHARE, PLACE);

    /**
     * Every action that answers, which is {@link #ACTIONS} plus the names they used to have.
     *
     * <p>Separate from {@code ACTIONS} because that list is also what tab completion offers and
     * what an unknown action is told to try, and an old name should keep working without being
     * advertised. Validating against {@code ACTIONS} instead turns every alias into a rejection
     * before the dispatch below ever sees it, which is exactly what happened the first time.
     */
    private static final List<String> ACCEPTED =
        Stream.concat(ACTIONS.stream(), Stream.of(MATERIALS)).toList();

    private static final String USAGE = "Usage: ";

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
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "Preview options moved to "
                + actionList());
            return;
        }
        if (args.length > 2)
        {
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                + USAGE + command("/wormhole gate build <shape> [group]"));
            return;
        }
        if (!StargateHelper.isStargateShape(args[0]))
        {
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "No shape called " + name(args[0])
                + ".");
            return;
        }
        final StargateShape shape = StargateHelper.getStargateShape(args[0]);
        MaterialGroup group = MaterialGroupRegistry.getDefaultGroup();
        if (args.length == 2)
        {
            group = MaterialGroupRegistry.getGroup(args[1]);
            if ((group == null) || !shape.acceptsMaterialGroup(group.getName()))
            {
                player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + name(args[0])
                    + " has no group " + name(args[1]) + ". Try " + groupsFor(shape) + ".");
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
            player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + "Building " + name(args[0])
                + ". Press the button on its DHD when it is done.");
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
        final String error = ConfigManager.MessageStrings.ERROR_HEADER.toString();
        switch (shown)
        {
            case SHOWN ->
            {
                player.sendMessage(header + "Previewing " + name(shape.getShapeName())
                    + ((group == null) ? "" : " in " + name(group.getName()))
                    + ((dhdFacing == null) ? ". Build inside it, then press a real button on its DHD."
                        : " on your DHD. Finish it, then press the button."));
                player.sendMessage(header + "Look at it and use " + actionList());
            }
            case OVER_LIMIT -> player.sendMessage(error + ((ConfigManager.getGatePreviewMaxBlocks() == 0)
                ? "Previews are off on this server."
                : "Too many preview blocks on the server. Clear one with " + hint(CLEAR) + "."));
            case NO_DHD -> player.sendMessage(error + name(shape.getShapeName()) + " has no DHD, so it cannot be previewed.");
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

    /**
     * {@code /wormhole gate preview <action> [...]}, on the preview the sender looks at.
     *
     * @param sender
     *            whoever typed it
     * @param args
     *            the action and what follows it, {@code preview} already taken off
     * @return true, since every path answers
     */
    public static boolean previewAction(final CommandSender sender, final String[] args)
    {
        return CommandUtilities.runCommandSafe(sender, () ->
        {
            if (!CommandUtilities.playerCheck(sender))
            {
                return true;
            }
            final Player player = (Player) sender;
            final boolean mayPreview = PreviewPermissions.mayPreview(player);
            if (!mayPreview && CommandHandlerUtils.lacksConfigPermission(player))
            {
                return true;
            }
            final String[] arguments = CommandUtilities.commandEscaper(args);
            if ((arguments.length < 1) || (arguments.length > 3))
            {
                player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + USAGE
                    + actionList());
                return true;
            }
            option(player, arguments, mayPreview);
            return true;
        });
    }

    /** Runs an action on the preview the player looks at. */
    private static void option(final Player player, final String[] args, final boolean mayPreview)
    {
        final String option = args[0].toLowerCase(Locale.ROOT);
        if (!ACCEPTED.contains(option))
        {
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "No preview action "
                + name(args[0]) + ". Try " + commands(ACTIONS));
            return;
        }
        if (CLEAR.equals(option))
        {
            clear(player, args);
            return;
        }
        if (!mayPreview || (PLACE.equals(option) && !PreviewPermissions.mayPlace(player))
            || (SHARE.equals(option) && !PreviewPermissions.mayShare(player)))
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
            case NEEDS, MATERIALS -> listMaterials(player);
            case LAYER -> layers(player, args);
            case PLACE -> place(player);
            case SHARE -> share(player, args);
            default -> material(player, args);
        };
        if (done != null)
        {
            tell(player, done);
        }
    }

    /** {@code material <group>} or {@code material -<role> <block>}; null once it has answered itself. */
    private static GatePreviews.Control material(final Player player, final String[] args)
    {
        final String error = ConfigManager.MessageStrings.ERROR_HEADER.toString();
        // Named with their dash, the way they are offered and documented, so the usage line
        // teaches the form that cannot be mistaken for a material group's name.
        final String roles = Arrays.stream(Role.values()).map(Role::option).collect(Collectors.joining(", "));
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
            player.sendMessage(error + USAGE + hint(MATERIAL + " <group>") + " or "
                + hint(MATERIAL + " -<role> <block>") + ". Roles: " + roles + ".");
            return null;
        }
        final Material block = Material.matchMaterial(args[2]);
        return (block == null) ? GatePreviews.Control.NOT_A_BLOCK : GatePreviews.material(player, role, block);
    }

    /** {@code share [<player>|-all]}; null once it has answered itself. */
    private static GatePreviews.Control share(final Player player, final String[] args)
    {
        final String header = ConfigManager.MessageStrings.NORMAL_HEADER.toString();
        if (args.length == 1)
        {
            final GatePreviews.Audience audience = GatePreviews.audience(player);
            if (audience == null)
            {
                return GatePreviews.Control.NOT_LOOKING;
            }
            final List<String> who = new java.util.ArrayList<>(audience.names().stream().map(PreviewText::name).toList());
            if (audience.everyone())
            {
                who.add(0, "everyone in this world");
            }
            player.sendMessage(header + (who.isEmpty()
                ? "Only you see it. " + hint(SHARE + " <player>") + " or " + hint(SHARE + " " + ALL) + " shows it."
                : "Shown to " + String.join(", ", who) + "."));
            return null;
        }
        if (ALL.equalsIgnoreCase(args[1]))
        {
            return tellShared(player, GatePreviews.shareAll(player), null);
        }
        final Player with = player.getServer().getPlayerExact(args[1]);
        if (with == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "No player called " + name(args[1])
                + " is online.");
            return null;
        }
        return tellShared(player, GatePreviews.share(player, with), with);
    }

    private static GatePreviews.Control tellShared(final Player player, final GatePreviews.Shared done, final Player with)
    {
        final String header = ConfigManager.MessageStrings.NORMAL_HEADER.toString();
        switch (done)
        {
            case NOT_LOOKING -> {
                return GatePreviews.Control.NOT_LOOKING;
            }
            case SELF -> player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "You already see it.");
            case SHARED -> {
                player.sendMessage(header + "Showing it to " + name(with.getName()) + ". " + hint(SHARE + " "
                    + with.getName()) + " again stops.");
                with.sendMessage(header + name(player.getName()) + " is showing you a gate preview.");
            }
            case UNSHARED -> player.sendMessage(header + "Stopped showing it to " + name(with.getName()) + ".");
            case SHARED_ALL -> player.sendMessage(header + "Showing it to everyone in this world. " + hint(SHARE + " "
                + ALL) + " again stops.");
            case UNSHARED_ALL -> player.sendMessage(header + "Stopped showing it to everyone.");
        }
        return null;
    }

    /** {@code place}; null once it has answered itself. */
    private static GatePreviews.Control place(final Player player)
    {
        // Filling in a gate already there regenerates it, which is an admin's to do, as regen is.
        final GatePreviews.Placed placed = GatePreviews.place(player, CommandHandlerUtils.hasConfigPermission(player));
        final String error = ConfigManager.MessageStrings.ERROR_HEADER.toString();
        switch (placed.outcome())
        {
            case NOT_LOOKING -> {
                return GatePreviews.Control.NOT_LOOKING;
            }
            case NOT_FINDABLE -> player.sendMessage(error + "No material group uses that frame block, so the gate "
                + "would not be found. Nothing placed.");
            case NOT_LOADED -> player.sendMessage(error + "Part of it is in an unloaded chunk. Move closer. Nothing placed.");
            case OUTSIDE_BORDER -> player.sendMessage(error + "Part of it is outside the world border. Nothing placed.");
            case IN_THE_WAY -> player.sendMessage(error + "Nothing placed. In the way: "
                + bad(String.join(", ", placed.inTheWay())) + ". " + hint(GUIDE) + " marks them.");
            case NOT_FOUND -> player.sendMessage(error + "Placed, but no gate was found in it. " + hint(GUIDE)
                + " shows what is wrong.");
            case PLACED -> {
                player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                    + good("Placed " + placed.gate().getGateShape().getShapeName() + "."));
                GateInteractionHandler.offerNewGate(player, placed.button(), placed.gate());
            }
            case REPAIRED -> {
                player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                    + good("Filled in " + placed.gate().getGateName() + "'s missing blocks.") + " Regenerating it.");
                com.wormhole_xtreme.wormhole.command.handlers.RegenerateCommand.regenerateAt(player, placed.gate(),
                    placed.button(), null, false);
            }
        }
        return null;
    }

    /** {@code layer [<n>|-next|-all]}; null once it has answered itself. */
    private static GatePreviews.Control layers(final Player player, final String[] args)
    {
        final String error = ConfigManager.MessageStrings.ERROR_HEADER.toString();
        final int asked = layerAsked(args);
        if (asked < GatePreviews.NEXT_LAYER)
        {
            player.sendMessage(error + USAGE + hint(LAYER + " [number|" + NEXT + "|" + ALL + "]"));
            return null;
        }
        final GatePreviews.Layers layers = GatePreviews.layers(player, asked);
        if (layers == null)
        {
            return GatePreviews.Control.NOT_LOOKING;
        }
        if (!layers.valid())
        {
            player.sendMessage(error + "It has only " + layers.of() + " layer" + ((layers.of() == 1) ? "" : "s") + ".");
        }
        else if (layers.shown() == GatePreviews.ALL_LAYERS)
        {
            player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + "Showing all " + layers.of()
                + " layers.");
        }
        else
        {
            player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + "Showing layer "
                + ((layers.shown() == 1) ? "1" : "1-" + layers.shown()) + " of " + layers.of() + ". "
                + hint(LAYER) + " for the next.");
        }
        return null;
    }

    /** What {@code layer} asked for, or less than {@link GatePreviews#NEXT_LAYER} if it makes no sense. */
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

    /** {@code materials}; null once it has answered itself. */
    private static GatePreviews.Control listMaterials(final Player player)
    {
        final GatePreviews.Materials list = GatePreviews.materials(player);
        if (list == null)
        {
            return GatePreviews.Control.NOT_LOOKING;
        }
        final String header = ConfigManager.MessageStrings.NORMAL_HEADER.toString();
        player.sendMessage(header + name(list.shape()) + " needs:");
        for (final BuildGuide.Need need : list.needs())
        {
            player.sendMessage(header + "  " + need.count() + " " + PreviewText.material(need.name()) + " - "
                + ((need.toPlace() == 0) ? good("done") : need.toPlace() + " left"));
        }
        if (list.blocked() > 0)
        {
            player.sendMessage(header + "  " + bad(list.blocked() + " in the way") + " in the opening");
        }
        if (!list.detectable())
        {
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "No material group uses "
                + PreviewText.material(list.frame().name().toLowerCase(Locale.ROOT))
                + " for a frame, so this gate would not be found.");
        }
        return null;
    }

    /** {@code /wormhole gate preview <action>} and every action, for a message that lists them. */
    private static String actionList()
    {
        return hint("<action>") + ": " + commands(ACTIONS);
    }

    /** A preview command, highlighted, for a message that points at it. */
    private static String hint(final String words)
    {
        return command(PREVIEW_COMMAND + words);
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
            case DIALLING -> "Dialling. " + hint(ACTIVATE) + " again shuts it down.";
            case SHUT_DOWN -> "Shut down.";
            case IRIS_CLOSED -> "Iris closed. " + hint(IRIS) + " opens it.";
            case IRIS_OPENED -> "Iris open.";
            case CHANGED -> "Materials changed.";
            case NOT_A_BLOCK -> "That is not a block.";
            case NOT_IN_GROUP -> "This shape cannot use that group.";
            case DHD_HIDDEN -> "DHD hidden. " + hint(DHD) + " shows it.";
            case DHD_SHOWN -> "DHD shown.";
            case CHEVRONS_PLAIN -> "Chevrons shown as frame. " + hint(CHEVRONS) + " undoes it.";
            case CHEVRONS_SHOWN -> "Chevrons shown.";
            case GUIDE_ON -> "Guide on: small = to place, " + bad("red") + " = wrong, gone = done. " + hint(GUIDE)
                + " turns it off.";
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
            player.sendMessage(header + "Cleared. " + GatePreviews.countOf(player.getUniqueId()) + " left.");
            return;
        }
        player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
            + "Look at a preview to clear it, or use " + hint(CLEAR + " " + ALL) + ".");
    }

    /** The group names a shape may be built in, for an error message. */
    private static String groupsFor(final StargateShape shape)
    {
        return MaterialGroupRegistry.getGroups().stream()
            .map(MaterialGroup::getName)
            .filter(shape::acceptsMaterialGroup)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .map(PreviewText::name)
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
     * Whether a sender may run {@code gate build} or {@code gate preview} without {@code wormhole.config}.
     *
     * @param sender
     *            whoever typed it
     * @param args
     *            the full argument array, {@code gate} at index 0
     * @return true for {@code gate build} or {@code gate preview} typed by somebody who may preview
     */
    public static boolean admitsWithoutConfig(final CommandSender sender, final String[] args)
    {
        if (args.length < 2)
        {
            return false;
        }
        final String verb = args[1].toLowerCase(Locale.ROOT);
        return ("build".equals(verb) || "preview".equals(verb)) && PreviewPermissions.mayPreview(sender);
    }
}
