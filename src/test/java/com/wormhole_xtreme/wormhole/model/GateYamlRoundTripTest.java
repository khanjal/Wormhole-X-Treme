package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
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

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * What survives a gate being written to disk and read back.
 *
 * <p>Loading had no test. {@code loadStargates} was named in one comment and called by
 * nothing, so the owner fallbacks, the network wiring and the refusal to load a broken file
 * were all uncovered -- in the one place where getting it wrong loses somebody's gates on the
 * next restart rather than throwing.
 *
 * <p>These go through the real writer rather than hand-built YAML, so the two halves have to
 * agree with each other and not merely with this test.
 */
class GateYamlRoundTripTest
{
    @TempDir
    File tempDir;

    private Object previousPlugin;
    private Server server;
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        previousPlugin = f.get(null);
        f.set(null, mock(WormholeXTreme.class));

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

        for (final Stargate s : new java.util.ArrayList<Stargate>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    @AfterEach
    void restorePlugin() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, previousPlugin);
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

    private Stargate gate(final String name)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
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

    /** A gate written out comes back with its name, owner and network. */
    @Test
    void aSavedGateComesBackWhole()
    {
        final Stargate saved = gate("alpha");
        saved.setGateOwner("00000000-0000-0000-0000-000000000001");
        saved.setGateOwnerName("Ada");
        saved.setGateNetwork(StargateManager.addStargateNetwork("secret"));
        StargateYamlManager.saveStargate(saved, gatesDir());

        StargateYamlManager.loadStargates(server, gatesDir());

        final Stargate loaded = StargateManager.getStargate("alpha");
        assertNotNull(loaded, "the gate should be back in the registry");
        assertEquals("00000000-0000-0000-0000-000000000001", loaded.getGateOwner());
        assertEquals("Ada", loaded.getGateOwnerName());
        assertNotNull(loaded.getGateNetwork(), "the network is wired up on load, not just stored");
        assertEquals("secret", loaded.getGateNetwork().getNetworkName());
    }

    /**
     * A file naming its owner the old way, without a UUID, still loads.
     *
     * <p>Gates saved before ownership moved to UUIDs carry a plain {@code Owner} name. The
     * loader falls back to it, and uses it as the display name too, because there is no UUID
     * to resolve one from.
     */
    @Test
    void aLegacyNameOnlyOwnerIsStillHonoured() throws Exception
    {
        final Stargate saved = gate("beta");
        saved.setGateOwner("Ada");
        StargateYamlManager.saveStargate(saved, gatesDir());

        // Rewrite the file the way an old save wrote it: Owner, and no OwnerUUID.
        final File file = new File(gatesDir(), "beta.yml");
        final String yaml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
            .replace("OwnerUUID: Ada", "Owner: Ada");
        Files.write(file.toPath(), yaml.getBytes(StandardCharsets.UTF_8));

        StargateYamlManager.loadStargates(server, gatesDir());

        final Stargate loaded = StargateManager.getStargate("beta");
        assertNotNull(loaded);
        assertEquals("Ada", loaded.getGateOwner(), "the legacy Owner field is the fallback");
        assertEquals("Ada", loaded.getGateOwnerName(),
            "with no UUID to resolve, the owner string is the display name");
    }

    /**
     * One unreadable file does not stop the others loading.
     *
     * <p>The gates directory is read on startup. A single corrupted file taking the whole
     * load down would lose every gate on the server, so each is read on its own.
     */
    @Test
    void oneBrokenFileDoesNotCostTheRest() throws Exception
    {
        StargateYamlManager.saveStargate(gate("good"), gatesDir());
        Files.write(new File(gatesDir(), "broken.yml").toPath(),
            "GateData: not-valid-base64!!".getBytes(StandardCharsets.UTF_8));

        StargateYamlManager.loadStargates(server, gatesDir());

        assertNotNull(StargateManager.getStargate("good"), "the readable gate still loads");
    }

    /** A file with no gate data in it is skipped rather than half-loaded. */
    @Test
    void aFileWithNoGateDataIsSkipped() throws Exception
    {
        gatesDir().mkdirs();
        Files.write(new File(gatesDir(), "empty.yml").toPath(),
            "Name: ghost\nNetwork: nowhere\n".getBytes(StandardCharsets.UTF_8));

        StargateYamlManager.loadStargates(server, gatesDir());

        assertNull(StargateManager.getStargate("ghost"),
            "a file without GateData describes no gate");
    }

    /** A directory that is not there yet is not an error; there is simply nothing to load. */
    @Test
    void anAbsentDirectoryLoadsNothing()
    {
        StargateYamlManager.loadStargates(server, new File(tempDir, "never-created"));

        assertEquals(0, StargateManager.getAllGates().size());
    }
}
