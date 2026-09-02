/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Which way the light runs through a ring stack at the moment of transport.
 */
package com.wormhole_xtreme.wormhole.model.ring;

/**
 * Which way the light runs through the stack when a ring pair fires.
 *
 * <p>With the rings up and still, each one lights in turn and the transport happens as the
 * last of them goes out. It is the moment the whole cycle is built around: everything before
 * it is the rings getting into position, and everything after is them putting themselves
 * away.
 */
public enum RingFlashDirection
{
    /** Starts at the ring that flew highest and runs down to the floor. As the show does it. */
    TOP_DOWN,

    /** Starts at the floor and runs up. */
    BOTTOM_UP
}
