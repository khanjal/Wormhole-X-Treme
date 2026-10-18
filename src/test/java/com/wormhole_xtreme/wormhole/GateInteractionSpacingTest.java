package com.wormhole_xtreme.wormhole;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainContains;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;

/**
 * A newly detected gate, plain or sign-dial, is held to {@code min-gate-distance} when its
 * builder is offered it, and the offer is withdrawn when it is refused (#635).
 */
class GateInteractionSpacingTest
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

    private Stargate gateAt(final String name, final int x, final boolean signed)
    {
        final Stargate gate = mock(Stargate.class);
        when(gate.getGateName()).thenReturn(name);
        when(gate.getGateWorld()).thenReturn(world);
        when(gate.getGatePortalBlocks()).thenReturn(List.of(new Location(world, x, 64, 0)));
        when(gate.isGateSignPowered()).thenReturn(signed);
        return gate;
    }

    private Player builder()
    {
        final Player p = mock(Player.class);
        when(p.getName()).thenReturn("builder");
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        return p;
    }

    private Block button()
    {
        final Block b = mock(Block.class);
        when(b.getWorld()).thenReturn(world);
        return b;
    }

    /**
     * Offering a gate beside another is refused and nothing is held for the builder; a sign-dial
     * gate has its sign put back as it was, as for any refused offer.
     */
    @Test
    void aSignDialGateTooCloseIsWithdrawnAndNotHeld()
    {
        ConfigTestSupport.set(ConfigKeys.MIN_GATE_DISTANCE, 50);
        final Player player = builder();
        final Stargate near = gateAt("Nearby", 0, false);
        final Stargate offered = gateAt("Offered", 5, true);
        try (MockedStatic<WXPermissions> perms = mockStatic(WXPermissions.class);
            MockedStatic<StargateManager> gates = mockStatic(StargateManager.class, Mockito.CALLS_REAL_METHODS))
        {
            perms.when(() -> WXPermissions.checkWXPermissions(player, offered, WXPermissions.PermissionType.BUILD))
                .thenReturn(true);
            gates.when(StargateManager::getAllGatesUnsorted).thenReturn(List.of(near));

            GateInteractionHandler.offerNewGate(player, button(), offered);

            verify(player).sendMessage(plainContains("Too close to Nearby"));
            verify(offered).resetTeleportSign();
            assertNull(StargateManager.getIncompleteStargate(player), "a refused gate must not be held");
        }
    }

    /** Far enough away, the same offer is held for the builder to name. */
    @Test
    void aGateFarEnoughIsHeld()
    {
        ConfigTestSupport.set(ConfigKeys.MIN_GATE_DISTANCE, 5);
        final Player player = builder();
        final Stargate near = gateAt("Nearby", 0, false);
        final Stargate offered = gateAt("Offered", 5, false);
        try (MockedStatic<WXPermissions> perms = mockStatic(WXPermissions.class);
            MockedStatic<StargateManager> gates = mockStatic(StargateManager.class, Mockito.CALLS_REAL_METHODS))
        {
            perms.when(() -> WXPermissions.checkWXPermissions(player, offered, WXPermissions.PermissionType.BUILD))
                .thenReturn(true);
            gates.when(StargateManager::getAllGatesUnsorted).thenReturn(List.of(near));

            GateInteractionHandler.offerNewGate(player, button(), offered);

            assertNotNull(StargateManager.getIncompleteStargate(player), "a gate far enough is held for naming");
        }
        finally
        {
            StargateManager.removeIncompleteStargate(player);
        }
    }
}
