package com.wormhole_xtreme.wormhole.model.freya;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * One companion per player, seen only by them, and never left behind or left over.
 *
 * <p>The command is open to everyone with no cooldown, so replacing rather than adding on each
 * spawn is the only thing between it and a world full of cats. And vanilla pet following never
 * crosses worlds, so without {@link FreyaCompanion#catchUp} the first gate trip would lose her.
 */
class FreyaCompanionTest
{
    private static final UUID OWNER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID OTHER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TempDir
    File dataFolder;

    private WormholeXTreme plugin;
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        final Server server = mock(Server.class);
        doReturn(Collections.emptyList()).when(server).getOnlinePlayers();
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        world = mock(World.class);
        FreyaCompanion.forgetAll();
        FreyaPreferences.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        FreyaCompanion.forgetAll();
        FreyaPreferences.clear();
        PluginTestSupport.remove();
    }

    /**
     * A player standing somewhere, whose world will hand back the given cat.
     *
     * @param id
     *            their player id
     * @param cat
     *            what the world should spawn for them
     * @return the mock player
     */
    private Player playerWith(final UUID id, final Cat cat)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getLocation()).thenReturn(new Location(world, 1.0, 64.0, 1.0));
        when(world.spawn(any(Location.class), eq(Cat.class))).thenReturn(cat);
        return player;
    }

    /**
     * A live cat standing at the given spot.
     *
     * @param where
     *            where she is
     * @return the mock cat
     */
    private static Cat catAt(final Location where)
    {
        final Cat cat = liveCat();
        when(cat.getLocation()).thenReturn(where);
        return cat;
    }

    /** @return a cat that is in the world and not dead */
    private static Cat liveCat()
    {
        final Cat cat = mock(Cat.class);
        when(cat.isValid()).thenReturn(true);
        when(cat.isDead()).thenReturn(false);
        return cat;
    }

    @Test
    void askingTwiceReplacesTheCatRatherThanAddingASecond()
    {
        final Cat first = liveCat();
        final Player player = playerWith(OWNER, first);

        FreyaCompanion.spawnFor(player);
        assertEquals(1, FreyaCompanion.liveCount(), "one ask, one cat");

        final Cat second = liveCat();
        when(world.spawn(any(Location.class), eq(Cat.class))).thenReturn(second);
        FreyaCompanion.spawnFor(player);

        assertEquals(1, FreyaCompanion.liveCount(),
            "a player has one companion however many times they ask");
        verify(first).remove();
    }

    @Test
    void twoPlayersEachGetTheirOwn()
    {
        final Player one = playerWith(OWNER, liveCat());
        FreyaCompanion.spawnFor(one);
        final Player two = playerWith(OTHER, liveCat());
        FreyaCompanion.spawnFor(two);

        assertEquals(2, FreyaCompanion.liveCount(), "one each, not one between them");
    }

    @Test
    void sheIsMarkedNotToPersistSoNoWorldEverStoresHer()
    {
        final Cat cat = liveCat();
        FreyaCompanion.spawnFor(playerWith(OWNER, cat));

        verify(cat).setPersistent(false);
        verify(cat).setInvulnerable(true);
        verify(cat).setCollidable(false);
        verify(cat).setSilent(true);
        verify(cat).setTamed(true);
        // Following is vanilla tamed-cat AI; turning AI off would stop it.
        verify(cat, never()).setAI(false);
    }

    @Test
    void sheIsHiddenFromEveryoneAndShownOnlyToHerOwner()
    {
        final Cat cat = liveCat();
        final Player owner = playerWith(OWNER, cat);

        FreyaCompanion.spawnFor(owner);

        verify(cat).setVisibleByDefault(false);
        verify(owner).showEntity(plugin, cat);
    }

    @Test
    void leavingTakesHerWithThem()
    {
        final Cat cat = liveCat();
        FreyaCompanion.spawnFor(playerWith(OWNER, cat));

        assertTrue(FreyaCompanion.removeFor(OWNER), "there was one to remove");
        verify(cat, times(1)).remove();
        assertEquals(0, FreyaCompanion.liveCount(), "and nothing is still tracked");
        assertFalse(FreyaCompanion.removeFor(OWNER), "removing again is not an error");
    }

    @Test
    void aPlayerWithNowhereToStandGetsNoCatRatherThanAnException()
    {
        final Player nowhere = mock(Player.class);
        when(nowhere.getUniqueId()).thenReturn(OWNER);
        when(nowhere.getLocation()).thenReturn(null);

        assertNull(FreyaCompanion.spawnFor(nowhere),
            "a cosmetic command must fail quietly, not throw out of a join handler");
        assertEquals(0, FreyaCompanion.liveCount(), "and nothing is tracked for them");
    }

    @Test
    void onlyTheTrackedCatsCountAsCompanions()
    {
        final Cat hers = liveCat();
        FreyaCompanion.spawnFor(playerWith(OWNER, hers));
        final Cat someoneElsesPet = liveCat();

        assertTrue(FreyaCompanion.isCompanion(hers), "the one we spawned is ours");
        assertFalse(FreyaCompanion.isCompanion(someoneElsesPet),
            "a player's own black cat called Freya is still an ordinary cat");
    }

    @Test
    void shuttingDownTakesEveryCompanionAway()
    {
        final Cat one = liveCat();
        FreyaCompanion.spawnFor(playerWith(OWNER, one));
        final Cat two = liveCat();
        FreyaCompanion.spawnFor(playerWith(OTHER, two));

        FreyaCompanion.removeAll();

        assertEquals(0, FreyaCompanion.liveCount(), "nothing tracked after a shutdown");
        verify(one).remove();
        verify(two).remove();
    }

    @Test
    void aCatInAnotherWorldHasBeenLeftBehind()
    {
        final Cat cat = catAt(new Location(mock(World.class), 1.0, 64.0, 1.0));

        assertTrue(FreyaCompanion.isLeftBehind(cat, new Location(world, 1.0, 64.0, 1.0)),
            "vanilla following never crosses worlds, so a gate to another world would lose her");
    }

    @Test
    void aCatFarAwayInTheSameWorldHasBeenLeftBehind()
    {
        final Cat cat = catAt(new Location(world, 1.0, 64.0, 1.0));

        assertTrue(FreyaCompanion.isLeftBehind(cat, new Location(world, 500.0, 64.0, 1.0)),
            "a beam or ring trip across the world leaves her in a chunk that is about to unload");
    }

    @Test
    void aCatThatHasBeenDiscardedHasBeenLeftBehind()
    {
        final Cat gone = liveCat();
        when(gone.isValid()).thenReturn(false);
        when(gone.isDead()).thenReturn(true);

        assertTrue(FreyaCompanion.isLeftBehind(gone, new Location(world, 1.0, 64.0, 1.0)),
            "a non-persistent cat is discarded when her chunk unloads, leaving a dead reference");
    }

    @Test
    void aCatAFewBlocksAwayIsLeftToVanillaFollowing()
    {
        final Cat cat = catAt(new Location(world, 1.0, 64.0, 1.0));

        assertFalse(FreyaCompanion.isLeftBehind(cat, new Location(world, 9.0, 64.0, 1.0)),
            "re-summoning after every short hop would make her blink in and out of existence");
    }

    @Test
    void catchingUpResummonsHerBesideAnOwnerWhoTravelled()
    {
        FreyaPreferences.setEnabled(OWNER, true);
        final Cat before = catAt(new Location(mock(World.class), 1.0, 64.0, 1.0));
        final Player owner = playerWith(OWNER, before);
        FreyaCompanion.spawnFor(owner);

        final Cat after = liveCat();
        when(world.spawn(any(Location.class), eq(Cat.class))).thenReturn(after);

        assertTrue(FreyaCompanion.catchUp(owner), "she was in another world, so she comes back");
        verify(before).remove();
        assertTrue(FreyaCompanion.isCompanion(after), "and the new cat is the one tracked");
        assertEquals(1, FreyaCompanion.liveCount(), "still exactly one");
    }

    @Test
    void catchingUpDoesNothingForAPlayerWhoHasHerTurnedOff()
    {
        final Player owner = playerWith(OWNER, liveCat());

        assertFalse(FreyaCompanion.catchUp(owner),
            "a teleport must never hand a cat to somebody who did not ask for one");
        assertEquals(0, FreyaCompanion.liveCount(), "nothing spawned");
    }

    @Test
    void catchingUpLeavesACatThatIsAlreadyBesideHerOwner()
    {
        FreyaPreferences.setEnabled(OWNER, true);
        final Cat beside = catAt(new Location(world, 2.0, 64.0, 1.0));
        final Player owner = playerWith(OWNER, beside);
        FreyaCompanion.spawnFor(owner);

        assertFalse(FreyaCompanion.catchUp(owner), "she is right there");
        verify(beside, never()).remove();
        verify(world, times(1)).spawn(any(Location.class), eq(Cat.class));
        assertTrue(FreyaCompanion.isCompanion(beside), "and still tracked");
    }

    @Test
    void sheIsAwayWhileHerOwnerSleepsSoThereIsNoMorningGift()
    {
        FreyaPreferences.setEnabled(OWNER, true);
        final Cat cat = liveCat();
        final Player owner = playerWith(OWNER, cat);
        FreyaCompanion.spawnFor(owner);

        FreyaCompanion.ownerSleeps(OWNER);

        verify(cat).remove();
        assertFalse(FreyaCompanion.catchUp(owner),
            "a tamed cat beside a sleeping owner gives a gift on waking, phantom membrane included");
        assertEquals(0, FreyaCompanion.liveCount(), "nothing is out while they sleep");

        FreyaCompanion.ownerWakes(OWNER);
        assertTrue(FreyaCompanion.catchUp(owner), "and she is back once they are up");
    }

    @Test
    void sheIsAwayWhileACreeperOrPhantomHuntsHerOwner()
    {
        FreyaPreferences.setEnabled(OWNER, true);
        final Cat cat = liveCat();
        final Player owner = playerWith(OWNER, cat);
        FreyaCompanion.spawnFor(owner);

        assertTrue(FreyaCompanion.ownerHunted(OWNER), "the first hunt starts the watch");
        assertFalse(FreyaCompanion.ownerHunted(OWNER), "a second hunter does not start another");

        verify(cat).remove();
        assertFalse(FreyaCompanion.catchUp(owner),
            "creepers and phantoms keep away from cats, so her being there would protect her owner");
    }

    @Test
    void theHuntEndsOnlyWhenNothingNearbyStillTargetsHerOwner()
    {
        FreyaPreferences.setEnabled(OWNER, true);
        final Player owner = playerWith(OWNER, liveCat());
        when(owner.isOnline()).thenReturn(true);
        final Creeper creeper = mock(Creeper.class);
        when(creeper.getTarget()).thenReturn(owner);
        when(owner.getNearbyEntities(any(Double.class), any(Double.class), any(Double.class)))
            .thenReturn(List.of(creeper));
        FreyaCompanion.ownerHunted(OWNER);

        assertFalse(FreyaCompanion.huntOver(owner), "the creeper is still after them");
        assertTrue(FreyaCompanion.isAway(OWNER), "so she stays away");

        when(creeper.getTarget()).thenReturn(null);
        assertTrue(FreyaCompanion.huntOver(owner), "it has given up");
        assertFalse(FreyaCompanion.isAway(OWNER), "so she may come back");
    }

    @Test
    void onlyCreepersAndPhantomsTargetingTheOwnerCountAsHunting()
    {
        final Player owner = mock(Player.class);
        final Player someoneElse = mock(Player.class);
        final Phantom phantom = mock(Phantom.class);
        when(phantom.getTarget()).thenReturn(owner);
        final Creeper elsewhere = mock(Creeper.class);
        when(elsewhere.getTarget()).thenReturn(someoneElse);
        final Zombie zombie = mock(Zombie.class);
        when(zombie.getTarget()).thenReturn(owner);

        assertTrue(FreyaCompanion.isHunting(List.<Entity>of(phantom), owner), "a phantom diving at them");
        assertFalse(FreyaCompanion.isHunting(List.<Entity>of(elsewhere), owner),
            "a creeper after somebody else is not this owner's hunt");
        assertFalse(FreyaCompanion.isHunting(List.<Entity>of(zombie), owner),
            "zombies do not care about cats, so she need not leave for one");
    }

    @Test
    void openingAChestStandsUpACompanionSittingOnIt()
    {
        final Cat onChest = catAt(new Location(world, 10.5, 65.0, 10.5));
        FreyaCompanion.spawnFor(playerWith(OWNER, onChest));
        final Cat farAway = catAt(new Location(world, 40.5, 64.0, 40.5));
        FreyaCompanion.spawnFor(playerWith(OTHER, farAway));

        assertEquals(1, FreyaCompanion.standUpNear(new Location(world, 10.5, 64.5, 10.5)),
            "a sitting cat keeps a chest shut, and nobody but her owner can see why");
        verify(onChest).setSitting(false);
        verify(farAway, never()).setSitting(false);
    }

    @Test
    void leavingForgetsWhySheWasAway()
    {
        FreyaPreferences.setEnabled(OWNER, true);
        FreyaCompanion.ownerSleeps(OWNER);
        FreyaCompanion.ownerHunted(OWNER);

        FreyaCompanion.forgetOwner(OWNER);

        assertFalse(FreyaCompanion.isAway(OWNER),
            "a player who quit in bed would otherwise never see her again");
    }

    @Test
    void aRefusedSpawnIsNotTrackedAsACatThatIsNotThere()
    {
        final FreyaListener listener = new FreyaListener();
        final Cat refused = mock(Cat.class);
        final CreatureSpawnEvent spawn = mock(CreatureSpawnEvent.class);
        when(spawn.getEntity()).thenReturn(refused);
        when(spawn.isCancelled()).thenReturn(true);
        final Player owner = playerWith(OWNER, refused);
        when(world.spawn(any(Location.class), eq(Cat.class))).thenAnswer(call ->
        {
            listener.onSpawnSettled(spawn);
            return refused;
        });

        assertNull(FreyaCompanion.spawnFor(owner),
            "the command would otherwise say she came when nothing is in the world");
        assertEquals(0, FreyaCompanion.liveCount(), "and catch-up can still try again later");
        verify(refused).remove();
    }

    @Test
    void aCatPlacedBeforeHerChunkTracksEntitiesIsStillKept()
    {
        final Cat pending = mock(Cat.class);
        when(pending.isValid()).thenReturn(false);
        when(pending.isDead()).thenReturn(false);
        when(pending.getLocation()).thenReturn(new Location(world, 1.0, 64.0, 1.0));

        assertEquals(pending, FreyaCompanion.spawnFor(playerWith(OWNER, pending)),
            "straight after a cross-world trip a placed cat is not valid yet; dropping her then "
                + "lost her and left an unsettled, visible cat behind");
        verify(pending).setVisibleByDefault(false);
        assertFalse(FreyaCompanion.isLeftBehind(pending, new Location(world, 1.0, 64.0, 1.0)),
            "and the second catch-up of the same trip must not replace her");
    }

    @Test
    void herSpawnIsLetThroughWhenAnotherPluginCancelsIt()
    {
        final FreyaListener listener = new FreyaListener();
        final Cat cat = liveCat();
        final CreatureSpawnEvent spawn = mock(CreatureSpawnEvent.class);
        when(spawn.getEntity()).thenReturn(cat);
        final Player owner = playerWith(OWNER, cat);
        when(world.spawn(any(Location.class), eq(Cat.class))).thenAnswer(call ->
        {
            listener.onSpawn(spawn);
            return cat;
        });

        FreyaCompanion.spawnFor(owner);

        verify(spawn).setCancelled(false);
        assertFalse(FreyaCompanion.isBeingSummoned(), "the window closes once she is placed");
    }

    @Test
    void anOrdinaryCatSpawnIsLeftToTheOtherPlugins()
    {
        final Cat stray = liveCat();
        final CreatureSpawnEvent spawn = mock(CreatureSpawnEvent.class);
        when(spawn.getEntity()).thenReturn(stray);

        new FreyaListener().onSpawn(spawn);

        verify(spawn, never()).setCancelled(false);
    }
}
