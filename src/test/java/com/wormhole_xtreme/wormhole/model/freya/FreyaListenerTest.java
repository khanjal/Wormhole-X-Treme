package com.wormhole_xtreme.wormhole.model.freya;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * The events that keep the companion with her owner and take away what a real cat would give.
 *
 * <p>Each handler is a line or two, and each is a promise made somewhere else: that she leaves
 * with her owner, that nothing hurts or leashes her, that a creeper or a bed sends her away and
 * she comes back after. A handler that stopped being called on would break the promise without
 * breaking anything the companion's own tests can see. Events are mocked because their
 * constructors differ across the server versions this plugin supports.
 */
class FreyaListenerTest
{
    private static final UUID OWNER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @TempDir
    File dataFolder;

    private final FreyaListener listener = new FreyaListener();
    private WormholeXTreme plugin;
    private BukkitScheduler scheduler;
    private World world;
    private Player owner;
    private Cat cat;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        final Server server = mock(Server.class);
        doReturn(Collections.emptyList()).when(server).getOnlinePlayers();
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        scheduler = mock(BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        FreyaCompanion.forgetAll();
        FreyaPreferences.clear();

        world = mock(World.class);
        cat = mock(Cat.class);
        when(cat.getLocation()).thenReturn(new Location(world, 1.5, 64.0, 1.5));
        when(world.spawn(any(Location.class), eq(Cat.class))).thenReturn(cat);
        owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(OWNER);
        when(owner.getName()).thenReturn("owner");
        when(owner.isOnline()).thenReturn(true);
        when(owner.getLocation()).thenReturn(new Location(world, 1.0, 64.0, 1.0));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        FreyaCompanion.forgetAll();
        FreyaPreferences.clear();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    /** Runs every task scheduled so far, once each, in order. */
    private void runScheduled()
    {
        final ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeastOnce()).scheduleSyncDelayedTask(eq(plugin), tasks.capture(), anyLong());
        for (final Runnable task : new ArrayList<>(tasks.getAllValues()))
        {
            task.run();
        }
    }

    /** Her owner has her turned on, and she is out beside them. */
    private void herOut()
    {
        FreyaPreferences.setEnabled(OWNER, true);
        FreyaCompanion.spawnFor(owner);
    }

    @Test
    void aJoiningOwnerGetsHerOnceTheyHaveArrived()
    {
        FreyaPreferences.setEnabled(OWNER, true);
        final PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(owner);

        listener.onJoin(join);
        assertEquals(0, FreyaCompanion.liveCount(), "not inside the event, before the player has a world");
        runScheduled();

        assertEquals(1, FreyaCompanion.liveCount(), "and there once the task runs");
    }

    @Test
    void aPlayerWhoNeverTurnedHerOnSchedulesNothing()
    {
        final PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(owner);

        listener.onJoin(join);

        verify(scheduler, never()).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
    }

    @Test
    void travellingChangingWorldAndRespawningEachCheckOnHer()
    {
        FreyaPreferences.setEnabled(OWNER, true);
        final PlayerTeleportEvent teleport = mock(PlayerTeleportEvent.class);
        when(teleport.getPlayer()).thenReturn(owner);
        final PlayerChangedWorldEvent changed = mock(PlayerChangedWorldEvent.class);
        when(changed.getPlayer()).thenReturn(owner);
        final PlayerRespawnEvent respawn = mock(PlayerRespawnEvent.class);
        when(respawn.getPlayer()).thenReturn(owner);

        listener.onTeleport(teleport);
        listener.onChangedWorld(changed);
        listener.onRespawn(respawn);
        runScheduled();

        assertEquals(1, FreyaCompanion.liveCount(), "three checks, still one cat");
    }

    @Test
    void leavingTheServerTakesHerAway()
    {
        herOut();
        final PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(owner);

        listener.onQuit(quit);

        assertEquals(0, FreyaCompanion.liveCount());
        verify(cat).remove();
    }

    @Test
    void nothingDamagesHerButOtherCatsStillTakeDamage()
    {
        herOut();
        final EntityDamageEvent hers = mock(EntityDamageEvent.class);
        when(hers.getEntity()).thenReturn(cat);
        final EntityDamageEvent stray = mock(EntityDamageEvent.class);
        when(stray.getEntity()).thenReturn(mock(Cat.class));

        listener.onDamage(hers);
        listener.onDamage(stray);

        verify(hers).setCancelled(true);
        verify(stray, never()).setCancelled(true);
    }

