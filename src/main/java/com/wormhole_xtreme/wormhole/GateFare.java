package com.wormhole_xtreme.wormhole;

import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;

/**
 * What a gate trip costs: asked before the trip, taken only once it has happened.
 *
 * <p>Shared by the player and vehicle listeners, so whichever of them actually moves a rider
 * is the one that charges them.
 */
final class GateFare
{
    private GateFare() {}

    /**
     * What this trip will cost, if the player can afford it.
     *
     * @param player
     *            the traveller
     * @return the fare to take once the trip is certain, 0 if there is none, or -1 if they
     *         cannot afford it and have been told so
     */
    static double affordable(final Player player)
    {
        if (!ConfigManager.isEconomyEnabled() || !EconomySupport.isAvailable())
        {
            return 0.0;
        }
        final double useCost = ConfigManager.getEconomyUseCost();
        if (useCost <= 0)
        {
            return 0.0;
        }
        if (!EconomySupport.canAfford(player, useCost))
        {
            player.sendMessage(ConfigManager.MessageStrings.ECONOMY_INSUFFICIENT_FUNDS.toString());
            return -1.0;
        }
        return useCost;
    }

    /**
     * Takes the fare, now that the trip has actually happened.
     *
     * @param player
     *            the traveller
     * @param fare
     *            what they owe, 0 for nothing
     */
    static void charge(final Player player, final double fare)
    {
        if (fare > 0)
        {
            EconomySupport.charge(player, fare);
            player.sendMessage(ConfigManager.MessageStrings.ECONOMY_CHARGED.toString()
                + fare + " " + EconomySupport.currencyName(fare));
        }
    }
}
