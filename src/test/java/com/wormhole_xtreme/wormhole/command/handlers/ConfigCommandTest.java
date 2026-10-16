package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.errorBody;
import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.infoBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.utils.ChatMatchers;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@code /wormhole config} says, and under which header (#438).
 *
 * <p>{@code applySetting} answers a refusal and a change in the same kind of string, so the
 * command asks {@link ConfigManager#settingRefusal} first: without that a refused value would read
 * as a change, under the ordinary header, and the console lab could not tell them apart.
 */
class ConfigCommandTest
{
    private ConsoleCommandSender sender;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.loadDefaults();
        sender = mock(ConsoleCommandSender.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private void run(final String... words)
    {
        new ConfigCommand().execute(sender, words);
    }

    /** The refusal is the parser's own words, and a value it would take has none. */
    @Test
    void settingRefusalSaysWhyAWordIsRefusedAndNothingForOneTaken()
    {
        assertEquals("GATE_SOUNDS_ENABLED is true or false, not \"loud\".",
            ConfigManager.settingRefusal("gate-sounds-enabled", "loud"));
        assertNull(ConfigManager.settingRefusal("gate-sounds-enabled", "false"), "false is a switch's value");
        assertNull(ConfigManager.settingRefusal("no-such-setting", "1"), "no setting, nothing to refuse it with");
    }

    /** A refused value is an error, with the parser's words, and the setting keeps its value. */
    @Test
    void aRefusedValueIsAnErrorAndChangesNothing()
    {
        final String before = ConfigManager.describeSetting("gate-sounds-enabled");

        run("config", "gate-sounds-enabled", "loud");

        verify(sender).sendMessage(errorBody("GATE_SOUNDS_ENABLED is true or false, not \"loud\"."));
        verify(sender, never()).sendMessage(infoBody("GATE_SOUNDS_ENABLED is now loud."));
        assertEquals(before, ConfigManager.describeSetting("gate-sounds-enabled"), "the value stands");
    }

    /** A value taken is said under the ordinary header. */
    @Test
    void aValueTakenIsSaidAsAChange()
    {
        run("config", "gate-sounds-enabled", "false");

        verify(sender).sendMessage(infoBody("GATE_SOUNDS_ENABLED is now false."));
    }

    /** A name that is no setting is an error, and the search that follows finds nothing. */
    @Test
    void anUnknownSettingIsAnError()
    {
        run("config", "no-such-setting", "1");

        verify(sender).sendMessage(errorBody("No setting called no-such-setting."));
        verify(sender).sendMessage(errorBody("No setting matches no-such-setting."));
    }

    /**
     * Describing a setting sends its value and what it is for as two lines, each with the header.
     *
     * <p>{@code describeSetting} joins them with a line separator; sent as one message, the second
     * line arrived with no header and its own colour.
     */
    @Test
    void describingASettingSendsEachLineUnderTheHeader()
    {
        run("config", "gate-sounds-enabled");

        final ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(sent.capture());
        final List<String> lines = sent.getAllValues();
        assertEquals(2, lines.size(), "the value and what it is for: " + lines);
        for (final String line : lines)
        {
            assertTrue(line.startsWith(ChatText.HEADER), "each line opens with the header: " + line);
            assertFalse(line.contains("\n"), "no line carries another: " + line);
        }
        assertTrue(ChatMatchers.body(lines.get(0)).startsWith("GATE_SOUNDS_ENABLED = "), lines.get(0));
        assertFalse(ChatMatchers.body(lines.get(1)).isBlank(), "what it is for: " + lines.get(1));
    }
}
