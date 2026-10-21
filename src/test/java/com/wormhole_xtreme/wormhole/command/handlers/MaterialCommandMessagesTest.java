package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.gate;
import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.handlers.MaterialCommand.Kind;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What the three material commands say, read by key from {@code lang/} (#26).
 *
 * <p>The old lines spliced each kind's noun into one sentence, which fixes the word order for every
 * language; now each kind has whole sentences of its own. So every kind's lines are sent through the
 * real command to an English admin, against the old splice, and to a qq admin, whose sentences name
 * the kind, so a kind reading another kind's key, or a call site reading the wrong recipient, shows.
 */
class MaterialCommandMessagesTest
{
    private static final String ALPHA = "alpha";
    private static final String PLAIN = "plain";
    private static final String DIRT = "dirt";

    /** A material each kind accepts that is not what it starts with, so a change shows. */
    private static final Map<Kind, Material> BEFORE = Map.of(Kind.PORTAL, Material.LAVA, Kind.IRIS, Material.GLASS,
        Kind.LIGHT, Material.REDSTONE_ORE);

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

    /** For each kind: a gate not in custom mode, the usage, the current value, a refusal, and a change. */
    private static void editEachKind(final Player player)
    {
        gate(PLAIN);
        final Stargate alpha = gate(ALPHA);
        alpha.setGateCustom(true);
        for (final Kind kind : Kind.values())
        {
            kind.setter().accept(alpha, BEFORE.get(kind));
            final MaterialCommand command = new MaterialCommand(kind);
            command.execute(player, new String[] { kind.command(), PLAIN });
            command.execute(player, new String[] { kind.command() });
            command.execute(player, new String[] { kind.command(), ALPHA });
            command.execute(player, new String[] { kind.command(), ALPHA, DIRT });
            command.execute(player, new String[] { kind.command(), ALPHA, kind.allowedNames().get(0) });
        }
    }

    /** The noun each kind's old lines spliced in. */
    private static String oldNoun(final Kind kind)
    {
        return switch (kind)
        {
            case PORTAL -> "portal";
            case IRIS -> "iris";
            case LIGHT -> "light";
        };
    }

    /** The advice line as the code built it before the move. */
    private static String oldValid(final Kind kind)
    {
        return ChatText.info("Valid materials are: " + kind.allowedNames().stream().map(ChatText::material)
            .collect(Collectors.joining(", ")) + ".");
    }

    @Test
    void eachKindReadsAsItDidBefore()
    {
        final Player player = reading("en");
        editEachKind(player);

        verify(player, times(3)).sendMessage(ChatText.error(ChatText.name(PLAIN) + " is not in custom mode. Turn it on with "
            + ChatText.command("/wormhole custom " + PLAIN + " true") + "."));
        for (final Kind kind : Kind.values())
        {
            final String noun = oldNoun(kind);
            verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " " + noun + " material is currently: "
                + ChatText.material(String.valueOf(BEFORE.get(kind))) + "."));
            verify(player).sendMessage(ChatText.error("Invalid " + noun + " material: " + ChatText.material(DIRT) + "."));
            verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " " + noun + " material set to: "
                + ChatText.material(kind.allowedNames().get(0)) + "."));
            verify(player, times(3)).sendMessage(oldValid(kind));
        }
    }

    @Test
    void eachKindIsInTheReadersLanguage()
    {
        final Player player = reading("qq");
        editEachKind(player);

        verify(player, times(3)).sendMessage(ChatText.error("QQ " + ChatText.name(PLAIN) + " not custom "
            + ChatText.command("/wormhole custom " + PLAIN + " true") + "."));
        for (final Kind kind : Kind.values())
        {
            final String noun = oldNoun(kind);
            verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " " + noun + " is "
                + ChatText.material(String.valueOf(BEFORE.get(kind))) + "."));
            verify(player).sendMessage(ChatText.error("QQ bad " + noun + " " + ChatText.material(DIRT) + "."));
            verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " " + noun + " set "
                + ChatText.material(kind.allowedNames().get(0)) + "."));
            verify(player, times(3)).sendMessage(ChatText.info("QQ valid "
                + String.join(" | ", kind.allowedNames().stream().map(ChatText::material).toList()) + "."));
        }
    }
}
