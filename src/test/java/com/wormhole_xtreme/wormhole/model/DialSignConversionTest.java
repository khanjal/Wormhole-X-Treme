package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.PluginForTests;

/**
 * What survives a dial sign being restyled.
 *
 * <p>{@link DialSignMaterialMatchTest} covers whether to convert at all. This covers what
 * happens when the answer is yes, which is a block replacement: the plugin swaps a block a
 * player placed for one in the gate's own wood.
 *
 * <p>Changing a block's type wipes a sign. Everything on it therefore has to be read out
 * first and written back after -- the text on both faces, whether each face glows, and the way
 * the sign is facing -- and none of that was covered by anything. A gate that regenerates
 * restyles its dial sign every time, so a step missed here is a player's sign quietly emptying
 * itself on a command that was supposed to leave it alone.
 */
class DialSignConversionTest
{
    private static final String[] FRONT = { "-Alpha-", "Beta", "", "Gamma" };
    private static final String[] BACK = { "back one", "back two", "back three", "" };

    private Block block;
    private Sign before;
    private Sign after;
    private SignSide oldFront, oldBack, newFront, newBack;
    private Directional oldData, newData;
    private Stargate gate;
    private WormholeXTreme plugin;
    private MockedStatic<ConfigManager> config;

    @BeforeEach
    void setUp()
    {
        oldFront = sideHolding(FRONT, true);
        oldBack = sideHolding(BACK, false);
        newFront = sideHolding(new String[] { "", "", "", "" }, false);
        newBack = sideHolding(new String[] { "", "", "", "" }, false);

        before = mock(Sign.class);
        when(before.getSide(Side.FRONT)).thenReturn(oldFront);
        when(before.getSide(Side.BACK)).thenReturn(oldBack);

        after = mock(Sign.class);
        when(after.getSide(Side.FRONT)).thenReturn(newFront);
        when(after.getSide(Side.BACK)).thenReturn(newBack);

        // Directional extends BlockData, so one mock stands in for both.
        oldData = mock(Directional.class);
        when(oldData.getFacing()).thenReturn(BlockFace.EAST);
        newData = mock(Directional.class);

        block = mock(Block.class);
        when(block.getType()).thenReturn(Material.OAK_WALL_SIGN);
        // The block is read twice: once as it was, and once as it is after the retype.
        when(block.getState()).thenReturn(before, after);
        when(block.getBlockData()).thenReturn(oldData, newData);

        gate = mock(Stargate.class);
        when(gate.getGateName()).thenReturn("alpha");
        when(gate.getGateDialSignBlock()).thenReturn(block);
        when(gate.getEffectiveSignMaterial()).thenReturn(Material.CRIMSON_WALL_SIGN);

        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::isSignDialMatchMaterial).thenReturn(Boolean.TRUE);

