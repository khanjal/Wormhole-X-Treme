package com.wormhole_xtreme.wormhole;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.scheduler.BukkitSchedulerMock;
import org.mockbukkit.mockbukkit.scheduler.RepeatingTask;
import org.mockbukkit.mockbukkit.world.ChunkMock;
import org.mockbukkit.mockbukkit.world.Coordinate;
import org.mockbukkit.mockbukkit.world.WorldMock;

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
    private static com.wormhole_xtreme.wormhole.config.ConfigSnapshot configBefore;

    /**
     * Starts a server and loads the plugin onto it, with a mirror's view drawn 4 blocks deep: a
     * capture copies every block in reach, and each one MockBukkit touches becomes an object.
     */
    static ServerMock start()
    {
        configBefore = com.wormhole_xtreme.wormhole.config.ConfigSnapshot.take();
        final ServerMock server = MockBukkit.mock(new Server());
        MockBukkit.load(WormholeXTreme.class);
        com.wormhole_xtreme.wormhole.config.ConfigTestSupport.set(
            com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.MIRROR_VIEW_DEPTH, 4);
        com.wormhole_xtreme.wormhole.model.mirror.MirrorCaptureSeam.readFromWorldBlocks();
        return server;
    }

    /**
     * Stops the server, and first empties the registries and settings the plugin's own disable
     * leaves full, so a Mockito test later in the fork does not inherit them.
     */
    static void stop()
    {
        try
        {
            com.wormhole_xtreme.wormhole.model.mirror.MirrorCaptureSeam.readFromServer();
            configBefore.restore();
            PluginTestSupport.forgetAllGates();
            com.wormhole_xtreme.wormhole.model.ring.RingManager.clear();
            com.wormhole_xtreme.wormhole.model.beam.BeamManager.clear();
            com.wormhole_xtreme.wormhole.model.mirror.MirrorManager.clear();
            com.wormhole_xtreme.wormhole.model.mirror.MirrorNetwork.clear();
            com.wormhole_xtreme.wormhole.model.mirror.MirrorProximity.clear();
            com.wormhole_xtreme.wormhole.model.mirror.MirrorCaptures.clear();
            com.wormhole_xtreme.wormhole.model.mirror.MirrorSettle.clear();
        }
        finally
        {
            MockBukkit.unmock();
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
            .map(t -> String.valueOf(((org.mockbukkit.mockbukkit.scheduler.ScheduledTask) t).getRunnable()))
            .toList();
    }

    /** The ids of the pending tasks that repeat until cancelled. */
    static Set<Integer> repeatingTasks(final ServerMock server)
    {
        final Set<Integer> ids = new java.util.TreeSet<>();
        server.getScheduler().getPendingTasks().stream().filter(t -> t instanceof RepeatingTask)
            .forEach(t -> ids.add(Integer.valueOf(t.getTaskId())));
        return ids;
    }

    /**
     * A server whose asynchronous tasks run on the next tick instead.
     *
     * <p>MockBukkit runs them on a pool, and a task the pool schedules back onto the main thread
     * can be lost to a race in its task list: a mirror capture then never finished, sometimes.
     */
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

        @Override
        @SuppressWarnings("unchecked")
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
            if (org.mockito.Mockito.mockingDetails(stored).isSpy())
            {
                return stored;
            }
            final BlockData data = org.mockito.Mockito.spy(stored);
            org.mockito.Mockito.doReturn(Boolean.valueOf(getType().isOccluding())).when(data).isOccluding();
            // A mirror's view clones, turns and flips what it copies; nothing asserts how it looks.
            org.mockito.Mockito.doReturn(data).when(data).clone();
            org.mockito.Mockito.doNothing().when(data).mirror(org.mockito.ArgumentMatchers.any());
            org.mockito.Mockito.doNothing().when(data).rotate(org.mockito.ArgumentMatchers.any());
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
        public void sendBlockChanges(final java.util.Collection<org.bukkit.block.BlockState> blocks)
        {
            // Nothing to draw on.
        }

        @Override
        public void sendBlockChanges(final java.util.Collection<org.bukkit.block.BlockState> blocks,
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
            final List<String> out = new java.util.ArrayList<>();
            String m;
            while ((m = nextMessage()) != null)
            {
                out.add(m.replaceAll("§.", ""));
            }
            return out;
        }
    }
}
