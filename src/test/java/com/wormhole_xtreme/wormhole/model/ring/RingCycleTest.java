package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * The swap and the restore: the two things in this subsystem that fail quietly.
 *
 * <p>A wrong swap does not throw. It puts somebody back where they started, or carries
 * somebody who was not allowed, and a live server shows that only as a confused player. A
 * wrong restore does not throw either — it leaves a slab standing in a floor, or eats a
 * block somebody had built, and nobody notices until the damage has been there a while.
 * Both are why the cycle reaches the world through an interface rather than through Bukkit.
 */
public class RingCycleTest
{
    private static final int REACH = 3;
    private static final String OWNER = "owner-uuid";
    private static final String FRIEND = "friend-uuid";
    private static final String STRANGER = "stranger-uuid";

    /** A passenger that just says what it is. */
    private static final class Traveller implements RingPassenger
    {
        private final String name;
        private final String uuid;
        private final boolean player;

        Traveller(final String name, final String uuid, final boolean player)
        {
            this.name = name;
            this.uuid = uuid;
            this.player = player;
        }

        @Override
        public boolean isPlayer()
        {
            return player;
        }

        @Override
        public String getUniqueId()
        {
            return uuid;
        }

        @Override
        public String getName()
        {
            return name;
        }
    }

    /** A world of blocks in a map, and passengers pinned to a ring. */
    private static final class FakeWorld implements RingCycle.Surroundings
    {
        private final Map<Long, Material> blocks = new HashMap<Long, Material>();
        private final Map<String, List<RingPassenger>> standing = new HashMap<String, List<RingPassenger>>();
        final List<String> deliveries = new ArrayList<String>();

        void set(final int x, final int y, final int z, final Material material)
        {
            blocks.put(Long.valueOf(RingIndex.pack(x, y, z)), material);
        }

        void put(final Ring ring, final RingPassenger... passengers)
        {
            final List<RingPassenger> list = new ArrayList<RingPassenger>();
            for (final RingPassenger passenger : passengers)
            {
                list.add(passenger);
            }
            standing.put(key(ring), list);
        }

        private static String key(final Ring ring)
        {
            return ring.getAnchorX() + ":" + ring.getAnchorY() + ":" + ring.getAnchorZ();
        }

