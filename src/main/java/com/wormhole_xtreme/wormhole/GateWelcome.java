package com.wormhole_xtreme.wormhole;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * Tells a player which gate they have just come out of, when {@code show-gate-welcome-message} is on.
 */
final class GateWelcome
{
    private GateWelcome()
    {
    }

    /**
     * Greets a traveller who has arrived through a gate.
     *
     * @param traveller
     *            whatever arrived; only a player is greeted, so a pet or a mount says nothing
     * @param destination
     *            the gate they arrived at
     */
    static void greet(final Entity traveller, final Stargate destination)
    {
        if ((traveller instanceof Player player) && (destination != null) && ConfigManager.isShowGateWelcomeMessage())
        {
            player.sendMessage(ConfigManager.MessageStrings.GATE_ARRIVED.toString() + destination.getGateName());
        }
    }
}
