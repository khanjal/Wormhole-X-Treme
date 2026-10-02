package com.wormhole_xtreme.wormhole.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        RegionFlagsTestSupport.remove();
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
        RegionFlagsTestSupport.install((who, where, action) -> action != Action.USE);
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

        RegionFlagsTestSupport.install((who, where, action) -> {
            throw new IllegalStateException("region manager not loaded");
        });
        assertTrue(RegionFlags.mayUse(player, gate), "an exception must fail open");
        assertFalse(RegionFlags.refusesBuild(player, gate), "an exception must fail open");

        RegionFlagsTestSupport.install((who, where, action) -> {
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
        RegionFlagsTestSupport.install((who, where, action) -> !where.equals(denied));
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
        RegionFlagsTestSupport.install((who, where, action) -> !where.equals(at(2)));
        final Stargate gate = gateArrivingAt(at(1));
        when(gate.getGateStructureBlocks()).thenReturn(List.of(at(5)));

        assertFalse(RegionFlags.mayBuild(player, gate));
    }

    /** Use is asked at the DHD and where travellers arrive, and a missing arrival point is skipped. */
    @Test
    void useIsAskedAtTheDhdAndWhereTravellersArrive()
    {
        final List<Location> asked = new ArrayList<>();
        RegionFlagsTestSupport.install((who, where, action) -> asked.add(where));

        RegionFlags.mayUse(player, gateArrivingAt(at(1)));
        RegionFlags.mayUse(player, gateArrivingAt(null));

        assertEquals(List.of(at(2), at(1), at(2)), asked);
    }

    /**
     * A region drawn tightly round the DHD, or round the arrival point alone, refuses use either way.
     *
     * <p>Asking only where travellers arrive let a region round the ring and DHD, with the arrival
     * point a block outside it, be dialled and travelled through.
     */
    @Test
    void aRegionRoundEitherTheDhdOrTheArrivalRefusesUseWithOneMessage()
    {
        final Stargate gate = gateArrivingAt(at(1));

        RegionFlagsTestSupport.install((who, where, action) -> !where.equals(at(2)));
        assertFalse(RegionFlags.mayUse(player, gate), "a denied DHD refuses though the arrival allows");

        RegionFlagsTestSupport.install((who, where, action) -> !where.equals(at(1)));
        assertFalse(RegionFlags.mayUse(player, gate), "a denied arrival refuses though the DHD allows");

        RegionFlagsTestSupport.install((who, where, action) -> false);
        assertTrue(RegionFlags.refusesUse(player, gate));
        verify(player, times(1)).sendMessage(RegionFlags.USE_REFUSED);
    }

    /** The opening counts as part of the gate, as it does for a preview or a coordinate build. */
    @Test
    void aGateWhoseOpeningIsInADenyingRegionMayNotBeBuilt()
    {
        RegionFlagsTestSupport.install((who, where, action) -> !where.equals(at(9)));
        final Stargate gate = gateArrivingAt(at(1));
        when(gate.getGateDialLeverBlock()).thenReturn(null);
        when(gate.getGateStructureBlocks()).thenReturn(List.of(at(5)));
        when(gate.getGatePortalBlocks()).thenReturn(List.of(at(9)));

        assertFalse(RegionFlags.mayBuild(player, gate));
    }

    /**
     * The first failing region check is logged as a warning, and later ones quietly.
     *
     * <p>Failing open with only a FINE line meant a WorldGuard that always threw looked exactly like
     * a server with no denying regions.
     */
    @Test
    void theFirstFailingCheckIsAWarningAndLaterOnesAreNot()
    {
        RegionFlagsTestSupport.install((who, where, action) -> {
            throw new IllegalStateException("region manager not loaded");
        });

        assertTrue(RegionFlags.mayBuild(player, List.of(at(1), at(2), at(3))));

        verify(plugin, times(1)).prettyLog(eq(Level.WARNING), contains("region check failed"), any(Throwable.class));
        verify(plugin, times(2)).prettyLog(eq(Level.FINE), contains("region check failed"), any(Throwable.class));
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
        RegionFlagsTestSupport.install((who, where, action) -> true);

        RegionFlags.listen(plugin);

        verify(plugins).registerEvents(any(RegionTravelListener.class), eq(plugin));
    }

    /**
     * Turning worldguard-enabled off in-game stops regions refusing at once, and back on they refuse again.
     *
     * <p>The flags stay registered with WorldGuard until a restart, so the setting has to be read at
     * every check; otherwise the command said it was off while every denying region still refused.
     */
    @Test
    void turningTheSettingOffStopsRegionsRefusingAtOnce()
    {
        RegionFlagsTestSupport.install((who, where, action) -> false);
        final Stargate gate = gateArrivingAt(at(1));

        ConfigTestSupport.set(ConfigManager.ConfigKeys.WORLDGUARD_ENABLED, false);
        assertTrue(RegionFlags.mayUse(player, gate), "off, a denying region must not refuse use");
        assertTrue(RegionFlags.mayBuild(player, gate), "off, a denying region must not refuse building");
        assertTrue(RegionFlags.mayBuild(player, List.of(at(5))));

        ConfigTestSupport.set(ConfigManager.ConfigKeys.WORLDGUARD_ENABLED, true);
        assertFalse(RegionFlags.mayUse(player, gate), "back on, the flags registered at startup refuse again");
        assertFalse(RegionFlags.mayBuild(player, gate));
        assertFalse(RegionFlags.mayBuild(player, List.of(at(5))));
    }

    /**
     * Turning the setting on with no flags registered cannot take effect, and says so; anything else can.
     *
     * <p>WorldGuard closes its flag registry before plugins enable, so only a restart can add them.
     */
    @Test
    void followingTheSettingFailsOnlyWhenTurnedOnWithNothingRegistered()
    {
        ConfigTestSupport.set(ConfigManager.ConfigKeys.WORLDGUARD_ENABLED, true);
        assertThrows(IllegalStateException.class, RegionFlags::follow);

        ConfigTestSupport.set(ConfigManager.ConfigKeys.WORLDGUARD_ENABLED, false);
        assertDoesNotThrow(RegionFlags::follow, "turning it off always applies");

        RegionFlagsTestSupport.install((who, where, action) -> true);
        assertDoesNotThrow(RegionFlags::follow, "flags registered at startup can be turned back on");
    }
}
