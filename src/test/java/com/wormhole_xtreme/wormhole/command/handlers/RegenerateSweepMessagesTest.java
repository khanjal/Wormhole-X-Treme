package com.wormhole_xtreme.wormhole.command.handlers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.GateRederivation;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@code /wormhole gate regenerate -all} says, read by key from {@code lang/} (#26).
 *
 * <p>This is the command with the grammar: a count of gates, a count of arrival points that agrees
 * with {@code was} or {@code were}, a gate or gates that light {@code its} or {@code their} chevrons,
 * and a list of names that ends in a full stop or in {@code and N more}. Each report is sent through the
 * real command twice. Once to an English player, against the concatenation the code built before the
 * move, copied from it. Once to a player whose client reads the test language qq, where every variant has
 * a wording of its own, so a call site that picks the wrong variant, reads the wrong recipient or splices a
 * word in English shows as a changed line.
 */
class RegenerateSweepMessagesTest
{
    private static final GateRederivation.LightResult SAME = GateRederivation.LightResult.UNCHANGED;
    private static final GateRederivation.LightResult REBUILT = GateRederivation.LightResult.REBUILT;
    private static final GateRederivation.LightResult BUSY = GateRederivation.LightResult.BUSY;
    private static final GateRederivation.LightResult MISFIT = GateRederivation.LightResult.DOES_NOT_FIT;

    private static final GateRederivation.LightResult[] ONE_OF_EACH_LIGHTS = {SAME, BUSY, REBUILT, MISFIT};

    private static final GateRederivation.LightResult[] TWO_OF_EACH_LIGHTS = {SAME, SAME, SAME, SAME, REBUILT, REBUILT,
        BUSY, BUSY, MISFIT, MISFIT};

    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        world = mock(World.class);
        clearGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        clearGates();
        PluginTestSupport.remove();
    }

    private static void clearGates()
    {
        for (final Stargate s : new ArrayList<>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    /** An admin whose client reads {@code locale}. */
    private static Player adminReading(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        when(player.getName()).thenReturn("Admin");
        when(player.getUniqueId()).thenReturn(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        when(player.isOp()).thenReturn(Boolean.TRUE);
        when(player.hasPermission(anyString())).thenReturn(Boolean.TRUE);
        return player;
    }

    /** How a gate fares in the sweep: whether its arrival point works out, and whether it moves. */
    private enum Exit
    {
        MOVES, STAYS, CANNOT_BE_WORKED_OUT
    }

    private Stargate gate(final String name, final Exit exit)
    {
        final Stargate made = mock(Stargate.class);
        when(made.getGateName()).thenReturn(name);
        when(made.recomputeGatePlayerTeleportLocation()).thenReturn(exit != Exit.CANNOT_BE_WORKED_OUT);
        if (exit == Exit.MOVES)
        {
            when(made.getGatePlayerTeleportLocation())
                .thenReturn(new Location(world, 1, 64, 1), new Location(world, 9, 64, 9));
        }
        else
        {
            when(made.getGatePlayerTeleportLocation()).thenReturn(new Location(world, 5, 64, 5));
        }
        StargateManager.registerStargate(made);
        return made;
    }

    /** Sweeps every registered gate, each light order coming out as {@code lights} says, else unchanged. */
    private static void sweep(final Player player, final Stargate[] gates, final GateRederivation.LightResult[] lights)
    {
        try (MockedStatic<GateRederivation> rederive = mockStatic(GateRederivation.class);
             MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            for (int i = 0; i < gates.length; i++)
            {
                final Stargate each = gates[i];
                final GateRederivation.LightResult result = lights[i];
                rederive.when(() -> GateRederivation.rebuildLightOrder(each)).thenReturn(result);
            }
            new RegenerateCommand().execute(player, new String[] {"regen", "-all"});
        }
    }

    private static String name(final String text)
    {
        return ChatText.name(text);
    }

    private static String value(final String text)
    {
        return ChatText.value(text);
    }

    /** One gate of each kind: every count is one, except the gates checked. */
    private Stargate[] oneOfEach()
    {
        return new Stargate[] {gate("moved", Exit.MOVES), gate("stuck", Exit.CANNOT_BE_WORKED_OUT),
            gate("relit", Exit.STAYS), gate("misfit", Exit.STAYS)};
    }

    /** Two gates of each kind: every count is two. */
    private Stargate[] twoOfEach()
    {
        return new Stargate[] {gate("m1", Exit.MOVES), gate("m2", Exit.MOVES),
            gate("c1", Exit.CANNOT_BE_WORKED_OUT), gate("c2", Exit.CANNOT_BE_WORKED_OUT),
            gate("r1", Exit.STAYS), gate("r2", Exit.STAYS), gate("b1", Exit.STAYS), gate("b2", Exit.STAYS),
            gate("d1", Exit.STAYS), gate("d2", Exit.STAYS)};
    }

    /** Every singular, against the old expression. */
    @Test
    void theSingularsReadAsTheyDidBefore()
    {
        final Player player = adminReading("en");

        sweep(player, oneOfEach(), ONE_OF_EACH_LIGHTS);

        verify(player).sendMessage(ChatText.info("Checked " + value("4 gates") + ". " + value("1")
            + " arrival point was out of place and has been recomputed."));
        verify(player).sendMessage(ChatText.info(ChatText.bad("1 gate")
            + " had no arrival point to work out (no world, facing or portal blocks recorded): " + name("stuck") + "."));
        verify(player).sendMessage(ChatText.info(ChatText.good("1 gate") + " now lights its chevrons in its shape's order."));
        verify(player).sendMessage(ChatText.info(ChatText.bad("1 gate") + " kept its old light order because it was"
            + " dialling or open; run it again once they are shut: " + name("stuck") + "."));
        verify(player).sendMessage(ChatText.info(ChatText.bad("1 gate") + " kept its old light order because the frame"
            + " no longer fits the shape; regen each by name, or with " + ChatText.command("-shape") + ": "
            + name("misfit") + "."));
    }

    /** Every singular reaches a qq player in qq, each choosing the one variant. */
    @Test
    void theSingularsAreInThePlayersLanguage()
    {
        final Player player = adminReading("qq");

        sweep(player, oneOfEach(), ONE_OF_EACH_LIGHTS);

        verify(player).sendMessage(ChatText.info("QQ checked " + value("4 QQgates") + " QQ moved-one " + value("1")));
        verify(player).sendMessage(ChatText.info("QQ no arrival " + ChatText.bad("1 QQgate") + " | " + name("stuck")
            + " QQ end."));
        verify(player).sendMessage(ChatText.info("QQ relit-one 1"));
        verify(player).sendMessage(ChatText.info("QQ busy-one 1 | " + name("stuck") + " QQ end."));
        verify(player).sendMessage(ChatText.info("QQ misfit-one 1 " + ChatText.command("-shape") + " | " + name("misfit")
            + " QQ end."));
    }

    /** Every plural, against the old expression, the names sorted and joined. */
    @Test
    void thePluralsReadAsTheyDidBefore()
    {
        final Player player = adminReading("en");

        sweep(player, twoOfEach(), TWO_OF_EACH_LIGHTS);

        verify(player).sendMessage(ChatText.info("Checked " + value("10 gates") + ". " + value("2")
            + " arrival points were out of place and have been recomputed."));
        verify(player).sendMessage(ChatText.info(ChatText.bad("2 gates")
            + " had no arrival point to work out (no world, facing or portal blocks recorded): " + name("c1") + ", "
            + name("c2") + "."));
        verify(player).sendMessage(ChatText.info(ChatText.good("2 gates")
            + " now light their chevrons in their shapes' order."));
        verify(player).sendMessage(ChatText.info(ChatText.bad("2 gates") + " kept their old light order because they were"
            + " dialling or open; run it again once they are shut: " + name("b1") + ", " + name("b2") + "."));
        verify(player).sendMessage(ChatText.info(ChatText.bad("2 gates") + " kept their old light order because the frame"
            + " no longer fits the shape; regen each by name, or with " + ChatText.command("-shape") + ": "
            + name("d1") + ", " + name("d2") + "."));
    }

    /** Every plural reaches a qq player in qq, the names joined by the language's separator. */
    @Test
    void thePluralsAreInThePlayersLanguage()
    {
        final Player player = adminReading("qq");

        sweep(player, twoOfEach(), TWO_OF_EACH_LIGHTS);

        verify(player).sendMessage(ChatText.info("QQ checked " + value("10 QQgates") + " QQ moved-many " + value("2")));
        verify(player).sendMessage(ChatText.info("QQ no arrival " + ChatText.bad("2 QQgates") + " | " + name("c1")
            + " / " + name("c2") + " QQ end."));
        verify(player).sendMessage(ChatText.info("QQ relit-many 2"));
        verify(player).sendMessage(ChatText.info("QQ busy-many 2 | " + name("b1") + " / " + name("b2") + " QQ end."));
        verify(player).sendMessage(ChatText.info("QQ misfit-many 2 " + ChatText.command("-shape") + " | " + name("d1")
            + " / " + name("d2") + " QQ end."));
    }

    /** One gate checked and moved: the singular gate and arrival point, against the old expression. */
    @Test
    void oneGateCheckedReadsAsItDidBefore()
    {
        final Player player = adminReading("en");

        sweep(player, new Stargate[] {gate("solo", Exit.MOVES)}, new GateRederivation.LightResult[] {SAME});

        verify(player).sendMessage(ChatText.info("Checked " + value("1 gate") + ". " + value("1")
            + " arrival point was out of place and has been recomputed."));
    }

    /** The same line reaches a qq player in qq. */
    @Test
    void oneGateCheckedIsInThePlayersLanguage()
    {
        final Player player = adminReading("qq");

        sweep(player, new Stargate[] {gate("solo", Exit.MOVES)}, new GateRederivation.LightResult[] {SAME});

        verify(player).sendMessage(ChatText.info("QQ checked " + value("1 QQgate") + " QQ moved-one " + value("1")));
    }

    /** Nothing moved is zero arrival points, which read as a plural; twenty-two busy gates list twenty. */
    @Test
    void aLongListEndsInAndMoreAsItDidBefore()
    {
        final Player player = adminReading("en");
        final List<Stargate> gates = busyGates(22);
        final GateRederivation.LightResult[] lights = new GateRederivation.LightResult[22];
        Arrays.fill(lights, BUSY);

        sweep(player, gates.toArray(new Stargate[0]), lights);

        final List<String> shown = new ArrayList<>();
        for (int i = 1; i <= 20; i++)
        {
            shown.add(name(String.format("g%02d", i)));
        }
        verify(player).sendMessage(ChatText.info("Checked " + value("22 gates") + ". " + value("0")
            + " arrival points were out of place and have been recomputed."));
        verify(player).sendMessage(ChatText.info(ChatText.bad("22 gates") + " kept their old light order because they were"
            + " dialling or open; run it again once they are shut: " + String.join(", ", shown) + ", and " + value("2")
            + " more."));
    }

    /** The same long list reaches a qq player in qq, with the language's own tail. */
    @Test
    void aLongListEndsInAndMoreInThePlayersLanguage()
    {
        final Player player = adminReading("qq");
        final List<Stargate> gates = busyGates(22);
        final GateRederivation.LightResult[] lights = new GateRederivation.LightResult[22];
        Arrays.fill(lights, BUSY);

        sweep(player, gates.toArray(new Stargate[0]), lights);

        final List<String> shown = new ArrayList<>();
        for (int i = 1; i <= 20; i++)
        {
            shown.add(name(String.format("g%02d", i)));
        }
        verify(player).sendMessage(ChatText.info("QQ busy-many 22 | " + String.join(" / ", shown) + " +" + value("2")
            + " QQ more."));
        verify(player).sendMessage(ChatText.info("QQ checked " + value("22 QQgates") + " QQ moved-many " + value("0")));
    }

    private List<Stargate> busyGates(final int count)
    {
        final List<Stargate> gates = new ArrayList<>();
        for (int i = 1; i <= count; i++)
        {
            gates.add(gate(String.format("g%02d", i), Exit.STAYS));
        }
        return gates;
    }
}
