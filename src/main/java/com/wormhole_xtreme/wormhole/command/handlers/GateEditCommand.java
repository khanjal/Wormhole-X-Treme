package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.command.WXIDC;
import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.command.CommandUtilities;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * One command for every per-gate setting.
 *
 * <p>These were eight separate top-level commands -- {@code portalmaterial},
 * {@code irismaterial}, {@code lightmaterial}, {@code wooshdepth}, {@code redstone},
 * {@code idc}, {@code owner} and {@code custom}. That is how a plugin ends up with
 * twenty-two subcommands: every new gate setting needed a new name at the top level, while
 * the rings had already solved the same problem with a single {@code ring edit} and one more
 * case per setting.
 *
 * <p>Nothing here re-implements what those commands did. Each field routes to the handler
 * that already owned it, with the arguments rearranged into the shape it expects, so the
 * validation, the permission checks and the messages are all still the originals. This class
 * is a front door, not a rewrite.
 */
public class GateEditCommand implements SubCommand
{
    /**
     * A field, and what it does about it.
     */
    private interface Field
    {
        /**
         * @param sender
         *            who typed it
         * @param gate
         *            the gate named
         * @param value
         *            the value given, or empty if none was
         * @return true if the command was handled
         */
        boolean apply(CommandSender sender, String gate, String value);
    }

    /** The fields, in the order they are offered. */
    private static final String OWNER = "owner";

    /** The field for a gate's own iris animation, named as the setting is. */
    private static final String IRIS_ANIMATION = "iris-animation";

    /** The iris code: the one field a gate's owner may set without {@code wormhole.config}. */
    private static final String IDC = "idc";

    private static final Map<String, Field> FIELDS = new LinkedHashMap<>();

    static
    {
        FIELDS.put("portal", (sender, gate, value) ->
            new MaterialCommand(MaterialCommand.Kind.PORTAL).execute(sender, new String[] { "portalmaterial", gate, value }));
        FIELDS.put("iris", (sender, gate, value) ->
            new MaterialCommand(MaterialCommand.Kind.IRIS).execute(sender, new String[] { "irismaterial", gate, value }));
        FIELDS.put("light", (sender, gate, value) ->
            new MaterialCommand(MaterialCommand.Kind.LIGHT).execute(sender, new String[] { "lightmaterial", gate, value }));
        FIELDS.put("woosh", (sender, gate, value) ->
            new WooshDepthCommand().execute(sender, new String[] { "wooshdepth", gate, value }));
        FIELDS.put("redstone", (sender, gate, value) ->
            new RedstoneCommand().execute(sender, new String[] { "redstone", gate, value }));
        FIELDS.put(OWNER, (sender, gate, value) ->
            new OwnerCommand().execute(sender, value.isEmpty()
                ? new String[] { OWNER, gate } : new String[] { OWNER, gate, value }));
        FIELDS.put("custom", (sender, gate, value) ->
            new CustomCommand().execute(sender, new String[] { "custom", gate, value }));
        // The odd one out: WXIDC was written as a standalone command and takes its own
        // arguments from index zero, where the handlers above still expect the subcommand
        // name in front of them. No value reports the code; -clear is what clears it.
        FIELDS.put(IDC, (sender, gate, value) ->
            new WXIDC().onCommand(sender, null, IDC, value.isEmpty()
                ? new String[] { gate } : new String[] { gate, value }));
        FIELDS.put("group", GateEditCommand::setGroup);
        FIELDS.put("spin", GateEditCommand::setSpin);
        FIELDS.put(IRIS_ANIMATION, GateEditCommand::setIrisAnimation);
    }

