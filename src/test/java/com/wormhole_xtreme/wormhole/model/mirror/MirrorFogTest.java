package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * Pulling a viewer's own fog in to where a mirror's room ends.
 *
 * <p>{@code Player.setSendViewDistance} is Paper's and no Spigot jar has it, so the reflective
 * lookup finds nothing on the compile path here and every decision in
 * {@link MirrorFog#apply} would be unreachable in a test. A stand-in goes in through
 * {@link MirrorFog#sendDistanceWith} instead, which records what it was asked to set: the
 * arithmetic, the two reasons to do nothing, and putting back exactly what was there are all
 * this class's own and worth pinning whatever server is underneath.
 */
class MirrorFogTest
{
    /** What the stand-in was asked to set, in order. */
    private final List<Integer> set = new ArrayList<>();

    /** What the server is pretending to send each player, before any mirror touches it. */
    private int sending = 10;

    @BeforeEach
    void setUp()
    {
        ConfigTestSupport.clear();
        ConfigTestSupport.set(ConfigKeys.MIRROR_FOG_AT_DEPTH, true);
        MirrorFog.sendDistanceWith(new MirrorFog.SendDistance()
        {
            @Override
            public int get(final Player player)
            {
                return sending;
            }

            @Override
            public void set(final Player player, final int chunks)
            {
                set.add(chunks);
                sending = chunks;
            }
        });
    }

    @AfterEach
    void tearDown()
    {
        MirrorFog.sendDistanceWith(null);
        ConfigTestSupport.clear();
    }

    private static Player player()
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    /**
     * A shallow room ends in the client's own fog, and the viewer gets their distance back.
     *
     * <p>A chunk over the depth, the same allowance a chunk crossing makes, so the far edge of
     * the room is inside what the client holds rather than exactly at its limit. Forty-eight
     * blocks is three chunks, so four.
     */
    @Test
    void aShallowRoomNarrowsTheViewerAndLeavingPutsItBack()
    {
        final Player viewer = player();

        MirrorFog.apply(viewer, 48);
        assertEquals(List.of(4), set, "three chunks of room, and one over for the edge");
        assertTrue(MirrorFog.narrowed(viewer.getUniqueId()));

        MirrorFog.restore(viewer.getUniqueId(), viewer);
        assertEquals(List.of(4, 10), set, "exactly what they were being sent before");
        assertFalse(MirrorFog.narrowed(viewer.getUniqueId()));
    }

    /**
     * At the default depth there is nothing to gain, so nothing is touched.
     *
     * <p>A room 160 blocks deep is ten chunks, and asking for eleven where the client is already
     * being sent ten would push the fog out rather than pull it in. This is the case on an
     * ordinary server, which is why the setting is documented as something to pair with a
     * shallower depth.
     */
    @Test
    void aRoomAsDeepAsTheClientIsSentChangesNothing()
    {
        MirrorFog.apply(player(), 160);

        assertEquals(List.of(), set, "the room already reaches as far as the chunks do");
    }

    /** Off by default, and off means the server is never asked for anything. */
    @Test
    void theSettingOffLeavesTheViewerAlone()
    {
        ConfigTestSupport.set(ConfigKeys.MIRROR_FOG_AT_DEPTH, false);

        MirrorFog.apply(player(), 48);

        assertEquals(List.of(), set, "nothing asked for with the setting off");
    }

    /**
     * A client is never sent less than the chunk it stands in and its neighbours.
     *
     * <p>A depth of four blocks asks for two chunks, which is the floor, not one.
     */
    @Test
    void aTinyRoomStillLeavesTheViewerTwoChunks()
    {
        MirrorFog.apply(player(), 4);

        assertEquals(List.of(MirrorFog.LEAST_CHUNKS), set, "the floor, not a chunk on its own");
    }

    /**
     * What is remembered is what the viewer had before any mirror touched it.
     *
     * <p>A second window coming into view while the first is still drawn asks again. Recording
     * the narrowed value there would put back the fog the mirror set rather than the one the
     * player had, and it would stay that way for the rest of their session.
     */
    @Test
    void aChangedDepthMovesTheFogButRemembersWhatTheyHad()
    {
        final Player viewer = player();

        MirrorFog.apply(viewer, 48);
        MirrorFog.apply(viewer, 16);
        MirrorFog.restore(viewer.getUniqueId(), viewer);

        assertEquals(List.of(4, 2, 10), set,
            "a shallower room pulls it in further, and leaving still gives back the original ten");
    }

    /** Turning the setting off while somebody stands at a mirror gives their fog back. */
    @Test
    void turningTheSettingOffWhileNarrowedGivesTheFogBack()
    {
        final Player viewer = player();
        MirrorFog.apply(viewer, 48);

        ConfigTestSupport.set(ConfigKeys.MIRROR_FOG_AT_DEPTH, false);
        MirrorFog.apply(viewer, 48);

        assertEquals(List.of(4, 10), set, "given back the moment the setting is off");
        assertFalse(MirrorFog.narrowed(viewer.getUniqueId()));
    }

    /** Deepening the room past what the client is sent gives the fog back: nothing left to gain. */
    @Test
    void deepeningTheRoomPastWhatIsSentGivesTheFogBack()
    {
        final Player viewer = player();
        MirrorFog.apply(viewer, 48);

        MirrorFog.apply(viewer, 160);

        assertEquals(List.of(4, 10), set, "eleven chunks is no nearer than the ten being sent");
        assertFalse(MirrorFog.narrowed(viewer.getUniqueId()));
    }

    /** Runs every redraw, and each change of send distance is chunk traffic: ask only when it moves. */
    @Test
    void anUnchangedDepthAsksTheServerForNothingMore()
    {
        final Player viewer = player();

        MirrorFog.apply(viewer, 48);
        MirrorFog.apply(viewer, 48);
        MirrorFog.apply(viewer, 48);

        assertEquals(List.of(4), set, "set once, however many redraws");
    }

    /** Most viewers were never narrowed, and putting them back must send nothing. */
    @Test
    void aViewerWhoWasNeverNarrowedIsNotSentAnything()
    {
        final Player viewer = player();

        MirrorFog.restore(viewer.getUniqueId(), viewer);

        assertEquals(List.of(), set, "nothing to put back");
    }

    /**
     * A viewer who logged out mid-view is forgotten, though nothing can be sent to them.
     *
     * <p>From the review. Their send distance dies with the connection, so there is nothing to
     * put back -- but leaving them remembered as narrowed would keep the entry for the life of
     * the server and, worse, make {@link MirrorFog#apply} skip them if they came back on the
     * same id, since it does nothing for a viewer it thinks is already narrowed.
     */
    @Test
    void aViewerWhoWentAwayIsForgottenAndCanBeNarrowedAgainOnReturn()
    {
        final Player viewer = player();
        MirrorFog.apply(viewer, 48);
        set.clear();
        sending = 10;

        // Gone: the server has the id but no player to send anything to.
        MirrorFog.restore(viewer.getUniqueId(), null);

        assertFalse(MirrorFog.narrowed(viewer.getUniqueId()), "not remembered as narrowed");
        assertEquals(List.of(), set, "and nothing sent to somebody who is not there");

        MirrorFog.apply(viewer, 48);
        assertEquals(List.of(4), set, "so coming back narrows again rather than being skipped");
    }

    /**
     * Forgetting is not putting back: a reload drops what it remembered and sends nothing.
     *
     * <p>The caller has either already restored each player one at a time or has none left to
     * restore, so a second set here would be a packet for nothing.
     */
    @Test
    void clearForgetsWithoutSendingAnything()
    {
        final Player viewer = player();
        MirrorFog.apply(viewer, 48);
        set.clear();

        MirrorFog.clear();

        assertFalse(MirrorFog.narrowed(viewer.getUniqueId()));
        assertEquals(List.of(), set, "a reload sends nothing");
    }

    /** Without the API there is no feature, which is every Spigot server. */
    @Test
    void withoutTheServersMethodThereIsNoFeature()
    {
        MirrorFog.sendDistanceWith(null);

        assertFalse(MirrorFog.available(), "no Spigot jar in the supported range has it");
        MirrorFog.apply(player(), 48);
        assertEquals(List.of(), set, "and nothing is asked of the server");
    }
}
