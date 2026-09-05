package com.wormhole_xtreme.wormhole.model.ring;

/**
 * Why a ring has nowhere to put a traveller.
 *
 * <p>A ring is invisible and its interior is ordinary ground, so nothing stops somebody
 * building in one long after it was made — or digging its floor away. Either leaves an end
 * that still fires and cannot honestly receive anybody, so the pair refuses to engage and
 * says which of the two it is.
 *
 * <p>Both are judged over the whole interior rather than by hunting for one clear square.
 * Somewhere to stand is not the same as somewhere fit to arrive: a ring with one block
 * dropped in it would still have twenty free columns, and delivering people to whichever
 * corner happened to be empty is not what a transport ring should do.
 *
 * <p>Only the interior counts. What anybody has built around a ring is their business, and
 * arriving next to it is no trouble — you can walk away, or step back in and go home.
 *
 * <p>Room to stand and room to work are separate questions, and the ceiling answers them
 * differently. A stack is four blocks tall and a traveller is two, so a room can have space
 * for somebody and no space for the rings that are supposed to arrive around them.
 *
 * <p>The last two are a ceiling ring's own problem. Its rings fall all the way to the floor
 * and stack up from there, so it needs a floor to fall to: near enough that they reach it,
 * and far enough that the stack has somewhere to form.
 */
public enum RingBlockage
{
    /** Something has been built in the space travellers would arrive in. */
    OBSTRUCTED,

    /** The floor under the ring has gone, so there is nothing to arrive on. */
    NO_GROUND,

    /** A floor ring with too little room above it for the stack to stand up in. */
    NO_HEADROOM,

    /** A ceiling ring with no floor near enough below it for the rings to reach. */
    CEILING_TOO_HIGH,

    /** A ceiling ring so close to the floor that its stack has nowhere to form. */
    CEILING_TOO_LOW
}
