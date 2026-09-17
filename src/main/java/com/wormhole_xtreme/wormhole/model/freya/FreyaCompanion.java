package com.wormhole_xtreme.wormhole.model.freya;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * The companion herself: spawning her, keeping her to one per player, and taking her away
 * again.
 *
 * <p>She is a real entity that the server owns, shown to exactly one client. There is no such
 * thing as a client-side entity from a server plugin -- a cat that existed only in one player's
 * game would mean building and sending entity packets, which across ten Minecraft versions in
 * one jar is the most fragile code this plugin could carry. So she is spawned normally and
 * hidden from everyone but her owner, the same {@code hideEntity} call
 * {@link com.wormhole_xtreme.wormhole.model.beam.BeamVisibility} uses to take a beam traveller
 * off other people's screens, inverted.
 *
 * <p>The following comes free with that decision. A tamed cat walks after its owner and
 * teleports to them when it falls behind, all of it vanilla mob AI, so there is no repeating
 * task here and nothing to keep in step with the player. What the flags in
 * {@link #settle(Cat, Player)} do is take away everything else a cat would otherwise do.
 *
 * <p>Two things a real entity does that no flag reaches, and they are the honest cost of the
 * choice above: creepers and phantoms avoid cats, so somebody may watch a creeper shy away from
 * what looks to them like empty air; and she counts toward the world's mob cap. One cat per
 * online player is a small price for not hand-rolling a packet protocol.
 */
public final class FreyaCompanion
{
    /** Her name, above her head, for the one person who can see her. */
    public static final String NAME = "Freya";

    /**
     * Live companions, by the id of the player they belong to.
     *
     * <p>Keyed by id rather than by {@link Player}, and emptied on quit, because this plugin
     * has already been bitten once by static maps keyed on Player objects that nothing ever
     * removed from -- see {@code PlayerStateIsReleasedOnQuitTest}.
     */
    private static final Map<UUID, Cat> LIVE = new LinkedHashMap<>();

    private FreyaCompanion() {}

    /**
     * Spawns a companion for a player, replacing any they already have.
     *
     * <p>Replacing rather than adding is the whole defence against this becoming a way to fill
     * a world with cats: however many times the command is typed, a player has one.
     *
     * @param owner
     *            who she belongs to
     * @return the cat, or null if she could not be spawned
     */
    public static Cat spawnFor(final Player owner)
    {
        if (owner == null)
        {
            return null;
        }
        removeFor(owner.getUniqueId());

        final Location at = owner.getLocation();
        if ((at == null) || (at.getWorld() == null))
        {
            return null;
        }

        try
        {
            final Cat cat = at.getWorld().spawn(at, Cat.class);
            settle(cat, owner);
            LIVE.put(owner.getUniqueId(), cat);
            hideFromOthers(cat, owner);
            return cat;
        }
        catch (final RuntimeException e)
        {
            // A world that refuses the spawn is not worth a stack trace in an operator's log
            // over a cosmetic command. The player is told nothing appeared; that is enough.
            return null;
        }
    }

    /**
     * Turns a freshly spawned cat into Freya, and takes away everything else a cat does.
     *
     * <p>{@code setAI} is deliberately left alone. Turning it off would stop her following,
     * which is the one behaviour worth keeping, and it would not help with the creeper quirk
     * anyway -- that is the creeper's AI reacting to a cat being nearby, not hers.
     *
     * @param cat
     *            the newly spawned cat
     * @param owner
     *            who she belongs to
     */
    private static void settle(final Cat cat, final Player owner)
    {
        cat.setCatType(blackVariant());
        cat.setAdult();
        cat.setTamed(true);
        cat.setOwner(owner);
        cat.setCustomName(NAME);
        cat.setCustomNameVisible(true);

        // Nothing may touch her, and she may touch nothing.
        cat.setInvulnerable(true);
        cat.setCollidable(false);
        // Sound is positional and reaches everybody, so an unsilenced cat that nobody can see
        // is a cat people can hear. This is the one flag whose absence would give her away.
        cat.setSilent(true);
        // She is spawned on join and removed on quit, so she must never be written to the
        // world's entity data. A server that is killed rather than stopped leaves nothing.
        cat.setPersistent(false);
        cat.setRemoveWhenFarAway(false);
    }

    /**
     * The all-black variant.
     *
     * <p>Its own method because it is the one line here that is sensitive to the Minecraft
     * version. {@code Cat.Type} is an enum on the older servers this plugin supports and a
     * registry-keyed type on the newer ones; the constant is a field access either way, which
     * is why this compiles across the range. If a version ever moves it, this is the only
     * place that has to change.
     *
     * @return the variant a black cat wears
     */
    private static Cat.Type blackVariant()
    {
        return Cat.Type.ALL_BLACK;
    }

    /**
     * Hides a companion from every player except her owner.
     *
     * @param cat
     *            the companion
     * @param owner
     *            who may see her
     */
    private static void hideFromOthers(final Cat cat, final Player owner)
    {
        final Plugin plugin = WormholeXTreme.getThisPlugin();
        if (plugin == null)
        {
            return;
        }
        for (final Player observer : plugin.getServer().getOnlinePlayers())
        {
            if (!observer.equals(owner))
            {
                hide(observer, cat, plugin);
            }
        }
    }

    /**
     * Hides every companion that is not theirs from one player.
     *
     * <p>Called when somebody joins. Hiding is per-observer and is applied at spawn time, so a
     * player who logs in after a cat was spawned has not been told to hide her and would
     * otherwise be the one person on the server who can see somebody else's.
     *
     * @param observer
     *            the player who has just arrived
     */
    public static void hideOthersFrom(final Player observer)
    {
        final Plugin plugin = WormholeXTreme.getThisPlugin();
        if ((observer == null) || (plugin == null))
        {
            return;
        }
        for (final Map.Entry<UUID, Cat> entry : LIVE.entrySet())
        {
            if (!entry.getKey().equals(observer.getUniqueId()))
            {
                hide(observer, entry.getValue(), plugin);
            }
        }
    }

    /**
     * One observer, one cat. Silent on failure, matching {@code BeamVisibility}: a client that
     * could not be told to hide one cat is not worth an exception on the main thread.
     *
     * @param observer
     *            who must not see her
     * @param cat
     *            the companion
     * @param plugin
     *            this plugin, which owns the hide
     */
    private static void hide(final Player observer, final Cat cat, final Plugin plugin)
    {
        try
        {
            observer.hideEntity(plugin, cat);
        }
        catch (final RuntimeException ignored)
        {
            // deliberately silent
        }
    }

    /**
     * Takes a player's companion away, if they have one.
     *
     * @param ownerId
     *            the player's id
     * @return true if there was one to remove
     */
    public static boolean removeFor(final UUID ownerId)
    {
        final Cat cat = (ownerId == null) ? null : LIVE.remove(ownerId);
        if (cat == null)
        {
            return false;
        }
        try
        {
            cat.remove();
        }
        catch (final RuntimeException ignored)
        {
            // Already gone, or her world is unloading. Either way she is not ours any more.
        }
        return true;
    }

    /** Takes every companion away. Called as the plugin stops, so none is left in a world. */
    public static void removeAll()
    {
        for (final UUID ownerId : new ArrayList<>(LIVE.keySet()))
        {
            removeFor(ownerId);
        }
    }

    /**
     * Whether an entity is somebody's companion.
     *
     * <p>Asked by the listeners that refuse damage and interaction. Identity against the
     * tracked cats rather than a name or type check, so a player's own black cat called Freya
     * is still an ordinary cat they can pet.
     *
     * @param entity
     *            the entity in question
     * @return true if this is a companion
     */
    public static boolean isCompanion(final Entity entity)
    {
        return (entity instanceof Cat) && LIVE.containsValue(entity);
    }

    /** @return how many companions are currently out. For tests. */
    public static int liveCount()
    {
        return LIVE.size();
    }

    /** Forgets every tracked companion without removing her. For tests. */
    public static void forgetAll()
    {
        LIVE.clear();
    }

    /**
     * Spawns companions for everyone already online who has one turned on.
     *
     * <p>Only reached on a reload, when players are on the server before the plugin starts.
     * An ordinary start finds nobody online and spawns nothing; each player's join does it.
     *
     * @return how many were spawned
     */
    public static int spawnForOnline()
    {
        final Plugin plugin = WormholeXTreme.getThisPlugin();
        if (plugin == null)
        {
            return 0;
        }
        final List<Player> waiting = new ArrayList<>();
        for (final Player player : plugin.getServer().getOnlinePlayers())
        {
            if (FreyaPreferences.isEnabled(player.getUniqueId()))
            {
                waiting.add(player);
            }
        }
        for (final Player player : waiting)
        {
            spawnFor(player);
        }
        return waiting.size();
    }
}
