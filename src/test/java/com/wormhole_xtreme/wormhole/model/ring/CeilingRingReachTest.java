package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A ceiling ring carries the people it armed for.
 *
 * <p>The volume that arms a ring and the volume that decides who rides it were computed two
 * different ways. {@link RingIndex} armed a ceiling ring over {@code maxCeilingDrop + 2}
 * layers, through {@link Ring#volumeDepth}; {@link RingCycle} looked for passengers over the
 * raw reach, four layers, with no ceiling adjustment at all.
 *
 * <p>So in any room deeper than the reach, somebody standing on the floor under a ceiling
 * ring was inside the arming volume and outside the carrying one. The rings fired, found
 * nobody, and carried zero -- and a cycle that carries nobody is owed no cooldown, by
 * deliberate decision in {@code RingTransit.lingerThenClose}, because there was no arrival to
 * guard against. So it re-armed instantly, with the same person still standing in the same
 * place, and went again. Reported from a room with a seven-block ceiling as rings that
 * "keep powering up and down".
 *
 * <h2>Why RingCycleTest could not have caught this</h2>
 *
 * <p>Its {@code FakeWorld.passengersIn} matches a volume to a ring by x and z and ignores y
 * entirely -- reasonable for the swap and restore behaviour those tests were written for, and
 * the reason a depth bug was invisible to thirty of them. The world below is the same idea
 * with the y honoured, which is the whole point of it.
 */
class CeilingRingReachTest
{
    /** The config default: block layers of passenger volume from the plane into the room. */
    private static final int REACH = 4;

    /** The config default: how far below its plane a ceiling ring looks for its floor. */
    private static final int MAX_DROP = 10;

    /** The reported room: a ceiling ring with the floor seven blocks below it. */
    private static final int CEILING_HEIGHT = 7;

    private static final int PLANE_Y = 64;

    /** A traveller that only has to say who it is. */
    private static final class Traveller implements RingPassenger
    {
        private final String name;

        Traveller(final String name)
        {
            this.name = name;
        }

        @Override
        public String getVehicleId()
        {
            return null;
        }

        @Override
        public boolean isPlayer()
        {
            return true;
        }

        @Override
        public String getUniqueId()
        {
            return name;
        }

        @Override
        public String getName()
        {
            return name;
        }
    }

    /**
     * A world that puts passengers at a block and finds them only if that block is asked for.
     *
     * <p>The y is the entire point. A fake that matches on the column alone reports a
     * passenger found however shallow the volume was, which is exactly how this bug survived.
     */
    private static final class ColumnAwareWorld implements RingCycle.Surroundings
    {
        private final Map<Long, List<RingPassenger>> standing = new HashMap<>();
        final List<String> deliveries = new ArrayList<>();

        void standAt(final int x, final int y, final int z, final RingPassenger passenger)
        {
            standing.computeIfAbsent(Long.valueOf(RingIndex.pack(x, y, z)),
                k -> new ArrayList<>()).add(passenger);
        }

        @Override
        public void showBlock(final int x, final int y, final int z, final Material material)
        {
            // Drawings are not what this test is about.
        }

        @Override
        public void showSlab(final int x, final int y, final int z, final Material material,
            final boolean top)
        {
            // Drawings are not what this test is about.
        }

        @Override
        public void reveal(final int x, final int y, final int z)
        {
            // Drawings are not what this test is about.
        }

        @Override
        public List<RingPassenger> passengersIn(final List<int[]> volume)
        {
            final List<RingPassenger> found = new ArrayList<>();
            for (final int[] block : volume)
            {
                final List<RingPassenger> here =
                    standing.get(Long.valueOf(RingIndex.pack(block[0], block[1], block[2])));
                if (here != null)
                {
                    found.addAll(here);
                }
            }
            return found;
        }

        @Override
        public RingBlockage survey(final Ring ring)
        {
            return null;
        }

        @Override
        public boolean mayTravel(final RingPassenger passenger, final Ring from, final Ring to)
        {
            return true;
        }

        @Override
        public void deliver(final RingPassenger passenger, final Ring destination)
        {
            deliveries.add(passenger.getName() + " -> " + destination.getAnchorY());
        }
    }

    /**
     * A pair with a ceiling ring at one end and a floor ring at the other.
     *
     * @return the pair, with the ceiling end already surveyed to its real drop
     */
    private static RingPair ceilingToFloorPair()
    {
        final Ring ceiling = new Ring(0, PLANE_Y, 0, RingPattern.ODD, RingOrientation.CEILING,
            Material.STONE_SLAB, Material.GLOWSTONE);
        ceiling.setDrop(CEILING_HEIGHT);
        final Ring floor = new Ring(100, 20, 100, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final RingPair pair = new RingPair("ceil0001", "world", ceiling, floor);
        pair.setOwner("owner-uuid");
        pair.setAccess(RingAccess.PUBLIC);
        return pair;
    }

    @AfterEach
    void clearIndex()
    {
        RingIndex.clear();
    }

    /**
     * Somebody on the floor under a ceiling ring actually travels.
     *
     * <p>This is the bug. Feet at seven blocks below the plane is outside a four-layer
     * volume and inside a twelve-layer one, so before the fix the cycle armed and carried
     * nobody.
     */
    @Test
    void somebodyStandingOnTheFloorUnderACeilingRingIsCarried()
    {
        final RingPair pair = ceilingToFloorPair();
        final ColumnAwareWorld world = new ColumnAwareWorld();
        world.standAt(0, PLANE_Y - CEILING_HEIGHT, 0, new Traveller("alice"));

        final int carried = new RingCycle(pair, world, REACH, MAX_DROP).flash();

        assertEquals(1, carried,
            "the rings armed for somebody standing on the floor and then carried nobody, which "
                + "is the cycle that fires over and over because a cycle carrying nobody is "
                + "owed no cooldown");
        assertTrue(world.deliveries.contains("alice -> 20"), "got: " + world.deliveries);
    }

    /**
     * Every layer the index arms is a layer the cycle will look in.
     *
     * <p>The drift guard, and it guards one specific joint: that what {@link RingIndex} arms
     * is covered by what {@link Ring#volumeDepth} describes. It does not go through
     * {@link RingCycle} -- the test above is what pins that, by carrying somebody -- so the
     * two are needed together and neither replaces the other.
     */
    @Test
    void theArmingVolumeAndTheCarryingVolumeCoverTheSameLayers()
    {
        final RingPair pair = ceilingToFloorPair();
        final Ring ceiling = pair.getEndA();
        RingIndex.add(pair, REACH);

        final List<Integer> armedOnly = new ArrayList<>();
        int armedLayers = 0;
        for (int down = 0; down < (MAX_DROP + 8); down++)
        {
            final int y = PLANE_Y - down;
            final boolean armed = RingIndex.volumeAt("world", ceiling.getAnchorX(), y,
                ceiling.getAnchorZ()) != null;
            if (!armed)
            {
                continue;
            }
            armedLayers++;
            final boolean carried = containsLayer(
                ceiling.triggerVolumeBlocks(ceiling.volumeDepth(REACH, MAX_DROP)), y);
            if (!carried)
            {
                armedOnly.add(Integer.valueOf(y));
            }
        }

        assertTrue(armedLayers > REACH,
            "this ceiling ring should arm deeper than the raw reach, or the test proves nothing "
                + "about the mismatch it exists for. Armed layers: " + armedLayers);
        assertTrue(armedOnly.isEmpty(),
            "these layers arm the rings but are not searched for passengers, so somebody "
                + "standing there sets the rings off and is left behind: " + armedOnly);
    }

    private static boolean containsLayer(final List<int[]> volume, final int y)
    {
        for (final int[] block : volume)
        {
            if (block[1] == y)
            {
                return true;
            }
        }
        return false;
    }

    /** A floor ring's volume is unchanged: the depth rule only bends for ceiling rings. */
    @Test
    void aFloorRingsVolumeIsStillJustTheReach()
    {
        final Ring floor = new Ring(0, PLANE_Y, 0, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);

        assertEquals(REACH, floor.volumeDepth(REACH, MAX_DROP),
            "a floor ring holds its passengers just above the plane; widening it to the "
                + "ceiling drop would have floor rings grabbing people off a balcony");
    }

    /** The index is actually populated, so the guard above is reading something real. */
    @Test
    void theIndexArmsTheCeilingRingAtAll()
    {
        final RingPair pair = ceilingToFloorPair();
        RingIndex.add(pair, REACH);

        assertNotNull(RingIndex.volumeAt("world", 0, PLANE_Y, 0),
            "the ring plane itself should arm the ring");
        assertNotNull(RingIndex.volumeAt("world", 0, PLANE_Y - CEILING_HEIGHT, 0),
            "the floor seven blocks down should arm it too, which is what makes the mismatch "
                + "reachable by a player");
    }
}
