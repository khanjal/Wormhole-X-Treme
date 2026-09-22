package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * A shut iris over an open wormhole, stacked from whichever side it is seen.
 *
 * <p>An upright gate's iris is drawn, so it can be put where each viewer needs it. From the
 * front the iris is in the ring and the horizon a block behind; from behind it is the other
 * way about, the horizon in the ring and the iris a block further off. Before this the horizon
 * was drawn a block behind for everybody, which put it on the near side for anyone round the
 * back -- in front of the iris instead of behind it, and in the cells they could walk into.
 *
 * <p>The gate faces north, so its front is the smaller-z side. The ring is at z=20, the cell
 * behind it at z=21 and the one in front at z=19.
 */
class IrisLayeringTest
{
    private static final int X = 10, Y = 64, Z = 20;

    /** Held: a Location keeps its World weakly, so an inline mock can be collected mid-test. */
    private World world;
    private Stargate gate;
    private Player viewer;
    private BlockData iris;
    private BlockData horizon;
    private BlockData truthBehind;
    private BlockData truthAhead;
    private Block ahead;
    private MockedStatic<MaterialUtils> materials;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");

        iris = mock(BlockData.class);
        horizon = mock(BlockData.class);
        truthBehind = mock(BlockData.class);
        truthAhead = mock(BlockData.class);
        materials = mockStatic(MaterialUtils.class);
        materials.when(() -> MaterialUtils.drawnAs(Material.IRON_BLOCK)).thenReturn(iris);
        materials.when(() -> MaterialUtils.drawnAs(Material.WATER)).thenReturn(horizon);
        materials.when(() -> MaterialUtils.isAirMaterial(Material.AIR)).thenReturn(true);
        materials.when(() -> MaterialUtils.isAirMaterial(Material.STONE)).thenReturn(false);

        final Block ring = blockAt(Z, null);
        when(ring.getType()).thenReturn(Material.AIR);
        blockAt(Z + 1, truthBehind);
        ahead = blockAt(Z - 1, truthAhead);

