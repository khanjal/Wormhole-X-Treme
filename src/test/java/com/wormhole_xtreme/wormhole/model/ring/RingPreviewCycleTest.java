package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.HeldScheduler;
import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * {@code ring preview cycle} (#332): the whole cycle on one end, for one viewer, with the real
 * pauses and nobody carried.
 *
 * <p>Every pause has its own length here, so a cycle that waited on the wrong setting between two
 * phases, or in the wrong order, shows in the list of delays it queued. The drawing is a fake
 * that records what the viewer would be looking at, and every step is checked against what is
 * <em>showing</em>. The scheduler is {@link HeldScheduler}, which honours cancel and counts what
 * is pending, since "nothing is drawn after an interruption" is a claim about exactly that.
 */
class RingPreviewCycleTest
{
    private static final int AX = 100, AY = 64, AZ = 100;
    private static final int DEPLOY_TICKS = 3;
    private static final int FLASH_TICKS = 2;
    private static final int SETTLE_TICKS = 7;
    private static final int HOLD_TICKS = 11;
    private static final int LINGER_TICKS = 13;
    private static final String SOUND_OPEN = "test.open";
    private static final String SOUND_RING = "test.ring";
    private static final String SOUND_FLASH = "test.flash";
    private static final String SOUND_CLOSE = "test.close";
    private static final Material PAD_LIGHT = Material.SEA_LANTERN;
    private static final Material FLASH = Material.GOLD_BLOCK;

    private final HeldScheduler held = new HeldScheduler();
    private MockedStatic<ConfigManager> config;
    private Player viewer;
    private Drawing drawing;
    private Ring ring;
    private RingPair pair;
    private RingPreview preview;

    /** Records what the viewer is looking at, and everything it was ever sent. */
    private static final class Drawing implements RingCycle.Surroundings
    {
        final Map<Long, String> shown = new HashMap<>();
        final List<String> history = new ArrayList<>();
        RingBlockage found;

        @Override
        public void showBlock(final int x, final int y, final int z, final Material material)
        {
            history.add("block " + x + "," + y + "," + z);
            shown.put(RingIndex.pack(x, y, z), "block:" + material);
        }

        @Override
        public void showSlab(final int x, final int y, final int z, final Material material, final boolean top)
        {
            history.add("slab " + x + "," + y + "," + z + " " + material);
            shown.put(RingIndex.pack(x, y, z), "slab:" + material + ":" + (top ? "top" : "bottom"));
        }

        @Override
        public void reveal(final int x, final int y, final int z)
        {
            history.add("reveal " + x + "," + y + "," + z);
            shown.remove(RingIndex.pack(x, y, z));
        }

        @Override
        public List<RingPassenger> passengersIn(final List<int[]> blocks)
        {
            throw new AssertionError("a cycle preview never looks for passengers");
        }

        @Override
        public boolean mayTravel(final RingPassenger passenger, final Ring from, final Ring to)
        {
            throw new AssertionError("a cycle preview never carries anybody");
        }

        @Override
        public RingBlockage survey(final Ring measured)
        {
            return found;
        }

        @Override
        public void deliver(final RingPassenger passenger, final Ring destination)
        {
            throw new AssertionError("a cycle preview never delivers anybody");
        }
    }

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        held.install();

        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingDeployTicks).thenReturn(Integer.valueOf(DEPLOY_TICKS));
        config.when(ConfigManager::getRingFlashTicks).thenReturn(Integer.valueOf(FLASH_TICKS));
        config.when(ConfigManager::getRingSettleTicks).thenReturn(Integer.valueOf(SETTLE_TICKS));
        config.when(ConfigManager::getRingHoldTicks).thenReturn(Integer.valueOf(HOLD_TICKS));
        config.when(ConfigManager::getRingLightsLingerTicks).thenReturn(Integer.valueOf(LINGER_TICKS));
        config.when(ConfigManager::isRingSoundsEnabled).thenReturn(Boolean.TRUE);
        config.when(ConfigManager::getRingSoundVolume).thenReturn(Float.valueOf(1.0f));
        config.when(ConfigManager::getRingSoundOpen).thenReturn(SOUND_OPEN);
        config.when(ConfigManager::getRingSoundRing).thenReturn(SOUND_RING);
        config.when(ConfigManager::getRingSoundFlash).thenReturn(SOUND_FLASH);
        config.when(ConfigManager::getRingSoundClose).thenReturn(SOUND_CLOSE);

        viewer = mock(Player.class);
        newPreview();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        config.close();
        held.remove();
        PluginTestSupport.remove();
    }

    /** A fresh preview over a fresh drawing, for a test that runs the cycle more than once. */
    private void newPreview()
    {
        drawing = new Drawing();
        ring = ringAt(AX, AY, AZ, RingStyle.SEQUENTIAL);
        pair = new RingPair("aaaa0001", "world", ring, ringAt(200, AY, 200, RingStyle.SEQUENTIAL));
        preview = new RingPreview(viewer, pair, ring, drawing);
    }

    private static Ring ringAt(final int x, final int y, final int z, final RingStyle style)
    {
        final Ring made = new Ring(x, y, z, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB, PAD_LIGHT);
        made.setFlashMaterial(FLASH);
        made.setStyle(style);
        return made;
    }

    /** What the viewer should see of the lit pad. */
    private Map<Long, String> pad()
    {
        final Map<Long, String> out = new HashMap<>();
        for (final int[] block : RingAnimator.openedBlocks(ring))
        {
            out.put(RingIndex.pack(block[0], block[1], block[2]), "block:" + RingAnimator.OPENED_MATERIAL);
        }
        for (final int[] block : RingAnimator.lightBlocks(ring))
        {
            out.put(RingIndex.pack(block[0], block[1], block[2]), "block:" + PAD_LIGHT);
        }
        return out;
    }

    private Map<Long, String> padWith(final List<RingAnimator.Placement> placements, final Material material)
    {
        final Map<Long, String> out = pad();
        add(out, placements, material);
        return out;
    }

    private static void add(final Map<Long, String> out, final List<RingAnimator.Placement> placements,
        final Material material)
    {
        for (final RingAnimator.Placement placement : placements)
        {
            out.put(RingIndex.pack(placement.getX(), placement.getY(), placement.getZ()),
                "slab:" + material + ":" + (placement.isTop() ? "top" : "bottom"));
        }
    }

    /** The pauses a whole cycle queues, in order, for a stack of this many frames. */
    private static List<Long> pauses(final int frames)
    {
        final List<Long> out = new ArrayList<>();
        for (int i = 1; i < frames; i++)
        {
            out.add((long) DEPLOY_TICKS);
        }
        out.add((long) SETTLE_TICKS);
        for (int i = 0; i < RingAnimator.flashFrames(); i++)
        {
            out.add((long) FLASH_TICKS);
        }
        out.add((long) HOLD_TICKS);
        for (int i = 0; i < frames; i++)
        {
            out.add((long) DEPLOY_TICKS);
        }
        out.add((long) LINGER_TICKS);
        return out;
    }

    /** Runs the one waiting step, which must be the only one, and says how long it waited. */
    private long runOne()
    {
        assertEquals(1, held.pending(), "a cycle has exactly one step waiting");
        final long waited = held.delays().get(0);
        held.runNext();
        return waited;
    }

    /** Runs the cycle to its end, returning each pause it waited. */
    private List<Long> runToTheEnd()
    {
        final List<Long> seen = new ArrayList<>();
        while (held.pending() > 0)
        {
            seen.add(runOne());
        }
        return seen;
    }

    private void verifySound(final String sound, final int times)
    {
        verify(viewer, times(times)).playSound(any(Location.class), eq(sound), eq(SoundCategory.BLOCKS),
            anyFloat(), anyFloat());
    }

    /** Runs this many waiting steps, one at a time, noting how long each waited. */
    private void runSteps(final int count, final List<Long> seen)
    {
        for (int i = 0; i < count; i++)
        {
            seen.add(runOne());
        }
    }

    /** The stack as the cycle leaves it standing between the rise and the light. */
    private Map<Long, String> settledStack()
    {
        return padWith(RingAnimator.settledStack(ring, RingStyle.SEQUENTIAL), Material.STONE_SLAB);
    }

    /**
     * The first phase: the first ring leaves as the command runs, the rest rise at the deploy
     * pace, and the stack settles for the real settle pause with no other step able to cut in.
     */
    @Test
    void theCycleRaisesTheStackAndSettlesBeforeTheLight()
    {
        preview.show();
        final int frames = RingAnimator.deployFrames(ring, RingStyle.SEQUENTIAL);
        assertTrue(frames > 2, "a stack that rises in one frame would prove nothing about its pauses");

        assertEquals(RingPreview.Result.DONE, preview.cycle());

        assertEquals(padWith(RingAnimator.deployFrame(ring, RingStyle.SEQUENTIAL, 0), Material.STONE_SLAB),
            drawing.shown, "the first ring leaves as the command runs");
        runSteps(frames - 1, new ArrayList<>());
        final Map<Long, String> settled = settledStack();
        assertEquals(settled, drawing.shown, "the stack is up and still");
        assertTrue(preview.isCycling(), "and the cycle has not finished with it");

        assertEquals(List.of((long) SETTLE_TICKS), held.delays(), "the stack settles before the light");
        assertEquals(RingPreview.Result.BUSY, preview.light(), "no other step can cut into the settle");
        assertEquals(RingPreview.Result.BUSY, preview.collapse());
        assertEquals(settled, drawing.shown);
    }

    /**
     * The middle phase: the one sweep of transport light, as for a trip with passengers, at the
     * flash pace, then the stack holds for the real hold pause.
     */
    @Test
    void theCycleSweepsTheLightOnceThenHoldsTheStack()
    {
        preview.show();
        preview.cycle();
        final List<Long> seen = new ArrayList<>();
        runSteps(RingAnimator.deployFrames(ring, RingStyle.SEQUENTIAL) - 1, seen);
        runSteps(1, seen);

        final Map<Long, String> lit = new HashMap<>(settledStack());
        add(lit, RingAnimator.ringAtRest(ring, RingAnimator.litRing(0, false)), FLASH);
        assertEquals(lit, drawing.shown, "the transport light is on the first ring, as for a trip with passengers");
        assertEquals(RingPreview.State.LIGHTING, preview.getState());
        runSteps(RingAnimator.flashFrames(), seen);
        assertEquals(settledStack(), drawing.shown, "the light has gone and the stack stands");

        assertEquals(List.of((long) HOLD_TICKS), held.delays(), "the stack holds before it comes home");
    }

    /**
     * The last phase: the rings come home at the deploy pace, the pad stays lit through the
     * linger with nothing able to start in it, and the cycle then ends.
     */
    @Test
    void theCycleBringsTheRingsHomeAndLetsTheLightsLinger()
    {
        preview.show();
        preview.cycle();
        final List<Long> seen = new ArrayList<>();
        final int frames = RingAnimator.deployFrames(ring, RingStyle.SEQUENTIAL);
        runSteps(frames - 1, seen);
        runSteps(1 + RingAnimator.flashFrames(), seen);
        runSteps(1, seen);

        assertEquals(padWith(RingAnimator.retractFrame(ring, RingStyle.SEQUENTIAL, 0), Material.STONE_SLAB),
            drawing.shown, "the rings start home");
        runSteps(frames, seen);

        assertEquals(pad(), drawing.shown, "no ring left and the pad is still lit");
        assertEquals(RingPreview.State.LIT, preview.getState());
        assertTrue(preview.isCycling(), "the lights linger first");
        assertEquals(RingPreview.Result.BUSY, preview.deploy(), "and nothing starts in that pause");
        assertEquals(List.of((long) LINGER_TICKS), held.delays());
        runSteps(1, seen);
        assertFalse(preview.isCycling());
    }

    /** Every pause of the whole cycle is the setting the real cycle waits on, in its place. */
    @Test
    void theWholeCycleWaitsTheRealPausesInOrderAndEndsOnTheLitPad()
    {
        preview.show();
        final int frames = RingAnimator.deployFrames(ring, RingStyle.SEQUENTIAL);
        preview.cycle();

        final List<Long> seen = runToTheEnd();

        assertEquals(pauses(frames), seen, "every pause is the setting the real cycle waits on, in its place");
        assertEquals(pad(), drawing.shown);
        assertFalse(preview.isCycling());
        assertEquals(0, held.pending());
    }

    /** The settings are read when each pause is taken, so a change made during a cycle is honoured. */
    @Test
    void aPauseIsReadWhenItIsTakenNotWhenTheCycleStarts()
    {
        preview.show();
        preview.cycle();
        config.when(ConfigManager::getRingSettleTicks).thenReturn(Integer.valueOf(SETTLE_TICKS + 1));
        config.when(ConfigManager::getRingHoldTicks).thenReturn(Integer.valueOf(HOLD_TICKS + 1));
        config.when(ConfigManager::getRingLightsLingerTicks).thenReturn(Integer.valueOf(LINGER_TICKS + 1));

        final List<Long> seen = runToTheEnd();

        assertTrue(seen.contains((long) (SETTLE_TICKS + 1)));
        assertTrue(seen.contains((long) (HOLD_TICKS + 1)));
        assertTrue(seen.contains((long) (LINGER_TICKS + 1)));
        assertFalse(seen.contains((long) SETTLE_TICKS));
    }

    /** What is tried on the drawing is what the cycle shows, and the style decides how long it takes. */
    @Test
    void trialValuesAreUsedByTheWholeCycle()
    {
        preview.show();
        preview.tryRingMaterial(Material.OAK_SLAB);
        preview.tryFlashMaterial(Material.EMERALD_BLOCK);
        preview.tryStyle(RingStyle.CONCURRENT);
        final int sequential = RingAnimator.deployFrames(ring, RingStyle.SEQUENTIAL);
        final int concurrent = RingAnimator.deployFrames(ring, RingStyle.CONCURRENT);
        assertNotEquals(sequential, concurrent, "the two styles must differ or the trial style proves nothing");
        drawing.history.clear();

        assertEquals(RingPreview.Result.DONE, preview.cycle());
        final List<Long> seen = runToTheEnd();

        assertEquals(pauses(concurrent), seen, "the trial style sets the number of frames");
        assertTrue(drawing.history.stream().anyMatch(call -> call.endsWith("OAK_SLAB")));
        assertTrue(drawing.history.stream().anyMatch(call -> call.endsWith("EMERALD_BLOCK")),
            "the light is the trial flash");
        assertTrue(drawing.history.stream().noneMatch(call -> call.endsWith("STONE_SLAB") || call.endsWith("GOLD_BLOCK")),
            "nothing is drawn in the stored materials");
        assertEquals(Material.STONE_SLAB, ring.getRingMaterial(), "and the stored ring is as it was");
        assertEquals(RingStyle.SEQUENTIAL, ring.getStyle());
    }

    /** A cycle starts from the lit pad: with a stack up it says to collapse, and draws nothing. */
    @Test
    void aCycleWithAStackStandingIsRefusedAndChangesNothing()
    {
        preview.show();
        preview.deploy();
        held.runAll();
        final Map<Long, String> standing = new HashMap<>(drawing.shown);
        final int sent = drawing.history.size();

        assertEquals(RingPreview.Result.CYCLE_STANDING, preview.cycle());

        assertEquals(standing, drawing.shown);
        assertEquals(sent, drawing.history.size(), "nothing was drawn");
        assertEquals(0, held.pending(), "and nothing was queued");
        assertFalse(preview.isCycling());
        assertEquals(RingPreview.State.DEPLOYED, preview.getState());
    }

    /** Asked again while it runs, a cycle is busy rather than starting a second sequence. */
    @Test
    void aCycleAskedWhileOneIsRunningIsBusy()
    {
        preview.show();
        preview.cycle();
        held.runNext();
        final int queued = held.pending();
        final int sent = drawing.history.size();

        assertEquals(RingPreview.Result.BUSY, preview.cycle());

        assertEquals(queued, held.pending(), "still one step waiting");
        assertEquals(sent, drawing.history.size());
    }

    /** A ceiling ring with nowhere to drop to cannot cycle, and is not left cycling. */
    @Test
    void aCycleThatFindsNoFloorSaysSoAndIsNotLeftRunning()
    {
        final Drawing noFloor = new Drawing();
        noFloor.found = RingBlockage.CEILING_TOO_HIGH;
        final RingPreview ceiling = new RingPreview(viewer, pair, ring, noFloor);
        ceiling.show();

        assertEquals(RingPreview.Result.NO_FLOOR, ceiling.cycle());

        assertFalse(ceiling.isCycling());
        assertEquals(0, held.pending());
        assertEquals(RingPreview.Result.NO_FLOOR, ceiling.deploy(), "so a step can be asked again");
    }

    /**
     * Clearing at every pause of the cycle, and at the first frame of each phase, takes all of it
     * back and stops it: nothing is drawn afterwards, not even by a step that was already queued.
     */
    @Test
    void clearingAtAnyPointStopsTheCycleWithNothingDrawnAfter()
    {
        final int total = pauses(RingAnimator.deployFrames(ring, RingStyle.SEQUENTIAL)).size();
        int scheduled = 0;
        for (int ran = 0; ran < total; ran++)
        {
            newPreview();
            preview.show();
            preview.cycle();
            for (int i = 0; i < ran; i++)
            {
                held.runNext();
            }

            preview.clear();

            final String at = "after " + ran + " of " + total + " steps";
            assertTrue(drawing.shown.isEmpty(), "everything is taken back " + at);
            assertFalse(preview.isCycling(), at);
            assertEquals(0, held.pending(), "the waiting step was cancelled " + at);
            final int sent = drawing.history.size();
            held.runEvenIfCancelled(scheduled + ran);
            assertEquals(sent, drawing.history.size(), "a step that was already on its way draws nothing " + at);
            assertTrue(drawing.shown.isEmpty(), at);
            scheduled += ran + 1;
        }
    }

    /** A cycle that has finished leaves a preview that works as before: lit, and ready for a step. */
    @Test
    void afterTheCycleThePreviewTakesTheNextStepAsNormal()
    {
        preview.show();
        preview.cycle();
        runToTheEnd();

        assertEquals(RingPreview.Result.DONE, preview.deploy());
        held.runAll();
        assertEquals(RingPreview.State.DEPLOYED, preview.getState());
        assertEquals(RingPreview.Result.DONE, preview.collapse());
        held.runAll();
        assertEquals(RingPreview.Result.DONE, preview.cycle(), "and a cycle can run again");
        runToTheEnd();
        assertEquals(pad(), drawing.shown);
    }

    /**
     * The sounds go to the viewer, and where the real cycle plays them: the opening when the
     * pad lights, a ring as each starts to move, the transport sound as the light starts, and
     * the closing as the rings reach home, before the lights linger.
     */
    @Test
    void theSoundsPlayToTheViewerWhereTheRealCycleMakesThem()
    {
        preview.show();
        verifySound(SOUND_OPEN, 1);
        preview.cycle();
        for (int i = 1; i < RingAnimator.deployFrames(ring, RingStyle.SEQUENTIAL); i++)
        {
            held.runNext();
        }
        verifySound(SOUND_FLASH, 0);
        verifySound(SOUND_CLOSE, 0);

        held.runNext();
        verifySound(SOUND_FLASH, 1);

        while (held.delays().get(0) != HOLD_TICKS)
        {
            held.runNext();
        }
        verifySound(SOUND_FLASH, 1);
        verifySound(SOUND_CLOSE, 0);

        held.runNext();
        while (held.delays().get(0) != LINGER_TICKS)
        {
            verifySound(SOUND_CLOSE, 0);
            held.runNext();
        }
        verifySound(SOUND_CLOSE, 1);
        held.runNext();

        verifySound(SOUND_OPEN, 1);
        verifySound(SOUND_FLASH, 1);
        verifySound(SOUND_CLOSE, 1);
        verify(viewer, times(RingAnimator.RING_COUNT * 2)).playSound(any(Location.class), eq(SOUND_RING),
            eq(SoundCategory.BLOCKS), anyFloat(), anyFloat());
    }

    /** With the closing sound unset, the cycle is silent at that point and carries on. */
    @Test
    void anEmptyClosingSoundPlaysNothingAndTheCycleStillEnds()
    {
        config.when(ConfigManager::getRingSoundClose).thenReturn("");
        preview.show();
        preview.cycle();

        runToTheEnd();

        verify(viewer, never()).playSound(any(Location.class), eq(SOUND_CLOSE), eq(SoundCategory.BLOCKS),
            anyFloat(), anyFloat());
        assertFalse(preview.isCycling());
    }
}
