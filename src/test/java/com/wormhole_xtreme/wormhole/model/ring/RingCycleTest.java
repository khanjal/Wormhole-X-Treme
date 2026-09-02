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
 * drawing left behind does not throw either — a ring simply stays hanging in somebody's
 * room until they relog. Both are why the cycle reaches the world through an interface
 * rather than through Bukkit.
 *
 * <p>The fake world below keeps the real blocks and the drawn ones apart, which is the whole
 * point of the change these tests cover: rings are shown to clients and never written, so
 * every test here can assert that the world came out of a cycle exactly as it went in.
 */
public class RingCycleTest
{
    private static final int REACH = 3;
    private static final String OWNER = "owner-uuid";
    private static final String FRIEND = "friend-uuid";
    private static final String STRANGER = "stranger-uuid";

    /** A passenger that just says what it is, and what it is riding. */
    private static final class Traveller implements RingPassenger
    {
        private final String name;
        private final String uuid;
        private final boolean player;
        private String riding;

        Traveller(final String name, final String uuid, final boolean player)
        {
            this.name = name;
            this.uuid = uuid;
            this.player = player;
        }

        /** Seats this traveller on another, and returns it for chaining. */
        Traveller riding(final Traveller mount)
        {
            this.riding = mount.uuid;
            return this;
        }

