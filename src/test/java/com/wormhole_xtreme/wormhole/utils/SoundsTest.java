package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * {@link Sounds#stopForEveryoneIn}, the counterpart {@link #play} never had.
 *
 * <p>{@code World} has no {@code stopSound} -- only {@code Player} does -- so unlike playing,
 * which Bukkit broadcasts to everyone in range on its own, stopping a sound has to be told to
 * each player in the world individually. This is what let a gate's ambient hum keep playing to
 * its natural end after the gate had already shut down: nothing was ever telling anyone to stop
 * hearing it.
 */
public class SoundsTest
{
    @Test
    public void everyPlayerInTheWorldIsToldToStopTheSound()
    {
        final Player p1 = mock(Player.class);
        final Player p2 = mock(Player.class);
        final World world = mock(World.class);
        when(world.getPlayers()).thenReturn(Arrays.asList(p1, p2));

        Sounds.stopForEveryoneIn(world, "ambient.underwater.loop");

        verify(p1).stopSound("ambient.underwater.loop", SoundCategory.BLOCKS);
        verify(p2).stopSound("ambient.underwater.loop", SoundCategory.BLOCKS);
    }

    @Test
    public void oneMisbehavingPlayerDoesNotStopTheRestFromBeingTold()
    {
        // Decoration -- a client that throws on one player must not leave everyone else still
        // hearing a sound the gate that made it has already shut down.
        final Player broken = mock(Player.class);
        doThrow(new RuntimeException("client gone")).when(broken)
            .stopSound(anyString(), any(SoundCategory.class));
        final Player fine = mock(Player.class);
        final World world = mock(World.class);
        when(world.getPlayers()).thenReturn(Arrays.asList(broken, fine));

        assertDoesNotThrow(() -> Sounds.stopForEveryoneIn(world, "ambient.underwater.loop"));

        verify(fine).stopSound("ambient.underwater.loop", SoundCategory.BLOCKS);
    }

    @Test
    public void aNullWorldDoesNothingRatherThanThrowing()
    {
        assertDoesNotThrow(() -> Sounds.stopForEveryoneIn(null, "ambient.underwater.loop"));
    }

    @Test
    public void anEmptySoundNameDoesNothing()
    {
        final Player p = mock(Player.class);
        final World world = mock(World.class);
        when(world.getPlayers()).thenReturn(Collections.singletonList(p));

        Sounds.stopForEveryoneIn(world, "");

        verify(p, never()).stopSound(anyString(), any(SoundCategory.class));
    }
}
