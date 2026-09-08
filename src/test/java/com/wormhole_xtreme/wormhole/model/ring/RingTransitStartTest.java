package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Whether a pair fires at all, and how often somebody is told when it will not.
 *
 * <p>{@code RingTransit} is deliberately thin: the animation's arithmetic lives in
 * {@link RingAnimator} and the frame-by-frame state in {@link RingCycle}, both of which are
 * covered. What was left here and covered by nothing is the set of rules deciding whether a
 * cycle starts -- and one of them exists purely to stop a wall of chat.
 *
 * <p>{@code start} runs on every block boundary somebody crosses inside a ring. A pair whose
 * far end has been built over is refused, and without the survey it caches that refusal is
 * re-read and re-announced several times a second. It is the same fault the gate side already
 * has a test for, in a place nothing was watching.
 */
class RingTransitStartTest
{
    private static final String WORLD = "world";
    private static final int AX = 100, AY = 64, AZ = 100;
    private static final int BX = 200, BY = 64, BZ = 200;

    /**
     * The last stone layer: the pads stand on it, so it is one below their own plane.
     *
     * <p>A floor ring's stack starts at its anchor, and the survey wants that layer clear and
     * the one under it solid. Ground level with the pads reads as a ring built over.
     */
    private static final int GROUND = AY - 1;

    private World world;
    private Player walker;
    private BukkitScheduler scheduler;
    private MockedStatic<ConfigManager> config;
    private MockedStatic<Bukkit> bukkit;
    private final Map<String, Block> blocks = new HashMap<>();
    private final Map<String, Chunk> chunks = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception
    {
        RingTransit.clear();
        RingManager.clear();
        blocks.clear();
        chunks.clear();

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        when(world.getMinHeight()).thenReturn(Integer.valueOf(-64));
        when(world.getMaxHeight()).thenReturn(Integer.valueOf(320));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> blockAt(
            inv.getArgument(0, Integer.class).intValue(),
            inv.getArgument(1, Integer.class).intValue(),
            inv.getArgument(2, Integer.class).intValue()));
        when(world.getChunkAt(anyInt(), anyInt())).thenAnswer(inv -> chunks.computeIfAbsent(
            inv.getArgument(0, Integer.class) + "," + inv.getArgument(1, Integer.class),
            key -> mock(Chunk.class)));

