package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    /** The command every preview action follows, as hints print it. */
    private static final String PREVIEW = "/wormhole gate preview ";
    /** The preview actions, as a message lists them. */
    private static final String ACTIONS = "clear activate iris material materials guide layer chevrons dhd share place";

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

    /** A chat line that reads as this once its colours are taken out. */
    private static String saying(final String words)
    {
        return argThat((String line) -> (line != null) && line.replaceAll("\u00A7.", "").contains(words));
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
        verify(player, never()).sendMessage(saying(NO_PERMISSION));
        verify(player).sendMessage(saying("Previewing Standard in Standard"));
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
        verify(player).sendMessage(saying(NO_PERMISSION));
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
        verify(player, never()).sendMessage(saying(NO_PERMISSION));
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
        verify(player).sendMessage(saying("Previews are off on this server"));
        verify(player, never()).sendMessage(saying("Clear one"));
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
        verify(player).sendMessage(saying("Try Atlantis, Standard."));
        assertNull(StargateManager.getPlayerBuilderShape(player));
    }

    /**
     * preview clear takes the preview looked at, and clear -all takes every one. Under build, a bare word
     * is a shape, and a dashed one points at preview rather than reading as a missing shape.
     */
    @Test
    void clearAndClearAllReachTheirPreviews()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.clearAll(player)).thenReturn(3);

            run("gate", "preview", "clear");
            run("gate", "preview", "clear", "-all");
            run("gate", "build", "clear");
            run("gate", "build", "-guide");
            run("gate", "preview", "bogus");

            previews.verify(() -> GatePreviews.clearLookedAt(player));
            previews.verify(() -> GatePreviews.clearAll(player));
        }
        verify(player).sendMessage(saying("Cleared 3 previews."));
        verify(player).sendMessage(saying("No shape called clear."));
        verify(player).sendMessage(saying("Preview options moved to " + PREVIEW + "<action>: " + ACTIONS));
        verify(player).sendMessage(saying("No preview action bogus. Try " + ACTIONS));
    }

    /** Completion offers only shapes after build, the groups after a shape, and -all after preview clear. */
    @Test
    void completionOffersClearTheShapesAndTheirGroups()
    {
        final SubCommands.Entry gate = SubCommands.find("gate");

        final List<String> third = gate.completeArgs(player, new String[] { "gate", "build", "" });
        assertTrue(third.contains("Standard") && !third.contains("clear"), "got " + third);
        assertEquals(List.of("-all"), gate.completeArgs(player, new String[] { "gate", "preview", "clear", "" }));
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
            previews.when(() -> GatePreviews.guide(player)).thenReturn(GatePreviews.Control.GUIDE_ON);
            previews.when(() -> GatePreviews.material(any(Player.class), any(MaterialGroup.class)))
                .thenReturn(GatePreviews.Control.CHANGED);
            previews.when(() -> GatePreviews.material(any(Player.class), any(com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role.class),
                any(org.bukkit.Material.class))).thenReturn(GatePreviews.Control.NOT_LOOKING);

            run("gate", "preview", "activate");
            run("gate", "preview", "IRIS");
            run("gate", "preview", "dhd");
            run("gate", "preview", "chevrons");
            run("gate", "preview", "guide");
            run("gate", "preview", "material", "atlantis");
            run("gate", "preview", "material", "frame", "gold_block");

            previews.verify(() -> GatePreviews.material(eq(player),
                argThat((MaterialGroup group) -> "Atlantis".equals(group.getName()))));
            previews.verify(() -> GatePreviews.material(player, com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role.FRAME,
                org.bukkit.Material.GOLD_BLOCK));
        }
        verify(player).sendMessage(saying("Dialling."));
        verify(player).sendMessage(saying("Iris closed."));
        verify(player).sendMessage(saying("DHD hidden."));
        verify(player).sendMessage(saying("Chevrons shown as frame"));
        verify(player).sendMessage(saying("Guide on"));
        verify(player).sendMessage(contains("\u00A7f/wormhole gate preview activate\u00A77 again shuts it down"));
        verify(player).sendMessage(saying("Materials changed."));
        verify(player).sendMessage(saying("Look at one of your previews first."));
    }

    /**
     * -materials lists each material with what is left of it, says what is in the opening, and warns
     * when no group would find a gate built in that frame.
     */
    @Test
    void materialsListsWhatIsLeftAndWarnsOfAFrameNoGroupFinds()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.materials(player)).thenReturn(
                new GatePreviews.Materials("Standard", List.of(new com.wormhole_xtreme.wormhole.model.preview.BuildGuide.Need(
                    "obsidian", 18, 4), new com.wormhole_xtreme.wormhole.model.preview.BuildGuide.Need("button or lever", 1, 0)),
                    2, false, org.bukkit.Material.GOLD_BLOCK),
                (GatePreviews.Materials) null);

            run("gate", "preview", "materials");
            run("gate", "preview", "MATERIALS");
        }
        verify(player).sendMessage(saying("Standard needs:"));
        verify(player).sendMessage(saying("18 obsidian - 4 left"));
        verify(player).sendMessage(saying("1 button or lever - done"));
        verify(player).sendMessage(contains("18 \u00A7eobsidian\u00A77 - 4 left"));
        verify(player).sendMessage(contains("\u00A7ebutton\u00A77 or \u00A7elever\u00A77 - \u00A7adone\u00A77"));
        verify(player).sendMessage(saying("2 in the way in the opening"));
        verify(player).sendMessage(saying("No material group uses gold_block for a frame"));
        verify(player).sendMessage(saying("Look at one of your previews first."));
    }

    /** -layer takes a number, -next or -all, and says which layers are shown. */
    @Test
    void layerTakesANumberNextOrAll()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.layers(player, GatePreviews.NEXT_LAYER))
                .thenReturn(new GatePreviews.Layers(1, 4, true));
            previews.when(() -> GatePreviews.layers(player, 3)).thenReturn(new GatePreviews.Layers(3, 4, true));
            previews.when(() -> GatePreviews.layers(player, 9)).thenReturn(new GatePreviews.Layers(3, 4, false));
            previews.when(() -> GatePreviews.layers(player, GatePreviews.ALL_LAYERS))
                .thenReturn(new GatePreviews.Layers(0, 4, true));

            run("gate", "preview", "layer");
            run("gate", "preview", "layer", "-NEXT");
            run("gate", "preview", "layer", "3");
            run("gate", "preview", "layer", "9");
            run("gate", "preview", "layer", "-all");
            run("gate", "preview", "layer", "0");
            run("gate", "preview", "layer", "top");

            previews.verify(() -> GatePreviews.layers(player, GatePreviews.NEXT_LAYER), times(2));
            previews.verify(() -> GatePreviews.layers(eq(player), org.mockito.ArgumentMatchers.intThat(n -> n < -1)),
                never());
        }
        verify(player, times(2)).sendMessage(saying("Showing layer 1 of 4."));
        verify(player).sendMessage(saying("Showing layer 1-3 of 4."));
        verify(player).sendMessage(saying("It has only 4 layers."));
        verify(player).sendMessage(saying("Showing all 4 layers."));
        verify(player, times(2)).sendMessage(saying(PREVIEW + "layer [number|-next|-all]"));
        assertEquals(List.of("-next", "-all"),
            SubCommands.find("gate").completeArgs(player, new String[] { "gate", "preview", "layer", "" }));
    }

    /**
     * Looking at a button on the side of a block stands the preview on it, as the DHD; one on a floor,
     * or no button, stands it in front of the player as usual.
     */
    @Test
    void lookingAtAPlacedDhdButtonStandsThePreviewOnIt()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        final org.bukkit.block.Block onWall = button(org.bukkit.block.data.FaceAttachable.AttachedFace.WALL);
        final org.bukkit.block.Block onFloor = button(org.bukkit.block.data.FaceAttachable.AttachedFace.FLOOR);
        when(player.getTargetBlockExact(6)).thenReturn(onWall, onFloor);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.showOn(any(), any(), any(), any(), any())).thenReturn(GatePreviews.Shown.SHOWN);
            previews.when(() -> GatePreviews.show(any(), any(), any())).thenReturn(GatePreviews.Shown.SHOWN);

            run("gate", "build", "Standard");
            run("gate", "build", "Standard");

            previews.verify(() -> GatePreviews.showOn(eq(player), eq(standard), any(MaterialGroup.class), eq(onWall),
                eq(org.bukkit.block.BlockFace.EAST)));
            previews.verify(() -> GatePreviews.show(eq(player), eq(standard), any(MaterialGroup.class)));
        }
        verify(player).sendMessage(saying("on your DHD"));
    }

    private static org.bukkit.block.Block button(final org.bukkit.block.data.FaceAttachable.AttachedFace face)
    {
        final org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
        final org.bukkit.block.data.type.Switch data = mock(org.bukkit.block.data.type.Switch.class);
        when(data.getAttachedFace()).thenReturn(face);
        when(data.getFacing()).thenReturn(org.bukkit.block.BlockFace.EAST);
        when(block.getType()).thenReturn(org.bukkit.Material.OAK_BUTTON);
        when(block.getBlockData()).thenReturn(data);
        return block;
    }

    /** -place needs its own node on top of the preview node, and hands a placed gate on as a pressed button does. */
    @Test
    void placeNeedsItsNodeAndHandsTheGateOnToBeNamed()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        final com.wormhole_xtreme.wormhole.model.Stargate gate = mock(com.wormhole_xtreme.wormhole.model.Stargate.class);
        when(gate.getGateShape()).thenReturn(standard);
        final org.bukkit.block.Block button = mock(org.bukkit.block.Block.class);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class);
            MockedStatic<com.wormhole_xtreme.wormhole.GateInteractionHandler> handler =
                mockStatic(com.wormhole_xtreme.wormhole.GateInteractionHandler.class))
        {
            previews.when(() -> GatePreviews.place(eq(player), anyBoolean())).thenReturn(
                new GatePreviews.Placed(GatePreviews.Outcome.IN_THE_WAY, List.of("stone at 1 2 3", "4 more"), null, null),
                new GatePreviews.Placed(GatePreviews.Outcome.PLACED, List.of(), gate, button));

            run("gate", "preview", "place");
            previews.verify(() -> GatePreviews.place(any(), anyBoolean()), never());
            verify(player).sendMessage(saying("You lack the permissions"));

            when(player.hasPermission("wormhole.build.preview.place")).thenReturn(true);
            run("gate", "preview", "place");
            run("gate", "preview", "PLACE");

            handler.verify(() -> com.wormhole_xtreme.wormhole.GateInteractionHandler.offerNewGate(player, button, gate));
        }
        verify(player).sendMessage(saying("Nothing placed. In the way: stone at 1 2 3, 4 more. "
            + PREVIEW + "guide marks them."));
        verify(player).sendMessage(saying("Placed Standard."));
    }

    /**
     * -share needs its own node, shows the preview to a named player and tells them, shares with
     * everyone with -all, and lists who sees it with nothing after it.
     */
    @Test
    void shareNeedsItsNodeAndSaysWhoSeesIt()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        final org.bukkit.Server server = mock(org.bukkit.Server.class);
        when(player.getServer()).thenReturn(server);
        final Player alex = mock(Player.class);
        when(alex.getName()).thenReturn("Alex");
        when(server.getPlayerExact("Alex")).thenReturn(alex);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.share(player, alex)).thenReturn(GatePreviews.Shared.SHARED,
                GatePreviews.Shared.UNSHARED);
            previews.when(() -> GatePreviews.shareAll(player)).thenReturn(GatePreviews.Shared.SHARED_ALL);
            previews.when(() -> GatePreviews.audience(player)).thenReturn(
                new GatePreviews.Audience(true, List.of("Alex")), new GatePreviews.Audience(false, List.of()));

            run("gate", "preview", "share", "Alex");
            previews.verify(() -> GatePreviews.share(any(), any()), never());
            verify(player).sendMessage(saying("You lack the permissions"));

            when(player.hasPermission("wormhole.build.preview.share")).thenReturn(true);
            run("gate", "preview", "share", "Alex");
            run("gate", "preview", "SHARE", "Alex");
            run("gate", "preview", "share", "-all");
            run("gate", "preview", "share", "Nobody");
            run("gate", "preview", "share");
            run("gate", "preview", "share");
        }
        verify(player).sendMessage(saying("Showing it to Alex. " + PREVIEW + "share Alex again stops."));
        verify(alex).sendMessage(saying("builder is showing you a gate preview."));
        verify(player).sendMessage(saying("Stopped showing it to Alex."));
        verify(player).sendMessage(saying("Showing it to everyone in this world. "
            + PREVIEW + "share -all again stops."));
        verify(player).sendMessage(saying("No player called Nobody is online."));
        verify(player).sendMessage(saying("Shown to everyone in this world, Alex."));
        verify(player).sendMessage(saying("Only you see it. " + PREVIEW + "share <player> or "
            + PREVIEW + "share -all shows it."));
        assertEquals(List.of("-all"),
            SubCommands.find("gate").completeArgs(player, new String[] { "gate", "preview", "share", "-" }));
    }

    /** -material names what it takes when given something else, and refuses a block that does not exist. */
    @Test
    void materialSaysWhatItTakes()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            run("gate", "preview", "material", "sparkly");
            run("gate", "preview", "material", "frame", "unobtainium");

            previews.verify(() -> GatePreviews.material(any(Player.class),
                any(com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role.class), any(org.bukkit.Material.class)), never());
        }
        verify(player).sendMessage(saying(PREVIEW + "material <role> <block>. "
            + "Roles: frame, chevron, light, portal, iris, sign."));
        verify(player).sendMessage(saying("That is not a block."));
    }

    /** The controls are the preview node's; an admin without it may clear, and nothing else. */
    @Test
    void theControlsNeedThePreviewNode()
    {
        when(player.hasPermission("wormhole.config")).thenReturn(true);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            run("gate", "preview", "activate");

            previews.verify(() -> GatePreviews.activate(any()), never());
        }
        verify(player).sendMessage(saying(NO_PERMISSION));
    }

    /** Completion offers the actions after preview, the groups and roles after material, and blocks after a role. */
    @Test
    void completionOffersTheOptionsAndWhatMaterialTakes()
    {
        final SubCommands.Entry gate = SubCommands.find("gate");

        assertEquals(List.of(ACTIONS.split(" ")),
            gate.completeArgs(player, new String[] { "gate", "preview", "" }));
        assertTrue(gate.completeArgs(player, new String[] { "gate", "" }).contains("preview"));
        assertTrue(gate.completeArgs(player, new String[] { "gate", "preview", "material", "" })
            .containsAll(List.of("Atlantis", "Standard", "frame", "iris")));
        assertTrue(gate.completeArgs(player, new String[] { "gate", "preview", "material", "frame", "gold_b" })
            .contains("gold_block"));
        assertTrue(gate.completeArgs(player, new String[] { "gate", "preview", "material", "frame", "" }).isEmpty(),
            "every block at once is not a list anybody reads");
    }

    /** A preview placed over a gate already there regenerates that gate instead of naming a new one. */
    @Test
    void placeOverAGateRegeneratesItRatherThanOfferingANewOne()
    {
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        when(player.hasPermission("wormhole.build.preview.place")).thenReturn(true);
        final com.wormhole_xtreme.wormhole.model.Stargate gate = mock(com.wormhole_xtreme.wormhole.model.Stargate.class);
        when(gate.getGateName()).thenReturn("Lithium");
        final org.bukkit.block.Block button = mock(org.bukkit.block.Block.class);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class);
            MockedStatic<com.wormhole_xtreme.wormhole.command.handlers.RegenerateCommand> regen =
                mockStatic(com.wormhole_xtreme.wormhole.command.handlers.RegenerateCommand.class);
            MockedStatic<com.wormhole_xtreme.wormhole.GateInteractionHandler> handler =
                mockStatic(com.wormhole_xtreme.wormhole.GateInteractionHandler.class))
        {
            previews.when(() -> GatePreviews.place(eq(player), anyBoolean())).thenReturn(
                new GatePreviews.Placed(GatePreviews.Outcome.REPAIRED, List.of(), gate, button));

            run("gate", "preview", "place");

            regen.verify(() -> com.wormhole_xtreme.wormhole.command.handlers.RegenerateCommand.regenerateAt(player,
                gate, button, null, false));
            handler.verify(() -> com.wormhole_xtreme.wormhole.GateInteractionHandler.offerNewGate(any(), any(), any()),
                never());
        }
        verify(player).sendMessage(saying("Filled in Lithium's missing blocks."));
    }

}
