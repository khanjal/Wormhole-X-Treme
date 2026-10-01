package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateTestSupport;

/**
 * Catching a thrown or dispensed item as it crosses a gate (#537).
 *
 * <p>Items travelled only by the once-a-second sweep, which sees an item only if it is in the
 * opening when the sweep runs. One tossed by a player or fired by a dispenser crosses the
 * one-block opening in a tick or two and lands behind the ring, so in the test facility most of
 * five tossed and five dispensed items missed the gate. Here the path between ticks is walked,
 * as it is for an arrow.
 */
class ItemGateTrackerTest
{
    private World world;
    private Stargate origin;
    private Stargate destination;
    private Item item;
    private boolean itemValid;
    private Location itemAt;
    private Runnable ticker;

    /** Tasks booked on the scheduler and not yet run, by id; a cancelled one is really dropped. */
    private final Map<Integer, Runnable> pending = new LinkedHashMap<>();
    private int nextTask = 1;

    private static final int BX = 10, BY = 64, BZ = 20;

    /** Where the destination gate's portal is, for the tests that need one. */
    private static final int DX = 40;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        ItemGateTracker.clear();

        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);

        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong())).thenAnswer(inv ->
        {
            final int id = nextTask++;
            pending.put(Integer.valueOf(id), inv.getArgument(1, Runnable.class));
            return Integer.valueOf(id);
        });
        doAnswer(inv -> pending.remove(Integer.valueOf(inv.getArgument(0, Integer.class).intValue())))
            .when(scheduler).cancelTask(anyInt());
        PluginTestSupport.scheduler(scheduler);

        world = mock(World.class);
        when(world.getName()).thenReturn("w");
        final Block elsewhere = mock(Block.class);
        when(elsewhere.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        when(elsewhere.getWorld()).thenReturn(world);
        when(elsewhere.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(elsewhere);
        final Block portal = portalBlock(BX);

        destination = new Stargate();
        destination.setGateName("destination");
        destination.setGateWorld(world);
        destination.setGateFacing(BlockFace.EAST);
        destination.setGateActive(true);
        destination.setGatePortalOpen(true);
        destination.setGatePlayerTeleportLocation(new Location(world, DX + 0.5, BY, BZ + 0.5));

        origin = new Stargate();
        origin.setGateName("origin");
        origin.setGateWorld(world);
        origin.setGateFacing(BlockFace.NORTH);
        origin.setGateActive(true);
        origin.setGatePortalOpen(true);
        origin.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ + 0.5));
        origin.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        StargateTestSupport.target(origin, destination);
        StargateManager.addBlockIndex(portal, origin);
        StargateManager.registerStargate(origin);

        item = mock(Item.class);
        itemValid = true;
        when(item.getUniqueId()).thenReturn(UUID.randomUUID());
        when(item.isValid()).thenAnswer(inv -> Boolean.valueOf(itemValid));
        doAnswer(inv -> { itemValid = false; return null; }).when(item).remove();
        when(item.getVelocity()).thenReturn(new Vector(0, 0, -0.5));
        when(item.getLocation()).thenAnswer(inv -> itemAt);
        when(item.teleport(any(Location.class))).thenReturn(Boolean.TRUE);
        itemAt(BX + 0.5, BY, BZ + 3.5);

        ticker = ItemGateTracker.createTicker();
    }

    @AfterEach
    void tearDown()
    {
        StargateManager.removeStargate(origin);
        StargateManager.removeStargate(destination);
        ItemGateTracker.clear();
        GateSpatialIndex.clear();
        PluginTestSupport.forgetAllGates();
    }

    /** A one-block portal at (x, BY, BZ). */
    private Block portalBlock(final int x)
    {
        final Block portal = mock(Block.class);
        when(portal.getLocation()).thenReturn(new Location(world, x, BY, BZ));
        when(portal.getX()).thenReturn(Integer.valueOf(x));
        when(portal.getY()).thenReturn(Integer.valueOf(BY));
        when(portal.getZ()).thenReturn(Integer.valueOf(BZ));
        when(portal.getWorld()).thenReturn(world);
        when(portal.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(x, BY, BZ)).thenReturn(portal);
        return portal;
    }

    private void itemAt(final double x, final double y, final double z)
    {
        itemAt = new Location(world, x, y, z);
    }

    /** The item, thrown by a player from where it is now. */
    private void toss()
    {
        new ItemGateTracker().onPlayerDropItem(new PlayerDropItemEvent(mock(Player.class), item));
    }

    /** Runs whatever the scheduler is holding, as the server would over the next second. */
    private void runPending()
    {
        final List<Runnable> due = new ArrayList<>(pending.values());
        pending.clear();
        due.forEach(Runnable::run);
    }

    /**
     * The facility's failure: an item four blocks short of the opening on one tick and two past it
     * on the next was never inside it when looked at, and landed behind the ring.
     */
    @Test
    void aTossedItemWhosePathCrossesTheOpeningBetweenTicksIsSent()
    {
        toss();
        ticker.run();
        verify(item, never()).teleport(any(Location.class));

        itemAt(BX + 0.5, BY, BZ - 2.5);
        ticker.run();

        final ArgumentCaptor<Location> sent = ArgumentCaptor.forClass(Location.class);
        verify(item).teleport(sent.capture());
        assertEquals(DX + 1.5, sent.getValue().getX(), 0.01,
            "it comes out in front of the far gate, not behind the near one");
        verify(item, never()).remove();
    }

    @Test
    void anItemWhosePathMissesTheOpeningIsNotSent()
    {
        itemAt(BX + 3.5, BY, BZ + 3.5);
        toss();
        ticker.run();
        itemAt(BX + 3.5, BY, BZ - 3.5);
        ticker.run();

        verify(item, never()).teleport(any(Location.class));
        assertEquals(1, ItemGateTracker.trackedCount(), "still followed: it has not landed");
    }

    /** A dispenser's shot raises no drop event; it is found as an item spawning in motion. */
    @Test
    void aDispensedItemIsFollowedAndSent()
    {
        new ItemGateTracker().onItemSpawn(new ItemSpawnEvent(item));
        assertEquals(1, ItemGateTracker.trackedCount());

        itemAt(BX + 0.5, BY, BZ - 1.5);
        ticker.run();

        verify(item).teleport(any(Location.class));
    }

    /** An item that comes into the world lying still is the sweep's, as it always was. */
    @Test
    void anItemSpawningStillIsLeftToTheSweep()
    {
        when(item.getVelocity()).thenReturn(new Vector(0, 0, 0));

        new ItemGateTracker().onItemSpawn(new ItemSpawnEvent(item));

        assertEquals(0, ItemGateTracker.trackedCount());
    }

    @Test
    void anItemThrownFarFromAnyGateIsNotFollowed()
    {
        itemAt(BX + 30.5, BY, BZ + 3.5);

        toss();

        assertEquals(0, ItemGateTracker.trackedCount());
    }

    /** The far gate shut its iris after the wormhole opened: the item is destroyed, not delivered. */
    @Test
    void aShutIrisAtTheFarEndDestroysTheItem()
    {
        destination.setGateIrisActive(true);
        toss();
        itemAt(BX + 0.5, BY, BZ - 2.5);

        ticker.run();

        verify(item).remove();
        verify(item, never()).teleport(any(Location.class));
        assertEquals(0, ItemGateTracker.trackedCount(), "and it is followed no further");
    }

    /** An idle gate's drawn iris is air to the server; a thrown item must not fly through it. */
    @Test
    void anIdleGatesShutIrisDestroysTheItem()
    {
        origin.setGateActive(false);
        origin.setGateIrisActive(true);
        toss();
        assertEquals(1, ItemGateTracker.trackedCount(), "followed near a shut iris, though no gate is open");

        itemAt(BX + 0.5, BY, BZ - 2.5);
        ticker.run();

        verify(item).remove();
    }

    @Test
    void anItemIsForgottenOnceItLands()
    {
        toss();
        ticker.run();
        assertEquals(1, ItemGateTracker.trackedCount(), "followed while in the air");

        when(item.isOnGround()).thenReturn(Boolean.TRUE);
        itemAt(BX + 0.5, BY, BZ + 2.5);
        ticker.run();

        assertEquals(0, ItemGateTracker.trackedCount(), "a landed item is the sweep's again");
    }

    /** An item landing in the opening on the tick it got there still goes through then. */
    @Test
    void anItemThatLandsInTheOpeningIsSentOnThatTick()
    {
        toss();
        when(item.isOnGround()).thenReturn(Boolean.TRUE);
        itemAt(BX + 0.5, BY, BZ + 0.5);

        ticker.run();

        verify(item).teleport(any(Location.class));
    }

    @Test
    void anItemThatNeverLandsIsForgottenAfterAFewSeconds()
    {
        toss();
        itemAt(BX + 0.5, BY + 50, BZ + 3.5);
        for (int i = 0; i < 100; i++)
        {
            ticker.run();
        }
        assertEquals(1, ItemGateTracker.trackedCount(), "still followed within its time");

        ticker.run();
        ticker.run();

        assertEquals(0, ItemGateTracker.trackedCount(), "dropped once its time is up");
    }

    @Test
    void anItemPickedUpIsForgotten()
    {
        toss();
        itemValid = false;

        ticker.run();

        assertEquals(0, ItemGateTracker.trackedCount());
    }

    /** An item farm beside a gate cannot grow the followed set without end. */
    @Test
    void onlySoManyItemsAreFollowedAtOnce()
    {
        for (int i = 0; i <= ItemGateTracker.MOST_TRACKED; i++)
        {
            final Item another = mock(Item.class);
            when(another.getLocation()).thenReturn(new Location(world, BX + 0.5, BY, BZ + 3.5));
            new ItemGateTracker().onPlayerDropItem(new PlayerDropItemEvent(mock(Player.class), another));
        }

        assertEquals(ItemGateTracker.MOST_TRACKED, ItemGateTracker.trackedCount());
    }

    /**
     * A long jump between ticks is a teleport, not a flight, and is not walked.
     *
     * <p>Walking it would cross every gate between the two places: an item the sweep sent through
     * one gate would be sent through another that merely lay between them.
     */
    @Test
    void anItemMovedFarInOneTickIsNotWalkedAcrossTheGate()
    {
        toss();
        ticker.run();

        itemAt(BX + 0.5, BY, BZ - 20.5);
        ticker.run();

        verify(item, never()).teleport(any(Location.class));
    }

    /**
     * Facing gates hand an item back and forth a bounded number of times, as they do an arrow.
     *
     * <p>Here the item is back in the near opening every tick, as it would be flying out of a far
     * gate facing it, and the one-second guard against bouncing back is allowed to lapse.
     */
    @Test
    void anItemBetweenFacingGatesCrossesABoundedNumberOfTimes()
    {
        itemAt(BX + 0.5, BY, BZ + 0.5);
        toss();

        for (int i = 0; i < 20; i++)
        {
            ticker.run();
            runPending();
        }

        verify(item, times(ItemGateTracker.MOST_CROSSINGS)).teleport(any(Location.class));
        assertEquals(0, ItemGateTracker.trackedCount(), "and then it is let fall");
    }

    /**
     * An item just sent is not sent again while the one-second guard holds, as the sweep's are not:
     * otherwise one arriving in a facing gate's opening crosses every tick.
     */
    @Test
    void anItemJustSentIsNotSentAgainWithinTheSecond()
    {
        itemAt(BX + 0.5, BY, BZ + 0.5);
        toss();

        for (int i = 0; i < 5; i++)
        {
            ticker.run();
        }

        verify(item, times(1)).teleport(any(Location.class));
    }

    /** An item coming out of a gate is not sent straight back into it. */
    @Test
    void anItemIsNotSentBackIntoTheGateItCameOutOf()
    {
        final Block exitPortal = portalBlock(DX);
        destination.getGatePortalBlocks().add(new Location(world, DX, BY, BZ));
        StargateTestSupport.target(destination, origin);
        StargateManager.addBlockIndex(exitPortal, destination);
        StargateManager.registerStargate(destination);
        when(item.teleport(any(Location.class))).thenAnswer(inv ->
        {
            itemAt(DX + 0.5, BY, BZ + 0.5);
            return Boolean.TRUE;
        });
        itemAt(BX + 0.5, BY, BZ + 0.5);
        toss();

        ticker.run();
        runPending();
        ticker.run();

        verify(item, times(1)).teleport(any(Location.class));
        assertEquals(1, ItemGateTracker.trackedCount(), "still followed after its one crossing");
    }

    @Test
    void anItemThatThrowsIsDroppedRatherThanRetriedForever()
    {
        toss();
        when(item.getLocation()).thenThrow(new IllegalStateException("entity gone"));

        assertDoesNotThrow(() -> ticker.run());

        assertEquals(0, ItemGateTracker.trackedCount());
    }
}
