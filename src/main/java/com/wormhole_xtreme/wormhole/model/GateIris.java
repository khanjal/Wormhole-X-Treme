package com.wormhole_xtreme.wormhole.model;

/**
 * Anything with an iris over its opening: a real gate, or a build preview of one.
 *
 * <p>The same name on both, so whatever asks about an iris -- the woosh, for one -- asks either
 * the same way. Each keeps its own state: setting a real gate's also files it with the server,
 * which a preview must never be.
 */
@FunctionalInterface
public interface GateIris
{
    /** @return true if the iris is shut */
    boolean isGateIrisActive();
}
