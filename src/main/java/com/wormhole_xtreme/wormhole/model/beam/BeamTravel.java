package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;

/**
 * Resolves a name to a beam destination and sends the player there -- shared by
 * {@code /wormhole beam to} and {@code /wormhole go}, so a player can reach the same place
 * either way rather than the two commands quietly disagreeing about what a name means.
 *
 * <p>Cooldown and cost are checked here, before {@link BeamAnimation#start} is even called,
 * but only actually applied from inside its {@code onDepart} hook -- once the real teleport
 * has fired, not at the point of merely starting the sequence. The same split gate travel
 * already makes, and for the same reason: applying either at the check would spend it on a
 * trip that had not happened yet and, if the player disconnected mid-sequence, might never
 * happen at all.
 */
public final class BeamTravel
{
    private BeamTravel() {}

    /**
     * Attempts to send a player to a named destination, checking their own places before the
     * public list -- a private place is a deliberate, personal choice, so it wins if a player
     * happens to have named one the same as something public.
     *
     * <p>Existence is checked before permission, deliberately: a player without
     * {@link BeamPermissions#USE} who names a real destination is told they lack permission,
     * not that nothing exists by that name, which would misreport a permission problem as a
     * typo. Only a name that matches nothing at all returns false, which is what lets a caller
     * like {@code /wormhole go} fall through to try somewhere else (a gate) without this
     * method's own messages getting in the way of that attempt.
     *
     * @param player the traveller
     * @param name the destination name
     * @return true if the name resolved to something and the attempt was fully handled
     *         (travel started, or a refusal was sent); false only if nothing anywhere is
     *         named this, so the caller is free to try elsewhere
     */
    public static boolean travelTo(final Player player, final String name)
    {
        BeamDestination destination = BeamManager.getPlace(player.getUniqueId(), name);
        if (destination == null)
        {
            destination = BeamManager.getPublicDestination(name);
        }
        if (destination == null)
        {
            return false;
        }

        if (!BeamPermissions.has(player, BeamPermissions.USE))
        {
            player.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }
        final Location location = destination.toLocation();
        if (location == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "That destination's world is not currently loaded.");
            return true;
        }
        if (ConfigManager.isBeamUseCooldownEnabled() && BeamCooldown.isActive(player))
        {
            // Not ConfigManager.MessageStrings.playerUseCooldownRestricted -- its wording
            // names a stargate specifically, which would be wrong here.
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "You must wait longer before beaming again.");
            player.sendMessage(ConfigManager.MessageStrings.playerUseCooldownWaitTime.toString()
                + BeamCooldown.remainingSeconds(player));
            return true;
        }
        final double useCost = ConfigManager.getBeamEconomyUseCost();
        if ((useCost > 0) && !EconomySupport.canAfford(player, useCost))
        {
            // Not ConfigManager.MessageStrings.economyInsufficientFunds -- same reason: its
            // wording says "this gate."
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "Insufficient funds to beam.");
            return true;
        }

        // BeamAnimation.start already refuses (and messages) a player who is mid-beam, so
        // there is nothing left to do with its result either way -- this call has handled
        // the attempt fully regardless of which way it went.
        BeamAnimation.start(player, location, destination.getName(), () ->
        {
            if (useCost > 0)
            {
                EconomySupport.charge(player, useCost);
                player.sendMessage(ConfigManager.MessageStrings.economyCharged.toString()
                    + useCost + " " + EconomySupport.currencyName(useCost));
            }
            if (ConfigManager.isBeamUseCooldownEnabled())
            {
                BeamCooldown.start(player);
            }
        });
        return true;
    }
}
