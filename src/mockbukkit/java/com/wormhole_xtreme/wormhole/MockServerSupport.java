package com.wormhole_xtreme.wormhole;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.scheduler.BukkitSchedulerMock;
import org.mockbukkit.mockbukkit.scheduler.RepeatingTask;
import org.mockbukkit.mockbukkit.scheduler.ScheduledTask;
import org.mockbukkit.mockbukkit.world.ChunkMock;
import org.mockbukkit.mockbukkit.world.Coordinate;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigSnapshot;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.window.CaptureSeam;
import com.wormhole_xtreme.wormhole.model.window.Captures;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorNetwork;
import com.wormhole_xtreme.wormhole.model.window.WindowSweep;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorSettle;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviewSeam;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;

/**
 * A MockBukkit server with the plugin on it, and stand-ins for the methods MockBukkit leaves
 * unimplemented that a trip through a gate, ring, beam or mirror calls.
 *
 * <p>Each stand-in is an approximation: passable is "not solid", occluding is the material's
 * own answer, a player sees exactly the block the test points them at.
 */
final class MockServerSupport
{
    private MockServerSupport()
    {
    }

    /** The settings as they were before the plugin loaded, put back by {@link #stop()}. */
    private static ConfigSnapshot configBefore;

    /**
     * Starts a server and loads the plugin onto it, with a mirror's view drawn 4 blocks deep: a
     * capture copies every block in reach, and each one MockBukkit touches becomes an object.
     */
    static ServerMock start()
    {
        configBefore = ConfigSnapshot.take();
        final ServerMock server = MockBukkit.mock(new Server());
        MockBukkit.load(WormholeXTreme.class);
        ConfigTestSupport.set(
            ConfigManager.ConfigKeys.MIRROR_VIEW_DEPTH, 4);
        CaptureSeam.readFromWorldBlocks();
        return server;
    }

    /**
     * Stops the server, so the plugin's own disable runs against live state, then empties the
     * registries and settings that disable leaves full, so the next MockBukkit class in the JVM
     * does not inherit them.
     */
    static void stop()
    {
        try
        {
            MockBukkit.unmock();
        }
        finally
        {
            CaptureSeam.readFromServer();
            configBefore.restore();
            PluginTestSupport.forgetAllGates();
            ProjectileGateTracker.clear();
            // A gate name's last redstone trigger, timed by the clock, would silence the next class's.
            WormholeXTremeRedstoneListener.clearTriggerHistory();
            GatePreviewSeam.clear();
            RingManager.clear();
            BeamManager.clear();
            MirrorManager.clear();
            MirrorNetwork.clear();
            WindowSweep.clear();
            Captures.clear();
            MirrorSettle.clear();
            CaptureSeam.forgetViewers();
        }
    }

    /** Longest {@link #settle} waits for one-off tasks to finish: 30 minutes of ticks. */
    static final int SETTLE_CAP_TICKS = 20 * 60 * 30;

    /** Shortest {@link #settle} runs, so a repeating task with an end, a capture, can reach it. */
    static final int SETTLE_MIN_TICKS = 20 * 60;

    /**
     * Runs the server for a minute, then until no one-off task is pending or
     * {@link #SETTLE_CAP_TICKS} have passed.
     *
     * <p>Waiting on the tasks rather than a fixed number of ticks: a gate's shutdown is timed
     * partly from the clock, so where it lands in ticks depends on how fast the test ran.
     */
    static void settle(final ServerMock server)
    {
        server.getScheduler().performTicks(SETTLE_MIN_TICKS);
        for (int t = SETTLE_MIN_TICKS; (t < SETTLE_CAP_TICKS) && (oneOffTasks(server) > 0); t += 100)
        {
            server.getScheduler().performTicks(100);
        }
    }

