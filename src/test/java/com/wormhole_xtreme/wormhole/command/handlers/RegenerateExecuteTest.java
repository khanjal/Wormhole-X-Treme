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

import java.util.ArrayList;
import java.util.List;
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
import com.wormhole_xtreme.wormhole.logic.GateRederivation;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

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

    /**
     * Every way re-deriving a gate's shape can decline names something the admin can act on.
     *
     * <p>The reporting is what is being pinned here, not the derivation --
     * {@code GateRederivationTest} runs that against real shapes in a real world of blocks.
     * What matters at this end is that an admin who ran the command is never left guessing:
     * a silent decline looks exactly like a command that worked, which is the shape of the
     * original #42 bug and not a thing to reintroduce one level up.
     */
    @Test
    void aGateWhoseShapeCannotBeReReadSaysWhichWayItFailed()
    {
        assertDeclineMentions(GateRederivation.Result.NO_ANCHOR, "records no dial button");
        assertDeclineMentions(GateRederivation.Result.NOT_DETECTED, "no longer matches shape");
        // NO_SHAPE covers two causes -- a shape that is not in the folder, and one that is
        // there but 2D -- so the message must not assert the first. A 2D shape file is any
        // without Version=2, which this release still loads, so an admin with one would
        // otherwise be sent looking for a file sitting in front of them.
        assertDeclineMentions(GateRederivation.Result.NO_SHAPE, "is not a 3D shape");
        assertDeclineDoesNotSay(GateRederivation.Result.NO_SHAPE, "is not in the shapes folder");
    }

    /**
     * Runs regenerate against a stubbed decline and checks it does <em>not</em> say something.
     *
     * @param result
     *            how re-derivation declined
     * @param forbidden
     *            wording the message must not carry
     */
    private void assertDeclineDoesNotSay(final GateRederivation.Result result, final String forbidden)
    {
        final Stargate gate = registeredGate("alpha");
        when(gate.getGateShapeName()).thenReturn("Bespoke");

        try (MockedStatic<GateRederivation> rederive = mockStatic(GateRederivation.class))
        {
            rederive.when(() -> GateRederivation.rederive(gate))
                .thenReturn(new GateRederivation.Outcome(result, List.of()));

            assertTrue(run("regenerate", "alpha"));
        }

        verify(sender, never()).sendMessage(contains(forbidden));
    }

    /**
     * Runs regenerate against a stubbed decline and checks the admin is told about it.
     *
     * @param result
     *            how re-derivation declined
     * @param expected
     *            wording the message has to carry
     */
    private void assertDeclineMentions(final GateRederivation.Result result, final String expected)
    {
        final Stargate gate = registeredGate("alpha");
        when(gate.getGateShapeName()).thenReturn("Bespoke");

        try (MockedStatic<GateRederivation> rederive = mockStatic(GateRederivation.class))
        {
            rederive.when(() -> GateRederivation.rederive(gate))
                .thenReturn(new GateRederivation.Outcome(result, List.of()));

            assertTrue(run("regenerate", "alpha"));
        }

        verify(sender).sendMessage(contains(expected));
    }

    /**
     * A gate whose markers moved is saved, and the admin is told what moved.
     *
     * <p>The save is the half that would be missed. Marker positions live in the gate file,
     * so a re-derivation nobody wrote down is undone by the next restart -- and the admin
     * would have watched the command work and then watched it stop working, which is worse
     * than it never having worked at all.
     */
    @Test
    void markersThatMovedAreSavedAndNamed()
    {
        final Stargate gate = registeredGate("alpha");
        when(gate.getGateShapeName()).thenReturn("MinimalSignDial");

        try (MockedStatic<GateRederivation> rederive = mockStatic(GateRederivation.class);
             MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            rederive.when(() -> GateRederivation.rederive(gate))
                .thenReturn(new GateRederivation.Outcome(GateRederivation.Result.REDERIVED,
                    List.of("redstone dial input", "iris lever")));

            assertTrue(run("regenerate", "alpha"));

            db.verify(() -> StargateDBManager.saveStargate(gate));
        }

        verify(sender).sendMessage(contains("redstone dial input, iris lever"));
    }

    /**
     * A gate nothing has changed underneath is neither saved nor reported.
     *
     * <p>Otherwise every regenerate writes a file and claims something moved, and the report
     * says the same thing whether or not anything was wrong -- the same failure
     * {@code exitMoved} exists to prevent for arrival points.
     */
    @Test
    void aGateWhoseMarkersAreAlreadyRightIsLeftAlone()
    {
        final Stargate gate = registeredGate("alpha");

        try (MockedStatic<GateRederivation> rederive = mockStatic(GateRederivation.class);
             MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            rederive.when(() -> GateRederivation.rederive(gate))
                .thenReturn(new GateRederivation.Outcome(GateRederivation.Result.REDERIVED,
                    List.of()));

            assertTrue(run("regenerate", "alpha"));

            db.verify(() -> StargateDBManager.saveStargate(gate), never());
        }

        verify(sender, never()).sendMessage(contains("and moved:"));
    }

    /**
     * Regenerate never lifts a gate's redstone before re-deriving it.
     *
     * <p>An earlier draft of the re-derivation took the wires up first, so a marker that had
     * moved would not leave its old wire behind. That is wrong, and the reason is the
     * {@code [RA]} lever: {@code setupRedstone} lifts it along with the dust, and puts back a
     * fresh, unpowered one. The dust is stateless, but that lever is an <em>output</em> --
     * "this block will provide redstone charge when the gate is activated" -- so on a gate
     * that happened to be open, regenerating it would have switched off whatever the gate was
     * powering while the wormhole was still running, and nothing in the command syncs it back
     * ({@code toggleRedstoneGateActivatedPower} is not called from here).
     *
     * <p>{@code setupRedstone} already declines to overwrite an occupied cell, so a marker
     * that has not moved needs no lifting anyway. A stale wire left by one that has is
     * cosmetic and gets named in the report; a dead output on a live gate is not.
     */
    @Test
    void regenerateNeverLiftsTheRedstoneItIsAboutToReplace()
    {
        final Stargate gate = registeredGate("alpha");
        when(gate.isGateRedstonePowered()).thenReturn(true);

        assertTrue(run("regenerate", "alpha"));

        verify(gate, never()).setupRedstone(false);
        verify(gate).setupRedstone(true);
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