    /**
     * Sets how this gate's iris crosses (#427), or with {@code default} clears it so the gate
     * follows its material group and then {@code gate-iris-animation}. No value says what it is.
     *
     * @param sender
     *            who typed it
     * @param gateName
     *            the gate
     * @param value
     *            an animation, {@code default}, or empty
     * @return true, the command was handled
     */
    // Behind the Field interface, whose other implementations do return false.
    @SuppressWarnings("java:S3516")
    private static boolean setIrisAnimation(final CommandSender sender, final String gateName, final String value)
    {
        final Stargate gate =
            StargateManager.getStargate(gateName);
        if (gate == null)
        {
            sender.sendMessage(CommandUtilities.noGateCalled(sender, gateName));
            return true;
        }
        final String animations = ChatText.info(Messages.get(sender, "gate.edit.iris-animation.list",
            options(sender, irisAnimationNames())));
        if ((value == null) || value.isEmpty())
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.edit.iris-animation.is",
                ChatText.name(gate.getGateName()), ChatText.value(gate.getEffectiveIrisAnimation()),
                irisAnimationSource(sender, gate))));
            sender.sendMessage(animations);
            return true;
        }
        final boolean clear = DEFAULT.equalsIgnoreCase(value.trim());
        final String animation = clear ? null : ConfigManager.parseIrisAnimation(value);
        if ((animation == null) && !clear)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.edit.iris-animation.unknown",
                ChatText.command(value))));
            sender.sendMessage(animations);
            return true;
        }
        gate.setGateIrisAnimation(animation);
        StargateDBManager.saveStargate(gate);
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.edit.iris-animation.now",
            ChatText.name(gate.getGateName()), ChatText.value(gate.getEffectiveIrisAnimation()),
            irisAnimationSource(sender, gate))));
        return true;
    }

    /** Values to type, each white, for a line that lists them. */
    private static String options(final CommandSender sender, final List<String> values)
    {
        return joined(sender, values.stream().map(ChatText::command).toList());
    }

    /** @return the entries joined as {@code sender}'s language joins a list */
    private static String joined(final CommandSender sender, final List<String> entries)
    {
        return String.join(Messages.get(sender, "gate.edit.separator"), entries);
    }

    /** Where a gate's iris animation comes from, when not its own. */
    private static String irisAnimationSource(final CommandSender sender, final Stargate gate)
    {
        if (gate.getGateIrisAnimation() != null)
        {
            return "";
        }
        final MaterialGroup group = gate.getGateMaterialGroup();
        return inherited(sender, ((group != null) && (group.getIrisAnimation() != null)) ? group.getName() : null);
    }

    /** @return {@code (from group X)} for a group that sets it, else {@code (the server default)}, each with its space */
    private static String inherited(final CommandSender sender, final String groupName)
    {
        if (groupName != null)
        {
            return Messages.get(sender, "gate.edit.source.group", ChatText.name(groupName));
        }
        return Messages.get(sender, "gate.edit.source.default");
    }

    /**
     * The iris animations and {@code default}, for tab completion and messages.
     *
     * @return the values {@code gate edit <gate> iris-animation} takes
     */
    public static List<String> irisAnimationNames()
    {
        final List<String> names = new ArrayList<>(ConfigManager.irisAnimations());
        names.add(DEFAULT);
        return names;
    }

    /** The value that clears a gate's own ring pattern. */
    private static final String DEFAULT = "default";

    /** The value that hands a gate's group back to its frame. */
    private static final String CLEAR = "-clear";

    /**
     * Sets the ring pattern this gate dials with (#366), or with {@code default} clears it so the
     * gate follows its material group and then {@code gate-dial-spin}. No value says what it is.
     *
     * @param sender
     *            who typed it
     * @param gateName
     *            the gate's name; one that matches no gate is reported to the sender
     * @param value
     *            a pattern, {@code default}, or empty
     * @return true, the command was handled
     */
    // Behind the Field interface, whose other implementations do return false.
    @SuppressWarnings("java:S3516")
    private static boolean setSpin(final CommandSender sender, final String gateName, final String value)
    {
        final Stargate gate =
            StargateManager.getStargate(gateName);
        if (gate == null)
        {
            sender.sendMessage(CommandUtilities.noGateCalled(sender, gateName));
            return true;
        }
        final String patterns = ChatText.info(Messages.get(sender, "gate.edit.spin.list", options(sender, spinNames())));
        if ((value == null) || value.isEmpty())
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.edit.spin.is", ChatText.name(gate.getGateName()),
                ChatText.value(spinName(gate.getEffectiveDialSpin())), spinSource(sender, gate))));
            sender.sendMessage(patterns);
            return true;
        }
        final DialSpinPattern pattern = DEFAULT.equalsIgnoreCase(value.trim()) ? null : DialSpinPattern.parse(value);
        if ((pattern == null) && !DEFAULT.equalsIgnoreCase(value.trim()))
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.edit.spin.unknown", ChatText.command(value))));
            sender.sendMessage(patterns);
            return true;
        }
        gate.setGateDialSpin(pattern);
        StargateDBManager.saveStargate(gate);
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.edit.spin.now", ChatText.name(gate.getGateName()),
            ChatText.value(spinName(gate.getEffectiveDialSpin())), spinSource(sender, gate))));
        return true;
    }

    /** Where a gate's pattern comes from, when not its own. */
    private static String spinSource(final CommandSender sender, final Stargate gate)
    {
        if (gate.getGateDialSpin() != null)
        {
            return "";
        }
        final MaterialGroup group = gate.getGateMaterialGroup();
        return inherited(sender, ((group != null) && (group.getDialSpin() != null)) ? group.getName() : null);
    }

    private static String spinName(final DialSpinPattern pattern)
    {
        return pattern.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The ring patterns and {@code default}, for tab completion and messages.
     *
     * @return the values {@code gate edit <gate> spin} takes
     */
    public static List<String> spinNames()
    {
        final List<String> names = Arrays.stream(DialSpinPattern.values())
            .map(GateEditCommand::spinName)
            .collect(Collectors.toCollection(ArrayList::new));
        names.add(DEFAULT);
        return names;
    }

    /**
     * Puts a gate on a different material group.
     *
     * <p>The one field with no command of its own before this, and the reason it is worth
     * having: a group is the whole palette at once, so restyling a gate was four commands --
     * portal, iris, light, and whatever the frame was meant to be.
     *
     * <p>The frame blocks themselves are left alone. They are real blocks somebody built, and
     * rewriting them is not what "change this gate's group" should quietly mean. What changes
     * is what the gate draws: its portal, its lights, and the iris it places when sealed.
     *
     * @param sender
     *            who typed it
     * @param gateName
     *            the gate to restyle
     * @param value
     *            the group name
     * @return true, the command was handled
     */
    // Behind the Field interface, whose other implementations do return false.
    @SuppressWarnings("java:S3516")
    private static boolean setGroup(final CommandSender sender, final String gateName,
        final String value)
    {
        final Stargate gate =
            StargateManager.getStargate(gateName);
        if (gate == null)
        {
            sender.sendMessage(CommandUtilities.noGateCalled(sender, gateName));
            return true;
        }
        final String name = ChatText.name(gate.getGateName());
        if ((value == null) || value.isEmpty())
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.edit.group.is", name,
                groupName(sender, gate.getGateMaterialGroup()))));
            sender.sendMessage(groupList(sender));
            return true;
        }
        // A dash, as options take, so a group an admin named "default" can still be chosen.
        if (CLEAR.equalsIgnoreCase(value.trim()))
        {
            gate.chooseGateMaterialGroup(null);
            StargateDBManager.saveStargate(gate);
            sender.sendMessage(ChatText.info(Messages.get(sender, "gate.edit.group.cleared", name,
                groupName(sender, gate.getGateMaterialGroup()))));
            return true;
        }
        final MaterialGroup group =
            MaterialGroupRegistry.getGroup(value);
        if (group == null)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.edit.group.unknown", ChatText.name(value))));
            sender.sendMessage(groupList(sender));
            return true;
        }
        gate.chooseGateMaterialGroup(group);
        StargateDBManager.saveStargate(gate);
        sender.sendMessage(ChatText.info(Messages.get(sender, "gate.edit.group.set", name,
            ChatText.name(group.getName()))));
        return true;
    }

    /** @return a group's name in aqua, or {@code (none)} for no group */
    private static String groupName(final CommandSender sender, final MaterialGroup group)
    {
        if (group == null)
        {
            return Messages.get(sender, "gate.edit.group.none");
        }
        return ChatText.name(group.getName());
    }

    /** The line listing the material groups, each aqua. */
    private static String groupList(final CommandSender sender)
    {
        return ChatText.info(Messages.get(sender, "gate.edit.group.list",
            joined(sender, groupNames().stream().map(ChatText::name).toList())));
    }

    /**
     * Every material group there is, by name.
     */
    public static List<String> groupNames()
    {
        return MaterialGroupRegistry.getGroups().stream()
            .map(MaterialGroup::getName)
            .sorted()
            .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * The field names, for tab completion and for the help line.
     *
     * @return the fields in the order they are offered
     */
    public static List<String> fieldNames()
    {
        return new ArrayList<>(FIELDS.keySet());
    }

    /**
     * Whether a name is one of the fields.
     *
     * @param field
     *            the name typed
     * @return true if it is a field
     */
    public static boolean isField(final String field)
    {
        return (field != null) && FIELDS.containsKey(field.toLowerCase(Locale.ROOT));
    }

    /**
     * Whether a line of {@code gate} may be run without {@code wormhole.config}.
     *
     * @param args
     *            the full argument array, {@code gate} at index 0
     * @return true for {@code gate edit <gate> idc}, which decides for itself who may
     */
    public static boolean admitsWithoutConfig(final String[] args)
    {
        return (args.length > 1) && "edit".equalsIgnoreCase(args[1]) && editsTheCode(args);
    }

    private static boolean editsTheCode(final String[] args)
    {
        return (args.length > 3) && IDC.equalsIgnoreCase(args[3]);
    }

    // Bukkit reads the boolean as "handled"; every path here has handled it.
    @SuppressWarnings("java:S3516")
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        // Checked here rather than only in the legacy handlers each field delegates to.
        // Most fields inherit a check that way already, but "group" does not delegate to
        // anything -- it is its own logic below -- so it had none at all until this. One
        // guard on the front door covers every field today and covers whatever field is
        // added next without relying on its author to remember this. The code is the one
        // exception: WXIDC admits the gate's owner as well, and checks for itself.
        if (!editsTheCode(args) && CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return true;
        }
        // args: gate edit <gate> <field> [value]
        if (args.length < 4)
        {
            sender.sendMessage(ChatText.info(ChatText.usage("/wormhole gate edit <gate> <field> [value]")));
            sender.sendMessage(fieldList(sender));
            return true;
        }
        final String gate = args[2];
        final String field = args[3].toLowerCase(Locale.ROOT);
        final Field handler = FIELDS.get(field);
        if (handler == null)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "gate.edit.no-field", ChatText.command(args[3]))));
            sender.sendMessage(fieldList(sender));
            return true;
        }
        // Most fields report their current value when given nothing, so an absent value is
        // passed through rather than refused here. The handler that owns the field decides.
        final String value = (args.length > 4)
            ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : "";
        return handler.apply(sender, gate, value);
    }

    /** The line listing the fields. */
    private static String fieldList(final CommandSender sender)
    {
        return ChatText.info(Messages.get(sender, "gate.edit.fields", options(sender, fieldNames())));
    }
}
