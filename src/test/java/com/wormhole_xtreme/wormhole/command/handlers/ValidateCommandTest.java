package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Running {@code /wormhole gate validate}, item 3 of #54: asking on demand whether a gate is
 * still standing where it says it is, rather than waiting for a click or a dial to find out.
 *
 * <p>This is the command layer's own logic -- argument handling, permissions, which gates get
 * reported and how -- not {@link com.wormhole_xtreme.wormhole.model.GateIntegrityTest}'s
 * question of what counts as broken. Nothing here re-covers that; it stubs
 * {@code Stargate}'s own accessors to control what {@code GateIntegrity} sees.
 */
class ValidateCommandTest
{
    private CommandSender sender;
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));

        // Not a player, so the admin node is not asked for.
        sender = mock(CommandSender.class);
        world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(Boolean.TRUE);
        clearGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        clearGates();
        PluginTestSupport.remove();
    }

    private static void clearGates()
    {
        for (final Stargate s : new ArrayList<>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    /** A registered gate with nothing missing: an empty frame list and no dial sign at all. */
    private static Stargate registeredGate(final String name)
    {
        final Stargate gate = mock(Stargate.class);
        when(gate.getGateName()).thenReturn(name);
        when(gate.getGateStructureBlocks()).thenReturn(new ArrayList<>());
        StargateManager.registerStargate(gate);
        return gate;
    }

    /** A frame block location backed by a real block in the mock world. */
    private Location blockAt(final int x, final int y, final int z, final Material material)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getZ()).thenReturn(z);
        final Location location = new Location(world, x, y, z);
        when(world.getBlockAt(x, y, z)).thenReturn(block);
        when(world.getBlockAt(location)).thenReturn(block);
        return location;
    }

    /** A dial sign block, present or already replaced by something else. */
    private Block dialSignBlock(final boolean stillASign)
    {
        final Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(0);
        when(block.getZ()).thenReturn(0);
        final BlockState state = stillASign ? mock(Sign.class) : mock(BlockState.class);
        when(block.getState()).thenReturn(state);
        return block;
    }

    private boolean run(final String... args)
    {
        return new ValidateCommand().execute(sender, args);
    }

    /** With no gate named, and no {@code -all}, there is nothing to check. */
    @Test
    void namingNoGateIsAUsageError()
    {
        assertFalse(run("validate"));

        verify(sender).sendMessage(contains("No gate name specified"));
    }

    /** A gate nobody built is named back, the same way {@code regenerate} handles it. */
    @Test
    void anUnknownGateIsNamedBack()
    {
        assertTrue(run("validate", "nowhere"));

        verify(sender).sendMessage(contains("nowhere"));
    }

    /** A gate with nothing missing says so by name, since this was asked about it directly. */
    @Test
    void aGateWithNothingMissingIsReportedClean()
    {
        registeredGate("alpha");

        assertTrue(run("validate", "alpha"));

        verify(sender).sendMessage(contains("alpha is standing where it says it is"));
    }

    /** A frame turned to air is exactly what GateIntegrity was built to catch. See #54. */
    @Test
    void aGateMissingFrameBlocksNamesHowMany()
    {
        final Stargate gate = registeredGate("alpha");
        // Built first: these stub mocks of their own, and doing that inside an open
        // when(...) leaves the outer stubbing unfinished.
        final List<Location> frame = Arrays.asList(
            blockAt(0, 64, 0, Material.AIR),
            blockAt(1, 64, 0, Material.OBSIDIAN),
            blockAt(2, 64, 0, Material.AIR));
        when(gate.getGateStructureBlocks()).thenReturn(frame);

        assertTrue(run("validate", "alpha"));

        verify(sender).sendMessage(contains("alpha: 2 frame blocks missing"));
    }

    /** The DHD sign case the 2011 report and #54 both describe: gone, and now said out loud. */
    @Test
    void aGateMissingItsDialSignSaysSo()
    {
        final Stargate gate = registeredGate("alpha");
        final Block sign = dialSignBlock(false);
        when(gate.getGateDialSignBlock()).thenReturn(sign);

        assertTrue(run("validate", "alpha"));

        verify(sender).sendMessage(contains("alpha: dial sign missing"));
    }

    /** A dial sign still in place is not reported as missing. */
    @Test
    void aGateWithItsDialSignStillThereIsClean()
    {
        final Stargate gate = registeredGate("alpha");
        final Block sign = dialSignBlock(true);
        when(gate.getGateDialSignBlock()).thenReturn(sign);

        assertTrue(run("validate", "alpha"));

        verify(sender).sendMessage(contains("is standing where it says it is"));
    }

    /** Both problems at once are joined into one line, not reported twice. */
    @Test
    void bothProblemsAtOnceAreReportedTogether()
    {
        final Stargate gate = registeredGate("alpha");
        final List<Location> frame = Arrays.asList(blockAt(0, 64, 0, Material.AIR));
        final Block sign = dialSignBlock(false);
        when(gate.getGateStructureBlocks()).thenReturn(frame);
        when(gate.getGateDialSignBlock()).thenReturn(sign);

        assertTrue(run("validate", "alpha"));

        verify(sender).sendMessage(contains(
            "alpha: 1 frame block missing; dial sign missing"));
    }

    /**
     * {@code -all} names the broken gates and leaves the clean ones out of the per-gate list,
     * the same restraint {@code regenerate -all} uses -- a server with hundreds of gates and
     * one broken one should not scroll past hundreds of "fine" lines to find it.
     */
    @Test
    void allNamesOnlyTheBrokenGates()
    {
        registeredGate("clean");
        final Stargate broken = registeredGate("broken");
        final List<Location> frame = Arrays.asList(blockAt(5, 64, 5, Material.AIR));
        when(broken.getGateStructureBlocks()).thenReturn(frame);

        assertTrue(run("validate", "-all"));

        verify(sender).sendMessage(contains("Checked 2 gates. 1 has something missing."));
        verify(sender).sendMessage(contains("broken: 1 frame block missing"));
        verify(sender, never()).sendMessage(contains("clean is standing"));
    }

    /** Nothing broken is still worth a summary line, just with nothing under it. */
    @Test
    void allWithNothingBrokenStillReportsTheCount()
    {
        registeredGate("alpha");

        assertTrue(run("validate", "-all"));

        verify(sender).sendMessage(contains("Checked 1 gate. 0 have something missing."));
    }

    /** A player without the admin node may not validate anything. */
    @Test
    void aPlayerWithoutTheConfigNodeIsRefused()
    {
        registeredGate("alpha");
        final Player player = mock(Player.class);
        when(player.getName()).thenReturn("nobody");
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        assertTrue(new ValidateCommand().execute(player, new String[] {"validate", "alpha"}));

        verify(player).sendMessage(contains("ermission"));
    }
}
