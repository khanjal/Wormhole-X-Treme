package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

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
class DialSignTargetRestoreTest
{
    /** The gate under test: sign-powered, three peers to choose between. */
    private Stargate gate;

    /** The sign face the gate writes, so a test can say what it shows. */
    private SignSide face;

    /** Saving a gate, which a click does: held off the disk, and told to the test. */
    private MockedStatic<StargateDBManager> db;

    @BeforeEach
    void setUp()
    {
        // The sign code logs, and the logger goes through the static plugin reference.
        final WormholeXTreme pluginMock = mock(WormholeXTreme.class);
        try
        {
            PluginTestSupport.install(pluginMock);
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }

        db = mockStatic(StargateDBManager.class);
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
    void tearDown()
    {
        db.close();
        for (final Stargate s : StargateManager.getAllGates())
        {
            StargateManager.removeStargate(s);
        }
        PluginTestSupport.forgetAllGates();
    }

    /** A wall sign block whose state can be read and written the way the sign code expects. */
    private Block signBlock()
    {
        final Block block = mock(Block.class);
        final Sign state = mock(Sign.class);
        when(block.getType()).thenReturn(Material.OAK_WALL_SIGN);
        when(block.getState()).thenReturn(state);
        face = mock(SignSide.class);
        when(state.getSide(Side.FRONT)).thenReturn(face);
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
    void aReloadedGateDialsTheDestinationItsSignWasShowing()
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
    void clickingTheSignStillAdvancesToTheNextDestination()
    {
        gate.setGateDialSignIndex(1);

        gate.teleportSignClicked(true);

        assertEquals("charlie", gate.getGateDialSignTarget().getGateName());
        assertEquals(2, gate.getGateDialSignIndex());
    }

    /** Clicking backwards still goes back, and wraps rather than going negative. */
    @Test
    void clickingBackwardsFromTheFirstDestinationWrapsToTheLast()
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
    void aSignThatWasNeverClickedSelectsNothing()
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
    void aSavedIndexPastTheEndOfTheListStillResolves()
    {
        gate.setGateDialSignIndex(7);

        gate.refreshDialSignTarget();

        assertNotNull(gate.getGateDialSignTarget());
        assertEquals("bravo", gate.getGateDialSignTarget().getGateName(), "7 of three peers wraps to index 1");
    }

    /**
     * A gate on a named network cycles that network's gates, not the public pool.
     *
     * <p>The two pools are built by separate branches, and only the public one carries the
     * gates every other test here uses. Without this, a change to the network branch -- the one
     * a server with organised networks actually runs -- would break with nothing to catch it.
     */
    @Test
    void aNetworkedGateResolvesAgainstItsOwnNetwork()
    {
        final Stargate networked = new Stargate();
        networked.setGateName("hub");
        networked.setGateSignPowered(true);
        networked.setGateDialSignBlock(signBlock());
        StargateManager.addGateToNetwork(networked, "milkyway");
        networked.setGateNetwork(StargateManager.getStargateNetwork("milkyway"));
        StargateManager.registerStargate(networked);

        final Stargate onNet = new Stargate();
        onNet.setGateName("abydos");
        StargateManager.addGateToNetwork(onNet, "milkyway");
        onNet.setGateNetwork(StargateManager.getStargateNetwork("milkyway"));
        StargateManager.registerStargate(onNet);

        networked.setGateDialSignIndex(0);
        networked.refreshDialSignTarget();

        // alpha/bravo/charlie are networkless and sort ahead of abydos on name, so answering
        // any of them would mean the public pool leaked into a named network's sign.
        assertNotNull(networked.getGateDialSignTarget());
        assertEquals("abydos", networked.getGateDialSignTarget().getGateName());
    }

    /**
     * A gate with nothing to dial says so, rather than keeping a stale destination.
     *
     * <p>The last peer can be removed while a gate is unloaded. Leaving the old destination in
     * place would have the gate keep dialling a gate that is no longer there.
     */
    @Test
    void aGateWithNoPeersSelectsNothing()
    {
        gate.setGateDialSignIndex(1);
        gate.refreshDialSignTarget();
        assertNotNull(gate.getGateDialSignTarget(), "precondition: it has a destination to lose");

        for (final Stargate s : StargateManager.getAllGates())
        {
            if (!"home".equals(s.getGateName()))
            {
                StargateManager.removeStargate(s);
            }
        }

        gate.refreshDialSignTarget();

        assertNull(gate.getGateDialSignTarget());
    }

    /**
     * A dial sign that is no longer a sign leaves the gate's selection alone.
     *
     * <p>Someone breaks the sign, or it pops off when its support goes. The block position is
     * still recorded against the gate, so the next press reads a block state that is not a
     * sign at all -- and the refresh runs on exactly that path, on a gate that has just been
     * loaded and has no destination yet.
     *
     * <p>Reading the sign's lines from that state would throw, and the throw would surface as
     * a DHD that does nothing with no explanation. Leaving the selection untouched is what
     * lets the gate keep working once the sign is put back.
     */
    @Test
    void aDialSignThatIsNoLongerASignLeavesTheSelectionAlone()
    {
        final Block broken = mock(Block.class);
        when(broken.getType()).thenReturn(Material.AIR);
        final BlockState brokenState = mock(BlockState.class);
        when(broken.getState()).thenReturn(brokenState);
        gate.setGateDialSignBlock(broken);
        gate.setGateDialSignIndex(1);

        gate.refreshDialSignTarget();

        assertNull(gate.getGateDialSignTarget(), "nothing can be resolved from a block that is not a sign");
        assertEquals(1, gate.getGateDialSignIndex(), "and the saved selection survives for when the sign goes back");
    }

    /**
     * A restored selection is found by the name it had, not by the place it had in the list (#706).
     *
     * <p>The index is a place in a list of peers that moves when a gate is added or removed, and the file's
     * index is only as new as the last save. The sign in the world still shows the name.
     */
    @Test
    void aSelectionIsFoundByNameWhenTheListHasMoved()
    {
        gate.setGateDialSignIndex(0);
        gate.setGateDialSignTargetName("charlie");

        gate.refreshDialSignTarget();

        assertEquals("charlie", gate.getGateDialSignTarget().getGateName(), "not the first gate in the list");
        assertEquals(2, gate.getGateDialSignIndex(), "and the index follows the name");
    }

    /** A name that is no longer in the list falls back to the saved index, as before. */
    @Test
    void aSelectionWhoseGateIsGoneFallsBackToTheSavedIndex()
    {
        gate.setGateDialSignIndex(1);
        gate.setGateDialSignTargetName("removed");

        gate.refreshDialSignTarget();

        assertEquals("bravo", gate.getGateDialSignTarget().getGateName());
    }

    /**
     * Clicking the sign records the choice by name and saves the gate at once (#706).
     *
     * <p>It was written only at shutdown, so a server that stopped any other way came back with the
     * selection from the last save.
     */
    @Test
    void clickingTheSignRecordsTheNameAndSavesTheGate()
    {
        gate.setGateDialSignIndex(0);

        gate.teleportSignClicked(true);

        db.verify(() -> StargateDBManager.saveStargate(gate));

        assertEquals("bravo", gate.getGateDialSignTargetName());
    }

    /**
     * Restoring a selection is not a choice: nothing is written for it.
     *
     * <p>The name is of a gate no longer there, so the sign is restored to the saved place and its name
     * changes; that is still not a click.
     */
    @Test
    void restoringASelectionSavesNothing()
    {
        gate.setGateDialSignIndex(1);
        gate.setGateDialSignTargetName("removed");

        gate.refreshDialSignTarget();

        db.verifyNoInteractions();
    }

    /** A sign nobody has chosen with stays unchosen though a name was left on the gate. */
    @Test
    void aNameDoesNotChooseForASignThatWasReset()
    {
        gate.setGateDialSignIndex(-1);
        gate.setGateDialSignTargetName("bravo");

        gate.refreshDialSignTarget();

        assertNull(gate.getGateDialSignTarget(), "a refresh recovers a choice somebody made; it does not make one");
    }

    /**
     * A gate saved before the name was kept is found by what its sign shows (#706).
     *
     * <p>The sign is in the world, so it is as new as the last click even when the file's index is not:
     * the gate file said the first gate, and the sign said charlie.
     */
    @Test
    void aGateWithNoSavedNameIsFoundByWhatItsSignShows()
    {
        gate.setGateDialSignIndex(0);
        when(face.getLine(2)).thenReturn("\u00a72\u00bbcharlie\u00ab");

        gate.refreshDialSignTarget();

        assertEquals("charlie", gate.getGateDialSignTarget().getGateName());
        assertEquals(2, gate.getGateDialSignIndex());
    }

    /** A sign that shows a peer's name but not as the selection (no markers) leaves the saved index in charge. */
    @Test
    void aSignThatShowsNoNameLeavesTheSavedIndex()
    {
        gate.setGateDialSignIndex(1);
        when(face.getLine(2)).thenReturn("-charlie-");

        gate.refreshDialSignTarget();

        assertEquals("bravo", gate.getGateDialSignTarget().getGateName());
    }

    /** The name the gate saved wins over what the sign shows; the sign is only for a gate with no saved name. */
    @Test
    void aSavedNameWinsOverTheSign()
    {
        gate.setGateDialSignIndex(0);
        gate.setGateDialSignTargetName("bravo");
        when(face.getLine(2)).thenReturn("\u00bbcharlie\u00ab");

        gate.refreshDialSignTarget();

        assertEquals("bravo", gate.getGateDialSignTarget().getGateName());
    }

    /**
     * The reset of a gate that was replaced under its name leaves the sign alone (#706).
     *
     * <p>A regenerate puts a new gate on the same sign; the old one's idle reset, two ticks behind, blanked
     * what the new one had just drawn.
     */
    @Test
    void theResetOfAGateThatWasReplacedLeavesTheSignAlone()
    {
        final Stargate replaced = new Stargate();
        replaced.setGateName("home");
        replaced.setGateDialSignBlock(gate.getGateDialSignBlock());
        replaced.setGateDialSignIndex(2);

        StargateDialManager.resetSign(replaced, true);

        assertEquals(2, replaced.getGateDialSignIndex(), "gate is the one registered as home, replaced is not");
    }

    /** A gate nothing replaced is reset as before, including one being removed. */
    @Test
    void theResetOfAGateNothingReplacedStillGoesIdle()
    {
        gate.setGateDialSignIndex(2);

        StargateDialManager.resetSign(gate, true);

        assertEquals(-1, gate.getGateDialSignIndex());
        StargateManager.removeStargate(gate);
        gate.setGateDialSignIndex(2);

        StargateDialManager.resetSign(gate, true);

        assertEquals(-1, gate.getGateDialSignIndex(), "with no successor, a removed gate's sign goes idle too");
    }
}
