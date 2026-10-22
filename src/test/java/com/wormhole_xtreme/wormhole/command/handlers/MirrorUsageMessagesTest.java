package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static com.wormhole_xtreme.wormhole.utils.ChatText.usageBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorNetwork;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPresetRegistry;

/**
 * The usage lines of {@code /wormhole mirror}, read by key from {@code lang/} (#26).
 *
 * <p>Each goes through the real command to an English reader, against the text the code built before
 * the move, written here from it; and to a reader of the test language qr, which holds the usage keys,
 * so a call site that reads the server's language or nobody's shows as English.
 */
class MirrorUsageMessagesTest
{
    private static final String MIRROR = "mirror";
    private static final String CREATE = "create";
    private static final String SET = "set";
    private static final String START = "-start";
    private static final String STAMP = "-stamp";
    private static final String QR = "qr";
    private static final String BASE = "/wormhole mirror ";
    private static final String LOOKS_PREFIX = BASE + "set [<name>] -stamp [";

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        MirrorManager.clear();
        MirrorNetwork.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        MirrorNetwork.clear();
        PluginTestSupport.remove();
    }

    private static String oldForm(final String line)
    {
        return info("Usage: " + usageBody(line));
    }

    private static String qrForm(final String line)
    {
        return info("QR use " + usageBody(line) + ".");
    }

    /** What an admin who reads {@code locale} is sent by {@code words}, which no banner is looked at for. */
    private static List<String> sentBy(final String locale, final String... words)
    {
        final Player player = reading(locale);
        new MirrorCommand().execute(player, words);
        final ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(player, atLeastOnce()).sendMessage(lines.capture());
        return new ArrayList<>(lines.getAllValues());
    }

    /** Whether each reader is sent {@code form} by {@code words}, in the old wording and in qr's. */
    private static void assertSays(final String form, final String... words)
    {
        final List<String> english = sentBy("en", words);
        final List<String> qr = sentBy(QR, words);
        assertTrue(english.contains(oldForm(form)), () -> form + " to an English reader: " + english);
        assertTrue(qr.contains(qrForm(form)), () -> form + " to a qr reader: " + qr);
    }

    @Test
    void theHelpOpensWithTheVerbsUsage()
    {
        assertEquals(oldForm(BASE + "create|set|remove|list"), sentBy("en", MIRROR).get(0));
        assertEquals(qrForm(BASE + "create|set|remove|list"), sentBy(QR, MIRROR).get(0));
    }

    @Test
    void createWithoutANameOrWithHalfAPlaceAnswersWithItsUsage()
    {
        final String both = BASE + "create <name> [<world> <x> <y> <z>]";
        assertSays(both, MIRROR, CREATE);
        assertSays(both, MIRROR, CREATE, "hall", "world", "1");
    }

    @Test
    void setWithNoPropertyAnswersWithItsUsage()
    {
        assertSays(BASE + "set [<name>] <-stamp|-start|-capture> ...", MIRROR, SET);
    }

    @Test
    void startWithNothingToStartAtAnswersWithItsUsage()
    {
        final String form = BASE + "set [<name>] -start <mirror|-none>";
        assertSays(form, MIRROR, SET, START);
        assertSays(form, MIRROR, SET, "hall", START);
    }

    @Test
    void captureRemoveAndDebugAnswerWithTheirUsageWhenNothingIsNamedOrLookedAt()
    {
        assertSays(BASE + "set [<name>] -capture", MIRROR, SET, "-capture");
        assertSays(BASE + "remove [<name>]", MIRROR, "remove");
        assertSays(BASE + "debug [<name>] [-all|-full] | debug -views | debug -off|-on", MIRROR, "debug");
    }

    /** Runs {@code words} as each reader with the looks the registry holds being {@code names}. */
    private static void assertSaysWithLooks(final List<String> names, final String form, final String... words)
    {
        try (MockedStatic<MirrorPresetRegistry> registry = mockStatic(MirrorPresetRegistry.class,
            CALLS_REAL_METHODS))
        {
            registry.when(MirrorPresetRegistry::names).thenReturn(names.toArray(new String[0]));
            assertSays(form, words);
        }
    }

    /** The stamp usage lists the looks while they fit, and is the bare {@code <look>} form past that. */
    @Test
    void stampAnswersWithItsUsageListingTheLooksOrNot()
    {
        assertSaysWithLooks(List.of("nether", "end"), LOOKS_PREFIX + "nether|end]", MIRROR, SET, STAMP);
        assertSaysWithLooks(IntStream.range(0, 23).mapToObj(i -> "look" + i).toList(), LOOKS_PREFIX + "<look>]",
            MIRROR, SET, STAMP);
    }
}
