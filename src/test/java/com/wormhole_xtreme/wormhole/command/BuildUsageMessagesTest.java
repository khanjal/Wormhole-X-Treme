package com.wormhole_xtreme.wormhole.command;

import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static com.wormhole_xtreme.wormhole.utils.ChatText.usageBody;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * The usage lines of {@code gate build} and {@code gate preview}, read by key from {@code lang/} (#26).
 *
 * <p>Chat links are off, so each line is the one string it read as before links existed. Each goes
 * through the real command to an English builder, against the text the code built before the move,
 * and to a builder of the test language qr, whose {@code Usage:} differs, so a call site that reads
 * the server's language or nobody's shows as English. {@link BuildLinksMessagesTest} reads the links.
 */
class BuildUsageMessagesTest extends BuildPreviewCase
{
    private static final String QR_LEAD = "QR use ";

    /** The roles as the material usage lists them, in the reader's separator. */
    private static String roles()
    {
        return Arrays.stream(GateBlueprint.Role.values()).map(GateBlueprint.Role::option).map(ChatText::command)
            .collect(Collectors.joining(", "));
    }

    @Test
    void theBuildUsageReadsAsItDidBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qr = reading("qr");
        run(english, "gate", "build", "a", "b", "c");
        run(qr, "gate", "build", "a", "b", "c");

        verify(english).sendMessage(info("Usage: " + usageBody("/wormhole gate build <shape> [group]")));
        verify(qr).sendMessage(info(QR_LEAD + usageBody("/wormhole gate build <shape> [group]") + "."));
    }

    @Test
    void thePreviewUsageLeadsTheActionListAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qr = reading("qr");
        run(english, "gate", "preview");
        run(qr, "gate", "preview");

        verify(english).sendMessage(info("Usage: ") + listed());
        verify(qr).sendMessage(info(QR_LEAD) + listed());
    }

    @Test
    void theMaterialUsageLeadsItsTwoFormsAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qr = reading("qr");
        run(english, "gate", "preview", "material", "sparkly");
        run(qr, "gate", "preview", "material", "sparkly");

        final String forms = hint("material <group>") + " or " + hint("material -<role> <block>") + ". Roles: "
            + roles() + ".";
        verify(english).sendMessage(info("Usage: ") + forms);
        verify(qr).sendMessage(info(QR_LEAD) + forms);
    }

    @Test
    void theLayerUsageLeadsItsFormAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qr = reading("qr");
        run(english, "gate", "preview", "layer", "bogus");
        run(qr, "gate", "preview", "layer", "bogus");

        verify(english).sendMessage(info("Usage: ") + hint("layer [number|-next|-all]"));
        verify(qr).sendMessage(info(QR_LEAD) + hint("layer [number|-next|-all]"));
    }
}
