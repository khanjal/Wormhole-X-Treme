package com.wormhole_xtreme.wormhole.model.ping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.HeldScheduler;
import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.utils.ChunkTickets;

/**
 * The locator bar ping's markers: each is a real entity holding its chunk loaded, so every way it
 * can end has to take the entity down, let the chunk go and cancel whatever was still waiting to
 * do either (#16).
 *
 * <p>The scheduler holds every task and honours cancel, so a path that forgot to cancel its
 * expiry shows up as a task still pending, and one that never scheduled one shows up as nothing
 * pending at all.
 */
class PingMarkersTest
{
    private final HeldScheduler held = new HeldScheduler();

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private final List<ArmorStand> spawned = new ArrayList<>();

    private WormholeXTreme plugin;

    private Attribute transmit;

    private World world;

    private Chunk chunk;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = PluginTestSupport.install();
        held.install();
        ConfigTestSupport.loadDefaults();
        transmit = mock(Attribute.class);
        Waypoints.lookupWith(key -> Waypoints.TRANSMIT_KEY.equals(key) ? transmit : null);
        PingMarkers.clockWith(now::get);
        PingMarkers.spawnWith(at -> stand(true));
        world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        chunk = chunkIn(world);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PingMarkers.reset();
        Waypoints.lookupWith(null);
        ChunkTickets.clear();
        ConfigTestSupport.clear();
        held.remove();
        PluginTestSupport.remove();
    }

    /** @return a chunk of this world, which a location there hands back */
    private static Chunk chunkIn(final World world)
    {
        final Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getChunkAt(any(Location.class))).thenReturn(chunk);
        return chunk;
    }

    /**
     * A new armour stand, recorded as spawned.
     *
     * @param hasRange
     *            whether it has a transmit range attribute; one that does keeps what it is set to
     */
    private ArmorStand stand(final boolean hasRange)
    {
        final ArmorStand stand = mock(ArmorStand.class);
        final Set<String> tags = new HashSet<>();
        doAnswer(call -> tags.add(call.getArgument(0))).when(stand).addScoreboardTag(any());
        when(stand.getScoreboardTags()).thenReturn(tags);
        if (hasRange)
        {
            final AttributeInstance instance = mock(AttributeInstance.class);
            final double[] base = { 0 };
            doAnswer(call ->
            {
                base[0] = call.getArgument(0, Double.class).doubleValue();
                return null;
            }).when(instance).setBaseValue(anyDouble());
            when(instance.getValue()).thenAnswer(call -> Double.valueOf(base[0]));
            when(instance.getBaseValue()).thenAnswer(call -> Double.valueOf(base[0]));
            when(stand.getAttribute(transmit)).thenReturn(instance);
        }
        spawned.add(stand);
        return stand;
    }

    private static Player player(final UUID id)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }

    private Location gate()
    {
        return new Location(world, 10, 64, 10);
    }

    /** A ping stands until its time is up, and then its entity and its chunk ticket both go. */
    @Test
    void aPingIsTakenDownWhenItsTimeIsUp()
    {
        final Player owner = player(UUID.randomUUID());

        assertEquals(PingMarkers.Outcome.PLACED, PingMarkers.place(owner, gate()));
        assertEquals(1, PingMarkers.live());
        assertEquals(List.of(Long.valueOf(8 * 20L)), held.delays(), "one expiry, ping-seconds from now");
        verify(chunk).addPluginChunkTicket(plugin);

        held.runNext();

        verify(spawned.get(0)).remove();
        verify(chunk).removePluginChunkTicket(plugin);
        assertEquals(0, PingMarkers.live(), "an expired ping must leave the registry");
    }

    /**
     * The expiry is keyed by id and holds no player, so a marker whose owner has gone without
     * a quit reaching the plugin still comes down on time.
     */
    @Test
    void theExpiryTakesTheMarkerDownWhenItsOwnerIsNoLongerOnline()
    {
        final Player owner = player(UUID.randomUUID());
        PingMarkers.place(owner, gate());
        when(owner.isOnline()).thenReturn(false);

        held.runNext();

        verify(spawned.get(0)).remove();
        assertEquals(0, PingMarkers.live());
    }

    /** Leaving takes the ping down at once and cancels the expiry that would have done it later. */
    @Test
    void quittingTakesThePingDownAndCancelsItsExpiry()
    {
        final Player owner = player(UUID.randomUUID());
        PingMarkers.place(owner, gate());

        final PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(owner);

        new PingListener().onQuit(quit);

        verify(spawned.get(0)).remove();
        verify(chunk).removePluginChunkTicket(plugin);
        assertEquals(0, PingMarkers.live());
        assertEquals(0, held.pending(), "the expiry must be cancelled, not left to fire on nothing");
        assertEquals(1, held.cancelled());
    }

    /** A world unloading takes its own pings with it and leaves another world's standing. */
    @Test
    void aWorldUnloadingTakesDownOnlyItsOwnPings()
    {
        final World other = mock(World.class);
        when(other.getUID()).thenReturn(UUID.randomUUID());
        chunkIn(other);
        PingMarkers.place(player(UUID.randomUUID()), gate());
        PingMarkers.place(player(UUID.randomUUID()), new Location(other, 0, 64, 0));

        final WorldUnloadEvent unload = mock(WorldUnloadEvent.class);
        when(unload.getWorld()).thenReturn(world);

        new PingListener().onWorldUnload(unload);

        verify(spawned.get(0)).remove();
        verify(spawned.get(1), never()).remove();
        assertEquals(1, PingMarkers.live(), "the other world's ping is still up");
        assertEquals(1, held.pending(), "and so is its expiry");
    }

    /** Stopping the plugin, or turning ping off, takes every ping down and leaves no task behind. */
    @Test
    void removingAllEmptiesTheRegistryAndCancelsEveryExpiry()
    {
        PingMarkers.place(player(UUID.randomUUID()), gate());
        PingMarkers.place(player(UUID.randomUUID()), gate());

        assertEquals(2, PingMarkers.removeAll());

        spawned.forEach(stand -> verify(stand).remove());
        assertEquals(0, PingMarkers.live());
        assertEquals(0, held.pending());
        assertEquals(2, held.cancelled());
    }

    /** One ping each: a second replaces the first rather than adding to it. */
    @Test
    void aSecondPingReplacesThePlayersFirst()
    {
        final Player owner = player(UUID.randomUUID());
        PingMarkers.place(owner, gate());
        PingMarkers.place(owner, gate());

        assertEquals(2, spawned.size());
        assertNotSame(spawned.get(0), spawned.get(1));
        verify(spawned.get(0)).remove();
        verify(spawned.get(1), never()).remove();
        assertEquals(1, PingMarkers.live(), "a player has one ping at a time");
        assertEquals(1, held.pending(), "the first ping's expiry was cancelled with it");
    }

    /**
     * The first ping's expiry, should it still run, leaves the second alone: it is for the stand
     * it was scheduled with, not whatever that player has now.
     */
    @Test
    void anOldExpiryLeavesTheNewerPingStanding()
    {
        final Player owner = player(UUID.randomUUID());
        PingMarkers.place(owner, gate());
        PingMarkers.place(owner, gate());

        held.runEvenIfCancelled(0);

        verify(spawned.get(1), never()).remove();
        assertEquals(1, PingMarkers.live());
    }

    /** The cooldown counts from the ping, in whole seconds rounded up, and ends on time. */
    @Test
    void theCooldownCountsDownFromThePing()
    {
        final UUID id = UUID.randomUUID();
        assertEquals(0, PingMarkers.cooldownLeft(id), "nobody waits before their first ping");

        PingMarkers.place(player(id), gate());
        assertEquals(20, PingMarkers.cooldownLeft(id));

        now.addAndGet(19_001L);
        assertEquals(1, PingMarkers.cooldownLeft(id), "a part second still left is a second to wait");

        now.addAndGet(999L);
        assertEquals(0, PingMarkers.cooldownLeft(id));
    }

    /** On a server before 1.21.6 nothing is spawned and no chunk is held. */
    @Test
    void withNoLocatorBarNothingIsSpawned()
    {
        Waypoints.lookupWith(key -> null);

        assertEquals(PingMarkers.Outcome.UNSUPPORTED, PingMarkers.place(player(UUID.randomUUID()), gate()));

        assertTrue(spawned.isEmpty());
        verify(chunk, never()).addPluginChunkTicket(any());
        assertEquals(0, held.pending());
    }

    /** A marker that will not take the transmit range is taken down again, never left standing. */
    @Test
    void aMarkerWithoutTheAttributeIsRemovedAgain()
    {
        PingMarkers.spawnWith(at -> stand(false));

        assertEquals(PingMarkers.Outcome.FAILED, PingMarkers.place(player(UUID.randomUUID()), gate()));

        verify(spawned.get(0)).remove();
        verify(chunk).removePluginChunkTicket(plugin);
        assertEquals(0, PingMarkers.live());
        assertEquals(0, held.pending());
    }

    /** The range is read back, not assumed: a server that keeps it at 0 gets no marker either. */
    @Test
    void aMarkerThatKeepsARangeOfNothingIsRemovedAgain()
    {
        PingMarkers.spawnWith(at ->
        {
            final ArmorStand stand = stand(false);
            final AttributeInstance stuck = mock(AttributeInstance.class);
            when(stand.getAttribute(transmit)).thenReturn(stuck);
            return stand;
        });

        assertEquals(PingMarkers.Outcome.FAILED, PingMarkers.place(player(UUID.randomUUID()), gate()));

        verify(spawned.get(0)).remove();
        assertEquals(0, PingMarkers.live());
    }

    /**
     * The marker is set up so nobody can see, hit, push or hear it, it is never saved, it is
     * tagged for the orphan sweep, it is shown to its owner, and it transmits as far as the
     * attribute allows.
     */
    @Test
    void theMarkerIsInvisibleUnsavedTaggedAndTransmitting()
    {
        final Player owner = player(UUID.randomUUID());
        PingMarkers.place(owner, gate());
        final ArmorStand stand = spawned.get(0);

        verify(stand).setMarker(true);
        verify(stand).setInvisible(true);
        verify(stand).setGravity(false);
        verify(stand).setSilent(true);
        verify(stand).setInvulnerable(true);
        verify(stand).setCollidable(false);
        verify(stand).setPersistent(false);
        verify(stand).setVisibleByDefault(false);
        verify(owner).showEntity(plugin, stand);
        assertTrue(stand.getScoreboardTags().contains(PingMarkers.TAG));
        assertEquals(Waypoints.TRANSMIT_RANGE, stand.getAttribute(transmit).getBaseValue());
    }

    /** At startup a tagged stand the registry does not hold is removed; its own and others are left. */
    @Test
    void theSweepRemovesOnlyTaggedStandsNoPingHolds()
    {
        PingMarkers.place(player(UUID.randomUUID()), gate());
        final ArmorStand live = spawned.get(0);
        final ArmorStand orphan = stand(true);
        orphan.addScoreboardTag(PingMarkers.TAG);
        final ArmorStand unrelated = stand(true);
        when(world.getEntitiesByClass(ArmorStand.class)).thenReturn(List.of(live, orphan, unrelated));

        assertEquals(1, PingMarkers.sweepOrphans(List.of(world)));

        verify(orphan).remove();
        verify(live, never()).remove();
        verify(unrelated, never()).remove();
    }
}
