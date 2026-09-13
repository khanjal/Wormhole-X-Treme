package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The registry every right-click on the server asks a question of.
 *
 * <p>Two indexes over the same mirrors -- by name for an admin, by block for the interact
 * handler -- and the thing worth testing is that they never disagree. An entry left in the
 * block index after its mirror has gone is a banner that still teleports people and that no
 * command can see to remove.
 */
class MirrorManagerTest
{
    private static final MirrorPoint SOMEWHERE_ELSE =
        new MirrorPoint("museum_1_18", 100.5, 64.0, -20.5, 90.0f, 0.0f);

    @BeforeEach
    void setUp()
    {
        MirrorManager.clear();
    }

    @AfterEach
    void tearDown()
    {
        MirrorManager.clear();
    }

    private static QuantumMirror mirror(final String name, final int x)
    {
        return new QuantumMirror(name, new MirrorBlock("world", x, 64, 0), SOMEWHERE_ELSE);
    }

    @Test
    void aMirrorIsFoundByNameAndByItsBlock()
    {
        final QuantumMirror m = mirror("Museum", 10);
        MirrorManager.add(m);

        assertEquals(m, MirrorManager.byName("Museum"));
        assertEquals(m, MirrorManager.at(new MirrorBlock("world", 10, 64, 0)));
    }

    /** Names match the way gate and beam names do, so a remembered capital is not a trap. */
    @Test
    void nameLookupIgnoresCase()
    {
        MirrorManager.add(mirror("Museum", 10));

        assertNotNull(MirrorManager.byName("museum"));
        assertNotNull(MirrorManager.byName("MUSEUM"));
    }

    /** A block that is not a mirror is the overwhelmingly common answer and must be cheap. */
    @Test
    void anUnboundBlockIsNotAMirror()
    {
        MirrorManager.add(mirror("Museum", 10));

        assertNull(MirrorManager.at(new MirrorBlock("world", 11, 64, 0)));
        assertNull(MirrorManager.at(new MirrorBlock("nether", 10, 64, 0)),
            "same coordinates in another world is a different block");
    }

    /**
     * Rebinding a name to a different banner releases the old one.
     *
     * <p>The failure this guards is specific: without it the previous banner stays in the
     * block index, so it keeps teleporting whoever clicks it while no command lists it and
     * {@code mirror remove} cannot reach it. A banner nothing can see and nothing can remove
     * is worse than one that does not work.
     */
    @Test
    void rebindingANameStopsTheOldBannerWorking()
    {
        MirrorManager.add(mirror("Museum", 10));
        MirrorManager.add(mirror("Museum", 20));

        assertNull(MirrorManager.at(new MirrorBlock("world", 10, 64, 0)),
            "the banner this name used to be on must stop answering");
        assertNotNull(MirrorManager.at(new MirrorBlock("world", 20, 64, 0)));
        assertEquals(1, MirrorManager.count(), "it is the same mirror, renamed to a new block");
    }

    /** Removing by name takes the block index with it. */
    @Test
    void removingAMirrorTakesItsBlockToo()
    {
        MirrorManager.add(mirror("Museum", 10));

        final QuantumMirror removed = MirrorManager.remove("MUSEUM");

        assertNotNull(removed, "removal matches case-insensitively too");
        assertNull(MirrorManager.byName("Museum"));
        assertNull(MirrorManager.at(new MirrorBlock("world", 10, 64, 0)),
            "a removed mirror's banner must stop teleporting people");
        assertEquals(0, MirrorManager.count());
    }

    /** Removing something that is not there is not an error. */
    @Test
    void removingAnUnknownNameAnswersNothing()
    {
        assertNull(MirrorManager.remove("nothing-by-that-name"));
    }

    /** Two mirrors in one world, each to a different world, is the museum corridor. */
    @Test
    void oneWorldCanHoldManyMirrors()
    {
        MirrorManager.add(new QuantumMirror("Museum1",
            new MirrorBlock("world", 10, 64, 0), new MirrorPoint("snapshot_a", 0, 64, 0, 0, 0)));
        MirrorManager.add(new QuantumMirror("Museum2",
            new MirrorBlock("world", 12, 64, 0), new MirrorPoint("snapshot_b", 0, 64, 0, 0, 0)));

        assertEquals(2, MirrorManager.count(),
            "the cross-world rule is per mirror, not a one-mirror-per-world cap");
        assertNotNull(MirrorManager.at(new MirrorBlock("world", 10, 64, 0)));
        assertNotNull(MirrorManager.at(new MirrorBlock("world", 12, 64, 0)));
    }

    /** The cross-world rule is about the mirror's own two ends. */
    @Test
    void aMirrorKnowsWhetherBothItsEndsShareAWorld()
    {
        assertFalse(mirror("Away", 10).isSameWorld(), "world -> museum_1_18 is two worlds");
        assertTrue(new QuantumMirror("Here", new MirrorBlock("world", 10, 64, 0),
            new MirrorPoint("world", 200, 64, 200, 0, 0)).isSameWorld());
    }

    /** A named but unpointed mirror is a real state, not a broken one. */
    @Test
    void aMirrorWithNoDestinationIsNotSameWorld()
    {
        final QuantumMirror unpointed =
            new QuantumMirror("New", new MirrorBlock("world", 10, 64, 0), null);

        assertFalse(unpointed.isSameWorld(),
            "with nowhere to go it cannot be going somewhere in this world");
    }

    /**
     * Removing one of two mirrors on one banner leaves the other one working.
     *
     * <p>Two names on one banner is not a state this class will produce -- {@code add} clears
     * the old banner when it replaces a name, and the command layer refuses the case it cannot
     * read. A hand-edited {@code mirror.yml} can still hold one, and this is the removal that
     * used to make it worse: clearing the block index by the removed mirror's banner unhooked
     * whichever mirror was actually using it. That survivor keeps its name and its entry, still
     * lists, and does nothing at all when clicked -- which looks like the plugin having eaten
     * the banner rather than like a file that needs a line taking out.
     */
    @Test
    void removingOneOfTwoMirrorsOnOneBannerLeavesTheOtherClickable()
    {
        final MirrorBlock shared = new MirrorBlock("world", 10, 64, 0);
        MirrorManager.add(new QuantumMirror("stale", shared, null));
        final QuantumMirror live = new QuantumMirror("live", shared,
            new MirrorPoint("world", 200, 64, 200, 0, 0));
        MirrorManager.add(live);

        assertNotNull(MirrorManager.remove("stale"), "the stale entry should come out");

        assertNull(MirrorManager.byName("stale"), "and stay out");
        assertEquals(live, MirrorManager.at(shared),
            "while the banner still answers to the mirror that holds it");
    }
}
