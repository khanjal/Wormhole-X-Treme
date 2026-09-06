package com.wormhole_xtreme.wormhole.permissions;
import org.bukkit.entity.Player;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;


/**
 * The Class WXPermissions.
 * 
 * @author alron
 */
public class WXPermissions
{

    /**
     * The Enum PermissionType.
     */
    public static enum PermissionType
    {

        /** The DAMAGE permission. */
        DAMAGE,

        /** The SIGN permission. */
        SIGN,

        /** The DIALER permission. */
        DIALER,

        /** The BUILD permission. */
        BUILD,

        /** The REMOVE permission. */
        REMOVE,

        /** The USE permission. */
        USE,

        /** The LIST permission. */
        LIST,

        /** The CONFIG permission. */
        CONFIG,

        /** The GO permission. */
        GO,

        /** The COMPASS permission. */
        COMPASS;
    }
    /** The network a gate is on when it names none of its own, and the one open to all. */
    private static final String PUBLIC_NETWORK = "Public";

    /**
     * Check wx permissions.
     * 
     * @param player
     *            the player
     * @param permissiontype
     *            the permissiontype
     * @return true, if successful
     */
    public static boolean checkWXPermissions(final Player player, final PermissionType permissiontype)
    {
        return checkWXPermissions(player, null, null, permissiontype);
    }

    /**
     * Check wx permisssions.
     * 
     * @param player
     *            the player
     * @param stargate
     *            the stargate
     * @param permissionstype
     *            the permissionstype
     * @return true, if successful
     */
    public static boolean checkWXPermissions(final Player player, final Stargate stargate, final PermissionType permissionstype)
    {
        return checkWXPermissions(player, stargate, null, permissionstype);
    }

    /**
     * Check wx permissions.
     * 
     * @param player
     *            the player
     * @param stargate
     *            the stargate
     * @param network
     *            the network
     * @param permissiontype
     *            the permissiontype
     * @return true, if successful
     */
    private static boolean checkWXPermissions(final Player player, final Stargate stargate, final String network, final PermissionType permissiontype)
    {
        // An operator may do anything with a gate, with or without a permissions plugin
        // installed. This deliberately outranks a negated node: on a server where someone
        // has been given op, that is taken as the final word.
        //
        // Written as a blanket allow rather than a list of the permission types that
        // happen to exist today. It used to be a switch naming all ten with
        // `default: return false`, so adding an eleventh type and forgetting to list it
        // would have silently denied it to operators -- the failure would look like a
        // permissions plugin misconfiguration rather than a missing case.
        if (player.isOp())
        {
            return true;
        }
        // A gate with no owner is treated as public: gates built before ownership was
        // recorded have none, and locking everyone out of them would strand them.
        if ((stargate != null) && (stargate.getGateOwner() == null) && isCommonUse(permissiontype))
        {
            return true;
        }
        // The owner may use and manage their own gate.
        if ((stargate != null) && (stargate.getGateOwner() != null) && stargate.isOwner(player))
        {
            return isOwnerAction(permissiontype);
        }
        if (ConfigManager.getPermissionsSupportDisable())
        {
            // Simple mode: no permission plugin installed. Anyone may use, dial and travel;
            // build, remove and config need op, which was settled above.
            return isCommonUse(permissiontype);
        }
        return checkAgainstNodes(player, stargate, network, permissiontype);
    }

    /** The actions any player may take on a gate nobody owns, and the whole of simple mode. */
    private static boolean isCommonUse(final PermissionType permissiontype)
    {
        switch (permissiontype)
        {
            case SIGN:
            case DIALER:
            case USE:
            case LIST:
            case COMPASS:
                return true;
            default:
                return false;
        }
    }

    /** What the owner of a gate may do to it without holding any node. */
    private static boolean isOwnerAction(final PermissionType permissiontype)
    {
        switch (permissiontype)
        {
            case DAMAGE:
            case REMOVE:
            case CONFIG:
            case GO:
            case SIGN:
            case DIALER:
            case USE:
            case LIST:
            case COMPASS:
            case BUILD:
                return true;
            default:
                return false;
        }
    }

