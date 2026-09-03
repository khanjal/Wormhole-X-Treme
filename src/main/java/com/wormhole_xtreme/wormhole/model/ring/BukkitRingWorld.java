/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   The ring subsystem's one point of contact with a real world.
 */
package com.wormhole_xtreme.wormhole.model.ring;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Everything the ring subsystem does to a real world, in one place.
 *
 * <p>This class exists to be boring. All the decisions — what order things happen in, who
 * travels, what is drawn when — live in {@link RingCycle} and {@link RingAnimator}, where
 * they can be checked without a server. What is left here is drawing blocks, finding
 * entities and moving them, with no logic worth arguing about.
 *
 * <p><b>Nothing here changes the world.</b> Rings and their lights are sent to clients as
 * block changes and the server's own blocks are never touched, exactly as a gate draws its
 * portal. A ring is scenery that exists for five seconds; making it real would mean a server
 * stopped mid-cycle keeping it for good, block loggers recording a floor being replaced on
 * every trip, and players able to mine free glowstone out of their own floor while it stood
 * there. It also means nobody can stand on a rising ring or be shoved by one, which is the
 * right behaviour and one less hazard to design around.
 *
 * <p>The cost of an illusion is that it only exists for those it was sent to, and that
 * anything handing a client a fresh copy of the chunk erases it. Both are the same trade
 * gates already make.
 */
public class BukkitRingWorld implements RingCycle.Surroundings
{
    /** How far from a ring a player has to be to be sent its drawing. */
    private static final int VIEW_DISTANCE = 96;

    /** Squared, so the range check needs no square root. */
    private static final double VIEW_DISTANCE_SQUARED = (double) VIEW_DISTANCE * VIEW_DISTANCE;

    /** How long a computed audience is reused before being worked out again. */
    private static final long AUDIENCE_TTL_MILLIS = 50L;

    /** The world this operates in. Both ends of a pair are always in it. */
    private final World world;

    /** How deep a trigger volume runs, needed to work out where arrivals land. */
    private final int reach;

    /** The pair being drawn, whose two ends decide who can see it. */
    private final RingPair pair;

    /** Who is currently being drawn to. */
    private List<Player> audience = new ArrayList<Player>();

    /** When that list was last worked out. */
    private long audienceComputedAt = 0L;

    /**
     * Instantiates a world adapter.
     *
     * @param world
     *            the world to operate in
     * @param pair
     *            the pair being drawn
     * @param reach
     *            how deep each trigger volume runs
     */
    public BukkitRingWorld(final World world, final RingPair pair, final int reach)
    {
        this.world = world;
        this.pair = pair;
        this.reach = reach;
    }

    /**
     * Who should be sent this pair's drawing.
     *
     * <p>Recomputed at most once a tick rather than per block, because a frame draws dozens
     * of them and the answer cannot change in between. Recomputed at all, rather than fixed
     * when the cycle starts, so that somebody who has just been carried to the far end sees
     * the rings that brought them there.
     *
     * @return the players in range of either end
     */
    private List<Player> audience()
    {
        final long now = System.currentTimeMillis();
        if ((now - audienceComputedAt) < AUDIENCE_TTL_MILLIS)
        {
            return audience;
        }
        final List<Player> found = new ArrayList<Player>();
        for (final Player player : world.getPlayers())
        {
            if (inRangeOf(player, pair.getEndA()) || inRangeOf(player, pair.getEndB()))
            {
                found.add(player);
            }
        }
        audience = found;
        audienceComputedAt = now;
        return audience;
    }

    /**
     * Whether a player is close enough to one end to be shown it.
     *
     * @param player
     *            the player
     * @param ring
     *            the end
     * @return true if they are in range
     */
    private static boolean inRangeOf(final Player player, final Ring ring)
    {
        final Location at = player.getLocation();
        final double dx = at.getX() - ring.getAnchorX();
        final double dy = at.getY() - ring.getAnchorY();
        final double dz = at.getZ() - ring.getAnchorZ();
        return ((dx * dx) + (dy * dy) + (dz * dz)) <= VIEW_DISTANCE_SQUARED;
    }

