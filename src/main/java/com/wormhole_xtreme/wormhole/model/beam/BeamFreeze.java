package com.wormhole_xtreme.wormhole.model.beam;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

/**
 * Two related but distinct states for a player mid-beam.
 *
 * <p><b>Active</b> covers the whole sequence, from the instant it starts to the instant it
 * finishes -- envelope included, while the traveller is still free to walk around. This is
 * what {@link BeamAnimation#start} checks to refuse a second beam stacking onto a first.
 *
 * <p><b>Frozen</b> is the narrower, later state: position-locked, starting only at the vanish
 * tick, once the traveller has stopped being free to move and the departure column has rooted
 * itself where they stood. Position only -- camera look (yaw/pitch) cannot be locked
 * server-side at all, that is purely client-rendered, so {@link BeamFreezeListener} lets it
 * through untouched and only ever reverts x/y/z.
 *
 * <p>Every active player is not yet frozen for however long the envelope lasts, which is the
 * entire reason these are two sets rather than one: collapsing them back into a single flag
 * would mean either the already-beaming guard stops working during the envelope (a second
 * beam could start while the first is still gathering), or the traveller is locked in place
 * from the first tick again, defeating the point of letting them move at all.
 */
public final class BeamFreeze
{
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> FROZEN = ConcurrentHashMap.newKeySet();

    private BeamFreeze() {}

    /** Marks a sequence as started. Call once, at tick zero. */
    public static void markActive(final Player player)
    {
        ACTIVE.add(player.getUniqueId());
    }

    /**
     * Whether a player already has a sequence running, whether or not they are frozen yet.
     *
     * @param player the player
     * @return true if a beam is already in progress for them
     */
    public static boolean isActive(final Player player)
    {
        return ACTIVE.contains(player.getUniqueId());
    }

    public static void freeze(final Player player)
    {
        FROZEN.add(player.getUniqueId());
    }

    public static boolean isFrozen(final Player player)
    {
        return FROZEN.contains(player.getUniqueId());
    }

    /** Clears both states. Call once, when a sequence ends -- successfully or otherwise. */
    public static void clear(final Player player)
    {
        FROZEN.remove(player.getUniqueId());
        ACTIVE.remove(player.getUniqueId());
    }
}
