package com.wormhole_xtreme.wormhole;

import java.lang.reflect.Field;

/**
 * Reaches a private static field that a test needs to read or reset.
 *
 * <p>Five tests were doing this for themselves -- three lines of {@code getDeclaredField},
 * {@code setAccessible} and a cast, each with its own {@code @SuppressWarnings("unchecked")}
 * to quiet the cast. The cast is unavoidable: reflection erases the type, so something has to
 * assert what came back. Doing it once puts that assertion in one place with the reason
 * written down.
 *
 * <p>What these tests are reaching for is static state that outlives them. The listeners
 * remember who was recently teleported and clear it from a scheduled task; {@link
 * com.wormhole_xtreme.wormhole.model.ring.RingTransit} remembers which pairs are mid-cycle.
 * Under a mock scheduler nothing ever runs to empty those, so without a reset one test decides
 * what the next one sees and the result depends on the order they ran in.
 *
 * <h2>Why reflection rather than a seam</h2>
 *
 * <p>This project does add package-private seams to production classes for tests -- {@code
 * GateEvents.dispatcher} is one. Those exist where a test needs to observe behaviour it cannot
 * otherwise reach. Emptying a set between tests is not that: it would mean production API that
 * exists only so tests can undo themselves, which is a worse trade than a reflective read
 * confined to test code.
 *
 * <p>The cost is real and worth naming: this is not compile-checked, so renaming one of those
 * fields breaks the test at run time rather than at build time. It fails loudly when it does --
 * {@code getDeclaredField} throws and the exception is not caught here, which is the one thing
 * the hand-written versions did not all get right.
 */
public final class PrivateStatics
{
    /** Static helpers only. */
    private PrivateStatics()
    {
    }

    /**
     * The value of a private static field.
     *
     * <p>The returned type is inferred from where the result is put, so a call usually reads
     * as {@code final Set<UUID> marked = PrivateStatics.of(Listener.class, "field");} with no
     * cast in sight. Where there is nothing to infer from, name it: {@code
     * PrivateStatics.<Set<UUID>>of(...)}.
     *
     * @param <T>
     *            what the caller expects the field to hold
     * @param owner
     *            the class declaring it
     * @param name
     *            the field name
     * @return its current value
     * @throws ReflectiveOperationException
     *             if there is no such field, which means it was renamed
     */
    @SuppressWarnings("unchecked")
    public static <T> T of(final Class<?> owner, final String name) throws ReflectiveOperationException
    {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(null);
    }
}
