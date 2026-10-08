package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

/**
 * After {@code gate build}, each preview action is a button to click rather than a word to read
 * and type, and a refusal that names a command links it (#538).
 *
 * <p>Read through whichever chat a mocked player reaches: Adventure's where the API's Player is an
 * Audience, as on Paper, and Spigot's elsewhere, so both server families check the same buttons.
 */
class GatePreviewLinksTest
{
    private static final String PREVIEW = Build.PREVIEW_COMMAND;

    /** On Paper's API a Player is an Audience, and a line goes as Adventure components. */
    private static final boolean PAPER = Audience.class.isAssignableFrom(Player.class);

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Wormhole command = new Wormhole();
    private Player player;
    private Player.Spigot spigot;

    /**
     * A link as sent, whichever chat sent it.
     *
     * @param click
     *            its click event, Adventure's or BungeeCord's
     * @param label
     *            what the line shows, as legacy text
     * @param hover
     *            what pointing at it shows, as legacy text
     */
    private record Link(Object click, String label, String hover)
    {
    }

    @BeforeEach
    void setUp() throws Exception
    {
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

    /** Every link in every line sent, through Adventure on Paper and Spigot's chat elsewhere. */
    private List<Link> sent()
    {
        final List<Link> links = new ArrayList<>();
        if (PAPER)
        {
            final ArgumentCaptor<Component> lines = ArgumentCaptor.forClass(Component.class);
            verify((Audience) player, atLeastOnce()).sendMessage(lines.capture());
            lines.getAllValues().forEach(line -> line.children().stream().filter(bit -> bit.clickEvent() != null)
                .forEach(bit -> links.add(adventure(bit))));
        }
        else
        {
            final ArgumentCaptor<BaseComponent> lines = ArgumentCaptor.forClass(BaseComponent.class);
            verify(spigot, atLeastOnce()).sendMessage(lines.capture());
            lines.getAllValues().forEach(line -> line.getExtra().stream().filter(bit -> bit.getClickEvent() != null)
                .forEach(bit -> links.add(bungee(bit))));
        }
        return links;
    }

    /** A link sent as Adventure components. */
    private static Link adventure(final Component bit)
    {
        final String hover = (bit.hoverEvent() == null) ? "" : LEGACY.serialize((Component) bit.hoverEvent().value());
        return new Link(bit.clickEvent(), LEGACY.serialize(bit), hover);
    }

    /** A link sent as BungeeCord components. */
    private static Link bungee(final BaseComponent bit)
    {
        final String hover = (bit.getHoverEvent() == null) ? ""
            : (String) ((Text) bit.getHoverEvent().getContents().get(0)).getValue();
        return new Link(bit.getClickEvent(), ((TextComponent) bit).getText(), hover);
    }

    /** The link whose click runs this, or fills it in if {@code suggest}, or a failure listing the links. */
    private Link clicking(final boolean suggest, final String command)
    {
        final Object wanted;
        if (PAPER)
        {
            wanted = suggest ? net.kyori.adventure.text.event.ClickEvent.suggestCommand(command)
                : net.kyori.adventure.text.event.ClickEvent.runCommand(command);
        }
        else
        {
            wanted = new ClickEvent(suggest ? ClickEvent.Action.SUGGEST_COMMAND : ClickEvent.Action.RUN_COMMAND, command);
        }
        final List<Link> links = sent();
        return links.stream().filter(link -> wanted.equals(link.click())).findFirst()
            .orElseThrow(() -> new AssertionError("no link to " + wanted + " in "
                + links.stream().map(Link::click).toList()));
    }

    /** Legacy text as this chat's link label reads: Adventure drops a colour code nothing follows. */
    private static String asSent(final String legacy)
    {
        return PAPER ? LEGACY.serialize(LEGACY.deserialize(legacy)) : legacy;
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
        assertEquals(asSent(ChatText.BODY_COLOUR + ChatText.command("[Place]")), clicking(false, PREVIEW + "place").label(),
            "a button, after the body colour carried into it, not the bare word a plain line names");
        for (final String action : Build.ACTIONS)
        {
            final Link button = Build.MATERIAL.equals(action) ? clicking(true, PREVIEW + action + " ")
                : clicking(false, PREVIEW + action);
            final String hover = button.hover();
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
        clicking(false, PREVIEW + "clear -all");
    }

    /** A usage line names a command still to be finished, so a click types it rather than runs it. */
    @Test
    void aUsageLineFillsTheCommandIn()
    {
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            command.onCommand(player, null, "wormhole", new String[] {"gate", "preview", "layer", "nonsense"});
        }
        clicking(true, PREVIEW + "layer ");
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
        clicking(false, PREVIEW + "clear");
    }
}
