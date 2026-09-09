package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.events.GateEvents;
import com.wormhole_xtreme.wormhole.PluginForTests;

/**
 * Turning a detected gate into a registered one.
 *
 * <p>This is the last step of building: the half-built gate held against the player is looked
 * up, given a name and an owner, joined to a network if one was asked for, and registered.
 *
 * <p>Nothing ran it. The one test that names {@code completeStargate} stubs it out, so what
 * it actually does with the network, the owner and the registry was never checked.
 */
class CompleteStargateTest
{
    private Player player;
    private static final UUID BUILDER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @BeforeEach
    void setUp() throws Exception
    {
        PluginForTests.install(mock(WormholeXTreme.class));

        player = mock(Player.class);
        when(player.getName()).thenReturn("builder");
        when(player.getUniqueId()).thenReturn(BUILDER);

        clearRegistry();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        clearRegistry();
        PluginForTests.remove();
    }

    private static void clearRegistry()
    {
        for (final Stargate s : new ArrayList<>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
        StargateManager.removeIncompleteStargate(mock(Player.class));
    }

    /** A gate detected but not yet named, waiting against the player who built it. */
    private Stargate halfBuilt()
    {
        final Stargate gate = new Stargate();
        StargateManager.addIncompleteStargate(player, gate);
        return gate;
    }

    /** Runs completeStargate with the persistence and event plumbing stubbed out. */
    private boolean complete(final String name, final String idc, final String network)
    {
        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class);
             MockedStatic<GateEvents> events = mockStatic(GateEvents.class);
             MockedStatic<StargateDialManager> dial = mockStatic(StargateDialManager.class))
        {
            return StargateManager.completeStargate(player, name, idc, network);
        }
    }

    /**
     * A dial sign block the debug dump can read.
     *
     * <p>It needs a location and a type because that dump asks every block it names for both,
     * guarded only against the block itself being null.
     */
    private static org.bukkit.block.Block signBlock()
    {
        final org.bukkit.World world = mock(org.bukkit.World.class);
        final org.bukkit.block.Block b = mock(org.bukkit.block.Block.class);
        when(b.getLocation()).thenReturn(new org.bukkit.Location(world, 1, 64, 1));
        when(b.getType()).thenReturn(org.bukkit.Material.OAK_WALL_SIGN);
        return b;
    }

    /** Nothing waiting means nothing to complete. */
    @Test
    void aPlayerWithNoHalfBuiltGateCompletesNothing()
    {
        assertFalse(complete("alpha", "", ""));
        assertNull(StargateManager.getStargate("alpha"));
    }

    /** The gate is named, owned and registered. */
    @Test
    void aCompletedGateIsNamedOwnedAndRegistered()
    {
        final Stargate gate = halfBuilt();

        assertTrue(complete("alpha", "", ""));

        assertSame(gate, StargateManager.getStargate("alpha"), "it is findable by name");
        assertEquals("alpha", gate.getGateName());
        assertEquals(BUILDER.toString(), gate.getGateOwner());
        assertEquals("builder", gate.getGateOwnerName());
    }

    /** Completing it spends the pending build, so a second attempt has nothing to do. */
    @Test
    void completingSpendsThePendingBuild()
    {
        halfBuilt();

        assertTrue(complete("alpha", "", ""));
        assertFalse(complete("bravo", "", ""), "the same build cannot be completed twice");
        assertNull(StargateManager.getStargate("bravo"));
    }

    /** Naming a network that does not exist yet creates it and joins the gate to it. */
    @Test
    void aNamedNetworkIsCreatedIfItDoesNotExist()
    {
        final Stargate gate = halfBuilt();
        assertNull(StargateManager.getStargateNetwork("traders"),
            "the network does not exist before this");

        assertTrue(complete("alpha", "", "traders"));

        final StargateNetwork net = StargateManager.getStargateNetwork("traders");
        assertNotNull(net, "it was created");
        assertSame(net, gate.getGateNetwork(), "and the gate belongs to it");
        assertTrue(net.getNetworkGateList().contains(gate));
    }

    /** A network that already exists is joined rather than replaced. */
    @Test
    void anExistingNetworkIsReused()
    {
        final StargateNetwork existing = StargateManager.addStargateNetwork("traders");
        final Stargate gate = halfBuilt();

        assertTrue(complete("alpha", "", "traders"));

        assertSame(existing, gate.getGateNetwork(), "the same network object, not a new one");
    }

    /** No network named means no network joined. */
    @Test
    void anEmptyNetworkNameJoinsNothing()
    {
        final Stargate gate = halfBuilt();

        assertTrue(complete("alpha", "", ""));

        assertNull(gate.getGateNetwork(), "an unnamed gate belongs to no network");
    }

    /**
     * A sign-powered gate has its dial sign cycled to a first destination.
     *
     * <p>Without it the sign is blank until somebody clicks it, which reads as a broken gate.
     */
    @Test
    void aSignPoweredGateGetsItsSignCycled()
    {
        final Stargate gate = halfBuilt();
        gate.setGateSignPowered(true);
        gate.setGateDialSignBlock(signBlock());

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class);
             MockedStatic<GateEvents> events = mockStatic(GateEvents.class);
             MockedStatic<StargateDialManager> dial = mockStatic(StargateDialManager.class))
        {
            assertTrue(StargateManager.completeStargate(player, "alpha", "", ""));

            dial.verify(() -> StargateDialManager.teleportSignClicked(gate, true));
        }
    }

    /** A gate with no dial sign is not asked to cycle one. */
    @Test
    void aGateWithoutASignIsNotCycled()
    {
        final Stargate gate = halfBuilt();
        gate.setGateSignPowered(true);

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class);
             MockedStatic<GateEvents> events = mockStatic(GateEvents.class);
             MockedStatic<StargateDialManager> dial = mockStatic(StargateDialManager.class))
        {
            assertTrue(StargateManager.completeStargate(player, "alpha", "", ""));

            dial.verify(() -> StargateDialManager.teleportSignClicked(any(), anyBoolean()), never());
        }
    }

    /** The creation event fires once the gate is registered, so a listener can find it. */
    @Test
    void theCreatedEventFiresAfterRegistration()
    {
        final Stargate gate = halfBuilt();

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class);
             MockedStatic<GateEvents> events = mockStatic(GateEvents.class);
             MockedStatic<StargateDialManager> dial = mockStatic(StargateDialManager.class))
        {
            events.when(() -> GateEvents.fireCreated(any(), any())).thenAnswer(invocation ->
            {
                assertSame(gate, StargateManager.getStargate("alpha"),
                    "a listener must be able to look it up by name already");
                return null;
            });

            assertTrue(StargateManager.completeStargate(player, "alpha", "", ""));

            events.verify(() -> GateEvents.fireCreated(gate, player));
        }
    }
}
