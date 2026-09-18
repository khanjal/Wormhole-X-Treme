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
     * The stage a woosh step and direction stand for, as a gate keeps them.
     *
     * @param index
     *            the woosh step, from 0
     * @param back
     *            whether it is being taken back
     * @param steps
     *            how many woosh steps the gate has
     * @return the stage
     */
    public static int stageOf(final int index, final boolean back, final int steps)
    {
        return back ? (((2 * steps) - 1) - index) : index;
    }
}