    /* (non-Javadoc)
     * @see RingCycle.Surroundings#showBlock(int, int, int, org.bukkit.Material)
     */
    @Override
    public void showBlock(final int x, final int y, final int z, final Material material)
    {
        send(x, y, z, com.wormhole_xtreme.wormhole.utils.MaterialUtils.drawnAs(material));
    }

    /* (non-Javadoc)
     * @see RingCycle.Surroundings#showSlab(int, int, int, org.bukkit.Material, boolean)
     */
    @Override
    public void showSlab(final int x, final int y, final int z, final Material material,
        final boolean top)
    {
        final BlockData data = com.wormhole_xtreme.wormhole.utils.MaterialUtils.drawnAs(material);
        if (data instanceof Slab)
        {
            ((Slab) data).setType(top ? Slab.Type.TOP : Slab.Type.BOTTOM);
        }
        // A ring material is validated as a slab wherever one is set, so the cast above
        // should always hold. Showing it as a plain block if it somehow does not still
        // animates — just in whole blocks — which beats throwing mid-cycle.
        send(x, y, z, data);
    }

    /* (non-Javadoc)
     * @see RingCycle.Surroundings#reveal(int, int, int)
     */
    @Override
    public void reveal(final int x, final int y, final int z)
    {
        // Whatever is really there, which is whatever was always there: this class has not
        // changed a block. If a player altered it while the rings were up, they see their
        // own change, which is exactly right.
        send(x, y, z, world.getBlockAt(x, y, z).getBlockData());
    }

    /**
     * Sends one block change to everyone watching.
     *
     * @param x
     *            block x
     * @param y
     *            block y
     * @param z
     *            block z
     * @param data
     *            what to show them
     */
    private void send(final int x, final int y, final int z, final BlockData data)
    {
        final Location at = new Location(world, x, y, z);
        for (final Player player : audience())
        {
            player.sendBlockChange(at, data);
        }
    }

    /* (non-Javadoc)
     * @see RingCycle.Surroundings#passengersIn(java.util.List)
     */
    @Override
    public List<RingPassenger> passengersIn(final List<int[]> blocks)
    {
        final List<RingPassenger> out = new ArrayList<RingPassenger>();
        if (blocks.isEmpty())
        {
            return out;
        }
        // One region query rather than one per block: a ring interior is a handful of
        // columns and asking the world about each of them separately would be the same
        // answer several dozen times over.
        for (final Entity entity : world.getNearbyEntities(boundsOf(blocks)))
        {
            if (standsInAny(entity, blocks))
            {
                out.add(new BukkitRingPassenger(entity));
            }
        }
        return out;
    }

