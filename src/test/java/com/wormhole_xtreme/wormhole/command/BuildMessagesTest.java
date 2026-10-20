package com.wormhole_xtreme.wormhole.command;

import static com.wormhole_xtreme.wormhole.utils.ChatText.bad;
import static com.wormhole_xtreme.wormhole.utils.ChatText.error;
import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static com.wormhole_xtreme.wormhole.utils.ChatText.name;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;

/**
 * What {@code gate build} and the preview's one-word controls say, read by key from {@code lang/} (#26).
 *
 * <p>Each line goes through the real command twice: to an English builder, against the text the code
 * built before the move, copied from it, so a reworded template, a swapped argument or a lost
 * conditional shows as a changed line; and to a builder whose client reads the test language qq,
 * so a call site that reads the server's language, or nobody's, instead of the reader's shows as English.
 * Lines that end in a list of actions read as the one string they were before links existed.
 */
class BuildMessagesTest extends BuildPreviewCase
{
    private static final String STANDARD = "Standard";
    private static final String ATLANTIS = "Atlantis";
    private static final String ODD = "Odd";

    /** A shape that takes no group the server has, so it is shown in none. */
    private static Stargate3DShape oddShape()
    {
        final Stargate3DShape odd = mock(Stargate3DShape.class);
        when(odd.getShapeName()).thenReturn(ODD);
        when(odd.acceptsMaterialGroup(any())).thenReturn(false);
        StargateShapeRegistry.getStargateShapes().put(ODD, odd);
        return odd;
    }

    private static Block wallButton()
    {
        final Block block = mock(Block.class);
        final Switch data = mock(Switch.class);
        when(data.getAttachedFace()).thenReturn(FaceAttachable.AttachedFace.WALL);
        when(data.getFacing()).thenReturn(BlockFace.EAST);
        when(block.getType()).thenReturn(Material.OAK_BUTTON);
        when(block.getBlockData()).thenReturn(data);
        return block;
    }

    /** The refusals of {@code gate build}, then a shape turned off, for a reader with the preview node. */
    private void refusals(final Player player)
    {
        run(player, "gate", "build", "-guide");
        run(player, "gate", "build", "Nope");
        run(player, "gate", "build", STANDARD, "Nowhere");
        assertTrue(new File(tempDir, "shapes" + File.separator + "gate").mkdirs());
        StargateShapeRegistry.setEnabled(STANDARD, false);
        run(player, "gate", "build", STANDARD);
        StargateShapeRegistry.setEnabled(STANDARD, true);
    }

    /** Each refusal as it read before, and the list of groups to try as it listed them. */
    @Test
    void theBuildRefusalsReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        refusals(player);

