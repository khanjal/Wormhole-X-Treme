/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Why a ring cannot take anybody.
 */
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
 */
public enum RingBlockage
{
    /** Something has been built in the space travellers would arrive in. */
    OBSTRUCTED,

    /** The floor under the ring has gone, so there is nothing to arrive on. */
    NO_GROUND
}
