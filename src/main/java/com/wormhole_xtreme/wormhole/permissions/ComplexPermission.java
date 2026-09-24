package com.wormhole_xtreme.wormhole.permissions;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * The Enum ComplexPermission.
 * 
 * @author alron
 */
enum ComplexPermission
{

    /** Sign Dialer Use */
    USE_SIGN("wormhole.use.sign"),

    /** Normal Dial Use */
    USE_DIALER("wormhole.use.dialer"),

    /** Compass Use */
    USE_COMPASS("wormhole.use.compass"),

    // Per-group cooldown permission nodes removed; cooldowns handled centrally when enabled.

    /** Remove Own */
    REMOVE_OWN("wormhole.remove.own"),

    /** Remove All */
    REMOVE_ALL("wormhole.remove.all"),

    /** Build */
    BUILD("wormhole.build"),
    /** Build */
    // Build restriction group permission nodes removed; use standard permission backend instead.

    /** Config */
    CONFIG("wormhole.config"),

    /** List */
    LIST("wormhole.list"),

    /** Use Network */
    NETWORK_USE("wormhole.network.use."),

    /** Build Network. */
    NETWORK_BUILD("wormhole.network.build."),

    /** Go. */
    GO("wormhole.go");

    /** The complex permission node. */
    private final String complexPermissionNode;

    /** The Constant complexPermissionMap. */
    private static final Map<String, ComplexPermission> complexPermissionMap = new HashMap<>();

    static
    {
        for (final ComplexPermission type : EnumSet.allOf(ComplexPermission.class))
        {
            complexPermissionMap.put(type.complexPermissionNode, type);
        }
    }

    /**
     * From complex permission node.
     * 
     * @return the permission with exactly that node, or null if none has it
     */
    public static ComplexPermission fromComplexPermissionNode(final String complexPermissionNode) // NO_UCD
    {
        return complexPermissionMap.get(complexPermissionNode);
    }

    /**
     * Instantiates a new complex permission.
     * 
     * @param complexPermissionNode
     *            the node checked, or for the network permissions the prefix a network name completes
     */
    private ComplexPermission(final String complexPermissionNode)
    {
        this.complexPermissionNode = complexPermissionNode;
    }

    /**
     * Check permission.
     * 
     * @return true, if successful
     */
    protected boolean checkPermission(final Player player)
    {
        return checkPermission(player, null, null);
    }

    /**
     * Check permission.
     * 
     * @param stargate
     *            the gate acted on; only {@code REMOVE_OWN} looks at it
     * @return true, if successful
     */
    protected boolean checkPermission(final Player player, final Stargate stargate)
    {
        return checkPermission(player, stargate, null);
    }

    /**
     * Check permission.
     * 
     * @return true, if successful
     */
    public boolean checkPermission(final Player player, final Stargate stargate, final String networkName)
    {
        if (player != null)
        {
            boolean allowed = false;

            switch (this)
            {
                case NETWORK_USE, NETWORK_BUILD:
                    allowed = (networkName != null)
                        && player.hasPermission(complexPermissionNode + networkName);
                    break;
                case REMOVE_OWN :
                    allowed = ((stargate != null) && (stargate.getGateOwner() != null) && stargate.isOwner(player) && player.hasPermission(complexPermissionNode));
                    break;
                default :
                    allowed = player.hasPermission(complexPermissionNode);
                    break;
            }
            if (allowed)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Player: " + player.getName() + "\" granted complex \"" + toString() + "\" permission" + (networkName != null
                    ? " on network \"" + networkName + "\""
                    : "") + ".");
                return true;
            }
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Player: " + player.getName() + "\" denied complex \"" + toString() + "\" permission" + (networkName != null
                ? " on network \"" + networkName + "\""
                : "") + ".");
        }
        return false;
    }

    /**
     * Check permission.
     * 
     * @return true, if successful
     */
    protected boolean checkPermission(final Player player, final String networkName)
    {
        return checkPermission(player, null, networkName);
    }

    /**
     * Gets the complex permission.
     * 
     * @return the node, which for the network permissions is a prefix the network name completes
     */
    public String getComplexPermission()
    {
        return complexPermissionNode;
    }
}
