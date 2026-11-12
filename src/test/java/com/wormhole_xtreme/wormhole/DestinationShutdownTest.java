package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.events.StargateShutdownEvent;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * The lever of the gate a wormhole arrives at, with {@code destination-shutdown} on (#637).
 *
 * <p>Only the dialling end of a wormhole holds a target, so before this the arriving end's lever
 * fell through to "Gate remotely activated." and an unwanted dial ran its whole timeout. The
 * setting lets that lever close it, by shutting the dialling gate, which closes both ends. Off,
 * the lever must do exactly what it always did.
 */
class DestinationShutdownTest
{
    /** The player working the destination's lever; an op, so no permission check intervenes. */
    private Player player;

    /** The gate that dialled, open and pointing at {@link #destination}. */
    private Stargate source;

    /** The gate the wormhole arrives at: open, with no target of its own. */
    private Stargate destination;

    @BeforeEach
    void setUp()
    {
        try
        {
            PluginTestSupport.install(mock(WormholeXTreme.class));
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
        player = player("closer");

        destination = spy(new Stargate());
        destination.setGateName("arrival");
        destination.setGateActive(true);
        StargateManager.registerStargate(destination);

        source = openGateTargeting("dialler", destination);
    }

    @AfterEach
    void tearDown()
    {
        ConfigTestSupport.clear();
        StargateManager.removeActivatedStargate(player);
        for (final Stargate s : StargateManager.getAllGates())
        {
            StargateManager.removeStargate(s);
        }
        PluginTestSupport.forgetAllGates();
        try
        {
            PluginTestSupport.remove();
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** A player mock with the identity plugin code keys on. */
    private static Player player(final String name)
    {
        final Player p = mock(Player.class);
        when(p.isOp()).thenReturn(true);
        when(p.getName()).thenReturn(name);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        return p;
    }

    /** A registered, open gate whose target is the given gate, with its shutdown stubbed out. */
    private static Stargate openGateTargeting(final String name, final Stargate target)
    {
        final Stargate gate = spy(new Stargate());
        gate.setGateName(name);
        gate.setGateActive(true);
        doReturn(target).when(gate).getGateTarget();
        doNothing().when(gate).shutdownStargate(anyBoolean(), any(StargateShutdownEvent.Reason.class));
        StargateManager.registerStargate(gate);
        return gate;
    }

    /** With the setting on, the arriving end's lever closes the wormhole from its dialling end. */
    @Test
    void theArrivingEndsLeverShutsTheDiallingGateWhenTheSettingIsOn()
    {
        ConfigTestSupport.set(ConfigKeys.DESTINATION_SHUTDOWN, true);

        assertTrue(GateInteractionHandler.closeOrDeactivate(destination, player));

        // MANUAL, because a lever was pulled; the dialling gate's shutdown closes this end as FAR_END.
        verify(source).shutdownStargate(true, StargateShutdownEvent.Reason.MANUAL);
        verify(player).sendMessage(ChatText.info("Gate shut down."));
        verify(player, never()).sendMessage(ChatText.error("Gate remotely activated."));
    }

    /**
     * Off, the arriving end's lever does what it always did: says the gate was dialled from
     * elsewhere and shuts nothing.
     */
    @Test
    void withTheSettingOffTheArrivingEndsLeverStillOnlySaysItWasRemotelyActivated()
    {
        ConfigTestSupport.set(ConfigKeys.DESTINATION_SHUTDOWN, false);

        assertFalse(GateInteractionHandler.closeOrDeactivate(destination, player));

        verify(player).sendMessage(ChatText.error("Gate remotely activated."));
        verify(source, never()).shutdownStargate(anyBoolean(), any(StargateShutdownEvent.Reason.class));
    }

    /**
     * A server that never sets it gets the old behaviour, so upgrading changes nothing.
     *
     * <p>Loads the shipped defaults rather than leaving the map empty, so this reads the default
     * the plugin writes; the test above with the setting on uses the same gates and does shut.
     */
    @Test
    void theShippedDefaultLeavesTheArrivingEndsLeverAsItWas()
    {
        ConfigTestSupport.loadDefaults();

        assertFalse(GateInteractionHandler.closeOrDeactivate(destination, player));

        verify(source, never()).shutdownStargate(anyBoolean(), any(StargateShutdownEvent.Reason.class));
        verify(player).sendMessage(ChatText.error("Gate remotely activated."));
    }

    /** An open gate nothing has dialled into is reported as before, setting or no setting. */
    @Test
    void anOpenGateNothingTargetsIsStillReportedAsRemotelyActivated()
    {
        ConfigTestSupport.set(ConfigKeys.DESTINATION_SHUTDOWN, true);
        final Stargate lonely = spy(new Stargate());
        lonely.setGateName("lonely");
        lonely.setGateActive(true);
        StargateManager.registerStargate(lonely);

        assertFalse(GateInteractionHandler.closeOrDeactivate(lonely, player));

        verify(player).sendMessage(ChatText.error("Gate remotely activated."));
        verify(source, never()).shutdownStargate(anyBoolean(), any(StargateShutdownEvent.Reason.class));
    }

    /**
     * The lookup finds the gate that dialled this one, not merely some open gate.
     *
     * <p>A server can have several wormholes open at once. Shutting the wrong one would close a
     * stranger's connection from a lever they never touched.
     */
    @Test
    void theLookupFindsTheGateDialledIntoThisOneNotAnotherOpenGate()
    {
        final Stargate elsewhere = spy(new Stargate());
        elsewhere.setGateName("elsewhere");
        elsewhere.setGateActive(true);
        StargateManager.registerStargate(elsewhere);
        final Stargate other = openGateTargeting("otherDialler", elsewhere);

        assertSame(source, StargateManager.findIncomingSource(destination));
        assertSame(other, StargateManager.findIncomingSource(elsewhere));
        assertNull(StargateManager.findIncomingSource(source), "nothing dialled the dialling gate");
    }

    /**
     * Whoever stands at the dialling gate is told the far end closed it; nobody else is.
     *
     * <p>Without the notice the wormhole would vanish in front of them with no reason given. The
     * radius keeps it to the people at that gate: someone at the arriving end, or at the same
     * spot in another world, has no wormhole to wonder about.
     */
    @Test
    void playersAtTheDiallingGateAreToldTheFarEndClosedIt()
    {
        final World world = mock(World.class);
        final World otherWorld = mock(World.class);
        source.setGateWorld(world);
        source.setGatePlayerTeleportLocation(new Location(world, 0, 64, 0));

        final Player near = playerAt(world, 5, 64, 3);
        final Player atDestination = playerAt(world, 100, 64, 100);
        final Player inOtherWorld = playerAt(otherWorld, 0, 64, 0);
        // Listed in the gate's world too, as a stale list might, so only the world check keeps them out.
        when(world.getPlayers()).thenReturn(List.of(near, atDestination, inOtherWorld, player));
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 1, 64, 1));
        ConfigTestSupport.set(ConfigKeys.DESTINATION_SHUTDOWN, true);

        assertTrue(GateInteractionHandler.closeOrDeactivate(destination, player));

        final String notice = ChatText.info("The far end closed the wormhole.");
        verify(near).sendMessage(notice);
        verify(atDestination, never()).sendMessage(anyString());
        verify(inOtherWorld, never()).sendMessage(anyString());
        verify(player, never()).sendMessage(notice);
    }

    /**
     * Closing an arriving wormhole leaves the player's own activation of another gate alone.
     *
     * <p>Looking up the player's activation removes it. Run before this branch, it dropped the
     * mapping to a gate the player had lit elsewhere, leaving that gate lit with nobody able to
     * deactivate it as theirs until its timer ran out.
     */
    @Test
    void closingAnArrivingWormholeKeepsThePlayersActivationOfAnotherGate()
    {
        final Stargate lit = new Stargate();
        lit.setGateName("litElsewhere");
        lit.setGateLightsActive(true);
        StargateManager.registerStargate(lit);
        StargateManager.addActivatedStargate(player, lit);
        ConfigTestSupport.set(ConfigKeys.DESTINATION_SHUTDOWN, true);

        assertTrue(GateInteractionHandler.closeOrDeactivate(destination, player));

        verify(source).shutdownStargate(true, StargateShutdownEvent.Reason.MANUAL);
        assertSame(lit, StargateManager.removeActivatedStargate(player),
            "the player's activation of the other gate must survive closing this one");
    }

    /** A notice that throws still leaves the wormhole closed and the lever reporting success. */
    @Test
    void aFailedNoticeDoesNotStopTheShutdown()
    {
        final World world = mock(World.class);
        source.setGateWorld(world);
        source.setGatePlayerTeleportLocation(new Location(world, 0, 64, 0));
        when(world.getPlayers()).thenThrow(new IllegalStateException("world gone"));
        ConfigTestSupport.set(ConfigKeys.DESTINATION_SHUTDOWN, true);

        assertTrue(GateInteractionHandler.closeOrDeactivate(destination, player));

        verify(source).shutdownStargate(true, StargateShutdownEvent.Reason.MANUAL);
        verify(player).sendMessage(ChatText.info("Gate shut down."));
    }

    /** A player standing at the given spot. */
    private static Player playerAt(final World world, final double x, final double y, final double z)
    {
        final Player p = player("at" + x);
        when(p.getWorld()).thenReturn(world);
        when(p.getLocation()).thenReturn(new Location(world, x, y, z));
        return p;
    }
}
