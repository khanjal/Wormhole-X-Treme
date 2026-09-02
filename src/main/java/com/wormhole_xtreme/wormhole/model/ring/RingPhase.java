/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Where a ring pair is in its cycle.
 */
package com.wormhole_xtreme.wormhole.model.ring;

/**
 * Where a {@link RingPair} is in its cycle.
 *
 * <p>The important line in this enum is the one between {@link #COUNTDOWN} and
 * {@link #DEPLOY}. A cycle can be called off during the countdown, when nothing has been
 * written but a flat set of glowstone and one restore map undoes all of it. Once the ring
 * starts deploying it is committed and runs to the end, whoever walks away.
 *
 * <p>That is what keeps the animation tractable: the abortable phase is trivially
 * reversible, and the phase with all the moving parts cannot be interrupted halfway. An
 * empty committed cycle is a legal outcome — the ring deploys, flashes, sends nobody, and
 * retracts.
 */
public enum RingPhase
{
    /** Doing nothing. A move inside either interior can arm the pair. */
    IDLE,

    /** Glowstone is showing and the clock is running. Aborts if both interiors empty. */
    COUNTDOWN,

    /** Slabs are travelling. Committed: this runs to the end regardless. */
    DEPLOY,

    /** Fully deployed. Both interiors are snapshotted in this one tick and swapped. */
    FLASH,

    /**
     * Stacked and still, for a beat after the swap.
     *
     * <p>The rings do not travel up and immediately back down. They stand for a couple of
     * seconds with the travellers already gone, and that pause is most of what makes the
     * effect read as a transport rather than as blocks moving.
     */
    HOLD,

    /** Slabs travelling back, every block being restored. */
    RETRACT,

    /** Recently fired. Refuses every trigger until the cooldown expires. */
    COOLDOWN
}
