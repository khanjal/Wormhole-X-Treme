package com.wormhole_xtreme.wormhole.model;

/**
 * The order a kawoosh plays in: each woosh step drawn from the shallowest out, taken back from the
 * deepest in, and then the wormhole settles into the opening.
 *
 * <p>Numbers only, so a real gate and a build preview play the same woosh however each draws it.
 * A stage counts every step of the woosh from 0; a woosh of {@code n} steps has {@code 2n} stages
 * and settles at stage {@code 2n}.
 */
public final class WooshSequence
{
    /** What a stage does. */
    public enum Move
    {
        /** Draw woosh step {@link Step#index()}. */
        OUT,
        /** Take woosh step {@link Step#index()} back. */
        BACK,
        /** The woosh is over; fill the opening. */
        SETTLE
    }

    /**
     * One stage of the woosh.
     *
     * @param move
     *            what it does
     * @param index
     *            which woosh step, from 0 at the shallowest; 0 for {@link Move#SETTLE}
     */
    public record Step(Move move, int index) {}

    /**
     * What a woosh is played on: a real gate, or a build preview. Each draws in its own way;
     * {@link #play} decides what is drawn and when, so the two cannot disagree about it.
     */
    public interface Canvas
    {
        /** Plays the kawoosh sound. */
        void kawoosh();

        /**
         * Draws one woosh step.
         *
         * @param index
         *            the step, from 0 at the shallowest
         */
        void draw(int index);

        /**
         * Takes one woosh step back.
         *
         * @param index
         *            the step, from 0 at the shallowest
         */
        void undraw(int index);

        /** Takes back whatever of the woosh is still drawn. */
        void undrawAll();

        /** The woosh is over with the iris open: the wormhole fills the opening. */
        void settle();

        /** The woosh is over, or was cut short, behind a shut iris. */
        void settleBehindIris();
    }

    private WooshSequence() {}

    /**
     * @param stage
     *            the stage, from 0
     * @param steps
     *            how many woosh steps the gate has
     * @return what that stage does; {@link Move#SETTLE} from stage {@code 2 * steps} on
     */
    public static Step at(final int stage, final int steps)
    {
        if (stage < steps)
        {
            return new Step(Move.OUT, stage);
        }
        if (stage < (2 * steps))
        {
            return new Step(Move.BACK, ((2 * steps) - 1) - stage);
        }
        return new Step(Move.SETTLE, 0);
    }

    /**
     * Plays one stage of a woosh.
     *
     * <p>The iris is asked at every stage, not once when dialling began, so an iris shut partway
     * through stops the woosh there and takes back what was out, and one opened before the woosh
     * lets it through. Every woosh step lands on or past the iris, so a shut one hides them all.
     * The kawoosh is heard either way, once: the wormhole forms, just out of sight.
     *
     * @param stage
     *            the stage to play, from 0
     * @param steps
     *            how many woosh steps the gate has
     * @param iris
     *            the iris over the opening: the gate's own, or the preview's
     * @param canvas
     *            what to play it on
     * @return the next stage, or -1 once the woosh is over and nothing more should be booked
     */
    public static int play(final int stage, final int steps, final GateIris iris, final Canvas canvas)
    {
        if ((stage == 0) && (steps > 0))
        {
            canvas.kawoosh();
        }
        if (iris.isGateIrisActive())
        {
            canvas.undrawAll();
            canvas.settleBehindIris();
            return -1;
        }
        final Step now = at(Math.max(0, stage), steps);
        if (now.move() == Move.OUT)
        {
            canvas.draw(now.index());
        }
        else if (now.move() == Move.BACK)
        {
            canvas.undraw(now.index());
        }
        // Settled in the same stage as the shallowest step is taken back: ending a stage later
        // left that step showing for as long as the gate was open.
        if ((now.move() == Move.SETTLE) || (at(stage + 1, steps).move() == Move.SETTLE))
        {
            canvas.settle();
            return -1;
        }
        return stage + 1;
    }

    /**
     * The stage a woosh step and direction stand for, as a gate keeps them.
     *
     * @param index
     *            the woosh step, from 0
     * @param back
     *            whether it is being taken back
     * @param steps
     *            how many woosh steps the gate has
     */
    public static int stageOf(final int index, final boolean back, final int steps)
    {
        return back ? (((2 * steps) - 1) - index) : index;
    }
}
