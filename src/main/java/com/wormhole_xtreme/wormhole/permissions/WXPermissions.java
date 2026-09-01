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
        if (player == null)
        {
            return false;
        }
        // An operator may do anything with a gate, with or without a permissions plugin
        // installed. This deliberately outranks a negated node: on a server where someone
        // has been given op, that is taken as the final word.
        //
        // Written as a blanket allow rather than a list of the permission types that
        // happen to exist today. It used to be a switch naming all ten with
        // `default: return false`, so adding an eleventh type and forgetting to list it
        // would have silently denied it to operators — the failure would look like a
        // permissions plugin misconfiguration rather than a missing case.
        if (player.isOp())
        {
            return true;
        }
        // If a gate exists but has no owner, treat it as public: allow common use actions.
        // This makes owner==null gates usable by any player.
        if ((stargate != null) && (stargate.getGateOwner() == null))
        {
            switch (permissiontype)
            {
                case SIGN :
                case DIALER :
                case USE :
                case LIST :
                case COMPASS :
                    return true;
                default :
                    break;
            }
        }
        // Gate owner should always be allowed to use and manage their own gate.
        if ((stargate != null) && (stargate.getGateOwner() != null) && stargate.isOwner(player))
        {
            switch (permissiontype)
            {
                case DAMAGE :
                case REMOVE :
                case CONFIG :
                case GO :
                case SIGN :
                case DIALER :
                case USE :
                case LIST :
                case COMPASS :
                case BUILD :
                    return true;
                default :
                    return false;
            }
        }
        else if (!ConfigManager.getPermissionsSupportDisable())
        {

            {
                String networkName = "Public";
                switch (permissiontype)
                {
                    case LIST :
                        return (ComplexPermission.LIST.checkPermission(player) || ComplexPermission.CONFIG.checkPermission(player));
                    case CONFIG :
                        return ComplexPermission.CONFIG.checkPermission(player);
                    case GO :
                        return ComplexPermission.GO.checkPermission(player);
                    case COMPASS :
                        return ComplexPermission.USE_COMPASS.checkPermission(player);
                    case DAMAGE :
                    case REMOVE :
                        return (ComplexPermission.CONFIG.checkPermission(player) || ComplexPermission.REMOVE_ALL.checkPermission(player) || ComplexPermission.REMOVE_OWN.checkPermission(player, stargate));
                    case SIGN :
                        if ((stargate != null) && (stargate.getGateNetwork() != null))
                        {
                            networkName = stargate.getGateNetwork().getNetworkName();
                        }
                        return ((ComplexPermission.USE_SIGN.checkPermission(player) && (networkName.equals("Public") || ( !networkName.equals("Public") && ComplexPermission.NETWORK_USE.checkPermission(player, networkName)))));
                    case DIALER :
                        if ((stargate != null) && (stargate.getGateNetwork() != null))
                        {
                            networkName = stargate.getGateNetwork().getNetworkName();
                        }
                        return ((ComplexPermission.USE_DIALER.checkPermission(player) && (networkName.equals("Public") || ( !networkName.equals("Public") && ComplexPermission.NETWORK_USE.checkPermission(player, networkName)))));
                    case USE :
                        if ((stargate != null) && (stargate.getGateNetwork() != null))
                        {
                            networkName = stargate.getGateNetwork().getNetworkName();
                        }
                        return (((ComplexPermission.USE_SIGN.checkPermission(player) && (networkName.equals("Public") || ( !networkName.equals("Public") && ComplexPermission.NETWORK_USE.checkPermission(player, networkName)))) || (ComplexPermission.USE_DIALER.checkPermission(player) && (networkName.equals("Public") || ( !networkName.equals("Public") && ComplexPermission.NETWORK_USE.checkPermission(player, networkName))))));
                    case BUILD :
                        if (stargate != null)
                        {
                            if (stargate.getGateNetwork() != null)
                            {
                                networkName = stargate.getGateNetwork().getNetworkName();
                            }
                        }
                        else
                        {
                            if (network != null)
                            {
                                networkName = network;
                            }
                        }
                        return ((ComplexPermission.BUILD.checkPermission(player) && (networkName.equals("Public") || ( !networkName.equals("Public") && ComplexPermission.NETWORK_BUILD.checkPermission(player, networkName)))));
                    // legacy cooldown group permission checks removed
                    // legacy build-group permission checks removed
                    default :
                        return false;
                }
            }
        }
        else
        {
            // Simple mode: no permission plugin installed.
            // Any player may use/dial/travel through gates.
            // Build, remove, and config actions require OP (already handled above).
            switch (permissiontype)
            {
                case SIGN :
                case DIALER :
                case USE :
                case LIST :
                case COMPASS :
                    return true;
                default :
                    return false;
            }
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
