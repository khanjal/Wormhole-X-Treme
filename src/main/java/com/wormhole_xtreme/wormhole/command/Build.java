package com.wormhole_xtreme.wormhole.command;

import static com.wormhole_xtreme.wormhole.utils.ChatText.bad;
import static com.wormhole_xtreme.wormhole.utils.ChatText.command;
import static com.wormhole_xtreme.wormhole.utils.ChatText.good;
import static com.wormhole_xtreme.wormhole.utils.ChatText.name;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.wormhole_xtreme.wormhole.command.handlers.RegenerateCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.integration.RegionFlags;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.model.preview.BuildGuide;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;
import com.wormhole_xtreme.wormhole.model.preview.PreviewPermissions;
import com.wormhole_xtreme.wormhole.utils.ChatLine;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;
import com.wormhole_xtreme.wormhole.utils.Messages;

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

    /** What each of {@link #ACTIONS} is called on its button, inside its brackets; the button is the command, so it is not translated. */
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry(CLEAR, "Clear"),
        Map.entry(ACTIVATE, "Activate"),
        Map.entry(IRIS, "Iris"),
        Map.entry(MATERIAL, "Material"),
        Map.entry(NEEDS, "Needs"),
        Map.entry(GUIDE, "Guide"),
        Map.entry(LAYER, "Layer"),
        Map.entry(CHEVRONS, "Chevrons"),
        Map.entry(DHD, "DHD"),
        Map.entry(SHARE, "Share"),
        Map.entry(PLACE, "Place"));

    /** The one action that does nothing without more typed after it, so its button fills it in. */
    private static final List<String> NEEDS_MORE = List.of(MATERIAL);

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
            actionList(player, ChatText.error(Messages.get(player, "gate.preview.options-moved"))).send(player);
            return;
        }
        if (args.length > 2)
        {
            player.sendMessage(ChatText.info(ChatText.usage(player, "/wormhole gate build <shape> [group]")));
            return;
        }
        if (!StargateShapeRegistry.isStargateShape(args[0]))
        {
            player.sendMessage(ChatText.error(Messages.get(player, "gate.build.no-shape", name(args[0]))));
            return;
        }
        if (!StargateShapeRegistry.isEnabled(args[0]))
        {
            player.sendMessage(ChatText.error(Messages.get(player, "gate.build.shape-off", name(args[0]))));
            return;
        }
        final StargateShape shape = StargateShapeRegistry.getStargateShape(args[0]);
        MaterialGroup group = MaterialGroupRegistry.getDefaultGroup();
        if (args.length == 2)
        {
            group = MaterialGroupRegistry.getGroup(args[1]);
            if ((group == null) || !shape.acceptsMaterialGroup(group.getName()))
            {
                player.sendMessage(ChatText.error(Messages.get(player, "gate.preview.no-group", name(args[0]),
                    name(args[1]), groupsFor(player, shape))));
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
            player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.building", name(args[0]))));
            return;
        }
        preview(player, shape3d, group);
    }

    private static void preview(final Player player, final Stargate3DShape shape, final MaterialGroup group)
    {
        final Block looked = player.getTargetBlockExact(DHD_REACH);
        final BlockFace dhdFacing = (looked == null) ? null : wallFacing(looked);
        final GatePreviews.Shown shown = (dhdFacing == null) ? GatePreviews.show(player, shape, group)
            : GatePreviews.showOn(player, shape, group, looked, dhdFacing);
        switch (shown)
        {
            case SHOWN ->
            {
                player.sendMessage(ChatText.info(previewing(player, shape, group, dhdFacing != null)));
                actionList(player, ChatText.info(Messages.get(player, "gate.preview.look-at-it"))).send(player);
            }
            case OVER_LIMIT -> overLimit(player);
            case NO_DHD -> player.sendMessage(ChatText.error(Messages.get(player, "gate.preview.no-dhd",
                name(shape.getShapeName()))));
        }
    }

    /** What a shown preview says: the shape, the group if there is one, and what to do on the DHD. */
    private static String previewing(final Player player, final Stargate3DShape shape, final MaterialGroup group,
        final boolean onDhd)
    {
        final String shapeName = name(shape.getShapeName());
        if (group == null)
        {
            if (onDhd)
            {
                return Messages.get(player, "gate.preview.showing-on-dhd", shapeName);
            }
            return Messages.get(player, "gate.preview.showing", shapeName);
        }
        if (onDhd)
        {
            return Messages.get(player, "gate.preview.showing-on-dhd-group", shapeName, name(group.getName()));
        }
        return Messages.get(player, "gate.preview.showing-group", shapeName, name(group.getName()));
    }

    private static void overLimit(final Player player)
    {
        if (ConfigManager.getGatePreviewMaxBlocks() == 0)
        {
            player.sendMessage(ChatText.error(Messages.get(player, "gate.preview.off")));
            return;
        }
        linkHint(ChatLine.of(ChatText.error(Messages.get(player, "gate.preview.over-limit"))), CLEAR).text(".")
            .send(player);
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
                actionList(player, ChatText.info(ChatText.usageLead(player))).send(player);
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
            buttons(player, ChatLine.of(ChatText.error(Messages.get(player, "gate.preview.no-action", name(args[0])))))
                .send(player);
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
        // Named with their dash, the way they are offered and documented, so the usage line
        // teaches the form that cannot be mistaken for a material group's name.
        final String roles = Arrays.stream(Role.values()).map(Role::option).map(ChatText::command)
            .collect(Collectors.joining(separator(player)));
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
            ChatLine.of(ChatText.info(ChatText.usageLead(player))).suggest(hint(MATERIAL + " <group>"), PREVIEW_COMMAND + MATERIAL + " ")
                .text(Messages.get(player, "gate.preview.material.or"))
                .suggest(hint(MATERIAL + " -<role> <block>"), PREVIEW_COMMAND + MATERIAL + " -")
                .text(Messages.get(player, "gate.preview.material.roles", roles)).send(player);
            return null;
        }
        final Material block = Material.matchMaterial(args[2]);
        return (block == null) ? GatePreviews.Control.NOT_A_BLOCK : GatePreviews.material(player, role, block);
    }

    /** {@code share [<player>|-all]}; null once it has answered itself. */
    private static GatePreviews.Control share(final Player player, final String[] args)
    {
        if (args.length == 1)
        {
            final GatePreviews.Audience audience = GatePreviews.audience(player);
            if (audience == null)
            {
                return GatePreviews.Control.NOT_LOOKING;
            }
            final List<String> who = new ArrayList<>(audience.names().stream().map(ChatText::name).toList());
            if (audience.everyone())
            {
                who.add(0, Messages.get(player, "gate.preview.share.everyone"));
            }
            final String line;
            if (who.isEmpty())
            {
                line = Messages.get(player, "gate.preview.share.only-you", hint(SHARE + " <player>"),
                    hint(SHARE + " " + ALL));
            }
            else
            {
                line = Messages.get(player, "gate.preview.share.shown-to", String.join(separator(player), who));
            }
            player.sendMessage(ChatText.info(line));
            return null;
        }
        if (ALL.equalsIgnoreCase(args[1]))
        {
            return tellShared(player, GatePreviews.shareAll(player), null);
        }
        final Player with = player.getServer().getPlayerExact(args[1]);
        if (with == null)
        {
            player.sendMessage(ChatText.error(Messages.get(player, "gate.preview.share.no-player", name(args[1]))));
            return null;
        }
        return tellShared(player, GatePreviews.share(player, with), with);
    }

    private static GatePreviews.Control tellShared(final Player player, final GatePreviews.Shared done, final Player with)
    {
        switch (done)
        {
            case NOT_LOOKING -> {
                return GatePreviews.Control.NOT_LOOKING;
            }
            case SELF -> player.sendMessage(ChatText.error(Messages.get(player, "gate.preview.share.self")));
            case SHARED -> {
                player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.share.started",
                    name(with.getName()), hint(SHARE + " " + with.getName()))));
                with.sendMessage(ChatText.info(Messages.get(with, "gate.preview.share.notice", name(player.getName()))));
            }
            case UNSHARED -> player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.share.stopped",
                name(with.getName()))));
            case SHARED_ALL -> player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.share.started-all",
                hint(SHARE + " " + ALL))));
            case UNSHARED_ALL -> player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.share.stopped-all")));
        }
        return null;
    }

    /** {@code place}; null once it has answered itself. */
    private static GatePreviews.Control place(final Player player)
    {
        // Filling in a gate already there regenerates it, which is an admin's to do, as regen is.
        final GatePreviews.Placed placed = GatePreviews.place(player, CommandHandlerUtils.hasConfigPermission(player));
        switch (placed.outcome())
        {
            case NOT_LOOKING -> {
                return GatePreviews.Control.NOT_LOOKING;
            }
            case NOT_FINDABLE -> player.sendMessage(ChatText.error(Messages.get(player,
                "gate.preview.place.not-findable")));
            case NOT_LOADED -> player.sendMessage(ChatText.error(Messages.get(player, "gate.preview.place.not-loaded")));
            case OUTSIDE_BORDER -> player.sendMessage(ChatText.error(Messages.get(player,
                "gate.preview.place.outside-border")));
            case NOT_ALLOWED_HERE -> player.sendMessage(RegionFlags.buildRefusedNothingPlaced(player));
            case TOO_CLOSE -> player.sendMessage(ChatText.error(Messages.get(player, "gate.preview.place.too-close",
                placed.inTheWay().get(0))));
            case SHAPE_OFF -> player.sendMessage(ChatText.error(Messages.get(player, "gate.preview.place.shape-off")));
            case IN_THE_WAY -> linkHint(ChatLine.of(ChatText.error(Messages.get(player, "gate.preview.place.in-the-way",
                bad(String.join(separator(player), placed.inTheWay()))))), GUIDE)
                .text(Messages.get(player, "gate.preview.place.in-the-way-tail")).send(player);
            case NOT_FOUND -> linkHint(ChatLine.of(ChatText.error(Messages.get(player, "gate.preview.place.not-found"))),
                GUIDE).text(Messages.get(player, "gate.preview.place.not-found-tail")).send(player);
            case PLACED -> {
                player.sendMessage(ChatText.info(good(Messages.get(player, "gate.preview.place.placed",
                    placed.gate().getGateShape().getShapeName()))));
                GateInteractionHandler.offerNewGate(player, placed.button(), placed.gate());
            }
            case REPAIRED -> {
                player.sendMessage(ChatText.info(good(Messages.get(player, "gate.preview.place.filled-in",
                    placed.gate().getGateName())) + Messages.get(player, "gate.preview.place.regenerating")));
                RegenerateCommand.regenerateAt(player, placed.gate(),
                    placed.button(), null, false);
            }
        }
        return null;
    }

    /** {@code layer [<n>|-next|-all]}; null once it has answered itself. */
    private static GatePreviews.Control layers(final Player player, final String[] args)
    {
        final int asked = layerAsked(args);
        if (asked < GatePreviews.NEXT_LAYER)
        {
            ChatLine.of(ChatText.info(ChatText.usageLead(player))).suggest(hint(LAYER + " [number|" + NEXT + "|" + ALL + "]"),
                PREVIEW_COMMAND + LAYER + " ").send(player);
            return null;
        }
        final GatePreviews.Layers layers = GatePreviews.layers(player, asked);
        if (layers == null)
        {
            return GatePreviews.Control.NOT_LOOKING;
        }
        if (!layers.valid())
        {
            final String only;
            if (layers.of() == 1)
            {
                only = Messages.get(player, "gate.preview.layers.only.one", ChatText.value(String.valueOf(layers.of())));
            }
            else
            {
                only = Messages.get(player, "gate.preview.layers.only.other", ChatText.value(String.valueOf(layers.of())));
            }
            player.sendMessage(ChatText.error(only));
        }
        else if (layers.shown() == GatePreviews.ALL_LAYERS)
        {
            player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.layers.all",
                ChatText.value(String.valueOf(layers.of())))));
        }
        else
        {
            player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.layers.showing",
                ChatText.value((layers.shown() == 1) ? "1" : "1-" + layers.shown()),
                ChatText.value(String.valueOf(layers.of())), hint(LAYER))));
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
        player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.needs.header", name(list.shape()))));
        for (final BuildGuide.Need need : list.needs())
        {
            final String progress;
            if (need.toPlace() == 0)
            {
                progress = good(Messages.get(player, "gate.preview.needs.done"));
            }
            else
            {
                progress = Messages.get(player, "gate.preview.needs.left", ChatText.value(String.valueOf(need.toPlace())));
            }
            player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.needs.line",
                ChatText.value(String.valueOf(need.count())), ChatText.material(need.name()), progress)));
        }
        if (list.blocked() > 0)
        {
            player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.needs.blocked",
                bad(Messages.get(player, "gate.preview.needs.in-the-way", list.blocked())))));
        }
        if (!list.detectable())
        {
            player.sendMessage(ChatText.error(Messages.get(player, "gate.preview.needs.not-findable",
                ChatText.material(list.frame().name().toLowerCase(Locale.ROOT)))));
        }
        return null;
    }

    /**
     * {@code /wormhole gate preview <action>: } and a button for every action, for a message that
     * lists them.
     *
     * @param player
     *            who reads it, for the help each button shows
     * @param before
     *            the line up to the list, header included
     * @return the line; as plain text, the command and the actions spelled out
     */
    static ChatLine actionList(final Player player, final String before)
    {
        return buttons(player, ChatLine.of(before).text(hint("<action>") + ": "));
    }

    /**
     * A button for each of {@link #ACTIONS}, one space apart, reading as {@code commands(ACTIONS)}
     * where nothing can be clicked.
     */
    private static ChatLine buttons(final Player player, final ChatLine line)
    {
        for (int i = 0; i < ACTIONS.size(); i++)
        {
            final String action = ACTIONS.get(i);
            final boolean needsMore = NEEDS_MORE.contains(action);
            final String typed = PREVIEW_COMMAND + action + (needsMore ? " " : "");
            String finish = "";
            if (needsMore)
            {
                finish = Messages.get(player, "gate.preview.help.finish");
            }
            if (i > 0)
            {
                line.text(" ");
            }
            line.link(command("[" + LABELS.getOrDefault(action, action) + "]"), command(action), typed,
                command(typed.trim() + (needsMore ? " ..." : "")) + "\n" + help(player, action) + finish, needsMore);
        }
        return line;
    }

    /** What pointing at an action's button says it does; nothing for an action with no button. */
    private static String help(final Player player, final String action)
    {
        switch (action)
        {
            case CLEAR -> {
                return Messages.get(player, "gate.preview.help.clear");
            }
            case ACTIVATE -> {
                return Messages.get(player, "gate.preview.help.activate");
            }
            case IRIS -> {
                return Messages.get(player, "gate.preview.help.iris");
            }
            case MATERIAL -> {
                return Messages.get(player, "gate.preview.help.material");
            }
            case NEEDS -> {
                return Messages.get(player, "gate.preview.help.needs");
            }
            case GUIDE -> {
                return Messages.get(player, "gate.preview.help.guide");
            }
            case LAYER -> {
                return Messages.get(player, "gate.preview.help.layer");
            }
            case CHEVRONS -> {
                return Messages.get(player, "gate.preview.help.chevrons");
            }
            case DHD -> {
                return Messages.get(player, "gate.preview.help.dhd");
            }
            case SHARE -> {
                return Messages.get(player, "gate.preview.help.share");
            }
            case PLACE -> {
                return Messages.get(player, "gate.preview.help.place");
            }
            default -> {
                return "";
            }
        }
    }

    /** What goes between the names in a list, in the reader's language. */
    private static String separator(final Player player)
    {
        return Messages.get(player, "gate.preview.separator");
    }

    /** A preview command, highlighted, for a message that points at it. */
    private static String hint(final String words)
    {
        return command(PREVIEW_COMMAND + words);
    }

    /**
     * Adds a preview command a refusal points at, which a click runs.
     *
     * @param line
     *            the refusal so far
     * @param words
     *            the action and anything after it
     * @return the line
     */
    private static ChatLine linkHint(final ChatLine line, final String words)
    {
        return line.run(hint(words), PREVIEW_COMMAND + words);
    }

    private static void tell(final Player player, final GatePreviews.Control done)
    {
        switch (done)
        {
            case NOT_LOOKING -> player.sendMessage(ChatText.error(Messages.get(player, "gate.preview.control.not-looking")));
            case NOT_A_BLOCK -> player.sendMessage(ChatText.error(Messages.get(player, "gate.preview.control.not-a-block")));
            case NOT_IN_GROUP -> player.sendMessage(ChatText.error(Messages.get(player,
                "gate.preview.control.not-in-group")));
            case DIALLING -> player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.control.dialling",
                hint(ACTIVATE))));
            case SHUT_DOWN -> player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.control.shut-down")));
            case IRIS_CLOSED -> player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.control.iris-closed",
                hint(IRIS))));
            case IRIS_OPENED -> player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.control.iris-opened")));
            case CHANGED -> player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.control.changed")));
            case DHD_HIDDEN -> player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.control.dhd-hidden",
                hint(DHD))));
            case DHD_SHOWN -> player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.control.dhd-shown")));
            case CHEVRONS_PLAIN -> player.sendMessage(ChatText.info(Messages.get(player,
                "gate.preview.control.chevrons-plain", hint(CHEVRONS))));
            case CHEVRONS_SHOWN -> player.sendMessage(ChatText.info(Messages.get(player,
                "gate.preview.control.chevrons-shown")));
            case GUIDE_ON -> player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.control.guide-on",
                bad(Messages.get(player, "gate.preview.control.red")), hint(GUIDE))));
            case GUIDE_OFF -> player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.control.guide-off")));
        }
    }

    private static void clear(final Player player, final String[] args)
    {
        if ((args.length == 2) && ALL.equalsIgnoreCase(args[1]))
        {
            final int cleared = GatePreviews.clearAll(player);
            final String line;
            if (cleared == 1)
            {
                line = Messages.get(player, "gate.preview.cleared.one", ChatText.value(String.valueOf(cleared)));
            }
            else
            {
                line = Messages.get(player, "gate.preview.cleared.other", ChatText.value(String.valueOf(cleared)));
            }
            player.sendMessage(ChatText.info(line));
            return;
        }
        if (GatePreviews.clearLookedAt(player))
        {
            player.sendMessage(ChatText.info(Messages.get(player, "gate.preview.cleared-looked-at",
                ChatText.value(String.valueOf(GatePreviews.countOf(player.getUniqueId()))))));
            return;
        }
        linkHint(ChatLine.of(ChatText.error(Messages.get(player, "gate.preview.clear-hint"))), CLEAR + " " + ALL).text(".")
            .send(player);
    }

    /** The group names a shape may be built in, for an error message. */
    private static String groupsFor(final Player player, final StargateShape shape)
    {
        return MaterialGroupRegistry.getGroups().stream()
            .map(MaterialGroup::getName)
            .filter(shape::acceptsMaterialGroup)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .map(ChatText::name)
            .collect(Collectors.joining(separator(player)));
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
