package com.wormhole_xtreme.wormhole.permissions;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;

/**
 * The permission nodes for each gate shape, {@code wormhole.build.<shape>} and
 * {@code wormhole.use.<shape>} (#631).
 *
 * <p>{@code wormhole.build} stays the wildcard: a player holding it builds every shape, as before.
 * A server that wants a rank to build only the small shape leaves {@code wormhole.build} off that
 * rank and gives it {@code wormhole.build.standard}. The node is the shape's name, lower case, with
 * every character that is not a letter, a digit or an underscore made an underscore.
 *
 * <p>The use node narrows instead: everyone holds it unless it is set false, and it is wanted on
 * top of {@code wormhole.use.sign} or {@code wormhole.use.dialer} to dial from a gate of that shape.
 */
public final class ShapePermissions
{
    /** What a shape's build node begins with. */
    static final String BUILD_PREFIX = "wormhole.build.";

    /** What a shape's use node begins with. */
    static final String USE_PREFIX = "wormhole.use.";

    /**
     * Words after {@code wormhole.build.} that are nodes already: a shape called "Preview" would
     * otherwise be built by anybody who may preview. Such a shape has no node of its own.
     */
    private static final Set<String> TAKEN = Set.of("preview", "mindistance");

    /** As {@link #TAKEN}, after {@code wormhole.use.}. */
    private static final Set<String> TAKEN_USE = Set.of("sign", "dialer", "compass");

    /** Static helpers only. */
    private ShapePermissions()
    {
    }

    /**
     * The node that lets a player build a shape.
     *
     * @param shapeName
     *            the shape's declared name
     * @return its node, or null if the name leaves nothing to name one by, or the word is taken
     */
    public static String buildNode(final String shapeName)
    {
        return node(BUILD_PREFIX, TAKEN, shapeName);
    }

    /**
     * The node a player needs to dial from a gate of a shape.
     *
     * @param shapeName
     *            the shape's declared name
     * @return its node, or null if the name leaves nothing to name one by, or the word is taken
     */
    public static String useNode(final String shapeName)
    {
        return node(USE_PREFIX, TAKEN_USE, shapeName);
    }

    /** A shape's name made safe after a prefix, or null as {@link #buildNode} says. */
    private static String node(final String prefix, final Set<String> taken, final String shapeName)
    {
        if (shapeName == null)
        {
            return null;
        }
        final String word = shapeName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return (word.replace("_", "").isEmpty() || taken.contains(word)) ? null : (prefix + word);
    }

    /**
     * Whether a player may dial from a gate of this shape: yes unless its use node is set false.
     *
     * @param player
     *            who is dialling
     * @param gate
     *            the gate dialled from, or null where none is involved, as for a mirror
     * @return false only if the shape's node is set and not held
     */
    static boolean mayUse(final Player player, final Stargate gate)
    {
        if ((gate == null) || (gate.getGateShape() == null))
        {
            return true;
        }
        final String node = useNode(gate.getGateShape().getShapeName());
        // Not hasPermission alone: a node the server never declared defaults to op.
        return (node == null) || !player.isPermissionSet(node) || player.hasPermission(node);
    }

    /**
     * Whether a player's own node for the gate's shape lets them build it.
     *
     * @param player
     *            who is building
     * @param gate
     *            the gate found, or null if none is known yet, as when a gate is named before its
     *            frame is clicked: then any shape's node will do, and the shape is checked once
     *            the frame is found
     * @return true if they hold the shape's node
     */
    static boolean mayBuild(final Player player, final Stargate gate)
    {
        if (gate == null)
        {
            return holdsAny(player);
        }
        if (gate.getGateShape() == null)
        {
            return false;
        }
        final String node = buildNode(gate.getGateShape().getShapeName());
        return (node != null) && player.hasPermission(node);
    }

    /** Whether a player holds the node of any shape loaded. */
    private static boolean holdsAny(final Player player)
    {
        for (final String name : StargateShapeRegistry.getStargateShapes().keySet())
        {
            final String node = buildNode(name);
            if ((node != null) && player.hasPermission(node))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Declares the nodes of each shape to the server, so a permissions plugin can list and
     * complete them. Operators have the build nodes by default, as they have {@code wormhole.build};
     * everyone has the use nodes.
     *
     * @param shapeNames
     *            the shapes now loaded
     */
    public static void declare(final Collection<String> shapeNames)
    {
        // None before the server exists (a test, or startup order): then there is nothing to declare to.
        final PluginManager manager = Optional.ofNullable(Bukkit.getServer()).map(Server::getPluginManager).orElse(null);
        if (manager == null)
        {
            return;
        }
        for (final String name : shapeNames)
        {
            declareOne(manager, buildNode(name), "Allow building a " + name + " gate.", PermissionDefault.OP);
            declareOne(manager, useNode(name), "Allow dialling from a " + name + " gate.", PermissionDefault.TRUE);
        }
    }

    /** Declares one node unless it is null or declared already. */
    private static void declareOne(final PluginManager manager, final String node, final String description,
        final PermissionDefault byDefault)
    {
        try
        {
            if ((node != null) && (manager.getPermission(node) == null))
            {
                manager.addPermission(new Permission(node, description, byDefault));
            }
        }
        catch (final RuntimeException refused)
        {
            // A node that is not declared still works; it is only not listed. Loading shapes goes on.
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Could not declare " + node, refused);
        }
    }
}