        gate = new Stargate();
        gate.setGateName("Layered");
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.NORTH);
        // Its own materials, so the iris and the horizon can be told apart in what is sent;
        // per-gate materials only count on a gate marked custom.
        gate.setGateCustom(true);
        gate.setGateCustomIrisMaterial(Material.IRON_BLOCK);
        gate.setGateCustomPortalMaterial(Material.WATER);
        gate.getGatePortalBlocks().add(new Location(world, X, Y, Z));
        gate.setGateActive(true);
        gate.setGateIrisActive(true);

        viewer = mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.getWorld()).thenReturn(world);
        when(world.getPlayers()).thenReturn(List.of(viewer));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        materials.close();
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    /**
     * An air block at X, Y, z, which knows where it is.
     *
     * <p>Its coordinates matter: whether a cell is free to draw a layer in asks the block index
     * what gate claims it, and the index answers by position.
     *
     * @param z
     *            the cell's z
     * @param truth
     *            the block data a hand-back should send, or null if the test never checks
     * @return the block
     */
    private Block blockAt(final int z, final BlockData truth)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.AIR);
        when(block.getX()).thenReturn(Integer.valueOf(X));
        when(block.getY()).thenReturn(Integer.valueOf(Y));
        when(block.getZ()).thenReturn(Integer.valueOf(z));
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(new Location(world, X, Y, z));
        if (truth != null)
        {
            when(block.getBlockData()).thenReturn(truth);
        }
        when(world.getBlockAt(X, Y, z)).thenReturn(block);
        return block;
    }

    /** Matches a location by its block, whatever its world. */
    private static Location at(final int z)
    {
        return argThat(l -> (l != null) && (l.getBlockX() == X) && (l.getBlockY() == Y) && (l.getBlockZ() == z));
    }

    private void standAt(final int z)
    {
        when(viewer.getLocation()).thenReturn(new Location(world, X + 0.5, Y, z + 0.5));
    }

    // -----------------------------------------------------------------------
    // Which side
    // -----------------------------------------------------------------------

    /**
     * The front is the side the gate faces, and behind is the other.
     *
     * <p>Get the sign wrong and every viewer sees the other side's picture: the horizon in front
     * of the iris from the front, which hides the very barrier it was meant to be behind.
     */
    @Test
    void theFrontIsTheSideTheGateFaces()
    {
        assertTrue(StargateBlockSetup.seesFront(gate, new Location(world, X + 0.5, Y, Z - 3.0)),
            "a north-facing gate's front is to the north, the smaller z");
        assertFalse(StargateBlockSetup.seesFront(gate, new Location(world, X + 0.5, Y, Z + 4.0)),
            "and three blocks the other way is behind it");
    }

    // -----------------------------------------------------------------------
    // What each side sees
    // -----------------------------------------------------------------------

    /**
     * From the front: iris in the ring, horizon a block behind it.
     */
    @Test
    void fromTheFrontTheIrisIsInTheRingAndTheHorizonBehindIt()
    {
        standAt(Z - 4);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer).sendBlockChange(at(Z), eq(iris));
        verify(viewer).sendBlockChange(at(Z + 1), eq(horizon));
        verify(viewer, never()).sendBlockChange(at(Z - 1), eq(iris));
    }

    /**
     * From behind: horizon in the ring, iris a block further off.
     *
     * <p>The whole point of the step. Before it, the horizon was a block behind the ring for
     * everybody, which for somebody round the back is the side they are standing on.
     */
    @Test
    void fromBehindTheHorizonIsInTheRingAndTheIrisBeyondIt()
    {
        standAt(Z + 4);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer).sendBlockChange(at(Z), eq(horizon));
        verify(viewer).sendBlockChange(at(Z - 1), eq(iris));
        verify(viewer, never()).sendBlockChange(at(Z + 1), eq(horizon));
    }

    /**
     * From behind, with something built where the iris would go, the iris stays in the ring.
     *
     * <p>Only air is drawn in. Without room for the iris beyond the ring there is no layering to
     * be had, and drawing the horizon in the ring anyway would leave a viewer looking at a shut
     * gate through what looks like an open one.
     */
    @Test
    void fromBehindWithNoRoomBeyondTheIrisStaysInTheRing()
    {
        standAt(Z + 4);
        when(ahead.getType()).thenReturn(Material.STONE);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer).sendBlockChange(at(Z), eq(iris));
        verify(viewer, never()).sendBlockChange(at(Z - 1), any(BlockData.class));
        verify(viewer, never()).sendBlockChange(any(Location.class), eq(horizon));
    }

    /**
     * A rear draw hands back the cell the front's horizon uses.
     *
     * <p>Sent every time rather than only after a crossing, because the draw does not know what
     * this viewer was shown before: a player who has just walked round would otherwise keep the
     * front's horizon hanging behind the gate. The crossing itself is
     * {@link #crossingThePlaneRestacksTheGateAndWanderingDoesNot}.
     */
    @Test
    void aRearDrawHandsBackTheCellTheFrontsHorizonUses()
    {
        standAt(Z + 4);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer).sendBlockChange(at(Z + 1), eq(truthBehind));
    }

    /**
     * A gate that has stopped being layered has both cells handed back on the next refresh.
     *
     * <p>The hand-back when a wormhole closes reaches whoever is in drawing range at that
     * moment, and a client holds a chunk far past that. Walk away, let the gate close behind
     * you, walk back: the refresh redraws the idle gate's iris in the ring and used to leave
     * the layers alone -- a sheet of water behind the gate, and worse, a solid iris block in
     * the cell travellers arrive in, which the viewer's own client will not let them walk
     * through.
     */
    @Test
    void comingBackToAGateThatHasClosedHandsBothLayersBack()
    {
        standAt(Z - 4);
        StargateBlockSetup.sendLayeredTo(viewer, gate);
        StargateManager.registerStargate(gate);
        clearInvocations(viewer);
        // The wormhole closed while they were away, out of range of the hand-back.
        gate.setGateActive(false);

        StargateBlockSetup.refreshPortalVisuals(viewer);

        verify(viewer).sendBlockChange(at(Z + 1), eq(truthBehind));
        verify(viewer).sendBlockChange(at(Z - 1), eq(truthAhead));
    }

    /**
     * A neighbouring gate's opening is not a free cell to draw a layer in.
     *
     * <p>An upright gate's ring is air on the server now that its iris is drawn, so "is it air"
     * stopped being enough: two gates built a block apart would draw this one's horizon over
     * that one's iris, and hand back air over it when the layers came down.
     */
    @Test
    void anotherGatesOpeningIsNotFreeToDrawIn()
    {
        standAt(Z - 4);
        final Stargate neighbour = new Stargate();
        neighbour.setGateName("Neighbour");
        neighbour.setGateWorld(world);
        neighbour.setGateFacing(BlockFace.SOUTH);
        neighbour.getGatePortalBlocks().add(new Location(world, X, Y, Z + 1));
        StargateManager.registerStargate(neighbour);
        StargateManager.addBlockIndex(world.getBlockAt(X, Y, Z + 1), neighbour);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer).sendBlockChange(at(Z), eq(iris));
        verify(viewer, never()).sendBlockChange(at(Z + 1), eq(horizon));
    }

    // -----------------------------------------------------------------------
    // Crossing the plane
    // -----------------------------------------------------------------------

    /**
     * Crossing the gate's plane restacks it, and moving about on one side does not.
     *
     * <p>Asked on every step that changes block, so it must cost nothing on a step that does
     * not cross, and the one step that does must redraw -- or a player who walks round a gate
     * goes on seeing the other side's picture until they cross a chunk.
     */
    @Test
    void crossingThePlaneRestacksTheGateAndWanderingDoesNot()
    {
        StargateManager.registerStargate(gate);
        standAt(Z - 4);
        StargateBlockSetup.relayerFor(viewer, new Location(world, X + 0.5, Y, Z - 3.5));
        clearInvocations(viewer);

        StargateBlockSetup.relayerFor(viewer, new Location(world, X + 1.5, Y, Z - 2.5));
        verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));

        StargateBlockSetup.relayerFor(viewer, new Location(world, X + 0.5, Y, Z + 2.5));
        verify(viewer).sendBlockChange(at(Z), eq(horizon));
        verify(viewer).sendBlockChange(at(Z - 1), eq(iris));
    }

    // -----------------------------------------------------------------------
    // Taking it back
    // -----------------------------------------------------------------------

    /**
     * When the wormhole goes, both layers go with it.
     *
     * <p>The horizon a block behind a shut iris was never taken back when the gate went idle, so
     * it hung there for anyone who had been watching. With the iris a block in front for
     * viewers behind, there are two cells to hand back, not one.
     */
    @Test
    void takingTheLayersBackHandsBothCellsTheirRealBlocks()
    {
        standAt(Z - 4);

        StargateBlockSetup.takeBackLayers(gate);

        verify(viewer).sendBlockChange(at(Z + 1), eq(truthBehind));
        verify(viewer).sendBlockChange(at(Z - 1), eq(truthAhead));
    }

    /**
     * Shutting an idle gate's iris hands back what was drawn either side of its ring.
     *
     * <p>A gate that was layered while its wormhole was open is left with the layers on clients'
     * screens once the wormhole goes. Shutting the iris on an idle gate is the moment there is
     * certainly nothing to stack, so it is where they come down.
     */
    @Test
    void shuttingTheIrisOnAnIdleGateHandsTheLayersBack()
    {
        standAt(Z - 4);
        gate.setGateActive(false);
        gate.setGateIrisActive(false);
        com.wormhole_xtreme.wormhole.config.ConfigTestSupport.set(
            com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.GATE_IRIS_ANIMATION, "instant");
        try
        {
            StargateLifecycle.setIrisState(gate, true);
        }
        finally
        {
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.clear();
        }

        verify(viewer).sendBlockChange(at(Z + 1), eq(truthBehind));
        verify(viewer).sendBlockChange(at(Z - 1), eq(truthAhead));
    }

    /**
     * A wormhole closing under a shut iris takes its layers with it, whatever the iris defaults to.
     *
     * <p>Shutdown only touches the iris when it defaults shut or is already open. One shut
     * against an open default reaches neither branch, and without its own hand-back the horizon
     * would go on hanging behind a gate that has closed.
     */
    @Test
    void aWormholeClosingUnderAShutIrisTakesItsLayersWithIt()
    {
        standAt(Z - 4);
        gate.setGateIrisDefaultActive(false);
        gate.setGatePlayerTeleportLocation(new Location(world, X + 0.5, Y, Z - 1.5));
        final Stargate quiet = spy(gate);
        doNothing().when(quiet).toggleDialLeverState(anyBoolean());
        doNothing().when(quiet).toggleRedstoneGateActivatedPower();
        doNothing().when(quiet).lightStargate(anyBoolean());
        com.wormhole_xtreme.wormhole.events.GateEvents.setDispatcherForTest(e -> { });
        try (MockedStatic<com.wormhole_xtreme.wormhole.utils.WorldUtils> utils =
            mockStatic(com.wormhole_xtreme.wormhole.utils.WorldUtils.class))
        {
            quiet.shutdownStargate(false, com.wormhole_xtreme.wormhole.events.StargateShutdownEvent.Reason.MANUAL);
        }
        finally
        {
            com.wormhole_xtreme.wormhole.events.GateEvents.setDispatcherForTest(null);
        }

        assertTrue(quiet.isGateIrisActive(), "the iris was left shut, against its open default");
        verify(viewer).sendBlockChange(at(Z + 1), eq(truthBehind));
        verify(viewer).sendBlockChange(at(Z - 1), eq(truthAhead));
    }

    /**
     * A horizontal gate is never layered.
     *
     * <p>Its iris is real blocks, a floor, and cannot be moved for anybody.
     */
    @Test
    void aHorizontalGateIsNotLayered()
    {
        gate.setGateFacing(BlockFace.UP);

        assertFalse(StargateBlockSetup.isLayered(gate), "a built iris stays where it is built");
    }

    /**
     * An idle gate with its iris shut is not layered either: there is no horizon to stack.
     */
    @Test
    void anIdleGateIsNotLayered()
    {
        gate.setGateActive(false);

        assertFalse(StargateBlockSetup.isLayered(gate), "no wormhole, nothing behind the iris");
    }
}