    /** Pending tasks that run once, a task that keeps rescheduling itself included. */
    static long oneOffTasks(final ServerMock server)
    {
        return server.getScheduler().getPendingTasks().stream().filter(t -> !(t instanceof RepeatingTask)).count();
    }

    /** What each pending one-off task runs, for a failure message. */
    static List<String> describeOneOffTasks(final ServerMock server)
    {
        return server.getScheduler().getPendingTasks().stream().filter(t -> !(t instanceof RepeatingTask))
            .map(t -> String.valueOf(((ScheduledTask) t).getRunnable()))
            .toList();
    }

    /** The ids of the pending tasks that repeat until cancelled. */
    static Set<Integer> repeatingTasks(final ServerMock server)
    {
        final Set<Integer> ids = new TreeSet<>();
        server.getScheduler().getPendingTasks().stream().filter(t -> t instanceof RepeatingTask)
            .forEach(t -> ids.add(Integer.valueOf(t.getTaskId())));
        return ids;
    }

    /**
     * A server whose asynchronous tasks run on the main thread instead, when they are due.
     *
     * <p>MockBukkit runs them on a pool, and a task the pool schedules back onto the main thread
     * can be lost to a race in its task list: a mirror capture then never finished, sometimes.
     * An asynchronous body now holds up the tick it runs in, as it would not on a server.
     */
    // ServerMock implements Server's generic getBanList with a raw BanList, and a subclass inherits the warning.
    @SuppressWarnings("unchecked")
    static final class Server extends ServerMock
    {
        // Not initialised in the declaration: ServerMock's constructor already asks for it.
        private BukkitSchedulerMock scheduler;

        @Override
        public BukkitSchedulerMock getScheduler()
        {
            if (scheduler == null)
            {
                scheduler = new BukkitSchedulerMock()
                {
                    @Override
                    public BukkitTask runTaskAsynchronously(final Plugin plugin, final Runnable task)
                    {
                        return runTask(plugin, task);
                    }

                    @Override
                    public BukkitTask runTaskLaterAsynchronously(final Plugin plugin, final Runnable task,
                        final long delay)
                    {
                        return runTaskLater(plugin, task, delay);
                    }

                    @Override
                    public BukkitTask runTaskTimerAsynchronously(final Plugin plugin, final Runnable task,
                        final long delay, final long period)
                    {
                        return runTaskTimer(plugin, task, delay, period);
                    }
                };
            }
            return scheduler;
        }
    }

    /** A stone world with its ground at y 63 and the chunks round the origin loaded. */
    static final class World extends WorldMock
    {
        private final Map<Long, ChunkMock> chunks = new HashMap<>();

        World(final String name, final int chunkRadius)
        {
            super(Material.STONE, -64, 320, 63);
            setName(name);
            for (int cx = -chunkRadius; cx <= chunkRadius; cx++)
            {
                for (int cz = -chunkRadius; cz <= chunkRadius; cz++)
                {
                    loadChunk(cx, cz);
                }
            }
        }

        // The cast of WorldMock's private map, read by reflection, has nothing to check against.
        @SuppressWarnings("unchecked")
        @Override
        public BlockMock createBlock(final Coordinate c)
        {
            final BlockMock block = new Block(super.createBlock(c));
            try
            {
                // createBlock caches what it made; ours goes in its place, so every lookup finds it.
                final Field blocks = WorldMock.class.getDeclaredField("blocks");
                blocks.setAccessible(true);
                ((Map<Coordinate, BlockMock>) blocks.get(this)).put(c, block);
            }
            catch (final ReflectiveOperationException e)
            {
                throw new IllegalStateException("MockBukkit's WorldMock has changed", e);
            }
            return block;
        }

        @Override
        public ChunkMock getChunkAt(final int x, final int z)
        {
            return chunks.computeIfAbsent((((long) x) << 32) ^ (z & 0xffffffffL), k -> new Chunk(this, x, z));
        }