    /**
     * The network a check applies to.
     *
     * <p>A gate names its own. Only when there is no gate at all does the caller's network
     * stand in, which is how a build check works before the gate exists.
     */
    private static String networkNameFor(final Stargate stargate, final String fallback)
    {
        if ((stargate != null) && (stargate.getGateNetwork() != null))
        {
            return stargate.getGateNetwork().getNetworkName();
        }
        if ((stargate == null) && (fallback != null))
        {
            return fallback;
        }
        return PUBLIC_NETWORK;
    }

    /**
     * Whether the player is admitted to this network at all.
     *
     * <p>The second half of every network-gated check: holding the node for an action is not
     * admission to a network that is not Public. Public admits everyone.
     */
    private static boolean mayUseNetwork(final Player player, final String networkName)
    {
        return PUBLIC_NETWORK.equals(networkName)
            || ComplexPermission.NETWORK_USE.checkPermission(player, networkName);
    }

    /** As {@link #mayUseNetwork}, for building rather than travelling. */
    private static boolean mayBuildOnNetwork(final Player player, final String networkName)
    {
        return PUBLIC_NETWORK.equals(networkName)
            || ComplexPermission.NETWORK_BUILD.checkPermission(player, networkName);
    }

    /** Whether the player holds the sign node for the network this gate is on. */
    private static boolean maySign(final Player player, final String networkName)
    {
        return ComplexPermission.USE_SIGN.checkPermission(player) && mayUseNetwork(player, networkName);
    }

    /** Whether the player holds the dialler node for the network this gate is on. */
    private static boolean mayDial(final Player player, final String networkName)
    {
        return ComplexPermission.USE_DIALER.checkPermission(player) && mayUseNetwork(player, networkName);
    }

    /**
     * The permission-plugin path: what the player's nodes say about this action.
     *
     * <p>Every gate-facing action is two nodes, not one -- the node for the action, and
     * admission to the network the gate is on.
     */
    private static boolean checkAgainstNodes(final Player player, final Stargate stargate,
        final String network, final PermissionType permissiontype)
    {
        final String networkName = networkNameFor(stargate, network);
        switch (permissiontype)
        {
            case LIST:
                return ComplexPermission.LIST.checkPermission(player)
                    || ComplexPermission.CONFIG.checkPermission(player);
            case CONFIG:
                return ComplexPermission.CONFIG.checkPermission(player);
            case GO:
                // Mirrors DIALER: GO used to skip network privacy entirely, reachable only by
                // passing a null stargate, which meant it could never know which network to
                // check. Go.java looks the gate up and passes it in before checking
                // permission, specifically so this branch has a network name to read.
                return ComplexPermission.GO.checkPermission(player) && mayUseNetwork(player, networkName);
            case COMPASS:
                return ComplexPermission.USE_COMPASS.checkPermission(player);
            case DAMAGE:
            case REMOVE:
                return ComplexPermission.CONFIG.checkPermission(player)
                    || ComplexPermission.REMOVE_ALL.checkPermission(player)
                    || ComplexPermission.REMOVE_OWN.checkPermission(player, stargate);
            case SIGN:
                return maySign(player, networkName);
            case DIALER:
                return mayDial(player, networkName);
            case USE:
                return maySign(player, networkName) || mayDial(player, networkName);
            case BUILD:
                return ComplexPermission.BUILD.checkPermission(player)
                    && mayBuildOnNetwork(player, networkName);
            default:
                return false;
        }
    }


    /**
     * Check wx permissions.
     * 
     * @param player
     *            the player
     * @param network
     *            the network
     * @param permissiontype
     *            the permissiontype
     * @return true, if successful
     */
    public static boolean checkWXPermissions(final Player player, final String network, final PermissionType permissiontype)
    {
        return checkWXPermissions(player, null, network, permissiontype);
    }
}
