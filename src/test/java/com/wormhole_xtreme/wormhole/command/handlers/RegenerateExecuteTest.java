package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Running {@code /wormhole regenerate}, as opposed to the {@code exitMoved} helper that
 * {@link RegenerateCommandTest} already covers.
 *
 * <p>The command has two quite different jobs behind one name. On one gate it redoes
 * everything -- levers, redstone, sign, arrival point. On {@code -all} it deliberately does
 * only the arrival point, because silently rewriting every gate's levers and signs on the
 * whole server is not what an admin asked for.
 *
 * <p>Neither was covered.
 */
class RegenerateExecuteTest
{
    private CommandSender sender;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));

        // Not a player, so the admin node is not asked for.
        sender = mock(CommandSender.class);
        clearGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        clearGates();
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, null);
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

    /** A registered gate that answers everything regenerate asks of it. */
    private static Stargate registeredGate(final String name)
    {
        final Stargate gate = mock(Stargate.class);
        when(gate.getGateName()).thenReturn(name);
        StargateManager.registerStargate(gate);
        return gate;
    }

    private boolean run(final String... args)
    {
        return new RegenerateCommand().execute(sender, args);
    }

    /** With no gate named there is nothing to do, and false prints the usage line. */
    @Test
    void namingNoGateIsAUsageError()
    {
        assertFalse(run("regenerate"));

        verify(sender).sendMessage(contains("No gate name specified"));
    }

    /** A gate nobody built is named back. */
    @Test
    void anUnknownGateIsNamedBack()
    {
        assertTrue(run("regenerate", "nowhere"));

        verify(sender).sendMessage(contains("nowhere"));
    }

    /** One named gate gets the full refresh: lever, sign, and its arrival point. */
    @Test
    void oneGateGetsTheFullRefresh()
    {
        final Stargate gate = registeredGate("alpha");
        when(gate.recomputeGatePlayerTeleportLocation()).thenReturn(true);

        assertTrue(run("regenerate", "alpha"));

        verify(gate).toggleDialLeverState(true);
        verify(gate).setupGateSign(true);
        verify(gate).matchDialSignMaterial();
        verify(sender).sendMessage(contains("Arrival point recomputed for alpha"));
        verify(sender).sendMessage(contains("Regenerating Gate: alpha"));
    }

    /** A gate whose exit cannot be worked out is not told it was. */
    @Test
    void aGateWhoseExitCannotBeComputedSaysSoByStayingQuiet()
    {
        final Stargate gate = registeredGate("alpha");
        when(gate.recomputeGatePlayerTeleportLocation()).thenReturn(false);

        assertTrue(run("regenerate", "alpha"));

        verify(sender, never()).sendMessage(contains("Arrival point recomputed"));
        verify(sender).sendMessage(contains("Regenerating Gate: alpha"));
    }

    /** The iris lever is only redone on a gate that has a code to protect. */
    @Test
    void theIrisLeverIsOnlyRedoneWhenThereIsACode()
    {
        final Stargate withCode = registeredGate("coded");
        when(withCode.getGateIrisDeactivationCode()).thenReturn("secret");
        final Stargate withoutCode = registeredGate("plain");
        when(withoutCode.getGateIrisDeactivationCode()).thenReturn("");

        assertTrue(run("regenerate", "coded"));
        assertTrue(run("regenerate", "plain"));

        verify(withCode).setupIrisLever(true);
        verify(withoutCode, never()).setupIrisLever(anyBoolean());
    }

    /**
     * {@code -all} recomputes arrival points and nothing else.
     *
     * <p>This is the difference between the two jobs. Rewriting every gate's levers, redstone
     * and sign unattended is not what was asked for, and the command says so in its own
     * comment -- so it is worth a test rather than a comment alone.
     */
    @Test
    void allTouchesOnlyTheArrivalPoints()
    {
        final Stargate gate = registeredGate("alpha");
        when(gate.recomputeGatePlayerTeleportLocation()).thenReturn(true);

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            assertTrue(run("regenerate", "-all"));
        }

        verify(gate).recomputeGatePlayerTeleportLocation();
        verify(gate, never()).toggleDialLeverState(anyBoolean());
        verify(gate, never()).setupGateSign(anyBoolean());
        verify(gate, never()).setupRedstone(anyBoolean());
    }

    /** Only a gate whose exit actually moved is written back to disk. */
    @Test
    void onlyAMovedExitIsSaved()
    {
        final World world = mock(World.class);
        final Stargate moved = registeredGate("moved");
        when(moved.recomputeGatePlayerTeleportLocation()).thenReturn(true);
        when(moved.getGatePlayerTeleportLocation())
            .thenReturn(new Location(world, 1, 64, 1), new Location(world, 9, 64, 9));

        final Stargate settled = registeredGate("settled");
        when(settled.recomputeGatePlayerTeleportLocation()).thenReturn(true);
        when(settled.getGatePlayerTeleportLocation())
            .thenReturn(new Location(world, 5, 64, 5));

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            assertTrue(run("regenerate", "-all"));

            db.verify(() -> StargateDBManager.saveStargate(moved));
            db.verify(() -> StargateDBManager.saveStargate(settled), never());
        }
    }

    /** A gate that cannot be computed is counted and reported separately. */
    @Test
    void gatesThatCannotBeComputedAreReported()
    {
        final Stargate broken = registeredGate("broken");
        when(broken.recomputeGatePlayerTeleportLocation()).thenReturn(false);

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            assertTrue(run("regenerate", "-all"));
        }

        verify(sender).sendMessage(contains("could not be checked"));
    }

    /** A player without the admin node may not regenerate anything. */
    @Test
    void aPlayerWithoutTheConfigNodeIsRefused()
    {
        final Stargate gate = registeredGate("alpha");
        final Player player = mock(Player.class);
        when(player.getName()).thenReturn("nobody");
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        assertTrue(new RegenerateCommand().execute(player, new String[] {"regenerate", "alpha"}));

        verify(player).sendMessage(contains("ermission"));
        verify(gate, never()).toggleDialLeverState(anyBoolean());
    }
}
