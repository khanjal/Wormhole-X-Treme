package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * A player's following pets travel with them.
 *
 * <p>Vanilla pets only ever follow by walking, or by a short hop to an owner in the same world,
 * so before this every gate, beam and mirror trip left them behind, and a ring took only the
 * ones standing in it. The rules for which pets come are the ones a player can see: tamed to
 * them, and not told to sit.
 */
class PetEscortTest
{
    private static final UUID OWNER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private Player owner;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong())).thenReturn(1);
        PluginTestSupport.scheduler(scheduler);
        ConfigTestSupport.clear();
        owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(OWNER);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    /**
     * A tamed wolf belonging to someone.
     *
     * @param tamer
     *            who it belongs to
     * @return the wolf, standing
     */
    static Wolf wolfOf(final Player tamer)
    {
        final Wolf wolf = mock(Wolf.class);
        when(wolf.isTamed()).thenReturn(true);
        when(wolf.getOwner()).thenReturn(tamer);
        when(wolf.getUniqueId()).thenReturn(UUID.randomUUID());
        when(wolf.teleport(any(Location.class))).thenReturn(true);
        return wolf;
    }

    @Test
    void aFollowingWolfTravelsWithItsOwner()
    {
        assertTrue(PetEscort.follows(wolfOf(owner), OWNER));
    }

    @Test
    void aPetToldToSitStaysWhereItWasTold()
    {
        final Wolf sitting = wolfOf(owner);
        when(sitting.isSitting()).thenReturn(true);

        assertFalse(PetEscort.follows(sitting, OWNER),
            "sitting is how a player leaves a pet at home; a gate must not undo that");
    }

    @Test
    void somebodyElsesPetStays()
    {
        final Player stranger = mock(Player.class);
        when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());

        assertFalse(PetEscort.follows(wolfOf(stranger), OWNER),
            "walking past someone's dog on the way into a gate must not take it");
    }

    @Test
    void anUntamedCatStays()
    {
        final Cat stray = mock(Cat.class);

        assertFalse(PetEscort.follows(stray, OWNER), "a stray belongs to nobody");
    }

    @Test
    void aTamedHorseIsNotAFollower()
    {
        final Horse horse = mock(Horse.class);
        when(horse.isTamed()).thenReturn(true);
        when(horse.getOwner()).thenReturn(owner);

        assertFalse(PetEscort.follows(horse, OWNER),
            "a tamed horse stays where it was left; it travels only when ridden");
    }

    @Test
    void aPetRidingSomethingStaysWithWhatItRides()
    {
        final Wolf riding = wolfOf(owner);
        when(riding.isInsideVehicle()).thenReturn(true);

        assertFalse(PetEscort.follows(riding, OWNER));
    }

    @Test
    void gatheringKeepsOnlyTheOwnersFollowingPets()
    {
        final Wolf mine = wolfOf(owner);
        final Player stranger = mock(Player.class);
        when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());
        final Wolf theirs = wolfOf(stranger);
        final Zombie zombie = mock(Zombie.class);
        when(owner.getNearbyEntities(anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(List.of(mine, theirs, zombie));

        assertEquals(List.of(mine), PetEscort.gather(owner));
    }

    @Test
    void turningTheSettingOffLeavesEveryPetBehind()
    {
        ConfigTestSupport.set(ConfigKeys.PETS_FOLLOW_OWNER, false);
        final Wolf wolf = wolfOf(owner);
        when(owner.getNearbyEntities(anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(List.of(wolf));

        assertTrue(PetEscort.gather(owner).isEmpty());
    }

    @Test
    void bringingSendsEachPetToTheArrivalAndMarksItArrived()
    {
        final Wolf wolf = wolfOf(owner);
        final Location arrival = new Location(mock(World.class), 100.5, 64.0, -20.5);

        assertEquals(1, PetEscort.bring(List.of(wolf), arrival));

        verify(wolf).teleport(arrival);
        verify(wolf).setFallDistance(0f);
        assertTrue(WormholeXTremeVehicleListener.isVehicleRecentlyTeleported(wolf.getUniqueId()),
            "a pet landing in front of a gate must not be swept straight back through it");
    }

    @Test
    void aPetThatWillNotMoveDoesNotStopTheRest()
    {
        final Wolf stuck = wolfOf(owner);
        when(stuck.teleport(any(Location.class))).thenThrow(new IllegalStateException("refused"));
        final Wolf free = wolfOf(owner);
        final List<Entity> pets = List.of(stuck, free);

        assertEquals(1, PetEscort.bring(pets, new Location(mock(World.class), 0.0, 64.0, 0.0)));
        verify(free).teleport(any(Location.class));
    }

    @Test
    void anArrivalWithNoWorldBringsNobody()
    {
        final Wolf wolf = wolfOf(owner);

        assertEquals(0, PetEscort.bring(List.of(wolf), new Location(null, 0.0, 64.0, 0.0)));
        verify(wolf, never()).teleport(any(Location.class));
    }
}
