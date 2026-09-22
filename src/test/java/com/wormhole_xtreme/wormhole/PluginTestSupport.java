package com.wormhole_xtreme.wormhole;

import static org.mockito.Mockito.mock;

import org.bukkit.scheduler.BukkitScheduler;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

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

    /** The {@link StargateManager} statics a test class can leave something in. */
    private static final String[] GATE_STATICS =
    {
        "stargateList", "gateBlocksByWorld", "incompleteStargates", "activatedStargates",
        "stargateNetworks", "playerBuilders"
    };

    /**
     * Puts {@link StargateManager}'s shared statics back to empty.
     *
     * <p>A gate built in a test is a gate the manager keeps for the life of the JVM, and the
     * suite runs in one fork, so anything left behind is inherited by every class that runs
     * afterwards. That shows up as a test which passes alone and fails in the suite -- the way
     * to recognise it is an assertion on a count that is larger than the test's own gates.
     *
     * <p>The open set is drained through {@code setGateActive(false)} rather than cleared,
     * because the set mirrors that flag: clearing it alone would leave a gate object claiming
     * to be open while the manager no longer thinks so.
     *
     * <p>Unchecked on purpose, unlike the rest of this class: this is called from teardowns
     * that have no other reason to declare a checked exception, and a renamed field here is a
     * mistake to fix rather than a condition to handle.
     */
    public static void forgetAllGates()
    {
        for (final Stargate gate : new java.util.ArrayList<>(StargateManager.getOpenGates()))
        {
            gate.setGateActive(false);
        }
        // The iris set is the same shape of state as the open set, and leaks the same way: a
        // gate left shut by one test is a gate every later test in the fork has to draw an
        // iris for, in a world its own test has finished with.
        for (final Stargate gate : new java.util.ArrayList<>(StargateManager.getIrisGates()))
        {
            gate.setGateIrisActive(false);
        }
        try
        {
            for (final String name : GATE_STATICS)
            {
                final Object value = PrivateStatics.of(StargateManager.class, name);
                if (value instanceof java.util.Map)
                {
                    ((java.util.Map<?, ?>) value).clear();
                }
            }
        }
        catch (final ReflectiveOperationException e)
        {
            throw new AssertionError("StargateManager's statics were renamed", e);
        }
        GateSpatialIndex.clear();
    }
}
