package com.wormhole_xtreme.wormhole.command;

import static com.wormhole_xtreme.wormhole.command.CommandReaders.reading;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
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
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * What {@code /wx list} and {@code /wormhole} say when they refuse or fail, and the lines shared by every
 * command that names a gate, read by key from {@code lang/} (#26).
 *
 * <p>The gate list wraps its names into messages of about seventy-five visible characters, so the lines
 * it sends are compared whole, against the packing the code did before the move, copied from it: the
 * wrapping must not change, and in a language whose separator is a different length it must count that
 * length, not the English two.
 */
class ListAndFallbackMessagesTest
{
    private static final String EN = "en";
    private static final String QQ = "qq";
    private static final String PEGASUS = "Pegasus";
    private static final String NOWHERE = "nowhere";
    private static final String INTERNAL = "An internal error occurred. Check server logs.";

    /** The old line budget: past it a message is cut by the client rather than wrapped. */
    private static final int LINE_BUDGET = 75;

    @TempDir
    File dataFolder;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        CommandReaders.clearGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        CommandReaders.clearGates();
        PluginTestSupport.forgetAllGates();
        ConfigTestSupport.clear();
        Messages.load();
        PluginTestSupport.remove();
    }

    /** Twenty-five gates called gate01 and on, and the last two of them on a network. */
    private static void registerGates()
    {
        for (int i = 1; i <= 25; i++)
        {
            final Stargate gate = CommandReaders.gate(String.format("gate%02d", i));
            if (i > 23)
            {
                gate.setGateNetwork(StargateManager.addStargateNetwork(PEGASUS));
            }
        }
    }

    /** The gate names the way the code packed them before the move, and then the lines it sent. */
    private static List<String> packed(final List<String> names, final String separator)
    {
        final List<String> lines = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < names.size(); i++)
        {
            sb.append(ChatText.name(names.get(i)));
            visible += names.get(i).length();
            if (i != (names.size() - 1))
            {
                sb.append(separator);
                visible += separator.length();
            }
            if (visible >= LINE_BUDGET)
            {
                lines.add(ChatText.info(sb.toString()));
                sb = new StringBuilder();
                visible = 0;
            }
        }
        if (!sb.isEmpty())
        {
            lines.add(ChatText.info(sb.toString()));
        }
        return lines;
    }

    private static List<String> allNames()
    {
        return StargateManager.getAllGates().stream().map(Stargate::getGateName).toList();
    }

    private static List<String> sentTo(final CommandSender who)
    {
        final ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(who, atLeastOnce()).sendMessage(sent.capture());
        return sent.getAllValues();
    }

    private static List<String> list(final CommandSender who, final String... args)
    {
        new WXList().onCommand(who, null, "list", args);
        return sentTo(who);
    }

    private static List<String> expected(final String header, final List<String> rest)
    {
        final List<String> all = new ArrayList<>();
        all.add(header);
        all.addAll(rest);
        return all;
    }

    @Test
    void theListReadsAsItDidBefore()
    {
        registerGates();

        assertEquals(expected(ChatText.info("Available gates " + ChatText.heading("::")), packed(allNames(), ", ")),
            list(reading(EN)), "every gate, wrapped as before");
        assertEquals(3, packed(allNames(), ", ").size(), "the fixture wraps, so the packing is being compared");
        assertEquals(expected(ChatText.info("Gates on network " + ChatText.name(PEGASUS) + ChatText.heading(" ::")),
            List.of(ChatText.info(ChatText.name("gate24") + ", " + ChatText.name("gate25")))),
            list(reading(EN), PEGASUS), "one network, its two gates on one line");
        assertEquals(List.of(ChatText.info("Gates on network " + ChatText.name("Atlantis") + ChatText.heading(" ::")),
            ChatText.info("No gates found.")), list(reading(EN), "Atlantis"), "a network with none says so");
    }

    @Test
    void theListIsInTheReadersLanguageAndWrapsOnItsSeparator()
    {
        registerGates();
        final List<String> qqLines = packed(allNames(), " | ");

        assertEquals(expected(ChatText.info("QQ all " + ChatText.heading("::")), qqLines), list(reading(QQ)),
            "every gate, wrapped by the length of the separator the reader's language joins with");
        assertEquals(3, qqLines.size(), "the fixture wraps");
        assertEquals(9, qqLines.get(0).split(" \\| ").length, "a three-letter separator crosses the budget at the "
            + "ninth name, where English's two-letter one crosses it at the tenth");
        assertEquals(10, packed(allNames(), ", ").get(0).split(", ").length);
        assertEquals(expected(ChatText.info("QQ on " + ChatText.name(PEGASUS) + ChatText.heading(" ::")),
            List.of(ChatText.info(ChatText.name("gate24") + " | " + ChatText.name("gate25")))),
            list(reading(QQ), PEGASUS));
        assertEquals(List.of(ChatText.info("QQ on " + ChatText.name("Atlantis") + ChatText.heading(" ::")),
            ChatText.info("QQ none found.")), list(reading(QQ), "Atlantis"));
    }

    /** A command that cannot read its arguments says so in the reader's language, from any command that catches it. */
    @Test
    void aCommandThatThrewSaysSoInTheReadersLanguage()
    {
        final Player english = reading(EN);
        final Player englishWormhole = reading(EN);
        final Player qq = reading(QQ);
        final Player qqWormhole = reading(QQ);
        new WXList().onCommand(english, null, "list", null);
        new Wormhole().onCommand(englishWormhole, null, "wormhole", null);
        new WXList().onCommand(qq, null, "list", null);
        new Wormhole().onCommand(qqWormhole, null, "wormhole", null);

        verify(english).sendMessage(ChatText.error(INTERNAL));
        verify(englishWormhole).sendMessage(ChatText.error(INTERNAL));
        verify(qq).sendMessage(ChatText.error("QQ internal error."));
        verify(qqWormhole).sendMessage(ChatText.error("QQ internal error."));
        assertEquals(ChatText.error(INTERNAL), CommandUtilities.INTERNAL_ERROR, "the constant stays what English says");
    }

    @Test
    void anUnknownCommandIsNamedInTheReadersLanguage()
    {
        final Player english = reading(EN);
        final Player qq = reading(QQ);
        new Wormhole().onCommand(english, null, "wormhole", new String[] { "zzz" });
        new Wormhole().onCommand(qq, null, "wormhole", new String[] { "zzz" });

        verify(english).sendMessage(ChatText.error("No command called " + ChatText.command("zzz")
            + ". Try one of these:"));
        verify(qq).sendMessage(ChatText.error("QQ no command " + ChatText.command("zzz") + "."));
    }

    /** A caller with nobody to ask gets the server's language: English, then qq once the server speaks it. */
    @Test
    void theHelpersWithNoRecipientReadTheServersLanguage()
    {
        final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        final String english = CommandUtilities.noGateCalled(NOWHERE);
        final String consoleEnglish = CommandUtilities.noGateCalled(console, NOWHERE);
        ConfigTestSupport.set(ConfigKeys.SERVER_LANGUAGE, QQ);
        Messages.load();

        assertEquals(ChatText.error("No gate called " + ChatText.name(NOWHERE) + ". "
            + ChatText.command("/wormhole list") + " shows them."), english);
        assertEquals(english, consoleEnglish);
        assertEquals(ChatText.error("QQ no gate " + ChatText.name(NOWHERE) + " " + ChatText.command("/wormhole list")
            + "."), CommandUtilities.noGateCalled(NOWHERE));
        assertEquals(ChatText.error("QQ internal error."), CommandUtilities.internalError(console));
        new WXList().onCommand(console, null, "list", null);
        verify(console).sendMessage(ChatText.error("QQ internal error."));
    }
}
