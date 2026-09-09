package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

/**
 * A gate that cannot be written does not take the rest of the save down with it.
 *
 * <p>Three sites built a file name straight off {@code getGateName()}, and a fourth handed
 * {@code stargateToBinary}'s result to Base64 without checking it. SonarCloud raises the first
 * as a guaranteed NPE, and it is right: {@code StargateManager.normalizeGateName} returns null
 * for a null name rather than throwing, so the rest of the model tolerates a nameless gate.
 *
 * <p>Where it matters is the shutdown save. Every gate is written on every clean stop, in a
 * loop. One gate throwing there stops every gate after it in the loop from being written at
 * all, and the owner finds out at the next start.
 */
class NamelessGateYamlTest
{
    @TempDir
    File gatesDir;

    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));

        world = mock(World.class);
        when(world.getName()).thenReturn("gw");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            final int x = inv.getArgument(0, Integer.class).intValue();
            final int y = inv.getArgument(1, Integer.class).intValue();
            final int z = inv.getArgument(2, Integer.class).intValue();
            final Block b = mock(Block.class);
            when(b.getX()).thenReturn(Integer.valueOf(x));
            when(b.getY()).thenReturn(Integer.valueOf(y));
            when(b.getZ()).thenReturn(Integer.valueOf(z));
            when(b.getLocation()).thenReturn(new Location(world, x, y, z));
            return b;
        });
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    /** A gate carrying enough to serialise, so a failed save is about the name and nothing else. */
    private Stargate writableGate(final String name)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
        s.setGateWorld(world);
        s.setGateFacing(BlockFace.NORTH);
        s.setGateDialLeverBlock(world.getBlockAt(10, 64, 20));
        s.setGatePlayerTeleportLocation(new Location(world, 65.0, 70.0, 66.0));
        return s;
    }

    /** A gate whose name is null is skipped rather than thrown over. */
    @Test
    void aGateWithANullNameIsSkipped()
    {
        assertDoesNotThrow(() -> StargateYamlManager.saveStargate(writableGate(null), gatesDir));

        assertEquals(0, gatesDir.listFiles().length, "and no file is written for it");
    }

    /**
     * So is one whose name is empty.
     *
     * <p>Empty is the default for a gate that has been constructed but not named. Sanitised it
     * becomes ".yml" -- a hidden file the loader would read straight back in as a gate.
     */
    @Test
    void aGateWithAnEmptyNameIsSkipped()
    {
        StargateYamlManager.saveStargate(writableGate(""), gatesDir);

        assertEquals(0, gatesDir.listFiles().length, "no file, hidden or otherwise");
        assertFalse(new File(gatesDir, ".yml").exists(), "and specifically not a bare .yml");
    }

    /** The gates after it in the same loop are still written. */
    @Test
    void anUnwritableGateDoesNotStopTheGatesAfterIt()
    {
        for (final Stargate s : new Stargate[] { writableGate(null), writableGate("after") })
        {
            StargateYamlManager.saveStargate(s, gatesDir);
        }

        assertEquals(1, gatesDir.listFiles().length, "one file, for the gate that had a name");
        assertTrue(new File(gatesDir, "after.yml").isFile(), "and it is that gate's");
    }

    /**
     * A gate that will not serialise is skipped too, rather than written without its blocks.
     *
     * <p>{@code stargateToBinary} returns null when the encoding fails, having logged why. A
     * file with no GateData loads back as a gate with no blocks at all, which is worse than
     * no file: the gate looks present and does nothing.
     */
    @Test
    void aGateThatWillNotSerialiseIsNotWrittenAtAll()
    {
        final Stargate unserialisable = new Stargate();
        unserialisable.setGateName("broken");
        unserialisable.setGateWorld(world);
        // No facing, which is what stargateToBinary fails on.

        assertDoesNotThrow(() -> StargateYamlManager.saveStargate(unserialisable, gatesDir));

        assertEquals(0, gatesDir.listFiles().length, "nothing half-written");
    }

    /** Removing a nameless gate has nothing to delete, and does nothing. */
    @Test
    void removingANamelessGateDoesNothing()
    {
        assertDoesNotThrow(() -> StargateYamlManager.removeStargate(writableGate(null)));
    }

    /** And nobody owns a gate that has no name. */
    @Test
    void theOwnerOfANamelessGateIsNobody()
    {
        assertNull(assertDoesNotThrow(() -> StargateYamlManager.readOwnerFromYaml(null)));
    }

    /** A real name full of path characters still lands on one flat file in the gates directory. */
    @Test
    void anAwkwardNameIsFlattenedIntoOneFileName()
    {
        StargateYamlManager.saveStargate(writableGate("../../etc/pass wd"), gatesDir);

        assertTrue(new File(gatesDir, ".._.._etc_pass_wd.yml").isFile(),
            "every character that could leave the directory is replaced: "
                + java.util.Arrays.toString(gatesDir.list()));
    }
}
