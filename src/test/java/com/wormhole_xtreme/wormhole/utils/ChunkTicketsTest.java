package com.wormhole_xtreme.wormhole.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A chunk stays loaded until the last thing in the plugin holding it lets go.
 *
 * <p>Bukkit gives a plugin one ticket per chunk, not a count. A ring and a pet waiting to follow
 * (issue #505), or two owners' pets, can need the same chunk at once, and before this the first
 * to finish removed the ticket under the other: the chunk unloaded and the pet stayed behind.
 */
class ChunkTicketsTest
{
    private WormholeXTreme plugin;
    /** Held here because a mock world only a chunk refers to could be collected mid-test. */
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        ChunkTickets.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ChunkTickets.clear();
        PluginTestSupport.remove();
    }

    /**
     * A chunk as Bukkit hands it out: a fresh object each time, saying where it is.
     *
     * @return the chunk
     */
    private Chunk chunk(final World in, final int x, final int z)
    {
        final Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(in);
        when(chunk.getX()).thenReturn(x);
        when(chunk.getZ()).thenReturn(z);
        return chunk;
    }

    @Test
    void oneHolderTakesTheTicketAndGivesItBack()
    {
        final Chunk chunk = chunk(world, 3, -4);

        ChunkTickets.hold(chunk);
        verify(chunk).addPluginChunkTicket(plugin);
        ChunkTickets.release(chunk);

        verify(chunk).removePluginChunkTicket(plugin);
    }

    /**
     * The same chunk, asked for twice, is two holders; Bukkit's own Chunk objects are not the same
     * object for the same chunk, so the count goes by world and position.
     */
    @Test
    void theTicketStaysUntilTheSecondHolderLetsGo()
    {
        final Chunk ring = chunk(world, 3, -4);
        final Chunk pet = chunk(world, 3, -4);

        ChunkTickets.hold(ring);
        ChunkTickets.hold(pet);
        ChunkTickets.release(ring);

        verify(ring).addPluginChunkTicket(plugin);
        verify(ring, never()).removePluginChunkTicket(any());
        verify(pet, never()).removePluginChunkTicket(any());

        ChunkTickets.release(pet);
        verify(pet).removePluginChunkTicket(plugin);
    }

    @Test
    void theSamePositionInAnotherWorldIsAnotherChunk()
    {
        final World other = mock(World.class);
        when(other.getUID()).thenReturn(UUID.randomUUID());
        final Chunk here = chunk(world, 0, 0);
        final Chunk there = chunk(other, 0, 0);

        ChunkTickets.hold(here);
        ChunkTickets.hold(there);
        ChunkTickets.release(there);

        verify(there).addPluginChunkTicket(plugin);
        verify(there).removePluginChunkTicket(plugin);
        verify(here, never()).removePluginChunkTicket(any());
    }

    /**
     * A second hold takes the ticket again. A world unloaded and loaded again under the same UID
     * loses its tickets but not this count, and trusting the count left the chunk unheld: #505
     * again, silently.
     */
    @Test
    void aSecondHoldTakesTheTicketAgainInCaseItWasLostWithItsWorld()
    {
        final Chunk before = chunk(world, 5, 5);
        final Chunk afterReload = chunk(world, 5, 5);

        ChunkTickets.hold(before);
        ChunkTickets.hold(afterReload);

        verify(afterReload).addPluginChunkTicket(plugin);
    }

    /** A release nobody held for must not take a ticket something outside the count put there. */
    @Test
    void aReleaseWithoutAHoldTakesNothing()
    {
        final Chunk chunk = chunk(world, 1, 1);

        ChunkTickets.hold(chunk);
        ChunkTickets.release(chunk);
        ChunkTickets.release(chunk);

        verify(chunk, times(1)).removePluginChunkTicket(plugin);
    }
}
