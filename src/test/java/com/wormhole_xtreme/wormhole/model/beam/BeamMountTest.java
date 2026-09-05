package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Beaming used to leave a mount standing at the origin, and nobody was told.
 *
 * <p>The cause was Bukkit's own contract rather than a mistake in the sequence:
 * {@code Entity#teleport(Location)} dismounts a rider before it moves them, so the single
 * {@code player.teleport(destination)} the beam made tipped the traveller off their horse and
 * beamed them alone. {@link BeamMount} is what notices the mount, parks it, and sends it
 * after them.
 *
 * <p>These tests pin the two halves of that which can be checked without a live server: what
 * a capture actually collects (a mount alone is not the whole story -- anything riding it has
 * to be hidden and re-seated too), and that {@link BeamMount#hold} and
 * {@link BeamMount#release} really are a matched pair. The second matters more than it looks:
 * {@code hold} switches a mount's AI off, and a mount left that way has nothing still running
 * that would ever turn it back on -- it just stands there for the rest of the server's life.
 * Every ending in {@code BeamAnimation} calls {@code release} for that reason, including the
 * ones that never got as far as holding anything.
 *
 * <p>{@link BeamMount#carry} is deliberately not covered here: it teleports and schedules
 * through {@code WormholeXTreme}'s static plugin and scheduler, which is the part that needs a
 * running server. That is the same split the ring subsystem draws between {@code RingCycle}
 * and {@code RingTransit}.
 */
public class BeamMountTest
{
    private static Player riderOf(final Entity vehicle)
    {
        final Player player = mock(Player.class);
        when(player.getVehicle()).thenReturn(vehicle);
        return player;
    }

    /** A horse, because {@code setAI} is on {@link org.bukkit.entity.LivingEntity}, not {@link Entity}. */
    private static Horse someHorse(final boolean hasAi, final List<Entity> passengers)
    {
        final Horse horse = mock(Horse.class);
        when(horse.hasAI()).thenReturn(hasAi);
        when(horse.getPassengers()).thenReturn(passengers);
        return horse;
    }

    @Test
    public void aTravellerOnFootCapturesAnAbsentMountRatherThanNull()
    {
        // BeamAnimation holds this field unconditionally and calls into it from all four of
        // its endings. If an unmounted traveller produced null instead, every one of those
        // endings would need its own null check -- and the one that got forgotten would throw
        // inside a tick, which is precisely the "frozen with no way out" failure the whole
        // recover path exists to prevent.
        final BeamMount mount = BeamMount.capture(riderOf(null));
        assertNotNull(mount, "an unmounted traveller must still get a BeamMount");
        assertTrue(mount.stack().isEmpty(), "nothing to hide for a traveller on foot");
    }

    @Test
    public void aPlayerWhoWillNotSayWhatTheyAreRidingIsTreatedAsRidingNothing()
    {
        // Not hypothetical padding: this runs inside a scheduled tick, and a throw here would
        // land in BeamAnimation's recover path and abort a beam that had no mount problem at
        // all. Riding nothing is the safe reading of "cannot tell".
        final Player player = mock(Player.class);
        when(player.getVehicle()).thenThrow(new IllegalStateException("entity not ticking"));
        assertTrue(BeamMount.capture(player).stack().isEmpty(),
            "a refused getVehicle() must degrade to no mount, not propagate out of the tick");
    }

    @Test
    public void theCapturedStackIsTheMountAndEverythingRidingIt()
    {
        // The stack is what BeamVisibility hides and reveals. A mount with a second passenger
        // -- a friend on the same horse, a mob a player has leashed aboard -- would otherwise
        // stay fully rendered, standing in the departure column, while the traveller
        // themselves dissolved out of it.
        final Entity secondRider = mock(Entity.class);
        when(secondRider.getPassengers()).thenReturn(Collections.<Entity>emptyList());
        final Horse horse = someHorse(true, Arrays.<Entity>asList(secondRider));

        final List<Entity> stack = BeamMount.capture(riderOf(horse)).stack();

        assertEquals(2, stack.size(), "the mount and its passenger both have to be hidden");
        assertSame(horse, stack.get(0), "the mount itself comes first");
        assertTrue(stack.contains(secondRider), "a passenger left visible breaks the vanish");
    }

    @Test
    public void aMountSomebodyElseIsAlsoSittingOnIsLeftAlone()
    {
        // Boats and camels seat two. Carrying a shared mount re-seats its whole passenger
        // stack at the destination, so the second rider would be teleported wherever the
        // traveller was going -- past the permission check, the cooldown and the cost that
        // beaming them would have charged, and without having asked to go anywhere at all.
        //
        // Treated as no mount rather than as a mount left behind, so nothing is held, hidden
        // or moved: the co-rider keeps a boat that behaves normally.
        final Player traveller = mock(Player.class);
        final Player coRider = mock(Player.class);
        final Horse shared = someHorse(true, Arrays.<Entity>asList(traveller, coRider));
        when(traveller.getVehicle()).thenReturn((Entity) shared);

        final BeamMount mount = BeamMount.capture(traveller);

        assertTrue(mount.stack().isEmpty(), "a mount shared with another player must not be carried");
        mount.hold(traveller);
        verify(traveller, never()).leaveVehicle();
        verify(shared, never()).setAI(false);
    }

    @Test
    public void aMountTheTravellerIsAloneOnIsStillCarried()
    {
        // The guard above keys off other passengers, not off there being any passengers --
        // the traveller is still aboard when capture() runs, since hold() has not dismounted
        // them yet. Reading their own presence as "shared" would leave every mount behind
        // and quietly undo the whole feature.
        final Player traveller = mock(Player.class);
        final Horse horse = someHorse(true, Arrays.<Entity>asList((Entity) traveller));
        when(traveller.getVehicle()).thenReturn((Entity) horse);

        final List<Entity> stack = BeamMount.capture(traveller).stack();

        assertFalse(stack.isEmpty(), "the traveller riding alone must still have their mount carried");
        assertSame(horse, stack.get(0), "the mount itself comes first");
    }

    @Test
    public void holdingAMountDismountsTheRiderSoTheFreezeCanActuallyHoldThem()
    {
        // BeamFreeze locks a traveller by reverting PlayerMoveEvent, and a rider does not
        // raise one -- their position comes from the vehicle. Without this dismount a frozen
        // player could still steer a horse clean out of the departure column during the rise.
        final Horse horse = someHorse(true, Collections.<Entity>emptyList());
        final Player player = riderOf(horse);

        BeamMount.capture(player).hold(player);

        verify(player).leaveVehicle();
        verify(horse).setAI(false);
    }

    @Test
    public void releasingGivesBackTheAiThatHoldingTookAway()
    {
        final Horse horse = someHorse(true, Collections.<Entity>emptyList());
        final Player player = riderOf(horse);

        final BeamMount mount = BeamMount.capture(player);
        mount.hold(player);
        mount.release();

        verify(horse).setAI(true);
    }

    @Test
    public void releasingTwiceOnlyGivesTheAiBackOnce()
    {
        // release() is reachable from more than one ending at a time -- carry() calls it on
        // the normal path, and recover() calls it again if a later tick in the same sequence
        // throws. Switching AI back on for a mount that has since been re-parked by something
        // else would be this method overreaching well past what it turned off.
        final Horse horse = someHorse(true, Collections.<Entity>emptyList());
        final Player player = riderOf(horse);

        final BeamMount mount = BeamMount.capture(player);
        mount.hold(player);
        mount.release();
        mount.release();

        verify(horse, times(1)).setAI(true);
    }

    @Test
    public void releasingAMountWhoseAiWasAlreadyOffLeavesItOff()
    {
        // An admin's or another plugin's deliberately AI-less mount: hold() never switched it
        // off, so release() must not switch it on. Restoring "whatever it was" rather than
        // "on" is the difference between undoing our own change and overwriting someone
        // else's.
        final Horse horse = someHorse(false, Collections.<Entity>emptyList());
        final Player player = riderOf(horse);

        final BeamMount mount = BeamMount.capture(player);
        mount.hold(player);
        mount.release();

        verify(horse, never()).setAI(true);
    }

    @Test
    public void anAbsentMountSurvivesEveryCallBeamAnimationMakesOnIt()
    {
        // The whole point of none(): a sequence that fails during the envelope, before the
        // vanish tick ever captured anything, still runs recover(), which calls straight
        // through to these. None of them may throw on a mount that is not there.
        final BeamMount mount = BeamMount.none();
        assertDoesNotThrow(new org.junit.jupiter.api.function.Executable()
        {
            @Override
            public void execute()
            {
                mount.hold(mock(Player.class));
                mount.release();
                mount.stack();
            }
        }, "recover() calls these before a mount has ever been captured");
    }
}
