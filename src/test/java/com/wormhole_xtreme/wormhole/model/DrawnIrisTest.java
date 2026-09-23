package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
 * A vertical gate's iris is drawn on clients; a horizontal one's is still real blocks.
 *
 * <p>The iris used to be real blocks in every gate, which meant a server that died between
 * placing them and saving left a wall standing in a gate the save says is open, and meant the
 * barrier could only ever be in the cells the portal is in. Drawing it fixes both: a drawing
 * cannot outlive the server, and it can be put wherever the viewer needs to see it.
 *
 * <p>A horizontal gate is the exception and has to stay the exception. Its iris is a floor, and
 * a drawn floor is air as far as the server is concerned -- the client stands on it, the server
 * sees somebody hovering over nothing, and a server that does not allow flight kicks them for
 * it. These pin which gate gets which, because getting it the wrong way round is not a visual
 * bug: it is players being dropped out of the world, or kicked.
 */
class DrawnIrisTest
{
    /** Held: a Location keeps its World weakly, so an inline mock can be collected mid-test. */
    private World world;
    private Stargate gate;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");

        gate = new Stargate();
        gate.setGateName("IrisGate");
        gate.setGateWorld(world);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    /**
     * Gives the gate two portal cells with a mocked block behind each.
     *
     * @return the blocks, in the order the cells were added
     */
    private Block[] twoPortalCells()
    {
        final Block lower = mock(Block.class);
        final Block upper = mock(Block.class);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        gate.getGatePortalBlocks().add(new Location(world, 10, 65, 20));
        when(world.getBlockAt(10, 64, 20)).thenReturn(lower);
        when(world.getBlockAt(10, 65, 20)).thenReturn(upper);
        return new Block[] {lower, upper};
    }

    // -----------------------------------------------------------------------
    // Which gates draw their iris
    // -----------------------------------------------------------------------

    /**
     * A vertical gate's iris leaves the server holding air, and is sent to the client instead.
     *
     * <p>This is the whole point of the change. If the blocks go back to being real, a crash
     * leaves them standing in the world and the iris can never move for the viewer.
     */
    @Test
    void aVerticalGatesIrisIsDrawnOverAirRatherThanBuilt()
    {
        gate.setGateFacing(BlockFace.NORTH);
        final Block[] cells = twoPortalCells();
        final Player watcher = mock(Player.class);
        when(watcher.getLocation()).thenReturn(new Location(world, 10, 64, 24));
        when(world.getPlayers()).thenReturn(List.of(watcher));

        final BlockData iris = mock(BlockData.class);
        try (MockedStatic<MaterialUtils> materials = mockStatic(MaterialUtils.class))
        {
            materials.when(() -> MaterialUtils.drawnAs(any(Material.class))).thenReturn(iris);

            StargateBlockSetup.fillGateIris(gate, Material.STONE);
        }

        verify(cells[0]).setType(Material.AIR);
        verify(cells[1]).setType(Material.AIR);
        verify(cells[0], never()).setType(Material.STONE);
        // Both cells, so a drawing that covered half the opening would not pass for one.
        verify(watcher).sendBlockChange(org.mockito.ArgumentMatchers.argThat(at -> at.getBlockY() == 64), eq(iris));
        verify(watcher).sendBlockChange(org.mockito.ArgumentMatchers.argThat(at -> at.getBlockY() == 65), eq(iris));
    }

    /**
     * A horizontal gate's iris is placed for real, because somebody stands on it.
     *
     * <p>Drawn, it would be air to the server: the client holds the player up on a block the
     * server does not have, and the floating check kicks them for flying a few seconds later.
     */
    @Test
    void aHorizontalGatesIrisStaysRealSoNobodyStandsOnNothing()
    {
        gate.setGateFacing(BlockFace.UP);
        final Block[] cells = twoPortalCells();

        StargateBlockSetup.fillGateIris(gate, Material.STONE);

        verify(cells[0]).setType(Material.STONE);
        verify(cells[1]).setType(Material.STONE);
        verify(cells[0], never()).setType(Material.AIR);
        // Nothing is drawn, so nobody is looked up to draw it for.
        verify(world, never()).getPlayers();
    }

    /**
     * A gate with no facing yet keeps the old, built iris.
     *
     * <p>A gate part-way through detection has no facing, and guessing wrong in that state
     * would mean guessing whether a barrier holds. Real blocks hold whichever way it turns out
     * to face, so that is what an unknown answer gets.
     */
    @Test
    void aGateWithNoFacingKeepsTheBuiltIris()
    {
        final Block[] cells = twoPortalCells();

        StargateBlockSetup.fillGateIris(gate, Material.STONE);

        assertFalse(gate.isGateIrisDrawn(), "no facing is not a licence to draw the barrier");
        verify(cells[0]).setType(Material.STONE);
    }

    // -----------------------------------------------------------------------
    // The set of gates whose iris has to be redrawn
    // -----------------------------------------------------------------------

    /**
     * Shutting an iris puts the gate in the set the redraw walks, and opening takes it out.
     *
     * <p>The redraw runs on every chunk boundary somebody crosses, so it walks a set rather
     * than filtering every gate on the server. That only works if the set follows the flag,
     * which is why the flag's own setter is what writes it.
     */
    @Test
    void shuttingAnIrisTracksTheGateInTheSetThatGetsRedrawn()
    {
        assertFalse(StargateManager.getIrisGates().contains(gate), "a new gate has nothing to draw");

        gate.setGateIrisActive(true);
        assertTrue(StargateManager.getIrisGates().contains(gate), "a shut iris has to reach clients");

        gate.setGateIrisActive(false);
        assertFalse(StargateManager.getIrisGates().contains(gate), "an open iris must not be drawn");
    }

