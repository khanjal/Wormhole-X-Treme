package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.gate;
import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@code /wormhole gate edit} says, read by key from {@code lang/} (#26).
 *
 * <p>Each line goes through the real command twice: to an English admin, against the concatenation the
 * code built before the move, copied from it, so a reworded template, a swapped argument or a list
 * joined differently shows as a changed line; and to an admin whose client reads the test language qq,
 * so a call site that reads the server's language instead of the reader's shows as English. Where a
 * setting comes from (the gate's own, its group's, the server's) and each list of names are covered
 * in both.
 */
class GateEditMessagesTest
{
    private static final String ALPHA = "alpha";
    private static final String BETA = "beta";
    private static final String GAMMA = "gamma";
    private static final String ATLANTIS = "Atlantis";
    private static final String IRIS = "iris-animation";
    private static final String SPIN = "spin";
    private static final String GROUP = "group";

    private MockedStatic<StargateDBManager> db;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        AdminReaders.clearGates();
        MaterialGroupRegistry.load(Map.of(ATLANTIS,
            Map.of("structure", "LAPIS_BLOCK", "iris-animation", "rows", "dial-spin", "chase"),
            "Tollan", Map.of("structure", "DIAMOND_BLOCK")));
        db = mockStatic(StargateDBManager.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        db.close();
        AdminReaders.clearGates();
        MaterialGroupRegistry.load(null);
        PluginTestSupport.remove();
    }

    private static void edit(final CommandSender sender, final String... rest)
    {
        final String[] args = new String[rest.length + 2];
        args[0] = "gate";
        args[1] = "edit";
        System.arraycopy(rest, 0, args, 2, rest.length);
        new GateEditCommand().execute(sender, args);
    }

    /** Alpha with its own setting, beta on a group that sets it, gamma on nothing: the server's. */
    private static Stargate threeGates()
    {
        final Stargate alpha = gate(ALPHA);
        alpha.setGateIrisAnimation("columns");
        alpha.setGateDialSpin(DialSpinPattern.LAP);
        gate(BETA).setGateMaterialGroup(MaterialGroupRegistry.getGroup(ATLANTIS));
        return gate(GAMMA);
    }

    /** Asks each gate, refuses a name, sets one and hands beta back to its group. */
    private static void editEach(final Player player, final String field, final String unknown, final String chosen)
    {
        edit(player, ALPHA, field);
        edit(player, BETA, field);
        edit(player, GAMMA, field);
        edit(player, ALPHA, field, unknown);
        edit(player, ALPHA, field, chosen);
        edit(player, BETA, field, "default");
    }

    /** A list of values as the code built it before the move: each white, comma separated. */
    private static String oldOptions(final List<String> values)
    {
        return values.stream().map(ChatText::command).collect(Collectors.joining(", "));
    }

    private static String qqOptions(final List<String> values)
    {
        return String.join(" | ", values.stream().map(ChatText::command).toList());
    }

    private static String oldFromGroup()
    {
        return " (from group " + ChatText.name(ATLANTIS) + ")";
    }

    /** Each source of an iris animation, a refusal, and a change, as before. */
    @Test
    void irisAnimationLinesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        final String server = threeGates().getEffectiveIrisAnimation();
        editEach(player, IRIS, "sideways", "spiral");

        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + "'s iris crosses as "
            + ChatText.value("columns") + "" + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(BETA) + "'s iris crosses as "
            + ChatText.value("rows") + oldFromGroup() + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(GAMMA) + "'s iris crosses as "
            + ChatText.value(server) + " (the server default)" + "."));
        verify(player).sendMessage(ChatText.error("No iris animation called " + ChatText.command("sideways") + "."));
        verify(player, times(4)).sendMessage(ChatText.info("Animations are: "
            + oldOptions(GateEditCommand.irisAnimationNames()) + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + "'s iris now crosses as "
            + ChatText.value("spiral") + "" + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(BETA) + "'s iris now crosses as "
            + ChatText.value("rows") + oldFromGroup() + "."));
    }

    /** The same lines reach a qq admin in qq, the sources and the list as qq says them. */
    @Test
    void irisAnimationLinesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        final String server = threeGates().getEffectiveIrisAnimation();
        editEach(player, IRIS, "sideways", "spiral");

        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " iris " + ChatText.value("columns") + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(BETA) + " iris " + ChatText.value("rows")
            + " QQgroup " + ChatText.name(ATLANTIS) + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(GAMMA) + " iris " + ChatText.value(server)
            + " QQdefault."));
        verify(player).sendMessage(ChatText.error("QQ no animation " + ChatText.command("sideways") + "."));
        verify(player, times(4)).sendMessage(ChatText.info("QQ animations "
            + qqOptions(GateEditCommand.irisAnimationNames()) + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " iris now " + ChatText.value("spiral")
            + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(BETA) + " iris now " + ChatText.value("rows")
            + " QQgroup " + ChatText.name(ATLANTIS) + "."));
    }

    /** Each source of a ring pattern, a refusal, and a change, as before. */
    @Test
    void spinLinesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        final String server = threeGates().getEffectiveDialSpin().name().toLowerCase(Locale.ROOT);
        editEach(player, SPIN, "sideways", "pegasus");

        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " dials with " + ChatText.value("lap") + ""
            + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(BETA) + " dials with " + ChatText.value("chase")
            + oldFromGroup() + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(GAMMA) + " dials with " + ChatText.value(server)
            + " (the server default)" + "."));
        verify(player).sendMessage(ChatText.error("No ring pattern called " + ChatText.command("sideways") + "."));
        verify(player, times(4)).sendMessage(ChatText.info("Patterns are: " + oldOptions(GateEditCommand.spinNames())
            + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " now dials with " + ChatText.value("pegasus")
            + "" + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(BETA) + " now dials with " + ChatText.value("chase")
            + oldFromGroup() + "."));
    }

    /** The same lines reach a qq admin in qq. */
    @Test
    void spinLinesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        final String server = threeGates().getEffectiveDialSpin().name().toLowerCase(Locale.ROOT);
        editEach(player, SPIN, "sideways", "pegasus");

        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " spin " + ChatText.value("lap") + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(BETA) + " spin " + ChatText.value("chase")
            + " QQgroup " + ChatText.name(ATLANTIS) + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(GAMMA) + " spin " + ChatText.value(server)
            + " QQdefault."));
        verify(player).sendMessage(ChatText.error("QQ no pattern " + ChatText.command("sideways") + "."));
        verify(player, times(4)).sendMessage(ChatText.info("QQ patterns " + qqOptions(GateEditCommand.spinNames()) + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " spin now " + ChatText.value("pegasus")
            + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(BETA) + " spin now " + ChatText.value("chase")
            + " QQgroup " + ChatText.name(ATLANTIS) + "."));
    }

    /** Asks alpha (no group) and beta, refuses a group, chooses one and hands beta back to its frame. */
    private static void editGroups(final Player player)
    {
        threeGates();
        edit(player, ALPHA, GROUP);
        edit(player, BETA, GROUP);
        edit(player, ALPHA, GROUP, "Nowhere");
        edit(player, ALPHA, GROUP, "Tollan");
        edit(player, BETA, GROUP, "-clear");
    }

    /** Each group line as before, the two groups listed in name order. */
    @Test
    void groupLinesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        editGroups(player);

        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " is on group " + "(none)" + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(BETA) + " is on group " + ChatText.name(ATLANTIS) + "."));
        verify(player, times(3)).sendMessage(ChatText.info("Groups are: " + ChatText.name(ATLANTIS) + ", "
            + ChatText.name("Tollan") + "."));
        verify(player).sendMessage(ChatText.error("No material group called " + ChatText.name("Nowhere") + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " is now on group " + ChatText.name("Tollan")
            + ". Its frame blocks are untouched; this changes what the gate draws."));
        verify(player).sendMessage(ChatText.info(ChatText.name(BETA) + " follows its frame again: group " + "(none)"
            + "."));
    }

    /** The same lines reach a qq admin in qq, the groups joined as qq joins a list. */
    @Test
    void groupLinesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        editGroups(player);

        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " group QQnone."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(BETA) + " group " + ChatText.name(ATLANTIS) + "."));
        verify(player, times(3)).sendMessage(ChatText.info("QQ groups " + ChatText.name(ATLANTIS) + " | "
            + ChatText.name("Tollan") + "."));
        verify(player).sendMessage(ChatText.error("QQ no group " + ChatText.name("Nowhere") + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " now group " + ChatText.name("Tollan")
            + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(BETA) + " frame group QQnone."));
    }

    /** One group alone is listed with no separator, in either language. */
    @Test
    void aSingleGroupIsListedAlone()
    {
        MaterialGroupRegistry.load(null);
        final Player english = reading("en");
        final Player qq = reading("qq");
        gate(ALPHA);
        edit(english, ALPHA, GROUP);
        edit(qq, ALPHA, GROUP);

        verify(english).sendMessage(ChatText.info("Groups are: " + ChatText.name("Standard") + "."));
        verify(qq).sendMessage(ChatText.info("QQ groups " + ChatText.name("Standard") + "."));
    }

    /** The fields, after the usage line and after an unknown field, as before and in qq. */
    @Test
    void theFieldsReadAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qq = reading("qq");
        for (final Player player : List.of(english, qq))
        {
            edit(player, ALPHA);
            edit(player, ALPHA, "banana");
        }

        verify(english).sendMessage(ChatText.error("No field called " + ChatText.command("banana") + "."));
        verify(english, times(2)).sendMessage(ChatText.info("Fields are: " + oldOptions(GateEditCommand.fieldNames())
            + "."));
        verify(qq).sendMessage(ChatText.error("QQ no field " + ChatText.command("banana") + "."));
        verify(qq, times(2)).sendMessage(ChatText.info("QQ fields " + qqOptions(GateEditCommand.fieldNames()) + "."));
    }
}
