package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static com.wormhole_xtreme.wormhole.utils.ChatText.usageBody;
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
import com.wormhole_xtreme.wormhole.command.handlers.MaterialCommand.Kind;

/**
 * The usage line of {@code custom}, the three material commands, {@code redstone}, {@code wooshdepth},
 * {@code gate edit} and {@code gate shapes}, read by key from {@code lang/} (#26).
 *
 * <p>Each goes through the real command, typed with too little, to an English admin, against the text
 * the code built before the move, written here from it, and to a reader of the test language qr, so a
 * call site that reads the server's language or nobody's shows as English.
 */
class GateCommandsUsageMessagesTest
{
    private static final String QR = "qr";
    private static final String ENGLISH = "en";
    private static final String SHAPES_FORM = "/wormhole gate shapes ";
    private static final String NAME = " <name>";

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
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

    /** What the syntax-only lines said before: the label, then the coloured syntax. */
    private static String oldUsage(final String line)
    {
        return info("Usage: " + usageBody(line));
    }

    private static String qrUsage(final String line)
    {
        return info("QR use " + usageBody(line) + ".");
    }

    private static List<String> shapes(final String locale, final String... rest)
    {
        final String[] args = new String[rest.length + 2];
        args[0] = "gate";
        args[1] = "shapes";
        System.arraycopy(rest, 0, args, 2, rest.length);
        return sent(locale, player -> new GateShapesCommand().execute(player, args));
    }

    @Test
    void customReadsAsBeforeAndInTheReadersLanguage()
    {
        final String form = "/wormhole custom <gate|-all|-clean> [true|false|-confirm]";

        assertEquals(List.of(oldUsage(form)),
            sent(ENGLISH, player -> new CustomCommand().execute(player, new String[] { "custom" })));
        assertEquals(List.of(qrUsage(form)),
            sent(QR, player -> new CustomCommand().execute(player, new String[] { "custom" })));
    }

    @Test
    void eachMaterialCommandReadsAsBeforeAndInTheReadersLanguage()
    {
        for (final Kind kind : Kind.values())
        {
            final String form = "/wormhole " + kind.command() + " <gate> [material]";
            final MaterialCommand command = new MaterialCommand(kind);
            final String[] args = { kind.command() };

            // The usage is the first of two lines; the second lists the materials.
            assertEquals(oldUsage(form), sent(ENGLISH, player -> command.execute(player, args)).get(0));
            assertEquals(qrUsage(form), sent(QR, player -> command.execute(player, args)).get(0));
        }
    }

    @Test
    void redstoneReadsAsBeforeAndInTheReadersLanguage()
    {
        final String form = "/wormhole redstone <gate> [true|false]";
        final String[] args = { "redstone" };

        assertEquals(List.of(oldUsage(form)), sent(ENGLISH, player -> new RedstoneCommand().execute(player, args)));
        assertEquals(List.of(qrUsage(form)), sent(QR, player -> new RedstoneCommand().execute(player, args)));
    }

    /** The usage is the first of two lines; the second, the range, is covered with its own key. */
    @Test
    void wooshDepthReadsAsBeforeAndInTheReadersLanguage()
    {
        final String form = "/wormhole wooshdepth <gate> [depth]";
        final String[] args = { "wooshdepth" };

        assertEquals(oldUsage(form), sent(ENGLISH, player -> new WooshDepthCommand().execute(player, args)).get(0));
        assertEquals(qrUsage(form), sent(QR, player -> new WooshDepthCommand().execute(player, args)).get(0));
    }

    /** The usage is the first of two lines; the second lists the fields. */
    @Test
    void gateEditReadsAsBeforeAndInTheReadersLanguage()
    {
        final String form = "/wormhole gate edit <gate> <field> [value]";
        final String[] args = { "gate", "edit", "alpha" };

        assertEquals(oldUsage(form), sent(ENGLISH, player -> new GateEditCommand().execute(player, args)).get(0));
        assertEquals(qrUsage(form), sent(QR, player -> new GateEditCommand().execute(player, args)).get(0));
    }

    @Test
    void gateShapesReadsAsBeforeAndInTheReadersLanguage()
    {
        final String all = SHAPES_FORM + "reload|validate|enable|disable [name]";

        assertEquals(List.of(oldUsage(all)), shapes(ENGLISH));
        assertEquals(List.of(qrUsage(all)), shapes(QR));
        assertEquals(List.of(oldUsage(SHAPES_FORM + "validate" + NAME)), shapes(ENGLISH, "validate"));
        assertEquals(List.of(qrUsage(SHAPES_FORM + "validate" + NAME)), shapes(QR, "validate"));
    }

    /** The verb in the line is the one typed, enable or disable, in either language. */
    @Test
    void gateShapesEnableAndDisableNameTheirOwnVerb()
    {
        assertEquals(List.of(oldUsage(SHAPES_FORM + "enable" + NAME)), shapes(ENGLISH, "enable"));
        assertEquals(List.of(oldUsage(SHAPES_FORM + "disable" + NAME)), shapes(ENGLISH, "disable"));
        assertEquals(List.of(qrUsage(SHAPES_FORM + "enable" + NAME)), shapes(QR, "enable"));
        assertEquals(List.of(qrUsage(SHAPES_FORM + "disable" + NAME)), shapes(QR, "disable"));
    }
}
