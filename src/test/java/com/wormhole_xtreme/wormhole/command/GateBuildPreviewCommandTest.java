package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;

/**
 * {@code /wormhole gate build}: who reaches it, what it chooses, and what it shows.
 *
 * <p>{@code gate} is admin-only, behind {@code wormhole.config}. A builder given only
 * {@code wormhole.build.preview} has to get through to {@code build} and nothing else under
 * {@code gate}, or the node either does nothing or opens gate editing along with it.
 */
class GateBuildPreviewCommandTest
{
    private static final String NO_PERMISSION = "You lack the permissions";

    private final Wormhole command = new Wormhole();
    private Player player;
    private Stargate3DShape standard;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        standard = new Stargate3DShape(Files.readAllLines(
            Paths.get("src/main/resources/shapes/gate/Standard.shape")).toArray(new String[0]));
        StargateShapeRegistry.getStargateShapes().put("Standard", standard);
        final Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("Standard", Map.of("structure", "OBSIDIAN", "chevron", "REDSTONE_LAMP"));
        groups.put("Atlantis", Map.of("structure", "LAPIS_BLOCK"));
        MaterialGroupRegistry.load(groups);

        player = mock(Player.class);
        when(player.getName()).thenReturn("builder");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateManager.forgetPlayer(player);
        StargateShapeRegistry.getStargateShapes().remove("Standard");
        MaterialGroupRegistry.load(null);
        PluginTestSupport.remove();
    }

    private void run(final String... args)
    {
        command.onCommand(player, null, "wormhole", args);
    }

    /**
     * A player holding only the preview node is shown the shape, in the default group, and has it
     * chosen for their next DHD.
     */
    @Test
    void thePreviewNodeAloneReachesGateBuildAndShowsTheShape()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.show(any(), any(), any())).thenReturn(GatePreviews.Shown.SHOWN);

            run("gate", "build", "Standard");

            previews.verify(() -> GatePreviews.show(eq(player), eq(standard),
                argThat((MaterialGroup group) -> "Standard".equals(group.getName()))));
        }
        verify(player, never()).sendMessage(contains(NO_PERMISSION));
        verify(player).sendMessage(contains("Previewing Standard in Standard"));
        assertSame(standard, StargateManager.getPlayerBuilderShape(player));
    }

    /** Naming a group shows the shape in that group. */
    @Test
    void aNamedGroupIsTheOneShown()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.show(any(), any(), any())).thenReturn(GatePreviews.Shown.SHOWN);

            run("gate", "build", "Standard", "atlantis");

            previews.verify(() -> GatePreviews.show(eq(player), eq(standard),
                argThat((MaterialGroup group) -> "Atlantis".equals(group.getName()))));
        }
    }

    /**
     * The preview node opens build and no other gate verb.
     *
     * <p>Asked of the dispatcher's own decision: several gate verbs refuse again in their handlers,
     * so a refusal message alone would not show which check said no.
     */
    @Test
    void thePreviewNodeOpensNoOtherGateVerb()
    {
        final SubCommands.Entry gate = SubCommands.find("gate");
        assertFalse(gate.admits(player, new String[] { "gate", "build", "Standard" }), "not without the node");

        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);

        assertTrue(gate.admits(player, new String[] { "gate", "BUILD", "Standard" }));
        for (final String verb : List.of("edit", "remove", "complete", "list", "import", "shapes", "force"))
        {
            assertFalse(gate.admits(player, new String[] { "gate", verb }), verb + " stays admin-only");
        }
        assertFalse(gate.admits(player, new String[] { "gate" }));
    }

    /** Without either node, build is refused and nothing is shown or chosen. */
    @Test
    void withNeitherNodeNothingIsShownOrChosen()
    {
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            run("gate", "build", "Standard");

            previews.verify(() -> GatePreviews.show(any(), any(), any()), never());
        }
        verify(player).sendMessage(contains(NO_PERMISSION));
        assertNull(StargateManager.getPlayerBuilderShape(player));
    }

    /** An admin without the preview node chooses the shape as before, and sees nothing new. */
    @Test
    void theConfigNodeAloneChoosesTheShapeWithoutAPreview()
    {
        when(player.hasPermission("wormhole.config")).thenReturn(true);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            run("gate", "build", "Standard");

            previews.verify(() -> GatePreviews.show(any(), any(), any()), never());
        }
        verify(player, never()).sendMessage(contains(NO_PERMISSION));
        assertSame(standard, StargateManager.getPlayerBuilderShape(player));
    }

    /**
     * With the block limit at 0 a player is told previews are off, not to clear one: there is
     * nothing to clear that would help.
     */
    @Test
    void aLimitOfZeroSaysPreviewsAreOffRatherThanToClearOne()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        com.wormhole_xtreme.wormhole.config.ConfigTestSupport.set(
            com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, 0);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.show(any(), any(), any())).thenReturn(GatePreviews.Shown.OVER_LIMIT);

            run("gate", "build", "Standard");
        }
        finally
        {
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.clear();
        }
        verify(player).sendMessage(contains("Previews are turned off on this server"));
        verify(player, never()).sendMessage(contains("clear a preview"));
        assertSame(standard, StargateManager.getPlayerBuilderShape(player));
    }

    /** A group that does not exist is refused, naming the groups that do, and nothing is chosen. */
    @Test
    void aGroupThatDoesNotExistIsRefusedNamingTheOnesThatDo()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            run("gate", "build", "Standard", "Nowhere");

            previews.verify(() -> GatePreviews.show(any(), any(), any()), never());
        }
        verify(player).sendMessage(contains("Try one of: Atlantis, Standard."));
        assertNull(StargateManager.getPlayerBuilderShape(player));
    }

    /** -clear takes the preview looked at, and -clear -all takes every one; a bare word is a shape. */
    @Test
    void clearAndClearAllReachTheirPreviews()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.clearAll(player)).thenReturn(3);

            run("gate", "build", "-clear");
            run("gate", "build", "-clear", "-all");
            run("gate", "build", "clear");
            run("gate", "build", "-bogus");

            previews.verify(() -> GatePreviews.clearLookedAt(player));
            previews.verify(() -> GatePreviews.clearAll(player));
        }
        verify(player).sendMessage(contains("Cleared 3 previews."));
        verify(player).sendMessage(contains("Invalid shape: clear"));
        verify(player).sendMessage(contains("No such option: -bogus. Try -clear, -activate, -iris, -material, -chevrons, -dhd."));
    }

    /** Completion offers clear beside the shapes, all after clear, and the groups after a shape. */
    @Test
    void completionOffersClearTheShapesAndTheirGroups()
    {
        final SubCommands.Entry gate = SubCommands.find("gate");

        final List<String> third = gate.completeArgs(player, new String[] { "gate", "build", "" });
        assertTrue(third.contains("-clear") && third.contains("Standard"), "got " + third);
        assertEquals(List.of("-all"), gate.completeArgs(player, new String[] { "gate", "build", "-clear", "" }));
        assertEquals(List.of("Atlantis", "Standard"),
            gate.completeArgs(player, new String[] { "gate", "build", "Standard", "" }));
        assertEquals(List.of("Atlantis"), gate.completeArgs(player, new String[] { "gate", "build", "Standard", "a" }));
    }

    /** Each option reaches its control on the preview looked at, and says what it did. */
    @Test
    void theOptionsReachTheirControls()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.activate(player)).thenReturn(GatePreviews.Control.DIALLING);
            previews.when(() -> GatePreviews.iris(player)).thenReturn(GatePreviews.Control.IRIS_CLOSED);
            previews.when(() -> GatePreviews.toggleDhd(player)).thenReturn(GatePreviews.Control.DHD_HIDDEN);
            previews.when(() -> GatePreviews.toggleChevrons(player)).thenReturn(GatePreviews.Control.CHEVRONS_PLAIN);
            previews.when(() -> GatePreviews.material(any(Player.class), any(MaterialGroup.class)))
                .thenReturn(GatePreviews.Control.CHANGED);
            previews.when(() -> GatePreviews.material(any(Player.class), any(com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role.class),
                any(org.bukkit.Material.class))).thenReturn(GatePreviews.Control.NOT_LOOKING);

            run("gate", "build", "-activate");
            run("gate", "build", "-IRIS");
            run("gate", "build", "-dhd");
            run("gate", "build", "-chevrons");
            run("gate", "build", "-material", "atlantis");
            run("gate", "build", "-material", "frame", "gold_block");

            previews.verify(() -> GatePreviews.material(eq(player),
                argThat((MaterialGroup group) -> "Atlantis".equals(group.getName()))));
            previews.verify(() -> GatePreviews.material(player, com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role.FRAME,
                org.bukkit.Material.GOLD_BLOCK));
        }
        verify(player).sendMessage(contains("Dialling."));
        verify(player).sendMessage(contains("Iris closed."));
        verify(player).sendMessage(contains("DHD hidden."));
        verify(player).sendMessage(contains("Chevrons drawn as frame"));
        verify(player).sendMessage(contains("Materials changed."));
        verify(player).sendMessage(contains("Look at one of your previews first."));
    }

    /** -material names what it takes when given something else, and refuses a block that does not exist. */
    @Test
    void materialSaysWhatItTakes()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            run("gate", "build", "-material", "sparkly");
            run("gate", "build", "-material", "frame", "unobtainium");

            previews.verify(() -> GatePreviews.material(any(Player.class),
                any(com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role.class), any(org.bukkit.Material.class)), never());
        }
        verify(player).sendMessage(contains("-material <frame|chevron|light|portal|iris|sign> <block>"));
        verify(player).sendMessage(contains("That is not a block that can be shown."));
    }

    /** The controls are the preview node's; an admin without it may clear, and nothing else. */
    @Test
    void theControlsNeedThePreviewNode()
    {
        when(player.hasPermission("wormhole.config")).thenReturn(true);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            run("gate", "build", "-activate");

            previews.verify(() -> GatePreviews.activate(any()), never());
        }
        verify(player).sendMessage(contains(NO_PERMISSION));
    }

    /** Completion offers the options, the groups and roles after -material, and blocks after a role. */
    @Test
    void completionOffersTheOptionsAndWhatMaterialTakes()
    {
        final SubCommands.Entry gate = SubCommands.find("gate");

        assertTrue(gate.completeArgs(player, new String[] { "gate", "build", "-" })
            .containsAll(List.of("-clear", "-activate", "-iris", "-material", "-chevrons", "-dhd")));
        assertTrue(gate.completeArgs(player, new String[] { "gate", "build", "-material", "" })
            .containsAll(List.of("Atlantis", "Standard", "frame", "iris")));
        assertTrue(gate.completeArgs(player, new String[] { "gate", "build", "-material", "frame", "gold_b" })
            .contains("gold_block"));
        assertTrue(gate.completeArgs(player, new String[] { "gate", "build", "-material", "frame", "" }).isEmpty(),
            "every block at once is not a list anybody reads");
    }
}
