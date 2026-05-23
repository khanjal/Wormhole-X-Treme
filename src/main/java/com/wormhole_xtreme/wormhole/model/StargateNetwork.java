package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.HashMap;

import com.wormhole_xtreme.wormhole.permissions.PermissionsManager;

/**
 * WormholeXtreme StargateNetwork.
 * 
 * @author Ben Echols (Lologarithm)
 * @author Dean Bailey (alron)
 */
public class StargateNetwork
{

    /** The net name. */
    private String networkName;

    /** The gate list. */
    private final ArrayList<Stargate> networkGateList = new ArrayList<Stargate>();

    /** The sign gate list. */
    private final ArrayList<Stargate> networkSignGateList = new ArrayList<Stargate>();

    /** The gate lock. */
    private Object networkGateLock = new Object();

    /** The individual permissions. */
    private final HashMap<String, PermissionsManager.PermissionLevel> networkIndividualPermissions = new HashMap<String, PermissionsManager.PermissionLevel>();

    /**
     * Gets the network gate list.
     * 
     * @return the network gate list
     */
    public ArrayList<Stargate> getNetworkGateList()
    {
        return networkGateList;
    }

    /**
     * Gets the network gate lock.
     * 
     * @return the network gate lock
     */
    public Object getNetworkGateLock()
    {
        return networkGateLock;
    }

    /**
     * Gets the network individual permissions.
     * 
     * @return the network individual permissions
     */
    public HashMap<String, PermissionsManager.PermissionLevel> getNetworkIndividualPermissions()
    {
        return networkIndividualPermissions;
    }

    /**
     * Gets the network name.
     * 
     * @return the network name
     */
    public String getNetworkName()
    {
        return networkName;
    }

    /**
     * Gets the network sign gate list.
     * 
     * @return the network sign gate list
     */
    public ArrayList<Stargate> getNetworkSignGateList()
    {
        return networkSignGateList;
    }

    /**
     * Sets the network gate lock.
     * 
     * @param networkGateLock
     *            the new network gate lock
     */
    public void setNetworkGateLock(final Object networkGateLock)
    {
        this.networkGateLock = networkGateLock;
    }

    /**
     * Sets the network name.
     * 
     * @param networkName
     *            the new network name
     */
    public void setNetworkName(final String networkName)
    {
        this.networkName = networkName;
    }
}