        int placedCount()
        {
            int count = 0;
            for (final Material material : blocks.values())
            {
                if (material != Material.AIR)
                {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Material materialAt(final int x, final int y, final int z)
        {
            final Material found = blocks.get(Long.valueOf(RingIndex.pack(x, y, z)));
            return found == null ? Material.AIR : found;
        }

        @Override
        public void setBlock(final int x, final int y, final int z, final Material material)
        {
            set(x, y, z, material);
        }

        @Override
        public void setSlab(final int x, final int y, final int z, final Material material,
            final boolean top)
        {
            set(x, y, z, material);
        }

        @Override
        public List<RingPassenger> passengersIn(final List<int[]> volume)
        {
            // The volume always belongs to one ring, so its first block identifies it well
            // enough for a test: match on whichever ring's anchor lies inside it.
            for (final Map.Entry<String, List<RingPassenger>> entry : standing.entrySet())
            {
                final String[] parts = entry.getKey().split(":");
                for (final int[] block : volume)
                {
                    if ((block[0] == Integer.parseInt(parts[0]))
                        && (block[2] == Integer.parseInt(parts[2])))
                    {
                        return new ArrayList<RingPassenger>(entry.getValue());
                    }
                }
            }
            return new ArrayList<RingPassenger>();
        }

        @Override
        public void deliver(final RingPassenger passenger, final Ring destination)
        {
            deliveries.add(passenger.getName() + " -> " + key(destination));
        }
    }

    private static RingPair pair()
    {
        final Ring a = new Ring(0, 64, 0, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final Ring b = new Ring(500, 64, 500, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final RingPair pair = new RingPair("cyc00001", "world", a, b);
        pair.setOwner(OWNER);
        pair.setAccess(RingAccess.PUBLIC);
        return pair;
    }

    @Test
    public void everyoneAtBothEndsTravelsInTheSameInstant()
    {
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.put(pair.getEndA(), new Traveller("alice", "a", true));
        world.put(pair.getEndB(), new Traveller("bob", "b", true));

        assertEquals(2, new RingCycle(pair, world, REACH).flash());
        assertTrue(world.deliveries.contains("alice -> 500:64:500"));
        assertTrue(world.deliveries.contains("bob -> 0:64:0"));
    }

    @Test
    public void arrivalsFromOneEndAreNeverPickedUpAsOccupantsOfTheOther()
    {
        // The single most important ordering here. Reading A, moving them, then reading B
        // would find alice standing in B and send her straight home again.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.put(pair.getEndA(), new Traveller("alice", "a", true));
        world.put(pair.getEndB(), new Traveller("bob", "b", true));

        new RingCycle(pair, world, REACH).flash();

        assertEquals(2, world.deliveries.size(), "each traveller moved exactly once");
        assertFalse(world.deliveries.contains("alice -> 0:64:0"), "alice was bounced back");
    }

    @Test
    public void aOneSidedCycleStillWorks()
    {
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.put(pair.getEndA(), new Traveller("alice", "a", true));

        assertEquals(1, new RingCycle(pair, world, REACH).flash());
    }

    @Test
    public void anEmptyCommittedCycleSendsNobodyAndDoesNotComplain()
    {
        assertEquals(0, new RingCycle(pair(), new FakeWorld(), REACH).flash());
    }

    @Test
    public void aPrivatePairLeavesAnUnpermittedPlayerStandingThere()
    {
        // Access governs being carried, not only arming. Standing in somebody's private ring
        // when they use it is not a free ride.
        final RingPair pair = pair();
        pair.setAccess(RingAccess.PRIVATE);
        pair.allow(FRIEND);

        final FakeWorld world = new FakeWorld();
        world.put(pair.getEndA(),
            new Traveller("owner", OWNER, true),
            new Traveller("friend", FRIEND, true),
            new Traveller("stranger", STRANGER, true));

        assertEquals(2, new RingCycle(pair, world, REACH).flash());
        assertTrue(world.deliveries.contains("owner -> 500:64:500"));
        assertTrue(world.deliveries.contains("friend -> 500:64:500"));
        assertFalse(world.deliveries.contains("stranger -> 500:64:500"));
    }

    @Test
    public void cargoTravelsThroughAPrivateRingBecauseItCannotHaveArmedIt()
    {
        final RingPair pair = pair();
        pair.setAccess(RingAccess.PRIVATE);

        final FakeWorld world = new FakeWorld();
        world.put(pair.getEndA(),
            new Traveller("a dropped pickaxe", null, false),
            new Traveller("a cow", null, false));

        assertEquals(2, new RingCycle(pair, world, REACH).flash());
    }

    @Test
    public void theCountdownLightsGoUpAndComeBackDownOnAbort()
    {
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        final RingCycle cycle = new RingCycle(pair, world, REACH);

        cycle.beginCountdown();
        assertEquals(RingPhase.COUNTDOWN, pair.getPhase());
        assertEquals(RingPattern.ODD.getPerimeter().size() * 2, world.placedCount());

        cycle.abort();
        assertEquals(RingPhase.IDLE, pair.getPhase());
        assertEquals(0, world.placedCount(), "nothing left behind");
    }

    @Test
    public void anEmptyPairAbortsAndAnOccupiedOneDoesNot()
    {
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        final RingCycle cycle = new RingCycle(pair, world, REACH);
        cycle.beginCountdown();
        assertTrue(cycle.shouldAbort());

        world.put(pair.getEndA(), new Traveller("alice", "a", true));
        assertFalse(cycle.shouldAbort());
    }

    @Test
    public void onceDeployingTheCycleWillNoLongerAbort()
    {
        // The committed phase. Everyone can walk away and it still runs to the end.
        final RingPair pair = pair();
        final RingCycle cycle = new RingCycle(pair, new FakeWorld(), REACH);
        cycle.beginCountdown();
        cycle.beginDeploy();

        assertEquals(RingPhase.DEPLOY, pair.getPhase());
        assertFalse(cycle.shouldAbort(), "an empty deploy is still committed");
    }

    @Test
    public void aFullRunPutsEveryBlockBackExactlyAsItWas()
    {
        // The thing that would otherwise leave slabs standing in somebody's floor forever.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        final RingCycle cycle = new RingCycle(pair, world, REACH);

        cycle.beginCountdown();
        cycle.beginDeploy();
        while (cycle.advanceFrame())
        {
            // run the deploy out
        }
        cycle.flash();
        cycle.beginHold();
        cycle.beginRetract();
        while (cycle.advanceFrame())
        {
            // run the retract out
        }
        cycle.finish(0L);

        assertEquals(RingPhase.IDLE, pair.getPhase());
        assertEquals(0, world.placedCount(), "not one slab left standing");
    }

    @Test
    public void theAnimationRefusesToWriteOverSomethingThatIsAlreadyThere()
    {
        // A ring is decoration and whatever is in the way might not be, so a blocked
        // position is skipped and the frame is drawn with a gap rather than a hole in
        // somebody's wall.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.set(-2, 64, 0, Material.DIAMOND_BLOCK);

        final RingCycle cycle = new RingCycle(pair, world, REACH);
        cycle.beginCountdown();

        assertEquals(Material.DIAMOND_BLOCK, world.materialAt(-2, 64, 0));
        cycle.abort();
        assertEquals(Material.DIAMOND_BLOCK, world.materialAt(-2, 64, 0), "and it survives the restore");
    }

    @Test
    public void aBlockSomebodyChangedWhileTheRingsWereUpIsLeftAlone()
    {
        // Restoring here would be the plugin vandalising the world on somebody's behalf.
        // The record is dropped instead: the block is no longer ours to think about.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        final RingCycle cycle = new RingCycle(pair, world, REACH);

        cycle.beginCountdown();
        world.set(-2, 64, 0, Material.DIAMOND_BLOCK);

        cycle.abort();
        assertEquals(Material.DIAMOND_BLOCK, world.materialAt(-2, 64, 0));
    }

    @Test
    public void bothStylesRunToTheEndAndCleanUpAfterThemselves()
    {
        for (final RingStyle style : RingStyle.values())
        {
            final RingPair pair = pair();
            pair.setStyle(style);
            final FakeWorld world = new FakeWorld();
            final RingCycle cycle = new RingCycle(pair, world, REACH);

            cycle.beginCountdown();
            cycle.beginDeploy();
            int frames = 1;
            while (cycle.advanceFrame())
            {
                frames++;
            }
            assertEquals(RingAnimator.deployFrames(style), frames, style + " ran the wrong length");

            cycle.flash();
            cycle.beginRetract();
            while (cycle.advanceFrame())
            {
                // run it out
            }
            cycle.finish(0L);
            assertEquals(0, world.placedCount(), style + " left something behind");
        }
    }

    @Test
    public void aRingBelowSeaLevelStillPutsItsBlocksBack()
    {
        // Regression: positions are packed into a long to be remembered, and y lives in the
        // low twelve bits where a plain mask loses its sign. A ring at a negative height was
        // restoring its blocks thousands of blocks away, which left the slabs standing in
        // the floor for good and wrote stray air into the sky.
        final Ring a = new Ring(0, -32, 0, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final Ring b = new Ring(500, -32, 500, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final RingPair deep = new RingPair("deep0001", "world", a, b);

        final FakeWorld world = new FakeWorld();
        final RingCycle cycle = new RingCycle(deep, world, REACH);

        cycle.beginCountdown();
        assertTrue(world.placedCount() > 0, "the lights went up");
        cycle.abort();
        assertEquals(0, world.placedCount(), "and came back down from the right blocks");
    }

    @Test
    public void finishingStartsTheCooldownThatKeepsArrivalsFromBouncing()
    {
        final RingPair pair = pair();
        final RingCycle cycle = new RingCycle(pair, new FakeWorld(), REACH);
        cycle.beginCountdown();
        cycle.finish(5000L);

        assertEquals(RingPhase.IDLE, pair.getPhase());
        assertFalse(pair.canFire(4999L), "still cooling down");
        assertTrue(pair.canFire(5000L));
    }
}
