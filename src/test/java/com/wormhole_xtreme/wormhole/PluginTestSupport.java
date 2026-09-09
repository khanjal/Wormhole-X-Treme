package com.wormhole_xtreme.wormhole;

import static org.mockito.Mockito.mock;

import org.bukkit.scheduler.BukkitScheduler;

/**
 * Puts a plugin instance where {@link WormholeXTreme#getThisPlugin()} will find it.
 *
 * <p>Almost everything in this plugin reaches the running instance through that static, and
 * almost nothing works without one -- {@code prettyLog} needs it to log, the managers need it
 * for the data folder, the listeners need it for the scheduler. So a test that exercises any of
 * that has to put one there first, and take it away afterwards so the next test is not looking
 * at a mock configured for something else.
 *
 * <p>Seventy-eight test classes were doing that for themselves, in a hundred and fifteen
 * places, by the same three lines of reflection each time. This is those three lines, named
 * for what they are for.
 *
 * <h2>Take it away in a teardown</h2>
 *
 * <p>Every {@link #install} should be matched by a {@link #remove}, in an {@code @AfterEach}.
 * The static outlives the test class, so one left behind is a mock that the next class inherits
 * without asking for it -- which shows up as a test that passes alone and fails in a suite, or
 * worse, the other way round.
 *
 * <p>{@code remove} puts back whatever was there before the matching {@code install}, rather
 * than always writing null. Two test classes were already careful enough to do that by hand;
 * everywhere else it comes to the same thing, because a class that cleans up leaves null
 * behind for the next one anyway.
 */
public final class PluginTestSupport
{
    /** What was in the static before the current install, put back by {@link #remove()}. */
    private static Object previous;

    /** Static helpers only. */
    private PluginTestSupport()
    {
    }

    /**
     * Installs a fresh mock plugin.
     *
     * @return the mock, for a test that wants to stub something on it
     * @throws ReflectiveOperationException
     *             if the field was renamed
     */
    public static WormholeXTreme install() throws ReflectiveOperationException
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        install(plugin);
        return plugin;
    }

    /**
     * Installs a particular plugin instance.
     *
     * @param plugin
     *            the instance to install, which may be a mock, a spy or a real one
     * @throws ReflectiveOperationException
     *             if the field was renamed
     */
    public static void install(final WormholeXTreme plugin) throws ReflectiveOperationException
    {
        previous = PrivateStatics.of(WormholeXTreme.class, "thisPlugin");
        PrivateStatics.set(WormholeXTreme.class, "thisPlugin", plugin);
    }

    /**
     * Puts back whatever was there before the last {@link #install}.
     *
     * @throws ReflectiveOperationException
     *             if the field was renamed
     */
    public static void remove() throws ReflectiveOperationException
    {
        PrivateStatics.set(WormholeXTreme.class, "thisPlugin", previous);
        previous = null;
    }

    /**
     * Installs the scheduler the plugin hands out.
     *
     * <p>Separate from the plugin because the two are separate statics and not every test that
     * needs one needs the other. A test that schedules anything needs this: without it the
     * scheduler is null and the call throws rather than quietly doing nothing.
     *
     * @param scheduler
     *            the scheduler to install, or null to take it away again
     * @throws ReflectiveOperationException
     *             if the field was renamed
     */
    public static void scheduler(final BukkitScheduler scheduler) throws ReflectiveOperationException
    {
        PrivateStatics.set(WormholeXTreme.class, "scheduler", scheduler);
    }
}
