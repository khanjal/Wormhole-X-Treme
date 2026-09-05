package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

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
 *
 * <p>{@code wormhole.beam.admin} bypasses both limits entirely -- neither checked nor
 * applied. That is a deliberate departure from gate travel, whose own cooldown and cost
 * apply uniformly regardless of permission with no such bypass; beaming adds one because
 * staff testing destinations or handling a support request are the common case this is
 * actually for, and it costs nothing new to wire up -- it reuses the same node that already
 * gates managing public destinations, rather than inventing a second one that would mean
 * the same thing.
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
        final Location stored = destination.toLocation();
        if (stored == null)
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "That destination's world is not currently loaded.");
            return true;
        }
        // Terrain can drift away from the coordinates a destination was set at -- dug out,
        // built up, whatever -- since nothing here re-checks them until someone actually
        // travels. Snapping to the nearest standable ground now, once, means the real
        // teleport and the arrival column both end up at the same corrected spot: this
        // location becomes BeamAnimation.Sequence's destination field, which both the
        // teleport call and the descend/fade columns already share, so there is nothing
        // further to keep in sync.
        final Location location = WorldUtils.findSafePlayerLocation(stored);

        final boolean bypassesLimits = BeamPermissions.has(player, BeamPermissions.ADMIN);

        if (!bypassesLimits && ConfigManager.isBeamUseCooldownEnabled() && BeamCooldown.isActive(player))
        {
            // Not ConfigManager.MessageStrings.playerUseCooldownRestricted -- its wording
            // names a stargate specifically, which would be wrong here.
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "You must wait longer before beaming again.");
            player.sendMessage(ConfigManager.MessageStrings.playerUseCooldownWaitTime.toString()
                + BeamCooldown.remainingSeconds(player));
            return true;
        }

        final double useCost = bypassesLimits ? 0.0 : resolveCost(destination);
        if ((useCost > 0) && !EconomySupport.canAfford(player, useCost))
        {
            // Not ConfigManager.MessageStrings.economyInsufficientFunds -- same reason: its
            // wording says "this gate."
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "Insufficient funds to beam -- costs " + useCost + " "
                + EconomySupport.currencyName(useCost) + ".");
            return true;
        }
        if (useCost > 0)
        {
            // Said up front, before the sequence starts, rather than only discovered once
            // charged: with per-destination cost now real, a silent auto-charge could be a
            // genuine surprise. A hard confirm-before-travelling step felt like more
            // friction than gate travel has ever needed for the same kind of cost, so this
            // is the middle ground -- seen, not gated on.
            player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + "This will cost " + useCost + " " + EconomySupport.currencyName(useCost) + ".");
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
            if (!bypassesLimits && ConfigManager.isBeamUseCooldownEnabled())
            {
                BeamCooldown.start(player);
            }
        });
        return true;
    }

    /**
     * What this destination actually costs -- its own override if it has one, otherwise
     * whatever the global default currently says, or {@code 0.0} if economy is not actually
     * active.
     *
     * <p>That last check matters: without it, a non-zero {@code BEAM_ECONOMY_USE_COST} or
     * per-destination override would still show "This will cost X..." and "Charged X..." to
     * the player even with economy turned off in config or Vault not installed --
     * {@link EconomySupport#canAfford} and {@link EconomySupport#charge} already fail open
     * in that situation (nothing is actually withdrawn), so the messages would be describing
     * a charge that never happened. Every other cost path in this plugin gates the same way
     * ({@code WormholeXTremePlayerListener}'s gate-use cost, {@code GateInteractionHandler}'s
     * build cost) -- this was missed when per-destination cost was added.
     *
     * @param destination the destination being travelled to
     * @return the cost to use, or {@code 0.0} if economy is disabled or unavailable
     */
    // Package-private, not private: BeamTravelTest exercises this directly, the same reason
    // BeamYamlManager.readDestination/writeDestination are package-private.
    static double resolveCost(final BeamDestination destination)
    {
        if (!(ConfigManager.isEconomyEnabled() && EconomySupport.isAvailable()))
        {
            return 0.0;
        }
        final Double override = destination.getCost();
        return override != null ? override : ConfigManager.getBeamEconomyUseCost();
    }
}