        // A gate asks for its chunks to go once it shuts; nothing here is ever unloaded.
        @Override
        public boolean unloadChunkRequest(final int x, final int z)
        {
            return true;
        }

        // Unimplemented in MockBukkit; 0 leaves a mirror capture at the configured depth alone.
        @Override
        public int getViewDistance()
        {
            return 0;
        }

        /** Every chunk handed out that still holds a plugin ticket. */
        long ticketedChunks()
        {
            return chunks.values().stream().filter(ch -> !ch.getPluginChunkTickets().isEmpty()).count();
        }
    }

    /** A block that answers isPassable, and whose plain data answers isOccluding. */
    private static final class Block extends BlockMock
    {
        Block(final BlockMock base)
        {
            super(base.getType(), base.getLocation());
        }

        @Override
        public boolean isPassable()
        {
            return !getType().isSolid();
        }

        // A spy, so a banner's facing and a slab's half survive; only isOccluding is answered.
        @Override
        public BlockData getBlockData()
        {
            final BlockData stored = super.getBlockData();
            // Data set back from an earlier call is already one of these.
            if (Mockito.mockingDetails(stored).isSpy())
            {
                return stored;
            }
            final BlockData data = Mockito.spy(stored);
            Mockito.doReturn(Boolean.valueOf(getType().isOccluding())).when(data).isOccluding();
            // A mirror's view clones, turns and flips what it copies; nothing asserts how it looks.
            Mockito.doReturn(data).when(data).clone();
            Mockito.doNothing().when(data).mirror(ArgumentMatchers.any());
            Mockito.doNothing().when(data).rotate(ArgumentMatchers.any());
            return data;
        }
    }

    /** A chunk that holds plugin tickets, which a firing ring takes on both its ends. */
    private static final class Chunk extends ChunkMock
    {
        private final Set<Plugin> tickets = new HashSet<>();

        Chunk(final org.bukkit.World world, final int x, final int z)
        {
            super(world, x, z);
        }

        @Override
        public boolean addPluginChunkTicket(final Plugin plugin)
        {
            return tickets.add(plugin);
        }

        @Override
        public boolean removePluginChunkTicket(final Plugin plugin)
        {
            return tickets.remove(plugin);
        }

        @Override
        public Collection<Plugin> getPluginChunkTickets()
        {
            return Collections.unmodifiableSet(tickets);
        }
    }

    /** An opped player who sees whatever block the test points them at, and nothing else. */
    static final class Player extends PlayerMock
    {
        private org.bukkit.block.Block target;

        Player(final ServerMock server, final String name)
        {
            super(server, name);
            server.addPlayer(this);
            setOp(true);
        }

        void lookAt(final org.bukkit.block.Block block)
        {
            target = block;
        }

        @Override
        public org.bukkit.block.Block getTargetBlockExact(final int reach)
        {
            return target;
        }

        @Override
        public org.bukkit.block.Block getTargetBlockExact(final int reach, final FluidCollisionMode mode)
        {
            return target;
        }

        // A mirror's view is drawn on the client only; nothing here reads it back.
        @Override
        public void sendBlockChanges(final Collection<BlockState> blocks)
        {
            // Nothing to draw on.
        }

        @Override
        public void sendBlockChanges(final Collection<BlockState> blocks,
            final boolean suppressLightUpdates)
        {
            // Nothing to draw on.
        }

        // Small, so a mirror's view has few chunks to think the client holds.
        @Override
        public int getClientViewDistance()
        {
            return 2;
        }

        @Override
        public List<org.bukkit.block.Block> getLineOfSight(final Set<Material> transparent, final int reach)
        {
            return (target == null) ? List.of() : List.of(target);
        }

        /** Every chat line sent so far, colour codes stripped. */
        List<String> messages()
        {
            final List<String> out = new ArrayList<>();
            String m;
            while ((m = nextMessage()) != null)
            {
                out.add(m.replaceAll("§.", ""));
            }
            return out;
        }
    }
}
