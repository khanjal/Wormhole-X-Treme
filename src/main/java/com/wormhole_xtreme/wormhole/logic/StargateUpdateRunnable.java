// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2011 Ben Echols, Dean Bailey. See LICENSE.txt for terms.
package com.wormhole_xtreme.wormhole.logic;

import java.util.logging.Level;

import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;

/**
 * WormholeXtreme Runnable thread for updating stargates.
 * 
 * @author Ben Echols (Lologarithm)
 */
public class StargateUpdateRunnable implements Runnable
{

    /**
     * The Enum ActionToTake.
     */
    public enum ActionToTake
    {

        /** The SHUTDOWN task. */
        SHUTDOWN,

        /** The ANIMATE OPENING task. */
        ANIMATE_WOOSH,

        /** The DEACTIVATE task. */
        DEACTIVATE,

        /** The AFTERSHUTDOWN task. */
        AFTERSHUTDOWN,

        /** The SIGNCLICK. */
        DIAL_SIGN_CLICK,

        /** Action to iterate over lighting up blocks during activation. */
        LIGHTUP,

        COOLDOWN_REMOVE,
        DIAL_SIGN_RESET;
    }

    /** The stargate. */
    private final Stargate stargate;

    /** The player. */
    private final Player player;

    /** The action. */
    private final ActionToTake action;

    public StargateUpdateRunnable(final Player player, final ActionToTake action)
    {
        this(null, player, action);
    }

    /**
     * Instantiates a new stargate update runnable.
     * 
     * @param stargate
     *            the s
     * @param action
     *            the act
     */
    public StargateUpdateRunnable(final Stargate stargate, final ActionToTake action)
    {
        this(stargate, null, action);
    }

    /**
     * Instantiates a new stargate update runnable.
     * 
     * @param stargate
     *            the s
     * @param player
     *            the p
     * @param action
     *            the act
     */
    public StargateUpdateRunnable(final Stargate stargate, final Player player, final ActionToTake action)
    {
        this.stargate = stargate;
        this.action = action;
        this.player = player;
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run()
    {
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Run Action \"" + action.toString() + (stargate != null
            ? "\" Stargate \"" + stargate.getGateName()
            : "") + "\"");
        switch (action)
        {
            case SHUTDOWN :
                stargate.shutdownStargate(true);
                break;
            case ANIMATE_WOOSH :
                stargate.animateOpening();
                break;
            case DEACTIVATE :
                stargate.timeoutStargate(player);
                break;
            case AFTERSHUTDOWN :
                stargate.stopAfterShutdownTimer();
                break;
            case DIAL_SIGN_CLICK :
                stargate.teleportSignClicked();
                if (player != null)
                {
                    if (stargate.getGateDialSignTarget() != null)
                    {
                        final String target = stargate.getGateDialSignTarget().getGateName();
                        player.sendMessage("Dialer set to: " + target);
                    }
                    else
                    {
                        player.sendMessage("No available target to set dialer to.");
                    }
                }
                break;
            case DIAL_SIGN_RESET :
                stargate.resetSign(true);
                break;
            case LIGHTUP :
                stargate.lightStargate(true);
                break;
            case COOLDOWN_REMOVE :
                StargateRestrictions.removePlayerUseCooldown(player);
                break;
            default :
                break;
        }
    }
}