        // Installed for every test, not just the one about logging: two of these are about
        // this method staying quiet, which cannot be checked without something to be quiet to.
        plugin = mock(WormholeXTreme.class);
        setPlugin(plugin);
    }

    private static void setPlugin(final WormholeXTreme value)
    {
        try
        {
            PluginForTests.install(value);
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void tearDown()
    {
        setPlugin(null);
        config.close();
    }

    private static SignSide sideHolding(final String[] lines, final boolean glowing)
    {
        final SignSide side = mock(SignSide.class);
        when(side.getLines()).thenReturn(lines.clone());
        when(side.isGlowingText()).thenReturn(Boolean.valueOf(glowing));
        return side;
    }

    /** The four lines written back onto one face, in order. */
    private static String[] linesWrittenTo(final SignSide side)
    {
        final ArgumentCaptor<Integer> index = ArgumentCaptor.forClass(Integer.class);
        final ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(side, org.mockito.Mockito.atLeast(0)).setLine(index.capture(), text.capture());
        final String[] written = { null, null, null, null };
        for (int i = 0; i < index.getAllValues().size(); i++)
        {
            written[index.getAllValues().get(i).intValue()] = text.getAllValues().get(i);
        }
        return written;
    }

    /** Both faces come back with the text that was on them. */
    @Test
    void theTextOnBothFacesSurvivesTheRestyle()
    {
        StargateBlockSetup.matchDialSignMaterial(gate);

        assertArrayEquals(FRONT, linesWrittenTo(newFront), "the front is put back as it was");
        assertArrayEquals(BACK, linesWrittenTo(newBack), "and so is the back");
    }

    /**
     * Glow is per face, and is not simply turned off.
     *
     * <p>Set from what each face had rather than from the other one or from a default: a sign
     * with a glowing front and a plain back has to come back that way round.
     */
    @Test
    void eachFaceKeepsItsOwnGlow()
    {
        StargateBlockSetup.matchDialSignMaterial(gate);

        verify(newFront).setGlowingText(true);
        verify(newBack).setGlowingText(false);
    }

    /**
     * The sign keeps facing the way it faced.
     *
     * <p>A wall sign that comes back facing somewhere else is a sign on the wrong wall, and
     * the gate's dial block is the one it was mounted on.
     */
    @Test
    void theSignStillFacesTheWayItDid()
    {
        StargateBlockSetup.matchDialSignMaterial(gate);

        verify(newData).setFacing(BlockFace.EAST);
        verify(block).setBlockData(newData, false);
    }

    /**
     * The gate is handed the new sign state, not left holding the old one.
     *
     * <p>Every destination the plugin later writes to the dial sign goes through this. Left
     * pointing at the state read before the retype, each of those writes would be aimed at a
     * block that no longer exists.
     */
    @Test
    void theGateIsGivenTheReplacementSignToWriteThrough()
    {
        StargateBlockSetup.matchDialSignMaterial(gate);

        verify(gate).setGateDialSign(after);
        verify(after).update(true, false);
    }

    /**
     * The retype does not run block physics.
     *
     * <p>A wall sign is only attached to the block behind it. Replacing it with physics on is
     * an invitation for the server to decide it has nothing to hang from and pop it off as an
     * item, which is the sign gone rather than restyled.
     */
    @Test
    void theBlockIsRetypedWithoutPhysics()
    {
        StargateBlockSetup.matchDialSignMaterial(gate);

        verify(block).setType(Material.CRIMSON_WALL_SIGN, false);
        verify(block, never()).setType(any(Material.class));
    }

    /**
     * The text is read before the block is retyped, not after.
     *
     * <p>The one ordering the whole method exists to get right. Reading afterwards reads a
     * fresh, blank sign, and writes four empty lines back with nothing to say anything was
     * lost.
     */
    @Test
    void whatIsOnTheSignIsReadBeforeTheBlockIsChanged()
    {
        StargateBlockSetup.matchDialSignMaterial(gate);

        final InOrder order = inOrder(oldFront, block, newFront);
        order.verify(oldFront).getLines();
        order.verify(block).setType(any(Material.class), anyBoolean());
        order.verify(newFront, atLeastOnce()).setLine(anyInt(), anyString());
    }

    /** A face with nothing on a line writes an empty line rather than a null one. */
    @Test
    void aBlankLineIsWrittenBackAsEmptyRatherThanNull()
    {
        when(oldFront.getLines()).thenReturn(new String[] { "kept", null, null, "also kept" });

        StargateBlockSetup.matchDialSignMaterial(gate);

        assertArrayEquals(new String[] { "kept", "", "", "also kept" }, linesWrittenTo(newFront));
    }

    /**
     * A sign reporting more lines than a sign has does not overrun.
     *
     * <p>A sign has four. Nothing in Bukkit promises that today and forever, and writing a
     * fifth would throw out of a method whose whole job is not to lose the first four.
     */
    @Test
    void aSignClaimingMoreThanFourLinesIsNotWrittenPastTheFourth()
    {
        when(oldFront.getLines()).thenReturn(new String[] { "a", "b", "c", "d", "e", "f" });

        assertDoesNotThrow(() -> StargateBlockSetup.matchDialSignMaterial(gate));

        verify(newFront, never()).setLine(eq(4), anyString());
        assertArrayEquals(new String[] { "a", "b", "c", "d" }, linesWrittenTo(newFront));
    }

    /**
     * A sign that will not convert is reported and left, not thrown at the caller.
     *
     * <p>This runs from {@code /wormhole regenerate} and from completing a gate. A server
     * whose block state does something unexpected should cost a restyle, not the command.
     */
    @Test
    void aFailurePartWayThroughIsLoggedAndSwallowed()
    {
        when(block.getState()).thenThrow(new IllegalStateException("chunk went away"));

        assertDoesNotThrow(() -> StargateBlockSetup.matchDialSignMaterial(gate));

        verify(plugin).prettyLog(eq(Level.WARNING),
            eq("Could not match dial sign material on gate alpha"),
            any(IllegalStateException.class));
    }

    /**
     * A block whose state is not a sign any more is left alone, quietly.
     *
     * <p>Quietly is the point. Somebody breaking their dial sign is an ordinary thing to do,
     * and this runs on every complete and every regenerate -- so treating it as a fault would
     * put a warning in the log for a gate that is merely waiting for its sign back. Checked
     * rather than left to the catch below, which would reach the same outcome by way of a
     * ClassCastException and a log line.
     */
    @Test
    void aBlockThatIsNoLongerASignIsLeftAloneWithoutComplaint()
    {
        final org.bukkit.block.BlockState notASign = mock(org.bukkit.block.BlockState.class);
        when(block.getState()).thenReturn(notASign);

        StargateBlockSetup.matchDialSignMaterial(gate);

        verify(block, never()).setType(any(Material.class), anyBoolean());
        verify(gate, never()).setGateDialSign(any(Sign.class));
        verify(plugin, never()).prettyLog(any(Level.class), anyString(), any(Throwable.class));
        verify(plugin, never()).prettyLog(any(Level.class), anyString());
    }

    /**
     * A face that reports no lines at all does not take the rest of the sign down with it.
     *
     * <p>The one place a missing guard costs more than the thing it guards. By the time the
     * faces are written the block has already been retyped, so an exception here leaves a
     * blank sign in the wall *and* the gate still writing through the state it read before --
     * worse than either alone. The other face and the handover have to happen anyway.
     */
    @Test
    void aFaceReportingNoLinesDoesNotStopTheRestOfTheConversion()
    {
        when(oldFront.getLines()).thenReturn(null);

        StargateBlockSetup.matchDialSignMaterial(gate);

        assertArrayEquals(BACK, linesWrittenTo(newBack), "the other face is still put back");
        verify(newFront).setGlowingText(true);
        verify(gate).setGateDialSign(after);
    }
}
