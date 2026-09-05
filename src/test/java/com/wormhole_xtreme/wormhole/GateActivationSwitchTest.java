package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * What pressing a gate's DHD button actually does.
 *
 * <p>This is the path a player takes every time they use a gate, and until now nothing
 * exercised it -- the reported bug that a sign gate ignores its lever after a restart lived
 * here, and the fix for it went in with only the redstone half under test. The reason is
 * ordinary rather than interesting: the decision needs a {@link Player}, so it looked like it
 * needed a live server. It does not. A mock player that reports {@code isOp()} takes the
 * owner/op bypass and never reaches the permission checks, which is the seam that makes all
 * of this reachable.
 *
 * <p>The methods under test were opened from private to package-private for exactly that
 * reason, in the same spirit as the rest of this suite: the decision is pulled far enough out
 * to be asked directly, rather than a fake world being built around it.
 */
public class GateActivationSwitchTest
{
    /** A player who owns everything, so permission checks never stand in the way. */
    private Player player;

    @BeforeEach
    public void setUp()
    {
        final WormholeXTreme pluginMock = mock(WormholeXTreme.class);
        try
        {
            final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
            f.setAccessible(true);
            f.set(null, pluginMock);
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }

        player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
        when(player.getName()).thenReturn("tester");
        // A bare Player mock answers null for getUniqueId(), and anything keying a map on it
        // then dies with an unhelpful NPE from inside plugin code.
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @AfterEach
    public void tearDown()
    {
        for (final Stargate s : StargateManager.getAllGates())
        {
            StargateManager.removeStargate(s);
        }
    }

    /** A wall sign block whose state reads back the way the sign code expects. */
    private static Block signBlock()
    {
        final Block block = mock(Block.class);
        final Sign state = mock(Sign.class);
        when(block.getType()).thenReturn(Material.OAK_WALL_SIGN);
        when(block.getState()).thenReturn(state);
        when(state.getSide(Side.FRONT)).thenReturn(mock(SignSide.class));
        return block;
    }

    /**
     * Pressing the DHD after a restart dials the destination the sign was left showing.
     *
     * <p>The bug as it was actually reported, on the path it was actually reported on. The
     * gate comes back from disk with its saved index and no destination object, and the press
     * has to resolve one without advancing it -- the old recovery advanced it, so the first
     * press dialled nothing and the next dialled one gate too far.
     */
    @Test
    public void pressingTheDhdAfterAReloadDialsWhatTheSignWasShowing()
    {
        final Stargate gate = spy(new Stargate());
        gate.setGateName("home");
        gate.setGateSignPowered(true);
        gate.setGateDialSignBlock(signBlock());
        StargateManager.registerStargate(gate);

        for (final String name : new String[] { "alpha", "bravo" })
        {
            final Stargate peer = new Stargate();
            peer.setGateName(name);
            StargateManager.registerStargate(peer);
        }

        // Straight off disk: the index survived, the gate it names did not.
        gate.setGateDialSignIndex(1);
        assertNull(gate.getGateDialSignTarget());
        doReturn(true).when(gate).dialStargate(any(Stargate.class), anyBoolean());

        assertTrue(GateInteractionHandler.dialFromSign(gate, player));

        // bravo, not alpha: index 1 of alpha/bravo is what the sign in the world still reads.
        verify(gate).dialStargate(argThat(t -> "bravo".equals(t.getGateName())), eq(false));
        assertEquals(1, gate.getGateDialSignIndex(), "the press must not have advanced the selection");
    }

    /**
     * A sign gate with nothing selected says so instead of dialling something.
     *
     * <p>Restoring recovers a choice somebody made. Dialling a destination the sign never
     * named would be the same surprise as the bug, pointed the other way.
     */
    @Test
    public void pressingTheDhdOnASignNobodyHasSetDialsNothing()
    {
        final Stargate gate = spy(new Stargate());
        gate.setGateName("unset");
        gate.setGateSignPowered(true);
        gate.setGateDialSignBlock(signBlock());
        gate.setGateDialSignIndex(-1);
        StargateManager.registerStargate(gate);

        final Stargate peer = new Stargate();
        peer.setGateName("somewhere");
        StargateManager.registerStargate(peer);

        assertFalse(GateInteractionHandler.dialFromSign(gate, player));
        verify(gate, never()).dialStargate(any(Stargate.class), anyBoolean());
    }

    /** Pressing the DHD on an open wormhole closes it. */
    @Test
    public void pressingTheDhdOnAnOpenGateShutsItDown()
    {
        final Stargate gate = spy(new Stargate());
        gate.setGateName("open");
        gate.setGateActive(true);
        doReturn(new Stargate()).when(gate).getGateTarget();
        doNothing().when(gate).shutdownStargate(anyBoolean());

        assertTrue(GateInteractionHandler.handleGateActivationSwitch(gate, player));
        verify(gate).shutdownStargate(true);
    }

    /**
     * A gate left lit by an activation with nobody behind it can still be put out.
     *
     * <p>An activation mapping can outlive the player it belongs to -- they log out, or it is
     * lost. Without this arm the gate would stay lit with no way for anyone to clear it, which
     * is why it exists at all.
     */
    @Test
    public void aGateLitByNobodyCanStillBeCleared()
    {
        final Stargate gate = spy(new Stargate());
        gate.setGateName("stale");
        gate.setGateLightsActive(true);
        doNothing().when(gate).lightStargate(anyBoolean());
        doNothing().when(gate).toggleDialLeverState(anyBoolean());

        assertTrue(GateInteractionHandler.handleGateActivationSwitch(gate, player));

        assertFalse(gate.isGateActive());
        verify(gate).lightStargate(false);
        verify(gate).toggleDialLeverState(false);
    }

    /** Pressing the DHD on a plain dial gate lights it and waits for a /dial. */
    @Test
    public void pressingTheDhdOnAPlainGateLightsItForDialling()
    {
        final Stargate gate = spy(new Stargate());
        gate.setGateName("plain");
        doNothing().when(gate).lightStargate(anyBoolean());
        doNothing().when(gate).startActivationTimer(any(Player.class));

        assertTrue(GateInteractionHandler.handleGateActivationSwitch(gate, player));

        verify(gate).lightStargate(true);
        verify(gate).startActivationTimer(player);
    }

    /**
     * A closed sign gate is routed to its sign, not lit for a manual /dial.
     *
     * <p>The two ways a gate is worked are mutually exclusive and the branch that chooses
     * between them is one line. Getting it wrong would light a sign gate and leave the player
     * typing /dial at a gate that has never accepted one.
     */
    @Test
    public void aClosedSignGateIsRoutedToItsSign()
    {
        final Stargate gate = spy(new Stargate());
        gate.setGateName("signRouted");
        gate.setGateSignPowered(true);
        gate.setGateDialSignBlock(signBlock());
        gate.setGateDialSignIndex(0);
        StargateManager.registerStargate(gate);

        final Stargate peer = new Stargate();
        peer.setGateName("peer");
        StargateManager.registerStargate(peer);
        doReturn(true).when(gate).dialStargate(any(Stargate.class), anyBoolean());

        assertTrue(GateInteractionHandler.handleGateActivationSwitch(gate, player));

        verify(gate).dialStargate(eq(peer), eq(false));
        verify(gate, never()).startActivationTimer(any(Player.class));
    }

    /**
     * Pressing the DHD of a gate you lit yourself puts it out.
     *
     * <p>The ordinary way to change your mind after activating a gate and before dialling it.
     */
    @Test
    public void pressingTheDhdOnYourOwnActivationDeactivatesIt()
    {
        final Stargate gate = spy(new Stargate());
        gate.setGateName("mine");
        gate.setGateLightsActive(true);
        doNothing().when(gate).lightStargate(anyBoolean());
        doNothing().when(gate).toggleDialLeverState(anyBoolean());
        StargateManager.addActivatedStargate(player, gate);

        assertTrue(GateInteractionHandler.handleGateActivationSwitch(gate, player));

        assertFalse(gate.isGateActive());
        verify(gate).lightStargate(false);
    }

    /**
     * A gate that is neither open nor lit is not something this arm can act on.
     *
     * <p>Reachable when a gate reports active without lights and without a target -- a state
     * the dial leaves behind if it fails part way. Saying so beats silently doing nothing.
     */
    @Test
    public void aGateInNeitherStateIsReportedRatherThanSilentlyIgnored()
    {
        final Stargate gate = spy(new Stargate());
        gate.setGateName("neither");
        gate.setGateActive(true);

        assertFalse(GateInteractionHandler.closeOrDeactivate(gate, player));

        verify(player).sendMessage(ConfigManager.MessageStrings.gateRemoveActive.toString());
        verify(gate, never()).shutdownStargate(anyBoolean());
    }

    /**
     * Clearing somebody else's activation tells both people.
     *
     * <p>Whoever pressed the button learns whose activation they cleared, and the original
     * activator learns theirs is gone rather than finding the gate dark later with no idea why.
     */
    @Test
    public void clearingSomebodyElsesActivationTellsThemBoth()
    {
        final Player activator = mock(Player.class);
        when(activator.getName()).thenReturn("someoneElse");
        when(activator.isOnline()).thenReturn(true);
        when(activator.getUniqueId()).thenReturn(UUID.randomUUID());

        final Stargate gate = spy(new Stargate());
        gate.setGateName("theirs");
        gate.setGateLightsActive(true);
        doNothing().when(gate).lightStargate(anyBoolean());
        doNothing().when(gate).toggleDialLeverState(anyBoolean());
        StargateManager.addActivatedStargate(activator, gate);

        GateInteractionHandler.forceClearStaleActivation(gate, player);

        verify(player).sendMessage(contains("was activated by: someoneElse"));
        verify(activator).sendMessage(contains("force-cleared by: tester"));
    }

    /**
     * A dial the far end refuses is reported, not silently swallowed.
     *
     * <p>{@code dialStargate} returns false whenever the target is busy -- already open,
     * already targeted, or behind a closed iris. Without this the player would press the
     * button and get nothing back at all.
     */
    @Test
    public void aRefusedDialTellsThePlayerTheGateIsBusy()
    {
        final Stargate gate = spy(new Stargate());
        gate.setGateName("refused");
        gate.setGateSignPowered(true);
        gate.setGateDialSignBlock(signBlock());
        gate.setGateDialSignIndex(0);
        StargateManager.registerStargate(gate);

        final Stargate peer = new Stargate();
        peer.setGateName("busy");
        StargateManager.registerStargate(peer);
        doReturn(false).when(gate).dialStargate(any(Stargate.class), anyBoolean());

        assertFalse(GateInteractionHandler.dialFromSign(gate, player));

        verify(player).sendMessage(ConfigManager.MessageStrings.gateRemoveActive.toString());
    }
}
