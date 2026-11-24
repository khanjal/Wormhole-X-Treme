package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.gate;
import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.Dial;
import com.wormhole_xtreme.wormhole.command.handlers.MaterialCommand.Kind;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * Every command that names a gate it cannot find tells a player in their own language (#26).
 *
 * <p>The refusal is one shared helper, and each caller must hand it the reader: a caller that does not
 * sends the server's language to a player whose client reads another. Each command is run for a
 * player whose client reads the test language qq, through the real command, and must send the qq line.
 */
class NoGateReaderTest
{
    private static final String NOWHERE = "nowhere";
    private static final String QQ = "qq";

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        AdminReaders.clearGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        AdminReaders.clearGates();
        PluginTestSupport.remove();
    }

    /** Runs a command as a qq reader and checks the refusal that comes back is the qq one. */
    private static void refusedInQq(final Consumer<Player> command)
    {
        final Player player = reading(QQ);
        command.accept(player);

        verify(player).sendMessage(ChatText.error("QQ no gate " + ChatText.name(NOWHERE) + " "
            + ChatText.command("/wormhole list") + "."));
    }

    private static void edit(final Player player, final String field, final String value)
    {
        new GateEditCommand().execute(player, new String[] { "gate", "edit", NOWHERE, field, value });
    }

    @Test
    void dialNamesAGateThereIsNotInTheReadersLanguage()
    {
        refusedInQq(player -> Dial.dialFrom(player, gate("origin"), new String[] { NOWHERE }));
    }

    @Test
    void theConsoleDialNamesAGateThereIsNotInTheReadersLanguage()
    {
        refusedInQq(player -> GateConsoleCommands.dial(player, new String[] { NOWHERE, "other" }));
    }

    @Test
    void customNamesAGateThereIsNotInTheReadersLanguage()
    {
        refusedInQq(player -> new CustomCommand().execute(player, new String[] { "custom", NOWHERE }));
    }

    @Test
    void materialNamesAGateThereIsNotInTheReadersLanguage()
    {
        refusedInQq(player -> new MaterialCommand(Kind.PORTAL).execute(player, new String[] { "portalmaterial", NOWHERE }));
    }

    @Test
    void wooshDepthNamesAGateThereIsNotInTheReadersLanguage()
    {
        refusedInQq(player -> new WooshDepthCommand().execute(player, new String[] { "wooshdepth", NOWHERE }));
    }

    @Test
    void editingTheIrisAnimationNamesAGateThereIsNotInTheReadersLanguage()
    {
        refusedInQq(player -> edit(player, "iris-animation", "rows"));
    }

    @Test
    void editingTheDialSpinNamesAGateThereIsNotInTheReadersLanguage()
    {
        refusedInQq(player -> edit(player, "spin", "chase"));
    }

    @Test
    void editingTheGroupNamesAGateThereIsNotInTheReadersLanguage()
    {
        refusedInQq(player -> edit(player, "group", "Atlantis"));
    }
}