        @Override
        public String getVehicleId()
        {
            return riding;
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

    /** Real blocks and drawn blocks, kept deliberately apart. */
    private static final class FakeWorld implements RingCycle.Surroundings
    {
        /** What the server actually has. Nothing in the cycle may ever change this. */
        private final Map<Long, Material> real = new HashMap<Long, Material>();

        /** What clients are currently being shown over the top of it. */
        private final Map<Long, Material> drawn = new HashMap<Long, Material>();

        private final Map<String, List<RingPassenger>> standing = new HashMap<String, List<RingPassenger>>();
        final List<String> deliveries = new ArrayList<String>();

        void setReal(final int x, final int y, final int z, final Material material)
        {
            real.put(Long.valueOf(RingIndex.pack(x, y, z)), material);
        }

        Material realAt(final int x, final int y, final int z)
        {
            final Material found = real.get(Long.valueOf(RingIndex.pack(x, y, z)));
            return found == null ? Material.AIR : found;
        }

        /** What a client sees: the drawing if there is one, otherwise the real block. */
        Material seenAt(final int x, final int y, final int z)
        {
            final Material shown = drawn.get(Long.valueOf(RingIndex.pack(x, y, z)));
            return shown == null ? realAt(x, y, z) : shown;
        }

        int drawnCount()
        {
            return drawn.size();
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

        @Override
        public void showBlock(final int x, final int y, final int z, final Material material)
        {
            drawn.put(Long.valueOf(RingIndex.pack(x, y, z)), material);
        }

        @Override
        public void showSlab(final int x, final int y, final int z, final Material material,
            final boolean top)
        {
            showBlock(x, y, z, material);
        }

        @Override
        public void reveal(final int x, final int y, final int z)
        {
            drawn.remove(Long.valueOf(RingIndex.pack(x, y, z)));
        }

        @Override
        public List<RingPassenger> passengersIn(final List<int[]> volume)
        {
            // The volume always belongs to one ring, so matching on whichever ring's anchor
            // column lies inside it identifies it well enough for a test.
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

        /** Names of passengers an outside plugin refuses to carry. */
        final List<String> refuse = new ArrayList<String>();

        /** Rings that have been built in or dug out since, by anchor key. */
        final Map<String, RingBlockage> blocked = new HashMap<String, RingBlockage>();

        @Override
        public RingBlockage survey(final Ring ring)
        {
            return blocked.get(key(ring));
        }

        @Override
        public boolean mayTravel(final RingPassenger passenger, final Ring from, final Ring to)
        {
            return !refuse.contains(passenger.getName());
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
    public void anOutsidePluginCanTakeOnePassengerOutOfATrip()
    {
        // What RingTravelEvent is for. A refusal drops that traveller and leaves the rest of
        // the trip alone: the rings still fire and everyone else still goes.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.put(pair.getEndA(), new Traveller("alice", "a", true), new Traveller("bob", "b", true));
        world.refuse.add("alice");

        assertEquals(1, new RingCycle(pair, world, REACH).flash());
        assertTrue(world.deliveries.contains("bob -> 500:64:500"));
        assertFalse(world.deliveries.contains("alice -> 500:64:500"));
    }

    @Test
    public void anOutsidePluginIsAskedOnlyAfterBothEndsHaveBeenRead()
    {
        // The ordering the event's contract rests on. A listener must always see the trip as
        // it was before any of it happened, never a half-finished one with the people from
        // one end already standing in the other.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.put(pair.getEndA(), new Traveller("alice", "a", true));
        world.put(pair.getEndB(), new Traveller("bob", "b", true));
        world.refuse.add("bob");

        assertEquals(1, new RingCycle(pair, world, REACH).flash());
        assertTrue(world.deliveries.contains("alice -> 500:64:500"),
            "alice still travels though bob was refused");
        assertEquals(1, world.deliveries.size());
    }

    @Test
    public void aRiderIsCarriedByItsMountRatherThanSentSeparately()
    {
        // The camel bug. A rider and its mount are two things standing in the same ring, and
        // moving them one at a time leaves whichever went first without the other for an
        // instant — the game breaks the seat rather than stretching it, and the player lands
        // beside their camel. Only the mount is delivered; the rider goes with it.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        final Traveller camel = new Traveller("camel", "c", false);
        final Traveller rider = new Traveller("alice", "a", true).riding(camel);
        world.put(pair.getEndA(), camel, rider);

        assertEquals(1, new RingCycle(pair, world, REACH).flash(), "one delivery, not two");
        assertTrue(world.deliveries.contains("camel -> 500:64:500"));
        assertFalse(world.deliveries.contains("alice -> 500:64:500"),
            "the rider must not be moved out from under itself");
    }

    @Test
    public void aRiderWhoseMountIsStayingBehindTravelsAlone()
    {
        // Their ride is not going, so dismounting is the right answer rather than a bug.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        final Traveller horseOutside = new Traveller("horse", "h", false);
        final Traveller rider = new Traveller("alice", "a", true).riding(horseOutside);
        world.put(pair.getEndA(), rider);

        assertEquals(1, new RingCycle(pair, world, REACH).flash());
        assertTrue(world.deliveries.contains("alice -> 500:64:500"));
    }

    @Test
    public void aRiderRefusedByAccessDoesNotStopTheMountGoing()
    {
        // Cargo is not subject to access, so the camel travels and the stranger stays. They
        // are dropped for the access rule, not by being counted as a passenger of it.
        final RingPair pair = pair();
        pair.setAccess(RingAccess.PRIVATE);
        final FakeWorld world = new FakeWorld();
        final Traveller camel = new Traveller("camel", "c", false);
        final Traveller stranger = new Traveller("stranger", STRANGER, true).riding(camel);
        world.put(pair.getEndA(), camel, stranger);

        assertEquals(1, new RingCycle(pair, world, REACH).flash());
        assertTrue(world.deliveries.contains("camel -> 500:64:500"));
    }

    @Test
    public void nobodyIsSentIntoARingThatHasBeenBuiltIn()
    {
        // A ring is invisible and its inside is ordinary ground, so somebody can drop a block
        // in one long after it was made. Arriving inside that block is the one outcome worse
        // than not arriving, so the whole end simply takes nobody.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.put(pair.getEndA(), new Traveller("alice", "a", true));
        world.blocked.put("500:64:500", RingBlockage.OBSTRUCTED);

        assertEquals(0, new RingCycle(pair, world, REACH).flash());
        assertTrue(world.deliveries.isEmpty());
    }

    @Test
    public void aRingWithAHoleInItsFloorTakesNobodyEither()
    {
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.put(pair.getEndA(), new Traveller("alice", "a", true));
        world.blocked.put("500:64:500", RingBlockage.NO_GROUND);

        assertEquals(0, new RingCycle(pair, world, REACH).flash());
    }

    @Test
    public void oneBlockedEndDoesNotStopTheOtherDirection()
    {
        // The two directions are separate journeys. Somebody standing in the blocked end can
        // still leave it — there is nothing wrong with departing from a ring you cannot
        // arrive in — while nobody is sent the other way.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.put(pair.getEndA(), new Traveller("alice", "a", true));
        world.put(pair.getEndB(), new Traveller("bob", "b", true));
        world.blocked.put("500:64:500", RingBlockage.OBSTRUCTED);

        assertEquals(1, new RingCycle(pair, world, REACH).flash());
        assertTrue(world.deliveries.contains("bob -> 0:64:0"), "bob leaves the blocked end");
        assertFalse(world.deliveries.contains("alice -> 500:64:500"), "alice is not sent into it");
    }

    @Test
    public void aCycleThatCarriedNobodyOwesNoCooldown()
    {
        // The cooldown exists so an arrival cannot immediately re-fire the ring it landed in.
        // A cycle that moved nobody has no arrival to guard against, so making somebody wait
        // a minute to retry a trip that never happened would just punish them for stepping
        // out of the ring.
        final RingPair pair = pair();
        final RingCycle cycle = new RingCycle(pair, new FakeWorld(), REACH);
        assertEquals(0, cycle.flash(), "nobody was in it");
        assertEquals(0, cycle.getCarried());

        final RingPair carried = pair();
        final FakeWorld busy = new FakeWorld();
        busy.put(carried.getEndA(), new Traveller("alice", "a", true));
        final RingCycle went = new RingCycle(carried, busy, REACH);
        went.flash();
        assertEquals(1, went.getCarried(), "and this one did carry somebody");
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
    public void theCountdownLightsAreDrawnAndCleanedUpOnAbort()
    {
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        final RingCycle cycle = new RingCycle(pair, world, REACH);

        cycle.beginCountdown();
        assertEquals(RingPhase.COUNTDOWN, pair.getPhase());
        assertEquals(RingPattern.ODD.getPerimeter().size() * 2, world.drawnCount());

        cycle.abort();
        assertEquals(RingPhase.IDLE, pair.getPhase());
        assertEquals(0, world.drawnCount(), "nothing left drawn");
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
    public void thePadStaysLitAllTheWayThroughTheCycle()
    {
        // The lights are lit for the whole trip, not just the countdown. Putting them out as
        // the rings start rising would have the pad go dark exactly as it does the thing it
        // was lit for.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.setReal(-3, 63, 0, Material.STONE);
        final RingCycle cycle = new RingCycle(pair, world, REACH);

        cycle.beginCountdown();
        assertEquals(Material.GLOWSTONE, world.seenAt(-3, 63, 0), "lit while counting down");

        cycle.beginDeploy();
        assertEquals(Material.GLOWSTONE, world.seenAt(-3, 63, 0), "still lit as the rings rise");

        while (cycle.advanceFrame())
        {
            assertEquals(Material.GLOWSTONE, world.seenAt(-3, 63, 0), "lit for every frame");
        }
        cycle.flash();
        assertEquals(Material.GLOWSTONE, world.seenAt(-3, 63, 0), "lit through the transport");

        cycle.beginRetract();
        while (cycle.advanceFrame())
        {
            // run it out
        }
        assertEquals(Material.GLOWSTONE, world.seenAt(-3, 63, 0), "lit as the rings come home");
    }

    @Test
    public void theRingsCanGoWhileThePadIsStillLit()
    {
        // What the linger is made of: the rings are taken down on their own, and the lights
        // follow a beat later rather than on the same tick.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.setReal(-3, 63, 0, Material.STONE);
        final RingCycle cycle = new RingCycle(pair, world, REACH);

        cycle.beginCountdown();
        cycle.beginDeploy();
        cycle.clearRings();

        assertEquals(Material.GLOWSTONE, world.seenAt(-3, 63, 0), "the pad is still lit");
        assertEquals(RingPattern.ODD.getPerimeter().size() * 2, world.drawnCount(),
            "and only the lights are left drawn");

        cycle.clearLights();
        assertEquals(0, world.drawnCount());
        assertEquals(Material.STONE, world.seenAt(-3, 63, 0));
    }

    @Test
    public void aFullRunLeavesNothingDrawnBehind()
    {
        // The thing that would otherwise leave a ring hanging in somebody's room until they
        // next relogged.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        final RingCycle cycle = new RingCycle(pair, world, REACH);

        runWholeCycle(cycle);

        assertEquals(RingPhase.IDLE, pair.getPhase());
        assertEquals(0, world.drawnCount(), "not one block left drawn");
    }

    @Test
    public void aWholeCycleNeverChangesASingleRealBlock()
    {
        // The point of drawing rather than placing. A server stopped mid-cycle keeps nothing,
        // block loggers see nothing, and nobody can mine the glowstone out of their own floor
        // while it is lit.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.setReal(-3, 63, 0, Material.STONE);
        world.setReal(-3, 64, 0, Material.DIAMOND_BLOCK);

        runWholeCycle(new RingCycle(pair, world, REACH));

        assertEquals(Material.STONE, world.realAt(-3, 63, 0), "the floor was never replaced");
        assertEquals(Material.DIAMOND_BLOCK, world.realAt(-3, 64, 0), "nor anything in the way");
    }

    @Test
    public void aLightIsDrawnOverTheFloorItIsSetIntoAndThenTakenAway()
    {
        // A light sits inside the surface, so there is always a solid block where it goes.
        // Drawing costs nothing, which is why it can simply cover it.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.setReal(-3, 63, 0, Material.STONE);

        final RingCycle cycle = new RingCycle(pair, world, REACH);
        cycle.beginCountdown();
        assertEquals(Material.GLOWSTONE, world.seenAt(-3, 63, 0), "the floor appears lit");
        assertEquals(Material.STONE, world.realAt(-3, 63, 0), "but is still really stone");

        cycle.abort();
        assertEquals(Material.STONE, world.seenAt(-3, 63, 0), "and looks like stone again");
    }

    @Test
    public void aRingIsDrawnStraightOverWhateverIsInItsWay()
    {
        // Since nothing is being replaced, a ring passing through somebody's staircase can
        // simply be drawn over it and still look like a complete ring. Placing real blocks
        // had to skip those positions and leave the ring with a hole in it.
        final RingPair pair = pair();
        final FakeWorld world = new FakeWorld();
        world.setReal(-3, 64, 0, Material.DIAMOND_BLOCK);

        final RingCycle cycle = new RingCycle(pair, world, REACH);
        cycle.beginCountdown();
        cycle.beginDeploy();

        assertEquals(Material.STONE_SLAB, world.seenAt(-3, 64, 0), "the ring is unbroken");
        assertEquals(Material.DIAMOND_BLOCK, world.realAt(-3, 64, 0), "and the block is untouched");
    }

    /** Runs a cycle from countdown through to cooldown. */
    private static void runWholeCycle(final RingCycle cycle)
    {
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
    }

    @Test
    public void bothStylesRunToTheEndAndCleanUpAfterThemselves()
    {
        for (final RingStyle style : RingStyle.values())
        {
            final RingPair pair = pair();
            pair.getEndA().setStyle(style);
            pair.getEndB().setStyle(style);
            final FakeWorld world = new FakeWorld();
            final RingCycle cycle = new RingCycle(pair, world, REACH);

            cycle.beginCountdown();
            cycle.beginDeploy();
            int frames = 1;
            while (cycle.advanceFrame())
            {
                frames++;
            }
            assertEquals(RingAnimator.deployFrames(pair.getEndA(), style), frames,
                style + " ran the wrong length");

            cycle.flash();
            cycle.beginRetract();
            while (cycle.advanceFrame())
            {
                // run it out
            }
            cycle.finish(0L);
            assertEquals(0, world.drawnCount(), style + " left something behind");
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
        assertTrue(world.drawnCount() > 0, "the lights went up");
        cycle.abort();
        assertEquals(0, world.drawnCount(), "and came down from the right blocks");
    }

    @Test
    public void aRunningPairSaysSoSoNothingElseDrawsOverIt()
    {
        // Regression: stepping out of a ring and back in while it counted down took the
        // refusal path, which lit an outline for that player and scheduled it to put "the
        // real blocks" back two seconds later — landing as the rings deployed and wiping the
        // cycle's own lights. Anything that draws over a ring has to be able to tell that a
        // cycle owns those blocks.
        final RingPair pair = pair();
        final RingCycle cycle = new RingCycle(pair, new FakeWorld(), REACH);
        assertFalse(pair.isMidCycle(), "idle to begin with");

        cycle.beginCountdown();
        assertTrue(pair.isMidCycle(), "counting down counts as running");

        cycle.beginDeploy();
        assertTrue(pair.isMidCycle());
        cycle.flash();
        assertTrue(pair.isMidCycle());
        cycle.beginHold();
        assertTrue(pair.isMidCycle());
        cycle.beginRetract();
        assertTrue(pair.isMidCycle());

        cycle.finish(0L);
        assertFalse(pair.isMidCycle(), "and idle again once it is over");
    }

    @Test
    public void anAbortedCycleIsIdleAgainStraightAway()
    {
        // The other way a cycle ends. It has to release the pad too, or a ring that stood
        // down would stay marked as busy and nothing could draw on it again.
        final RingPair pair = pair();
        final RingCycle cycle = new RingCycle(pair, new FakeWorld(), REACH);
        cycle.beginCountdown();
        cycle.abort();
        assertFalse(pair.isMidCycle());
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
