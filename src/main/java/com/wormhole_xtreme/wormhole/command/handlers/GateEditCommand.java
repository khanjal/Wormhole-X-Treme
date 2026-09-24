package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.command.WXIDC;
import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;

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
        // name in front of them.
        FIELDS.put("idc", (sender, gate, value) ->
            new WXIDC().onCommand(sender, null, "idc", new String[] { gate, value }));
        FIELDS.put("group", GateEditCommand::setGroup);
        FIELDS.put("spin", GateEditCommand::setSpin);
    }

    /** The value that clears a gate's own ring pattern. */
    private static final String DEFAULT = "default";

    /**
     * Sets the ring pattern this gate dials with (#366), or with {@code default} clears it so the
     * gate follows its material group and then {@code gate-dial-spin}. No value says what it is.
     *
     * @param sender
     *            who typed it
     * @param gateName
     *            the gate
     * @param value
     *            a pattern, {@code default}, or empty
     * @return true, the command was handled
     */
    // Behind the Field interface, whose other implementations do return false.
    @SuppressWarnings("java:S3516")
    private static boolean setSpin(final CommandSender sender, final String gateName, final String value)
    {
        final com.wormhole_xtreme.wormhole.model.Stargate gate =
            com.wormhole_xtreme.wormhole.model.StargateManager.getStargate(gateName);
        if (gate == null)
        {
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "No gate called " + gateName + ".");
            return true;
        }
        if ((value == null) || value.isEmpty())
        {
            sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + gate.getGateName()
                + " dials with " + spinName(gate.getEffectiveDialSpin()) + spinSource(gate)
                + ". Patterns are: " + String.join(", ", spinNames()) + ".");
            return true;
        }
        final DialSpinPattern pattern = DEFAULT.equalsIgnoreCase(value.trim()) ? null : DialSpinPattern.parse(value);
        if ((pattern == null) && !DEFAULT.equalsIgnoreCase(value.trim()))
        {
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "No ring pattern called " + value
                + ". Patterns are: " + String.join(", ", spinNames()) + ".");
            return true;
        }
        gate.setGateDialSpin(pattern);
        StargateDBManager.saveStargate(gate);
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + gate.getGateName()
            + " now dials with " + spinName(gate.getEffectiveDialSpin()) + spinSource(gate) + ".");
        return true;
    }

    /** Where a gate's pattern comes from, when not its own. */
    private static String spinSource(final com.wormhole_xtreme.wormhole.model.Stargate gate)
    {
        if (gate.getGateDialSpin() != null)
        {
            return "";
        }
        final com.wormhole_xtreme.wormhole.model.MaterialGroup group = gate.getGateMaterialGroup();
        return ((group != null) && (group.getDialSpin() != null))
            ? " (from group " + group.getName() + ")" : " (the server default)";
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
        final List<String> names = new ArrayList<>();
        for (final DialSpinPattern pattern : DialSpinPattern.values())
        {
            names.add(spinName(pattern));
        }
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
        final com.wormhole_xtreme.wormhole.model.Stargate gate =
            com.wormhole_xtreme.wormhole.model.StargateManager.getStargate(gateName);
        if (gate == null)
        {
            sender.sendMessage("No gate called " + gateName + ".");
            return true;
        }
        if ((value == null) || value.isEmpty())
        {
            final com.wormhole_xtreme.wormhole.model.MaterialGroup current =
                gate.getGateMaterialGroup();
            sender.sendMessage(gate.getGateName() + " is on group "
                + (current == null ? "(none)" : current.getName()) + ". Groups are: "
                + String.join(", ", groupNames()) + ".");
            return true;
        }
        final com.wormhole_xtreme.wormhole.model.MaterialGroup group =
            com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry.getGroup(value);
        if (group == null)
        {
            sender.sendMessage("No material group called " + value + ". Groups are: "
                + String.join(", ", groupNames()) + ".");
            return true;
        }
        gate.setGateMaterialGroup(group);
        sender.sendMessage(gate.getGateName() + " is now on group " + group.getName()
            + ". Its frame blocks are untouched -- this changes what the gate draws.");
        return true;
    }

    /**
     * Every material group there is, by name.
     *
     * @return the group names
     */
    public static List<String> groupNames()
    {
        final List<String> names = new ArrayList<>();
        for (final com.wormhole_xtreme.wormhole.model.MaterialGroup g
            : com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry.getGroups())
        {
            names.add(g.getName());
        }
        java.util.Collections.sort(names);
        return names;
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

    // Bukkit reads the boolean as "handled"; every path here has handled it.
    @SuppressWarnings("java:S3516")
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        // Checked here rather than only in the legacy handlers each field delegates to.
        // Most fields inherit a check that way already, but "group" does not delegate to
        // anything -- it is its own logic below -- so it had none at all until this. One
        // guard on the front door covers every field today and covers whatever field is
        // added next without relying on its author to remember this.
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return true;
        }
        // args: gate edit <gate> <field> [value]
        if (args.length < 4)
        {
            sender.sendMessage("Usage: /wormhole gate edit <gate> <"
                + String.join("|", FIELDS.keySet()) + "> [value]");
            return true;
        }
        final String gate = args[2];
        final String field = args[3].toLowerCase(Locale.ROOT);
        final Field handler = FIELDS.get(field);
        if (handler == null)
        {
            sender.sendMessage("No such field: " + args[3] + ". Fields are: "
                + String.join(", ", FIELDS.keySet()) + ".");
            return true;
        }
        // Several of these read as "unset" when given nothing -- idc clears a code, owner
        // reports the current one -- so an absent value is passed through rather than
        // refused here. The handler that owns the field decides what no value means.
        final String value = (args.length > 4)
            ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : "";
        return handler.apply(sender, gate, value);
    }
}
