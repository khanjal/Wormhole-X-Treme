package com.wormhole_xtreme.wormhole.model;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.Complete;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;

/**
 * {@code min-gate-distance} (#635): off by default, same world only, centre to centre, the
 * nearest gate named, and checked before a fare is charged.
 */
class GateSpacingTest
{
    private final World world = mock(World.class);

    @BeforeEach
    void beforeEach()
    {
        ConfigTestSupport.clear();
        try
        {
            PluginTestSupport.install(mock(WormholeXTreme.class));
        }
        catch (final Throwable ignore) { /* only some paths need the stub server */ }
    }

    @AfterEach
    void afterEach()
    {
        ConfigTestSupport.clear();
    }

    /** A gate whose opening is the one block at x, 64, 0. */
    private static Stargate gateAt(final String name, final World in, final int x)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        gate.setGateWorld(in);
        gate.getGatePortalBlocks().add(new Location(in, x, 64, 0));
        return gate;
    }

    private static Player player(final boolean bypass)
    {
        final Player p = mock(Player.class);
        when(p.getName()).thenReturn("builder");
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.hasPermission(GateSpacing.BYPASS)).thenReturn(bypass);
        StargateManager.removeIncompleteStargate(p);
        return p;
    }

    private static void withEconomy(final int minDistance)
    {
        ConfigTestSupport.set(ConfigKeys.MIN_GATE_DISTANCE, minDistance);
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, true);
        ConfigTestSupport.set(ConfigKeys.ECONOMY_BUILD_COST, 5.0);
    }

    /** The shipped default changes nothing for a server that never sets it. */
    @Test
    void theLimitIsOffUnlessSet()
    {
        ConfigTestSupport.loadDefaults();
        assertEquals(0, ConfigManager.getMinGateDistance());
        ConfigTestSupport.clear();
        assertEquals(0, ConfigManager.getMinGateDistance());
    }

    /** A gate nearer than the limit is refused, naming the nearest gate and the distance needed. */
    @Test
    void aGateTooCloseIsRefusedNamingTheNearestOne()
    {
        final String why = GateSpacing.refusal(20, world, 10, 64, 0,
            List.of(gateAt("Far", world, 90), gateAt("Near", world, 0)));
        assertNotNull(why);
        assertTrue(why.contains("Near") && !why.contains("Far"), why);
        assertTrue(why.contains("10.0") && why.contains("20"), why);
    }

    /** Exactly the limit away is allowed, so 20 means gates may stand 20 apart. */
    @Test
    void exactlyTheLimitAwayIsAllowed()
    {
        assertNull(GateSpacing.refusal(20, world, 20, 64, 0, List.of(gateAt("A", world, 0))));
        assertNotNull(GateSpacing.refusal(20, world, 19, 64, 0, List.of(gateAt("A", world, 0))));
    }

    /** A gate in another world is never near, however close its coordinates. */
    @Test
    void anotherWorldIsNeverNear()
    {
        assertNull(GateSpacing.refusal(20, world, 0, 64, 0, List.of(gateAt("Elsewhere", mock(World.class), 0))));
    }

    /** The distance is measured between centres, not between nearest blocks. */
    @Test
    void distanceIsBetweenCentresOfTheOpenings()
    {
        final Stargate wide = gateAt("Wide", world, 0);
        wide.getGatePortalBlocks().add(new Location(world, 10, 64, 0));
        assertNull(GateSpacing.refusal(7, world, 12, 64, 0, List.of(wide)), "centre 5 is 7 away");
        assertNotNull(GateSpacing.refusal(8, world, 12, 64, 0, List.of(wide)));
    }

    /** With the setting at 0 nothing is checked, whatever stands beside it. */
    @Test
    void nothingIsCheckedWhileTheLimitIsOff()
    {
        final Stargate near = gateAt("Near", world, 0);
        StargateManager.registerStargate(near);
        try
        {
            assertNull(GateSpacing.refusal(player(false), world, List.of(new Location(world, 1, 64, 0))));
        }
        finally
        {
            StargateManager.removeStargate(near);
        }
    }

    /** The bypass node exempts a builder who would otherwise be refused. */
    @Test
    void theBypassNodeExemptsABuilder()
    {
        ConfigTestSupport.set(ConfigKeys.MIN_GATE_DISTANCE, 50);
        final Stargate near = gateAt("Near", world, 0);
        StargateManager.registerStargate(near);
        try
        {
            final List<Location> opening = List.of(new Location(world, 5, 64, 0));
            assertNotNull(GateSpacing.refusal(player(false), world, opening));
            assertNull(GateSpacing.refusal(player(true), world, opening));
        }
        finally
        {
            StargateManager.removeStargate(near);
        }
    }

    /**
     * A refused completion charges nothing: the check comes before the fare, so a player turned
     * away for standing too close is not out of pocket and the gate is not made.
     */
    @Test
    void aRefusedCompletionIsNotCharged()
    {
        final Player player = player(false);
        final Stargate near = gateAt("Near", world, 0);
        StargateManager.registerStargate(near);
        StargateManager.addIncompleteStargate(player, gateAt("Held", world, 5));
        withEconomy(50);
        try (MockedStatic<EconomySupport> eco = Mockito.mockStatic(EconomySupport.class))
        {
            eco.when(EconomySupport::isAvailable).thenReturn(true);
            eco.when(() -> EconomySupport.canAfford(any(), anyDouble())).thenReturn(true);

            assertFalse(Complete.completeAndCharge(player, "Mine", "", "", "failed", null));

            eco.verify(() -> EconomySupport.canAfford(any(), anyDouble()), never());
            eco.verify(() -> EconomySupport.charge(any(), anyDouble()), never());
        }
        finally
        {
            StargateManager.removeStargate(near);
            StargateManager.removeIncompleteStargate(player);
        }
        verify(player).sendMessage(plainContains("Too close to Near"));
        assertNull(StargateManager.getStargate("Mine"));
    }

    /** Somebody holding the node completes beside another gate and still pays the fare. */
    @Test
    void anExemptBuilderIsStillChargedAndCompletes()
    {
        final Player player = player(true);
        final Stargate near = gateAt("Near", world, 0);
        StargateManager.registerStargate(near);
        StargateManager.addIncompleteStargate(player, gateAt("Held", world, 5));
        withEconomy(50);
        try (MockedStatic<EconomySupport> eco = Mockito.mockStatic(EconomySupport.class);
            MockedStatic<StargateManager> mgr = Mockito.mockStatic(StargateManager.class, Mockito.CALLS_REAL_METHODS))
        {
            eco.when(EconomySupport::isAvailable).thenReturn(true);
            eco.when(() -> EconomySupport.canAfford(any(), anyDouble())).thenReturn(true);
            eco.when(() -> EconomySupport.currencyName(anyDouble())).thenReturn("coins");
            mgr.when(() -> StargateManager.completeStargate(any(), anyString(), anyString(), anyString()))
                .thenReturn(true);

            assertTrue(Complete.completeAndCharge(player, "Mine", "", "", "failed", null));

            eco.verify(() -> EconomySupport.charge(player, 5.0));
        }
        finally
        {
            StargateManager.removeStargate(near);
            StargateManager.removeIncompleteStargate(player);
        }
    }
}