        final Server server = mock(Server.class);
        when(server.getWorld(WORLD)).thenReturn(world);

        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getServer()).thenReturn(server);
        set("thisPlugin", plugin);

        scheduler = mock(BukkitScheduler.class);
        // Never actually runs the task: the countdown reschedules itself, and every test here
        // is about the decision taken before the first tick.
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenReturn(Integer.valueOf(1));
        set("scheduler", scheduler);

        walker = mock(Player.class);
        when(walker.getName()).thenReturn("walker");
        when(walker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(walker.isOnline()).thenReturn(Boolean.TRUE);

        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingReach).thenReturn(Integer.valueOf(4));
        config.when(ConfigManager::getRingMaxCeilingDrop).thenReturn(Integer.valueOf(16));
        config.when(ConfigManager::getRingCountdownTicks).thenReturn(Integer.valueOf(60));
        // Named, not blank: a blank name is how the config turns a sound off, and these
        // tests are not about that.
        config.when(ConfigManager::getRingSoundOpen).thenReturn("block.beacon.activate");
        config.when(ConfigManager::getRingSoundVolume).thenReturn(Float.valueOf(1.0f));
        config.when(ConfigManager::isRingSoundsEnabled).thenReturn(Boolean.TRUE);

        // A cycle that starts draws its first frame, and drawing a block asks Bukkit to turn a
        // Material into BlockData -- which on a real server means asking the server. There
        // isn't one. Nothing here reads the result; it just has to exist.
        final BlockData anyBlockData = mock(BlockData.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenReturn(anyBlockData);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        bukkit.close();
        config.close();
        RingTransit.clear();
        RingManager.clear();
        set("thisPlugin", null);
        set("scheduler", null);
    }

    private static void set(final String name, final Object value) throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    /**
     * Ages the remembered blockage answer so the next walk-in reads the world again.
     *
     * <p>It is trusted for one second, which is not a thing to sit through in a test. Reaching
     * into the map is the only way to be somewhere other than "just now" without waiting.
     */
    private static void ageOutTheRememberedSurvey() throws Exception
    {
        final Map<String, Long> surveyed = surveyed();
        final Long past = Long.valueOf(System.currentTimeMillis() - 1L);
        // put over the keys rather than Map.Entry#setValue, which not every Map.Entry
        // implementation supports.
        for (final String id : new java.util.ArrayList<String>(surveyed.keySet()))
        {
            surveyed.put(id, past);
        }
    }

    /** What start() currently believes is mid-cycle. */
    @SuppressWarnings("unchecked")
    private static java.util.Set<String> running() throws Exception
    {
        final Field f = RingTransit.class.getDeclaredField("running");
        f.setAccessible(true);
        return (java.util.Set<String>) f.get(null);
    }

    /** What start() currently remembers as blocked. */
    @SuppressWarnings("unchecked")
    private static Map<String, Long> surveyed() throws Exception
    {
        final Field f = RingTransit.class.getDeclaredField("surveyed");
        f.setAccessible(true);
        return (Map<String, Long>) f.get(null);
    }

    /**
     * A flat world: stone up to the pads' own level, open air above it.
     *
     * <p>Not simply passable everywhere. A ring needs solid ground directly under every one of
     * its interior columns -- a gap with a floor three blocks further down is still a gap to
     * fall through -- so an all-air world surveys as {@code NO_GROUND} at both ends and no
     * cycle can ever start in it.
     */
    private Block blockAt(final int x, final int y, final int z)
    {
        return blocks.computeIfAbsent(x + "," + y + "," + z, key -> {
            final Block b = mock(Block.class);
            final boolean air = y > GROUND;
            when(b.isPassable()).thenReturn(Boolean.valueOf(air));
            when(b.getType()).thenReturn(air ? Material.AIR : Material.STONE);
            return b;
        });
    }

    /** Builds a pillar in a pad, of the kind somebody puts up without thinking about it. */
    private void buildOver(final int x, final int y, final int z)
    {
        for (int dy = 1; dy <= 6; dy++)
        {
            when(blockAt(x, y + dy, z).isPassable()).thenReturn(Boolean.FALSE);
            when(blockAt(x, y + dy, z).getType()).thenReturn(Material.STONE);
        }
    }

    private static Ring ringAt(final int x, final int y, final int z, final String name)
    {
        final Ring ring = new Ring(x, y, z, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        ring.setName(name);
        return ring;
    }

    private RingPair pair()
    {
        final RingPair pair = new RingPair("aaaa0001", WORLD,
            ringAt(AX, AY, AZ, "here"), ringAt(BX, BY, BZ, "there"));
        RingManager.addPair(pair, 4);
        return pair;
    }

    /** One step into the pad, of the kind that is worth a refusal message. */
    private static boolean walkIn(final RingPair pair, final Player who)
    {
        return RingTransit.start(pair, who, true);
    }

    /** Nothing to fire is not a refusal, it is nothing at all. */
    @Test
    void aPairThatIsNotThereFiresNothing()
    {
        assertFalse(RingTransit.start(null, walker, true));
    }

    /** A clear pair in a loaded world starts, and holds both ends' chunks while it runs. */
    @Test
    void aClearPairStartsAndPinsBothEnds()
    {
        final RingPair pair = pair();

        assertTrue(walkIn(pair, walker), "both ends are clear, so the cycle runs");

        verify(scheduler, atLeastOnce())
            .scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
        for (final Chunk chunk : chunks.values())
        {
            verify(chunk, atLeastOnce()).addPluginChunkTicket(any());
            verify(chunk, never()).removePluginChunkTicket(any());
        }
        assertFalse(chunks.isEmpty(), "no chunks were pinned, so this proved nothing");
    }

    /**
     * A pair already running a cycle is not started a second time.
     *
     * <p>Every block boundary crossed inside a ring reaches here, so a player walking about
     * during their own countdown would restart it under themselves without this.
     */
    @Test
    void aPairAlreadyRunningIsNotStartedAgain()
    {
        final RingPair pair = pair();
        assertTrue(walkIn(pair, walker));

        assertFalse(walkIn(pair, walker), "the cycle already running is left alone");
    }

    /** A pair that is mid-cycle by its own reckoning is not fired either. */
    @Test
    void aPairThatSaysItIsBusyIsNotFired()
    {
        final RingPair pair = pair();
        pair.setPhase(RingPhase.DEPLOY);

        assertFalse(walkIn(pair, walker), "the pair itself says it is not idle");
        verify(scheduler, never()).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
    }

    /**
     * A pair still cooling down is not fired, however many times somebody walks in.
     *
     * <p>The cooldown is what stops a pair being run back to back by somebody stepping in and
     * out of it, and it is read from the same call that decides everything else here.
     */
    @Test
    void aPairStillCoolingDownIsNotFired()
    {
        final RingPair pair = pair();
        pair.setCooldownUntil(System.currentTimeMillis() + 60_000L);

        assertFalse(walkIn(pair, walker), "the pair is not ready to go again yet");
        verify(scheduler, never()).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());

        pair.setCooldownUntil(System.currentTimeMillis() - 1L);
        assertTrue(walkIn(pair, walker), "and goes as soon as it is");
    }

    /** A world that is not loaded has nothing to animate in. */
    @Test
    void aPairInAWorldThatIsNotLoadedFiresNothing()
    {
        final RingPair elsewhere = new RingPair("bbbb0002", "nether",
            ringAt(AX, AY, AZ, "here"), ringAt(BX, BY, BZ, "there"));

        assertFalse(walkIn(elsewhere, walker), "an unloaded world cannot be read or drawn in");
    }

    /**
     * An end that has been built over refuses the cycle, and says which end.
     *
     * <p>Refused before anything moves rather than run and then found to have nowhere to put
     * anybody: a cycle that deploys, flashes and carries nobody looks broken, while being told
     * the far end is blocked points at the thing that actually needs fixing.
     */
    @Test
    void anEndThatHasBeenBuiltOverRefusesTheCycleAndSaysWhich()
    {
        final RingPair pair = pair();
        buildOver(BX, BY, BZ);

        assertFalse(walkIn(pair, walker), "there is nowhere to put an arrival");

        verify(walker, atLeastOnce()).sendMessage(contains("there"));
    }

    /**
     * And says it once, not on every step taken inside the ring.
     *
     * <p>This is the whole reason the survey is remembered. A player standing on a blocked
     * pair crosses block boundaries several times a second, and each one reaches here. Without
     * the cache each would re-read both interiors and send its own chat line.
     */
    @Test
    void theRefusalIsSaidOnceNotOnEveryStepInside()
    {
        final RingPair pair = pair();
        buildOver(BX, BY, BZ);

        walkIn(pair, walker);
        walkIn(pair, walker);
        walkIn(pair, walker);

        verify(walker, times(1)).sendMessage(contains("there"));
    }

    /**
     * Moving about inside a ring says nothing at all.
     *
     * <p>The flag the caller passes when the step did not take them in. The refusal is news
     * when they walk in and not afterwards, so the survey is still consulted -- it just keeps
     * quiet about the answer.
     */
    @Test
    void merelyMovingAboutInsideARingSaysNothing()
    {
        final RingPair pair = pair();
        buildOver(BX, BY, BZ);

        assertFalse(RingTransit.start(pair, walker, false));

        verify(walker, never()).sendMessage(anyString());
    }

    /** Somebody who logged out between the survey and the answer is not written to. */
    @Test
    void aPlayerWhoHasGoneOfflineIsNotToldAnything()
    {
        final RingPair pair = pair();
        buildOver(BX, BY, BZ);
        when(walker.isOnline()).thenReturn(Boolean.FALSE);

        assertFalse(walkIn(pair, walker));

        verify(walker, never()).sendMessage(anyString());
    }

    /**
     * A pair refused for being blocked lets its chunks go again.
     *
     * <p>They are pinned before the survey, because reading a ring's interior means reading
     * its blocks. A refusal that kept them would hold a chunk loaded for a cycle that never
     * ran, and there is nothing left to release it.
     */
    @Test
    void aRefusedPairReleasesTheChunksItPinnedToSurveyItself()
    {
        final RingPair pair = pair();
        buildOver(BX, BY, BZ);

        walkIn(pair, walker);

        assertFalse(chunks.isEmpty(), "no chunks were pinned, so this proved nothing");
        for (final Chunk chunk : chunks.values())
        {
            verify(chunk, atLeastOnce()).addPluginChunkTicket(any());
            verify(chunk, atLeastOnce()).removePluginChunkTicket(any());
        }
    }

    /**
     * A pair refused for being blocked can fire once the way is clear.
     *
     * <p>Deliberately not through {@code RingTransit.clear()}, which empties both the running
     * marks and the remembered surveys at once and so cannot tell a leaked mark from a
     * remembered refusal. Only the survey is aged out here; a running mark left behind by the
     * refusal would refuse this second walk-in, and the pair would never work again.
     */
    @Test
    void aPairRefusedOnceFiresWhenTheWayIsClearAgain() throws Exception
    {
        final RingPair pair = pair();
        buildOver(BX, BY, BZ);
        assertFalse(walkIn(pair, walker));

        blocks.clear();
        ageOutTheRememberedSurvey();

        assertTrue(walkIn(pair, walker), "once the way is clear again the pair fires");
    }

    /**
     * The world is read again once the remembered answer is old enough.
     *
     * <p>The other half of saying it once. An answer trusted forever would leave a pair that
     * somebody has since cleared refusing every walk-in for the rest of the server's life.
     */
    @Test
    void theWorldIsReadAgainOnceTheRememberedAnswerIsStale() throws Exception
    {
        final RingPair pair = pair();
        buildOver(BX, BY, BZ);
        walkIn(pair, walker);
        walkIn(pair, walker);
        verify(walker, times(1)).sendMessage(contains("there"));

        ageOutTheRememberedSurvey();
        walkIn(pair, walker);

        verify(walker, times(2)).sendMessage(contains("there"));
    }

    /**
     * A cycle that is already under way is not started underneath itself.
     *
     * <p>Reached by hand because nothing single-threaded can be between {@code running.add}
     * and the pair's own phase changing. The state is real all the same: it is what a cycle
     * looks like from the moment it claims the pair to the moment it begins counting down,
     * and anything that leaves a mark behind puts a pair here permanently.
     */
    @Test
    void aPairAlreadyClaimedByACycleIsNotStartedUnderneathIt() throws Exception
    {
        final RingPair pair = pair();
        running().add(pair.getId());

        assertFalse(walkIn(pair, walker), "a cycle already has this pair");
        verify(scheduler, never()).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
    }

    /** A refusal leaves no claim on the pair, so the next walk-in can try. */
    @Test
    void aRefusedPairIsNotLeftClaimed() throws Exception
    {
        final RingPair pair = pair();
        buildOver(BX, BY, BZ);

        walkIn(pair, walker);

        assertFalse(running().contains(pair.getId()),
            "no cycle started, so nothing should be holding the pair");
    }

    /**
     * A pair that fires forgets it was ever refused.
     *
     * <p>Housekeeping rather than behaviour: the entry left behind would be stale and refuse
     * nobody. But the map is keyed by pair id and nothing else empties it, so a server that
     * has ever blocked a pad would carry that entry for as long as it runs.
     */
    @Test
    void aPairThatFiresForgetsItWasEverRefused() throws Exception
    {
        final RingPair pair = pair();
        buildOver(BX, BY, BZ);
        walkIn(pair, walker);
        assertTrue(surveyed().containsKey(pair.getId()), "the refusal was remembered");

        blocks.clear();
        ageOutTheRememberedSurvey();
        assertTrue(walkIn(pair, walker));

        assertFalse(surveyed().containsKey(pair.getId()), "and forgotten once it fired");
    }

    /** A pair that fires starts counting down, which is what everything after this reads. */
    @Test
    void aPairThatFiresBeginsItsCountdown()
    {
        final RingPair pair = pair();

        assertTrue(walkIn(pair, walker));

        assertEquals(RingPhase.COUNTDOWN, pair.getPhase(),
            "the cycle has claimed the pair, so nothing else draws over it");
    }

    /**
     * Both ends are heard opening, not just the one somebody walked into.
     *
     * <p>Somebody standing at the far end gets the only warning they are going to get.
     */
    @Test
    void bothEndsAreHeardOpening()
    {
        final RingPair pair = pair();

        assertTrue(walkIn(pair, walker));

        verify(world, times(2)).playSound(any(org.bukkit.Location.class), anyString(),
            any(org.bukkit.SoundCategory.class), anyFloat(), anyFloat());
    }
}
