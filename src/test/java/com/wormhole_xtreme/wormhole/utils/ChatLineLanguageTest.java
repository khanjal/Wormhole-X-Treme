package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.File;
import java.util.List;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * The "Click to run" and "Click to fill in" hover on a clickable command is read by key, in the
 * language of the player it is sent to (#689).
 *
 * <p>The test language rr holds the two hovers; the rest of the language chain is English.
 */
class ChatLineLanguageTest
{
    private static final String COMMAND = "/wormhole gate preview clear";

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    @TempDir
    File folder;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.set(ConfigKeys.CLICKABLE_CHAT, true);
        Messages.load(Messages.DEFAULT_LANGUAGE, new File(folder, "none"));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        Messages.load(Messages.DEFAULT_LANGUAGE, new File(folder, "none"));
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private static Player reading(final String locale)
    {
        final Player player = Audience.class.isAssignableFrom(Player.class) ? mock(Player.class)
            : mock(Player.class, withSettings().extraInterfaces(Audience.class));
        when(player.getLocale()).thenReturn(locale);
        return player;
    }

    /** The text as a client reads it: the colour code a component ends on is not part of what it shows. */
    private static String asRead(final Component hover)
    {
        return LEGACY.serialize(hover);
    }

    /** The hover on the one link of the line the player was sent. */
    private static Component hoverOf(final Player player)
    {
        final ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify((Audience) player).sendMessage(sent.capture());
        final List<Component> bits = sent.getValue().children();
        final HoverEvent<?> hover = bits.get(1).hoverEvent();
        return (Component) hover.value();
    }

    /** A run link says in the reader's language what a click does, and keeps the command as typed. */
    @Test
    void theRunHoverReadsInThePlayersLanguage()
    {
        final Player rr = reading("rr");
        ChatLine.of("§7Use ").run(rr, ChatText.command(COMMAND), COMMAND).send(rr);
        assertEquals(LEGACY.serialize(LEGACY.deserialize("RR run " + ChatText.command(COMMAND))), asRead(hoverOf(rr)));

        final Player en = reading("xx");
        ChatLine.of("§7Use ").run(en, ChatText.command(COMMAND), COMMAND).send(en);
        assertEquals(LEGACY.serialize(LEGACY.deserialize("Click to run " + ChatText.command(COMMAND))), asRead(hoverOf(en)));
    }

    /** A fill-in link does the same, naming the command without its trailing space. */
    @Test
    void theFillInHoverReadsInThePlayersLanguage()
    {
        final Player rr = reading("rr");
        ChatLine.of("§7Use ").suggest(rr, ChatText.command(COMMAND), COMMAND + " ").send(rr);
        assertEquals(LEGACY.serialize(LEGACY.deserialize("RR fill " + ChatText.command(COMMAND))), asRead(hoverOf(rr)));
    }
}
