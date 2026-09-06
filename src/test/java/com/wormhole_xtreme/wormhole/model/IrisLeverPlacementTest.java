package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Where the iris lever goes when the shape does not say.
 *
 * <p>A shape can mark {@code :IA} and name the block itself. Most do not, and then
 * {@code setupIrisLever} works the position out from the DHD button: back one step from the
 * face the button points out of, down one to the base of the column, then forward along the
 * gate's own facing so the lever ends up facing whoever is standing at the gate.
 *
 * <p>None of that was covered. Nothing called {@code setupIrisLever} at all --
 * {@code ShippedShapeIrisTest} reads the shape files rather than running this -- so neither
 * the derivation nor the refusal below had anything holding them in place.
 */
class IrisLeverPlacementTest
{
    private World world;

    @BeforeEach
    void installPlugin() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));
        world = mock(World.class);
    }

    private Block blockAt(final int x, final int y, final int z)
    {
        final Block b = mock(Block.class);
        when(b.getX()).thenReturn(x);
        when(b.getY()).thenReturn(y);
        when(b.getZ()).thenReturn(z);
        when(b.getLocation()).thenReturn(new Location(world, x, y, z));
        return b;
    }

    /**
     * A gate whose button points SOUTH, with the three steps of the walk stubbed out.
     *
     * <p>The button face points SOUTH, so the column is one step NORTH of it; the base is one
     * below that; and the lever hangs on the base's SOUTH face, which is the gate's facing.
     */
    private Stargate gateWithButton(final Block irisBlock)
    {
        final Stargate gate = new Stargate();
        gate.setGateName("iris");
        gate.setGateFacing(BlockFace.SOUTH);
        gate.setGateShape(new StargateShape());

        final Block button = blockAt(10, 65, 20);
        final Directional buttonData = mock(Directional.class);
        when(buttonData.getFacing()).thenReturn(BlockFace.SOUTH);
        when(button.getBlockData()).thenReturn(buttonData);

        final Block backing = blockAt(10, 65, 19);
        final Block dhdBase = blockAt(10, 64, 19);
        when(button.getRelative(BlockFace.NORTH)).thenReturn(backing);
        when(backing.getRelative(BlockFace.DOWN)).thenReturn(dhdBase);
        when(dhdBase.getRelative(BlockFace.SOUTH)).thenReturn(irisBlock);

        gate.setGateDialLeverBlock(button);
        return gate;
    }

    @Test
    void theLeverIsWorkedBackFromTheButtonToTheFrontOfTheDhdBase()
    {
        final Block irisBlock = blockAt(10, 64, 20);
        final Stargate gate = gateWithButton(irisBlock);

        StargateBlockSetup.setupIrisLever(gate, false);

        assertSame(irisBlock, gate.getGateIrisLeverBlock(),
            "back from the button face, down to the base, then forward along the gate facing");
    }

    /**
     * The lever does not take a block a redstone dial activator already holds.
     *
     * <p>StandardSignDialRedstone puts {@code [S:RD]} directly below {@code [S:A]}, which is
     * exactly where this walk lands. Claiming it would put a lever on top of the block that
     * dials the gate.
     */
    @Test
    void aBlockAlreadyHoldingTheRedstoneDialActivatorIsNotClaimed()
    {
        final Block irisBlock = blockAt(10, 64, 20);
        final Stargate gate = gateWithButton(irisBlock);
        gate.setGateRedstoneDialActivationBlock(blockAt(10, 64, 20));

        StargateBlockSetup.setupIrisLever(gate, false);

        assertNull(gate.getGateIrisLeverBlock(),
            "the redstone dial block was there first and keeps the spot");
    }

    /** Same refusal for the redstone sign activator. */
    @Test
    void aBlockAlreadyHoldingTheRedstoneSignActivatorIsNotClaimed()
    {
        final Block irisBlock = blockAt(10, 64, 20);
        final Stargate gate = gateWithButton(irisBlock);
        gate.setGateRedstoneSignActivationBlock(blockAt(10, 64, 20));

        StargateBlockSetup.setupIrisLever(gate, false);

        assertNull(gate.getGateIrisLeverBlock());
    }

    /** A block one step away is a different block, and does not stop the claim. */
    @Test
    void aRedstoneBlockSomewhereElseDoesNotStopTheClaim()
    {
        final Block irisBlock = blockAt(10, 64, 20);
        final Stargate gate = gateWithButton(irisBlock);
        gate.setGateRedstoneDialActivationBlock(blockAt(11, 64, 20));

        StargateBlockSetup.setupIrisLever(gate, false);

        assertSame(irisBlock, gate.getGateIrisLeverBlock());
    }

    /**
     * A redstone-activated shape has no DHD button to work from, so nothing is derived.
     *
     * <p>Those gates are dialled by redstone rather than by a button, and the walk above
     * starts at a button that is not there.
     */
    @Test
    void aRedstoneActivatedShapeGetsNoDerivedLever()
    {
        final Block irisBlock = blockAt(10, 64, 20);
        final Stargate gate = gateWithButton(irisBlock);
        gate.setGateShape(new Stargate3DShape(new String[] {
            "Name=Redstone", "REDSTONE_ACTIVATED=TRUE", "GateShape=", "Layer#1=",
            "[S][S]", "[S:EP][S]", "" }));

        StargateBlockSetup.setupIrisLever(gate, false);

        assertNull(gate.getGateIrisLeverBlock(),
            "a redstone-dialled gate has no button for the walk to start from");
    }

    /** Taking the lever down only touches a block that is actually a lever. */
    @Test
    void removalLeavesABlockThatIsNotALeverAlone()
    {
        final Block notALever = blockAt(10, 64, 20);
        when(notALever.getType()).thenReturn(Material.STONE);
        final Stargate gate = new Stargate();
        gate.setGateName("iris");
        gate.setGateFacing(BlockFace.SOUTH);
        gate.setGateIrisLeverBlock(notALever);

        StargateBlockSetup.setupIrisLever(gate, false);

        verify(notALever, never()).setType(Material.AIR);
    }
}
