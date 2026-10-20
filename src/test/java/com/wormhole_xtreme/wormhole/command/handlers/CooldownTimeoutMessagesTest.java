package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@code /wormhole cooldown} and the two timeout commands say, read by key from {@code lang/} (#26).
 *
 * <p>Each line goes through the real command twice: to an English admin, against the concatenation
 * the code built before the move, copied from it, and to an admin whose client reads the test
 * language qq, so a line built in the server's language instead of the reader's shows as English.
 * The usage lines themselves stay English for now and are not checked here.
 */
class CooldownTimeoutMessagesTest
{
    private static final String ACTIVATE = "activate_timeout";

    private static final String SHUTDOWN = "shutdown_timeout";

    private static final String SOON = "soon";

    private static final String SECONDS = " seconds.";

    private int savedActivate;

    private int savedShutdown;

    private boolean savedEnabled;

    private int savedCooldown;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.loadDefaults();
        savedActivate = ConfigManager.getTimeoutActivate();
        savedShutdown = ConfigManager.getTimeoutShutdown();
        savedEnabled = ConfigManager.isUseCooldownEnabled();
        savedCooldown = ConfigManager.getUseCooldownSeconds();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigManager.setTimeoutActivate(savedActivate);
        ConfigManager.setTimeoutShutdown(savedShutdown);
        ConfigManager.setUseCooldownEnabled(savedEnabled);
        ConfigManager.setUseCooldownSeconds(savedCooldown);
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** An old group name, a switch, a word, a number out of range and one taken. */
    private static void cooldowns(final Player player)
    {
        ConfigManager.setUseCooldownEnabled(true);
        final CooldownCommand command = new CooldownCommand();
        command.execute(player, new String[] { "cooldown", "two" });
        command.execute(player, new String[] { "cooldown", "true" });
        command.execute(player, new String[] { "cooldown", SOON });
        command.execute(player, new String[] { "cooldown", "5" });
        command.execute(player, new String[] { "cooldown", "60" });
    }

    /** Each timeout asked, refused as a word and out of range, and set. */
    private static void timeouts(final Player player)
    {
        final TimeoutsCommand command = new TimeoutsCommand();
        command.execute(player, new String[] { ACTIVATE });
        command.execute(player, new String[] { ACTIVATE, SOON });
        command.execute(player, new String[] { ACTIVATE, "5" });
        command.execute(player, new String[] { ACTIVATE, "30" });
        command.execute(player, new String[] { SHUTDOWN });
        command.execute(player, new String[] { "timeout", "61" });
        command.execute(player, new String[] { "timeout", "0" });
    }

    private static String value(final Object shown)
    {
        return ChatText.value(String.valueOf(shown));
    }

    /** The cooldown lines as before; the current line follows each usage, so it is sent twice. */
    @Test
    void cooldownLinesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        cooldowns(player);

        verify(player).sendMessage(ChatText.error("Cooldown groups are gone; they never took effect. Give the seconds instead."));
        verify(player).sendMessage(ChatText.info("Wormhole use cooldowns set to: " + value(true) + "."));
        verify(player).sendMessage(ChatText.error("Invalid cooldown time: " + ChatText.command(SOON) + "."));
        verify(player).sendMessage(ChatText.error("Invalid cooldown time: " + ChatText.command("5") + ". Give "
            + ChatText.value(String.valueOf(15)) + " to " + ChatText.value(String.valueOf(3600)) + SECONDS));
        verify(player).sendMessage(ChatText.info("Wormhole cooldown time set to: " + value(60) + SECONDS));
        verify(player, times(2)).sendMessage(ChatText.info("Cooldowns enabled: " + value(true)
            + ", currently " + value(savedCooldown) + SECONDS));
    }

    /** The same lines reach a qq admin in qq. */
    @Test
    void cooldownLinesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        cooldowns(player);

        verify(player).sendMessage(ChatText.error("QQ groups gone."));
        verify(player).sendMessage(ChatText.info("QQ cooldowns " + value(true) + "."));
        verify(player).sendMessage(ChatText.error("QQ bad cooldown " + ChatText.command(SOON) + "."));
        verify(player).sendMessage(ChatText.error("QQ bad cooldown " + ChatText.command("5") + " " + value(15) + "-"
            + value(3600) + "."));
        verify(player).sendMessage(ChatText.info("QQ cooldown set " + value(60) + "."));
        verify(player, times(2)).sendMessage(ChatText.info("QQ cooldowns " + value(true) + " at "
            + value(savedCooldown) + "."));
    }

    /** The timeout lines as before, each range after the question and after each refusal. */
    @Test
    void timeoutLinesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        timeouts(player);

        verify(player).sendMessage(ChatText.info("Current " + ChatText.command(ACTIVATE) + " is: "
            + value(savedActivate) + SECONDS));
        verify(player).sendMessage(ChatText.error("Invalid " + ChatText.command(ACTIVATE) + ": "
            + ChatText.command(SOON) + "."));
        verify(player).sendMessage(ChatText.error("Invalid " + ChatText.command(ACTIVATE) + ": "
            + ChatText.command("5") + "."));
        verify(player, times(3)).sendMessage(ChatText.info("Valid timeout is between " + value(10) + " and "
            + value(60) + SECONDS));
        verify(player).sendMessage(ChatText.info(ChatText.command(ACTIVATE) + " set to: " + value(30) + SECONDS));
        verify(player).sendMessage(ChatText.info("Current " + ChatText.command(SHUTDOWN) + " is: "
            + value(savedShutdown) + SECONDS));
        verify(player).sendMessage(ChatText.error("Invalid " + ChatText.command(SHUTDOWN) + ": "
            + ChatText.command("61") + "."));
        verify(player, times(2)).sendMessage(ChatText.info("Valid timeout is between " + value(0) + " and "
            + value(60) + SECONDS));
        verify(player).sendMessage(ChatText.info(ChatText.command(SHUTDOWN) + " set to: " + value(0) + SECONDS));
    }

    /** The same lines reach a qq admin in qq. */
    @Test
    void timeoutLinesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        timeouts(player);

        verify(player).sendMessage(ChatText.info("QQ current " + ChatText.command(ACTIVATE) + " " + value(savedActivate)
            + "."));
        verify(player).sendMessage(ChatText.error("QQ bad " + ChatText.command(ACTIVATE) + " " + ChatText.command(SOON)
            + "."));
        verify(player).sendMessage(ChatText.error("QQ bad " + ChatText.command(ACTIVATE) + " " + ChatText.command("5")
            + "."));
        verify(player, times(3)).sendMessage(ChatText.info("QQ range " + value(10) + "-" + value(60) + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.command(ACTIVATE) + " set " + value(30) + "."));
        verify(player).sendMessage(ChatText.info("QQ current " + ChatText.command(SHUTDOWN) + " " + value(savedShutdown)
            + "."));
        verify(player).sendMessage(ChatText.error("QQ bad " + ChatText.command(SHUTDOWN) + " " + ChatText.command("61")
            + "."));
        verify(player, times(2)).sendMessage(ChatText.info("QQ range " + value(0) + "-" + value(60) + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.command(SHUTDOWN) + " set " + value(0) + "."));
    }
}