        verify(player).sendMessage(error("Preview options moved to ") + listed());
        verify(player).sendMessage(error("No shape called " + name("Nope") + "."));
        verify(player).sendMessage(error(name(STANDARD) + " has no group " + name("Nowhere") + ". Try "
            + name(ATLANTIS) + ", " + name(STANDARD) + "."));
        verify(player).sendMessage(error(name(STANDARD) + " is turned off on this server."));
    }

    /** The same refusals reach a qq builder in qq, the list of groups joined as qq joins it. */
    @Test
    void theBuildRefusalsAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        refusals(player);

        verify(player).sendMessage(error("QQ moved ") + listed());
        verify(player).sendMessage(error("QQ build no shape " + name("Nope") + "."));
        verify(player).sendMessage(error("QQ " + name(STANDARD) + " no group " + name("Nowhere") + " try "
            + name(ATLANTIS) + " | " + name(STANDARD) + "."));
        verify(player).sendMessage(error("QQ build shape off " + name(STANDARD) + "."));
    }

    /** A builder without the preview node is only told which shape is chosen. */
    @Test
    void choosingAShapeWithoutAPreviewReadsAsBeforeAndInTheReadersLanguage()
    {
        final Player english = readingWithoutPreview("en");
        final Player qq = readingWithoutPreview("qq");
        run(english, "gate", "build", STANDARD);
        run(qq, "gate", "build", STANDARD);

        verify(english).sendMessage(info("Building " + name(STANDARD) + ". Press the button on its DHD when it is done."));
        verify(qq).sendMessage(info("QQ building " + name(STANDARD) + "."));
        StargateManager.forgetPlayer(english);
        StargateManager.forgetPlayer(qq);
    }

    /** Looks at nothing, a wall button, nothing and a wall button again, building each shape. */
    private void showBoth(final Player player, final MockedStatic<GatePreviews> previews)
    {
        previews.when(() -> GatePreviews.show(any(), any(), any())).thenReturn(GatePreviews.Shown.SHOWN);
        previews.when(() -> GatePreviews.showOn(any(), any(), any(), any(), any())).thenReturn(GatePreviews.Shown.SHOWN);
        final Block wall = wallButton();
        when(player.getTargetBlockExact(6)).thenReturn(null, wall, null, wall);
        run(player, "gate", "build", STANDARD);
        run(player, "gate", "build", STANDARD);
        run(player, "gate", "build", ODD);
        run(player, "gate", "build", ODD);
    }

    /** The shown preview, in a group or in none, free-standing or on a DHD, then its action list. */
    @Test
    void aShownPreviewReadsAsItDidBeforeForEachGroupAndDhdVariant()
    {
        oddShape();
        final Player player = reading("en");
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            showBoth(player, previews);
        }

        verify(player).sendMessage(info("Previewing " + name(STANDARD) + " in " + name(STANDARD)
            + ". Build inside it, then press a real button on its DHD."));
        verify(player).sendMessage(info("Previewing " + name(STANDARD) + " in " + name(STANDARD)
            + " on your DHD. Finish it, then press the button."));
        verify(player).sendMessage(info("Previewing " + name(ODD) + ". Build inside it, then press a real button on its DHD."));
        verify(player).sendMessage(info("Previewing " + name(ODD) + " on your DHD. Finish it, then press the button."));
        verify(player, times(4)).sendMessage(info("Look at it and use ") + listed());
    }

    /** The same four lines reach a qq builder in qq. */
    @Test
    void aShownPreviewIsInTheReadersLanguageForEachGroupAndDhdVariant()
    {
        oddShape();
        final Player player = reading("qq");
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            showBoth(player, previews);
        }

        verify(player).sendMessage(info("QQ showing " + name(STANDARD) + " group " + name(STANDARD) + "."));
        verify(player).sendMessage(info("QQ showing " + name(STANDARD) + " group " + name(STANDARD) + " on dhd."));
        verify(player).sendMessage(info("QQ showing " + name(ODD) + "."));
        verify(player).sendMessage(info("QQ showing " + name(ODD) + " on dhd."));
        verify(player, times(4)).sendMessage(info("QQ look ") + listed());
    }

    /** A shape with no DHD, a limit of none and a limit reached, each as before. */
    @Test
    void theShapeWithNoDhdAndTheLimitsReadAsBefore()
    {
        final Player player = reading("en");
        previewOutcomes(player);

        verify(player).sendMessage(error(name(STANDARD) + " has no DHD, so it cannot be previewed."));
        verify(player).sendMessage(error("Previews are off on this server."));
        verify(player).sendMessage(error("Too many preview blocks on the server. Clear one with ") + hint("clear") + ".");
    }

    /** The same three lines reach a qq builder in qq, the link between them kept. */
    @Test
    void theShapeWithNoDhdAndTheLimitsAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        previewOutcomes(player);

        verify(player).sendMessage(error("QQ " + name(STANDARD) + " no dhd."));
        verify(player).sendMessage(error("QQ previews off."));
        verify(player).sendMessage(error("QQ too many ") + hint("clear") + ".");
    }

    private void previewOutcomes(final Player player)
    {
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.show(any(), any(), any())).thenReturn(GatePreviews.Shown.NO_DHD,
                GatePreviews.Shown.OVER_LIMIT, GatePreviews.Shown.OVER_LIMIT);
            run(player, "gate", "build", STANDARD);
            ConfigTestSupport.set(ConfigManager.ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, 0);
            run(player, "gate", "build", STANDARD);
            ConfigTestSupport.set(ConfigManager.ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, 5);
            run(player, "gate", "build", STANDARD);
        }
    }

    /** Each control's reply twice, so both of its outcomes are heard, and one block that is not. */
    private void controls(final Player player, final MockedStatic<GatePreviews> previews)
    {
        previews.when(() -> GatePreviews.activate(any())).thenReturn(GatePreviews.Control.DIALLING,
            GatePreviews.Control.SHUT_DOWN);
        previews.when(() -> GatePreviews.iris(any())).thenReturn(GatePreviews.Control.IRIS_CLOSED,
            GatePreviews.Control.IRIS_OPENED);
        previews.when(() -> GatePreviews.toggleDhd(any())).thenReturn(GatePreviews.Control.DHD_HIDDEN,
            GatePreviews.Control.DHD_SHOWN);
        previews.when(() -> GatePreviews.toggleChevrons(any())).thenReturn(GatePreviews.Control.CHEVRONS_PLAIN,
            GatePreviews.Control.CHEVRONS_SHOWN);
        previews.when(() -> GatePreviews.guide(any())).thenReturn(GatePreviews.Control.GUIDE_ON,
            GatePreviews.Control.GUIDE_OFF);
        previews.when(() -> GatePreviews.material(any(Player.class), any(MaterialGroup.class)))
            .thenReturn(GatePreviews.Control.CHANGED, GatePreviews.Control.NOT_IN_GROUP);
        previews.when(() -> GatePreviews.material(any(Player.class), any(GateBlueprint.Role.class), any(Material.class)))
            .thenReturn(GatePreviews.Control.NOT_LOOKING);
        for (final String action : new String[] {"activate", "iris", "dhd", "chevrons", "guide"})
        {
            run(player, "gate", "preview", action);
            run(player, "gate", "preview", action);
        }
        run(player, "gate", "preview", "material", "atlantis");
        run(player, "gate", "preview", "material", "standard");
        run(player, "gate", "preview", "material", "frame", "gold_block");
        run(player, "gate", "preview", "material", "frame", "unobtainium");
    }

    /** Every reply to a control, with the command it names, and the three that refuse under the error header. */
    @Test
    void theControlRepliesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            controls(player, previews);
        }

        verify(player).sendMessage(info("Dialling. " + hint("activate") + " again shuts it down."));
        verify(player).sendMessage(info("Shut down."));
        verify(player).sendMessage(info("Iris closed. " + hint("iris") + " opens it."));
        verify(player).sendMessage(info("Iris open."));
        verify(player).sendMessage(info("DHD hidden. " + hint("dhd") + " shows it."));
        verify(player).sendMessage(info("DHD shown."));
        verify(player).sendMessage(info("Chevrons shown as frame. " + hint("chevrons") + " undoes it."));
        verify(player).sendMessage(info("Chevrons shown."));
        verify(player).sendMessage(info("Guide on: small = to place, " + bad("red") + " = wrong, gone = done. "
            + hint("guide") + " turns it off."));
        verify(player).sendMessage(info("Guide off."));
        verify(player).sendMessage(info("Materials changed."));
        verify(player).sendMessage(error("This shape cannot use that group."));
        verify(player).sendMessage(error("That is not a block."));
        verify(player).sendMessage(error("Look at one of your previews first."));
    }

    /** The same fourteen replies reach a qq builder in qq, under the same headers. */
    @Test
    void theControlRepliesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            controls(player, previews);
        }

        verify(player).sendMessage(info("QQ dialling " + hint("activate") + "."));
        verify(player).sendMessage(info("QQ shut down."));
        verify(player).sendMessage(info("QQ iris closed " + hint("iris") + "."));
        verify(player).sendMessage(info("QQ iris open."));
        verify(player).sendMessage(info("QQ dhd hidden " + hint("dhd") + "."));
        verify(player).sendMessage(info("QQ dhd shown."));
        verify(player).sendMessage(info("QQ chevrons plain " + hint("chevrons") + "."));
        verify(player).sendMessage(info("QQ chevrons shown."));
        verify(player).sendMessage(info("QQ guide on " + bad("QQred") + " " + hint("guide") + "."));
        verify(player).sendMessage(info("QQ guide off."));
        verify(player).sendMessage(info("QQ changed."));
        verify(player).sendMessage(error("QQ not in group."));
        verify(player).sendMessage(error("QQ not a block."));
        verify(player).sendMessage(error("QQ not looking."));
    }

    /** No arguments and an unknown action, each ending in the list of actions. */
    @Test
    void theUsageAndTheUnknownActionEndInTheActionsAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qq = reading("qq");
        for (final Player player : new Player[] {english, qq})
        {
            run(player, "gate", "preview");
            run(player, "gate", "preview", "bogus");
        }

        verify(english).sendMessage(info("Usage: ") + listed());
        verify(english).sendMessage(error("No preview action " + name("bogus") + ". Try ") + actions());
        verify(qq).sendMessage(info("Usage: ") + listed());
        verify(qq).sendMessage(error("QQ no action " + name("bogus") + " ") + actions());
    }
}
