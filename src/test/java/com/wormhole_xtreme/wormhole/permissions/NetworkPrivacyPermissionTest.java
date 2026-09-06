package com.wormhole_xtreme.wormhole.permissions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * Who may use a gate on a network that is not Public.
 *
 * <p>The rule is two nodes, not one: holding {@code wormhole.use.dialer} lets a player work a
 * dialler, and holding {@code wormhole.network.use.<name>} lets them onto that network. A
 * private network needs both. Only the operator path and a blanket denial were covered, so
 * the half of the check that actually keeps a private network private -- the second node --
 * had nothing asserting it.
 *
 * <p>The same expression is written out five times in the method under test, once per
 * permission type, which is exactly the shape of thing that gets consolidated wrongly.
 */
class NetworkPrivacyPermissionTest
{
    private static final String OWNER = "00000000-0000-0000-0000-000000000001";

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));
    }

    /** A player holding exactly the nodes named, op to nobody. */
    private static Player playerWith(final String... nodes)
    {
        final Player p = mock(Player.class);
        when(p.getName()).thenReturn("visitor");
        when(p.isOp()).thenReturn(false);
        when(p.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));
        when(p.hasPermission(anyString())).thenReturn(false);
        for (final String node : nodes)
        {
            when(p.hasPermission(node)).thenReturn(true);
        }
        return p;
    }

    /** An owned gate on the named network, or on Public when the name is null. */
    private static Stargate gateOn(final String network)
    {
        final Stargate gate = new Stargate();
        gate.setGateName("far");
        gate.setGateOwner(OWNER);
        if (network != null)
        {
            gate.setGateNetwork(StargateManager.addStargateNetwork(network));
        }
        return gate;
    }

    @Test
    void thePublicNetworkNeedsOnlyTheDiallerNode()
    {
        final Player p = playerWith("wormhole.use.dialer");

        assertTrue(WXPermissions.checkWXPermissions(p, gateOn(null), PermissionType.DIALER),
            "a gate with no network of its own is Public, and the dialler node is enough");
    }

    /**
     * A private network is not opened by the dialler node alone.
     *
     * <p>This is the assertion that keeps private networks private. If the two halves of the
     * check are ever folded together wrongly, this is what fails.
     */
    @Test
    void aPrivateNetworkIsNotOpenedByTheDiallerNodeAlone()
    {
        final Player p = playerWith("wormhole.use.dialer");

        assertFalse(WXPermissions.checkWXPermissions(p, gateOn("secret"), PermissionType.DIALER),
            "the dialler node is not admission to somebody else's private network");
    }

    @Test
    void aPrivateNetworkOpensWithBothNodes()
    {
        final Player p = playerWith("wormhole.use.dialer", "wormhole.network.use.secret");

        assertTrue(WXPermissions.checkWXPermissions(p, gateOn("secret"), PermissionType.DIALER));
    }

    /** The network node on its own is not a substitute for the action node. */
    @Test
    void theNetworkNodeAloneIsNotEnough()
    {
        final Player p = playerWith("wormhole.network.use.secret");

        assertFalse(WXPermissions.checkWXPermissions(p, gateOn("secret"), PermissionType.DIALER),
            "being allowed on the network is not the same as being allowed to dial");
    }

    /** The network node is per network, not a blanket pass. */
    @Test
    void aNodeForOneNetworkDoesNotOpenAnother()
    {
        final Player p = playerWith("wormhole.use.dialer", "wormhole.network.use.secret");

        assertFalse(WXPermissions.checkWXPermissions(p, gateOn("other"), PermissionType.DIALER),
            "wormhole.network.use.secret says nothing about the network called other");
    }

    /** SIGN carries the same two-node rule as DIALER. */
    @Test
    void theSignPathIsGuardedTheSameWay()
    {
        assertFalse(WXPermissions.checkWXPermissions(playerWith("wormhole.use.sign"),
            gateOn("secret"), PermissionType.SIGN),
            "a sign on a private network is still on a private network");
        assertTrue(WXPermissions.checkWXPermissions(
            playerWith("wormhole.use.sign", "wormhole.network.use.secret"),
            gateOn("secret"), PermissionType.SIGN));
    }

    /** USE is satisfied by either the sign route or the dialler route, both network-gated. */
    @Test
    void useAcceptsEitherRouteAndStillChecksTheNetwork()
    {
        assertTrue(WXPermissions.checkWXPermissions(
            playerWith("wormhole.use.sign", "wormhole.network.use.secret"),
            gateOn("secret"), PermissionType.USE));
        assertTrue(WXPermissions.checkWXPermissions(
            playerWith("wormhole.use.dialer", "wormhole.network.use.secret"),
            gateOn("secret"), PermissionType.USE));
        assertFalse(WXPermissions.checkWXPermissions(
            playerWith("wormhole.use.sign", "wormhole.use.dialer"),
            gateOn("secret"), PermissionType.USE),
            "neither route lets a player past a network they are not on");
    }

    /** BUILD reads the build network node, not the use one. */
    @Test
    void buildingOnAPrivateNetworkWantsTheBuildNode()
    {
        assertFalse(WXPermissions.checkWXPermissions(
            playerWith("wormhole.build", "wormhole.network.use.secret"),
            gateOn("secret"), PermissionType.BUILD),
            "permission to travel a network is not permission to build on it");
        assertTrue(WXPermissions.checkWXPermissions(
            playerWith("wormhole.build", "wormhole.network.build.secret"),
            gateOn("secret"), PermissionType.BUILD));
    }

    /**
     * A gate nobody owns is public property for the common actions.
     *
     * <p>Deliberate: gates created before ownership was recorded have no owner, and locking
     * everyone out of them would strand them.
     */
    @Test
    void anOwnerlessGateIsUsableByAnyone()
    {
        final Stargate ownerless = new Stargate();
        ownerless.setGateName("orphan");

        final Player p = playerWith();

        assertTrue(WXPermissions.checkWXPermissions(p, ownerless, PermissionType.DIALER));
        assertTrue(WXPermissions.checkWXPermissions(p, ownerless, PermissionType.USE));
        assertFalse(WXPermissions.checkWXPermissions(p, ownerless, PermissionType.REMOVE),
            "usable by anyone is not the same as removable by anyone");
    }
}
