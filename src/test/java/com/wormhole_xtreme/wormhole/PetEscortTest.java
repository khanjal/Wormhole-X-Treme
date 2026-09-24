package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
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
    private WormholeXTreme plugin;
    /** Held here because a Location keeps its World weakly, and a collected mock reads as unloaded. */
    private World ownerWorld;
    private World petWorld;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        ownerWorld = mock(World.class);
        petWorld = mock(World.class);
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
        // Static, and emptied by a scheduled task a mocked scheduler never runs.
        final java.util.Set<UUID> marked = PrivateStatics.of(WormholeXTremeVehicleListener.class, "recentlyTeleported");
        marked.clear();
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

    /**
     * A pet the nearby-entity box turned up but that really stands out of reach is left.
     *
     * <p>{@code getNearbyEntities} takes half-widths, so the corner of its box is twenty blocks
     * from the middle while the rule, and {@link PetEscort#apart} at the far end, are twelve. A
     * pet gathered from that corner would be carried through a gate it was never following its
     * owner towards, and then judged out of reach when it arrived.
     */
    @Test
    void aPetFoundInTheCornerOfTheBoxButOutOfReachStays()
    {
        final World here = mock(World.class);
        ownerNowAt(new Location(here, 0.5, 64.0, 0.5));
        final Wolf corner = at(wolfOf(owner), new Location(here, 11.5, 64.0, 11.5));
        final Wolf beside = at(wolfOf(owner), new Location(here, 5.5, 64.0, 5.5));
        when(owner.getNearbyEntities(anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(List.of(corner, beside));

        assertEquals(List.of(beside), PetEscort.gather(owner),
            "the box reaches further diagonally than a pet ever follows");
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

    /**
     * Where the owner stands once they have arrived.
     *
     * @param where
     *            the arrival
     */
    private void ownerNowAt(final Location where)
    {
        when(owner.isOnline()).thenReturn(true);
        when(owner.getLocation()).thenReturn(where);
    }

    /**
     * A pet standing somewhere.
     *
     * @param where
     *            where it stands
     * @return the pet
     */
    private static Wolf at(final Wolf pet, final Location where)
    {
        when(pet.getLocation()).thenReturn(where);
        return pet;
    }

    @Test
    void bringingSendsEachPetToWhereTheOwnerNowIsAndMarksItArrived()
    {
        final Location arrival = new Location(ownerWorld, 100.5, 64.0, -20.5);
        ownerNowAt(arrival);
        final Wolf wolf = at(wolfOf(owner), new Location(petWorld, 0.5, 64.0, 0.5));

        assertEquals(1, PetEscort.bring(List.of(wolf), owner));

        verify(wolf).teleport(arrival);
        verify(wolf).setFallDistance(0f);
        assertTrue(WormholeXTremeVehicleListener.isVehicleRecentlyTeleported(wolf.getUniqueId()),
            "a pet landing in front of a gate must not be swept straight back through it");
    }

    @Test
    void aPetStillBesideAnOwnerWhoseTripWasRefusedStays()
    {
        final World here = mock(World.class);
        ownerNowAt(new Location(here, 0.5, 64.0, 0.5));
        final Wolf wolf = at(wolfOf(owner), new Location(here, 3.5, 64.0, 0.5));

        assertEquals(0, PetEscort.bring(List.of(wolf), owner));
        verify(wolf, never()).teleport(any(Location.class));
    }

    @Test
    void aPetToldToSitBeforeItFollowedStays()
    {
        ownerNowAt(new Location(ownerWorld, 100.5, 64.0, 0.5));
        final Wolf wolf = at(wolfOf(owner), new Location(petWorld, 0.5, 64.0, 0.5));
        when(wolf.isSitting()).thenReturn(true);

        assertEquals(0, PetEscort.bring(List.of(wolf), owner),
            "sitting in the second before it follows still means stay");
    }

    @Test
    void aPetThatWillNotMoveDoesNotStopTheRest()
    {
        ownerNowAt(new Location(ownerWorld, 100.5, 64.0, 0.5));
        final Wolf stuck = at(wolfOf(owner), new Location(petWorld, 0.5, 64.0, 0.5));
        when(stuck.teleport(any(Location.class))).thenThrow(new IllegalStateException("refused"));
        final Wolf free = at(wolfOf(owner), new Location(petWorld, 0.5, 64.0, 0.5));
        final List<Entity> pets = List.of(stuck, free);

        assertEquals(1, PetEscort.bring(pets, owner));
        verify(free).teleport(any(Location.class));
    }

    @Test
    void anOwnerWhoHasLeftTheServerBringsNobody()
    {
        final Wolf wolf = at(wolfOf(owner), new Location(petWorld, 0.5, 64.0, 0.5));
        when(owner.isOnline()).thenReturn(false);

        assertEquals(0, PetEscort.bring(List.of(wolf), owner));
        verify(wolf, never()).teleport(any(Location.class));
    }

    /**
     * Pets follow a moment after the trip, not in the same tick.
     *
     * <p>A pet sent to a client still switching worlds was on the server beside its owner and
     * invisible to them; and a mounted or vehicle rider is re-seated at the far end ticks later.
     */
    @Test
    void followingWaitsBeforeBringing() throws Exception
    {
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        final Wolf wolf = wolfOf(owner);

        PetEscort.follow(List.of(wolf), owner);

        verify(scheduler).scheduleSyncDelayedTask(any(), any(Runnable.class),
            org.mockito.ArgumentMatchers.eq(PetEscort.FOLLOW_DELAY_TICKS));
        verify(wolf, never()).teleport(any(Location.class));
    }

    /**
     * With detail logging on, each of the owner's own pets that stays says why.
     *
     * <p>These lines are how a server owner testing a transport tells "the escort never saw my
     * dog" from "it saw her sitting", so each reason has to reach the log.
     */
    @Test
    void withDetailLoggingEachOwnPetThatStaysSaysWhy()
    {
        when(plugin.isLoggable(java.util.logging.Level.FINE)).thenReturn(true);
        final Wolf sitting = wolfOf(owner);
        when(sitting.isSitting()).thenReturn(true);
        final Wolf riding = wolfOf(owner);
        when(riding.isInsideVehicle()).thenReturn(true);
        final Wolf dead = wolfOf(owner);
        when(dead.isDead()).thenReturn(true);
        final Horse horse = mock(Horse.class);
        when(horse.isTamed()).thenReturn(true);
        when(horse.getOwner()).thenReturn(owner);
        final Zombie zombie = mock(Zombie.class);
        when(owner.getNearbyEntities(anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(List.of(sitting, riding, dead, horse, zombie));

        assertTrue(PetEscort.gather(owner).isEmpty());

        verify(plugin).prettyLog(eq(java.util.logging.Level.FINE), contains("stays behind: sitting"));
        verify(plugin).prettyLog(eq(java.util.logging.Level.FINE), contains("stays behind: riding something"));
        verify(plugin).prettyLog(eq(java.util.logging.Level.FINE), contains("stays behind: dead"));
        verify(plugin).prettyLog(eq(java.util.logging.Level.FINE), contains("stays behind: not a following pet"));
        verify(plugin).prettyLog(eq(java.util.logging.Level.FINE), contains("Pets travelling with"));
    }

    @Test
    void aLookupThatFailsLeavesEveryPetBehindAndSaysSo()
    {
        when(owner.getNearbyEntities(anyDouble(), anyDouble(), anyDouble()))
            .thenThrow(new IllegalStateException("off the main thread"));

        assertTrue(PetEscort.gather(owner).isEmpty(), "the owner's own trip must still go ahead");
        verify(plugin).prettyLog(eq(java.util.logging.Level.FINE), contains("Could not look for"),
            nullable(Throwable.class));
    }

    @Test
    void aRefusedTeleportIsNotCountedAndIsLogged()
    {
        ownerNowAt(new Location(ownerWorld, 100.5, 64.0, 0.5));
        final Wolf wolf = at(wolfOf(owner), new Location(petWorld, 0.5, 64.0, 0.5));
        when(wolf.teleport(any(Location.class))).thenReturn(false);

        assertEquals(0, PetEscort.bring(List.of(wolf), owner));
        verify(plugin).prettyLog(eq(java.util.logging.Level.FINE), contains("the teleport was refused"));
    }
}
