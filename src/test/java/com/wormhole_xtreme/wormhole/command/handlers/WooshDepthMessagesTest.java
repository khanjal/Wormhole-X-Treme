package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.gate;
import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@code /wormhole wooshdepth} says, read by key from {@code lang/} (#26).
 *
 * <p>The range line was a constant built once, so it could only ever be in one language; it is now
 * built for whoever reads it. Each line goes through the real command to an English admin, against
 * the concatenation the code built before the move, and to a qq admin, so a call site reading the
 * wrong recipient shows as English.
 */
class WooshDepthMessagesTest
{
    private static final String ALPHA = "alpha";
    private static final String PLAIN = "plain";

    private MockedStatic<StargateDBManager> db;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        AdminReaders.clearGates();
        db = mockStatic(StargateDBManager.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        db.close();
        AdminReaders.clearGates();
        PluginTestSupport.remove();
    }

    private static void woosh(final Player player, final String... rest)
    {
        final String[] args = new String[rest.length + 1];
        args[0] = "wooshdepth";
        System.arraycopy(rest, 0, args, 1, rest.length);
        new WooshDepthCommand().execute(player, args);
    }

    /** The usage, a gate not in custom mode, a report, two refusals and a change, on a shape with waves. */
    private static void editDepth(final Player player)
    {
        gate(PLAIN);
        final Stargate alpha = gate(ALPHA);
        alpha.setGateCustom(true);
        alpha.setGateCustomWooshDepth(2);
        // A null world: the note asks only whether the shape authors waves.
        alpha.getGateWooshBlocks().add(new ArrayList<>(List.of(new Location(null, 0, 64, 0))));
        woosh(player);
        woosh(player, PLAIN);
        woosh(player, ALPHA);
        woosh(player, ALPHA, "deep");
        woosh(player, ALPHA, "9");
        woosh(player, ALPHA, "4");
    }

    @Test
    void depthLinesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        editDepth(player);

        verify(player).sendMessage(ChatText.error(ChatText.name(PLAIN) + " is not in custom mode. Turn it on with "
            + ChatText.command("/wormhole custom " + PLAIN + " true") + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " woosh depth is currently: "
            + ChatText.value("2") + "."));
        verify(player).sendMessage(ChatText.error("Invalid woosh depth: " + ChatText.command("deep") + "."));
        verify(player).sendMessage(ChatText.error("Invalid woosh depth: " + ChatText.command("9") + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " woosh depth set to: "
            + ChatText.value("4") + "."));
        verify(player, times(4)).sendMessage(ChatText.info("Depth is " + ChatText.value(String.valueOf(0))
            + " to " + ChatText.value(String.valueOf(5)) + "."));
        verify(player, times(2)).sendMessage(ChatText.info("This gate's shape draws its own woosh, so depth sets only how far its "
            + "protection reaches."));
    }

    @Test
    void depthLinesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        editDepth(player);

        verify(player).sendMessage(ChatText.error("QQ " + ChatText.name(PLAIN) + " woosh not custom "
            + ChatText.command("/wormhole custom " + PLAIN + " true") + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " depth " + ChatText.value("2") + "."));
        verify(player).sendMessage(ChatText.error("QQ bad depth " + ChatText.command("deep") + "."));
        verify(player).sendMessage(ChatText.error("QQ bad depth " + ChatText.command("9") + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " depth set " + ChatText.value("4") + "."));
        verify(player, times(4)).sendMessage(ChatText.info("QQ range " + ChatText.value("0") + "-" + ChatText.value("5")
            + "."));
        verify(player, times(2)).sendMessage(ChatText.info("QQ shape owns."));
    }
}
