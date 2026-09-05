package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Handing a pair to somebody else, and what that has to check.
 *
 * <p>The case this is written for is staff building rings for a player, but a gift between
 * players works the same way. The part worth being careful about is the quota: it counts
 * pairs by owner, so a transfer that skipped the check would let anyone go past their limit
 * by having a friend build the ring and hand it over.
 */
class RingOwnershipTest
{
    private static final String WORLD = "world";
    private static final int REACH = 4;
    private static final String STAFF = "staff-uuid";
    private static final String PLAYER = "player-uuid";

    private static RingPair pairAt(final String id, final int x, final int owner)
    {
        final Ring a = new Ring(x, 64, 0, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final Ring b = new Ring(x + 200, 64, 200, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final RingPair pair = new RingPair(id, WORLD, a, b);
        pair.setOwner(owner == 0 ? STAFF : PLAYER);
        return pair;
    }

    @BeforeEach
    void clearBefore()
    {
        RingManager.clear();
    }

    @AfterEach
    void clearAfter()
    {
        RingManager.clear();
    }

    @Test
    void ownershipMovesAndTheQuotaMovesWithIt()
    {
        final RingPair pair = pairAt("own00001", 0, 0);
        RingManager.addPair(pair, REACH);
        assertEquals(1, RingManager.countPairsOwnedBy(STAFF));
        assertEquals(0, RingManager.countPairsOwnedBy(PLAYER));

        pair.setOwner(PLAYER);
        pair.setOwnerName("Justin");

        assertEquals(0, RingManager.countPairsOwnedBy(STAFF), "it no longer counts against staff");
        assertEquals(1, RingManager.countPairsOwnedBy(PLAYER));
        assertTrue(pair.isOwnedBy(PLAYER));
    }

    @Test
    void theNewOwnerCanUseAPrivatePairAndTheOldOneCannot()
    {
        // The consequence a transfer has to say out loud. Staff who built a ring for
        // somebody should not keep standing access to it afterwards, and the new owner can
        // add them back if that is what they want.
        final RingPair pair = pairAt("own00002", 0, 0);
        pair.setAccess(RingAccess.PRIVATE);
        RingManager.addPair(pair, REACH);
        assertTrue(pair.mayUse(STAFF));

        pair.setOwner(PLAYER);

        assertTrue(pair.mayUse(PLAYER));
        assertFalse(pair.mayUse(STAFF));
    }

    @Test
    void anAllowedPlayerKeepsAccessAcrossATransfer()
    {
        // The allow list belongs to the pair rather than to whoever owns it, so people who
        // were using a ring do not lose it because it changed hands.
        final RingPair pair = pairAt("own00003", 0, 0);
        pair.setAccess(RingAccess.PRIVATE);
        pair.allow("friend-uuid");
        RingManager.addPair(pair, REACH);

        pair.setOwner(PLAYER);

        assertTrue(pair.mayUse("friend-uuid"));
    }

    @Test
    void aQuotaIsCountedPerOwnerSoTransfersCanBeCheckedAgainstIt()
    {
        // What the command consults before handing anything over. Without this, a limit of
        // three could be walked around by having somebody else build the fourth.
        for (int i = 0; i < 3; i++)
        {
            RingManager.addPair(pairAt("full000" + i, i * 100, 1), REACH);
        }
        assertEquals(3, RingManager.countPairsOwnedBy(PLAYER));

        final RingPair gift = pairAt("gift0001", 900, 0);
        RingManager.addPair(gift, REACH);
        assertEquals(3, RingManager.countPairsOwnedBy(PLAYER), "still three until it moves");

        gift.setOwner(PLAYER);
        assertEquals(4, RingManager.countPairsOwnedBy(PLAYER));
    }

    @Test
    void ownershipSurvivesBeingWrittenOutAndReadBack()
    {
        final RingPair pair = pairAt("own00004", 0, 0);
        pair.setOwner(PLAYER);
        pair.setOwnerName("Justin");
        RingManager.addPair(pair, REACH);

        assertTrue(RingManager.getPair("own00004").isOwnedBy(PLAYER));
        assertEquals("Justin", RingManager.getPair("own00004").getOwnerName());
    }
}
