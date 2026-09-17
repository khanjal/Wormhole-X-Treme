package com.wormhole_xtreme.wormhole.model.freya;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.PluginLog;

/**
 * The companion: a real, tamed cat that only her owner's client is shown.
 *
 * <p>Following is vanilla tamed-cat AI, and she travels by gate, ring, beam or mirror the way
 * any pet does, through {@link com.wormhole_xtreme.wormhole.PetEscort}. {@link #catchUp(Player)}
 * re-summons her only when that failed, and logs it, so a transport that drops pets shows up.
 * She is kept away while her owner sleeps or is hunted, so she gives no advantage a real cat would.
 */
public final class FreyaCompanion
{
    /** Her name, above her head, for the one person who can see her. */
    public static final String NAME = "Freya";

    /** Shown under the summon message. */
    public static final String YEARS = "2005 – 2025";

    /** Past this, re-summon her rather than leave it to vanilla, which teleports pets from 12. */
    private static final double LEFT_BEHIND_DISTANCE_SQUARED = 16.0 * 16.0;

    /** Live companions by owner id; emptied on quit so no Player is held. */
    private static final Map<UUID, Cat> LIVE = new LinkedHashMap<>();

    /** Owners in bed; she is away so no morning gift is ever given. */
    private static final Set<UUID> ASLEEP = new HashSet<>();

    /** Owners a creeper or phantom is hunting; she is away so it does not shy off. */
    private static final Set<UUID> HUNTED = new HashSet<>();

    /** How far to look for a creeper or phantom still hunting her owner. */
    private static final double HUNT_RADIUS = 32.0;

    /** Phantoms pick a target from up to 64 blocks above or below. */
    private static final double HUNT_HEIGHT = 64.0;

    /** Covers both halves of a double chest from its clicked half. */
    private static final double CHEST_REACH_SQUARED = 2.5 * 2.5;

    /** True only while she is being placed, so her own spawn event can be told apart. */
    private static boolean summoning;

    /** Whether her spawn event finished cancelled, which only something past HIGHEST can do. */
    private static boolean summonRefused;

    private FreyaCompanion() {}

    /**
     * Spawns a companion for a player, replacing any they already have.
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
        // Replace, never add: this is what keeps an open command from filling a world.
        removeFor(owner.getUniqueId());

        final Location at = owner.getLocation();
        if ((at == null) || (at.getWorld() == null))
        {
            return null;
        }

        try
        {
            final Cat cat;
            summoning = true;
            summonRefused = false;
            try
            {
                cat = at.getWorld().spawn(at, Cat.class);
            }
            finally
            {
                summoning = false;
            }
            if (cat == null)
            {
                return null;
            }
            // Read from the event, not isValid: after a cross-world trip she is not valid for a few ticks.
            if (summonRefused)
            {
                cat.remove();
                return null;
            }
            settle(cat, owner);
            LIVE.put(owner.getUniqueId(), cat);
            return cat;
        }
        catch (final RuntimeException e)
        {
            return null;
        }
    }

    /**
     * Turns a freshly spawned cat into Freya.
     *
     * @param cat
     *            the newly spawned cat
     * @param owner
     *            who she belongs to
     */
    private static void settle(final Cat cat, final Player owner)
    {
        cat.setCatType(Cat.Type.ALL_BLACK);
        cat.setAdult();
        cat.setTamed(true);
        cat.setOwner(owner);
        cat.setCustomName(NAME);
        cat.setCustomNameVisible(true);

        cat.setInvulnerable(true);
        cat.setCollidable(false);
        // Sound is positional; an unsilenced cat nobody else can see is one they can hear.
        cat.setSilent(true);
        cat.setPersistent(false);
        cat.setRemoveWhenFarAway(false);

        // Hidden by default covers players who join later, which a per-observer hide does not.
        cat.setVisibleByDefault(false);
        final Plugin plugin = WormholeXTreme.getThisPlugin();
        if (plugin != null)
        {
            owner.showEntity(plugin, cat);
        }
    }

    /**
     * Whether the spawn now happening is a companion being summoned.
     *
     * @return true only inside {@link #spawnFor(Player)}'s own spawn call
     */
    public static boolean isBeingSummoned()
    {
        return summoning;
    }

    /**
     * Records how her spawn event finished.
     *
     * @param cancelled
     *            whether it ended cancelled after every listener had run
     */
    public static void summonSettled(final boolean cancelled)
    {
        if (summoning)
        {
            summonRefused = cancelled;
        }
    }

    /**
     * Brings a player's companion back beside them if she has been left behind.
     *
     * @param owner
     *            the player, just arrived somewhere
     * @return true if she was re-summoned
     */
    public static boolean catchUp(final Player owner)
    {
        if ((owner == null) || !FreyaPreferences.isEnabled(owner.getUniqueId())
            || isAway(owner.getUniqueId()))
        {
            return false;
        }
        final Cat before = LIVE.get(owner.getUniqueId());
        if (PluginLog.isLoggable(Level.FINE))
        {
            PluginLog.log(Level.FINE, "Companion check for " + owner.getName() + ": " + describe(before, owner.getLocation()));
        }
        if (!isLeftBehind(before, owner.getLocation()))
        {
            return false;
        }
        if ((before != null) && PluginLog.isLoggable(Level.FINE))
        {
            PluginLog.log(Level.FINE, "Re-summoned " + owner.getName() + "'s companion; she did not travel with them.");
        }
        return spawnFor(owner) != null;
    }