    @Test
    void sheCannotBeSatLeashedOrRenamed()
    {
        herOut();
        final PlayerInteractEntityEvent click = mock(PlayerInteractEntityEvent.class);
        when(click.getRightClicked()).thenReturn(cat);

        listener.onInteractEntity(click);

        verify(click).setCancelled(true);
    }

    @Test
    void aCreeperHuntingHerOwnerSendsHerAwayUntilItGivesUp()
    {
        herOut();
        final Creeper creeper = mock(Creeper.class);
        final EntityTargetLivingEntityEvent target = mock(EntityTargetLivingEntityEvent.class);
        when(target.getEntity()).thenReturn(creeper);
        when(target.getTarget()).thenReturn(owner);
        when(owner.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());

        listener.onTarget(target);
        assertTrue(FreyaCompanion.isAway(OWNER), "a creeper keeps away from a cat, so she must not be there");
        assertEquals(0, FreyaCompanion.liveCount());

        runScheduled();
        assertFalse(FreyaCompanion.isAway(OWNER), "nothing is hunting any more");
        assertEquals(1, FreyaCompanion.liveCount(), "so she comes back");
    }

    @Test
    void aZombieHuntingHerOwnerChangesNothing()
    {
        herOut();
        final EntityTargetLivingEntityEvent target = mock(EntityTargetLivingEntityEvent.class);
        when(target.getEntity()).thenReturn(mock(Zombie.class));
        when(target.getTarget()).thenReturn(owner);

        listener.onTarget(target);

        assertFalse(FreyaCompanion.isAway(OWNER), "zombies do not care about cats");
    }

    @Test
    void sheIsAwayForTheNightAndBackInTheMorning()
    {
        herOut();
        final PlayerBedEnterEvent bed = mock(PlayerBedEnterEvent.class);
        when(bed.getPlayer()).thenReturn(owner);
        when(bed.getBedEnterResult()).thenReturn(PlayerBedEnterEvent.BedEnterResult.OK);
        final PlayerBedLeaveEvent up = mock(PlayerBedLeaveEvent.class);
        when(up.getPlayer()).thenReturn(owner);

        listener.onBedEnter(bed);
        assertTrue(FreyaCompanion.isAway(OWNER), "no cat beside a sleeping owner, so no morning gift");

        listener.onBedLeave(up);
        runScheduled();
        assertFalse(FreyaCompanion.isAway(OWNER));
        assertEquals(1, FreyaCompanion.liveCount(), "back once they are up");
    }

    @Test
    void aBedTheyCouldNotGetIntoLeavesHerWhereSheIs()
    {
        herOut();
        final PlayerBedEnterEvent bed = mock(PlayerBedEnterEvent.class);
        when(bed.getPlayer()).thenReturn(owner);
        when(bed.getBedEnterResult()).thenReturn(PlayerBedEnterEvent.BedEnterResult.NOT_SAFE);

        listener.onBedEnter(bed);

        assertFalse(FreyaCompanion.isAway(OWNER), "they never slept, so there is no gift to prevent");
    }

    @Test
    void openingAChestSheSitsOnStandsHerUp()
    {
        herOut();
        final Block chest = mock(Block.class);
        when(chest.getType()).thenReturn(Material.CHEST);
        when(chest.getLocation()).thenReturn(new Location(world, 1.0, 63.0, 1.0));
        final PlayerInteractEvent click = mock(PlayerInteractEvent.class);
        when(click.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(click.getClickedBlock()).thenReturn(chest);

        listener.onChestOpen(click);

        verify(cat).setSitting(false);
    }

    @Test
    void clickingAnythingElseLeavesHerSitting()
    {
        herOut();
        final Block furnace = mock(Block.class);
        when(furnace.getType()).thenReturn(Material.FURNACE);
        when(furnace.getLocation()).thenReturn(new Location(world, 1.0, 63.0, 1.0));
        final PlayerInteractEvent click = mock(PlayerInteractEvent.class);
        when(click.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(click.getClickedBlock()).thenReturn(furnace);

        listener.onChestOpen(click);

        verify(cat, never()).setSitting(false);
    }

    @Test
    void onlyChestsAndTrappedChestsAreKeptShutByACat()
    {
        assertTrue(FreyaListener.isChest(Material.CHEST));
        assertTrue(FreyaListener.isChest(Material.TRAPPED_CHEST));
        assertFalse(FreyaListener.isChest(Material.BARREL), "a barrel opens whatever sits on it");
        assertFalse(FreyaListener.isChest(Material.ENDER_CHEST), "an ender chest only cares about a solid block");
    }
}
