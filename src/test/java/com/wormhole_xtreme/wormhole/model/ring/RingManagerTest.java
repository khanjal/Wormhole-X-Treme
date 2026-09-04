package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Where a ring may be built, which is not a question about how many there are.
 *
 * <p>Density costs nothing: the index is keyed by block, so a hundred rings in a chunk look
 * up as fast as one. What is not free is two rings sharing ground. A player standing between
 * overlapping footprints is inside two trigger volumes at once, and two animations write and
 * restore the same blocks — each undoing the other's idea of what was there before. So
 * overlap is refused absolutely, and separation is a comfort margin layered on top of a rule
 * that already holds.
 */
public class RingManagerTest
{
    private static final String WORLD = "world";
    private static final int REACH = 4;
    private static final int SEPARATION = 8;

    private static Ring ringAt(final int x, final int z)
    {
        return new Ring(x, 64, z, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
    }

    private static RingPair pairAt(final String id, final int x, final int z)
    {
        return new RingPair(id, WORLD, ringAt(x, z), ringAt(x + 200, z + 200));
    }

    @BeforeEach
    public void clearBefore()
    {
        RingManager.clear();
    }

    @AfterEach
    public void clearAfter()
    {
        RingManager.clear();
    }

    @Test
    public void emptyGroundAcceptsARing()
    {
        assertNull(RingManager.checkPlacement(ringAt(0, 0), WORLD, SEPARATION));
    }

    @Test
    public void aRingOnTopOfAnotherIsRefused()
    {
        RingManager.addPair(pairAt("aaaa1111", 0, 0), REACH);
        assertEquals(RingManager.Refusal.OVERLAPS_RING,
            RingManager.checkPlacement(ringAt(0, 0), WORLD, SEPARATION));
    }

    @Test
    public void twoRingsSharingEvenOneColumnAreRefused()
    {
        // Two ODD rings six apart just touch: one ring's east edge lands on the other's
        // west edge. That is enough, because the blocks they share are ones both animations
        // would write and both restore maps would claim as their own.
        RingManager.addPair(pairAt("aaaa1111", 0, 0), REACH);
        assertEquals(RingManager.Refusal.OVERLAPS_RING,
            RingManager.checkPlacement(ringAt(6, 0), WORLD, SEPARATION));
    }

    @Test
    public void overlapIgnoresHeightBecauseTheProblemIsTheGroundNotTheGap()
    {
        // A ring directly above another still shares its columns. The animation of one runs
        // through where the other's passengers stand.
        RingManager.addPair(pairAt("aaaa1111", 0, 0), REACH);
        final Ring above = new Ring(0, 120, 0, RingPattern.ODD, RingOrientation.CEILING,
            Material.STONE_SLAB, Material.GLOWSTONE);
        assertEquals(RingManager.Refusal.OVERLAPS_RING,
            RingManager.checkPlacement(above, WORLD, SEPARATION));
    }

    @Test
    public void clearOfOverlapButInsideTheSeparationIsRefusedSeparately()
    {
        // Distinguishing these two matters for the message. "Too close" tells the player to
        // move along a bit; "overlaps" tells them it will never work there.
        RingManager.addPair(pairAt("aaaa1111", 0, 0), REACH);
        assertEquals(RingManager.Refusal.TOO_CLOSE,
            RingManager.checkPlacement(ringAt(7, 0), WORLD, SEPARATION));
    }

    @Test
    public void farEnoughApartIsAccepted()
    {
        RingManager.addPair(pairAt("aaaa1111", 0, 0), REACH);
        assertNull(RingManager.checkPlacement(ringAt(20, 20), WORLD, SEPARATION));
    }

    @Test
    public void ringsInAnotherWorldDoNotObject()
    {
        // Coordinates repeat across worlds. A ring in the nether must not block one built at
        // the same numbers in the overworld.
        RingManager.addPair(pairAt("aaaa1111", 0, 0), REACH);
        assertNull(RingManager.checkPlacement(ringAt(0, 0), "world_nether", SEPARATION));
    }

    @Test
    public void bothEndsOfAnExistingPairAreCheckedNotJustTheFirst()
    {
        // The far end of a pair is just as real as the near one, and is the end somebody is
        // more likely to forget about when building nearby.
        RingManager.addPair(pairAt("aaaa1111", 0, 0), REACH);
        assertEquals(RingManager.Refusal.OVERLAPS_RING,
            RingManager.checkPlacement(ringAt(200, 200), WORLD, SEPARATION));
    }

    @Test
    public void pairsAreCountedPerOwnerForTheQuota()
    {
        final String mine = UUID.randomUUID().toString();
        final String theirs = UUID.randomUUID().toString();

        final RingPair a = pairAt("aaaa1111", 0, 0);
        a.setOwner(mine);
        final RingPair b = pairAt("bbbb2222", 1000, 1000);
        b.setOwner(mine);
        final RingPair c = pairAt("cccc3333", 2000, 2000);
        c.setOwner(theirs);
        RingManager.addPair(a, REACH);
        RingManager.addPair(b, REACH);
        RingManager.addPair(c, REACH);

        assertEquals(2, RingManager.countPairsOwnedBy(mine));
        assertEquals(1, RingManager.countPairsOwnedBy(theirs));
        assertEquals(0, RingManager.countPairsOwnedBy(UUID.randomUUID().toString()));
    }

    @Test
    public void removingAPairFreesItsGroundAgain()
    {
        final RingPair pair = pairAt("aaaa1111", 0, 0);
        RingManager.addPair(pair, REACH);
        assertNotNull(RingManager.checkPlacement(ringAt(0, 0), WORLD, SEPARATION));

        RingManager.removePair(pair, REACH);
        assertNull(RingManager.checkPlacement(ringAt(0, 0), WORLD, SEPARATION));
        assertNull(RingIndex.volumeAt(WORLD, 0, 64, 0));
    }

    @Test
    public void aPendingEndIsHeldPerPlayerAndRemembersItsWorld()
    {
        final UUID player = UUID.randomUUID();
        assertNull(RingManager.getPending(player));

        RingManager.setPending(player, ringAt(5, 5), WORLD);
        final RingManager.PendingRing held = RingManager.getPending(player);
        assertNotNull(held);
        assertEquals(WORLD, held.getWorldName());
        assertEquals(5, held.getRing().getAnchorX());

        assertNotNull(RingManager.clearPending(player));
        assertNull(RingManager.getPending(player));
    }

    @Test
    public void generatedIdsAreShortAndDoNotCollideWithWhatExists()
    {
        for (int i = 0; i < 200; i++)
        {
            final String id = RingManager.newId();
            assertEquals(8, id.length());
            assertNull(RingManager.getPair(id));
            RingManager.addPair(pairAt(id, i * 50, i * 50), REACH);
        }
        assertEquals(200, RingManager.getAllPairs().size());
    }
}