    /**
     * Where a companion stands relative to her owner, for the log.
     *
     * @param cat
     *            the tracked companion, or null
     * @param owner
     *            where her owner is
     * @return a one-line summary
     */
    private static String describe(final Cat cat, final Location owner)
    {
        if (cat == null)
        {
            return "none out";
        }
        final Location at = cat.getLocation();
        final String world = ((at == null) || (at.getWorld() == null)) ? "?" : at.getWorld().getName();
        final boolean together = (at != null) && (owner != null) && Objects.equals(at.getWorld(), owner.getWorld());
        return "in " + world + (together ? " " + Math.round(Math.sqrt(at.distanceSquared(owner))) + " blocks away" : ", another world")
            + ", valid " + cat.isValid() + ", dead " + cat.isDead();
    }

    /**
     * Whether a companion is gone, in another world, or too far for vanilla following.
     *
     * @param cat
     *            the tracked companion, or null
     * @param owner
     *            where her owner is
     * @return true if she needs re-summoning
     */
    static boolean isLeftBehind(final Cat cat, final Location owner)
    {
        // A non-persistent cat is discarded when her chunk unloads. Not isValid: one just placed
        // in a chunk that is not yet tracking entities is invalid, but she is on her way.
        if ((cat == null) || cat.isDead())
        {
            return true;
        }
        final Location at = cat.getLocation();
        if ((owner == null) || (at == null))
        {
            return false;
        }
        return !Objects.equals(at.getWorld(), owner.getWorld())
            || (at.distanceSquared(owner) > LEFT_BEHIND_DISTANCE_SQUARED);
    }

    /**
     * Whether she is staying away from her owner for now.
     *
     * @param ownerId
     *            the owner
     * @return true while they sleep or are hunted
     */
    public static boolean isAway(final UUID ownerId)
    {
        return ASLEEP.contains(ownerId) || HUNTED.contains(ownerId);
    }

    /**
     * Sends her away while her owner sleeps, so vanilla has no cat to give a morning gift.
     *
     * @param ownerId
     *            the owner getting into bed
     */
    public static void ownerSleeps(final UUID ownerId)
    {
        if (FreyaPreferences.isEnabled(ownerId))
        {
            ASLEEP.add(ownerId);
            removeFor(ownerId);
        }
    }

    /**
     * Lets her come back once her owner is up; the caller brings her with {@link #catchUp}.
     *
     * @param ownerId
     *            the owner leaving bed
     */
    public static void ownerWakes(final UUID ownerId)
    {
        ASLEEP.remove(ownerId);
    }

    /**
     * Sends her away from an owner something hostile has started hunting.
     *
     * @param ownerId
     *            the hunted owner
     * @return true if she has only now left, so the caller should start watching for it to end
     */
    public static boolean ownerHunted(final UUID ownerId)
    {
        if (!FreyaPreferences.isEnabled(ownerId))
        {
            return false;
        }
        removeFor(ownerId);
        return HUNTED.add(ownerId);
    }

    /**
     * Ends a hunt once nothing nearby is still after her owner.
     *
     * @param owner
     *            the owner
     * @return true if the hunt is over and she may come back
     */
    public static boolean huntOver(final Player owner)
    {
        if (owner.isOnline() && isHunting(owner.getNearbyEntities(HUNT_RADIUS, HUNT_HEIGHT, HUNT_RADIUS), owner))
        {
            return false;
        }
        HUNTED.remove(owner.getUniqueId());
        return true;
    }

    /**
     * Whether any of these entities is a creeper or phantom hunting the player.
     *
     * @param nearby
     *            entities around the player
     * @param owner
     *            the player
     * @return true if one of them has the player as its target
     */
    static boolean isHunting(final Collection<Entity> nearby, final Player owner)
    {
        for (final Entity entity : nearby)
        {
            if (scaredOfCats(entity) && owner.equals(((Mob) entity).getTarget()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The mobs vanilla makes keep away from cats.
     *
     * @param entity
     *            the entity
     * @return true for a creeper or phantom
     */
    public static boolean scaredOfCats(final Entity entity)
    {
        return (entity instanceof Creeper) || (entity instanceof Phantom);
    }

    /**
     * Stands up any companion sitting on or beside a chest, since a sitting cat keeps it shut
     * and nobody else can see why.
     *
     * @param chest
     *            the centre of the chest being opened
     * @return how many were stood up
     */
    public static int standUpNear(final Location chest)
    {
        int stood = 0;
        for (final Cat cat : LIVE.values())
        {
            final Location at = cat.getLocation();
            if ((at != null) && Objects.equals(at.getWorld(), chest.getWorld())
                && (at.distanceSquared(chest) <= CHEST_REACH_SQUARED))
            {
                cat.setSitting(false);
                stood++;
            }
        }
        return stood;
    }

    /**
     * Forgets everything about a player who has left: her, and why she was away.
     *
     * @param ownerId
     *            the player's id
     */
    public static void forgetOwner(final UUID ownerId)
    {
        removeFor(ownerId);
        ASLEEP.remove(ownerId);
        HUNTED.remove(ownerId);
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
            // already gone, or her world is unloading
        }
        return true;
    }

    /** Takes every companion away, as the plugin stops. */
    public static void removeAll()
    {
        for (final UUID ownerId : new ArrayList<>(LIVE.keySet()))
        {
            removeFor(ownerId);
        }
    }

    /**
     * Whether an entity is somebody's companion, by identity, so a player's own cat named
     * Freya is still an ordinary cat.
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
        ASLEEP.clear();
        HUNTED.clear();
    }

    /** Spawns companions for players already online, which only happens on a reload. */
    public static void spawnForOnline()
    {
        final Plugin plugin = WormholeXTreme.getThisPlugin();
        if (plugin == null)
        {
            return;
        }
        for (final Player player : new ArrayList<>(plugin.getServer().getOnlinePlayers()))
        {
            catchUp(player);
        }
    }
}
