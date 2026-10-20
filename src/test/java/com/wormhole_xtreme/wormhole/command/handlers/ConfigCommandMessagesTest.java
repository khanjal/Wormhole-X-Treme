package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@code /wormhole config} says when it sets a value, finds no setting or lists them, read by
 * key from {@code lang/} (#26).
 *
 * <p>Each line goes through the real command twice: to an English admin, against the concatenation
 * the code built before the move, copied from it, and to an admin whose client reads the test
 * language qq, so a line built in the server's language instead of the reader's shows as English.
 * A change applied at once and one that waits for a restart, and a list short enough to show whole
 * and one cut short with its tail, are covered in both.
 */
class ConfigCommandMessagesTest
{
    /** How many names the list shows before its tail; ConfigCommand's own limit. */
    private static final int SHOWN = 30;

    private static final String NOWHERE = "no-such-setting";

    private static final String NARROW = "RING_DEFAULT";

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.loadDefaults();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private static void run(final Player player, final String... words)
    {
        final String[] args = new String[words.length + 1];
        args[0] = "config";
        System.arraycopy(words, 0, args, 1, words.length);
        new ConfigCommand().execute(player, args);
    }

    /** Turns the economy on, its attaching failing as it would with no Vault. */
    private static void setEconomyWithoutVault(final Player player)
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            economy.when(EconomySupport::enableEconomy).thenThrow(new IllegalStateException("no Vault"));
            run(player, "economy-enabled", "true");
        }
    }

    /** The names as the code listed them before the move: the first thirty, white, comma separated. */
    private static String oldList(final List<String> names)
    {
        final StringBuilder found = new StringBuilder();
        for (int i = 0; (i < names.size()) && (i < SHOWN); i++)
        {
            found.append(found.isEmpty() ? "" : ", ").append(ChatText.command(names.get(i)));
        }
        return found.toString();
    }

    private static String qqList(final List<String> names)
    {
        return names.stream().limit(SHOWN).map(ChatText::command).collect(Collectors.joining(" | "));
    }

    /** A value taken, applied at once or waiting for a restart, and a name that is no setting, as before. */
    @Test
    void settingLinesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        run(player, "gate-sounds-enabled", "false");
        setEconomyWithoutVault(player);
        run(player, NOWHERE, "1");

        verify(player).sendMessage(ChatText.info("GATE_SOUNDS_ENABLED" + " is now " + false + "."));
        verify(player).sendMessage(ChatText.info("ECONOMY_ENABLED" + " is now " + true
            + ", saved, but it could not be applied now: " + new IllegalStateException("no Vault")
            + ". It applies at the next restart."));
        verify(player).sendMessage(ChatText.error("No setting called " + ChatText.command(NOWHERE) + "."));
        verify(player).sendMessage(ChatText.error("No setting matches " + ChatText.command(NOWHERE) + "."));
    }

    /** The same lines reach a qq admin in qq, the setting and its value unchanged. */
    @Test
    void settingLinesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        run(player, "gate-sounds-enabled", "false");
        setEconomyWithoutVault(player);
        run(player, NOWHERE, "1");

        verify(player).sendMessage(ChatText.info("QQ GATE_SOUNDS_ENABLED now false."));
        verify(player).sendMessage(ChatText.info("QQ ECONOMY_ENABLED now true unapplied "
            + new IllegalStateException("no Vault") + "."));
        verify(player).sendMessage(ChatText.error("QQ no setting " + ChatText.command(NOWHERE) + "."));
        verify(player).sendMessage(ChatText.error("QQ no match " + ChatText.command(NOWHERE) + "."));
    }

    /** Every setting is far more than thirty, so the list is cut short and says how many it left out. */
    @Test
    void theWholeListIsCutShortWithItsTailAsBeforeAndInTheReadersLanguage()
    {
        final List<String> names = ConfigManager.settingNamesMatching("");
        assertTrue(names.size() > SHOWN + 1, "more settings than the list shows: " + names.size());
        final String left = ChatText.value(String.valueOf(names.size() - SHOWN));
        final Player english = reading("en");
        final Player qq = reading("qq");
        run(english);
        run(qq);

        verify(english).sendMessage(ChatText.info(oldList(names)));
        verify(english).sendMessage(ChatText.info("...and " + left + " more. Type part of a name to narrow it down."));
        verify(qq).sendMessage(ChatText.info(qqList(names)));
        verify(qq).sendMessage(ChatText.info("QQ " + left + " more."));
    }

    /** A search with few matches lists them all, with no tail, in either language. */
    @Test
    void aShortListIsShownWholeWithNoTail()
    {
        final List<String> names = ConfigManager.settingNamesMatching(NARROW);
        assertTrue((names.size() > 1) && (names.size() <= SHOWN), "a few ring defaults: " + names);
        final Player english = reading("en");
        final Player qq = reading("qq");
        run(english, NARROW);
        run(qq, NARROW);

        verify(english).sendMessage(ChatText.info(oldList(names)));
        verify(qq).sendMessage(ChatText.info(qqList(names)));
        // The list and the two usage lines: no tail.
        verify(english, times(3)).sendMessage(anyString());
        verify(qq, times(3)).sendMessage(anyString());
    }
}
