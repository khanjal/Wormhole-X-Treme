package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.HeldScheduler;
import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * The paused preview of one built ring (#332): its steps, what each leaves standing, and what
 * it refuses.
 *
 * <p>The drawing is a fake that records what the viewer would be looking at, position by
 * position, so each step is checked against what is <em>showing</em> rather than against the
 * calls that put it there. Frames are the ones {@link RingAnimator} computes for the real
 * cycle; what is under test is that the preview steps through them, holds between phases,
 * and never has more than one step waiting.
 *
 * <p>The scheduler is {@link HeldScheduler}: it honours cancel and counts what is pending,
 * which a bare mock does not, and "a stack is never redrawn after it was taken away" is a
 * claim about exactly that.
 */
class RingPreviewTest
{
    private static final int AX = 100, AY = 64, AZ = 100;
    private static final int DEPLOY_TICKS = 3;
    private static final int FLASH_TICKS = 2;
    private static final String SOUND_OPEN = "test.open";
    private static final String SOUND_RING = "test.ring";
    private static final String SOUND_FLASH = "test.flash";
    private static final Material PAD_LIGHT = Material.SEA_LANTERN;
    private static final Material FLASH = Material.GOLD_BLOCK;

    private final HeldScheduler held = new HeldScheduler();
    private MockedStatic<ConfigManager> config;
    private Player viewer;
    private Drawing drawing;
    private Ring ring;
    private RingPair pair;
    private RingPreview preview;

    /** Records what the viewer is looking at, position by position. */
    private static final class Drawing implements RingCycle.Surroundings
    {
        final Map<Long, String> shown = new HashMap<>();
        final List<Long> revealed = new ArrayList<>();
        RingBlockage found;
        int surveys;
        boolean failOnSlab;
        java.util.function.Consumer<Ring> onSurvey = measured -> { };

        @Override
        public void showBlock(final int x, final int y, final int z, final Material material)
        {
            shown.put(RingIndex.pack(x, y, z), "block:" + material);
        }

        @Override
        public void showSlab(final int x, final int y, final int z, final Material material, final boolean top)
        {
            if (failOnSlab)
            {
                throw new IllegalStateException("the client went away");
            }
            shown.put(RingIndex.pack(x, y, z), "slab:" + material + ":" + (top ? "top" : "bottom"));
        }

        @Override
        public void reveal(final int x, final int y, final int z)
        {
            shown.remove(RingIndex.pack(x, y, z));
            revealed.add(RingIndex.pack(x, y, z));
        }

        @Override
        public List<RingPassenger> passengersIn(final List<int[]> blocks)
        {
            throw new AssertionError("a preview never looks for passengers");
        }

        @Override
        public boolean mayTravel(final RingPassenger passenger, final Ring from, final Ring to)
        {
            throw new AssertionError("a preview never carries anybody");
        }

        @Override
        public RingBlockage survey(final Ring measured)
        {
            surveys++;
            onSurvey.accept(measured);
            return found;
        }

        @Override
        public void deliver(final RingPassenger passenger, final Ring destination)
        {
            throw new AssertionError("a preview never delivers anybody");
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
        config.when(ConfigManager::isRingSoundsEnabled).thenReturn(Boolean.TRUE);
        config.when(ConfigManager::getRingSoundVolume).thenReturn(Float.valueOf(1.0f));
        config.when(ConfigManager::getRingSoundOpen).thenReturn(SOUND_OPEN);
        config.when(ConfigManager::getRingSoundRing).thenReturn(SOUND_RING);
        config.when(ConfigManager::getRingSoundFlash).thenReturn(SOUND_FLASH);

        viewer = mock(Player.class);
        drawing = new Drawing();
        ring = ringAt(AX, AY, AZ, RingOrientation.FLOOR, RingStyle.SEQUENTIAL);
        pair = new RingPair("aaaa0001", "world", ring,
            ringAt(200, AY, 200, RingOrientation.FLOOR, RingStyle.SEQUENTIAL));
        preview = new RingPreview(viewer, pair, ring, drawing);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        config.close();
        held.remove();
        PluginTestSupport.remove();
    }

    private static Ring ringAt(final int x, final int y, final int z, final RingOrientation orientation,
        final RingStyle style)
    {
        final Ring made = new Ring(x, y, z, RingPattern.ODD, orientation, Material.STONE_SLAB, PAD_LIGHT);
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

    /** What the viewer should see of a set of slabs, over the pad. */
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

    /** Raises the stack and lets every frame draw. */
    private void standTheStack()
    {
        preview.show();
        assertEquals(RingPreview.Result.DONE, preview.deploy());
        held.runAll();
        assertEquals(RingPreview.State.DEPLOYED, preview.getState());
    }

    /** The pitches played on {@link #SOUND_RING}, in order. */
    private List<Float> ringPitches()
    {
        final ArgumentCaptor<Float> pitch = ArgumentCaptor.forClass(Float.class);
        verify(viewer, atLeast(0)).playSound(any(Location.class), eq(SOUND_RING),
            eq(SoundCategory.BLOCKS), anyFloat(), pitch.capture());
        return pitch.getAllValues();
    }

    /** Lighting the pad draws what the countdown draws, in this end's materials. */
    @Test
    void lightingThePadShowsTheOpenedPartsAndTheLightMaterialAndNothingElse()
    {
        preview.show();

        assertEquals(pad(), drawing.shown, "the pad as the countdown lights it, in the end's own light");
        assertEquals(RingPreview.State.LIT, preview.getState());
        assertEquals(0, held.pending(), "lighting the pad queues nothing");
        verify(viewer, times(1)).playSound(any(Location.class), eq(SOUND_OPEN), eq(SoundCategory.BLOCKS),
            anyFloat(), anyFloat());
    }

    /**
     * The first frame is drawn at once and the rest one step apart, at the server's own
     * pace, with exactly one step waiting at any time.
     */
    @Test
    void deployDrawsTheFirstFrameNowAndTheRestAtTheConfiguredPace()
    {
        preview.show();
        final int frames = RingAnimator.deployFrames(ring, RingStyle.SEQUENTIAL);
        assertTrue(frames > 2, "a stack that rises in one frame would prove nothing about pacing");

        assertEquals(RingPreview.Result.DONE, preview.deploy());

        assertEquals(RingPreview.State.DEPLOYING, preview.getState());
        assertEquals(padWith(RingAnimator.deployFrame(ring, RingStyle.SEQUENTIAL, 0), Material.STONE_SLAB),
            drawing.shown, "frame zero is on screen as soon as the command runs");
        for (int frame = 1; frame < frames; frame++)
        {
            assertEquals(List.of((long) DEPLOY_TICKS), held.delays(),
                "one step is waiting, a deploy tick away");
            held.runNext();
            assertEquals(padWith(RingAnimator.deployFrame(ring, RingStyle.SEQUENTIAL, frame), Material.STONE_SLAB),
                drawing.shown, "frame " + frame);
        }
        assertEquals(0, held.pending(), "nothing is waiting once the stack is up");
    }

    /** A stack that has come up stays up: nothing is scheduled to take it back. */
    @Test
    void theStackIsLeftStandingWithNothingPendingToTakeItDown()
    {
        standTheStack();

        assertEquals(0, held.pending(), "no timer, no retract: it stands until asked");
        assertEquals(padWith(RingAnimator.settledStack(ring, RingStyle.SEQUENTIAL), Material.STONE_SLAB),
            drawing.shown);
    }

    /** Slow and fast are different drawings: the end's own style decides how long it takes. */
    @Test
    void theEndsStyleDecidesHowManyFramesTheStackTakes()
    {
        final Ring fast = ringAt(AX, AY, AZ, RingOrientation.FLOOR, RingStyle.CONCURRENT);
        assertNotEquals(RingAnimator.deployFrames(ring, RingStyle.SEQUENTIAL),
            RingAnimator.deployFrames(fast, RingStyle.CONCURRENT), "the two styles must differ for this to mean anything");

        standTheStack();
        final int slow = RingAnimator.deployFrames(ring, RingStyle.SEQUENTIAL);
        final RingPreview quick = new RingPreview(viewer, pair, fast, new Drawing());
        quick.show();
        quick.deploy();
        final int steps = held.runAll();

        assertEquals(RingAnimator.deployFrames(fast, RingStyle.CONCURRENT) - 1, steps);
        assertNotEquals(slow - 1, steps);
    }

    /** Asking for a second stack while one stands says so and draws nothing. */
    @Test
    void deployWithAStackStandingSaysSoAndChangesNothing()
    {
        standTheStack();
        final Map<Long, String> before = new HashMap<>(drawing.shown);

        assertEquals(RingPreview.Result.ALREADY_STANDING, preview.deploy());

        assertEquals(before, drawing.shown);
        assertEquals(0, held.pending());
        assertEquals(RingPreview.State.DEPLOYED, preview.getState());
    }

    /** No step may cut across one that is still moving. */
    @Test
    void everyStepIsRefusedWhileTheRingsAreMoving()
    {
        preview.show();
        preview.deploy();

        assertEquals(RingPreview.Result.BUSY, preview.deploy());
        assertEquals(RingPreview.Result.BUSY, preview.light());
        assertEquals(RingPreview.Result.BUSY, preview.collapse());
        assertEquals(1, held.pending(), "still exactly the one step that was already waiting");
        assertEquals(RingPreview.State.DEPLOYING, preview.getState());
    }

    /** The light needs a stack to run through, and says so when there is none. */
    @Test
    void lightWithNoStackSaysSoAndDrawsNothing()
    {
        preview.show();
        final Map<Long, String> before = new HashMap<>(drawing.shown);

        assertEquals(RingPreview.Result.NO_STACK, preview.light());

        assertEquals(before, drawing.shown);
        assertEquals(0, held.pending());
        assertEquals(RingPreview.State.LIT, preview.getState());
        verify(viewer, never()).playSound(any(Location.class), eq(SOUND_FLASH), any(SoundCategory.class),
            anyFloat(), anyFloat());
    }

    /**
     * The light runs furthest ring first, towards the pad, in the flash material, and the
     * plain stack comes back afterwards. It can be run again.
     */
    @Test
    void lightRunsThroughTheStackTowardsThePadInTheFlashMaterialAndCanBeRunAgain()
    {
        standTheStack();
        final int furthest = RingAnimator.ringAtRest(ring, 0).get(0).getY();
        final int nearest = RingAnimator.ringAtRest(ring, RingAnimator.RING_COUNT - 1).get(0).getY();
        assertTrue(Math.abs(furthest - ring.getAnchorY()) > Math.abs(nearest - ring.getAnchorY()),
            "ring 0 is the one furthest from the pad on a floor ring");

        for (int pass = 0; pass < 2; pass++)
        {
            assertEquals(RingPreview.Result.DONE, preview.light(), "pass " + pass);
            assertEquals(RingPreview.State.LIGHTING, preview.getState());
            for (int step = 0; step < RingAnimator.flashFrames(); step++)
            {
                final Map<Long, String> expected = padWith(RingAnimator.settledStack(ring, RingStyle.SEQUENTIAL),
                    Material.STONE_SLAB);
                add(expected, RingAnimator.ringAtRest(ring, step), FLASH);
                assertEquals(expected, drawing.shown, "step " + step + " lights ring " + step);
                assertEquals(List.of((long) FLASH_TICKS), held.delays());
                held.runNext();
            }
            assertEquals(padWith(RingAnimator.settledStack(ring, RingStyle.SEQUENTIAL), Material.STONE_SLAB),
                drawing.shown, "the plain stack is back once the light has gone");
            assertEquals(RingPreview.State.DEPLOYED, preview.getState());
            assertEquals(0, held.pending());
        }
        verify(viewer, times(2)).playSound(any(Location.class), eq(SOUND_FLASH), eq(SoundCategory.BLOCKS),
            anyFloat(), anyFloat());
    }

    /** There is nothing to bring home before a stack is up. */
    @Test
    void collapseWithNoStackSaysSoAndDrawsNothing()
    {
        preview.show();

        assertEquals(RingPreview.Result.NO_STACK, preview.collapse());

        assertEquals(pad(), drawing.shown);
        assertEquals(0, held.pending());
        assertEquals(RingPreview.State.LIT, preview.getState());
    }

    /**
     * The rings come home as the deploy played backwards, and the pad stays lit afterwards.
     */
    @Test
    void collapseBringsTheRingsHomeFrameByFrameAndLeavesThePadLit()
    {
        standTheStack();
        final int frames = RingAnimator.deployFrames(ring, RingStyle.SEQUENTIAL);

        assertEquals(RingPreview.Result.DONE, preview.collapse());

        assertEquals(RingPreview.State.COLLAPSING, preview.getState());
        for (int frame = 0; frame < frames; frame++)
        {
            assertEquals(padWith(RingAnimator.retractFrame(ring, RingStyle.SEQUENTIAL, frame), Material.STONE_SLAB),
                drawing.shown, "retract frame " + frame);
            assertEquals(List.of((long) DEPLOY_TICKS), held.delays());
            held.runNext();
        }
        assertEquals(RingPreview.State.LIT, preview.getState());
        assertEquals(pad(), drawing.shown, "no ring left, and the pad is still lit");
        assertEquals(0, held.pending());
    }

    /** Each ring sounds once on the way out, rising, and once on the way home, falling. */
    @Test
    void theRingsSoundToTheViewerAloneClimbingOutAndFallingBack()
    {
        preview.show();
        preview.deploy();
        held.runAll();
        final List<Float> out = new ArrayList<>(ringPitches());
        assertEquals(RingAnimator.RING_COUNT, out.size(), "one sound per ring, not one per frame");
        for (int i = 1; i < out.size(); i++)
        {
            assertTrue(out.get(i) > out.get(i - 1), "climbing as the stack rises");
        }

        preview.collapse();
        held.runAll();
        final List<Float> all = ringPitches();
        assertEquals(RingAnimator.RING_COUNT * 2, all.size());
        final List<Float> back = all.subList(RingAnimator.RING_COUNT, all.size());
        for (int i = 1; i < back.size(); i++)
        {
            assertTrue(back.get(i) < back.get(i - 1), "falling as they come home");
        }
    }

    /** With sounds switched off for the server, a preview is silent too. */
    @Test
    void aServerWithRingSoundsOffGetsASilentPreview()
    {
        config.when(ConfigManager::isRingSoundsEnabled).thenReturn(Boolean.FALSE);

        preview.show();
        preview.deploy();
        held.runAll();

        verify(viewer, never()).playSound(any(Location.class), any(String.class), any(SoundCategory.class),
            anyFloat(), anyFloat());
    }

    /** Clearing mid-rise shows the viewer the real block everywhere and leaves no step waiting. */
    @Test
    void clearingTakesEverythingBackAndCancelsTheStepThatWasWaiting()
    {
        preview.show();
        preview.deploy();
        assertEquals(1, held.pending());

        preview.clear();

        assertTrue(drawing.shown.isEmpty(), "nothing of the drawing is left on the viewer's screen");
        assertEquals(0, held.pending(), "the waiting step was cancelled");
        assertEquals(1, held.cancelled());
        assertTrue(drawing.revealed.containsAll(pad().keySet()), "the pad was handed back as well as the rings");
    }

    /**
     * A step that was already queued when the preview was cleared still runs, as a main-thread
     * task does when it is cancelled too late, and must not put the stack back.
     */
    @Test
    void aStepThatRacesTheClearDrawsNothing()
    {
        preview.show();
        preview.deploy();
        preview.clear();

        held.runEvenIfCancelled(0);

        assertTrue(drawing.shown.isEmpty(), "the stack must not come back after it was taken away");
    }

    /** A ceiling ring is measured first, so its stack forms on the floor it found. */
    @Test
    void aCeilingRingsStackDropsToTheFloorTheSurveyFound()
    {
        final Ring ceiling = ringAt(AX, 70, AZ, RingOrientation.CEILING, RingStyle.CONCURRENT);
        final int floor = Ring.MIN_CEILING_DROP + 2;
        drawing.onSurvey = measured -> measured.setDrop(floor);
        final RingPreview hanging = new RingPreview(viewer, pair, ceiling, drawing);
        hanging.show();

        assertEquals(RingPreview.Result.DONE, hanging.deploy());
        held.runAll();

        assertEquals(1, drawing.surveys, "measured once, before anything rose");
        final int base = 70 - floor;
        final Set<Integer> heights = new HashSet<>();
        drawing.shown.forEach((key, what) ->
        {
            if (what.startsWith("slab:"))
            {
                heights.add(RingIndex.unpackY(key.longValue()));
            }
        });
        assertFalse(heights.isEmpty(), "the stack is standing");
        assertEquals(Ring.MIN_CEILING_DROP, ceiling.getDrop(),
            "the drop the survey found stays on the drawing: the stored ring is not written");
        assertTrue(heights.stream().allMatch(y -> (y >= base) && (y < 70)),
            "every ring stands between the floor and the plane, not above the ceiling pad: " + heights);
        assertEquals(RingPreview.State.DEPLOYED, hanging.getState());
    }

    /**
     * The coming-home sounds follow the style the stack was raised in, as the frames do.
     *
     * <p>They were read from the stored ring, so a style changed while the stack stood played
     * them on frames the retract never reaches.
     */
    @Test
    void theComingHomeSoundsFollowTheStyleTheStackWasRaisedIn()
    {
        final Ring fast = ringAt(AX, AY, AZ, RingOrientation.FLOOR, RingStyle.CONCURRENT);
        final RingPreview quick = new RingPreview(viewer, pair, fast, drawing);
        quick.show();
        quick.deploy();
        held.runAll();
        fast.setStyle(RingStyle.SEQUENTIAL);
        assertNotEquals(RingAnimator.deployFrames(fast, RingStyle.CONCURRENT),
            RingAnimator.deployFrames(fast, RingStyle.SEQUENTIAL), "the styles must differ for this to mean anything");

        quick.collapse();
        held.runAll();

        final List<Float> all = ringPitches();
        assertEquals(RingAnimator.RING_COUNT * 2, all.size(), "every ring sounds on the way home");
    }

    /** A ceiling ring with no floor in reach is refused as a real cycle refuses it. */
    @Test
    void aCeilingRingWithNoFloorToDropToIsRefusedAndDrawsNothingMore()
    {
        final Ring ceiling = ringAt(AX, 70, AZ, RingOrientation.CEILING, RingStyle.CONCURRENT);
        final RingPreview hanging = new RingPreview(viewer, pair, ceiling, drawing);
        hanging.show();
        final Map<Long, String> padOnly = new HashMap<>(drawing.shown);

        for (final RingBlockage why : List.of(RingBlockage.CEILING_TOO_HIGH, RingBlockage.CEILING_TOO_LOW))
        {
            drawing.found = why;

            assertEquals(RingPreview.Result.NO_FLOOR, hanging.deploy(), why.name());

            assertSame(why, hanging.getBlockage());
            assertEquals(padOnly, drawing.shown);
            assertEquals(RingPreview.State.LIT, hanging.getState());
            assertEquals(0, held.pending());
        }
    }

    /** A ring built over is still worth drawing: the drawing passes through it. */
    @Test
    void aRingThatIsBuiltOverStillDeploys()
    {
        drawing.found = RingBlockage.OBSTRUCTED;
        preview.show();

        assertEquals(RingPreview.Result.DONE, preview.deploy());
        held.runAll();

        assertEquals(RingPreview.State.DEPLOYED, preview.getState());
        assertNull(preview.getBlockage(), "only a missing floor is a refusal");
    }

    /** A style changed while the stack stands waits for the next deploy, as the issue says. */
    @Test
    void aStyleChangedWhileTheStackStandsTakesEffectOnTheNextDeploy()
    {
        final Ring fast = ringAt(AX, AY, AZ, RingOrientation.FLOOR, RingStyle.CONCURRENT);
        final RingPreview quick = new RingPreview(viewer, pair, fast, drawing);
        quick.show();
        quick.deploy();
        held.runAll();
        fast.setStyle(RingStyle.SEQUENTIAL);

        quick.collapse();
        final int home = held.runAll();
        assertEquals(RingAnimator.deployFrames(fast, RingStyle.CONCURRENT), home,
            "the stack goes home the way it came up");

        quick.deploy();
        assertEquals(RingAnimator.deployFrames(fast, RingStyle.SEQUENTIAL) - 1, held.runAll(),
            "and the next one rises in the new style");
    }

    /** A frame that cannot be drawn takes the whole preview back rather than leave half of it. */
    @Test
    void aFrameThatFailsClearsThePreviewInsteadOfLeavingItHalfDrawn()
    {
        preview.show();
        preview.deploy();
        drawing.failOnSlab = true;

        held.runNext();

        assertTrue(drawing.shown.isEmpty(), "a stack nobody will finish is a wall only this player can see");
        assertEquals(0, held.pending(), "and nothing is left queued to continue it");
        assertFalse(drawing.revealed.isEmpty());
    }
}
