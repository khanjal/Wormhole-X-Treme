package com.wormhole_xtreme.wormhole.command.handlers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.Refresh;
import com.wormhole_xtreme.wormhole.logic.GateRederivation;
import com.wormhole_xtreme.wormhole.logic.GateRefresh;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@code /wormhole gate regenerate} says about one gate, read by key from {@code lang/} (#26).
 *
 * <p>Each line is sent through the real command twice. Once to an English player, against the
 * concatenation the code built before the move, copied from it, so a reworded template, a lost
 * plural or a swapped argument shows as a changed message. Once to a player whose client reads the
 * test language qq, so a call site that reads the server's language instead of the player's shows as
 * English, and each singular and plural variant is told apart by its own qq wording. The
 * {@code -all} sweep is in {@link RegenerateSweepMessagesTest}.
 */
class RegenerateMessagesTest
{
    private Stargate gate;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        clearGates();
        gate = registeredGate("alpha");
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

    /** A registered gate on the Standard shape that answers everything regenerate asks of it. */
    private static Stargate registeredGate(final String name)
    {
        final Stargate made = mock(Stargate.class);
        when(made.getGateName()).thenReturn(name);
        when(made.getGateShapeName()).thenReturn("Standard");
        StargateManager.registerStargate(made);
        return made;
    }

    /** An admin whose client reads {@code locale}. */
    private static Player adminReading(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        when(player.getName()).thenReturn("Admin");
        when(player.getUniqueId()).thenReturn(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        when(player.isOp()).thenReturn(Boolean.TRUE);
        when(player.hasPermission(anyString())).thenReturn(Boolean.TRUE);
        return player;
    }

    private static String name(final String text)
    {
        return ChatText.name(text);
    }

    private static String value(final String text)
    {
        return ChatText.value(text);
    }

    private static String command(final String text)
    {
        return ChatText.command(text);
    }

    /**
     * Runs {@code /wormhole gate regen} with re-derivation doing nothing, unless {@code stubs} says otherwise.
     *
     * @param fresh
     *            the gate a re-detection finds, or null for none
     * @param stubs
     *            what to change about the defaults
     */
    private static void regen(final Player player, final Stargate fresh,
        final Consumer<MockedStatic<GateRederivation>> stubs, final String... args)
    {
        try (MockedStatic<GateRederivation> rederive = mockStatic(GateRederivation.class);
             MockedStatic<GateRefresh> refresh = mockStatic(GateRefresh.class);
             MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            rederive.when(() -> GateRederivation.restoreFrameUnderSigns(any())).thenReturn(List.of());
            rederive.when(() -> GateRederivation.rebuildLightOrder(any()))
                .thenReturn(GateRederivation.LightResult.UNCHANGED);
            rederive.when(() -> GateRederivation.rederive(any()))
                .thenReturn(new GateRederivation.Outcome(GateRederivation.Result.REDERIVED, List.of()));
            rederive.when(() -> GateRederivation.fillCap(anyInt())).thenCallRealMethod();
            refresh.when(() -> GateRefresh.refresh(any(), any(), any())).thenReturn(fresh);
            stubs.accept(rederive);
            new RegenerateCommand().execute(player, args);
        }
    }

    private static void regen(final Player player, final String... args)
    {
        regen(player, null, rederive -> { }, args);
    }

    /** Runs {@code regen alpha -shape Massive} against one loaded shape, fitting as {@code fit} says. */
    private static void regenOntoMassive(final Player player, final boolean enabled,
        final GateRederivation.ShapeFit fit, final Consumer<MockedStatic<GateRederivation>> stubs)
    {
        final ConcurrentMap<String, StargateShape> shapes = new ConcurrentHashMap<>();
        final Stargate3DShape shape = mock(Stargate3DShape.class);
        when(shape.getShapeName()).thenReturn("Massive");
        shapes.put("Massive", shape);
        try (MockedStatic<StargateShapeRegistry> registry = mockStatic(StargateShapeRegistry.class))
        {
            registry.when(StargateShapeRegistry::getStargateShapes).thenReturn(shapes);
            registry.when(() -> StargateShapeRegistry.isEnabled("Massive")).thenReturn(enabled);
            regen(player, null, rederive ->
            {
                rederive.when(() -> GateRederivation.adoptShape(any(), any())).thenReturn(fit);
                stubs.accept(rederive);
            }, "regen", "alpha", "-shape", "Massive");
        }
    }

    private static GateRederivation.Outcome outcome(final GateRederivation.Result result, final String... changes)
    {
        return new GateRederivation.Outcome(result, List.of(changes));
    }

    // ---- what a command that cannot start says ----

    /** Each way the command declines to start, against the old expression. */
    @Test
    void theUsageLinesReadAsTheyDidBefore()
    {
        final Player player = adminReading("en");
        try
        {
            regen(player, "regen", "-fill");
            regen(player, "regen", "alpha", "-shape");
            regen(player, "regen");
        }
        finally
        {
            Refresh.removePendingRefresh(player);
        }

        verify(player).sendMessage(ChatText.error("Name the gate to fill: "
            + command("/wormhole gate regen <gate> -fill")));
        verify(player).sendMessage(ChatText.error("Name the shape: "
            + command("/wormhole gate regen <gate> -shape <shape>")));
        verify(player).sendMessage(ChatText.info("Click the DHD of the gate to regenerate it."));
    }

    /** The same lines reach a qq player in qq. */
    @Test
    void theUsageLinesAreInThePlayersLanguage()
    {
        final Player player = adminReading("qq");
        try
        {
            regen(player, "regen", "-fill");
            regen(player, "regen", "alpha", "-shape");
            regen(player, "regen");
        }
        finally
        {
            Refresh.removePendingRefresh(player);
        }

        verify(player).sendMessage(ChatText.error("QQ name gate " + command("/wormhole gate regen <gate> -fill")));
        verify(player).sendMessage(ChatText.error("QQ name shape "
            + command("/wormhole gate regen <gate> -shape <shape>")));
        verify(player).sendMessage(ChatText.info("QQ click dhd"));
    }

    /** A click on a block no gate owns, against the old expression. */
    @Test
    void aClickOnNoGateReadsAsItDidBefore()
    {
        final Player player = adminReading("en");

        clickOnNoGate(player);

        verify(player).sendMessage(ChatText.error(
            "No registered gate found at that block. Build or complete the gate first."));
    }

    /** The same refusal reaches a qq player in qq. */
    @Test
    void aClickOnNoGateIsInThePlayersLanguage()
    {
        final Player player = adminReading("qq");

        clickOnNoGate(player);

        verify(player).sendMessage(ChatText.error("QQ no gate here"));
    }

    private static void clickOnNoGate(final Player player)
    {
        try (MockedStatic<StargateManager> manager = mockStatic(StargateManager.class))
        {
            RegenerateCommand.regenerateClicked(player, mock(Block.class), null, false);
        }
    }

    /** A gate nobody built is named back in the reader's own language, English or qq. */
    @Test
    void anUnknownGateIsNamedBackInThePlayersLanguage()
    {
        final Player english = adminReading("en");
        final Player qq = adminReading("qq");

        regen(english, "regen", "nowhere");
        regen(qq, "regen", "nowhere");

        verify(english).sendMessage(ChatText.error("No gate called " + name("nowhere") + ". "
            + command("/wormhole list") + " shows them."));
        verify(qq).sendMessage(ChatText.error("QQ no gate " + name("nowhere") + " " + command("/wormhole list") + "."));
    }

    // ---- shutting down, detecting afresh, the arrival point ----

    /** An open gate is shut and keeps its geometry; its arrival point is recomputed. */
    @Test
    void anOpenGateReadsAsItDidBefore()
    {
        when(gate.isGateActive()).thenReturn(true);
        when(gate.recomputeGatePlayerTeleportLocation()).thenReturn(true);
        final Player player = adminReading("en");

        regen(player, "regen", "alpha");

        verify(player).sendMessage(ChatText.info("Shut " + name("alpha") + " down to regenerate it."));
        verify(player).sendMessage(ChatText.info(name("alpha")
            + " is open or dialling, so its recorded geometry is kept. Regenerate it again once it is shut."));
        verify(player).sendMessage(ChatText.info("Arrival point recomputed for " + name("alpha") + "."));
        verify(player).sendMessage(ChatText.info("Regenerated " + name("alpha") + "."));
    }

    /** The same lines reach a qq player in qq. */
    @Test
    void anOpenGateIsInThePlayersLanguage()
    {
        when(gate.isGateActive()).thenReturn(true);
        when(gate.recomputeGatePlayerTeleportLocation()).thenReturn(true);
        final Player player = adminReading("qq");

        regen(player, "regen", "alpha");

        verify(player).sendMessage(ChatText.info("QQ shut " + name("alpha")));
        verify(player).sendMessage(ChatText.info("QQ redetect busy " + name("alpha")));
        verify(player).sendMessage(ChatText.info("QQ arrival " + name("alpha")));
        verify(player).sendMessage(ChatText.info("QQ regenerated " + name("alpha")));
    }

    /** A shut gate that matches no shape keeps its geometry; one that does is detected afresh. */
    @Test
    void redetectingReadsAsItDidBefore()
    {
        final Stargate fresh = mock(Stargate.class);
        when(fresh.getGateName()).thenReturn("alpha");
        when(fresh.getGateShapeName()).thenReturn("Grand");
        final Player none = adminReading("en");
        final Player found = adminReading("en");

        regen(none, "regen", "alpha");
        regen(found, fresh, rederive -> { }, "regen", "alpha");

        verify(none).sendMessage(ChatText.info("No shape matches all of " + name("alpha")
            + "'s frame, so its recorded geometry is kept."));
        verify(found).sendMessage(ChatText.info("Re-detected " + name("alpha") + " from its frame as " + name("Grand")
            + ": frame, opening and chevrons taken afresh."));
    }

    /** The same two lines reach a qq player in qq. */
    @Test
    void redetectingIsInThePlayersLanguage()
    {
        final Stargate fresh = mock(Stargate.class);
        when(fresh.getGateName()).thenReturn("alpha");
        when(fresh.getGateShapeName()).thenReturn("Grand");
        final Player none = adminReading("qq");
        final Player found = adminReading("qq");

        regen(none, "regen", "alpha");
        regen(found, fresh, rederive -> { }, "regen", "alpha");

        verify(none).sendMessage(ChatText.info("QQ no match " + name("alpha")));
        verify(found).sendMessage(ChatText.info("QQ redetected " + name("alpha") + " as " + name("Grand")));
    }

    /** A sign taken out of the frame says where the block went back. */
    @Test
    void aSignTakenOutOfTheFrameReadsAsItDidBeforeAndInThePlayersLanguage()
    {
        final Block restored = mock(Block.class);
        when(restored.getX()).thenReturn(1);
        when(restored.getY()).thenReturn(64);
        when(restored.getZ()).thenReturn(-3);
        final Player english = adminReading("en");
        final Player qq = adminReading("qq");

        regen(english, null, rederive -> rederive.when(() -> GateRederivation.restoreFrameUnderSigns(any()))
            .thenReturn(List.of(restored)), "regen", "alpha");
        regen(qq, null, rederive -> rederive.when(() -> GateRederivation.restoreFrameUnderSigns(any()))
            .thenReturn(List.of(restored)), "regen", "alpha");

        verify(english).sendMessage(ChatText.info("Took a sign out of " + name("alpha") + "'s frame at "
            + value("1 64 -3") + " and put the block back."));
        verify(qq).sendMessage(ChatText.info("QQ sign " + value("1 64 -3") + " out of " + name("alpha")));
    }

    // ---- water and lava ----

    /** One block and several, standing and cleared, against the old expression. */
    @Test
    void waterAndLavaReadAsTheyDidBefore()
    {
        final Block puddle = mock(Block.class);
        final Player player = adminReading("en");

        when(gate.strandedLiquid()).thenReturn(List.of(puddle));
        regen(player, "regen", "alpha");
        when(gate.strandedLiquid()).thenReturn(List.of(puddle, puddle, puddle));
        regen(player, "regen", "alpha");
        when(gate.clearStrandedLiquid()).thenReturn(1);
        regen(player, "regen", "alpha", "-water");
        when(gate.clearStrandedLiquid()).thenReturn(3);
        regen(player, "regen", "alpha", "-water");

        final String clearWith = command("/wormhole gate regen alpha -water");
        verify(player).sendMessage(ChatText.info(ChatText.bad("1 water or lava block") + " stand in " + name("alpha")
            + "'s opening. If a dial left them, clear them with " + clearWith + "."));
        verify(player).sendMessage(ChatText.info(ChatText.bad("3 water or lava blocks") + " stand in " + name("alpha")
            + "'s opening. If a dial left them, clear them with " + clearWith + "."));
        verify(player).sendMessage(ChatText.info(ChatText.good("Cleared 1") + " water or lava block"
            + " left standing in " + name("alpha") + "."));
        verify(player).sendMessage(ChatText.info(ChatText.good("Cleared 3") + " water or lava blocks"
            + " left standing in " + name("alpha") + "."));
    }

    /** The same four lines reach a qq player in qq, one told apart from several. */
    @Test
    void waterAndLavaAreInThePlayersLanguage()
    {
        final Block puddle = mock(Block.class);
        final Player player = adminReading("qq");

        when(gate.strandedLiquid()).thenReturn(List.of(puddle));
        regen(player, "regen", "alpha");
        when(gate.strandedLiquid()).thenReturn(List.of(puddle, puddle, puddle));
        regen(player, "regen", "alpha");
        when(gate.clearStrandedLiquid()).thenReturn(1);
        regen(player, "regen", "alpha", "-water");
        when(gate.clearStrandedLiquid()).thenReturn(3);
        regen(player, "regen", "alpha", "-water");

        final String clearWith = command("/wormhole gate regen alpha -water");
        verify(player).sendMessage(ChatText.info("QQ standing-one 1 " + name("alpha") + " " + clearWith));
        verify(player).sendMessage(ChatText.info("QQ standing-many 3 " + name("alpha") + " " + clearWith));
        verify(player).sendMessage(ChatText.info("QQ cleared-one 1 in " + name("alpha")));
        verify(player).sendMessage(ChatText.info("QQ cleared-many 3 in " + name("alpha")));
    }

    // ---- what re-reading the shape and the light order came to ----

    /** Each way re-deriving ends, against the old expression. */
    @Test
    void theRederivationReportsReadAsTheyDidBefore()
    {
        final Player player = adminReading("en");

        regen(player, null, r -> r.when(() -> GateRederivation.rederive(any()))
            .thenReturn(outcome(GateRederivation.Result.NO_SHAPE)), "regen", "alpha");
        regen(player, null, r -> r.when(() -> GateRederivation.rederive(any()))
            .thenReturn(outcome(GateRederivation.Result.NO_ANCHOR)), "regen", "alpha");
        regen(player, null, r -> r.when(() -> GateRederivation.rederive(any()))
            .thenReturn(outcome(GateRederivation.Result.NOT_DETECTED)), "regen", "alpha");
        regen(player, null, r -> r.when(() -> GateRederivation.rederive(any()))
            .thenReturn(outcome(GateRederivation.Result.REDERIVED, "redstone dial input", "iris lever")),
            "regen", "alpha");

        verify(player).sendMessage(ChatText.info("Cannot re-read the shape of " + name("alpha") + ": " + name("Standard")
            + " is not a 3D shape to re-derive from -- either it is missing from the shapes"
            + " folder, or it is an older 2D shape file with no Version=2 line. Markers left as they are."));
        verify(player).sendMessage(ChatText.info(name("alpha")
            + " records no dial button, so its shape cannot be re-read. Markers left as they are."));
        verify(player).sendMessage(ChatText.info(name("alpha") + " no longer matches shape " + name("Standard")
            + ", so its markers were left alone. Check the frame is still standing."));
        verify(player).sendMessage(ChatText.info("Re-read " + name("Standard") + " for " + name("alpha")
            + " and moved: " + value("redstone dial input, iris lever") + "."));
    }

    /** The same four lines reach a qq player in qq, the changes joined by the language's own separator. */
    @Test
    void theRederivationReportsAreInThePlayersLanguage()
    {
        final Player player = adminReading("qq");

        regen(player, null, r -> r.when(() -> GateRederivation.rederive(any()))
            .thenReturn(outcome(GateRederivation.Result.NO_SHAPE)), "regen", "alpha");
        regen(player, null, r -> r.when(() -> GateRederivation.rederive(any()))
            .thenReturn(outcome(GateRederivation.Result.NO_ANCHOR)), "regen", "alpha");
        regen(player, null, r -> r.when(() -> GateRederivation.rederive(any()))
            .thenReturn(outcome(GateRederivation.Result.NOT_DETECTED)), "regen", "alpha");
        regen(player, null, r -> r.when(() -> GateRederivation.rederive(any()))
            .thenReturn(outcome(GateRederivation.Result.REDERIVED, "redstone dial input", "iris lever")),
            "regen", "alpha");

        verify(player).sendMessage(ChatText.info("QQ no shape " + name("alpha") + " " + name("Standard")));
        verify(player).sendMessage(ChatText.info("QQ no anchor " + name("alpha")));
        verify(player).sendMessage(ChatText.info("QQ no longer " + name("alpha") + " " + name("Standard")));
        verify(player).sendMessage(ChatText.info("QQ moved " + name("Standard") + " " + name("alpha") + " "
            + value("redstone dial input / iris lever")));
    }

    /** Each way the light order ends, against the old expression. */
    @Test
    void theLightOrderReportsReadAsTheyDidBefore()
    {
        final Player rebuilt = adminReading("en");
        final Player busy = adminReading("en");
        final Player misfit = adminReading("en");

        regen(rebuilt, null, r -> r.when(() -> GateRederivation.rebuildLightOrder(any()))
            .thenReturn(GateRederivation.LightResult.REBUILT), "regen", "alpha");
        regen(busy, null, r -> r.when(() -> GateRederivation.rebuildLightOrder(any()))
            .thenReturn(GateRederivation.LightResult.BUSY), "regen", "alpha");
        regen(misfit, null, r -> r.when(() -> GateRederivation.rebuildLightOrder(any()))
            .thenReturn(GateRederivation.LightResult.DOES_NOT_FIT), "regen", "alpha");

        verify(rebuilt).sendMessage(ChatText.info(name("alpha") + " now lights its chevrons in " + name("Standard")
            + "'s order."));
        verify(busy).sendMessage(ChatText.info(name("alpha")
            + " is dialling or open, so its light order was left alone. Try again once it is shut."));
        verify(misfit).sendMessage(ChatText.info("Shape " + name("Standard") + " lights blocks that are not part of "
            + name("alpha") + "'s frame, so its light order was left alone."));
    }

    /** The same three lines reach a qq player in qq. */
    @Test
    void theLightOrderReportsAreInThePlayersLanguage()
    {
        final Player rebuilt = adminReading("qq");
        final Player busy = adminReading("qq");
        final Player misfit = adminReading("qq");

        regen(rebuilt, null, r -> r.when(() -> GateRederivation.rebuildLightOrder(any()))
            .thenReturn(GateRederivation.LightResult.REBUILT), "regen", "alpha");
        regen(busy, null, r -> r.when(() -> GateRederivation.rebuildLightOrder(any()))
            .thenReturn(GateRederivation.LightResult.BUSY), "regen", "alpha");
        regen(misfit, null, r -> r.when(() -> GateRederivation.rebuildLightOrder(any()))
            .thenReturn(GateRederivation.LightResult.DOES_NOT_FIT), "regen", "alpha");

        verify(rebuilt).sendMessage(ChatText.info("QQ rebuilt " + name("alpha") + " " + name("Standard")));
        verify(busy).sendMessage(ChatText.info("QQ light busy " + name("alpha")));
        verify(misfit).sendMessage(ChatText.info("QQ light misfit " + name("Standard") + " " + name("alpha")));
    }

    // ---- naming a shape ----

    private static GateRederivation.ShapeFit fitting(final int present, final int expected, final int gaps,
        final boolean accepted)
    {
        final List<GateRederivation.Gap> missing = new ArrayList<>();
        for (int i = 0; i < gaps; i++)
        {
            missing.add(new GateRederivation.Gap(i, 64, 0, Material.AIR));
        }
        return new GateRederivation.ShapeFit(accepted, present, expected, missing,
            new GateRederivation.Layout(null, 2, 0, 0, false));
    }

    /** The shape refusals: not loaded, turned off, and a gate that records nothing to lay it from. */
    @Test
    void theShapeRefusalsReadAsTheyDidBefore()
    {
        final Player unknown = adminReading("en");
        final Player off = adminReading("en");
        final Player anchorless = adminReading("en");

        regen(unknown, "regen", "alpha", "-shape", "Colossal");
        regenOntoMassive(off, false, fitting(0, 0, 0, false), r -> { });
        regenOntoMassive(anchorless, true, fitting(0, 0, 0, false), r -> { });

        verify(unknown).sendMessage(ChatText.error("No gate shape called " + name("Colossal") + " is loaded."));
        verify(off).sendMessage(ChatText.error(name("Massive") + " is turned off, so a gate cannot be moved onto it."));
        verify(anchorless).sendMessage(ChatText.error(name("alpha")
            + " records no dial button, facing or world to lay " + name("Massive") + " from, so it stays "
            + name("Standard") + "."));
    }

    /** The same three refusals reach a qq player in qq. */
    @Test
    void theShapeRefusalsAreInThePlayersLanguage()
    {
        final Player unknown = adminReading("qq");
        final Player off = adminReading("qq");
        final Player anchorless = adminReading("qq");

        regen(unknown, "regen", "alpha", "-shape", "Colossal");
        regenOntoMassive(off, false, fitting(0, 0, 0, false), r -> { });
        regenOntoMassive(anchorless, true, fitting(0, 0, 0, false), r -> { });

        verify(unknown).sendMessage(ChatText.error("QQ unknown shape " + name("Colossal")));
        verify(off).sendMessage(ChatText.error("QQ shape off " + name("Massive")));
        verify(anchorless).sendMessage(ChatText.error("QQ no anchor " + name("alpha") + " " + name("Massive") + " "
            + name("Standard")));
    }

    /** A shape taken, with a gap, its layout and the hint to fill, against the old expression. */
    @Test
    void aShapeTakenReadsAsItDidBefore()
    {
        final Player player = adminReading("en");

        regenOntoMassive(player, true, fitting(99, 100, 1, true), r -> { });

        verify(player).sendMessage(ChatText.info(name("alpha") + " is now " + name("Massive") + " (was "
            + name("Standard") + "): " + ChatText.good("99 of 100") + " frame blocks in place."));
        verify(player).sendMessage(ChatText.info("Laid by its recorded frame, " + value("2 toward the DHD")
            + " from where its DHD puts it."));
        verify(player).sendMessage(ChatText.info(ChatText.bad("Missing or wrong") + ": " + value("0 64 0") + " ("
            + ChatText.material("AIR") + ")."));
        verify(player).sendMessage(ChatText.info("Add " + command("-fill")
            + " to place the missing blocks from the gate's own materials."));
    }

    /** The same four lines reach a qq player in qq. */
    @Test
    void aShapeTakenIsInThePlayersLanguage()
    {
        final Player player = adminReading("qq");

        regenOntoMassive(player, true, fitting(99, 100, 1, true), r -> { });

        verify(player).sendMessage(ChatText.info("QQ adopted " + name("alpha") + " " + name("Massive") + " "
            + name("Standard") + " 99/100"));
        verify(player).sendMessage(ChatText.info("QQ laid " + value("2 toward the DHD")));
        verify(player).sendMessage(ChatText.info("QQ gaps: QQ " + value("0 64 0") + " [" + ChatText.material("AIR")
            + "]!"));
        verify(player).sendMessage(ChatText.info("QQ add " + command("-fill")));
    }

    /** A shape refused, with twelve gaps: ten listed and the other two counted, against the old expression. */
    @Test
    void aShapeRefusedAndItsLongGapListReadAsTheyDidBefore()
    {
        final Player player = adminReading("en");

        regenOntoMassive(player, true, fitting(20, 100, 12, false), r -> { });

        final List<String> shown = new ArrayList<>();
        for (int i = 0; i < 10; i++)
        {
            shown.add(value(i + " 64 0") + " (" + ChatText.material("AIR") + ")");
        }
        verify(player).sendMessage(ChatText.error(name("alpha") + " stays " + name("Standard") + ": only "
            + ChatText.bad("20 of 100") + " frame blocks of " + name("Massive") + " are in place, under the "
            + value("90%") + " needed."));
        verify(player).sendMessage(ChatText.info(ChatText.bad("Missing or wrong") + ": " + String.join(", ", shown)
            + ", and " + value("2") + " more."));
    }

    /** The same two lines reach a qq player in qq, with the list's own separator and tail. */
    @Test
    void aShapeRefusedAndItsLongGapListAreInThePlayersLanguage()
    {
        final Player player = adminReading("qq");

        regenOntoMassive(player, true, fitting(20, 100, 12, false), r -> { });

        final List<String> shown = new ArrayList<>();
        for (int i = 0; i < 10; i++)
        {
            shown.add("QQ " + value(i + " 64 0") + " [" + ChatText.material("AIR") + "]");
        }
        verify(player).sendMessage(ChatText.error("QQ stays " + name("alpha") + " " + name("Standard") + " 20/100 "
            + name("Massive") + " " + value("90%")));
        verify(player).sendMessage(ChatText.info("QQ gaps: " + String.join(" / ", shown) + " +" + value("2")
            + " QQ more!"));
    }

    /** A shape taken that leaves the markers waiting on its frame, one block short and several. */
    @Test
    void markersWaitingOnTheFrameReadAsTheyDidBefore()
    {
        final Player one = adminReading("en");
        final Player several = adminReading("en");
        final Consumer<MockedStatic<GateRederivation>> undetected = r -> r.when(() -> GateRederivation.rederive(any()))
            .thenReturn(outcome(GateRederivation.Result.NOT_DETECTED));

        regenOntoMassive(one, true, fitting(99, 100, 1, true), undetected);
        regenOntoMassive(several, true, fitting(97, 100, 3, true), undetected);

        verify(one).sendMessage(ChatText.info("Markers on " + name("alpha")
            + " left as they are until its frame is whole: " + value("1") + " block short of a " + name("Standard")
            + "."));
        verify(several).sendMessage(ChatText.info("Markers on " + name("alpha")
            + " left as they are until its frame is whole: " + value("3") + " blocks short of a " + name("Standard")
            + "."));
    }

    /** The same two lines reach a qq player in qq, one block told apart from several. */
    @Test
    void markersWaitingOnTheFrameAreInThePlayersLanguage()
    {
        final Player one = adminReading("qq");
        final Player several = adminReading("qq");
        final Consumer<MockedStatic<GateRederivation>> undetected = r -> r.when(() -> GateRederivation.rederive(any()))
            .thenReturn(outcome(GateRederivation.Result.NOT_DETECTED));

        regenOntoMassive(one, true, fitting(99, 100, 1, true), undetected);
        regenOntoMassive(several, true, fitting(97, 100, 3, true), undetected);

        verify(one).sendMessage(ChatText.info("QQ short-one " + name("alpha") + " " + value("1") + " "
            + name("Standard")));
        verify(several).sendMessage(ChatText.info("QQ short-many " + name("alpha") + " " + value("3") + " "
            + name("Standard")));
    }

    // ---- filling the frame ----

    /** A block placed by -fill, at a place a message can name. */
    private static Block placedBlock()
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.OBSIDIAN);
        when(block.getX()).thenReturn(5998);
        when(block.getY()).thenReturn(141);
        when(block.getZ()).thenReturn(3);
        return block;
    }

    private static Consumer<MockedStatic<GateRederivation>> filling(final GateRederivation.Fill fill)
    {
        return r -> r.when(() -> GateRederivation.fillFrame(any())).thenReturn(fill);
    }

    /** What -fill says when it places blocks, refuses for the cap, and meets a solid block. */
    @Test
    void fillReadsAsItDidBefore()
    {
        final GateRederivation.Gap air = new GateRederivation.Gap(5998, 141, 3, Material.AIR);
        final GateRederivation.Gap stone = new GateRederivation.Gap(5998, 141, 3, Material.STONE);
        final Player placed = adminReading("en");
        final Player capped = adminReading("en");
        final Player blocked = adminReading("en");

        regen(placed, null, filling(new GateRederivation.Fill(List.of(placedBlock()), List.of(air), List.of(), 4)),
            "regen", "alpha", "-fill");
        regen(capped, null, filling(new GateRederivation.Fill(List.of(), List.of(air, air, air, air, air), List.of(), 4)),
            "regen", "alpha", "-fill");
        regen(blocked, null, filling(new GateRederivation.Fill(List.of(), List.of(stone), List.of(stone), 4)),
            "regen", "alpha", "-fill");

        verify(placed).sendMessage(ChatText.info("Placed " + ChatText.material("OBSIDIAN") + " at "
            + value("5998 141 3") + "."));
        verify(capped).sendMessage(ChatText.error("Not filling " + name("alpha") + ": " + ChatText.bad("5 blocks")
            + " are missing, more than the " + value("4") + " " + command("-fill")
            + " will place. Check the shape, and where it is laid."));
        verify(blocked).sendMessage(ChatText.error("Not filling " + name("alpha") + ": " + value("5998 141 3")
            + " holds " + ChatText.material("STONE") + ". Clear it, or place the block yourself."));
    }

    /** The same three lines reach a qq player in qq, the block and its place in the other order. */
    @Test
    void fillIsInThePlayersLanguage()
    {
        final GateRederivation.Gap air = new GateRederivation.Gap(5998, 141, 3, Material.AIR);
        final GateRederivation.Gap stone = new GateRederivation.Gap(5998, 141, 3, Material.STONE);
        final Player placed = adminReading("qq");
        final Player capped = adminReading("qq");
        final Player blocked = adminReading("qq");

        regen(placed, null, filling(new GateRederivation.Fill(List.of(placedBlock()), List.of(air), List.of(), 4)),
            "regen", "alpha", "-fill");
        regen(capped, null, filling(new GateRederivation.Fill(List.of(), List.of(air, air, air, air, air), List.of(), 4)),
            "regen", "alpha", "-fill");
        regen(blocked, null, filling(new GateRederivation.Fill(List.of(), List.of(stone), List.of(stone), 4)),
            "regen", "alpha", "-fill");

        verify(placed).sendMessage(ChatText.info("QQ placed " + value("5998 141 3") + " at "
            + ChatText.material("OBSIDIAN")));
        verify(capped).sendMessage(ChatText.error("QQ overcap " + name("alpha") + " " + ChatText.bad("5") + " "
            + value("4") + " " + command("-fill")));
        verify(blocked).sendMessage(ChatText.error("QQ blocked " + name("alpha") + " " + value("5998 141 3") + " "
            + ChatText.material("STONE")));
    }
}
