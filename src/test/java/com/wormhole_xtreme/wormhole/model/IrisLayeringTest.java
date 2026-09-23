package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import static org.mockito.Mockito.times;
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
    private com.wormhole_xtreme.wormhole.WormholeXTreme plugin;
    private MockedStatic<MaterialUtils> materials;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = PluginTestSupport.install();
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
        return blockAt(X, z, truth);
    }

    /**
     * The same, in a column beside the gate's own.
     *
     * @param x
     *            the cell's x
     * @param z
     *            the cell's z
     * @param truth
     *            the block data a hand-back should send, or null if the test never checks
     * @return the block
     */
    private Block blockAt(final int x, final int z, final BlockData truth)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.AIR);
        when(block.getX()).thenReturn(Integer.valueOf(x));
        when(block.getY()).thenReturn(Integer.valueOf(Y));
        when(block.getZ()).thenReturn(Integer.valueOf(z));
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(new Location(world, x, Y, z));
        if (truth != null)
        {
            when(block.getBlockData()).thenReturn(truth);
        }
        when(world.getBlockAt(x, Y, z)).thenReturn(block);
        return block;
    }

    /**
     * An air block anywhere, registered with the world so the layering can read it.
     *
     * @param x
     *            the cell's x
     * @param y
     *            the cell's y
     * @param z
     *            the cell's z
     */
    private void airAt(final int x, final int y, final int z)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.AIR);
        when(block.getX()).thenReturn(Integer.valueOf(x));
        when(block.getY()).thenReturn(Integer.valueOf(y));
        when(block.getZ()).thenReturn(Integer.valueOf(z));
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(new Location(world, x, y, z));
        when(block.getBlockData()).thenReturn(truthBehind);
        when(world.getBlockAt(x, y, z)).thenReturn(block);
    }

    /** Matches a location by its block, whatever its world. */
    private static Location at(final int z)
    {
        return argThat(l -> (l != null) && (l.getBlockX() == X) && (l.getBlockY() == Y) && (l.getBlockZ() == z));
    }

    private void standAt(final int z)
    {
        standAt(X, z);
    }

    /**
     * The same, off to one side of the gate's own column.
     *
     * @param x
     *            the cell to stand in the middle of, across the gate
     * @param z
     *            the cell to stand in the middle of, along the facing
     */
    private void standAt(final int x, final int z)
    {
        when(viewer.getLocation()).thenReturn(new Location(world, x + 0.5, Y, z + 0.5));
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
     * From behind, with something built where the iris would go, the horizon keeps the ring.
     *
     * <p>Only air is drawn in, so there is nowhere to put the iris. What belongs in the plane
     * is drawn first and the other layer follows when there is somewhere for it: from behind
     * that means the wormhole, alone.
     *
     * <p>This inverts what the test here used to assert. The iris kept the ring so that a shut
     * gate could never read as an open one -- but in the world that meant walking along the
     * back of a gate swapped the wormhole out for a wall of bare iris, and swapped it back
     * again, which looked far more broken than it looked safe. Nothing about the barrier
     * itself moved: a traveller is refused by the gate's state, never by its picture, which is
     * what {@code DrawnIrisHoldsShutTest} covers.
     */
    @Test
    void fromBehindWithNoRoomBeyondTheHorizonKeepsTheRing()
    {
        standAt(Z + 4);
        when(ahead.getType()).thenReturn(Material.STONE);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer).sendBlockChange(at(Z), eq(horizon));
        verify(viewer, never()).sendBlockChange(at(Z - 1), any(BlockData.class));
        verify(viewer, never()).sendBlockChange(any(Location.class), eq(iris));
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
    // Seen from the side
    // -----------------------------------------------------------------------

    /**
     * From far enough round the side, the gate shows one layer and hands the other back.
     *
     * <p>Two layers a block apart read as one picture only while the opening is between the
     * viewer and the far one. Step round the side of a two-dimensional gate -- a single sheet
     * of blocks, with nothing else to hide anything -- and the far layer is simply a slab of
     * water hanging in the air beside the gate, which is what this looked like in the world.
     * A gate seen from there goes back to the one layer it can tell the truth with.
     */
    @Test
    void fromOffToTheSideTheGateShowsOneLayerOnly()
    {
        standAt(X + 4, Z - 4);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer).sendBlockChange(at(Z), eq(iris));
        verify(viewer, never()).sendBlockChange(any(Location.class), eq(horizon));
        verify(viewer).sendBlockChange(at(Z + 1), eq(truthBehind));
    }

    /**
     * A step to the side is only a step: the layers hold while the opening still hides them.
     *
     * <p>The paired half of {@link #fromOffToTheSideTheGateShowsOneLayerOnly}. Collapsing to one
     * layer at the first step off the gate's own column would throw the effect away for anyone
     * not standing dead in front of it.
     */
    @Test
    void aStepOrTwoToTheSideKeepsBothLayers()
    {
        standAt(X + 1, Z - 4);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer).sendBlockChange(at(Z), eq(iris));
        verify(viewer).sendBlockChange(at(Z + 1), eq(horizon));
    }

    /**
     * A whole Standard gate, stood in front of: every cell gets the horizon a block behind it.
     *
     * <p>Every other test here uses an opening one cell wide, which is a poor stand-in for the
     * thing that goes wrong in a world: 21 cells at 21 different angles from one pair of eyes,
     * and a ring of blocks with gaps at its corners. Written because a glass iris was showing
     * the landscape rather than the wormhole and it was not clear whether the horizon was being
     * drawn and not rendered, or never drawn at all. It is drawn: all 21 of it.
     *
     * <p>The layout is {@code Standard.shape}'s own ring layer, S for a gate block and P for
     * the opening, and the viewer stands where a player would -- a few blocks out, a step off
     * the gate's middle, feet on the ground below the opening.
     */
    @Test
    void awholeStandardOpeningSendsTheHorizonBehindEveryCell()
    {
        final String[] ring = {"IISSSII", "ISPPPSI", "SPPPPPS", "SPPPPPS", "SPPPPPS", "ISPPPSI", "IISSSII"};
        gate.getGatePortalBlocks().clear();
        for (int row = 0; row < ring.length; row++)
        {
            for (int col = 0; col < ring[row].length(); col++)
            {
                final int x = X + col - 3;
                final int y = Y + 3 - row;
                // The plane itself and the cells either side of it, all of them open air.
                airAt(x, y, Z - 1);
                airAt(x, y, Z);
                airAt(x, y, Z + 1);
                if (ring[row].charAt(col) == 'P')
                {
                    gate.getGatePortalBlocks().add(new Location(world, x, y, Z));
                }
                else if (ring[row].charAt(col) == 'S')
                {
                    gate.getGateStructureBlocks().add(new Location(world, x, y, Z));
                }
            }
        }
        assertEquals(21, gate.getGatePortalBlocks().size(), "the opening this shape really has");
        when(viewer.getLocation()).thenReturn(new Location(world, X + 1.5, Y - 3, Z - 4.5));

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer, times(21)).sendBlockChange(any(Location.class), eq(horizon));
        verify(viewer, times(21)).sendBlockChange(any(Location.class), eq(iris));
    }

    /**
     * Behind a glass iris the horizon is drawn as a look-alike, not as the liquid itself.
     *
     * <p>Java Edition skips the face between a fluid and a translucent block, so a wormhole
     * drawn right behind a stained-glass iris had its near face culled and its far face
     * pointing away: nothing left to see, and the gate showed the landscape through its own
     * iris. Nothing else behind one is hidden -- the world beyond it is drawn -- so the horizon
     * is drawn in something solid that looks like water, and the fluid rule stops applying.
     */
    @Test
    void behindAGlassIrisTheHorizonIsDrawnAsALookAlike()
    {
        final BlockData ice = mock(BlockData.class);
        final BlockData packed = mock(BlockData.class);
        glassIris(ice, packed);
        standAt(Z - 4);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer).sendBlockChange(at(Z), eq(iris));
        verify(viewer).sendBlockChange(at(Z + 1), argThat(d -> (d == ice) || (d == packed)));
        verify(viewer, never()).sendBlockChange(any(Location.class), eq(horizon));
    }

    /**
     * From behind a glass iris the ring keeps the real liquid, look-alike or not.
     *
     * <p>The stand-in is only for a horizon that ends up behind the iris. In the ring, where a
     * viewer behind the gate gets it, the real thing has air in front of it and is drawn as it
     * always was -- and a gate should show its actual wormhole wherever it can.
     */
    @Test
    void fromBehindAGlassIrisTheRingKeepsTheRealHorizon()
    {
        final BlockData ice = mock(BlockData.class);
        final BlockData packed = mock(BlockData.class);
        glassIris(ice, packed);
        standAt(Z + 4);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer).sendBlockChange(at(Z), eq(horizon));
        verify(viewer, never()).sendBlockChange(any(Location.class), eq(ice));
        verify(viewer, never()).sendBlockChange(any(Location.class), eq(packed));
    }

    /**
     * The blocks around the opening are cover as much as the opening is.
     *
     * <p>The same viewing position as {@link #fromOffToTheSideTheGateShowsOneLayerOnly}, with
     * the gate's own ring put where their sight line crosses. Counting only the opening as
     * cover took the second layer away from anybody who was not nearly square in front of a
     * big gate -- through a glass iris that read as the wormhole having gone -- when what they
     * were looking through the gate past was its ring all along.
     */
    @Test
    void theBlocksAroundTheOpeningAreCoverToo()
    {
        gate.getGateStructureBlocks().add(new Location(world, X + 1, Y, Z));
        standAt(X + 4, Z - 4);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer).sendBlockChange(at(Z), eq(iris));
        verify(viewer).sendBlockChange(at(Z + 1), eq(horizon));
    }

    /**
     * One cell seen past the gate takes the second layer off all of them.
     *
     * <p>Asked cell by cell, a gate seen from an angle came apart into a patchwork: some cells
     * with two layers and some fallen back to one, which from behind is a square of bare iris
     * sitting in the middle of the wormhole. A gate is one picture, so it is one decision.
     *
     * <p>The two cells here are deliberately unalike from where the viewer stands: the sight
     * line to the far layer of the nearer one still crosses the opening, and the line to the
     * other one's leaves the gate entirely.
     */
    @Test
    void oneCellSeenPastTheGateTakesTheLayerOffAllOfThem()
    {
        blockAt(X + 1, Z, null);
        blockAt(X + 1, Z + 1, truthBehind);
        blockAt(X + 1, Z - 1, truthAhead);
        gate.getGatePortalBlocks().add(new Location(world, X + 1, Y, Z));
        standAt(X + 4, Z - 4);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer, never()).sendBlockChange(any(Location.class), eq(horizon));
        verify(viewer).sendBlockChange(at(Z), eq(iris));
    }

    /**
     * From behind and off to the side, the wormhole keeps the ring and the iris is not drawn.
     *
     * <p>The same rule the other way about. From behind the far layer is the iris itself, so a
     * viewer round the back corner of a gate was shown its iris standing a block clear of the
     * ring with daylight around it. That layer goes; the wormhole in the ring stays, because
     * walking along the back of a gate should not keep taking it away and giving it back.
     */
    @Test
    void fromBehindAndOffToTheSideTheHorizonKeepsTheRing()
    {
        standAt(X + 4, Z + 4);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(viewer).sendBlockChange(at(Z), eq(horizon));
        verify(viewer, never()).sendBlockChange(any(Location.class), eq(iris));
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

    /**
     * Walking round the side redraws too, without ever crossing the plane.
     *
     * <p>The quiet half of the same step. While only the side a player was on decided this, a
     * walk along the front of a gate never redrew anything -- so the far layer stayed drawn
     * long after the opening had stopped hiding it, and went on hanging beside the gate until
     * the player happened to cross a chunk or the plane.
     */
    @Test
    void walkingRoundTheSideRedrawsWithoutCrossingThePlane()
    {
        StargateManager.registerStargate(gate);
        standAt(Z - 4);
        StargateBlockSetup.relayerFor(viewer, new Location(world, X + 0.5, Y, Z - 3.5));
        clearInvocations(viewer);

        StargateBlockSetup.relayerFor(viewer, new Location(world, X + 4.5, Y, Z - 3.5));

        verify(viewer).sendBlockChange(at(Z + 1), eq(truthBehind));
        verify(viewer, never()).sendBlockChange(any(Location.class), eq(horizon));
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
     * The stand-in horizon moves, because the thing it is drawn in does not.
     *
     * <p>Water animates itself. Ice does not, so a wormhole behind a see-through iris would sit
     * there as a frozen sheet. Two ices swapping places give it a surface. Asserted as the two
     * frames actually differing at a cell, not merely as something having been sent: a tick
     * that resent the same block would be an animation that does not move.
     */
    @Test
    void theStandInHorizonMovesBetweenFrames()
    {
        final BlockData ice = mock(BlockData.class);
        final BlockData packed = mock(BlockData.class);
        glassIris(ice, packed);
        standAt(Z - 4);
        StargateManager.registerStargate(gate);
        StargateBlockSetup.sendLayeredTo(viewer, gate);
        clearInvocations(viewer);

        StargateBlockSetup.tickHorizon();
        final BlockData first = sentBehind();
        clearInvocations(viewer);
        StargateBlockSetup.tickHorizon();
        final BlockData second = sentBehind();

        assertNotNull(first, "a frame is sent to somebody holding the stand-in");
        assertNotEquals(first, second, "and the next frame is the other ice, or nothing is moving");
    }

    /**
     * A gate whose iris shows the real wormhole is left alone by the frames.
     *
     * <p>Water moves on its own, so there is nothing to do for it, and the tick walks every
     * open gate on the server: it has to cost nothing for the gates that do not need it.
     */
    @Test
    void aGateShowingRealWaterIsNotTicked()
    {
        standAt(Z - 4);
        StargateManager.registerStargate(gate);
        StargateBlockSetup.sendLayeredTo(viewer, gate);
        clearInvocations(viewer);

        StargateBlockSetup.tickHorizon();

        verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /** Dresses the fixture's gate in a see-through iris with the two stand-ins stubbed. */
    private void glassIris(final BlockData ice, final BlockData packed)
    {
        gate.setGateCustomIrisMaterial(Material.YELLOW_STAINED_GLASS);
        materials.when(() -> MaterialUtils.drawnAs(Material.YELLOW_STAINED_GLASS)).thenReturn(iris);
        materials.when(() -> MaterialUtils.cullsWaterBehindIt(Material.YELLOW_STAINED_GLASS))
            .thenReturn(Boolean.TRUE);
        materials.when(() -> MaterialUtils.shownBehindGlassAs(Material.WATER, false))
            .thenReturn(Material.BLUE_ICE);
        materials.when(() -> MaterialUtils.shownBehindGlassAs(Material.WATER, true))
            .thenReturn(Material.PACKED_ICE);
        materials.when(() -> MaterialUtils.drawnAs(Material.BLUE_ICE)).thenReturn(ice);
        materials.when(() -> MaterialUtils.drawnAs(Material.PACKED_ICE)).thenReturn(packed);
    }

    /** What was last sent into the cell behind the ring, or null if nothing was. */
    private BlockData sentBehind()
    {
        final org.mockito.ArgumentCaptor<BlockData> sent =
            org.mockito.ArgumentCaptor.forClass(BlockData.class);
        verify(viewer, org.mockito.Mockito.atLeastOnce()).sendBlockChange(at(Z + 1), sent.capture());
        return sent.getValue();
    }

    /**
     * A draw says in the log what it decided, once the log is asking for it.
     *
     * <p>Where the layers went cannot be seen from a screenshot: a wormhole never drawn and one
     * drawn where the client will not show it look exactly alike, and several rounds of
     * guessing failed to tell them apart. The line has to carry the things that settle it --
     * which materials, how many cells got a horizon, and where the first one went.
     */
    @Test
    void aDrawSaysInTheLogWhereItPutTheLayers()
    {
        when(plugin.isLoggable(java.util.logging.Level.FINE)).thenReturn(Boolean.TRUE);
        when(viewer.getName()).thenReturn("Tester");
        standAt(Z - 4);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        final org.mockito.ArgumentCaptor<String> said = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(plugin).prettyLog(eq(java.util.logging.Level.FINE), said.capture());
        final String line = said.getValue();
        assertTrue(line.startsWith("Iris layers:"), line);
        assertTrue(line.contains("Gate=Layered"), line);
        assertTrue(line.contains("IrisMaterial=IRON_BLOCK"), line);
        assertTrue(line.contains("WithHorizon=1"), "the count that says the horizon was drawn: " + line);
        assertTrue(line.contains("FirstHorizon=At[x=10, y=64, z=21]"), "and where it went: " + line);
    }

    /**
     * Nothing is built for the log when the log is not asking.
     *
     * <p>This runs on every draw, and a draw runs on every step every nearby player takes.
     */
    @Test
    void aDrawBuildsNoLogLineWhenFineIsOff()
    {
        standAt(Z - 4);

        StargateBlockSetup.sendLayeredTo(viewer, gate);

        verify(plugin, never()).prettyLog(any(java.util.logging.Level.class), any(String.class));
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
