package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The timing strip in the documentation still shows the sequence the plugin actually runs.
 *
 * <p>A beam is particles rather than blocks, so there is nothing to draw a picture of and no
 * resource file to fingerprint. What {@code scripts/render_beam_sheets.py} draws is the
 * arithmetic: when each phase runs, when the five moments fall inside them, and how the
 * envelope's density ramps up and the fade's ramps down.
 *
 * <p>It draws that from numbers transcribed out of {@link BeamFrame} and the shipped defaults,
 * not read from them, so this recomputes the whole sequence by running the real
 * {@link BeamFrame#at(int, BeamTiming)} and compares it tick for tick. A timing diagram that
 * has quietly stopped matching is worse than none: somebody would cut a capture to it.
 *
 * <p>Which is not hypothetical. {@code docs/CAPTURES.md} said the cycle was 58 ticks --
 * 12 + 18 + 20 + 8, the four durations added up -- and it is 52, because the descend column
 * starts at the teleport tick rather than at the end of the rise. Six ticks run at both ends
 * at once. The strip is what made that visible, and this is what keeps it true.
 */
class BeamGalleryTest
{
    /** The drawing. */
    private static final Path IMAGE = Paths.get("docs/images/beams/timing.svg");

    /** The document it is in. */
    private static final Path DOCUMENT = Paths.get("docs/BEAMS.md");

    /** Where the wrong number used to live, and where the right one now has to stay. */
    private static final Path CAPTURES = Paths.get("docs/CAPTURES.md");

    /** How to put it right, said in the failure rather than left to be worked out. */
    private static final String REGENERATE =
        " -- re-run: python scripts/render_beam_sheets.py";

    /** The line the drawing carries, describing itself. */
    private static final Pattern CLAIM = Pattern.compile("<!-- (beam .*?) -->");

    /**
     * The shipped defaults, as {@code DefaultSettings} registers them.
     *
     * <p>Resolved rather than used raw, because that is what {@code BeamAnimation} does. At
     * these values no clamp bites, and this failing would mean the defaults had drifted into a
     * range {@link BeamTiming} has to correct -- which the strip does not draw and should not
     * be made to.
     */
    private static BeamTiming shipped()
    {
        return BeamTiming.resolve(12, 6, 18, 12, 20, 8);
    }

    /** Every tick of the sequence, up to and including the one that reports finished. */
    private static List<BeamFrame> sequence()
    {
        final BeamTiming timing = shipped();
        final List<BeamFrame> frames = new ArrayList<>();
        for (int tick = 0; tick < 1000; tick++)
        {
            final BeamFrame frame = BeamFrame.at(tick, timing);
            if (frame.marks().finished())
            {
                return frames;
            }
            frames.add(frame);
        }
        throw new IllegalStateException("a beam that never finishes is a bug in BeamFrame,"
            + " not in this test");
    }

    /** What the drawing says about itself. */
    private static String claim() throws IOException
    {
        final String svg = Files.readString(IMAGE, StandardCharsets.UTF_8);
        final Matcher m = CLAIM.matcher(svg);
        assertTrue(m.find(), IMAGE.getFileName() + " carries no line saying what it drew"
            + REGENERATE);
        return m.group(1);
    }

    /** A phase as {@code first-last}, in the order the renderer writes it. */
    private static String span(final List<BeamFrame> frames,
        final java.util.function.Predicate<BeamFrame> active)
    {
        int first = -1;
        int last = -1;
        for (int tick = 0; tick < frames.size(); tick++)
        {
            if (active.test(frames.get(tick)))
            {
                first = (first < 0) ? tick : first;
                last = tick;
            }
        }
        return first + "-" + last;
    }

    /** The tick one of the marks falls on. */
    private static int mark(final List<BeamFrame> frames,
        final java.util.function.Predicate<BeamFrame> is)
    {
        for (int tick = 0; tick < frames.size(); tick++)
        {
            if (is.test(frames.get(tick)))
            {
                return tick;
            }
        }
        throw new IllegalStateException("every beam has all five marks; this one does not");
    }

    /** One comma-separated run of numbers, as the renderer writes them. */
    private static String numbers(final List<Integer> values)
    {
        final List<String> out = new ArrayList<>();
        values.forEach(value -> out.add(String.valueOf(value)));
        return String.join(",", out);
    }

    /**
     * The strip is the sequence {@link BeamFrame} actually produces, tick for tick.
     *
     * <p>Everything the drawing asserts, in one comparison: how long it runs, where each phase
     * starts and stops, which tick each of the marks falls on, how many ticks have both columns
     * running, and both density ramps. A strip drawn from stale numbers fails here with the
     * command that redraws it.
     */
    @Test
    void theStripIsTheSequenceTheAnimatorProduces() throws IOException
    {
        final List<BeamFrame> frames = sequence();
        final List<Integer> densities = new ArrayList<>();
        final List<Integer> fades = new ArrayList<>();
        int overlap = 0;
        for (final BeamFrame frame : frames)
        {
            if (frame.envelop().active())
            {
                densities.add(frame.envelop().density());
            }
            if (frame.fade().active())
            {
                fades.add(frame.fade().density());
            }
            if (frame.rise().active() && frame.descend().active())
            {
                overlap++;
            }
        }

        assertEquals("beam total=" + frames.size()
            + " envelop=" + span(frames, f -> f.envelop().active())
            + " rise=" + span(frames, f -> f.rise().active())
            + " descend=" + span(frames, f -> f.descend().active())
            + " fade=" + span(frames, f -> f.fade().active())
            + " vanish=" + mark(frames, f -> f.marks().vanish())
            + " teleport=" + mark(frames, f -> f.marks().teleport())
            + " arrive=" + mark(frames, f -> f.marks().arrive())
            + " overlap=" + overlap
            + " densities=" + numbers(densities)
            + " fades=" + numbers(fades),
            claim(),
            "the timing strip shows a sequence the animator does not run" + REGENERATE);
    }

    /**
     * The phases really do overlap, so the strip is not drawing a distinction that isn't there.
     *
     * <p>Stated separately from the comparison above because it is the claim the drawing exists
     * to make. If a change ever made the descend wait for the rise to finish, the test above
     * would fail on the numbers and this would say which property had gone.
     */
    @Test
    void theDescendStartsBeforeTheRiseHasFinished() throws IOException
    {
        final List<BeamFrame> frames = sequence();
        final int both = (int) frames.stream()
            .filter(f -> f.rise().active() && f.descend().active()).count();

        assertTrue(both > 0,
            "the descend column should start at the teleport tick, partway through the rise;"
                + " with no overlap the whole point of the strip in " + DOCUMENT.getFileName()
                + " is gone");
        assertTrue(frames.size() < (12 + 18 + 20 + 8),
            "the sequence should finish sooner than the four durations added together, by"
                + " exactly the overlap; adding them is the mistake docs/CAPTURES.md made");
        assertEquals(12 + 18 + 20 + 8 - both, frames.size(),
            "the whole difference between the sum of the phases and the real length should be"
                + " the overlap, and nothing else");
    }

    /**
     * The document shows the strip, and the capture length agrees with it.
     *
     * <p>{@code docs/CAPTURES.md} tells somebody what to cut a recording to. It said 2.9
     * seconds, from the 58 that adding the durations gives, and a clip cut to that would carry
     * a third of a second of nothing on the end. It is the one number in these documents that
     * somebody acts on with a video editor open, so it is worth a test of its own.
     */
    @Test
    void theDocumentsAgreeOnHowLongABeamIs() throws IOException
    {
        final String beams = Files.readString(DOCUMENT, StandardCharsets.UTF_8);
        final int start = beams.indexOf("<!-- timing:start -->");
        final int end = beams.indexOf("<!-- timing:end -->");
        assertTrue((start > 0) && (end > start), "the timing markers should still be in "
            + DOCUMENT.getFileName() + "; the renderer writes between them");
        assertTrue(beams.contains("images/beams/timing.svg"),
            "the strip exists but nothing on the page shows it" + REGENERATE);

        final int ticks = sequence().size();
        final String captures = Files.readString(CAPTURES, StandardCharsets.UTF_8);
        assertTrue(captures.contains(ticks + " ticks"),
            CAPTURES.getFileName() + " should say the cycle is " + ticks + " ticks");
        // Twenty ticks is one second, which that document says itself.
        final String seconds = String.format("%.1fs", ticks / 20.0);
        assertTrue(captures.contains(seconds),
            CAPTURES.getFileName() + " should cut the beam capture to " + seconds
                + ", which is what " + ticks + " ticks is");
    }
}
