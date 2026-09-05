package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A sign gate dials what its sign has been showing since before the server restarted.
 *
 * <p>A gate's selected destination is stored two ways and only one of them survives a
 * restart. The index is written into the gate's save data; the {@link Stargate} it points at
 * is worked out from the network when somebody clicks the sign, and is simply absent on a
 * freshly loaded gate. So a gate whose sign named a destination all along came back with no
 * destination to dial, and pressing its button did nothing.
 *
 * <p>The recovery that was there made it worse rather than better: it pretended somebody had
 * clicked the sign, which moved the selection on by one. Reported from in game as a gate that
 * ignores its lever until you cycle the sign by hand -- and what it dialled after that was the
 * gate <em>after</em> the one it had been left showing.
 */
public class DialSignTargetRestoreTest
{
    /** The gate under test: sign-powered, three peers to choose between. */
    private Stargate gate;

    @BeforeEach
    public void setUp()
    {
        // The sign code logs, and the logger goes through the static plugin reference.
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

        gate = new Stargate();
        gate.setGateName("home");
        gate.setGateSignPowered(true);
        gate.setGateDialSignBlock(signBlock());
        StargateManager.registerStargate(gate);

        // Networkless peers form the public pool the sign cycles through, sorted by name.
        for (final String name : new String[] { "alpha", "bravo", "charlie" })
        {
            final Stargate peer = new Stargate();
            peer.setGateName(name);
            StargateManager.registerStargate(peer);
        }
    }

    @AfterEach
    public void tearDown()
    {
        for (final Stargate s : StargateManager.getAllGates())
        {
            StargateManager.removeStargate(s);
        }
    }

    /** A wall sign block whose state can be read and written the way the sign code expects. */
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
     * A gate loaded with a saved selection dials that selection, not the one after it.
     *
     * <p>This is the whole bug: index 1 of alpha/bravo/charlie is bravo, which is the name
     * the sign in the world is still showing. Anything that answers charlie here has advanced
     * the selection while trying to restore it.
     */
    @Test
    public void aReloadedGateDialsTheDestinationItsSignWasShowing()
    {
        gate.setGateDialSignIndex(1);
        assertNull(gate.getGateDialSignTarget(), "a freshly loaded gate has no destination object yet");

        gate.refreshDialSignTarget();

        assertNotNull(gate.getGateDialSignTarget(), "the saved index names a destination; it must be resolvable");
        assertEquals("bravo", gate.getGateDialSignTarget().getGateName());
        assertEquals(1, gate.getGateDialSignIndex(), "restoring a selection must not move it on");
    }

    /**
     * Clicking the sign still advances it.
     *
     * <p>Restoring and cycling now share one method, so this is what stops the fix from
     * turning the dial sign into something that can never be changed.
     */
    @Test
    public void clickingTheSignStillAdvancesToTheNextDestination()
    {
        gate.setGateDialSignIndex(1);

        gate.teleportSignClicked(true);

        assertEquals("charlie", gate.getGateDialSignTarget().getGateName());
        assertEquals(2, gate.getGateDialSignIndex());
    }

    /** Clicking backwards still goes back, and wraps rather than going negative. */
    @Test
    public void clickingBackwardsFromTheFirstDestinationWrapsToTheLast()
    {
        gate.setGateDialSignIndex(0);

        gate.teleportSignClicked(false);

        assertEquals("charlie", gate.getGateDialSignTarget().getGateName());
        assertEquals(2, gate.getGateDialSignIndex());
    }

    /**
     * A sign nobody has ever clicked still selects nothing.
     *
     * <p>Restoring recovers a choice somebody made. Making one for them would have a gate
     * dial a destination its sign never named, which is the same surprise the bug caused,
     * pointed the other way.
     */
    @Test
    public void aSignThatWasNeverClickedSelectsNothing()
    {
        gate.setGateDialSignIndex(-1);

        gate.refreshDialSignTarget();

        assertNull(gate.getGateDialSignTarget());
        assertEquals(-1, gate.getGateDialSignIndex());
    }

    /**
     * A saved index left pointing past the end of the list comes back inside it.
     *
     * <p>Peers can be removed while a gate is unloaded, so an index saved against a longer
     * list is a case that really happens. Reading the list at that index would throw, and the
     * throw would surface as a gate that silently refuses to dial.
     */
    @Test
    public void aSavedIndexPastTheEndOfTheListStillResolves()
    {
        gate.setGateDialSignIndex(7);

        gate.refreshDialSignTarget();

        assertNotNull(gate.getGateDialSignTarget());
        assertEquals("bravo", gate.getGateDialSignTarget().getGateName(), "7 of three peers wraps to index 1");
    }
}
