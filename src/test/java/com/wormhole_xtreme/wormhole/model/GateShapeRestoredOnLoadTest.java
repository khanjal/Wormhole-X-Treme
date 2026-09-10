package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.bukkit.Location;
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
 * A gate comes back from disk knowing which shape it was built from.
 *
 * <p>The shape name has always been written into every gate file, and nothing ever read it
 * back. {@link Stargate}'s constructor installs a placeholder shape that calls itself
 * "Standard", so that is what every loaded gate reported, whatever it really was -- and the
 * next save wrote that placeholder's name over the real one. One restart and one save turned
 * every gate on a server into a Standard gate as far as its own file was concerned, silently,
 * and there was nothing left afterwards to say what it had been.
 *
 * <p>That is also what stopped {@code /wormhole regenerate} from ever being able to re-read a
 * gate's shape: the shape it would have re-read was the placeholder.
 */
class GateShapeRestoredOnLoadTest
{
    @TempDir
    File tempDir;

    private Server server;
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));

        world = mock(World.class);
        when(world.getName()).thenReturn("gw");
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            final int x = call.getArgument(0);
            final int y = call.getArgument(1);
            final int z = call.getArgument(2);
            final Block b = mock(Block.class);
            when(b.getX()).thenReturn(x);
            when(b.getY()).thenReturn(y);
            when(b.getZ()).thenReturn(z);
            when(b.getLocation()).thenReturn(new Location(world, x, y, z));
            return b;
        });

        server = mock(Server.class);
        when(server.getWorld(anyString())).thenReturn(world);

        clearGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
        clearGates();
        StargateShapeRegistry.getStargateShapes().remove("Bespoke");
        StargateShapeRegistry.getStargateShapes().remove("Minimal");
    }

    private static void clearGates()
    {
        for (final Stargate s : new java.util.ArrayList<Stargate>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    private File gatesDir()
    {
        return new File(tempDir, "gates");
    }

    /** Registers a shape under a name, the way loading the shapes folder would. */
    private static StargateShape register(final String name)
    {
        final StargateShape shape = new StargateShape();
        shape.setShapeName(name);
        StargateShapeRegistry.getStargateShapes().put(name, shape);
        return shape;
    }

    private Stargate gate(final String name, final StargateShape shape)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
        s.setGateShape(shape);
        final Block dial = mock(Block.class);
        when(dial.getX()).thenReturn(10);
        when(dial.getY()).thenReturn(64);
        when(dial.getZ()).thenReturn(20);
        when(dial.getLocation()).thenReturn(new Location(world, 10, 64, 20));
        s.setGateDialLeverBlock(dial);
        s.setGatePlayerTeleportLocation(new Location(world, 65.0, 65.0, 65.0));
        s.setGateFacing(BlockFace.NORTH);
        return s;
    }

    private String yamlFor(final String gateName) throws Exception
    {
        final File file = new File(gatesDir(), gateName + ".yml");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * The shape named in the file is looked up and put back on the gate.
     *
     * <p>Not merely the name: the shape object itself, because everything that reads a gate's
     * woosh timings, its pinned materials, or asks whether it is a {@link Stargate3DShape} at
     * all goes through {@code getGateShape()} rather than the name.
     */
    @Test
    void aGatesShapeIsPutBackFromTheNameItsFileRecords()
    {
        final StargateShape minimal = register("Minimal");
        StargateYamlManager.saveStargate(gate("alpha", minimal), gatesDir());

        StargateYamlManager.loadStargates(server, gatesDir());

        final Stargate loaded = StargateManager.getStargate("alpha");
        assertNotNull(loaded, "the gate should be back in the registry");
        assertSame(minimal, loaded.getGateShape(),
            "a loaded gate should carry the shape its file names, not the placeholder the"
                + " constructor installs");
        assertEquals("Minimal", loaded.getGateShapeName());
    }

    /**
     * Saving a gate that came off disk does not rewrite its shape to Standard.
     *
     * <p>This is the destructive half of the bug and the reason it was worth fixing rather
     * than working around: the old code wrote {@code getGateShape().getShapeName()}, and for
     * a loaded gate that was the placeholder, so the real shape name was gone from the file
     * after one ordinary save -- a shutdown was enough.
     */
    @Test
    void savingAGateThatCameOffDiskKeepsItsShapeName() throws Exception
    {
        final StargateShape minimal = register("Minimal");
        StargateYamlManager.saveStargate(gate("beta", minimal), gatesDir());
        StargateYamlManager.loadStargates(server, gatesDir());

        final Stargate loaded = StargateManager.getStargate("beta");
        assertNotNull(loaded, "the gate should be back in the registry");
        StargateYamlManager.saveStargate(loaded, gatesDir());

        assertTrue(yamlFor("beta").contains("GateShape: Minimal"),
            "re-saving a loaded gate must not turn it into a Standard gate on disk; the file"
                + " said: " + yamlFor("beta"));
    }

    /**
     * A shape that is no longer in the folder keeps its name instead of becoming Standard.
     *
     * <p>An admin who renames or temporarily moves a custom shape file should get a warning,
     * not a folder full of gates permanently relabelled. The name is the only record of what
     * the gate was, and it is still true even when the shape it names cannot be resolved --
     * so it is kept and written back out unchanged.
     */
    @Test
    void aShapeMissingFromTheFolderKeepsItsNameRatherThanBecomingStandard() throws Exception
    {
        final StargateShape bespoke = register("Bespoke");
        StargateYamlManager.saveStargate(gate("gamma", bespoke), gatesDir());
        // The admin renames the shape file; the gate file still names it.
        StargateShapeRegistry.getStargateShapes().remove("Bespoke");

        StargateYamlManager.loadStargates(server, gatesDir());
        final Stargate loaded = StargateManager.getStargate("gamma");
        assertNotNull(loaded, "an unresolvable shape must not stop the gate loading");
        assertEquals("Bespoke", loaded.getGateShapeName(),
            "the recorded name is the only surviving record of what the gate was built from");

        StargateYamlManager.saveStargate(loaded, gatesDir());
        assertTrue(yamlFor("gamma").contains("GateShape: Bespoke"),
            "saving must not overwrite a shape name it merely could not resolve; the file"
                + " said: " + yamlFor("gamma"));
    }

    /**
     * A gate file too old to name a shape at all still loads.
     *
     * <p>{@code GateShape} has been written for a long time, but nothing guarantees every
     * file in a server's folder has it, and a missing key must leave the gate exactly as it
     * was rather than throwing on the way past.
     */
    @Test
    void aFileWithNoShapeNameStillLoads() throws Exception
    {
        final StargateShape minimal = register("Minimal");
        StargateYamlManager.saveStargate(gate("delta", minimal), gatesDir());
        final File file = new File(gatesDir(), "delta.yml");
        Files.write(file.toPath(), yamlFor("delta").replace("GateShape: Minimal", "")
            .getBytes(StandardCharsets.UTF_8));

        StargateYamlManager.loadStargates(server, gatesDir());

        final Stargate loaded = StargateManager.getStargate("delta");
        assertNotNull(loaded, "a file with no GateShape key should still load");
        assertEquals("Standard", loaded.getGateShapeName(),
            "with nothing recorded there is nothing to restore, and Standard is what the"
                + " placeholder has always reported");
    }
}
