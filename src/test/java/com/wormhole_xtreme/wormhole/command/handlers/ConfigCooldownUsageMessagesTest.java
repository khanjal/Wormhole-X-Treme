package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static com.wormhole_xtreme.wormhole.utils.ChatText.usageBody;
import static com.wormhole_xtreme.wormhole.utils.ChatText.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * The explanatory lines of the {@code /wormhole config} listing and the {@code /wormhole cooldown}
 * usage, read by key from {@code lang/} (#26).
 *
 * <p>Each goes through the real command to an English admin, against the text the code built before
 * the move, written here from it, and to a reader of the test language qr, whose templates put the
 * syntax and the range in another order, so a call site that reads the server's language or nobody's,
 * or hands the range over the wrong way round, shows.
 */
class ConfigCooldownUsageMessagesTest
{
    private static final String QR = "qr";
    private static final String ENGLISH = "en";
    private static final String COOLDOWN = "cooldown";
    private static final String SECONDS_FORM = "/wormhole cooldown <seconds>";
    private static final String SWITCH_FORM = "/wormhole cooldown true|false";

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

    /** What a reader of {@code locale} is sent when {@code act} runs as them. */
    private static List<String> sent(final String locale, final Consumer<Player> act)
    {
        final Player player = reading(locale);
        act.accept(player);
        final ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(player, atLeastOnce()).sendMessage(lines.capture());
        return new ArrayList<>(lines.getAllValues());
    }

    private static List<String> config(final String locale)
    {
        return sent(locale, player -> new ConfigCommand().execute(player, new String[] { "config", "RING_DEFAULT" }));
    }

    private static List<String> cooldown(final String locale)
    {
        return sent(locale, player ->
        {
            ConfigManager.setUseCooldownEnabled(true);
            new CooldownCommand().execute(player, new String[] { COOLDOWN });
        });
    }

    /** The last two lines of the listing: what to type to read a setting or change it, and to reload. */
    private static List<String> lastTwo(final List<String> lines)
    {
        return lines.subList(lines.size() - 2, lines.size());
    }

    @Test
    void theConfigListingsExplanationsReadAsBefore()
    {
        assertEquals(List.of(
            info(usageBody("/wormhole config <name>") + " shows one, " + usageBody("/wormhole config <name> <value>")
                + " changes it."),
            info(usageBody("/wormhole config -reload") + " re-reads config.yml.")), lastTwo(config(ENGLISH)));
    }

    @Test
    void theConfigListingsExplanationsAreInTheReadersLanguage()
    {
        assertEquals(List.of(
            info("QR " + usageBody("/wormhole config <name> <value>") + " sets, " + usageBody("/wormhole config <name>")
                + " reads!"),
            info("QR reload " + usageBody("/wormhole config -reload") + "!")), lastTwo(config(QR)));
    }

    @Test
    void theCooldownUsageReadsAsBefore()
    {
        final List<String> lines = cooldown(ENGLISH);

        assertEquals(info("Usage: " + usageBody(SECONDS_FORM) + " sets the wait between trips, " + value("15") + " to " + value("3600")
            + "."), lines.get(0));
        assertEquals(info("Usage: " + usageBody(SWITCH_FORM) + " switches cooldowns on or off."), lines.get(1));
    }

    @Test
    void theCooldownUsageIsInTheReadersLanguage()
    {
        final List<String> lines = cooldown(QR);

        assertEquals(info("QR use " + usageBody(SECONDS_FORM) + ". waits " + value("3600") + " down to " + value("15")
            + "!"), lines.get(0));
        assertEquals(info("QR use " + usageBody(SWITCH_FORM) + ". toggles!"), lines.get(1));
    }
}
