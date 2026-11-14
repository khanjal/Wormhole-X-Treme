package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.scheduler.BukkitScheduler;

/**
 * A scheduler that holds every task it is given until a test lets one go.
 *
 * <p>Two things a plain mock scheduler gets wrong, and both have hidden a bug in this
 * project. It forgets cancellations, so a test cannot tell a task that was cancelled from one
 * that was left pending; and it keeps no count, so "exactly one step is waiting" cannot be
 * asked. This one removes a task when it is cancelled, counts what is pending, and remembers
 * every task it ever held so that a test can run one that <em>was</em> cancelled -- the way a
 * task already queued on the main thread still runs when it is cancelled too late.
 */
public final class HeldScheduler
{
    /** One waiting task. */
    private record Held(Runnable task, long delay) {}

    /** The mock, to be installed as the plugin's scheduler. */
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);

    /** What is waiting, oldest first. */
    private final Map<Integer, Held> pending = new LinkedHashMap<>();

    /** Everything ever held, including what was cancelled. */
    private final List<Runnable> everHeld = new ArrayList<>();

    /** The id the next task gets. */
    private int nextId = 1;

    /** How many tasks have been cancelled. */
    private int cancelled = 0;

    /** Instantiates a scheduler holding nothing. */
    public HeldScheduler()
    {
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong())).thenAnswer(call ->
        {
            final int id = nextId++;
            final Runnable task = call.getArgument(1, Runnable.class);
            pending.put(Integer.valueOf(id), new Held(task, call.getArgument(2, Long.class).longValue()));
            everHeld.add(task);
            return Integer.valueOf(id);
        });
        doAnswer(call ->
        {
            if (pending.remove(call.getArgument(0, Integer.class)) != null)
            {
                cancelled++;
            }
            return null;
        }).when(scheduler).cancelTask(anyInt());
    }

    /**
     * Installs this as the plugin's scheduler.
     *
     * @throws ReflectiveOperationException
     *             if the field was renamed
     */
    public void install() throws ReflectiveOperationException
    {
        PluginTestSupport.scheduler(scheduler);
    }

    /**
     * Takes it away again.
     *
     * @throws ReflectiveOperationException
     *             if the field was renamed
     */
    public void remove() throws ReflectiveOperationException
    {
        PluginTestSupport.scheduler(null);
    }

    /** @return how many tasks are waiting */
    public int pending()
    {
        return pending.size();
    }

    /** @return how many tasks have been cancelled while waiting */
    public int cancelled()
    {
        return cancelled;
    }

    /** @return the delays of what is waiting, oldest first */
    public List<Long> delays()
    {
        return pending.values().stream().map(Held::delay).toList();
    }

    /** Lets the oldest waiting task run, which may queue another. */
    public void runNext()
    {
        final Integer id = pending.keySet().iterator().next();
        pending.remove(id).task().run();
    }

    /**
     * Lets the oldest waiting task run that was not scheduled with this delay.
     *
     * <p>For a test with two kinds of task waiting, such as an animation step and a timeout:
     * the timeout is told apart by its delay, since starting it again after every command
     * makes it the newest as often as the oldest.
     *
     * @param skip
     *            the delay of the tasks to leave alone
     */
    public void runNextOtherThan(final long skip)
    {
        for (final Map.Entry<Integer, Held> entry : pending.entrySet())
        {
            if (entry.getValue().delay() != skip)
            {
                pending.remove(entry.getKey());
                entry.getValue().task().run();
                return;
            }
        }
        throw new IllegalStateException("nothing is waiting but tasks with delay " + skip);
    }

    /**
     * Runs tasks until nothing is waiting.
     *
     * @return how many ran
     */
    public int runAll()
    {
        int ran = 0;
        while (!pending.isEmpty())
        {
            if (++ran > 10_000)
            {
                throw new IllegalStateException("a task keeps scheduling itself");
            }
            runNext();
        }
        return ran;
    }

    /**
     * Runs the task handed out at one position, whether or not it was cancelled since.
     *
     * @param index
     *            0 for the first task ever scheduled
     */
    public void runEvenIfCancelled(final int index)
    {
        everHeld.get(index).run();
    }
}
