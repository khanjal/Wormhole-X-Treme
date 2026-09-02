/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Driving a ring cycle through its phases on the server clock.
 */
package com.wormhole_xtreme.wormhole.model.ring;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.World;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Walks a ring pair through its phases on the server clock.
 *
 * <p>{@link RingCycle} knows what each phase does; this knows when. Everything here is one
 * scheduled task handing off to the next, which is why the phase logic was kept out of it —
 * scheduling is the part that cannot be tested without a server, so it should contain as
 * little as possible worth testing.
 *
 * <p>A pair runs at most one cycle at a time. That is enforced by the phase on the pair
 * itself rather than by a lock here: anything not {@code IDLE} is already busy, which is the
 * same check that stops a second player arming a ring that is already going.
 */
public final class RingTransit
{
    /** Pairs with a cycle running, so nothing starts a second one. */
    private static final Set<String> running = ConcurrentHashMap.newKeySet();

    private RingTransit() {}

    /**
     * Starts a cycle, if the pair is willing to run one.
     *
     * @param pair
     *            the pair to fire
     * @return true if a cycle started
     */
    public static boolean start(final RingPair pair)
    {
        if ((pair == null) || !pair.canFire(System.currentTimeMillis()))
        {
            return false;
        }
        final World world = worldOf(pair);
        if (world == null)
        {
            return false;
        }
        if (!running.add(pair.getId()))
        {
            return false;
        }

        final int reach = ConfigManager.getRingReach();
        final RingCycle cycle = new RingCycle(pair, new BukkitRingWorld(world, pair, reach), reach);
        // Both ends have to be loaded for the whole cycle. The far end is usually nowhere
        // near a player, and animating into an unloaded chunk writes blocks nobody will see
        // put back and lands travellers in terrain that has not been generated.
        hold(world, pair);
        cycle.beginCountdown();
        countDown(cycle, world, ConfigManager.getRingCountdownTicks());
        return true;
    }

