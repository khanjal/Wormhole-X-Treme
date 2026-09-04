package com.wormhole_xtreme.wormhole.utils;

import java.util.List;

import org.bukkit.entity.Entity;

/**
 * Entity helpers that more than one part of the plugin needs.
 *
 * <p>{@link #collectPassengerPairs} lived on the vehicle listener, which is fine while only
 * gates ride anything. Rings need it too and are in another package, and widening a listener
 * class so a model could reach into it would be the wrong way round — walking a passenger
 * stack is a fact about entities rather than anything to do with listening for events.
 */
public final class EntityUtils
{
    private EntityUtils() {}

    /**
     * Collects a pre-order list of parent-to-child passenger pairs for an entity tree.
     *
     * <p>The lists come back parallel: {@code parents.get(i)} is what
     * {@code children.get(i)} was riding. Recursive because riding nests — a player on a
     * horse in a minecart is two seats, and putting the stack back means knowing both.
     *
     * @param root
     *            the entity at the bottom of the stack
     * @param parents
     *            collects what each passenger was riding
     * @param children
     *            collects the passengers, in the same order
     */
    public static void collectPassengerPairs(final Entity root, final List<Entity> parents,
        final List<Entity> children)
    {
        if ((root == null) || (parents == null) || (children == null))
        {
            return;
        }
        try
        {
            for (final Entity child : root.getPassengers())
            {
                parents.add(root);
                children.add(child);
                collectPassengerPairs(child, parents, children);
            }
        }
        // An entity that will not report its passengers contributes none, which leaves the
        // caller re-seating whatever it did manage to collect rather than nothing at all.
        catch (final RuntimeException ignored)
        {
            // deliberately silent
        }
    }
}
