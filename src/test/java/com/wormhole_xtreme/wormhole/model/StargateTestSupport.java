package com.wormhole_xtreme.wormhole.model;

/**
 * Points one gate at another, for a test that needs a dialled pair without dialling.
 *
 * <p>{@link Stargate#setGateTarget} is package-private on purpose. Only two things in the
 * plugin set a target -- {@code StargateDialManager} when a gate is dialled, and
 * {@code StargateLifecycle} when one shuts down -- and both do a good deal else besides:
 * chevrons, portal blocks, and the state of the gate at the other end. A public setter would
 * invite pointing a gate somewhere without any of that, which is a gate that looks dialled and
 * is not.
 *
 * <p>Tests still need a pair that is pointing at each other, without standing up the whole dial
 * sequence to get one. Nine test classes were reaching past the modifier with reflection to do
 * it, fifteen times over. This is that, from inside the package, so it is a compile-checked
 * call rather than a string that goes stale silently.
 *
 * <p>Lives here for the same reason {@code ConfigTestSupport} lives in the config package: the
 * thing it needs is package-private, and it is only for tests.
 */
public final class StargateTestSupport
{
    /** Static helpers only. */
    private StargateTestSupport()
    {
    }

    /**
     * Points a gate at another one, the way dialling would leave it.
     *
     * <p>Only the target field. A test that needs the rest of what dialling does -- lit
     * chevrons, an open portal -- has to arrange that itself, and most do not need it.
     *
     * @param gate
     *            the gate doing the pointing
     * @param target
     *            what it points at, or null for none
     */
    public static void target(final Stargate gate, final Stargate target)
    {
        gate.setGateTarget(target);
    }
}
