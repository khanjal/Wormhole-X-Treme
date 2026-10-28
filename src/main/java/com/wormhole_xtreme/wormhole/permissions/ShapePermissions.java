package com.wormhole_xtreme.wormhole.permissions;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;

/**
 * The permission node for building each gate shape, {@code wormhole.build.<shape>} (#631).
 *
 * <p>{@code wormhole.build} stays the wildcard: a player holding it builds every shape, as before.
 * A server that wants a rank to build only the small shape leaves {@code wormhole.build} off that
 * rank and gives it {@code wormhole.build.standard}. The node is the shape's name, lower case, with
 * every character that is not a letter, a digit or an underscore made an underscore.
 */
public final class ShapePermissions
{
    /** What a shape's node begins with. */
    static final String BUILD_PREFIX = "wormhole.build.";

    /**
     * Words after {@code wormhole.build.} that are nodes already: a shape called "Preview" would
     * otherwise be built by anybody who may preview. Such a shape has no node of its own.
     */
    private static final Set<String> TAKEN = Set.of("preview", "mindistance");

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
        if (shapeName == null)
        {
            return null;
        }
        final String word = shapeName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return (word.replace("_", "").isEmpty() || TAKEN.contains(word)) ? null : (BUILD_PREFIX + word);
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
     * Declares the node of each shape to the server, so a permissions plugin can list and
     * complete them. Operators have them by default, as they have {@code wormhole.build}.
     *
     * @param shapeNames
     *            the shapes now loaded
     */
    public static void declare(final Collection<String> shapeNames)
    {
        final Server server = Bukkit.getServer();
        if ((server == null) || (server.getPluginManager() == null))
        {
            return;
        }
        for (final String name : shapeNames)
        {
            final String node = buildNode(name);
            try
            {
                if ((node != null) && (server.getPluginManager().getPermission(node) == null))
                {
                    server.getPluginManager().addPermission(new Permission(node,
                        "Allow building a " + name + " gate.", PermissionDefault.OP));
                }
            }
            catch (final RuntimeException refused)
            {
                // A node that is not declared still works; it is only not listed. Loading shapes goes on.
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Could not declare " + node, refused);
            }
        }
    }
}
