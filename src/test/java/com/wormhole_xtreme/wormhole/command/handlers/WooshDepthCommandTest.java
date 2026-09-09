package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

/**
 * Setting and reading a gate's woosh depth.
 *
 * <p>Depth only applies to a gate in custom mode, only takes 0 to 5, and on most gates does
 * not change the animation at all -- the shape's own waves win, and depth is left governing
 * how far the block and entity protection reaches. The command says so, which is the only
 * reason somebody setting it does not conclude the feature is broken.
 *
 * <p>None of that was covered.
 */
class WooshDepthCommandTest
{
    private CommandSender sender;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));

        // Not a player, so the admin node is not asked for.
        sender = mock(CommandSender.class);
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

    /** A registered gate in custom mode, which is the only kind this command will change. */
    private static Stargate customGate(final String name)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        gate.setGateCustom(true);
        StargateManager.registerStargate(gate);
        return gate;
    }

    private boolean run(final String... args)
    {
        return new WooshDepthCommand().execute(sender, args);
    }

    /** Too few or too many words gets the usage line, and false to print it again. */
    @Test
    void theWrongNumberOfArgumentsIsAUsageError()
    {
        assertFalse(run("wooshdepth"));
        assertFalse(run("wooshdepth", "alpha", "3", "extra"));

        verify(sender, org.mockito.Mockito.atLeastOnce())
            .sendMessage(contains("/wormhole wooshdepth"));
    }

    /** A gate nobody built is refused by name. */
    @Test
    void anUnknownGateIsRefused()
    {
        assertTrue(run("wooshdepth", "nowhere", "3"));

        verify(sender).sendMessage(contains("Invalid gate target"));
    }

    /** A gate not in custom mode is told which command to run first. */
    @Test
    void aGateNotInCustomModeIsRefused()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("plain");
        StargateManager.registerStargate(gate);

        assertTrue(run("wooshdepth", "plain", "3"));

        verify(sender).sendMessage(contains("not in custom mode"));
        assertEquals(-1, gate.getGateCustomWooshDepth(), "still unset; -1 is the sentinel, 0 is a real depth");
    }

    /** A depth inside the range is stored, along with its square. */
    @Test
    void aDepthInRangeIsStoredWithItsSquare()
    {
        final Stargate gate = customGate("alpha");

        assertTrue(run("wooshdepth", "alpha", "4"));

        assertEquals(4, gate.getGateCustomWooshDepth());
        assertEquals(16, gate.getGateCustomWooshDepthSquared(),
            "the square is kept alongside so the animation does not compute it per block");
        verify(sender).sendMessage(contains("woosh depth set to: 4"));
    }

    /**
     * Zero is a real depth, not a missing one.
     *
     * <p>Unset is -1, so a gate set to 0 has been deliberately given no woosh rather than
     * never having been asked.
     */
    @Test
    void zeroIsAcceptedAsADepth()
    {
        final Stargate gate = customGate("alpha");
        gate.setGateCustomWooshDepth(3);

        assertTrue(run("wooshdepth", "alpha", "0"));

        assertEquals(0, gate.getGateCustomWooshDepth());
    }

    /** Past the top of the range is refused and changes nothing. */
    @Test
    void aDepthOutOfRangeIsRefused()
    {
        final Stargate gate = customGate("alpha");
        gate.setGateCustomWooshDepth(2);

        assertTrue(run("wooshdepth", "alpha", "6"));

        verify(sender).sendMessage(contains("Invalid woosh depth: 6"));
        assertEquals(2, gate.getGateCustomWooshDepth(), "the old depth stands");
    }

    /** A word that is not a number is refused the same way as one out of range. */
    @Test
    void somethingThatIsNotANumberIsRefused()
    {
        final Stargate gate = customGate("alpha");
        gate.setGateCustomWooshDepth(2);

        assertTrue(run("wooshdepth", "alpha", "deep"));

        verify(sender).sendMessage(contains("Invalid woosh depth: deep"));
        assertEquals(2, gate.getGateCustomWooshDepth());
    }

    /** Asked with no depth, it reports the current one and changes nothing. */
    @Test
    void askingReportsTheCurrentDepth()
    {
        final Stargate gate = customGate("alpha");
        gate.setGateCustomWooshDepth(3);

        assertTrue(run("wooshdepth", "alpha"));

        verify(sender).sendMessage(contains("woosh depth is currently: 3"));
        assertEquals(3, gate.getGateCustomWooshDepth());
    }

    /**
     * A gate whose shape authors its own waves is told the setting will not move them.
     *
     * <p>Every shipped shape does, so this is the ordinary case -- and without the note the
     * player sets a number, sees nothing change, and concludes it is broken.
     */
    @Test
    void aShapeThatOwnsItsWavesGetsANote()
    {
        final Stargate gate = customGate("alpha");
        final World world = mock(World.class);
        gate.getGateWooshBlocks().add(
            new ArrayList<>(Collections.singletonList(new Location(world, 0, 64, 0))));

        assertTrue(run("wooshdepth", "alpha", "4"));

        verify(sender).sendMessage(contains("shape defines its own woosh waves"));
    }

    /** A gate whose shape authors no waves gets no note, because depth really does drive it. */
    @Test
    void aShapeWithNoWavesOfItsOwnGetsNoNote()
    {
        customGate("alpha");

        assertTrue(run("wooshdepth", "alpha", "4"));

        verify(sender, never()).sendMessage(contains("shape defines its own woosh waves"));
    }

    /** A player without the admin node may not touch it. */
    @Test
    void aPlayerWithoutTheConfigNodeIsRefused()
    {
        final Stargate gate = customGate("alpha");
        final Player player = mock(Player.class);
        when(player.getName()).thenReturn("nobody");
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        assertTrue(new WooshDepthCommand().execute(player, new String[] {"wooshdepth", "alpha", "5"}));

        verify(player).sendMessage(contains("ermission"));
        assertEquals(-1, gate.getGateCustomWooshDepth(), "still unset; -1 is the sentinel, 0 is a real depth");
    }
}