    /**
     * A gate loaded with its iris already shut is drawn without anybody touching the lever.
     *
     * <p>Gates come back from disk with the flag already set, so nothing moves it and nothing
     * would put them in the set. Before this, an iris that was shut when the server stopped
     * was invisible to every client after it started.
     */
    @Test
    void aGateThatArrivesAlreadyShutIsDrawnAnyway()
    {
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGateIrisActive(true);
        StargateManager.setGateIrisState(gate, false); // as if it had never been registered

        StargateManager.registerStargate(gate);

        assertTrue(StargateManager.getIrisGates().contains(gate),
            "a gate that loads shut is owed its drawing like any other");
    }

    /**
     * A removed gate leaves the set.
     *
     * <p>The same leak the open set had: nothing on the removal path cleared it, so a deleted
     * gate stayed pinned in memory for the life of the server and the redraw went on drawing
     * an iris for a gate that no longer exists.
     */
    @Test
    void removingAGateTakesItsIrisOutOfTheSet()
    {
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGateIrisActive(true);
        StargateManager.registerStargate(gate);

        StargateManager.removeStargate(gate, null, false);

        assertFalse(StargateManager.getIrisGates().contains(gate),
            "a gate that is gone is not drawn for anybody");
    }

    // -----------------------------------------------------------------------
    // Whose swing is about an iris
    // -----------------------------------------------------------------------

    /**
     * Somebody standing at a shut, drawn iris is near enough for their swing to be at it.
     *
     * <p>Mining a drawn block gets the client the truth about that cell, which is air, and the
     * iris comes off their screen in a hole. The swing is the only thing there is to listen for,
     * and this is what decides whether a swing is worth a redraw.
     */
    @Test
    void aSwingBesideADrawnIrisIsWorthARedraw()
    {
        gate.setGateFacing(BlockFace.NORTH);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        gate.setGateIrisActive(true);
        StargateManager.registerStargate(gate);

        assertTrue(StargateManager.nearDrawnIris(new Location(world, 10, 64, 23)),
            "three blocks from the iris is well within reach of it");
    }

    /**
     * Somebody well away from it is not.
     *
     * <p>A swing is every punch and every attack anybody makes. Redrawing every gate within
     * the 64 blocks a portal is drawn at, on each of them, would be most of a server's packets.
     */
    @Test
    void aSwingAcrossTheValleyIsNotAboutTheIris()
    {
        gate.setGateFacing(BlockFace.NORTH);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        gate.setGateIrisActive(true);
        StargateManager.registerStargate(gate);

        assertFalse(StargateManager.nearDrawnIris(new Location(world, 10, 64, 60)),
            "forty blocks away is inside the drawing range but nowhere near reach");
    }

    /**
     * A horizontal gate's iris is real, so nothing about it needs putting back.
     */
    @Test
    void aSwingAtARealIrisNeedsNoRedraw()
    {
        gate.setGateFacing(BlockFace.UP);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        gate.setGateIrisActive(true);
        StargateManager.registerStargate(gate);

        assertFalse(StargateManager.nearDrawnIris(new Location(world, 10, 65, 20)),
            "standing right on it, but the server has those blocks");
    }

    // -----------------------------------------------------------------------
    // Redrawing it for somebody who was not there when it shut
    // -----------------------------------------------------------------------

    /**
     * An idle gate sitting with its iris shut is still drawn for somebody walking up to it.
     *
     * <p>The redraw used to walk only the open gates, which is right for the portal and wrong
     * for the iris: a gate can sit shut and unlit for days, and everybody who was not standing
     * there when it closed would walk up to an open ring.
     */
    @Test
    void anIdleGateWithAShutIrisIsDrawnForSomebodyWhoWalksUp()
    {
        gate.setGateFacing(BlockFace.NORTH);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        gate.setGateIrisActive(true);
        StargateManager.registerStargate(gate);
        assertFalse(gate.isGateActive(), "the gate is idle: nothing has dialled it");

        final Player walker = mock(Player.class);
        when(walker.isOnline()).thenReturn(true);
        when(walker.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(walker.getLocation()).thenReturn(new Location(world, 10, 64, 26));

        final BlockData iris = mock(BlockData.class);
        try (MockedStatic<MaterialUtils> materials = mockStatic(MaterialUtils.class))
        {
            materials.when(() -> MaterialUtils.drawnAs(any(Material.class))).thenReturn(iris);

            StargateBlockSetup.refreshPortalVisuals(walker);
        }

        verify(walker).sendBlockChange(any(Location.class), eq(iris));
    }

    /**
     * A gate the server never registered is not drawn, however its flag reads.
     *
     * <p>Loading sets the flag partway through reading a gate file, and that alone files the
     * gate in the iris set. One whose file then fails to load was drawn as a shut iris over its
     * cells for everybody nearby until restart.
     */
    @Test
    void aGateThatNeverRegisteredIsNotDrawn()
    {
        gate.setGateFacing(BlockFace.NORTH);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        gate.setGateIrisActive(true);
        assertTrue(StargateManager.getIrisGates().contains(gate), "the flag alone files it");

        final Player walker = mock(Player.class);
        when(walker.isOnline()).thenReturn(true);
        when(walker.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(walker.getLocation()).thenReturn(new Location(world, 10, 64, 26));

        final BlockData iris = mock(BlockData.class);
        try (MockedStatic<MaterialUtils> materials = mockStatic(MaterialUtils.class))
        {
            materials.when(() -> MaterialUtils.drawnAs(any(Material.class))).thenReturn(iris);

            StargateBlockSetup.refreshPortalVisuals(walker);
        }
        finally
        {
            gate.setGateIrisActive(false);
        }

        verify(walker, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }
}
