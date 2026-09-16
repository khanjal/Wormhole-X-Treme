package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * The sweep that offers every mirror to {@link MirrorWindows}.
 *
 * <p>It runs on a timer for the life of the server, so what is pinned down here is what it does
 * not touch: an unloaded world, an unloaded chunk, and the far side of a mirror somebody walks up to.
 */
class MirrorProximityTest
{
    /** Where saves go, so no test writes a mirror file into the repository. */
    @TempDir
    File dataFolder;

    private World world;
    private Block banner;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        MirrorManager.clear();
        MirrorProximity.clear();

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);

        banner = mock(Block.class);
        when(banner.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(banner.getState()).thenAnswer(invocation -> mock(Banner.class));
        when(banner.getLocation()).thenReturn(new Location(world, 10.0, 64.0, 10.0));
        when(banner.getWorld()).thenReturn(world);
        when(world.getBlockAt(10, 64, 10)).thenReturn(banner);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        MirrorProximity.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /**
     * A mirror in an unloaded chunk is not what keeps that chunk resident.
     *
     * <p>The check has to come before {@code getBlockAt}, which would load it. A corridor in a
     * corner of the map nobody has visited would otherwise be pinned in memory by the sweep.
     */
    @Test
    void doesNotTouchABlockInAnUnloadedChunk()
    {
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        pointedMirror();

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            MirrorProximity.tick();
        }

        verify(world).isChunkLoaded(0, 0);
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void doesNothingForAMirrorWhoseWorldIsNotLoaded()
    {
        pointedMirror();

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(null);
            MirrorProximity.tick();
        }

        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    /**
     * Walking up to a mirror never reads its far side or changes its banner.
     *
     * <p>{@code mode dynamic} used to do both on approach, so the room and the banner changed
     * under a player who had asked for neither. "We shouldn't update the banner automatically.
     * It should be an understood command." A look changes when somebody runs {@code set stamp}.
     */
    @Test
    void walkingUpNeverReadsTheFarSideOrChangesTheLook()
    {
        final World destination = destinationWorld();
        final Player walker = playerAt(200.0);
        when(world.getPlayers()).thenReturn(List.of(walker));
        pointedMirror();
        final MirrorLook before = MirrorManager.byName("museum").look();

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(destination);
            bukkit.when(() -> Bukkit.getPlayer(walker.getUniqueId())).thenReturn(walker);

            MirrorProximity.tick();
            when(walker.getLocation()).thenReturn(new Location(world, 11.0, 64.0, 10.0));
            MirrorProximity.tick();
        }

        verify(world, times(2)).getBlockAt(10, 64, 10);
        assertEquals(0, mockingDetails(destination).getInvocations().size(),
            "the far side is never read on approach");
        assertEquals(before, MirrorManager.byName("museum").look(),
            "and the look is whatever stamp last put there");
    }

    @Test
    void offersATickerToSchedule()
    {
        assertNotNull(MirrorProximity.createTicker());
    }

    /** A mirror on the banner block, stamped with a named look and going somewhere. */
    private void pointedMirror()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 10, 64, 10),
            new MirrorPoint("far", 0, 64, 0, 0f, 0f)).withLook(MirrorLook.named("nether")));
    }

    /** A world on the far side, solid stone all through, with an ordinary floor and ceiling. */
    private static World destinationWorld()
    {
        final World far = mock(World.class);
        when(far.getName()).thenReturn("far");
        when(far.getMinHeight()).thenReturn(-64);
        when(far.getMaxHeight()).thenReturn(320);
        when(far.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
        {
            final Block block = mock(Block.class);
            when(block.getType()).thenReturn(Material.DEEPSLATE);
            return block;
        });
        return far;
    }

    /** A player standing that far away along x, in the banner's world. */
    private Player playerAt(final double x)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("someone");
        when(player.getLocation()).thenReturn(new Location(world, x, 64.0, 10.0));
        when(player.getWorld()).thenReturn(world);
        return player;
    }
}
