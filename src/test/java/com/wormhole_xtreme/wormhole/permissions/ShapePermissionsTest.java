package com.wormhole_xtreme.wormhole.permissions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * {@code wormhole.build.<shape>}: building a gate of one shape without holding {@code wormhole.build} (#631).
 */
class ShapePermissionsTest
{
    private static final String OWNER = "00000000-0000-0000-0000-000000000001";

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
        StargateShapeRegistry.getStargateShapes().remove("Standard");
        StargateShapeRegistry.getStargateShapes().remove("Grand");
    }

    /** Loads a shape of that name, as the registry holds them. */
    private static void load(final String name)
    {
        final StargateShape shape = new StargateShape();
        shape.setShapeName(name);
        StargateShapeRegistry.getStargateShapes().put(name, shape);
    }

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

    /** A gate of the shape of that name, owned by somebody else, as one found by detection is not yet owned by the builder. */
    private static Stargate gateOf(final String shapeName)
    {
        final StargateShape shape = new StargateShape();
        shape.setShapeName(shapeName);
        final Stargate gate = new Stargate();
        gate.setGateName("far");
        gate.setGateOwner(OWNER);
        gate.setGateShape(shape);
        return gate;
    }

    /** The node is the shape's name in lower case, with anything but a letter, digit or underscore made an underscore. */
    @Test
    void aShapesNodeIsItsNameMadeSafe()
    {
        assertEquals("wormhole.build.standard", ShapePermissions.buildNode("Standard"));
        assertEquals("wormhole.build.grand_gate", ShapePermissions.buildNode("Grand Gate"));
        assertEquals("wormhole.build.my_shape_2", ShapePermissions.buildNode("My-Shape.2"));
        assertEquals("wormhole.build.__a", ShapePermissions.buildNode("é-A"), "everything outside a-z0-9_ becomes an underscore");
    }

    /** A name with nothing to name a node by gets none, and a word that is a node already is not given away. */
    @Test
    void aNameThatLeavesNothingOrIsTakenHasNoNode()
    {
        assertNull(ShapePermissions.buildNode(null));
        assertNull(ShapePermissions.buildNode(""));
        assertNull(ShapePermissions.buildNode("---"), "nothing but underscores would be one node for many shapes");
        assertNull(ShapePermissions.buildNode("Preview"), "wormhole.build.preview is the preview node");
        assertNull(ShapePermissions.buildNode("MinDistance"), "wormhole.build.mindistance is a node's start");
    }

    /** The wildcard: wormhole.build builds every shape, as before. */
    @Test
    void wormholeBuildStillBuildsEveryShape()
    {
        assertTrue(WXPermissions.checkWXPermissions(playerWith("wormhole.build"), gateOf("Grand"), PermissionType.BUILD));
    }

    /** The point: the shape's own node builds that shape without the wildcard. */
    @Test
    void aShapesOwnNodeBuildsThatShape()
    {
        assertTrue(WXPermissions.checkWXPermissions(playerWith("wormhole.build.standard"), gateOf("Standard"),
            PermissionType.BUILD));
    }

    /** And only that shape: the small shape's node is not the large one's. */
    @Test
    void aShapesNodeDoesNotBuildAnotherShape()
    {
        assertFalse(WXPermissions.checkWXPermissions(playerWith("wormhole.build.standard"), gateOf("Grand"),
            PermissionType.BUILD), "the Standard node says nothing about Grand");
    }

    /** A shape's name made safe reaches its node: the check reads the same word the declaration does. */
    @Test
    void theCheckUsesTheSafeNameOfTheShape()
    {
        assertTrue(WXPermissions.checkWXPermissions(playerWith("wormhole.build.grand_gate"), gateOf("Grand Gate"),
            PermissionType.BUILD));
    }

    /** The shape node is not admission to a private network: the network's build node is still wanted. */
    @Test
    void aShapeNodeStillNeedsTheNetworksBuildNode()
    {
        final Stargate gate = gateOf("Standard");
        gate.setGateNetwork(com.wormhole_xtreme.wormhole.model.StargateManager.addStargateNetwork("secret"));

        assertFalse(WXPermissions.checkWXPermissions(playerWith("wormhole.build.standard"), gate, PermissionType.BUILD));
        assertTrue(WXPermissions.checkWXPermissions(playerWith("wormhole.build.standard", "wormhole.network.build.secret"),
            gate, PermissionType.BUILD));
    }

    /** A gate with no shape known (a check before detection) is not opened by any shape node. */
    @Test
    void aGateWithNoShapeIsNotOpenedByAShapeNode()
    {
        final Stargate gate = new Stargate();
        gate.setGateOwner(OWNER);
        gate.setGateShape(null);

        assertFalse(WXPermissions.checkWXPermissions(playerWith("wormhole.build.standard"), gate, PermissionType.BUILD));
    }

    /**
     * Naming a gate before its frame is clicked is checked against the network alone, and a shape's node passes it (#631).
     *
     * <p>{@code /wormhole complete} has no gate yet at that point. Refusing a player who holds only
     * {@code wormhole.build.standard} there let them click the frame and then never finish the gate.
     */
    @Test
    void aShapeNodeOpensTheNetworkOnlyCheckBeforeAGateIsKnown()
    {
        load("Standard");

        assertTrue(WXPermissions.checkWXPermissions(playerWith("wormhole.build.standard"), "", PermissionType.BUILD));
        assertFalse(WXPermissions.checkWXPermissions(playerWith("wormhole.build.grand"), "", PermissionType.BUILD),
            "a node for a shape that is not loaded is not one to build with");
        assertFalse(WXPermissions.checkWXPermissions(playerWith(), "", PermissionType.BUILD));
    }

    /**
     * No network typed means the Public one (#631).
     *
     * <p>{@code /wormhole complete <name>} without {@code net=} passes an empty network, which was
     * read as a network called "" and needed {@code wormhole.network.build.}: a player holding
     * plain {@code wormhole.build} was refused unless they typed {@code net=Public}.
     */
    @Test
    void anEmptyNetworkIsThePublicOne()
    {
        assertTrue(WXPermissions.checkWXPermissions(playerWith("wormhole.build"), "", PermissionType.BUILD));
        assertTrue(WXPermissions.checkWXPermissions(playerWith("wormhole.build"), "  ", PermissionType.BUILD));
        assertFalse(WXPermissions.checkWXPermissions(playerWith("wormhole.build"), "secret", PermissionType.BUILD),
            "a network that is named still wants its own node");
    }

    /** Once the frame is found the shape is held to its own node, and the network to its build node. */
    @Test
    void theShapeIsCheckedOnceTheFrameIsFound()
    {
        load("Standard");
        load("Grand");

        assertTrue(WXPermissions.checkBuildOf(playerWith("wormhole.build.standard"), gateOf("Standard"), ""));
        assertFalse(WXPermissions.checkBuildOf(playerWith("wormhole.build.standard"), gateOf("Grand"), ""),
            "named first on the strength of the Standard node, the frame turned out to be Grand");
        assertTrue(WXPermissions.checkBuildOf(playerWith("wormhole.build"), gateOf("Grand"), ""),
            "the wildcard builds every shape");
        assertFalse(WXPermissions.checkBuildOf(playerWith("wormhole.build.standard"), gateOf("Standard"), "secret"),
            "the shape node is not the network's build node");
        assertTrue(WXPermissions.checkBuildOf(
            playerWith("wormhole.build.standard", "wormhole.network.build.secret"), gateOf("Standard"), "secret"));
    }

    /** Declaring a node that the server refuses does not stop the shapes loading. */
    @Test
    void aRefusedDeclarationIsContained()
    {
        final Server server = mock(Server.class);
        final PluginManager manager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(manager);
        doThrow(new IllegalArgumentException("already registered"))
            .when(manager).addPermission(any(Permission.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            ShapePermissions.declare(List.of("Standard", "Grand"));
        }

        verify(manager, times(2)).addPermission(any(Permission.class));
    }

    /** Declared to the server so a permissions plugin lists them, for operators by default, and not twice. */
    @Test
    void declareAddsEachShapesNodeOnce()
    {
        final Server server = mock(Server.class);
        final PluginManager manager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(manager);
        final Permission declared = mock(Permission.class);
        when(manager.getPermission("wormhole.build.grand")).thenReturn(declared);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            ShapePermissions.declare(List.of("Standard", "Grand", "Preview"));
        }

        verify(manager, times(1)).addPermission(argThat((Permission p) ->
            "wormhole.build.standard".equals(p.getName()) && (p.getDefault() == PermissionDefault.OP)));
        verify(manager, never()).addPermission(argThat((Permission p) -> "wormhole.build.grand".equals(p.getName())));
        verify(manager, never()).addPermission(argThat((Permission p) -> "wormhole.build.preview".equals(p.getName())));
    }

    /** With no server (startup order, or a test) declaring does nothing rather than failing. */
    @Test
    void declareWithoutAServerDoesNothing()
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::getServer).thenReturn(null);
            assertDoesNotThrow(() -> ShapePermissions.declare(List.of("Standard")));
        }
    }
}