    /**
     * A box enclosing every block of a volume.
     *
     * @param blocks
     *            the volume, each entry {@code {x, y, z}}
     * @return a box containing all of them
     */
    private static BoundingBox boundsOf(final List<int[]> blocks)
    {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (final int[] block : blocks)
        {
            minX = Math.min(minX, block[0]);
            minY = Math.min(minY, block[1]);
            minZ = Math.min(minZ, block[2]);
            maxX = Math.max(maxX, block[0]);
            maxY = Math.max(maxY, block[1]);
            maxZ = Math.max(maxZ, block[2]);
        }
        return new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    /**
     * Whether an entity is standing in one of these blocks.
     *
     * <p>The box above is the ring's bounding cube, which includes the cut corners the ring
     * itself does not occupy. Checking the actual blocks afterwards is what stops somebody
     * standing just outside a circle from being counted as inside it.
     *
     * @param entity
     *            the entity to place
     * @param blocks
     *            the volume, each entry {@code {x, y, z}}
     * @return true if it is in the volume
     */
    private static boolean standsInAny(final Entity entity, final List<int[]> blocks)
    {
        final Location at = entity.getLocation();
        final int x = at.getBlockX();
        final int y = at.getBlockY();
        final int z = at.getBlockZ();
        for (final int[] block : blocks)
        {
            if ((block[0] == x) && (block[1] == y) && (block[2] == z))
            {
                return true;
            }
        }
        return false;
    }

    /* (non-Javadoc)
     * @see RingCycle.Surroundings#mayTravel(RingPassenger, Ring, Ring)
     */
    @Override
    public boolean mayTravel(final RingPassenger passenger, final Ring from, final Ring to)
    {
        if (!(passenger instanceof BukkitRingPassenger))
        {
            return true;
        }
        final org.bukkit.entity.Entity entity = ((BukkitRingPassenger) passenger).getEntity();
        if (!(entity instanceof Player))
        {
            // Cargo raises nothing, so cancelling stops a person and not the world around
            // them. An item drifting onto a pad is not a decision anybody wants to make.
            return true;
        }
        final com.wormhole_xtreme.wormhole.events.RingTravelEvent event =
            new com.wormhole_xtreme.wormhole.events.RingTravelEvent(pair, (Player) entity, from, to);
        try
        {
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
        }
        // No server to dispatch through, which happens in tests and during shutdown. A trip
        // nobody could object to is better than a trip that throws.
        catch (final RuntimeException ignored)
        {
            return true;
        }
        return !event.isCancelled();
    }

    /* (non-Javadoc)
     * @see RingCycle.Surroundings#deliver(RingPassenger, Ring)
     */
    @Override
    public void deliver(final RingPassenger passenger, final Ring destination)
    {
        if (!(passenger instanceof BukkitRingPassenger))
        {
            return;
        }
        final Entity entity = ((BukkitRingPassenger) passenger).getEntity();
        final Location arrival = arrivalPoint(destination);
        if (arrival == null)
        {
            // Should not happen: the far end is checked before the cycle starts and again
            // before anybody moves. Leaving them where they are is still the right answer if
            // it somehow does.
            return;
        }
        // Keep the way they were facing. Being spun round on arrival is disorienting, and
        // unlike a gate a ring has no direction of its own to face them in.
        arrival.setYaw(entity.getLocation().getYaw());
        arrival.setPitch(entity.getLocation().getPitch());
        if (entity instanceof Player)
        {
            ((Player) entity).setNoDamageTicks(5);
        }
        // Teleporting an entity throws off whatever is riding it, so the stack is noted
        // first and put back once everything has landed.
        final List<Entity> parents = new ArrayList<Entity>();
        final List<Entity> children = new ArrayList<Entity>();
        com.wormhole_xtreme.wormhole.utils.EntityUtils.collectPassengerPairs(entity, parents, children);

        entity.teleport(arrival);
        for (final Entity child : children)
        {
            child.teleport(arrival);
        }
        reseat(parents, children);

        // After the teleport, so it lands on a client that is already looking at the far end.
        if (entity instanceof Player)
        {
            RingMessages.arrived((Player) entity, destination.getName());
        }
        for (final Entity child : children)
        {
            if (child instanceof Player)
            {
                RingMessages.arrived((Player) child, destination.getName());
            }
        }
    }

    /**
     * Puts a passenger stack back together, a tick after it landed.
     *
     * <p>Not on the same tick. A seat is refused while the two are still apart in the
     * server's eyes, and they have only just been moved, so this waits for the positions to
     * settle before asking.
     *
     * @param parents
     *            what each passenger was riding
     * @param children
     *            the passengers, in the same order
     */
    private static void reseat(final List<Entity> parents, final List<Entity> children)
    {
        if (children.isEmpty() || (WormholeXTreme.getScheduler() == null))
        {
            return;
        }
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new Runnable()
            {
                @Override
                public void run()
                {
                    for (int i = 0; i < children.size(); i++)
                    {
                        final Entity parent = parents.get(i);
                        final Entity child = children.get(i);
                        try
                        {
                            if (parent.isValid() && child.isValid()
                                && !parent.getPassengers().contains(child))
                            {
                                // Close any gap first: a seat is refused when the two are
                                // far enough apart, which they may still be after a teleport.
                                child.teleport(parent.getLocation());
                                parent.addPassenger(child);
                            }
                        }
                        // Cosmetic settling. Somebody standing beside their camel is a worse
                        // outcome than a stack trace, but not a reason to abandon the rest.
                        catch (final RuntimeException e)
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.FINE,
                                false, "Could not re-seat a ring passenger: " + e.getMessage());
                        }
                    }
                }
            }, 1L);
    }

    /* (non-Javadoc)
     * @see RingCycle.Surroundings#survey(Ring)
     */
    @Override
    public RingBlockage survey(final Ring ring)
    {
        if (ring.getOrientation() == RingOrientation.CEILING)
        {
            final int found = floorBelow(ring);
            if (found < 0)
            {
                return RingBlockage.CEILING_TOO_HIGH;
            }
            if (found < Ring.MIN_CEILING_DROP)
            {
                return RingBlockage.CEILING_TOO_LOW;
            }
            // Recorded on the ring so the stack forms on this floor rather than under the
            // ceiling. Everything downstream measures from it.
            ring.setDrop(found);
        }
        final int y = arrivalHeight(ring);
        // Every column, not just the middle. Somewhere to stand is not the same as somewhere
        // fit to arrive: one block dropped into a ring would still leave twenty free columns,
        // and delivering people to whichever corner happened to be empty is not what a
        // transport ring should do. One block in it stops the whole thing.
        for (final int[] block : ring.interiorBlocks())
        {
            if ((y - 1 < world.getMinHeight()) || ((y + 1) >= world.getMaxHeight()))
            {
                return RingBlockage.NO_GROUND;
            }
            if (!world.getBlockAt(block[0], y, block[2]).isPassable()
                || !world.getBlockAt(block[0], y + 1, block[2]).isPassable())
            {
                return RingBlockage.OBSTRUCTED;
            }
            // Ground directly under every column, so nobody arrives over a hole somebody dug.
            // Directly, not somewhere below: a gap with a floor three blocks further down is
            // still a gap to fall through, and a ring you drop out of is not a ring that
            // works. Water and lava are passable and count as no ground too, which is the
            // right answer — landing in either is not arriving.
            if (world.getBlockAt(block[0], y - 1, block[2]).isPassable())
            {
                return RingBlockage.NO_GROUND;
            }
        }
        return null;
    }

    /**
     * How far below a ceiling ring's plane the floor is.
     *
     * <p>Searched down the middle of the ring, out to the configured limit. Beyond that a
     * ceiling ring is over a shaft rather than a room, and rings that fall out of sight are
     * not a transport.
     *
     * @param ring
     *            the ceiling ring
     * @return the drop in blocks, or -1 if there is no floor within reach
     */
    private int floorBelow(final Ring ring)
    {
        final int limit = ConfigManager.getRingMaxCeilingDrop();
        for (int down = 1; down <= (limit + 1); down++)
        {
            final int y = ring.getAnchorY() - down;
            if (y < world.getMinHeight())
            {
                return -1;
            }
            if (!world.getBlockAt(ring.getAnchorX(), y, ring.getAnchorZ()).isPassable())
            {
                // Feet go on top of it, so the drop is to the layer above the solid block.
                return down - 1;
            }
        }
        return -1;
    }

    /**
     * The layer travellers arrive in.
     *
     * <p>The stack base for both orientations: the plane for a floor ring, and the floor a
     * ceiling ring's rings have dropped to. People arrive standing inside the stack rather
     * than under it.
     *
     * @param ring
     *            the ring
     * @return the block layer a traveller's feet land in
     */
    private int arrivalHeight(final Ring ring)
    {
        return ring.stackBase();
    }

    /**
     * Where travellers land at a ring.
     *
     * <p>Always the middle, with nothing searched for. A ring only fires at all when its whole
     * interior is clear and floored, so the centre is as good as anywhere — and is where a
     * transport ring ought to put people rather than whichever corner happened to be free.
     *
     * @param ring
     *            the ring being arrived at
     * @return where to put a traveller
     */
    public Location arrivalPoint(final Ring ring)
    {
        return centreOf(ring.getAnchorX(), arrivalHeight(ring), ring.getAnchorZ());
    }

    /**
     * The middle of a block, facing however the traveller already was.
     *
     * @param x
     *            block x
     * @param y
     *            block y
     * @param z
     *            block z
     * @return a location in the centre of that block
     */
    private Location centreOf(final int x, final int y, final int z)
    {
        // Half a block along x and z so they stand in the middle rather than on a corner. An
        // even ring has no centre block, so this lands in one of the middle four, which is as
        // central as a ring with no centre gets.
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }
}
