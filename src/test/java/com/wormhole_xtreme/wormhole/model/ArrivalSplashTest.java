package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * What the arrival splash does, step by step.
 *
 * <p>{@code ArrivalSplashLengthTest} pins only the constant. Nothing ran {@code splashArrival},
 * so it could have been drawn in a ceiling, drawn at the wrong times, left on for ever, or
 * sent to somebody who had already walked on, with no test to say so.
 *
 * <p>The scheduler holds each step instead of running it, so a test can say when each one was
 * asked for and then run exactly the one it is about.
 */
class ArrivalSplashTest
{
    private BukkitScheduler scheduler;
    private final List<Runnable> steps = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();

    private World world;
    private Player player;
    private Block eyeBlock;
    private Location eyeBlockAt;
    private Location eye;
    private MockedStatic<Bukkit> bukkit;
    private BlockData waterData;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(inv ->
        {
            steps.add(inv.getArgument(1, Runnable.class));
            delays.add(inv.getArgument(2, Long.class));
            return null;
        });
        PluginTestSupport.scheduler(scheduler);

        world = mock(World.class);
        eyeBlockAt = new Location(world, 5, 70, 5);
        eyeBlock = mock(Block.class);
        when(eyeBlock.getType()).thenReturn(Material.AIR);
        when(eyeBlock.getLocation()).thenReturn(eyeBlockAt);
        eye = mock(Location.class);
        when(eye.getBlock()).thenReturn(eyeBlock);

        player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getEyeLocation()).thenReturn(eye);

        waterData = mock(BlockData.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(Material.WATER)).thenReturn(waterData);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        bukkit.close();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    @Test
    void nobodyOfflineIsSplashed()
    {
        when(player.isOnline()).thenReturn(false);

        ArrivalSplash.splashArrival(player);

        assertTrue(steps.isEmpty());
    }

    @Test
    void noPlayerIsNotAnError()
    {
        assertDoesNotThrow(() -> ArrivalSplash.splashArrival(null));
        assertTrue(steps.isEmpty());
    }

    @Test
    void waterIsNotDrawnWhereTheEyeIsNotInOpenAir()
    {
        when(eyeBlock.getType()).thenReturn(Material.STONE);

        ArrivalSplash.splashArrival(player);

        assertTrue(steps.isEmpty(), "water drawn over somebody's ceiling would be a strange thing to see");
    }

    /**
     * Water every second tick for the length of the splash, then the clear.
     *
     * <p>The first draw is asked for at tick 1 and not 0, which {@code runTaskLater} would take
     * as "now"; the last, at {@code SPLASH_TICKS}, is the one that puts the real block back.
     */
    @Test
    void waterIsRedrawnEveryOtherTickAndThenCleared()
    {
        ArrivalSplash.splashArrival(player);

        assertEquals(List.of(1L, 2L, 4L, 6L, 8L, 10L, 12L, 14L, 16L, 18L, ArrivalSplash.SPLASH_TICKS), delays);
    }

    @Test
    void aWaterStepSendsWaterWhileTheTravellerIsStillInTheCell()
    {
        ArrivalSplash.splashArrival(player);

        steps.get(0).run();

        verify(player).sendBlockChange(eyeBlockAt, waterData);
    }

    @Test
    void aWaterStepSendsNothingOnceTheTravellerHasWalkedOn()
    {
        ArrivalSplash.splashArrival(player);
        when(eyeBlock.getLocation()).thenReturn(new Location(world, 6, 70, 5));

        steps.get(0).run();

        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    @Test
    void aWaterStepSendsNothingToSomebodyWhoHasLeft()
    {
        ArrivalSplash.splashArrival(player);
        when(player.isOnline()).thenReturn(false);

        steps.get(0).run();

        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /** The clear reads the block again, so whatever is really there is what they are shown. */
    @Test
    void theLastStepPutsBackWhateverIsReallyThere()
    {
        final Block real = mock(Block.class);
        final BlockData realData = mock(BlockData.class);
        when(real.getBlockData()).thenReturn(realData);
        when(world.getBlockAt(5, 70, 5)).thenReturn(real);
        ArrivalSplash.splashArrival(player);

        steps.get(steps.size() - 1).run();

        verify(player).sendBlockChange(eyeBlockAt, realData);
        verify(player, never()).sendBlockChange(eyeBlockAt, waterData);
    }

    @Test
    void theLastStepStillClearsForSomebodyWhoHasWalkedOn()
    {
        final Block real = mock(Block.class);
        final BlockData realData = mock(BlockData.class);
        when(real.getBlockData()).thenReturn(realData);
        when(world.getBlockAt(5, 70, 5)).thenReturn(real);
        ArrivalSplash.splashArrival(player);
        when(eyeBlock.getLocation()).thenReturn(new Location(world, 9, 70, 9));

        steps.get(steps.size() - 1).run();

        verify(player).sendBlockChange(eyeBlockAt, realData);
    }

    @Test
    void aClientThatCannotBeReadIsLeftAloneWithoutAnError()
    {
        when(player.getEyeLocation()).thenThrow(new IllegalStateException("gone"));

        assertDoesNotThrow(() -> ArrivalSplash.splashArrival(player));
        verifyNoInteractions(scheduler);
    }

    @Test
    void theClearStepThatFailsIsSwallowedToo()
    {
        when(world.getBlockAt(5, 70, 5)).thenThrow(new IllegalStateException("world gone"));
        ArrivalSplash.splashArrival(player);

        assertDoesNotThrow(() -> steps.get(steps.size() - 1).run());
        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    @Test
    void aStepThatFailsIsSwallowed()
    {
        ArrivalSplash.splashArrival(player);
        when(player.getEyeLocation()).thenThrow(new IllegalStateException("gone"));

        assertDoesNotThrow(() -> steps.get(0).run());
    }
}