    /**
     * Counts the cycle down, saying so once a second.
     *
     * <p>Stepped a second at a time rather than waited out in one go, so the people standing
     * in the rings can be told how long they have. A countdown that is not a whole number of
     * seconds spends its remainder on the last step, which keeps the total exact however it
     * is configured.
     *
     * @param cycle
     *            the cycle running
     * @param world
     *            the world it is in
     * @param remaining
     *            ticks left before the rings commit
     */
    private static void countDown(final RingCycle cycle, final World world, final int remaining)
    {
        if (remaining <= 0)
        {
            commitOrAbort(cycle, world);
            return;
        }
        final int seconds = (remaining + 19) / 20;
        RingMessages.counting(cycle.everyoneInside(), seconds);
        final int step = Math.min(20, remaining);
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        countDown(cycle, world, remaining - step);
                    }
                    catch (final RuntimeException e)
                    {
                        recover(cycle, world, e);
                    }
                }
            }, step);
    }

    /**
     * The end of the countdown: the last moment a cycle can be called off.
     *
     * @param cycle
     *            the cycle running
     * @param world
     *            the world it is in
     */
    private static void commitOrAbort(final RingCycle cycle, final World world)
    {
        try
        {
            if (cycle.shouldAbort())
            {
                cycle.abort();
                finished(cycle, world);
                return;
            }
            // Said before the rings move, because this is the last moment anybody could have
            // walked away and the first at which it no longer matters that they did.
            RingMessages.committed(cycle.everyoneInside());
            cycle.beginDeploy();
            step(cycle, world);
        }
        catch (final RuntimeException e)
        {
            recover(cycle, world, e);
        }
    }

    /**
     * Draws one frame and books the next, or moves on to the phase after this one.
     *
     * @param cycle
     *            the cycle running
     * @param world
     *            the world it is in
     */
    private static void step(final RingCycle cycle, final World world)
    {
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        if (cycle.advanceFrame())
                        {
                            step(cycle, world);
                        }
                        else if (cycle.getPair().getPhase() == RingPhase.RETRACT)
                        {
                            cycle.finish(System.currentTimeMillis()
                                + (ConfigManager.getRingCooldownTicks() * 50L));
                            finished(cycle, world);
                        }
                        else
                        {
                            settleThenSwap(cycle, world);
                        }
                    }
                    catch (final RuntimeException e)
                    {
                        recover(cycle, world, e);
                    }
                }
            }, ConfigManager.getRingDeployTicks());
    }

    /**
     * The stack is up. Let it stand a moment, then move everybody.
     *
     * <p>The pause before the swap is deliberate and is the last beat of the animation
     * rather than dead time. Taking people the instant the final ring stops reads as the
     * teleport interrupting the rings; letting them arrive, hold, and only then flash reads
     * as the rings doing it.
     *
     * @param cycle
     *            the cycle running
     * @param world
     *            the world it is in
     */
    private static void settleThenSwap(final RingCycle cycle, final World world)
    {
        cycle.beginHold();
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        runFlash(cycle, world, 0);
                    }
                    catch (final RuntimeException e)
                    {
                        recover(cycle, world, e);
                    }
                }
            }, ConfigManager.getRingSettleTicks());
    }

    /**
     * Runs the light through the stack, one ring at a time, then moves everybody.
     *
     * <p>This is the moment the whole cycle is built around. Everything before it is the
     * rings getting into position and everything after is them putting themselves away, so
     * the transport itself gets an animation of its own rather than being an instant nobody
     * sees.
     *
     * @param cycle
     *            the cycle running
     * @param world
     *            the world it is in
     * @param step
     *            which frame of the flash
     */
    private static void runFlash(final RingCycle cycle, final World world, final int step)
    {
        if (step >= RingAnimator.flashFrames())
        {
            swapAndHold(cycle, world);
            return;
        }
        cycle.drawFlash(ConfigManager.getRingFlashDirection(), step);
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        runFlash(cycle, world, step + 1);
                    }
                    catch (final RuntimeException e)
                    {
                        recover(cycle, world, e);
                    }
                }
            }, ConfigManager.getRingFlashTicks());
    }

    /**
     * Moves everybody, then stands still again before bringing the rings home.
     *
     * @param cycle
     *            the cycle running
     * @param world
     *            the world it is in
     */
    private static void swapAndHold(final RingCycle cycle, final World world)
    {
        final int travelled = cycle.flash();
        if (WormholeXTreme.getThisPlugin().isLoggable(Level.FINE))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                "Ring pair " + cycle.getPair().getId() + " carried " + travelled + " passengers.");
        }
        // Back to the plain stack: the light has passed and the travellers have gone.
        cycle.drawSettled();
        cycle.beginHold();
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        cycle.beginRetract();
                        step(cycle, world);
                    }
                    catch (final RuntimeException e)
                    {
                        recover(cycle, world, e);
                    }
                }
            }, ConfigManager.getRingHoldTicks());
    }

    /**
     * Puts a cycle down after it has run to the end.
     *
     * @param cycle
     *            the cycle that has finished
     * @param world
     *            the world it was in
     */
    private static void finished(final RingCycle cycle, final World world)
    {
        release(world, cycle.getPair());
        running.remove(cycle.getPair().getId());
    }

    /**
     * Salvages a cycle that threw partway through.
     *
     * <p>An exception mid-animation would otherwise leave the rings standing in somebody's
     * floor for good, the chunks pinned loaded, and the pair stuck in a phase that never
     * returns to idle so nobody can ever use it again. Whatever went wrong, the world is put
     * back and the pair is released.
     *
     * @param cycle
     *            the cycle that failed
     * @param world
     *            the world it was in
     * @param cause
     *            what went wrong
     */
    private static void recover(final RingCycle cycle, final World world, final RuntimeException cause)
    {
        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
            "Ring pair " + cycle.getPair().getId() + " failed mid-cycle, putting it back: "
                + cause.getMessage());
        try
        {
            cycle.finish(System.currentTimeMillis() + (ConfigManager.getRingCooldownTicks() * 50L));
        }
        // Even the clean-up failing must not leave the pair marked busy forever.
        catch (final RuntimeException ignored)
        {
            cycle.getPair().setPhase(RingPhase.IDLE);
        }
        finished(cycle, world);
    }

    /**
     * Pins both ends' chunks for the length of a cycle.
     *
     * @param world
     *            the world the pair is in
     * @param pair
     *            the pair running
     */
    private static void hold(final World world, final RingPair pair)
    {
        for (final Chunk chunk : chunksOf(world, pair))
        {
            chunk.addPluginChunkTicket(WormholeXTreme.getThisPlugin());
        }
    }

    /**
     * Lets both ends' chunks go again.
     *
     * @param world
     *            the world the pair is in
     * @param pair
     *            the pair that has finished
     */
    private static void release(final World world, final RingPair pair)
    {
        for (final Chunk chunk : chunksOf(world, pair))
        {
            chunk.removePluginChunkTicket(WormholeXTreme.getThisPlugin());
        }
    }

    /**
     * The chunks both ends sit in.
     *
     * <p>A ring is at most six blocks across, so it spans four chunks at the very worst;
     * taking the corners of each end's footprint covers it without walking every block.
     *
     * @param world
     *            the world the pair is in
     * @param pair
     *            the pair
     * @return the chunks to pin
     */
    private static Set<Chunk> chunksOf(final World world, final RingPair pair)
    {
        final Set<Chunk> chunks = new java.util.HashSet<Chunk>();
        for (final Ring ring : new Ring[] { pair.getEndA(), pair.getEndB() })
        {
            for (final int[] block : ring.perimeterBlocks())
            {
                chunks.add(world.getChunkAt(block[0] >> 4, block[2] >> 4));
            }
        }
        return chunks;
    }

    /**
     * The world a pair is in, if it is loaded.
     *
     * @param pair
     *            the pair
     * @return the world, or null if it is not loaded
     */
    private static World worldOf(final RingPair pair)
    {
        return WormholeXTreme.getThisPlugin().getServer().getWorld(pair.getWorldName());
    }

    /**
     * Forgets every running cycle, for shutdown.
     */
    public static void clear()
    {
        running.clear();
    }
}
