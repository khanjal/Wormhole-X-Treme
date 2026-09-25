package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A gate saved in the middle of a journey comes back as the gate it is between journeys.
 *
 * <p>A journey opens a shut iris without touching the default, and shutting down puts it back.
 * The file used to record the live iris, and a restart made that the default, so the only thing
 * needed to lose a gate's iris for good was a save during somebody's trip: {@code redstone},
 * {@code wooshdepth}, {@code owner}, a material or {@code gate edit} all save on the spot. A
 * clean shutdown closes every gate first, so that save only mattered once the server crashed --
 * and a crash also keeps the world as it was, which on a floor gate means no iris blocks.
 */
class MidDialSaveTest
{
    @TempDir
    File tempDir;

    private Server server;
    /** Held: a Location keeps its World weakly, so an inline mock can be collected mid-test. */
    private World world;
    private Block lower;
    private Block upper;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));

        world = mock(World.class);
        when(world.getName()).thenReturn("gw");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        lower = blockAt(10, 64, 20);
        upper = blockAt(11, 64, 20);
        // One answer for every cell: stubbing the two portal cells separately would call this
        // answer, and its own stubbing, in the middle of stubbing them.
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            final int x = call.getArgument(0);
            final int y = call.getArgument(1);
            final int z = call.getArgument(2);
            if ((y == 64) && (z == 20) && ((x == 10) || (x == 11)))
            {
                return (x == 10) ? lower : upper;
            }
            return blockAt(x, y, z);
        });

        server = mock(Server.class);
        when(server.getWorld(anyString())).thenReturn(world);
        forgetGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        forgetGates();
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    private static void forgetGates()
    {
        for (final Stargate s : new java.util.ArrayList<Stargate>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    private Block blockAt(final int x, final int y, final int z)
    {
        final Block b = mock(Block.class);
        when(b.getX()).thenReturn(x);
        when(b.getY()).thenReturn(y);
        when(b.getZ()).thenReturn(z);
        // A real block always has a type; the dial lever's is read when the gate settles.
        when(b.getType()).thenReturn(Material.AIR);
        when(b.getWorld()).thenReturn(world);
        when(b.getLocation()).thenReturn(new Location(world, x, y, z));
        return b;
    }

    /**
     * A floor gate whose iris is shut by default, open for a journey to {@code far}.
     *
     * <p>A floor gate because its iris is real blocks, so a crash mid-journey leaves the
     * opening empty and loading has to put the iris back, not just the flag.
     */
    private Stargate floorGateMidJourney(final String name)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
        s.setGateWorld(world);
        s.setGateFacing(BlockFace.UP);
        s.setGateDialLeverBlock(blockAt(9, 64, 20));
        s.setGatePlayerTeleportLocation(new Location(world, 10.5, 65.0, 20.5));
        s.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        s.getGatePortalBlocks().add(new Location(world, 11, 64, 20));
        s.setGateIrisDeactivationCode("1234");
        s.setGateCustom(true);
        s.setGateCustomIrisMaterial(Material.IRON_BLOCK);

        final Stargate far = new Stargate();
        far.setGateName("far");
        s.setGateIrisDefaultActive(true);
        s.setGateIrisActive(false);
        s.setGateActive(true);
        s.setGateTarget(far);
        s.setGateLightsActive(true);
        return s;
    }

    private Stargate saveAndRestart(final Stargate s)
    {
        StargateYamlManager.saveStargate(s, new File(tempDir, "gates"));
        forgetGates();
        StargateYamlManager.loadStargates(server, new File(tempDir, "gates"));
        final Stargate back = StargateManager.getStargate(s.getGateName());
        assertNotNull(back, "the gate loaded");
        return back;
    }

    /** The iris a journey opened is not what the gate comes back with, or what it returns to. */
    @Test
    void anIrisOpenedForAJourneyComesBackShutAndStillShutByDefault()
    {
        final Stargate back = saveAndRestart(floorGateMidJourney("alpha"));

        assertTrue(back.isGateIrisDefaultActive(),
            "a save during a journey made the journey's open iris the gate's default");
        assertTrue(back.isGateIrisActive(), "the gate loads with its iris at its default");
    }

    /**
     * The iris blocks come back with the flag.
     *
     * <p>The crash kept the world with the floor gate's iris taken out for the journey; a flag
     * that says shut over an empty opening lets anyone fall through a gate that reads as locked.
     */
    @Test
    void aFloorGateSavedMidJourneyHasItsIrisBlocksPutBack()
    {
        saveAndRestart(floorGateMidJourney("alpha"));

        verify(lower).setType(Material.IRON_BLOCK);
        verify(upper).setType(Material.IRON_BLOCK);
    }

    /**
     * Nothing about the dial survives: no wormhole, no lights.
     *
     * <p>The lights are drawn on clients, so none are left after a restart; a gate still
     * flagged lit or open would refuse {@code regen} and a refresh until somebody pulled its
     * lever, over a gate that shows nothing to switch off.
     */
    @Test
    void aGateSavedMidJourneyComesBackIdle()
    {
        final Stargate back = saveAndRestart(floorGateMidJourney("alpha"));

        assertFalse(back.isGateActive(), "the wormhole it was saved with has no far end any more");
        assertFalse(back.isGateLightsActive(), "and no lights on a gate that shows none");
    }

    /**
     * A settled gate is saved settled, so the next restart finds nothing to settle.
     *
     * <p>Left as it was, the file said mid-dial on every start, and every start loaded the
     * gate's chunks and rewrote its blocks again.
     */
    @Test
    void aSettledGateIsSavedIdle()
    {
        saveAndRestart(floorGateMidJourney("alpha"));
        forgetGates();
        StargateYamlManager.loadStargates(server, new File(tempDir, "gates"));

        assertNotNull(StargateManager.getStargate("alpha"), "the gate loaded a second time");
        verify(lower, times(1)).setType(Material.IRON_BLOCK);
    }

    /**
     * A gate an older version saved lit, and nothing more, comes back unlit.
     *
     * <p>The writer saves every gate unlit now, so only an older file loads one lit; this is
     * the gate such a file gives the loader.
     */
    @Test
    void aGateAnOlderFileSavedLitComesBackUnlit()
    {
        final Stargate lit = floorGateMidJourney("gamma");
        lit.setGateTarget(null);
        lit.setGateActive(false);
        lit.setGateIrisActive(true);

        assertTrue(StargateYamlManager.settleIfSavedMidDial(lit), "a lit gate needs settling");
        assertFalse(lit.isGateLightsActive(), "flagged lit with nothing drawn, it refuses regen");
        verify(lower).setType(Material.IRON_BLOCK);
    }

    /**
     * An idle gate is left exactly as it loaded.
     *
     * <p>Putting a gate back to idle loads its chunks and writes its blocks; every gate on a
     * server passes through the loader, and almost none of them were saved mid-dial.
     */
    @Test
    void anIdleGateIsNotTouchedOnLoad()
    {
        final Stargate idle = floorGateMidJourney("beta");
        idle.setGateTarget(null);
        idle.setGateActive(false);
        idle.setGateLightsActive(false);

        final Stargate back = saveAndRestart(idle);

        assertTrue(back.isGateIrisActive(), "it still loads with its iris at its default");
        verify(lower, never()).setType(Material.IRON_BLOCK);
        verify(lower, never()).setType(Material.AIR);
    }
}
