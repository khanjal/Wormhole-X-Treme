package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;
import com.wormhole_xtreme.wormhole.utils.ChatText;

import net.kyori.adventure.audience.Audience;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

/**
 * After {@code gate build}, each preview action is a button to click rather than a word to read
 * and type, and a refusal that names a command links it (#538).
 *
 * <p>Read through Spigot's chat, the one a mocked player reaches; {@code ChatLineTest} covers how
 * the same line goes on Paper and on a server with neither.
 */
class GatePreviewLinksTest
{
    private static final String PREVIEW = Build.PREVIEW_COMMAND;

    private final Wormhole command = new Wormhole();
    private Player player;
    private Player.Spigot spigot;

    @BeforeEach
    void setUp() throws Exception
    {
        assumeFalse(Audience.class.isAssignableFrom(Player.class), "Paper sends Adventure, not BungeeCord chat");
        PluginTestSupport.install(mock(WormholeXTreme.class));
        StargateShapeRegistry.getStargateShapes().put("Standard", new Stargate3DShape(Files.readAllLines(
            Paths.get("src/main/resources/shapes/gate/Standard.shape")).toArray(new String[0])));
        final Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("Standard", Map.of("structure", "OBSIDIAN", "chevron", "REDSTONE_LAMP"));
        MaterialGroupRegistry.load(groups);

        player = mock(Player.class);
        spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        when(player.getName()).thenReturn("builder");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(anyString())).thenReturn(false);
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        if (player != null)
        {
            StargateManager.forgetPlayer(player);
        }
        StargateShapeRegistry.getStargateShapes().remove("Standard");
        MaterialGroupRegistry.load(null);
        PluginTestSupport.remove();
    }

    /** Every piece of every line sent through Spigot's chat. */
    private List<BaseComponent> sent()
    {
        final ArgumentCaptor<BaseComponent> lines = ArgumentCaptor.forClass(BaseComponent.class);
        verify(spigot, atLeastOnce()).sendMessage(lines.capture());
        final List<BaseComponent> pieces = new ArrayList<>();
        lines.getAllValues().forEach(line -> pieces.addAll(line.getExtra()));
        return pieces;
    }

    /** The piece a click on which does this, or a failure naming what was there instead. */
    private BaseComponent clicking(final ClickEvent.Action action, final String command)
    {
        final ClickEvent wanted = new ClickEvent(action, command);
        final List<BaseComponent> pieces = sent();
        return pieces.stream().filter(piece -> wanted.equals(piece.getClickEvent())).findFirst()
            .orElseThrow(() -> new AssertionError("no link to " + action + " " + command + " in "
                + pieces.stream().map(BaseComponent::getClickEvent).toList()));
    }

    /**
     * The preview's line of actions is a button per action, each running its own command, and
     * {@code material}, which does nothing without a group or a role after it, filling the chat
     * box in instead. Each says what it does when pointed at.
     */
    @Test
    void afterGateBuildEveryPreviewActionIsAButton()
    {
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.show(any(), any(), any())).thenReturn(GatePreviews.Shown.SHOWN);

            command.onCommand(player, null, "wormhole", new String[] {"gate", "build", "Standard"});
        }
        assertEquals(ChatText.command("[Place]"),
            ((TextComponent) clicking(ClickEvent.Action.RUN_COMMAND, PREVIEW + "place")).getText().substring(2),
            "after the body colour carried into it");
        for (final String action : Build.ACTIONS)
        {
            final BaseComponent button = Build.MATERIAL.equals(action)
                ? clicking(ClickEvent.Action.SUGGEST_COMMAND, PREVIEW + action + " ")
                : clicking(ClickEvent.Action.RUN_COMMAND, PREVIEW + action);
            final String hover = (String) ((Text) button.getHoverEvent().getContents().get(0)).getValue();
            assertTrue(hover.indexOf('\n') > 0, action + "'s hover should put its help on a line of its own: \"" + hover + "\"");
            final String help = hover.substring(hover.indexOf('\n') + 1);
            assertTrue(help.length() > 10, action + "'s button should say what it does, not \"" + hover + "\"");
        }
    }

    /** "Look at a preview to clear it, or use ... clear -all": a click on the command clears them all. */
    @Test
    void aRefusalThatNamesACommandRunsItWhenClicked()
    {
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            command.onCommand(player, null, "wormhole", new String[] {"gate", "preview", "clear"});
        }
        clicking(ClickEvent.Action.RUN_COMMAND, PREVIEW + "clear -all");
    }

    /** A usage line names a command still to be finished, so a click types it rather than runs it. */
    @Test
    void aUsageLineFillsTheCommandIn()
    {
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            command.onCommand(player, null, "wormhole", new String[] {"gate", "preview", "layer", "nonsense"});
        }
        clicking(ClickEvent.Action.SUGGEST_COMMAND, PREVIEW + "layer ");
    }

    /** Too many preview blocks: the refusal's {@code clear} clears the one looked at. */
    @Test
    void tooManyPreviewBlocksLinksClear()
    {
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.show(any(), any(), any())).thenReturn(GatePreviews.Shown.OVER_LIMIT);

            command.onCommand(player, null, "wormhole", new String[] {"gate", "build", "Standard"});
        }
        clicking(ClickEvent.Action.RUN_COMMAND, PREVIEW + "clear");
    }
}
