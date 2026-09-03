package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Resolves a name to a beam destination and sends the player there -- shared by
 * {@code /wormhole beam to} and {@code /wormhole go}, so a player can reach the same place
 * either way rather than the two commands quietly disagreeing about what a name means.
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
        // BeamAnimation.start already refuses (and messages) a player who is mid-beam, so
        // there is nothing left to do with its result either way -- this call has handled
        // the attempt fully regardless of which way it went.
        BeamAnimation.start(player, location, destination.getName());
        return true;
    }
}
