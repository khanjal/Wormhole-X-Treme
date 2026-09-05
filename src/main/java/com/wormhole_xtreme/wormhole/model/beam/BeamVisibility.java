package com.wormhole_xtreme.wormhole.model.beam;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Takes the traveller -- and whatever they are riding -- off every other player's screen for
 * the length of a beam, and puts them back.
 *
 * <p>Two mechanisms, because no single one covers both audiences.
 *
 * <p>{@link Player#hideEntity(Plugin, Entity)} is what other players see, and it is the one
 * that actually matters. This was {@link PotionEffectType#INVISIBILITY} alone to begin with,
 * which is the obvious tool and does not do the job: invisibility hides a player's
 * <em>body</em> and nothing else, so a held sword, worn armour, a shield and an elytra all
 * keep rendering exactly where they were. A traveller carrying anything never dissolved into
 * the column at all -- their equipment stayed standing in it, in the shape of a person, which
 * is the one read the whole sequence exists to sell. {@code hideEntity} stops the entity being
 * sent to that client at all, so equipment, nameplate and hitbox go with it. It has been on
 * plain Spigot, not just Paper, for the whole 1.20-1.21.10 range this plugin supports, and
 * unlike the one-argument {@code hidePlayer(Player)} it is not deprecated.
 *
 * <p>Invisibility is still applied on top of that, and it is worth being precise about what
 * for, because it is not the hiding. Hiding is observer-relative and this deliberately skips
 * the traveller themselves -- and a client always renders its own player regardless, so a
 * traveller in third person watches themselves stand solid in the column while everyone else
 * sees an empty beam. Invisibility is the only thing that reaches their own camera. It is a
 * partial answer by nature: their own equipment keeps rendering for them either way, so what
 * an armoured traveller sees in third person is their kit without a body inside it. That is
 * the trade, taken knowingly, for the unarmoured case looking right.
 *
 * <p>The effect is applied only when the traveller does not already have it. Removing it
 * unconditionally at the end is what used to cancel an invisibility potion someone had drunk
 * themselves -- the sequence taking away something it never gave. {@link #appliedInvisibility}
 * is the whole guard: what this class did not apply, it does not remove.
 *
 * <p>One instance per sequence, since that flag is per-traveller state rather than something
 * static can hold. {@link BeamMount} has the same shape for the same reason.
 *
 * <p>Two consequences of hiding being per-observer are worth naming: a player who logs in
 * mid-beam sees the traveller for the remaining tick or so, which is not worth a join listener
 * to close; and Bukkit reference-counts hiding per plugin, so another plugin hiding the same
 * entity keeps it hidden after this one reveals it, which is the correct outcome rather than a
 * leak.
 *
 * <p>Revealing is deliberately forgiving: {@code showEntity} for an entity that was never
 * hidden is a no-op, and the guard above means an effect that was never applied is never
 * removed. So {@link BeamAnimation.Sequence#recover} can call {@link #show} after a failure
 * that happened before anything was hidden at all, without having to know which.
 */
final class BeamVisibility
{
    private final Player traveller;

    /**
     * Whether {@link #hide} actually applied invisibility, and so owes its removal. False when
     * the traveller already had the effect from their own potion, which this must not cancel.
     */
    private boolean appliedInvisibility;

    private BeamVisibility(final Player traveller)
    {
        this.traveller = traveller;
        this.appliedInvisibility = false;
    }

    /**
     * @param traveller the beaming player
     * @return visibility handling for one sequence
     */
    static BeamVisibility of(final Player traveller)
    {
        return new BeamVisibility(traveller);
    }

    /**
     * Hides the traveller, and anything in {@code alsoHide}, from every other online player,
     * and makes the traveller invisible to their own camera as well.
     *
     * @param alsoHide whatever they are riding, and its own passengers; may be null or empty
     * @param invisibilityTicks how long to hold the effect for -- a ceiling only, since
     *            {@link #show} removes it explicitly and is what the timing depends on
     */
    void hide(final List<Entity> alsoHide, final int invisibilityTicks)
    {
        apply(alsoHide, true);
        try
        {
            if (!traveller.hasPotionEffect(PotionEffectType.INVISIBILITY))
            {
                traveller.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    invisibilityTicks, 0, false, false));
                appliedInvisibility = true;
            }
        }
        catch (final RuntimeException ignored)
        {
            // A beam whose self-view effect would not apply is still a beam; hideEntity above
            // is the half that other players actually see.
        }
    }

    /**
     * Puts the traveller, and anything in {@code alsoHide}, back on every other online
     * player's screen, and gives the traveller back their own view of themselves.
     *
     * @param alsoHide the same list that was passed to {@link #hide}; may be null or empty
     */
    void show(final List<Entity> alsoHide)
    {
        apply(alsoHide, false);
        if (!appliedInvisibility)
        {
            return;
        }
        appliedInvisibility = false;
        try
        {
            traveller.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        catch (final RuntimeException ignored)
        {
            // deliberately silent
        }
    }

    private void apply(final List<Entity> alsoHide, final boolean hide)
    {
        final Plugin plugin = WormholeXTreme.getThisPlugin();
        if ((plugin == null) || (traveller == null))
        {
            return;
        }
        for (final Player observer : Bukkit.getOnlinePlayers())
        {
            if (traveller.equals(observer))
            {
                continue;
            }
            change(observer, traveller, plugin, hide);
            if (alsoHide == null)
            {
                continue;
            }
            for (final Entity entity : alsoHide)
            {
                change(observer, entity, plugin, hide);
            }
        }
    }

    /**
     * One observer, one subject. Swallowing the failure is the point: a beam that could not
     * hide one entity from one client is still a beam, and must not die mid-tick and leave
     * the traveller frozen.
     */
    private static void change(final Player observer, final Entity subject, final Plugin plugin,
        final boolean hide)
    {
        if (subject == null)
        {
            return;
        }
        try
        {
            if (hide)
            {
                observer.hideEntity(plugin, subject);
            }
            else
            {
                observer.showEntity(plugin, subject);
            }
        }
        catch (final RuntimeException ignored)
        {
            // deliberately silent
        }
    }
}
