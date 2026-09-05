package com.wormhole_xtreme.wormhole.model.beam;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Takes the traveller -- and whatever they are riding -- off every other player's screen for
 * the length of a beam, and puts them back.
 *
 * <p>This used to be {@link org.bukkit.potion.PotionEffectType#INVISIBILITY}, which was the
 * obvious tool and the wrong one. Invisibility hides a player's <em>body</em> and nothing
 * else: a held sword, worn armour, a shield, an elytra all keep rendering exactly where they
 * were. So a traveller who happened to be holding anything did not dissolve into the column
 * at all -- their equipment stayed standing in it, in the shape of a person, which is the one
 * read the whole sequence exists to sell.
 *
 * <p>{@link Player#hideEntity(Plugin, Entity)} is the API that actually does what
 * invisibility looked like it did: it stops the entity being sent to that client at all, so
 * equipment, nameplate and hitbox go with it. It has been on plain Spigot, not just Paper,
 * for the whole 1.20-1.21.10 range this plugin supports, and unlike the one-argument
 * {@code hidePlayer(Player)} it is not deprecated.
 *
 * <p>Hiding is per-observer, so this walks {@link Bukkit#getOnlinePlayers()} -- skipping the
 * traveller themselves, who still needs to see their own surroundings, the same
 * observer-relative property {@link BeamAnimation} already leans on. Two consequences worth
 * naming: a player who logs in mid-beam sees the traveller for the remaining tick or so,
 * which is not worth a join listener to close; and Bukkit reference-counts hiding per plugin,
 * so another plugin hiding the same entity keeps it hidden after this one reveals it, which
 * is the correct outcome rather than a leak.
 *
 * <p>Revealing is deliberately forgiving: {@code showEntity} for an entity that was never
 * hidden is a no-op, so {@link BeamAnimation.Sequence#recover} can call this after a failure
 * that happened before anything was hidden at all, without having to know which.
 */
public final class BeamVisibility
{
    private BeamVisibility() {}

    /**
     * Hides the traveller, and anything in {@code alsoHide}, from every other online player.
     *
     * @param traveller the beaming player, never hidden from themselves
     * @param alsoHide whatever they are riding, and its own passengers; may be null or empty
     */
    static void hide(final Player traveller, final List<Entity> alsoHide)
    {
        apply(traveller, alsoHide, true);
    }

    /**
     * Puts the traveller, and anything in {@code alsoHide}, back on every other online
     * player's screen.
     *
     * @param traveller the beaming player
     * @param alsoHide the same list that was passed to {@link #hide}; may be null or empty
     */
    static void show(final Player traveller, final List<Entity> alsoHide)
    {
        apply(traveller, alsoHide, false);
    }

    private static void apply(final Player traveller, final List<Entity> alsoHide, final boolean hide)
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
