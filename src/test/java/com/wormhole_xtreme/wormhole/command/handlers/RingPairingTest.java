package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingAccess;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingOrientation;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingPattern;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;
import com.wormhole_xtreme.wormhole.model.ring.RingYamlManager;

/**
 * The rules for joining a second ring to the one already waiting.
 *
 * <p>Both ends in the same world, near enough on the ground, near enough in height, and not
 * the same circle of slabs twice. Nothing covered any of them: every line of
 * {@code completePair} was uncovered, which is how PR #185 -- a two-field record conversion --
 * came to report 11.5% coverage on its own rename.
 *
 * <p>The refusals matter as much as the success. A player who is told no has still laid a
 * second circle of slabs, and the first end has to still be waiting for them afterwards.
 */
class RingPairingTest
{
    private static final String WORLD = "world";
    private static final String ELSEWHERE = "nether";
    private static final String OWNER = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

    private Player player;
    private World world;
    private Map<String, Block> blocks;
    private MockedStatic<ConfigManager> config;
    private MockedStatic<RingYamlManager> yaml;

    @BeforeEach
    void setUp()
    {
        RingManager.clear();
        blocks = new HashMap<>();
        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            final int x = inv.getArgument(0, Integer.class).intValue();
            final int y = inv.getArgument(1, Integer.class).intValue();
            final int z = inv.getArgument(2, Integer.class).intValue();
            return blocks.computeIfAbsent(x + "," + y + "," + z, key -> {
                final Block b = mock(Block.class);
                // Slabs, so taking the template back is something that can be seen happening.
                when(b.getType()).thenReturn(Material.STONE_SLAB);
                return b;
            });
        });

        player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.fromString(OWNER));
        when(player.getName()).thenReturn("Justin");
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));

        config = mockStatic(ConfigManager.class);
        // A mocked config answers 0 to every int, and 0 means "no limit" to two of these
        // rules. Every test that cares sets its own.
        config.when(ConfigManager::getRingMaxLinkDistance).thenReturn(Integer.valueOf(64));
        config.when(ConfigManager::getRingMaxLinkHeight).thenReturn(Integer.valueOf(32));
        config.when(ConfigManager::getRingReach).thenReturn(Integer.valueOf(4));
        config.when(ConfigManager::getRingDefaultAccess).thenReturn(RingAccess.PUBLIC);
        config.when(ConfigManager::getRingDefaultStyle).thenReturn(RingStyle.SEQUENTIAL);
        config.when(ConfigManager::getRingDefaultFlash).thenReturn(Material.GLOWSTONE);

        yaml = mockStatic(RingYamlManager.class);
    }

    @AfterEach
    void tearDown()
    {
        yaml.close();
        config.close();
        RingManager.clear();
    }

    /**
     * A ring as the detector would hand one over, lit with something the config does not name.
     *
     * <p>Deliberately not {@code GLOWSTONE}: a ring built with the same flash the config
     * supplies would look identical whether or not the configured one was ever applied.
     */
    private static Ring ringAt(final int x, final int y, final int z)
    {
        return new Ring(x, y, z, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.SEA_LANTERN);
    }

    /** Puts a first end on hold and returns it, the way the command's first half would. */
    private RingManager.PendingRing waitingAt(final int x, final int y, final int z, final String in)
    {
        RingManager.setPending(UUID.fromString(OWNER), ringAt(x, y, z), in);
        return RingManager.getPending(UUID.fromString(OWNER));
    }

    private boolean pairWith(final RingManager.PendingRing waiting, final Ring second)
    {
        return RingCommand.completePair(player, waiting, second, WORLD);
    }

    /** The pair the command registered, or null if it registered none. */
    private static RingPair registered()
    {
        return RingManager.getAllPairs().isEmpty()
            ? null : RingManager.getAllPairs().iterator().next();
    }

    private static RingManager.PendingRing stillWaiting()
    {
        return RingManager.getPending(UUID.fromString(OWNER));
    }

    /**
     * Two ends in the same world, close enough, make a live pair.
     *
     * <p>The whole of the success path in one place: the pair is registered, it belongs to
     * whoever built it, the first end stops waiting, and both circles of slabs are taken up.
     */
    @Test
    void twoEndsInOneWorldBecomeALivePair()
    {
        final RingManager.PendingRing waiting = waitingAt(0, 64, 0, WORLD);

        assertTrue(pairWith(waiting, ringAt(20, 64, 0)));

        final RingPair pair = registered();
        assertNotNull(pair, "the pair is registered");
        assertEquals(OWNER, pair.getOwner(), "and belongs to whoever built it");
        assertEquals("Justin", pair.getOwnerName());
        assertEquals(WORLD, pair.getWorldName());
        assertTrue(pair.getCreated() > 0L, "and is stamped with when");
        // PUBLIC rather than PRIVATE, which is what a RingPair is before anybody sets it: the
        // point is that the configured default was read, not that the field holds something.
        assertEquals(RingAccess.PUBLIC, pair.getAccess(), "and opens as the config says");
        assertNull(stillWaiting(), "nothing is left waiting once the pair exists");
        verify(player).sendMessage(contains("is live"));
    }

    /**
     * Both circles of slabs are taken up, not just the second.
     *
     * <p>They are left lying until now on purpose: a crash between the two halves would
     * otherwise cost somebody a circle of slabs for a ring that never existed.
     */
    @Test
    void bothTemplatesAreTakenUpTogether()
    {
        final RingManager.PendingRing waiting = waitingAt(0, 64, 0, WORLD);
        final Ring second = ringAt(20, 64, 0);

        pairWith(waiting, second);

        for (final Ring end : new Ring[] { waiting.ring(), second })
        {
            final int[] block = end.perimeterBlocks().get(0);
            verify(world.getBlockAt(block[0], block[1], block[2]))
                .setType(Material.AIR, false);
        }
    }

    /** Both ends take the configured look, so a pair does not arrive half-styled. */
    @Test
    void bothEndsTakeTheConfiguredLook()
    {
        final RingManager.PendingRing waiting = waitingAt(0, 64, 0, WORLD);
        final Ring second = ringAt(20, 64, 0);

        pairWith(waiting, second);

        for (final Ring end : new Ring[] { waiting.ring(), second })
        {
            assertEquals(RingStyle.SEQUENTIAL, end.getStyle());
            assertEquals(Material.GLOWSTONE, end.getFlashMaterial());
        }
    }

    /** A pair that starts private says how to let anybody else in. */
    @Test
    void aPrivatePairIsToldHowToLetOthersIn()
    {
        config.when(ConfigManager::getRingDefaultAccess).thenReturn(RingAccess.PRIVATE);

        pairWith(waitingAt(0, 64, 0, WORLD), ringAt(20, 64, 0));

        assertEquals(RingAccess.PRIVATE, registered().getAccess());
        verify(player).sendMessage(contains("/wormhole ring allow"));
    }

    /**
     * The two ends have to be in one world, and the refusal says which two.
     *
     * <p>Said here rather than discovered later: the player has already laid a second circle
     * of slabs by the time this runs, and needs to know which one to give up on.
     */
    @Test
    void endsInDifferentWorldsAreRefusedAndTheFirstKeepsWaiting()
    {
        final RingManager.PendingRing waiting = waitingAt(0, 64, 0, ELSEWHERE);

        assertTrue(pairWith(waiting, ringAt(20, 64, 0)));

        assertNull(registered(), "no pair is made across worlds");
        assertNotNull(stillWaiting(), "and the first end is still waiting");
        verify(player).sendMessage(contains("Your first ring is in " + ELSEWHERE
            + ", and this one is in " + WORLD));
    }

    /** Too far apart on the ground is refused, and the message names both numbers. */
    @Test
    void endsFurtherApartThanRingsReachAreRefused()
    {
        config.when(ConfigManager::getRingMaxLinkDistance).thenReturn(Integer.valueOf(64));
        final RingManager.PendingRing waiting = waitingAt(0, 64, 0, WORLD);

        assertTrue(pairWith(waiting, ringAt(100, 64, 0)));

        assertNull(registered());
        assertNotNull(stillWaiting());
        verify(player).sendMessage(contains("are 100 blocks apart on the ground, and rings reach 64"));
    }

    /**
     * The limit is a distance, not a squared one.
     *
     * <p>Exactly at the limit is allowed, one block past it is not. Comparing the squared
     * distance against an unsquared limit would refuse anything past eight blocks here, and
     * nothing would look wrong about it.
     */
    @Test
    void theGroundLimitIsMeasuredInBlocksAndIsInclusive()
    {
        config.when(ConfigManager::getRingMaxLinkDistance).thenReturn(Integer.valueOf(64));

        pairWith(waitingAt(0, 64, 0, WORLD), ringAt(64, 64, 0));
        assertNotNull(registered(), "exactly at the limit is near enough");

        RingManager.clear();
        pairWith(waitingAt(0, 64, 0, WORLD), ringAt(65, 64, 0));
        assertNull(registered(), "one block past it is not");
    }

    /** A limit of zero means rings reach as far as anybody wants. */
    @Test
    void aGroundLimitOfZeroIsNoLimit()
    {
        config.when(ConfigManager::getRingMaxLinkDistance).thenReturn(Integer.valueOf(0));

        pairWith(waitingAt(0, 64, 0, WORLD), ringAt(30000, 64, 0));

        assertNotNull(registered(), "zero turns the ground limit off");
    }

    /**
     * Height is asked about separately, and either way up.
     *
     * <p>Straight down is what rings are for, so the two questions have different limits.
     * Without the absolute value, a second ring far below the first would pass a check that a
     * ring the same distance above it fails.
     */
    @Test
    void endsTooFarApartInHeightAreRefusedInEitherDirection()
    {
        config.when(ConfigManager::getRingMaxLinkHeight).thenReturn(Integer.valueOf(32));

        pairWith(waitingAt(0, 64, 0, WORLD), ringAt(20, 130, 0));
        assertNull(registered(), "66 blocks above is too far");
        verify(player).sendMessage(contains("are 66 blocks apart in height, and rings reach 32"));

        RingManager.clear();
        pairWith(waitingAt(0, 130, 0, WORLD), ringAt(20, 64, 0));
        assertNull(registered(), "and so is 66 blocks below");
    }

    /**
     * Exactly the configured climb is allowed; one block more is not.
     *
     * <p>The same boundary the ground limit has. Rings are for going straight down, so where
     * this line falls is the difference between a shaft that works and one that does not.
     */
    @Test
    void theHeightLimitIsInclusive()
    {
        config.when(ConfigManager::getRingMaxLinkHeight).thenReturn(Integer.valueOf(32));

        pairWith(waitingAt(0, 64, 0, WORLD), ringAt(20, 96, 0));
        assertNotNull(registered(), "exactly 32 blocks of climb is near enough");

        RingManager.clear();
        pairWith(waitingAt(0, 64, 0, WORLD), ringAt(20, 97, 0));
        assertNull(registered(), "33 is not");
    }

    /** A height limit of zero means rings reach as far up or down as anybody wants. */
    @Test
    void aHeightLimitOfZeroIsNoLimit()
    {
        config.when(ConfigManager::getRingMaxLinkHeight).thenReturn(Integer.valueOf(0));

        pairWith(waitingAt(0, 5, 0, WORLD), ringAt(20, 300, 0));

        assertNotNull(registered(), "zero turns the height limit off");
    }

    /**
     * Running the command again in the same circle does not pair it with itself.
     *
     * <p>The first ring's slabs are still lying there, so the detector finds the same circle a
     * second time. A pair of one ring is a transport that goes nowhere, and its distance and
     * height are both zero, so no earlier rule catches it.
     */
    @Test
    void thesameCircleTwiceIsRefusedAndTheFirstKeepsWaiting()
    {
        final RingManager.PendingRing waiting = waitingAt(0, 64, 0, WORLD);

        assertTrue(pairWith(waiting, ringAt(0, 64, 0)));

        assertNull(registered(), "a ring is not paired with itself");
        assertNotNull(stillWaiting(), "and the end already laid is still waiting for a partner");
        verify(player).sendMessage(contains("That is the ring you already laid"));
    }

    /**
     * Two ends differing on any one axis are a pair, the vertical one included.
     *
     * <p>A ring directly above another is the shape rings exist for -- a shaft between two
     * floors -- and it differs from the first end in nothing but height. If the same-circle
     * rule stopped asking about {@code y}, that pair would be refused as "the ring you already
     * laid" and there would be no way to build one.
     */
    @Test
    void endsThatDifferOnAnySingleAxisArePaired()
    {
        final int[][] offsets = { { 20, 0, 0 }, { 0, 20, 0 }, { 0, 0, 20 } };
        for (final int[] offset : offsets)
        {
            RingManager.clear();
            final RingManager.PendingRing waiting = waitingAt(0, 64, 0, WORLD);

            pairWith(waiting, ringAt(offset[0], 64 + offset[1], offset[2]));

            assertNotNull(registered(), "a second end offset by "
                + offset[0] + "," + offset[1] + "," + offset[2] + " is a different ring");
        }
    }

    /** A refused pairing leaves the slabs where they are, both circles of them. */
    @Test
    void aRefusedPairingTakesNobodysSlabs()
    {
        pairWith(waitingAt(0, 64, 0, ELSEWHERE), ringAt(20, 64, 0));

        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    /** Nothing is written to disk for a pairing that was refused. */
    @Test
    void aRefusedPairingSavesNothing()
    {
        pairWith(waitingAt(0, 64, 0, WORLD), ringAt(0, 64, 0));

        yaml.verify(RingYamlManager::savePending, never());
        yaml.verify(() -> RingYamlManager.saveWorld(WORLD), never());
    }

    /** A pair that was made is written out, both the pair and the emptied pending list. */
    @Test
    void aFinishedPairIsWrittenOut()
    {
        pairWith(waitingAt(0, 64, 0, WORLD), ringAt(20, 64, 0));

        yaml.verify(RingYamlManager::savePending);
        yaml.verify(() -> RingYamlManager.saveWorld(WORLD));
    }

    /** The pair holds the two ends that were joined, not copies of them. */
    @Test
    void thePairHoldsTheTwoEndsThatWereJoined()
    {
        final RingManager.PendingRing waiting = waitingAt(0, 64, 0, WORLD);
        final Ring second = ringAt(20, 64, 0);

        pairWith(waiting, second);

        assertSame(waiting.ring(), registered().getEndA(), "the end that was waiting is A");
        assertSame(second, registered().getEndB(), "and the one just laid is B");
    }

    /** The block-taking is not fussy about what it finds, but it does check. */
    @Test
    void aTemplateBlockSomebodyAlreadyBrokeIsLeftAlone()
    {
        final RingManager.PendingRing waiting = waitingAt(0, 64, 0, WORLD);
        final int[] gone = waiting.ring().perimeterBlocks().get(0);
        final Block broken = world.getBlockAt(gone[0], gone[1], gone[2]);
        when(broken.getType()).thenReturn(Material.AIR);

        pairWith(waiting, ringAt(20, 64, 0));

        verify(broken, never()).setType(any(Material.class), anyBoolean());
    }
}
