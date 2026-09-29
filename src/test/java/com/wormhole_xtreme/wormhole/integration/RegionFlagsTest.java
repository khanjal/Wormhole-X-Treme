package com.wormhole_xtreme.wormhole.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.integration.RegionFlags.Action;
import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * What the WorldGuard facade decides, with a stand-in for the regions.
 *
 * <p>The rule that matters is that a region only ever takes away: with no check installed, or a
 * check that throws, everything is allowed. A region query failing on some WorldGuard version
 * must not turn into players locked out of every gate on the server.
 */
class RegionFlagsTest
{
    private final Player player = mock(Player.class);

    private WormholeXTreme plugin;

    @BeforeEach
    void setUp() throws ReflectiveOperationException
    {
        plugin = PluginTestSupport.install();
    }

    @AfterEach
    void tearDown() throws ReflectiveOperationException
    {
        RegionFlags.setCheckForTest(null);
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private static Location at(final int x)
    {
        return new Location(null, x, 64, 0);
    }

    /** A gate whose travellers arrive there, with its lever at x=2. */
    private static Stargate gateArrivingAt(final Location arrival)
    {
        final Stargate gate = mock(Stargate.class);
        final Block lever = mock(Block.class);
        when(lever.getLocation()).thenReturn(at(2));
        when(gate.getGatePlayerTeleportLocation()).thenReturn(arrival);
        when(gate.getGateDialLeverBlock()).thenReturn(lever);
        return gate;
    }

    /** With WorldGuard off or absent nothing is refused, which is what installing this must change. */
    @Test
    void withNoRegionCheckEverythingIsAllowed()
    {
        final Stargate gate = gateArrivingAt(at(1));

        assertTrue(RegionFlags.mayUse(player, gate));
        assertTrue(RegionFlags.mayBuild(player, gate));
        assertFalse(RegionFlags.refusesUse(player, gate));
        verify(player, never()).sendMessage(any(String.class));
    }

    /** A region denying use refuses it and tells the player it was the region, not their rights. */
    @Test
    void aRegionDenyingUseRefusesItAndSaysSo()
    {
        RegionFlags.setCheckForTest((who, where, action) -> action != Action.USE);
        final Stargate gate = gateArrivingAt(at(1));

        assertFalse(RegionFlags.mayUse(player, gate));
        assertTrue(RegionFlags.refusesUse(player, gate));
        verify(player).sendMessage(RegionFlags.USE_REFUSED);
        assertTrue(RegionFlags.mayBuild(player, gate), "denying use says nothing about building");
    }

    /**
     * A check that throws lets the player through, for an exception and for a linkage error alike.
     *
     * <p>A WorldGuard update that moves a class arrives as NoClassDefFoundError, not an exception;
     * catching only exceptions would let it escape into the listener and refuse every gate.
     */
    @Test
    void aRegionCheckThatFailsLetsThePlayerThrough()
    {
        final Stargate gate = gateArrivingAt(at(1));

        RegionFlags.setCheckForTest((who, where, action) -> {
            throw new IllegalStateException("region manager not loaded");
        });
        assertTrue(RegionFlags.mayUse(player, gate), "an exception must fail open");
        assertFalse(RegionFlags.refusesBuild(player, gate), "an exception must fail open");

        RegionFlags.setCheckForTest((who, where, action) -> {
            throw new NoClassDefFoundError("com/sk89q/worldguard/protection/regions/RegionQuery");
        });
        assertTrue(RegionFlags.mayUse(player, gate), "a linkage error must fail open too");
        verify(player, never()).sendMessage(any(String.class));
    }

    /** One block of the gate inside a denying region refuses the whole gate. */
    @Test
    void aGateWithOneBlockInADenyingRegionMayNotBeBuilt()
    {
        final Location denied = at(7);
        RegionFlags.setCheckForTest((who, where, action) -> !where.equals(denied));
        final Stargate gate = gateArrivingAt(at(1));
        when(gate.getGateStructureBlocks()).thenReturn(List.of(at(5), at(6), denied, at(8)));

        assertFalse(RegionFlags.mayBuild(player, gate));
        assertTrue(RegionFlags.refusesBuild(player, gate));
        verify(player).sendMessage(RegionFlags.BUILD_REFUSED);
    }

    /** Its lever counts as part of the gate: a DHD reaching into a denying region is refused. */
    @Test
    void aLeverInADenyingRegionRefusesTheGate()
    {
        RegionFlags.setCheckForTest((who, where, action) -> !where.equals(at(2)));
        final Stargate gate = gateArrivingAt(at(1));
        when(gate.getGateStructureBlocks()).thenReturn(List.of(at(5)));

        assertFalse(RegionFlags.mayBuild(player, gate));
    }

    /** Use is asked where travellers arrive, and at the lever only when a gate has no arrival point. */
    @Test
    void useIsAskedWhereTravellersArriveOrElseAtTheLever()
    {
        final List<Location> asked = new ArrayList<>();
        RegionFlags.setCheckForTest((who, where, action) -> asked.add(where));

        RegionFlags.mayUse(player, gateArrivingAt(at(1)));
        RegionFlags.mayUse(player, gateArrivingAt(null));

        assertEquals(List.of(at(1), at(2)), asked);
    }

    /** With worldguard-enabled off, nothing is registered and the server is not even asked. */
    @Test
    void withTheSettingOffNothingIsRegistered()
    {
        ConfigTestSupport.set(ConfigManager.ConfigKeys.WORLDGUARD_ENABLED, false);

        RegionFlags.register();

        verify(plugin, never()).getServer();
    }

    /** Asked for, with WorldGuard absent, it says so once and stays off rather than failing. */
    @Test
    void withWorldGuardAbsentItSaysSoAndListensForNothing()
    {
        ConfigTestSupport.set(ConfigManager.ConfigKeys.WORLDGUARD_ENABLED, true);
        final Server server = mock(Server.class);
        final PluginManager plugins = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);

        RegionFlags.register();
        RegionFlags.listen(plugin);

        verify(plugin).prettyLog(eq(Level.INFO), contains("WorldGuard is not installed"));
        verify(plugins, never()).registerEvents(any(Listener.class), any(Plugin.class));
    }

    /** The counterpart: once a check is in place, travel is listened for. */
    @Test
    void withARegionCheckTravelIsListenedFor()
    {
        final Server server = mock(Server.class);
        final PluginManager plugins = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        RegionFlags.setCheckForTest((who, where, action) -> true);

        RegionFlags.listen(plugin);

        verify(plugins).registerEvents(any(RegionTravelListener.class), eq(plugin));
    }
}
