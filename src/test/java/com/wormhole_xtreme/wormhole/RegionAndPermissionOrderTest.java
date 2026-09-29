package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.events.StargateShutdownEvent;
import com.wormhole_xtreme.wormhole.integration.RegionFlags;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * Which wins when a permission node and a WorldGuard region disagree (#240).
 *
 * <p>Neither: both must allow. The node decides who, the region decides where, and a flag only
 * ever takes away. The permission is asked first, so a player without the node hears that and
 * not about a region they could never have used anyway. Owning the gate, or being an operator,
 * does not get past a region; WorldGuard's own bypass is the only way round one.
 */
class RegionAndPermissionOrderTest
{
    private Player player;

    @BeforeEach
    void setUp() throws ReflectiveOperationException
    {
        PluginTestSupport.install();
        player = mock(Player.class);
        when(player.getName()).thenReturn("builder");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        // Every region here denies everything, so only the permission can make a difference.
        RegionFlags.setCheckForTest((who, where, action) -> false);
    }

    @AfterEach
    void tearDown() throws ReflectiveOperationException
    {
        RegionFlags.setCheckForTest(null);
        StargateManager.removeIncompleteStargate(player);
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    /** A gate just built, standing at one spot, with its button. */
    private static Stargate builtGate()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("found");
        gate.getGateStructureBlocks().add(new Location(null, 0, 64, 0));
        return gate;
    }

    private static Block button()
    {
        final Block button = mock(Block.class);
        when(button.getWorld()).thenReturn(mock(World.class));
        return button;
    }

    /** Without the build node the player is told that, and not about the region. */
    @Test
    void withoutTheBuildNodeThePermissionIsWhatRefuses()
    {
        final Stargate gate = builtGate();
        try (MockedStatic<WXPermissions> perms = mockStatic(WXPermissions.class);
             MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            perms.when(() -> WXPermissions.checkWXPermissions(player, gate, PermissionType.BUILD)).thenReturn(false);

            GateInteractionHandler.offerNewGate(player, button(), gate);
        }

        verify(player).sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
        verify(player, never()).sendMessage(RegionFlags.BUILD_REFUSED);
        assertNull(StargateManager.getIncompleteStargate(player));
    }

    /** With the node, a region denying gate building still refuses it, and says it was the region. */
    @Test
    void withTheBuildNodeADenyingRegionStillRefuses()
    {
        final Stargate gate = builtGate();
        try (MockedStatic<WXPermissions> perms = mockStatic(WXPermissions.class);
             MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            perms.when(() -> WXPermissions.checkWXPermissions(player, gate, PermissionType.BUILD)).thenReturn(true);

            GateInteractionHandler.offerNewGate(player, button(), gate);
        }

        verify(player).sendMessage(RegionFlags.BUILD_REFUSED);
        verify(player, never()).sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
        assertNull(StargateManager.getIncompleteStargate(player), "a refused gate must not wait to be completed");
    }

    /** The counterpart: node and region both allowing, the gate waits to be named. */
    @Test
    void withTheNodeAndAnAllowingRegionTheGateIsOffered()
    {
        RegionFlags.setCheckForTest((who, where, action) -> true);
        final Stargate gate = builtGate();
        try (MockedStatic<WXPermissions> perms = mockStatic(WXPermissions.class);
             MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            perms.when(() -> WXPermissions.checkWXPermissions(player, gate, PermissionType.BUILD)).thenReturn(true);

            GateInteractionHandler.offerNewGate(player, button(), gate);
        }

        assertSame(gate, StargateManager.getIncompleteStargate(player));
    }

    /**
     * The gate's owner, an operator, pressing its DHD in a region denying use is refused.
     *
     * <p>The lever path lets owners and operators past every permission node before it gets here;
     * the region is asked after that, so it holds for them too.
     */
    @Test
    void theOwnerIsRefusedByADenyingRegionToo()
    {
        when(player.isOp()).thenReturn(true);
        final Stargate gate = spy(new Stargate());
        gate.setGateName("owned");
        gate.setGatePlayerTeleportLocation(new Location(null, 0, 64, 0));
        doReturn(true).when(gate).isOwner(player);
        doNothing().when(gate).lightAllChevrons();
        doNothing().when(gate).startActivationTimer(any(Player.class));

        assertFalse(GateInteractionHandler.handleGateActivationSwitch(gate, player));

        verify(player).sendMessage(RegionFlags.USE_REFUSED);
        verify(gate, never()).lightAllChevrons();
        verify(gate, never()).startActivationTimer(any(Player.class));
    }

    /** Shutting an open gate down is not a region's to refuse: a denied gate must still be closable. */
    @Test
    void anOpenGateInADenyingRegionCanStillBeShut()
    {
        final Stargate gate = spy(new Stargate());
        gate.setGateName("open");
        gate.setGateActive(true);
        gate.setGatePlayerTeleportLocation(new Location(null, 0, 64, 0));
        doReturn(new Stargate()).when(gate).getGateTarget();
        doNothing().when(gate).shutdownStargate(anyBoolean(), any(StargateShutdownEvent.Reason.class));

        assertTrue(GateInteractionHandler.handleGateActivationSwitch(gate, player));

        verify(gate).shutdownStargate(true, StargateShutdownEvent.Reason.MANUAL);
        verify(player, never()).sendMessage(RegionFlags.USE_REFUSED);
    }
}
